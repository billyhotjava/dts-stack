import { Button, Empty, Spin } from "antd";
import { ArrowRight, FileInput, FileOutput, FileSearch, TerminalSquare } from "lucide-react";
import { lazy, Suspense, type ReactNode } from "react";
import type { ModelingWorkspaceModule, ModelingWorkspaceView } from "./modelingWorkspaceRouteState";

const WarehousePlanDetailPage = lazy(() => import("./WarehousePlanDetailPage"));
const ElementsPage = lazy(() => import("../governance/ElementsPage"));
const ReferenceCodesPage = lazy(() => import("../governance/ReferenceCodesPage"));
const GlossaryPage = lazy(() => import("../governance/GlossaryPage"));
const MeasurementUnitsPage = lazy(() => import("../governance/MeasurementUnitsPage"));
const DimensionCatalogPage = lazy(() => import("./DimensionCatalogPage"));
const ModelCenterPage = lazy(() => import("./ModelCenterPage"));
const MetricWorkbenchPage = lazy(() => import("./MetricWorkbenchPage"));
const LineageGraphPage = lazy(() => import("../catalog/LineageGraphPage"));

type PanelView = {
	key: ModelingWorkspaceView;
	label: string;
};

const PANEL_VIEWS: Partial<Record<ModelingWorkspaceModule, PanelView[]>> = {
	planning: [
		{ key: "overview", label: "规划概览" },
		{ key: "categories", label: "业务分类" },
		{ key: "data-marts", label: "数据集市" },
		{ key: "layers", label: "数仓分层" },
		{ key: "sources", label: "来源盘点" },
	],
	standards: [
		{ key: "elements", label: "数据元" },
		{ key: "reference", label: "公共码表" },
		{ key: "glossary", label: "业务术语" },
		{ key: "units", label: "计量单位" },
	],
	models: [
		{ key: "dimensions", label: "业务维度" },
		{ key: "model-specs", label: "逻辑模型" },
	],
	metrics: [{ key: "definitions", label: "指标工作台" }],
	tools: [{ key: "utilities", label: "建模工具" }],
	graph: [{ key: "relationships", label: "资产关系" }],
};

const defaultView = (module: ModelingWorkspaceModule): ModelingWorkspaceView | undefined =>
	PANEL_VIEWS[module]?.[0]?.key;

const loading = (
	<div className="grid min-h-[320px] place-items-center" aria-label="模块加载中">
		<Spin />
	</div>
);

type ModelingWorkspacePanelsProps = {
	module: Exclude<ModelingWorkspaceModule, "home">;
	planId?: string;
	workspaceView?: ModelingWorkspaceView;
	onViewChange: (view: ModelingWorkspaceView) => void;
	onNavigate: (route: string) => void;
};

type PlanningView = "overview" | "categories" | "data-marts" | "layers" | "sources";

const isPlanningView = (view: ModelingWorkspaceView): view is PlanningView =>
	["overview", "categories", "data-marts", "layers", "sources"].includes(view);

const Canonical = ({ children }: { children: ReactNode }) => <Suspense fallback={loading}>{children}</Suspense>;

function ToolPanel({ onNavigate }: Pick<ModelingWorkspacePanelsProps, "onNavigate">) {
	const tools = [
		{ label: "SQL/dbt 建模", detail: "进入专业代码工作区", route: "/studio/sql-modeling", icon: TerminalSquare },
		{
			label: "导入模型包",
			detail: "复用建设计划导入向导",
			route: "/modeling/workbench?modelImport=open",
			icon: FileInput,
		},
		{ label: "模型中心", detail: "导出与版本由模型 owner 管理", route: "/modeling/models", icon: FileOutput },
		{ label: "元数据盘点", detail: "从资产目录核对来源", route: "/catalog/metadata-management", icon: FileSearch },
	];
	return (
		<div className="grid gap-3 p-4 md:grid-cols-2">
			{tools.map((tool) => {
				const Icon = tool.icon;
				return (
					<button
						key={tool.route}
						type="button"
						className="flex min-h-24 items-center gap-4 rounded-lg border border-slate-200 bg-white px-4 text-left transition-colors hover:border-blue-300 hover:bg-blue-50/40"
						onClick={() => onNavigate(tool.route)}
					>
						<span className="grid size-10 shrink-0 place-items-center rounded-md bg-slate-100 text-slate-700">
							<Icon size={19} aria-hidden="true" />
						</span>
						<span className="min-w-0">
							<span className="block font-medium text-slate-950">{tool.label}</span>
							<span className="mt-1 block text-xs text-slate-500">{tool.detail}</span>
						</span>
						<ArrowRight className="ml-auto shrink-0 text-slate-400" size={16} aria-hidden="true" />
					</button>
				);
			})}
		</div>
	);
}

function activePanel(
	module: ModelingWorkspacePanelsProps["module"],
	view: ModelingWorkspaceView,
	planId: string | undefined,
	onViewChange: ModelingWorkspacePanelsProps["onViewChange"],
	onNavigate: ModelingWorkspacePanelsProps["onNavigate"],
) {
	if (module === "planning") {
		if (!planId) {
			return (
				<div className="grid min-h-[360px] place-items-center">
					<Empty description="请先在顶部选择建设计划" />
				</div>
			);
		}
		const planningView: PlanningView = isPlanningView(view) ? view : "overview";
		return (
			<Canonical>
				<WarehousePlanDetailPage
					embedded
					planIdOverride={planId}
					sectionOverride={planningView === "overview" ? "overview" : "baseline"}
					baselineTabOverride={planningView === "overview" ? undefined : planningView}
					onWorkspaceViewChange={onViewChange}
				/>
			</Canonical>
		);
	}
	if (module === "standards") {
		if (view === "reference")
			return (
				<Canonical>
					<ReferenceCodesPage />
				</Canonical>
			);
		if (view === "glossary")
			return (
				<Canonical>
					<GlossaryPage />
				</Canonical>
			);
		if (view === "units")
			return (
				<Canonical>
					<MeasurementUnitsPage />
				</Canonical>
			);
		return (
			<Canonical>
				<ElementsPage embedded />
			</Canonical>
		);
	}
	if (module === "models") {
		return view === "model-specs" ? (
			<Canonical>
				<ModelCenterPage />
			</Canonical>
		) : (
			<Canonical>
				<DimensionCatalogPage />
			</Canonical>
		);
	}
	if (module === "metrics")
		return (
			<Canonical>
				<MetricWorkbenchPage />
			</Canonical>
		);
	if (module === "graph")
		return (
			<Canonical>
				<LineageGraphPage />
			</Canonical>
		);
	return <ToolPanel onNavigate={onNavigate} />;
}

export function ModelingWorkspacePanels({
	module,
	planId,
	workspaceView,
	onViewChange,
	onNavigate,
}: ModelingWorkspacePanelsProps) {
	const views = PANEL_VIEWS[module] || [];
	const selectedView = views.find((item) => item.key === workspaceView)?.key || defaultView(module) || "overview";

	return (
		<div
			className="grid min-h-[460px] grid-cols-1 bg-slate-50/40 md:grid-cols-[168px_minmax(0,1fr)]"
			data-testid="modeling-workspace-canonical-panel"
		>
			<nav
				className="flex gap-1 overflow-x-auto border-b border-slate-200 bg-white p-2 md:flex-col md:border-r md:border-b-0 md:p-3"
				aria-label="当前模块功能"
			>
				{views.map((item) => {
					const active = item.key === selectedView;
					return (
						<Button
							key={item.key}
							type={active ? "primary" : "text"}
							className="shrink-0 justify-start md:w-full"
							aria-current={active ? "page" : undefined}
							onClick={() => onViewChange(item.key)}
						>
							{item.label}
						</Button>
					);
				})}
			</nav>
			<main className="min-w-0 overflow-x-hidden bg-white">
				{activePanel(module, selectedView, planId, onViewChange, onNavigate)}
			</main>
		</div>
	);
}
