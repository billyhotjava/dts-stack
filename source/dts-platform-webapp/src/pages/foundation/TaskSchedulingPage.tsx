import { Card, Space, Tag, Typography } from "antd";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";
import { useRouter } from "@/routes/hooks";
import { Button } from "@/ui/button";

const { Text } = Typography;

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

	return (
		<div className="space-y-6">
			<PageHeader title="任务运维中心" description="统一进入运行概览、编排入口与任务执行管理。" />

			<div className="grid gap-4 xl:grid-cols-3">
				{ENTRY_CARDS.map((item) => (
					<Card key={item.key} className="rounded-[24px] shadow-sm">
						<div className="space-y-4">
							<div className="flex items-start justify-between gap-3">
								<div className="space-y-1">
									<div className="text-base font-semibold text-text-primary">{item.title}</div>
									<Text type="secondary">{item.description}</Text>
								</div>
								<Tag bordered={false} color="blue">
									{item.tag}
								</Tag>
							</div>
							<Button className="rounded-2xl" onClick={() => push(item.path)}>
								{item.actionLabel}
							</Button>
						</div>
					</Card>
				))}
			</div>

			<Card className="rounded-[24px] shadow-sm">
				<EmptyState
					title="从现有运维入口开始处理任务"
					description="当前版本把任务监控、编排和执行记录收敛到现有页面中，优先保证现场可用性与入口清晰度。"
					actions={
						<Space wrap>
							<Button variant="outline" className="rounded-2xl" onClick={() => push("/dashboard/ops/overview")}>
								打开运行概览
							</Button>
							<Button variant="outline" className="rounded-2xl" onClick={() => push("/dashboard/explore/etl/orchestration")}>
								打开任务编排
							</Button>
							<Button variant="outline" className="rounded-2xl" onClick={() => push("/dashboard/explore/etl/transform")}>
								打开任务列表
							</Button>
						</Space>
					}
				/>
			</Card>
		</div>
	);
}
