import { describe, expect, it } from "vitest";
import type { ManagedFileColumn } from "@/api/ingestion";
import {
	applyTargetSchemaTemplate,
	fillUnmatchedByPosition,
	normalizeTargetFieldKey,
	type TargetSchemaColumn,
	validateFileTargetColumns,
} from "./fileTargetSchemaMapping";

const fileColumns: ManagedFileColumn[] = [
	{ name: "project_no", label: "Project No", type: "string" },
	{ name: "issue_date", label: "Issue Date", type: "string" },
	{ name: "owner_name", label: "Owner Name", type: "string" },
];

const targetColumns: TargetSchemaColumn[] = [
	{ name: "project_no", type: "varchar", description: "项目编号", ordinalPosition: 1 },
	{ name: "issueDate", type: "date", description: "问题日期", ordinalPosition: 2 },
	{ name: "assignee", type: "varchar", description: "负责人", ordinalPosition: 3 },
];

describe("fileTargetSchemaMapping", () => {
	it("normalizes case, spaces and underscores without changing other characters", () => {
		expect(normalizeTargetFieldKey(" Issue_Date ")).toBe("issuedate");
		expect(normalizeTargetFieldKey("owner-name")).toBe("owner-name");
	});

	it("prefers exact matching and then normalized matching", () => {
		const mapped = applyTargetSchemaTemplate(fileColumns, targetColumns);

		expect(mapped).toEqual([
			{
				name: "project_no",
				label: "Project No",
				type: "varchar",
				description: "项目编号",
				_odsMatched: true,
			},
			{
				name: "issueDate",
				label: "Issue Date",
				type: "date",
				description: "问题日期",
				_odsMatched: true,
			},
			{
				name: "owner_name",
				label: "Owner Name",
				type: "string",
				_odsMatched: false,
			},
		]);
	});

	it("does not silently map unmatched fields by ordinal position", () => {
		const mapped = applyTargetSchemaTemplate(fileColumns, targetColumns);
		expect(mapped[2]?.name).toBe("owner_name");
		expect(mapped[2]?._odsMatched).toBe(false);
	});

	it("fills only unmatched fields by position when explicitly requested", () => {
		const mapped = applyTargetSchemaTemplate(fileColumns, targetColumns);
		const filled = fillUnmatchedByPosition(mapped, targetColumns);

		expect(filled[0]?.name).toBe("project_no");
		expect(filled[1]?.name).toBe("issueDate");
		expect(filled[2]).toMatchObject({
			name: "assignee",
			type: "varchar",
			description: "负责人",
			_odsMatched: true,
		});
	});

	it("preserves target length and numeric precision when reusing a table schema", () => {
		const mapped = applyTargetSchemaTemplate(
			[
				{ name: "order_no", type: "string" },
				{ name: "amount", type: "string" },
			],
			[
				{ name: "order_no", type: "varchar", columnSize: 64 },
				{ name: "amount", type: "numeric", columnSize: 18, decimalDigits: 4 },
			],
		);

		expect(mapped[0]).toMatchObject({ type: "varchar", length: 64 });
		expect(mapped[1]).toMatchObject({ type: "numeric", precision: 18, scale: 4 });
	});

	it("reports required, illegal and duplicate target field names", () => {
		expect(
			validateFileTargetColumns([
				{ name: "", label: "A", type: "string" },
				{ name: "bad-name", label: "B", type: "string" },
				{ name: "owner", label: "C", type: "string" },
				{ name: "OWNER", label: "D", type: "string" },
			]),
		).toEqual([
			{ index: 0, code: "REQUIRED", message: "目标字段名不能为空" },
			{ index: 1, code: "INVALID_IDENTIFIER", message: "目标字段名只能包含字母、数字和下划线，且不能以数字开头" },
			{ index: 2, code: "DUPLICATE", message: "目标字段名不能重复" },
			{ index: 3, code: "DUPLICATE", message: "目标字段名不能重复" },
		]);
	});
});
