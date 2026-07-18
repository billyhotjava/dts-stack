import type { ModelingModelType } from "./modelingVnextContract.ts";

export const CANONICAL_MODELING_OBJECT_GROUPS = Object.freeze([
	{
		key: "BUSINESS_CATEGORY",
		label: "业务分类",
		meaning: "管什么业务",
		helpAliases: Object.freeze(["数据域", "主题域"]),
	},
	{ key: "WAREHOUSE_LAYER", label: "数仓分层", meaning: "数据放在哪一层" },
	{ key: "DATA_STANDARD", label: "数据标准", meaning: "字段长什么样" },
	{ key: "DIMENSION", label: "维度", meaning: "从什么角度分析" },
	{ key: "MODEL_TABLE", label: "四类表", meaning: "形成什么数据表" },
	{ key: "METRIC", label: "指标", meaning: "数字怎么算" },
] as const);

export const MODEL_TYPE_CUSTOMER_LABELS = Object.freeze({
	FACT: "明细表",
	DIMENSION: "维度表",
	SUMMARY: "汇总表",
	APPLICATION: "应用表",
} satisfies Record<ModelingModelType, string>);

export const METRIC_TYPE_CUSTOMER_LABELS = Object.freeze({
	ATOMIC: "原子指标",
	DERIVED: "派生指标",
} as const);

export const CANONICAL_MODELING_MAINLINE = Object.freeze([
	{ key: "BUSINESS_CATEGORY", label: "业务分类" },
	{ key: "DIMENSION_OR_MODEL_TABLE", label: "维度/四类表" },
	{ key: "STANDARD_AND_IMPLEMENTATION", label: "标准/实现" },
	{ key: "METRIC", label: "指标" },
] as const);

export const RETIRED_MODELING_OBJECT_TERMS = Object.freeze(["业务对象", "语义对象"] as const);
export const RETIRED_METRIC_CUSTOMER_TERMS = Object.freeze(["原始指标", "二次指标"] as const);

export const LEGACY_MODELING_OBJECT_COMPATIBILITY = Object.freeze({
	menuKey: "semantic-objects",
	route: "/modeling/semantic/objects",
} as const);

export const CANONICAL_MODELING_LANGUAGE = Object.freeze({
	objectGroups: CANONICAL_MODELING_OBJECT_GROUPS,
	modelTypeLabels: MODEL_TYPE_CUSTOMER_LABELS,
	metricTypeLabels: METRIC_TYPE_CUSTOMER_LABELS,
	mainline: CANONICAL_MODELING_MAINLINE,
	retiredObjectTerms: RETIRED_MODELING_OBJECT_TERMS,
	retiredMetricTerms: RETIRED_METRIC_CUSTOMER_TERMS,
	legacyCompatibility: LEGACY_MODELING_OBJECT_COMPATIBILITY,
});
