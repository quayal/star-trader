package dev.adriangrzebyk.trader.game.adapter.dynamodb

import dev.adriangrzebyk.trader.game.loan.Loan
import dev.adriangrzebyk.trader.game.loan.LoanTerm
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/** A [Loan] as the attribute map nested in its player's item (engineering standards §12). */
internal class LoanItem(val attributes: Map<String, AttributeValue>) {

	constructor(loan: Loan) : this(
		mapOf(
			AMOUNT to number(loan.amount),
			INTEREST to number(loan.interest),
			TAKEN_IN_TURN to number(loan.takenInTurn),
			TERM to AttributeValue.fromS(loan.term.name)
		)
	)

	fun toLoan(): Loan = Loan(
		amount = attributes.attribute(AMOUNT).n().toInt(),
		interest = attributes.attribute(INTEREST).n().toInt(),
		takenInTurn = attributes.attribute(TAKEN_IN_TURN).n().toInt(),
		term = LoanTerm.valueOf(attributes.attribute(TERM).s())
	)

	private companion object {
		const val AMOUNT = "amount"
		const val INTEREST = "interest"
		const val TAKEN_IN_TURN = "takenInTurn"
		const val TERM = "term"
	}
}
