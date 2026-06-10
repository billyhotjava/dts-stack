import { useEffect, useMemo, useState } from "react";
import { Alert, Card, Select, Space, Tag } from "antd";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { useSearchParams } from "react-router";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { getDatasetQuality, listDatasets, listQualityRuns } from "@/api/platformApi";

type DatasetOption = {
	id: string;
	name: string;
};

type QualitySummary = {
	total?: number;
	passed?: number;
	failed?: number;
	aborted?: number;
	missing?: number;
	lastRunAt?: string;
};

type QualityCase = {
	id?: string;
	name?: string;
	description?: string;
	status?: string;
	owner?: string;
	testSuite?: string;
	lastRunAt?: string;
	resultValue?: string;
	passedRows?: number;
	failedRows?: number;
};

type QualityResult = {
	source?: "governance" | "openmetadata";
	enabled?: boolean;
	found?: boolean;
	message?: string;
	fqn?: string;
	metadataSource?: string;
	fallbackReason?: string;
	snapshot?: {
		summary?: QualitySummary;
		cases?: QualityCase[];
	};
};

type GovernanceQualityRun = {
	id?: string;
	ruleId?: string;
	triggerType?: string;
	triggerRef?: string;
	status?: string;
	message?: string;
	metrics?: Array<{
		metricKey?: string;
		metricValue?: number | string;
		thresholdValue?: number | string;
		status?: string;
		detail?: string;
	}>;
	startedAt?: string;
	finishedAt?: string;
	createdDate?: string;
};

const PASS_STATUSES = new Set(["COMPLETED", "SUCCESS", "PASSED"]);
const FAIL_STATUSES = new Set(["FAILED", "ERROR"]);
const ABORTED_STATUSES = new Set(["SKIPPED", "ABORTED", "CANCELED"]);

const normalizeStatus = (value?: string) => String(value || "").trim().toUpperCase();

const latestTime = (run: GovernanceQualityRun) => run.finishedAt || run.startedAt || run.createdDate;

const metadataSourceLabel = (value?: string) => {
	const normalized = String(value || "").toLowerCase();
	if (normalized === "catalog") return "本地 Catalog";
	if (normalized === "openmetadata") return "OpenMetadata";
	if (normalized === "disabled") return "未启用";
	return "未知来源";
};

const qualitySourceLabel = (quality?: QualityResult | null) => {
	if (quality?.source === "governance") return "治理运行结果";
	return metadataSourceLabel(quality?.metadataSource || "openmetadata");
};

const toNumber = (value: unknown): number => {
	const n = Number(value);
	return Number.isFinite(n) ? n : 0;
};

const estimateFailedRows = (run: GovernanceQualityRun): number => {
	const metrics = Array.isArray(run.metrics) ? run.metrics : [];
	let failed = 0;
	for (const metric of metrics) {
		const key = normalizeStatus(metric?.metricKey);
		const status = normalizeStatus(metric?.status);
		const value = toNumber(metric?.metricValue);
		if (status === "FAILED" || key.includes("FAILED") || key.includes("VIOLATION")) {
			failed += value;
		}
	}
	return failed;
};

const toGovernanceQuality = (runs: GovernanceQualityRun[]): QualityResult => {
	const sorted = [...runs].sort((a, b) => {
		const ta = Date.parse(latestTime(a) || "");
		const tb = Date.parse(latestTime(b) || "");
		if (Number.isNaN(ta) && Number.isNaN(tb)) return 0;
		if (Number.isNaN(ta)) return 1;
		if (Number.isNaN(tb)) return -1;
		return tb - ta;
	});
	let passed = 0;
	let failed = 0;
	let aborted = 0;
	for (const run of sorted) {
		const status = normalizeStatus(run.status);
		if (PASS_STATUSES.has(status)) passed += 1;
		else if (FAIL_STATUSES.has(status)) failed += 1;
		else if (ABORTED_STATUSES.has(status)) aborted += 1;
	}
	const cases: QualityCase[] = sorted.map((run) => ({
		id: run.id,
		name: run.ruleId ? `规则 ${run.ruleId}` : "未命名规则",
		status: normalizeStatus(run.status) || "UNKNOWN",
		owner: run.triggerRef || "-",
		testSuite: run.triggerType || "-",
		lastRunAt: latestTime(run),
		resultValue: run.message || "-",
		failedRows: estimateFailedRows(run),
	}));
	return {
		source: "governance",
		enabled: true,
		found: true,
		message: "已使用治理质量运行结果",
		metadataSource: "catalog",
		snapshot: {
			summary: {
				total: sorted.length,
				passed,
				failed,
				aborted,
				lastRunAt: latestTime(sorted[0]),
			},
			cases,
		},
	};
};

export default function QualityPage() {
	const [searchParams, setSearchParams] = useSearchParams();
	const [datasets, setDatasets] = useState<DatasetOption[]>([]);
	const [selectedId, setSelectedId] = useState<string | undefined>(() => searchParams.get("datasetId") || undefined);
	const [loading, setLoading] = useState(false);
	const [quality, setQuality] = useState<QualityResult | null>(null);

	useEffect(() => {
		void loadDatasets();
	}, []);

	useEffect(() => {
		if (selectedId) {
			void loadQuality(selectedId);
		} else {
			setQuality(null);
		}
	}, [selectedId]);

	useEffect(() => {
		const params = new URLSearchParams(searchParams);
		if (selectedId) {
			params.set("datasetId", selectedId);
		} else {
			params.delete("datasetId");
		}
		setSearchParams(params, { replace: true });
	}, [selectedId]);

	const datasetOptions = useMemo(
		() => datasets.map((item) => ({ label: item.name, value: item.id })),
		[datasets],
	);

	const loadDatasets = async () => {
		try {
			const resp: any = await listDatasets({ page: 0, size: 200, enabledOnly: true });
			const content = Array.isArray(resp?.content) ? resp.content : [];
			const options = content
				.map((item: any) => ({ id: String(item.id || ""), name: String(item.name || "").trim() }))
				.filter((item: DatasetOption) => item.id && item.name);
			setDatasets(options);
			if (options.length === 0) {
				setSelectedId(undefined);
				return;
			}
			const requestedId = searchParams.get("datasetId") || selectedId;
			const matched = requestedId ? options.find((item: DatasetOption) => item.id === requestedId) : undefined;
			if (matched) {
				setSelectedId(matched.id);
			} else if (!selectedId) {
				setSelectedId(options[0].id);
			}
		} catch {
			// error toast handled by global interceptor
		}
	};

	const loadQuality = async (id: string) => {
		setLoading(true);
		try {
			const governanceRuns: any = await listQualityRuns({ datasetId: id, limit: 100 });
			const runList = Array.isArray(governanceRuns) ? (governanceRuns as GovernanceQualityRun[]) : [];
			if (runList.length > 0) {
				setQuality(toGovernanceQuality(runList));
				return;
			}
			const resp: any = await getDatasetQuality(id);
			setQuality(resp ? ({ ...resp, source: "openmetadata", metadataSource: resp?.metadataSource || "openmetadata" } as QualityResult) : null);
		} catch (error: any) {
			try {
				const resp: any = await getDatasetQuality(id);
				setQuality(resp ? ({ ...resp, source: "openmetadata", metadataSource: resp?.metadataSource || "openmetadata" } as QualityResult) : null);
			} catch {
				// error toast handled by global interceptor
				setQuality(null);
			}
		} finally {
			setLoading(false);
		}
	};

	const summary: QualitySummary = quality?.snapshot?.summary || {};
	const cases: QualityCase[] = Array.isArray(quality?.snapshot?.cases) ? quality?.snapshot?.cases ?? [] : [];

	const columns: ColumnsType<QualityCase> = [
		{
			title: "规则",
			dataIndex: "name",
			sorter: (a, b) => (a.name || "").localeCompare(b.name || ""),
			render: (value) => value || "-",
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 120,
			render: (value) => {
				const label = String(value || "UNKNOWN").toUpperCase();
				const color = PASS_STATUSES.has(label) ? "green" : FAIL_STATUSES.has(label) ? "red" : "default";
				return <Tag color={color}>{label}</Tag>;
			},
		},
		{
			title: "负责人",
			dataIndex: "owner",
			render: (value) => value || "-",
		},
		{
			title: "测试集",
			dataIndex: "testSuite",
			render: (value) => value || "-",
		},
		{
			title: "最近运行",
			dataIndex: "lastRunAt",
			sorter: (a, b) => {
				const ta = a.lastRunAt ? new Date(a.lastRunAt as any).getTime() : 0;
				const tb = b.lastRunAt ? new Date(b.lastRunAt as any).getTime() : 0;
				return ta - tb;
			},
			render: (value) => value || "-",
		},
		{
			title: "结果值",
			dataIndex: "resultValue",
			render: (value) => value || "-",
		},
		{
			title: "失败行数",
			dataIndex: "failedRows",
			render: (value) => (value ?? "-"),
		},
	];

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据治理中心 · 质量报告"
			/>

			<Card title="选择数据集">
				<Space size={12} wrap>
					<Select
						placeholder="选择数据集"
						style={{ minWidth: 320 }}
						value={selectedId}
						options={datasetOptions}
						onChange={(value) => setSelectedId(value)}
						showSearch
						optionFilterProp="label"
					/>
				</Space>
			</Card>

			{quality?.enabled === false ? (
				<Alert type="warning" message="元数据服务未启用，暂无法获取质量结果。" showIcon />
			) : quality?.found === false ? (
				<Alert type="info" message={quality?.message || "未找到质量结果"} showIcon />
			) : null}
			{quality ? (
				<Alert
					type={quality.fallbackReason ? "warning" : "info"}
					showIcon
					message={`当前数据来源：${qualitySourceLabel(quality)}`}
					description={quality.fallbackReason || undefined}
				/>
			) : null}

			<Card title="质量概览">
				{quality?.snapshot ? (
					<div className="grid gap-3 md:grid-cols-3">
						<Card size="small" title="总规则数">
							<div className="text-lg font-semibold">{summary.total ?? 0}</div>
						</Card>
						<Card size="small" title="通过">
							<div className="text-lg font-semibold">{summary.passed ?? 0}</div>
						</Card>
						<Card size="small" title="失败">
							<div className="text-lg font-semibold">{summary.failed ?? 0}</div>
						</Card>
					</div>
				) : (
					<EmptyState title="暂无质量概览" description="请先运行质量检测任务。" />
				)}
			</Card>

			<Card title="质量规则详情">
				{cases.length ? (
					<CompactTable
						rowKey={(row, idx) => row.id || row.name || String(idx)}
						columns={columns}
						dataSource={cases}
						loading={loading}
						pagination={{ defaultPageSize: 10 }}
					/>
				) : (
					<EmptyState title="暂无规则结果" description="当前数据集暂无质量规则执行记录。" />
				)}
			</Card>
		</div>
	);
}
