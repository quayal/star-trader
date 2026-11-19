import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { goodsParts } from '../../main/resources/static/js/goods-colour.js';

describe('goods in their colour wherever they are named (creator, 2026-11-20)', () => {
	it('splits out each goods name, keeping the text around it', () => {
		assert.deepEqual(goodsParts('Bought 4 ISO and 2 CHIPS at Tau Ceti'), ['Bought 4 ', { goods: 'ISO' }, ' and 2 ', { goods: 'CHIPS' }, ' at Tau Ceti']);
		assert.deepEqual(goodsParts('POLY'), [{ goods: 'POLY' }]);
		assert.deepEqual(goodsParts('Mu Herculis · FOOD'), ['Mu Herculis · ', { goods: 'FOOD' }]);
	});

	it('leaves text that names no good, part of a word or lower case, as it is', () => {
		assert.deepEqual(goodsParts('ISOLATED food'), ['ISOLATED food']);
		assert.deepEqual(goodsParts(''), ['']);
	});
});

describe('goodsParts called again and again (the loop of 2026-11-20)', () => {
	it('finds the same goods every time, whatever was matched before', () => {
		for (let i = 0; i < 3; i++) {
			assert.deepEqual(goodsParts('ISO at Tau Ceti'), [{ goods: 'ISO' }, ' at Tau Ceti']);
		}
	});
});
