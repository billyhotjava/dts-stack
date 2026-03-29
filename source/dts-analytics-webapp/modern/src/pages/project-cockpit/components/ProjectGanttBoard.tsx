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

/* ── Gantt bar gradient backgrounds ── */
const TONE_BG: Record<string, string> = {
	normal: "linear-gradient(90deg, #2563eb, #1d4ed8)",
	warn: "linear-gradient(90deg, #f59e0b, #d97706)",
	high: "linear-gradient(90deg, #ef4444, #dc2626)",
};

/* ── Owner outside-label tone styles ── */
const OWNER_TONE_LIGHT: Record<string, string> = {
	normal: "border-blue-600/[0.18] bg-blue-50 text-blue-800",
	warn: "border-amber-600/20 bg-orange-50 text-amber-700",
	high: "border-red-600/20 bg-red-50 text-red-700",
};

const OWNER_TONE_DARK: Record<string, string> = {
	normal: "border-blue-500/[0.35] bg-blue-600/[0.22] text-blue-300",
	warn: "border-amber-400/[0.35] bg-amber-600/[0.22] text-yellow-300",
	high: "border-red-500/[0.35] bg-red-600/[0.22] text-red-300",
};

export function ProjectGanttBoard({ tasks, maxHeight, onTaskClick, sideTextColor, dark }: Props) {
	const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());
	const sideTextStyle = resolveGanttSideTextStyle(sideTextColor);

	if (tasks.length === 0) {
		return (
			<div className={`flex items-center justify-center min-h-[220px] border border-dashed rounded-2xl ${dark ? "border-slate-400/20 bg-slate-900/60 text-white/50" : "border-border-default bg-surface-muted text-text-secondary"}`}>
				当前筛选范围暂无执行任务。
			</div>
		);
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
		return (
			<div className={`flex items-center justify-center min-h-[220px] border border-dashed rounded-2xl ${dark ? "border-slate-400/20 bg-slate-900/60 text-white/50" : "border-border-default bg-surface-muted text-text-secondary"}`}>
				缺少计划日期，无法渲染甘特视图。
			</div>
		);
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

	const trackBg = dark
		? "repeating-linear-gradient(90deg, rgba(148,163,184,0.14) 0, rgba(148,163,184,0.14) 8%, transparent 8%, transparent 16%)"
		: "repeating-linear-gradient(90deg, rgba(148,163,184,0.08) 0, rgba(148,163,184,0.08) 8%, transparent 8%, transparent 16%)";

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

		/* Actual bar style */
		const barStyle: React.CSSProperties = {
			left: `${actLeft}%`,
			width: `${actWidth}%`,
		};
		if (isOngoing) {
			barStyle.border = "2px dashed currentColor";
			barStyle.background = "none";
			barStyle.color = dark ? "rgba(255,255,255,0.5)" : "#6b7280";
			barStyle.boxShadow = "none";
		} else {
			barStyle.background = TONE_BG[tone];
		}

		return (
			<div
				key={task.id ?? task.name}
				className={`grid grid-cols-[240px_1fr_170px] gap-3 items-center rounded-xl transition-[background,box-shadow] duration-[180ms] max-[1200px]:grid-cols-1${rowInteractive ? ` cursor-pointer ${dark ? "hover:bg-blue-500/[0.12] hover:shadow-[inset_0_0_0_1px_rgba(59,130,246,0.16)]" : "hover:bg-blue-600/[0.06] hover:shadow-[inset_0_0_0_1px_rgba(37,99,235,0.08)]"} focus-visible:outline-2 focus-visible:outline-blue-400/50 focus-visible:outline-offset-2` : ""}`}
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
				{/* Meta column */}
				<div className={`flex flex-col gap-1 text-xs ${dark ? "text-white/85" : "text-text-secondary"}`}>
					<strong className={`text-[13px] ${dark ? "text-[#e8ecf2]" : "text-text-primary"}`}>{task.name}</strong>
					<span>{task.subprojectName ?? ""}</span>
				</div>
				{/* Track */}
				<div
					className="relative h-8 rounded-full overflow-visible"
					style={{ background: trackBg }}
				>
					{/* Baseline bar (gray, behind) */}
					{baselineEnd >= baselineStart && (
						<div
							className="absolute top-3 h-2 rounded"
							style={{
								left: `${baseLeft}%`,
								width: `${baseWidth}%`,
								background: dark ? "rgba(148,163,184,0.35)" : "#e5e7eb",
								zIndex: 0,
							}}
							title={`基线: ${baseline.startDate ?? ""} → ${baseline.endDate ?? ""}`}
						/>
					)}
					{/* Actual bar (colored, front) */}
					<div
						className="absolute top-1 h-6 inline-flex items-center justify-center px-2.5 rounded-full text-white text-xs whitespace-nowrap overflow-hidden shadow-[0_8px_16px_rgba(15,23,42,0.12)] z-[1]"
						style={barStyle}
						title={owner || undefined}
					>
						{owner && ownerPlacement === "inside" ? (
							<span className="text-xs font-bold tracking-wide text-white/[0.98] [text-shadow:0_1px_2px_rgba(15,23,42,0.28)]">{owner}</span>
						) : null}
					</div>
					{/* Owner outside label */}
					{owner && ownerPlacement !== "inside" ? (
						<span
							className={`absolute top-1/2 z-[2] max-w-24 px-2 py-0.5 border rounded-full whitespace-nowrap overflow-hidden text-ellipsis text-xs font-bold tracking-wide pointer-events-none ${dark ? `shadow-[0_8px_18px_rgba(0,0,0,0.35)] ${OWNER_TONE_DARK[tone]}` : `shadow-[0_8px_18px_rgba(15,23,42,0.14)] bg-white/[0.98] ${OWNER_TONE_LIGHT[tone]}`}`}
							style={{
								left: `${Math.min(ownerAnchor, 100)}%`,
								transform: ownerPlacement === "outside-left"
									? "translate(calc(-100% - 6px), -50%)"
									: "translate(6px, -50%)",
							}}
							title={owner}
						>
							{owner}
						</span>
					) : null}
				</div>
				{/* Side column */}
				<div className={`flex flex-col gap-1 text-xs ${dark ? "text-white/85" : "text-text-secondary"}`}>
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
		<div className="flex flex-col gap-2.5" style={{ maxHeight: maxHeight ?? 520, overflowY: "auto" }}>
			{useGroups
				? groups.map((group) => {
						const collapsed = collapsedGroups.has(group.name);
						return (
							<div key={group.name}>
								<div
									className={`flex items-center gap-2 py-2 cursor-pointer select-none border-b ${dark ? "border-slate-400/[0.18] text-[#e8ecf2]" : "border-border-default"}`}
									onClick={() => toggleGroup(group.name)}
								>
									<span className={`text-xs ${dark ? "text-white/50" : "text-gray-400"}`}>{collapsed ? "▸" : "▾"}</span>
									<strong>{group.name}</strong>
									<span className={`text-xs ${dark ? "text-white/50" : "text-text-secondary"}`}>{group.tasks.length} 项</span>
								</div>
								{!collapsed && group.tasks.map(renderRow)}
							</div>
						);
				  })
				: tasks.map(renderRow)}
		</div>
	);
}
