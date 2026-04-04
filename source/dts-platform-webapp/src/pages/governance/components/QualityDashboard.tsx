import { useEffect, useState } from "react";
import { Card, Col, Row, Spin, Statistic, Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import {
	CheckCircleOutlined,
	DatabaseOutlined,
	FileProtectOutlined,
	ToolOutlined,
} from "@ant-design/icons";
import { Chart } from "@/components/chart/chart";
import { getQualityDashboard } from "@/api/platformApi";
import type { QualityDashboard as QualityDashboardData } from "@/api/platformApi";
import { formatTime } from "@/utils/textUtils";

export default function QualityDashboard() {
	const [data, setData] = useState<QualityDashboardData | null>(null);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		getQualityDashboard()
			.then(setData)
			.catch(console.error)
			.finally(() => setLoading(false));
	}, []);

	if (loading) {
		return (
			<div className="flex items-center justify-center py-24">
				<Spin size="large" />
			</div>
		);
	}

	if (!data) {
		return (
			<div className="flex items-center justify-center py-24 text-gray-400">
				暂无数据
			</div>
		);
	}

	const trendOption = {
		tooltip: { trigger: "axis" as const },
		xAxis: {
			type: "category" as const,
			data: data.trend7d.map((item) => item.date),
		},
		yAxis: {
			type: "value" as const,
			min: 0,
			max: 100,
			axisLabel: { formatter: "{value}%" },
		},
		series: [
			{
				name: "通过率",
				type: "line" as const,
				data: data.trend7d.map((item) => item.passRate),
				smooth: true,
				areaStyle: { opacity: 0.15 },
				itemStyle: { color: "#1677ff" },
			},
		],
		grid: { left: 50, right: 24, top: 24, bottom: 32 },
	};

	const failingDatasetColumns: ColumnsType<{ name: string; failingRows: number }> = [
		{ title: "数据集", dataIndex: "name" },
		{
			title: "失败行数",
			dataIndex: "failingRows",
			align: "right",
			render: (v: number) => v.toLocaleString(),
		},
	];

	const recentFailedColumns: ColumnsType<{
		ruleName: string;
		dataset: string;
		time: string;
		status: string;
	}> = [
		{ title: "规则", dataIndex: "ruleName" },
		{ title: "数据集", dataIndex: "dataset" },
		{ title: "时间", dataIndex: "time", render: formatTime },
		{ title: "状态", dataIndex: "status" },
	];

	return (
		<div className="space-y-4">
			{/* Row 1: Stat Cards */}
			<Row gutter={[16, 16]}>
				<Col xs={24} sm={12} lg={6}>
					<Card>
						<Statistic
							title="规则总数"
							value={data.ruleCount}
							prefix={<FileProtectOutlined />}
						/>
					</Card>
				</Col>
				<Col xs={24} sm={12} lg={6}>
					<Card>
						<Statistic
							title="覆盖数据集"
							value={data.coveredDatasets}
							suffix={`/ ${data.totalDatasets}`}
							prefix={<DatabaseOutlined />}
						/>
					</Card>
				</Col>
				<Col xs={24} sm={12} lg={6}>
					<Card>
						<Statistic
							title="今日检查"
							value={data.todayPassed}
							suffix={
								<span className="text-sm font-normal text-gray-500">
									通过
									<span className="mx-1 text-red-500">{data.todayFailed}</span>
									失败
								</span>
							}
							prefix={<CheckCircleOutlined />}
							valueStyle={{ color: data.todayFailed > 0 ? undefined : "#52c41a" }}
						/>
					</Card>
				</Col>
				<Col xs={24} sm={12} lg={6}>
					<Card>
						<Statistic
							title="待修复行数"
							value={data.pendingFixRows}
							prefix={<ToolOutlined />}
							valueStyle={data.pendingFixRows > 0 ? { color: "#faad14" } : undefined}
						/>
					</Card>
				</Col>
			</Row>

			{/* Row 2: 7-day trend */}
			<Card title="近 7 日质量趋势">
				{data.trend7d.length > 0 ? (
					<Chart option={trendOption} height={280} />
				) : (
					<div className="flex items-center justify-center py-12 text-gray-400">
						暂无趋势数据
					</div>
				)}
			</Card>

			{/* Row 3: Two columns */}
			<Row gutter={[16, 16]}>
				<Col xs={24} lg={12}>
					<Card title="Top 5 问题数据集">
						<Table
							rowKey="name"
							dataSource={data.topFailingDatasets}
							columns={failingDatasetColumns}
							pagination={false}
							size="small"
							locale={{ emptyText: "暂无数据" }}
						/>
					</Card>
				</Col>
				<Col xs={24} lg={12}>
					<Card title="最近失败的检查">
						<Table
							rowKey={(_, index) => String(index)}
							dataSource={data.recentFailedRuns}
							columns={recentFailedColumns}
							pagination={false}
							size="small"
							locale={{ emptyText: "暂无数据" }}
						/>
					</Card>
				</Col>
			</Row>
		</div>
	);
}
