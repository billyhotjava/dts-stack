/**
 * 工作区 = 部门/处室（组织边界）。
 * 平台 → 工作区(部门) → 项目(交付目标) 三层模型的中间层。
 */
export interface Workspace {
	id: string;
	name: string;
	/** 部门编码（稳定 ASCII 主键，避免中文名漂移） */
	deptCode: string;
	description?: string;
	owner?: string;
	memberCount?: number;
}
