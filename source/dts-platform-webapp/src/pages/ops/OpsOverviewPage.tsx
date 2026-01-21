import { Card, Col, Progress, Row, Statistic, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";

const { Title, Text } = Typography;

type SlaRow = {
	key: string;
	name: string;
	expected: string;
	current: string;
	delay: string;
	owner: string;
};

const slaRows: SlaRow[] = [
	{ key: "1", name: "ads_finance_report", expected: "08:00", current: "08:45", delay: "45min", owner: "张三" },
	{ key: "2", name: "dws_user_assets", expected: "06:00", current: "06:10", delay: "10min", owner: "李四" },
];

const slaColumns: ColumnsType<SlaRow> = [
	{ title: "任务名称", dataIndex: "name" },
	{ title: "预期产出", dataIndex: "expected" },
	{ title: "当前进度", dataIndex: "current" },
	{ title: "延迟时长", dataIndex: "delay", render: (value) => <Text type="danger">{value}</Text> },
	{ title: "负责人", dataIndex: "owner" },
];

export default function OpsOverviewPage() {
	return (
		<div className="mx-auto max-w-7xl space-y-6">
			<div>
				<Title level={3} style={{ margin: 0 }}>
					任务运行概览
				</Title>
			</div>

			<Row gutter={16} className="mb-2">
				<Col span={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="今日任务总数" value={1240} prefix={<span>📊</span>} />
					</Card>
				</Col>
				<Col span={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="成功率" value={98.5} suffix="%" valueStyle={{ color: "#3f8600" }} />
					</Card>
				</Col>
				<Col span={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="异常告警" value={3} valueStyle={{ color: "#cf1322" }} />
					</Card>
				</Col>
				<Col span={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="正在运行" value={12} />
					</Card>
				</Col>
			</Row>

			<Row gutter={16}>
				<Col span={16}>
					<Card title="任务执行趋势 (近24小时)" className="mb-6">
						<div className="flex h-[200px] items-end justify-around rounded-lg bg-slate-50 px-4 py-3">
							{[40, 60, 90, 30, 50, 80].map((height, idx) => (
								<div
									key={`bar-${height}-${idx}`}
									className={`w-7 rounded-t-md ${idx === 4 ? "bg-blue-300" : "bg-blue-500"}`}
									style={{ height: `${height}%` }}
								/>
							))}
						</div>
						<div className="mt-2 flex justify-between text-xs text-slate-400">
							<span>02:00 (高峰)</span>
							<span>08:00</span>
							<span>14:00</span>
							<span>20:00</span>
						</div>
					</Card>
				</Col>
				<Col span={8}>
					<Card title="数仓分层成功率" className="mb-6">
						<div className="mb-4">ODS 层 <Progress percent={100} size="small" /></div>
						<div className="mb-4">DWD 层 <Progress percent={98} size="small" /></div>
						<div className="mb-4">DWS 层 <Progress percent={95} size="small" status="active" /></div>
						<div className="mb-4">ADS 层 <Progress percent={80} size="small" status="exception" /></div>
					</Card>
				</Col>
			</Row>

			<Card title="SLA 延迟预警 (关键任务路径)">
				<Table size="small" pagination={false} dataSource={slaRows} columns={slaColumns} />
			</Card>
		</div>
	);
}
