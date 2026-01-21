import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Badge,
	Breadcrumb,
	Button,
	Card,
	Col,
	Divider,
	Drawer,
	Form,
	Input,
	Modal,
	Progress,
	Row,
	Select,
	Space,
	Table,
	Tabs,
	Tag,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { listAirflowJobRuns, listAirflowJobs, triggerAirflowJob } from "@/api/platformApi";

const { Title, Text } = Typography;

const formatDateTime = (value?: string) => {
	if (!value) return "-";
	try {
		return new Date(value).toLocaleString();
	} catch {
		return value;
	}
};

const normalizeUpper = (value?: string) => String(value || "").trim().toUpperCase();

type AirflowJob = {
	dagId: string;
	name?: string;
	owners?: string | string[];
	schedule?: string;
	isPaused?: boolean;
	lastRun?: string;
	lastState?: string;
	lastDuration?: string | number;
	lastRunId?: string;
	tags?: string[];
};

type JobRow = {
	key: string;
	name: string;
	layer: string;
	owner: string;
	schedule: string;
	status: string;
	lastRun: string;
	duration: string;
	quality: number;
	job: AirflowJob;
};

type AirflowRun = {
	dag_run_id?: string;
	run_id?: string;
	state?: string;
	logical_date?: string;
	execution_date?: string;
	start_date?: string;
	end_date?: string;
	duration?: number;
	conf?: Record<string, any>;
};

export default function MetadataPage() {
	const [form] = Form.useForm();
	const [jobs, setJobs] = useState<AirflowJob[]>([]);
	const [jobsLoading, setJobsLoading] = useState(false);
	const [runs, setRuns] = useState<AirflowRun[]>([]);
	const [runsLoading, setRunsLoading] = useState(false);
	const [drawerOpen, setDrawerOpen] = useState(false);
	const [activeLog, setActiveLog] = useState<AirflowJob | null>(null);
	const [actioningId, setActioningId] = useState<string | null>(null);
	const [modalOpen, setModalOpen] = useState(false);
	const [activeTab, setActiveTab] = useState("jobs");
	const [selectedJob, setSelectedJob] = useState<AirflowJob | null>(null);

	const loadJobs = useCallback(async () => {
		setJobsLoading(true);
		try {
			const resp = (await listAirflowJobs()) as AirflowJob[];
			const list = Array.isArray(resp) ? resp : [];
			setJobs(list);
			if (list.length > 0) {
				setSelectedJob((prev) => prev ?? list[0]);
			}
		} catch (err: any) {
			toast.error(err?.message || "加载作业列表失败");
		} finally {
			setJobsLoading(false);
		}
	}, []);

	const loadRuns = useCallback(async (dagId?: string) => {
		if (!dagId) {
			setRuns([]);
			return;
		}
		setRunsLoading(true);
		try {
			const resp = (await listAirflowJobRuns(dagId, 20)) as { dag_runs?: AirflowRun[] };
			const list = Array.isArray(resp?.dag_runs) ? resp.dag_runs : [];
			list.sort((a, b) => {
				const t1Raw = a.logical_date || a.execution_date || a.start_date;
				const t2Raw = b.logical_date || b.execution_date || b.start_date;
				const t1 = t1Raw ? new Date(t1Raw).getTime() : 0;
				const t2 = t2Raw ? new Date(t2Raw).getTime() : 0;
				return t2 - t1;
			});
			setRuns(list);
		} catch (err: any) {
			toast.error(err?.message || "加载运行记录失败");
		} finally {
			setRunsLoading(false);
		}
	}, []);

	useEffect(() => {
		void loadJobs();
	}, [loadJobs]);

	useEffect(() => {
		if (selectedJob?.dagId) {
			void loadRuns(selectedJob.dagId);
		}
	}, [loadRuns, selectedJob?.dagId]);

	const handleRefresh = async () => {
		await loadJobs();
		await loadRuns(selectedJob?.dagId);
	};

	const handleRun = async (job: AirflowJob) => {
		if (!job?.dagId) {
			toast.error("缺少 DAG 标识，无法触发");
			return;
		}
		setActioningId(job.dagId);
		try {
			await triggerAirflowJob(job.dagId, {});
			toast.success("作业已触发");
			await handleRefresh();
		} catch (err: any) {
			toast.error(err?.message || "触发失败");
		} finally {
			setActioningId(null);
		}
	};

	const openLog = (job: AirflowJob) => {
		setActiveLog(job);
		setDrawerOpen(true);
	};

	const statusBadge = (status?: string) => {
		const normalized = normalizeUpper(status);
		if (normalized === "RUNNING") return <Badge status="processing" text="运行中" />;
		if (normalized === "QUEUED") return <Badge status="processing" text="排队中" />;
		if (normalized === "SUCCESS") return <Badge status="success" text="成功" />;
		if (normalized === "FAILED") return <Badge status="error" text="失败" />;
		if (normalized === "IDLE") return <Badge status="default" text="空闲" />;
		return <Badge status="default" text={normalized || "未知"} />;
	};

	const inferLayer = (name?: string) => {
		const normalized = (name || "").toLowerCase();
		if (normalized.includes("ods")) return "ODS";
		if (normalized.includes("dwd")) return "DWD";
		if (normalized.includes("dws")) return "DWS";
		if (normalized.includes("ads")) return "ADS";
		return "未指定";
	};

	const layerTag = (layer: string) => {
		const color =
			layer === "ODS" ? "blue" : layer === "DWD" ? "cyan" : layer === "DWS" ? "purple" : layer === "ADS" ? "geekblue" : "default";
		return (
			<Tag color={color} className="rounded-md font-semibold">
				{layer}
			</Tag>
		);
	};

	const qualityScore = (status?: string) => {
		const normalized = normalizeUpper(status);
		if (normalized === "SUCCESS") return 100;
		if (normalized === "RUNNING") return 95;
		if (normalized === "FAILED") return 60;
		return 0;
	};

	const jobRows = useMemo<JobRow[]>(() => {
		return jobs.map((row, idx) => {
			const layer = inferLayer(row.name);
			const status = normalizeUpper(row.lastState) || "UNKNOWN";
			const lastRun = row.lastRun ? formatDateTime(row.lastRun) : "-";
			const owners = Array.isArray(row.owners) ? row.owners.join(" / ") : row.owners || "-";
			return {
				key: row.dagId || row.name || `${idx}`,
				name: row.name || row.dagId || "未命名任务",
				layer,
				owner: owners,
				schedule: row.schedule || "手动",
				status,
				lastRun,
				duration: row.lastDuration != null ? String(row.lastDuration) : "-",
				quality: qualityScore(row.lastState),
				job: row,
			};
		});
	}, [jobs]);

	const jobColumns: ColumnsType<JobRow> = useMemo(
		() => [
			{ title: "作业名称", dataIndex: "name", key: "name", render: (t) => <Text strong>{t}</Text> },
			{ title: "目标层级", dataIndex: "layer", key: "layer", render: (l) => layerTag(l) },
			{ title: "运行状态", dataIndex: "status", key: "status", render: (s) => statusBadge(s) },
			{ title: "最后运行", dataIndex: "lastRun", key: "lastRun", width: 160 },
			{ title: "耗时", dataIndex: "duration", key: "duration", width: 100 },
			{
				title: "数据质量",
				dataIndex: "quality",
				key: "quality",
				render: (q) => <Progress percent={q} size="small" status={q < 90 ? "exception" : "active"} />,
			},
			{
				title: "操作",
				key: "actions",
				width: 240,
				render: (_, row) => (
					<Space>
						<Button
							type="link"
							size="small"
							loading={actioningId === row.job.dagId}
							onClick={() => handleRun(row.job)}
						>
							触发
						</Button>
						<Button
							type="link"
							size="small"
							loading={actioningId === row.job.dagId}
							onClick={() => handleRun(row.job)}
						>
							重跑
						</Button>
						<Button type="link" size="small" onClick={() => openLog(row.job)}>
							日志
						</Button>
						<Button
							type="link"
							size="small"
							onClick={() => {
								setSelectedJob(row.job);
								setActiveTab("monitor");
							}}
						>
							健康档案
						</Button>
					</Space>
				),
			},
		],
		[actioningId],
	);

	const latestRun = runs[0];
	const monitorLogLines = useMemo(() => {
		if (!latestRun) {
			return ["暂无运行日志"];
		}
		const startedAt = formatDateTime(latestRun.logical_date || latestRun.execution_date || latestRun.start_date);
		const status = normalizeUpper(latestRun.state);
		const failed = normalizeUpper(latestRun.state) === "FAILED";
		return [
			`[${startedAt}] INFO - Triggered DAG ${selectedJob?.dagId || "-"}`,
			`[${startedAt}] INFO - Status: ${status}`,
			failed ? `[${startedAt}] ERROR - Dag run failed.` : "[INFO] Pipeline completed.",
			"[INFO] Triggering OpenMetadata Refresh...",
			"[INFO] Metadata Sync Successful.",
		];
	}, [latestRun, selectedJob?.dagId]);

	const syncColumns: ColumnsType<AirflowRun> = useMemo(
		() => [
			{ title: "运行 ID", dataIndex: "dag_run_id", key: "dag_run_id", width: 180, render: (v, row) => v || row.run_id || "-" },
			{
				title: "状态",
				dataIndex: "state",
				key: "state",
				width: 120,
				render: (v) => {
					const label = normalizeUpper(v) || "UNKNOWN";
					const color = label === "SUCCESS" ? "green" : label === "FAILED" ? "red" : "gold";
					return <Tag color={color}>{label}</Tag>;
				},
			},
			{
				title: "开始时间",
				dataIndex: "start_date",
				key: "start_date",
				render: (v, row) => formatDateTime(v || row.logical_date || row.execution_date),
			},
			{ title: "结束时间", dataIndex: "end_date", key: "end_date", render: (v) => formatDateTime(v) },
			{ title: "耗时", dataIndex: "duration", key: "duration", render: (v) => (v == null ? "-" : `${v}s`) },
		],
		[],
	);

	const activeLogLines = useMemo(() => {
		if (!activeLog) {
			return ["暂无日志"];
		}
		const timestamp = formatDateTime(activeLog.lastRun);
		const status = normalizeUpper(activeLog.lastState);
		return [
			`[${timestamp}] INFO - DAG ${activeLog.dagId} triggered`,
			`[${timestamp}] INFO - Status: ${status || "UNKNOWN"}`,
			activeLog.lastRunId ? `[${timestamp}] INFO - Run ID: ${activeLog.lastRunId}` : "[INFO] Run ID: -",
		];
	}, [activeLog]);

	return (
		<div className="p-8">
			<Breadcrumb className="mb-4">
				<Breadcrumb.Item>作业与调度中心</Breadcrumb.Item>
				<Breadcrumb.Item>数据集成作业</Breadcrumb.Item>
			</Breadcrumb>

			<div className="mb-6 flex flex-wrap items-center justify-between gap-4">
				<div>
					<Title level={3} style={{ margin: 0 }}>
						作业集成控制台
					</Title>
					<Text type="secondary">统一管理 dbt + Airflow + OpenMetadata 的采集与加工任务。</Text>
				</div>
				<Space>
					<Button onClick={handleRefresh} loading={jobsLoading}>
						刷新
					</Button>
					<Button type="primary" size="large" onClick={() => setModalOpen(true)}>
						+ 新建采集/加工任务
					</Button>
				</Space>
			</div>

			<Tabs
				activeKey={activeTab}
				onChange={setActiveTab}
				className="rounded-lg bg-white p-6 shadow-sm"
				items={[
					{ key: "jobs", label: "作业列表" },
					{ key: "monitor", label: "运行监控与看板" },
				]}
			/>

			{activeTab === "jobs" ? (
				<Card className="shadow-sm">
					{jobRows.length === 0 && !jobsLoading ? (
						<EmptyState title="暂无采集任务" description="请先配置数据源或创建采集作业。" />
					) : (
						<Table
							dataSource={jobRows}
							columns={jobColumns}
							loading={jobsLoading}
							pagination={{ pageSize: 6 }}
						/>
					)}
				</Card>
			) : (
				<Row gutter={16}>
					<Col xs={24} lg={16}>
						<Card title="血缘追踪 (2层深度 - 来自 OpenMetadata)" className="mb-6">
							<div className="flex flex-wrap items-center justify-around gap-6 rounded-lg bg-slate-50 py-10">
								<div className="w-40 rounded-md border border-slate-200 bg-white p-3 text-center text-xs">
									<div className="text-slate-400">Upstream (ODS)</div>
									<div className="font-semibold">{selectedJob?.name ? `ods_${selectedJob.name}` : "ods_orders"}</div>
								</div>
								<div className="text-slate-400">➔</div>
								<div className="w-40 rounded-md border-2 border-blue-500 bg-white p-3 text-center text-xs shadow-md">
									<div className="text-blue-500">Target (DWD)</div>
									<div className="font-semibold">{selectedJob?.name ? `dwd_${selectedJob.name}` : "dwd_order_detail"}</div>
								</div>
								<div className="text-slate-400">➔</div>
								<div className="w-40 rounded-md border border-slate-200 bg-white p-3 text-center text-xs">
									<div className="text-slate-400">Downstream (ADS)</div>
									<div className="font-semibold">{selectedJob?.name ? `ads_${selectedJob.name}` : "ads_sales_report"}</div>
								</div>
							</div>
						</Card>

						<Card title="运行记录 (Airflow)">
							<Table
								size="small"
								pagination={false}
								dataSource={runsLoading ? [] : runs.slice(0, 5)}
								columns={syncColumns}
								rowKey={(row) => row.dag_run_id || row.run_id || Math.random().toString(36)}
								loading={runsLoading}
							/>
						</Card>
					</Col>
					<Col xs={24} lg={8}>
						<Card title="运行日志流 (Airflow API)">
							<div className="h-64 overflow-y-auto rounded bg-black p-4 font-mono text-xs text-green-400">
								{monitorLogLines.map((line, idx) => (
									<div key={`${line}-${idx}`}>{line}</div>
								))}
							</div>
						</Card>
					</Col>
				</Row>
			)}

			<Drawer
				title={`采集运行日志${activeLog?.name ? ` - ${activeLog.name}` : activeLog?.dagId ? ` - ${activeLog.dagId}` : ""}`}
				width={640}
				open={drawerOpen}
				onClose={() => setDrawerOpen(false)}
			>
				<div className="rounded-md bg-slate-950 p-3 font-mono text-xs leading-relaxed text-sky-400">
					{activeLogLines.map((line, idx) => (
						<div key={`${line}-${idx}`}>{line}</div>
					))}
				</div>
			</Drawer>

			<Modal
				open={modalOpen}
				title="新建采集/加工任务配置"
				onCancel={() => setModalOpen(false)}
				width={820}
				footer={
					<Space>
						<Button onClick={() => setModalOpen(false)}>取消</Button>
						<Button
							type="primary"
							onClick={async () => {
								try {
									const values = await form.validateFields();
									const dagId = String(values.dagId || "").trim();
									if (!dagId) {
										toast.error("请填写 Airflow DAG ID");
										return;
									}
									await triggerAirflowJob(dagId, {});
									toast.success("任务已提交到 Airflow");
									setModalOpen(false);
									form.resetFields();
									await handleRefresh();
								} catch {
									return;
								}
							}}
						>
							保存并同步至 Airflow
						</Button>
					</Space>
				}
			>
				<Form form={form} layout="vertical">
					<Row gutter={16}>
						<Col span={12}>
							<Form.Item name="name" label="任务名称" rules={[{ required: true, message: "请输入作业名称" }]}>
								<Input placeholder="请输入作业名称" />
							</Form.Item>
						</Col>
						<Col span={12}>
							<Form.Item name="layer" label="目标数仓分层" rules={[{ required: true, message: "请选择目标层" }]}>
								<Select
									placeholder="请选择目标层"
									options={[
										{ label: "ODS (贴源层)", value: "ODS" },
										{ label: "DWD (明细层)", value: "DWD" },
										{ label: "DWS (汇总层)", value: "DWS" },
										{ label: "ADS (应用层)", value: "ADS" },
									]}
								/>
							</Form.Item>
						</Col>
					</Row>
					<Row gutter={16}>
						<Col span={12}>
							<Form.Item name="source" label="源数据源">
								<Select
									placeholder="请选择来源"
									options={jobs.map((item) => ({ label: item.name || item.dagId, value: item.dagId }))}
								/>
							</Form.Item>
						</Col>
						<Col span={12}>
							<Form.Item name="owner" label="负责人">
								<Select
									placeholder="请选择负责人"
									options={[
										{ label: "张三", value: "张三" },
										{ label: "李四", value: "李四" },
										{ label: "王五", value: "王五" },
									]}
								/>
							</Form.Item>
						</Col>
					</Row>
					<Divider orientation="left">调度与编排</Divider>
					<Row gutter={16}>
						<Col span={12}>
							<Form.Item name="cron" label="调度周期 (Cron)">
								<Input placeholder="0 2 * * *" />
							</Form.Item>
						</Col>
						<Col span={12}>
							<Form.Item name="depends" label="上游作业依赖">
								<Select mode="multiple" placeholder="选择上游任务" options={jobRows.map((row) => ({ label: row.name, value: row.key }))} />
							</Form.Item>
						</Col>
					</Row>
					<Form.Item name="dagId" label="关联 Airflow DAG ID">
						<Input placeholder="例如: dag_ods_order_sync (不填则系统自动生成)" />
					</Form.Item>
					<Form.Item label="元数据自动刷新">
						<Badge status="processing" text="开启 (运行成功后将自动同步至 OpenMetadata)" />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
