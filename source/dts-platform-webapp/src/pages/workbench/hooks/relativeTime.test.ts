import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { relativeTime } from "./relativeTime";

describe("relativeTime", () => {
	const FIXED_NOW = new Date("2026-04-24T12:00:00.000Z").getTime();

	beforeEach(() => {
		vi.useFakeTimers();
		vi.setSystemTime(FIXED_NOW);
	});

	afterEach(() => {
		vi.useRealTimers();
	});

	it("returns_empty_string_for_null_or_undefined", () => {
		expect(relativeTime(null)).toBe("");
		expect(relativeTime(undefined)).toBe("");
		expect(relativeTime("")).toBe("");
	});

	it("returns_刚刚_for_values_less_than_one_minute_old", () => {
		const thirtySecondsAgo = new Date(FIXED_NOW - 30_000).toISOString();
		expect(relativeTime(thirtySecondsAgo)).toBe("刚刚");
	});

	it("returns_minutes_for_less_than_one_hour", () => {
		const twelveMinutesAgo = new Date(FIXED_NOW - 12 * 60_000).toISOString();
		expect(relativeTime(twelveMinutesAgo)).toBe("12 分钟前");
	});

	it("returns_hours_for_less_than_one_day", () => {
		const threeHoursAgo = new Date(FIXED_NOW - 3 * 60 * 60_000).toISOString();
		expect(relativeTime(threeHoursAgo)).toBe("3 小时前");
	});

	it("returns_days_for_longer_durations", () => {
		const fiveDaysAgo = new Date(FIXED_NOW - 5 * 24 * 60 * 60_000).toISOString();
		expect(relativeTime(fiveDaysAgo)).toBe("5 天前");
	});

	it("returns_raw_value_for_future_dates", () => {
		const future = new Date(FIXED_NOW + 60_000).toISOString();
		expect(relativeTime(future)).toBe(future);
	});

	it("returns_raw_value_for_invalid_date_strings", () => {
		expect(relativeTime("not-a-date")).toBe("not-a-date");
	});
});
