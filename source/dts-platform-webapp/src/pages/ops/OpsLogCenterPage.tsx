import { Button, Card, Input, Select, Space, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router";
import { getDbtRunLog } from "@/api/platformApi";
import opsService, { type ExternalRun } from "@/api/services/opsService";
import { PageHeader } from "@/components/page-header";
import { actionColumn, appendDetailAction, CompactTable, RecordDetailDrawer } from "@/components/table";
import { statusLabel } from "@/utils/customerDisplayLabels";

const { Text } = Typography;

const STATUS_TAG_COLOR: Record<string, string> = {
	SUCCESS: "green",
	FAILED: "red",
	RUNNING: "blue",
	SUBMITTED: "cyan",
};

const ENTRY_OPTIONS = [
	{ label: "全部类型", value: "" },
	{ label: "Airflow 调度工作流", value: "AIRFLOW_DAG" },
	{ label: "入湖任务", value: "INGESTION_TASK" },
	{ label: "dbt 任务", value: "DBT_RUN" },
];

const STATUS_OPTIONS = [
	{ label: "全部状态", value: "" },
	{ label: "运行中", value: "RUNNING" },
	{ label: "成功", value: "SUCCESS" },
	{ label: "失败", value: "FAILED" },
];

const TASK_OPTIONS = [
	{ label: "准备执行环境", value: "prepare_runtime" },
	{ label: "执行模型构建", value: "dbt_build" },
	{ label: "同步结果与核验物理表", value: "sync_manifest_and_probe" },
	{ label: "完成构建与资产登记", value: "finalize_run" },
	{ label: "常规任务执行", value: "dbt_run" },
];

export default function OpsLogCenterPage() {
	const [searchParams] = useSearchParams();
	const [records, setRecords] = useState<ExternalRun[]>([]);
	const [loading, setLoading] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [entryKey, setEntryKey] = useState(searchParams.get("entryKey") ?? "");
	const [status, setStatus] = useState(searchParams.get("status") ?? "");
	const [expandedRunId, setExpandedRunId] = useState<string | null>(searchParams.get("runId"));
	const [logContent, setLogContent] = useState<Record<string, string>>({});
	const [logLoading, setLogLoading] = useState<Record<string, boolean>>({});
	const [logTryNumber, setLogTryNumber] = useState<Record<string, number>>({});
	const [logTaskId, setLogTaskId] = useState<Record<string, string>>(() => {
		const runId = searchParams.get("runId");
		const taskId = searchParams.get("taskId");
		return runId && taskId ? { [runId]: taskId } : {};
	});
	const [detailRow, setDetailRow] = useState<ExternalRun | null>(null);
	const logRef = useRef<HTMLPreElement>(null);

	const loadRecords = async () => {
		setLoading(true);
		try {
			const list = await opsService.externalRuns({
				entryKey: entryKey || undefined,
				status: status || undefined,
				keyword: keyword.trim() || undefined,
				limit: 100,
			});
			setRecords(Array.isArray(list) ? list : []);
		} catch {
			// handled by interceptor
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadRecords();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [entryKey, status]);

	const loadLog = useCallback(
		async (record: ExternalRun, selectedTaskId?: string) => {
			const runId = record.externalRunId ?? record.id;
			const tryNum = logTryNumber[runId] ?? 1;
			const taskId =
				selectedTaskId ??
				logTaskId[runId] ??
				(record.dagId?.startsWith("dts_release_build_") ? "dbt_build" : "dbt_run");
			setLogLoading((prev) => ({ ...prev, [runId]: true }));
			try {
				let logText = "";
				if (record.entryKey === "AIRFLOW_DAG" || record.entryKey === "DBT_RUN") {
					const result = await getDbtRunLog(runId, {
						dagId: record.dagId,
						taskId,
						tryNumber: tryNum,
					});
					logText = typeof result === "string" ? result : ((result as any)?.log ?? "");
				} else {
					logText = "（该类型日志暂不支持直接查看，请前往对应任务详情页）";
				}
				setLogContent((prev) => ({ ...prev, [runId]: logText }));
				setTimeout(() => {
					if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight;
				}, 50);
			} catch (e: unknown) {
				setLogContent((prev) => ({
					...prev,
					[runId]: `[错误] ${(e as { message?: string })?.message ?? "日志加载失败"}`,
				}));
			} finally {
				setLogLoading((prev) => ({ ...prev, [runId]: false }));
			}
		},
		[logTaskId, logTryNumber],
	);

	useEffect(() => {
		if (!expandedRunId || logContent[expandedRunId] !== undefined || logLoading[expandedRunId]) return;
		const record = records.find((item) => (item.externalRunId ?? item.id) === expandedRunId);
		if (record) void loadLog(record);
	}, [expandedRunId, records, loadLog, logContent, logLoading]);

	const baseColumns: ColumnsType<ExternalRun> = [
		{ title: "任务名称", dataIndex: "artifactName", render: (v) => v || "-" },
		{
			title: "类型",
			dataIndex: "entryKey",
			width: 140,
			render: (v) => <Tag>{v || "-"}</Tag>,
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 110,
			render: (v) => <Tag color={STATUS_TAG_COLOR[v] ?? "default"}>{statusLabel(v, "-")}</Tag>,
		},
		{ title: "DAG ID", dataIndex: "dagId", width: 180, render: (v) => v || "-" },
		{
			title: "开始时间",
			dataIndex: "startedAt",
			sorter: (a, b) => {
				const ta = a.startedAt ? new Date(a.startedAt as any).getTime() : 0;
				const tb = b.startedAt ? new Date(b.startedAt as any).getTime() : 0;
				return ta - tb;
			},
			width: 170,
			render: (v) => (v ? dayjs(v).format("MM-DD HH:mm:ss") : "-"),
		},
		{
			title: "结束时间",
			dataIndex: "finishedAt",
			sorter: (a, b) => {
				const ta = a.finishedAt ? new Date(a.finishedAt as any).getTime() : 0;
				const tb = b.finishedAt ? new Date(b.finishedAt as any).getTime() : 0;
				return ta - tb;
			},
			width: 170,
			render: (v) => (v ? dayjs(v).format("MM-DD HH:mm:ss") : "-"),
		},
		{
			title: "耗时",
			dataIndex: "durationMs",
			width: 100,
			render: (v) => (v != null ? `${(v / 1000).toFixed(1)}s` : "-"),
		},
		actionColumn<ExternalRun>(
			(record) => {
				const runId = record.externalRunId ?? record.id;
				const isExpanded = expandedRunId === runId;
				return [
					{
						key: "log",
						label: isExpanded ? "收起日志" : "查看日志",
						hidden: record.entryKey !== "AIRFLOW_DAG" && record.entryKey !== "DBT_RUN",
						loading: logLoading[runId],
						onClick: () => {
							if (isExpanded) {
								setExpandedRunId(null);
								return;
							}
							setExpandedRunId(runId);
							if (!logContent[runId]) void loadLog(record);
						},
					},
				];
			},
			{ width: 200 },
		),
	];

	const columns = appendDetailAction(baseColumns, (row) => setDetailRow(row));

	return (
		<div className="space-y-6 px-6 py-6">
			<PageHeader title="日志查看" />
			<Card
				extra={
					<Space wrap>
						<Input
							placeholder="搜索任务名称..."
							value={keyword}
							onChange={(e) => setKeyword(e.target.value)}
							onPressEnter={() => void loadRecords()}
							style={{ width: 200 }}
						/>
						<Select value={entryKey} options={ENTRY_OPTIONS} onChange={setEntryKey} style={{ width: 150 }} />
						<Select value={status} options={STATUS_OPTIONS} onChange={setStatus} style={{ width: 130 }} />
						<Button onClick={() => void loadRecords()}>刷新</Button>
					</Space>
				}
			>
				<CompactTable<ExternalRun>
					rowKey={(r) => r.id}
					columns={columns}
					dataSource={records}
					loading={loading}
					expandable={{
						expandedRowKeys: expandedRunId
							? [records.find((r) => (r.externalRunId ?? r.id) === expandedRunId)?.id ?? ""]
							: [],
						showExpandColumn: false,
						expandedRowRender: (record) => {
							const runId = record.externalRunId ?? record.id;
							const content = logContent[runId];
							const tryNum = logTryNumber[runId] ?? 1;
							const taskId =
								logTaskId[runId] ?? (record.dagId?.startsWith("dts_release_build_") ? "dbt_build" : "dbt_run");
							return (
								<div style={{ padding: "8px 0" }}>
									<Space wrap style={{ marginBottom: 8 }}>
										<Text type="secondary">执行步骤:</Text>
										<Select
											aria-label="执行步骤"
											value={taskId}
											options={TASK_OPTIONS}
											style={{ width: 210 }}
											disabled={logLoading[runId]}
											onChange={(value) => {
												setLogTaskId((prev) => ({ ...prev, [runId]: value }));
												void loadLog(record, value);
											}}
										/>
										<Text type="secondary">尝试次数:</Text>
										<Input
											type="number"
											min={1}
											max={10}
											value={tryNum}
											style={{ width: 70 }}
											onChange={(e) => {
												const n = Number(e.target.value);
												if (n >= 1) setLogTryNumber((prev) => ({ ...prev, [runId]: n }));
											}}
										/>
										<Button size="small" onClick={() => void loadLog(record)}>
											重新加载
										</Button>
									</Space>
									<pre
										ref={logRef}
										style={{
											background: "#1e1e1e",
											color: "#d4d4d4",
											fontFamily: "monospace",
											fontSize: 12,
											lineHeight: 1.6,
											padding: 16,
											borderRadius: 6,
											maxHeight: 400,
											overflowY: "auto",
											overflowX: "auto",
											whiteSpace: "pre-wrap",
											wordBreak: "break-all",
											margin: 0,
										}}
									>
										{logLoading[runId] ? "加载中..." : (content ?? "点击「查看日志」加载")}
									</pre>
								</div>
							);
						},
					}}
				/>
			</Card>
			<RecordDetailDrawer<ExternalRun>
				open={detailRow !== null}
				onClose={() => setDetailRow(null)}
				record={detailRow}
				columns={baseColumns}
				title="运行详情"
			/>
		</div>
	);
}
