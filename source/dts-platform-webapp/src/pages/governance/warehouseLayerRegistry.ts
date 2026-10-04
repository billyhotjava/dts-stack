export type WarehouseLayer = "ODS_RAW" | "ODS_STANDARDIZED" | "STG" | "DWD" | "DWS" | "ADS";

export type WarehouseLayerKind = "INGESTION" | "TECHNICAL" | "DETAIL" | "SERVICE" | "APPLICATION";

export type WarehouseLayerDefinition = {
	key: WarehouseLayer;
	title: string;
	responsibility: string;
	kind: WarehouseLayerKind;
	optional: boolean;
	businessOutput: boolean;
	dbtRole: string;
	allowedUpstream: WarehouseLayer[];
	namingPrefixes: string[];
};

export const WAREHOUSE_LAYER_REGISTRY: WarehouseLayerDefinition[] = [
	{
		key: "ODS_RAW",
		title: "原始接入层",
		responsibility: "保留源端原始记录与技术字段，保证可追溯和可重放。",
		kind: "INGESTION",
		optional: false,
		businessOutput: false,
		dbtRole: "dbt source 的原始入口，不做业务加工。",
		allowedUpstream: [],
		namingPrefixes: ["ods_raw_", "raw_"],
	},
	{
		key: "ODS_STANDARDIZED",
		title: "标准化接入层",
		responsibility: "完成基础类型、字段和技术元数据标准化，不承载业务汇总。",
		kind: "INGESTION",
		optional: false,
		businessOutput: false,
		dbtRole: "可作为 source freshness 和基础标准化的输入边界。",
		allowedUpstream: ["ODS_RAW"],
		namingPrefixes: ["ods_", "stg_"],
	},
	{
		key: "STG",
		title: "技术过渡层",
		responsibility: "承接 dbt source 到业务模型之间的类型转换、字段重命名、去重和轻量清洗。",
		kind: "TECHNICAL",
		optional: true,
		businessOutput: false,
		dbtRole: "dbt staging：一张源表对应一个轻量模型，不做业务指标聚合。",
		allowedUpstream: ["ODS_STANDARDIZED", "STG"],
		namingPrefixes: ["stg_"],
	},
	{
		key: "DWD",
		title: "明细事实 / 维度层",
		responsibility: "按业务过程和声明粒度沉淀可复用明细事实与一致性维度。",
		kind: "DETAIL",
		optional: false,
		businessOutput: true,
		dbtRole: "dbt intermediate/core 的业务明细和维度模型。",
		allowedUpstream: ["ODS_STANDARDIZED", "STG", "DWD"],
		namingPrefixes: ["biz_dwd_", "dwd_", "dim_", "fact_"],
	},
	{
		key: "DWS",
		title: "汇总服务层",
		responsibility: "面向主题域和业务过程沉淀稳定的公共汇总口径。",
		kind: "SERVICE",
		optional: true,
		businessOutput: true,
		dbtRole: "dbt 汇总模型，复用 DWD 和一致性维度。",
		allowedUpstream: ["DWD", "DWS"],
		namingPrefixes: ["biz_dws_", "dws_"],
	},
	{
		key: "ADS",
		title: "应用服务层",
		responsibility: "面向报表、数据产品和 API 输出消费结果，不绕过公共明细与汇总层。",
		kind: "APPLICATION",
		optional: true,
		businessOutput: true,
		dbtRole: "dbt 应用模型或面向消费的最终数据集。",
		allowedUpstream: ["DWD", "DWS", "ADS"],
		namingPrefixes: ["biz_ads_", "ads_"],
	},
];

export const DEFAULT_WAREHOUSE_LAYER_SCHEME = {
	id: "standard-lakehouse",
	name: "标准数仓分层方案",
	version: 1,
	enabledLayers: WAREHOUSE_LAYER_REGISTRY.map((layer) => layer.key),
	outputLayers: WAREHOUSE_LAYER_REGISTRY.filter((layer) => layer.businessOutput).map((layer) => layer.key),
} as const;

export const resolveLayer = (key: string | undefined | null): WarehouseLayerDefinition | undefined =>
	WAREHOUSE_LAYER_REGISTRY.find((item) => item.key === key);

export const isLayerFlowAllowed = (from: WarehouseLayer | string, to: WarehouseLayer | string): boolean => {
	const target = resolveLayer(to);
	return Boolean(target?.allowedUpstream.includes(from as WarehouseLayer));
};
