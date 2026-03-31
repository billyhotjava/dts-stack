import { Button, Card } from "antd";
import { useRouter } from "@/routes/hooks";

const ENTRY_CARDS = [
	{
		key: "ops",
		title: "运行概览",
		description: "查看任务成功率、失败作业、异常告警与近期运行趋势。",
		tag: "监控",
		path: "/ops/overview",
		actionLabel: "查看概览",
	},
	{
		key: "orchestration",
		title: "任务编排",
		description: "进入编排入口，查看外部 DAG 状态、失败记录与最近执行。",
		tag: "编排",
		path: "/explore/etl/orchestration",
		actionLabel: "查看编排",
	},
	{
		key: "transform",
		title: "任务列表",
		description: "查看数据集成任务、执行历史与任务详情。",
		tag: "执行",
		path: "/explore/etl/transform",
		actionLabel: "查看任务",
	},
];

export default function Page() {
	const { push } = useRouter();

	return (
		<div className="space-y-6">
			<Card
				title="任务运维中心"
				extra={
					<>
						<Button style={{ marginRight: 8 }} onClick={() => push("/ops/overview")}>
							运行概览
						</Button>
						<Button style={{ marginRight: 8 }} onClick={() => push("/explore/etl/orchestration")}>
							任务编排
						</Button>
						<Button type="primary" onClick={() => push("/explore/etl/transform")}>
							任务列表
						</Button>
					</>
				}
			>
				<div className="grid gap-4 xl:grid-cols-3">
					{ENTRY_CARDS.map((item) => (
						<Card key={item.key} size="small" title={item.title} extra={<span style={{ fontSize: 12, color: "#888" }}>{item.tag}</span>}>
							<p style={{ marginBottom: 12, color: "#666" }}>{item.description}</p>
							<Button type="primary" onClick={() => push(item.path)}>
								{item.actionLabel}
							</Button>
						</Card>
					))}
				</div>
			</Card>

			<Card title="处理建议">
				<div className="grid gap-3">
					{[
						"任务整体失败率、异常作业和告警先看运行概览。",
						"涉及 DAG、编排链路或调度失败先看任务编排页。",
						"涉及单个任务的执行历史、日志和配置先看任务列表与详情页。",
					].map((item) => (
						<div key={item} style={{ padding: "12px 16px", background: "#fafafa", borderRadius: 8, fontSize: 14, color: "#666" }}>
							{item}
						</div>
					))}
				</div>
			</Card>
		</div>
	);
}
