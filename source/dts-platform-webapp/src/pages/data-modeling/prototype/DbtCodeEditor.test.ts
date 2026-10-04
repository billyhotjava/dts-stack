import { describe, expect, it } from "vitest";
import {
	dbtDraftStatusLabel,
	dbtEditorLanguage,
	isDbtDraftConflictStatus,
	isDbtSaveShortcut,
	toDbtMarkerData,
} from "./dbtCodeEditorContract";

describe("DbtCodeEditor contracts", () => {
	it("maps dbt file paths without interpreting Jinja", () => {
		expect(dbtEditorLanguage("models/orders.sql")).toBe("sql");
		expect(dbtEditorLanguage("models/orders.yml")).toBe("yaml");
		expect(dbtEditorLanguage("dbt_project.yml")).toBe("yaml");
		expect(dbtEditorLanguage("README.md")).toBe("plaintext");
	});

	it("intercepts only Ctrl/Cmd+S, never Ctrl+Enter execution", () => {
		expect(isDbtSaveShortcut({ key: "s", ctrlKey: true, metaKey: false })).toBe(true);
		expect(isDbtSaveShortcut({ key: "s", ctrlKey: false, metaKey: true })).toBe(true);
		expect(isDbtSaveShortcut({ key: "Enter", ctrlKey: true, metaKey: false })).toBe(false);
	});

	it("maps positioned diagnostics to markers and keeps project diagnostics out of Monaco", () => {
		expect(
			toDbtMarkerData([
				{ severity: "ERROR", message: "bad ref", line: 3, column: 7 },
				{ severity: "WARNING", message: "project warning", line: null, column: null },
			]),
		).toEqual([
			{
				severity: "ERROR",
				message: "bad ref",
				startLineNumber: 3,
				startColumn: 7,
				endLineNumber: 3,
				endColumn: 8,
			},
		]);
	});

	it("treats both conflict status codes as write locks and derives customer-facing state", () => {
		expect(isDbtDraftConflictStatus(409)).toBe(true);
		expect(isDbtDraftConflictStatus(412)).toBe(true);
		expect(isDbtDraftConflictStatus(422)).toBe(false);
		expect(dbtDraftStatusLabel({ conflict: true, dirty: true, committed: false, validated: false })).toBe("版本冲突");
		expect(dbtDraftStatusLabel({ conflict: false, dirty: false, committed: false, validated: true })).toBe("校验通过");
	});
});
