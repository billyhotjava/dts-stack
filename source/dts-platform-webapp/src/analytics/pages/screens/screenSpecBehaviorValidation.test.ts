import { describe, expect, it } from "vitest";
import { validateComponentBehavior } from "./screenSpecBehaviorValidation";

describe("validateComponentBehavior drill contracts", () => {
	it("rejects an incomplete executable target even when mappings exist", () => {
		const errors: string[] = [];

		validateComponentBehavior(
			{
				drillDown: {
					enabled: true,
					levels: [
						{
							label: "明细",
							dataSource: { type: "sql", sourceType: "sql", sqlConfig: { databaseId: 0, query: "" } },
							mappings: [{ sourcePath: "data.key", variableKey: "selectedKey" }],
						},
					],
				},
			},
			"components[0]",
			errors,
		);

		expect(errors).toEqual(expect.arrayContaining([
			expect.stringContaining("databaseId"),
			expect.stringContaining("query"),
		]));
	});

	it("rejects drill actions when the component has no enabled drill chain", () => {
		const errors: string[] = [];

		validateComponentBehavior(
			{ actions: [{ type: "drill-down" }, { type: "drill-up" }] },
			"components[0]",
			errors,
		);

		expect(errors).toEqual(expect.arrayContaining([
			expect.stringContaining("actions[0]"),
			expect.stringContaining("actions[1]"),
		]));
		expect(errors.every((error) => error.includes("启用有效下钻链路"))).toBe(true);
	});
});
