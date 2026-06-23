import type { Department, StageKey, StageMetrics, StageStatus } from "@/types/department";

export interface StageDef {
	key: StageKey;
	index: number;
	label: string;
	path: string;
	/** 该阶段"完成"的判据描述 */
	doneWhen: string;
	/** 引导卡里的下一步动作文案 */
	nextAction: (d: Department) => string;
	/** 从部门度量判定该阶段是否已完成 */
	isComplete: (m: StageMetrics) => boolean;
}

export const STAGES: StageDef[] = [
	{
		key: "connect",
		index: 1,
		label: "连接",
		path: "/connect",
		doneWhen: "已连通 ≥ 1 个数据源",
		nextAction: () => "接入第一个数据源并完成连通测试",
		isComplete: (m) => m.connectedSources >= 1,
	},
	{
		key: "integrate",
		index: 2,
		label: "集成",
		path: "/integrate",
		doneWhen: "跑通 ≥ 1 个转换作业",
		nextAction: (d) => `把已连接的 ${d.metrics.connectedSources} 个源在画布上搭一条转换并跑通`,
		isComplete: (m) => m.transformRunsSucceeded >= 1,
	},
	{
		key: "assets",
		index: 3,
		label: "资产",
		path: "/assets",
		doneWhen: "发布 ≥ 1 个数据集",
		nextAction: () => "把转换产出的 ODS 宽表发布为部门数据资产",
		isComplete: (m) => m.publishedDatasets >= 1,
	},
	{
		key: "metrics",
		index: 4,
		label: "指标",
		path: "/metrics",
		doneWhen: "发布 ≥ 1 个指标",
		nextAction: () => "基于部门已发布的数据集设计并发布业务指标",
		isComplete: (m) => m.publishedIndicators >= 1,
	},
];

/**
 * 派生每个阶段的状态（按部门度量）：
 * - 已满足判据 → done
 * - 第一个未完成的阶段 → active
 * - 其余 → todo
 */
export function deriveStageStatuses(department: Department): Record<StageKey, StageStatus> {
	const result = {} as Record<StageKey, StageStatus>;
	let activeAssigned = false;
	for (const stage of STAGES) {
		if (stage.isComplete(department.metrics)) {
			result[stage.key] = "done";
		} else if (!activeAssigned) {
			result[stage.key] = "active";
			activeAssigned = true;
		} else {
			result[stage.key] = "todo";
		}
	}
	return result;
}

/** 当前应引导用户进入的阶段（第一个未完成；全完成则回到指标阶段）。 */
export function currentStage(department: Department): StageDef {
	return STAGES.find((s) => !s.isComplete(department.metrics)) ?? STAGES[STAGES.length - 1];
}
