// @vitest-environment jsdom

import { describe, expect, it } from "vitest";
import { buildDatasetRuntimeRequest } from "./useCardDataSource";

describe("buildDatasetRuntimeRequest", () => {
	it("adds drill parameters to a dataset query without dropping existing parameters", () => {
		const body = buildDatasetRuntimeRequest(
			{
				database: 1,
				type: "native",
				native: { query: "select * from sales where tenant={{tenant}} and region={{selectedKey}}" },
				parameters: [
					{ name: "tenant", value: "T-01" },
					{
						target: ["variable", ["template-tag", "selectedKey"]],
						type: "category",
						value: "OLD",
					},
				],
			},
			[
				{ name: "selectedKey", value: "A-01" },
				{ name: "childKey", value: "B-02" },
			],
			{ componentId: "chart-1" },
		);

		expect(body.parameters).toEqual([
			{ name: "tenant", value: "T-01" },
			{
				target: ["variable", ["template-tag", "selectedKey"]],
				type: "category",
				value: "A-01",
			},
			{ name: "childKey", value: "B-02" },
		]);
		expect(body.queryContext).toEqual({ componentId: "chart-1" });
	});

	it("preserves object-form dataset parameters while applying drill overrides", () => {
		const body = buildDatasetRuntimeRequest(
			{ database: 1, type: "native", native: { query: "select 1" }, parameters: { tenant: "T-01" } },
			[{ name: "selectedKey", value: "A-01" }],
		);

		expect(body.parameters).toEqual({ tenant: "T-01", selectedKey: "A-01" });
	});
});
