import type { AssetGrant, Dataset, DataProduct, DatasetLineage, QualityRule } from "@/types/asset";

/**
 * 种子数据集 —— 归口部门。
 * 销售处：ods_sales_wide（草稿，集成画布产出，尚未发布 → 资产阶段仍待办）。
 * 质量处：2 个已发布数据集（成熟部门）。
 */
export const SEED_DATASETS: Dataset[] = [
	{
		id: "dsx-sales-wide",
		departmentId: "dept-sales",
		name: "ods_sales_wide",
		layer: "ODS",
		kind: "table",
		status: "draft",
		rowCount: 31800,
		qualityScore: 82,
		owner: "销售处",
		updatedAt: "2026-06-23",
		description: "PLM 订单 + ERP 客户连接后的销售宽表（集成画布产出）",
		columns: [
			{ name: "order_id", type: "varchar", comment: "订单号(主键)" },
			{ name: "cust_id", type: "varchar", comment: "客户号" },
			{ name: "cust_name", type: "varchar", comment: "客户名称" },
			{ name: "amount", type: "decimal", comment: "金额" },
			{ name: "order_date", type: "date", comment: "下单日期" },
		],
	},
	{
		id: "dsx-quality-inspect",
		departmentId: "dept-quality",
		name: "dwd_quality_inspect",
		layer: "DWD",
		kind: "table",
		status: "published",
		rowCount: 12400,
		qualityScore: 95,
		owner: "质量处",
		updatedAt: "2026-06-20",
		description: "QMIS 质量抽检明细",
		columns: [
			{ name: "inspect_id", type: "varchar", comment: "抽检号" },
			{ name: "batch_no", type: "varchar", comment: "批次" },
			{ name: "result", type: "varchar", comment: "结果" },
			{ name: "inspect_date", type: "date", comment: "抽检日期" },
		],
	},
	{
		id: "dsx-quality-monthly",
		departmentId: "dept-quality",
		name: "ads_quality_monthly",
		layer: "ADS",
		kind: "view",
		status: "published",
		rowCount: 36,
		qualityScore: 98,
		owner: "质量处",
		updatedAt: "2026-06-20",
		description: "质量月报聚合（合格率/批次数）",
		columns: [
			{ name: "month", type: "varchar", comment: "月份" },
			{ name: "pass_rate", type: "decimal", comment: "合格率" },
			{ name: "batch_cnt", type: "int", comment: "批次数" },
		],
	},
];

export const SEED_DATA_PRODUCTS: DataProduct[] = [
	{
		id: "dp-quality-monthly",
		departmentId: "dept-quality",
		name: "质量月报数据产品",
		status: "published",
		datasetIds: ["dsx-quality-monthly", "dsx-quality-inspect"],
		description: "对接领导驾驶舱的质量月报",
	},
];

/** 每个数据集的血缘 DAG（横向布局，x 按沿袭深度）。 */
export const SEED_LINEAGE: Record<string, DatasetLineage> = {
	"dsx-sales-wide": {
		nodes: [
			{ id: "src-plm", label: "PLM.订单", kind: "source", x: 0, y: 0 },
			{ id: "src-erp", label: "ERP.客户", kind: "source", x: 0, y: 110 },
			{ id: "m-stg", label: "stg_orders(去重)", kind: "model", x: 230, y: 0 },
			{ id: "m-int", label: "int_sales(连接)", kind: "model", x: 460, y: 55 },
			{ id: "ds-wide", label: "ods_sales_wide", kind: "dataset", x: 690, y: 55 },
			{ id: "mt-rate", label: "销售达成率(指标)", kind: "metric", x: 920, y: 55 },
		],
		edges: [
			{ source: "src-plm", target: "m-stg" },
			{ source: "m-stg", target: "m-int" },
			{ source: "src-erp", target: "m-int" },
			{ source: "m-int", target: "ds-wide" },
			{ source: "ds-wide", target: "mt-rate" },
		],
	},
	"dsx-quality-inspect": {
		nodes: [
			{ id: "src-qmis", label: "QMIS.抽检", kind: "source", x: 0, y: 30 },
			{ id: "ds-inspect", label: "dwd_quality_inspect", kind: "dataset", x: 260, y: 30 },
			{ id: "ds-monthly2", label: "ads_quality_monthly", kind: "dataset", x: 540, y: 30 },
		],
		edges: [
			{ source: "src-qmis", target: "ds-inspect" },
			{ source: "ds-inspect", target: "ds-monthly2" },
		],
	},
	"dsx-quality-monthly": {
		nodes: [
			{ id: "ds-inspect3", label: "dwd_quality_inspect", kind: "dataset", x: 0, y: 30 },
			{ id: "ds-monthly3", label: "ads_quality_monthly", kind: "dataset", x: 280, y: 30 },
			{ id: "mt-pass", label: "合格率(指标)", kind: "metric", x: 560, y: 30 },
		],
		edges: [
			{ source: "ds-inspect3", target: "ds-monthly3" },
			{ source: "ds-monthly3", target: "mt-pass" },
		],
	},
};

export const SEED_QUALITY_RULES: QualityRule[] = [
	{ id: "qr-1", datasetId: "dsx-sales-wide", name: "order_id 非空", dimension: "完整性", status: "pass", lastRun: "2026-06-23" },
	{ id: "qr-2", datasetId: "dsx-sales-wide", name: "order_id 唯一", dimension: "唯一性", status: "warn", lastRun: "2026-06-23" },
	{ id: "qr-3", datasetId: "dsx-sales-wide", name: "amount >= 0", dimension: "有效性", status: "pass", lastRun: "2026-06-23" },
	{ id: "qr-4", datasetId: "dsx-quality-inspect", name: "batch_no 非空", dimension: "完整性", status: "pass", lastRun: "2026-06-20" },
	{ id: "qr-5", datasetId: "dsx-quality-inspect", name: "result 枚举有效", dimension: "有效性", status: "pass", lastRun: "2026-06-20" },
	{ id: "qr-6", datasetId: "dsx-quality-monthly", name: "月度及时产出", dimension: "及时性", status: "pass", lastRun: "2026-06-20" },
];

export const SEED_ASSET_GRANTS: AssetGrant[] = [
	{ id: "g-1", datasetId: "dsx-quality-monthly", granteeDept: "销售处", level: "read", grantedAt: "2026-06-21" },
	{ id: "g-2", datasetId: "dsx-quality-inspect", granteeDept: "网信中心", level: "read", grantedAt: "2026-06-19" },
];
