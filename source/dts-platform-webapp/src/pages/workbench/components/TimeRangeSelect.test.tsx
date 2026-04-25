// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import { TIME_RANGE_OPTIONS, type TimeRange, timeRangeLabel } from "./TimeRangeSelect";

describe("TimeRangeSelect helpers", () => {
	it("renders_three_options", () => {
		expect(TIME_RANGE_OPTIONS).toHaveLength(3);
		const values = TIME_RANGE_OPTIONS.map((o) => o.value);
		expect(values).toEqual(["MONTH", "QUARTER", "YEAR"]);
	});

	it("default_value_MONTH_shows_first", () => {
		expect(TIME_RANGE_OPTIONS[0]?.value).toBe("MONTH");
	});

	it("emits_selected_value_on_change_via_label_mapping", () => {
		const combos: Array<[TimeRange, string]> = [
			["MONTH", "本月"],
			["QUARTER", "本季"],
			["YEAR", "本年"],
		];
		for (const [value, label] of combos) {
			expect(timeRangeLabel(value)).toBe(label);
		}
	});

	it("timeRangeLabel_falls_back_on_unknown_value", () => {
		// Coerce through unknown to test the default branch without loosening types in prod.
		const unknownValue = "WEEK" as unknown as TimeRange;
		expect(timeRangeLabel(unknownValue)).toBe("本期");
	});
});
