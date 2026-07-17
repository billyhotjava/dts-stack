import assert from "node:assert/strict";
import test from "node:test";
import { MODELING_CONTRACT_VERSION, type ModelSpec, validateModelSpec } from "./modelingVnextContract.ts";
import { buildPjmProjectNodeFixture } from "./pjmModelingFixture.test-support.ts";

test("PJM fixture declares a reusable project-node fact object and DWD ModelSpec", () => {
	const fixture = buildPjmProjectNodeFixture();

	assert.equal(fixture.contractVersion, MODELING_CONTRACT_VERSION);
	assert.equal(fixture.businessObject.objectKind, "FACT");
	assert.equal(fixture.businessObject.processId, "project-node-plan-loop");
	assert.deepEqual(fixture.businessObject.businessKey, ["project_no", "subsystem", "node_task", "plan_date"]);
	assert.equal(fixture.modelSpec.layer, "DWD");
	assert.equal(fixture.modelSpec.implementationMode, "DESIGNER_GENERATED");
	assert.deepEqual(validateModelSpec(fixture.modelSpec), { valid: true, issues: [] });
});

test("DWD designer models require a grain and at least one standard binding", () => {
	const invalid: ModelSpec = {
		id: "model-1",
		objectId: "object-1",
		processId: "project-node-plan-loop",
		layer: "DWD",
		modelType: "FACT",
		implementationMode: "DESIGNER_GENERATED",
		name: "project_node_detail",
		grain: { statement: "", keys: [] },
		standardBindings: [],
		sourceRefs: [{ kind: "TABLE", ref: "ods_project_subject_domain_v2", layer: "ODS" }],
		revision: 1,
	};

	const result = validateModelSpec(invalid);
	assert.equal(result.valid, false);
	assert.deepEqual(result.issues, ["DWD 模型必须声明粒度键", "DWD 设计器模型至少绑定一个数据标准"]);
});

test("dbt-managed models may omit UI standard bindings but retain an explicit ownership mode", () => {
	const model: ModelSpec = {
		id: "dbt-model-1",
		objectId: "object-1",
		processId: "project-node-plan-loop",
		layer: "DWS",
		modelType: "SUMMARY",
		implementationMode: "DBT_MANAGED",
		name: "project_progress_monthly",
		grain: { statement: "一行代表项目和月份", keys: ["project_no", "plan_month"] },
		standardBindings: [],
		sourceRefs: [{ kind: "DBT_MODEL", ref: "model.pjm.biz_dws_progress_monthly_v2", layer: "DWS" }],
		revision: 3,
	};

	assert.deepEqual(validateModelSpec(model), { valid: true, issues: [] });
});
