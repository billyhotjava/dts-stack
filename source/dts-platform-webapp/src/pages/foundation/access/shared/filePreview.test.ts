import { describe, expect, it } from "vitest";
import { buildFilePreviewRows, normalizeFilePreviewLimit } from "./filePreview";

describe("filePreview", () => {
	it("uses 10 rows by default and clamps custom values to the available 1-20 range", () => {
		expect(normalizeFilePreviewLimit(undefined)).toBe(10);
		expect(normalizeFilePreviewLimit(Number.NaN)).toBe(10);
		expect(normalizeFilePreviewLimit(0)).toBe(1);
		expect(normalizeFilePreviewLimit(7.8)).toBe(7);
		expect(normalizeFilePreviewLimit(30)).toBe(20);
	});

	it("returns a non-mutating slice of the parsed preview", () => {
		const preview = Array.from({ length: 20 }, (_, index) => [`row-${index + 1}`]);
		const rows = buildFilePreviewRows(preview, 10);

		expect(rows).toHaveLength(10);
		expect(rows[0]).toEqual(["row-1"]);
		expect(rows[9]).toEqual(["row-10"]);
		expect(rows).not.toBe(preview);
		expect(preview).toHaveLength(20);
	});

	it("keeps short and empty preview samples valid", () => {
		expect(buildFilePreviewRows([["a"], ["b"]], 10)).toEqual([["a"], ["b"]]);
		expect(buildFilePreviewRows(undefined, 10)).toEqual([]);
	});
});
