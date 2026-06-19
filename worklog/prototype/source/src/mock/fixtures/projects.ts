import type { Project } from "@/types/project";

/**
 * 种子项目 —— "销售准备项目" 贯穿 4 阶段，是黄金主线可走通的样例。
 * 当前状态：已连通 2 个源（阶段① 完成），尚无转换跑通（阶段② 进行中），
 * 故项目门户会引导用户进入阶段② 集成。
 */
export const SEED_PROJECTS: Project[] = [
	{
		id: "proj-sales",
		name: "销售准备项目",
		description: "整合 PLM 订单与 ERP 客户，产出销售达成率指标",
		owner: "崔耀文",
		updatedAt: "2026-06-19",
		metrics: {
			connectedSources: 2,
			transformRunsSucceeded: 0,
			publishedDatasets: 0,
			publishedIndicators: 0,
		},
	},
	{
		id: "proj-quality",
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
