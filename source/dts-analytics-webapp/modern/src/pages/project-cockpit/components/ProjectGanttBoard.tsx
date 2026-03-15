type Task = {
	id?: string;
	name?: string;
	type?: string;
	planDate?: string;
	actualDate?: string;
	delayDays?: number;
	riskLevel?: string;
	owner?: string;
	majorProjectName?: string;
	subprojectName?: string;
};

type Props = {
	tasks: Task[];
};

function toDateValue(value?: string) {
	if (!value) {
		return null;
	}
	const time = new Date(value).getTime();
	return Number.isFinite(time) ? time : null;
}

export function ProjectGanttBoard({ tasks }: Props) {
	if (tasks.length === 0) {
		return <div className="project-cockpit__empty-block">当前筛选范围暂无执行任务。</div>;
	}

	const allDates = tasks.flatMap((task) => [toDateValue(task.planDate), toDateValue(task.actualDate)]).filter(
		(value): value is number => value != null,
	);
	if (allDates.length === 0) {
		return <div className="project-cockpit__empty-block">缺少计划日期，无法渲染甘特视图。</div>;
	}

	const start = Math.min(...allDates);
	const end = Math.max(...allDates, Date.now());
	const total = Math.max(end - start, 1);

	return (
		<div className="project-cockpit__gantt">
			{tasks.slice(0, 16).map((task) => {
				const plan = toDateValue(task.planDate) ?? start;
				const actual = toDateValue(task.actualDate) ?? Date.now();
				const left = ((plan - start) / total) * 100;
				const width = Math.max(((actual - plan) / total) * 100, 1.5);
				return (
					<div key={task.id ?? task.name} className="project-cockpit__gantt-row">
						<div className="project-cockpit__gantt-meta">
							<strong>{task.name}</strong>
							<span>
								{task.majorProjectName} / {task.subprojectName}
							</span>
						</div>
						<div className="project-cockpit__gantt-track">
							<div
								className={`project-cockpit__gantt-bar project-cockpit__gantt-bar--${task.riskLevel === "高" ? "high" : task.delayDays ? "warn" : "normal"}`}
								style={{ left: `${left}%`, width: `${width}%` }}
							>
								<span>{task.owner}</span>
							</div>
						</div>
						<div className="project-cockpit__gantt-side">
							<span>{task.planDate || "--"}</span>
							<span>{task.actualDate || "进行中"}</span>
						</div>
					</div>
				);
			})}
		</div>
	);
}
