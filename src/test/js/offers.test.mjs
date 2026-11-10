import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	ALL_OFFERED,
	CLOSED_TEXT,
	PRICE_EMPTY,
	PRICE_ERROR,
	dictatorText,
	hasShipAt,
	isBidding,
	isClosedRefusal,
	noOffersText,
	showsYourOffers,
	offerChoices,
	offerText,
	placedText,
	refusalText,
	validatePrice,
} from '../../main/resources/static/js/offers.js';

const ME = 'me@example.com';

function game({ offers = [], positions = [], phase = 'BIDDING', ships = [] } = {}) {
	return {
		status: 'RUNNING',
		turn: { number: 1, phase, doneBidding: [] },
		board: [
			{ id: 'TAU_CETI', name: 'Tau Ceti', market: [{ goods: 'ISO' }, { goods: 'POLY' }] },
			{ id: 'GAMMA_LEPORIS', name: 'Gamma Leporis', market: [{ goods: 'POLY' }] },
		],
		players: [
			{ email: ME, ships, marketPositions: positions },
			{ email: 'other@example.com', ships: [{ name: 'Corvid', system: 'GAMMA_LEPORIS' }], marketPositions: [] },
		],
		you: { money: 300, ships: [], offers },
	};
}

describe('validatePrice', () => {
	it('accepts whole numbers from 1 to 20', () => {
		assert.deepEqual(validatePrice('1'), { price: 1 });
		assert.deepEqual(validatePrice(' 20 '), { price: 20 });
	});

	it('asks for a price when the field is empty', () => {
		assert.deepEqual(validatePrice(''), { error: PRICE_EMPTY });
		assert.deepEqual(validatePrice('   '), { error: PRICE_EMPTY });
		assert.deepEqual(validatePrice(undefined), { error: PRICE_EMPTY });
	});

	it('refuses anything outside 1 to 20 or not whole', () => {
		for (const raw of ['0', '21', '-3', '2.5', 'eight', '1e1']) {
			assert.deepEqual(validatePrice(raw), { error: PRICE_ERROR }, raw);
		}
	});
});

describe('offerChoices', () => {
	it('offers every good traded at the system, in market order', () => {
		assert.deepEqual(offerChoices(game(), 'TAU_CETI', ME), { goods: ['ISO', 'POLY'], notes: [] });
	});

	it('leaves out goods the player already has an offer on there (R33)', () => {
		const g = game({ offers: [{ system: 'TAU_CETI', goods: 'POLY', type: 'BUY', price: 8 }] });

		assert.deepEqual(offerChoices(g, 'TAU_CETI', ME), { goods: ['ISO'], notes: [] });
		assert.deepEqual(offerChoices(g, 'GAMMA_LEPORIS', ME), { goods: ['POLY'], notes: [] });
	});

	it('says so once every good there has an offer', () => {
		const g = game({ offers: [{ system: 'GAMMA_LEPORIS', goods: 'POLY', type: 'BUY', price: 8 }] });

		assert.deepEqual(offerChoices(g, 'GAMMA_LEPORIS', ME), { goods: [], notes: [ALL_OFFERED] });
	});

	it('leaves out the good the player is Dictator of there, and says why (R38)', () => {
		const g = game({ positions: [{ system: 'TAU_CETI', goods: 'POLY', tier: 'DICTATOR' }] });

		assert.deepEqual(offerChoices(g, 'TAU_CETI', ME), { goods: ['ISO'], notes: [dictatorText('POLY')] });
		assert.deepEqual(offerChoices(g, 'GAMMA_LEPORIS', ME), { goods: ['POLY'], notes: [] });
	});

	it('keeps a Wholesaler or Retailer good in the form', () => {
		const g = game({ positions: [{ system: 'TAU_CETI', goods: 'POLY', tier: 'WHOLESALER' }] });

		assert.deepEqual(offerChoices(g, 'TAU_CETI', ME).goods, ['ISO', 'POLY']);
	});

	it('gives only the Dictator line when that is the one good there', () => {
		const g = game({ positions: [{ system: 'GAMMA_LEPORIS', goods: 'POLY', tier: 'DICTATOR' }] });

		assert.deepEqual(offerChoices(g, 'GAMMA_LEPORIS', ME), { goods: [], notes: [dictatorText('POLY')] });
	});

	it('has nothing for an unknown system', () => {
		assert.deepEqual(offerChoices(game(), 'NOWHERE', ME), { goods: [], notes: [] });
	});
});

describe('texts', () => {
	it('words the Dictator refusal as the spec does', () => {
		assert.equal(dictatorText('POLY'), "You're Dictator of POLY here — you place that offer when it resolves in Trading.");
	});

	it('describes an offer and its placement', () => {
		const offer = { system: 'TAU_CETI', systemName: 'Tau Ceti', goods: 'POLY', type: 'SELL', price: 12 };

		assert.equal(offerText(offer), 'Sell POLY at 12 HT');
		assert.equal(placedText(offer, 'Tau Ceti'), 'Offer placed: Sell POLY at 12 HT, Tau Ceti. Hidden from other players until Trading.');
	});

	it('says the empty list differently once Bidding has closed', () => {
		assert.equal(noOffersText(game()), 'No offers yet this turn.');
		assert.equal(noOffersText(game({ phase: 'SHIP_JUMPS' })), 'You placed no offers this turn.');
	});

	it('hides your offers once the game is over (E05-S08-6)', () => {
		assert.equal(showsYourOffers(game()), true);
		assert.equal(showsYourOffers({ ...game({ phase: 'TRADING' }), status: 'FINISHED' }), false);
	});
});

describe('hasShipAt', () => {
	it('looks only at the player’s own ships', () => {
		const g = game({ ships: [{ name: 'Wayfarer', system: 'TAU_CETI' }] });

		assert.equal(hasShipAt(g, 'TAU_CETI', ME), true);
		assert.equal(hasShipAt(g, 'GAMMA_LEPORIS', ME), false); // another player's ship is there
	});
});

describe('isBidding', () => {
	it('is true only in a running game’s Bidding', () => {
		assert.equal(isBidding(game()), true);
		assert.equal(isBidding(game({ phase: 'TRADING' })), false);
		assert.equal(isBidding({ status: 'LOBBY', turn: null }), false);
		assert.equal(isBidding({ ...game(), status: 'FINISHED' }), false);
	});
});

describe('refusals', () => {
	it('words a close as S03-7 does', () => {
		assert.equal(isClosedRefusal(409, 'Bidding has closed'), true);
		assert.equal(refusalText(409, 'Bidding has closed', 'x'), CLOSED_TEXT);
		assert.equal(CLOSED_TEXT, "Bidding has closed — this offer wasn't placed.");
	});

	it('shows any other refusal as the server words it, or the fallback', () => {
		assert.equal(isClosedRefusal(409, 'This game is over'), false);
		assert.equal(refusalText(400, 'Price must be a whole number from 1 to 20.', 'x'), 'Price must be a whole number from 1 to 20.');
		assert.equal(refusalText(500, '', 'Try again.'), 'Try again.');
	});
});
