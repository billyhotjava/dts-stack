export type WarehouseLayer = "ODS_RAW" | "ODS_STANDARDIZED" | "DWD" | "DWS" | "ADS";

export type WarehouseLayerDefinition = {
	key: WarehouseLayer;
	title: string;
	responsibility: string;
	allowedUpstream: WarehouseLayer[];
	namingPrefixes: string[];
};

export const WAREHOUSE_LAYER_REGISTRY: WarehouseLayerDefinition[] = [
	{
		key: "ODS_RAW",
		title: "原始接入层",
		responsibility: "保留源端原始记录与技术字段，保证可追溯和可重放。",
		allowedUpstream: [],
		namingPrefixes: ["ods_raw_", "raw_"],
	},
	{
		key: "ODS_STANDARDIZED",
		title: "标准化接入层",
		responsibility: "完成基础类型、字段和技术元数据标准化，不承载业务汇总。",
		allowedUpstream: ["ODS_RAW"],
		namingPrefixes: ["ods_", "stg_"],
	},
	{
		key: "DWD",
		title: "明细事实 / 维度层",
		responsibility: "按业务过程和声明粒度沉淀可复用明细事实与一致性维度。",
		allowedUpstream: ["ODS_STANDARDIZED", "DWD"],
		namingPrefixes: ["biz_dwd_", "dwd_", "dim_", "fact_"],
	},
	{
		key: "DWS",
		title: "汇总服务层",
		responsibility: "面向主题域和业务过程沉淀稳定的公共汇总口径。",
		allowedUpstream: ["DWD", "DWS"],
		namingPrefixes: ["biz_dws_", "dws_"],
	},
	{
		key: "ADS",
		title: "应用服务层",
		responsibility: "面向报表、数据产品和 API 输出消费结果，不绕过公共明细与汇总层。",
		allowedUpstream: ["DWD", "DWS", "ADS"],
		namingPrefixes: ["biz_ads_", "ads_"],
	},
];

export const resolveLayer = (key: string | undefined | null): WarehouseLayerDefinition | undefined =>
	WAREHOUSE_LAYER_REGISTRY.find((item) => item.key === key);

export const isLayerFlowAllowed = (from: WarehouseLayer | string, to: WarehouseLayer | string): boolean => {
	const target = resolveLayer(to);
	return Boolean(target?.allowedUpstream.includes(from as WarehouseLayer));
};
