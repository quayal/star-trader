package dev.adriangrzebyk.trader.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FUN
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * Engineering standards §5: a function over a value is an extension on that value (`game.toItem(version)`),
 * not a function on an `object` (`GameItemMapper.toItem(game, version)`).
 *
 * ktlint has no type resolution, so the rule goes by declaration shape:
 * - in an `object` declaration, a function with parameters or a receiver is reported, unless it `override`s;
 * - in a `companion object`, a function taking exactly one parameter of a non-built-in type is a conversion
 *   (`TradeOfferView.from(offer)`) and is reported. Factories of several values (`Game.lobby(id, turns, creator)`)
 *   and functions over built-in types only stay allowed: they have no natural receiver.
 */
class NoFunctionsOnObjectsRule :
	Rule(
		ruleId = RuleId("${TraderRuleSetProvider.RULE_SET_ID}:no-functions-on-objects"),
		about = About(maintainer = "Star Trader")
	),
	RuleAutocorrectApproveHandler {

	override fun beforeVisitChildNodes(
		node: ASTNode,
		emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
	) {
		if (node.elementType != FUN) return
		val function = node.psi as? KtNamedFunction ?: return
		val owner = function.containingClassOrObject as? KtObjectDeclaration ?: return
		if (owner.isObjectLiteral() || function.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return

		val message = if (owner.isCompanion()) companionMessage(function) else objectMessage(function, owner)
		if (message != null) {
			emit(function.nameIdentifier?.textOffset ?: node.startOffset, message, false)
		}
	}

	private fun objectMessage(function: KtNamedFunction, owner: KtObjectDeclaration): String? {
		if (function.valueParameters.isEmpty() && function.receiverTypeReference == null) return null
		return "${owner.name}.${function.name} works on a value, so it belongs outside the object: an extension on " +
			"that value's type, or a top-level function when the value is a built-in type (engineering standards §5)"
	}

	private fun companionMessage(function: KtNamedFunction): String? {
		val parameter = function.valueParameters.singleOrNull() ?: return null
		val type = simpleTypeName(parameter) ?: return null
		if (type in BUILT_IN_TYPES) return null
		return "${function.name} converts a $type, so make it an extension on $type " +
			"(e.g. $type.to${function.containingClassOrObject?.containingClassOrObject?.name}()), " +
			"not a companion function (engineering standards §5)"
	}

	/** The parameter type's simple name, or null for function types and other shapes with no natural receiver. */
	private fun simpleTypeName(parameter: KtParameter): String? {
		var element = parameter.typeReference?.typeElement
		if (element is KtNullableType) element = element.innerType
		if (element is KtFunctionType) return null
		return (element as? KtUserType)?.referencedName
	}

	private companion object {
		val BUILT_IN_TYPES = setOf(
			"String", "CharSequence", "Char", "Boolean",
			"Byte", "Short", "Int", "Long", "Float", "Double", "Number",
			"UByte", "UShort", "UInt", "ULong",
			"Any", "Unit", "Nothing"
		)
	}
}
