/**
 * 部门 = 主组织边界（权限分割 + 资产/指标归口）。
 * 对齐现网后端：dept 是主权限轴，资产实体普遍带 ownerDept。
 * 黄金主线四阶段的成熟度归口到部门。
 */
export type StageKey = "connect" | "integrate" | "assets" | "metrics";

export type StageStatus = "done" | "active" | "todo";

/**
 * 部门级阶段度量 —— 黄金主线状态由此派生（资产/指标归口部门，而非项目）。
 */
export interface StageMetrics {
	/** 已连通的数据源数（阶段① 完成判据：>=1） */
	connectedSources: number;
	/** 跑通的转换作业数（阶段② 完成判据：>=1） */
	transformRunsSucceeded: number;
	/** 已发布的数据集数（阶段③ 完成判据：>=1） */
	publishedDatasets: number;
	/** 已发布的指标数（阶段④ 完成判据：>=1） */
	publishedIndicators: number;
}

export interface Department {
	id: string;
	name: string;
	/** 部门编码（稳定 ASCII 主键，避免中文名漂移） */
	deptCode: string;
	description?: string;
	owner?: string;
	memberCount?: number;
	/** 部门的黄金主线成熟度 */
	metrics: StageMetrics;
}
