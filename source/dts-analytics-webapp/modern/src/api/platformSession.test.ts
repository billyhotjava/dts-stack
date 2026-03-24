import assert from "node:assert/strict";
import test from "node:test";
import { computeNextRefreshDelayMs } from "./platformSession";

function createJwt(expSeconds: number): string {
	const encode = (value: unknown) =>
		Buffer.from(JSON.stringify(value))
			.toString("base64")
			.replace(/\+/g, "-")
			.replace(/\//g, "_")
			.replace(/=+$/g, "");
	return `${encode({ alg: "none", typ: "JWT" })}.${encode({ exp: expSeconds })}.signature`;
}

test("computeNextRefreshDelayMs refreshes before short-lived token expiry", () => {
	const originalNow = Date.now;
	Date.now = () => 1_700_000_000_000;

	try {
		const token = createJwt(Math.floor(Date.now() / 1000) + 3 * 60);
		const delay = computeNextRefreshDelayMs(token, 30);
		assert.equal(delay, 2 * 60 * 1000);
	} finally {
		Date.now = originalNow;
	}
});

test("computeNextRefreshDelayMs respects short configured session timeout when token is opaque", () => {
	const delay = computeNextRefreshDelayMs("opaque-portal-token", 3);
	assert.equal(delay, 90_000);
});

test("computeNextRefreshDelayMs defaults to four minutes when timeout is absent", () => {
	const delay = computeNextRefreshDelayMs("opaque-portal-token", 30);
	assert.equal(delay, 4 * 60 * 1000);
});
