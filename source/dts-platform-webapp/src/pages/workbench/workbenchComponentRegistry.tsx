import { Button, Card, Space, Typography } from "antd";
import {
	Activity,
	BarChart3,
	Boxes,
	Database,
	Gauge,
	GitBranch,
	ListTodo,
	MonitorPlay,
	Server,
	ShieldAlert,
	type LucideIcon,
} from "lucide-react";
import type { ReactNode } from "react";
import { useNavigate } from "react-router";
import type { WorkbenchComponentDescriptor } from "@/api/services/workbenchService";

export type WorkbenchComponentDefinition = {
	key: string;
	title: string;
	description: string;
	actionText?: string;
	route?: string;
	icon: LucideIcon;
	leaderOverview?: boolean;
	render?: () => ReactNode;
};

const LEADER_OVERVIEW_COMPONENT_KEYS = new Set([
	"leader-kpi",
	"top-reports",
	"core-assets",
	"screen-strip",
]);

type WorkbenchEntryCardProps = {
	definition: WorkbenchComponentDefinition;
};

function WorkbenchEntryCard({ definition }: WorkbenchEntryCardProps) {
	const navigate = useNavigate();
	const Icon = definition.icon;

	return (
		<Card
			size="small"
			style={{ borderRadius: 8, height: "100%" }}
			bodyStyle={{ height: "100%", display: "flex", flexDirection: "column", gap: 12 }}
		>
			<Space align="start" style={{ width: "100%" }}>
				<span
					style={{
						width: 36,
						height: 36,
						borderRadius: 8,
						display: "inline-flex",
						alignItems: "center",
						justifyContent: "center",
						background: "#eef4ff",
						color: "#2563eb",
						flex: "0 0 auto",
					}}
				>
					<Icon size={18} aria-hidden="true" />
				</span>
				<Space direction="vertical" size={2} style={{ minWidth: 0 }}>
					<Typography.Text strong>{definition.title}</Typography.Text>
					<Typography.Text type="secondary" style={{ fontSize: 13 }}>
						{definition.description}
					</Typography.Text>
				</Space>
			</Space>
			<div style={{ marginTop: "auto" }}>
				<Button
					type="primary"
					size="small"
					icon={<Icon size={14} aria-hidden="true" />}
					onClick={() => definition.route && navigate(definition.route)}
					disabled={!definition.route}
				>
					{definition.actionText ?? "进入"}
				</Button>
			</div>
		</Card>
	);
}

function entry(definition: Omit<WorkbenchComponentDefinition, "render">): WorkbenchComponentDefinition {
	return {
		...definition,
		render: () => <WorkbenchEntryCard definition={definition} />,
	};
}

export const WORKBENCH_COMPONENT_REGISTRY: WorkbenchComponentDefinition[] = [
	{
		key: "leader-kpi",
		title: "概览指标",
		description: "查看平台核心指标和使用趋势",
		icon: Gauge,
		leaderOverview: true,
	},
	{
		key: "top-reports",
		title: "常用大屏",
		description: "快速访问常用报表和大屏",
		icon: BarChart3,
		leaderOverview: true,
	},
	{
		key: "core-assets",
		title: "核心资产",
		description: "查看重点数据资产入口",
		icon: Boxes,
		leaderOverview: true,
	},
	{
		key: "screen-strip",
		title: "已发布大屏",
		description: "查看已发布的大屏成果",
		icon: MonitorPlay,
		leaderOverview: true,
	},
	entry({
		key: "todo",
		title: "待办事项",
		description: "处理审批、阻断和异常任务",
		actionText: "查看待办",
		route: "/workbench/todo",
		icon: ListTodo,
	}),
	entry({
		key: "bi-delivery",
		title: "BI 与大屏成果",
		description: "查看 BI 看板、大屏和分析成果",
		actionText: "查看成果",
		route: "/bi/dashboards",
		icon: MonitorPlay,
	}),
	entry({
		key: "data-sources",
		title: "数据源接入",
		description: "配置和查看数据源接入状态",
		actionText: "配置数据源",
		route: "/foundation/data-sources",
		icon: Database,
	}),
	entry({
		key: "golden-chain",
		title: "数据交付链路",
		description: "查看数据从接入到消费的交付链路",
		actionText: "查看链路",
		route: "/workbench?section=data-management",
		icon: GitBranch,
	}),
	entry({
		key: "governance-blockers",
		title: "治理阻断",
		description: "查看质量、权限和发布门禁阻断",
		actionText: "处理阻断",
		route: "/governance/quality",
		icon: ShieldAlert,
	}),
	entry({
		key: "api-services",
		title: "数据 API 服务",
		description: "查看和发布数据 API 服务",
		actionText: "发布 API",
		route: "/services/apis",
		icon: Server,
	}),
	entry({
		key: "ops-health",
		title: "运行健康",
		description: "查看调度、运行和异常状态",
		actionText: "查看运行",
		route: "/ops/overview",
		icon: Activity,
	}),
];

export function getWorkbenchComponent(key: string): WorkbenchComponentDefinition | undefined {
	return WORKBENCH_COMPONENT_REGISTRY.find((component) => component.key === key);
}

export function isLeaderOverviewComponentKey(key: string): boolean {
	return LEADER_OVERVIEW_COMPONENT_KEYS.has(key);
}

export function renderWorkbenchComponent(key: string): ReactNode {
	return getWorkbenchComponent(key)?.render?.() ?? null;
}

export function toWorkbenchComponentDescriptors(): WorkbenchComponentDescriptor[] {
	return WORKBENCH_COMPONENT_REGISTRY.map((component) => ({
		key: component.key,
		title: component.title,
		description: component.description,
		enabled: true,
		disabledReason: null,
	}));
}

export function registryOrderOf(key: string): number {
	const index = WORKBENCH_COMPONENT_REGISTRY.findIndex((component) => component.key === key);
	return index < 0 ? Number.MAX_SAFE_INTEGER : index;
}
