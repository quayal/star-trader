/*
 * Every mention of a good in its own colour (creator, 2026-11-20): on the Deck, in the log, in notices, dialogs and
 * tables, not only the goods columns. Rather than each render wrapping its goods, one pass finds the words POLY, CHIPS,
 * FOOD and ISO in the page's text and wraps each in `.good .good--<name>`; the text itself doesn't change, so
 * `textContent` comparisons and what a screen reader reads stay the same.
 */

export const GOODS = ['POLY', 'CHIPS', 'FOOD', 'ISO'];

// Not global: a /g regex keeps lastIndex between calls, and a test() that left it past a match made matchAll() miss
// that match, so the node was swapped for an identical one and the observer ran again forever.
const GOODS_WORD = /\b(POLY|CHIPS|FOOD|ISO)\b/;

/**
 * [text] split into plain strings and `{ goods }` parts for each goods name in it, in order; `[text]` when it names none.
 * Only the names as the game writes them, whole and in capitals: "ISO" is a good, "ISOLATED" and "food" aren't.
 */
export function goodsParts(text) {
	const parts = [];
	let last = 0;
	for (const match of text.matchAll(new RegExp(GOODS_WORD.source, 'g'))) {
		if (match.index > last) {
			parts.push(text.slice(last, match.index));
		}
		parts.push({ goods: match[1] });
		last = match.index + match[0].length;
	}
	if (last < text.length || parts.length === 0) {
		parts.push(text.slice(last));
	}
	return parts;
}

// Not inside these: form controls can't hold a span, the map's SVG colours its own goods, an already-coloured good is
// done, and a filled button's background would hide the colour.
const SKIP = 'script, style, option, select, textarea, input, svg, .good, .btn';

/** Wraps every goods name in the text under [root] in its colour. */
export function colourGoods(root) {
	const document = root.ownerDocument ?? root;
	const walker = document.createTreeWalker(root, 4 /* NodeFilter.SHOW_TEXT */);
	const found = [];
	for (let node = walker.nextNode(); node; node = walker.nextNode()) {
		if (GOODS_WORD.test(node.data) && !node.parentElement?.closest(SKIP)) {
			found.push(node);
		}
	}
	for (const node of found) {
		const parts = goodsParts(node.data);
		if (parts.every(part => typeof part === 'string')) {
			continue; // nothing to wrap: replacing the node would only wake the observer again
		}
		const pieces = parts.map(part => {
			if (typeof part === 'string') {
				return document.createTextNode(part);
			}
			const span = document.createElement('span');
			span.className = `good good--${part.goods.toLowerCase()}`;
			span.textContent = part.goods;
			return span;
		});
		node.replaceWith(...pieces);
	}
}

/** Keeps [root] coloured as the page redraws it; returns the observer. */
export function keepGoodsColoured(root) {
	colourGoods(root);
	const observer = new MutationObserver(records => {
		const roots = new Set();
		for (const record of records) {
			const target = record.type === 'characterData' ? record.target.parentNode : record.target;
			if (target) {
				roots.add(target);
			}
		}
		for (const target of roots) {
			if (target.isConnected) {
				colourGoods(target);
			}
		}
	});
	observer.observe(root, { childList: true, characterData: true, subtree: true });
	return observer;
}
