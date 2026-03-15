import { HealthScoreCard } from "./HealthScoreCard";

type Props = {
	overdueTaskCount: number;
	maxDelayDays: number;
	nextMilestoneName: string;
	dueSoonCount: number;
	busiestDept: string;
};

export function ExecutionKpiPanel(props: Props) {
	return (
		<div className="project-cockpit__metric-grid project-cockpit__metric-grid--execution">
			<HealthScoreCard
				label="延期任务"
				value={String(props.overdueTaskCount)}
				unit="个"
				hint="当前筛选范围内已经显性延期的节点。"
				tone={props.overdueTaskCount > 0 ? "error" : "success"}
			/>
			<HealthScoreCard
				label="最大延期"
				value={String(props.maxDelayDays)}
				unit="天"
				hint="拖期最长的节点，用来识别执行瓶颈。"
				tone={props.maxDelayDays > 0 ? "warning" : "success"}
			/>
			<HealthScoreCard
				label="下一里程碑"
				value={props.nextMilestoneName || "--"}
				hint="执行主题默认展示最近一个需要盯紧的里程碑。"
				tone="info"
			/>
			<HealthScoreCard
				label="近期节点"
				value={String(props.dueSoonCount)}
				unit="项"
				hint={`最忙责任科室：${props.busiestDept || "--"}`}
				tone="default"
			/>
		</div>
	);
}
