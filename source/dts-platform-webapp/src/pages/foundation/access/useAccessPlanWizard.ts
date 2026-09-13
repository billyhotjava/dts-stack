import type { FormInstance } from "antd/es/form";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
	type DefaultDestinationStatus,
	type IngestionTaskDTO,
	ingestionTaskAPI,
	type ManagedFileUploadResult,
} from "@/api/ingestion";
import { createIngestionTask } from "@/api/platformApi";
import dataSourcesService, { type DataSourceSelectionItem } from "@/api/services/dataSourcesService";
import { deriveDataLevels } from "@/constants/governance";
import { useUserInfo } from "@/store/userStore";
import { classificationRank, normalizeClassification } from "@/utils/classification";
import { normalizeText } from "@/utils/textUtils";
import {
	extractManagedFileFromTask,
	resolveManagedFileAdmissionState,
	restoreManagedFileAdmissionFromTask,
	toAdmissionFile,
	toManagedFile,
} from "./accessManagedFile";
import {
	ACCESS_KIND_LABELS,
	type AccessKind,
	type AccessPlanFormValues,
	type AccessPlanPayloadContext,
	type AccessPlanRuntimeState,
} from "./accessPlan.types";
import {
	buildAccessPlanCreateRequest,
	buildAccessPlanUpdateDTO,
	buildManagedApiConnectionTestRequest,
	inferAccessKind,
	toAccessPlanFormValues,
} from "./accessPlanPayload";
import { buildFileBaseName, isApiDataSource, isJdbcSource, normalizeTableName } from "./shared/ingestionFormHelpers";
import { validateFileTargetColumns } from "./shared/fileTargetSchemaMapping";
import { resolveCreatedTaskId } from "./shared/transformCreateAsyncRun.helpers";
import { uploadTransformFileWithAdmission } from "./shared/transformCreateFileFlow.helpers";

type BootstrapResult = {
	dataSources: DataSourceSelectionItem[];
	targetDataSources: DataSourceSelectionItem[];
	defaultDestination: DefaultDestinationStatus | null;
	defaultTargetDataSourceId?: string;
};

export async function loadAccessPlanBootstrap(): Promise<BootstrapResult> {
	const [sourceSelections, targetSelections, defaultDestination] = await Promise.all([
		dataSourcesService.selections({ capability: "INGESTION_SOURCE" }),
		dataSourcesService.selections({ capability: "DBT_TARGET" }),
		ingestionTaskAPI.getDefaultDestinationStatus(),
	]);
	return {
		dataSources: Array.isArray(sourceSelections?.items) ? sourceSelections.items : [],
		targetDataSources: Array.isArray(targetSelections?.items) ? targetSelections.items : [],
		defaultDestination: defaultDestination || null,
		defaultTargetDataSourceId: normalizeText(targetSelections?.defaultDataSourceId) || undefined,
	};
}

export async function loadAccessPlanEdit(editId: number, expectedKind: AccessKind) {
	const task = await ingestionTaskAPI.getTask(editId);
	const actualKind = inferAccessKind(task);
	if (actualKind !== expectedKind) {
		throw new Error(`任务类型为${ACCESS_KIND_LABELS[actualKind]}，请从对应入口编辑`);
	}
	const extracted = extractManagedFileFromTask(task);
	return {
		task,
		values: toAccessPlanFormValues(task),
		fileUploadResult: expectedKind === "file" ? restoreManagedFileAdmissionFromTask(extracted, task) : null,
	};
}

type EditResult = Awaited<ReturnType<typeof loadAccessPlanEdit>>;

const SAFE_ACCESS_PLAN_MESSAGES = new Set([
	"缺少 API 资源路径",
	"API 资源路径必须是站内相对路径",
	"API 资源路径格式无效",
	"API 资源路径不能包含凭据参数，请在托管连接中配置认证信息",
	"API 资源配置无效",
	"缺少 API 资源标识",
	"缺少 API 请求方法",
	"缺少 API 目标表",
	"请选择目标数据源",
	"平台默认数据湖不可用",
	"平台默认数据湖配置不完整",
	"API 来源配置无效",
	"缺少 API Reader 类型",
	"请选择数据库连接",
	"请选择 API 连接",
	"请输入任务名称",
	"请选择需要接入的表",
	"目标表名格式不合法",
	"请先上传并解析文件",
	"编辑任务缺少 ID",
	"编辑任务尚未加载完成，不能创建新任务",
	"新建任务上下文尚未初始化，不能更新旧任务",
	"请先选择数据库连接",
	"数据库认证失败，请检查用户名、密码及来源 IP 授权",
	"数据库连接失败，请检查地址、端口、网络及数据库服务状态",
	"数据库连接或筛选条件已变更，请重新发现源表",
	"请先选择 API 连接",
	"API 连接或资源参数已变更，请重新预览",
	"请选择有效的文件密级",
	"文件密级已变更，请按当前密级重新上传",
	"文件密级封存与当前选择不一致，请重新上传",
	"文件仍在上传解析，请等待完成后再保存",
	"数据库连接或筛选条件已变更，请重新发现并选择源表",
	"请先上传文件",
	"缺少文件密级封存，请重新上传文件",
	"文件上传响应无效",
	"请选择已有表结构",
	"请输入完整目标表名",
	"目标字段名不能为空",
	"目标字段名只能包含字母、数字和下划线，且不能以数字开头",
	"目标字段名不能重复",
	"全量重建必须使用已选择的原表名",
	"请确认全量重建原表",
	"未发现可用表，请重新发现并选择源表",
	"单个任务最多接入 1000 张表，请分批配置",
	"目标表名称重复或未确定，请调整表映射或分开接入",
	"源表与目标表数量不一致，请逐表配置映射",
	"已保存的草稿读取失败，请从任务列表重新进入",
]);

export const safeAccessPlanErrorMessage = (error: unknown, fallback: string) => {
	if (error instanceof AccessPlanAdmissionError) return error.message;
	const message = error instanceof Error ? normalizeText(error.message) : "";
	const detail = (error as { response?: { data?: { detail?: unknown } } } | null)?.response?.data?.detail;
	if (
		/\bCLASSIFICATION_SEAL_STALE\b/.test(message) ||
		(typeof detail === "string" && /\bCLASSIFICATION_SEAL_STALE\b/.test(detail))
	) {
		return "文件密级证据已变化，本次配置尚未生效。请重新进入编辑页核对配置；如更换文件或调整字段密级，请重新上传并确认封存后保存。";
	}
	const knownTaskKindMessage = /^任务类型为(?:数据库|API|离线文件)，请从对应入口编辑$/.test(message);
	return message && (SAFE_ACCESS_PLAN_MESSAGES.has(message) || knownTaskKindMessage) ? message : fallback;
};

export class AccessPlanAdmissionError extends Error {
	constructor(
		readonly taskId: number,
		cause: unknown,
	) {
		super(
			`任务草稿 ${taskId} 已保存，但尚未生效。${safeAccessPlanErrorMessage(cause, "请核对配置后重试。")} 再次保存将更新此草稿。`,
		);
		this.name = "AccessPlanAdmissionError";
	}
}

export class AccessPlanInitializationError extends Error {
	constructor(
		readonly stage: "bootstrap" | "edit",
		cause: unknown,
	) {
		super(safeAccessPlanErrorMessage(cause, stage === "bootstrap" ? "接入配置加载失败" : "任务加载失败"));
		this.name = "AccessPlanInitializationError";
	}
}

export async function loadAccessPlanInitialization(editId: number | undefined, expectedKind: AccessKind) {
	const bootstrapPromise = loadAccessPlanBootstrap().then(
		(value) => ({ ok: true as const, value }),
		(error: unknown) => ({ ok: false as const, error }),
	);
	const editPromise: Promise<{ ok: true; value: EditResult | null } | { ok: false; error: unknown }> = editId
		? loadAccessPlanEdit(editId, expectedKind).then(
				(value) => ({ ok: true as const, value }),
				(error: unknown) => ({ ok: false as const, error }),
			)
		: Promise.resolve({ ok: true as const, value: null });
	const [bootstrap, edit] = await Promise.all([bootstrapPromise, editPromise]);
	if (!bootstrap.ok) throw new AccessPlanInitializationError("bootstrap", bootstrap.error);
	if (!edit.ok) throw new AccessPlanInitializationError("edit", edit.error);
	return { bootstrap: bootstrap.value, edit: edit.value };
}

type SaveAccessPlanInput = AccessPlanPayloadContext & {
	existingTask: IngestionTaskDTO | null;
	editId?: number;
	resumeTaskId?: number;
	onTaskSaved?: (taskId: number) => void;
};

export async function saveAccessPlan(
	input: SaveAccessPlanInput,
): Promise<{ taskId: number | string | null; updated: boolean }> {
	if (input.editId !== undefined && input.existingTask?.id !== input.editId) {
		throw new Error("编辑任务尚未加载完成，不能创建新任务");
	}
	if (input.editId === undefined && input.existingTask !== null) {
		throw new Error("新建任务上下文尚未初始化，不能更新旧任务");
	}
	const existingTask =
		input.existingTask || (input.resumeTaskId ? await ingestionTaskAPI.getTask(input.resumeTaskId) : null);
	if (input.resumeTaskId && existingTask?.id !== input.resumeTaskId)
		throw new Error("已保存的草稿读取失败，请从任务列表重新进入");
	let context: AccessPlanPayloadContext = input;
	if (input.kind === "database" && input.values.tableSelectionMode === "all") {
		const sourceDataSourceId = normalizeText(input.values.sourceDataSourceId);
		if (!sourceDataSourceId) throw new Error("请选择数据库连接");
		const tables = await ingestionTaskAPI.discoverTables({
			source: { dataSourceId: sourceDataSourceId },
			filter: {
				schema: input.values.readerSchema || undefined,
				tablePattern: input.values.readerTablePattern || undefined,
				limit: 0,
				includeColumns: false,
			},
		});
		const selectedTables = tables.map((table) => (table.schema ? `${table.schema}.${table.name}` : table.name));
		if (!selectedTables.length) throw new Error("未发现可用表，请重新发现并选择源表");
		context = { ...input, values: { ...input.values, tableSelectionMode: "manual", selectedTables } };
	}
	let taskId: number;
	if (existingTask?.id) {
		const updated = await ingestionTaskAPI.updateTask(existingTask.id, buildAccessPlanUpdateDTO(existingTask, context));
		taskId = Number(updated?.id ?? existingTask.id);
	} else {
		const created = await createIngestionTask(buildAccessPlanCreateRequest(context));
		taskId = Number(resolveCreatedTaskId(created));
	}
	if (!Number.isSafeInteger(taskId) || taskId <= 0) throw new Error("接入任务编号无效");
	input.onTaskSaved?.(taskId);
	try {
		await ingestionTaskAPI.admitTask(taskId);
	} catch (error: unknown) {
		throw new AccessPlanAdmissionError(taskId, error);
	}
	return { taskId, updated: existingTask !== null };
}

const INITIAL_STATE: AccessPlanRuntimeState = {
	dataSources: [],
	targetDataSources: [],
	defaultDestination: null,
	loading: true,
	error: "",
	loadedContextKey: null,
	existingTask: null,
	fileUploadResult: null,
	discoveredTables: [],
	discoveryFingerprint: null,
	discoveringTables: false,
	discoverError: "",
	apiPreview: null,
	apiPreviewFingerprint: null,
	apiPreviewing: false,
	uploadingFile: false,
	saving: false,
	editError: "",
};

type UseAccessPlanWizardInput = {
	kind: AccessKind;
	editId?: number;
	form: FormInstance<AccessPlanFormValues>;
};

const databaseDiscoveryFingerprint = (values: Partial<AccessPlanFormValues>) =>
	JSON.stringify([
		normalizeText(values.sourceDataSourceId),
		normalizeText(values.readerSchema),
		normalizeText(values.readerTablePattern),
	]);

const apiPreviewFingerprint = (values: Partial<AccessPlanFormValues>) =>
	JSON.stringify([
		normalizeText(values.sourceDataSourceId),
		normalizeText(values.apiResourcePath),
		values.apiMethod || "GET",
		normalizeText(values.apiRecordPath),
		normalizeText(values.apiPageParam),
		normalizeText(values.apiSizeParam),
		Number(values.apiPageSize) || 0,
		normalizeText(values.apiCursorField),
		normalizeText(values.apiCursorParam),
	]);

const fileFloorMatchesSelection = (file: ManagedFileUploadResult | null, selected: unknown) => {
	const requested = normalizeClassification(typeof selected === "string" ? selected : undefined, undefined);
	const sealedFloor = normalizeClassification(
		typeof file?.classificationSeal?.fileFloor === "string"
			? file.classificationSeal.fileFloor
			: typeof file?.classification === "string"
				? file.classification
				: undefined,
		undefined,
	);
	return Boolean(requested && sealedFloor && requested === sealedFloor);
};

export const resolveUserClassificationRank = (user: unknown) => {
	if (!user || typeof user !== "object") return undefined;
	const record = user as Record<string, unknown>;
	const attributes =
		record.attributes && typeof record.attributes === "object" ? (record.attributes as Record<string, unknown>) : {};
	const personnelValues = [
		record.person_level,
		record.personLevel,
		record.personnel_level,
		record.personnelLevel,
		record.person_security_level,
		record.personSecurityLevel,
		record.personnel_security_level,
		record.personnelSecurityLevel,
		attributes.person_level,
		attributes.personLevel,
		attributes.personnel_level,
		attributes.personnelLevel,
		attributes.person_security_level,
		attributes.personSecurityLevel,
		attributes.personnel_security_level,
		attributes.personnelSecurityLevel,
	];
	const dataValues = [
		record.maxDataLevel,
		record.dataLevel,
		attributes.max_data_level,
		attributes.maxDataLevel,
		attributes.data_level,
		attributes.classification,
	];
	let highestRank: number | undefined;
	const considerRank = (rank: number | undefined) => {
		if (rank !== undefined && (highestRank === undefined || rank > highestRank)) highestRank = rank;
	};
	for (const raw of personnelValues) {
		const candidates = Array.isArray(raw) ? raw : [raw];
		for (const candidate of candidates) {
			if (typeof candidate !== "string" || !candidate.trim()) continue;
			for (const allowedLevel of deriveDataLevels(candidate)) considerRank(classificationRank(allowedLevel));
		}
	}
	for (const raw of dataValues) {
		const candidates = Array.isArray(raw) ? raw : [raw];
		for (const candidate of candidates) {
			if (typeof candidate !== "string" || !candidate.trim()) continue;
			// Distinct fallbacks let us reject unknown aliases without duplicating the shared classification map.
			const lowFallback = normalizeClassification(candidate, "PUBLIC");
			const highFallback = normalizeClassification(candidate, "CONFIDENTIAL");
			if (lowFallback !== highFallback) continue;
			considerRank(classificationRank(lowFallback));
		}
	}
	return highestRank;
};

export function useAccessPlanWizard({ kind, editId, form }: UseAccessPlanWizardInput) {
	const [state, setState] = useState<AccessPlanRuntimeState>(INITIAL_STATE);
	const userInfo = useUserInfo() as ({ username?: string; login?: string } & Record<string, unknown>) | null;
	const contextKey = `${kind}:${editId ?? "create"}`;
	const discoveryRequestIdRef = useRef(0);
	const apiPreviewRequestIdRef = useRef(0);
	const fileUploadRequestIdRef = useRef(0);
	const savedTaskIdRef = useRef<number>();

	useEffect(() => {
		savedTaskIdRef.current = undefined;
		let active = true;
		discoveryRequestIdRef.current += 1;
		apiPreviewRequestIdRef.current += 1;
		fileUploadRequestIdRef.current += 1;
		form.resetFields();
		setState({ ...INITIAL_STATE, loading: true });
		void loadAccessPlanInitialization(editId, kind)
			.then((result) => {
				if (!active) return;
				const targetDataSourceId = result.edit?.values.targetDataSourceId || result.bootstrap.defaultTargetDataSourceId;
				if (result.edit) {
					form.setFieldsValue({ ...result.edit.values, targetDataSourceId });
				} else if (!form.getFieldValue("targetDataSourceId") && targetDataSourceId) {
					form.setFieldValue("targetDataSourceId", targetDataSourceId);
				}
				setState((previous) => ({
					...previous,
					...result.bootstrap,
					loading: false,
					error: "",
					editError: "",
					loadedContextKey: contextKey,
					existingTask: result.edit?.task || null,
					fileUploadResult: result.edit?.fileUploadResult || null,
					discoveryFingerprint:
						kind === "database" && result.edit?.values.tableSelectionMode === "manual"
							? databaseDiscoveryFingerprint(result.edit.values)
							: null,
				}));
			})
			.catch((error: unknown) => {
				if (!active) return;
				const stage = error instanceof AccessPlanInitializationError ? error.stage : editId ? "edit" : "bootstrap";
				setState((previous) => ({
					...previous,
					loading: false,
					...(stage === "edit"
						? { editError: safeAccessPlanErrorMessage(error, "任务加载失败") }
						: { error: safeAccessPlanErrorMessage(error, "接入配置加载失败") }),
				}));
			});
		return () => {
			active = false;
		};
	}, [contextKey, editId, form, kind]);

	const sourceDataSources = useMemo(() => {
		if (kind === "api") return state.dataSources.filter(isApiDataSource);
		if (kind === "database")
			return state.dataSources.filter((source) => isJdbcSource(source) && !isApiDataSource(source));
		return [];
	}, [kind, state.dataSources]);

	const discoverTables = useCallback(async () => {
		const requestValues = form.getFieldsValue(true);
		const sourceDataSourceId = normalizeText(requestValues.sourceDataSourceId);
		if (!sourceDataSourceId) throw new Error("请先选择数据库连接");
		const requestId = ++discoveryRequestIdRef.current;
		const fingerprint = databaseDiscoveryFingerprint(requestValues);
		form.setFieldValue("selectedTables", []);
		setState((previous) => ({
			...previous,
			discoveringTables: true,
			discoverError: "",
			discoveredTables: [],
			discoveryFingerprint: null,
		}));
		try {
			const tables = await ingestionTaskAPI.discoverTables({
				source: { dataSourceId: sourceDataSourceId },
				filter: {
					schema: normalizeText(requestValues.readerSchema) || undefined,
					tablePattern: normalizeText(requestValues.readerTablePattern) || undefined,
					limit: 0,
					includeColumns: false,
				},
			});
			if (
				discoveryRequestIdRef.current !== requestId ||
				databaseDiscoveryFingerprint(form.getFieldsValue(true)) !== fingerprint
			) {
				throw new Error("数据库连接或筛选条件已变更，请重新发现源表");
			}
			setState((previous) => ({
				...previous,
				discoveringTables: false,
				discoveredTables: Array.isArray(tables) ? tables : [],
				discoveryFingerprint: fingerprint,
			}));
			return tables;
		} catch (error: unknown) {
			if (discoveryRequestIdRef.current === requestId) {
				setState((previous) => ({
					...previous,
					discoveringTables: false,
					discoverError: safeAccessPlanErrorMessage(error, "源表发现失败"),
				}));
			}
			throw error;
		}
	}, [form]);

	const resetDatabaseDiscovery = useCallback(() => {
		discoveryRequestIdRef.current += 1;
		form.setFieldValue("selectedTables", []);
		setState((previous) => ({
			...previous,
			discoveredTables: [],
			discoveryFingerprint: null,
			discoveringTables: false,
			discoverError: "",
		}));
	}, [form]);

	const previewApi = useCallback(async () => {
		const values = form.getFieldsValue(true);
		const dataSourceId = normalizeText(values.sourceDataSourceId);
		if (!dataSourceId) throw new Error("请先选择 API 连接");
		const requestId = ++apiPreviewRequestIdRef.current;
		const fingerprint = apiPreviewFingerprint(values);
		setState((previous) => ({
			...previous,
			apiPreviewing: true,
			apiPreview: null,
			apiPreviewFingerprint: null,
		}));
		try {
			const preview = await ingestionTaskAPI.testManagedApiConnection(buildManagedApiConnectionTestRequest(values));
			if (
				apiPreviewRequestIdRef.current !== requestId ||
				apiPreviewFingerprint(form.getFieldsValue(true)) !== fingerprint
			) {
				throw new Error("API 连接或资源参数已变更，请重新预览");
			}
			setState((previous) => ({
				...previous,
				apiPreviewing: false,
				apiPreview: preview,
				apiPreviewFingerprint: fingerprint,
			}));
			return preview;
		} catch (error: unknown) {
			if (apiPreviewRequestIdRef.current === requestId) {
				setState((previous) => ({ ...previous, apiPreviewing: false }));
			}
			throw error;
		}
	}, [form]);

	const resetApiPreview = useCallback(() => {
		apiPreviewRequestIdRef.current += 1;
		setState((previous) => ({
			...previous,
			apiPreview: null,
			apiPreviewFingerprint: null,
			apiPreviewing: false,
		}));
	}, []);

	const uploadFile = useCallback(
		async (file: File) => {
			const classification = normalizeClassification(form.getFieldValue("fileClassification"), undefined);
			if (!classification) throw new Error("请选择有效的文件密级");
			const requestId = ++fileUploadRequestIdRef.current;
			const previousFile = state.fileUploadResult;
			setState((previous) => ({ ...previous, uploadingFile: true, fileUploadResult: null }));
			try {
				const uploaded = toManagedFile(
					await uploadTransformFileWithAdmission({
						file,
						classification,
						previewLimit: 20,
						previousFile: previousFile ? toAdmissionFile(previousFile) : null,
						preserveSavedMapping: Boolean(editId),
						uploadAndParse: async (upload, options) =>
							toAdmissionFile(await ingestionTaskAPI.uploadAndParseFile(upload, options)),
					}),
				);
				if (
					fileUploadRequestIdRef.current !== requestId ||
					normalizeClassification(form.getFieldValue("fileClassification"), undefined) !== classification
				) {
					throw new Error("文件密级已变更，请按当前密级重新上传");
				}
				if (!fileFloorMatchesSelection(uploaded, classification)) {
					throw new Error("文件密级封存与当前选择不一致，请重新上传");
				}
				setState((previous) => ({ ...previous, uploadingFile: false, fileUploadResult: uploaded }));
				if (!form.getFieldValue("fileTargetTable")) {
					const generated = normalizeTableName(
						`${normalizeText(form.getFieldValue("syncPrefix"))}${buildFileBaseName(uploaded.originalName)}`,
					);
					if (generated) form.setFieldValue("fileTargetTable", generated);
				}
				return uploaded;
			} catch (error: unknown) {
				if (fileUploadRequestIdRef.current === requestId) {
					setState((previous) => ({ ...previous, uploadingFile: false }));
				}
				throw error;
			}
		},
		[editId, form, state.fileUploadResult],
	);

	// Keep the last valid name-to-column identity at wizard scope so back/next
	// navigation cannot discard it while a field name is temporarily invalid.
	const classificationFileRef = useRef<ManagedFileUploadResult | null>(null);
	useEffect(() => {
		const file = state.fileUploadResult;
		if (!file || !validateFileTargetColumns(file.columns).length) classificationFileRef.current = file;
	}, [state.fileUploadResult]);

	const setFileUploadResult = useCallback((fileUploadResult: ManagedFileUploadResult | null) => {
		if (fileUploadResult === null) fileUploadRequestIdRef.current += 1;
		const baseline = classificationFileRef.current;
		let nextFile = fileUploadResult;
		if (
			fileUploadResult &&
			baseline?.fileId === fileUploadResult.fileId &&
			!validateFileTargetColumns(fileUploadResult.columns).length &&
			baseline.columns.some((column, index) => column.name !== fileUploadResult.columns[index]?.name)
		) {
			// Read every level from the immutable baseline before assigning any new
			// keys. This also handles swaps without overwriting another column.
			const fields = { ...baseline.fieldClassifications };
			baseline.columns.forEach((column) => {
				delete fields[column.name];
			});
			baseline.columns.forEach((column, index) => {
				const next = fileUploadResult.columns[index];
				const level = baseline.fieldClassifications?.[column.name];
				if (next && level) fields[next.name] = level;
			});
			nextFile = { ...fileUploadResult, fieldClassifications: fields };
		}
		setState((previous) => ({
			...previous,
			fileUploadResult: nextFile,
			uploadingFile: nextFile === null ? false : previous.uploadingFile,
		}));
	}, []);

	const submit = useCallback(async () => {
		const existingTaskMatches = editId === undefined ? state.existingTask === null : state.existingTask?.id === editId;
		if (state.loading || state.loadedContextKey !== contextKey || !existingTaskMatches) {
			throw new Error("编辑任务尚未加载完成，不能创建新任务");
		}
		if (state.uploadingFile) throw new Error("文件仍在上传解析，请等待完成后再保存");
		await form.validateFields();
		const values = form.getFieldsValue(true);
		if (
			kind === "database" &&
			values.tableSelectionMode === "manual" &&
			state.discoveryFingerprint !== databaseDiscoveryFingerprint(values)
		) {
			throw new Error("数据库连接或筛选条件已变更，请重新发现并选择源表");
		}
		if (kind === "file") {
			const admission = resolveManagedFileAdmissionState(state.fileUploadResult);
			if (!admission.ready) throw new Error(admission.reason);
			if (!fileFloorMatchesSelection(state.fileUploadResult, values.fileClassification)) {
				throw new Error("文件密级封存与当前选择不一致，请重新上传");
			}
		}
		const selectedSource = state.dataSources.find((item) => item.id === values.sourceDataSourceId);
		const selectedTarget = state.targetDataSources.find((item) => item.id === values.targetDataSourceId);
		setState((previous) => ({ ...previous, saving: true }));
		try {
			return await saveAccessPlan({
				kind,
				values,
				owner: userInfo?.username || userInfo?.login,
				defaultDestination: state.defaultDestination,
				selectedSource,
				selectedTarget,
				fileUploadResult: state.fileUploadResult,
				existingTask: state.existingTask,
				editId,
				resumeTaskId: editId === undefined ? savedTaskIdRef.current : undefined,
				onTaskSaved: (taskId) => {
					savedTaskIdRef.current = taskId;
				},
			});
		} finally {
			setState((previous) => ({ ...previous, saving: false }));
		}
	}, [
		contextKey,
		editId,
		form,
		kind,
		state.dataSources,
		state.defaultDestination,
		state.discoveryFingerprint,
		state.existingTask,
		state.fileUploadResult,
		state.loadedContextKey,
		state.loading,
		state.targetDataSources,
		state.uploadingFile,
		userInfo,
	]);

	return {
		...state,
		sourceDataSources,
		fileAdmission: resolveManagedFileAdmissionState(state.fileUploadResult),
		userClassificationRank: resolveUserClassificationRank(userInfo),
		discoverTables,
		resetDatabaseDiscovery,
		previewApi,
		resetApiPreview,
		uploadFile,
		setFileUploadResult,
		submit,
	};
}
