import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import Ajv2020 from "ajv/dist/2020.js";
import {
	type CreateModelSpecCommand,
	MODEL_SPEC_COLLECTION_FIELDS,
	MODEL_SPEC_CONTRACT_VERSION,
	MODEL_SPEC_CREATE_FIELDS,
	MODEL_SPEC_REQUIRED_FIELD_CODES,
	MODEL_SPEC_UPDATE_FIELDS,
	toModelSpecEtag,
	validateModelSpecCreate,
	validateModelSpecUpdate,
} from "./modelSpecV2Contract.ts";

const UUID_FORMAT = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const RFC3339_DATE_TIME_FORMAT =
	/^(\d{4})-(\d{2})-(\d{2})[Tt](\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(?:[Zz]|[+-](\d{2}):(\d{2}))$/;

const isRfc3339DateTime = (value: string) => {
	const match = RFC3339_DATE_TIME_FORMAT.exec(value);
	if (!match) return false;
	const [, yearText, monthText, dayText, hourText, minuteText, secondText, offsetHourText, offsetMinuteText] = match;
	const year = Number(yearText);
	const month = Number(monthText);
	const day = Number(dayText);
	const hour = Number(hourText);
	const minute = Number(minuteText);
	const second = Number(secondText);
	const offsetHour = offsetHourText == null ? 0 : Number(offsetHourText);
	const offsetMinute = offsetMinuteText == null ? 0 : Number(offsetMinuteText);
	const leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
	const daysInMonth = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
	return (
		year > 0 &&
		month >= 1 &&
		month <= 12 &&
		day >= 1 &&
		day <= daysInMonth[month - 1] &&
		hour <= 23 &&
		minute <= 59 &&
		second <= 60 &&
		offsetHour <= 23 &&
		offsetMinute <= 59
	);
};

const createContractAjv = () => {
	const ajv = new Ajv2020({ allErrors: true, strict: false });
	ajv.addFormat("uuid", { type: "string", validate: (value: string) => UUID_FORMAT.test(value) });
	ajv.addFormat("date-time", { type: "string", validate: isRfc3339DateTime });
	ajv.addKeyword({
		keyword: "x-dtsUniqueBy",
		type: "array",
		schemaType: "array",
		errors: false,
		validate: (fields: string[], data: unknown[]) => {
			const seen = new Set<string>();
			for (const item of data) {
				if (item == null || typeof item !== "object" || Array.isArray(item)) continue;
				const record = item as Record<string, unknown>;
				const key = fields
					.map((field) => {
						const value = record[field];
						return typeof value === "string" ? value.trim() : JSON.stringify(value);
					})
					.join("\u001f");
				if (seen.has(key)) return false;
				seen.add(key);
			}
			return true;
		},
	});
	return ajv;
};

const valid = (modelType: CreateModelSpecCommand["modelType"]): CreateModelSpecCommand => ({
	planId: "10000000-0000-0000-0000-000000000001",
	domainId: "20000000-0000-0000-0000-000000000001",
	modelType,
	layer: modelType === "SUMMARY" ? "DWS" : modelType === "APPLICATION" ? "ADS" : "DWD",
	name: `generic_${modelType.toLowerCase()}`,
	description: "Generic cross-industry model",
	implementationMode: "DESIGNER_GENERATED",
	materialization: "table",
	consumptionScenario: modelType === "APPLICATION" ? "operational reporting" : null,
	grain: { statement: "one row per record", keys: ["record_id"] },
	factShape: modelType === "FACT" ? "TRANSACTION" : undefined,
	timeSemantics: modelType === "FACT" ? { type: "EVENT_TIME", fields: ["event_time"] } : undefined,
	fields: [
		{ name: "record_id", dataType: "varchar", nullable: false, sourceFieldRef: "source.record_id", role: "KEY" },
		{ name: "event_time", dataType: "timestamp", nullable: false, sourceFieldRef: "source.event_time", role: "TIME" },
	],
	sourceRefs:
		modelType === "SUMMARY" || modelType === "APPLICATION"
			? []
			: [
					{
						kind: "TABLE",
						ref: "catalog.dataset.source",
						layer: "ODS",
						role: "PRIMARY",
						sortOrder: 0,
						sourceBindingId: "50000000-0000-0000-0000-000000000001",
						resolvedVersion: "v1",
					},
				],
	dependsOn:
		modelType === "SUMMARY" || modelType === "APPLICATION"
			? [{ modelSpecId: "30000000-0000-0000-0000-000000000001", revision: 1 }]
			: [],
	dimensionRefs: [],
	metricRefs: [],
	standardBindings: [],
	generationStrategy:
		modelType === "DIMENSION" ? { type: "REFERENCE", reference: "catalog.dataset.source" } : undefined,
	idempotencyKey: `model-spec-contract-test-${modelType.toLowerCase()}`,
});

const minimal = (modelType: CreateModelSpecCommand["modelType"]): CreateModelSpecCommand => ({
	planId: "10000000-0000-0000-0000-000000000001",
	domainId: "20000000-0000-0000-0000-000000000001",
	modelType,
	layer: modelType === "SUMMARY" ? "DWS" : modelType === "APPLICATION" ? "ADS" : "DWD",
	name: `minimal_${modelType.toLowerCase()}`,
	implementationMode: "DESIGNER_GENERATED",
	grain: { statement: "one row per record", keys: ["record_id"] },
	...(modelType === "DIMENSION"
		? { fields: [{ name: "record_id", dataType: "varchar", nullable: false, role: "KEY" as const }] }
		: {}),
	...(modelType === "FACT"
		? {
				sourceRefs: [
					{
						kind: "TABLE" as const,
						ref: "catalog.dataset.source",
						layer: "ODS" as const,
						role: "PRIMARY" as const,
						sortOrder: 0,
						sourceBindingId: "50000000-0000-0000-0000-000000000001",
						resolvedVersion: "v1",
					},
				],
			}
		: {}),
	...(modelType === "SUMMARY" || modelType === "APPLICATION"
		? { dependsOn: [{ modelSpecId: "30000000-0000-0000-0000-000000000001", revision: 1 }] }
		: {}),
	...(modelType === "APPLICATION" ? { consumptionScenario: "operational reporting" } : {}),
	idempotencyKey: `minimal-${modelType.toLowerCase()}`,
});

test("canonical create fields exclude client-owned and retired metadata", () => {
	assert.equal(MODEL_SPEC_CONTRACT_VERSION, 2);
	assert.equal(new Set(MODEL_SPEC_CREATE_FIELDS).size, MODEL_SPEC_CREATE_FIELDS.length);
	for (const forbidden of [
		"id",
		"status",
		"revision",
		"checksum",
		"objectId",
		"processId",
		"legacyRef",
		"legacyRefs",
	]) {
		assert.equal((MODEL_SPEC_CREATE_FIELDS as readonly string[]).includes(forbidden), false, forbidden);
	}
});

test("canonical update is a full replacement without create-only or retired metadata", () => {
	assert.equal((MODEL_SPEC_UPDATE_FIELDS as readonly string[]).includes("idempotencyKey"), false);
	const { idempotencyKey: _idempotencyKey, ...update } = valid("FACT");
	assert.deepEqual(validateModelSpecUpdate(update), []);
	for (const forbidden of ["idempotencyKey", "objectId", "processId", "legacyRef", "legacyRefs"]) {
		assert.deepEqual(
			validateModelSpecUpdate({ ...update, [forbidden]: "forbidden" })
				.filter((item) => item.code === "MODEL_SPEC_FIELD_NOT_ALLOWED")
				.map((item) => item.field),
			[forbidden],
			forbidden,
		);
	}
});

test("canonical CAS token produces the backend strong ETag exactly", () => {
	assert.equal(
		toModelSpecEtag({
			id: "40000000-0000-0000-0000-000000000001",
			revision: 7,
			checksum: "a".repeat(64),
		}),
		`"model-spec:40000000-0000-0000-0000-000000000001:7:${"a".repeat(64)}"`,
	);
});

test("all four model types satisfy their deterministic save boundary", () => {
	for (const modelType of ["DIMENSION", "FACT", "SUMMARY", "APPLICATION"] as const) {
		assert.deepEqual(validateModelSpecCreate(valid(modelType)), [], modelType);
	}
});

test("all four model types accept their minimal request with unrelated collections absent", () => {
	for (const modelType of ["DIMENSION", "FACT", "SUMMARY", "APPLICATION"] as const) {
		assert.deepEqual(validateModelSpecCreate(minimal(modelType)), [], modelType);
	}
});

test("dimension draft requires grain and a KEY but not a source or generation strategy", () => {
	const dimension = minimal("DIMENSION");
	assert.deepEqual(validateModelSpecCreate(dimension), []);
	assert.deepEqual(
		validateModelSpecCreate({
			...dimension,
			fields: [{ name: "label", dataType: "varchar", nullable: true, role: "ATTRIBUTE" }],
			generationStrategy: { type: "SEQUENCE" },
		}).map((item) => item.code),
		["MODEL_SPEC_DIMENSION_KEY_REQUIRED"],
	);
	assert.deepEqual(
		validateModelSpecCreate({ ...dimension, grain: undefined }).map((item) => item.code),
		["MODEL_SPEC_GRAIN_REQUIRED"],
	);
});

test("legacy v2 dimension definition and key mapping remain backward compatible", () => {
	const dimension = minimal("DIMENSION");
	assert.deepEqual(
		validateModelSpecCreate({ ...dimension, description: "   " }).map((item) => item.code),
		[],
	);
	assert.deepEqual(
		validateModelSpecCreate({
			...dimension,
			grain: { statement: "one row per record", keys: [" record_id", "record_id "] },
		}).map((item) => item.code),
		[],
	);
	assert.deepEqual(
		validateModelSpecCreate({
			...dimension,
			grain: { statement: "one row per record", keys: ["other_id"] },
		}).map((item) => item.code),
		[],
	);
});

test("FACT draft requires grain and a source but permits fact shape and time semantics to be completed later", () => {
	const fact = minimal("FACT");
	assert.equal(fact.factShape, undefined);
	assert.equal(fact.timeSemantics, undefined);
	assert.deepEqual(validateModelSpecCreate(fact), []);
});

test("standard bindings must resolve to declared fields", () => {
	const fact = valid("FACT");
	const issues = validateModelSpecCreate({
		...fact,
		standardBindings: [{ fieldName: "missing_standard_field", securityLevel: "INTERNAL" }],
	});
	assert.deepEqual(
		issues.map((item) => item.code),
		["MODEL_SPEC_STANDARD_BINDING_INVALID"],
	);
});

test("a field accepts only one standard binding", () => {
	const fact = valid("FACT");
	const binding = { fieldName: "record_id", securityLevel: "INTERNAL" };
	const issues = validateModelSpecCreate({
		...fact,
		standardBindings: [binding, binding],
	});
	assert.deepEqual(
		issues.map((item) => item.code),
		["MODEL_SPEC_STANDARD_BINDING_INVALID"],
	);
});

test("APPLICATION requires a consumption scenario and other model types reject one", () => {
	const application = minimal("APPLICATION");
	assert.deepEqual(validateModelSpecCreate(application), []);
	assert.deepEqual(
		validateModelSpecCreate({ ...application, consumptionScenario: "   " } as unknown).map((item) => item.code),
		["MODEL_SPEC_CONSUMPTION_SCENARIO_REQUIRED"],
	);
	assert.deepEqual(
		validateModelSpecCreate({ ...minimal("FACT"), consumptionScenario: "dashboard" } as unknown).map(
			(item) => item.code,
		),
		["MODEL_SPEC_CONSUMPTION_SCENARIO_NOT_ALLOWED"],
	);
});

test("only FACT accepts optional business activity", () => {
	for (const modelType of ["DIMENSION", "SUMMARY", "APPLICATION"] as const) {
		assert.deepEqual(
			validateModelSpecCreate({ ...valid(modelType), businessActivityRef: "activity-ref" }).map((issue) => issue.code),
			["MODEL_SPEC_BUSINESS_ACTIVITY_NOT_ALLOWED"],
		);
	}
	assert.deepEqual(validateModelSpecCreate({ ...valid("FACT"), businessActivityRef: "activity-ref" }), []);
	assert.deepEqual(validateModelSpecCreate(valid("FACT")), []);
});

test("retired and unknown fields are rejected", () => {
	const input = { ...valid("FACT"), objectId: "retired-object", futureGuess: true } as unknown;
	assert.deepEqual(
		validateModelSpecCreate(input)
			.filter((issue) => issue.code === "MODEL_SPEC_FIELD_NOT_ALLOWED")
			.map((issue) => issue.field)
			.sort(),
		["futureGuess", "objectId"],
	);
});

test("required field codes stay explicit and backend-compatible", () => {
	assert.deepEqual(MODEL_SPEC_REQUIRED_FIELD_CODES, {
		planId: "MODEL_SPEC_PLAN_REQUIRED",
		domainId: "MODEL_SPEC_DOMAIN_REQUIRED",
		modelType: "MODEL_SPEC_TYPE_REQUIRED",
		layer: "MODEL_SPEC_LAYER_REQUIRED",
		name: "MODEL_SPEC_NAME_REQUIRED",
		implementationMode: "MODEL_SPEC_IMPLEMENTATION_MODE_REQUIRED",
		idempotencyKey: "MODEL_SPEC_IDEMPOTENCY_KEY_REQUIRED",
	});
	const invalid = { ...valid("FACT"), planId: "", domainId: "", modelType: undefined } as unknown;
	assert.deepEqual(
		validateModelSpecCreate(invalid)
			.filter((issue) => ["planId", "domainId", "modelType"].includes(issue.field))
			.map((issue) => issue.code),
		["MODEL_SPEC_PLAN_REQUIRED", "MODEL_SPEC_DOMAIN_REQUIRED", "MODEL_SPEC_TYPE_REQUIRED"],
	);
});

test("shared fixtures keep Java and TypeScript validation issue codes aligned", () => {
	const cases = JSON.parse(
		readFileSync(
			new URL(
				"../../../../dts-platform/src/test/resources/fixtures/modeling-v2/validation-cases.json",
				import.meta.url,
			),
			"utf8",
		),
	);
	const schema = JSON.parse(
		readFileSync(
			new URL("../../../../dts-platform/src/main/resources/config/modeling/model-spec-v2.schema.json", import.meta.url),
			"utf8",
		),
	);
	const validateSchema = createContractAjv().compile(schema);
	for (const testCase of cases) {
		assert.equal(typeof testCase.schemaValid, "boolean", `${testCase.name}: schemaValid must be explicit`);
		const candidate = (
			testCase.request ? structuredClone(testCase.request) : { ...valid("FACT"), ...testCase.overrides }
		) as Record<string, unknown>;
		for (const field of testCase.deleteFields ?? []) delete candidate[field];
		assert.deepEqual(
			validateModelSpecCreate(candidate)
				.map((issue) => issue.code)
				.sort(),
			[...testCase.expectedIssueCodes].sort(),
			testCase.name,
		);
		assert.equal(
			validateSchema(candidate),
			testCase.schemaValid,
			`${testCase.name}: ${JSON.stringify(validateSchema.errors)}`,
		);
		if (testCase.expectedSchemaMissingFields) {
			assert.deepEqual(
				(validateSchema.errors ?? [])
					.filter((error) => error.keyword === "required")
					.map((error) => String(error.params.missingProperty))
					.sort(),
				[...testCase.expectedSchemaMissingFields].sort(),
				`${testCase.name}: Ajv must retain all required errors`,
			);
			assert.ok(
				(validateSchema.errors ?? []).some((error) => error.keyword === testCase.expectedSchemaErrorKeyword),
				`${testCase.name}: Ajv must retain ${testCase.expectedSchemaErrorKeyword}`,
			);
		}
	}
	assert.deepEqual(MODEL_SPEC_COLLECTION_FIELDS, [
		"fields",
		"sourceRefs",
		"dependsOn",
		"dimensionRefs",
		"metricRefs",
		"standardBindings",
	]);
});

test("nested references reject incomplete ids and non-positive versions", () => {
	const input = {
		...valid("SUMMARY"),
		sourceRefs: [
			{
				kind: "TABLE",
				ref: "catalog.dataset.source",
				layer: "ODS",
				role: "PRIMARY",
				sortOrder: -1,
				sourceBindingId: "50000000-0000-0000-0000-000000000001",
				resolvedVersion: "v1",
			},
		],
		dependsOn: [{ modelSpecId: "", revision: 0 }],
		dimensionRefs: [{ modelSpecId: "", revision: -1 }],
		metricRefs: [{ metricId: "", version: 0 }],
		standardBindings: [{ fieldName: "record_id", standardElementVersion: 1, referenceCodeVersion: -1 }],
	} as unknown;

	assert.deepEqual(
		new Set(validateModelSpecCreate(input).map((issue) => issue.code)),
		new Set([
			"MODEL_SPEC_SOURCE_INVALID",
			"MODEL_SPEC_DEPENDENCY_INVALID",
			"MODEL_SPEC_DIMENSION_REF_INVALID",
			"MODEL_SPEC_METRIC_REF_INVALID",
			"MODEL_SPEC_STANDARD_BINDING_INVALID",
		]),
	);
});

test("JSON Schema accepts canonical view and rejects client-only or unknown view fields", () => {
	const schema = JSON.parse(
		readFileSync(
			new URL("../../../../dts-platform/src/main/resources/config/modeling/model-spec-v2.schema.json", import.meta.url),
			"utf8",
		),
	);
	const ajv = createContractAjv();
	ajv.addSchema(schema);
	const validate = ajv.getSchema(`${schema.$id}#/$defs/modelSpecView`);
	assert.ok(validate);
	const { idempotencyKey: _idempotencyKey, ...body } = valid("FACT");
	const view = {
		...body,
		contractVersion: 2,
		id: "40000000-0000-0000-0000-000000000001",
		status: "DRAFT",
		revision: 1,
		checksum: "a".repeat(64),
		createdAt: "2026-07-19T00:00:00Z",
		updatedAt: "2026-07-19T00:00:00Z",
		compatibilityMode: "CANONICAL",
		legacyRefs: null,
	};

	assert.equal(validate(view), true, JSON.stringify(validate.errors));
	assert.equal(validate({ ...view, idempotencyKey: "server-must-not-return-this" }), false);
	assert.equal(validate({ ...view, futureGuess: true }), false);
	assert.equal(validate({ ...view, id: "not-a-uuid" }), false);
	assert.equal(validate({ ...view, createdAt: "2026-02-30T25:61:61Z" }), false);
});

test("JSON Schema update payload is a strict full replacement without create-only or legacy fields", () => {
	const schema = JSON.parse(
		readFileSync(
			new URL("../../../../dts-platform/src/main/resources/config/modeling/model-spec-v2.schema.json", import.meta.url),
			"utf8",
		),
	);
	const ajv = createContractAjv();
	ajv.addSchema(schema);
	const validate = ajv.getSchema(`${schema.$id}#/$defs/updateModelSpecCommand`);
	assert.ok(validate);
	const { idempotencyKey: _idempotencyKey, ...update } = valid("FACT");
	assert.equal(validate(update), true, JSON.stringify(validate.errors));
	for (const forbidden of ["idempotencyKey", "objectId", "processId", "legacyRef", "legacyRefs"]) {
		assert.equal(validate({ ...update, [forbidden]: "forbidden" }), false, forbidden);
	}
});

test("JSON Schema directly accepts the generic wire fixture including explicit null optionals", () => {
	const schema = JSON.parse(
		readFileSync(
			new URL("../../../../dts-platform/src/main/resources/config/modeling/model-spec-v2.schema.json", import.meta.url),
			"utf8",
		),
	);
	const genericFact = JSON.parse(
		readFileSync(
			new URL("../../../../dts-platform/src/test/resources/fixtures/modeling-v2/generic-fact.json", import.meta.url),
			"utf8",
		),
	);
	const validate = createContractAjv().compile(schema);

	assert.equal(validate(genericFact), true, JSON.stringify(validate.errors));
});

test("JSON Schema declares executable trim-aware uniqueness for fields and sources", () => {
	const schema = JSON.parse(
		readFileSync(
			new URL("../../../../dts-platform/src/main/resources/config/modeling/model-spec-v2.schema.json", import.meta.url),
			"utf8",
		),
	);
	const fields = schema.$defs.createModelSpecCommand.properties.fields;
	const sourceRefs = schema.$defs.createModelSpecCommand.properties.sourceRefs;

	assert.equal(fields.uniqueItems, true);
	assert.deepEqual(fields["x-dtsUniqueBy"], ["name"]);
	assert.equal(sourceRefs.uniqueItems, true);
	assert.deepEqual(sourceRefs["x-dtsUniqueBy"], ["kind", "ref"]);
});

test("model views are a strict contract-version and compatibility-mode discriminated union", () => {
	const schema = JSON.parse(
		readFileSync(
			new URL("../../../../dts-platform/src/main/resources/config/modeling/model-spec-v2.schema.json", import.meta.url),
			"utf8",
		),
	);
	const ajv = createContractAjv();
	ajv.addSchema(schema);
	const validate = ajv.getSchema(`${schema.$id}#/$defs/modelSpecView`);
	assert.ok(validate);

	for (const modelType of ["DIMENSION", "FACT", "SUMMARY", "APPLICATION"] as const) {
		const { idempotencyKey: _idempotencyKey, ...body } = valid(modelType);
		const invalidBoundary = {
			...body,
			...(modelType === "DIMENSION"
				? { fields: [{ name: "label", dataType: "varchar", nullable: true, role: "ATTRIBUTE" }] }
				: {}),
			...(modelType === "FACT" ? { sourceRefs: [] } : {}),
			...(modelType === "SUMMARY" ? { dependsOn: [] } : {}),
			...(modelType === "APPLICATION" ? { consumptionScenario: null } : {}),
			contractVersion: 2,
			id: "40000000-0000-0000-0000-000000000001",
			status: "DRAFT",
			revision: 1,
			checksum: "a".repeat(64),
			createdAt: "2026-07-19T00:00:00Z",
			updatedAt: "2026-07-19T00:00:00Z",
			compatibilityMode: "CANONICAL",
			legacyRefs: null,
		};

		assert.equal(validate(invalidBoundary), false, `${modelType} canonical: ${JSON.stringify(validate.errors)}`);
		const legacyView = {
			...invalidBoundary,
			contractVersion: 1,
			planId: null,
			domainId: null,
			sourceRefs: invalidBoundary.sourceRefs.map((source) => ({
				...source,
				sourceBindingId: null,
				resolvedVersion: null,
			})),
			compatibilityMode: "LEGACY_READONLY",
			legacyRefs: {
				legacyModelRef: "legacy-model-ref",
				unresolvedDependencyRefs: ["legacy-upstream-id"],
				unresolvedSourceRefs: [{ kind: "LEGACY_FILE", ref: "legacy/customer.csv", layer: "ODS" }],
				unresolvedStandardRefs: [
					{
						fieldName: "record_id",
						standardElementId: "legacy.standard.id",
						referenceCode: null,
						securityLevel: null,
					},
				],
			},
		};
		assert.equal(validate(legacyView), true, `${modelType} legacy: ${JSON.stringify(validate.errors)}`);
		assert.equal(validate({ ...legacyView, contractVersion: 2 }), false, "v2 cannot claim LEGACY_READONLY");
		assert.equal(validate({ ...legacyView, compatibilityMode: "CANONICAL" }), false, "v1 cannot claim CANONICAL");
		assert.equal(validate({ ...legacyView, legacyRefs: null }), false, "legacy read must preserve legacyRefs");
	}
});

test("JSON Schema enforces all four model-type save boundaries", () => {
	const schema = JSON.parse(
		readFileSync(
			new URL("../../../../dts-platform/src/main/resources/config/modeling/model-spec-v2.schema.json", import.meta.url),
			"utf8",
		),
	);
	const ajv = createContractAjv();
	const validate = ajv.compile(schema);

	for (const modelType of ["DIMENSION", "FACT", "SUMMARY", "APPLICATION"] as const) {
		assert.equal(validate(valid(modelType)), true, `${modelType}: ${JSON.stringify(validate.errors)}`);
		assert.equal(validate(minimal(modelType)), true, `minimal ${modelType}: ${JSON.stringify(validate.errors)}`);
	}
	assert.equal(validate({ ...valid("FACT"), grain: undefined }), false);
	assert.equal(validate({ ...valid("FACT"), name: "   " }), false);
	assert.equal(validate({ ...valid("FACT"), idempotencyKey: "   " }), false);
	assert.equal(validate({ ...valid("FACT"), name: "x".repeat(257) }), false);
	assert.equal(validate({ ...valid("SUMMARY"), dependsOn: [] }), false);
	assert.equal(validate({ ...valid("APPLICATION"), businessActivityRef: "not-allowed" }), false);
	assert.equal(validate({ ...valid("DIMENSION"), fields: [], sourceRefs: [], generationStrategy: undefined }), false);
	assert.equal(
		validate({
			...minimal("DIMENSION"),
			fields: [{ name: "label", dataType: "varchar", nullable: true, role: "ATTRIBUTE" }],
			generationStrategy: { type: "SEQUENCE" },
		}),
		false,
	);
	assert.equal(validate({ ...minimal("DIMENSION"), grain: { statement: "one row per record", keys: [] } }), false);
	assert.equal(validate({ ...valid("FACT"), timeSemantics: { type: "EVENT_TIME", fields: [] } }), false);
	assert.equal(
		validate({ ...valid("FACT"), standardBindings: [{ fieldName: "record_id", securityLevel: "   " }] }),
		false,
	);
});
