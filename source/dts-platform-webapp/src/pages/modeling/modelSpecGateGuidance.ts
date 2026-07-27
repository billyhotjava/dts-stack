import type { ModelSpecGateBlocker } from "@/api/modelSpecApi";
import type { ModelSpecDetailStage, ModelSpecDetailTab } from "./modelSpecDetailNavigation";

export type ModelSpecGateGuidance = {
	message: string;
	description: string;
	location: string;
	actionLabel: string;
	stage: ModelSpecDetailStage;
	tab?: ModelSpecDetailTab;
};

const logicalDefinition = (
	message: string,
	description: string,
	actionLabel = "完善逻辑定义",
): ModelSpecGateGuidance => ({
	message,
	description,
	location: "逻辑设计 · 逻辑定义",
	actionLabel,
	stage: "logical",
	tab: "design",
});

const logicalFields = (message: string, description: string, actionLabel = "完善字段设计"): ModelSpecGateGuidance => ({
	message,
	description,
	location: "逻辑设计 · 字段设计",
	actionLabel,
	stage: "logical",
	tab: "fields",
});

const implementation = (message: string, description: string, actionLabel = "配置数据实现"): ModelSpecGateGuidance => ({
	message,
	description,
	location: "数据实现",
	actionLabel,
	stage: "implementation",
});

const physical = (message: string, description: string, actionLabel = "查看物理资产"): ModelSpecGateGuidance => ({
	message,
	description,
	location: "物理资产",
	actionLabel,
	stage: "physical",
});

const standards = (message: string, description: string, actionLabel = "配置字段标准"): ModelSpecGateGuidance => ({
	message,
	description,
	location: "逻辑设计 · 字段标准",
	actionLabel,
	stage: "logical",
	tab: "standards",
});

const exactGuidance: Record<string, ModelSpecGateGuidance> = {
	MODEL_SPEC_FACT_INPUT_REQUIRED: implementation(
		"尚未配置明细表的实现输入",
		"上游模型是本模型加工所依赖的已存在模型，不是当前正在创建的表。若直接从业务库表加工，可选择已确认的物理来源，无需先创建上游模型；若基于已有模型加工，再选择并锁定上游模型版本。",
		"选择实现输入",
	),
	MODEL_SPEC_DIMENSION_INPUT_REQUIRED: implementation(
		"尚未配置维度表的实现输入",
		"请在数据实现中选择已确认物理来源；只有系统生成维度才选择生成器。来源不是在逻辑定义中手工填写的表名。",
		"选择实现输入",
	),
	MODEL_SPEC_UPSTREAM_REQUIRED: logicalDefinition(
		"当前模型尚未锁定上游逻辑模型",
		"上游模型是已经存在并可复用的模型产物。汇总表和应用表必须基于一个或多个上游模型，并固定所使用的模型版本。",
		"选择上游模型",
	),
	MODEL_SPEC_TIME_FIELD_INVALID: logicalFields(
		"业务时间尚未引用有效的 TIME 字段",
		"TIME 是本模型字段的“字段作用”，不是数据库数据类型，也不会从来源表自动猜测。请先在字段设计中添加业务时间字段并把字段作用设为“时间（TIME）”，再在逻辑定义中填写同名时间字段。",
		"配置 TIME 字段",
	),
	MODEL_SPEC_TIME_SEMANTICS_REQUIRED: logicalDefinition(
		"尚未声明业务时间语义",
		"请根据记录含义选择事件时间、快照日期、统计周期或里程碑日期，并填写本模型字段设计中作用为“时间（TIME）”的字段名。",
		"配置业务时间",
	),
	MODEL_SPEC_FACT_TIME_SHAPE_MISMATCH: logicalDefinition(
		"业务时间与事实形态不匹配",
		"事务记录应使用事件时间，周期快照应使用快照日期或统计周期，生命周期快照应使用里程碑日期。",
		"调整业务时间",
	),
	MODEL_SPEC_FACT_SHAPE_REQUIRED: logicalDefinition(
		"尚未选择事实形态",
		"请选择事务记录、周期快照或生命周期快照；该选择决定业务时间应如何配置。",
		"选择事实形态",
	),
	MODEL_SPEC_GRAIN_KEY_MAPPING_INVALID: logicalFields(
		"粒度键没有唯一对应到 KEY 字段",
		"逻辑定义中的每个粒度键都必须在字段设计中存在一次，并将字段作用设为“键（KEY）”。",
		"配置粒度键字段",
	),
	MODEL_SPEC_SUMMARY_MEASURE_REQUIRED: logicalFields(
		"汇总表尚未声明汇总字段或指标",
		"请在字段设计中增加度量字段并将字段作用设为“度量（MEASURE）”，或补充指标引用。",
	),
	MODEL_SPEC_APPLICATION_OUTPUT_REQUIRED: logicalFields(
		"应用表尚未声明输出字段",
		"请在字段设计中定义报表、接口或应用场景需要输出的字段契约。",
	),
	MODEL_SPEC_STANDARD_EVIDENCE_STALE: standards(
		"字段标准未覆盖当前模型版本",
		"请为当前字段绑定有效版本的数据元、参考代码或计量单位。",
	),
	MODEL_SPEC_STANDARD_EVIDENCE_UNKNOWN: standards(
		"暂时无法确认当前版本的字段标准证据",
		"请检查字段标准绑定并重新保存当前模型版本。",
	),
	MODEL_SPEC_PERMISSION_EVIDENCE_STALE: logicalFields(
		"字段安全等级尚未完成",
		"请为每个字段设置有效的数据安全等级；也可以通过字段标准绑定继承安全等级。",
		"配置安全等级",
	),
	MODEL_SPEC_PERMISSION_EVIDENCE_UNKNOWN: logicalFields(
		"暂时无法确认字段安全等级",
		"请检查字段设计或字段标准中的安全等级配置。",
		"检查安全等级",
	),
	MODEL_SPEC_QUALITY_EVIDENCE_STALE: physical(
		"当前版本缺少有效的质量测试证据",
		"质量证据来自当前实现版本的测试结果，不是在逻辑设计中手工填写。",
		"查看测试证据",
	),
	MODEL_SPEC_QUALITY_EVIDENCE_UNKNOWN: physical(
		"暂时无法确认当前版本的质量测试证据",
		"请先保存并验证数据实现，再检查当前实现版本的测试结果。",
		"查看测试证据",
	),
	MODEL_SPEC_BUILD_EVIDENCE_UNKNOWN: physical(
		"当前版本尚无完整构建产物",
		"构建产物由当前数据实现编译生成；先完成并验证数据实现，再进入高级实现执行编译。",
		"查看构建状态",
	),
	MODEL_SPEC_TEST_EVIDENCE_UNKNOWN: physical(
		"当前版本尚无测试产物",
		"测试产物由当前数据实现的测试运行生成，不需要在逻辑设计中手工登记。",
		"查看测试状态",
	),
	CLASSIFICATION_IMPLEMENTATION_EVIDENCE_MISSING: implementation(
		"当前模型缺少可用于密级传播的实现证据",
		"请先保存当前 ModelSpec 版本对应的数据实现并锁定输入版本，系统才能计算上游密级传播结果。",
	),
	CLASSIFICATION_INPUT_REQUIRED: logicalFields(
		"缺少可计算模型密级的输入",
		"至少需要一个已封存的上游输入密级，或在字段设计中明确设置字段安全等级。",
		"配置安全等级",
	),
	CLASSIFICATION_FIELD_LEVEL_INVALID: logicalFields(
		"字段安全等级无效",
		"请将字段安全等级调整为系统安全等级目录中的有效值。",
		"修正安全等级",
	),
	CLASSIFICATION_MODEL_REVISION_STALE: physical(
		"密级证据不属于当前模型版本",
		"模型 revision 或校验值已变化，请基于当前版本重新生成实现与发布证据。",
		"检查当前版本",
	),
	PENDING_CLASSIFICATION: physical(
		"上游输入尚无已封存的密级证据",
		"该证据来自已锁定的上游来源或模型版本；请先完成上游密级确认与传播，再重新检查当前发布条件。",
		"检查密级证据",
	),
	CLASSIFICATION_PROPAGATION_PENDING: physical(
		"上游密级传播尚未完成",
		"上游已存在密级证据，但传播结果尚未收敛；请稍后重试或检查上游密级任务。",
		"检查密级传播",
	),
};

export const modelSpecGateGuidance = (blocker: ModelSpecGateBlocker): ModelSpecGateGuidance => {
	const exact = exactGuidance[blocker.code];
	if (exact) return exact;
	if (blocker.code.startsWith("MODEL_IMPLEMENTATION_") || blocker.field === "implementation.inputs") {
		return implementation(
			blocker.code === "MODEL_IMPLEMENTATION_REQUIRED"
				? "尚未保存当前模型的数据实现"
				: blocker.code === "MODEL_IMPLEMENTATION_INPUT_MIGRATION_REQUIRED"
					? "旧版实现输入需要重新确认"
					: "实现输入未通过当前来源与依赖校验",
			"请在数据实现中选择一种输入方式：已确认物理来源、锁定版本的上游模型，或适用的系统生成器。",
		);
	}
	if (blocker.code.startsWith("MODEL_SPEC_SOURCE_EVIDENCE")) {
		return implementation(
			blocker.message,
			"该证据来自建设计划中已确认的来源版本。请在数据实现中刷新或重新选择规划来源。",
			"修复来源版本",
		);
	}
	if (blocker.code.startsWith("MODEL_SPEC_UPSTREAM_EVIDENCE")) {
		return logicalDefinition(
			blocker.message,
			"已锁定的上游模型已有新 revision 或不可访问；请明确重新选择当前可用版本，系统不会静默升级引用。",
			"检查上游版本",
		);
	}
	if (blocker.code.startsWith("MODEL_SPEC_DIMENSION_EVIDENCE")) {
		return logicalDefinition(
			blocker.message,
			"已引用的分析维度版本已变化或不可访问，请重新选择当前可用的维度版本。",
			"检查分析维度",
		);
	}
	if (blocker.field === "standardBindings") {
		return standards(blocker.message, "请检查当前字段与数据标准版本的绑定关系。");
	}
	if (blocker.field === "fields") {
		return logicalFields(blocker.message, "该限制由当前模型的字段定义产生，请在字段设计中补齐或修正。");
	}
	if (blocker.field === "sourceRefs") {
		return implementation(blocker.message, "请在数据实现中选择并锁定当前计划已确认的输入来源。");
	}
	return logicalDefinition(blocker.message, "该限制属于当前模型的逻辑定义；进入对应配置区后可查看并完善相关字段。");
};

export const modelSpecGateRepairPath = (blocker: ModelSpecGateBlocker): string => {
	const guidance = modelSpecGateGuidance(blocker);
	if (!blocker.repairRoute.startsWith("/modeling/models/")) return blocker.repairRoute;
	const [pathname] = blocker.repairRoute.split("?", 1);
	const params = new URLSearchParams({ activeStage: guidance.stage });
	if (guidance.tab) params.set("tab", guidance.tab);
	return `${pathname}?${params.toString()}`;
};
