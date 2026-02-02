import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Card, Col, Row, Statistic, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import opsService, { type OpsAlert, type OpsOverview } from "@/api/services/opsService";

const { Text } = Typography;

const alertColumns: ColumnsType<OpsAlert> = [
	{ title: "类型", dataIndex: "type", width: 120 },
	{ title: "规则", dataIndex: "ruleName", render: (value) => value || "-" },
	{ title: "状态", dataIndex: "status", width: 120 },
	{ title: "严重性", dataIndex: "severity", width: 120, render: (value) => value || "-" },
	{ title: "描述", dataIndex: "message", render: (value) => value || "-" },
];

export default function OpsOverviewPage() {
	const [overview, setOverview] = useState<OpsOverview | null>(null);
	const [alerts, setAlerts] = useState<OpsAlert[]>([]);
	const [loading, setLoading] = useState(false);

	const loadData = async () => {
		setLoading(true);
		try {
			const [summary, alertList] = await Promise.all([opsService.overview(), opsService.alerts({ limit: 20 })]);
			setOverview(summary as OpsOverview);
			setAlerts(Array.isArray(alertList) ? (alertList as OpsAlert[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "运维概览加载失败");
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadData();
	}, []);

	return (
		<div className="mx-auto w-full max-w-none space-y-6 px-6 py-6">
			<PageHeader title="任务运行概览" description="监控任务执行与告警情况。" />

			<Row gutter={[16, 16]} className="mb-2">
				<Col xs={24} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="今日任务总数" value={overview?.totalRuns ?? "--"} />
					</Card>
				</Col>
				<Col xs={24} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="成功率" value={overview?.successRate ?? "--"} suffix="%" />
					</Card>
				</Col>
				<Col xs={24} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="异常告警" value={overview?.alerts ?? "--"} />
					</Card>
				</Col>
				<Col xs={24} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="正在运行" value={overview?.running ?? "--"} />
					</Card>
				</Col>
			</Row>

			<Card title="任务执行趋势 (近24小时)">
				<EmptyState title="暂无趋势数据" description="趋势分析将在接入运行统计后展示。" />
			</Card>

			<Card title="告警概览">
				{alerts.length ? (
					<Table size="small" pagination={false} dataSource={alerts} columns={alertColumns} rowKey={(row, idx) => `${row.type}-${idx}`} loading={loading} />
				) : (
					<EmptyState title="暂无告警" description="当前没有质量或任务告警。" />
				)}
			</Card>
		</div>
	);
}
