import type {
	ModelPackageJson,
	ModelSpecImportApplyResult,
	ModelSpecImportPreview,
	ModelSpecImportPreviewItem,
} from "@/api/modelSpecImportApi";

export const MODEL_PACKAGE_SCHEMA_VERSION = "dts.model-package/v1";
export const MODEL_PACKAGE_MAX_BYTES = 8 * 1024 * 1024;

export type ModelPackageMetadata = {
	fileName: string;
	fileSize: number;
	packageId: string;
	checksum: string;
	projectName: string;
	projectVersion: string;
	schemaVersion: string;
	modelCount: number;
	technicalNodeCount: number;
};

export type ModelPackageImportState = {
	step: 0 | 1 | 2 | 3;
	modelPackage: ModelPackageJson | null;
	metadata: ModelPackageMetadata | null;
	planId: string;
	domainMappings: Record<string, string>;
	sourceMappings: Record<string, string>;
	preview: ModelSpecImportPreview | null;
	selectedUniqueIds: string[];
	result: ModelSpecImportApplyResult | null;
	runId: string;
	busy: boolean;
	error: string;
	diagnosticCode: string;
};

export type ModelPackageImportAction =
	| { type: "RESET"; planId?: string; runId?: string }
	| { type: "PACKAGE_LOADED"; modelPackage: ModelPackageJson; metadata: ModelPackageMetadata }
	| { type: "PLAN_SELECTED"; planId: string }
	| { type: "DOMAIN_MAPPED"; code: string; domainId: string }
	| { type: "SOURCE_MAPPED"; uniqueId: string; bindingId: string }
	| { type: "BACK" }
	| { type: "REQUEST_STARTED" }
	| { type: "PREVIEW_SUCCEEDED"; preview: ModelSpecImportPreview }
	| { type: "RESULT_SUCCEEDED"; result: ModelSpecImportApplyResult }
	| { type: "RUN_RESTORED"; preview: ModelSpecImportPreview; result?: ModelSpecImportApplyResult | null }
	| { type: "SELECTION_CHANGED"; selectedUniqueIds: string[] }
	| { type: "PREVIEW_INVALIDATED" }
	| { type: "SENSITIVE_CLEARED" }
	| { type: "REQUEST_FAILED"; message: string; diagnosticCode?: string; rawMessage?: unknown }
	| { type: "CLEAR_ERROR" };

export const MODEL_IMPORT_GENERIC_DIAGNOSTIC_CODE = "MODEL_IMPORT_REQUEST_FAILED";
const SAFE_DIAGNOSTIC_CODE = /^[A-Z0-9_]{1,64}$/;

export const sanitizeModelImportDiagnosticCode = (value: unknown): string =>
	typeof value === "string" && SAFE_DIAGNOSTIC_CODE.test(value)
		? value
		: MODEL_IMPORT_GENERIC_DIAGNOSTIC_CODE;

export const createModelPackageImportState = (planId = "", runId = ""): ModelPackageImportState => ({
	step: runId ? 2 : 0,
	modelPackage: null,
	metadata: null,
	planId,
	domainMappings: {},
	sourceMappings: {},
	preview: null,
	selectedUniqueIds: [],
	result: null,
	runId,
	busy: false,
	error: "",
	diagnosticCode: "",
});

export const isSelectablePreviewItem = (item: ModelSpecImportPreviewItem): boolean =>
	item.action !== "BLOCKED" &&
	item.action !== "CONFLICT" &&
	!(item.issues || []).some((issue) => issue.severity === "ERROR");

export const defaultSelectedUniqueIds = (items: ModelSpecImportPreviewItem[]): string[] =>
	items.filter(isSelectablePreviewItem).map((item) => item.dbtUniqueId);

export const reduceModelPackageImportState = (
	state: ModelPackageImportState,
	action: ModelPackageImportAction,
): ModelPackageImportState => {
	let next: ModelPackageImportState;
	switch (action.type) {
		case "RESET":
			next = createModelPackageImportState(action.planId, action.runId);
			break;
		case "PACKAGE_LOADED":
			next = {
				...state,
				step: 1,
				modelPackage: action.modelPackage,
				metadata: action.metadata,
				domainMappings: {},
				sourceMappings: {},
				preview: null,
				selectedUniqueIds: [],
				result: null,
				runId: "",
				error: "",
				diagnosticCode: "",
			};
			break;
		case "PLAN_SELECTED":
			if (action.planId === state.planId) {
				next = {
					...state,
					step: state.modelPackage ? 1 : state.step,
					error: "",
					diagnosticCode: "",
				};
				break;
			}
			if (!state.planId && !state.preview && !state.runId) {
				next = {
					...state,
					step: state.modelPackage ? 1 : 0,
					planId: action.planId,
					error: "",
					diagnosticCode: "",
				};
				break;
			}
			next = {
				...state,
				step: 0,
				modelPackage: null,
				metadata: null,
				planId: action.planId,
				domainMappings: {},
				sourceMappings: {},
				preview: null,
				selectedUniqueIds: [],
				result: null,
				runId: "",
				error: "",
				diagnosticCode: "",
			};
			break;
		case "DOMAIN_MAPPED":
			next = { ...state, domainMappings: { ...state.domainMappings, [action.code]: action.domainId }, error: "", diagnosticCode: "" };
			break;
		case "SOURCE_MAPPED":
			next = { ...state, sourceMappings: { ...state.sourceMappings, [action.uniqueId]: action.bindingId }, error: "", diagnosticCode: "" };
			break;
		case "BACK":
			next = { ...state, step: Math.max(0, state.step - 1) as 0 | 1 | 2, error: "", diagnosticCode: "" };
			break;
		case "REQUEST_STARTED":
			next = { ...state, busy: true, error: "", diagnosticCode: "" };
			break;
		case "PREVIEW_SUCCEEDED":
			next = {
				...state,
				step: 2,
				modelPackage: null,
				metadata: null,
				domainMappings: {},
				sourceMappings: {},
				preview: action.preview,
				selectedUniqueIds: defaultSelectedUniqueIds(action.preview.items),
				result: null,
				runId: action.preview.runId,
				busy: false,
				error: "",
				diagnosticCode: "",
			};
			break;
		case "RESULT_SUCCEEDED":
			next = { ...state, step: 3, result: action.result, runId: action.result.runId, busy: false, error: "", diagnosticCode: "" };
			break;
		case "RUN_RESTORED":
			next = {
				...state,
				step: action.result ? 3 : 2,
				modelPackage: null,
				metadata: null,
				domainMappings: {},
				sourceMappings: {},
				planId: action.preview.planId || state.planId,
				preview: action.preview,
				selectedUniqueIds: defaultSelectedUniqueIds(action.preview.items),
				result: action.result || null,
				runId: action.preview.runId,
				busy: false,
				error: "",
				diagnosticCode: "",
			};
			break;
		case "SELECTION_CHANGED":
			next = { ...state, selectedUniqueIds: [...new Set(action.selectedUniqueIds)] };
			break;
		case "PREVIEW_INVALIDATED":
			next = {
				...state,
				step: state.modelPackage ? 1 : 0,
				preview: null,
				selectedUniqueIds: [],
				result: null,
				runId: "",
				error: "",
				diagnosticCode: "",
			};
			break;
		case "SENSITIVE_CLEARED":
			next = {
				...state,
				step: state.preview ? state.step : 0,
				modelPackage: null,
				metadata: null,
				domainMappings: {},
				sourceMappings: {},
				error: "",
				diagnosticCode: "",
			};
			break;
		case "REQUEST_FAILED":
			next = {
				...state,
				step: state.modelPackage ? 0 : state.step,
				modelPackage: null,
				metadata: null,
				domainMappings: state.modelPackage ? {} : state.domainMappings,
				sourceMappings: state.modelPackage ? {} : state.sourceMappings,
				busy: false,
				error: action.message,
				diagnosticCode: sanitizeModelImportDiagnosticCode(action.diagnosticCode),
			};
			break;
		case "CLEAR_ERROR":
			next = { ...state, error: "", diagnosticCode: "" };
			break;
	}
	return next;
};

const requiredObject = (value: unknown): Record<string, unknown> | null =>
	value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : null;

export const parseModelPackage = (
	text: string,
	fileName: string,
	fileSize: number,
): { modelPackage: ModelPackageJson; metadata: ModelPackageMetadata } => {
	if (!fileName.toLowerCase().endsWith(".json")) throw new Error("请选择 .json 格式的 DTS 模型包");
	if (fileSize <= 0 || fileSize > MODEL_PACKAGE_MAX_BYTES) throw new Error("模型包大小必须在 1 B 到 8 MB 之间");
	let parsed: unknown;
	try {
		parsed = JSON.parse(text);
	} catch {
		throw new Error("文件不是有效的 JSON，无法读取模型包");
	}
	const value = requiredObject(parsed);
	const dbt = requiredObject(value?.dbt);
	if (
		!value ||
		value.schemaVersion !== MODEL_PACKAGE_SCHEMA_VERSION ||
		typeof value.packageId !== "string" ||
		typeof value.packageChecksum !== "string" ||
		typeof dbt?.projectName !== "string" ||
		!Array.isArray(value.models) ||
		!Array.isArray(value.sources)
	) {
		throw new Error("文件不符合 dts.model-package/v1 基本结构");
	}
	const modelPackage = parsed as ModelPackageJson;
	return {
		modelPackage,
		metadata: {
			fileName,
			fileSize,
			packageId: modelPackage.packageId,
			checksum: modelPackage.packageChecksum,
			projectName: modelPackage.dbt.projectName,
			projectVersion: modelPackage.dbt.projectVersion || "未声明",
			schemaVersion: modelPackage.schemaVersion,
			modelCount: modelPackage.models.length,
			technicalNodeCount: modelPackage.technicalNodes?.length || 0,
		},
	};
};

export const modelPackageDomainCodes = (modelPackage: ModelPackageJson | null): string[] =>
	[
		...new Set(
			(modelPackage?.models || [])
				.map((model) => model.semantics?.domainCode?.trim())
				.filter((value): value is string => Boolean(value)),
		),
	].sort();

export const modelPackageSourceIds = (modelPackage: ModelPackageJson | null): string[] =>
	(modelPackage?.sources || []).map((source) => source.dbtUniqueId);

export const hasCompleteImportContext = (state: ModelPackageImportState): boolean =>
	Boolean(state.planId) &&
	modelPackageDomainCodes(state.modelPackage).every((code) => Boolean(state.domainMappings[code])) &&
	modelPackageSourceIds(state.modelPackage).every((uniqueId) => Boolean(state.sourceMappings[uniqueId]));

export const isPreviewApplicable = (preview: ModelSpecImportPreview | null, now = Date.now()): boolean => {
	if (!preview) return false;
	if (preview.status != null && preview.status !== "PREVIEWED") return false;
	if (!preview.expiresAt) return true;
	const expiresAt = Date.parse(preview.expiresAt);
	return Number.isFinite(expiresAt) && expiresAt > now;
};

export const canRetryFailedImport = (
	canEdit: boolean,
	selectedPlanEditable: boolean,
	preview: ModelSpecImportPreview | null,
	failedCount: number,
	now = Date.now(),
): boolean =>
	canEdit &&
	selectedPlanEditable &&
	failedCount > 0 &&
	isPreviewApplicable(preview, now);

export const createModelPackageImportIdempotencyKey = (): string => {
	const random = globalThis.crypto?.randomUUID?.();
	return random || `model-package-${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

export type ModelPackageImportIdempotencySlot = { intent: string; key: string };

export const resolveModelPackageImportIdempotencySlot = (
	current: ModelPackageImportIdempotencySlot | null,
	intent: string,
	createKey = createModelPackageImportIdempotencyKey,
): ModelPackageImportIdempotencySlot =>
	current?.intent === intent ? current : { intent, key: createKey() };
