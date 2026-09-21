import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Button,
	Card,
	Col,
	Descriptions,
	Form,
	Input,
	Modal,
	Progress,
	Row,
	Select,
	Space,
	Spin,
	Switch,
	Tag,
	Typography,
} from "antd";
import { actionColumn, CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { useCatalogManageAccess } from "@/hooks/useModuleManageAccess";
import { useRouter } from "@/routes/hooks";
import {
	type CatalogSyncConfig,
	getCatalogSyncConfig,
	getCatalogSyncRunDiagnostics,
	getCatalogSyncStatus,
	getTechMetadataTableDetail,
	getTechMetadataTables,
	type SchemaDriftEvent,
	listCatalogSyncPipelines,
	listCatalogSyncRuns,
	listSchemaDriftEvents,
	triggerCatalogSync,
	triggerJdbcCatalogSync,
	updateSchemaDriftPolicy,
	updateSchemaDriftTicket,
	updateCatalogSyncConfig,
} from "@/api/platformApi";

const { Text } = Typography;

type SyncPipeline = {
	id?: string;
	sourceId?: string;
	integration?: string;
	name?: string;
	source?: string;
	schedule?: string;
	lastRun?: string;
	status?: string;
	tablesFound?: number;
	autoEnabled?: boolean;
	logLines?: string[];
	error?: string;
};

type SyncRun = {
	id?: string;
	integration?: string;
	status?: string;
	startedAt?: string;
	finishedAt?: string;
	tablesDiscovered?: number;
	tablesCreated?: number;
	columnsImported?: number;
	datasetsCreated?: number;
	datasetsUpdated?: number;
	datasetsRemoved?: number;
	datasetsMarkedStale?: number;
	datasetsPurged?: number;
	errorCategory?: string;
	error?: string;
};

type SyncStatusPayload = {
	primary?: { inProgress?: boolean };
	jdbc?: { inProgress?: boolean };
};

type TableSummary = {
	fqn?: string;
	name?: string;
	service?: string;
	database?: string;
	schema?: string;
	owner?: string;
	domain?: string;
	tags?: string;
	description?: string;
	columnCount?: number;
	metadataSource?: string;
	fallbackReason?: string;
};

type TableDetail = {
	enabled?: boolean;
	found?: boolean;
	fqn?: string;
	message?: string;
	metadataSource?: string;
	fallbackReason?: string;
	entity?: Record<string, any>;
};

type TablePageMeta = {
	metadataSource?: string;
	fallbackReason?: string;
	message?: string;
};

type ColumnRow = {
	key: string;
	name: string;
	type: string;
	comment: string;
	status?: string;
};

const statusTag = (status?: string) => {
	if (!status) return <Tag>未知</Tag>;
	const normalized = status.toUpperCase();
	if (["SUCCESS", "SUCCEEDED"].includes(normalized)) return <Tag color="green">成功</Tag>;
	if (normalized === "FAILED") return <Tag color="red">失败</Tag>;
	if (normalized === "RUNNING") return <Tag color="blue">运行中</Tag>;
	return <Tag>{status}</Tag>;
};

const errorCategoryTag = (value?: string) => {
	const normalized = String(value || "").toUpperCase();
	if (!normalized || normalized === "NONE") return <Tag>无</Tag>;
	if (normalized === "AUTH") return <Tag color="red">权限</Tag>;
	if (normalized === "TIMEOUT") return <Tag color="orange">超时</Tag>;
	if (normalized === "NETWORK") return <Tag color="gold">网络</Tag>;
	if (normalized === "SQL") return <Tag color="magenta">SQL</Tag>;
	if (normalized === "DRIVER") return <Tag color="purple">驱动</Tag>;
	return <Tag>{normalized}</Tag>;
};

const driftPolicyTag = (value?: string) => {
	const normalized = String(value || "").toUpperCase();
	if (normalized === "AUTO_APPLY") return <Tag color="green">自动迁移</Tag>;
	if (normalized === "BLOCK") return <Tag color="red">阻断</Tag>;
	if (normalized === "REVIEW") return <Tag color="gold">待审批</Tag>;
	return <Tag>{value || "待审批"}</Tag>;
};

const driftTicketTag = (value?: string) => {
	const normalized = String(value || "").toUpperCase();
	if (normalized === "OPEN") return <Tag color="blue">待处理</Tag>;
	if (normalized === "IN_REVIEW") return <Tag color="gold">处理中</Tag>;
	if (normalized === "RESOLVED") return <Tag color="green">已处理</Tag>;
	if (normalized === "IGNORED") return <Tag>已忽略</Tag>;
	if (normalized === "REJECTED") return <Tag color="red">已驳回</Tag>;
	return <Tag>{value || "待处理"}</Tag>;
};

const metadataSourceLabel = (value?: string) => {
	const normalized = String(value || "").toLowerCase();
	if (normalized === "catalog") return "本地 Catalog";
	if (normalized === "openmetadata") return "OpenMetadata";
	if (normalized === "disabled") return "未启用";
	return "未知来源";
};

const metadataSourceTag = (value?: string) => {
	const normalized = String(value || "").toLowerCase();
	if (normalized === "catalog") return <Tag color="blue">{metadataSourceLabel(value)}</Tag>;
	if (normalized === "openmetadata") return <Tag color="green">{metadataSourceLabel(value)}</Tag>;
	if (normalized === "disabled") return <Tag color="orange">{metadataSourceLabel(value)}</Tag>;
	return <Tag>{metadataSourceLabel(value)}</Tag>;
};

const buildColumnRows = (detail?: TableDetail | null): ColumnRow[] => {
	if (!detail?.entity) return [];
	const columns = Array.isArray(detail.entity.columns) ? detail.entity.columns : [];
	return columns.map((item: any, idx: number) => ({
		key: String(item?.name || item?.displayName || idx),
		name: String(item?.name || item?.displayName || "-").trim(),
		type: String(item?.dataType || item?.dataTypeDisplay || "-").trim(),
		comment: String(item?.description || item?.comment || "").trim(),
		status: String(item?.status || "").trim(),
	}));
};

const validateCronExpression = (value?: string) => {
	const text = String(value || "").trim();
	if (!text) {
		return false;
	}
	const parts = text.split(/\s+/).filter(Boolean);
	return parts.length >= 5 && parts.length <= 7;
};

export default function MetadataPage() {
	const router = useRouter();
	const [form] = Form.useForm();
	const [pipelines, setPipelines] = useState<SyncPipeline[]>([]);
	const [selectedPipelineId, setSelectedPipelineId] = useState<string | undefined>();
	const [runs, setRuns] = useState<SyncRun[]>([]);
	const [tables, setTables] = useState<TableSummary[]>([]);
	const [selectedFqn, setSelectedFqn] = useState<string | undefined>();
	const [tableDetail, setTableDetail] = useState<TableDetail | null>(null);
	const [tablePageMeta, setTablePageMeta] = useState<TablePageMeta | null>(null);
	const [loadingPipelines, setLoadingPipelines] = useState(false);
	const [loadingRuns, setLoadingRuns] = useState(false);
	const [loadingTables, setLoadingTables] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [syncConfig, setSyncConfig] = useState<CatalogSyncConfig | null>(null);
	const [syncConfigUpdating, setSyncConfigUpdating] = useState(false);
	const [cronDraft, setCronDraft] = useState("");
	const [driftEvents, setDriftEvents] = useState<SchemaDriftEvent[]>([]);
	const [loadingDrift, setLoadingDrift] = useState(false);
	const [driftPolicyFilter, setDriftPolicyFilter] = useState("ALL");
	const [driftTicketFilter, setDriftTicketFilter] = useState("ALL");
	const [driftActionOpen, setDriftActionOpen] = useState(false);
	const [driftActionType, setDriftActionType] = useState<"policy" | "ticket" | null>(null);
	const [driftActionTarget, setDriftActionTarget] = useState<SchemaDriftEvent | null>(null);
	const [driftActionSubmitting, setDriftActionSubmitting] = useState(false);
	const [syncStatus, setSyncStatus] = useState<SyncStatusPayload | null>(null);
	const [diagOpen, setDiagOpen] = useState(false);
	const [diagLoading, setDiagLoading] = useState(false);
	const [diagData, setDiagData] = useState<any>(null);
	const [driftForm] = Form.useForm();
	const canManage = useCatalogManageAccess();

	const selectedPipeline = useMemo(() => {
		if (!pipelines.length) return null;
		return pipelines.find((item) => String(item.id) === String(selectedPipelineId)) || pipelines[0];
	}, [pipelines, selectedPipelineId]);

	useEffect(() => {
		void loadPipelines();
		void loadSyncConfig();
		void loadSyncStatus();
	}, []);

	useEffect(() => {
		const timer = window.setInterval(() => {
			void loadSyncStatus();
		}, 5000);
		return () => window.clearInterval(timer);
	}, []);

	useEffect(() => {
		void loadDriftEvents();
	}, [driftPolicyFilter, driftTicketFilter]);

	useEffect(() => {
		if (!selectedPipeline && pipelines.length) {
			setSelectedPipelineId(pipelines[0]?.id);
		}
	}, [pipelines, selectedPipeline]);

	useEffect(() => {
		if (!selectedPipeline?.integration) {
			setRuns([]);
			return;
		}
		void loadRuns(selectedPipeline.integration);
	}, [selectedPipeline?.integration, selectedPipeline?.sourceId]);

	useEffect(() => {
		if (!selectedPipeline) {
			setTables([]);
			setTablePageMeta(null);
			setSelectedFqn(undefined);
			return;
		}
		void loadTables(keyword);
		// Only reload table candidates when selected source changes.
		// Keyword search remains manual via the "搜索" button.
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [selectedPipeline?.id, selectedPipeline?.sourceId, selectedPipeline?.integration]);

	useEffect(() => {
		if (!selectedFqn) {
			setTableDetail(null);
			return;
		}
		void loadTableDetail(selectedFqn);
	}, [selectedFqn]);

	const loadPipelines = async () => {
		setLoadingPipelines(true);
		try {
			const resp: any = await listCatalogSyncPipelines();
			const list = Array.isArray(resp) ? resp : [];
			setPipelines(list as SyncPipeline[]);
			if (!list.length) {
				setSelectedPipelineId(undefined);
			} else {
				const exists = selectedPipelineId
					? list.some((item: SyncPipeline) => String(item.id) === String(selectedPipelineId))
					: false;
				if (!exists) {
					setSelectedPipelineId(list[0]?.id);
				}
			}
		} catch {
			// error toast handled by global interceptor
		} finally {
			setLoadingPipelines(false);
		}
	};

	const loadSyncConfig = async () => {
		try {
			const resp: any = await getCatalogSyncConfig();
			setSyncConfig(resp || null);
			setCronDraft(String(resp?.autoSyncCron || ""));
		} catch {
			// error toast handled by global interceptor
			setSyncConfig(null);
			setCronDraft("");
		}
	};

	const loadSyncStatus = async () => {
		try {
			const resp: any = await getCatalogSyncStatus();
			setSyncStatus(resp || null);
		} catch {
			setSyncStatus(null);
		}
	};

	const loadDriftEvents = async () => {
		setLoadingDrift(true);
		try {
			const resp: any = await listSchemaDriftEvents({
				policyMode: driftPolicyFilter === "ALL" ? undefined : driftPolicyFilter,
				ticketStatus: driftTicketFilter === "ALL" ? undefined : driftTicketFilter,
				limit: 50,
				includeDetails: false,
			});
			setDriftEvents(Array.isArray(resp) ? (resp as SchemaDriftEvent[]) : []);
		} catch {
			// error toast handled by global interceptor
			setDriftEvents([]);
		} finally {
			setLoadingDrift(false);
		}
	};

	const loadRuns = async (integration: string) => {
		setLoadingRuns(true);
		try {
			const resp: any = await listCatalogSyncRuns({
				integration,
				limit: 20,
				includeDetails: false,
				sourceId: integration === "JDBC" ? selectedPipeline?.sourceId : undefined,
			});
			setRuns(Array.isArray(resp) ? (resp as SyncRun[]) : []);
		} catch {
			// error toast handled by global interceptor
		} finally {
			setLoadingRuns(false);
		}
	};

	const openRunDiagnostics = async (run: SyncRun) => {
		if (!run?.id) return;
		setDiagOpen(true);
		setDiagLoading(true);
		setDiagData(null);
		try {
			const sourceId = run.integration === "JDBC" ? selectedPipeline?.sourceId || selectedPipeline?.id : undefined;
			const resp: any = await getCatalogSyncRunDiagnostics(run.id, {
				sourceId: sourceId || undefined,
			});
			setDiagData(resp || null);
		} catch {
			// error toast handled by global interceptor
			setDiagData(null);
		} finally {
			setDiagLoading(false);
		}
	};

	const loadTables = async (nextKeyword: string) => {
		setLoadingTables(true);
		try {
			const sourceId =
				selectedPipeline?.integration === "JDBC" ? selectedPipeline?.sourceId || selectedPipeline?.id : undefined;
			const resp: any = await getTechMetadataTables({
				keyword: nextKeyword || undefined,
				size: 50,
				sourceId: sourceId || undefined,
			});
			const items = Array.isArray(resp?.items) ? resp.items : [];
			setTables(items as TableSummary[]);
			setTablePageMeta({
				metadataSource: resp?.metadataSource,
				fallbackReason: resp?.fallbackReason,
				message: resp?.message,
			});
			setSelectedFqn((prev) => {
				if (!items.length) return undefined;
				if (prev && items.some((item: TableSummary) => item.fqn === prev)) return prev;
				return items[0]?.fqn;
			});
		} catch {
			// error toast handled by global interceptor
			setTables([]);
			setTablePageMeta(null);
			setSelectedFqn(undefined);
		} finally {
			setLoadingTables(false);
		}
	};

	const loadTableDetail = async (fqn: string) => {
		if (!fqn) return;
		try {
			const resp: any = await getTechMetadataTableDetail(fqn);
			setTableDetail(resp || null);
		} catch {
			// error toast handled by global interceptor
			setTableDetail(null);
		}
	};

	const handleTrigger = async () => {
		if (!canManage) {
			toast.error("当前账号无资产维护权限");
			return;
		}
		if (!selectedPipeline) {
			toast.error("请先选择采集任务");
			return;
		}
		const reason = String(form.getFieldValue("reason") || "manual").trim();
		try {
			if (selectedPipeline.integration === "JDBC") {
				const sourceId = selectedPipeline.sourceId || selectedPipeline.id;
				if (!sourceId) {
					toast.error("缺少数据源标识");
					return;
				}
				await triggerJdbcCatalogSync(sourceId, { reason });
			} else {
				await triggerCatalogSync({ includePrimary: true, includeJdbc: false, reason });
			}
			toast.success("已触发采集任务");
			await loadPipelines();
			await loadSyncStatus();
			void loadTables(keyword);
			if (selectedPipeline.integration) {
				void loadRuns(selectedPipeline.integration);
			}
		} catch {
			// error toast handled by global interceptor
		}
	};

	const handleAutoSyncToggle = async (checked: boolean) => {
		if (!canManage) {
			toast.error("当前账号无资产维护权限");
			return;
		}
		setSyncConfigUpdating(true);
		try {
			const resp: any = await updateCatalogSyncConfig({ autoSyncEnabled: checked });
			setSyncConfig(resp || null);
			toast.success(checked ? "已开启自动采集" : "已关闭自动采集");
			await loadPipelines();
		} catch {
			// error toast handled by global interceptor
		} finally {
			setSyncConfigUpdating(false);
		}
	};

	const handleSyncCronSave = async () => {
		if (!canManage) {
			toast.error("当前账号无资产维护权限");
			return;
		}
		const cron = cronDraft.trim();
		if (!validateCronExpression(cron)) {
			toast.error("Cron 表达式格式不正确（需 5-7 段）");
			return;
		}
		setSyncConfigUpdating(true);
		try {
			const resp: any = await updateCatalogSyncConfig({ autoSyncCron: cron });
			setSyncConfig(resp || null);
			setCronDraft(String(resp?.autoSyncCron || cron));
			toast.success("Cron 配置已更新");
		} catch {
			// error toast handled by global interceptor
		} finally {
			setSyncConfigUpdating(false);
		}
	};

	const openDriftPolicyModal = (event: SchemaDriftEvent) => {
		setDriftActionTarget(event);
		setDriftActionType("policy");
		driftForm.setFieldsValue({
			policyMode: event.policyMode || "REVIEW",
			ticketStatus: event.ticketStatus || "OPEN",
			assignee: event.ticketAssignee || "",
			note: event.workflowNote || "",
		});
		setDriftActionOpen(true);
	};

	const openDriftTicketModal = (event: SchemaDriftEvent) => {
		setDriftActionTarget(event);
		setDriftActionType("ticket");
		driftForm.setFieldsValue({
			policyMode: event.policyMode || "REVIEW",
			ticketStatus: event.ticketStatus || "OPEN",
			assignee: event.ticketAssignee || "",
			note: event.workflowNote || "",
		});
		setDriftActionOpen(true);
	};

	const submitDriftAction = async () => {
		if (!canManage) {
			toast.error("当前账号无资产维护权限");
			return;
		}
		if (!driftActionTarget?.id || !driftActionType) return;
		try {
			const values = await driftForm.validateFields();
			setDriftActionSubmitting(true);
			if (driftActionType === "policy") {
				await updateSchemaDriftPolicy(driftActionTarget.id, {
					policyMode: values.policyMode,
					note: values.note,
				});
				toast.success("策略已更新");
			} else {
				await updateSchemaDriftTicket(driftActionTarget.id, {
					ticketStatus: values.ticketStatus,
					assignee: values.assignee,
					note: values.note,
				});
				toast.success("工单状态已更新");
			}
			setDriftActionOpen(false);
			setDriftActionTarget(null);
			setDriftActionType(null);
			driftForm.resetFields();
			await loadDriftEvents();
		} catch (error: any) {
			if (error?.errorFields) {
				return;
			}
			// error toast handled by global interceptor
		} finally {
			setDriftActionSubmitting(false);
		}
	};

	const runColumns: ColumnsType<SyncRun> = [
		{
			title: "开始时间",
			dataIndex: "startedAt",
			sorter: (a, b) => {
				const ta = a.startedAt ? new Date(a.startedAt as any).getTime() : 0;
				const tb = b.startedAt ? new Date(b.startedAt as any).getTime() : 0;
				return ta - tb;
			},
		},
		{
			title: "结束时间",
			dataIndex: "finishedAt",
			sorter: (a, b) => {
				const ta = a.finishedAt ? new Date(a.finishedAt as any).getTime() : 0;
				const tb = b.finishedAt ? new Date(b.finishedAt as any).getTime() : 0;
				return ta - tb;
			},
		},
		{ title: "状态", dataIndex: "status", render: statusTag },
		{ title: "发现表", dataIndex: "tablesDiscovered" },
		{ title: "新增表", dataIndex: "tablesCreated" },
		{ title: "更新表", dataIndex: "datasetsUpdated" },
		{ title: "失效总数", dataIndex: "datasetsRemoved" },
		{ title: "失效标记", dataIndex: "datasetsMarkedStale" },
		{ title: "物理清理", dataIndex: "datasetsPurged" },
		{ title: "新增字段", dataIndex: "columnsImported" },
		{ title: "错误分类", dataIndex: "errorCategory", render: (value) => errorCategoryTag(value) },
		{ title: "错误", dataIndex: "error", render: (value) => <Text type="danger">{value || "-"}</Text> },
		{
			title: "诊断",
			render: (_, row) => (
				<Button type="link" size="small" onClick={() => void openRunDiagnostics(row)}>
					查看日志
				</Button>
			),
		},
	];

	const columnColumns: ColumnsType<ColumnRow> = [
		{ title: "字段", dataIndex: "name", sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
		{ title: "类型", dataIndex: "type" },
		{
			title: "状态",
			dataIndex: "status",
			render: (value) => {
				const normalized = String(value || "").toUpperCase();
				if (!normalized) return <Tag>未知</Tag>;
				if (normalized === "DRAFT") return <Tag color="orange">草稿</Tag>;
				if (normalized === "ACTIVE") return <Tag color="green">正式</Tag>;
				return <Tag>{value}</Tag>;
			},
		},
		{ title: "备注", dataIndex: "comment" },
	];

	const driftColumns: ColumnsType<SchemaDriftEvent> = [
		{
			title: "时间",
			dataIndex: "createdDate",
			render: (value) => value || "-",
			sorter: (a, b) => {
				const ta = a.createdDate ? new Date(a.createdDate as any).getTime() : 0;
				const tb = b.createdDate ? new Date(b.createdDate as any).getTime() : 0;
				return ta - tb;
			},
		},
		{
			title: "对象",
			render: (_, row) => {
				const table = [row.hiveDatabase, row.hiveTable].filter(Boolean).join(".");
				return row.datasetName ? `${row.datasetName}${table ? ` · ${table}` : ""}` : table || "-";
			},
		},
		{
			title: "变更量",
			render: (_, row) => `+${row.addedCount || 0} / -${row.removedCount || 0} / ~${row.changedCount || 0}`,
		},
		{ title: "策略", dataIndex: "policyMode", render: (value) => driftPolicyTag(value) },
		{ title: "工单", dataIndex: "ticketStatus", render: (value) => driftTicketTag(value) },
		{ title: "责任人", dataIndex: "ticketAssignee", render: (value) => value || "-" },
		actionColumn<SchemaDriftEvent>(
			(row) => [
				{ key: "policy", label: "策略", onClick: () => openDriftPolicyModal(row) },
				{ key: "ticket", label: "工单", onClick: () => openDriftTicketModal(row) },
			],
			{ maxActions: 2, fixed: false },
		),
	];

	const selectedSummary = useMemo(() => tables.find((item) => item.fqn === selectedFqn) || null, [tables, selectedFqn]);
	const latestRun = useMemo(() => {
		if (!runs.length) return null;
		return runs[0] || null;
	}, [runs]);
	const syncInProgress = useMemo(() => {
		if (!syncStatus) return false;
		if (selectedPipeline?.integration === "JDBC") return Boolean(syncStatus.jdbc?.inProgress);
		return Boolean(syncStatus.primary?.inProgress);
	}, [syncStatus, selectedPipeline?.integration]);
	const syncProgressPercent = useMemo(() => {
		if (!syncInProgress) return 100;
		const startedText = String(selectedPipeline?.lastRun || "").trim();
		const startedMs = startedText ? new Date(startedText).getTime() : 0;
		if (!Number.isFinite(startedMs) || startedMs <= 0) return 45;
		const elapsedSec = Math.max(0, (Date.now() - startedMs) / 1000);
		const estimated = Math.min(95, Math.floor((elapsedSec / 180) * 100));
		return Math.max(35, estimated);
	}, [syncInProgress, selectedPipeline?.lastRun]);
	const columnRows = useMemo(() => buildColumnRows(tableDetail), [tableDetail]);
	const columnStatusStats = useMemo(() => {
		let draft = 0;
		let active = 0;
		let other = 0;
		columnRows.forEach((row) => {
			const label = String(row.status || "").toUpperCase();
			if (label === "DRAFT") draft += 1;
			else if (label === "ACTIVE") active += 1;
			else other += 1;
		});
		return { draft, active, other };
	}, [columnRows]);
	return (
		<div className="space-y-4">
			<Card
				title="数据源结构采集"
				extra={
					<Space>
						<Button onClick={() => router.push("/catalog/metadata-management")}>返回元数据管理</Button>
						<Button onClick={() => void loadPipelines()}>刷新任务</Button>
					</Space>
				}
			>
				<Space wrap>
					<Tag>Cron {syncConfig?.autoSyncCron ? syncConfig.autoSyncCron : "未配置"}</Tag>
					<Tag>最近状态 {selectedPipeline?.status ? String(selectedPipeline.status) : "未知"}</Tag>
					<Tag>目标表数 {selectedPipeline?.tablesFound ?? 0}</Tag>
				</Space>
			</Card>

			<Row gutter={[24, 24]} align="top">
				<Col xs={24} xl={12}>
					<Card title="采集任务与触发">
						<Spin spinning={loadingPipelines}>
							<div className="space-y-4">
								<Space className="mb-3" align="center">
									<Text type="secondary">自动采集</Text>
									<Switch
										checked={Boolean(syncConfig?.autoSyncEnabled)}
										loading={syncConfigUpdating}
										disabled={!canManage}
										onChange={handleAutoSyncToggle}
										checkedChildren="开启"
										unCheckedChildren="关闭"
									/>
									<Tag>{syncConfig?.autoSyncCron ? `Cron: ${syncConfig.autoSyncCron}` : "Cron 未配置"}</Tag>
								</Space>
								<Space className="w-full" direction="vertical" size={8}>
									<Text type="secondary">自动采集 Cron</Text>
									<Space.Compact className="w-full">
										<Input
											value={cronDraft}
											onChange={(e) => setCronDraft(e.target.value)}
											placeholder="例如：0 0 3 * * *"
											disabled={!canManage || syncConfigUpdating}
										/>
										<Button
											onClick={handleSyncCronSave}
											loading={syncConfigUpdating}
											disabled={
												!canManage ||
												!cronDraft.trim() ||
												cronDraft.trim() === String(syncConfig?.autoSyncCron || "").trim()
											}
										>
											保存 Cron
										</Button>
									</Space.Compact>
								</Space>
								{syncConfig?.message ? <div className="mb-3 text-xs text-slate-500">{syncConfig.message}</div> : null}
								{pipelines.length ? (
									<Form form={form} layout="vertical">
										<Form.Item label="选择采集任务">
											<Select
												value={selectedPipeline?.id}
												onChange={(value) => setSelectedPipelineId(value)}
												options={pipelines.map((item) => ({
													label: `${item.name || "采集任务"} · ${item.source || ""}`.trim(),
													value: item.id,
												}))}
											/>
										</Form.Item>
										<Form.Item name="reason" label="触发说明">
											<Input placeholder="例如：测试同步" />
										</Form.Item>
										<Descriptions size="small" column={1} bordered>
											<Descriptions.Item label="来源">{selectedPipeline?.source || "-"}</Descriptions.Item>
											<Descriptions.Item label="调度策略">{selectedPipeline?.schedule || "-"}</Descriptions.Item>
											<Descriptions.Item label="最近状态">{statusTag(selectedPipeline?.status)}</Descriptions.Item>
											<Descriptions.Item label="最近发现表">{selectedPipeline?.tablesFound ?? "-"}</Descriptions.Item>
										</Descriptions>
										<div>
											<div className="mb-1 text-xs text-slate-500">同步进度</div>
											<Progress
												percent={syncProgressPercent}
												size="small"
												status={syncInProgress ? "active" : "normal"}
												format={() => (syncInProgress ? "运行中" : "空闲")}
											/>
										</div>
										{latestRun ? (
											<Space size={8} wrap className="mt-2">
												<Tag color="green">新增 {latestRun.datasetsCreated ?? 0}</Tag>
												<Tag color="blue">更新 {latestRun.datasetsUpdated ?? 0}</Tag>
												<Tag color="red">失效 {latestRun.datasetsRemoved ?? 0}</Tag>
											</Space>
										) : null}
										{selectedPipeline?.error ? (
											<div className="mt-3 text-sm text-red-500">错误：{selectedPipeline.error}</div>
										) : null}
										{selectedPipeline?.logLines && selectedPipeline.logLines.length ? (
											<div className="mt-3 rounded border bg-muted/20 p-3 text-xs text-muted-foreground">
												<div className="mb-2 font-medium text-foreground">最近日志</div>
												<ul className="list-disc space-y-1 pl-4">
													{selectedPipeline.logLines.slice(0, 10).map((line, idx) => (
														<li key={idx}>{line}</li>
													))}
												</ul>
											</div>
										) : null}
										<Space className="mt-4">
											<Button type="primary" onClick={handleTrigger} disabled={!canManage}>
												立即采集
											</Button>
											<Button onClick={() => selectedPipeline?.integration && loadRuns(selectedPipeline.integration)}>
												刷新历史
											</Button>
										</Space>
									</Form>
								) : (
									<EmptyState title="暂无采集任务" description="请先配置数据源或数据湖连接。" />
								)}
							</div>
						</Spin>
					</Card>
				</Col>
				<Col xs={24} xl={12}>
					<Card title="元数据结果预览">
						<Spin spinning={loadingTables}>
							<Space direction="vertical" className="w-full" size={12}>
								<Space className="w-full" align="start">
									<Input
										placeholder="搜索表名或关键字"
										value={keyword}
										onChange={(e) => setKeyword(e.target.value)}
										allowClear
									/>
									<Button onClick={() => void loadTables(keyword)}>搜索</Button>
								</Space>
								{tablePageMeta ? (
									<Space direction="vertical" className="w-full" size={8}>
										<Space size={8} wrap>
											<Text type="secondary">数据来源</Text>
											{metadataSourceTag(tablePageMeta.metadataSource)}
										</Space>
										{tablePageMeta.fallbackReason || tablePageMeta.message ? (
											<Alert
												type={tablePageMeta.metadataSource === "catalog" ? "warning" : "info"}
												showIcon
												message={tablePageMeta.fallbackReason || tablePageMeta.message}
											/>
										) : null}
									</Space>
								) : null}
								{tables.length ? (
									<>
										<Form layout="vertical">
											<Form.Item label="已发现表">
												<Select
													value={selectedFqn}
													onChange={(value) => setSelectedFqn(value)}
													options={tables.map((item) => ({
														label:
															[item.database, item.schema, item.name].filter(Boolean).join(".") ||
															item.name ||
															item.fqn ||
															"-",
														value: item.fqn,
													}))}
												/>
											</Form.Item>
										</Form>
										<Descriptions size="small" bordered column={1}>
											<Descriptions.Item label="详情来源">
												{metadataSourceTag(tableDetail?.metadataSource || tablePageMeta?.metadataSource)}
											</Descriptions.Item>
											<Descriptions.Item label="FQN">
												{tableDetail?.fqn || selectedSummary?.fqn || "-"}
											</Descriptions.Item>
											<Descriptions.Item label="服务">{selectedSummary?.service || "-"}</Descriptions.Item>
											<Descriptions.Item label="库/Schema">
												{[selectedSummary?.database, selectedSummary?.schema].filter(Boolean).join(".") || "-"}
											</Descriptions.Item>
											<Descriptions.Item label="表名">{selectedSummary?.name || "-"}</Descriptions.Item>
											<Descriptions.Item label="描述">{selectedSummary?.description || "-"}</Descriptions.Item>
											<Descriptions.Item label="字段数">{selectedSummary?.columnCount ?? "-"}</Descriptions.Item>
										</Descriptions>
										{tableDetail?.fallbackReason || tableDetail?.message ? (
											<Alert
												type={tableDetail?.metadataSource === "catalog" ? "warning" : "info"}
												showIcon
												message={tableDetail?.fallbackReason || tableDetail?.message}
											/>
										) : null}
										{columnRows.length ? (
											<Space size={6} className="mt-3 flex flex-wrap">
												<Tag color="orange">草稿 {columnStatusStats.draft}</Tag>
												<Tag color="green">正式 {columnStatusStats.active}</Tag>
												{columnStatusStats.other ? <Tag>其他 {columnStatusStats.other}</Tag> : null}
											</Space>
										) : null}
										<div>
											<Text type="secondary">字段列表</Text>
											<CompactTable
												size="small"
												pagination={false}
												columns={columnColumns}
												dataSource={columnRows}
												rowKey={(row) => row.key}
											/>
										</div>
									</>
								) : (
									<EmptyState title="暂无结构元数据" description="请先完成数据源结构采集或检查采集服务连接。" />
								)}
							</Space>
						</Spin>
					</Card>
				</Col>
			</Row>

			<Card
				title="采集历史"
				extra={
					<Button onClick={() => selectedPipeline?.integration && loadRuns(selectedPipeline.integration)}>刷新</Button>
				}
			>
				<CompactTable
					rowKey={(row) => row.id || `${row.startedAt}-${row.finishedAt}`}
					columns={runColumns}
					dataSource={runs}
					loading={loadingRuns}
					scroll={{ x: 1600 }}
					pagination={{ defaultPageSize: 10 }}
				/>
			</Card>

			<Modal
				open={diagOpen}
				title="采集诊断"
				onCancel={() => setDiagOpen(false)}
				footer={<Button onClick={() => setDiagOpen(false)}>关闭</Button>}
				width={860}
			>
				<Descriptions size="small" bordered column={2}>
					<Descriptions.Item label="运行ID">{diagData?.id || "-"}</Descriptions.Item>
					<Descriptions.Item label="状态">{statusTag(diagData?.status)}</Descriptions.Item>
					<Descriptions.Item label="集成类型">{diagData?.integration || "-"}</Descriptions.Item>
					<Descriptions.Item label="错误分类">{errorCategoryTag(diagData?.errorCategory)}</Descriptions.Item>
					<Descriptions.Item label="开始时间">{diagData?.startedAt || "-"}</Descriptions.Item>
					<Descriptions.Item label="结束时间">{diagData?.finishedAt || "-"}</Descriptions.Item>
					<Descriptions.Item label="错误信息" span={2}>
						{diagData?.error || "-"}
					</Descriptions.Item>
				</Descriptions>
				<div className="mt-3 rounded border bg-muted/20 p-3 text-xs text-muted-foreground">
					<div className="mb-2 font-medium text-foreground">日志片段</div>
					{diagLoading ? (
						<div>加载中...</div>
					) : (
						<ul className="list-disc space-y-1 pl-4">
							{Array.isArray(diagData?.logLines) && diagData.logLines.length ? (
								diagData.logLines.map((line: string, idx: number) => <li key={idx}>{line}</li>)
							) : (
								<li>暂无日志</li>
							)}
						</ul>
					)}
				</div>
			</Modal>

			<Card
				title="Schema 漂移工单"
				extra={
					<Space>
						<Select
							value={driftPolicyFilter}
							onChange={setDriftPolicyFilter}
							style={{ width: 140 }}
							options={[
								{ label: "全部策略", value: "ALL" },
								{ label: "待审批", value: "REVIEW" },
								{ label: "自动迁移", value: "AUTO_APPLY" },
								{ label: "阻断", value: "BLOCK" },
							]}
						/>
						<Select
							value={driftTicketFilter}
							onChange={setDriftTicketFilter}
							style={{ width: 140 }}
							options={[
								{ label: "全部工单", value: "ALL" },
								{ label: "待处理", value: "OPEN" },
								{ label: "处理中", value: "IN_REVIEW" },
								{ label: "已处理", value: "RESOLVED" },
								{ label: "已忽略", value: "IGNORED" },
								{ label: "已驳回", value: "REJECTED" },
							]}
						/>
						<Button onClick={() => void loadDriftEvents()}>刷新</Button>
					</Space>
				}
			>
				<CompactTable
					rowKey={(row) => row.id || `${row.datasetId}-${row.hiveDatabase}-${row.hiveTable}-${row.createdDate}`}
					columns={driftColumns}
					dataSource={driftEvents}
					loading={loadingDrift}
					scroll={{ x: 1100 }}
					pagination={{ defaultPageSize: 10 }}
				/>
			</Card>

			<Modal
				open={driftActionOpen}
				title={driftActionType === "policy" ? "更新策略" : "更新工单状态"}
				onCancel={() => setDriftActionOpen(false)}
				onOk={submitDriftAction}
				okText="提交"
				cancelText="取消"
				confirmLoading={driftActionSubmitting}
				okButtonProps={{ disabled: !canManage }}
			>
				<Form form={driftForm} layout="vertical">
					{driftActionType === "policy" ? (
						<Form.Item name="policyMode" label="漂移策略" rules={[{ required: true, message: "请选择策略" }]}>
							<Select
								options={[
									{ label: "待审批", value: "REVIEW" },
									{ label: "自动迁移", value: "AUTO_APPLY" },
									{ label: "阻断", value: "BLOCK" },
								]}
							/>
						</Form.Item>
					) : (
						<>
							<Form.Item name="ticketStatus" label="工单状态" rules={[{ required: true, message: "请选择工单状态" }]}>
								<Select
									options={[
										{ label: "待处理", value: "OPEN" },
										{ label: "处理中", value: "IN_REVIEW" },
										{ label: "已处理", value: "RESOLVED" },
										{ label: "已忽略", value: "IGNORED" },
										{ label: "已驳回", value: "REJECTED" },
									]}
								/>
							</Form.Item>
							<Form.Item name="assignee" label="责任人">
								<Input placeholder="例如：opadmin" />
							</Form.Item>
						</>
					)}
					<Form.Item name="note" label="备注">
						<Input.TextArea rows={4} placeholder="填写策略说明或处理备注" />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
