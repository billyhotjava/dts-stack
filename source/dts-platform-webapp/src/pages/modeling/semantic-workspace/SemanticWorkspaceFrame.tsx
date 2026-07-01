import type { ReactNode } from "react";
import { Button } from "antd";
import { Activity, BarChart3, Box, CheckCircle2, CircleDot, Database, Layers3, MapPinned, Rocket } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { useNavigate } from "react-router";
import { PageHeader } from "@/components/page-header";
import { cn } from "@/utils";

export type SemanticWorkspaceKey = "workbench" | "objects" | "metrics" | "models" | "publish";

export type SemanticWorkspaceStat = {
	label: string;
	value: number | string;
	tone?: "blue" | "green" | "amber" | "red" | "gray";
};

type SemanticStep = {
	key: SemanticWorkspaceKey;
	label: string;
	path: string;
	icon: LucideIcon;
};

const STEPS: SemanticStep[] = [
	{
		key: "workbench",
		label: "指标工作台",
		path: "/modeling/metric-workbench",
		icon: Layers3,
	},
	{
		key: "objects",
		label: "业务对象",
		path: "/modeling/semantic/objects",
		icon: Box,
	},
	{
		key: "metrics",
		label: "指标管理",
		path: "/modeling/semantic/metrics",
		icon: BarChart3,
	},
	{
		key: "models",
		label: "模型管理",
		path: "/modeling/semantic/models",
		icon: Database,
	},
	{
		key: "publish",
		label: "发布审核",
		path: "/modeling/semantic/publish",
		icon: Rocket,
	},
];

const STAT_TONE_CLASS: Record<NonNullable<SemanticWorkspaceStat["tone"]>, string> = {
	blue: "border-blue-100 bg-blue-50 text-blue-700",
	green: "border-emerald-100 bg-emerald-50 text-emerald-700",
	amber: "border-amber-100 bg-amber-50 text-amber-700",
	red: "border-red-100 bg-red-50 text-red-700",
	gray: "border-gray-200 bg-white text-gray-700",
};

export function SemanticWorkspaceFrame({
	activeKey,
	title,
	description,
	stats = [],
	actions,
	children,
	className,
}: {
	activeKey: SemanticWorkspaceKey;
	title: string;
	description: string;
	stats?: SemanticWorkspaceStat[];
	actions?: ReactNode;
	children: ReactNode;
	className?: string;
}) {
	const navigate = useNavigate();
	const activeIndex = Math.max(0, STEPS.findIndex((step) => step.key === activeKey));

	return (
		<div className={cn("space-y-4 px-6 py-5", className)} data-testid="semantic-workspace-frame">
			<PageHeader
				title={title}
				actions={
					<div className="flex flex-wrap items-center gap-2">
						<Button onClick={() => navigate("/governance/subjects")}>
							<MapPinned size={16} />
							治理主题域
						</Button>
						<Button onClick={() => navigate("/ops/instances?entryKey=DBT_RUN")}>
							<Activity size={16} />
							任务运维中心
						</Button>
						{actions}
					</div>
				}
			/>

				<div className="rounded-lg border border-gray-200 bg-white px-4 py-3 shadow-sm">
					<div className="flex flex-wrap items-center justify-between gap-3">
						<div className="min-w-0">
							<div className="text-sm font-medium text-gray-900">{description}</div>
						</div>
						<div className="flex flex-wrap gap-2">
						{stats.map((item) => (
							<div
								key={item.label}
								className={cn(
									"min-w-[96px] rounded-md border px-3 py-2",
									STAT_TONE_CLASS[item.tone ?? "gray"],
								)}
							>
								<div className="text-lg font-semibold leading-5">{item.value}</div>
								<div className="mt-1 text-xs opacity-80">{item.label}</div>
							</div>
							))}
						</div>
					</div>

					<div className="mt-3">
						<div className="min-w-0" data-testid="semantic-workspace-flow">
							<div className="flex flex-wrap items-center gap-2" aria-label="指标建模导航">
								{STEPS.map((step, index) => {
									const Icon = step.icon;
									const active = step.key === activeKey;
									const done = index < activeIndex;
									return (
										<Button
											key={step.key}
											size="small"
											type={active ? "primary" : "default"}
											aria-current={active ? "step" : undefined}
											onClick={() => navigate(step.path)}
										>
											{done ? <CheckCircle2 size={14} /> : active ? <CircleDot size={14} /> : <Icon size={14} />}
											{step.label}
										</Button>
									);
								})}
							</div>
						</div>
					</div>
				</div>

			{children}
		</div>
	);
}
