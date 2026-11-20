/*
 * Pure helpers for the Opportunity phase (R102), sabotage (R97–R101), agents (R103–R108) and loans (R90–R96), all played
 * on Infrastructure (D12, D24). They read GET /api/games/{id}: `turn.opportunist`, `turn.doneOpportunity` and
 * `turn.owedChoices` are public; `you.agents`, `you.loan`, `you.sabotagesOrdered`, `you.sabotagesSuffered`,
 * `you.sabotagedWarehouse`, `you.agentOffers` and `you.opportunity` are the viewer's alone (BACKWARD_COMPATIBILITY.md §6).
 * The server decides everything; these only word and arrange what it sent.
 */

export const DONE_OPPORTUNITY_BUTTON = "I'm done with opportunities";
export const NOT_OPPORTUNITY_TEXT = 'Opportunity comes after Trading.';
export const OPENS_IN_INVESTMENT = 'Opens in Investment';

/** R97: a sabotage costs this many HT per point of the target system's legal system. */
export const SABOTAGE_COST_PER_LEVEL = 20;

/** R103: recruiting pays 10 to 60 HT; 2d6 × 5 at or below it succeeds. */
export const MIN_RECRUIT = 10;
export const MAX_RECRUIT = 60;

const LOCATION_NAMES = { PORT: 'Port', PLANET: 'Planet', SPACE: 'Space', SHIPYARD: 'Shipyard' };

/** Whether [game] is running and in this turn's Opportunity (R102). */
export function isOpportunity(game) {
	return game.status === 'RUNNING' && game.turn?.phase === 'OPPORTUNITY';
}

function corporation(game, id) {
	return game.players.find(p => p.id === id)?.corporationName ?? 'another player';
}

/** Whether it's [playerId]'s Opportunity turn (R102). */
export function isMyOpportunityTurn(game, playerId) {
	return isOpportunity(game) && game.turn.opportunist === playerId;
}

/**
 * The Opportunity status line for [playerId] (R102): their turn, whose turn it is and that theirs comes later
 * ("Your turn comes after Vega Lines"), that they're done, or whose owed choice the phase still waits for.
 */
export function opportunityStatusText(game, playerId) {
	if (!isOpportunity(game)) {
		return '';
	}
	const owed = game.turn.owedChoices ?? [];
	if (game.turn.opportunist === null || game.turn.opportunist === undefined) {
		if (owed.includes(playerId)) {
			return 'Place the sabotage hits on your ship: Investment waits for you.';
		}
		return owed.length ? `Waiting for ${owed.map(id => corporation(game, id)).join(', ')} to place sabotage hits.` : '';
	}
	if (game.turn.opportunist === playerId) {
		return "It's your Opportunity turn: collect first, then sabotage or deal in agents, and say when you're done.";
	}
	if ((game.turn.doneOpportunity ?? []).includes(playerId)) {
		return `You're done with opportunities. ${corporation(game, game.turn.opportunist)} is on turn.`;
	}
	return `Your turn comes after ${corporation(game, game.turn.opportunist)}.`;
}

/** What the sabotage form's submit button waits for out of turn (R102: forms can be filled in, submits wait). */
export function waitTurnText(game, playerId) {
	return isMyOpportunityTurn(game, playerId) ? '' : opportunityStatusText(game, playerId);
}

// ── Sabotage (R97–R101) ──

/**
 * Every asset of other players the viewer could sabotage (R97): ships in Port, warehouses, factories. Each is
 * `{ value, label, playerId, kind, system, ship, goods }`, `value` naming it in a select.
 */
export function sabotageTargets(game, playerId) {
	return game.players.filter(p => p.id !== playerId).flatMap(p => [
		...p.ships.filter(s => s.location === 'PORT').map(s => ({
			playerId: p.id, kind: 'SHIP', system: s.system, systemName: s.systemName, ship: s.name, goods: null,
			label: `${p.corporationName} — ${s.name} in Port at ${s.systemName}`,
		})),
		...p.warehouses.map(w => ({
			playerId: p.id, kind: 'WAREHOUSE', system: w.system, systemName: systemName(game, w.system), ship: null, goods: null,
			label: `${p.corporationName} — warehouse at ${systemName(game, w.system)}`,
		})),
		...p.factories.map(f => ({
			playerId: p.id, kind: 'FACTORY', system: f.system, systemName: systemName(game, f.system), ship: null, goods: f.goods,
			size: f.size,
			label: `${p.corporationName} — ${f.goods} factory at ${systemName(game, f.system)}`,
		})),
	]).map(target => ({ ...target, value: [target.playerId, target.kind, target.system, target.ship ?? target.goods ?? ''].join('|') }));
}

function systemName(game, id) {
	return game.board.find(s => s.id === id)?.name ?? id;
}

/** The players the viewer may name to take the blame for a sabotage on [targetPlayerId]'s asset (R97); none in a two-player game. */
export function blameChoices(game, playerId, targetPlayerId) {
	if (game.players.length === 2) {
		return [];
	}
	return game.players.filter(p => p.id !== playerId && p.id !== targetPlayerId).map(p => ({ id: p.id, name: p.corporationName }));
}

/** What sabotaging [target] costs (R97): 20 HT × its system's legal system, from the board. */
export function sabotageCost(game, target) {
	const system = game.board.find(s => s.id === target.system);
	return SABOTAGE_COST_PER_LEVEL * (system?.legalSystem ?? 0);
}

/**
 * The modifiers shown before confirming (R97 criterion 5): "Effect +3 criminal −4 factory units" and "Reputation +3
 * criminal". [criminal] is the viewer's criminal connections; Shorty is secret even to the form, so it's not counted.
 */
export function sabotageModifiers(criminal, target) {
	const sign = n => (n < 0 ? `−${-n}` : `+${n}`);
	const factory = target?.kind === 'FACTORY' ? ` −${target.size} factory units` : '';
	return { effect: `Effect ${sign(criminal)} criminal${factory}`, reputation: `Reputation ${sign(criminal)} criminal` };
}

/** One of the viewer's sabotages as their own result line (R101): the rolls and what came of them. */
export function sabotageResultText(game, sabotage) {
	const target = sabotage.kind === 'SHIP' ? sabotage.ship : sabotage.kind === 'WAREHOUSE' ? 'the warehouse' : `the ${sabotage.goods} factory`;
	const where = `${corporation(game, sabotage.targetPlayerId)}'s ${target === 'the warehouse' ? 'warehouse' : target.replace(/^the /, '')} at ${sabotage.systemName}`;
	if (sabotage.foiledBy) {
		return `Your sabotage of ${where} was foiled (${sabotage.cost} HT spent).`;
	}
	const effect = sabotage.effect === 0 ? 'no effect'
		: sabotage.kind === 'SHIP' ? `${sabotage.effect} ${sabotage.effect === 1 ? 'hit' : 'hits'}`
		: sabotage.kind === 'WAREHOUSE' ? `${sabotage.effect} u missing`
		: `disabled for ${sabotage.effect} ${sabotage.effect === 1 ? 'turn' : 'turns'}`;
	const reputation = sabotage.blamed ? `${corporation(game, sabotage.blamedId)} took the blame`
		: sabotage.reputationLoss ? `you lost ${sabotage.reputationLoss} reputation` : 'nobody suspects you';
	return `Sabotage of ${where}: effect roll ${sabotage.effectRoll} → ${sabotage.effectTotal}: ${effect}; reputation roll ${sabotage.reputationRoll} → ${sabotage.reputationTotal}: ${reputation}.`;
}

/** A sabotage on the viewer's asset (R101), never naming who did it. */
export function sufferedSabotageText(sabotage) {
	const target = sabotage.kind === 'SHIP' ? sabotage.ship : sabotage.kind === 'WAREHOUSE' ? 'Your warehouse' : `Your ${sabotage.goods} factory`;
	const lead = sabotage.kind === 'SHIP' ? `${target} at ${sabotage.systemName}` : `${target} at ${sabotage.systemName}`;
	if (sabotage.foiled) {
		return `A sabotage at ${sabotage.systemName} was foiled by Silent.`;
	}
	const lost = Object.entries(sabotage.lost ?? {}).map(([goods, units]) => `${units} ${goods}`).join(', ');
	if (sabotage.kind === 'SHIP') {
		const hits = `${sabotage.effect} ${sabotage.effect === 1 ? 'hit' : 'hits'}`;
		return sabotage.awaitingHits ? `${lead} was sabotaged: place ${hits}.` : `${lead} was sabotaged: ${hits}${lost ? `, lost ${lost}` : ''}.`;
	}
	if (sabotage.kind === 'WAREHOUSE') {
		return lost ? `${lead} was sabotaged: ${lost} missing.` : `${lead} was sabotaged.`;
	}
	return `${lead} was sabotaged: disabled for ${sabotage.effect} ${sabotage.effect === 1 ? 'turn' : 'turns'}.`;
}

// ── Agents (R103–R108) ──

/** P(2d6 × 5 ≤ [amount]) as "21/36", for the recruit form (R103). */
export function recruitOdds(amount) {
	let ways = 0;
	for (let a = 1; a <= 6; a++) {
		for (let b = 1; b <= 6; b++) {
			if ((a + b) * 5 <= amount) ways++;
		}
	}
	return `${ways}/36`;
}

/** A whole amount from 10 to 60, or the error the server would give. */
export function validateRecruit(text) {
	const value = String(text ?? '').trim();
	if (!/^\d+$/.test(value) || Number(value) < MIN_RECRUIT || Number(value) > MAX_RECRUIT) {
		return { error: `Pay a whole number from ${MIN_RECRUIT} to ${MAX_RECRUIT} HT.` };
	}
	return { amount: Number(value) };
}

/** Where the viewer may start a new agent (R103): aboard a ship out of the yard, or on its own where they have a ship or warehouse. */
export function agentStarts(game) {
	const ships = (game.you?.ships ?? []).filter(s => s.location !== 'SHIPYARD');
	const systems = [...new Set([...ships.map(s => s.system), ...(game.you?.warehouses ?? []).map(w => w.system)])];
	return [
		...ships.map(s => ({ value: `ship|${s.name}`, label: `Aboard ${s.name} (${s.systemName})`, body: { ship: s.name } })),
		...systems.flatMap(system => ['PORT', 'PLANET'].map(location => ({
			value: `own|${system}|${location}`,
			label: `On its own: ${LOCATION_NAMES[location]} at ${systemName(game, system)}`,
			body: { system, location },
		}))),
	];
}

/** Where [agent], one of the viewer's (`you.agents`), stands: its ship's place when aboard one. */
export function agentPlace(game, agent) {
	if (agent.ship) {
		const ship = (game.you?.ships ?? []).find(s => s.name === agent.ship);
		return ship ? { system: ship.system, location: ship.location } : null;
	}
	return { system: agent.system, location: agent.location };
}

/** Where the viewer's [agent] can move now (R104): aboard another of their ships at the same place, or off its ship there. */
export function agentMoves(game, agent) {
	const place = agentPlace(game, agent);
	if (!place) {
		return [];
	}
	const boards = (game.you?.ships ?? [])
		.filter(s => s.name !== agent.ship && s.system === place.system && s.location === place.location && s.location !== 'SHIPYARD')
		.map(s => ({ value: `ship|${s.name}`, label: `Board ${s.name}`, body: { ship: s.name } }));
	const off = agent.ship && (place.location === 'PORT' || place.location === 'PLANET')
		? [{ value: `own|${place.system}|${place.location}`, label: `Step off (${LOCATION_NAMES[place.location]})`, body: { system: place.system, location: place.location } }]
		: [];
	return [...boards, ...off];
}

/** Where one of the viewer's agents is, for its line: "aboard Starfarer" or "on its own in Port at Tau Ceti". */
export function agentWhereText(game, agent) {
	if (agent.ship) {
		return `aboard ${agent.ship}`;
	}
	return `on its own ${agent.location === 'PLANET' ? 'on the Planet' : 'in Port'} at ${systemName(game, agent.system)}`;
}

/** A player's public agent lines (R105): "1 agent in Port at Tau Ceti", and per ship "Starfarer: 2 agents aboard". */
export function publicAgentLines(player) {
	const agents = n => `${n} ${n === 1 ? 'agent' : 'agents'}`;
	const own = (player.agentsOnOwn ?? []).map(a => `${agents(a.count)} ${a.location === 'PLANET' ? 'on the Planet' : 'in Port'} at ${a.systemName}`);
	const aboard = (player.ships ?? []).filter(s => s.agents > 0).map(s => `${s.name}: ${agents(s.agents)} aboard`);
	return [...own, ...aboard];
}

/** An agent offer's line for the viewer (R106): the seller sees which agent, the buyer only where it is. */
export function agentOfferText(game, offer, playerId) {
	const where = offer.systemName ? ` (${offer.location === 'PLANET' ? 'Planet' : 'Port'} at ${offer.systemName})` : '';
	if (offer.sellerId === playerId) {
		return `You offered ${offer.agent} to ${corporation(game, offer.buyerId)} for ${offer.price} HT${where}.`;
	}
	return `${corporation(game, offer.sellerId)} offers you an agent for ${offer.price} HT${where}.`;
}

// ── Loans (R90–R96) ──

/** The interest a turn for borrowing [amount] over a term with [divisor] at [reputation] (R91): rounded down, at least [min]. */
export function loanInterest(amount, divisor, reputation, min) {
	return Math.max(min, Math.floor((amount - reputation) / divisor));
}

/** The loan form's preview for [text] (R91): "4 turns: 14 HT a turn · 8 turns: 28 HT a turn", or an error. */
export function loanPreview(text, loan, reputation) {
	const value = String(text ?? '').trim();
	if (!/^\d+$/.test(value) || Number(value) < 1) {
		return { error: 'Enter a whole number of HT, 1 or more.' };
	}
	const amount = Number(value);
	if (amount > loan.cap) {
		return { error: `You can borrow at most ${loan.cap} HT.` };
	}
	const terms = loan.terms.map(term => ({ turns: term.turns, interest: loanInterest(amount, term.divisor, reputation, loan.minInterest) }));
	return { amount, terms, text: terms.map(t => `${t.turns} turns: ${t.interest} HT a turn`).join(' · ') };
}

/** The open loan's summary (R92, R94, R95). */
export function openLoanText(loan) {
	const open = loan.loan;
	const due = loan.interestDue ? (loan.interestPaid ? "This turn's interest is paid." : `This turn's interest (${open.interest} HT) is unpaid.`) : '';
	const fallsDue = open.fallsDue ? ` Your loan (${open.amount} HT) falls due when this Investment closes.` : '';
	return `You owe ${open.amount} HT, ${open.interest} HT interest a turn, ${open.turnsLeft} ${open.turnsLeft === 1 ? 'payment' : 'payments'} left. ${due}${fallsDue}`.trim();
}

/** R92 criterion 8: the warning before signalling done with interest unpaid, or ''. */
export function unpaidInterestWarning(game) {
	const loan = game.you?.loan;
	if (!loan?.loan || !loan.interestDue || loan.interestPaid) {
		return '';
	}
	return `Your loan interest (${loan.loan.interest} HT) is unpaid: −4 reputation, and it's added to your loan.`;
}

/** A factory's disabled note (R99), or '': "disabled, 2 more turns". */
export function disabledText(factory) {
	if (!factory.disabledTurns) {
		return '';
	}
	return `disabled, ${factory.disabledTurns} more ${factory.disabledTurns === 1 ? 'turn' : 'turns'}`;
}
