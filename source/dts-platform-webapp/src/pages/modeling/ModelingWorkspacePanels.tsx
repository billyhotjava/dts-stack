import { Button, Empty, Spin } from "antd";
import { ArrowRight, FileInput, FileOutput, FileSearch, TerminalSquare } from "lucide-react";
import { lazy, type ReactNode, Suspense } from "react";
import { metricViewFromWorkspace, workspaceViewFromMetric } from "./metricWorkbenchNavigation";
import type {
	ModelingWorkspaceAssetKind,
	ModelingWorkspaceModelStage,
	ModelingWorkspaceModule,
	ModelingWorkspaceView,
} from "./modelingWorkspaceRouteState";

const WarehousePlanDetailPage = lazy(() => import("./WarehousePlanDetailPage"));
const ElementsPage = lazy(() => import("../governance/ElementsPage"));
const ReferenceCodesPage = lazy(() => import("../governance/ReferenceCodesPage"));
const GlossaryPage = lazy(() => import("../governance/GlossaryPage"));
const MeasurementUnitsPage = lazy(() => import("../governance/MeasurementUnitsPage"));
const DimensionCatalogPage = lazy(() => import("./DimensionCatalogPage"));
const ModelCenterPage = lazy(() => import("./ModelCenterPage"));
const ModelSpecDetailPage = lazy(() => import("./ModelSpecDetailPage"));
const MetricWorkbenchPage = lazy(() => import("./MetricWorkbenchPage"));
const RelationshipGraphPanel = lazy(() => import("./RelationshipGraphPanel"));

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
	metrics: [
		{ key: "definitions", label: "指标定义" },
		{ key: "model", label: "模型生成" },
		{ key: "templates", label: "模板复用" },
		{ key: "consumption", label: "运行消费" },
	],
	tools: [{ key: "utilities", label: "建模工具" }],
	graph: [{ key: "relationships", label: "资产关系" }],
};

const defaultView = (module: ModelingWorkspaceModule): ModelingWorkspaceView | undefined =>
	PANEL_VIEWS[module]?.[0]?.key;

const loading = (
	<output className="grid min-h-[320px] place-items-center" aria-label="模块加载中">
		<Spin />
	</output>
);

type ModelingWorkspacePanelsProps = {
	module: Exclude<ModelingWorkspaceModule, "home">;
	planId?: string;
	planOptions?: Array<{ value: string; label: string }>;
	canImportModels: boolean;
	workspaceView?: ModelingWorkspaceView;
	assetKind?: ModelingWorkspaceAssetKind;
	assetId?: string;
	onViewChange: (view: ModelingWorkspaceView) => void;
	onNavigate: (route: string) => void;
	onOpenModelImport: () => void;
	onOpenModel: (modelSpecId: string, planId?: string) => void;
	onOpenIndicator: (indicatorId?: string) => void;
	onCloseModel: () => void;
	onModelStageChange: (stage: ModelingWorkspaceModelStage) => void;
	onModelResolvedContext: (context: { modelSpecId: string; planId: string }) => void;
};

type PlanningView = "overview" | "categories" | "data-marts" | "layers" | "sources";

const isPlanningView = (view: ModelingWorkspaceView): view is PlanningView =>
	["overview", "categories", "data-marts", "layers", "sources"].includes(view);

const Canonical = ({ children }: { children: ReactNode }) => <Suspense fallback={loading}>{children}</Suspense>;

function ToolPanel({
	onNavigate,
	onOpenModelImport,
}: Pick<ModelingWorkspacePanelsProps, "onNavigate" | "onOpenModelImport">) {
	const tools = [
		{ label: "SQL/dbt 建模", detail: "进入专业代码工作区", route: "/studio/sql-modeling", icon: TerminalSquare },
		{
			label: "导入模型包",
			detail: "复用建设计划导入向导",
			onClick: onOpenModelImport,
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
						key={tool.route || tool.label}
						type="button"
						className="flex min-h-24 items-center gap-4 rounded-lg border border-slate-200 bg-white px-4 text-left transition-colors hover:border-blue-300 hover:bg-blue-50/40"
						onClick={tool.onClick || (() => onNavigate(tool.route))}
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
	props: ModelingWorkspacePanelsProps,
) {
	const {
		assetId,
		assetKind,
		canImportModels,
		onCloseModel,
		onModelResolvedContext,
		onModelStageChange,
		onNavigate,
		onOpenIndicator,
		onOpenModel,
		onOpenModelImport,
		onViewChange,
		planId,
		planOptions,
	} = props;
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
		if (view === "model-specs" && assetKind === "model" && assetId) {
			return (
				<Canonical>
					<ModelSpecDetailPage
						embedded
						modelSpecIdOverride={assetId}
						onBack={onCloseModel}
						onStageChange={onModelStageChange}
						onResolvedContext={onModelResolvedContext}
					/>
				</Canonical>
			);
		}
		return view === "model-specs" ? (
			<Canonical>
				<ModelCenterPage
					embedded
					planIdOverride={planId}
					planOptionsOverride={planOptions}
					canImportOverride={canImportModels}
					onOpenModel={onOpenModel}
					onOpenModelImport={onOpenModelImport}
				/>
			</Canonical>
		) : (
			<Canonical>
				<DimensionCatalogPage selectedDimensionIdOverride={assetKind === "dimension" ? assetId : undefined} />
			</Canonical>
		);
	}
	if (module === "metrics")
		return (
			<Canonical>
				<MetricWorkbenchPage
					embedded
					activeViewOverride={metricViewFromWorkspace(view)}
					onViewChange={(metricView) => onViewChange(workspaceViewFromMetric(metricView))}
					onOpenModel={(modelSpecId) => onOpenModel(modelSpecId, planId)}
					indicatorIdOverride={assetKind === "indicator" ? assetId : undefined}
					onSelectedIndicatorChange={onOpenIndicator}
				/>
			</Canonical>
		);
	if (module === "graph")
		return (
			<Canonical>
				<RelationshipGraphPanel planId={planId} onNavigate={onNavigate} />
			</Canonical>
		);
	return <ToolPanel onNavigate={onNavigate} onOpenModelImport={onOpenModelImport} />;
}

export function ModelingWorkspacePanels(props: ModelingWorkspacePanelsProps) {
	const { module, onViewChange, workspaceView } = props;
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
			<main className="min-w-0 overflow-x-hidden bg-white">{activePanel(module, selectedView, props)}</main>
		</div>
	);
}
