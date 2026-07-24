export const MODEL_SPEC_CONTRACT_VERSION = 2 as const;

export const MODEL_SPEC_CREATE_FIELDS = [
	"planId",
	"domainId",
	"modelType",
	"name",
	"description",
	"dimensionDefinitionRef",
	"idempotencyKey",
] as const;

export const MODEL_SPEC_UPDATE_FIELDS = [
	"planId",
	"domainId",
	"modelType",
	"layer",
	"name",
	"description",
	"implementationMode",
	"materialization",
	"businessActivityRef",
	"consumptionScenario",
	"grain",
	"factShape",
	"timeSemantics",
	"fields",
	"sourceRefs",
	"dependsOn",
	"dimensionRefs",
	"metricRefs",
	"standardBindings",
	"generationStrategy",
	"dimensionProfile",
] as const;

export const MODEL_SPEC_REQUIRED_FIELD_CODES = {
	planId: "MODEL_SPEC_PLAN_REQUIRED",
	domainId: "MODEL_SPEC_DOMAIN_REQUIRED",
	modelType: "MODEL_SPEC_TYPE_REQUIRED",
	name: "MODEL_SPEC_NAME_REQUIRED",
	idempotencyKey: "MODEL_SPEC_IDEMPOTENCY_KEY_REQUIRED",
} as const;

const MODEL_SPEC_FULL_REQUIRED_FIELD_CODES = {
	...MODEL_SPEC_REQUIRED_FIELD_CODES,
	layer: "MODEL_SPEC_LAYER_REQUIRED",
	implementationMode: "MODEL_SPEC_IMPLEMENTATION_MODE_REQUIRED",
} as const;
const MODEL_SPEC_FULL_FIELDS = [...MODEL_SPEC_UPDATE_FIELDS, "idempotencyKey"] as const;

export const MODEL_SPEC_COLLECTION_FIELDS = [
	"fields",
	"sourceRefs",
	"dependsOn",
	"dimensionRefs",
	"metricRefs",
	"standardBindings",
] as const;

export type ModelSpecLayer = "ODS" | "STG" | "DWD" | "DWS" | "ADS";
export type ModelSpecType = "FACT" | "DIMENSION" | "SUMMARY" | "APPLICATION";
export const MODEL_SPEC_TARGET_LAYER_BY_TYPE: Readonly<Record<ModelSpecType, ModelSpecLayer>> = {
	DIMENSION: "DWD",
	FACT: "DWD",
	SUMMARY: "DWS",
	APPLICATION: "ADS",
};
const MODEL_SPEC_DIRECT_INPUT_LAYERS = new Set<ModelSpecLayer>(["ODS", "STG", "DWD"]);

export const isModelSpecDirectInputLayerAllowed = (layer: ModelSpecLayer): boolean =>
	MODEL_SPEC_DIRECT_INPUT_LAYERS.has(layer);

export const isModelSpecUpstreamAllowed = (
	targetType: ModelSpecType,
	candidate: Pick<UpdateModelSpecCommand, "modelType" | "layer">,
): boolean => {
	if (MODEL_SPEC_TARGET_LAYER_BY_TYPE[candidate.modelType] !== candidate.layer) return false;
	if (targetType === "FACT") return candidate.modelType === "FACT" && candidate.layer === "DWD";
	if (targetType === "SUMMARY") return candidate.layer === "DWD" || candidate.layer === "DWS";
	if (targetType === "APPLICATION") return ["DWD", "DWS", "ADS"].includes(candidate.layer);
	return false;
};

export const isModelSpecDimensionRefAllowed = (
candidate: Pick<UpdateModelSpecCommand, "modelType" | "layer">,
): boolean => candidate.modelType === "DIMENSION" && candidate.layer === MODEL_SPEC_TARGET_LAYER_BY_TYPE.DIMENSION;

export type ModelSpecImplementationMode = "DESIGNER_GENERATED" | "DBT_MANAGED";
export type ModelSpecFactShape = "TRANSACTION" | "PERIODIC_SNAPSHOT" | "ACCUMULATING_SNAPSHOT";
export type ModelSpecTimeSemanticsType = "EVENT_TIME" | "SNAPSHOT_DATE" | "PERIOD" | "MILESTONE_DATES";
export type ModelSpecFieldRole = "KEY" | "ATTRIBUTE" | "TIME" | "MEASURE";
export type ModelSpecSourceKind = "TABLE" | "DBT_MODEL" | "DATASET";
export type ModelSpecSourceRole = "PRIMARY" | "JOINED";
export type ModelSpecJoinType = "INNER" | "LEFT" | "RIGHT" | "FULL";
export type ModelSpecStatus = "DRAFT" | "DESIGNING" | "VALIDATING" | "READY_TO_PUBLISH" | "PUBLISHED" | "ARCHIVED";
export type ModelSpecCompatibilityMode = "CANONICAL" | "LEGACY_READONLY";
export type ModelSpecScdType = "NONE" | "TYPE1" | "TYPE2";
export type ModelSpecReuseScope = "PLAN" | "DOMAIN" | "TENANT";

export type ModelSpecGrain = { statement: string; keys: string[] };
export type ModelSpecTimeSemantics = { type: ModelSpecTimeSemanticsType; fields: string[] };
export type ModelSpecField = {
	name: string;
	dataType: string;
	nullable: boolean;
	sourceFieldRef?: string | null;
	role: ModelSpecFieldRole;
	securityLevel?: string | null;
};
export type ModelSpecSourceRef = {
	kind: ModelSpecSourceKind;
	ref: string;
	layer: ModelSpecLayer;
	role: ModelSpecSourceRole;
	alias?: string | null;
	joinType?: ModelSpecJoinType | null;
	joinExpression?: string | null;
	sortOrder: number;
	sourceBindingId: string;
	resolvedVersion: string;
};
export type ModelSpecLegacySourceRef = Omit<ModelSpecSourceRef, "sourceBindingId" | "resolvedVersion"> & {
	sourceBindingId: null;
	resolvedVersion: null;
};
export type ModelSpecRevisionRef = { modelSpecId: string; revision: number };
export type ModelSpecMetricRef = { metricId: string; version: number };
export type ModelSpecStandardBinding = {
	fieldName: string;
	standardElementId?: string | null;
	standardElementVersion?: number | null;
	referenceCode?: string | null;
	referenceCodeVersion?: number | null;
	measurementUnitId?: string | null;
	measurementUnitVersion?: number | null;
	securityLevel?: string | null;
};
export type ModelSpecGenerationStrategy = { type: string; reference?: string | null };
export type ModelSpecDimensionLevel = { fieldName: string; order: number };
export type ModelSpecDimensionHierarchy = {
	code: string;
	name: string;
	levels: ModelSpecDimensionLevel[];
};
export type ModelSpecScdPolicy = {
	type: ModelSpecScdType;
	effectiveFromField?: string | null;
	effectiveToField?: string | null;
	currentFlagField?: string | null;
};
export type ModelSpecDimensionProfile = {
	dimensionCode: string;
	hierarchies: ModelSpecDimensionHierarchy[];
	scdPolicy: ModelSpecScdPolicy;
	reuseScope: ModelSpecReuseScope;
};
export type ModelSpecDimensionDefinitionRef = {
	dimensionDefinitionId: string;
	revision: number;
};
export type ModelSpecLegacySource = { kind: string | null; ref: string | null; layer: string | null };
export type ModelSpecLegacyStandard = {
	fieldName: string | null;
	standardElementId: string | null;
	referenceCode: string | null;
	securityLevel: string | null;
};
export type ModelSpecLegacyRefs = {
	legacyModelRef: string | null;
	unresolvedDependencyRefs: string[];
	unresolvedSourceRefs: ModelSpecLegacySource[];
	unresolvedStandardRefs: ModelSpecLegacyStandard[];
};

export type ModelSpecCollections = {
	fields: ModelSpecField[];
	sourceRefs: ModelSpecSourceRef[];
	dependsOn: ModelSpecRevisionRef[];
	dimensionRefs: ModelSpecRevisionRef[];
	metricRefs: ModelSpecMetricRef[];
	standardBindings: ModelSpecStandardBinding[];
};

/**
 * Create intentionally carries only the user's initial identity and optional DIMENSION definition.
 * Layer, implementation ownership, sources and logical collections are all server defaults.
 */
type CreateModelSpecBase = {
	planId: string;
	domainId: string;
	name: string;
	description?: string | null;
	idempotencyKey: string;
};

export type CreateModelSpecCommand =
	| (CreateModelSpecBase & {
			modelType: "DIMENSION";
			dimensionDefinitionRef: ModelSpecDimensionDefinitionRef;
		})
	| (CreateModelSpecBase & {
			modelType: Exclude<ModelSpecType, "DIMENSION">;
			dimensionDefinitionRef?: never;
		});

export type UpdateModelSpecCommand = {
	planId: string;
	domainId: string;
	modelType: ModelSpecType;
	layer: ModelSpecLayer;
	name: string;
	description?: string | null;
	implementationMode: ModelSpecImplementationMode;
	materialization?: string | null;
	businessActivityRef?: string | null;
	consumptionScenario?: string | null;
	grain?: ModelSpecGrain | null;
	factShape?: ModelSpecFactShape | null;
	timeSemantics?: ModelSpecTimeSemantics | null;
	generationStrategy?: ModelSpecGenerationStrategy | null;
	dimensionProfile?: ModelSpecDimensionProfile | null;
} & Partial<ModelSpecCollections>;

export const hasModelSpecTypeBoundaryMismatch = (
	model: Pick<
		UpdateModelSpecCommand,
		| "modelType"
		| "factShape"
		| "timeSemantics"
		| "businessActivityRef"
		| "consumptionScenario"
		| "sourceRefs"
		| "dependsOn"
		| "dimensionRefs"
		| "generationStrategy"
		| "dimensionProfile"
	>,
): boolean => {
	const acceptsPhysicalSources = model.modelType === "DIMENSION" || model.modelType === "FACT";
	const acceptsModelDependencies = model.modelType !== "DIMENSION";
	return (
		(!acceptsPhysicalSources && Boolean(model.sourceRefs?.length)) ||
		(!acceptsModelDependencies && Boolean(model.dependsOn?.length)) ||
		(model.dimensionRefs != null && model.modelType !== "FACT" && model.dimensionRefs.length > 0) ||
		(model.modelType !== "DIMENSION" && model.generationStrategy != null) ||
		(model.modelType !== "DIMENSION" && model.dimensionProfile != null) ||
		(model.modelType !== "FACT" && model.factShape != null) ||
		(model.modelType !== "FACT" && model.timeSemantics != null) ||
		(model.modelType !== "FACT" && isNonBlankString(model.businessActivityRef)) ||
		(model.modelType !== "APPLICATION" && isNonBlankString(model.consumptionScenario))
	);
};

type ModelSpecViewBase = Omit<UpdateModelSpecCommand, "planId" | "domainId" | keyof ModelSpecCollections> & {
	dimensionDefinitionRef: ModelSpecDimensionDefinitionRef | null;
	id: string;
	status: ModelSpecStatus;
	revision: number;
	checksum: string;
	createdAt: string;
	updatedAt: string;
};

export type CanonicalModelSpecView = ModelSpecViewBase &
	ModelSpecCollections & {
		contractVersion: typeof MODEL_SPEC_CONTRACT_VERSION;
		planId: string;
		domainId: string;
		compatibilityMode: "CANONICAL";
		legacyRefs: null;
	};

export type LegacyModelSpecView = ModelSpecViewBase &
	Omit<ModelSpecCollections, "sourceRefs"> & {
		contractVersion: 1;
		planId: string | null;
		domainId: string | null;
		sourceRefs: ModelSpecLegacySourceRef[];
		compatibilityMode: "LEGACY_READONLY";
		legacyRefs: ModelSpecLegacyRefs;
	};

export type ModelSpecView = CanonicalModelSpecView | LegacyModelSpecView;

export const isCanonicalModelSpecReferenceTarget = (candidate: ModelSpecView): candidate is CanonicalModelSpecView =>
	candidate.contractVersion === MODEL_SPEC_CONTRACT_VERSION &&
	candidate.compatibilityMode === "CANONICAL" &&
	MODEL_SPEC_TARGET_LAYER_BY_TYPE[candidate.modelType] === candidate.layer &&
	candidate.sourceRefs.every((source) => isModelSpecDirectInputLayerAllowed(source.layer)) &&
	!hasModelSpecTypeBoundaryMismatch(candidate);

export const isModelSpecReferenceTargetAllowed = (
	owner: Pick<CanonicalModelSpecView, "modelType" | "planId">,
	candidate: ModelSpecView,
	kind: "DEPENDENCY" | "DIMENSION",
): boolean => {
	if (!isCanonicalModelSpecReferenceTarget(candidate)) return false;
	if (candidate.planId !== owner.planId && candidate.status !== "PUBLISHED") return false;
	return kind === "DIMENSION"
		? isModelSpecDimensionRefAllowed(candidate)
		: isModelSpecUpstreamAllowed(owner.modelType, candidate);
};

export const modelSpecRevisionRefKey = (reference: ModelSpecRevisionRef): string =>
	`${reference.modelSpecId}@${reference.revision}`;

export type ModelSpecCasToken = Pick<CanonicalModelSpecView, "id" | "revision" | "checksum">;

export type ModelSpecRevisionConflictDetails = {
	currentRevision: number;
	currentChecksum: string;
	currentEtag: string;
};

export const toModelSpecEtag = ({ id, revision, checksum }: ModelSpecCasToken) =>
	`"model-spec:${id}:${revision}:${checksum}"`;

export type ModelSpecFieldIssue = {
	code: string;
	field: string;
	severity: "ERROR" | "WARNING";
	message: string;
};

const issue = (code: string, field: string, message: string): ModelSpecFieldIssue => ({
	code,
	field,
	severity: "ERROR",
	message,
});

const MODEL_SPEC_NESTED_COLLECTION_FIELDS: Record<
	(typeof MODEL_SPEC_COLLECTION_FIELDS)[number],
	ReadonlySet<string>
> = {
	fields: new Set(["name", "dataType", "nullable", "sourceFieldRef", "role", "securityLevel"]),
	sourceRefs: new Set([
		"kind",
		"ref",
		"layer",
		"role",
		"alias",
		"joinType",
		"joinExpression",
		"sortOrder",
		"sourceBindingId",
		"resolvedVersion",
	]),
	dependsOn: new Set(["modelSpecId", "revision"]),
	dimensionRefs: new Set(["modelSpecId", "revision"]),
	metricRefs: new Set(["metricId", "version"]),
	standardBindings: new Set([
		"fieldName",
		"standardElementId",
		"standardElementVersion",
		"referenceCode",
		"referenceCodeVersion",
		"measurementUnitId",
		"measurementUnitVersion",
		"securityLevel",
	]),
};

const MODEL_SPEC_NESTED_COLLECTION_ISSUE_CODES: Record<(typeof MODEL_SPEC_COLLECTION_FIELDS)[number], string> = {
	fields: "MODEL_SPEC_FIELD_INVALID",
	sourceRefs: "MODEL_SPEC_SOURCE_INVALID",
	dependsOn: "MODEL_SPEC_DEPENDENCY_INVALID",
	dimensionRefs: "MODEL_SPEC_DIMENSION_REF_INVALID",
	metricRefs: "MODEL_SPEC_METRIC_REF_INVALID",
	standardBindings: "MODEL_SPEC_STANDARD_BINDING_INVALID",
};

const MODEL_SPEC_LAYERS = new Set<ModelSpecLayer>(["ODS", "STG", "DWD", "DWS", "ADS"]);
const MODEL_SPEC_TYPES = new Set<ModelSpecType>(["FACT", "DIMENSION", "SUMMARY", "APPLICATION"]);
const MODEL_SPEC_IMPLEMENTATION_MODES = new Set<ModelSpecImplementationMode>(["DESIGNER_GENERATED", "DBT_MANAGED"]);
const MODEL_SPEC_FACT_SHAPES = new Set<ModelSpecFactShape>([
	"TRANSACTION",
	"PERIODIC_SNAPSHOT",
	"ACCUMULATING_SNAPSHOT",
]);
const MODEL_SPEC_TIME_TYPES = new Set<ModelSpecTimeSemanticsType>([
	"EVENT_TIME",
	"SNAPSHOT_DATE",
	"PERIOD",
	"MILESTONE_DATES",
]);
const MODEL_SPEC_FIELD_ROLES = new Set<ModelSpecFieldRole>(["KEY", "ATTRIBUTE", "TIME", "MEASURE"]);
const MODEL_SPEC_SOURCE_KINDS = new Set<ModelSpecSourceKind>(["TABLE", "DBT_MODEL", "DATASET"]);
const MODEL_SPEC_SOURCE_ROLES = new Set<ModelSpecSourceRole>(["PRIMARY", "JOINED"]);
const MODEL_SPEC_JOIN_TYPES = new Set<ModelSpecJoinType>(["INNER", "LEFT", "RIGHT", "FULL"]);
const GRAIN_FIELDS = new Set(["statement", "keys"]);
const TIME_SEMANTICS_FIELDS = new Set(["type", "fields"]);
const GENERATION_STRATEGY_FIELDS = new Set(["type", "reference"]);
const DIMENSION_PROFILE_FIELDS = new Set(["dimensionCode", "hierarchies", "scdPolicy", "reuseScope"]);
const DIMENSION_HIERARCHY_FIELDS = new Set(["code", "name", "levels"]);
const DIMENSION_LEVEL_FIELDS = new Set(["fieldName", "order"]);
const DIMENSION_SCD_POLICY_FIELDS = new Set(["type", "effectiveFromField", "effectiveToField", "currentFlagField"]);
const MODEL_SPEC_SCD_TYPES = new Set<ModelSpecScdType>(["NONE", "TYPE1", "TYPE2"]);
const MODEL_SPEC_REUSE_SCOPES = new Set<ModelSpecReuseScope>(["PLAN", "DOMAIN", "TENANT"]);
const DIMENSION_CODE_PATTERN = /^[A-Z][A-Z0-9_]{0,63}$/;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const JAVA_INT_MAX = 2_147_483_647;

const isRecord = (value: unknown): value is Record<string, unknown> =>
	value !== null && typeof value === "object" && !Array.isArray(value);
function isNonBlankString(value: unknown): value is string {
	return typeof value === "string" && Boolean(value.trim());
}
const isNullableString = (value: unknown) => value == null || typeof value === "string";
const isUuid = (value: unknown): value is string => typeof value === "string" && UUID_PATTERN.test(value);
const isIntInRange = (value: unknown, minimum: number) =>
	Number.isInteger(value) && Number(value) >= minimum && Number(value) <= JAVA_INT_MAX;
const isMissingText = (value: unknown) => typeof value === "string" && !value.trim();
const hasOnlyFields = (value: Record<string, unknown>, allowed: ReadonlySet<string>) =>
	Object.keys(value).every((field) => allowed.has(field));

const rawModelSpecIssues = (raw: Record<string, unknown>): ModelSpecFieldIssue[] => {
	const issues: ModelSpecFieldIssue[] = [];
	const add = (code: string, field: string, message: string) => {
		if (!issues.some((candidate) => candidate.code === code && candidate.field === field)) {
			issues.push(issue(code, field, message));
		}
	};
	const allowed = new Set<string>(MODEL_SPEC_FULL_FIELDS);
	for (const field of Object.keys(raw)
		.filter((field) => !allowed.has(field))
		.sort()) {
		add("MODEL_SPEC_FIELD_NOT_ALLOWED", field, "Field is not part of the canonical ModelSpec create contract");
	}
	for (const field of MODEL_SPEC_COLLECTION_FIELDS) {
		if (Object.hasOwn(raw, field) && !Array.isArray(raw[field])) {
			add("MODEL_SPEC_COLLECTION_INVALID", field, "Collection field must be an array when present");
		}
	}
	for (const [field, code] of Object.entries(MODEL_SPEC_FULL_REQUIRED_FIELD_CODES) as [
		keyof typeof MODEL_SPEC_FULL_REQUIRED_FIELD_CODES,
		(typeof MODEL_SPEC_FULL_REQUIRED_FIELD_CODES)[keyof typeof MODEL_SPEC_FULL_REQUIRED_FIELD_CODES],
	][]) {
		const value = raw[field];
		if (value == null || (typeof value === "string" && !value.trim())) {
			add(code, field, "Required field is missing");
		}
	}
	if (raw.planId != null && !isMissingText(raw.planId) && !isUuid(raw.planId)) {
		add("MODEL_SPEC_PLAN_INVALID", "planId", "Plan id must be a UUID");
	}
	if (raw.domainId != null && !isMissingText(raw.domainId) && !isUuid(raw.domainId)) {
		add("MODEL_SPEC_DOMAIN_INVALID", "domainId", "Domain id must be a UUID");
	}
	if (raw.modelType != null && !MODEL_SPEC_TYPES.has(raw.modelType as ModelSpecType)) {
		add("MODEL_SPEC_TYPE_INVALID", "modelType", "Model type is not supported");
	}
	if (raw.layer != null && !MODEL_SPEC_LAYERS.has(raw.layer as ModelSpecLayer)) {
		add("MODEL_SPEC_LAYER_INVALID", "layer", "Layer is not supported");
	}
	if (raw.name != null && typeof raw.name !== "string")
		add("MODEL_SPEC_NAME_INVALID", "name", "Model name must be text");
	if (
		raw.implementationMode != null &&
		!MODEL_SPEC_IMPLEMENTATION_MODES.has(raw.implementationMode as ModelSpecImplementationMode)
	) {
		add("MODEL_SPEC_IMPLEMENTATION_MODE_INVALID", "implementationMode", "Implementation mode is not supported");
	}
	if (raw.idempotencyKey != null && typeof raw.idempotencyKey !== "string") {
		add("MODEL_SPEC_IDEMPOTENCY_KEY_INVALID", "idempotencyKey", "Idempotency key must be text");
	}
	for (const field of ["description", "materialization"] as const) {
		if (!isNullableString(raw[field]))
			add("MODEL_SPEC_FIELD_INVALID", field, "Optional text field must be text or null");
	}
	if (!isNullableString(raw.businessActivityRef)) {
		add("MODEL_SPEC_BUSINESS_ACTIVITY_INVALID", "businessActivityRef", "Business activity must be text or null");
	}
	if (!isNullableString(raw.consumptionScenario)) {
		add("MODEL_SPEC_CONSUMPTION_SCENARIO_INVALID", "consumptionScenario", "Consumption scenario must be text or null");
	}
	if (raw.factShape != null && !MODEL_SPEC_FACT_SHAPES.has(raw.factShape as ModelSpecFactShape)) {
		add("MODEL_SPEC_FACT_SHAPE_INVALID", "factShape", "Fact shape is not supported");
	}

	if (
		raw.grain != null &&
		(!isRecord(raw.grain) ||
			!hasOnlyFields(raw.grain, GRAIN_FIELDS) ||
			!isNonBlankString(raw.grain.statement) ||
			!Array.isArray(raw.grain.keys) ||
			raw.grain.keys.length === 0 ||
			!raw.grain.keys.every(isNonBlankString))
	) {
		add("MODEL_SPEC_GRAIN_INVALID", "grain", "Grain requires a statement and non-empty keys");
	}
	if (
		raw.timeSemantics != null &&
		(!isRecord(raw.timeSemantics) ||
			!hasOnlyFields(raw.timeSemantics, TIME_SEMANTICS_FIELDS) ||
			!MODEL_SPEC_TIME_TYPES.has(raw.timeSemantics.type as ModelSpecTimeSemanticsType) ||
			!Array.isArray(raw.timeSemantics.fields) ||
			raw.timeSemantics.fields.length === 0 ||
			!raw.timeSemantics.fields.every(isNonBlankString))
	) {
		add("MODEL_SPEC_TIME_SEMANTICS_INVALID", "timeSemantics", "Time semantics are invalid");
	}
	if (
		raw.generationStrategy != null &&
		(!isRecord(raw.generationStrategy) ||
			!hasOnlyFields(raw.generationStrategy, GENERATION_STRATEGY_FIELDS) ||
			!isNonBlankString(raw.generationStrategy.type) ||
			!isNullableString(raw.generationStrategy.reference))
	) {
		add("MODEL_SPEC_GENERATION_STRATEGY_INVALID", "generationStrategy", "Generation strategy is invalid");
	}
	if (raw.dimensionProfile != null) {
		if (!isRecord(raw.dimensionProfile) || !hasOnlyFields(raw.dimensionProfile, DIMENSION_PROFILE_FIELDS)) {
			add("MODEL_SPEC_DIMENSION_PROFILE_INVALID", "dimensionProfile", "Dimension profile is invalid");
		} else {
			const profile = raw.dimensionProfile;
			if (!isNonBlankString(profile.dimensionCode) || !DIMENSION_CODE_PATTERN.test(profile.dimensionCode.trim())) {
				add("MODEL_SPEC_DIMENSION_CODE_INVALID", "dimensionProfile", "Dimension code is invalid");
			}
			if (!MODEL_SPEC_REUSE_SCOPES.has(profile.reuseScope as ModelSpecReuseScope)) {
				add("MODEL_SPEC_DIMENSION_REUSE_SCOPE_INVALID", "dimensionProfile.reuseScope", "Reuse scope is invalid");
			}
			if (
				!Array.isArray(profile.hierarchies) ||
				profile.hierarchies.some(
					(hierarchy) =>
						!isRecord(hierarchy) ||
						!hasOnlyFields(hierarchy, DIMENSION_HIERARCHY_FIELDS) ||
						!isNonBlankString(hierarchy.code) ||
						!DIMENSION_CODE_PATTERN.test(hierarchy.code.trim()) ||
						!isNonBlankString(hierarchy.name) ||
						!Array.isArray(hierarchy.levels) ||
						hierarchy.levels.length === 0 ||
						hierarchy.levels.some(
							(level) =>
								!isRecord(level) ||
								!hasOnlyFields(level, DIMENSION_LEVEL_FIELDS) ||
								!isNonBlankString(level.fieldName) ||
								!isIntInRange(level.order, 1),
						),
				)
			) {
				add("MODEL_SPEC_DIMENSION_HIERARCHY_INVALID", "dimensionProfile.hierarchies", "Hierarchy is invalid");
			}
			if (
				!isRecord(profile.scdPolicy) ||
				!hasOnlyFields(profile.scdPolicy, DIMENSION_SCD_POLICY_FIELDS) ||
				!MODEL_SPEC_SCD_TYPES.has(profile.scdPolicy.type as ModelSpecScdType) ||
				!isNullableString(profile.scdPolicy.effectiveFromField) ||
				!isNullableString(profile.scdPolicy.effectiveToField) ||
				!isNullableString(profile.scdPolicy.currentFlagField)
			) {
				add("MODEL_SPEC_DIMENSION_SCD_INVALID", "dimensionProfile.scdPolicy", "SCD policy is invalid");
			}
		}
	}

	const invalidCollection = (
		field: (typeof MODEL_SPEC_COLLECTION_FIELDS)[number],
		invalid: (item: Record<string, unknown>) => boolean,
	) => {
		const values = raw[field];
		if (
			Array.isArray(values) &&
			values.some(
				(value) =>
					!isRecord(value) || !hasOnlyFields(value, MODEL_SPEC_NESTED_COLLECTION_FIELDS[field]) || invalid(value),
			)
		) {
			add(MODEL_SPEC_NESTED_COLLECTION_ISSUE_CODES[field], field, "Collection item is outside the canonical contract");
		}
	};
	invalidCollection(
		"fields",
		(item) =>
			!isNonBlankString(item.name) ||
			!isNonBlankString(item.dataType) ||
			typeof item.nullable !== "boolean" ||
			!MODEL_SPEC_FIELD_ROLES.has(item.role as ModelSpecFieldRole) ||
			!isNullableString(item.sourceFieldRef) ||
			!isNullableString(item.securityLevel),
	);
	invalidCollection(
		"sourceRefs",
		(item) =>
			!MODEL_SPEC_SOURCE_KINDS.has(item.kind as ModelSpecSourceKind) ||
			!isNonBlankString(item.ref) ||
			!MODEL_SPEC_LAYERS.has(item.layer as ModelSpecLayer) ||
			!MODEL_SPEC_SOURCE_ROLES.has(item.role as ModelSpecSourceRole) ||
			(item.joinType != null && !MODEL_SPEC_JOIN_TYPES.has(item.joinType as ModelSpecJoinType)) ||
			!isNullableString(item.alias) ||
			!isNullableString(item.joinExpression) ||
			!isIntInRange(item.sortOrder, 0) ||
			!isUuid(item.sourceBindingId) ||
			!isNonBlankString(item.resolvedVersion),
	);
	const invalidRevisionRef = (item: Record<string, unknown>) =>
		!isUuid(item.modelSpecId) || !isIntInRange(item.revision, 1);
	invalidCollection("dependsOn", invalidRevisionRef);
	invalidCollection("dimensionRefs", invalidRevisionRef);
	invalidCollection("metricRefs", (item) => !isNonBlankString(item.metricId) || !isIntInRange(item.version, 1));
	invalidCollection(
		"standardBindings",
		(item) =>
			!isNonBlankString(item.fieldName) ||
			(item.standardElementId != null && !isUuid(item.standardElementId)) ||
			(item.standardElementVersion != null && !isIntInRange(item.standardElementVersion, 1)) ||
			!isNullableString(item.referenceCode) ||
			(item.referenceCodeVersion != null && !isIntInRange(item.referenceCodeVersion, 1)) ||
			(item.measurementUnitId != null && !isUuid(item.measurementUnitId)) ||
			(item.measurementUnitVersion != null && !isIntInRange(item.measurementUnitVersion, 1)) ||
			!isNullableString(item.securityLevel),
	);
	return issues;
};

export const validateModelSpecCreate = (input: unknown): ModelSpecFieldIssue[] => {
	if (!isRecord(input)) {
		return [issue("MODEL_SPEC_REQUEST_INVALID", "$", "ModelSpec create request is required")];
	}
	const issues: ModelSpecFieldIssue[] = [];
	const allowed = new Set<string>(MODEL_SPEC_CREATE_FIELDS);
	for (const field of Object.keys(input).filter((field) => !allowed.has(field)).sort()) {
		issues.push(issue("MODEL_SPEC_FIELD_NOT_ALLOWED", field, "Field is not part of the minimal ModelSpec create contract"));
	}
	for (const [field, code] of Object.entries(MODEL_SPEC_REQUIRED_FIELD_CODES) as [
		keyof typeof MODEL_SPEC_REQUIRED_FIELD_CODES,
		(typeof MODEL_SPEC_REQUIRED_FIELD_CODES)[keyof typeof MODEL_SPEC_REQUIRED_FIELD_CODES],
	][]) {
		const value = input[field];
		if (value == null || (typeof value === "string" && !value.trim())) {
			issues.push(issue(code, field, "Required field is missing"));
		}
	}
	if (input.planId != null && !isMissingText(input.planId) && !isUuid(input.planId))
		issues.push(issue("MODEL_SPEC_PLAN_INVALID", "planId", "Plan id must be a UUID"));
	if (input.domainId != null && !isMissingText(input.domainId) && !isUuid(input.domainId))
		issues.push(issue("MODEL_SPEC_DOMAIN_INVALID", "domainId", "Domain id must be a UUID"));
	if (input.modelType != null && !MODEL_SPEC_TYPES.has(input.modelType as ModelSpecType))
		issues.push(issue("MODEL_SPEC_TYPE_INVALID", "modelType", "Model type is not supported"));
	if (input.name != null && typeof input.name !== "string")
		issues.push(issue("MODEL_SPEC_NAME_INVALID", "name", "Model name must be text"));
	if (input.description != null && !isNullableString(input.description))
		issues.push(issue("MODEL_SPEC_FIELD_INVALID", "description", "Optional text field must be text or null"));
	if (input.idempotencyKey != null && typeof input.idempotencyKey !== "string")
		issues.push(issue("MODEL_SPEC_IDEMPOTENCY_KEY_INVALID", "idempotencyKey", "Idempotency key must be text"));
	const definitionRef = input.dimensionDefinitionRef;
	const validDefinitionRef =
		isRecord(definitionRef) &&
		Object.keys(definitionRef).every((field) => field === "dimensionDefinitionId" || field === "revision") &&
		isUuid(definitionRef.dimensionDefinitionId) &&
		isIntInRange(definitionRef.revision, 1);
	if (input.modelType === "DIMENSION" && !validDefinitionRef) {
		issues.push(
			issue(
				"MODEL_SPEC_DIMENSION_DEFINITION_REQUIRED",
				"dimensionDefinitionRef",
				"DIMENSION models require a revision-pinned dimension definition",
			),
		);
	}
	if (input.modelType != null && input.modelType !== "DIMENSION" && definitionRef != null) {
		issues.push(
			issue(
				"MODEL_SPEC_DIMENSION_DEFINITION_NOT_ALLOWED",
				"dimensionDefinitionRef",
				"Dimension definition references belong to DIMENSION models only",
			),
		);
	}
	return issues;
};

const validateModelSpecFull = (input: unknown): ModelSpecFieldIssue[] => {
	if (!input || typeof input !== "object" || Array.isArray(input)) {
		return [issue("MODEL_SPEC_REQUEST_INVALID", "$", "ModelSpec create request is required")];
	}
	const raw = input as Record<string, unknown>;
	const issues = rawModelSpecIssues(raw);
	if (issues.length > 0) return issues;
	const command = raw as Partial<UpdateModelSpecCommand> & { idempotencyKey?: string };
	if (command.modelType && command.layer && MODEL_SPEC_TARGET_LAYER_BY_TYPE[command.modelType] !== command.layer) {
		issues.push(
			issue(
				"MODEL_SPEC_TYPE_LAYER_MISMATCH",
				"layer",
				`${command.modelType} models must target ${MODEL_SPEC_TARGET_LAYER_BY_TYPE[command.modelType]}`,
			),
		);
	}
	if (isNonBlankString(command.businessActivityRef) && command.modelType !== "FACT") {
		issues.push(
			issue(
				"MODEL_SPEC_BUSINESS_ACTIVITY_NOT_ALLOWED",
				"businessActivityRef",
				"Business activity is optional FACT context only",
			),
		);
	}
	if (isNonBlankString(command.consumptionScenario) && command.modelType !== "APPLICATION") {
		issues.push(
			issue(
				"MODEL_SPEC_CONSUMPTION_SCENARIO_NOT_ALLOWED",
				"consumptionScenario",
				"Consumption scenario belongs to APPLICATION models only",
			),
		);
	}
	if (command.dimensionProfile != null && command.modelType !== "DIMENSION") {
		issues.push(
			issue(
				"MODEL_SPEC_DIMENSION_PROFILE_NOT_ALLOWED",
				"dimensionProfile",
				"Dimension profile belongs to DIMENSION models only",
			),
		);
	}
	if (command.generationStrategy != null && command.modelType !== "DIMENSION") {
		issues.push(
			issue(
				"MODEL_SPEC_INPUT_KIND_NOT_ALLOWED",
				"generationStrategy",
				"Generation strategy belongs to DIMENSION models only",
			),
		);
	}
	if (isNonBlankString(command.name) && command.name.trim().length > 256) {
		issues.push(issue("MODEL_SPEC_NAME_INVALID", "name", "Model name must not exceed 256 characters"));
	}
	if (isNonBlankString(command.idempotencyKey) && command.idempotencyKey.trim().length > 128) {
		issues.push(
			issue("MODEL_SPEC_IDEMPOTENCY_KEY_INVALID", "idempotencyKey", "Idempotency key must not exceed 128 characters"),
		);
	}
	const fields = Array.isArray(command.fields) ? command.fields : [];
	const sources = Array.isArray(command.sourceRefs) ? command.sourceRefs : [];
	const dependencies = Array.isArray(command.dependsOn) ? command.dependsOn : [];
	const dimensionRefs = Array.isArray(command.dimensionRefs) ? command.dimensionRefs : [];
	const metricRefs = Array.isArray(command.metricRefs) ? command.metricRefs : [];
	const standardBindings = Array.isArray(command.standardBindings) ? command.standardBindings : [];
	if ((command.modelType === "SUMMARY" || command.modelType === "APPLICATION") && sources.length > 0) {
		issues.push(
			issue(
				"MODEL_SPEC_INPUT_KIND_NOT_ALLOWED",
				"sourceRefs",
				"SUMMARY and APPLICATION models must use revision-pinned upstream models",
			),
		);
	}
	if (command.modelType === "DIMENSION" && dependencies.length > 0) {
		issues.push(
			issue(
				"MODEL_SPEC_INPUT_KIND_NOT_ALLOWED",
				"dependsOn",
				"DIMENSION uses physical sources or a generation strategy instead of model dependencies",
			),
		);
	}
	if (command.modelType !== "FACT" && dimensionRefs.length > 0) {
		issues.push(
			issue(
				"MODEL_SPEC_INPUT_KIND_NOT_ALLOWED",
				"dimensionRefs",
				"Analysis dimension references belong to FACT models only",
			),
		);
	}
	if (command.modelType !== "FACT" && command.factShape != null) {
		issues.push(issue("MODEL_SPEC_INPUT_KIND_NOT_ALLOWED", "factShape", "Fact shape belongs to FACT models only"));
	}
	if (command.modelType !== "FACT" && command.timeSemantics != null) {
		issues.push(
			issue("MODEL_SPEC_INPUT_KIND_NOT_ALLOWED", "timeSemantics", "Time semantics belongs to FACT models only"),
		);
	}
	const fieldNames = new Set<string>();
	if (
		fields.some((field) => {
			if (!field || typeof field !== "object" || Array.isArray(field)) return true;
			const normalizedName = field.name?.trim();
			if (
				!normalizedName ||
				!field.dataType?.trim() ||
				typeof field.nullable !== "boolean" ||
				!field.role ||
				fieldNames.has(normalizedName)
			)
				return true;
			fieldNames.add(normalizedName);
			return false;
		})
	) {
		issues.push(issue("MODEL_SPEC_FIELD_INVALID", "fields", "Fields require unique names, data types and roles"));
	}
	const sourceKeys = new Set<string>();
	const hasGrain = Boolean(
		command.grain?.statement?.trim() &&
			Array.isArray(command.grain.keys) &&
			command.grain.keys.some((key) => typeof key === "string" && key.trim()),
	);
	const invalidSource = sources.some((source) => {
		if (!source || typeof source !== "object" || Array.isArray(source)) return true;
		const normalizedRef = source.ref?.trim();
		const key = `${source.kind}:${normalizedRef}`;
		return (
			!source.kind ||
			!normalizedRef ||
			!source.layer ||
			!source.role ||
			!isIntInRange(source.sortOrder, 0) ||
			!isUuid(source.sourceBindingId) ||
			!isNonBlankString(source.resolvedVersion) ||
			sourceKeys.has(key) ||
			!sourceKeys.add(key)
		);
	});
	if (invalidSource)
		issues.push(
			issue(
				"MODEL_SPEC_SOURCE_INVALID",
				"sourceRefs",
				"Sources require complete metadata and a non-negative sort order",
			),
		);
	if (
		(command.modelType === "DIMENSION" || command.modelType === "FACT") &&
		sources.some((source) => source?.layer && !isModelSpecDirectInputLayerAllowed(source.layer))
	) {
		issues.push(
			issue(
				"MODEL_SPEC_UPSTREAM_LAYER_NOT_ALLOWED",
				"sourceRefs",
				"Direct physical inputs must come from ODS, STG or DWD",
			),
		);
	}
	const invalidRevisionRef = (ref: ModelSpecRevisionRef | null | undefined) =>
		!ref || !ref.modelSpecId?.trim() || !isIntInRange(ref.revision, 1);
	if (dependencies.some(invalidRevisionRef)) {
		issues.push(
			issue("MODEL_SPEC_DEPENDENCY_INVALID", "dependsOn", "Model references require an id and positive revision"),
		);
	}
	if (dimensionRefs.some(invalidRevisionRef)) {
		issues.push(
			issue(
				"MODEL_SPEC_DIMENSION_REF_INVALID",
				"dimensionRefs",
				"Model references require an id and positive revision",
			),
		);
	}
	if (metricRefs.some((ref) => !ref || !ref.metricId?.trim() || !isIntInRange(ref.version, 1))) {
		issues.push(
			issue("MODEL_SPEC_METRIC_REF_INVALID", "metricRefs", "Metric references require an id and positive version"),
		);
	}
	const incompleteVersionedRef = (reference: unknown, version: unknown) => {
		const normalizedReference = typeof reference === "string" ? reference.trim() || null : reference;
		return normalizedReference == null ? version != null : !isIntInRange(version, 1);
	};
	const standardBindingFieldNames = standardBindings
		.map((binding) => binding?.fieldName?.trim())
		.filter((fieldName): fieldName is string => Boolean(fieldName));
	const hasDuplicateStandardBinding = new Set(standardBindingFieldNames).size !== standardBindingFieldNames.length;
	if (
		hasDuplicateStandardBinding ||
		standardBindings.some(
			(binding) =>
				!binding ||
				!binding.fieldName?.trim() ||
				!fieldNames.has(binding.fieldName.trim()) ||
				incompleteVersionedRef(binding.standardElementId, binding.standardElementVersion) ||
				incompleteVersionedRef(binding.referenceCode, binding.referenceCodeVersion) ||
				incompleteVersionedRef(binding.measurementUnitId, binding.measurementUnitVersion) ||
				(!binding.standardElementId &&
					!binding.referenceCode?.trim() &&
					!binding.measurementUnitId &&
					!binding.securityLevel?.trim()),
		)
	) {
		issues.push(
			issue(
				"MODEL_SPEC_STANDARD_BINDING_INVALID",
				"standardBindings",
				"Standard bindings require a field and complete positive-version reference pairs",
			),
		);
	}
	if (command.generationStrategy && !command.generationStrategy.type?.trim()) {
		issues.push(
			issue("MODEL_SPEC_GENERATION_STRATEGY_INVALID", "generationStrategy", "Generation strategy requires a type"),
		);
	}
	switch (command.modelType) {
		case "DIMENSION":
			if (!hasGrain) {
				issues.push(issue("MODEL_SPEC_GRAIN_REQUIRED", "grain", "Dimension requires a grain statement and keys"));
			}
			if (!fields.some((field) => field?.role === "KEY")) {
				issues.push(issue("MODEL_SPEC_DIMENSION_KEY_REQUIRED", "fields", "Dimension requires at least one key field"));
			}
			break;
		case "FACT":
			if (!hasGrain)
				issues.push(issue("MODEL_SPEC_GRAIN_REQUIRED", "grain", "FACT requires a grain statement and keys"));
			break;
		case "SUMMARY":
		case "APPLICATION":
			if (dependencies.length === 0)
				issues.push(
					issue("MODEL_SPEC_UPSTREAM_REQUIRED", "dependsOn", "Derived models require a revision-pinned upstream model"),
				);
			if (!hasGrain) issues.push(issue("MODEL_SPEC_GRAIN_REQUIRED", "grain", "Derived models require an output grain"));
			if (command.modelType === "APPLICATION" && !isNonBlankString(command.consumptionScenario)) {
				issues.push(
					issue(
						"MODEL_SPEC_CONSUMPTION_SCENARIO_REQUIRED",
						"consumptionScenario",
						"APPLICATION requires a consumption scenario",
					),
				);
			}
			break;
	}
	return issues;
};

export const validateModelSpecUpdate = (input: unknown): ModelSpecFieldIssue[] => {
	if (!isRecord(input)) {
		return [issue("MODEL_SPEC_REQUEST_INVALID", "$", "ModelSpec update request is required")];
	}
	const allowed = new Set<string>(MODEL_SPEC_UPDATE_FIELDS);
	const fieldIssues = Object.keys(input)
		.filter((field) => !allowed.has(field))
		.sort()
		.map((field) =>
			issue("MODEL_SPEC_FIELD_NOT_ALLOWED", field, "Field is not part of the canonical ModelSpec update contract"),
		);
	const createShape: Record<string, unknown> = { idempotencyKey: "__model_spec_update__" };
	for (const field of MODEL_SPEC_UPDATE_FIELDS) {
		if (Object.hasOwn(input, field)) createShape[field] = input[field];
	}
	return [...fieldIssues, ...validateModelSpecFull(createShape)];
};
