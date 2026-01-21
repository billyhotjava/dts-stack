import { Badge, Button, Card, Descriptions, Space, Tabs, Tag } from "antd";

const logLines = [
	"[2026-01-20 04:30:01] INFO - Executing: dbt run --models dwd_trade_detail",
	"[2026-01-20 04:30:05] INFO - Connection successful.",
	"[2026-01-20 04:30:45] ERROR - Database Error: column \"order_typ\" does not exist",
	"    at line 14: SELECT order_id, order_typ FROM ods_orders",
	"[2026-01-20 04:30:46] INFO - Task failed. Retrying in 300s...",
];

export default function OpsAlertLogPage() {
	return (
		<div className="mx-auto max-w-5xl space-y-4">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<h2 className="text-xl font-bold">告警详情: dwd_trade_detail</h2>
				<Space>
					<Button>复制错误信息</Button>
					<Button type="primary">重新执行</Button>
				</Space>
			</div>

			<Card>
				<Descriptions column={3} size="small">
					<Descriptions.Item label="实例 ID">run_20260120_0430</Descriptions.Item>
					<Descriptions.Item label="状态">
						<Badge status="error" text="Failed" />
					</Descriptions.Item>
					<Descriptions.Item label="数据周期">2026-01-19</Descriptions.Item>
					<Descriptions.Item label="DAG ID">dag_sales_v2</Descriptions.Item>
					<Descriptions.Item label="负责人">李四</Descriptions.Item>
				</Descriptions>
			</Card>

			<Tabs
				defaultActiveKey="logs"
				className="rounded-lg bg-white p-4"
				items={[
					{
						key: "logs",
						label: "执行日志",
						children: (
							<div className="h-[360px] overflow-y-auto rounded-md bg-slate-900 p-4 font-mono text-xs text-slate-200">
								{logLines.map((line, idx) => (
									<div key={`${line}-${idx}`} className={line.includes("ERROR") ? "text-red-400" : ""}>
										{line}
									</div>
								))}
								<div className="mt-2 h-4 w-2 animate-pulse bg-blue-500" />
							</div>
						),
					},
					{
						key: "deps",
						label: "上游依赖",
						children: (
							<div className="flex h-64 items-center justify-center gap-8">
								<div className="w-36 rounded border border-slate-200 bg-white p-3 text-center">
									<div>ods_orders</div>
									<Tag color="green" className="mt-2">
										Success
									</Tag>
								</div>
								<div className="text-2xl text-slate-400">→</div>
								<div className="w-36 rounded border border-red-500 bg-white p-3 text-center shadow-sm">
									<div>dwd_trade_detail</div>
									<Tag color="red" className="mt-2">
										Failed
									</Tag>
								</div>
							</div>
						),
					},
				]}
			/>
		</div>
	);
}
