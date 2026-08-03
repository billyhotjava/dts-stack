import { expect, test } from "vitest";
import {
	acquireOwnedSingleFlight,
	acquireSingleFlight,
	ownsSingleFlight,
	releaseOwnedSingleFlight,
	releaseSingleFlight,
	resetOwnedSingleFlight,
} from "./accessSingleFlight";

test("single-flight acquires synchronously and rejects a second click until release", () => {
	const lock = { current: false };
	expect(acquireSingleFlight(lock)).toBe(true);
	expect(acquireSingleFlight(lock)).toBe(false);
	releaseSingleFlight(lock);
	expect(acquireSingleFlight(lock)).toBe(true);
});

test("owned single-flight does not let a stale operation release its replacement", () => {
	const lock = { current: null as symbol | null };
	const oldOwner = Symbol("old-operation");
	const newOwner = Symbol("new-operation");

	expect(acquireOwnedSingleFlight(lock, oldOwner)).toBe(true);
	expect(ownsSingleFlight(lock, oldOwner)).toBe(true);
	resetOwnedSingleFlight(lock);
	expect(acquireOwnedSingleFlight(lock, newOwner)).toBe(true);

	expect(releaseOwnedSingleFlight(lock, oldOwner)).toBe(false);
	expect(ownsSingleFlight(lock, newOwner)).toBe(true);
	expect(releaseOwnedSingleFlight(lock, newOwner)).toBe(true);
	expect(lock.current).toBeNull();
});
