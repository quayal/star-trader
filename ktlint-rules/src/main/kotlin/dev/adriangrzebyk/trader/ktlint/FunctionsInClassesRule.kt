package dev.adriangrzebyk.trader.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FUN
import com.pinterest.ktlint.rule.engine.core.api.ElementType.PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * Engineering standards §5: a function that works on one of our types belongs in that type's class. A function
 * over a `Game` is a method of `Game`; mapping between the domain and an adapter is a constructor or method of the
 * adapter-side class (`GameResponseDto(game, viewerEmail)`). A function no class owns may be top-level. Kotlin
 * `object` declarations aren't namespaces for functions, and extensions are only for library types we can't edit.
 *
 * ktlint has no type resolution, so the rule goes by declaration shape. It reports:
 * - in an `object` declaration, a function with parameters or a receiver, unless it `override`s;
 * - in a `companion object`, a function taking exactly one parameter of a non-built-in type: a conversion
 *   (`TradeOfferView.from(offer)`), which becomes a constructor. Factories of several values
 *   (`Game.lobby(id, turns, creator)`) and functions over built-in types only stay allowed;
 * - a top-level function whose first parameter is a project type;
 * - an extension function or property on a project type.
 *
 * A project type is one imported from [PROJECT_PACKAGE], written fully qualified under it, or used without an
 * import and not one of Kotlin's default-imported types (so it's from the same package).
 */
class FunctionsInClassesRule :
	Rule(
		ruleId = RuleId("${TraderRuleSetProvider.RULE_SET_ID}:functions-in-classes"),
		about = About(maintainer = "Star Trader")
	),
	RuleAutocorrectApproveHandler {

	override fun beforeVisitChildNodes(
		node: ASTNode,
		emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
	) {
		if (node.elementType != FUN && node.elementType != PROPERTY) return
		val declaration = node.psi as? KtCallableDeclaration ?: return
		if (declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return

		val message = when {
			declaration is KtNamedFunction -> functionMessage(declaration)
			else -> extensionMessage(declaration)
		}
		if (message != null) {
			emit(declaration.nameIdentifier?.textOffset ?: node.startOffset, message, false)
		}
	}

	private fun functionMessage(function: KtNamedFunction): String? {
		val owner = function.containingClassOrObject
		return when {
			owner is KtObjectDeclaration && owner.isCompanion() -> companionMessage(function) ?: extensionMessage(function)
			owner is KtObjectDeclaration && !owner.isObjectLiteral() -> objectMessage(function, owner)
			owner == null && function.parent is KtFile && function.receiverTypeReference == null -> topLevelMessage(function)
			else -> extensionMessage(function)
		}
	}

	private fun objectMessage(function: KtNamedFunction, owner: KtObjectDeclaration): String? {
		if (function.valueParameters.isEmpty() && function.receiverTypeReference == null) return null
		return "${owner.name}.${function.name} works on a value, so it belongs in that value's class " +
			"(engineering standards §5)"
	}

	private fun companionMessage(function: KtNamedFunction): String? {
		val parameter = function.valueParameters.singleOrNull() ?: return null
		val type = simpleName(parameter.typeReference) ?: return null
		if (type in BUILT_IN_TYPES) return null
		val built = function.containingClassOrObject?.containingClassOrObject?.name
		return "${function.name} converts a $type, so make it a constructor: $built(${parameter.name}: $type) " +
			"(engineering standards §5)"
	}

	/**
	 * A top-level function is fine when no class owns it: it assembles something from several inputs, or works on
	 * built-in or library types. One whose first parameter is a project type works on that type, so it's reported.
	 * A project type in a later parameter isn't caught here; reviews catch those (CODE_REVIEW.md).
	 */
	private fun topLevelMessage(function: KtNamedFunction): String? {
		val first = function.valueParameters.firstOrNull()?.typeReference ?: return null
		val type = projectTypeName(function, first) ?: return null
		return "${function.name} works on a $type, so make it a method of $type, or, when it maps to an adapter " +
			"type, a constructor or method of the adapter-side class (engineering standards §5)"
	}

	private fun extensionMessage(declaration: KtCallableDeclaration): String? {
		val receiver = declaration.receiverTypeReference ?: return null
		val type = projectTypeName(declaration, receiver) ?: return null
		return "${declaration.name} extends $type, a class of this project: declare it in $type, or, when it maps to " +
			"an adapter type, as a constructor or method of the adapter-side class (engineering standards §5)"
	}

	/** The receiver's name when it's one of this project's types, judged by the file's imports; null otherwise. */
	private fun projectTypeName(declaration: KtCallableDeclaration, receiver: KtTypeReference): String? {
		val userType = userType(receiver) ?: return null
		if (userType.qualifier != null) {
			return userType.text.takeIf { it.startsWith("$PROJECT_PACKAGE.") }?.substringBefore('<')
		}
		val name = userType.referencedName ?: return null
		if (declaration.typeParameters.any { it.name == name }) return null
		val import = (declaration.containingFile as KtFile).importDirectives.firstOrNull { directive ->
			(directive.aliasName ?: directive.importedFqName?.shortName()?.asString()) == name
		}
		return when {
			import != null -> name.takeIf { import.importedFqName?.asString()?.startsWith("$PROJECT_PACKAGE.") == true }
			name in KOTLIN_DEFAULT_TYPES -> null
			else -> name
		}
	}

	private fun simpleName(reference: KtTypeReference?): String? = userType(reference)?.referencedName

	/** The written type without nullability; null for function types and other shapes with no class to name. */
	private fun userType(reference: KtTypeReference?): KtUserType? {
		var element = reference?.typeElement
		if (element is KtNullableType) element = element.innerType
		if (element is KtFunctionType) return null
		return element as? KtUserType
	}

	private companion object {
		const val PROJECT_PACKAGE = "dev.adriangrzebyk.trader"

		val BUILT_IN_TYPES = setOf(
			"String", "CharSequence", "Char", "Boolean",
			"Byte", "Short", "Int", "Long", "Float", "Double", "Number",
			"UByte", "UShort", "UInt", "ULong",
			"Any", "Unit", "Nothing"
		)

		/** Types usable without an import (kotlin.*, kotlin.collections.*, kotlin.text.*, java.lang.*, …). */
		val KOTLIN_DEFAULT_TYPES = BUILT_IN_TYPES + setOf(
			"Array", "BooleanArray", "ByteArray", "CharArray", "ShortArray", "IntArray", "LongArray", "FloatArray",
			"DoubleArray", "Iterable", "MutableIterable", "Collection", "MutableCollection", "List", "MutableList",
			"Set", "MutableSet", "Map", "MutableMap", "ArrayList", "HashMap", "HashSet", "LinkedHashMap",
			"LinkedHashSet", "Sequence", "Pair", "Triple", "Result", "Comparable", "Comparator", "Enum", "Lazy",
			"IntRange", "LongRange", "CharRange", "ClosedRange", "Regex", "StringBuilder", "Appendable", "Throwable",
			"Exception", "RuntimeException", "Error", "Iterator", "MutableIterator", "Thread", "Class", "Object"
		)
	}
}
