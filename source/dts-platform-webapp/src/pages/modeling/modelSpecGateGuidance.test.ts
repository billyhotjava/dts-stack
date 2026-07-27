import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { modelSpecGateGuidance, modelSpecGateRepairPath } from "./modelSpecGateGuidance.ts";

test("fact input blockers explain the alternative inputs and open data implementation", () => {
	const blocker = {
		code: "MODEL_SPEC_FACT_INPUT_REQUIRED",
		field: "sourceRefs",
		message: "请至少选择已确认的上游输入来源或锁定上游模型",
		repairRoute: "/modeling/models/model-1?tab=design",
	};

	const guidance = modelSpecGateGuidance(blocker);

	assert.equal(guidance.location, "数据实现");
	assert.match(guidance.description, /上游模型.*不是当前正在创建的表/);
	assert.match(guidance.description, /物理来源.*无需先创建上游模型/);
	assert.equal(modelSpecGateRepairPath(blocker), "/modeling/models/model-1?activeStage=implementation");
});

test("TIME blockers explain the field role and open the fields tab", () => {
	const blocker = {
		code: "MODEL_SPEC_TIME_FIELD_INVALID",
		field: "timeSemantics",
		message: "业务时间必须引用 TIME 字段",
		repairRoute: "/modeling/models/model-1?tab=fields",
	};

	const guidance = modelSpecGateGuidance(blocker);

	assert.equal(guidance.message, "业务时间尚未引用有效的 TIME 字段");
	assert.match(guidance.description, /字段作用.*不会从来源表自动猜测/);
	assert.equal(modelSpecGateRepairPath(blocker), "/modeling/models/model-1?activeStage=logical&tab=fields");
});

test("classification backend messages are localized and routed to their owning stage", () => {
	const missingImplementation = {
		code: "CLASSIFICATION_IMPLEMENTATION_EVIDENCE_MISSING",
		field: "classification",
		message: "Current implementation evidence is required before classification can be propagated",
		repairRoute: "/modeling/models/model-1?tab=governance",
	};
	const pendingUpstream = {
		code: "PENDING_CLASSIFICATION",
		field: "classification",
		message: "Sealed upstream classification is missing",
		repairRoute: "/modeling/models/model-1?tab=governance",
	};

	const implementationGuidance = modelSpecGateGuidance(missingImplementation);
	const upstreamGuidance = modelSpecGateGuidance(pendingUpstream);

	assert.equal(implementationGuidance.message, "当前模型缺少可用于密级传播的实现证据");
	assert.equal(modelSpecGateRepairPath(missingImplementation), "/modeling/models/model-1?activeStage=implementation");
	assert.equal(upstreamGuidance.message, "上游输入尚无已封存的密级证据");
	assert.equal(modelSpecGateRepairPath(pendingUpstream), "/modeling/models/model-1?activeStage=physical");
});

test("repair links own both the outer stage and the logical subtab", () => {
	const blockerPanel = readFileSync(new URL("./components/ModelSpecBlockerPanel.tsx", import.meta.url), "utf8");
	const logicalStage = readFileSync(new URL("./components/ModelSpecLogicalDesignStage.tsx", import.meta.url), "utf8");

	assert.match(blockerPanel, /navigate\(modelSpecGateRepairPath\(blocker\)\)/);
	assert.match(logicalStage, /resolveModelSpecDetailTab\(searchParams\)/);
	assert.match(logicalStage, /activeKey=\{activeTab\}/);
	assert.match(logicalStage, /key:\s*"design"/);
});

test("logical standards tab explains the plan-owned release requirement without making it a design prerequisite", () => {
	const logicalStage = readFileSync(new URL("./components/ModelSpecLogicalDesignStage.tsx", import.meta.url), "utf8");

	assert.match(logicalStage, /getWarehousePlanPolicy\(model\.planId\)/);
	assert.match(logicalStage, /当前可选/);
	assert.match(logicalStage, /发布前键字段和度量字段必须绑定标准/);
	assert.match(logicalStage, /发布策略暂不可读/);
	assert.doesNotMatch(logicalStage, /rules=\{\[\{ required: true[^}]*字段标准/);
});
