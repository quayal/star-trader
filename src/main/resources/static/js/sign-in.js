/*
 * Pure helpers for the sign-in screen (s10). The page itself lives in sign-in.html.
 */

/*
 * E01-S01's validation messages (LoginCodeRequestService), by the field they belong to.
 * The server checks the fields in form order and reports the first invalid one.
 */
const DETAILS_ERROR_FIELDS = {
	'Player name is required': 'playerName',
	'Corporation name is required': 'corporationName',
	'Email address is invalid': 'email',
};

/** The details field a request-code error belongs to, or null for an error about the whole form. */
export function fieldForDetailsError(message) {
	return DETAILS_ERROR_FIELDS[(message ?? '').trim()] ?? null;
}

export function codeSentMessage(email) {
	return `We sent a code to ${email}. It may take a minute.`;
}

/** The server's plain-text reason, or a fallback when it sent none. */
export async function errorText(response, fallback) {
	const text = (await response.text()).trim();
	return text || fallback;
}
