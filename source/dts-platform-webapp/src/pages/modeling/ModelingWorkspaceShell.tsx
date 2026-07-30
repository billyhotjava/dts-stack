import { Button, Select } from "antd";
import { Boxes, ChartNoAxesCombined, House, Network, PanelTop, Ruler, Wrench } from "lucide-react";
import type { ReactNode } from "react";
import type { ModelingWorkspaceModule } from "./modelingWorkspaceRouteState";

type PlanOption = {
	label: string;
	value: string;
};

type ModelingWorkspaceShellProps = {
	activeModule: ModelingWorkspaceModule;
	planId?: string;
	planOptions: PlanOption[];
	canCreatePlan: boolean;
	onModuleChange: (module: ModelingWorkspaceModule) => void;
	onPlanChange: (planId: string) => void;
	onCreatePlan: () => void;
	onOpenAllPlans: () => void;
	children: ReactNode;
};

const MODULES: Array<{
	key: ModelingWorkspaceModule;
	label: string;
	icon: typeof House;
}> = [
	{ key: "home", label: "首页", icon: House },
	{ key: "planning", label: "数仓规划", icon: PanelTop },
	{ key: "standards", label: "数据标准", icon: Ruler },
	{ key: "models", label: "维度建模", icon: Boxes },
	{ key: "metrics", label: "数据指标", icon: ChartNoAxesCombined },
	{ key: "tools", label: "通用工具", icon: Wrench },
	{ key: "graph", label: "关系图", icon: Network },
];

export function ModelingWorkspaceShell({
	activeModule,
	planId,
	planOptions,
	canCreatePlan,
	onModuleChange,
	onPlanChange,
	onCreatePlan,
	onOpenAllPlans,
	children,
}: ModelingWorkspaceShellProps) {
	return (
		<section
			className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-[0_8px_28px_rgba(15,23,42,0.06)]"
			data-testid="modeling-workspace-shell"
		>
			<header className="flex min-h-14 flex-col gap-3 border-b border-slate-200 px-4 py-3 lg:flex-row lg:items-center lg:justify-between lg:px-5">
				<div className="flex min-w-0 items-center gap-3">
					<div className="grid size-9 shrink-0 place-items-center rounded-lg bg-slate-950 text-white">
						<Boxes size={18} aria-hidden="true" />
					</div>
					<div className="min-w-0">
						<div className="truncate text-base font-semibold tracking-tight text-slate-950">智能数据建模</div>
						<div className="truncate text-xs text-slate-500">规划、标准、模型与交付共享同一建设上下文</div>
					</div>
				</div>

				<div className="flex flex-wrap items-center gap-2">
					{planOptions.length > 0 ? (
						<Select
							aria-label="当前建设计划"
							value={planId || undefined}
							placeholder="选择建设计划"
							className="min-w-52 sm:min-w-64"
							options={planOptions}
							onChange={onPlanChange}
						/>
					) : null}
					<Button
						type="primary"
						disabled={!canCreatePlan}
						title={canCreatePlan ? undefined : "当前账号没有规划维护权限"}
						onClick={onCreatePlan}
					>
						新建规划
					</Button>
					<Button type="text" onClick={onOpenAllPlans}>
						全部规划
					</Button>
				</div>
			</header>

			<nav className="overflow-x-auto border-b border-slate-200 bg-slate-50/70" aria-label="建模工作区模块">
				<div className="flex min-w-max px-2" role="tablist" aria-orientation="horizontal">
					{MODULES.map((item) => {
						const active = activeModule === item.key;
						const Icon = item.icon;
						return (
							<button
								key={item.key}
								id={`modeling-workspace-tab-${item.key}`}
								type="button"
								role="tab"
								aria-selected={active}
								aria-controls="modeling-workspace-panel"
								tabIndex={active ? 0 : -1}
								className={[
									"relative flex h-11 items-center gap-2 px-3 text-sm font-medium transition-colors",
									active ? "text-blue-700" : "text-slate-600 hover:text-slate-950",
								].join(" ")}
								onClick={() => onModuleChange(item.key)}
							>
								<Icon size={15} aria-hidden="true" />
								<span>{item.label}</span>
								{active ? <span className="absolute inset-x-2 bottom-0 h-0.5 bg-blue-600" /> : null}
							</button>
						);
					})}
				</div>
			</nav>

			<div id="modeling-workspace-panel" role="tabpanel" aria-labelledby={`modeling-workspace-tab-${activeModule}`}>
				{children}
			</div>
		</section>
	);
}
