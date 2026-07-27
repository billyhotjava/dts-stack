import type { ModelSpecImplementationMode } from "./modelSpecV2Contract";

export type ModelImplementationInputMode = "PHYSICAL_ASSET" | "UPSTREAM_MODEL" | "GENERATED";

export type PhysicalAssetImplementationInput = {
	sourceBindingId: string;
	resolvedVersion: string;
};

export type UpstreamModelRevisionInput = {
	modelSpecId: string;
	revision: number;
	checksum: string;
};

export type PinnedUpstreamModelImplementationInput = UpstreamModelRevisionInput & {
	implementationRevision: number;
	implementationChecksum: string;
	dbtUniqueId: string;
};

export type UnpinnedUpstreamModelImplementationInput = UpstreamModelRevisionInput & {
	implementationRevision?: undefined;
	implementationChecksum?: undefined;
	dbtUniqueId?: undefined;
};

export type UpstreamModelImplementationInput =
	| PinnedUpstreamModelImplementationInput
	| UnpinnedUpstreamModelImplementationInput;

export const isUpstreamModelImplementationPinned = (
	input: UpstreamModelImplementationInput,
): input is PinnedUpstreamModelImplementationInput =>
	Number.isInteger(input.implementationRevision) &&
	(input.implementationRevision ?? 0) > 0 &&
	typeof input.implementationChecksum === "string" &&
	Boolean(input.implementationChecksum.trim()) &&
	typeof input.dbtUniqueId === "string" &&
	Boolean(input.dbtUniqueId.trim());

export type GeneratedImplementationInput = {
	generatorType: string;
	config?: Record<string, unknown>;
};

export type ModelImplementationInput =
	| PhysicalAssetImplementationInput
	| UpstreamModelImplementationInput
	| GeneratedImplementationInput;

export const resolvePhysicalAssetImplementationInputs = (
	sourceBindingIds: readonly string[],
	available: readonly PhysicalAssetImplementationInput[],
	persisted: readonly PhysicalAssetImplementationInput[],
	adoptCurrentIds: ReadonlySet<string> = new Set<string>(),
): PhysicalAssetImplementationInput[] => {
	const availableById = new Map(available.map((input) => [input.sourceBindingId, input]));
	const persistedById = new Map(persisted.map((input) => [input.sourceBindingId, input]));
	return sourceBindingIds.map((sourceBindingId) => {
		const input = adoptCurrentIds.has(sourceBindingId)
			? availableById.get(sourceBindingId) || persistedById.get(sourceBindingId)
			: persistedById.get(sourceBindingId) || availableById.get(sourceBindingId);
		return {
			sourceBindingId,
			resolvedVersion: input?.resolvedVersion || "",
		};
	});
};

export const resolveUpstreamModelImplementationInputs = (
	modelSpecIds: readonly string[],
	available: readonly UpstreamModelRevisionInput[],
	persisted: readonly PinnedUpstreamModelImplementationInput[],
	adoptCurrentIds: ReadonlySet<string> = new Set<string>(),
): UpstreamModelImplementationInput[] => {
	const availableById = new Map(available.map((input) => [input.modelSpecId, input]));
	const persistedById = new Map(persisted.map((input) => [input.modelSpecId, input]));
	return modelSpecIds.map((modelSpecId) => {
		const current = availableById.get(modelSpecId);
		const saved = persistedById.get(modelSpecId);
		if (adoptCurrentIds.has(modelSpecId)) {
			if (current && current.revision > 0 && current.checksum.trim()) return { ...current };
			return {
				modelSpecId,
				revision: 0,
				checksum: "",
			};
		}
		if (saved) {
			return {
				modelSpecId: saved.modelSpecId,
				revision: saved.revision,
				checksum: saved.checksum,
				implementationRevision: saved.implementationRevision,
				implementationChecksum: saved.implementationChecksum,
				dbtUniqueId: saved.dbtUniqueId,
			};
		}
		if (current) return { ...current };
		return {
			modelSpecId,
			revision: 0,
			checksum: "",
		};
	});
};

export type ModelImplementationFieldMapping = {
	sourceField: string;
	targetField: string;
};

type ModelImplementationWriteBase = {
	projectKey: string;
	dbtUniqueId: string;
	fieldMappings?: ModelImplementationFieldMapping[];
	settings?: Record<string, unknown>;
	ownership: ModelSpecImplementationMode;
	materialization: string;
	idempotencyKey: string;
};

export type ModelImplementationWriteCommand =
	| (ModelImplementationWriteBase & {
			inputMode: "PHYSICAL_ASSET";
			inputs: PhysicalAssetImplementationInput[];
		})
	| (ModelImplementationWriteBase & {
			inputMode: "UPSTREAM_MODEL";
			inputs: UpstreamModelImplementationInput[];
		})
	| (ModelImplementationWriteBase & {
			inputMode: "GENERATED";
			inputs: GeneratedImplementationInput[];
		});

export type ModelImplementationView = {
	id: string;
	modelSpecId: string;
	planId: string;
	revision: number;
	modelChecksum: string;
	ownership: ModelSpecImplementationMode;
	projectKey: string;
	dbtUniqueId: string;
	status: string;
	implementationRevision: number;
	implementationChecksum: string;
	inputMode: ModelImplementationInputMode;
	inputs: ModelImplementationInput[];
	fieldMappings: ModelImplementationFieldMapping[];
	settings: Record<string, unknown>;
	materialization: string;
};

export type ModelImplementationCasToken = Pick<
	ModelImplementationView,
	"modelSpecId" | "implementationRevision" | "implementationChecksum"
>;

export const toModelImplementationEtag = ({
	modelSpecId,
	implementationRevision,
	implementationChecksum,
}: ModelImplementationCasToken): string =>
	`"model-implementation:${modelSpecId}:${implementationRevision}:${implementationChecksum}"`;

export type ModelImplementationValidation = {
	valid: boolean;
	code: string | null;
	blockers?: ModelImplementationBlocker[];
	executionPlan?: ModelImplementationExecutionPlan | null;
};

export type ModelImplementationBlocker = {
	code: string;
	field: string;
	message: string;
	repairAction: string;
};

export type ModelImplementationExecutionPlan = {
	engine: "DBT";
	adapter: string;
	nodeUniqueId: string;
	selector: string;
	targetIdentifier: string;
	effectiveMaterialization: "table" | "view" | "incremental";
	physicalExpected: boolean;
	uniqueKey: string[];
	capabilityCodes: string[];
};

export const modelImplementationValidationMessage = (
	result: ModelImplementationValidation,
): string => {
	const guidance: Record<string, string> = {
		IMPLEMENTATION_SETTING_NOT_ALLOWED: "当前实现包含不支持的高级设置，请移除后重试。",
		IMPLEMENTATION_ADAPTER_UNSUPPORTED: "当前执行目标尚未通过物化能力验证，请选择已支持的执行目标。",
		IMPLEMENTATION_TARGET_REQUIRED: "请填写目标物理表名。",
		IMPLEMENTATION_TARGET_IDENTIFIER_INVALID: "目标物理表名只能使用小写字母、数字和下划线，且必须以字母开头。",
		IMPLEMENTATION_LOAD_STRATEGY_REQUIRED: "请选择数据装载策略。",
		IMPLEMENTATION_SNAPSHOT_STRATEGY_REQUIRED: "当前版本暂不支持周期快照，请改用全量或增量装载。",
		IMPLEMENTATION_LOAD_STRATEGY_UNSUPPORTED: "当前装载策略暂不支持构建。",
		IMPLEMENTATION_PARTITION_UNSUPPORTED: "当前 PostgreSQL 执行目标不支持普通模式分区转译，请先移除分区字段。",
		IMPLEMENTATION_MATERIALIZATION_CONFLICT: "数据装载策略与物化方式不一致：增量装载必须使用 incremental。",
		IMPLEMENTATION_INCREMENTAL_KEY_REQUIRED: "增量装载至少需要一个 KEY 字段，请先回到逻辑设计补充。",
		IMPLEMENTATION_DBT_UNIQUE_ID_INVALID: "系统生成的 dbt 节点标识无效，请刷新页面后重试。",
	};
	return (
		(result.code ? guidance[result.code] : undefined) ||
		result.blockers?.[0]?.message ||
		result.code ||
		"当前实现未通过验证，配置已保留。"
	);
};
