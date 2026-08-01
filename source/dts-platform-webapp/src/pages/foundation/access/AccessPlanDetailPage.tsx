import { ArrowLeftOutlined, EditOutlined, ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Descriptions, Dropdown, Empty, Modal, Space, Spin, Table, Tabs, Tag, Tooltip } from "antd";
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
import RollbackImpactModal, { type RollbackRequest } from "@/components/rollback/RollbackImpactModal";
import { useParams, useRouter } from "@/routes/hooks";
import { formatNumber, formatTimestamp } from "@/utils/format";
import ExecutionHistoryTable from "./shared/ExecutionHistoryTable";
import TaskAdmissionBasis from "./shared/TaskAdmissionBasis";
import { resolveTaskAdmissionState } from "./shared/fileClassificationAdmission.helpers";
import { AccessQualityPanel, AccessStructureDriftPanel } from "./AccessGovernancePanels";
import styles from "./AccessPlanDetailPage.module.css";
import { type AccessPlanOperation, runAccessPlanOperation } from "./accessPlanOperations";
import { inferAccessKind } from "./accessPlanPayload";
import { resolveAccessRevisionView } from "./accessRevisionView";
import { acquireSingleFlight, releaseSingleFlight } from "./accessSingleFlight";

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
	value && ["overview", "history", "drift", "quality", "admission", "changes"].includes(value) ? value : "overview";

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
	const [rollbackOpen, setRollbackOpen] = useState(false);
	const [rollbackRequest, setRollbackRequest] = useState<RollbackRequest | null>(null);
	const detailRequestIdRef = useRef(0);
	const changesRequestIdRef = useRef(0);
	const routeTaskIdRef = useRef(taskId);
	const operationLockRef = useRef(false);
	routeTaskIdRef.current = taskId;

	const loadDetail = useCallback(async () => {
		const requestId = ++detailRequestIdRef.current;
		changesRequestIdRef.current += 1;
		setRollbackOpen(false);
		setRollbackRequest(null);
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
		releaseSingleFlight(operationLockRef);
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
		() =>
			(task?.tableMapping || []).map((mapping, index) => ({
				key: `${mapping.source || "source"}-${mapping.target || "target"}-${index}`,
				source: mapping.source || "未记录",
				target: mapping.target || "未记录",
			})),
		[task?.tableMapping],
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
	const { activeRevisionNumber, draftRevisionNumber, hasDraftRevision, canExecuteActiveRevision, executeReason } =
		resolveAccessRevisionView(task, revisions, revisionsError);
	const admissionTask = task && hasDraftRevision ? { ...task, status: "draft" } : task;
	const admissionState = resolveTaskAdmissionState(admissionTask);
	const showAdmissionAction = hasDraftRevision;
	const taskBelongsToRoute = !task || task.id === undefined || Number(task.id) === taskId;

	const handleAdmit = async () => {
		if (!task || !admissionState.canAdmit) {
			toast.error(admissionState.reason);
			return;
		}
		if (!acquireSingleFlight(operationLockRef)) return;
		const operationTaskId = taskId;
		setOperation("admit");
		try {
			await runAccessPlanOperation("admit", operationTaskId);
			if (routeTaskIdRef.current !== operationTaskId) return;
			toast.success("密级与准入已完成，任务现在可以执行");
			setActiveTab("admission");
			await loadDetail();
		} catch {
			if (routeTaskIdRef.current === operationTaskId) {
				toast.error("密级与准入失败，请检查封存依据后重试");
			}
		} finally {
			releaseSingleFlight(operationLockRef);
			if (routeTaskIdRef.current === operationTaskId) setOperation(null);
		}
	};

	const handleExecute = () => {
		if (!task || !canExecuteActiveRevision) {
			toast.error(executeReason);
			return;
		}
		if (!acquireSingleFlight(operationLockRef)) return;
		const operationTaskId = taskId;
		const taskName = task.name || `任务 #${operationTaskId}`;
		const executingDifferentRevision = hasDraftRevision && task.revisionNumber !== activeRevisionNumber;
		const targetTables = executingDifferentRevision
			? `以生效版本 R${activeRevisionNumber} 的冻结映射为准`
			: targetTableSummary(tableMappings);
		const writeStrategy = executingDifferentRevision ? "以生效版本冻结策略为准" : writeStrategyLabel(task);
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
						message={`任务将按当前生效版本 R${activeRevisionNumber} 写入目标数据湖`}
						description={
							executingDifferentRevision
								? `待准入草稿 R${task.revisionNumber} 不会参与本次执行。`
								: "请核对任务、目标表和写入策略；提交后可在运行历史查看进度。"
						}
					/>
					<Descriptions bordered size="small" column={1}>
						<Descriptions.Item label="任务">{taskName}</Descriptions.Item>
						<Descriptions.Item label="任务编号">#{operationTaskId}</Descriptions.Item>
						<Descriptions.Item label="执行生效版本">R{activeRevisionNumber}</Descriptions.Item>
						<Descriptions.Item label="目标表">{targetTables}</Descriptions.Item>
						<Descriptions.Item label="写入策略">{writeStrategy}</Descriptions.Item>
						<Descriptions.Item label="目标类型">{task.destinationType || "未记录"}</Descriptions.Item>
					</Descriptions>
				</Space>
			),
			onOk: async () => {
				if (routeTaskIdRef.current !== operationTaskId) {
					releaseSingleFlight(operationLockRef);
					return;
				}
				setOperation("execute");
				try {
					await runAccessPlanOperation("execute", operationTaskId);
					if (routeTaskIdRef.current !== operationTaskId) return;
					toast.success("任务已提交，后台正在触发执行");
					setActiveTab("history");
					await loadDetail();
				} catch {
					if (routeTaskIdRef.current === operationTaskId) {
						toast.error("任务提交失败，请稍后重试");
					}
				} finally {
					releaseSingleFlight(operationLockRef);
					if (routeTaskIdRef.current === operationTaskId) setOperation(null);
				}
			},
			onCancel: () => releaseSingleFlight(operationLockRef),
		});
	};

	const handleRebuildDag = () => {
		if (!task || !canExecuteActiveRevision || task.airflowEnabled === false) return;
		if (!acquireSingleFlight(operationLockRef)) return;
		const operationTaskId = taskId;
		Modal.confirm({
			title: "重建 DAG",
			content: `确定要重建任务“${task.name}”的 DAG 文件吗？`,
			okText: "确认重建",
			cancelText: "取消",
			onOk: async () => {
				if (routeTaskIdRef.current !== operationTaskId) {
					releaseSingleFlight(operationLockRef);
					return;
				}
				setOperation("rebuildDag");
				try {
					await runAccessPlanOperation("rebuildDag", operationTaskId);
					if (routeTaskIdRef.current !== operationTaskId) return;
					toast.success("DAG 已重建");
					await loadDetail();
				} catch {
					if (routeTaskIdRef.current === operationTaskId) {
						toast.error("DAG 重建失败，请稍后重试");
						throw new Error("DAG rebuild failed");
					}
				} finally {
					releaseSingleFlight(operationLockRef);
					if (routeTaskIdRef.current === operationTaskId) setOperation(null);
				}
			},
			onCancel: () => releaseSingleFlight(operationLockRef),
		});
	};

	const openRollback = (level: number) => {
		if (!task || taskDeleted) return;
		setRollbackRequest({ level, scope: "task", taskId });
		setRollbackOpen(true);
	};

	const closeRollback = () => {
		setRollbackOpen(false);
		setRollbackRequest(null);
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
					actions={
						<Button icon={<ArrowLeftOutlined />} onClick={() => router.push("/foundation/data-sources")}>
							返回接入概览
						</Button>
					}
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
	const sealVersion = task.classificationSeal?.snapshotVersion;
	const revisionNumber = Number(task.revisionNumber);
	const versioned = Number.isInteger(revisionNumber) && revisionNumber > 0;
	const revisionLabel = versioned
		? `R${revisionNumber}${task.revisionState === "DRAFT" ? "（待准入草稿）" : ""}`
		: "未版本化（存量任务）";
	const activeRevisionLabel = activeRevisionNumber ? `R${activeRevisionNumber}` : "无生效 Revision";

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
					message="Revision 状态加载失败"
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
				<Descriptions bordered size="small" column={{ xs: 1, sm: 2, xl: 3 }}>
					<Descriptions.Item label="任务编号">#{task.id || taskId}</Descriptions.Item>
					<Descriptions.Item label="接入方式">{task.sourceType || "未记录"}</Descriptions.Item>
					<Descriptions.Item label="同步模式">{syncModeLabel(task.syncMode)}</Descriptions.Item>
					<Descriptions.Item label="当前编辑 Revision">
						<Tag color={versioned ? "blue" : "warning"}>{revisionLabel}</Tag>
					</Descriptions.Item>
					<Descriptions.Item label="当前生效 Revision">
						<Tag color={activeRevisionNumber ? "success" : "warning"}>{activeRevisionLabel}</Tag>
					</Descriptions.Item>
					<Descriptions.Item label="编辑版本状态">
						{versioned ? task.revisionState || "未记录" : "不适用"}
					</Descriptions.Item>
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
						<div>
							<span>执行版本</span>
							<strong>{latestExecution.revisionNumber ? `R${latestExecution.revisionNumber}` : "未记录"}</strong>
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
				<Table<TableMappingRow>
					rowKey="key"
					size="small"
					columns={mappingColumns}
					dataSource={tableMappings}
					pagination={tableMappings.length > 10 ? { pageSize: 10 } : false}
					locale={{ emptyText: "任务未记录表级映射" }}
				/>
			</section>
		</div>
	);

	const admission = (
		<div className={styles.tabStack}>
			<Alert
				type="info"
				showIcon
				message="密级封存与任务版本是两个独立概念"
				description={`密级封存版本 ${sealVersion === undefined ? "未记录" : `v${sealVersion}`} 只表示密级依据快照；接入任务版本为 ${revisionLabel}，两者分别审计。`}
			/>
			<section className={styles.section}>
				<TaskAdmissionBasis task={admissionTask || task} />
			</section>
			<section className={styles.section}>
				<div className={styles.sectionHeading}>
					<div>
						<h2>密级封存摘要</h2>
						<p>校验结果由现有密级准入规则计算；校验值与底层封存标识不在界面展示。</p>
					</div>
				</div>
				<Descriptions bordered size="small" column={{ xs: 1, sm: 2, xl: 3 }}>
					<Descriptions.Item label="有效密级">
						{effectiveClassification ? (
							<ClassificationTag value={effectiveClassification} />
						) : (
							<Tag color="orange">密级未记录</Tag>
						)}
					</Descriptions.Item>
					<Descriptions.Item label="密级封存版本">
						{sealVersion === undefined ? "未记录" : `v${sealVersion}`}
					</Descriptions.Item>
					<Descriptions.Item label="封存时间">
						{formatTimestamp(task.classificationSeal?.sealedAt) || "未记录"}
					</Descriptions.Item>
				</Descriptions>
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
					<Button
						icon={<ReloadOutlined />}
						disabled={changesLoading}
						onClick={() => void loadChanges(1, changesPagination.pageSize)}
					>
						刷新
					</Button>
				</div>
				<Table<IngestionChangeLogDTO>
					rowKey={(row) => row.id || `${row.createdDate || "change"}-${row.changeType}`}
					columns={changeColumns}
					dataSource={changes}
					loading={changesLoading}
					scroll={{ x: 920 }}
					pagination={{
						current: changesPagination.current,
						pageSize: changesPagination.pageSize,
						total: changesPagination.total,
						showSizeChanger: true,
						pageSizeOptions: [10, 20, 50],
						showTotal: (total) => `共 ${total} 条`,
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
						<Button icon={<ArrowLeftOutlined />} onClick={() => router.push("/foundation/data-sources")}>
							返回接入概览
						</Button>
						<Button
							icon={<EditOutlined />}
							disabled={taskDeleted || operation !== null}
							onClick={() =>
								router.push(
									`/foundation/data-sources/access/new?kind=${inferAccessKind(task)}&editId=${task.id || taskId}`,
								)
							}
						>
							编辑计划
						</Button>
						{showAdmissionAction ? (
							<Tooltip title={admissionState.canAdmit ? undefined : admissionState.reason}>
								<Button
									type="primary"
									loading={operation === "admit"}
									disabled={!admissionState.canAdmit || operation !== null}
									onClick={() => void handleAdmit()}
									data-testid="platform-access-admit"
								>
									准入草稿
									{draftRevisionNumber || task.revisionNumber ? ` R${draftRevisionNumber || task.revisionNumber}` : ""}
								</Button>
							</Tooltip>
						) : null}
						<Tooltip title={executeReason}>
							<Button
								type={showAdmissionAction ? "default" : "primary"}
								loading={operation === "execute"}
								disabled={taskDeleted || !canExecuteActiveRevision || operation !== null}
								onClick={() => void handleExecute()}
								data-testid="platform-access-execute"
							>
								立即执行
							</Button>
						</Tooltip>
						<Dropdown
							disabled={operation !== null}
							menu={{
								items: [
									{
										key: "rebuild",
										label: "重建 DAG",
										disabled: taskDeleted || !canExecuteActiveRevision || task.airflowEnabled === false,
									},
									{ type: "divider" },
									{ key: "rollback-1", label: "数据回退 Level 1 — 清空数据", disabled: taskDeleted },
									{ key: "rollback-2", label: "数据回退 Level 2 — 重建表结构", disabled: taskDeleted },
									{ key: "rollback-3", label: "数据回退 Level 3 — 全链路回退", danger: true, disabled: taskDeleted },
								],
								onClick: ({ key }) => {
									if (key === "rebuild") handleRebuildDag();
									if (key.startsWith("rollback-")) openRollback(Number(key.slice("rollback-".length)));
								},
							}}
						>
							<Button loading={operation === "rebuildDag"}>更多操作</Button>
						</Dropdown>
						<Button
							icon={<ReloadOutlined />}
							onClick={() => void loadDetail()}
							disabled={loading || operation !== null}
						>
							刷新
						</Button>
					</Space>
				}
			/>

			<section className={styles.identityBar}>
				<div className={styles.identityMain}>
					<span className={styles.eyebrow}>接入任务 #{task.id || taskId}</span>
					<p>{task.description || "统一管理接入配置、运行结果、密级准入与变更留痕。"}</p>
				</div>
				<div className={styles.identityTags}>
					{statusTag(task.status)}
					<Tag color={activeRevisionNumber ? "success" : "warning"}>生效 {activeRevisionLabel}</Tag>
					{hasDraftRevision ? <Tag color="gold">待准入 {revisionLabel}</Tag> : null}
					{hasDraftRevision && effectiveClassification ? <Tag color="gold">草稿密级</Tag> : null}
					{effectiveClassification ? (
						<ClassificationTag value={effectiveClassification} />
					) : (
						<Tag color="orange">密级未记录</Tag>
					)}
				</div>
			</section>

			<Tabs
				className={styles.tabs}
				activeKey={activeTab}
				onChange={setActiveTab}
				items={[
					{ key: "overview", label: "概览", children: overview },
					{ key: "history", label: "运行历史", children: <ExecutionHistoryTable taskId={taskId} /> },
					{ key: "drift", label: "结构漂移", children: <AccessStructureDriftPanel task={task} /> },
					{
						key: "quality",
						label: inferAccessKind(task) === "file" ? "文件预检" : "异常数据",
						children: (
							<AccessQualityPanel
								task={task}
								latestExecution={latestExecution}
								onTaskChanged={() => loadDetail()}
							/>
						),
					},
					{ key: "admission", label: "密级准入", children: admission },
					{ key: "changes", label: "变更记录", children: changeLog },
				]}
			/>

			{rollbackRequest?.taskId === taskId ? (
				<RollbackImpactModal
					key={`access-plan-rollback-${taskId}`}
					open={rollbackOpen}
					request={rollbackRequest}
					onClose={closeRollback}
					onSuccess={() => void loadDetail()}
				/>
			) : null}
		</div>
	);
}
