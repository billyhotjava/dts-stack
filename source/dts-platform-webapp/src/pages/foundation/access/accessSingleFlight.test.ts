import { expect, test } from "vitest";
import { acquireSingleFlight, releaseSingleFlight } from "./accessSingleFlight";

test("single-flight acquires synchronously and rejects a second click until release", () => {
	const lock = { current: false };
	expect(acquireSingleFlight(lock)).toBe(true);
	expect(acquireSingleFlight(lock)).toBe(false);
	releaseSingleFlight(lock);
	expect(acquireSingleFlight(lock)).toBe(true);
});
