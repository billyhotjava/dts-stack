import assert from "node:assert/strict";
import test from "node:test";
import {
	isUpstreamModelImplementationPinned,
	modelImplementationValidationMessage,
	resolvePhysicalAssetImplementationInputs,
	resolveUpstreamModelImplementationInputs,
} from "./modelImplementationContract.ts";

test("implementation input resolution preserves persisted physical pins until the user explicitly adopts current", () => {
	const selected = ["binding-a"];
	const available = [{ sourceBindingId: "binding-a", resolvedVersion: "landing-v2" }];
	const persisted = [{ sourceBindingId: "binding-a", resolvedVersion: "landing-v1" }];

	assert.deepEqual(resolvePhysicalAssetImplementationInputs(selected, available, persisted), persisted);
	assert.deepEqual(
		resolvePhysicalAssetImplementationInputs(selected, available, persisted, new Set(selected)),
		available,
	);
});

test("implementation input resolution preserves the persisted six-field upstream pin until explicit adoption", () => {
	const selected = ["model-a"];
	const available = [{ modelSpecId: "model-a", revision: 2, checksum: "model-checksum-v2" }];
	const persisted = [
		{
			modelSpecId: "model-a",
			revision: 2,
			checksum: "model-checksum-v2",
			implementationRevision: 7,
			implementationChecksum: "implementation-checksum-v7",
			dbtUniqueId: "model.project.model_a",
		},
	];

	assert.deepEqual(resolveUpstreamModelImplementationInputs(selected, available, persisted), persisted);
	const adopted = resolveUpstreamModelImplementationInputs(selected, available, persisted, new Set(selected));
	assert.deepEqual(adopted, available);
	assert.equal("implementationRevision" in adopted[0], false);
	assert.equal("implementationChecksum" in adopted[0], false);
	assert.equal("dbtUniqueId" in adopted[0], false);
	assert.equal(isUpstreamModelImplementationPinned(persisted[0]), true);
	assert.equal(isUpstreamModelImplementationPinned(adopted[0]), false);
});

test("explicit upstream adoption never falls back to a persisted pin when current is unavailable", () => {
	const persisted = [
		{
			modelSpecId: "model-a",
			revision: 2,
			checksum: "model-checksum-v2",
			implementationRevision: 7,
			implementationChecksum: "implementation-checksum-v7",
			dbtUniqueId: "model.project.model_a",
		},
	];
	const unresolved = resolveUpstreamModelImplementationInputs(["model-a"], [], persisted, new Set(["model-a"]));

	assert.deepEqual(unresolved, [{ modelSpecId: "model-a", revision: 0, checksum: "" }]);
	assert.equal(isUpstreamModelImplementationPinned(unresolved[0]), false);
});

test("implementation input resolution uses current selectable references for newly selected inputs", () => {
	assert.deepEqual(
		resolvePhysicalAssetImplementationInputs(
			["binding-new"],
			[{ sourceBindingId: "binding-new", resolvedVersion: "landing-v1" }],
			[],
		),
		[{ sourceBindingId: "binding-new", resolvedVersion: "landing-v1" }],
	);
	const newUpstream = resolveUpstreamModelImplementationInputs(
		["model-new"],
		[{ modelSpecId: "model-new", revision: 1, checksum: "checksum-v1" }],
		[],
	);
	assert.deepEqual(newUpstream, [{ modelSpecId: "model-new", revision: 1, checksum: "checksum-v1" }]);
	assert.equal(isUpstreamModelImplementationPinned(newUpstream[0]), false);
});

test("implementation validation prefers the server-owned blocker guidance", () => {
	assert.equal(
		modelImplementationValidationMessage({
			valid: false,
			code: "IMPLEMENTATION_PARTITION_UNSUPPORTED",
			blockers: [
				{
					code: "IMPLEMENTATION_PARTITION_UNSUPPORTED",
					field: "settings.partitionFields",
					message: "当前 PostgreSQL 执行目标不支持普通模式分区转译",
					repairAction: "REMOVE_PARTITION_FIELDS",
				},
			],
			executionPlan: null,
		}),
		"当前 PostgreSQL 执行目标不支持普通模式分区转译，请先移除分区字段。",
	);
});

test("implementation validation remains compatible with an older response shape", () => {
	assert.equal(
		modelImplementationValidationMessage({
			valid: false,
			code: "IMPLEMENTATION_INCREMENTAL_KEY_REQUIRED",
		}),
		"增量装载至少需要一个 KEY 字段，请先回到逻辑设计补充。",
	);
});
