import { Alert, Button, Card, DatePicker, Form, Select, Slider, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";

const { RangePicker } = DatePicker;

type BackfillRecord = {
	key: string;
	name: string;
	range: string;
	progress: string;
	status: string;
};

const historyRows: BackfillRecord[] = [];

const historyColumns: ColumnsType<BackfillRecord> = [
	{ title: "补数名称", dataIndex: "name" },
	{ title: "日期范围", dataIndex: "range" },
	{ title: "进度", dataIndex: "progress" },
	{ title: "状态", dataIndex: "status", render: (s) => <Tag color="blue">{s}</Tag> },
];

export default function OpsBackfillPage() {
	return (
		<div className="mx-auto w-full max-w-none space-y-6 px-6 py-6">
			<PageHeader title="补数管理" description="对历史数据进行重跑与补数，自动处理依赖与重试。" />
			<Alert message="补数操作会消耗大量计算资源，请避开业务高峰期执行。" type="warning" showIcon />

			<Card>
				<Form layout="vertical">
					<Form.Item label="选择目标任务" required>
						<Select mode="multiple" placeholder="请选择需要重新跑数据的任务">
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
				{historyRows.length ? (
					<Table size="small" dataSource={historyRows} columns={historyColumns} />
				) : (
					<EmptyState title="暂无补数记录" description="创建补数任务后将在此处展示历史记录。" />
				)}
			</Card>
		</div>
	);
}
