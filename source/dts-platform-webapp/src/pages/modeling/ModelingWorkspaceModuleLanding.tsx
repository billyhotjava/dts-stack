import { ArrowRight, Boxes, ChartNoAxesCombined, Network, Ruler, Wrench } from "lucide-react";
import type { ModelingWorkspaceModule } from "./modelingWorkspaceRouteState";

type HandoffModule = Exclude<ModelingWorkspaceModule, "home" | "planning">;

type ModuleAction = {
	label: string;
	route: string;
};

type ModuleMeta = {
	kicker: string;
	title: string;
	description: string;
	icon: typeof Boxes;
	actions: ModuleAction[];
};

const MODULE_META: Record<HandoffModule, ModuleMeta> = {
	standards: {
		kicker: "Standards",
		title: "标准先行，模型字段只引用唯一标准事实",
		description: "维护数据元、公共码表和计量单位；模型字段映射继续由 canonical ModelSpec 持有。",
		icon: Ruler,
		actions: [
			{ label: "数据元", route: "/governance/standards/elements" },
			{ label: "公共码表", route: "/governance/standards/reference" },
			{ label: "计量单位", route: "/governance/standards/units" },
		],
	},
	models: {
		kicker: "Dimensional modeling",
		title: "业务维度与四类逻辑模型",
		description: "业务维度进入维度目录；维度表、明细表、汇总表和应用表进入统一模型中心。",
		icon: Boxes,
		actions: [
			{ label: "模型中心", route: "/modeling/models" },
			{ label: "业务维度", route: "/modeling/dimensions" },
		],
	},
	metrics: {
		kicker: "Metrics",
		title: "从已发布模型字段定义指标",
		description: "指标定义、版本和模型字段引用继续由指标 owner 维护，不在工作台复制台账。",
		icon: ChartNoAxesCombined,
		actions: [{ label: "指标工作台", route: "/modeling/metric-workbench" }],
	},
	tools: {
		kicker: "Tools",
		title: "专业 SQL/dbt 与模型交换工具",
		description: "保留高级建模能力，通过建设计划上下文进入，不把专业编辑器塞进主画布。",
		icon: Wrench,
		actions: [
			{ label: "SQL/dbt 建模", route: "/studio/sql-modeling" },
			{ label: "项目空间", route: "/studio/projects" },
		],
	},
	graph: {
		kicker: "Relationships",
		title: "查看规划、模型与物理资产关系",
		description: "关系图只读取依赖与血缘投影，不创建第二套关系事实。",
		icon: Network,
		actions: [{ label: "打开关系图", route: "/catalog/lineage/graph" }],
	},
};

type ModelingWorkspaceModuleLandingProps = {
	module: HandoffModule;
	planId?: string;
	onNavigate: (route: string) => void;
};

const withPlan = (route: string, planId?: string) => {
	if (!planId) return route;
	const separator = route.includes("?") ? "&" : "?";
	return `${route}${separator}planId=${encodeURIComponent(planId)}`;
};

export function ModelingWorkspaceModuleLanding({ module, planId, onNavigate }: ModelingWorkspaceModuleLandingProps) {
	const meta = MODULE_META[module];
	const Icon = meta.icon;

	return (
		<div
			className="grid min-h-[360px] place-items-center px-5 py-10"
			data-testid={`modeling-workspace-${module}-landing`}
		>
			<div className="w-full max-w-3xl border-l-2 border-blue-600 pl-5 md:pl-7">
				<div className="mb-5 flex size-11 items-center justify-center rounded-lg bg-blue-50 text-blue-700">
					<Icon size={21} aria-hidden="true" />
				</div>
				<div className="text-xs font-semibold uppercase tracking-[0.16em] text-blue-700">{meta.kicker}</div>
				<h2 className="mt-2 text-2xl font-semibold tracking-tight text-slate-950">{meta.title}</h2>
				<p className="mt-3 max-w-2xl text-sm leading-6 text-slate-600">{meta.description}</p>
				<div className="mt-7 flex flex-wrap gap-2">
					{meta.actions.map((action, index) => (
						<button
							key={action.route}
							type="button"
							className={[
								"inline-flex h-9 items-center gap-2 rounded-md border px-3 text-sm font-medium transition-colors",
								index === 0
									? "border-blue-600 bg-blue-600 text-white hover:bg-blue-700"
									: "border-slate-300 bg-white text-slate-700 hover:border-slate-400 hover:text-slate-950",
							].join(" ")}
							onClick={() => onNavigate(withPlan(action.route, planId))}
						>
							{action.label}
							<ArrowRight size={14} aria-hidden="true" />
						</button>
					))}
				</div>
			</div>
		</div>
	);
}
