import { Alert, Button, Descriptions, Empty, Modal, Space, Spin, Tabs, Tag, Tooltip } from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router";
import { toast } from "sonner";
import { ClassificationTag } from "@/analytics/pages/screens/components/ClassificationTag";
import {
	type IngestionChangeLogDTO,
	type IngestionExecutionDTO,
	type IngestionTaskDTO,
	type IngestionTaskRevisionDTO,
	ingestionTaskAPI,
} from "@/api/ingestion";
import { PageHeader } from "@/components/page-header";
import { CompactTable } from "@/components/table";
import { useParams, useRouter } from "@/routes/hooks";
import { formatNumber, formatTimestamp } from "@/utils/format";
import { AccessQualityPanel } from "./AccessGovernancePanels";
import styles from "./AccessPlanDetailPage.module.css";
import { type AccessPlanOperation, runAccessPlanOperation } from "./accessPlanOperations";
import { inferAccessKind } from "./accessPlanPayload";
import { resolveAccessRevisionView } from "./accessRevisionView";
import { accessTargetMappings, isModelBoundAccess } from "./accessTargetSummary";
import {
	acquireOwnedSingleFlight,
	ownsSingleFlight,
	releaseOwnedSingleFlight,
	resetOwnedSingleFlight,
} from "./accessSingleFlight";
import ExecutionHistoryTable from "./shared/ExecutionHistoryTable";

const CHANGE_TYPE_LABELS: Record<string, string> = {
	TASK_CREATE: "新建任务",
	CONN_PARAM: "连接参数变更",
	CATALOG_CHANGE: "同步范围变更",
	SCHEDULE_CHANGE: "调度配置变更",
	TASK_UPDATE: "任务信息更新",
};

const CHANGE_STATUS_LABELS: Record<string, string> = {
	DONE: "已完成",
	PENDING: "待处理",
	NEEDS_REVIEW: "需复核",
	APPROVAL: "待审批",
	REJECTED: "已驳回",
};

const TASK_STATUS_LABELS: Record<string, string> = {
	active: "活跃",
	draft: "草稿",
	paused: "暂停",
	deleted: "已删除",
	failed: "异常",
};

const EXECUTION_STATUS_COLORS: Record<string, string> = {
	SUCCESS: "success",
	RUNNING: "processing",
	QUEUED: "blue",
	FAILED: "error",
	TIMEOUT: "warning",
};

const statusTag = (status?: string) => {
	const normalized = String(status || "")
		.trim()
		.toLowerCase();
	return (
		<Tag color={normalized === "active" ? "success" : normalized === "failed" ? "error" : undefined}>
			{TASK_STATUS_LABELS[normalized] || status || "未记录"}
		</Tag>
	);
};

const executionStatusTag = (status?: string) => {
	const normalized = String(status || "")
		.trim()
		.toUpperCase();
	return <Tag color={EXECUTION_STATUS_COLORS[normalized]}>{normalized || "未记录"}</Tag>;
};

const changeStatusTag = (status?: string) => {
	const normalized = String(status || "")
		.trim()
		.toUpperCase();
	const color =
		normalized === "DONE" ? "success" : normalized === "REJECTED" ? "error" : normalized ? "warning" : undefined;
	return <Tag color={color}>{CHANGE_STATUS_LABELS[normalized] || status || "未记录"}</Tag>;
};

const riskTag = (risk?: string) => {
	const normalized = String(risk || "")
		.trim()
		.toUpperCase();
	if (normalized === "H") return <Tag color="error">高</Tag>;
	if (normalized === "M") return <Tag color="warning">中</Tag>;
	if (normalized === "L") return <Tag color="success">低</Tag>;
	return <Tag>未评估</Tag>;
};

const syncModeLabel = (mode?: string) => {
	const normalized = String(mode || "").toLowerCase();
	if (normalized.includes("incremental")) return "增量同步";
	if (normalized.includes("full")) return "全量同步";
	return mode || "未记录";
};

type TableMappingRow = {
	key: string;
	source: string;
	target: string;
};

const WRITE_MODE_LABELS: Record<string, string> = {
	append: "追加写入",
	insert: "追加写入",
	overwrite: "覆盖写入",
	replace: "覆盖写入",
	truncate: "覆盖写入",
	update: "更新写入",
	upsert: "更新或插入",
};

const writeStrategyLabel = (task: IngestionTaskDTO) => {
	if (isModelBoundAccess(task)) return "追加写入已有模型表（不清空、不重建）";
	const writeMode = String(task.syncConfig?.writeMode || task.syncConfig?.replaceMode || "")
		.trim()
		.toLowerCase();
	return WRITE_MODE_LABELS[writeMode] || syncModeLabel(task.syncMode);
};

const targetTableSummary = (mappings: TableMappingRow[]) => {
	const targets = [
		...new Set(mappings.map((mapping) => mapping.target).filter((target) => target && target !== "未记录")),
	];
	if (!targets.length) return "未记录，请先核对任务配置";
	const visible = targets.slice(0, 5).join("、");
	return targets.length > 5 ? `${visible} 等 ${targets.length} 张表` : visible;
};

const resolveDetailTab = (value: string | null) =>
	value && ["overview", "history", "quality", "changes"].includes(value) ? value : "overview";

export default function AccessPlanDetailPage() {
	const { taskId: taskIdParam } = useParams();
	const router = useRouter();
	const [searchParams] = useSearchParams();
	const parsedTaskId = Number(taskIdParam);
	const validTaskId = Number.isSafeInteger(parsedTaskId) && parsedTaskId > 0;
	const taskId = validTaskId ? parsedTaskId : 0;
	const [task, setTask] = useState<IngestionTaskDTO | null>(null);
	const [latestExecution, setLatestExecution] = useState<IngestionExecutionDTO | null>(null);
	const [revisions, setRevisions] = useState<IngestionTaskRevisionDTO[]>([]);
	const [loading, setLoading] = useState(false);
	const [loadError, setLoadError] = useState<string>();
	const [latestError, setLatestError] = useState(false);
	const [revisionsError, setRevisionsError] = useState(false);
	const requestedTab = resolveDetailTab(searchParams.get("tab"));
	const [activeTab, setActiveTab] = useState(requestedTab);
	const [changes, setChanges] = useState<IngestionChangeLogDTO[]>([]);
	const [changesLoading, setChangesLoading] = useState(false);
	const [changesError, setChangesError] = useState(false);
	const [changesLoaded, setChangesLoaded] = useState(false);
	const [changesPagination, setChangesPagination] = useState({ current: 1, pageSize: 10, total: 0 });
	const [operation, setOperation] = useState<AccessPlanOperation | null>(null);
	const detailRequestIdRef = useRef(0);
	const changesRequestIdRef = useRef(0);
	const routeTaskIdRef = useRef(taskId);
	const operationLockRef = useRef<symbol | null>(null);
	routeTaskIdRef.current = taskId;

	const loadDetail = useCallback(async () => {
		const requestId = ++detailRequestIdRef.current;
		changesRequestIdRef.current += 1;
		setTask(null);
		setLatestExecution(null);
		setRevisions([]);
		setLoadError(undefined);
		setLatestError(false);
		setRevisionsError(false);
		setChanges([]);
		setChangesLoaded(false);
		setChangesError(false);
		setChangesPagination({ current: 1, pageSize: 10, total: 0 });
		if (!validTaskId) {
			if (detailRequestIdRef.current === requestId) {
				setLoading(false);
				setLoadError("接入任务编号无效，无法加载详情。");
			}
			return;
		}

		setLoading(true);
		try {
			const [taskResult, latestResult, revisionsResult] = await Promise.allSettled([
				ingestionTaskAPI.getTask(taskId),
				ingestionTaskAPI.getLatestExecution(taskId),
				ingestionTaskAPI.getTaskRevisions(taskId),
			]);
			if (detailRequestIdRef.current !== requestId) return;
			if (taskResult.status === "rejected") {
				setTask(null);
				setLoadError("接入任务加载失败，请稍后重试。");
				return;
			}
			setTask(taskResult.value);
			if (latestResult.status === "fulfilled") {
				setLatestExecution(latestResult.value);
			} else {
				setLatestExecution(null);
				setLatestError(true);
			}
			if (revisionsResult.status === "fulfilled") {
				setRevisions(revisionsResult.value);
			} else {
				setRevisions([]);
				setRevisionsError(true);
			}
		} finally {
			if (detailRequestIdRef.current === requestId) {
				setLoading(false);
			}
		}
	}, [taskId, validTaskId]);

	useEffect(() => {
		void loadDetail();
		return () => {
			detailRequestIdRef.current += 1;
			changesRequestIdRef.current += 1;
		};
	}, [loadDetail]);

	// biome-ignore lint/correctness/useExhaustiveDependencies: changing the route task must reapply the requested tab even when the tab query is unchanged.
	useEffect(() => {
		setActiveTab(requestedTab);
	}, [requestedTab, taskId]);

	// biome-ignore lint/correctness/useExhaustiveDependencies: route task changes must clear an in-flight operation state.
	useEffect(() => {
		setOperation(null);
		resetOwnedSingleFlight(operationLockRef);
	}, [taskId]);

	useEffect(() => {
		if (task && searchParams.get("mode") === "edit") {
			router.replace(`/foundation/data-sources/access/new?kind=${inferAccessKind(task)}&editId=${task.id || taskId}`);
		}
	}, [router, searchParams, task, taskId]);

	const loadChanges = useCallback(
		async (page: number, pageSize: number) => {
			if (!validTaskId) return;
			const requestId = ++changesRequestIdRef.current;
			setChangesLoading(true);
			setChangesError(false);
			try {
				const result = await ingestionTaskAPI.getChangeLogs({
					taskId,
					page: page - 1,
					size: pageSize,
					sort: "createdDate,desc",
				});
				if (changesRequestIdRef.current !== requestId) return;
				const content = Array.isArray(result?.content) ? result.content : [];
				setChanges(content);
				setChangesPagination({
					current: typeof result?.number === "number" ? result.number + 1 : page,
					pageSize: typeof result?.size === "number" ? result.size : pageSize,
					total: typeof result?.totalElements === "number" ? result.totalElements : content.length,
				});
				setChangesLoaded(true);
			} catch {
				if (changesRequestIdRef.current !== requestId) return;
				setChanges([]);
				setChangesError(true);
				setChangesLoaded(true);
			} finally {
				if (changesRequestIdRef.current === requestId) {
					setChangesLoading(false);
				}
			}
		},
		[taskId, validTaskId],
	);

	useEffect(() => {
		if (activeTab === "changes" && task && !changesLoaded) {
			void loadChanges(1, 10);
		}
	}, [activeTab, changesLoaded, loadChanges, task]);

	const tableMappings = useMemo<TableMappingRow[]>(
		() => accessTargetMappings(task),
		[task],
	);

	const mappingColumns: ColumnsType<TableMappingRow> = [
		{ title: "源表 / 资源", dataIndex: "source", key: "source" },
		{ title: "目标表", dataIndex: "target", key: "target" },
	];

	const changeColumns: ColumnsType<IngestionChangeLogDTO> = [
		{
			title: "发生时间",
			dataIndex: "createdDate",
			key: "createdDate",
			width: 180,
			render: (value) => formatTimestamp(value) || "-",
		},
		{
			title: "变更类型",
			dataIndex: "changeType",
			key: "changeType",
			width: 150,
			render: (value) => CHANGE_TYPE_LABELS[String(value || "")] || value || "未记录",
		},
		{ title: "摘要", dataIndex: "summary", key: "summary", ellipsis: true },
		{ title: "风险", dataIndex: "riskLevel", key: "riskLevel", width: 100, render: riskTag },
		{ title: "状态", dataIndex: "status", key: "status", width: 110, render: changeStatusTag },
		{ title: "记录人", dataIndex: "createdBy", key: "createdBy", width: 130, render: (value) => value || "-" },
	];

	const onChangesTableChange = (pagination: TablePaginationConfig) => {
		const nextPageSize = pagination.pageSize || 10;
		const nextPage = nextPageSize === changesPagination.pageSize ? pagination.current || 1 : 1;
		void loadChanges(nextPage, nextPageSize);
	};

	const taskDeleted = String(task?.status || "").toLowerCase() === "deleted";
	const { activeRevisionNumber, hasDraftRevision, canExecuteActiveRevision, executeReason } = resolveAccessRevisionView(
		task,
		revisions,
		revisionsError,
	);
	const taskBelongsToRoute = !task || task.id === undefined || Number(task.id) === taskId;

	const handleExecute = () => {
		if (!task || !canExecuteActiveRevision) {
			toast.error(executeReason);
			return;
		}
		const operationTaskId = taskId;
		const operationOwner = Symbol("access-plan-execute");
		if (!acquireOwnedSingleFlight(operationLockRef, operationOwner)) return;
		const taskName = task.name || `任务 #${operationTaskId}`;
		const executingDifferentRevision = hasDraftRevision && task.revisionNumber !== activeRevisionNumber;
		const targetTables = executingDifferentRevision
			? "以当前有效配置的冻结映射为准"
			: targetTableSummary(tableMappings);
		const writeStrategy = executingDifferentRevision ? "以当前有效配置的冻结策略为准" : writeStrategyLabel(task);
		Modal.confirm({
			title: "确认立即执行",
			width: 620,
			okText: "确认并立即执行",
			cancelText: "取消",
			content: (
				<Space direction="vertical" size={12} className="w-full">
					<Alert
						type="warning"
						showIcon
						message="任务将按当前有效配置写入目标数据湖"
						description={
							executingDifferentRevision
								? "尚未完成文件预检的新配置不会参与本次执行。"
								: "请核对任务、目标表和写入策略；提交后可在运行历史查看进度。"
						}
					/>
					<Descriptions size="small" column={1}>
						<Descriptions.Item label="任务">{taskName}</Descriptions.Item>
						<Descriptions.Item label="任务编号">#{operationTaskId}</Descriptions.Item>
						<Descriptions.Item label="目标表">{targetTables}</Descriptions.Item>
						<Descriptions.Item label="写入策略">{writeStrategy}</Descriptions.Item>
						<Descriptions.Item label="目标类型">{task.destinationType || "未记录"}</Descriptions.Item>
					</Descriptions>
				</Space>
			),
			onOk: async () => {
				if (routeTaskIdRef.current !== operationTaskId || !ownsSingleFlight(operationLockRef, operationOwner)) {
					releaseOwnedSingleFlight(operationLockRef, operationOwner);
					return;
				}
				setOperation("execute");
				try {
					await runAccessPlanOperation("execute", operationTaskId, ingestionTaskAPI);
					if (routeTaskIdRef.current !== operationTaskId || !ownsSingleFlight(operationLockRef, operationOwner)) return;
					toast.success("任务已提交，后台正在触发执行");
					setActiveTab("history");
					await loadDetail();
				} catch {
					if (routeTaskIdRef.current === operationTaskId && ownsSingleFlight(operationLockRef, operationOwner)) {
						toast.error("任务提交失败，请稍后重试");
					}
				} finally {
					const released = releaseOwnedSingleFlight(operationLockRef, operationOwner);
					if (released && routeTaskIdRef.current === operationTaskId) setOperation(null);
				}
			},
			onCancel: () => releaseOwnedSingleFlight(operationLockRef, operationOwner),
		});
	};

	const handleDelete = () => {
		if (!task || taskDeleted) return;
		const operationTaskId = taskId;
		const operationOwner = Symbol("access-plan-delete");
		if (!acquireOwnedSingleFlight(operationLockRef, operationOwner)) return;
		Modal.confirm({
			title: "确认删除接入计划",
			width: 620,
			okText: "确认删除",
			okButtonProps: { danger: true },
			cancelText: "取消",
			content: (
				<Space direction="vertical" size={12} className="w-full">
					<Alert
						type="warning"
						showIcon
						message={`将删除计划“${task.name || `任务 #${operationTaskId}`}”并停止后续调度`}
						description="正在运行的计划不能删除；删除后不能再编辑或执行，已落地的 ODS 数据不会被清空，运行历史、变更记录和审计证据仍会保留。"
					/>
					<Descriptions size="small" column={1}>
						<Descriptions.Item label="任务编号">#{operationTaskId}</Descriptions.Item>
						<Descriptions.Item label="当前状态">{statusTag(task.status)}</Descriptions.Item>
					</Descriptions>
				</Space>
			),
			onOk: async () => {
				if (routeTaskIdRef.current !== operationTaskId || !ownsSingleFlight(operationLockRef, operationOwner)) {
					releaseOwnedSingleFlight(operationLockRef, operationOwner);
					return;
				}
				setOperation("delete");
				try {
					await runAccessPlanOperation("delete", operationTaskId, ingestionTaskAPI);
					if (routeTaskIdRef.current !== operationTaskId || !ownsSingleFlight(operationLockRef, operationOwner)) return;
					toast.success("接入计划已删除，历史证据已保留");
					router.push("/foundation/data-sources");
				} catch {
					if (routeTaskIdRef.current === operationTaskId && ownsSingleFlight(operationLockRef, operationOwner)) {
						toast.error("删除失败；如任务正在运行，请等待执行结束后重试");
					}
				} finally {
					const released = releaseOwnedSingleFlight(operationLockRef, operationOwner);
					if (released && routeTaskIdRef.current === operationTaskId) setOperation(null);
				}
			},
			onCancel: () => releaseOwnedSingleFlight(operationLockRef, operationOwner),
		});
	};

	if ((loading && !task) || !taskBelongsToRoute) {
		return (
			<div className={styles.centerState}>
				<Spin tip="正在加载接入任务" />
			</div>
		);
	}

	if (!task) {
		return (
			<div className={styles.page}>
				<PageHeader
					title="接入任务详情"
					actions={<Button onClick={() => router.push("/foundation/data-sources")}>返回接入概览</Button>}
				/>
				<Alert
					type="error"
					showIcon
					message="无法展示接入任务"
					description={loadError || "接入任务不存在或不可访问。"}
				/>
			</div>
		);
	}

	const effectiveClassification = task.classificationSeal?.effectiveLevel;

	const overview = (
		<div className={styles.tabStack}>
			{latestError ? (
				<Alert
					type="error"
					showIcon
					message="最新运行状态加载失败"
					description="本页不会沿用旧状态或推断任务健康度，请稍后刷新。"
				/>
			) : null}
			{revisionsError ? (
				<Alert
					type="error"
					showIcon
					message="有效配置状态加载失败"
					description="无法确认当前生效版本，执行入口已关闭；刷新成功前不会根据任务状态猜测执行能力。"
				/>
			) : null}
			<section className={styles.section}>
				<div className={styles.sectionHeading}>
					<div>
						<h2>接入配置摘要</h2>
						<p>仅展示可审计的业务字段，不展示连接凭据和底层配置原文。</p>
					</div>
				</div>
				<Descriptions size="small" column={{ xs: 1, sm: 2, xl: 3 }}>
					<Descriptions.Item label="任务编号">#{task.id || taskId}</Descriptions.Item>
					<Descriptions.Item label="接入方式">{task.sourceType || "未记录"}</Descriptions.Item>
					<Descriptions.Item label="同步模式">{writeStrategyLabel(task)}</Descriptions.Item>
					<Descriptions.Item label="源数据源">{task.sourceDataSourceId || "未关联"}</Descriptions.Item>
					<Descriptions.Item label="目标类型">{task.destinationType || "未记录"}</Descriptions.Item>
					<Descriptions.Item label="调度表达式">{task.syncSchedule || "手动触发"}</Descriptions.Item>
					<Descriptions.Item label="创建人">{task.createdBy || "未记录"}</Descriptions.Item>
					<Descriptions.Item label="最近修改">{formatTimestamp(task.lastModifiedDate) || "未记录"}</Descriptions.Item>
					<Descriptions.Item label="默认策略版本">
						{task.defaultPolicyVersion ? `v${task.defaultPolicyVersion}` : "未记录"}
					</Descriptions.Item>
					<Descriptions.Item label="有效配置校验">
						{task.effectiveConfigChecksum ? task.effectiveConfigChecksum.slice(0, 12) : "未记录"}
					</Descriptions.Item>
				</Descriptions>
			</section>

			<section className={styles.section}>
				<div className={styles.sectionHeading}>
					<div>
						<h2>最近运行</h2>
						<p>用于快速判断任务是否完成；完整诊断请进入“运行历史”。</p>
					</div>
				</div>
				{latestExecution ? (
					<div className={styles.runStrip}>
						<div>
							<span>状态</span>
							{executionStatusTag(latestExecution.status)}
						</div>
						<div>
							<span>执行编号</span>
							<strong>{latestExecution.executionId || latestExecution.id}</strong>
						</div>
						<div>
							<span>开始时间</span>
							<strong>{formatTimestamp(latestExecution.startTime) || "-"}</strong>
						</div>
						<div>
							<span>写入行数</span>
							<strong>{formatNumber(latestExecution.rowsWritten) || "-"}</strong>
						</div>
					</div>
				) : (
					<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚无运行记录" />
				)}
			</section>

			<section className={styles.section}>
				<div className={styles.sectionHeading}>
					<div>
						<h2>同步范围</h2>
						<p>展示任务已保存的源到目标映射，不读取或回显连接参数。</p>
					</div>
				</div>
				<CompactTable<TableMappingRow>
					rowKey="key"
					columns={mappingColumns}
					dataSource={tableMappings}
					pagination={tableMappings.length > 10 ? { pageSize: 10 } : false}
					locale={{ emptyText: "任务未记录表级映射" }}
				/>
			</section>
		</div>
	);

	const changeLog = (
		<div className={styles.tabStack}>
			{changesError ? (
				<Alert
					type="error"
					showIcon
					message="变更记录加载失败"
					description="为避免误判，失败时不展示缓存记录；请刷新后重试。"
				/>
			) : null}
			<section className={styles.section}>
				<div className={styles.sectionHeading}>
					<div>
						<h2>接入变更记录</h2>
						<p>仅查询当前任务的登记记录，不将变更状态解释为客户审批结论。</p>
					</div>
					<Button disabled={changesLoading} onClick={() => void loadChanges(1, changesPagination.pageSize)}>
						刷新
					</Button>
				</div>
				<CompactTable<IngestionChangeLogDTO>
					rowKey={(row) => row.id || `${row.createdDate || "change"}-${row.changeType}`}
					columns={changeColumns}
					dataSource={changes}
					loading={changesLoading}
					pagination={{
						current: changesPagination.current,
						pageSize: changesPagination.pageSize,
						total: changesPagination.total,
					}}
					onChange={onChangesTableChange}
					locale={{ emptyText: changesLoaded ? "暂无变更记录" : "正在读取变更记录" }}
				/>
			</section>
		</div>
	);

	return (
		<div className={styles.page}>
			<PageHeader
				title={task.name || "接入任务详情"}
				actions={
					<Space wrap>
						<Button onClick={() => router.push("/foundation/data-sources")}>返回接入概览</Button>
						<Button
							disabled={taskDeleted || operation !== null}
							onClick={() =>
								router.push(
									`/foundation/data-sources/access/new?kind=${inferAccessKind(task)}&editId=${task.id || taskId}`,
								)
							}
						>
							编辑计划
						</Button>
						<Tooltip title={executeReason}>
							<Button
								type="primary"
								loading={operation === "execute"}
								disabled={taskDeleted || !canExecuteActiveRevision || operation !== null}
								onClick={() => void handleExecute()}
								data-testid="platform-access-execute"
							>
								立即执行
							</Button>
						</Tooltip>
						<Button
							danger
							loading={operation === "delete"}
							disabled={taskDeleted || operation !== null}
							onClick={handleDelete}
							data-testid="platform-access-delete"
						>
							删除计划
						</Button>
						<Button onClick={() => void loadDetail()} disabled={loading || operation !== null}>
							刷新
						</Button>
					</Space>
				}
			/>

			<section className={styles.identityBar}>
				<div className={styles.identityMain}>
					<span className={styles.eyebrow}>接入任务 #{task.id || taskId}</span>
					<p>{task.description || "统一管理接入配置、运行结果、密级信息与变更留痕。"}</p>
				</div>
				<div className={styles.identityTags}>
					{statusTag(task.status)}
					{effectiveClassification ? <ClassificationTag value={effectiveClassification} /> : null}
				</div>
			</section>

			<Tabs
				className={styles.tabs}
				activeKey={activeTab}
				onChange={setActiveTab}
				items={[
					{ key: "overview", label: "概览", children: overview },
					{ key: "history", label: "运行历史", children: <ExecutionHistoryTable taskId={taskId} /> },
					{
						key: "quality",
						label: inferAccessKind(task) === "file" ? "文件预检" : "异常数据",
						children: (
							<AccessQualityPanel task={task} latestExecution={latestExecution} onTaskChanged={() => loadDetail()} />
						),
					},
					{ key: "changes", label: "变更记录", children: changeLog },
				]}
			/>
		</div>
	);
}
