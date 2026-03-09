import { Activity, ArrowRight, ListChecks, Workflow } from "lucide-react";
import {
	PlatformFilterBar,
	PlatformMetaPill,
	PlatformPageHero,
	PlatformSectionCard,
	PlatformSummaryCards,
} from "@/components/console-page";
import { EmptyState } from "@/components/empty-state";
import { useRouter } from "@/routes/hooks";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";

const ENTRY_CARDS = [
	{
		key: "ops",
		title: "运行概览",
		description: "查看任务成功率、失败作业、异常告警与近期运行趋势。",
		tag: "监控",
		path: "/dashboard/ops/overview",
		actionLabel: "查看概览",
	},
	{
		key: "orchestration",
		title: "任务编排",
		description: "进入编排入口，查看外部 DAG 状态、失败记录与最近执行。",
		tag: "编排",
		path: "/dashboard/explore/etl/orchestration",
		actionLabel: "查看编排",
	},
	{
		key: "transform",
		title: "任务列表",
		description: "查看数据集成任务、执行历史与任务详情。",
		tag: "执行",
		path: "/dashboard/explore/etl/transform",
		actionLabel: "查看任务",
	},
];

export default function Page() {
	const { push } = useRouter();

	const summaryCards = [
		{
			label: "运维入口",
			value: String(ENTRY_CARDS.length),
			note: "运行、编排、执行三类入口",
			icon: <Workflow className="h-5 w-5" />,
		},
		{
			label: "运行视角",
			value: "1",
			note: "统一进入运行概览",
			icon: <Activity className="h-5 w-5" />,
			tone: "info" as const,
		},
		{
			label: "编排视角",
			value: "1",
			note: "Airflow / DAG 处理入口",
			icon: <ListChecks className="h-5 w-5" />,
			tone: "warning" as const,
		},
		{
			label: "执行视角",
			value: "1",
			note: "任务列表与详情联动",
			icon: <ArrowRight className="h-5 w-5" />,
			tone: "success" as const,
		},
	];

	return (
		<div className="space-y-6">
			<PlatformPageHero
				title="任务运维中心"
				description="把任务监控、编排入口和执行管理收敛到同一个任务运营面板，优先保证现场入口清晰。"
				eyebrow="Task Operations"
				actions={
					<>
						<Button variant="outline" onClick={() => push("/dashboard/ops/overview")}>
							运行概览
						</Button>
						<Button variant="outline" onClick={() => push("/dashboard/explore/etl/orchestration")}>
							任务编排
						</Button>
						<Button onClick={() => push("/dashboard/explore/etl/transform")}>
							任务列表
						</Button>
					</>
				}
				meta={
					<>
						<PlatformMetaPill>任务入口统一收口</PlatformMetaPill>
						<PlatformMetaPill>先看运行，再看编排与执行</PlatformMetaPill>
						<PlatformMetaPill>不再展示纯占位说明</PlatformMetaPill>
					</>
				}
			/>

			<PlatformSummaryCards items={summaryCards} />

			<PlatformFilterBar>
				<div>
					<div className="text-sm font-semibold text-foreground">按处理视角进入任务链路</div>
					<div className="mt-1 text-sm text-muted-foreground">
						这页不再承载额外占位功能，只负责把现有可用入口组织清楚。
					</div>
				</div>
				<div className="flex flex-wrap items-center gap-2">
					{ENTRY_CARDS.map((item) => (
						<Button key={item.key} variant="outline" size="sm" onClick={() => push(item.path)}>
							{item.title}
						</Button>
					))}
				</div>
			</PlatformFilterBar>

			<PlatformSectionCard
				title="任务入口"
				description="每个入口都对应真实页面，不再出现“预留中心”或空壳子页。"
			>
				<div className="grid gap-4 xl:grid-cols-3">
					{ENTRY_CARDS.map((item) => (
						<div key={item.key} className="rounded-[24px] border border-border/70 bg-muted/35 p-5">
							<div className="space-y-4">
								<div className="flex items-start justify-between gap-3">
									<div className="space-y-1">
										<div className="text-base font-semibold text-foreground">{item.title}</div>
										<div className="text-sm leading-6 text-muted-foreground">{item.description}</div>
									</div>
									<Badge variant="info" className="rounded-full px-2.5 py-1">
										{item.tag}
									</Badge>
								</div>
								<Button className="rounded-2xl" onClick={() => push(item.path)}>
									{item.actionLabel}
								</Button>
							</div>
						</div>
					))}
				</div>
			</PlatformSectionCard>

			<div className="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
				<PlatformSectionCard
					title="处理建议"
					description="先判断问题发生在哪个层次，再进入对应页面。"
				>
					<div className="grid gap-3">
						{[
							"任务整体失败率、异常作业和告警先看运行概览。",
							"涉及 DAG、编排链路或调度失败先看任务编排页。",
							"涉及单个任务的执行历史、日志和配置先看任务列表与详情页。",
						].map((item) => (
							<div key={item} className="rounded-[22px] border border-border/70 bg-muted/35 px-4 py-4 text-sm leading-6 text-muted-foreground">
								{item}
							</div>
						))}
					</div>
				</PlatformSectionCard>

				<PlatformSectionCard
					title="入口收口说明"
					description="当前基线先保证入口清楚，再把复杂状态放回各自业务页处理。"
				>
					<EmptyState
						title="从现有运维入口开始处理任务"
						description="任务监控、编排和执行记录已经收敛到现有页面中，这里只保留真实可用的导航与处理说明。"
						actions={
							<div className="flex flex-wrap justify-center gap-2">
								<Button variant="outline" className="rounded-2xl" onClick={() => push("/dashboard/ops/overview")}>
									打开运行概览
								</Button>
								<Button variant="outline" className="rounded-2xl" onClick={() => push("/dashboard/explore/etl/orchestration")}>
									打开任务编排
								</Button>
								<Button variant="outline" className="rounded-2xl" onClick={() => push("/dashboard/explore/etl/transform")}>
									打开任务列表
								</Button>
							</div>
						}
					/>
				</PlatformSectionCard>
			</div>
		</div>
	);
}
