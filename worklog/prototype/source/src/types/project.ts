/** 黄金主线四阶段。 */
export type StageKey = "connect" | "integrate" | "assets" | "metrics";

/** 阶段状态：完成 / 进行中 / 待开始。 */
export type StageStatus = "done" | "active" | "todo";

/**
 * 项目级度量 —— 阶段状态由这些数值派生，而非手工设置。
 * 这是"项目/工作空间 + 阶段向导"范式的事实源。
 */
export interface ProjectMetrics {
	/** 已连通的数据源数（阶段① 完成判据：>=1） */
	connectedSources: number;
	/** 跑通的转换作业数（阶段② 完成判据：>=1） */
	transformRunsSucceeded: number;
	/** 已发布的数据集数（阶段③ 完成判据：>=1） */
	publishedDatasets: number;
	/** 已发布的指标数（阶段④ 完成判据：>=1） */
	publishedIndicators: number;
}

export interface Project {
	id: string;
	name: string;
	description?: string;
	owner?: string;
	updatedAt?: string;
	metrics: ProjectMetrics;
}
