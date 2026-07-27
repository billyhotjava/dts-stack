import assert from "node:assert/strict";
import test from "node:test";
import {
	canApplyImplementationMigration,
	canApplyModelReclassification,
	implementationMigrationReason,
	reclassificationFieldLabel,
} from "./modelSpecCorrection.ts";

test("only an eligible single-model dry-run can be applied", () => {
	assert.equal(
		canApplyImplementationMigration({
			previewChecksum: "a".repeat(64),
			total: 1,
			eligible: 1,
			conflict: 0,
			orphan: 0,
			skipped: 0,
			applied: 0,
			results: [],
		}),
		true,
	);
	assert.equal(
		canApplyImplementationMigration({
			previewChecksum: "a".repeat(64),
			total: 1,
			eligible: 0,
			conflict: 1,
			orphan: 0,
			skipped: 0,
			applied: 0,
			results: [],
		}),
		false,
	);
});

test("reclassification requires explicit acceptance of every cleared field", () => {
	const preview = {
		eligible: true,
		fromType: "FACT" as const,
		toType: "DIMENSION" as const,
		targetLayer: "DWD" as const,
		retainedFields: ["name", "fields"],
		requiredFields: ["dimensionDefinitionRef", "grain"],
		clearFields: ["timeSemantics", "factShape"],
		reasonCodes: [],
		currentRevision: 4,
		checksum: "b".repeat(64),
	};
	assert.equal(canApplyModelReclassification(preview, ["timeSemantics"]), false);
	assert.equal(canApplyModelReclassification(preview, ["factShape", "timeSemantics"]), true);
});

test("correction copy is business-readable Chinese", () => {
	assert.equal(implementationMigrationReason("CURRENT_IMPLEMENTATION_WINS"), "已有数据实现是当前真值，不会被历史设置覆盖");
	assert.equal(reclassificationFieldLabel("fields.roles"), "字段角色（时间/度量将调整为属性）");
	assert.doesNotMatch(implementationMigrationReason("MODEL_IMPLEMENTATION_INPUT_STALE"), /^[A-Z0-9_]+$/);
});
