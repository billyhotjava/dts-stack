import { Alert, Button, Card, DatePicker, Form, Select, Slider, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";

const { RangePicker } = DatePicker;

type BackfillRecord = {
	key: string;
	name: string;
	range: string;
	progress: string;
	status: string;
};

const historyRows: BackfillRecord[] = [
	{ key: "1", name: "双十一数据回溯", range: "2025-11-11 ~ 2025-11-12", progress: "100%", status: "Finished" },
];

const historyColumns: ColumnsType<BackfillRecord> = [
	{ title: "补数名称", dataIndex: "name" },
	{ title: "日期范围", dataIndex: "range" },
	{ title: "进度", dataIndex: "progress" },
	{ title: "状态", dataIndex: "status", render: (s) => <Tag color="blue">{s}</Tag> },
];

export default function OpsBackfillPage() {
	return (
		<div className="mx-auto max-w-4xl space-y-6">
			<Typography.Title level={3} style={{ marginBottom: 0 }}>
				创建补数任务
			</Typography.Title>
			<Alert message="补数操作会消耗大量计算资源，请避开业务高峰期执行。" type="warning" showIcon />

			<Card>
				<Form layout="vertical">
					<Form.Item label="选择目标任务" required>
						<Select mode="multiple" placeholder="请选择需要重新跑数据的任务">
							<Select.Option value="1">dwd_trade_detail</Select.Option>
							<Select.Option value="2">ads_finance_summary</Select.Option>
						</Select>
					</Form.Item>
					<Form.Item label="业务日期范围" required>
						<RangePicker className="w-full" />
					</Form.Item>
					<Form.Item label="并行度控制 (同时执行的任务数)">
						<Slider defaultValue={2} min={1} max={10} marks={{ 1: "1", 10: "10" }} />
					</Form.Item>
					<Button type="primary" size="large" block>
						启动补数
					</Button>
				</Form>
			</Card>

			<Card title="补数历史记录">
				<Table size="small" dataSource={historyRows} columns={historyColumns} />
			</Card>
		</div>
	);
}
