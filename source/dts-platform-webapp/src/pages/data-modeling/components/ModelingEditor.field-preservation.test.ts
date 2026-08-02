// @vitest-environment jsdom

import { describe, expect, it } from "vitest";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { modelSpecFieldFromRow, rowsFromModel } from "./ModelingEditor";

describe("ModelingEditor field replacement", () => {
	it("round-trips governance and lineage fields that are not directly editable in the table", () => {
		const model = {
			id: "model-1",
			fields: [
				{
					name: "customer_code",
					displayName: "客户编码",
					dataType: "STRING",
					nullable: false,
					sourceFieldRef: "ods_customer.customer_code",
					role: "KEY",
					securityLevel: "INTERNAL",
					dimensionAttributeCode: "CUSTOMER_CODE",
					redundant: true,
					redundancySourceRef: "dim_party.customer_code",
				},
			],
			standardBindings: [],
		} satisfies Pick<ModelSpecView, "id" | "fields" | "standardBindings">;

		const [row] = rowsFromModel(model);

		expect(modelSpecFieldFromRow(row)).toEqual(model.fields[0]);
	});
});
