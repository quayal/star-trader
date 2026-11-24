/*
 * The Loan box on the Assets screen (R90–R95): what the viewer owes, the interest each turn, and when it is paid, as plain
 * sentences with the figures that matter in bold. It reads `you.loan` from GET /api/games/{id}: `loan` is the open loan or
 * null, `interestDue` and `interestPaid` say where this turn's interest stands (BACKWARD_COMPATIBILITY.md §6). The server
 * decides everything; this only words it.
 */

const plain = text => ({ text, strong: false });
const bold = text => ({ text, strong: true });

/**
 * The turn the loan is collected when its Investment closes (R94): the server's `dueTurn`, or, from a server that doesn't
 * send it, the last turn of interest: this one plus the turns left, less one when this turn's interest is still to come.
 */
function dueTurn(loan, turn) {
	return loan.dueTurn ?? turn + loan.turnsLeft - 1;
}

/**
 * The box's lines for [loan] (`you.loan.loan`) with [state] (`you.loan`) in turn [turn], each a list of `{ text, strong }`
 * parts, and a [note]; null without an open loan.
 */
export function loanBox(loan, state, turn) {
	if (!loan) {
		return null;
	}
	const left = loan.turnsLeft === 0 ? 'none left' : `${loan.turnsLeft} ${loan.turnsLeft === 1 ? 'turn' : 'turns'} left`;
	const lines = [
		[plain('You owe '), bold(`${loan.amount} HT`)],
		[plain('Interest '), bold(`${loan.interest} HT`), plain(` a turn, ${left}`)],
		[plain('The loan is paid in full in '), bold(`turn ${dueTurn(loan, turn)}`), plain(', as Investment closes')],
	];
	if (state?.interestDue) {
		lines.push([plain(`Turn ${turn}’s interest is `), bold(state.interestPaid ? 'paid' : 'due in Investment')]);
	}
	return { lines, note: `Repaying early costs ${loan.repayment} HT. An open loan is subtracted from your final money.` };
}
