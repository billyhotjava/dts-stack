import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Button, Card, Input, Select, Space, Tag, Tooltip, Typography } from "antd";
import { actionColumn, CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";
import {
	getExternalLink,
	listAirflowJobRuns,
	listAirflowJobs,
	triggerAirflowJob,
	visitExternalLink,
} from "@/api/platformApi";
import { formatDateTime } from "@/utils/textUtils";
import { useLogPreview } from "@/components/log-preview/LogPreviewContext";

const { Text } = Typography;

type OrchestrationLink = {
	name?: string;
	description?: string;
	url?: string;
	enabled?: boolean;
};

type AirflowJob = {
	dagId: string;
	name: string;
	owners?: string;
	isPaused?: boolean;
	schedule?: string;
	tags: string[];
	project: string;
	lastState?: string;
	lastRun?: string;
	lastDuration?: number;
	lastRunId?: string;
};

type AirflowRun = {
	runId: string;
	state?: string;
	logicalDate?: string;
	startDate?: string;
	endDate?: string;
	duration?: number;
	conf?: Record<string, unknown> | null;
};

const normalizeTags = (raw: unknown): string[] => {
	if (!Array.isArray(raw)) {
		return [];
	}
	const values = raw
		.map((item) => {
			if (typeof item === "string") {
				return item.trim();
			}
			if (item && typeof item === "object" && "name" in item) {
				const name = (item as { name?: unknown }).name;
				return typeof name === "string" ? name.trim() : "";
			}
			return "";
		})
		.filter((item) => item.length > 0);
	return Array.from(new Set(values));
};

const resolveProject = (dagId: string, tags: string[]) => {
	const projectTag = tags.find((tag) => tag.toLowerCase().startsWith("project:"));
	if (projectTag) {
		const parts = projectTag.split(":", 2);
		if (parts.length === 2 && parts[1].trim()) {
			return parts[1].trim();
		}
	}
	const tokens = dagId.split("_").filter((item) => item.trim().length > 0);
	if (tokens.length >= 2) {
		return `${tokens[0]}_${tokens[1]}`;
	}
	return tokens[0] || "default";
};

const formatDuration = (value?: number) => {
	if (typeof value !== "number" || Number.isNaN(value)) {
		return "-";
	}
	return `${value.toFixed(1)}s`;
};

const stateColor = (state?: string) => {
	const normalized = (state || "").toLowerCase();
	if (normalized === "success") {
		return "green";
	}
	if (normalized === "failed") {
		return "red";
	}
	if (normalized === "running" || normalized === "queued") {
		return "blue";
	}
	if (normalized === "up_for_retry" || normalized === "upstream_failed") {
		return "orange";
	}
	return "default";
};

export default function OrchestrationRunsTab() {
	const [link, setLink] = useState<OrchestrationLink | null>(null);
	const [linkLoading, setLinkLoading] = useState(false);

	const [jobs, setJobs] = useState<AirflowJob[]>([]);
	const [jobsLoading, setJobsLoading] = useState(false);
	const [selectedDagId, setSelectedDagId] = useState<string | null>(null);

	const [runs, setRuns] = useState<AirflowRun[]>([]);
	const [runsLoading, setRunsLoading] = useState(false);

	const [projectFilter, setProjectFilter] = useState<string>("ALL");
	const [tagFilter, setTagFilter] = useState<string>("ALL");
	const [keyword, setKeyword] = useState<string>("");

	const { openLogPreview } = useLogPreview();

	const loadExternalLink = useCallback(async () => {
		setLinkLoading(true);
		try {
			const resp = await getExternalLink("EXPLORE_ETL_ORCHESTRATION");
			setLink((resp || null) as OrchestrationLink | null);
		} catch (error: unknown) {
			const err = error as { message?: string };
			toast.error(err?.message || "入口加载失败");
		} finally {
			setLinkLoading(false);
		}
	}, []);

	const loadJobs = useCallback(async () => {
		setJobsLoading(true);
		try {
			const data = (await listAirflowJobs(200)) as Array<Record<string, unknown>>;
			const normalized: AirflowJob[] = (Array.isArray(data) ? data : []).map((item) => {
				const dagId = String(item.dagId || "");
				const tags = normalizeTags(item.tags);
				return {
					dagId,
					name: String(item.name || dagId),
					owners: typeof item.owners === "string" ? item.owners : undefined,
					isPaused: Boolean(item.isPaused),
					schedule: typeof item.schedule === "string" ? item.schedule : undefined,
					tags,
					project: resolveProject(dagId, tags),
					lastState: typeof item.lastState === "string" ? item.lastState : undefined,
					lastRun: typeof item.lastRun === "string" ? item.lastRun : undefined,
					lastDuration: typeof item.lastDuration === "number" ? item.lastDuration : undefined,
					lastRunId: typeof item.lastRunId === "string" ? item.lastRunId : undefined,
				};
			});
			setJobs(normalized);
			if (!selectedDagId && normalized.length > 0) {
				setSelectedDagId(normalized[0].dagId);
			}
			if (selectedDagId && !normalized.some((job) => job.dagId === selectedDagId)) {
				setSelectedDagId(normalized.length > 0 ? normalized[0].dagId : null);
			}
		} catch (error: unknown) {
			const err = error as { message?: string };
			toast.error(err?.message || "DAG 列表加载失败");
		} finally {
			setJobsLoading(false);
		}
	}, [selectedDagId]);

	const loadRuns = useCallback(async (dagId: string) => {
		setRunsLoading(true);
		try {
			const payload = (await listAirflowJobRuns(dagId, 30)) as { dag_runs?: Array<Record<string, unknown>> };
			const dagRuns = Array.isArray(payload?.dag_runs) ? payload.dag_runs : [];
			const normalized: AirflowRun[] = dagRuns.map((run) => ({
				runId: String(run.dag_run_id || run.run_id || ""),
				state: typeof run.state === "string" ? run.state : undefined,
				logicalDate:
					typeof run.logical_date === "string"
						? run.logical_date
						: typeof run.execution_date === "string"
							? run.execution_date
							: undefined,
				startDate: typeof run.start_date === "string" ? run.start_date : undefined,
				endDate: typeof run.end_date === "string" ? run.end_date : undefined,
				duration: typeof run.duration === "number" ? run.duration : undefined,
				conf: run.conf && typeof run.conf === "object" ? (run.conf as Record<string, unknown>) : null,
			}));
			setRuns(normalized);
		} catch (error: unknown) {
			const err = error as { message?: string };
			toast.error(err?.message || "运行记录加载失败");
			setRuns([]);
		} finally {
			setRunsLoading(false);
		}
	}, []);

	useEffect(() => {
		void loadExternalLink();
		void loadJobs();
	}, [loadExternalLink, loadJobs]);

	useEffect(() => {
		if (!selectedDagId) {
			setRuns([]);
			return;
		}
		void loadRuns(selectedDagId);
	}, [loadRuns, selectedDagId]);

	const projects = useMemo(() => Array.from(new Set(jobs.map((job) => job.project))).sort(), [jobs]);
	const tags = useMemo(() => Array.from(new Set(jobs.flatMap((job) => job.tags))).sort(), [jobs]);

	const filteredJobs = useMemo(() => {
		const normalizedKeyword = keyword.trim().toLowerCase();
		return jobs.filter((job) => {
			if (projectFilter !== "ALL" && job.project !== projectFilter) {
				return false;
			}
			if (tagFilter !== "ALL" && !job.tags.includes(tagFilter)) {
				return false;
			}
			if (!normalizedKeyword) {
				return true;
			}
			return (
				job.dagId.toLowerCase().includes(normalizedKeyword) ||
				job.name.toLowerCase().includes(normalizedKeyword) ||
				job.project.toLowerCase().includes(normalizedKeyword) ||
				job.tags.some((tag) => tag.toLowerCase().includes(normalizedKeyword))
			);
		});
	}, [jobs, keyword, projectFilter, tagFilter]);

	const selectedJob = useMemo(
		() => filteredJobs.find((job) => job.dagId === selectedDagId) || null,
		[filteredJobs, selectedDagId],
	);

	const failedJobs = useMemo(
		() => filteredJobs.filter((job) => (job.lastState || "").toLowerCase() === "failed"),
		[filteredJobs],
	);

	const summary = useMemo(
		() => ({
			total: filteredJobs.length,
			running: filteredJobs.filter((job) => {
				const state = (job.lastState || "").toLowerCase();
				return state === "running" || state === "queued";
			}).length,
			failed: failedJobs.length,
			paused: filteredJobs.filter((job) => job.isPaused).length,
		}),
		[failedJobs.length, filteredJobs],
	);

	const handleVisitExternal = async () => {
		if (!link?.url) {
			toast.warning("尚未配置外部编排地址");
			return;
		}
		try {
			await visitExternalLink("EXPLORE_ETL_ORCHESTRATION", { source: "platform" });
			window.location.href = link.url;
		} catch (error: unknown) {
			const err = error as { message?: string };
			toast.error(err?.message || "跳转失败");
		}
	};

	const handleTrigger = async (dagId: string, runId?: string) => {
		try {
			const payload = runId
				? { conf: { retryRunId: runId, source: "platform_orchestration" } }
				: { conf: { source: "platform_orchestration" } };
			await triggerAirflowJob(dagId, payload);
			toast.success("已触发重跑");
			await loadJobs();
			if (selectedDagId === dagId) {
				await loadRuns(dagId);
			}
		} catch (error: unknown) {
			const err = error as { message?: string };
			toast.error(err?.message || "重跑触发失败");
		}
	};

	const jobColumns: ColumnsType<AirflowJob> = [
		{
			title: "DAG",
			dataIndex: "dagId",
			sorter: (a, b) => (a.dagId || "").localeCompare(b.dagId || ""),
			key: "dagId",
			width: 320,
			render: (_, row) => (
				<Tooltip title={row.dagId}>
					<span className="font-medium">{row.name || row.dagId}</span>
				</Tooltip>
			),
		},
		{
			title: "项目",
			dataIndex: "project",
			key: "project",
			width: 140,
			render: (value) => <Tag>{value}</Tag>,
		},
		{
			title: "标签",
			dataIndex: "tags",
			key: "tags",
			width: 220,
			render: (value: string[]) =>
				value.length ? (
					<Space size={[4, 4]} wrap>
						{value.slice(0, 3).map((tag) => (
							<Tag key={tag}>{tag}</Tag>
						))}
					</Space>
				) : (
					<Text type="secondary">-</Text>
				),
		},
		{
			title: "最近状态",
			dataIndex: "lastState",
			key: "lastState",
			width: 140,
			render: (value) => <Tag color={stateColor(value)}>{value || "-"}</Tag>,
		},
		{
			title: "最近运行",
			dataIndex: "lastRun",
			key: "lastRun",
			width: 200,
			render: (value) => formatDateTime(value),
		},
		actionColumn<AirflowJob>(
			(row) => [{ key: "trigger", label: "立即触发", onClick: () => void handleTrigger(row.dagId) }],
			{ width: 120, fixed: false },
		),
	];

	const runColumns: ColumnsType<AirflowRun> = [
		{
			title: "Run ID",
			dataIndex: "runId",
			key: "runId",
			width: 320,
		},
		{
			title: "状态",
			dataIndex: "state",
			key: "state",
			width: 120,
			render: (value) => <Tag color={stateColor(value)}>{value || "-"}</Tag>,
		},
		{
			title: "调度时间",
			dataIndex: "logicalDate",
			sorter: (a, b) => {
				const ta = a.logicalDate ? new Date(a.logicalDate as any).getTime() : 0;
				const tb = b.logicalDate ? new Date(b.logicalDate as any).getTime() : 0;
				return ta - tb;
			},
			key: "logicalDate",
			width: 200,
			render: (value) => formatDateTime(value),
		},
		{
			title: "开始时间",
			dataIndex: "startDate",
			sorter: (a, b) => {
				const ta = a.startDate ? new Date(a.startDate as any).getTime() : 0;
				const tb = b.startDate ? new Date(b.startDate as any).getTime() : 0;
				return ta - tb;
			},
			key: "startDate",
			width: 200,
			render: (value) => formatDateTime(value),
		},
		{
			title: "耗时",
			dataIndex: "duration",
			key: "duration",
			width: 100,
			render: (value) => formatDuration(value),
		},
		actionColumn<AirflowRun>(
			(row) => [
				{
					key: "rerun",
					label: "重跑",
					disabled: !selectedDagId,
					onClick: () => void handleTrigger(selectedDagId || "", row.runId),
				},
				{
					key: "log",
					label: "日志",
					hidden: !(Boolean(selectedDagId?.toLowerCase().includes("dbt")) && row.runId),
					onClick: () =>
						openLogPreview({
							entryKey: "AIRFLOW_DAG",
							dagId: selectedDagId ?? "",
							dagRunId: row.runId,
							taskId: "dbt_run",
							tryNumber: 1,
							title: `日志 — ${selectedDagId} / ${row.runId}`,
						}),
				},
			],
			{ width: 180, fixed: false },
		),
	];

	return (
		<div className="space-y-6">
			<PageHeader
				title="数据开发中心 / 任务编排"
				actions={
					<Space>
						<Button onClick={() => void loadJobs()} loading={jobsLoading}>
							刷新状态
						</Button>
						<Button type="primary" onClick={() => void handleVisitExternal()} disabled={!link?.enabled || !link?.url}>
							进入编排平台
						</Button>
					</Space>
				}
			/>

			<div className="grid gap-4 lg:grid-cols-4">
				<Card>
					<div className="text-xs text-text-tertiary">DAG 数量</div>
					<div className="mt-2 text-2xl font-semibold">{summary.total}</div>
				</Card>
				<Card>
					<div className="text-xs text-text-tertiary">运行中</div>
					<div className="mt-2 text-2xl font-semibold">{summary.running}</div>
				</Card>
				<Card>
					<div className="text-xs text-text-tertiary">失败 DAG</div>
					<div className="mt-2 text-2xl font-semibold text-red-500">{summary.failed}</div>
				</Card>
				<Card>
					<div className="text-xs text-text-tertiary">暂停 DAG</div>
					<div className="mt-2 text-2xl font-semibold">{summary.paused}</div>
				</Card>
			</div>

			<Card loading={linkLoading}>
				{link ? (
					<div className="space-y-3">
						<Space align="center">
							<Text strong>{link.name || "外部编排平台"}</Text>
							<Tag color={link.enabled ? "green" : "default"}>{link.enabled ? "已启用" : "未启用"}</Tag>
						</Space>
						<Text type="secondary">{link.description || "通过外部平台完成作业编排与调度。"}</Text>
					</div>
				) : (
					<EmptyState title="暂无外部编排入口" description="请在运维侧配置外部编排平台链接。" compact />
				)}
			</Card>

			<Card
				title="DAG 列表"
				extra={
					<Space>
						<Select
							value={projectFilter}
							onChange={(value) => setProjectFilter(value)}
							style={{ width: 160 }}
							options={[
								{ label: "全部项目", value: "ALL" },
								...projects.map((project) => ({ label: project, value: project })),
							]}
						/>
						<Select
							value={tagFilter}
							onChange={(value) => setTagFilter(value)}
							style={{ width: 160 }}
							options={[{ label: "全部标签", value: "ALL" }, ...tags.map((tag) => ({ label: tag, value: tag }))]}
						/>
						<Input
							placeholder="搜索 DAG / 标签"
							value={keyword}
							onChange={(event) => setKeyword(event.target.value)}
							style={{ width: 220 }}
						/>
					</Space>
				}
			>
				{filteredJobs.length ? (
					<CompactTable
						rowKey="dagId"
						loading={jobsLoading}
						columns={jobColumns}
						dataSource={filteredJobs}
						pagination={{ defaultPageSize: 10, showSizeChanger: false }}
						onRow={(record) => ({
							onClick: () => setSelectedDagId(record.dagId),
						})}
					/>
				) : (
					<EmptyState title="未命中 DAG" description="请调整项目、标签或关键字筛选条件。" compact />
				)}
			</Card>

			<div className="grid gap-4 xl:grid-cols-3">
				<Card title="失败摘要" className="xl:col-span-1">
					{failedJobs.length ? (
						<div className="space-y-3">
							{failedJobs.slice(0, 8).map((job) => (
								<div key={job.dagId} className="rounded border border-red-200 bg-red-50 p-3">
									<div className="font-medium text-red-600">{job.name}</div>
									<div className="mt-1 text-xs text-red-500">{job.dagId}</div>
									<div className="mt-2 flex items-center justify-between text-xs">
										<span>{formatDateTime(job.lastRun)}</span>
										<Button
											size="small"
											danger
											type="link"
											onClick={() => void handleTrigger(job.dagId, job.lastRunId)}
										>
											重跑
										</Button>
									</div>
								</div>
							))}
						</div>
					) : (
						<EmptyState title="暂无失败 DAG" description="当前筛选范围内未发现失败任务。" compact />
					)}
				</Card>

				<Card
					title={selectedJob ? `最近运行 · ${selectedJob.name}` : "最近运行"}
					className="xl:col-span-2"
					extra={selectedJob ? <Tag>{selectedJob.dagId}</Tag> : null}
				>
					{selectedDagId ? (
						<CompactTable
							rowKey="runId"
							loading={runsLoading}
							columns={runColumns}
							dataSource={runs}
							pagination={{ defaultPageSize: 10, showSizeChanger: false }}
							size="small"
						/>
					) : (
						<EmptyState title="请选择 DAG" description="点击上方 DAG 列表项后查看最近运行。" compact />
					)}
				</Card>
			</div>
		</div>
	);
}
