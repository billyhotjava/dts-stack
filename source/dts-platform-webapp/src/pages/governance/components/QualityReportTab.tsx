import { CheckCircleOutlined, CloseCircleOutlined, } from "@ant-design/icons";
import { Alert, Button, Card, Col, Radio, Row, Select, Spin, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";
import { type DefaultDestinationStatus, ingestionTaskAPI } from "@/api/ingestion";
import type { QualityScoreResult, RuleRunHistory } from "@/api/platformApi";
import { getQualityScore, getRuleHistory, listDatasets, listQualityRules } from "@/api/platformApi";
import { Chart } from "@/components/chart/chart";
import { CompactTable } from "@/components/table";
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

const isPassedStatus = (status?: string) =>
	["PASSED", "SUCCESS", "SUCCEEDED", "COMPLETED"].includes(String(status || "").toUpperCase());

/* ---------- Score Card ---------- */
function ScoreCard({ label, score, delta }: { label: string; score: number; delta: number | null }) {
	return (
		<Card className="text-center" hoverable>
			<div className="text-3xl font-bold">{score}</div>
			<div className="mt-1 text-sm text-gray-500">{label}</div>
			{delta != null ? (
				<div
					className={`mt-1 text-sm font-medium ${delta > 0 ? "text-green-500" : delta < 0 ? "text-red-500" : "text-gray-400"}`}
				>
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
	const navigate = useNavigate();
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
			render: (v: string) => {
				if (v === "SKIPPED") {
					return <Tag>跳过</Tag>;
				}
				return v === "PASSED" ? (
					<CheckCircleOutlined className="text-green-500" />
				) : (
					<CloseCircleOutlined className="text-red-500" />
				);
			},
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
					<Typography.Link onClick={() => navigate(`/governance/rules?tab=repair&runId=${record.runId}`)}>去修复</Typography.Link>
				) : null,
		},
	];

	return <CompactTable rowKey="runId" dataSource={rows} columns={cols} pagination={false} size="small" />;
}

/* ---------- Main Component ---------- */
export default function QualityReportTab() {
	const [searchParams, setSearchParams] = useSearchParams();
	const [datasets, setDatasets] = useState<DatasetOption[]>([]);
	const [datasetId, setDatasetId] = useState<string | undefined>(() => searchParams.get("datasetId") || undefined);
	const [defaultLake, setDefaultLake] = useState<DefaultDestinationStatus | null>(null);
	const [datasetLoadMessage, setDatasetLoadMessage] = useState<string>();
	const [periodDays, setPeriodDays] = useState(30);
	const [scoreData, setScoreData] = useState<QualityScoreResult | null>(null);
	const [scoreLoading, setScoreLoading] = useState(false);
	const [rules, setRules] = useState<RuleRow[]>([]);
	const [rulesLoading, setRulesLoading] = useState(false);
	const [expandedKeys, setExpandedKeys] = useState<string[]>([]);

	/* load datasets */
	useEffect(() => {
		let cancelled = false;
		const loadDefaultLakeDatasets = async () => {
			const linkedDatasetId = searchParams.get("datasetId") || undefined;
			try {
				const lake = await ingestionTaskAPI.getDefaultDestinationStatus();
				if (cancelled) return;
				setDefaultLake(lake);
				if (!lake?.available || !lake.dataSourceId) {
					setDatasets([]);
					setDatasetId(linkedDatasetId);
					setDatasetLoadMessage(lake?.message || "未解析到默认数据湖连接");
					return;
				}
				const resp: any = await listDatasets({
					page: 0,
					size: 200,
					enabledOnly: true,
					sourceId: lake.dataSourceId,
				});
				if (cancelled) return;
				const list = Array.isArray(resp?.content) ? resp.content : [];
				const options = list.map((d: any) => ({ id: String(d.id), name: d.name || d.id }));
				setDatasets(options);
				setDatasetLoadMessage(options.length ? undefined : "默认数据湖连接下暂无可用数据集");
			} catch (err: any) {
				if (cancelled) return;
				setDefaultLake(null);
				setDatasets([]);
				setDatasetId(linkedDatasetId);
				setDatasetLoadMessage(err?.message || "默认数据湖连接读取失败");
			}
		};
		void loadDefaultLakeDatasets();
		return () => {
			cancelled = true;
		};
	}, []);

	useEffect(() => {
		const linkedDatasetId = searchParams.get("datasetId") || undefined;
		setDatasetId((prev) => (prev === linkedDatasetId ? prev : linkedDatasetId));
	}, [searchParams]);

	const handleDatasetChange = (nextDatasetId?: string) => {
		setDatasetId(nextDatasetId);
		const params = new URLSearchParams(searchParams);
		params.set("tab", "report");
		if (nextDatasetId) {
			params.set("datasetId", nextDatasetId);
		} else {
			params.delete("datasetId");
		}
		setSearchParams(params, { replace: true });
	};

	/* auto-select first dataset from default data lake only */
	useEffect(() => {
		if (datasetId && datasets.some((item) => item.id === datasetId)) {
			return;
		}
		if (datasetId && datasets.length === 0) {
			return;
		}
		if (datasets.length > 0) {
			setDatasetId(datasets[0].id);
		} else if (datasetId) {
			setDatasetId(undefined);
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

	/* export handler */
	const handleExport = useCallback(() => {
		if (!datasetId) return;
		const url = `/api/governance/quality/report/export?datasetId=${datasetId}&periodDays=${periodDays}`;
		window.open(url, "_blank");
	}, [datasetId, periodDays]);

	/* filter rules by dataset */
	const filteredRules = useMemo(
		() => (datasetId ? rules.filter((r) => String(r.datasetId) === datasetId) : rules),
		[rules, datasetId],
	);
	const hasEffectiveRuns = Boolean(scoreData && (scoreData.dimensions.length > 0 || scoreData.trend.length > 0));

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
		{
			title: "规则名称",
			dataIndex: "name",
			render: (v) => v || "-",
			sorter: (a, b) => (a.name || "").localeCompare(b.name || ""),
		},
		{
			title: "类型",
			dataIndex: "type",
			width: 100,
			render: (v: string) => <Tag color={DIMENSION_COLORS[v] || undefined}>{DIMENSION_LABELS[v] || v || "-"}</Tag>,
		},
		{
			title: "最近结果",
			width: 100,
			render: (_, record) => {
				const status = record.latestRun?.status;
				if (!status) return <span className="text-gray-400">-</span>;
				return isPassedStatus(status) ? (
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
							prev.includes(record.id) ? prev.filter((k) => k !== record.id) : [...prev, record.id],
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
					onChange={handleDatasetChange}
					options={datasets.map((d) => ({ label: d.name, value: d.id }))}
				/>
				<Radio.Group
					optionType="button"
					buttonStyle="solid"
					options={PERIOD_OPTIONS}
					value={periodDays}
					onChange={(e) => setPeriodDays(e.target.value)}
				/>
				<Button disabled={!datasetId} onClick={handleExport}>
					导出报告
				</Button>
			</div>
			<Alert
				type={datasetLoadMessage ? "warning" : "info"}
				showIcon
				message={
					defaultLake?.destinationName
						? `当前数据来源：默认数据湖（${defaultLake.destinationName}）`
						: "当前数据来源：默认数据湖"
				}
				description={datasetLoadMessage}
			/>

			{/* Row 1: Score Cards */}
			{scoreLoading ? (
				<div className="flex items-center justify-center py-12">
					<Spin size="large" />
				</div>
			) : scoreData && hasEffectiveRuns ? (
				<Row gutter={[16, 16]}>
					<Col xs={24} sm={12} md={8} lg={4}>
						<ScoreCard label="综合评分" score={scoreData.overall} delta={scoreData.overallDelta} />
					</Col>
					{scoreData.dimensions.map((dim) => (
						<Col key={dim.type} xs={24} sm={12} md={8} lg={4}>
							<ScoreCard label={DIMENSION_LABELS[dim.type] || dim.type} score={dim.score} delta={dim.delta} />
						</Col>
					))}
				</Row>
			) : (
				<div className="flex items-center justify-center py-12 text-gray-400">
					{datasetId ? "暂无有效检测结果" : "请选择数据集"}
				</div>
			)}

			{/* Row 2: Trend Chart */}
			<Card title={`评分趋势 (近 ${periodDays} 天)`}>
				{trendOption ? (
					<Chart option={trendOption} height={300} />
				) : (
					<div className="flex items-center justify-center py-12 text-gray-400">暂无趋势数据</div>
				)}
			</Card>

			{/* Row 3: Rule Detail Table */}
			<Card title="规则明细">
				<CompactTable
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
