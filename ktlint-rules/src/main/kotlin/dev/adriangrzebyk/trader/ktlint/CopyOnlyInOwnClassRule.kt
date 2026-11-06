package dev.adriangrzebyk.trader.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.CALL_EXPRESSION
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtThisExpression

/**
 * Engineering standards §8: a class changes its own data; others ask it to with a method. A data class's `copy` is
 * therefore called only on `this`, so `Game` can't reach inside a `Ship` with `ship.copy(system = …)` and must call
 * `ship.jumpTo(…)` instead.
 *
 * ktlint has no type resolution, so the rule reports, by shape:
 * - `x.copy(…)` or `x?.copy(…)` with any receiver but `this`, including another instance of the same class: give that
 *   class a method and call it on the instance;
 * - a bare `copy(…)` inside `x.run { }`, `x.apply { }` or `with(x) { }`, where `copy` is `x`'s, not the enclosing
 *   class's.
 *
 * It checks domain code only. Test sources (`src/test/`) build fixtures with `copy`, and adapters (`/adapter/`) may
 * call a library's `copy`; mapping there goes through constructors (§5). Building an object with its constructor from
 * another's fields is the one bypass the rule can't see, so review checks for it (`CODE_REVIEW.md`).
 */
class CopyOnlyInOwnClassRule :
	Rule(
		ruleId = RuleId("${TraderRuleSetProvider.RULE_SET_ID}:copy-only-in-own-class"),
		about = About(maintainer = "Star Trader")
	),
	RuleAutocorrectApproveHandler {

	override fun beforeVisitChildNodes(
		node: ASTNode,
		emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
	) {
		if (node.elementType != CALL_EXPRESSION || isOutsideDomain(node)) return
		val call = node.psi as KtCallExpression
		if (call.calleeExpression?.text != "copy") return
		val qualified = call.parent as? KtQualifiedExpression
		val receiver = if (qualified != null && qualified.selectorExpression == call) qualified.receiverExpression else scopeReceiver(call)
		if (receiver == null || receiver is KtThisExpression) return
		emit(
			node.startOffset,
			"${receiver.text}.copy(…) changes another object's data: give its class a method that makes the change and " +
				"call that (engineering standards §8)",
			false
		)
	}

	/** The object a bare `copy` belongs to when it sits in `x.run { }`, `x.apply { }` or `with(x) { }`, else null. */
	private fun scopeReceiver(call: KtCallExpression): KtExpression? {
		var element: PsiElement? = call.parent
		while (element != null && element !is KtNamedFunction && element !is KtClassOrObject) {
			if (element is KtLambdaExpression) {
				receiverOf(element)?.let { return it }
			}
			element = element.parent
		}
		return null
	}

	private fun receiverOf(lambda: KtLambdaExpression): KtExpression? {
		val scope = generateSequence(lambda.parent) { it.parent }.take(3).filterIsInstance<KtCallExpression>().firstOrNull()
			?: return null
		return when (scope.calleeExpression?.text) {
			"with" -> scope.valueArguments.firstOrNull()?.getArgumentExpression()
			"run", "apply" -> (scope.parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression == scope }?.receiverExpression
			else -> null
		}
	}

	private fun isOutsideDomain(node: ASTNode): Boolean {
		val path = node.psi.containingFile.virtualFile?.path?.replace('\\', '/') ?: return false
		return "/src/test/" in path || "/adapter/" in path
	}
}
