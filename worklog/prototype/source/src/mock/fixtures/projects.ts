import type { Project } from "@/types/project";

/**
 * 种子项目 —— 隶属于工作区（部门）。
 * 销售处下有 2 个项目（体现"一个部门多个项目"）；质量处 1 个成熟项目。
 * "销售准备项目" 贯穿 4 阶段，是黄金主线可走通的样例。
 */
export const SEED_PROJECTS: Project[] = [
	{
		id: "proj-sales",
		workspaceId: "ws-sales",
		name: "销售准备项目",
		description: "整合 PLM 订单与 ERP 客户，产出销售达成率指标",
		owner: "测试用户",
		updatedAt: "2026-06-19",
		metrics: {
			connectedSources: 2,
			transformRunsSucceeded: 0,
			publishedDatasets: 0,
			publishedIndicators: 0,
		},
	},
	{
		id: "proj-sales-forecast",
		workspaceId: "ws-sales",
		name: "销售预测项目",
		description: "基于历史订单的销售预测建模",
		owner: "测试用户",
		updatedAt: "2026-06-15",
		metrics: {
			connectedSources: 0,
			transformRunsSucceeded: 0,
			publishedDatasets: 0,
			publishedIndicators: 0,
		},
	},
	{
		id: "proj-quality",
		workspaceId: "ws-quality",
		name: "质量月报项目",
		description: "QMIS 质量数据月度入湖与看板",
		owner: "网信中心",
		updatedAt: "2026-06-17",
		metrics: {
			connectedSources: 3,
			transformRunsSucceeded: 4,
			publishedDatasets: 2,
			publishedIndicators: 1,
		},
	},
];
