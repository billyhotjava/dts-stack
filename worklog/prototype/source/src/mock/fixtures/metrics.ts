import type { GlossaryTerm, Metric, ReferenceCode, SemanticSubject } from "@/types/metric";

/**
 * 种子指标 —— 归口部门。
 * 销售处：销售达成率（草稿，指标阶段仍待办，与部门 metrics 一致）。
 * 质量处：合格率（已发布，对接领导驾驶舱）。
 */
export const SEED_METRICS: Metric[] = [
	{
		id: "mt-sales-rate",
		departmentId: "dept-sales",
		name: "销售达成率",
		code: "sales_achieve_rate",
		caliber: "实际销售额 / 目标销售额",
		expression: "sum(amount) / target_amount",
		unit: "%",
		datasetId: "dsx-sales-wide",
		status: "draft",
		owner: "销售处",
		updatedAt: "2026-06-23",
		currentValue: 86.4,
		target: 100,
		trend: [72, 78, 75, 81, 84, 86],
	},
	{
		id: "mt-quality-pass",
		departmentId: "dept-quality",
		name: "质量合格率",
		code: "quality_pass_rate",
		caliber: "合格批次 / 抽检批次",
		expression: "pass_cnt / batch_cnt",
		unit: "%",
		datasetId: "dsx-quality-monthly",
		status: "published",
		owner: "质量处",
		updatedAt: "2026-06-20",
		currentValue: 98.2,
		target: 99,
		trend: [97.1, 97.8, 98.0, 97.6, 98.4, 98.2],
	},
	{
		id: "mt-quality-batch",
		departmentId: "dept-quality",
		name: "月度抽检批次",
		code: "monthly_batch_cnt",
		caliber: "当月抽检批次总数",
		expression: "count(batch_no)",
		unit: "批",
		datasetId: "dsx-quality-monthly",
		status: "published",
		owner: "质量处",
		updatedAt: "2026-06-20",
		currentValue: 312,
		target: 300,
		trend: [280, 295, 305, 298, 320, 312],
	},
];

export const SEED_SEMANTIC_SUBJECTS: SemanticSubject[] = [
	{
		id: "ss-sales",
		departmentId: "dept-sales",
		name: "销售主题",
		datasetId: "dsx-sales-wide",
		metricCodes: ["sales_achieve_rate"],
		dimensions: ["客户", "下单日期", "产品"],
		status: "draft",
	},
	{
		id: "ss-quality",
		departmentId: "dept-quality",
		name: "质量主题",
		datasetId: "dsx-quality-monthly",
		metricCodes: ["quality_pass_rate", "monthly_batch_cnt"],
		dimensions: ["月份", "批次", "产线"],
		status: "published",
	},
];

export const SEED_GLOSSARY: GlossaryTerm[] = [
	{ id: "gl-1", departmentId: "dept-sales", term: "达成率", definition: "实际值与目标值之比，常以百分比表示。" },
	{ id: "gl-2", departmentId: "dept-quality", term: "合格率", definition: "合格批次数占抽检批次总数的比例。" },
	{ id: "gl-3", departmentId: "dept-quality", term: "抽检", definition: "按规则从批次中抽样并检验的质量活动。" },
];

export const SEED_REFERENCE_CODES: ReferenceCode[] = [
	{ id: "rc-1", codeType: "检验结果", code: "P", name: "合格" },
	{ id: "rc-2", codeType: "检验结果", code: "F", name: "不合格" },
	{ id: "rc-3", codeType: "订单状态", code: "1", name: "已下单" },
	{ id: "rc-4", codeType: "订单状态", code: "2", name: "已发货" },
	{ id: "rc-5", codeType: "订单状态", code: "9", name: "已完成" },
];
