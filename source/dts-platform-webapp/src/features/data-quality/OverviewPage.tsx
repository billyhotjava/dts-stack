import { PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Col, Progress, Row, Skeleton, Space, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { getQualityDashboard, type QualityDashboard } from "@/api/platformApi";
import { CompactTable } from "@/components/table";
import { formatTime } from "@/utils/textUtils";
import { QualityMetric, QualityPageHeading, QualityStatus } from "./QualityShared";
import { qualityPath } from "./qualityRoutes";
import { useQualityMaintainerAccess } from "./useQualityAccess";

const EMPTY_DASHBOARD: QualityDashboard = {
	ruleCount: 0,
	coveredDatasets: 0,
	totalDatasets: 0,
	todayPassed: 0,
	todayFailed: 0,
	pendingFixRows: 0,
	trend7d: [],
	topFailingDatasets: [],
	recentFailedRuns: [],
};

export function OverviewPage() {
	const navigate = useNavigate();
	const canManage = useQualityMaintainerAccess();
	const [dashboard, setDashboard] = useState<QualityDashboard>(EMPTY_DASHBOARD);
	const [loading, setLoading] = useState(true);
	const [loadError, setLoadError] = useState("");

	const load = useCallback(async () => {
		setLoading(true);
		setLoadError("");
		try {
			const data = await getQualityDashboard();
			if (!data) throw new Error("质量概览未返回有效数据");
			setDashboard({ ...EMPTY_DASHBOARD, ...(data || {}) });
		} catch (error) {
			const message = error instanceof Error ? error.message : "质量概览加载失败";
			setLoadError(message);
			toast.error(message);
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		void load();
	}, [load]);

	const totalToday = dashboard.todayPassed + dashboard.todayFailed;
	const passRate = totalToday ? Math.round((dashboard.todayPassed / totalToday) * 100) : 0;
	const coverage = dashboard.totalDatasets
		? Math.round((dashboard.coveredDatasets / dashboard.totalDatasets) * 100)
		: 0;

	const failureColumns: ColumnsType<QualityDashboard["recentFailedRuns"][number]> = [
		{ title: "规则", dataIndex: "ruleName", ellipsis: true },
		{ title: "数据资产", dataIndex: "dataset", ellipsis: true },
		{ title: "运行时间", dataIndex: "time", width: 170, render: formatTime },
		{ title: "状态", dataIndex: "status", width: 100, render: (value) => <QualityStatus status={value} /> },
	];

	return (
		<div className="dq-page">
			<QualityPageHeading
				title="质量大盘"
				description="以默认数据湖为边界，集中查看质量规则覆盖、运行健康度与待处理异常。"
				actions={[
					<Button key="reload" icon={<ReloadOutlined />} loading={loading} onClick={() => void load()}>
						刷新
					</Button>,
					<Button
						key="new"
						type="primary"
						icon={<PlusOutlined />}
						disabled={!canManage}
						onClick={() => navigate(qualityPath("rule-editor"))}
					>
						新建规则
					</Button>,
				]}
			/>

			{loading ? (
				<Card>
					<Skeleton active paragraph={{ rows: 5 }} />
				</Card>
			) : loadError ? (
				<Alert
					showIcon
					type="error"
					message="质量概览加载失败"
					description={loadError}
					action={<Button onClick={() => void load()}>重试</Button>}
				/>
			) : (
				<>
					<div className="dq-metric-grid">
						<QualityMetric label="质量规则" value={dashboard.ruleCount} note="已登记规则总数" />
						<QualityMetric
							label="资产覆盖率"
							value={`${coverage}%`}
							note={`${dashboard.coveredDatasets}/${dashboard.totalDatasets} 个资产`}
							color="#13c2c2"
						/>
						<QualityMetric
							label="今日通过率"
							value={totalToday ? `${passRate}%` : "暂无运行"}
							note={totalToday ? `${dashboard.todayPassed} 通过 / ${dashboard.todayFailed} 失败` : "当天尚无质量运行"}
							color="#52c41a"
						/>
						<QualityMetric
							label="待修复数据"
							value={dashboard.pendingFixRows.toLocaleString()}
							note="失败记录行数"
							color="#ff4d4f"
						/>
					</div>

					<Row gutter={[16, 16]}>
						<Col xs={24} xl={15}>
							<Card
								title="近 7 天通过率"
								extra={
									<Button type="link" onClick={() => navigate(qualityPath("run-records"))}>
										查看运行记录
									</Button>
								}
							>
								<Space direction="vertical" size={13} style={{ width: "100%" }}>
									{dashboard.trend7d.length ? (
										dashboard.trend7d.map((item) => (
											<div
												key={item.date}
												style={{
													display: "grid",
													gridTemplateColumns: "100px 1fr 48px",
													gap: 12,
													alignItems: "center",
												}}
											>
												<span className="dq-muted">{item.date}</span>
												<Progress percent={Math.round(item.passRate)} showInfo={false} strokeColor="#1677ff" />
												<strong>{Math.round(item.passRate)}%</strong>
											</div>
										))
									) : (
										<div className="dq-muted">暂无趋势数据</div>
									)}
								</Space>
							</Card>
						</Col>
						<Col xs={24} xl={9}>
							<Card title="异常资产 TOP">
								<Space direction="vertical" size={12} style={{ width: "100%" }}>
									{dashboard.topFailingDatasets.length ? (
										dashboard.topFailingDatasets.map((item, index) => (
											<div
												key={`${item.name}-${index}`}
												style={{ display: "flex", justifyContent: "space-between", gap: 12 }}
											>
												<span>
													<Tag color={index < 3 ? "red" : "default"}>{index + 1}</Tag>
													{item.name}
												</span>
												<strong>{item.failingRows.toLocaleString()} 行</strong>
											</div>
										))
									) : (
										<div className="dq-muted">暂无异常资产</div>
									)}
								</Space>
							</Card>
						</Col>
					</Row>

					<Card title="最近失败运行">
						<CompactTable
							rowKey={(row) => `${row.ruleName}-${row.dataset}-${row.time}`}
							columns={failureColumns}
							dataSource={dashboard.recentFailedRuns}
							pagination={false}
						/>
					</Card>
				</>
			)}
		</div>
	);
}
