/** `legacy-browser` installs crypto.randomUUID for Chrome 95; retain a fallback for isolated embeds/tests. */
export const newModelingIdempotencyKey = () =>
	typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
		? crypto.randomUUID()
		: `modeling-${Date.now()}-${Math.random().toString(16).slice(2)}`;
