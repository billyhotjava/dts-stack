import { describe, expect, it } from "vitest";
import { readTagIds, writeTagIds } from "./catalogTagUrlState";

describe("catalog tag URL state", () => {
	it("reads repeated tagIds, trims values and removes duplicates without reordering", () => {
		const params = new URLSearchParams("domain=finance&tagIds=tag-2&tagIds=%20tag-1%20&tagIds=tag-2&tagIds=&layer=ADS");

		expect(readTagIds(params)).toEqual(["tag-2", "tag-1"]);
	});

	it("writes repeated keys while preserving unrelated query parameters", () => {
		const current = new URLSearchParams("domain=finance&tagIds=old&layer=ADS");
		const next = writeTagIds(current, ["tag-2", "tag-1", "tag-2", " "]);

		expect(next.getAll("tagIds")).toEqual(["tag-2", "tag-1"]);
		expect(next.get("domain")).toBe("finance");
		expect(next.get("layer")).toBe("ADS");
		expect(current.getAll("tagIds")).toEqual(["old"]);
	});

	it("removes every tagIds value when the filter is cleared", () => {
		const next = writeTagIds(new URLSearchParams("tagIds=tag-1&domain=finance&tagIds=tag-2"), []);

		expect(next.has("tagIds")).toBe(false);
		expect(next.toString()).toBe("domain=finance");
	});
});
