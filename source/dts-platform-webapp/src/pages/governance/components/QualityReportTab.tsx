import { useCallback, useEffect, useMemo, useState } from "react";
import { Card, Col, Radio, Row, Select, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { CheckCircleOutlined, CloseCircleOutlined } from "@ant-design/icons";
import { toast } from "sonner";
import { Chart } from "@/components/chart/chart";
import {
	getQualityScore,
	listQualityRules,
	getRuleHistory,
	listDatasets,
} from "@/api/platformApi";
import type { QualityScoreResult, RuleRunHistory } from "@/api/platformApi";
import { formatTime } from "@/utils/textUtils";

const DIMENSION_LABELS: Record<string, string> = {
	COMPLETENESS: "完整性",
	CONSISTENCY: "一致性",
	ACCURACY: "准确性",
	UNIQUENESS: "唯一性",
	TIMELINESS: "及时性",
};

const DIMENSION_COLORS: Record<string, string> = {
	COMPLETENESS: "#1677ff",
	CONSISTENCY: "#722ed1",
	ACCURACY: "#13c2c2",
	UNIQUENESS: "#52c41a",
	TIMELINESS: "#fa8c16",
};

const PERIOD_OPTIONS = [
	{ label: "近 7 天", value: 7 },
	{ label: "近 30 天", value: 30 },
	{ label: "近 90 天", value: 90 },
];

type DatasetOption = { id: string; name: string };

type RuleRow = {
	id: string;
	name: string;
	type?: string;
	enabled?: boolean;
	datasetId?: string;
	latestRun?: { status?: string; passRate?: number; failingRows?: number };
};

/* ---------- Score Card ---------- */
function ScoreCard({
	label,
	score,
	delta,
}: {
	label: string;
	score: number;
	delta: number | null;
}) {
	return (
		<Card className="text-center" hoverable>
			<div className="text-3xl font-bold">{score}</div>
			<div className="mt-1 text-sm text-gray-500">{label}</div>
			{delta != null ? (
				<div className={`mt-1 text-sm font-medium ${delta > 0 ? "text-green-500" : delta < 0 ? "text-red-500" : "text-gray-400"}`}>
					{delta > 0 ? "↑" : delta < 0 ? "↓" : "—"}
					{delta !== 0 ? Math.abs(delta) : ""}
				</div>
			) : (
				<div className="mt-1 text-sm text-gray-400">—</div>
			)}
		</Card>
	);
}

/* ---------- Inline History ---------- */
function RuleHistoryInline({ ruleId }: { ruleId: string }) {
	const [rows, setRows] = useState<RuleRunHistory[]>([]);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		let cancelled = false;
		getRuleHistory(ruleId, 10)
			.then((data) => {
				if (!cancelled) setRows(Array.isArray(data) ? data : []);
			})
			.catch(() => {
				if (!cancelled) setRows([]);
			})
			.finally(() => {
				if (!cancelled) setLoading(false);
			});
		return () => {
			cancelled = true;
		};
	}, [ruleId]);

	if (loading) return <Spin size="small" />;
	if (rows.length === 0) return <Typography.Text type="secondary">暂无执行记录</Typography.Text>;

	const cols: ColumnsType<RuleRunHistory> = [
		{ title: "时间", dataIndex: "time", width: 180, render: formatTime },
		{
			title: "结果",
			dataIndex: "status",
			width: 80,
			render: (v: string) =>
				v === "PASSED" ? (
					<CheckCircleOutlined className="text-green-500" />
				) : (
					<CloseCircleOutlined className="text-red-500" />
				),
		},
		{
			title: "通过率",
			dataIndex: "passRate",
			width: 100,
			render: (v: number) => (v != null ? `${v}%` : "-"),
		},
		{
			title: "失败行数",
			dataIndex: "failingRows",
			width: 100,
			render: (v: number) => (v != null ? v.toLocaleString() : "-"),
		},
		{
			title: "操作",
			width: 80,
			render: (_, record) =>
				record.status === "FAILED" ? (
					<Typography.Link>去修复</Typography.Link>
				) : null,
		},
	];

	return (
		<Table
			rowKey="runId"
			dataSource={rows}
			columns={cols}
			pagination={false}
			size="small"
		/>
	);
}

/* ---------- Main Component ---------- */
export default function QualityReportTab() {
	const [datasets, setDatasets] = useState<DatasetOption[]>([]);
	const [datasetId, setDatasetId] = useState<string>();
	const [periodDays, setPeriodDays] = useState(30);
	const [scoreData, setScoreData] = useState<QualityScoreResult | null>(null);
	const [scoreLoading, setScoreLoading] = useState(false);
	const [rules, setRules] = useState<RuleRow[]>([]);
	const [rulesLoading, setRulesLoading] = useState(false);
	const [expandedKeys, setExpandedKeys] = useState<string[]>([]);

	/* load datasets */
	useEffect(() => {
		listDatasets({ page: 0, size: 200 })
			.then((resp: any) => {
				const list = Array.isArray(resp?.content) ? resp.content : [];
				setDatasets(list.map((d: any) => ({ id: String(d.id), name: d.name || d.id })));
			})
			.catch(() => {});
	}, []);

	/* auto-select first dataset */
	useEffect(() => {
		if (!datasetId && datasets.length > 0) {
			setDatasetId(datasets[0].id);
		}
	}, [datasets, datasetId]);

	/* load score */
	const loadScore = useCallback(async () => {
		if (!datasetId) return;
		setScoreLoading(true);
		try {
			const result = await getQualityScore(datasetId, periodDays);
			setScoreData(result as QualityScoreResult);
		} catch (err: any) {
			toast.error(err?.message || "评分加载失败");
			setScoreData(null);
		} finally {
			setScoreLoading(false);
		}
	}, [datasetId, periodDays]);

	useEffect(() => {
		void loadScore();
	}, [loadScore]);

	/* load rules */
	useEffect(() => {
		setRulesLoading(true);
		listQualityRules()
			.then((data) => setRules(Array.isArray(data) ? (data as RuleRow[]) : []))
			.catch(() => setRules([]))
			.finally(() => setRulesLoading(false));
	}, []);

	/* filter rules by dataset */
	const filteredRules = useMemo(
		() => (datasetId ? rules.filter((r) => String(r.datasetId) === datasetId) : rules),
		[rules, datasetId],
	);

	/* ---------- Trend Chart Option ---------- */
	const trendOption = useMemo(() => {
		const trend = scoreData?.trend ?? [];
		if (trend.length === 0) return null;

		return {
			tooltip: { trigger: "axis" as const },
			legend: { bottom: 0 },
			xAxis: {
				type: "category" as const,
				data: trend.map((t) => t.date),
			},
			yAxis: {
				type: "value" as const,
				min: 0,
				max: 100,
				axisLabel: { formatter: "{value}" },
			},
			series: [
				{
					name: "综合评分",
					type: "line" as const,
					data: trend.map((t) => t.overall),
					smooth: true,
					itemStyle: { color: "#1677ff" },
					lineStyle: { width: 3 },
				},
			],
			grid: { left: 50, right: 24, top: 24, bottom: 48 },
		};
	}, [scoreData]);

	/* ---------- Rule Table Columns ---------- */
	const ruleColumns: ColumnsType<RuleRow> = [
		{ title: "规则名称", dataIndex: "name", render: (v) => v || "-" },
		{
			title: "类型",
			dataIndex: "type",
			width: 100,
			render: (v: string) => (
				<Tag color={DIMENSION_COLORS[v] || undefined}>
					{DIMENSION_LABELS[v] || v || "-"}
				</Tag>
			),
		},
		{
			title: "最近结果",
			width: 100,
			render: (_, record) => {
				const status = record.latestRun?.status;
				if (!status) return <span className="text-gray-400">-</span>;
				return status === "PASSED" ? (
					<CheckCircleOutlined className="text-lg text-green-500" />
				) : (
					<CloseCircleOutlined className="text-lg text-red-500" />
				);
			},
		},
		{
			title: "通过率(%)",
			width: 110,
			render: (_, record) => {
				const rate = record.latestRun?.passRate;
				return rate != null ? `${rate}%` : "-";
			},
		},
		{
			title: "失败行数",
			width: 110,
			render: (_, record) => {
				const rows = record.latestRun?.failingRows;
				return rows != null ? rows.toLocaleString() : "-";
			},
		},
		{
			title: "操作",
			width: 120,
			render: (_, record) => (
				<Typography.Link
					onClick={() =>
						setExpandedKeys((prev) =>
							prev.includes(record.id)
								? prev.filter((k) => k !== record.id)
								: [...prev, record.id],
						)
					}
				>
					{expandedKeys.includes(record.id) ? "收起历史" : "查看历史"}
				</Typography.Link>
			),
		},
	];

	/* ---------- Render ---------- */
	return (
		<div className="space-y-4">
			{/* Filters */}
			<div className="flex flex-wrap items-center gap-3">
				<Select
					style={{ width: 260 }}
					placeholder="选择数据集"
					allowClear
					showSearch
					optionFilterProp="label"
					value={datasetId}
					onChange={setDatasetId}
					options={datasets.map((d) => ({ label: d.name, value: d.id }))}
				/>
				<Radio.Group
					optionType="button"
					buttonStyle="solid"
					options={PERIOD_OPTIONS}
					value={periodDays}
					onChange={(e) => setPeriodDays(e.target.value)}
				/>
			</div>

			{/* Row 1: Score Cards */}
			{scoreLoading ? (
				<div className="flex items-center justify-center py-12">
					<Spin size="large" />
				</div>
			) : scoreData ? (
				<Row gutter={[16, 16]}>
					<Col xs={24} sm={12} md={8} lg={4}>
						<ScoreCard
							label="综合评分"
							score={scoreData.overall}
							delta={scoreData.overallDelta}
						/>
					</Col>
					{scoreData.dimensions.map((dim) => (
						<Col key={dim.type} xs={24} sm={12} md={8} lg={4}>
							<ScoreCard
								label={DIMENSION_LABELS[dim.type] || dim.type}
								score={dim.score}
								delta={dim.delta}
							/>
						</Col>
					))}
				</Row>
			) : (
				<div className="flex items-center justify-center py-12 text-gray-400">
					{datasetId ? "暂无评分数据" : "请选择数据集"}
				</div>
			)}

			{/* Row 2: Trend Chart */}
			<Card title={`评分趋势 (近 ${periodDays} 天)`}>
				{trendOption ? (
					<Chart option={trendOption} height={300} />
				) : (
					<div className="flex items-center justify-center py-12 text-gray-400">
						暂无趋势数据
					</div>
				)}
			</Card>

			{/* Row 3: Rule Detail Table */}
			<Card title="规则明细">
				<Table
					rowKey="id"
					columns={ruleColumns}
					dataSource={filteredRules}
					loading={rulesLoading}
					pagination={{ showSizeChanger: true, defaultPageSize: 10 }}
					expandable={{
						expandedRowKeys: expandedKeys,
						expandedRowRender: (record) => <RuleHistoryInline ruleId={record.id} />,
						showExpandColumn: false,
					}}
				/>
			</Card>
		</div>
	);
}
