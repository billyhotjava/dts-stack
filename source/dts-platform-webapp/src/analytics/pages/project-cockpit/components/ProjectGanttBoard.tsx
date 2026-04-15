// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import { useCallback, useMemo, useRef, useState, memo } from "react";
import type { MajorProject } from "../../screens/types";
import { getGanttOwnerLabelPlacement, resolveGanttBaselineRange, resolveGanttSideTextStyle } from "./projectGanttBoard.helpers";

export type ProjectGanttTask = {
	id?: string;
	name?: string;
	type?: string;
	planDate?: string;
	planEndDate?: string;
	planStartDate?: string;
	actualStartDate?: string;
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
	tasks?: ProjectGanttTask[];
	majorProjects?: MajorProject[];
	renderMode?: "flat" | "hierarchical";
	onTaskClick?: (task: ProjectGanttTask) => void;
	onProjectClick?: (project: MajorProject) => void;
	highlightedTaskName?: string;
	maxHeight?: number;
	sideTextColor?: string;
	dark?: boolean;
};

// ──────────────────────────────────────────────────────────────────────────
// Time scale (enhancement B — zoom: day / week / month / quarter)
// ──────────────────────────────────────────────────────────────────────────

type ZoomLevel = "day" | "week" | "month" | "quarter";

interface TimeTick {
	pos: number;
	label: string;
	major: boolean;
}

interface TimeScale {
	start: number;
	end: number;
	pxPerDay: number;
	totalPx: number;
	zoom: ZoomLevel;
	ticks: TimeTick[];
	toPx: (ms: number) => number;
	widthPx: (startMs: number, endMs: number) => number;
}

const MS_PER_DAY = 86_400_000;
const LEFT_GRID_WIDTH_FLAT = 480;
const LEFT_GRID_WIDTH_HIER = 440;
const HEADER_HEIGHT = 48;
const FLAT_ROW_HEIGHT = 52;
const HIER_ROW_HEIGHT = 64;
const MIN_BAR_PX = 4;

const PX_PER_DAY: Record<ZoomLevel, number> = {
	day: 40,
	week: 14,
	month: 5,
	quarter: 2.2,
};

function pickDefaultZoom(totalDays: number): ZoomLevel {
	if (totalDays <= 60) return "day";
	if (totalDays <= 210) return "week";
	if (totalDays <= 720) return "month";
	return "quarter";
}

function toDateValue(value?: string) {
	if (!value) return null;
	const time = new Date(value).getTime();
	return Number.isFinite(time) ? time : null;
}

function minMax(arr: number[]): { min: number; max: number } {
	let min = arr[0];
	let max = arr[0];
	for (let i = 1; i < arr.length; i++) {
		if (arr[i] < min) min = arr[i];
		if (arr[i] > max) max = arr[i];
	}
	return { min, max };
}

function buildTimeScale(allDates: number[], zoom: ZoomLevel): TimeScale | null {
	if (allDates.length === 0) return null;
	const { min: rawStart, max: rawMax } = minMax(allDates);
	const rawEnd = Math.max(rawMax, Date.now());
	// Snap to day boundaries so ticks align cleanly
	const startDate = new Date(rawStart);
	startDate.setHours(0, 0, 0, 0);
	const endDate = new Date(rawEnd);
	endDate.setHours(23, 59, 59, 999);
	const start = startDate.getTime();
	const end = endDate.getTime();
	const pxPerDay = PX_PER_DAY[zoom];
	const totalDays = Math.max(1, (end - start) / MS_PER_DAY);
	const totalPx = Math.max(320, totalDays * pxPerDay);

	const toPx = (ms: number) => ((ms - start) / MS_PER_DAY) * pxPerDay;
	const widthPx = (s: number, e: number) =>
		Math.max(((e - s) / MS_PER_DAY) * pxPerDay, MIN_BAR_PX);

	const ticks: TimeTick[] = [];
	if (zoom === "day") {
		let d = new Date(start);
		while (d.getTime() <= end) {
			const mm = String(d.getMonth() + 1).padStart(2, "0");
			const dd = String(d.getDate()).padStart(2, "0");
			ticks.push({ pos: toPx(d.getTime()), label: `${mm}-${dd}`, major: d.getDay() === 1 });
			d = new Date(d.getTime() + MS_PER_DAY);
		}
	} else if (zoom === "week") {
		// Snap to Monday of containing week
		let d = new Date(start);
		const dow = d.getDay();
		const offset = (dow + 6) % 7;
		d = new Date(d.getTime() - offset * MS_PER_DAY);
		d.setHours(0, 0, 0, 0);
		while (d.getTime() <= end) {
			const mm = String(d.getMonth() + 1).padStart(2, "0");
			const dd = String(d.getDate()).padStart(2, "0");
			ticks.push({ pos: toPx(d.getTime()), label: `${mm}-${dd}`, major: d.getDate() <= 7 });
			d = new Date(d.getTime() + 7 * MS_PER_DAY);
		}
	} else if (zoom === "month") {
		let d = new Date(start);
		d = new Date(d.getFullYear(), d.getMonth(), 1);
		while (d.getTime() <= end) {
			const yyyy = d.getFullYear();
			const mm = String(d.getMonth() + 1).padStart(2, "0");
			ticks.push({ pos: toPx(d.getTime()), label: `${yyyy}-${mm}`, major: d.getMonth() === 0 });
			d = new Date(d.getFullYear(), d.getMonth() + 1, 1);
		}
	} else {
		let d = new Date(start);
		const q = Math.floor(d.getMonth() / 3);
		d = new Date(d.getFullYear(), q * 3, 1);
		while (d.getTime() <= end) {
			const yyyy = d.getFullYear();
			const qn = Math.floor(d.getMonth() / 3) + 1;
			ticks.push({ pos: toPx(d.getTime()), label: `${yyyy} Q${qn}`, major: qn === 1 });
			d = new Date(d.getFullYear(), d.getMonth() + 3, 1);
		}
	}

	return { start, end, pxPerDay, totalPx, zoom, ticks, toPx, widthPx };
}

function useGanttTimeScale(allDates: number[]) {
	const totalDays = useMemo(() => {
		if (allDates.length === 0) return 30;
		const { min, max } = minMax(allDates);
		return Math.max(1, (max - min) / MS_PER_DAY);
	}, [allDates]);
	const [zoom, setZoom] = useState<ZoomLevel>(() => pickDefaultZoom(totalDays));
	const scale = useMemo(() => buildTimeScale(allDates, zoom), [allDates, zoom]);
	return { zoom, setZoom, scale };
}

// ──────────────────────────────────────────────────────────────────────────
// Shared UI bits
// ──────────────────────────────────────────────────────────────────────────

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

const ZOOM_LABEL: Record<ZoomLevel, string> = {
	day: "日",
	week: "周",
	month: "月",
	quarter: "季度",
};

function GanttZoomToolbar({ zoom, setZoom, dark, meta }: {
	zoom: ZoomLevel;
	setZoom: (z: ZoomLevel) => void;
	dark?: boolean;
	meta?: string;
}) {
	const options: ZoomLevel[] = ["day", "week", "month", "quarter"];
	const activeCls = dark
		? "bg-blue-500/[0.22] border-blue-400/50 text-blue-200"
		: "bg-blue-600/10 border-blue-600/40 text-blue-700";
	const idleCls = dark
		? "bg-slate-700/30 border-slate-500/30 text-white/60 hover:bg-slate-700/50"
		: "bg-white border-border-default text-text-secondary hover:bg-surface-muted";
	return (
		<div className="flex items-center justify-between gap-4 px-1 pb-2">
			<div className="flex items-center gap-2">
				<span className={`text-xs ${dark ? "text-white/60" : "text-text-secondary"}`}>时间粒度</span>
				<div className="inline-flex -space-x-px">
					{options.map((k, idx) => {
						const isActive = zoom === k;
						const round = idx === 0
							? "rounded-l-md"
							: idx === options.length - 1
								? "rounded-r-md"
								: "";
						return (
							<button
								key={k}
								type="button"
								onClick={() => setZoom(k)}
								className={`px-2.5 py-1 text-xs border transition-colors ${isActive ? activeCls : idleCls} ${round}`}
							>
								{ZOOM_LABEL[k]}
							</button>
						);
					})}
				</div>
			</div>
			{meta ? (
				<span className={`text-[11px] ${dark ? "text-white/45" : "text-text-tertiary"}`}>{meta}</span>
			) : null}
		</div>
	);
}

function GanttTimelineHeader({ scale, dark }: { scale: TimeScale; dark?: boolean }) {
	const majorColor = dark ? "rgba(255,255,255,0.9)" : "rgba(15,23,42,0.85)";
	const minorColor = dark ? "rgba(255,255,255,0.5)" : "rgba(71,85,105,0.6)";
	const lineColor = dark ? "rgba(148,163,184,0.18)" : "rgba(148,163,184,0.22)";
	return (
		<div
			className="relative flex-shrink-0"
			style={{
				width: scale.totalPx,
				height: HEADER_HEIGHT,
			}}
		>
			{scale.ticks.map((tick, i) => (
				<div
					key={i}
					className="absolute top-0 bottom-0 flex items-center"
					style={{
						left: tick.pos,
						borderLeft: `1px ${tick.major ? "solid" : "dashed"} ${lineColor}`,
						paddingLeft: 4,
					}}
				>
					<span
						className="text-[11px] whitespace-nowrap select-none"
						style={{
							color: tick.major ? majorColor : minorColor,
							fontWeight: tick.major ? 600 : 400,
						}}
					>
						{tick.label}
					</span>
				</div>
			))}
		</div>
	);
}

/**
 * Background track rendered behind task bars. Draws vertical tick guides
 * at every timeline tick so rows visually align with the header.
 */
function GanttTrackGrid({ scale, dark }: { scale: TimeScale; dark?: boolean }) {
	const lineColor = dark ? "rgba(148,163,184,0.10)" : "rgba(148,163,184,0.14)";
	const majorLine = dark ? "rgba(148,163,184,0.18)" : "rgba(148,163,184,0.22)";
	return (
		<>
			{scale.ticks.map((tick, i) => (
				<div
					key={i}
					className="absolute top-0 bottom-0 pointer-events-none"
					style={{
						left: tick.pos,
						width: 1,
						background: tick.major ? majorLine : lineColor,
					}}
				/>
			))}
		</>
	);
}

function formatDateShort(ms: number | null | undefined): string {
	if (ms == null || !Number.isFinite(ms)) return "";
	const d = new Date(ms);
	const yyyy = d.getFullYear();
	const mm = String(d.getMonth() + 1).padStart(2, "0");
	const dd = String(d.getDate()).padStart(2, "0");
	return `${yyyy}-${mm}-${dd}`;
}

// ──────────────────────────────────────────────────────────────────────────
// Entry
// ──────────────────────────────────────────────────────────────────────────

export function ProjectGanttBoard(props: Props) {
	if (props.renderMode === "hierarchical" && props.majorProjects) {
		return (
			<HierarchicalGantt
				majorProjects={props.majorProjects}
				onProjectClick={props.onProjectClick}
				maxHeight={props.maxHeight}
				dark={props.dark}
				sideTextColor={props.sideTextColor}
			/>
		);
	}
	return (
		<FlatGantt
			tasks={props.tasks ?? []}
			maxHeight={props.maxHeight}
			onTaskClick={props.onTaskClick}
			sideTextColor={props.sideTextColor}
			dark={props.dark}
			highlightedTaskName={props.highlightedTaskName}
		/>
	);
}

// ──────────────────────────────────────────────────────────────────────────
// FlatGantt — two-pane layout with sticky left grid + scrollable timeline
// ──────────────────────────────────────────────────────────────────────────

function FlatGantt({ tasks, maxHeight, onTaskClick, sideTextColor, dark, highlightedTaskName }: {
	tasks: ProjectGanttTask[];
	maxHeight?: number;
	onTaskClick?: (task: ProjectGanttTask) => void;
	sideTextColor?: string;
	dark?: boolean;
	highlightedTaskName?: string;
}) {
	const sideTextStyle = resolveGanttSideTextStyle(sideTextColor);
	const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());

	const allDates = useMemo(() => (
		tasks
			.flatMap((task) => {
				const baseline = resolveGanttBaselineRange(task);
				return [
					toDateValue(task.planStartDate ?? task.planDate),
					toDateValue(task.planEndDate),
					toDateValue(task.actualStartDate),
					toDateValue(task.actualDate),
					toDateValue(baseline.startDate),
					toDateValue(baseline.endDate),
				];
			})
			.filter((value): value is number => value != null)
	), [tasks]);

	const { zoom, setZoom, scale } = useGanttTimeScale(allDates);

	const groups = useMemo(() => groupByProject(tasks), [tasks]);
	const useGroups = groups.length > 1;

	const toggleGroup = useCallback((name: string) => {
		setCollapsedGroups((prev) => {
			const next = new Set(prev);
			if (next.has(name)) next.delete(name);
			else next.add(name);
			return next;
		});
	}, []);

	if (tasks.length === 0) {
		return (
			<EmptyShell dark={dark} message="当前筛选范围暂无执行任务。" />
		);
	}
	if (!scale) {
		return (
			<EmptyShell dark={dark} message="缺少计划日期，无法渲染甘特视图。" />
		);
	}

	const headerBg = dark ? "rgba(15,23,36,0.95)" : "#f8fafc";
	const rowBg = dark ? "rgba(15,23,36,0.6)" : "#ffffff";
	const lineColor = dark ? "rgba(148,163,184,0.16)" : "rgba(148,163,184,0.22)";
	const rowHoverBg = dark ? "rgba(59,130,246,0.1)" : "rgba(37,99,235,0.05)";

	const meta = `${tasks.length} 项 · ${formatDateShort(scale.start)} ~ ${formatDateShort(scale.end)}`;

	return (
		<div className="flex flex-col">
			<GanttZoomToolbar zoom={zoom} setZoom={setZoom} dark={dark} meta={meta} />
			<div
				className="rounded-xl overflow-auto border"
				style={{
					maxHeight: maxHeight ?? 520,
					borderColor: lineColor,
					background: dark ? "rgba(15,23,36,0.4)" : "#fff",
				}}
			>
				<div style={{ minWidth: "100%", width: LEFT_GRID_WIDTH_FLAT + scale.totalPx }}>
					{/* Sticky header row */}
					<div
						className="flex sticky top-0 z-20"
						style={{ background: headerBg, borderBottom: `1px solid ${lineColor}`, height: HEADER_HEIGHT }}
					>
						<FlatGridHeader dark={dark} headerBg={headerBg} lineColor={lineColor} />
						<GanttTimelineHeader scale={scale} dark={dark} />
					</div>

					{/* Body rows */}
					{useGroups
						? groups.map((group) => {
								const collapsed = collapsedGroups.has(group.name);
								return (
									<div key={group.name}>
										<div
											className="flex items-center gap-2 py-2 px-3 cursor-pointer select-none sticky z-10"
											style={{
												left: 0,
												borderBottom: `1px solid ${lineColor}`,
												background: dark ? "rgba(15,23,36,0.85)" : "#f1f5f9",
												color: dark ? "#e8ecf2" : "inherit",
											}}
											onClick={() => toggleGroup(group.name)}
										>
											<span className={`text-xs ${dark ? "text-white/50" : "text-gray-400"}`}>{collapsed ? "▸" : "▾"}</span>
											<strong>{group.name}</strong>
											<span className={`text-xs ${dark ? "text-white/50" : "text-text-secondary"}`}>{group.tasks.length} 项</span>
										</div>
										{!collapsed && group.tasks.map((task) => (
											<FlatGanttRow
												key={task.id ?? task.name}
												task={task}
												scale={scale}
												dark={dark}
												rowBg={rowBg}
												rowHoverBg={rowHoverBg}
												lineColor={lineColor}
												sideTextStyle={sideTextStyle}
												highlightedTaskName={highlightedTaskName}
												onTaskClick={onTaskClick}
											/>
										))}
									</div>
								);
						  })
						: tasks.map((task) => (
							<FlatGanttRow
								key={task.id ?? task.name}
								task={task}
								scale={scale}
								dark={dark}
								rowBg={rowBg}
								rowHoverBg={rowHoverBg}
								lineColor={lineColor}
								sideTextStyle={sideTextStyle}
								highlightedTaskName={highlightedTaskName}
								onTaskClick={onTaskClick}
							/>
						))}
				</div>
			</div>
		</div>
	);
}

function FlatGridHeader({ dark, headerBg, lineColor }: {
	dark?: boolean;
	headerBg: string;
	lineColor: string;
}) {
	const text = dark ? "text-white/70" : "text-text-secondary";
	return (
		<div
			className={`sticky left-0 z-30 flex-shrink-0 grid gap-3 items-center px-4 text-xs font-semibold ${text}`}
			style={{
				width: LEFT_GRID_WIDTH_FLAT,
				height: HEADER_HEIGHT,
				gridTemplateColumns: "1.4fr 0.9fr 0.9fr 0.9fr 0.7fr",
				background: headerBg,
				borderRight: `1px solid ${lineColor}`,
			}}
		>
			<span>任务</span>
			<span>责任人</span>
			<span>计划</span>
			<span>实际</span>
			<span className="text-right">延期</span>
		</div>
	);
}

const FlatGanttRow = memo(function FlatGanttRow({ task, scale, dark, rowBg, rowHoverBg, lineColor, sideTextStyle, highlightedTaskName, onTaskClick }: {
	task: ProjectGanttTask;
	scale: TimeScale;
	dark?: boolean;
	rowBg: string;
	rowHoverBg: string;
	lineColor: string;
	sideTextStyle?: { color: string };
	highlightedTaskName?: string;
	onTaskClick?: (task: ProjectGanttTask) => void;
}) {
	// Plan range (gray backdrop) — 计划开始 → 计划完成
	const planStart = toDateValue(task.planStartDate ?? task.planDate) ?? scale.start;
	const planEnd = toDateValue(task.planEndDate) ?? planStart;
	// Actual range (colored overlay) — 实际开始 → 实际完成
	const actualStart = toDateValue(task.actualStartDate);
	const actualEnd = toDateValue(task.actualDate);
	const hasActualStart = actualStart != null;
	const hasActualEnd = actualEnd != null;
	const isOngoing = hasActualStart && !hasActualEnd;
	const isUnstarted = !hasActualStart;
	// Effective actual bar: [actualStart, actualEnd || today]
	const actual = hasActualEnd ? actualEnd! : (hasActualStart ? Date.now() : planStart);
	const isMilestone = task.type === "里程碑节点";
	const isHighlighted = !!highlightedTaskName && highlightedTaskName === task.name;

	// Pixel positions against the current scale
	const planLeftPx = scale.toPx(planStart);
	const planWidthPx = scale.widthPx(planStart, planEnd);
	const actLeftPx = hasActualStart ? scale.toPx(actualStart!) : planLeftPx;
	const actWidthPx = hasActualStart
		? scale.widthPx(actualStart!, Math.max(actualStart!, actual))
		: 0;
	const todayPx = scale.toPx(Date.now());
	const showToday = todayPx >= 0 && todayPx <= scale.totalPx;

	const tone = task.riskLevel === "高" ? "high" : task.delayDays ? "warn" : "normal";
	const owner = String(task.owner ?? "").trim();
	// Convert px back to percent of track width just for the owner-label heuristic
	const leftPctForOwner = (actLeftPx / Math.max(1, scale.totalPx)) * 100;
	const widthPctForOwner = (actWidthPx / Math.max(1, scale.totalPx)) * 100;
	const ownerPlacement = owner
		? getGanttOwnerLabelPlacement(leftPctForOwner, widthPctForOwner)
		: "inside";

	const delayDays = task.delayDays ?? 0;
	const deviationLabel = delayDays > 0 ? `+${delayDays}天` : delayDays < 0 ? `${delayDays}天` : null;
	const deviationColor = delayDays > 0 ? "#dc2626" : "#16a34a";
	const rowInteractive = typeof onTaskClick === "function";
	const triggerTaskClick = () => {
		onTaskClick?.(task);
	};

	const barStyle: React.CSSProperties = {
		left: actLeftPx,
		width: Math.max(0, actWidthPx),
	};
	if (isUnstarted) {
		// 未开始 — 不显示实际条（只显示计划底色条）
		barStyle.display = "none";
	} else if (isOngoing) {
		barStyle.border = "2px dashed currentColor";
		barStyle.background = "none";
		barStyle.color = dark ? "rgba(255,255,255,0.5)" : "#6b7280";
		barStyle.boxShadow = "none";
	} else {
		barStyle.background = TONE_BG[tone];
	}
	if (isHighlighted && !isUnstarted) {
		barStyle.boxShadow = "0 0 0 3px rgba(239,68,68,0.5), 0 8px 16px rgba(15,23,42,0.12)";
	}

	const [hovered, setHovered] = useState(false);
	const effectiveBg = hovered && rowInteractive ? rowHoverBg : rowBg;

	return (
		<div
			className="flex"
			style={{
				height: FLAT_ROW_HEIGHT,
				borderBottom: `1px solid ${lineColor}`,
				cursor: rowInteractive ? "pointer" : undefined,
			}}
			role={rowInteractive ? "button" : undefined}
			tabIndex={rowInteractive ? 0 : undefined}
			onClick={rowInteractive ? triggerTaskClick : undefined}
			onMouseEnter={() => setHovered(true)}
			onMouseLeave={() => setHovered(false)}
			onKeyDown={rowInteractive ? (event) => {
				if (event.key === "Enter" || event.key === " ") {
					event.preventDefault();
					triggerTaskClick();
				}
			} : undefined}
		>
			{/* Sticky left grid cell */}
			<div
				className="sticky left-0 z-10 flex-shrink-0 grid gap-3 items-center px-4"
				style={{
					width: LEFT_GRID_WIDTH_FLAT,
					gridTemplateColumns: "1.4fr 0.9fr 0.9fr 0.9fr 0.7fr",
					background: effectiveBg,
					borderRight: `1px solid ${lineColor}`,
				}}
			>
				<div className={`flex flex-col gap-0.5 min-w-0 text-xs ${dark ? "text-white/85" : "text-text-secondary"}`}>
					<strong className={`truncate text-[13px] ${dark ? "text-[#e8ecf2]" : "text-text-primary"}`} title={task.name}>{task.name}</strong>
					{task.subprojectName ? (
						<span className="truncate" title={task.subprojectName}>{task.subprojectName}</span>
					) : null}
				</div>
				<span className={`truncate text-xs ${dark ? "text-white/85" : "text-text-secondary"}`} title={owner || undefined}>
					{owner || "--"}
				</span>
				<span className="text-xs truncate" style={sideTextStyle} title="计划完成日期">{task.planEndDate || task.planDate || "--"}</span>
				<span className="text-xs truncate" style={sideTextStyle} title="实际完成日期">{task.actualDate || (task.actualStartDate ? "进行中" : "未开始")}</span>
				<span className="text-xs text-right" style={deviationLabel ? { color: deviationColor, fontWeight: 600 } : sideTextStyle}>
					{deviationLabel ?? "--"}
				</span>
			</div>

			{/* Right side: timeline track */}
			<div
				className="relative flex-shrink-0"
				style={{ width: scale.totalPx, background: effectiveBg }}
			>
				<GanttTrackGrid scale={scale} dark={dark} />
				{/* Plan bar (gray backdrop, 计划开始→计划完成) */}
				{!isMilestone && planEnd >= planStart && (
					<div
						className="absolute rounded-full"
						style={{
							left: planLeftPx,
							width: Math.max(2, planWidthPx),
							top: FLAT_ROW_HEIGHT / 2 - 12,
							height: 24,
							background: dark ? "rgba(148,163,184,0.30)" : "#e5e7eb",
							border: dark ? "1px dashed rgba(148,163,184,0.6)" : "1px dashed #94a3b8",
							zIndex: 1,
						}}
						title={`计划: ${task.planStartDate ?? task.planDate ?? ""} → ${task.planEndDate ?? ""}`}
					/>
				)}
				{isMilestone ? (
					<div
						className="absolute z-[2]"
						style={{
							left: actLeftPx,
							top: "50%",
							width: 16,
							height: 16,
							transform: "translate(-50%, -50%) rotate(45deg)",
							background: TONE_BG[tone],
							boxShadow: isHighlighted
								? "0 0 0 3px rgba(239,68,68,0.5), 0 4px 8px rgba(15,23,42,0.18)"
								: "0 4px 8px rgba(15,23,42,0.18)",
							border: dark ? "1px solid rgba(255,255,255,0.6)" : "1px solid rgba(15,23,42,0.3)",
						}}
						title={`里程碑: ${task.name}`}
					/>
				) : (
					<>
						{/* Actual bar */}
						<div
							className="absolute inline-flex items-center justify-center px-2.5 rounded-full text-white text-xs whitespace-nowrap overflow-hidden shadow-[0_6px_14px_rgba(15,23,42,0.12)] z-[2]"
							style={{
								...barStyle,
								top: FLAT_ROW_HEIGHT / 2 - 12,
								height: 24,
							}}
							title={owner || undefined}
						>
							{owner && ownerPlacement === "inside" && actWidthPx > 40 ? (
								<span className="text-xs font-bold tracking-wide text-white/[0.98] [text-shadow:0_1px_2px_rgba(15,23,42,0.28)]">{owner}</span>
							) : null}
						</div>
						{/* Owner outside label */}
						{owner && ownerPlacement !== "inside" ? (
							<span
								className={`absolute z-[3] max-w-24 px-2 py-0.5 border rounded-full whitespace-nowrap overflow-hidden text-ellipsis text-xs font-bold tracking-wide pointer-events-none ${dark ? `shadow-[0_8px_18px_rgba(0,0,0,0.35)] ${OWNER_TONE_DARK[tone]}` : `shadow-[0_8px_18px_rgba(15,23,42,0.14)] bg-white/[0.98] ${OWNER_TONE_LIGHT[tone]}`}`}
								style={{
									top: FLAT_ROW_HEIGHT / 2,
									left: ownerPlacement === "outside-left" ? actLeftPx : actLeftPx + actWidthPx,
									transform: ownerPlacement === "outside-left"
										? "translate(calc(-100% - 6px), -50%)"
										: "translate(6px, -50%)",
								}}
								title={owner}
							>
								{owner}
							</span>
						) : null}
					</>
				)}
				{showToday ? (
					<div
						className="absolute top-0 bottom-0 w-0.5 bg-red-500/60 pointer-events-none z-[4]"
						style={{ left: todayPx }}
					/>
				) : null}
			</div>
		</div>
	);
});

// ──────────────────────────────────────────────────────────────────────────
// HierarchicalGantt — same two-pane layout, project-level rows
// ──────────────────────────────────────────────────────────────────────────

function HierarchicalGantt({ majorProjects, onProjectClick, maxHeight, dark, sideTextColor }: {
	majorProjects: MajorProject[];
	onProjectClick?: (project: MajorProject) => void;
	maxHeight?: number;
	dark?: boolean;
	sideTextColor?: string;
}) {
	const sideTextStyle = resolveGanttSideTextStyle(sideTextColor);

	const allDates = useMemo(() => (
		majorProjects
			.flatMap((p) =>
				p.subprojects.flatMap((sp) =>
					sp.tasks.flatMap((t) => [
						toDateValue(t.planStartDate ?? t.planDate),
						toDateValue(t.planEndDate),
						toDateValue(t.actualStartDate),
						toDateValue(t.actualDate),
					])
				)
			)
			.filter((value): value is number => value != null)
	), [majorProjects]);

	const { zoom, setZoom, scale } = useGanttTimeScale(allDates);

	if (majorProjects.length === 0) {
		return <EmptyShell dark={dark} message="当前筛选范围暂无重大项目。" />;
	}
	if (!scale) {
		return <EmptyShell dark={dark} message="缺少计划日期，无法渲染甘特视图。" />;
	}

	const headerBg = dark ? "rgba(15,23,36,0.95)" : "#f8fafc";
	const rowBg = dark ? "rgba(15,23,36,0.6)" : "#ffffff";
	const rowHoverBg = dark ? "rgba(59,130,246,0.1)" : "rgba(37,99,235,0.05)";
	const lineColor = dark ? "rgba(148,163,184,0.16)" : "rgba(148,163,184,0.22)";

	const meta = `${majorProjects.length} 个重大项目 · ${formatDateShort(scale.start)} ~ ${formatDateShort(scale.end)}`;

	return (
		<div className="flex flex-col">
			<GanttZoomToolbar zoom={zoom} setZoom={setZoom} dark={dark} meta={meta} />
			<div
				className="rounded-xl overflow-auto border"
				style={{
					maxHeight: maxHeight ?? 520,
					borderColor: lineColor,
					background: dark ? "rgba(15,23,36,0.4)" : "#fff",
				}}
			>
				<div style={{ minWidth: "100%", width: LEFT_GRID_WIDTH_HIER + scale.totalPx }}>
					{/* Sticky header */}
					<div
						className="flex sticky top-0 z-20"
						style={{ background: headerBg, borderBottom: `1px solid ${lineColor}`, height: HEADER_HEIGHT }}
					>
						<HierGridHeader dark={dark} headerBg={headerBg} lineColor={lineColor} />
						<GanttTimelineHeader scale={scale} dark={dark} />
					</div>

					{majorProjects.map((project) => (
						<HierGanttRow
							key={project.name}
							project={project}
							scale={scale}
							dark={dark}
							rowBg={rowBg}
							rowHoverBg={rowHoverBg}
							lineColor={lineColor}
							sideTextStyle={sideTextStyle}
							onProjectClick={onProjectClick}
						/>
					))}
				</div>
			</div>
		</div>
	);
}

function HierGridHeader({ dark, headerBg, lineColor }: {
	dark?: boolean;
	headerBg: string;
	lineColor: string;
}) {
	const text = dark ? "text-white/70" : "text-text-secondary";
	return (
		<div
			className={`sticky left-0 z-30 flex-shrink-0 grid gap-3 items-center px-4 text-xs font-semibold ${text}`}
			style={{
				width: LEFT_GRID_WIDTH_HIER,
				height: HEADER_HEIGHT,
				gridTemplateColumns: "1.5fr 1fr 0.7fr 0.8fr",
				background: headerBg,
				borderRight: `1px solid ${lineColor}`,
			}}
		>
			<span>重大项目</span>
			<span>责任科室 / 经理</span>
			<span className="text-right">完成率</span>
			<span className="text-right">交付日期</span>
		</div>
	);
}

function HierGanttRow({ project, scale, dark, rowBg, rowHoverBg, lineColor, sideTextStyle, onProjectClick }: {
	project: MajorProject;
	scale: TimeScale;
	dark?: boolean;
	rowBg: string;
	rowHoverBg: string;
	lineColor: string;
	sideTextStyle?: { color: string };
	onProjectClick?: (project: MajorProject) => void;
}) {
	const allTasks = project.subprojects.flatMap((sp) => sp.tasks);
	const dates = allTasks
		.map((t) => toDateValue(t.planDate))
		.filter((v): v is number => v != null);

	const interactive = typeof onProjectClick === "function";
	const triggerClick = () => {
		onProjectClick?.(project);
	};
	const [hovered, setHovered] = useState(false);
	const effectiveBg = hovered && interactive ? rowHoverBg : rowBg;

	const tone = (project.kpi?.highRiskCount ?? 0) > 0
		? "high"
		: (project.kpi?.delayDays ?? 0) > 0
			? "warn"
			: "normal";

	// Compute bar position only if we have data
	let leftPx = 0;
	let widthPx = 0;
	let hasBar = false;
	if (dates.length > 0) {
		const { min: projectStart, max: projectEnd } = minMax(dates);
		leftPx = scale.toPx(projectStart);
		widthPx = scale.widthPx(projectStart, projectEnd);
		hasBar = true;
	}

	const todayPx = scale.toPx(Date.now());
	const showToday = todayPx >= 0 && todayPx <= scale.totalPx;

	return (
		<div
			className="flex"
			style={{
				height: HIER_ROW_HEIGHT,
				borderBottom: `1px solid ${lineColor}`,
				cursor: interactive ? "pointer" : undefined,
			}}
			role={interactive ? "button" : undefined}
			tabIndex={interactive ? 0 : undefined}
			onClick={interactive ? triggerClick : undefined}
			onMouseEnter={() => setHovered(true)}
			onMouseLeave={() => setHovered(false)}
			onKeyDown={interactive ? (event) => {
				if (event.key === "Enter" || event.key === " ") {
					event.preventDefault();
					triggerClick();
				}
			} : undefined}
		>
			{/* Sticky left grid cell */}
			<div
				className="sticky left-0 z-10 flex-shrink-0 grid gap-3 items-center px-4"
				style={{
					width: LEFT_GRID_WIDTH_HIER,
					gridTemplateColumns: "1.5fr 1fr 0.7fr 0.8fr",
					background: effectiveBg,
					borderRight: `1px solid ${lineColor}`,
				}}
			>
				<div className={`flex flex-col gap-0.5 min-w-0 text-xs ${dark ? "text-white/85" : "text-text-secondary"}`}>
					<strong className={`truncate text-[14px] ${dark ? "text-[#e8ecf2]" : "text-text-primary"}`} title={project.name}>{project.name}</strong>
					<span className="truncate">
						{project.subprojects.length} 子项目 · {allTasks.length} 任务
					</span>
				</div>
				<span className={`truncate text-xs ${dark ? "text-white/85" : "text-text-secondary"}`}>
					{project.responsibleDept ?? ""}{project.manager ? ` · ${project.manager}` : ""}
				</span>
				<span className="text-right text-[18px] font-bold" style={sideTextStyle}>
					{project.kpi?.completionRate ?? 0}%
				</span>
				<span className="text-right text-xs" style={sideTextStyle}>
					{project.plannedDeliveryDate ?? "--"}
				</span>
			</div>

			{/* Right side: timeline track */}
			<div
				className="relative flex-shrink-0"
				style={{ width: scale.totalPx, background: effectiveBg }}
			>
				<GanttTrackGrid scale={scale} dark={dark} />
				{hasBar ? (
					<div
						className="absolute inline-flex items-center justify-center px-3 rounded-full text-white text-xs whitespace-nowrap overflow-hidden shadow-[0_6px_14px_rgba(15,23,42,0.12)] z-[2]"
						style={{
							left: leftPx,
							width: widthPx,
							top: HIER_ROW_HEIGHT / 2 - 14,
							height: 28,
							background: TONE_BG[tone],
						}}
					>
						{widthPx > 120
							? `${project.subprojects.length} 子项目 · ${allTasks.length} 任务`
							: widthPx > 48
								? `${allTasks.length} 任务`
								: ""}
					</div>
				) : null}
				{showToday ? (
					<div
						className="absolute top-0 bottom-0 w-0.5 bg-red-500/60 pointer-events-none z-[4]"
						style={{ left: todayPx }}
					/>
				) : null}
			</div>
		</div>
	);
}

function EmptyShell({ dark, message }: { dark?: boolean; message: string }) {
	return (
		<div
			className={`flex items-center justify-center min-h-[220px] border border-dashed rounded-2xl ${dark ? "border-slate-400/20 bg-slate-900/60 text-white/50" : "border-border-default bg-surface-muted text-text-secondary"}`}
		>
			{message}
		</div>
	);
}
