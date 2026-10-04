import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
	isModelSpecUpstreamAllowed,
	validateModelSpecCreate,
	validateModelSpecUpdate,
} from "./modelSpecV2Contract.ts";

const sourceDefinition = {
	planId: "10000000-0000-0000-0000-000000000001",
	domainId: "20000000-0000-0000-0000-000000000001",
	modelType: "SOURCE",
	layer: "ODS",
	name: "ods_source_contract",
	implementationMode: "DESIGNER_GENERATED",
	materialization: "table",
	fields: [{ name: "record_id", dataType: "bigint", nullable: false, role: "KEY" }],
	grain: { statement: "one row per source record", keys: ["record_id"] },
	sourceRefs: [],
	dependsOn: [],
	dimensionRefs: [],
	metricRefs: [],
	standardBindings: [],
};

test("SOURCE can be created and its definition saved without an ingestion or physical source", () => {
	assert.deepEqual(validateModelSpecCreate({
		planId: sourceDefinition.planId,
		domainId: sourceDefinition.domainId,
		modelType: "SOURCE",
		name: sourceDefinition.name,
		idempotencyKey: "source-contract-create",
	}), []);
	assert.deepEqual(validateModelSpecUpdate(sourceDefinition), []);
});

test("SOURCE stays at ODS and cannot silently become a fact or STG model", () => {
	for (const layer of ["STG", "DWD", "DWS", "ADS"]) {
		assert.ok(validateModelSpecUpdate({ ...sourceDefinition, layer }).some(
			(issue) => issue.code === "MODEL_SPEC_TYPE_LAYER_MISMATCH",
		));
	}
	assert.ok(validateModelSpecUpdate({ ...sourceDefinition, factShape: "TRANSACTION" }).some(
		(issue) => issue.code === "MODEL_SPEC_INPUT_KIND_NOT_ALLOWED",
	));
});

test("SOURCE rejects model dependencies while DWD can reference an ODS design", () => {
	assert.ok(validateModelSpecUpdate({
		...sourceDefinition,
		dependsOn: [{ modelSpecId: "30000000-0000-0000-0000-000000000001", revision: 1 }],
	}).some((issue) => issue.code === "MODEL_SPEC_INPUT_KIND_NOT_ALLOWED"));
	assert.equal(isModelSpecUpstreamAllowed("FACT", { modelType: "SOURCE", layer: "ODS" }), true);
	assert.equal(isModelSpecUpstreamAllowed("FACT", { modelType: "SOURCE", layer: "STG" }), false);
	assert.equal(isModelSpecUpstreamAllowed("SOURCE", { modelType: "FACT", layer: "DWD" }), false);
});

test("SOURCE is part of the deliverable schema enum alongside the four existing types", () => {
	const schema = JSON.parse(readFileSync(new URL(
		"../../../../../dts-platform/src/main/resources/config/modeling/model-spec-v2.schema.json", import.meta.url,
	), "utf8"));
	assert.deepEqual(new Set(schema.$defs.modelType.enum), new Set([
		"SOURCE", "DIMENSION", "FACT", "SUMMARY", "APPLICATION",
	]));
});
