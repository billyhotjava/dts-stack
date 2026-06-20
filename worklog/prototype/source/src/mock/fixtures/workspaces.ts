import type { Workspace } from "@/types/workspace";

/** 种子工作区（部门）。一个工作区下可挂多个项目。 */
export const SEED_WORKSPACES: Workspace[] = [
	{
		id: "ws-sales",
		name: "销售处",
		deptCode: "SALES",
		description: "销售业务数据与分析",
		owner: "崔耀文",
		memberCount: 8,
	},
	{
		id: "ws-quality",
		name: "质量处",
		deptCode: "QUALITY",
		description: "质量管理与月报",
		owner: "网信中心",
		memberCount: 5,
	},
];
