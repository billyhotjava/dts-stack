import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Alert, Button, Card, Divider, Form, Input, InputNumber, Modal, Progress, Select, Space, Steps, Tag, Typography } from "antd";
import { CompactTable } from "@/components/table";
import { toast } from "sonner";
import { PageHeader } from "@/components/page-header";
import { createIngestionTask, listSqlModels } from "@/api/platformApi";
import { useUserInfo } from "@/store/userStore";
import { useParams, useRouter } from "@/routes/hooks";
import {
	ingestionTaskAPI,
	type DefaultDestinationStatus,
	type FileUploadResult,
	type IngestionConnectorCapabilityDTO,
	type IngestionTaskDTO,
	type IngestionTaskTemplateDTO,
	type TableInfo,
} from "@/api/ingestion";
import dataSourcesService, { type InfraDataSource } from "@/api/services/dataSourcesService";
import { listTables as sqlListTables, listColumns as sqlListColumns, type TableInfo as SqlTableInfo, type ColumnInfo } from "@/api/sql-workbench";
import { DbUnifiedStep } from "./steps/DbUnifiedStep";
import FileUnifiedStep from "./steps/FileUnifiedStep";
import { UnifiedReviewStep } from "./steps/UnifiedReviewStep";
import { getOrgTree, type OrgNode } from "@/api/services/directoryService";
import { useActiveDept } from "@/store/contextStore";
import type { ExtraColumnDef } from "./steps/types";
import { resolveAsyncRunPollHint, resolveCreatedTaskId } from "./transformCreateAsyncRun.helpers";
import { loadTransformCreateBootstrap } from "./transformCreateBootstrap.helpers";
import { buildTransformCreateDraftPayload } from "./transformCreateDraft.helpers";
import { filterBusinessFileMappingColumns } from "./fileColumnSystemFields.helpers";
import {
	buildRefreshFileParseInput,
	buildSheetChangeFileParseInput,
} from "./transformCreateFileParse.helpers";
import { buildFilePostParseOutcome } from "./transformCreateFilePostParse.helpers";
import { buildTemplateRenderRequest, resolveTemplateApplyOutcome } from "./transformCreateTemplate.helpers";
import {
	buildTransformEditRestoreState,
	parseTransformCreateDraft,
	serializeTransformCreateDraft,
} from "./transformCreateState.helpers";
import { useTransformAsyncRunProgress } from "./useTransformAsyncRunProgress";
import {
	buildAutoSyncPrefix,
	buildApiReaderConfig,
	DRAFT_STORAGE_KEY,
	GENERIC_JDBC_READER,
	SYNC_MODE_LABELS,
	applyPrefixToTables,
	applyReaderTypeToConfig,
	applyTablesToConfig,
	buildFileBaseName,
	buildGovernanceSyncFields,
	buildJobPreview,
	buildModelSelectorFromNames,
	buildReaderConfig,
	buildSyncConfigFromValues,
	buildSyncScheduleSpec,
	buildSyncScheduleText,
	buildTableKey,
	buildWriterConfig,
	clearDraft,
	deriveSyncModeOptions,
	extractFileUploadResult,
	extractMappingTables,
	extractReaderTables,
	extractWriterTables,
	hasConnectionOverride,
	hasTableEntries,
	isApiDataSource,
	isJdbcSource,
	isSourceAlignedTables,
	isSameTableList,
	mapTaskToForm,
	mergeTableSelections,
	normalizeDiscoveredTable,
	normalizeReaderType,
	normalizeTableName,
	normalizeTag,
	normalizeText,
	normalizeType,
	normalizeSyncModeValue,
	parseJson,
	resolveReaderTypeFromDataSource,
	resolveReaderTypeFromSourceId,
	resolveReaderTypeFromValues,
	resolveSourceSystemFromDataSource,
	resolveTaskName,
	shouldApplyWriterTables,
	splitLines,
	tryParseJson,
	validateCronExpression,
	type SyncModeValue,
} from "./ingestionFormHelpers";

const { Text } = Typography;

const loadDraft = () => {
	try {
		return parseTransformCreateDraft(localStorage.getItem(DRAFT_STORAGE_KEY));
	} catch {
		return null;
	}
};

const saveDraft = (values: Record<string, any>) => {
	try {
		localStorage.setItem(DRAFT_STORAGE_KEY, serializeTransformCreateDraft(values));
		return true;
	} catch {
		return false;
	}
};

export default function TransformCreatePage() {
	const [saving, setSaving] = useState(false);
	const [savingDraft, setSavingDraft] = useState(false);
	const [hasDraft, setHasDraft] = useState(false);
	const [loadingTask, setLoadingTask] = useState(false);
	const [editingTask, setEditingTask] = useState<IngestionTaskDTO | null>(null);
	const [submittedTaskId, setSubmittedTaskId] = useState<number | string | null>(null);
	const [discoveringTables, setDiscoveringTables] = useState(false);
	const [discoveredTables, setDiscoveredTables] = useState<TableInfo[]>([]);
	const [selectedTableKeys, setSelectedTableKeys] = useState<string[]>([]);
	const selectedTableKeysRef = useRef<string[]>([]);
	const [discoverError, setDiscoverError] = useState("");
	const [defaultDestinationStatus, setDefaultDestinationStatus] = useState<DefaultDestinationStatus | null>(null);
	const [loadingDefaultDestination, setLoadingDefaultDestination] = useState(false);
	const [defaultDestinationError, setDefaultDestinationError] = useState("");
	const [currentStep, setCurrentStep] = useState(0);
	const [dataSources, setDataSources] = useState<InfraDataSource[]>([]);
	const [loadingDataSources, setLoadingDataSources] = useState(false);
	const activeDept = useActiveDept();
	const [deptOptions, setDeptOptions] = useState<{ label: string; value: string }[]>([]);
	const [loadingDeptOptions, setLoadingDeptOptions] = useState(false);
	const [connectorCapabilities, setConnectorCapabilities] = useState<IngestionConnectorCapabilityDTO[]>([]);
	const [capabilityLoadFailed, setCapabilityLoadFailed] = useState(false);
	const [taskTemplates, setTaskTemplates] = useState<IngestionTaskTemplateDTO[]>([]);
	const [loadingTaskTemplates, setLoadingTaskTemplates] = useState(false);
	const [selectedTemplateId, setSelectedTemplateId] = useState<string | undefined>(undefined);
	const [applyingTemplate, setApplyingTemplate] = useState(false);
	const [sqlModels, setSqlModels] = useState<Array<{ id?: string; name?: string; alias?: string }>>([]);
	const [loadingSqlModels, setLoadingSqlModels] = useState(false);
	const [fileUploadResult, setFileUploadResult] = useState<FileUploadResult | null>(null);
	// ODS 表关联
	const [odsTableList, setOdsTableList] = useState<SqlTableInfo[]>([]);
	const [odsTableLoading, setOdsTableLoading] = useState(false);
	const [selectedOdsTable, setSelectedOdsTable] = useState<string | undefined>(undefined);
	const [odsColumns, setOdsColumns] = useState<ColumnInfo[]>([]);
	const [odsColumnsLoading, setOdsColumnsLoading] = useState(false);
	const [odsMatchApplied, setOdsMatchApplied] = useState(false);
	const [extraColumns, setExtraColumns] = useState<ExtraColumnDef[]>([]);
	const [uploadingFile, setUploadingFile] = useState(false);
	const [filePreviewRows, setFilePreviewRows] = useState(20);
	const [filePreviewCols, setFilePreviewCols] = useState(8);
	const [previewRefreshing, setPreviewRefreshing] = useState(false);
	const [errorPreviewOpen, setErrorPreviewOpen] = useState(false);
	const [errorPreviewLoading, setErrorPreviewLoading] = useState(false);
	const [errorPreviewRows, setErrorPreviewRows] = useState<Array<{ rowIndex?: number; message?: string }>>([]);
	const [errorPreviewLimit, setErrorPreviewLimit] = useState(50);
	// batchFieldModalOpen and batchFieldText moved to FileBasicStep
	const {
		asyncRunModalOpen,
		asyncRunTaskId,
		asyncRunTaskName,
		asyncRunExecution,
		asyncRunProgress,
		closeAsyncRunModal,
		startAsyncRunProgress,
	} = useTransformAsyncRunProgress();
	const [form] = Form.useForm();
	const lastAutoDiscoveryKeyRef = useRef("");
	const router = useRouter();
	const params = useParams();
	const editId = params?.id ? Number(params.id) : undefined;
	const isEdit = Number.isFinite(editId);
	const userInfo = useUserInfo() as any;
	const editorMode = Form.useWatch("editorMode", form);
	const [sourceCategory, setSourceCategory] = useState<string>("database");
	const handleSourceCategoryChange = useCallback(
		(next: string) => {
			setSourceCategory(next);
			setSelectedTableKeys([]);
			form.setFieldsValue({
				sourceCategory: next,
				sourceDataSourceId: undefined,
				readerType: next === "api" ? "httpreader" : undefined,
				tableSelectionMode: next === "api" ? "manual" : "all",
				selectedTables: "",
				readerTables: "",
				writerTables: "",
				airflowEnabled: next === "api" ? false : form.getFieldValue("airflowEnabled"),
				runNow: next === "api" ? false : form.getFieldValue("runNow"),
				syncMode: next === "api" ? "full_refresh" : form.getFieldValue("syncMode"),
			});
		},
		[form]
	);
	const syncMode = Form.useWatch("syncMode", form);
	const scheduleType = Form.useWatch("scheduleType", form);
	const tableSelectionMode = Form.useWatch("tableSelectionMode", form);
	const formValues = Form.useWatch([], form);
	const selectedTablesValue = Form.useWatch("selectedTables", form);
	const selectedDataSourceId = Form.useWatch("sourceDataSourceId", form);
	const selectedDataSource = useMemo(
		() => dataSources.find((item) => String(item.id) === String(selectedDataSourceId)),
		[dataSources, selectedDataSourceId]
	);
	const lakeDatasourceId = useMemo(() => {
		if (!defaultDestinationStatus) return null;
		if (defaultDestinationStatus.dataSourceId) {
			return defaultDestinationStatus.dataSourceId;
		}
		if (!defaultDestinationStatus.destinationName || !dataSources.length) return null;
		const match = dataSources.find(ds => ds.name === defaultDestinationStatus.destinationName);
		return match?.id ?? null;
	}, [dataSources, defaultDestinationStatus]);
	const discoveredTableKeys = useMemo(
		() => discoveredTables.map((item) => buildTableKey(item)),
		[discoveredTables]
	);

	useEffect(() => {
		const normalized = mergeTableSelections(selectedTablesValue);
		const currentMode = normalizeText(form.getFieldValue("tableSelectionMode")) || normalizeText(tableSelectionMode) || "all";
		if (!normalized.length) {
			if (selectedTableKeys.length && currentMode === "all") {
				setSelectedTableKeys([]);
			}
			selectedTableKeysRef.current = normalized;
			return;
		}
		if (!isSameTableList(normalized, selectedTableKeys)) {
			setSelectedTableKeys(normalized);
		}
		selectedTableKeysRef.current = normalized;
	}, [selectedTablesValue, selectedTableKeys, tableSelectionMode, form]);

	const initialValues = useMemo(
		() => ({
			editorMode: "visual",
			sourceCategory: "database",
			syncMode: "full_refresh",
			scheduleType: "manual",
			scheduleIntervalMinutes: 60,
			tableSelectionMode: "all",
			airflowEnabled: true,
			runNow: true,
			readerColumns: "*",
			writerColumns: "*",
			incrementalType: "datetime",
			syncPrefix: "",
			fileAutoId: true,
			priority: "MEDIUM",
			rejectPolicy: "REJECT",
			windowTimezone: "Asia/Shanghai",
			ownerDept: activeDept || undefined,
		}),
		[activeDept]
	);

	const tableMappingPreview = useMemo(() => {
		const vals = form.getFieldsValue(true);
		if (vals?.sourceCategory === "api") {
			try {
				const apiConfig = buildApiReaderConfig(vals);
				const resource = (apiConfig.resource || {}) as Record<string, any>;
				const source = normalizeText(resource.resourceId);
				const target = normalizeText(resource.targetTable);
				return source && target ? [{ source, target }] : [];
			} catch {
				return [];
			}
		}
		const prefix = normalizeText(vals?.syncPrefix);
		const tables = mergeTableSelections(
			selectedTableKeys,
			splitLines(vals?.readerTables),
		);
		if (!tables.length) return [];
		return tables.map((source) => {
			const base = source.includes(".") ? source.split(".").pop() || source : source;
			return { source, target: prefix ? `${prefix}${base}` : base };
		});
	}, [form, selectedTableKeys]);

	const handleCreateTaskResult = (result: any, runNow: boolean, taskName: string) => {
		const createdTaskId = resolveCreatedTaskId(result);
		const pollHint = resolveAsyncRunPollHint(result);
		clearDraft();
		setHasDraft(false);
		if (createdTaskId) {
			setSubmittedTaskId(createdTaskId);
		}
		if (runNow && createdTaskId) {
			toast.success("任务已提交，正在后台执行");
			startAsyncRunProgress(createdTaskId, taskName, pollHint);
			return;
		}
		toast.success("入湖任务已提交");
		router.push("/explore/etl/transform");
	};

	const syncSelectedTablesToForm = (tables: string[], opts?: { silent?: boolean }) => {
		const nextTables = mergeTableSelections(tables);
		setSelectedTableKeys(nextTables);
		selectedTableKeysRef.current = nextTables;
		form.setFieldValue("selectedTables", nextTables.join("\n"));
		if (!nextTables.length) {
			form.setFieldValue("tableSelectionMode", "all");
			form.setFieldValue("readerTables", "");
			form.setFieldValue("writerTables", "");
			if (!opts?.silent) {
				toast.info("已清空表清单");
			}
			return;
		}
		const values = form.getFieldsValue(true);
		const isJsonMode = values.editorMode === "json";
		const writerTables = applyPrefixToTables(nextTables, values.syncPrefix);
		try {
			if (isJsonMode) {
				const readerConfig = parseJson(values.readerConfig, "Reader 配置") as Record<string, any> | undefined;
				const nextReader = applyTablesToConfig(readerConfig || {}, nextTables);
				form.setFieldValue("readerConfig", JSON.stringify(nextReader || {}, null, 2));
				const writerConfig = parseJson(values.writerConfig, "Writer 配置") as Record<string, any> | undefined;
				const nextWriter = applyTablesToConfig(writerConfig || {}, writerTables);
				form.setFieldValue("writerConfig", JSON.stringify(nextWriter || {}, null, 2));
			}
			// Always sync visual-mode fields so they persist across steps
			form.setFieldValue("readerTables", nextTables.join("\n"));
			form.setFieldValue("writerTables", writerTables.join("\n"));
			form.setFieldValue("tableSelectionMode", "manual");
			if (!opts?.silent) {
				toast.success("已更新表清单");
			}
		} catch (err: any) {
			if (!opts?.silent) {
				toast.error(err?.message || "更新表清单失败");
			}
		}
	};

	const resolveSelectedTables = (values?: Record<string, any>) => {
		const snapshot = values ?? form.getFieldsValue(true);
		const selectionMode = normalizeText(snapshot?.tableSelectionMode) || "all";
		const selected = snapshot?.selectedTables ?? form.getFieldValue("selectedTables");
		const keys = mergeTableSelections(selectedTableKeysRef.current, selectedTableKeys);
		if (selectionMode !== "manual") {
			return mergeTableSelections(selected, keys);
		}
		return mergeTableSelections(selected, keys, snapshot?.readerTables, snapshot?.writerTables);
	};

	useEffect(() => {
		let active = true;
		const loadBootstrap = async () => {
			setLoadingDataSources(true);
			setLoadingTaskTemplates(true);
			setLoadingDefaultDestination(true);
			setLoadingSqlModels(true);
			const bootstrap = await loadTransformCreateBootstrap(
				{
					loadDataSources: () => dataSourcesService.list(),
					loadConnectorCapabilities: () => ingestionTaskAPI.getConnectorCapabilities(),
					loadTaskTemplates: () => ingestionTaskAPI.getTaskTemplates(),
					loadDefaultDestinationStatus: () => ingestionTaskAPI.getDefaultDestinationStatus(),
					loadSqlModels: () => listSqlModels() as Promise<Array<{ id?: string; name?: string; alias?: string }>>,
				},
				selectedTemplateId
			);
			if (!active) return;
			setDataSources(bootstrap.dataSources);
			setConnectorCapabilities(bootstrap.connectorCapabilities);
			setCapabilityLoadFailed(bootstrap.capabilityLoadFailed);
			setTaskTemplates(bootstrap.taskTemplates);
			if (bootstrap.selectedTemplateId && bootstrap.selectedTemplateId !== selectedTemplateId) {
				setSelectedTemplateId(bootstrap.selectedTemplateId);
			}
			setDefaultDestinationStatus(bootstrap.defaultDestinationStatus);
			setDefaultDestinationError(bootstrap.defaultDestinationError);
			if (bootstrap.defaultDestinationStatus?.writerType) {
				form.setFieldValue("writerType", bootstrap.defaultDestinationStatus.writerType);
			}
			setSqlModels(bootstrap.sqlModels);
			if (bootstrap.dataSourcesError) {
				toast.error(bootstrap.dataSourcesError);
			}
			if (bootstrap.sqlModelsError) {
				toast.error(bootstrap.sqlModelsError);
			}
			setLoadingDataSources(false);
			setLoadingTaskTemplates(false);
			setLoadingDefaultDestination(false);
			setLoadingSqlModels(false);
		};
		void loadBootstrap();
		return () => {
			active = false;
		};
	}, [form]);

	useEffect(() => {
		let active = true;
		setLoadingDeptOptions(true);
		getOrgTree()
			.then((nodes: OrgNode[]) => {
				if (!active) return;
				const flatten = (list: OrgNode[]): { label: string; value: string }[] =>
					list.flatMap((n) => [
						...(n.deptCode ? [{ label: n.name, value: n.deptCode }] : []),
						...(n.children ? flatten(n.children) : []),
					]);
				const options = flatten(nodes);
				if (options.length === 0 && activeDept) {
					options.push({ label: activeDept, value: activeDept });
				}
				setDeptOptions(options);
			})
			.catch(() => {
				if (!active) return;
				if (activeDept) {
					setDeptOptions([{ label: activeDept, value: activeDept }]);
				}
			})
			.finally(() => { if (active) setLoadingDeptOptions(false); });
		return () => { active = false; };
	}, []);

	useEffect(() => {
		if (!selectedDataSource) {
			if (!selectedDataSourceId && sourceCategory !== "file" && form.getFieldValue("readerType")) {
				form.setFieldValue("readerType", undefined);
			}
			if (selectedDataSourceId && sourceCategory !== "file") {
				form.setFieldValue("readerType", GENERIC_JDBC_READER);
			}
			return;
		}
		const readerType = resolveReaderTypeFromDataSource(selectedDataSource);
		if (readerType) {
			if (form.getFieldValue("readerType") !== readerType) {
				form.setFieldValue("readerType", readerType);
			}
		} else if (isJdbcSource(selectedDataSource)) {
			form.setFieldValue("readerType", GENERIC_JDBC_READER);
		}
		const currentSourceSystem = normalizeText(form.getFieldValue("sourceSystem"));
		if (!currentSourceSystem) {
			const resolved = resolveSourceSystemFromDataSource(selectedDataSource);
			if (resolved) {
				form.setFieldValue("sourceSystem", resolved);
			}
		}
		const currentDagSelector = normalizeText(form.getFieldValue("dbtDagSelector"));
		if (!currentDagSelector) {
			const sourceSystem = normalizeText(form.getFieldValue("sourceSystem")) || resolveSourceSystemFromDataSource(selectedDataSource);
			if (sourceSystem) {
				form.setFieldValue("dbtDagSelector", `tab:${normalizeTag(sourceSystem)}`);
			}
		}
		const currentSyncPrefix = normalizeText(form.getFieldValue("syncPrefix"));
		if (!currentSyncPrefix) {
			const autoPrefix = buildAutoSyncPrefix(selectedDataSource);
			if (autoPrefix) {
				form.setFieldValue("syncPrefix", autoPrefix);
			}
		}
	}, [form, selectedDataSource, selectedDataSourceId, sourceCategory]);

	useEffect(() => {
		setDiscoveredTables([]);
		setSelectedTableKeys([]);
		setDiscoverError("");
		lastAutoDiscoveryKeyRef.current = "";
	}, [selectedDataSourceId]);

	useEffect(() => {
		if (tableSelectionMode !== "manual") return;
		if (!selectedDataSourceId) return;
		if (!selectedDataSource || !isJdbcSource(selectedDataSource)) return;
		if (discoveringTables) return;
		if (discoveredTables.length > 0) return;
		const schema = normalizeText(form.getFieldValue("readerSchema"));
		const pattern = normalizeText(form.getFieldValue("readerTablePattern"));
		const autoKey = `${selectedDataSourceId || ""}:${schema}:${pattern}`;
		if (lastAutoDiscoveryKeyRef.current === autoKey) return;
		lastAutoDiscoveryKeyRef.current = autoKey;
		void handleDiscoverTables();
	}, [
		tableSelectionMode,
		selectedDataSourceId,
		selectedDataSource,
		discoveringTables,
		discoveredTables.length,
		form,
	]);

	// Selection clearing is handled explicitly when用户切换到“全部表”

	// Sync selected tables to writer fields when entering Step 2
	// This ensures values persist even when writerTables/writerConfig Form.Items
	// were not mounted (on Step 1) when the selection was made
	useEffect(() => {
		if (isFileFlow || currentStep !== 2) return;
		const selected = mergeTableSelections(
			selectedTableKeysRef.current,
			selectedTableKeys,
		);
		if (!selected.length) return;
		const values = form.getFieldsValue(true);
		const isJsonMode = values.editorMode === "json";
		const writerTables = applyPrefixToTables(selected, values.syncPrefix);
		if (isJsonMode) {
			const writerConfig = tryParseJson(values.writerConfig) || {};
			const existingTables = extractWriterTables(writerConfig);
			if (!existingTables.length) {
				const nextWriter = applyTablesToConfig(writerConfig, writerTables);
				form.setFieldValue("writerConfig", JSON.stringify(nextWriter || {}, null, 2));
			}
		} else {
			const current = splitLines(values.writerTables);
			if (!current.length) {
				form.setFieldValue("writerTables", writerTables.join("\n"));
			}
		}
	}, [currentStep, selectedTableKeys, form]);

	const isFileFlow = sourceCategory === "file";
	const isApiFlow = sourceCategory === "api";
	const resolvedConnectorType = useMemo(() => {
		if (isFileFlow) return "file";
		if (isApiFlow || isApiDataSource(selectedDataSource)) return "api";
		const sourceType = normalizeType(selectedDataSource?.type);
		if (sourceType === "airbyte") return "airbyte";
		return "addax";
	}, [isApiFlow, isFileFlow, selectedDataSource]);
	const activeConnectorCapability = useMemo(
		() =>
			connectorCapabilities.find(
				(item) => normalizeType(item.connectorType) === normalizeType(resolvedConnectorType)
			) || null,
		[connectorCapabilities, resolvedConnectorType]
	);
	const activeCapabilitySet = useMemo(() => {
		const set = new Set<string>();
		(activeConnectorCapability?.capabilities || []).forEach((item) => {
			const text = normalizeText(item);
			if (text) set.add(text.toUpperCase());
		});
		return set;
	}, [activeConnectorCapability]);
	const syncModeOptions = useMemo(
		() => deriveSyncModeOptions(resolvedConnectorType, activeConnectorCapability, isFileFlow),
		[resolvedConnectorType, activeConnectorCapability, isFileFlow]
	);
	const supportedSyncModes = useMemo(
		() => new Set(syncModeOptions.map((item) => item.value)),
		[syncModeOptions]
	);
	const fallbackSyncMode = useMemo<SyncModeValue>(() => {
		const fromContract = normalizeSyncModeValue(
			activeConnectorCapability?.constraints
				? String((activeConnectorCapability.constraints as any).fallbackSyncMode || "")
				: ""
		);
		if (fromContract && supportedSyncModes.has(fromContract)) {
			return fromContract;
		}
		if (supportedSyncModes.has("full_refresh")) {
			return "full_refresh";
		}
		return syncModeOptions[0]?.value || "full_refresh";
	}, [activeConnectorCapability, supportedSyncModes, syncModeOptions]);
	const supportsIncremental = useMemo(() => {
		if (isFileFlow) return false;
		return supportedSyncModes.has("incremental");
	}, [supportedSyncModes, isFileFlow]);
	const supportsCdc = useMemo(() => supportedSyncModes.has("cdc"), [supportedSyncModes]);
	const supportsBackfill = useMemo(() => supportedSyncModes.has("backfill"), [supportedSyncModes]);
	const dbStepItems = useMemo(
		() => [
			{ key: "unified", title: "数据源与表选择" },
			{ key: "review", title: "预览与执行" },
		],
		[]
	);
	const fileStepItems = useMemo(
		() => [
			{ key: "unified", title: "上传与配置" },
			{ key: "review", title: "预览与执行" },
		],
		[]
	);
	const stepItems = isFileFlow ? fileStepItems : dbStepItems;

	useEffect(() => {
		setCurrentStep(0);
	}, [sourceCategory]);

	useEffect(() => {
		if (!isFileFlow) return;
		const currentMode = normalizeSyncModeValue(syncMode) || "full_refresh";
		if (!supportedSyncModes.has(currentMode)) {
			form.setFieldValue("syncMode", fallbackSyncMode);
		}
	}, [isFileFlow, syncMode, form, supportedSyncModes, fallbackSyncMode]);

	useEffect(() => {
		const currentMode = normalizeSyncModeValue(syncMode) || "full_refresh";
		if (!supportedSyncModes.has(currentMode)) {
			form.setFieldValue("syncMode", fallbackSyncMode);
			toast.warning(`当前连接器不支持 ${currentMode}，已切换为 ${SYNC_MODE_LABELS[fallbackSyncMode]}`);
		}
	}, [form, supportedSyncModes, syncMode, fallbackSyncMode]);

	// Load draft on mount
	useEffect(() => {
		if (isEdit) {
			return;
		}
		const draft = loadDraft();
		if (draft) {
			setHasDraft(true);
			form.setFieldsValue(draft.formValues);
			if (draft.sourceCategory) {
				setSourceCategory(draft.sourceCategory);
			}
			toast.info(`已恢复草稿 (${new Date(draft.savedAt).toLocaleString()})`);
		}
	}, [form, isEdit]);

	useEffect(() => {
		if (!isEdit || !editId) {
			return;
		}
		const loadTask = async () => {
			try {
				setLoadingTask(true);
				const task = await ingestionTaskAPI.getTask(editId);
				setEditingTask(task);
				const restoreState = buildTransformEditRestoreState(task, {
					mapTaskToForm,
					extractFileUploadResult,
					extractMappingTables,
					tryParseJson,
				});
				form.setFieldsValue(restoreState.formValues);
				if (restoreState.fileUploadResult) {
					setFileUploadResult(restoreState.fileUploadResult);
				}
				if (restoreState.sourceCategory) {
					form.setFieldValue("sourceCategory", restoreState.sourceCategory);
					setSourceCategory(restoreState.sourceCategory);
				}
				if (restoreState.forceReaderType) {
					form.setFieldValue("readerType", restoreState.forceReaderType);
				}
				if (restoreState.extraColumns.length) {
					setExtraColumns(restoreState.extraColumns);
				}
				if (restoreState.mappingTables.length) {
					setSelectedTableKeys(restoreState.mappingTables);
					form.setFieldValue("selectedTables", restoreState.mappingTables.join("\n"));
					syncSelectedTablesToForm(restoreState.mappingTables, { silent: true });
				}
			} catch {
				// handled by global interceptor
			} finally {
				setLoadingTask(false);
			}
		};
		loadTask();
	}, [editId, form, isEdit]);

	const applyTemplate = async () => {
		const template = taskTemplates.find((item) => String(item.id) === String(selectedTemplateId));
		if (!template) {
			toast.warning("请选择模板");
			return;
		}
		setApplyingTemplate(true);
		try {
			const snapshot = (form.getFieldsValue(true) || {}) as Record<string, any>;
			const renderResult = await ingestionTaskAPI.renderTaskTemplate(
				template.id,
				buildTemplateRenderRequest(snapshot, selectedDataSource?.id, fileUploadResult?.originalName)
			);
			const outcome = resolveTemplateApplyOutcome({
				template,
				currentSourceCategory: form.getFieldValue("sourceCategory") as string,
				renderResult,
			});
			form.setFieldsValue(outcome.defaults);
			if (outcome.infoMessage) {
				toast.info(outcome.infoMessage);
			}
			if (outcome.warningMessage) {
				toast.warning(outcome.warningMessage);
			}
			if (outcome.sourceCategory) {
				setSourceCategory(outcome.sourceCategory);
			}
			toast.success(outcome.successMessage);
		} catch (error: any) {
			const outcome = resolveTemplateApplyOutcome({
				template,
				currentSourceCategory: form.getFieldValue("sourceCategory") as string,
				errorMessage: error?.message || "模板预检失败，已按默认值应用",
			});
			form.setFieldsValue(outcome.defaults);
			if (outcome.errorMessage) {
				toast.error(outcome.errorMessage);
			}
			if (outcome.sourceCategory) {
				setSourceCategory(outcome.sourceCategory);
			}
			toast.success(outcome.successMessage);
		}
		setApplyingTemplate(false);
	};

	const handleSaveDraft = async () => {
		if (isEdit) {
			toast.info("编辑模式不支持保存草稿");
			return;
		}
		if (submittedTaskId) {
			toast.info("任务已提交，无需重复保存");
			router.push(`/explore/etl/transform/${submittedTaskId}`);
			return;
		}
		let values: Record<string, any> = {};
		try {
			setSavingDraft(true);
			values = form.getFieldsValue(true) ?? {};
			let name = resolveTaskName(values, form);
			if (!name && selectedDataSource) {
				name = normalizeText(selectedDataSource.name);
			}
			if (!name) {
				throw new Error("请填写任务名称");
			}
			const isFileDraft = values.sourceCategory === "file";
			const isApiDraft = values.sourceCategory === "api";
			const sourceDataSourceId = normalizeText(values.sourceDataSourceId);
			if (!isFileDraft && !sourceDataSourceId) {
				throw new Error("请选择数据源连接");
			}
			let resolvedReaderType = normalizeReaderType(values.readerType);
			if (isFileDraft && fileUploadResult) {
				resolvedReaderType = "txtfilereader";
			} else if (isApiDraft) {
				resolvedReaderType = "httpreader";
			} else {
				if (!resolvedReaderType) {
					resolvedReaderType = resolveReaderTypeFromValues(values);
				}
				if (!resolvedReaderType) {
					resolvedReaderType = resolveReaderTypeFromSourceId(sourceDataSourceId, dataSources, selectedDataSource);
				}
				if (!resolvedReaderType && sourceDataSourceId) {
					resolvedReaderType = GENERIC_JDBC_READER;
				}
			}
			if (resolvedReaderType) {
				values.readerType = resolvedReaderType;
			}
			const isJsonMode = values.editorMode === "json";
			const safeParse = (raw: string, label: string) => {
				if (!normalizeText(raw)) return undefined;
				try {
					return parseJson(raw, label) as Record<string, any>;
				} catch {
					toast.warning(`${label} 格式不完整，已忽略该部分内容`);
					return undefined;
				}
			};
			let readerConfig: Record<string, any> | undefined;
			let writerConfig: Record<string, any> | undefined;
			if (isFileDraft && fileUploadResult) {
				readerConfig = {
					_filePath: fileUploadResult.hostPath,
					_containerPath: fileUploadResult.containerPath,
					_keyVersion: fileUploadResult.keyVersion,
					_encrypted: fileUploadResult.encrypted,
					_fileHash: fileUploadResult.fileHash,
					_fileSize: fileUploadResult.fileSize,
					_fileType: fileUploadResult.fileType || "csv",
					_fileColumns: fileUploadResult.columns,
					_originalName: fileUploadResult.originalName,
					_autoId: Boolean(values?.fileAutoId ?? true),
				};
			} else if (isApiDraft) {
				readerConfig = buildApiReaderConfig(values);
			} else if (isJsonMode) {
				readerConfig = safeParse(values.readerConfig, "Reader 配置");
				writerConfig = safeParse(values.writerConfig, "Writer 配置");
			} else {
				try {
					readerConfig = buildReaderConfig(values);
				} catch {
					toast.warning("Reader 配置尚未完整，已忽略该部分内容");
				}
				try {
					writerConfig = buildWriterConfig(values);
				} catch {
					toast.warning("Writer 配置尚未完整，已忽略该部分内容");
				}
			}
			readerConfig = applyReaderTypeToConfig(readerConfig, resolvedReaderType);
			const jobConfig = safeParse(values.jobConfig, "作业参数");
			const selectedTables = mergeTableSelections(
				values.selectedTables,
				selectedTableKeysRef.current,
				resolveSelectedTables(values)
			);
			let selectionMode = normalizeText(values.tableSelectionMode) || "all";
			if (isApiDraft) {
				selectionMode = "manual";
			} else if (selectedTables.length) {
				selectionMode = "manual";
			}
			const apiResourceId = normalizeText((readerConfig?.resource as any)?.resourceId);
			const includeTables =
				isApiDraft
					? apiResourceId
						? [apiResourceId]
						: []
					: selectionMode === "manual"
					? mergeTableSelections(selectedTables, selectedTableKeysRef.current)
					: [];
			const excludeTables = !isApiDraft && selectionMode === "all" ? splitLines(values.tableExclude) : [];
			if (!isApiDraft && selectionMode === "manual" && includeTables.length) {
				readerConfig = applyTablesToConfig(readerConfig, includeTables);
				if (shouldApplyWriterTables(writerConfig, values)) {
					writerConfig = applyTablesToConfig(writerConfig, includeTables);
				}
			}
			const draftPayload = buildTransformCreateDraftPayload({
				name,
				description: normalizeText(values.description) || undefined,
				owner: userInfo?.username || userInfo?.login,
				isFileDraft,
				sourceDataSourceId,
				resolvedReaderType: resolvedReaderType || undefined,
				readerConfig: readerConfig || {},
				writerConfig,
				syncMode: normalizeText(values.syncMode) || "full_refresh",
				syncSchedule: buildSyncScheduleSpec(values),
				syncPrefix: normalizeText(values.syncPrefix) || undefined,
				incrementalColumn: normalizeText(values.incrementalColumn) || undefined,
				incrementalType: normalizeText(values.incrementalType) || undefined,
				initialWatermark: normalizeText(values.initialWatermark) || undefined,
				governanceSyncFields: buildGovernanceSyncFields(values),
				selectionMode,
				includeTables,
				excludeTables,
				readerSchema: normalizeText(values.readerSchema) || undefined,
				readerTablePattern: normalizeText(values.readerTablePattern) || undefined,
				airflowEnabled: isApiDraft ? false : values.airflowEnabled ?? true,
				dbtModelSelector: isApiDraft ? undefined : normalizeText(values.dbtModelSelector) || undefined,
				dbtDagSelector: isApiDraft ? undefined : normalizeText(values.dbtDagSelector) || undefined,
				jobConfig: jobConfig || undefined,
			});
			const draftResult: any = await createIngestionTask(draftPayload);
			const taskId =
				draftResult?.task?.id ?? draftResult?.taskId ?? draftResult?.task?.taskId ?? undefined;
			clearDraft();
			setHasDraft(false);
			toast.success("草稿已保存");
			if (taskId) {
				router.push(`/explore/etl/transform/${taskId}/edit`);
			}
		} catch (err: any) {
			if (values && saveDraft(values)) {
				setHasDraft(true);
			}
			toast.error(err?.message || "保存草稿失败");
		} finally {
			setSavingDraft(false);
		}
	};

	const parseFile = async (
		fileId: string,
		fileName: string,
		batchCode: string,
		sheets: Array<{ index: number; name: string }> | undefined,
		selectedSheet?: { index?: number; name?: string },
		previewLimit: number = filePreviewRows
	) => {
		const sheetIndex = selectedSheet?.index;
		const sheetName = selectedSheet?.name;
		const parseResult = await ingestionTaskAPI.parseUploadedFileById({
			fileId,
			previewLimit,
			sheetIndex,
			sheetName,
		});
		return {
			...parseResult,
			fileId: parseResult.fileId || fileId,
			batchCode: parseResult.batchCode || batchCode || fileId,
			originalName: parseResult.originalName || fileName,
			sheets: parseResult.sheets && parseResult.sheets.length ? parseResult.sheets : sheets || [],
			sheetIndex: parseResult.sheetIndex ?? sheetIndex,
			sheetName: parseResult.sheetName || sheetName,
		};
	};

	// --- ODS 表关联 ---
	const loadOdsTables = useCallback(async () => {
		if (!lakeDatasourceId) return;
		try {
			setOdsTableLoading(true);
			const tables = await sqlListTables(lakeDatasourceId);
			const odsTables = (Array.isArray(tables) ? tables : []).filter(
				t => t.name && !t.name.toLowerCase().startsWith("pg_")
			);
			setOdsTableList(odsTables);
		} catch (err: any) {
			console.error("Failed to load ODS tables:", err);
			setOdsTableList([]);
		} finally {
			setOdsTableLoading(false);
		}
	}, [lakeDatasourceId]);

	const handleOdsTableSelect = useCallback(async (tableName: string | undefined) => {
		setSelectedOdsTable(tableName);
		setOdsColumns([]);
		setOdsMatchApplied(false);
		if (!tableName || !lakeDatasourceId) return;
		try {
			setOdsColumnsLoading(true);
			const tableInfo = odsTableList.find(t => t.name === tableName);
			const schema = tableInfo?.schema || "public";
			const cols = await sqlListColumns(lakeDatasourceId, schema, tableName);
			setOdsColumns(Array.isArray(cols) ? cols : []);
		} catch (err: any) {
			console.error("Failed to load ODS columns:", err);
			setOdsColumns([]);
		} finally {
			setOdsColumnsLoading(false);
		}
	}, [lakeDatasourceId, odsTableList]);

	const applyOdsMapping = useCallback(() => {
		if (!odsColumns.length || !fileUploadResult?.columns?.length) return;
		const bizOdsColumns = filterBusinessFileMappingColumns(odsColumns);
		if (!bizOdsColumns.length) return;
		const excelCols = [...fileUploadResult.columns];
		const odsLen = bizOdsColumns.length;
		const excelLen = excelCols.length;
		for (let i = 0; i < Math.min(odsLen, excelLen); i++) {
			excelCols[i] = { ...excelCols[i], name: bizOdsColumns[i].name, _odsMatched: true } as any;
		}
		for (let i = odsLen; i < excelLen; i++) {
			excelCols[i] = { ...excelCols[i], _odsExtra: true } as any;
		}
		setFileUploadResult({ ...fileUploadResult, columns: excelCols });
		setOdsMatchApplied(true);
	}, [odsColumns, fileUploadResult]);

	const unmatchedOdsFields = useMemo(() => {
		if (!odsMatchApplied || !odsColumns.length) return [];
		const bizOdsColumns = filterBusinessFileMappingColumns(odsColumns);
		const excelLen = fileUploadResult?.columns?.length || 0;
		if (bizOdsColumns.length <= excelLen) return [];
		return bizOdsColumns.slice(excelLen);
	}, [odsMatchApplied, odsColumns, fileUploadResult?.columns?.length]);

	const refreshFilePreview = async () => {
		const parseInput = buildRefreshFileParseInput(fileUploadResult, filePreviewRows);
		if (!parseInput) {
			return;
		}
		try {
			setPreviewRefreshing(true);
			const parsed = await parseFile(
				parseInput.fileId,
				parseInput.fileName,
				parseInput.batchCode,
				parseInput.sheets,
				parseInput.selectedSheet,
				parseInput.previewLimit
			);
			setFileUploadResult(parsed);
			toast.success("预览已刷新");
		} catch {
			// handled by global interceptor
		} finally {
			setPreviewRefreshing(false);
		}
	};

	const openErrorPreview = async () => {
		if (!fileUploadResult?.fileId) {
			toast.error("缺少文件标识，无法查看错误行");
			return;
		}
		try {
			setErrorPreviewLoading(true);
			setErrorPreviewRows([]);
			setErrorPreviewOpen(true);
			toast.info("入湖链路当前不提供错误明细接口，请在任务执行日志中排查。");
		} catch {
			// handled by global interceptor
		} finally {
			setErrorPreviewLoading(false);
		}
	};

	const handleDiscoverTables = async () => {
		try {
			setDiscoverError("");
			setDiscoveringTables(true);
			const values = form.getFieldsValue(true);
			const dataSourceId = normalizeText(values.sourceDataSourceId);
			if (!dataSourceId) {
				throw new Error("请先选择数据源连接");
			}
			if (!isJdbcSource(selectedDataSource)) {
				throw new Error("当前数据源不支持表发现");
			}
			const schema = normalizeText(values.readerSchema);
			const tablePattern = normalizeText(values.readerTablePattern);
			const rawTables = await ingestionTaskAPI.discoverTables({
				source: {
					dataSourceId,
				},
				filter: {
					schema: schema || undefined,
					tablePattern: tablePattern || undefined,
					limit: 0,
				},
			});
			const tables = Array.isArray(rawTables) ? rawTables.map((item) => normalizeDiscoveredTable(item)) : [];
			setDiscoveredTables(tables);
			setSelectedTableKeys([]);
			form.setFieldValue("selectedTables", "");
			if (tables.length === 0) {
				setDiscoverError("未发现可用表");
			}
		} catch (err: any) {
			setDiscoverError(err?.message || "获取表清单失败");
		} finally {
			setDiscoveringTables(false);
		}
	};

	const handleApplyTables = () => {
		if (!selectedTableKeys.length) {
			toast.error("请先选择表");
			return;
		}
		syncSelectedTablesToForm(selectedTableKeys);
	};

	const readerTablesValidator = (_: any, value: string) => {
		const mode = normalizeText(form.getFieldValue("tableSelectionMode")) || "all";
		if (mode === "all") {
			return Promise.resolve();
		}
		if (resolveSelectedTables().length) {
			return Promise.resolve();
		}
		const tables = splitLines(value);
		if (tables.length) {
			return Promise.resolve();
		}
		return Promise.reject(new Error("请输入表名"));
	};

	const readerTypeValidator = (_: any, value: string) => {
		const direct = normalizeText(value);
		if (direct) {
			return Promise.resolve();
		}
		const dataSourceId = normalizeText(form.getFieldValue("sourceDataSourceId"));
		if (!dataSourceId) {
			return Promise.reject(new Error("请选择数据源连接"));
		}
		const inferred = resolveReaderTypeFromDataSource(selectedDataSource);
		if (inferred) {
			form.setFieldValue("readerType", inferred);
			return Promise.resolve();
		}
		if (!selectedDataSource && dataSourceId) {
			form.setFieldValue("readerType", GENERIC_JDBC_READER);
			return Promise.resolve();
		}
		if (selectedDataSource && isJdbcSource(selectedDataSource)) {
			form.setFieldValue("readerType", GENERIC_JDBC_READER);
			return Promise.resolve();
		}
		form.setFieldValue("readerType", GENERIC_JDBC_READER);
		return Promise.resolve();
	};

	const resolveStepFields = (stepIndex: number, values: Record<string, any>) => {
		const isJsonMode = values?.editorMode === "json";
		if (values?.sourceCategory === "file") {
			switch (stepIndex) {
				case 0:
					return ["name", "ownerDept"];
				case 1:
					return ["airflowEnabled", "runNow"];
				default:
					return [];
			}
		}
		if (values?.sourceCategory === "api") {
			switch (stepIndex) {
				case 0:
					return ["name", "ownerDept", "sourceDataSourceId", "readerType", "apiResourcePath", "apiMethod"];
				case 1:
					return [];
				default:
					return [];
			}
		}
		switch (stepIndex) {
			case 0:
				return isJsonMode
					? ["name", "ownerDept", "sourceDataSourceId", "readerType", "readerConfig"]
					: ["name", "ownerDept", "sourceDataSourceId", "readerType"];
			case 1:
				return ["airflowEnabled", "runNow"];
			default:
				return [];
		}
	};

	const validateStep = async (stepIndex: number) => {
		const values = form.getFieldsValue(true);
		const fields = resolveStepFields(stepIndex, values);
		if (!fields.length) return true;
		try {
			await form.validateFields(fields);
			return true;
		} catch {
			return false;
		}
	};

	const handleStepChange = async (nextStep: number) => {
		if (nextStep <= currentStep) {
			setCurrentStep(nextStep);
			return;
		}
		const ok = await validateStep(currentStep);
		if (!ok) {
			toast.error("请先完成当前步骤必填项");
			return;
		}
		setCurrentStep(nextStep);
	};

	const handleNextStep = async () => {
		if (currentStep >= stepItems.length - 1) return;
		const vals = form.getFieldsValue(true);
		const fileFlow = vals.sourceCategory === "file";
		if (fileFlow) {
			if (currentStep === 0) {
				if (!normalizeText(vals.name)) {
					toast.error("请输入任务名称");
					return;
				}
				if (!fileUploadResult) {
					toast.error("请先上传文件");
					return;
				}
			}
			setCurrentStep((prev) => prev + 1);
			return;
		}
		const ok = await validateStep(currentStep);
		if (!ok) {
			toast.error("请先完成当前步骤必填项");
			return;
		}
		setCurrentStep((prev) => prev + 1);
	};

	const handlePrevStep = () => {
		setCurrentStep((prev) => Math.max(prev - 1, 0));
	};

	const handleSubmit = async (values: any) => {
		if (submittedTaskId) {
			toast.info("任务已提交，请勿重复提交");
			return;
		}
		try {
			setSaving(true);
			const mergedValues = { ...form.getFieldsValue(true), ...(values || {}) };
			let taskName = resolveTaskName(mergedValues, form);
			if (!taskName && selectedDataSource) {
				taskName = normalizeText(selectedDataSource.name);
			}
			if (!taskName) {
				throw new Error("请填写任务名称");
			}
			mergedValues.name = taskName;
			const isFileSource = mergedValues.sourceCategory === "file" && fileUploadResult;
			const isApiSource = mergedValues.sourceCategory === "api";
			const sourceDataSourceId = normalizeText(mergedValues.sourceDataSourceId);
			if (!isFileSource && !sourceDataSourceId) {
				throw new Error("请选择数据源连接");
			}
			if (isFileSource && !fileUploadResult) {
				throw new Error("请先上传文件");
			}
			if (isApiSource) {
				const readerConfig = buildApiReaderConfig(mergedValues);
				applyReaderTypeToConfig(readerConfig, "httpreader");
				const apiResource = (readerConfig.resource || {}) as Record<string, any>;
				const apiResourceId = normalizeText(apiResource.resourceId) || "api_resource";
				const apiSyncConfig = buildSyncConfigFromValues(mergedValues, false);
				const apiAirflowEnabled = mergedValues.airflowEnabled ?? editingTask?.airflowEnabled ?? true;
				if (isEdit && editId) {
					const governanceSyncFields = buildGovernanceSyncFields(mergedValues);
					const updatePayload: IngestionTaskDTO = {
						...(editingTask || {}),
						id: editId,
						name: taskName,
						description: normalizeText(mergedValues.description) || undefined,
						sourceType: "httpreader",
						sourceDataSourceId,
						sourceConfig: readerConfig,
						destinationType: undefined,
						destinationConfig: undefined,
						syncMode: mergedValues.syncMode || editingTask?.syncMode || "full_refresh",
						syncSchedule: buildSyncScheduleText(mergedValues),
						syncConfig: {
							...(apiSyncConfig || {}),
							...(Object.keys(governanceSyncFields).length ? { governance: governanceSyncFields } : {}),
						},
						syncPrefix: undefined,
						addaxJobPath: undefined,
						addaxConfig: undefined,
						airflowEnabled: Boolean(apiAirflowEnabled),
						airflowDagId: editingTask?.airflowDagId,
						tableMapping: [{ source: apiResourceId, target: normalizeText(apiResource.targetTable) || `ods_api_${apiResourceId}` }],
						dbtModelSelector: undefined,
						dbtDagSelector: undefined,
					};
					await ingestionTaskAPI.updateTask(editId, updatePayload);
					toast.success("API 入湖任务已更新");
					router.push(`/explore/etl/transform/${editId}`);
				} else {
					const payload = {
						taskName,
						name: taskName,
						description: normalizeText(mergedValues.description) || undefined,
						owner: userInfo?.username || userInfo?.login,
						ownerDept: normalizeText(mergedValues.ownerDept) || undefined,
						source: {
							dataSourceId: sourceDataSourceId,
							type: "httpreader",
							config: readerConfig,
						},
						sync: {
							mode: normalizeText(mergedValues.syncMode) || "full_refresh",
							schedule: buildSyncScheduleSpec(mergedValues),
							incrementalColumn: apiSyncConfig?.incrementalColumn,
							incrementalType: apiSyncConfig?.incrementalType,
							initialWatermark: apiSyncConfig?.initialWatermark,
							...buildGovernanceSyncFields(mergedValues),
						},
						streams: {
							selection: "manual",
							include: [apiResourceId],
						},
						airflow: { enabled: Boolean(apiAirflowEnabled) },
						runNow: Boolean(mergedValues.runNow),
					};
					const createResult = await createIngestionTask(payload);
					handleCreateTaskResult(createResult, Boolean(payload.runNow), taskName);
				}
				return;
			}
			const syncConfig = buildSyncConfigFromValues(mergedValues, Boolean(isFileSource));
			let resolvedReaderType = normalizeReaderType(mergedValues.readerType);
			if (isFileSource) {
				resolvedReaderType = "txtfilereader";
			} else {
				if (!resolvedReaderType) {
					resolvedReaderType = resolveReaderTypeFromValues(mergedValues);
				}
				if (!resolvedReaderType) {
					resolvedReaderType = resolveReaderTypeFromSourceId(sourceDataSourceId, dataSources, selectedDataSource);
				}
				if (!resolvedReaderType && sourceDataSourceId) {
					resolvedReaderType = GENERIC_JDBC_READER;
				}
			}
			if (resolvedReaderType) {
				mergedValues.readerType = resolvedReaderType;
			}
			const airflowEnabled = mergedValues.airflowEnabled ?? editingTask?.airflowEnabled ?? true;
			const isJsonMode = mergedValues.editorMode === "json";
			let readerConfig: Record<string, any>;
			if (isFileSource) {
				readerConfig = {
					_filePath: fileUploadResult.hostPath,
					_containerPath: fileUploadResult.containerPath,
					_keyVersion: fileUploadResult.keyVersion,
					_encrypted: fileUploadResult.encrypted,
					_fileHash: fileUploadResult.fileHash,
					_fileSize: fileUploadResult.fileSize,
					_fileType: fileUploadResult.fileType || "csv",
					_fileColumns: fileUploadResult.columns,
					_originalName: fileUploadResult.originalName,
					_autoId: Boolean(mergedValues?.fileAutoId ?? true),
				};
			} else {
				readerConfig = (isJsonMode
					? parseJson(mergedValues.readerConfig, "Reader 配置")
					: buildReaderConfig(mergedValues)) as Record<string, any>;
				applyReaderTypeToConfig(readerConfig, resolvedReaderType);
				if (hasConnectionOverride(readerConfig)) {
					throw new Error("入湖任务必须使用已配置的数据源连接，Reader 配置中不可包含连接信息");
				}
			}
			if (!defaultDestinationStatus?.available) {
				throw new Error("默认数据湖未配置，请先在管理端设置默认数据湖");
			}
			if (!defaultDestinationStatus.writerTypeReady) {
				throw new Error("默认数据湖未配置写入器类型");
			}
			if (!defaultDestinationStatus.writerConfigReady) {
				throw new Error("默认数据湖未配置写入器参数");
			}
			const defaultWriterType = normalizeText(defaultDestinationStatus.writerType);
			if (!defaultWriterType) {
				throw new Error("默认数据湖写入器类型不可用");
			}
			const sourceSystem = normalizeText(mergedValues.sourceSystem);
			if (sourceSystem && readerConfig && typeof readerConfig === "object" && !readerConfig.sourceSystem) {
				readerConfig.sourceSystem = sourceSystem;
			}
			if (isFileSource) {
				const baseName = buildFileBaseName(fileUploadResult.originalName);
				const fileTableInput = normalizeText(mergedValues.fileTableName);
				const requestedFileTable = normalizeTableName(fileTableInput);
				if (fileTableInput && !requestedFileTable) {
					throw new Error("目标表名格式不合法，仅支持字母、数字、下划线，可包含 schema");
				}
				const autoTableName = normalizeTableName(`${normalizeText(mergedValues.syncPrefix)}${baseName}`);
				const fileTableName = requestedFileTable || autoTableName || `${normalizeText(mergedValues.syncPrefix)}${baseName}`;
				if (!fileTableName) {
					throw new Error("请填写目标表名");
				}
				const fileIncludeTables = [fileTableName];
				const writerConfig: Record<string, any> = {};
				const fileWriterUsername = normalizeText(mergedValues.writerUsername);
				const fileWriterPassword = normalizeText(mergedValues.writerPassword);
				const fileWriterSchema = normalizeText(mergedValues.writerSchema);
				const fileWriterJdbc = normalizeText(mergedValues.writerJdbcUrls);
				if (fileWriterUsername) writerConfig.username = fileWriterUsername;
				if (fileWriterPassword) writerConfig.password = fileWriterPassword;
				if (fileWriterSchema) writerConfig.schema = fileWriterSchema;
				const fileConn: Record<string, any> = { table: [fileTableName] };
				if (fileWriterJdbc) {
					fileConn.jdbcUrl = splitLines(fileWriterJdbc);
				}
				writerConfig.connection = [fileConn];
				const jobConfig = parseJson(mergedValues.jobConfig, "作业参数");
				const modelSelector = normalizeText(mergedValues.dbtModelSelector) || buildModelSelectorFromNames(mergedValues.dbtModels || []);
				const dagSelector = normalizeText(mergedValues.dbtDagSelector);
				if (isEdit && editId) {
					const updatePayload: IngestionTaskDTO = {
						...(editingTask || {}),
						id: editId,
						name: taskName,
						description: normalizeText(mergedValues.description) || undefined,
						sourceType: resolvedReaderType,
						sourceConfig: readerConfig,
						destinationType: defaultWriterType,
						destinationConfig: writerConfig,
						syncMode: "full_refresh",
						syncSchedule: buildSyncScheduleText(mergedValues),
						syncConfig: (syncConfig ?? null) as any,
						syncPrefix: normalizeText(mergedValues.syncPrefix) || undefined,
						addaxConfig: (jobConfig as Record<string, any>) || editingTask?.addaxConfig,
						airflowEnabled: Boolean(airflowEnabled),
						tableMapping: [{ source: baseName, target: fileTableName }],
						dbtModelSelector: modelSelector || undefined,
						dbtDagSelector: dagSelector || undefined,
					};
					await ingestionTaskAPI.updateTask(editId, updatePayload);
					toast.success("入湖任务已更新");
					router.push(`/explore/etl/transform/${editId}`);
				} else {
					const payload = {
						taskName,
						name: taskName,
						description: normalizeText(mergedValues.description) || undefined,
						owner: userInfo?.username || userInfo?.login,
						ownerDept: normalizeText(mergedValues.ownerDept) || undefined,
						source: {
							type: resolvedReaderType,
							config: readerConfig,
						},
						destination: {
							usePlatformDefault: true,
							type: defaultWriterType,
							config: writerConfig,
						},
							sync: {
								mode: "full_refresh",
								schedule: buildSyncScheduleSpec(mergedValues),
								prefix: normalizeText(mergedValues.syncPrefix) || undefined,
								...buildGovernanceSyncFields(mergedValues),
							},
						streams: {
							selection: "manual",
							include: fileIncludeTables,
						},
						airflow: { enabled: airflowEnabled },
						dbt: {
							modelSelector: modelSelector || undefined,
							dagSelector: dagSelector || undefined,
						},
						runNow: Boolean(mergedValues.runNow),
						jobConfig: jobConfig || undefined,
					};
					const createResult = await createIngestionTask(payload);
					handleCreateTaskResult(createResult, Boolean(payload.runNow), taskName);
				}
				return;
			}
			const selectedTables = mergeTableSelections(
				mergedValues.selectedTables,
				selectedTableKeysRef.current,
				resolveSelectedTables(mergedValues)
			);
			let selectionMode = normalizeText(mergedValues.tableSelectionMode) || "all";
			if (selectedTables.length || selectedTableKeysRef.current.length) {
				selectionMode = "manual";
				mergedValues.tableSelectionMode = "manual";
			}
			let writerConfig = isJsonMode
				? parseJson(mergedValues.writerConfig, "Writer 配置")
				: buildWriterConfig(mergedValues);
			const inferredManualTables = mergeTableSelections(
				selectedTables,
				extractReaderTables(readerConfig),
				extractWriterTables(writerConfig),
				mergedValues.readerTables,
				mergedValues.writerTables
			);
			if (selectionMode === "manual" && !selectedTables.length && inferredManualTables.length) {
				mergedValues.selectedTables = inferredManualTables.join("\n");
			}
			if (selectionMode === "all" && inferredManualTables.length) {
				selectionMode = "manual";
				mergedValues.tableSelectionMode = "manual";
			}
			let includeTables: string[] = [];
			if (selectionMode === "manual") {
				includeTables = mergeTableSelections(selectedTables, selectedTableKeysRef.current, inferredManualTables);
				if (!includeTables.length) {
					includeTables = mergeTableSelections(selectedTableKeysRef.current, selectedTableKeys);
				}
				if (!includeTables.length) {
					if (isJsonMode) {
						includeTables = extractReaderTables(readerConfig);
					} else {
						includeTables = splitLines(mergedValues.readerTables);
					}
				}
				if (!includeTables.length) {
					includeTables = extractWriterTables(writerConfig);
				}
				if (!includeTables.length && !isJsonMode) {
					includeTables = splitLines(mergedValues.writerTables);
				}
				if (!includeTables.length) {
					throw new Error("请选择需要入湖的表");
				}
					readerConfig = applyTablesToConfig((readerConfig as Record<string, any>) ?? {}, includeTables) ?? {};
					if (shouldApplyWriterTables((writerConfig as Record<string, any>) ?? {}, mergedValues)) {
						const explicitWriterTables = splitLines(mergedValues.writerTables);
						const normalizedExplicitWriterTables =
							includeTables.length && isSourceAlignedTables(includeTables, explicitWriterTables)
								? applyPrefixToTables(includeTables, mergedValues.syncPrefix)
								: explicitWriterTables;
						const existingWriterTables = extractWriterTables(writerConfig as Record<string, any>);
						const derivedWriterTables = normalizedExplicitWriterTables.length
							? normalizedExplicitWriterTables
							: (!existingWriterTables.length || isSourceAlignedTables(includeTables, existingWriterTables))
								? applyPrefixToTables(includeTables, mergedValues.syncPrefix)
								: existingWriterTables;
						writerConfig = applyTablesToConfig((writerConfig as Record<string, any>) ?? {}, derivedWriterTables) ?? {};
					}
				}
			const excludeTables = selectionMode === "all" ? splitLines(mergedValues.tableExclude) : [];
			if (writerConfig && selectionMode !== "all" && !hasTableEntries(extractWriterTables(writerConfig))) {
				throw new Error("Writer 配置缺少目标表，请填写表清单");
			}
			const jobConfig = parseJson(mergedValues.jobConfig, "作业参数");
			const modelSelector = normalizeText(mergedValues.dbtModelSelector) || buildModelSelectorFromNames(mergedValues.dbtModels || []);
			const dagSelector = normalizeText(mergedValues.dbtDagSelector);
			if (modelSelector && !dagSelector) {
				toast.warning("未填写 DAG 族选择器，将使用默认 DAG 触发 dbt");
			}
			if (isEdit && editId) {
				const updatePayload: IngestionTaskDTO = {
					...(editingTask || {}),
					id: editId,
					name: taskName,
					description: normalizeText(mergedValues.description) || undefined,
					sourceType: normalizeText(resolvedReaderType) || "",
					sourceDataSourceId: sourceDataSourceId,
					sourceConfig: (readerConfig as Record<string, any>) || {},
						destinationType: defaultWriterType,
						destinationConfig: writerConfig as Record<string, any> | undefined,
						syncMode: mergedValues.syncMode || editingTask?.syncMode || "full_refresh",
						syncSchedule: buildSyncScheduleText(mergedValues),
						syncConfig: (syncConfig ?? null) as any,
						syncPrefix: normalizeText(mergedValues.syncPrefix) || undefined,
					addaxConfig: (jobConfig as Record<string, any>) || editingTask?.addaxConfig,
					airflowEnabled: Boolean(mergedValues.airflowEnabled),
					dbtModelSelector: modelSelector || undefined,
					dbtDagSelector: dagSelector || undefined,
				};
				updatePayload.airflowEnabled = airflowEnabled;
				await ingestionTaskAPI.updateTask(editId, updatePayload);
				toast.success("入湖任务已更新");
				router.push(`/explore/etl/transform/${editId}`);
			} else {
				const payload = {
					taskName: taskName,
					name: taskName,
					description: normalizeText(mergedValues.description) || undefined,
					owner: userInfo?.username || userInfo?.login,
					ownerDept: normalizeText(mergedValues.ownerDept) || undefined,
					source: {
						dataSourceId: isFileSource ? undefined : sourceDataSourceId,
						type: normalizeText(resolvedReaderType) || undefined,
						config: readerConfig || {},
					},
					destination: {
						usePlatformDefault: true,
						type: defaultWriterType,
						config: writerConfig || undefined,
					},
						sync: {
							mode: mergedValues.syncMode || "full_refresh",
							schedule: buildSyncScheduleSpec(mergedValues),
							prefix: normalizeText(mergedValues.syncPrefix) || undefined,
							incrementalColumn: syncConfig?.incrementalColumn,
							incrementalType: syncConfig?.incrementalType,
							initialWatermark: syncConfig?.initialWatermark,
							...buildGovernanceSyncFields(mergedValues),
						},
					streams: {
						selection: selectionMode,
						include: selectionMode === "manual" ? includeTables : undefined,
						exclude: selectionMode === "all" ? excludeTables : undefined,
						schema: normalizeText(mergedValues.readerSchema) || undefined,
						tablePattern: normalizeText(mergedValues.readerTablePattern) || undefined,
					},
					airflow: {
						enabled: airflowEnabled,
					},
					dbt: {
						modelSelector: modelSelector || undefined,
						dagSelector: dagSelector || undefined,
					},
					runNow: Boolean(mergedValues.runNow),
					jobConfig: jobConfig || undefined,
				};
				const createResult = await createIngestionTask(payload);
				handleCreateTaskResult(createResult, Boolean(payload.runNow), taskName);
			}
		} catch {
			// handled by global interceptor
		} finally {
			setSaving(false);
		}
	};

	const previewState = useMemo(() => {
		const snapshot = form.getFieldsValue(true);
		const mergedValues = { ...snapshot, ...(formValues || {}) } as Record<string, any>;
		const isFilePreview = mergedValues.sourceCategory === "file" && fileUploadResult;
		if (isFilePreview) {
			const fileTableName = normalizeTableName(mergedValues.fileTableName) ||
				normalizeTableName(`${normalizeText(mergedValues.syncPrefix)}${buildFileBaseName(fileUploadResult.originalName)}`);
			if (fileTableName) {
				mergedValues.writerTables = fileTableName;
			}
			mergedValues.readerType = "txtfilereader";
			mergedValues.readerConfig = JSON.stringify(
				{
					_filePath: fileUploadResult.hostPath,
					_containerPath: fileUploadResult.containerPath,
					_keyVersion: fileUploadResult.keyVersion,
					_encrypted: fileUploadResult.encrypted,
					_fileHash: fileUploadResult.fileHash,
					_fileSize: fileUploadResult.fileSize,
					_fileType: fileUploadResult.fileType || "csv",
					_fileColumns: fileUploadResult.columns,
					_originalName: fileUploadResult.originalName,
					_autoId: Boolean(mergedValues?.fileAutoId ?? true),
				},
				null,
				2
			);
		}
		if (mergedValues.sourceCategory === "api") {
			try {
				const readerConfig = buildApiReaderConfig(mergedValues);
				return {
					config: {
						source: {
							dataSourceId: normalizeText(mergedValues.sourceDataSourceId) || undefined,
							type: "httpreader",
							config: readerConfig,
						},
						sync: {
							mode: normalizeText(mergedValues.syncMode) || "full_refresh",
							schedule: buildSyncScheduleSpec(mergedValues),
						},
						airflow: { enabled: mergedValues.airflowEnabled ?? true },
						draft: false,
					},
					error: "",
				};
			} catch (error: any) {
				return { config: null, error: error?.message || "无法生成 API 预览" };
			}
		}
		if (!normalizeText(mergedValues.readerType) && selectedDataSource) {
			const inferredReader = resolveReaderTypeFromDataSource(selectedDataSource);
			if (inferredReader) {
				mergedValues.readerType = inferredReader;
			}
		}
		if (!normalizeText(mergedValues.writerType) && defaultDestinationStatus?.writerType) {
			mergedValues.writerType = normalizeText(defaultDestinationStatus.writerType);
		}
		try {
			const readerFallback = resolveReaderTypeFromSourceId(
				mergedValues.sourceDataSourceId,
				dataSources,
				selectedDataSource
			);
			const config = buildJobPreview(mergedValues, isFilePreview ? "json" : editorMode, readerFallback);
			return { config, error: "" };
		} catch (error: any) {
			return { config: null, error: error?.message || "无法生成预览" };
		}
	}, [
		formValues,
		editorMode,
		form,
		currentStep,
		selectedDataSource,
		defaultDestinationStatus,
		fileUploadResult,
		dataSources,
	]);

	return (
		<div className="flex flex-col gap-6">
			<PageHeader
				title={isEdit ? "编辑入湖任务" : "创建入湖任务"}
				actions={
					<Space>
						<Button
							onClick={() => {
								if (isEdit && editingTask) {
									const mapped = mapTaskToForm(editingTask);
									form.setFieldsValue(mapped);
									setSourceCategory(mapped.sourceCategory || "database");
								} else {
									form.resetFields();
									setSourceCategory("database");
								}
								setCurrentStep(0);
							}}
						>
							重置表单
						</Button>
						<Button
							loading={savingDraft}
							onClick={handleSaveDraft}
							disabled={isEdit || !!submittedTaskId}
						>
							保存草稿{hasDraft ? " ✓" : ""}
						</Button>
					</Space>
				}
			/>
			<Card>
				<Alert
					message="提示"
					description="入湖任务将使用管理端默认数据湖；请填写 Reader 配置与 Writer 覆盖参数（表名必填）。"
					type="info"
					showIcon
					className="mb-6"
				/>
				<Form
					form={form}
					layout="vertical"
					initialValues={initialValues}
					onFinish={handleSubmit}
					requiredMark
				>
					<Steps
						current={currentStep}
						items={stepItems}
						onChange={handleStepChange}
						className="mb-6"
					/>
					<Form.Item name="selectedTables" hidden>
						<Input type="hidden" />
					</Form.Item>
					<Form.Item name="writerType" hidden rules={[{ required: true, message: "请选择 Writer 类型" }]}>
						<Input type="hidden" />
					</Form.Item>
					{currentStep === 0 ? (
						<Card size="small" className="mb-4" title="快速模板">
							<Space wrap>
								<Select
									style={{ minWidth: 280 }}
									placeholder="选择模板"
									loading={loadingTaskTemplates}
									value={selectedTemplateId}
									options={taskTemplates.map((item) => ({
										label: `${item.name}${item.version ? ` (v${item.version})` : ""}`,
										value: item.id,
									}))}
									onChange={(value) => setSelectedTemplateId(value)}
								/>
								<Button onClick={() => void applyTemplate()} disabled={!selectedTemplateId} loading={applyingTemplate}>
									应用模板
								</Button>
								{selectedTemplateId ? (
									<Text type="secondary">
										{taskTemplates.find((item) => String(item.id) === String(selectedTemplateId))?.description || ""}
									</Text>
								) : null}
							</Space>
						</Card>
					) : null}
					{isFileFlow ? (
						<>
							{/* ===== 文件上传模式（精简 2 步） ===== */}
							{currentStep === 0 && (
								<FileUnifiedStep
									form={form}
									fileUploadResult={fileUploadResult}
									setFileUploadResult={setFileUploadResult}
									odsColumns={odsColumns}
									odsMatchApplied={odsMatchApplied}
									defaultDestinationStatus={defaultDestinationStatus}
									syncModeOptions={syncModeOptions}
									scheduleType={scheduleType}
									activeCapabilitySet={activeCapabilitySet}
									capabilityLoadFailed={capabilityLoadFailed}
									onSourceCategoryChange={handleSourceCategoryChange}
									uploadingFile={uploadingFile}
									onFileUpload={async (file, onSuccess, onError) => {
										try {
											setUploadingFile(true);
											const prevColumns = fileUploadResult?.columns;
											setFileUploadResult(null);
											const parsed = await ingestionTaskAPI.uploadAndParseFile(file, {
												previewLimit: filePreviewRows,
											});
											const outcome = buildFilePostParseOutcome({
												parsed,
												currentFileTableName: form.getFieldValue("fileTableName"),
												syncPrefix: form.getFieldValue("syncPrefix"),
												reason: "upload",
											});
											// 编辑模式：复用已保存的字段映射，不清空
											if (isEdit && prevColumns?.length && parsed.columns?.length) {
												const prevByLabel = new Map<string, any>();
												for (const col of prevColumns) {
													const label = ((col as any).label || '').trim().toLowerCase();
													if (label) prevByLabel.set(label, col);
												}
												parsed.columns = parsed.columns.map((newCol: any) => {
													const newLabel = (newCol.label || newCol.name || '').trim().toLowerCase();
													const prev = prevByLabel.get(newLabel);
													if (prev?.name && (prev as any)._odsMatched) {
														return { ...newCol, name: prev.name, _odsMatched: true };
													}
													return newCol;
												});
											}
											setFileUploadResult(parsed);
											if (outcome.shouldResetOds) {
												setSelectedOdsTable(undefined); setOdsColumns([]); setOdsMatchApplied(false);
											}
											form.setFieldValue("readerType", outcome.readerType);
											if (outcome.suggestedFileTableName) {
												form.setFieldValue("fileTableName", outcome.suggestedFileTableName);
											}
											onSuccess?.(parsed);
											toast.success(outcome.successMessage);
										} catch (err: any) {
											onError?.(err);
										} finally {
											setUploadingFile(false);
										}
									}}
									onSheetChange={async (value) => {
										const parseInput = buildSheetChangeFileParseInput(fileUploadResult, value, filePreviewRows);
										if (!parseInput) return;
										try {
											setUploadingFile(true);
											const parsed = await parseFile(
												parseInput.fileId,
												parseInput.fileName,
												parseInput.batchCode,
												parseInput.sheets,
												parseInput.selectedSheet,
												parseInput.previewLimit
											);
											const outcome = buildFilePostParseOutcome({
												parsed,
												currentFileTableName: form.getFieldValue("fileTableName"),
												syncPrefix: form.getFieldValue("syncPrefix"),
												reason: "sheet-change",
												sheetName: parseInput.selectedSheet?.name,
											});
											setFileUploadResult(parsed);
											if (outcome.shouldResetOds) {
												setSelectedOdsTable(undefined); setOdsColumns([]); setOdsMatchApplied(false);
											}
											form.setFieldValue("readerType", outcome.readerType);
											if (outcome.suggestedFileTableName) {
												form.setFieldValue("fileTableName", outcome.suggestedFileTableName);
											}
											toast.success(outcome.successMessage);
										} catch {
										} finally {
											setUploadingFile(false);
										}
									}}
									filePreviewRows={filePreviewRows}
									setFilePreviewRows={setFilePreviewRows}
									filePreviewCols={filePreviewCols}
									setFilePreviewCols={setFilePreviewCols}
									refreshFilePreview={refreshFilePreview}
									previewRefreshing={previewRefreshing}
									openErrorPreview={openErrorPreview}
									errorPreviewLoading={errorPreviewLoading}
									lakeDatasourceId={lakeDatasourceId}
									odsTableLoading={odsTableLoading}
									selectedOdsTable={selectedOdsTable}
									odsTableList={odsTableList.map(t => ({ label: t.name, value: t.name }))}
									loadOdsTables={loadOdsTables}
									handleOdsTableSelect={handleOdsTableSelect}
									odsColumnsLoading={odsColumnsLoading}
									applyOdsMapping={applyOdsMapping}
									unmatchedOdsFields={unmatchedOdsFields}
									validateCronExpression={validateCronExpression}
									deptOptions={deptOptions}
									loadingDeptOptions={loadingDeptOptions}
									extraColumns={extraColumns}
									setExtraColumns={setExtraColumns}
								/>
							)}
							{currentStep === 1 && (
								<UnifiedReviewStep
									form={form}
									isFileFlow={true}
									isApiFlow={false}
									defaultDestinationStatus={defaultDestinationStatus}
									extraColumns={extraColumns}
									editorMode={editorMode}
									previewState={previewState}
									sqlModels={sqlModels}
									loadingSqlModels={loadingSqlModels}
									onNavigateToModeling={() => router.push("/modeling/sql")}
									formValues={form.getFieldsValue(true)}
									loadingDefaultDestination={loadingDefaultDestination}
									defaultDestinationError={defaultDestinationError}
									tableMappingPreview={tableMappingPreview}
								/>
							)}
						</>
					) : (
						<>
							{/* ===== 数据库模式（精简 2 步） ===== */}
							{currentStep === 0 && (
								<DbUnifiedStep
									form={form}
									editorMode={editorMode}
									setEditorMode={(mode) => form.setFieldValue("editorMode", mode)}
									connectorCapability={activeConnectorCapability}
									syncModeOptions={syncModeOptions}
									sourceCategory={sourceCategory}
									setSourceCategory={setSourceCategory}
									onSourceCategoryChange={handleSourceCategoryChange}
									selectedTableKeys={selectedTableKeys}
									setSelectedTableKeys={setSelectedTableKeys}
									selectedDataSource={selectedDataSource ?? null}
									dataSources={dataSources}
									activeCapabilitySet={activeCapabilitySet}
									supportsIncremental={supportsIncremental}
									supportsCdc={supportsCdc}
									supportsBackfill={supportsBackfill}
									capabilityLoadFailed={capabilityLoadFailed}
									availableTables={discoveredTables}
									loadingTables={discoveringTables}
									discoveredTableKeys={discoveredTableKeys}
									discoverError={discoverError}
									loadingDataSources={loadingDataSources}
									onDiscoverTables={handleDiscoverTables}
									onApplyTables={handleApplyTables}
									syncSelectedTablesToForm={syncSelectedTablesToForm}
									readerTablesValidator={readerTablesValidator}
									readerTypeValidator={readerTypeValidator}
									deptOptions={deptOptions}
									loadingDeptOptions={loadingDeptOptions}
								/>
							)}
							{currentStep === 1 && (
								<UnifiedReviewStep
									form={form}
									isFileFlow={false}
									isApiFlow={isApiFlow}
									defaultDestinationStatus={defaultDestinationStatus}
									extraColumns={extraColumns}
									editorMode={editorMode}
									previewState={previewState}
									sqlModels={sqlModels}
									loadingSqlModels={loadingSqlModels}
									onNavigateToModeling={() => router.push("/modeling/sql")}
									formValues={form.getFieldsValue(true)}
									loadingDefaultDestination={loadingDefaultDestination}
									defaultDestinationError={defaultDestinationError}
									tableMappingPreview={tableMappingPreview}
								/>
							)}
						</>
					)}
					<Divider />
					<Space>
						<Button onClick={handlePrevStep} disabled={currentStep === 0}>
							上一步
						</Button>
						{currentStep < stepItems.length - 1 ? (
							<Button type="primary" onClick={handleNextStep}>
								下一步
							</Button>
						) : (
							<Button type="primary" loading={saving} onClick={() => form.submit()} disabled={loadingTask}>
								{isApiFlow ? "保存 API 草稿" : isEdit ? "保存修改" : "提交任务"}
							</Button>
						)}
					</Space>
				</Form>
			</Card>
			<Modal
				title="错误行明细"
				open={errorPreviewOpen}
				onCancel={() => setErrorPreviewOpen(false)}
				footer={null}
				width={720}
			>
				<Space className="mb-3" wrap>
					<Text type="secondary">预览条数</Text>
					<InputNumber
						min={1}
						max={500}
						value={errorPreviewLimit}
						onChange={(value) => setErrorPreviewLimit(value ? Number(value) : 50)}
					/>
					<Button size="small" onClick={openErrorPreview} loading={errorPreviewLoading}>
						刷新
					</Button>
				</Space>
				<CompactTable
					size="small"
					rowKey={(record, index) => `${record?.rowIndex || "row"}-${index}`}
					pagination={false}
					loading={errorPreviewLoading}
					dataSource={errorPreviewRows || []}
					locale={{ emptyText: "暂无错误行" }}
					columns={[
						{ title: "行号", dataIndex: "rowIndex", width: 100 },
						{ title: "错误信息", dataIndex: "message", ellipsis: true },
					]}
				/>
			</Modal>
			<Modal
				title="任务启动进度"
				open={asyncRunModalOpen}
				onCancel={closeAsyncRunModal}
				maskClosable={false}
				footer={
					<Space>
						{asyncRunTaskId ? (
							<Button
								onClick={() => {
									closeAsyncRunModal();
									router.push(`/explore/etl/transform/${asyncRunTaskId}`);
								}}
							>
								查看任务详情
							</Button>
						) : null}
						{!asyncRunProgress.terminal ? (
							<Button
								onClick={() => {
									closeAsyncRunModal();
									router.push("/explore/etl/transform");
								}}
							>
								后台查看列表
							</Button>
						) : null}
						<Button type="primary" onClick={closeAsyncRunModal}>
							{asyncRunProgress.terminal ? "关闭" : "最小化"}
						</Button>
					</Space>
				}
				width={640}
			>
				<Space direction="vertical" size={16} className="w-full">
					<div className="flex items-center justify-between">
						<Text>任务：{asyncRunTaskName || "-"}</Text>
						{asyncRunExecution?.executionId ? (
							<Tag color="blue">执行ID: {asyncRunExecution.executionId}</Tag>
						) : null}
					</div>
					<Progress percent={asyncRunProgress.progress} status={asyncRunProgress.status} />
					<Alert
						type={
							asyncRunProgress.status === "success"
								? "success"
								: asyncRunProgress.status === "exception"
									? "error"
									: "info"
						}
						showIcon
						message={asyncRunProgress.stage}
						description={asyncRunProgress.detail}
					/>
					{asyncRunExecution ? (
						<Space size={8}>
							<Text type="secondary">最新状态：</Text>
							<Tag
								color={
									normalizeText(asyncRunExecution.status).toLowerCase() === "success"
										? "success"
										: normalizeText(asyncRunExecution.status).toLowerCase() === "failed"
											? "error"
											: "processing"
								}
							>
								{normalizeText(asyncRunExecution.status) || "running"}
							</Tag>
						</Space>
					) : null}
				</Space>
			</Modal>
		</div>
	);
}
