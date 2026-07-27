import type {
	ModelImplementationMigrationBatch,
	ModelSpecReclassificationPreview,
} from "@/api/modelSpecApi";

const IMPLEMENTATION_MIGRATION_REASONS: Record<string, string> = {
	LEGACY_IMPLEMENTATION_PROJECTED: "历史实现信息已完成预检，可以迁入当前数据实现",
	NO_LEGACY_IMPLEMENTATION: "没有需要迁移的历史实现信息",
	ALREADY_MIGRATED: "历史实现信息已经迁移，无需重复处理",
	CURRENT_IMPLEMENTATION_WINS: "已有数据实现是当前真值，不会被历史设置覆盖",
	MODEL_IMPLEMENTATION_LEGACY_INPUT_CONFLICT: "历史记录同时包含多种实现来源，需要人工确认后处理",
	MODEL_IMPLEMENTATION_INPUT_REQUIRED: "历史记录没有可识别的实现来源",
	MODEL_IMPLEMENTATION_INPUT_STALE: "历史上游模型版本已变化，不能自动迁移",
	PHYSICAL_ASSET_NOT_CONFIRMED: "历史来源表尚未确认或版本已经变化",
	MODEL_IMPLEMENTATION_SETTINGS_INVALID: "历史产出设置不符合当前规则，需要人工修正",
	MODEL_SPEC_NOT_FOUND: "模型已经不存在，不能迁移",
};

const RECLASSIFICATION_FIELDS: Record<string, string> = {
	name: "模型名称",
	description: "业务说明",
	fields: "字段定义",
	"fields.KEY": "业务唯一键字段",
	"fields.TIME": "业务时间字段",
	"fields.MEASURE": "度量字段",
	"fields.roles": "字段角色（时间/度量将调整为属性）",
	standardBindings: "字段标准绑定",
	dataMartId: "数据集市归属",
	sourceRefs: "直接来源表",
	dependsOn: "上游模型",
	dimensionRefs: "关联维度",
	metricRefs: "关联指标",
	dimensionDefinitionRef: "现行业务维度定义",
	grain: "每行数据代表什么及粒度键",
	factShape: "事实记录方式",
	timeSemantics: "业务时间口径",
	businessActivityRef: "业务活动",
	consumptionScenario: "消费场景",
	generationStrategy: "系统生成规则",
	dimensionProfile: "维度层级与缓慢变化策略",
	"dimensionProfile.scdPolicy": "维度变化保留方式",
	variantCode: "维度变体编码",
	materialization: "旧数据生成方式",
	implementationPolicy: "旧物理产出设置（将转到数据实现）",
};

const RECLASSIFICATION_REASONS: Record<string, string> = {
	MODEL_RECLASSIFY_DRAFT_REQUIRED: "只有草稿模型可以调整类型",
	MODEL_RECLASSIFY_CANONICAL_REQUIRED: "历史兼容模型不能直接调整类型",
	MODEL_RECLASSIFY_TYPE_UNCHANGED: "请选择不同于当前类型的业务目的",
	MODEL_RECLASSIFY_RUNTIME_EVIDENCE_EXISTS: "模型已有数据实现、运行或发布证据，不能原地改型",
	MODEL_RECLASSIFY_DIMENSION_DEFINITION_REQUIRED: "改为维度表前必须选择现行业务维度定义",
};

export const implementationMigrationReason = (code: string): string =>
	IMPLEMENTATION_MIGRATION_REASONS[code] || "当前历史实现信息不能自动迁移，请在数据实现中人工确认";

export const reclassificationFieldLabel = (field: string): string => RECLASSIFICATION_FIELDS[field] || field;

export const reclassificationReason = (code: string): string =>
	RECLASSIFICATION_REASONS[code] || "当前模型不满足安全调整类型的条件";

export const canApplyImplementationMigration = (batch: ModelImplementationMigrationBatch | null): boolean =>
	Boolean(
		batch &&
			/^[0-9a-f]{64}$/.test(batch.previewChecksum) &&
			batch.total === 1 &&
			batch.eligible === 1 &&
			batch.conflict === 0 &&
			batch.orphan === 0,
	);

export const canApplyModelReclassification = (
	preview: ModelSpecReclassificationPreview | null,
	acceptedClearFields: string[],
): boolean => {
	if (!preview?.eligible) return false;
	const accepted = new Set(acceptedClearFields);
	return preview.clearFields.every((field) => accepted.has(field));
};
