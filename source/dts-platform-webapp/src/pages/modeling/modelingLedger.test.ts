import { describe, expect, it } from "vitest";
import { buildBusinessObjectLedgerRows, buildModelLedgerRows, toLegacyBusinessObject, toLegacySemanticModel } from "./modelingLedger";

describe("modeling ledger view models", () => {
	it("turns a business object into a semantic ledger row with a repair action", () => {
		const [row] = buildBusinessObjectLedgerRows([
			{ id: "obj-1", code: "project_node", name: "项目节点", processId: "pjm", primaryKey: "project_no", mainTable: "ods_project_node" },
		]);

		expect(row.objectKind).toBe("FACT");
		expect(row.processLabel).toBe("pjm");
		expect(row.grainStatus).toBe("READY");
		expect(row.sourceLabel).toBe("ods_project_node");
		expect(row.nextAction).toBe("进入模型台账");
	});

	it("marks an object without source or grain as blocked without requiring project space", () => {
		const [row] = buildBusinessObjectLedgerRows([{ id: "obj-2", code: "order", name: "订单" }]);

		expect(row.grainStatus).toBe("BLOCKED");
		expect(row.sourceLabel).toContain("待接入");
		expect(row.nextAction).toBe("补充来源");
	});

	it("distinguishes designer, dbt and legacy model ownership", () => {
		const rows = buildModelLedgerRows([
			{ id: "m1", name: "dws_order", type: "DWS", description: "designer model", reviewStatus: "APPROVED" },
			{ id: "m2", name: "dws_order_dbt", type: "DWS", description: "dbt managed", reviewStatus: "APPROVED" },
			{ id: "m3", name: "old_order", type: "ADS", description: "legacy dbt model", reviewStatus: "APPROVED" },
		]);

		expect(rows.map((row) => row.implementationMode)).toEqual(["DESIGNER_GENERATED", "DBT_MANAGED", "LEGACY_READONLY"]);
		expect(rows[2].nextAction).toBe("查看兼容来源");
	});

	it("keeps every vNext warehouse layer visible to the model ledger", () => {
		const rows = buildModelLedgerRows([
			{ id: "ods", name: "ods_order", type: "ODS", reviewStatus: "APPROVED" },
			{ id: "stg", name: "stg_order", type: "STG", reviewStatus: "APPROVED" },
			{ id: "dwd", name: "dwd_order", type: "DWD", reviewStatus: "APPROVED" },
			{ id: "dws", name: "dws_order", type: "DWS", reviewStatus: "APPROVED" },
			{ id: "ads", name: "ads_order", type: "ADS", reviewStatus: "APPROVED" },
		]);

		expect(rows.map((row) => row.layer)).toEqual(["ODS", "STG", "DWD", "DWS", "ADS"]);
	});

	it("adapts vNext records into the existing detail shells without losing process context", () => {
		const object = toLegacyBusinessObject({
			id: "pjm-project-node",
			code: "project_node",
			name: "项目节点",
			objectKind: "FACT",
			processId: "project-node-plan-loop",
			businessKey: ["project_no", "plan_date"],
			grain: { statement: "项目日节点", keys: ["project_no", "plan_date"] },
			sourceRefs: [{ kind: "TABLE", ref: "ods.project_node", layer: "ODS" }],
			status: "DRAFT",
			implementationMode: "DESIGNER_GENERATED",
		});
		const model = toLegacySemanticModel({
			id: "pjm-project-node-dws",
			objectId: object.id,
			processId: object.processId,
			layer: "DWS",
			modelType: "SUMMARY",
			implementationMode: "DBT_MANAGED",
			name: "project_progress_monthly",
			grain: { statement: "项目月度", keys: ["project_no", "plan_month"] },
			standardBindings: [],
			sourceRefs: [],
			dimensions: ["project_no"],
			metrics: ["completed_node_count"],
			materialization: "table",
			revision: 2,
		});

		expect(object.primaryKey).toBe("project_no, plan_date");
		expect(object.mainTable).toBe("ods.project_node");
		expect(object.grain).toBe("项目日节点");
		expect(object.implementationMode).toBe("DESIGNER_GENERATED");
		expect(model.type).toBe("DWS");
		expect(model.description).toContain("dbt managed");
	});
});
