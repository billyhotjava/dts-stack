import { DownloadOutlined, EyeOutlined, FileAddOutlined, ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Form, Input, Radio, Select, Space, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";
import { getQualityScore, listQualityRules, type QualityScoreResult } from "@/api/platformApi";
import { Chart } from "@/components/chart/chart";
import { CompactTable } from "@/components/table";
import { QualityMetric, QualityPageHeading, QualityStatus, UnavailableCapability } from "./QualityShared";
import { qualityPath } from "./qualityRoutes";
import { displayName, hasEffectiveQualityScore, type QualityRule, toList } from "./qualityTypes";
import { useDefaultLakeDatasets } from "./useDefaultLakeDatasets";

const PERIOD_OPTIONS = [
	{ label: "近 7 天", value: 7 },
	{ label: "近 30 天", value: 30 },
	{ label: "近 90 天", value: 90 },
];

function LiveQualityReport({ preview = false }: { preview?: boolean }) {
	const navigate = useNavigate();
	const [searchParams, setSearchParams] = useSearchParams();
	const { datasets, loading: datasetsLoading, message, lakeName } = useDefaultLakeDatasets();
	const [datasetId, setDatasetId] = useState(searchParams.get("datasetId") || "");
	const [periodDays, setPeriodDays] = useState(Number(searchParams.get("periodDays") || 30));
	const [score, setScore] = useState<QualityScoreResult>();
	const [rules, setRules] = useState<QualityRule[]>([]);
	const [loading, setLoading] = useState(false);
	const [loadError, setLoadError] = useState("");
	const loadSequence = useRef(0);
	const activeDatasetId = useRef(datasetId);
	const activePeriodDays = useRef(periodDays);
	activeDatasetId.current = datasetId;
	activePeriodDays.current = periodDays;

	useEffect(() => {
		if (!datasetId && datasets[0]?.id) setDatasetId(datasets[0].id);
	}, [datasetId, datasets]);

	useEffect(() => {
		void listQualityRules()
			.then((response) => setRules(toList<QualityRule>(response)))
			.catch(() => setRules([]));
	}, []);

	const load = useCallback(async () => {
		const requestedDatasetId = datasetId;
		const requestedPeriodDays = periodDays;
		const sequence = ++loadSequence.current;
		const isCurrentRequest = () =>
			sequence === loadSequence.current &&
			activeDatasetId.current === requestedDatasetId &&
			activePeriodDays.current === requestedPeriodDays;
		if (!requestedDatasetId) {
			if (!isCurrentRequest()) return;
			setScore(undefined);
			setLoadError("");
			setLoading(false);
			return;
		}
		setLoading(true);
		setLoadError("");
		setScore(undefined);
		try {
			const nextScore = await getQualityScore(requestedDatasetId, requestedPeriodDays);
			if (!isCurrentRequest()) return;
			if (!nextScore) throw new Error("质量评分未返回有效数据");
			setScore(nextScore);
			const next = new URLSearchParams();
			next.set("datasetId", requestedDatasetId);
			next.set("periodDays", String(requestedPeriodDays));
			setSearchParams(next, { replace: true });
		} catch (error) {
			if (!isCurrentRequest()) return;
			const message = error instanceof Error ? error.message : "质量报告加载失败";
			setLoadError(message);
			toast.error(message);
			setScore(undefined);
		} finally {
			if (isCurrentRequest()) setLoading(false);
		}
	}, [datasetId, periodDays, setSearchParams]);

	useEffect(() => {
		void load();
	}, [load]);

	const reportRules = useMemo(() => rules.filter((rule) => String(rule.datasetId) === datasetId), [datasetId, rules]);
	const selectedDataset = datasets.find((item) => item.id === datasetId);
	const hasEffectiveRuns = hasEffectiveQualityScore(score);
	const trendOption = useMemo(
		() =>
			score?.trend?.length
				? {
						tooltip: { trigger: "axis" as const },
						xAxis: { type: "category" as const, data: score.trend.map((item) => item.date) },
						yAxis: { type: "value" as const, min: 0, max: 100 },
						series: [
							{
								name: "综合评分",
								type: "line" as const,
								smooth: true,
								data: score.trend.map((item) => item.overall),
								itemStyle: { color: "#1677ff" },
								areaStyle: { color: "rgba(22,119,255,.12)" },
							},
						],
						grid: { left: 46, right: 20, top: 28, bottom: 32 },
					}
				: undefined,
		[score],
	);

	const exportReport = () => {
		if (!datasetId) return;
		window.open(
			`/api/governance/quality/report/export?datasetId=${encodeURIComponent(datasetId)}&periodDays=${periodDays}`,
			"_blank",
			"noopener,noreferrer",
		);
	};

	const ruleColumns: ColumnsType<QualityRule> = [
		{ title: "规则名称", dataIndex: "name", render: (value) => displayName(value) },
		{
			title: "质量维度",
			dataIndex: "type",
			width: 130,
			render: (value) => <Tag color="blue">{displayName(value)}</Tag>,
		},
		{ title: "严重性", dataIndex: "severity", width: 110, render: (value) => <Tag>{displayName(value)}</Tag> },
		{
			title: "启用状态",
			dataIndex: "enabled",
			width: 100,
			render: (value) => <QualityStatus status={Boolean(value)} />,
		},
		{
			title: "当前版本",
			width: 150,
			render: (_, row) => (
				<Space size={4}>
					<Tag color="blue">v{row.latestVersion?.version || "-"}</Tag>
					<QualityStatus status={row.latestVersion?.status} />
				</Space>
			),
		},
	];

	return (
		<div className="dq-page">
			<QualityPageHeading
				title={preview ? "即时质量报告预览" : "质量报告"}
				description={
					preview
						? "使用当前真实评分数据预览即时报告，不创建持久化报告模板。"
						: "按默认数据湖资产生成即时质量评分，并导出当前 Excel 报告。"
				}
				actions={
					preview ? (
						<Button
							onClick={() =>
								navigate(`${qualityPath("report")}${searchParams.toString() ? `?${searchParams.toString()}` : ""}`)
							}
						>
							返回质量报告
						</Button>
					) : (
						[
							<Button
								key="preview"
								icon={<EyeOutlined />}
								onClick={() => navigate(`${qualityPath("report-preview")}?${searchParams.toString()}`)}
							>
								报告预览
							</Button>,
							<UnavailableCapability key="template" capability="report-template" compact />,
						]
					)
				}
			/>
			{message ? (
				<Alert showIcon type="warning" message="默认数据湖资产不可用" description={message} />
			) : (
				<Alert showIcon type="info" message={`当前数据来源：${lakeName}`} />
			)}
			<Card size="small">
				<Space wrap>
					<Select
						showSearch
						optionFilterProp="label"
						loading={datasetsLoading}
						disabled={Boolean(message)}
						placeholder="选择数据资产"
						value={datasetId || undefined}
						onChange={setDatasetId}
						options={datasets.map((item) => ({ value: item.id, label: item.name }))}
						style={{ width: 300 }}
					/>
					<Radio.Group
						optionType="button"
						buttonStyle="solid"
						options={PERIOD_OPTIONS}
						value={periodDays}
						onChange={(event) => setPeriodDays(event.target.value)}
					/>
					<Button icon={<ReloadOutlined />} loading={loading} disabled={!datasetId} onClick={() => void load()}>
						刷新
					</Button>
					<Button icon={<DownloadOutlined />} disabled={!datasetId} onClick={exportReport}>
						导出 Excel
					</Button>
				</Space>
			</Card>
			{!loading && loadError ? (
				<Alert
					showIcon
					type="error"
					message="质量报告加载失败"
					description={loadError}
					action={<Button onClick={() => void load()}>重试</Button>}
				/>
			) : (
				<>
					{datasetId && !loading && score && !hasEffectiveRuns ? (
						<Alert
							showIcon
							type="info"
							message="暂无有效检测结果"
							description="该资产在所选周期内没有可用于评分的质量运行。"
						/>
					) : null}
					<div className="dq-metric-grid">
						<QualityMetric
							label="综合质量分"
							value={hasEffectiveRuns ? score?.overall : "-"}
							note={hasEffectiveRuns ? selectedDataset?.name || "请选择资产" : "暂无有效检测结果"}
						/>
						{(hasEffectiveRuns ? score?.dimensions || [] : []).slice(0, 3).map((item) => (
							<QualityMetric
								key={item.type}
								label={item.type}
								value={item.score}
								note={item.delta == null ? "暂无环比" : `环比 ${item.delta > 0 ? "+" : ""}${item.delta}`}
								color="#13c2c2"
							/>
						))}
					</div>
					<Card title={`评分趋势（近 ${periodDays} 天）`} loading={loading}>
						{trendOption ? (
							<Chart option={trendOption} height={300} />
						) : (
							<div className="dq-muted">暂无有效评分趋势</div>
						)}
					</Card>
				</>
			)}
			<Card title="规则明细">
				<CompactTable rowKey="id" columns={ruleColumns} dataSource={reportRules} pagination={{ pageSize: 10 }} />
			</Card>
			{!preview ? <UnavailableCapability capability="quality-subscription" title="报告订阅暂未开放" /> : null}
		</div>
	);
}

export function ReportPage() {
	return <LiveQualityReport />;
}

export function ReportPreviewPage() {
	return <LiveQualityReport preview />;
}

export function ReportEditorPage() {
	return (
		<div className="dq-page">
			<QualityPageHeading
				title="报告模板编辑"
				description="报告模板、持久化布局和定时发送尚无后端合同。"
				actions={<UnavailableCapability capability="report-template" compact />}
			/>
			<UnavailableCapability capability="report-template" title="报告模板编辑暂未开放" />
			<Card title="规划中的模板字段">
				<Form layout="vertical" disabled>
					<Form.Item label="模板名称">
						<Input prefix={<FileAddOutlined />} placeholder="暂未开放" />
					</Form.Item>
					<Form.Item label="报告范围">
						<Select placeholder="暂未开放" />
					</Form.Item>
					<Form.Item label="订阅渠道">
						<Select mode="multiple" placeholder="暂未开放" />
					</Form.Item>
					<Button type="primary" disabled>
						暂未开放
					</Button>
				</Form>
			</Card>
		</div>
	);
}
