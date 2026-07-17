import { Button } from "antd";
import type { LucideIcon } from "lucide-react";
import { Box, Database, Layers3, Rocket } from "lucide-react";
import type { ReactNode } from "react";
import { useNavigate } from "react-router";
import { PageHeader } from "@/components/page-header";
import { cn } from "@/utils";
import type { BusinessModelingContext } from "../businessModelingContext";
import { buildModelingJourneyRoute, type ModelingStage, modelingStagePath } from "../modelingJourneyContext";
import { BusinessModelingContextBar } from "./BusinessModelingContextBar";

export type SemanticWorkspaceKey = "workbench" | "objects" | "metrics" | "models" | "publish";

export type SemanticWorkspaceStat = {
	label: string;
	value: number | string;
	tone?: "blue" | "green" | "amber" | "red" | "gray";
};

type JourneyStage = {
	key: ModelingStage;
	label: string;
	path: string;
	icon: LucideIcon;
};

const JOURNEY_STAGES: JourneyStage[] = [
	{ key: "SCOPE", label: "范围与来源", path: modelingStagePath("SCOPE"), icon: Layers3 },
	{ key: "LOGICAL", label: "逻辑模型", path: modelingStagePath("LOGICAL"), icon: Box },
	{
		key: "IMPLEMENTATION",
		label: "实现与验证",
		path: modelingStagePath("IMPLEMENTATION"),
		icon: Database,
	},
	{ key: "RELEASE", label: "发布与运行", path: modelingStagePath("RELEASE"), icon: Rocket },
];

const STAGE_BY_ACTIVE_KEY: Record<SemanticWorkspaceKey, ModelingStage> = {
	workbench: "LOGICAL",
	objects: "LOGICAL",
	metrics: "LOGICAL",
	models: "IMPLEMENTATION",
	publish: "RELEASE",
};

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
	context,
}: {
	activeKey: SemanticWorkspaceKey;
	title: string;
	description: string;
	stats?: SemanticWorkspaceStat[];
	actions?: ReactNode;
	children: ReactNode;
	className?: string;
	context?: BusinessModelingContext;
}) {
	const navigate = useNavigate();
	const activeStage = STAGE_BY_ACTIVE_KEY[activeKey];

	return (
		<div className={cn("space-y-4 px-6 py-5", className)} data-testid="semantic-workspace-frame">
			<PageHeader title={title} actions={<div className="flex flex-wrap items-center gap-2">{actions}</div>} />
			{context ? <BusinessModelingContextBar context={context} /> : null}

			<div className="rounded-lg border border-gray-200 bg-white px-4 py-3 shadow-sm">
				<div className="flex flex-wrap items-center justify-between gap-3">
					<div className="min-w-0">
						<div className="text-sm font-medium text-gray-900">{description}</div>
					</div>
					<div className="flex flex-wrap gap-2">
						{stats.map((item) => (
							<span
								key={item.label}
								className={cn("rounded-full border px-3 py-1 text-xs", STAT_TONE_CLASS[item.tone ?? "gray"])}
							>
								{item.label}：<strong>{item.value}</strong>
							</span>
						))}
					</div>
				</div>

				<div className="mt-3">
					<div className="min-w-0" data-testid="semantic-workspace-flow">
						<nav className="flex flex-wrap items-center gap-2" aria-label="通用建模阶段导航">
							{JOURNEY_STAGES.map((step) => {
								const Icon = step.icon;
								const active = step.key === activeStage;
								return (
									<Button
										key={step.key}
										size="small"
										type={active ? "primary" : "default"}
										aria-current={active ? "step" : undefined}
										onClick={() => navigate(buildModelingJourneyRoute(step.path, { ...context, stage: step.key }))}
									>
										<Icon size={14} />
										{step.label}
									</Button>
								);
							})}
						</nav>
					</div>
				</div>
			</div>
			{children}
		</div>
	);
}
