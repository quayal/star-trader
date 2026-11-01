package dev.adriangrzebyk.trader.ktlint

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

/** This project's own ktlint rules; the root build runs them with the standard ones. */
class TraderRuleSetProvider : RuleSetProviderV3(RuleSetId(RULE_SET_ID)) {

	override fun getRuleProviders(): Set<RuleProvider> = setOf(RuleProvider { NoFunctionsOnObjectsRule() })

	companion object {
		const val RULE_SET_ID = "trader"
	}
}
