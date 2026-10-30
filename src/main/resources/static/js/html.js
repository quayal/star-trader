/*
 * Escapes text for an HTML template string. Any value a player typed (names, for one) must go
 * through this before it reaches innerHTML: the session token in localStorage is readable by
 * any script that gets onto the page.
 */
export function escapeHtml(value) {
	return String(value)
		.replaceAll('&', '&amp;')
		.replaceAll('<', '&lt;')
		.replaceAll('>', '&gt;')
		.replaceAll('"', '&quot;')
		.replaceAll("'", '&#39;');
}
