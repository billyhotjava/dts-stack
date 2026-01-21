import { Badge, Button, Card, DatePicker, Input, Select, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";

const { RangePicker } = DatePicker;

type InstanceRow = {
	key: string;
	name: string;
	layer: string;
	status: "Success" | "Running" | "Failed";
	retry: number;
	start: string;
	duration: string;
};

const dataSource: InstanceRow[] = [
	{ key: "1", name: "ods_user_center", layer: "ODS", status: "Success", retry: 0, start: "2026-01-20 02:00", duration: "1m 20s" },
	{ key: "2", name: "dwd_trade_detail", layer: "DWD", status: "Failed", retry: 2, start: "2026-01-20 04:30", duration: "45s" },
];

const columns: ColumnsType<InstanceRow> = [
	{ title: "任务名称", dataIndex: "name", key: "name", render: (t) => <span className="font-medium">{t}</span> },
	{ title: "数仓分层", dataIndex: "layer", render: (l) => <Tag>{l}</Tag> },
	{
		title: "运行状态",
		dataIndex: "status",
		render: (s) => (
			<Badge status={s === "Success" ? "success" : s === "Running" ? "processing" : "error"} text={s} />
		),
	},
	{ title: "重试", dataIndex: "retry", render: (r) => `第 ${r} 次` },
	{ title: "开始时间", dataIndex: "start" },
	{ title: "耗时", dataIndex: "duration" },
	{
		title: "操作",
		render: () => (
			<Space>
				<Button type="link" size="small">
					详情
				</Button>
				<Button type="link" size="small">
					重跑
				</Button>
				<Button type="link" size="small" danger>
					停止
				</Button>
			</Space>
		),
	},
];

export default function OpsInstancesPage() {
	return (
		<Card title="任务运行实例监控">
			<div className="mb-4 flex flex-wrap items-center justify-between gap-3">
				<Space wrap>
					<Input placeholder="搜索任务名称..." style={{ width: 200 }} />
					<Select defaultValue="all" style={{ width: 120 }}>
						<Select.Option value="all">所有状态</Select.Option>
						<Select.Option value="running">运行中</Select.Option>
						<Select.Option value="failed">失败</Select.Option>
					</Select>
					<RangePicker />
				</Space>
				<Button icon={<span>🔄</span>}>刷新</Button>
			</div>
			<Table dataSource={dataSource} columns={columns} />
		</Card>
	);
}
