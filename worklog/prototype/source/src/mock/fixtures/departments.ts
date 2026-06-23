import type { Department } from "@/types/department";

/**
 * 种子部门（主组织边界）。黄金主线成熟度归口到部门。
 * 销售处：已连通 2 源、尚无转换跑通 → 阶段① 完成、阶段② 进行中（引导用）。
 * 质量处：四阶段全完成（成熟部门）。
 */
export const SEED_DEPARTMENTS: Department[] = [
	{
		id: "dept-sales",
		name: "销售处",
		deptCode: "SALES",
		description: "销售业务数据与分析",
		owner: "测试用户",
		memberCount: 8,
		metrics: {
			connectedSources: 2,
			transformRunsSucceeded: 0,
			publishedDatasets: 0,
			publishedIndicators: 0,
		},
	},
	{
		id: "dept-quality",
		name: "质量处",
		deptCode: "QUALITY",
		description: "质量管理与月报",
		owner: "网信中心",
		memberCount: 5,
		metrics: {
			connectedSources: 3,
			transformRunsSucceeded: 4,
			publishedDatasets: 2,
			publishedIndicators: 1,
		},
	},
];
