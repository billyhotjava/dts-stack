import { Card, Col, Row, Statistic, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";

const { Title, Text } = Typography;

type SlaRow = {
	key: string;
	name: string;
	expected: string;
	current: string;
	delay: string;
	owner: string;
};

const slaRows: SlaRow[] = [];

const slaColumns: ColumnsType<SlaRow> = [
	{ title: "任务名称", dataIndex: "name" },
	{ title: "预期产出", dataIndex: "expected" },
	{ title: "当前进度", dataIndex: "current" },
	{ title: "延迟时长", dataIndex: "delay", render: (value) => <Text type="danger">{value}</Text> },
	{ title: "负责人", dataIndex: "owner" },
];

export default function OpsOverviewPage() {
	return (
		<div className="mx-auto w-full max-w-none space-y-6 px-6 py-6">
			<Title level={3} style={{ margin: 0 }}>
				任务运行概览
			</Title>

			<Row gutter={[16, 16]} className="mb-2">
				<Col xs={24} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="今日任务总数" value="--" />
					</Card>
				</Col>
				<Col xs={24} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="成功率" value="--" suffix="%" />
					</Card>
				</Col>
				<Col xs={24} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="异常告警" value="--" />
					</Card>
				</Col>
				<Col xs={24} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="正在运行" value="--" />
					</Card>
				</Col>
			</Row>

			<Card title="任务执行趋势 (近24小时)">
				<EmptyState title="暂无趋势数据" description="任务运行数据接入后展示趋势分析。" />
			</Card>

			<Card title="数仓分层成功率">
				<EmptyState title="暂无成功率统计" description="接入任务运行结果后展示分层成功率。" />
			</Card>

			<Card title="SLA 延迟预警 (关键任务路径)">
				{slaRows.length ? (
					<Table size="small" pagination={false} dataSource={slaRows} columns={slaColumns} />
				) : (
					<EmptyState title="暂无 SLA 预警" description="接入 SLA 数据后展示预警列表。" />
				)}
			</Card>
		</div>
	);
}
