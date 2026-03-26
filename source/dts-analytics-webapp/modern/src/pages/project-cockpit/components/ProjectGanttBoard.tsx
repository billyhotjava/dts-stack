import "./ProjectGanttBoard.css";
import { useState } from "react";
import { getGanttOwnerLabelPlacement, resolveGanttBaselineRange, resolveGanttSideTextStyle } from "./projectGanttBoard.helpers";

export type ProjectGanttTask = {
	id?: string;
	name?: string;
	type?: string;
	planDate?: string;
	planEndDate?: string;
	baselineStartDate?: string;
	baselineEndDate?: string;
	actualDate?: string;
	delayDays?: number;
	riskLevel?: string;
	owner?: string;
	majorProjectName?: string;
	subprojectName?: string;
};

type Props = {
	tasks: ProjectGanttTask[];
	maxHeight?: number;
	onTaskClick?: (task: ProjectGanttTask) => void;
	sideTextColor?: string;
	dark?: boolean;
};

function toDateValue(value?: string) {
	if (!value) return null;
	const time = new Date(value).getTime();
	return Number.isFinite(time) ? time : null;
}

type GroupedProject = {
	name: string;
	tasks: ProjectGanttTask[];
};

function groupByProject(tasks: ProjectGanttTask[]): GroupedProject[] {
	const map = new Map<string, ProjectGanttTask[]>();
	for (const task of tasks) {
		const key = task.majorProjectName ?? "未分组";
		const list = map.get(key);
		if (list) {
			list.push(task);
		} else {
			map.set(key, [task]);
		}
	}
	return Array.from(map.entries()).map(([name, items]) => ({ name, tasks: items }));
}

export function ProjectGanttBoard({ tasks, maxHeight, onTaskClick, sideTextColor, dark }: Props) {
	const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());
	const sideTextStyle = resolveGanttSideTextStyle(sideTextColor);

	if (tasks.length === 0) {
		return <div className="project-cockpit__empty-block">当前筛选范围暂无执行任务。</div>;
	}

	const allDates = tasks
		.flatMap((task) => {
			const baseline = resolveGanttBaselineRange(task);
			return [
				toDateValue(task.planDate),
				toDateValue(task.planEndDate),
				toDateValue(task.actualDate),
				toDateValue(baseline.startDate),
				toDateValue(baseline.endDate),
			];
		})
		.filter((value): value is number => value != null);
	if (allDates.length === 0) {
		return <div className="project-cockpit__empty-block">缺少计划日期，无法渲染甘特视图。</div>;
	}

	const start = Math.min(...allDates);
	const end = Math.max(...allDates, Date.now());
	const total = Math.max(end - start, 1);

	const groups = groupByProject(tasks);
	const useGroups = groups.length > 1;

	const toggleGroup = (name: string) => {
		setCollapsedGroups((prev) => {
			const next = new Set(prev);
			if (next.has(name)) {
				next.delete(name);
			} else {
				next.add(name);
			}
			return next;
		});
	};

	const renderRow = (task: ProjectGanttTask) => {
		const planStart = toDateValue(task.planDate) ?? start;
		const planEnd = toDateValue(task.planEndDate) ?? planStart;
		const baseline = resolveGanttBaselineRange(task);
		const baselineStart = toDateValue(baseline.startDate) ?? planStart;
		const baselineEnd = toDateValue(baseline.endDate) ?? baselineStart;
		const actual = toDateValue(task.actualDate) ?? Date.now();
		const isOngoing = !task.actualDate;

		// Baseline bar (plan)
		const baseLeft = ((baselineStart - start) / total) * 100;
		const baseWidth = Math.max(((baselineEnd - baselineStart) / total) * 100, 1.5);

		// Actual bar
		const actLeft = ((Math.min(planStart, actual) - start) / total) * 100;
		const actWidth = Math.max(((actual - planStart) / total) * 100, 1.5);

		const tone = task.riskLevel === "高" ? "high" : task.delayDays ? "warn" : "normal";
		const owner = String(task.owner ?? "").trim();
		const ownerPlacement = owner ? getGanttOwnerLabelPlacement(actLeft, actWidth) : "inside";
		const ownerAnchor = ownerPlacement === "outside-left" ? actLeft : actLeft + actWidth;

		// Deviation label
		const delayDays = task.delayDays ?? 0;
		const deviationLabel = delayDays > 0 ? `+${delayDays}天` : delayDays < 0 ? `${delayDays}天` : null;
		const deviationColor = delayDays > 0 ? "#dc2626" : "#16a34a";
		const rowInteractive = typeof onTaskClick === "function";
		const triggerTaskClick = () => {
			onTaskClick?.(task);
		};

		return (
			<div
				key={task.id ?? task.name}
				className={`project-cockpit__gantt-row${rowInteractive ? " project-cockpit__gantt-row--interactive" : ""}`}
				role={rowInteractive ? "button" : undefined}
				tabIndex={rowInteractive ? 0 : undefined}
				onClick={rowInteractive ? triggerTaskClick : undefined}
				onKeyDown={rowInteractive ? (event) => {
					if (event.key === "Enter" || event.key === " ") {
						event.preventDefault();
						triggerTaskClick();
					}
				} : undefined}
			>
				<div className="project-cockpit__gantt-meta">
					<strong>{task.name}</strong>
					<span>{task.subprojectName ?? ""}</span>
				</div>
				<div className="project-cockpit__gantt-track">
					{/* Baseline bar (gray, behind) */}
					{baselineEnd >= baselineStart && (
						<div
							className="project-cockpit__gantt-bar project-cockpit__gantt-bar--baseline"
							style={{ left: `${baseLeft}%`, width: `${baseWidth}%` }}
							title={`基线: ${baseline.startDate ?? ""} → ${baseline.endDate ?? ""}`}
						/>
					)}
					{/* Actual bar (colored, front) */}
					<div
						className={`project-cockpit__gantt-bar project-cockpit__gantt-bar--${tone}${isOngoing ? " project-cockpit__gantt-bar--ongoing" : ""}`}
						style={{ left: `${actLeft}%`, width: `${actWidth}%` }}
						title={owner || undefined}
					>
						{owner && ownerPlacement === "inside" ? (
							<span className="project-cockpit__gantt-owner project-cockpit__gantt-owner--inside">{owner}</span>
						) : null}
					</div>
					{owner && ownerPlacement !== "inside" ? (
						<span
							className={`project-cockpit__gantt-owner project-cockpit__gantt-owner--outside project-cockpit__gantt-owner--${tone} project-cockpit__gantt-owner--${ownerPlacement === "outside-left" ? "left" : "right"}`}
							style={{ left: `${Math.min(ownerAnchor, 100)}%` }}
							title={owner}
						>
							{owner}
						</span>
					) : null}
				</div>
				<div className="project-cockpit__gantt-side">
					<span style={sideTextStyle}>{task.planDate || "--"}</span>
					<span style={sideTextStyle}>{task.actualDate || "进行中"}</span>
					{deviationLabel ? (
						<span style={{ color: deviationColor, fontWeight: 600, fontSize: 12 }}>{deviationLabel}</span>
					) : null}
				</div>
			</div>
		);
	};

	return (
		<div className={`project-cockpit__gantt${dark ? " project-cockpit__gantt--dark" : ""}`} style={{ maxHeight: maxHeight ?? 520, overflowY: "auto" }}>
			{useGroups
				? groups.map((group) => {
						const collapsed = collapsedGroups.has(group.name);
						return (
							<div key={group.name}>
								<div
									className="project-cockpit__gantt-group-header"
									onClick={() => toggleGroup(group.name)}
								>
									<span className="project-cockpit__gantt-group-arrow">{collapsed ? "▸" : "▾"}</span>
									<strong>{group.name}</strong>
									<span className="project-cockpit__gantt-group-count">{group.tasks.length} 项</span>
								</div>
								{!collapsed && group.tasks.map(renderRow)}
							</div>
						);
				  })
				: tasks.map(renderRow)}
		</div>
	);
}
