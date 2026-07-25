import {
	Alert,
	Button,
	Card,
	Collapse,
	Descriptions,
	Divider,
	Drawer,
	Empty,
	Input,
	Select,
	Skeleton,
	Space,
	Steps,
	Table,
	Tag,
	Typography,
	Upload,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { Archive, ArrowLeft, CheckCircle2, ExternalLink, RefreshCw, Wrench } from "lucide-react";
import { useEffect, useMemo, useReducer, useRef, useState } from "react";
import {
	applyModelSpecImport,
	getModelSpecImportApplyResult,
	getModelSpecImportPreviewRun,
	inspectDbtModelArchive,
	previewModelSpecImport,
	retryModelSpecImport,
	type ModelSpecImportIssue,
	type ModelSpecImportPreviewItem,
	type ModelSpecImportResultItem,
} from "@/api/modelSpecImportApi";
import {
	getWarehousePlanCategories,
	getWarehousePlanSources,
	type WarehousePlanHeader,
	type WarehousePlanSourceBindingView,
} from "@/api/warehousePlanApi";
import {
	buildImportedModelRoute,
	buildImportPlanRoute,
	buildImportRepairRoute,
} from "./modelPackageImportNavigation";
import {
	canRetryFailedImport,
	createConvertedModelPackage,
	createModelPackageImportState,
	hasPreviewContext,
	isSelectablePreviewItem,
	isPreviewApplicable,
	modelPackageDomainCodes,
	modelPackageSourceIds,
	reduceModelPackageImportState,
	resolveModelPackageImportIdempotencySlot,
	sanitizeModelImportDiagnosticCode,
	validateDbtModelPackageArchive,
	type ModelPackageImportIdempotencySlot,
} from "./modelPackageImportState";

const { Dragger } = Upload;
const { Paragraph, Text, Title } = Typography;

const actionLabels: Record<string, string> = {
	CREATE: "新增",
	UPDATE: "更新",
	SKIP: "跳过",
	CONFLICT: "冲突",
	BLOCKED: "阻断",
};
const actionColors: Record<string, string> = {
	CREATE: "green",
	UPDATE: "blue",
	SKIP: "default",
	CONFLICT: "red",
	BLOCKED: "orange",
};
const conversionLabels: Record<string, string> = {
	DESIGNER_GENERATED: "普通模型 · 设计器生成",
	DBT_BACKED: "普通模型 · dbt 管理 SQL",
	BLOCKED: "暂不可转换",
};
const resultLabels: Record<string, string> = {
	CREATED: "已创建",
	UPDATED: "已更新",
	REPLAYED: "已确认（幂等重放）",
	SKIPPED: "已跳过",
	FAILED: "失败",
	BLOCKED: "阻断",
};

type ContextOption = { value: string; label: string };

const stableImportMessages: Record<string, string> = {
	MODEL_PACKAGE_SCHEMA_INVALID: "模型包格式不符合当前版本，请重新生成后上传。",
	MODEL_PACKAGE_CHECKSUM_MISMATCH: "模型包完整性校验失败，请重新生成后上传。",
	MODEL_PACKAGE_SEMANTIC_METADATA_REQUIRED: "模型缺少必要的业务语义，请先补充模型包语义。",
	MODEL_IMPORT_SOURCE_NOT_CONFIRMED: "模型引用的来源尚未在当前计划确认。",
	MODEL_IMPORT_SOURCE_VERSION_STALE: "规划来源版本已经变化，请刷新来源盘点后重新预检。",
	MODEL_IMPORT_DEPENDENCY_MISSING: "模型依赖不完整，请补齐上游模型后重新预检。",
	MODEL_IMPORT_DEPENDENCY_CYCLE: "模型依赖存在循环，暂时不能导入。",
	MODEL_PACKAGE_SEMANTIC_CONFIRMATION_REQUIRED: "模型结构已识别，但业务模型类型、粒度或消费场景尚未提供；该候选暂不可转换。",
	DBT_SOURCE_PROJECT_STATIC_ANALYSIS: "系统已从 dbt 源项目安全解析结构；本次没有执行模型 SQL。",
	MODEL_IMPORT_NODE_ALREADY_OWNED: "目标模型已由其他实现管理，不能直接覆盖。",
	MODEL_IMPORT_PREVIEW_STALE: "预检依据已经变化，请重新预检。",
	MODEL_IMPORT_IDEMPOTENCY_CONFLICT: "相同提交标识对应了不同内容，请刷新后重新提交。",
	MODEL_IMPORT_CONVERSION_BLOCKED: "该候选当前无法安全转换为普通模型。",
	MODEL_IMPORT_ARCHIVE_INVALID: "dbt ZIP 压缩包格式不正确，请重新导出后上传。",
	MODEL_IMPORT_ARCHIVE_EMPTY: "dbt ZIP 压缩包中没有可导入的模型，请确认 models 目录和对应 SQL 后重试。",
	MODEL_IMPORT_ARCHIVE_TOO_LARGE: "dbt ZIP 压缩包不能超过 32 MiB。",
	MODEL_IMPORT_ARCHIVE_LENGTH_REQUIRED: "dbt ZIP 上传缺少文件长度，请通过页面重新选择文件后上传。",
	MODEL_IMPORT_ARCHIVE_UNSAFE_PATH: "dbt ZIP 包含不安全或重复的文件路径，请重新打包后上传。",
	MODEL_IMPORT_ARCHIVE_MANIFEST_MISSING: "未找到 dbt_project.yml、manifest.json 或 models.tsv，请确认上传的是完整 dbt 项目。",
	MODEL_IMPORT_ARCHIVE_SOURCE_PROJECT_INVALID: "dbt 源项目结构无法安全解析，请检查项目根、models 目录和 SQL 引用。",
	MODEL_IMPORT_ARCHIVE_SQL_MISSING: "dbt manifest 引用的模型 SQL 不完整，请把对应 SQL 文件一并打包。",
	MODEL_IMPORT_ARCHIVE_INSPECTION_FAILED: "dbt ZIP 压缩包无法解析，请确认包内 dbt 项目完整后重试。",
};
const crossPlanMessage = "该导入批次不属于当前锁定计划，已阻止跨计划恢复。";
const safeClientPackageMessages = new Set([
	"请选择 dbt ZIP 压缩包",
	"dbt ZIP 压缩包不能为空",
	"dbt ZIP 压缩包不能超过 32 MiB",
	"转换后的内部模型包结构不完整，无法继续预检",
]);

export type ImportModelPackageWizardProps = {
	open: boolean;
	lockedPlanId?: string;
	initialRunId?: string;
	plans: WarehousePlanHeader[];
	canEdit: boolean;
	returnSurface: "workbench" | "model-center";
	onClose: (runId?: string) => void;
	onRunIdChange: (runId: string) => void;
	onApplied: () => void;
	onNavigate: (path: string) => void;
};

const requestFailure = (error: unknown, fallback: string): { message: string; diagnosticCode: string } => {
	const response = (error as { response?: { data?: { code?: unknown } } } | null)?.response?.data;
	const diagnosticCode = sanitizeModelImportDiagnosticCode(response?.code);
	return {
		message:
			typeof response?.code === "string" && stableImportMessages[response.code]
				? stableImportMessages[response.code]
				: fallback,
		diagnosticCode,
	};
};

const issueMessage = (issue: ModelSpecImportIssue): string =>
	stableImportMessages[issue.code] || "该候选存在未识别的校验问题，请联系管理员核对技术详情。";

const clientPackageMessage = (error: unknown): string =>
	error instanceof Error && safeClientPackageMessages.has(error.message)
		? error.message
		: "模型包读取失败，请确认文件完整后重试。";

const isNotFound = (error: unknown): boolean =>
	(error as { response?: { status?: unknown } } | null)?.response?.status === 404;

const proposedValue = (item: ModelSpecImportPreviewItem, key: string): string => {
	const value = item.proposedModelSpec?.[key];
	return value == null || value === "" ? "待服务端确认" : String(value);
};

const proposedGrain = (item: ModelSpecImportPreviewItem): string => {
	const grain = item.proposedModelSpec?.grain;
	if (typeof grain === "string") return grain || "待服务端确认";
	if (grain && typeof grain === "object") {
		const statement = (grain as { statement?: unknown }).statement;
		const keys = (grain as { keys?: unknown }).keys;
		const keyText = Array.isArray(keys) && keys.length ? `（键：${keys.map(String).join("、")}）` : "";
		if (typeof statement === "string" && statement.trim()) return `${statement}${keyText}`;
	}
	return "待服务端确认";
};

const issueRepairTarget = (issue: ModelSpecImportIssue): "categories" | "sources" | null => {
	const value = `${issue.code} ${issue.fieldPath || ""} ${issue.recoveryAction || ""}`.toUpperCase();
	if (value.includes("SOURCE")) return "sources";
	if (value.includes("DOMAIN") || value.includes("CATEGORY")) return "categories";
	return null;
};

const itemIssues = (item: ModelSpecImportPreviewItem): ModelSpecImportIssue[] =>
	(item.issues || []).filter((issue) => issue.code !== "DBT_SOURCE_PROJECT_STATIC_ANALYSIS");

export function ImportModelPackageWizard({
	open,
	lockedPlanId,
	initialRunId,
	plans,
	canEdit,
	returnSurface,
	onClose,
	onRunIdChange,
	onApplied,
	onNavigate,
}: ImportModelPackageWizardProps) {
	const [state, dispatch] = useReducer(
		reduceModelPackageImportState,
		undefined,
		() => createModelPackageImportState(lockedPlanId || "", initialRunId || ""),
	);
	const [contextLoading, setContextLoading] = useState(false);
	const [contextError, setContextError] = useState("");
	const [domainOptions, setDomainOptions] = useState<ContextOption[]>([]);
	const [sourceOptions, setSourceOptions] = useState<ContextOption[]>([]);
	const [previewSearch, setPreviewSearch] = useState("");
	const [restoredRunId, setRestoredRunId] = useState("");
	const [runReloadNonce, setRunReloadNonce] = useState(0);
	const [contextReloadNonce, setContextReloadNonce] = useState(0);
	const failedRestoreRunIdRef = useRef("");
	const applyIdempotencyRef = useRef<ModelPackageImportIdempotencySlot | null>(null);
	const retryIdempotencyRef = useRef<ModelPackageImportIdempotencySlot | null>(null);

	const editablePlans = useMemo(
		() => plans.filter((plan) => plan.lifecycleStatus !== "PUBLISHED" && plan.lifecycleStatus !== "ARCHIVED"),
		[plans],
	);
	const selectedPlan = useMemo(() => plans.find((plan) => plan.id === state.planId) || null, [plans, state.planId]);
	const selectedPlanEditable = Boolean(
		selectedPlan && editablePlans.some((plan) => plan.id === selectedPlan.id),
	);
	const domainCodes = useMemo(() => modelPackageDomainCodes(state.modelPackage), [state.modelPackage]);
	const sourceIds = useMemo(() => modelPackageSourceIds(state.modelPackage), [state.modelPackage]);
	const failedItems = state.result?.items.filter((item) => item.status === "FAILED") || [];
	const failedCount = failedItems.length;
	const blockedCount = state.result?.items.filter((item) => item.status === "BLOCKED").length || 0;
	const previewApplicable = isPreviewApplicable(state.preview);
	const sourceProjectStaticPreview = Boolean(
		state.preview?.items.some((item) =>
			(item.issues || []).some((issue) => issue.code === "DBT_SOURCE_PROJECT_STATIC_ANALYSIS"),
		),
	);
	const retryAllowed = canRetryFailedImport(canEdit, selectedPlanEditable, state.preview, failedCount);
	const previewNameByUniqueId = useMemo(
		() =>
			new Map(
				(state.preview?.items || []).map((item) => [
					item.dbtUniqueId,
					proposedValue(item, "name") === "待服务端确认" ? "模型导入候选" : proposedValue(item, "name"),
				]),
			),
		[state.preview],
	);

	useEffect(() => {
		if (lockedPlanId && state.planId !== lockedPlanId) dispatch({ type: "PLAN_SELECTED", planId: lockedPlanId });
	}, [lockedPlanId, state.planId]);

	useEffect(() => {
		const runId = initialRunId?.trim();
		if (
			!open ||
			!runId ||
			restoredRunId === runId ||
			failedRestoreRunIdRef.current === runId ||
			state.busy
		) return;
		setRestoredRunId(runId);
		dispatch({ type: "REQUEST_STARTED" });
		void getModelSpecImportPreviewRun(runId)
			.then(async (preview) => {
				if (lockedPlanId && preview.planId !== lockedPlanId) {
					dispatch({ type: "RESET", planId: lockedPlanId });
					throw new Error(crossPlanMessage);
				}
				const result = await getModelSpecImportApplyResult(runId).catch((error) => {
					if (isNotFound(error)) return null;
					throw error;
				});
				failedRestoreRunIdRef.current = "";
				dispatch({
					type: "RUN_RESTORED",
					preview,
					result,
				});
			})
			.catch((error) => {
				failedRestoreRunIdRef.current = runId;
				setRestoredRunId("");
				dispatch({ type: "RESET", planId: lockedPlanId || "" });
				dispatch({
					type: "REQUEST_FAILED",
					...(error instanceof Error && error.message === crossPlanMessage
						? { message: crossPlanMessage, diagnosticCode: "MODEL_IMPORT_PLAN_MISMATCH" }
						: requestFailure(error, "导入批次加载失败，请重试。")),
				});
			});
	}, [initialRunId, lockedPlanId, open, restoredRunId, runReloadNonce, state.busy]);

	useEffect(() => {
		if (!open || state.step < 1 || !state.planId) return;
		let active = true;
		setContextLoading(true);
		setContextError("");
		void Promise.all([getWarehousePlanCategories(state.planId), getWarehousePlanSources(state.planId)])
			.then(([categories, sources]) => {
				if (!active) return;
				setDomainOptions(
					categories.value.domainBindings
						.filter((binding) => binding.confirmationStatus === "CONFIRMED" && binding.resolutionStatus === "AVAILABLE")
						.map((binding) => ({
							value: binding.domainId,
							label: `${binding.name || "业务分类"}${binding.code ? `（${binding.code}）` : ""}`,
						})),
				);
				setSourceOptions(
					sources.bindings
						.filter(
							(binding: WarehousePlanSourceBindingView) =>
								binding.confirmationStatus === "CONFIRMED" && binding.resolutionStatus === "AVAILABLE",
						)
						.map((binding: WarehousePlanSourceBindingView) => ({
							value: binding.bindingId,
							label: `${binding.displayName || binding.sourceId || "已确认来源"} · ${binding.sourceType}${
								binding.confirmedVersion ? ` · ${binding.confirmedVersion.slice(0, 12)}` : ""
							}`,
						})),
				);
			})
			.catch(() => {
				if (active) setContextError("计划上下文读取失败；不会使用未知或未确认的分类与来源。");
			})
			.finally(() => {
				if (active) setContextLoading(false);
			});
		return () => {
			active = false;
		};
	}, [contextReloadNonce, open, state.planId, state.step]);

	const inspectArchive = async (file: File) => {
		try {
			validateDbtModelPackageArchive(file.name, file.size);
		} catch (error) {
			dispatch({
				type: "REQUEST_FAILED",
				message: clientPackageMessage(error),
				diagnosticCode: "MODEL_PACKAGE_CLIENT_INVALID",
			});
			return false;
		}
		dispatch({ type: "REQUEST_STARTED" });
		try {
			const modelPackage = await inspectDbtModelArchive(file);
			dispatch({ type: "PACKAGE_LOADED", ...createConvertedModelPackage(modelPackage, file.name, file.size) });
		} catch (error) {
			const clientMessage = clientPackageMessage(error);
			dispatch({
				type: "REQUEST_FAILED",
				...(error instanceof Error && safeClientPackageMessages.has(error.message)
					? { message: clientMessage, diagnosticCode: "MODEL_PACKAGE_CLIENT_INVALID" }
					: requestFailure(error, "dbt ZIP 压缩包无法转换为内部模型包，请确认包内包含可解析的 dbt 项目后重试。")),
			});
		}
		return false;
	};

	const runPreview = async () => {
		if (!state.modelPackage || !selectedPlanEditable || !hasPreviewContext(state)) {
			dispatch({
				type: "REQUEST_FAILED",
				message: "请先选择一个可编辑的建设计划",
				diagnosticCode: "MODEL_IMPORT_CONTEXT_INCOMPLETE",
			});
			return;
		}
		dispatch({ type: "REQUEST_STARTED" });
		try {
			const preview = await previewModelSpecImport({
				package: state.modelPackage,
				context: {
					planId: state.planId,
					domainMappings: state.domainMappings,
					sourceMappings: state.sourceMappings,
				},
				selectedUniqueIds: [],
			});
			dispatch({ type: "PREVIEW_SUCCEEDED", preview });
			onRunIdChange(preview.runId);
		} catch (error) {
			dispatch({ type: "REQUEST_FAILED", ...requestFailure(error, "预检失败，请根据问题修复后重试。") });
		}
	};

	const applyImport = async () => {
		if (!selectedPlanEditable) {
			dispatch({
				type: "REQUEST_FAILED",
				message: "目标计划已发布、归档、不可访问或不再可编辑，已阻止提交",
				diagnosticCode: "MODEL_IMPORT_PLAN_NOT_EDITABLE",
			});
			return;
		}
		if (
			!state.preview ||
			state.selectedUniqueIds.length === 0 ||
			!isPreviewApplicable(state.preview)
		) {
			dispatch({
				type: "REQUEST_FAILED",
				message: "预检已失效或过期，请重新预检后再导入",
				diagnosticCode: "MODEL_IMPORT_PREVIEW_STALE",
			});
			return;
		}
		const intent = `${state.preview.runId}:${state.preview.previewHash}:${[...state.selectedUniqueIds].sort().join("|")}`;
		const slot = resolveModelPackageImportIdempotencySlot(applyIdempotencyRef.current, intent);
		applyIdempotencyRef.current = slot;
		dispatch({ type: "REQUEST_STARTED" });
		try {
			const result = await applyModelSpecImport({
				runId: state.preview.runId,
				previewHash: state.preview.previewHash,
				selectedUniqueIds: state.selectedUniqueIds,
				idempotencyKey: slot.key,
			});
			dispatch({ type: "RESULT_SUCCEEDED", result });
			onRunIdChange(result.runId);
			onApplied();
		} catch (error) {
			dispatch({ type: "REQUEST_FAILED", ...requestFailure(error, "导入未提交，请核对预检是否已过期。") });
		}
	};

	const retryFailures = async () => {
		if (!canEdit || !selectedPlanEditable) {
			dispatch({
				type: "REQUEST_FAILED",
				message: "目标计划已发布、归档、不可访问或不再可编辑，已阻止失败重试",
				diagnosticCode: "MODEL_IMPORT_PLAN_NOT_EDITABLE",
			});
			return;
		}
		if (!isPreviewApplicable(state.preview)) {
			dispatch({
				type: "REQUEST_FAILED",
				message: "预检已失效或过期，请重新预检后再处理失败项",
				diagnosticCode: "MODEL_IMPORT_PREVIEW_STALE",
			});
			return;
		}
		if (!state.preview || failedCount === 0) return;
		const intent = `${state.preview.runId}:${state.result?.attemptId || "initial"}:${failedItems
			.map((item) => item.dbtUniqueId)
			.sort()
			.join("|")}`;
		const slot = resolveModelPackageImportIdempotencySlot(retryIdempotencyRef.current, intent);
		retryIdempotencyRef.current = slot;
		dispatch({ type: "REQUEST_STARTED" });
		try {
			const result = await retryModelSpecImport(state.preview.runId, {
				previewHash: state.preview.previewHash,
				idempotencyKey: slot.key,
			});
			dispatch({ type: "RESULT_SUCCEEDED", result });
			onApplied();
		} catch (error) {
			dispatch({ type: "REQUEST_FAILED", ...requestFailure(error, "失败项重试未完成，请稍后重试。") });
		}
	};

	const navigateRepair = (target: "categories" | "sources") => {
		if (state.planId) {
			dispatch({ type: "SENSITIVE_CLEARED" });
			onNavigate(buildImportRepairRoute(state.planId, target));
		}
	};

	const selectTargetPlan = (planId: string) => {
		dispatch({ type: "PLAN_SELECTED", planId });
		if (state.runId) onRunIdChange("");
	};

	const invalidatePreview = () => {
		dispatch({ type: "PREVIEW_INVALIDATED" });
		onRunIdChange("");
	};

	const closeWizard = () => {
		dispatch({ type: "SENSITIVE_CLEARED" });
		onClose(state.runId);
	};

	const previewItems = useMemo(() => {
		const keyword = previewSearch.trim().toLowerCase();
		if (!keyword) return state.preview?.items || [];
		return (state.preview?.items || []).filter((item) =>
			[item.dbtUniqueId, proposedValue(item, "name"), proposedValue(item, "modelType"), proposedValue(item, "layer")]
				.join(" ")
				.toLowerCase()
				.includes(keyword),
		);
	}, [previewSearch, state.preview]);

	const previewColumns: ColumnsType<ModelSpecImportPreviewItem> = [
		{
			title: "DTS 模型",
			width: 260,
			render: (_value, item) => (
				<div className="min-w-0">
					<div className="truncate font-medium text-slate-900">{proposedValue(item, "name")}</div>
					<Text className="block truncate text-xs" type="secondary">{proposedValue(item, "description")}</Text>
				</div>
			),
		},
		{
			title: "类型 / 目标层",
			width: 140,
			render: (_value, item) => (
				<Space size={4} wrap>
					<Tag>{proposedValue(item, "modelType")}</Tag>
					<Tag>{proposedValue(item, "layer")}</Tag>
				</Space>
			),
		},
		{
			title: "转换方式",
			width: 190,
			render: (_value, item) => conversionLabels[item.conversionMode] || item.conversionMode,
		},
		{
			title: "动作",
			width: 90,
			render: (_value, item) => <Tag color={actionColors[item.action]}>{actionLabels[item.action]}</Tag>,
		},
		{
			title: "问题",
			render: (_value, item) =>
				itemIssues(item).length ? (
					<div className="space-y-1">
						{itemIssues(item).slice(0, 2).map((issue) => (
							<div key={`${issue.code}-${issue.fieldPath || ""}`} className="text-xs text-slate-700">
								{issueMessage(issue)}
							</div>
						))}
					</div>
				) : (
					<Text type="secondary">可导入</Text>
				),
		},
	];

	const uploadStep = (
		<div className="mx-auto max-w-3xl py-5">
			<div className="mb-6">
				<Title level={4}>上传 dbt ZIP 压缩包</Title>
				<Paragraph type="secondary">
					上传高级建模使用的同一份 dbt 项目 ZIP，系统会读取项目结构并转换为普通模型候选。ZIP 内应包含 dbt_project.yml、models 目录及对应 SQL；manifest 等解析产物可选。
				</Paragraph>
			</div>
			<Dragger
				accept=".zip,application/zip"
				showUploadList={false}
				disabled={state.busy}
				beforeUpload={(file) => {
					void inspectArchive(file as File);
					return false;
				}}
			>
				<p className="ant-upload-drag-icon">
					<Archive className="mx-auto text-blue-600" size={42} />
				</p>
				<p className="ant-upload-text">点击或拖入 dbt ZIP 压缩包</p>
				<p className="ant-upload-hint">单文件，最大 32 MiB；系统自动转换，无需用户读取或准备内部 JSON</p>
			</Dragger>
			{state.metadata ? (
				<Card className="mt-5 border-blue-200 bg-blue-50/50" size="small">
					<Descriptions size="small" column={{ xs: 1, sm: 2 }} title={state.metadata.fileName}>
						<Descriptions.Item label="dbt 项目">{state.metadata.projectName}</Descriptions.Item>
						<Descriptions.Item label="模型">{state.metadata.modelCount} 个</Descriptions.Item>
						<Descriptions.Item label="解析方式">
							{state.metadata.inspectionMode === "SOURCE_PROJECT_STATIC"
								? "源项目安全解析（未执行 SQL）"
								: state.metadata.inspectionMode === "DBT_ARTIFACT"
									? "dbt 解析产物"
									: "旧 models.tsv 兼容"}
						</Descriptions.Item>
					</Descriptions>
					<Collapse
						ghost
						size="small"
						items={[{
							key: "package-technical",
							label: "技术详情",
							children: <Text type="secondary">内部包版本：{state.metadata.schemaVersion} · 包标识：{state.metadata.packageId} · 完整性校验：{state.metadata.checksum} · 技术节点：{state.metadata.technicalNodeCount} 个</Text>,
						}]}
					/>
				</Card>
			) : null}
		</div>
	);

	const contextStep = (
		<div className="mx-auto max-w-4xl py-3">
			<div className="mb-5">
				<Title level={4}>确认目标计划与业务上下文</Title>
				<Paragraph type="secondary">只显示当前计划内已确认、当前可用的业务分类和来源；界面不会要求复制 UUID。暂时无法映射的项也可先进入预检，由系统逐模型说明影响。</Paragraph>
			</div>
			<Card className="mb-4 border-slate-200" title="目标建设计划">
				<Select
					className="w-full"
					aria-label="目标建设计划"
					value={state.planId || undefined}
					disabled={Boolean(lockedPlanId) || state.busy}
					placeholder="选择一个可编辑的建设计划"
					options={editablePlans.map((plan) => ({ value: plan.id, label: `${plan.name} · ${plan.lifecycleStatus}` }))}
					onChange={selectTargetPlan}
				/>
				{lockedPlanId ? <Text className="mt-2 block" type="secondary">入口来自当前计划，导入期间不可静默切换。</Text> : null}
				{!editablePlans.length ? <Alert className="mt-3" type="warning" showIcon message="没有可编辑的建设计划" /> : null}
			</Card>
			{state.metadata ? (
				<Card className="mb-4 border-blue-200 bg-blue-50/40" size="small">
					<div className="flex flex-wrap items-center justify-between gap-2">
						<div>
							<Text strong>{state.metadata.projectName}</Text>
							<Text className="ml-2" type="secondary">{state.metadata.fileName} · {state.metadata.modelCount} 个模型</Text>
						</div>
					</div>
					<Collapse
						ghost
						size="small"
						items={[{
							key: "context-package-technical",
							label: "技术详情",
							children: <Text type="secondary">内部包版本：{state.metadata.schemaVersion} · 包标识：{state.metadata.packageId} · 完整性校验：{state.metadata.checksum}</Text>,
						}]}
					/>
					{state.metadata.inspectionMode === "SOURCE_PROJECT_STATIC" ? (
						<Alert
							className="mt-2"
							type="info"
							showIcon
							message="已从 dbt 源项目生成普通模型候选"
							description="系统只读取 SQL 中显式的 ref/source、分层标签和 models.tsv 清单，不会运行 SQL。无法证明的业务语义会在预检中逐模型列出，不会把整个项目降级为旧格式。"
						/>
					) : null}
				</Card>
			) : null}
			{contextLoading ? <Skeleton active paragraph={{ rows: 5 }} /> : null}
			{contextError ? (
				<Alert
					className="mb-4"
					type="error"
					showIcon
					message={contextError}
					action={<Button onClick={() => setContextReloadNonce((value) => value + 1)}>重试</Button>}
				/>
			) : null}
			{!contextLoading && state.planId ? (
				<div className="grid gap-4 lg:grid-cols-2">
					<Card
						title="业务分类映射"
						extra={<Button type="link" icon={<Wrench size={14} />} onClick={() => navigateRepair("categories")}>管理业务分类</Button>}
					>
						<div className="space-y-4">
							{domainCodes.length ? domainCodes.map((code) => (
								<div key={code}>
									<Text strong>{code}</Text>
									<Select
										className="mt-1 w-full"
										placeholder="选择计划内已确认业务分类"
										value={state.domainMappings[code]}
										options={domainOptions}
										onChange={(domainId) => dispatch({ type: "DOMAIN_MAPPED", code, domainId })}
									/>
								</div>
							)) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="包内没有需要映射的业务分类" />}
						</div>
					</Card>
					<Card
						title="物理来源映射"
						extra={<Button type="link" icon={<Wrench size={14} />} onClick={() => navigateRepair("sources")}>管理规划来源</Button>}
					>
						<div className="space-y-4">
							{sourceIds.length ? sourceIds.map((uniqueId) => {
								const source = state.modelPackage?.sources.find((item) => item.dbtUniqueId === uniqueId);
								return (
									<div key={uniqueId}>
										<Text strong>{source?.name || uniqueId}</Text>
										<Select
											className="mt-1 w-full"
											placeholder="选择计划内已确认来源"
											value={state.sourceMappings[uniqueId]}
											options={sourceOptions}
											onChange={(bindingId) => dispatch({ type: "SOURCE_MAPPED", uniqueId, bindingId })}
										/>
										<Collapse
											ghost
											size="small"
											items={[{
												key: `${uniqueId}-technical`,
												label: "技术详情",
												children: <Text type="secondary">dbt 来源标识：{uniqueId}</Text>,
											}]}
										/>
									</div>
								);
							}) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="包内没有外部物理来源" />}
						</div>
					</Card>
				</div>
			) : null}
		</div>
	);

	const previewStep = state.preview ? (
		<div className="py-2">
			{sourceProjectStaticPreview ? (
				<Alert
					className="mb-4"
					type="info"
					showIcon
					message="本批次来自 dbt 源项目安全解析"
					description="系统没有执行 dbt 或模型 SQL；该事实会随预检批次保留，恢复页面后仍可核对。"
				/>
			) : null}
			<div className="mb-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-6">
				{[
					["候选", state.preview.summary.total],
					["可导入", state.preview.summary.ready],
					["新增", state.preview.summary.create],
					["更新", state.preview.summary.update],
					["跳过", state.preview.summary.skip],
					["阻断/冲突", state.preview.summary.blocked + state.preview.summary.conflict],
				].map(([label, value]) => (
					<div key={String(label)} className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
						<div className="text-xs text-slate-500">{label}</div>
						<div className="mt-1 text-2xl font-semibold text-slate-950">{value}</div>
					</div>
				))}
			</div>
			<Alert
				className="mb-4"
				type={state.preview.summary.blocked || state.preview.summary.conflict ? "warning" : "success"}
				showIcon
				message={`预检已完成，当前选择 ${state.selectedUniqueIds.length} 个可导入候选`}
				description="预检不是导入成功；确认后才会按依赖拓扑创建或修订模型。阻断和冲突项不能选择。"
			/>
			{!previewApplicable ? (
				<Alert
					className="mb-4"
					type="error"
					showIcon
					message="该预检已失效或过期，不能继续应用"
					description="为防止计划、来源版本或模型 revision 漂移，请重新上传或确认当前包与上下文后再次预检。"
					action={<Button onClick={invalidatePreview}>重新预检</Button>}
				/>
			) : null}
			<Input.Search
				allowClear
				className="mb-3 max-w-md"
				placeholder="搜索 dbt 节点、模型名称、类型或分层"
				value={previewSearch}
				onChange={(event) => setPreviewSearch(event.target.value)}
			/>
			<div className="hidden md:block">
				<Table<ModelSpecImportPreviewItem>
					rowKey="dbtUniqueId"
					size="small"
					pagination={{ pageSize: 20, showSizeChanger: false }}
					scroll={{ x: 940 }}
					columns={previewColumns}
					dataSource={previewItems}
					rowSelection={{
						selectedRowKeys: state.selectedUniqueIds,
						getCheckboxProps: (item) => ({
							disabled: !isSelectablePreviewItem(item),
							"aria-label": `${proposedValue(item, "name")} ${isSelectablePreviewItem(item) ? "可选择" : "不可选择"}`,
						}),
						onChange: (keys) => dispatch({ type: "SELECTION_CHANGED", selectedUniqueIds: keys.map(String) }),
					}}
					expandable={{
						expandedRowRender: (item) => (
							<div className="grid gap-3 bg-slate-50 p-3 lg:grid-cols-3">
								<div><Text type="secondary">粒度</Text><div>{proposedGrain(item)}</div></div>
								<div><Text type="secondary">实现方式</Text><div>{conversionLabels[item.conversionMode]}</div></div>
								<div>
									<Text type="secondary">问题与修复</Text>
									{itemIssues(item).map((issue) => {
										const target = issueRepairTarget(issue);
										return (
											<div key={`${issue.code}-${issue.fieldPath || ""}`} className="mt-1">
												{issueMessage(issue)}
												{target ? <Button type="link" size="small" onClick={() => navigateRepair(target)}>去修复</Button> : null}
											</div>
										);
									})}
								</div>
								<div className="lg:col-span-3">
									<Collapse
										ghost
										size="small"
										items={[{
											key: `${item.dbtUniqueId}-technical`,
											label: "技术详情",
											children: (
												<div className="space-y-1 text-xs text-slate-600">
													<div>dbt 节点标识：{item.dbtUniqueId}</div>
													{itemIssues(item).map((issue) => (
														<div key={`${issue.code}-${issue.fieldPath || ""}`}>
															问题代码：{issue.code}{issue.fieldPath ? ` · 字段：${issue.fieldPath}` : ""}
														</div>
													))}
												</div>
											),
										}]}
									/>
								</div>
							</div>
						),
					}}
				/>
			</div>
			<div className="space-y-3 md:hidden">
				{previewItems.map((item) => (
					<Card key={item.dbtUniqueId} size="small">
						<div className="flex items-start justify-between gap-3">
							<div className="min-w-0">
								<div className="truncate font-medium">{proposedValue(item, "name")}</div>
							</div>
							<Tag color={actionColors[item.action]}>{actionLabels[item.action]}</Tag>
						</div>
						<Divider className="!my-3" />
						<Space size={4} wrap>
							<Tag>{proposedValue(item, "modelType")}</Tag>
							<Tag>{proposedValue(item, "layer")}</Tag>
						</Space>
						<div className="text-sm">{conversionLabels[item.conversionMode]}</div>
						{itemIssues(item).length ? (
							<div className="mt-2 space-y-1">
								{itemIssues(item).map((issue) => (
									<div key={`${issue.code}-${issue.fieldPath || ""}`} className="text-xs text-slate-700">
										{issueMessage(issue)}
									</div>
								))}
							</div>
						) : <Text className="mt-2 block" type="secondary">可导入</Text>}
						<Collapse
							ghost
							size="small"
							items={[{
								key: `${item.dbtUniqueId}-mobile-technical`,
								label: "技术详情",
								children: (
									<div className="space-y-1 text-xs text-slate-600">
										<div>dbt 节点标识：{item.dbtUniqueId}</div>
										{itemIssues(item).map((issue) => (
											<div key={`${issue.code}-${issue.fieldPath || ""}`}>
												问题代码：{issue.code}{issue.fieldPath ? ` · 字段：${issue.fieldPath}` : ""}
											</div>
										))}
									</div>
								),
							}]}
						/>
						<Button
							className="mt-3"
							size="small"
							disabled={!isSelectablePreviewItem(item)}
							onClick={() => {
								const selected = new Set(state.selectedUniqueIds);
								if (selected.has(item.dbtUniqueId)) selected.delete(item.dbtUniqueId);
								else selected.add(item.dbtUniqueId);
								dispatch({ type: "SELECTION_CHANGED", selectedUniqueIds: [...selected] });
							}}
						>
							{state.selectedUniqueIds.includes(item.dbtUniqueId) ? "取消选择" : "选择候选"}
						</Button>
					</Card>
				))}
			</div>
		</div>
	) : (
		<Empty description="尚未生成预检结果" />
	);

	const resultStep = state.result ? (
		<div className="py-2">
			<div className="mb-5 flex flex-col gap-3 rounded-2xl border border-slate-200 bg-slate-50 p-5 sm:flex-row sm:items-center sm:justify-between">
				<div>
					<div className="flex items-center gap-2 text-lg font-semibold text-slate-950">
						<CheckCircle2 className={failedCount || blockedCount ? "text-amber-600" : "text-emerald-600"} size={22} />
						{failedCount ? "导入已完成，但仍有失败项" : blockedCount ? "导入已完成，但仍有阻断项" : "模型包已完成导入"}
					</div>
					<Text type="secondary">结果来自服务端批次真值；刷新页面后可通过 runId 继续回看。</Text>
					{blockedCount ? <Text className="block" type="warning">阻断项不能通过失败重试执行，请先修复上下文后重新预检。</Text> : null}
				</div>
				<Tag color={state.result.status === "SUCCEEDED" ? "green" : "orange"}>{state.result.status}</Tag>
			</div>
			<div className="mb-4 grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
				{[
					["创建", state.result.summary.created],
					["更新", state.result.summary.updated],
					["重放", state.result.summary.replayed],
					["跳过", state.result.summary.skipped],
					["失败", state.result.summary.failed],
					["阻断", state.result.summary.blocked],
				].map(([label, value]) => (
					<div key={String(label)} className="rounded-xl border border-slate-200 px-4 py-3 text-center">
						<div className="text-xs text-slate-500">{label}</div>
						<div className="mt-1 text-xl font-semibold">{value}</div>
					</div>
				))}
			</div>
			<div className="space-y-3">
				{state.result.items.map((item: ModelSpecImportResultItem) => (
					<Card key={`${item.sequence || 0}-${item.dbtUniqueId}`} size="small">
						<div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
							<div className="min-w-0">
								<div className="flex flex-wrap items-center gap-2">
									<Tag color={item.status === "FAILED" || item.status === "BLOCKED" ? "red" : "green"}>
										{resultLabels[item.status] || item.status}
									</Tag>
									<span className="font-medium">{previewNameByUniqueId.get(item.dbtUniqueId) || "模型导入候选"}</span>
								</div>
								{item.issues?.map((issue) => <div key={issue.code} className="mt-1 text-sm text-red-700">{issueMessage(issue)}</div>)}
								<Collapse
									ghost
									size="small"
									items={[{
										key: `${item.dbtUniqueId}-result-technical`,
										label: "技术详情",
										children: (
											<div className="space-y-1 text-xs text-slate-600">
												<div>dbt 节点标识：{item.dbtUniqueId}</div>
												<div>模型修订 r{item.revision || "-"} · 实现修订 i{item.implementationRevision || "-"} · 工件 {item.artifactCount}</div>
												{item.issues?.map((issue) => <div key={issue.code}>问题代码：{issue.code}</div>)}
											</div>
										),
									}]}
								/>
							</div>
							{item.modelSpecId ? (
								<Button
									icon={<ExternalLink size={14} />}
									onClick={() => onNavigate(buildImportedModelRoute(item.modelSpecId!, state.planId))}
								>
									查看模型
								</Button>
							) : null}
						</div>
					</Card>
				))}
			</div>
			<div className="mt-5 flex flex-wrap gap-2">
				<Button onClick={() => onNavigate(buildImportPlanRoute(state.planId))}>返回当前计划</Button>
				<Button onClick={() => onNavigate(`/modeling/models?planId=${encodeURIComponent(state.planId)}`)}>查看模型中心</Button>
				<Button
					type="primary"
					danger
					icon={<RefreshCw size={14} />}
					disabled={!retryAllowed}
					loading={state.busy}
					onClick={() => void retryFailures()}
				>
					仅重试失败项（{failedCount}）
				</Button>
			</div>
		</div>
	) : (
		<Empty description="尚未提交导入" />
	);

	const nextDisabled =
		state.busy ||
		!canEdit ||
		(state.step === 0 && !state.modelPackage) ||
		(state.step === 1 && (!selectedPlanEditable || !hasPreviewContext(state))) ||
		(state.step === 2 && (!selectedPlanEditable || !state.preview || !previewApplicable || state.selectedUniqueIds.length === 0));

	const footer = state.step < 3 ? (
		<div className="flex items-center justify-between gap-3">
			{state.step === 2 ? (
				<Button
					disabled={state.busy}
					icon={<RefreshCw size={15} />}
					onClick={invalidatePreview}
				>
					重新上传并预检
				</Button>
			) : (
				<Button
					disabled={state.busy || state.step === 0}
					icon={<ArrowLeft size={15} />}
					onClick={() => dispatch({ type: "BACK" })}
				>
					上一步
				</Button>
			)}
			<Space>
				<Button disabled={state.busy} onClick={closeWizard}>稍后继续</Button>
				{state.step === 0 ? (
					<Button type="primary" disabled={nextDisabled} onClick={() => dispatch({ type: "PLAN_SELECTED", planId: lockedPlanId || state.planId })}>
						下一步
					</Button>
				) : state.step === 1 ? (
					<Button type="primary" loading={state.busy} disabled={nextDisabled} onClick={() => void runPreview()}>
						开始预检
					</Button>
				) : (
					<Button type="primary" loading={state.busy} disabled={nextDisabled} onClick={() => void applyImport()}>
						确认导入（{state.selectedUniqueIds.length}）
					</Button>
				)}
			</Space>
		</div>
	) : undefined;

	return (
		<Drawer
			open={open}
			width={1120}
			destroyOnClose={false}
			maskClosable={!state.busy}
			keyboard={!state.busy}
			closable={!state.busy}
			title={
				<div className="flex items-center gap-3">
					<div className="rounded-lg bg-blue-50 p-2 text-blue-700"><Archive size={20} /></div>
					<div>
						<div>导入已有模型</div>
						<div className="text-xs font-normal text-slate-500">
							{selectedPlan ? `目标计划：${selectedPlan.name}` : "上传 dbt ZIP，自动转换为内部模型包并纳入普通模型主线"}
						</div>
					</div>
				</div>
			}
			footer={footer}
			styles={{ body: { paddingTop: 16 }, footer: { padding: "12px 20px" } }}
			onClose={closeWizard}
		>
			<Steps
				className="mb-4"
				current={state.step}
				responsive
				items={[
					{ title: "上传 dbt ZIP", icon: <Archive size={16} /> },
					{ title: "建设上下文" },
					{ title: "预检确认" },
					{ title: "导入结果" },
				]}
			/>
			{state.error ? (
				<Alert
					className="mb-4"
					type="error"
					showIcon
					message={state.error}
					action={
						failedRestoreRunIdRef.current ? (
							<Button
								size="small"
								onClick={() => {
									failedRestoreRunIdRef.current = "";
									setRunReloadNonce((value) => value + 1);
								}}
							>
								重新加载批次
							</Button>
						) : undefined
					}
					closable
					onClose={() => dispatch({ type: "CLEAR_ERROR" })}
				/>
			) : null}
			{state.busy && state.step !== 3 ? <Alert className="mb-4" type="info" showIcon message="正在读取服务端真值，请勿重复提交" /> : null}
			{state.step === 0 ? uploadStep : null}
			{state.step === 1 ? contextStep : null}
			{state.step === 2 ? previewStep : null}
			{state.step === 3 ? resultStep : null}
			{!canEdit ? <Alert className="mt-4" type="info" showIcon message="当前账号为只读浏览，不能执行模型包导入" /> : null}
			<Collapse
				ghost
				size="small"
				className="mt-4"
				items={[{
					key: "import-session-technical",
					label: "技术详情",
					children: (
						<div className="space-y-1 text-xs text-slate-600">
							<div>入口：{returnSurface === "workbench" ? "数据建设工作台" : "模型中心"}</div>
							<div>批次标识：{state.runId || "预检后生成"}</div>
							{state.diagnosticCode ? <div>诊断码：{state.diagnosticCode}</div> : null}
						</div>
					),
				}]}
			/>
		</Drawer>
	);
}
