import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import type { ApexOptions } from "apexcharts";
import { Card, Col, Row, Select, Space, Statistic, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { Chart } from "@/components/chart/chart";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import opsService, {
	type OpsAlert,
	type OpsDevCenterMetrics,
	type OpsDevCenterTopFailure,
	type OpsOverview,
} from "@/api/services/opsService";

const alertColumns: ColumnsType<OpsAlert> = [
	{ title: "类型", dataIndex: "type", width: 120 },
	{ title: "规则", dataIndex: "ruleName", render: (value) => value || "-" },
	{ title: "状态", dataIndex: "status", width: 120 },
	{ title: "严重性", dataIndex: "severity", width: 120, render: (value) => value || "-" },
	{ title: "描述", dataIndex: "message", render: (value) => value || "-" },
];

const topFailureColumns: ColumnsType<OpsDevCenterTopFailure> = [
	{ title: "入口", dataIndex: "entryKey", width: 160, render: (value) => value || "-" },
	{ title: "项目空间", dataIndex: "planName", width: 180, render: (value) => value || "-" },
	{ title: "作业", dataIndex: "artifactName", render: (value) => value || "-" },
	{ title: "总运行", dataIndex: "totalRuns", width: 110, align: "right" },
	{ title: "失败数", dataIndex: "failedRuns", width: 110, align: "right" },
	{ title: "失败率", dataIndex: "failureRate", width: 120, align: "right", render: (value) => (value == null ? "-" : `${value}%`) },
];

export default function OpsOverviewPage() {
	const [overview, setOverview] = useState<OpsOverview | null>(null);
	const [metrics, setMetrics] = useState<OpsDevCenterMetrics | null>(null);
	const [alerts, setAlerts] = useState<OpsAlert[]>([]);
	const [days, setDays] = useState<number>(7);
	const [entryKey, setEntryKey] = useState<string | undefined>(undefined);
	const [ownerDept, setOwnerDept] = useState<string | undefined>(undefined);
	const [planId, setPlanId] = useState<string | undefined>(undefined);
	const [loading, setLoading] = useState(false);

	const loadData = async () => {
		setLoading(true);
		try {
			const [summary, alertList, metricPayload] = await Promise.all([
				opsService.overview(),
				opsService.alerts({ limit: 20 }),
				opsService.devCenterMetrics({
					days,
					entryKey,
					ownerDept,
					planId,
				}),
			]);
			setOverview(summary as OpsOverview);
			setMetrics(metricPayload as OpsDevCenterMetrics);
			setAlerts(Array.isArray(alertList) ? (alertList as OpsAlert[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "运维概览加载失败");
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadData();
	}, [days, entryKey, ownerDept, planId]);

	const trend = metrics?.trend ?? [];
	const topFailures = metrics?.topFailures ?? [];
	const ownerDeptOptions = (metrics?.availableOwnerDepts ?? []).map((dept) => ({
		label: dept,
		value: dept,
	}));
	const planOptions = (metrics?.availablePlans ?? []).map((plan) => ({
		label: plan.name ? `${plan.name} (${plan.id.slice(0, 8)})` : plan.id,
		value: plan.id,
	}));

	const trendSeries = useMemo(
		() => [
			{ name: "总运行", data: trend.map((item) => item.totalRuns ?? 0) },
			{ name: "成功", data: trend.map((item) => item.successRuns ?? 0) },
			{ name: "失败", data: trend.map((item) => item.failedRuns ?? 0) },
		],
		[trend],
	);

	const trendOptions = useMemo<ApexOptions>(
		() => ({
			chart: {
				type: "line",
				toolbar: { show: false },
			},
			stroke: {
				curve: "smooth",
				width: 2,
			},
			markers: { size: 3 },
			xaxis: {
				categories: trend.map((item) => item.date),
			},
			yaxis: {
				min: 0,
				forceNiceScale: true,
			},
			legend: {
				position: "top",
				horizontalAlign: "left",
			},
			colors: ["#1677ff", "#52c41a", "#ff4d4f"],
			grid: {
				borderColor: "#f0f0f0",
			},
		}),
		[trend],
	);

	return (
		<div className="mx-auto w-full max-w-none space-y-6 px-6 py-6">
			<PageHeader title="任务运行概览" description="监控任务执行与告警情况。" />

			<Row gutter={[16, 16]} className="mb-2">
				<Col xs={24} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="近24小时任务总数" value={overview?.totalRuns ?? "--"} />
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

			<Row gutter={[16, 16]}>
				<Col xs={24} md={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="失败率" value={metrics?.summary?.failureRate ?? "--"} suffix="%" />
					</Card>
				</Col>
				<Col xs={24} md={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="重试率" value={metrics?.summary?.retryRate ?? "--"} suffix="%" />
					</Card>
				</Col>
				<Col xs={24} md={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="MTTR(分钟)" value={metrics?.summary?.mttrMinutes ?? "--"} />
					</Card>
				</Col>
				<Col xs={24} md={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="发布成功率" value={metrics?.summary?.releaseSuccessRate ?? "--"} suffix="%" />
					</Card>
				</Col>
			</Row>

			<Card
				title="任务执行趋势（开发中心）"
				extra={
					<Space wrap>
						<Select
							style={{ width: 120 }}
							value={days}
							onChange={(value) => setDays(value)}
							options={[
								{ label: "近7天", value: 7 },
								{ label: "近14天", value: 14 },
								{ label: "近30天", value: 30 },
							]}
						/>
						<Select
							style={{ width: 160 }}
							allowClear
							placeholder="入口类型"
							value={entryKey}
							onChange={(value) => setEntryKey(value || undefined)}
							options={[
								{ label: "INGESTION_TASK", value: "INGESTION_TASK" },
								{ label: "AIRFLOW_DAG", value: "AIRFLOW_DAG" },
								{ label: "DBT_RUN", value: "DBT_RUN" },
							]}
						/>
						<Select
							style={{ width: 200 }}
							allowClear
							placeholder="所属部门"
							value={ownerDept}
							onChange={(value) => setOwnerDept(value || undefined)}
							options={ownerDeptOptions}
						/>
						<Select
							style={{ width: 260 }}
							allowClear
							showSearch
							placeholder="项目空间"
							value={planId}
							onChange={(value) => setPlanId(value || undefined)}
							options={planOptions}
							optionFilterProp="label"
						/>
					</Space>
				}
			>
				{trend.length ? (
					<Chart type="line" height={320} options={trendOptions} series={trendSeries} />
				) : (
					<EmptyState title="暂无趋势数据" description="当前筛选条件下没有可用运行数据。" />
				)}
			</Card>

			<Card title="失败作业 Top 10">
				<Table
					size="small"
					pagination={false}
					dataSource={topFailures}
					columns={topFailureColumns}
					rowKey={(row, idx) => `${row.entryKey || "unknown"}-${row.artifactName || "artifact"}-${idx}`}
					loading={loading}
					locale={{ emptyText: <EmptyState title="暂无失败作业" description="当前时间窗口内无失败记录。" /> }}
				/>
				<Typography.Text type="secondary">
					口径：失败率 = 失败数 / 总运行；重试率与 MTTR 基于同入口同作业（含项目空间）的连续运行记录估算。
				</Typography.Text>
			</Card>

			<Card title="告警概览">
				{alerts.length ? (
					<Table
						size="small"
						pagination={false}
						dataSource={alerts}
						columns={alertColumns}
						rowKey={(row, idx) => `${row.type}-${idx}`}
						loading={loading}
					/>
				) : (
					<EmptyState title="暂无告警" description="当前没有质量或任务告警。" />
				)}
			</Card>
		</div>
	);
}
