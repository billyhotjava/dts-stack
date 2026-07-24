import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import test from "node:test";

const helperUrl = new URL("./modelSpecDetailStageProjection.ts", import.meta.url);

test("stage projection follows the logical to physical primary-action sequence", async () => {
	assert.equal(existsSync(helperUrl), true, "model detail stage projection helper is missing");
	const { getModelSpecDetailStageProjection } = await import(helperUrl.href);

	assert.equal(getModelSpecDetailStageProjection({ stage: "logical", logicalDirty: true, canEdit: true }).primaryAction.label, "保存逻辑设计");
	assert.equal(
		getModelSpecDetailStageProjection({ stage: "logical", logicalDirty: false, canEdit: true }).primaryAction.label,
		"配置数据实现",
	);
	assert.equal(
		getModelSpecDetailStageProjection({
			stage: "implementation",
			logicalDirty: false,
			implementationConfigured: true,
			implementationValidated: false,
			canEdit: true,
		}).primaryAction.label,
		"验证实现",
	);
	assert.equal(
		getModelSpecDetailStageProjection({
			stage: "physical",
			logicalDirty: false,
			implementationConfigured: true,
			implementationValidated: true,
			canEdit: true,
		}).primaryAction.label,
		"生成并发布",
	);
});

test("stage projection recovers upstream dirtiness before implementation or publication", async () => {
	const { getModelSpecDetailStageProjection } = await import(helperUrl.href);

	const logicalRecovery = getModelSpecDetailStageProjection({
		stage: "physical",
		logicalDirty: true,
		implementationConfigured: true,
		implementationValidated: true,
		canEdit: true,
	});
	assert.equal(logicalRecovery.primaryAction.label, "保存逻辑设计");
	assert.equal(logicalRecovery.primaryAction.recoveryStage, "logical");

	const implementationRecovery = getModelSpecDetailStageProjection({
		stage: "physical",
		logicalDirty: false,
		implementationDirty: true,
		implementationConfigured: true,
		implementationValidated: true,
		canEdit: true,
	});
	assert.equal(implementationRecovery.primaryAction.label, "配置数据实现");
	assert.equal(implementationRecovery.primaryAction.recoveryStage, "implementation");
});

test("stage projection keeps a single disabled recovery action for permissions, blockers and readonly lifecycle", async () => {
	const { getModelSpecDetailStageProjection } = await import(helperUrl.href);

	const cases = [
		getModelSpecDetailStageProjection({ stage: "logical", logicalDirty: true, canEdit: false }),
		getModelSpecDetailStageProjection({
			stage: "physical",
			implementationConfigured: true,
			implementationValidated: true,
			canEdit: true,
			blocker: { message: "来源版本已漂移", recoveryStage: "implementation" },
		}),
		getModelSpecDetailStageProjection({
			stage: "physical",
			implementationConfigured: true,
			implementationValidated: true,
			canEdit: true,
			lifecycleStatus: "ARCHIVED",
		}),
	];

	for (const projection of cases) {
		assert.equal(projection.primaryAction.disabled, true);
		assert.equal(projection.primaryAction.label !== "", true);
		assert.equal("secondaryAction" in projection, false);
		assert.equal("primaryActions" in projection, false);
	}
	assert.equal(cases[1].primaryAction.label, "配置数据实现");
	assert.equal(cases[1].primaryAction.recoveryMessage, "来源版本已漂移");
});
