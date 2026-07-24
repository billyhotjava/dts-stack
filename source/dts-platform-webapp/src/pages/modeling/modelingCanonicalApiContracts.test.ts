import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { buildModelSpecCreateCommand, createEmptyModelSpecDraft } from "./modelSpecWorkbench.ts";
import { validateModelSpecCreate } from "./modelSpecV2Contract.ts";

const PLAN_ID = "10000000-0000-0000-0000-000000000001";
const DOMAIN_ID = "20000000-0000-0000-0000-000000000001";
const DEFINITION_ID = "30000000-0000-0000-0000-000000000001";
const implementationApi = readFileSync(new URL("../../api/modelImplementationApi.ts", import.meta.url), "utf8");
const implementationContract = readFileSync(new URL("./modelImplementationContract.ts", import.meta.url), "utf8");
const dimensionApi = readFileSync(new URL("../../api/dimensionDefinitionApi.ts", import.meta.url), "utf8");
const dimensionContract = readFileSync(new URL("./dimensionDefinitionContract.ts", import.meta.url), "utf8");

test("ModelSpec create emits only the minimum first-draft wire contract", () => {
	const draft = {
		...createEmptyModelSpecDraft("FACT", { planId: PLAN_ID, domainId: DOMAIN_ID }),
		name: "customer_event",
		description: "客户事件",
		layer: "DWD" as const,
		implementationMode: "DBT_MANAGED" as const,
		sources: [
			{
				kind: "TABLE" as const,
				ref: "ods.customer_event",
				layer: "ODS" as const,
				role: "PRIMARY" as const,
				sourceBindingId: "ignored",
				resolvedVersion: "ignored",
			},
		],
	};
	const command = buildModelSpecCreateCommand(draft, [], "create-customer-event");

	assert.deepEqual(command, {
		planId: PLAN_ID,
		domainId: DOMAIN_ID,
		modelType: "FACT",
		name: "customer_event",
		description: "客户事件",
		idempotencyKey: "create-customer-event",
	});
	for (const forbidden of ["layer", "implementationMode", "sourceRefs", "fields", "dependsOn", "schema", "tableName"]) {
		assert.equal(forbidden in command, false, `${forbidden} must be server-defaulted on create`);
	}
});

test("DIMENSION is the only create type that accepts a revision-pinned dimension definition", () => {
	const base = {
		planId: PLAN_ID,
		domainId: DOMAIN_ID,
		modelType: "DIMENSION" as const,
		name: "organization",
		idempotencyKey: "dimension-create",
	};
	assert.deepEqual(validateModelSpecCreate(base).map((entry) => entry.code), ["MODEL_SPEC_DIMENSION_DEFINITION_REQUIRED"]);
	assert.deepEqual(
		validateModelSpecCreate({
			...base,
			dimensionDefinitionRef: { dimensionDefinitionId: DEFINITION_ID, revision: 2 },
		}),
		[],
	);
	assert.ok(
		validateModelSpecCreate({
			...base,
			modelType: "FACT",
			dimensionDefinitionRef: { dimensionDefinitionId: DEFINITION_ID, revision: 2 },
		}).some((entry) => entry.code === "MODEL_SPEC_DIMENSION_DEFINITION_NOT_ALLOWED"),
	);
});

test("dimension system codes are response-only and all dimension writes use the strong ETag", () => {
	assert.match(dimensionContract, /must never be sent by a writer[\s\S]*systemCode: string;/);
	assert.match(dimensionContract, /"dimension-definition:\$\{id\}:\$\{revision\}:\$\{checksum\}"/);
	for (const path of [
		"/modeling/dimension-definitions",
		"/confirm",
		"/retire",
		'"If-Match": toDimensionDefinitionEtag(expected)',
	]) {
		assert.match(dimensionApi, new RegExp(path.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
	}
});

test("implementation API keeps the three mutually exclusive input modes and frozen paths", () => {
	for (const mode of ["PHYSICAL_ASSET", "UPSTREAM_MODEL", "GENERATED"]) {
		assert.match(implementationContract, new RegExp(`inputMode: "${mode}"`));
	}
	for (const field of ["implementationRevision", "implementationChecksum", "dbtUniqueId"]) {
		assert.match(implementationContract, new RegExp(`${field}:`));
	}
	assert.match(implementationContract, /PinnedUpstreamModelImplementationInput/);
	assert.match(implementationContract, /UnpinnedUpstreamModelImplementationInput/);
	assert.match(implementationApi, /\/modeling\/model-specs\/\$\{encodeURIComponent\(id\)\}\/implementation\$\{suffix\}/);
	for (const suffix of ["/inputs", "/inputs/validate", "/convert-to-designer-generated"]) {
		assert.match(implementationApi, new RegExp(suffix.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
	}
	assert.match(implementationApi, /"If-Match": toModelSpecEtag\(expected\)/);
	assert.match(implementationApi, /"If-Match-Implementation": implementation \? toModelImplementationEtag\(implementation\) : "\*"/);
});
