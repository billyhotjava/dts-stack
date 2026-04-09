// @ts-nocheck — relies on @ts-nocheck'd ProjectGanttBoard, JSX-heavy modal
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import ReactDOM from "react-dom";
import type { CardData, MajorProject, MajorProjectRisk } from "../../screens/types";
import { aggregateMajorProjects, type FlatProjectNodeRow } from "../utils/majorProjectAggregator";
import { ProjectGanttBoard, type ProjectGanttTask } from "./ProjectGanttBoard";
import "./ProjectDetailGanttModal.css";

interface Props {
	project: MajorProject | null;
	onClose: () => void;
}

/**
 * Wrapper that combines:
 *  - hierarchical ProjectGanttBoard rendering one row per major project
 *  - ProjectDetailGanttModal that opens on row click
 *
 * Used by EChartsRenderer when a gantt-chart's renderMode is 'board-hierarchical'.
 * Accepts CardData (SQL query result) and converts the flat rows into MajorProject[].
 */
export function BoardHierarchicalGanttWithModal({
	cardData,
	maxHeight,
	dark,
	sideTextColor,
}: {
	cardData: CardData | null | undefined;
	maxHeight?: number;
	dark?: boolean;
	sideTextColor?: string;
}) {
	const [activeProject, setActiveProject] = useState<MajorProject | null>(null);

	const majorProjects = useMemo(() => {
		if (!cardData?.rows?.length || !cardData.cols?.length) return [];
		const flatRows = buildFlatRowsFromCardData(cardData);
		return aggregateMajorProjects(flatRows);
	}, [cardData]);

	// Stable identity so child useEffect deps don't churn on each parent re-render
	const handleClose = useCallback(() => setActiveProject(null), []);

	return (
		<>
			<ProjectGanttBoard
				renderMode="hierarchical"
				majorProjects={majorProjects}
				maxHeight={maxHeight}
				dark={dark}
				sideTextColor={sideTextColor}
				onProjectClick={setActiveProject}
			/>
			<ProjectDetailGanttModal
				project={activeProject}
				onClose={handleClose}
			/>
		</>
	);
}

/**
 * Build FlatProjectNodeRow[] from CardData {rows, cols} where cols.name uses
 * Chinese aliases matching the SQL query in F6-T01 (e.g. "重大项目", "子项目").
 * Unrecognized columns are silently ignored; missing columns map to undefined.
 */
function buildFlatRowsFromCardData(cardData: CardData): FlatProjectNodeRow[] {
	const cols = cardData.cols ?? [];
	const colIndex = (name: string) => cols.findIndex((c) => c.name === name);
	const indices = {
		重大项目: colIndex("重大项目"),
		子项目: colIndex("子项目"),
		任务: colIndex("任务"),
		类型: colIndex("类型"),
		计划日期: colIndex("计划日期"),
		实际日期: colIndex("实际日期"),
		基线日期: colIndex("基线日期"),
		是否完成: colIndex("是否完成"),
		是否超期完成: colIndex("是否超期完成"),
		是否未完成: colIndex("是否未完成"),
		延期天数: colIndex("延期天数"),
		风险等级: colIndex("风险等级"),
		完成情况: colIndex("完成情况"),
		责任科室: colIndex("责任科室"),
		责任人: colIndex("责任人"),
		项目经理: colIndex("项目经理"),
		所长: colIndex("所长"),
		风险内容: colIndex("风险内容"),
		延期影响: colIndex("延期影响"),
	};

	const pickStr = (row: unknown[], idx: number): string | null => {
		if (idx < 0) return null;
		const v = row[idx];
		return v == null ? null : String(v);
	};
	const pickBool = (row: unknown[], idx: number): boolean | null => {
		if (idx < 0) return null;
		const v = row[idx];
		if (v == null) return null;
		if (typeof v === "boolean") return v;
		if (typeof v === "number") return v !== 0;
		const s = String(v).trim().toLowerCase();
		if (s === "true" || s === "t" || s === "1" || s === "yes") return true;
		if (s === "false" || s === "f" || s === "0" || s === "no") return false;
		return null;
	};
	const pickNum = (row: unknown[], idx: number): number | null => {
		if (idx < 0) return null;
		const v = row[idx];
		if (v == null) return null;
		const n = Number(v);
		return Number.isFinite(n) ? n : null;
	};

	return cardData.rows.map((row): FlatProjectNodeRow => ({
		重大项目: pickStr(row, indices.重大项目),
		子项目: pickStr(row, indices.子项目),
		任务: pickStr(row, indices.任务),
		类型: pickStr(row, indices.类型),
		计划日期: pickStr(row, indices.计划日期),
		实际日期: pickStr(row, indices.实际日期),
		基线日期: pickStr(row, indices.基线日期),
		是否完成: pickBool(row, indices.是否完成),
		是否超期完成: pickBool(row, indices.是否超期完成),
		是否未完成: pickBool(row, indices.是否未完成),
		延期天数: pickNum(row, indices.延期天数),
		风险等级: pickStr(row, indices.风险等级),
		完成情况: pickStr(row, indices.完成情况),
		责任科室: pickStr(row, indices.责任科室),
		责任人: pickStr(row, indices.责任人),
		项目经理: pickStr(row, indices.项目经理),
		所长: pickStr(row, indices.所长),
		风险内容: pickStr(row, indices.风险内容),
		延期影响: pickStr(row, indices.延期影响),
	}));
}

export function ProjectDetailGanttModal({ project, onClose }: Props) {
	const [highlightedTask, setHighlightedTask] = useState<string | undefined>(undefined);
	const [activeSub, setActiveSub] = useState<string>("全部");
	const highlightTimerRef = useRef<number | null>(null);

	// Reset chip + highlight whenever the project changes (depend on project only,
	// NOT onClose — otherwise an unstable parent callback would reset state on every
	// parent re-render mid-modal-open and lose the user's chip selection).
	useEffect(() => {
		if (!project) return;
		setActiveSub("全部");
		setHighlightedTask(undefined);
		if (highlightTimerRef.current != null) {
			window.clearTimeout(highlightTimerRef.current);
			highlightTimerRef.current = null;
		}
	}, [project]);

	// ESC handler — separate effect so onClose changes don't reset chip state.
	useEffect(() => {
		if (!project) return;
		const onKey = (e: KeyboardEvent) => {
			if (e.key === "Escape") onClose();
		};
		window.addEventListener("keydown", onKey);
		return () => window.removeEventListener("keydown", onKey);
	}, [project, onClose]);

	// Cleanup any pending highlight timer on unmount.
	useEffect(() => {
		return () => {
			if (highlightTimerRef.current != null) {
				window.clearTimeout(highlightTimerRef.current);
				highlightTimerRef.current = null;
			}
		};
	}, []);

	const allTasks: ProjectGanttTask[] = useMemo(() => {
		if (!project) return [];
		return project.subprojects.flatMap((sp) =>
			sp.tasks.map((t) => ({ ...t, subprojectName: sp.name }))
		);
	}, [project]);

	const visibleTasks: ProjectGanttTask[] = useMemo(() => {
		if (activeSub === "全部") return allTasks;
		return allTasks.filter((t) => t.subprojectName === activeSub);
	}, [allTasks, activeSub]);

	if (!project) return null;

	const handleRiskClick = (taskRef: string | undefined) => {
		if (!taskRef) return;
		// Clear any in-flight timer so a quick second click doesn't get prematurely
		// faded by the previous click's pending timer.
		if (highlightTimerRef.current != null) {
			window.clearTimeout(highlightTimerRef.current);
			highlightTimerRef.current = null;
		}
		setHighlightedTask(taskRef);
		highlightTimerRef.current = window.setTimeout(() => {
			setHighlightedTask(undefined);
			highlightTimerRef.current = null;
		}, 1200);
	};

	return ReactDOM.createPortal(
		<div className="pgm-modal-overlay" onClick={onClose}>
			<div className="pgm-modal-window" onClick={(e) => e.stopPropagation()}>
				<ModalHeader project={project} onClose={onClose} />
				<div className="pgm-modal-body">
					<ModalSidebar project={project} onRiskClick={handleRiskClick} />
					<ModalGanttArea
						project={project}
						visibleTasks={visibleTasks}
						activeSub={activeSub}
						onSubChange={setActiveSub}
						highlightedTaskName={highlightedTask}
					/>
				</div>
				<ModalFooter />
			</div>
		</div>,
		document.body
	);
}

function ModalHeader({ project, onClose }: { project: MajorProject; onClose: () => void }) {
	return (
		<div className="flex items-center justify-between px-6 h-14 border-b border-slate-200 flex-shrink-0">
			<div className="flex items-baseline gap-3">
				<h2 className="text-xl font-bold text-slate-900">{project.name}</h2>
				<span className="text-sm text-slate-500">
					{project.responsibleDept ?? ""}
					{project.manager ? ` · ${project.manager}` : ""}
				</span>
			</div>
			<button
				type="button"
				onClick={onClose}
				className="w-8 h-8 rounded-full flex items-center justify-center text-slate-500 hover:bg-slate-100"
				aria-label="关闭"
			>
				✕
			</button>
		</div>
	);
}

function ModalSidebar({ project, onRiskClick }: { project: MajorProject; onRiskClick: (taskRef: string | undefined) => void }) {
	const milestoneRatePct = project.kpi?.milestoneRate != null
		? Math.round(project.kpi.milestoneRate * 100)
		: undefined;
	return (
		<aside className="w-80 flex-shrink-0 border-r border-slate-200 overflow-y-auto p-5 flex flex-col gap-5">
			{/* 项目元信息 */}
			<section>
				<h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-2">项目信息</h3>
				<dl className="grid grid-cols-[80px_1fr] gap-y-1.5 text-xs">
					<dt className="text-slate-500">责任部门</dt>
					<dd className="font-semibold text-slate-700">{project.responsibleDept ?? "--"}</dd>
					<dt className="text-slate-500">项目经理</dt>
					<dd className="font-semibold text-slate-700">{project.manager ?? "--"}</dd>
					<dt className="text-slate-500">所长</dt>
					<dd className="font-semibold text-slate-700">{project.instituteLeader ?? "--"}</dd>
					<dt className="text-slate-500">启动</dt>
					<dd className="font-semibold text-slate-700">{project.startDate ?? "--"}</dd>
					<dt className="text-slate-500">计划交付</dt>
					<dd className="font-semibold text-slate-700">{project.plannedDeliveryDate ?? "--"}</dd>
					<dt className="text-slate-500">阶段</dt>
					<dd className="font-semibold text-slate-700">{project.stage ?? "--"}</dd>
				</dl>
			</section>

			{/* KPI */}
			<section>
				<h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-2">关键指标</h3>
				<div className="grid grid-cols-2 gap-2">
					<KpiMini label="完成率" value={project.kpi?.completionRate ?? 0} suffix="%" />
					<KpiMini
						label="里程碑"
						value={milestoneRatePct ?? "--"}
						suffix={milestoneRatePct != null ? "%" : ""}
					/>
					<KpiMini label="高风险" value={project.kpi?.highRiskCount ?? 0} suffix="项" />
					<KpiMini label="累计延期" value={project.kpi?.delayDays ?? 0} suffix="d" />
				</div>
			</section>

			{/* 风险列表 */}
			<section>
				<h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-2">风险与延期</h3>
				<ul className="flex flex-col gap-1.5">
					{(project.risks ?? []).length === 0 ? (
						<li className="text-xs text-slate-400">暂无风险项</li>
					) : (
						(project.risks ?? []).map((risk: MajorProjectRisk, i: number) => (
							<li
								key={`${risk.taskRef ?? "risk"}-${i}`}
								onClick={() => onRiskClick(risk.taskRef)}
								className="flex items-center gap-1.5 px-2 py-1.5 rounded text-xs cursor-pointer hover:bg-slate-100"
								role="button"
								tabIndex={0}
								onKeyDown={(e) => {
									if (e.key === "Enter" || e.key === " ") {
										e.preventDefault();
										onRiskClick(risk.taskRef);
									}
								}}
							>
								<span>{risk.level === "high" ? "🔴" : "🟡"}</span>
								<span className="flex-1 min-w-0 truncate text-slate-700">{risk.label}</span>
							</li>
						))
					)}
				</ul>
			</section>
		</aside>
	);
}

function KpiMini({ label, value, suffix }: { label: string; value: number | string; suffix?: string }) {
	return (
		<div className="bg-slate-50 rounded-lg p-2.5">
			<div className="text-[11px] text-slate-500">{label}</div>
			<div className="text-xl font-bold text-slate-900 leading-tight">
				{value}
				{suffix ? <span className="text-xs font-normal text-slate-500 ml-0.5">{suffix}</span> : null}
			</div>
		</div>
	);
}

function ModalGanttArea({
	project,
	visibleTasks,
	activeSub,
	onSubChange,
	highlightedTaskName,
}: {
	project: MajorProject;
	visibleTasks: ProjectGanttTask[];
	activeSub: string;
	onSubChange: (sub: string) => void;
	highlightedTaskName?: string;
}) {
	const subNames = ["全部", ...project.subprojects.map((sp) => sp.name)];
	return (
		<div className="flex-1 flex flex-col min-w-0 overflow-hidden">
			{/* Sub chips */}
			<div className="flex items-center gap-1.5 px-5 py-2 border-b border-slate-100 flex-shrink-0 flex-wrap">
				{subNames.map((name) => (
					<button
						key={name}
						type="button"
						onClick={() => onSubChange(name)}
						className={`px-2.5 py-1 rounded-full text-xs ${
							activeSub === name
								? "bg-blue-600 text-white"
								: "bg-slate-100 text-slate-600 hover:bg-slate-200"
						}`}
					>
						{name}
					</button>
				))}
			</div>
			{/* Gantt 主体 */}
			<div className="flex-1 overflow-y-auto px-5 py-3 min-h-0">
				<ProjectGanttBoard
					tasks={visibleTasks}
					renderMode="flat"
					highlightedTaskName={highlightedTaskName}
					dark={false}
				/>
			</div>
		</div>
	);
}

function ModalFooter() {
	return (
		<div className="flex items-center justify-center gap-5 h-11 border-t border-slate-200 bg-slate-50 flex-shrink-0 text-xs text-slate-500">
			<Legend color="#2563eb" label="计划" />
			<Legend color="#10b981" label="实际" />
			<Legend color="#cbd5e1" label="基线" />
			<Legend color="#f59e0b" label="里程碑" shape="diamond" />
			<Legend color="#ef4444" label="今日" shape="line" />
		</div>
	);
}

function Legend({ color, label, shape }: { color: string; label: string; shape?: "diamond" | "line" }) {
	return (
		<span className="flex items-center gap-1.5">
			{shape === "diamond" ? (
				<span className="w-2 h-2 rotate-45" style={{ background: color }} />
			) : shape === "line" ? (
				<span className="w-3 h-0.5" style={{ background: color }} />
			) : (
				<span className="w-3 h-2 rounded-sm" style={{ background: color }} />
			)}
			{label}
		</span>
	);
}
