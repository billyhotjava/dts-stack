import { useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "@/routes/hooks";
import { Alert, Button, Card, DatePicker, Drawer, Input, Modal, Progress, Segmented, Select, Space, Spin, Table, Tag, Tooltip, Typography, message } from "antd";
import { ArrowLeftOutlined, PlayCircleOutlined, ReloadOutlined } from "@ant-design/icons";
import { useRouter } from "@/routes/hooks";
import {
    ingestionTaskAPI,
    type IngestionExecutionDTO,
    type IngestionTaskDTO,
    type IngestionExecutionLog,
    type IngestionIncrementalAuditDTO,
    type IngestionIncrementalStateDTO,
} from "@/api/ingestion";
import { formatTimestamp, formatNumber } from "@/utils/format";

type ExecutionProgressView = {
    percent: number;
    status: "active" | "success" | "exception";
    stage: string;
    detail: string;
    terminal: boolean;
};

const GOVERNANCE_FAILURE_FILTER = "GOVERNANCE_LIMIT,GOVERNANCE_QUEUE_TIMEOUT";

export default function TransformExecutionHistoryPage() {
    const { id } = useParams();
    const router = useRouter();
    const [task, setTask] = useState<IngestionTaskDTO | null>(null);
    const [executions, setExecutions] = useState<IngestionExecutionDTO[]>([]);
    const [loading, setLoading] = useState(false);
    const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });
    const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);
    const [failureCategoryFilter, setFailureCategoryFilter] = useState<string | undefined>(undefined);
    const [failureQuickFilter, setFailureQuickFilter] = useState<"all" | "governance">("all");
    const [logVisible, setLogVisible] = useState(false);
    const [logLoading, setLogLoading] = useState(false);
    const [logContent, setLogContent] = useState("");
    const [logMeta, setLogMeta] = useState<IngestionExecutionLog | null>(null);
    const [activeExecution, setActiveExecution] = useState<IngestionExecutionDTO | null>(null);
    const [logKeyword, setLogKeyword] = useState("");
    const [logScope, setLogScope] = useState<"single" | "all">("single");
    const [executeSubmitting, setExecuteSubmitting] = useState(false);
    const [executeProgressOpen, setExecuteProgressOpen] = useState(false);
    const [executeProgress, setExecuteProgress] = useState<ExecutionProgressView>({
        percent: 0,
        status: "active",
        stage: "等待提交",
        detail: "",
        terminal: false,
    });
    const [latestExecution, setLatestExecution] = useState<IngestionExecutionDTO | null>(null);
    const [incrementalStates, setIncrementalStates] = useState<IngestionIncrementalStateDTO[]>([]);
    const [incrementalStatesLoading, setIncrementalStatesLoading] = useState(false);
    const [incrementalAudits, setIncrementalAudits] = useState<IngestionIncrementalAuditDTO[]>([]);
    const [incrementalAuditsLoading, setIncrementalAuditsLoading] = useState(false);
    const [auditDetailVisible, setAuditDetailVisible] = useState(false);
    const [auditDetailLoading, setAuditDetailLoading] = useState(false);
    const [auditDetailRows, setAuditDetailRows] = useState<IngestionIncrementalAuditDTO[]>([]);
    const [auditDetailExecution, setAuditDetailExecution] = useState<IngestionExecutionDTO | null>(null);
    const [auditFrom, setAuditFrom] = useState<string | undefined>();
    const [auditTo, setAuditTo] = useState<string | undefined>();
    const executePollTimerRef = useRef<number | null>(null);
    const executeStartedAtRef = useRef<number>(0);

    useEffect(() => {
        if (id) {
            loadTask();
            loadExecutions();
        }
    }, [id, pagination.current, statusFilter, failureCategoryFilter]);

    useEffect(() => {
        if (!task?.id) {
            setIncrementalStates([]);
            setIncrementalAudits([]);
            return;
        }
        if (normalizeText(task.syncMode).toLowerCase() !== "incremental") {
            setIncrementalStates([]);
            setIncrementalAudits([]);
            return;
        }
        void loadIncrementalStates(Number(task.id));
        const executionIds = executions.map((it) => it.id).filter((it): it is number => typeof it === "number");
        void loadIncrementalAudits(Number(task.id), undefined, true, executionIds);
    }, [task?.id, task?.syncMode, executions, auditFrom, auditTo]);

    useEffect(() => {
        return () => {
            if (executePollTimerRef.current !== null) {
                window.clearTimeout(executePollTimerRef.current);
                executePollTimerRef.current = null;
            }
        };
    }, []);

    const normalizeText = (value?: string) => String(value || "").trim();

    const loadTask = async () => {
        try {
            const result = await ingestionTaskAPI.getTask(Number(id));
            setTask(result);
        } catch (error: any) {
            message.error("加载任务信息失败: " + (error.message || "未知错误"));
        }
    };

    const loadExecutions = async () => {
        setLoading(true);
        try {
            const result = await ingestionTaskAPI.getExecutions(Number(id), {
                page: pagination.current - 1,
                size: pagination.pageSize,
                sort: "createdAt,desc",
                status: statusFilter,
                failureCategory: failureCategoryFilter,
            });
            const content = Array.isArray(result?.content) ? result.content : [];
            setExecutions(content);
            const total = typeof result?.totalElements === "number" ? result.totalElements : content.length;
            setPagination((prev) => ({ ...prev, total }));
        } catch (error: any) {
            message.error("加载执行历史失败: " + (error.message || "未知错误"));
            setExecutions([]);
        } finally {
            setLoading(false);
        }
    };

    const loadIncrementalStates = async (taskId: number, silent?: boolean) => {
        try {
            if (!silent) {
                setIncrementalStatesLoading(true);
            }
            const states = await ingestionTaskAPI.getIncrementalStates(taskId);
            setIncrementalStates(Array.isArray(states) ? states : []);
        } catch (error: any) {
            setIncrementalStates([]);
            message.error("加载增量检查点失败: " + (error.message || "未知错误"));
        } finally {
            if (!silent) {
                setIncrementalStatesLoading(false);
            }
        }
    };

    const loadIncrementalAudits = async (
        taskId: number,
        executionId?: number,
        silent?: boolean,
        executionIds?: number[]
    ) => {
        try {
            if (!silent) {
                setIncrementalAuditsLoading(true);
            }
            if (!executionId && (!executionIds || !executionIds.length)) {
                setIncrementalAudits([]);
                return;
            }
            const result = await ingestionTaskAPI.getIncrementalAuditsPage(taskId, {
                executionId,
                executionIds,
                from: auditFrom,
                to: auditTo,
                page: 0,
                size: 2000,
                sort: "createdAt,desc",
            });
            const content = Array.isArray(result?.content) ? result.content : [];
            setIncrementalAudits(content);
        } catch (error: any) {
            setIncrementalAudits([]);
            message.error("加载增量审计失败: " + (error.message || "未知错误"));
        } finally {
            if (!silent) {
                setIncrementalAuditsLoading(false);
            }
        }
    };

    /** Adaptive polling: starts fast, slows down over time. Never hard-stops. */
    const adaptivePollDelay = (elapsedMs: number): number => {
        if (elapsedMs < 30_000) return 3_000;    // first 30s: every 3s
        if (elapsedMs < 120_000) return 5_000;   // 30s-2min: every 5s
        if (elapsedMs < 300_000) return 10_000;  // 2-5min: every 10s
        return 30_000;                            // >5min: every 30s
    };

    const stopExecutePolling = () => {
        if (executePollTimerRef.current !== null) {
            window.clearTimeout(executePollTimerRef.current);
            executePollTimerRef.current = null;
        }
    };

    const mapExecutionProgress = (execution: IngestionExecutionDTO | null, elapsedMs: number): ExecutionProgressView => {
        if (!execution) {
            const percent = Math.min(45, 15 + Math.floor(elapsedMs / 5000) * 5);
            return {
                percent,
                status: "active",
                stage: "等待执行记录",
                detail: "任务已提交，系统正在准备 DAG 和作业参数。",
                terminal: false,
            };
        }
        const normalized = normalizeText(execution.status).toLowerCase();
        if (normalized === "success") {
            return {
                percent: 100,
                status: "success",
                stage: "执行成功",
                detail: "任务已执行完成。",
                terminal: true,
            };
        }
        if (normalized === "failed" || normalized === "error") {
            return {
                percent: 100,
                status: "exception",
                stage: "执行失败",
                detail: normalizeText(execution.errorMessage) || "执行失败，请查看日志。",
                terminal: true,
            };
        }
        if (normalized === "preparing") {
            return {
                percent: 60,
                status: "active",
                stage: "准备执行",
                detail: "正在生成/校验 Addax 作业并等待 DAG 就绪。",
                terminal: false,
            };
        }
        return {
            percent: 85,
            status: "active",
            stage: "执行中",
            detail: "已触发执行，正在同步运行状态。",
            terminal: false,
        };
    };

    const startExecuteProgressPolling = (taskId: number, _pollIntervalMs?: number) => {
        stopExecutePolling();
        const startTime = Date.now();
        executeStartedAtRef.current = startTime;
        setExecuteProgressOpen(true);
        setLatestExecution(null);
        setExecuteProgress({
            percent: 10,
            status: "active",
            stage: "任务已提交",
            detail: "正在后台触发执行。",
            terminal: false,
        });
        let slowNotified = false;

        const pollOnce = async () => {
            const elapsed = Date.now() - startTime;
            if (elapsed > 5 * 60 * 1000 && !slowNotified) {
                slowNotified = true;
                message.info("执行时间较长，已切换为低频刷新");
            }
            try {
                const execution = await ingestionTaskAPI.getLatestExecution(taskId);
                setLatestExecution(execution);
                const next = mapExecutionProgress(execution, elapsed);
                setExecuteProgress(next);
                if (next.terminal) {
                    stopExecutePolling();
                    void loadTask();
                    void loadExecutions();
                    if (task?.id && normalizeText(task.syncMode).toLowerCase() === "incremental") {
                        void loadIncrementalStates(Number(task.id), true);
                        const executionIds = executions.map((it) => it.id).filter((it): it is number => typeof it === "number");
                        void loadIncrementalAudits(Number(task.id), undefined, true, executionIds);
                    }
                    return;
                }
            } catch {
                setExecuteProgress((prev) => ({ ...prev, detail: "状态同步中，稍后自动重试。" }));
            }
            const elapsed2 = Date.now() - startTime;
            executePollTimerRef.current = window.setTimeout(pollOnce, adaptivePollDelay(elapsed2));
        };

        void pollOnce();
    };

    const handleExecute = async () => {
        if (!task?.id) return;
        setExecuteSubmitting(true);
        try {
            const submit = await ingestionTaskAPI.executeTaskAsync(Number(task.id));
            message.success("任务已提交，后台正在触发执行");
            startExecuteProgressPolling(Number(task.id), submit?.pollIntervalMs);
        } catch (error: any) {
            message.error("执行失败: " + (error.message || "未知错误"));
        } finally {
            setExecuteSubmitting(false);
        }
    };

    const submitRetry = async (
        record: IngestionExecutionDTO,
        mode: "FAILED_ONLY" | "FULL_RERUN",
    ) => {
        if (!id) return;
        try {
            await ingestionTaskAPI.retryExecution(Number(id), record.id, { mode });
            message.success(mode === "FULL_RERUN" ? "已提交整批重跑任务" : "已提交失败重试任务");
            startExecuteProgressPolling(Number(id));
        } catch (error: any) {
            message.error("重试失败: " + (error.message || "未知错误"));
        }
    };

    const handleFullRerun = (record: IngestionExecutionDTO) => {
        Modal.confirm({
            title: "确认整批重跑",
            content: "将按当前任务配置重新执行整批作业（FULL_RERUN），确认继续？",
            okText: "确认重跑",
            onOk: () => submitRetry(record, "FULL_RERUN"),
        });
    };

    const loadLog = async (record: IngestionExecutionDTO, opts?: { silent?: boolean }) => {
        if (!id) return;
        try {
            if (!opts?.silent) {
                setLogVisible(true);
            }
            setActiveExecution(record);
            setLogLoading(true);
            const result = await ingestionTaskAPI.getExecutionLog(Number(id), record.id, {
                tryNumber: 1,
                keyword: normalizeText(logKeyword) || undefined,
                scope: logScope,
            });
            setLogMeta(result);
            const content = String(result?.log || result?.message || "");
            setLogContent(content);
        } catch (error: any) {
            if (!opts?.silent) {
                message.error("获取日志失败: " + (error.message || "未知错误"));
            }
            setLogMeta(null);
            setLogContent("");
        } finally {
            setLogLoading(false);
        }
    };

    const renderStatus = (status: string) => {
        const statusMap: Record<string, { color: string; text: string }> = {
            running: { color: "processing", text: "运行中" },
            success: { color: "success", text: "成功" },
            failed: { color: "error", text: "失败" },
        };
        const config = statusMap[status] || { color: "default", text: status };
        return <Tag color={config.color}>{config.text}</Tag>;
    };

    const renderTriggerMode = (mode?: string) => {
        const normalized = normalizeText(mode).toUpperCase();
        if (normalized === "FAILED_ONLY") {
            return <Tag color="gold">失败重试</Tag>;
        }
        if (normalized === "FULL_RERUN") {
            return <Tag color="purple">整批重跑</Tag>;
        }
        return <Tag>手动执行</Tag>;
    };

    const calculateDuration = (start?: string, end?: string) => {
        if (!start) return "-";
        if (!end) return "进行中";

        const duration = new Date(end).getTime() - new Date(start).getTime();
        const seconds = Math.floor(duration / 1000);
        const minutes = Math.floor(seconds / 60);
        const hours = Math.floor(minutes / 60);

        if (hours > 0) {
            return `${hours}小时${minutes % 60}分${seconds % 60}秒`;
        }
        if (minutes > 0) {
            return `${minutes}分${seconds % 60}秒`;
        }
        return `${seconds}秒`;
    };

    const formatQueueWait = (seconds?: number) => {
        if (seconds === undefined || seconds === null) {
            return "-";
        }
        const safe = Math.max(0, Math.floor(seconds));
        if (safe < 60) {
            return `${safe}秒`;
        }
        const minutes = Math.floor(safe / 60);
        const remain = safe % 60;
        return `${minutes}分${remain}秒`;
    };

    const auditSummaryByExecution = useMemo(() => {
        const summary = new Map<number, { total: number; advanced: number; unchanged: number }>();
        for (const audit of incrementalAudits) {
            const executionId = Number(audit.executionId || 0);
            if (!executionId) {
                continue;
            }
            const item = summary.get(executionId) || { total: 0, advanced: 0, unchanged: 0 };
            item.total += 1;
            if (Boolean(audit.advanced)) {
                item.advanced += 1;
            } else {
                item.unchanged += 1;
            }
            summary.set(executionId, item);
        }
        return summary;
    }, [incrementalAudits]);

    const auditTablesByExecution = useMemo(() => {
        const map = new Map<number, { advanced: string[]; unchanged: string[] }>();
        for (const audit of incrementalAudits) {
            const executionId = Number(audit.executionId || 0);
            if (!executionId) {
                continue;
            }
            const sourceTable = normalizeText(audit.sourceTable) || "-";
            const item = map.get(executionId) || { advanced: [], unchanged: [] };
            const list = Boolean(audit.advanced) ? item.advanced : item.unchanged;
            if (!list.includes(sourceTable)) {
                list.push(sourceTable);
            }
            map.set(executionId, item);
        }
        return map;
    }, [incrementalAudits]);

    const openAuditDetail = async (record: IngestionExecutionDTO) => {
        if (!task?.id) {
            return;
        }
        setAuditDetailExecution(record);
        setAuditDetailVisible(true);
        setAuditDetailLoading(true);
        try {
            const result = await ingestionTaskAPI.getIncrementalAuditsPage(Number(task.id), {
                executionId: record.id,
                page: 0,
                size: 1000,
                sort: "createdAt,desc",
            });
            const rows = Array.isArray(result?.content) ? result.content : [];
            setAuditDetailRows(rows);
        } catch (error: any) {
            setAuditDetailRows([]);
            message.error("加载执行水位详情失败: " + (error.message || "未知错误"));
        } finally {
            setAuditDetailLoading(false);
        }
    };

    const auditDetailSummary = useMemo(() => {
        const total = auditDetailRows.length;
        const advanced = auditDetailRows.filter((it) => Boolean(it.advanced)).length;
        const unchanged = Math.max(0, total - advanced);
        const rate = total > 0 ? Math.round((advanced * 100) / total) : 0;
        return { total, advanced, unchanged, rate };
    }, [auditDetailRows]);

    const columns = [
        {
            title: "执行ID",
            dataIndex: "executionId",
            key: "executionId",
            width: 200,
        },
        {
            title: "触发方式",
            dataIndex: "triggerMode",
            key: "triggerMode",
            width: 120,
            render: (value: string) => renderTriggerMode(value),
        },
        {
            title: "状态",
            dataIndex: "status",
            key: "status",
            width: 100,
            render: renderStatus,
        },
        {
            title: "开始时间",
            dataIndex: "startTime",
            key: "startTime",
            width: 180,
            render: formatTimestamp,
        },
        {
            title: "结束时间",
            dataIndex: "endTime",
            key: "endTime",
            width: 180,
            render: formatTimestamp,
        },
        {
            title: "执行时长",
            key: "duration",
            width: 150,
            render: (_: any, record: IngestionExecutionDTO) => calculateDuration(record.startTime, record.endTime),
        },
        {
            title: "排队等待",
            dataIndex: "queueWaitSeconds",
            key: "queueWaitSeconds",
            width: 130,
            render: (value: number) => formatQueueWait(value),
        },
        {
            title: "读取行数",
            dataIndex: "rowsRead",
            key: "rowsRead",
            width: 120,
            render: formatNumber,
        },
        {
            title: "写入行数",
            dataIndex: "rowsWritten",
            key: "rowsWritten",
            width: 120,
            render: formatNumber,
        },
        {
            title: "水位推进",
            key: "checkpointUpdated",
            width: 180,
            render: (_: any, record: IngestionExecutionDTO) => {
                const summary = auditSummaryByExecution.get(record.id);
                const tables = auditTablesByExecution.get(record.id);
                if (!summary) {
                    return "-";
                }
                const tooltipTitle = (
                    <div style={{ maxWidth: 420 }}>
                        <div>推进表：{tables?.advanced?.length ? tables.advanced.join(", ") : "无"}</div>
                        <div>未推进表：{tables?.unchanged?.length ? tables.unchanged.join(", ") : "无"}</div>
                    </div>
                );
                return (
                    <Space size={4}>
                        <Tooltip title={tooltipTitle}>
                            {summary.advanced > 0 ? (
                                <Tag color="success">
                                    推进 {summary.advanced}/{summary.total} ({Math.round((summary.advanced * 100) / summary.total)}%)
                                </Tag>
                            ) : (
                                <Tag color="warning">未推进</Tag>
                            )}
                        </Tooltip>
                        <Button type="link" size="small" onClick={() => openAuditDetail(record)}>
                            详情
                        </Button>
                    </Space>
                );
            },
        },
        {
            title: "失败分类",
            dataIndex: "failureCategory",
            key: "failureCategory",
            width: 150,
            render: (_: string, record: IngestionExecutionDTO) => {
                if (!normalizeText(record.failureCategory)) {
                    return "-";
                }
                const normalized = normalizeText(record.failureCategory).toUpperCase();
                const color = normalized.startsWith("GOVERNANCE") ? "warning" : "error";
                return (
                    <Tooltip title={normalizeText(record.failureAdvice) || undefined}>
                        <Tag color={color}>{record.failureCategory}</Tag>
                    </Tooltip>
                );
            },
        },
        {
            title: "错误信息",
            dataIndex: "errorMessage",
            key: "errorMessage",
            ellipsis: true,
            render: (text: string) => text || "-",
        },
        {
            title: "日志",
            key: "log",
            width: 220,
            render: (_: any, record: IngestionExecutionDTO) => (
                <Space size={4}>
                    <Button size="small" onClick={() => loadLog(record)}>
                        查看日志
                    </Button>
                    {normalizeText(record.status).toLowerCase() === "failed" ? (
                        <Button
                            size="small"
                            onClick={() => submitRetry(record, "FAILED_ONLY")}
                        >
                            失败重试
                        </Button>
                    ) : null}
                    {normalizeText(record.status).toLowerCase() !== "running" ? (
                        <Button size="small" onClick={() => handleFullRerun(record)}>
                            整批重跑
                        </Button>
                    ) : null}
                </Space>
            ),
        },
    ];

    const failureCategoryOptions = [
        { label: "连接错误", value: "CONNECTION_ERROR" },
        { label: "权限错误", value: "PERMISSION_ERROR" },
        { label: "DDL 错误", value: "DDL_ERROR" },
        { label: "DML 错误", value: "DML_ERROR" },
        { label: "数据质量", value: "DATA_QUALITY_ERROR" },
        { label: "治理拒绝", value: "GOVERNANCE_LIMIT" },
        { label: "治理队列超时", value: "GOVERNANCE_QUEUE_TIMEOUT" },
        { label: "运行时错误", value: "RUNTIME_ERROR" },
    ];

    if (!task) {
        return (
            <div className="flex justify-center items-center h-96">
                <Spin size="large" />
            </div>
        );
    }

    return (
        <div className="space-y-6">
            <Card
                title={`${task.name} - 执行历史`}
                extra={
                    <Space wrap>
                        <Button icon={<ArrowLeftOutlined />} onClick={() => router.push(`/explore/etl/transform/${id}`)}>
                            返回
                        </Button>
                        <Button
                            type="primary"
                            icon={<PlayCircleOutlined />}
                            onClick={handleExecute}
                            loading={executeSubmitting}
                            disabled={task?.status === "deleted"}
                        >
                            执行任务
                        </Button>
                        <Button icon={<ReloadOutlined />} onClick={loadExecutions} loading={loading}>
                            刷新
                        </Button>
                    </Space>
                }
            >
                <Space wrap style={{ marginBottom: 16 }}>
                    <Select
                        allowClear
                        placeholder="执行状态"
                        style={{ width: 140 }}
                        value={statusFilter}
                        options={[
                            { label: "运行中", value: "running" },
                            { label: "成功", value: "success" },
                            { label: "失败", value: "failed" },
                            { label: "错误", value: "error" },
                        ]}
                        onChange={(value) => {
                            setStatusFilter(value);
                            setPagination((prev) => ({ ...prev, current: 1 }));
                        }}
                    />
                    <Select
                        allowClear
                        placeholder="失败分类"
                        style={{ width: 180 }}
                        value={failureQuickFilter === "governance" ? undefined : failureCategoryFilter}
                        options={failureCategoryOptions}
                        onChange={(value) => {
                            setFailureCategoryFilter(value);
                            setFailureQuickFilter("all");
                            setPagination((prev) => ({ ...prev, current: 1 }));
                        }}
                    />
                    <Segmented
                        options={[
                            { label: "全部失败", value: "all" },
                            { label: "仅治理失败", value: "governance" },
                        ]}
                        value={failureQuickFilter}
                        onChange={(value) => {
                            const next = String(value) === "governance" ? "governance" : "all";
                            setFailureQuickFilter(next);
                            setFailureCategoryFilter(next === "governance" ? GOVERNANCE_FAILURE_FILTER : undefined);
                            setPagination((prev) => ({ ...prev, current: 1 }));
                        }}
                    />
                    {normalizeText(task.syncMode).toLowerCase() === "incremental" ? (
                        <DatePicker.RangePicker
                            showTime
                            allowClear
                            placeholder={["审计开始时间", "审计结束时间"]}
                            onChange={(values: any) => {
                                const from = values?.[0]?.toISOString?.();
                                const to = values?.[1]?.toISOString?.();
                                setAuditFrom(from || undefined);
                                setAuditTo(to || undefined);
                            }}
                        />
                    ) : null}
                    {normalizeText(task.syncMode).toLowerCase() === "incremental" ? (
                        <Button
                            icon={<ReloadOutlined />}
                            onClick={() => {
                                if (!task?.id) return;
                                const executionIds = executions.map((it) => it.id).filter((it): it is number => typeof it === "number");
                                void loadIncrementalAudits(Number(task.id), undefined, false, executionIds);
                            }}
                            loading={incrementalAuditsLoading}
                        >
                            刷新审计
                        </Button>
                    ) : null}
                </Space>
            </Card>

            <Card title="执行记录" extra={<Tag color="blue">{pagination.total} 条记录</Tag>}>
                <Table
                    columns={columns}
                    dataSource={executions}
                    rowKey="id"
                    loading={loading}
                    scroll={{ x: 1900 }}
                    pagination={{
                        current: pagination.current,
                        pageSize: pagination.pageSize,
                        total: pagination.total,
                        showSizeChanger: true,
                        showQuickJumper: true,
                        showTotal: (total) => `共 ${total} 条执行记录`,
                        onChange: (page, pageSize) => {
                            setPagination((prev) => ({ ...prev, current: page, pageSize: pageSize || 20 }));
                        },
                    }}
                />
            </Card>

            {normalizeText(task.syncMode).toLowerCase() === "incremental" ? (
                <Card
                    title="增量检查点（当前）"
                    extra={
                        <Space>
                            {auditFrom || auditTo ? (
                                <Tag color="processing">审计范围已生效</Tag>
                            ) : (
                                <Tag>审计范围：全部</Tag>
                            )}
                            <Button
                                icon={<ReloadOutlined />}
                                loading={incrementalStatesLoading}
                                onClick={() => task?.id && loadIncrementalStates(Number(task.id))}
                            >
                                刷新检查点
                            </Button>
                        </Space>
                    }
                >
                    <Table<IngestionIncrementalStateDTO>
                        rowKey={(record) => `${record.taskId}-${record.sourceTable}`}
                        size="small"
                        loading={incrementalStatesLoading}
                        pagination={false}
                        dataSource={incrementalStates}
                        locale={{ emptyText: "暂无检查点（首次增量成功后写入）" }}
                        columns={[
                            {
                                title: "源表",
                                dataIndex: "sourceTable",
                                key: "sourceTable",
                                render: (value: string) => <Typography.Text code>{value || "-"}</Typography.Text>,
                            },
                            {
                                title: "最新水位",
                                dataIndex: "lastSuccessWatermark",
                                key: "lastSuccessWatermark",
                                render: (value?: string) => value || "-",
                            },
                            {
                                title: "最近运行ID",
                                dataIndex: "lastRunId",
                                key: "lastRunId",
                                render: (value?: string) => value || "-",
                            },
                            {
                                title: "更新时间",
                                dataIndex: "updatedAt",
                                key: "updatedAt",
                                render: (value?: string) => (value ? formatTimestamp(value) : "-"),
                            },
                        ]}
                    />
                    <div className="mt-3 text-xs text-muted-foreground">
                        {"该区域是当前最新检查点快照；单次执行的前后水位请看“执行历史 > 水位推进 > 详情”。"}
                    </div>
                </Card>
            ) : null}
            <Modal
                title="执行进度"
                open={executeProgressOpen}
                maskClosable={false}
                onCancel={() => {
                    stopExecutePolling();
                    setExecuteProgressOpen(false);
                }}
                footer={
                    <Space>
                        <Button
                            onClick={() => {
                                stopExecutePolling();
                                setExecuteProgressOpen(false);
                                void loadExecutions();
                            }}
                        >
                            刷新列表
                        </Button>
                        <Button
                            type="primary"
                            onClick={() => {
                                stopExecutePolling();
                                setExecuteProgressOpen(false);
                            }}
                        >
                            {executeProgress.terminal ? "关闭" : "最小化"}
                        </Button>
                    </Space>
                }
                width={620}
            >
                <Space direction="vertical" size="middle" className="w-full">
                    <Typography.Text>任务：{task?.name || "-"}</Typography.Text>
                    <Progress percent={executeProgress.percent} status={executeProgress.status} />
                    <Alert
                        showIcon
                        type={
                            executeProgress.status === "success"
                                ? "success"
                                : executeProgress.status === "exception"
                                    ? "error"
                                    : "info"
                        }
                        message={executeProgress.stage}
                        description={executeProgress.detail}
                    />
                    {latestExecution ? (
                        <Typography.Text type="secondary">
                            执行ID：{latestExecution.executionId || latestExecution.id}，状态：{latestExecution.status}
                        </Typography.Text>
                    ) : null}
                </Space>
            </Modal>
            <Drawer
                title="执行日志"
                width={720}
                open={logVisible}
                onClose={() => setLogVisible(false)}
                extra={
                    <Space>
                        <Segmented
                            size="small"
                            value={logScope}
                            options={[
                                { label: "单节点", value: "single" },
                                { label: "全节点", value: "all" },
                            ]}
                            onChange={(value) => setLogScope((value as "single" | "all") || "single")}
                        />
                        <Input
                            size="small"
                            placeholder="关键字过滤"
                            value={logKeyword}
                            onChange={(event) => setLogKeyword(event.target.value)}
                            style={{ width: 180 }}
                        />
                        <Button
                            icon={<ReloadOutlined />}
                            loading={logLoading}
                            onClick={() => activeExecution && loadLog(activeExecution, { silent: true })}
                        >
                            刷新
                        </Button>
                    </Space>
                }
            >
                {activeExecution ? (
                    <Space direction="vertical" size="small" className="w-full">
                        <Typography.Text type="secondary">
                            执行ID：{activeExecution.executionId || activeExecution.id}
                            {logMeta?.dagId ? ` · DAG: ${logMeta.dagId}` : ""}
                            {logMeta?.failureCategory ? ` · ${logMeta.failureCategory}` : ""}
                        </Typography.Text>
                        {logMeta?.failureAdvice ? (
                            <Typography.Text type="warning">建议：{logMeta.failureAdvice}</Typography.Text>
                        ) : null}
                        <div className="rounded-md bg-muted p-3 text-xs whitespace-pre-wrap overflow-auto max-h-[60vh]">
                            {logLoading ? "日志加载中..." : logContent || "暂无日志"}
                        </div>
                    </Space>
                ) : (
                    <Typography.Text type="secondary">请选择执行记录查看日志。</Typography.Text>
                )}
            </Drawer>
            <Drawer
                title="执行水位详情"
                width={780}
                open={auditDetailVisible}
                onClose={() => setAuditDetailVisible(false)}
                extra={
                    <Space>
                        <Button
                            icon={<ReloadOutlined />}
                            loading={auditDetailLoading}
                            onClick={() => auditDetailExecution && openAuditDetail(auditDetailExecution)}
                        >
                            刷新
                        </Button>
                    </Space>
                }
            >
                <Space direction="vertical" size="small" className="w-full">
                    <Typography.Text type="secondary">
                        执行ID：{auditDetailExecution?.executionId || auditDetailExecution?.id || "-"}
                    </Typography.Text>
                    <Typography.Text type="secondary">
                        推进率：{auditDetailSummary.advanced}/{auditDetailSummary.total} ({auditDetailSummary.rate}%)，
                        未推进：{auditDetailSummary.unchanged}
                    </Typography.Text>
                    <Table<IngestionIncrementalAuditDTO>
                        rowKey={(record) => String(record.id)}
                        size="small"
                        loading={auditDetailLoading}
                        pagination={false}
                        locale={{ emptyText: "该次执行没有生成增量水位审计" }}
                        dataSource={auditDetailRows}
                        columns={[
                            {
                                title: "源表",
                                dataIndex: "sourceTable",
                                key: "sourceTable",
                                render: (value: string) => <Typography.Text code>{value || "-"}</Typography.Text>,
                            },
                            {
                                title: "增量列",
                                dataIndex: "incrementalColumn",
                                key: "incrementalColumn",
                                render: (value?: string) => value || "-",
                            },
                            {
                                title: "执行前水位",
                                dataIndex: "beforeWatermark",
                                key: "beforeWatermark",
                                render: (value?: string) => value || "-",
                            },
                            {
                                title: "执行后水位",
                                dataIndex: "afterWatermark",
                                key: "afterWatermark",
                                render: (value?: string) => value || "-",
                            },
                            {
                                title: "是否推进",
                                dataIndex: "advanced",
                                key: "advanced",
                                width: 110,
                                render: (value?: boolean) =>
                                    value ? <Tag color="success">是</Tag> : <Tag color="warning">否</Tag>,
                            },
                            {
                                title: "记录时间",
                                dataIndex: "createdAt",
                                key: "createdAt",
                                render: (value?: string) => (value ? formatTimestamp(value) : "-"),
                            },
                        ]}
                    />
                </Space>
            </Drawer>
        </div>
    );
}
