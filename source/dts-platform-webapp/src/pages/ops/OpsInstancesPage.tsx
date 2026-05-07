import { useEffect, useMemo, useState } from "react";
import { Button, Card, Input, Select, Space, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { useNavigate } from "react-router";
import { PageHeader } from "@/components/page-header";
import opsService, { type OpsInstance } from "@/api/services/opsService";
import { useLogPreview } from "@/components/log-preview/LogPreviewContext";
import { listAirflowTaskInstances, type AirflowTaskInstance } from "@/api/platformApi";
import { CompactTable, RecordDetailDrawer, appendDetailAction } from "@/components/table";

const { Text } = Typography;

const STATUS_OPTIONS = [
	{ label: "全部", value: "ALL" },
	{ label: "RUNNING", value: "RUNNING" },
	{ label: "SUCCESS", value: "SUCCESS" },
	{ label: "FAILED", value: "FAILED" },
];

const ENTRY_OPTIONS = [
	{ label: "全部", value: "ALL" },
	{ label: "入湖任务", value: "INGESTION_TASK" },
	{ label: "dbt 任务", value: "DBT_RUN" },
	{ label: "Airflow DAG", value: "AIRFLOW_DAG" },
];

const formatDate = (value?: string) => {
	if (!value) return "-";
	const date = new Date(value);
	return Number.isNaN(date.valueOf()) ? value : date.toLocaleString();
};

export default function OpsInstancesPage() {
	const [records, setRecords] = useState<OpsInstance[]>([]);
	const [loading, setLoading] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [status, setStatus] = useState("ALL");
	const [entryKey, setEntryKey] = useState("ALL");
	const [taskInstances, setTaskInstances] = useState<Record<string, AirflowTaskInstance[]>>({});
	const [taskLoading, setTaskLoading] = useState<Record<string, boolean>>({});
	const [detailRow, setDetailRow] = useState<OpsInstance | null>(null);

	const { openLogPreview } = useLogPreview();
	const navigate = useNavigate();

	const loadInstances = async () => {
		setLoading(true);
		try {
			const list = await opsService.instances({
				keyword: keyword.trim() || undefined,
				status: status === "ALL" ? undefined : status,
				entryKey: entryKey === "ALL" ? undefined : entryKey,
				limit: 200,
			});
			setRecords(Array.isArray(list) ? (list as OpsInstance[]) : []);
		} catch {
			// handled by global interceptor
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadInstances();
	}, []);

	const loadTaskInstances = async (record: OpsInstance) => {
		const runId = record.id;
		if (!record.dagId || !record.externalRunId) return;
		setTaskLoading((prev) => ({ ...prev, [runId]: true }));
		try {
			const result = await listAirflowTaskInstances(record.dagId, record.externalRunId);
			const instances: AirflowTaskInstance[] = Array.isArray(result?.task_instances)
				? result.task_instances
				: [];
			setTaskInstances((prev) => ({ ...prev, [runId]: instances }));
		} catch {
			setTaskInstances((prev) => ({ ...prev, [runId]: [] }));
		} finally {
			setTaskLoading((prev) => ({ ...prev, [runId]: false }));
		}
	};

	const baseColumns: ColumnsType<OpsInstance> = [
		{ title: "任务", dataIndex: "artifactName", render: (v) => v || "-" },
		{ title: "类型", dataIndex: "entryKey", width: 140, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "DAG", dataIndex: "dagId", width: 160, render: (v) => v || "-" },
		{ title: "状态", dataIndex: "status", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "开始时间", dataIndex: "startedAt", render: (v) => formatDate(v) , sorter: (a, b) => { const ta = a.startedAt ? new Date(a.startedAt as any).getTime() : 0; const tb = b.startedAt ? new Date(b.startedAt as any).getTime() : 0; return ta - tb; } },
		{ title: "结束时间", dataIndex: "finishedAt", render: (v) => formatDate(v) , sorter: (a, b) => { const ta = a.finishedAt ? new Date(a.finishedAt as any).getTime() : 0; const tb = b.finishedAt ? new Date(b.finishedAt as any).getTime() : 0; return ta - tb; } },
		{ title: "耗时(ms)", dataIndex: "durationMs", render: (v) => v ?? "-" },
		{
			title: "日志/备注",
			dataIndex: "logPath",
			render: (_, record) =>
				record.logPath ? (
					<Text type="secondary">{record.logPath}</Text>
				) : (
					<Text type="secondary">{record.message || "-"}</Text>
				),
		},
		{
			title: "操作",
			dataIndex: "actions",
			width: 240,
			fixed: "right",
			render: (_: unknown, record: OpsInstance) => {
				const isDbt =
					record.entryKey === "DBT_RUN" ||
					(record.entryKey === "AIRFLOW_DAG" && record.dagId?.includes("dbt"));
				return (
					<Space size="small">
						{isDbt && record.externalRunId && (
							<Button
								type="link"
								size="small"
								onClick={() =>
									openLogPreview({
										entryKey: "AIRFLOW_DAG",
										dagId: record.dagId,
										dagRunId: record.externalRunId ?? undefined,
										taskId: "dbt_run",
										tryNumber: 1,
									})
								}
							>
								日志
							</Button>
						)}
						<Button
							type="link"
							size="small"
							onClick={() =>
								navigate(
									`/ops/logs?entryKey=${record.entryKey ?? ""}&runId=${record.externalRunId ?? record.id}`,
								)
							}
						>
							日志中心
						</Button>
					</Space>
				);
			},
		},
	];

	const columns = useMemo(
		() => appendDetailAction(baseColumns, (row) => setDetailRow(row)),
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[],
	);

	return (
		<div className="space-y-6 px-6 py-6">
			<PageHeader title="任务实例监控" />
			<Card
				extra={
					<Space>
						<Input placeholder="搜索任务名称..." value={keyword} onChange={(e) => setKeyword(e.target.value)} />
						<Select value={entryKey} options={ENTRY_OPTIONS} onChange={setEntryKey} style={{ width: 160 }} />
						<Select value={status} options={STATUS_OPTIONS} onChange={setStatus} style={{ width: 140 }} />
						<Button onClick={loadInstances}>刷新</Button>
					</Space>
				}
			>
				<CompactTable<OpsInstance>
					rowKey={(record) => record.id}
					columns={columns}
					dataSource={records}
					loading={loading}
					expandable={{
						rowExpandable: (record) =>
							record.entryKey === "AIRFLOW_DAG" &&
							Boolean(record.dagId) &&
							Boolean(record.externalRunId),
						onExpand: (expanded, record) => {
							if (expanded && !taskInstances[record.id]) {
								void loadTaskInstances(record);
							}
						},
						expandedRowRender: (record) => {
							const instances = taskInstances[record.id] ?? [];
							const isLoading = taskLoading[record.id];
							return (
								<CompactTable<AirflowTaskInstance>
									size="small"
									rowKey="task_id"
									loading={isLoading}
									pagination={false}
									dataSource={instances}
									locale={{ emptyText: "暂无 Task Instance 数据" }}
									columns={[
										{ title: "Task ID", dataIndex: "task_id", render: (v) => v || "-" },
										{
											title: "状态",
											dataIndex: "state",
											width: 110,
											render: (v) => (
												<Tag
													color={
														v === "success"
															? "green"
															: v === "failed"
																? "red"
																: v === "running"
																	? "blue"
																	: "default"
													}
												>
													{v || "-"}
												</Tag>
											),
										},
										{
											title: "开始时间",
											dataIndex: "start_date",
											render: (v) => (v ? dayjs(v).format("MM-DD HH:mm:ss") : "-"),
										},
										{
											title: "耗时",
											dataIndex: "duration",
											width: 100,
											render: (v) => (v != null ? `${Number(v).toFixed(1)}s` : "-"),
										},
										{
											title: "尝试次数",
											dataIndex: "try_number",
											width: 90,
											render: (v) => v ?? 1,
										},
										{
											title: "操作",
											width: 160,
											render: (_: unknown, ti: AirflowTaskInstance) => (
												<Space size="small">
													<Button
														type="link"
														size="small"
														onClick={() =>
															openLogPreview({
																entryKey: "AIRFLOW_DAG",
																dagId: record.dagId,
																dagRunId: record.externalRunId ?? undefined,
																taskId: ti.task_id,
																tryNumber: ti.try_number ?? 1,
																title: `${ti.task_id} — 第 ${ti.try_number ?? 1} 次`,
															})
														}
													>
														日志
													</Button>
													<Button
														type="link"
														size="small"
														onClick={() =>
															navigate(
																`/ops/logs?entryKey=AIRFLOW_DAG&runId=${record.externalRunId ?? ""}&taskId=${ti.task_id}`,
															)
														}
													>
														日志中心
													</Button>
												</Space>
											),
										},
									]}
								/>
							);
						},
					}}
				/>
			</Card>
			<RecordDetailDrawer<OpsInstance>
				open={detailRow !== null}
				onClose={() => setDetailRow(null)}
				record={detailRow}
				columns={baseColumns}
				title="任务实例详情"
			/>
		</div>
	);
}
