import { Alert, Button, Card, Descriptions, Form, Modal, Space, Spin } from "antd";
import { RefreshCw } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
	getModelLifecycle,
	getModelSpec,
	getModelSpecStageGates,
	listModelSpecs,
	type ModelLifecycleTimeline,
	type ModelSpecStageGate,
	retryModelReleaseRegistration,
	updateModelSpec,
} from "@/api/modelSpecApi";
import { getWarehousePlanSources, type WarehousePlanSourceInventoryView } from "@/api/warehousePlanApi";
import { useCatalogDomainOptions } from "@/hooks/useCatalogDomainOptions";
import { useUserRoles } from "@/store/userStore";
import { ModelSpecBlockerPanel } from "./components/ModelSpecBlockerPanel";
import { ModelSpecDetailHeader } from "./components/ModelSpecDetailHeader";
import { ModelSpecDetailNotices } from "./components/ModelSpecDetailNotices";
import { ModelSpecEditorCanvas } from "./components/ModelSpecEditorCanvas";
import { ModelSpecImplementationMigrationPanel } from "./components/ModelSpecImplementationMigrationPanel";
import {
	ModelSpecImplementationStage,
	type ModelSpecImplementationStageActionRef,
} from "./components/ModelSpecImplementationStage";
import { ModelSpecLogicalDesignStage, type ModelSpecSelectOption } from "./components/ModelSpecLogicalDesignStage";
import { ModelSpecPhysicalAssetStage } from "./components/ModelSpecPhysicalAssetStage";
import { ModelSpecSourceInventoryModal } from "./components/ModelSpecSourceInventoryModal";
import { type ModelSpecDetailPageProps, useModelSpecDetailRouteAdapter } from "./modelSpecDetailRouteAdapter";
import { getModelSpecDetailStageProjection } from "./modelSpecDetailStageProjection";
import {
	handleModelSpecFormValidationError,
	type ModelSpecIssueFormPath,
	modelSpecIssueFieldPath,
	stableModelSpecFormValue,
} from "./modelSpecIssueFieldPath";
import { resolveModelSpecReferenceTargets } from "./modelSpecReferenceResolution";
import {
	type ModelSpecSourceChoice,
	modelSpecSourceInventoryState,
	modelSpecSourcePermissionDenied,
	selectableModelSpecSources,
	withPinnedExistingSources,
} from "./modelSpecSourceSelection";
import {
	type CanonicalModelSpecView,
	hasModelSpecTypeBoundaryMismatch,
	isCanonicalModelSpecReferenceTarget,
	isModelSpecDirectInputLayerAllowed,
	isModelSpecReferenceTargetAllowed,
	MODEL_SPEC_TARGET_LAYER_BY_TYPE,
	type ModelSpecCasToken,
	type ModelSpecRevisionConflictDetails,
	type ModelSpecStandardBinding,
	type ModelSpecView,
	modelSpecRevisionRefKey,
	validateModelSpecUpdate,
} from "./modelSpecV2Contract";
import {
	buildModelSpecUpdateCommand,
	isModelSpecStatusReadonly,
	MODEL_TYPE_LABELS,
	type ModelSpecDraft,
	modelSpecDraftFromView,
	modelSpecErrorMessage,
	modelSpecIssueMessage,
	modelSpecRevisionConflict,
	modelSpecServerIssues,
} from "./modelSpecWorkbench";
import { hasWarehousePlanCreateAccess } from "./warehousePlanCreateFlow";

export default function ModelSpecDetailPage(props: ModelSpecDetailPageProps = {}) {
	const { embedded = false } = props;
	const { activeStage, changeStage, modelSpecId, navigate, notifyResolvedContext, returnToCatalog } =
		useModelSpecDetailRouteAdapter(props);
	const userRoles = useUserRoles();
	const roleAllowsEdit = hasWarehousePlanCreateAccess(userRoles);
	const [form] = Form.useForm<ModelSpecDraft>();
	const logicalFormValues = Form.useWatch([], { form, preserve: true });
	const [model, setModel] = useState<ModelSpecView | null>(null);
	const [availableModels, setAvailableModels] = useState<CanonicalModelSpecView[]>([]);
	const [dependencyMetadataLoaded, setDependencyMetadataLoaded] = useState(false);
	const [referenceTargets, setReferenceTargets] = useState<Record<string, ModelSpecView | null>>({});
	const [referenceMetadataLoaded, setReferenceMetadataLoaded] = useState(false);
	const [referenceResolutionFailed, setReferenceResolutionFailed] = useState(false);
	const [stageGates, setStageGates] = useState<ModelSpecStageGate[]>([]);
	const [sourceOptions, setSourceOptions] = useState<ModelSpecSourceChoice[]>([]);
	const [gateLoading, setGateLoading] = useState(false);
	const [sourceLoading, setSourceLoading] = useState(false);
	const [gateError, setGateError] = useState("");
	const [sourceError, setSourceError] = useState("");
	const [sourcePermissionDenied, setSourcePermissionDenied] = useState(false);
	const [sourceInventoryOpen, setSourceInventoryOpen] = useState(false);
	const [loading, setLoading] = useState(true);
	const [saving, setSaving] = useState(false);
	const [loadError, setLoadError] = useState("");
	const [saveError, setSaveError] = useState("");
	const [writeDenied, setWriteDenied] = useState(false);
	const [statusChanged, setStatusChanged] = useState(false);
	const [conflict, setConflict] = useState<ModelSpecRevisionConflictDetails | null>(null);
	const loadRequestRef = useRef(0);
	const referenceRequestRef = useRef(0);
	const sourceRequestRef = useRef(0);
	const gateRequestRef = useRef(0);
	const physicalRequestRef = useRef(0);
	const releaseRetryRequestRef = useRef(0);
	const implementationActionRef = useRef<ModelSpecImplementationStageActionRef>(null);
	const [implementationState, setImplementationState] = useState({ configured: false, dirty: false, validated: false });
	const [physicalTimeline, setPhysicalTimeline] = useState<ModelLifecycleTimeline | null>(null);
	const [physicalLoading, setPhysicalLoading] = useState(false);
	const [physicalError, setPhysicalError] = useState("");
	const { labelByKey } = useCatalogDomainOptions();
	const canonicalModel = model?.compatibilityMode === "CANONICAL" ? model : null;
	const persistedLogicalDraft = useMemo(
		() => (canonicalModel ? modelSpecDraftFromView(canonicalModel) : null),
		[canonicalModel],
	);
	const logicalDirty = useMemo(
		() =>
			Boolean(
				persistedLogicalDraft &&
					logicalFormValues &&
					JSON.stringify(stableModelSpecFormValue(logicalFormValues)) !==
						JSON.stringify(stableModelSpecFormValue(persistedLogicalDraft)),
			),
		[logicalFormValues, persistedLogicalDraft],
	);
	const statusAllowsEdit = canonicalModel?.status === "DRAFT";
	const targetLayerMismatch = Boolean(
		canonicalModel && MODEL_SPEC_TARGET_LAYER_BY_TYPE[canonicalModel.modelType] !== canonicalModel.layer,
	);
	const directInputLayerMismatch = Boolean(
		canonicalModel &&
			(canonicalModel.modelType === "DIMENSION" || canonicalModel.modelType === "FACT") &&
			canonicalModel.sourceRefs.some((source) => !isModelSpecDirectInputLayerAllowed(source.layer)),
	);
	const typeBoundaryMismatch = Boolean(canonicalModel && hasModelSpecTypeBoundaryMismatch(canonicalModel));
	const upstreamDependencyMismatch = Boolean(
		referenceMetadataLoaded &&
			!referenceResolutionFailed &&
			canonicalModel?.dependsOn.some((reference) => {
				const target = referenceTargets[modelSpecRevisionRefKey(reference)];
				if (!target) return true;
				return !isModelSpecReferenceTargetAllowed(canonicalModel, target, "DEPENDENCY");
			}),
	);
	const dimensionReferenceMismatch = Boolean(
		referenceMetadataLoaded &&
			!referenceResolutionFailed &&
			canonicalModel?.dimensionRefs.some((reference) => {
				const target = referenceTargets[modelSpecRevisionRefKey(reference)];
				if (!target) return true;
				return !isModelSpecReferenceTargetAllowed(canonicalModel, target, "DIMENSION");
			}),
	);
	const dependencyContractMismatch =
		targetLayerMismatch ||
		directInputLayerMismatch ||
		typeBoundaryMismatch ||
		upstreamDependencyMismatch ||
		dimensionReferenceMismatch;
	const canEdit = Boolean(
		canonicalModel &&
			statusAllowsEdit &&
			roleAllowsEdit &&
			!writeDenied &&
			!statusChanged &&
			referenceMetadataLoaded &&
			!referenceResolutionFailed &&
			!dependencyContractMismatch,
	);
	const currentImplementation = physicalTimeline?.implementation || null;
	const currentImplementationRevision = currentImplementation?.implementationRevision;
	const currentImplementationChecksum = currentImplementation?.implementationChecksum;
	const advancedImplementationReady = Boolean(
		canonicalModel &&
			currentImplementation &&
			currentImplementationRevision &&
			currentImplementationChecksum &&
			currentImplementation.modelSpecId === canonicalModel.id &&
			currentImplementation.planId === canonicalModel.planId &&
			currentImplementation.revision === canonicalModel.revision &&
			currentImplementation.modelChecksum === canonicalModel.checksum &&
			currentImplementation.ownership === canonicalModel.implementationMode,
	);
	const implementationPath =
		advancedImplementationReady && canonicalModel
			? `/studio/sql-modeling?planId=${encodeURIComponent(canonicalModel.planId)}&modelSpecId=${encodeURIComponent(canonicalModel.id)}&revision=${canonicalModel.revision}&implementationRevision=${currentImplementationRevision}&implementationChecksum=${encodeURIComponent(currentImplementationChecksum || "")}&implementationMode=${encodeURIComponent(canonicalModel.implementationMode)}`
			: "";
	const implementationRecoveryMessage =
		"当前实现绑定缺失或已不是此 ModelSpec 的当前版本；请返回数据实现阶段刷新并保存新的实现 revision。";
	const loadStageGates = useCallback(async () => {
		const requestId = ++gateRequestRef.current;
		setGateLoading(true);
		setGateError("");
		try {
			const gates = await getModelSpecStageGates(modelSpecId);
			if (requestId !== gateRequestRef.current) return;
			setStageGates(gates);
		} catch {
			if (requestId !== gateRequestRef.current) return;
			setStageGates([]);
			setGateError("暂时无法读取服务端门禁结果，不影响继续编辑草稿");
		} finally {
			if (requestId === gateRequestRef.current) setGateLoading(false);
		}
	}, [modelSpecId]);
	const loadPhysicalTimeline = useCallback(async () => {
		const requestId = ++physicalRequestRef.current;
		setPhysicalLoading(true);
		setPhysicalError("");
		try {
			const timeline = await getModelLifecycle(modelSpecId);
			if (requestId !== physicalRequestRef.current) return;
			setPhysicalTimeline(timeline);
		} catch {
			if (requestId !== physicalRequestRef.current) return;
			// A transient physical read must not erase earlier logical or implementation state.
			setPhysicalError("物理资产与运行证据暂时无法读取；此前已显示的阶段数据仍被保留。");
		} finally {
			if (requestId === physicalRequestRef.current) setPhysicalLoading(false);
		}
	}, [modelSpecId]);
	const loadSources = useCallback(
		async (
			planId: string,
			existingSources: ModelSpecDraft["sources"],
		): Promise<WarehousePlanSourceInventoryView | null> => {
			const requestId = ++sourceRequestRef.current;
			setSourceLoading(true);
			setSourceError("");
			setSourcePermissionDenied(false);
			try {
				const inventory = await getWarehousePlanSources(planId);
				if (requestId !== sourceRequestRef.current) return null;
				const choices = selectableModelSpecSources(inventory.bindings);
				const currentSources =
					(form.getFieldValue("sources") as ModelSpecDraft["sources"] | undefined) ?? existingSources;
				setSourceOptions(withPinnedExistingSources(choices, currentSources));
				const state = modelSpecSourceInventoryState(inventory.bindings);
				if (state === "EMPTY") {
					setSourceError(
						"当前计划尚未登记具体来源，不影响保存草稿。可锁定上游模型；或点击“在当前表单登记来源”，按已验证连接 → Schema → 具体表确认纳入；若没有可选表再执行元数据同步",
					);
				} else if (state === "FORBIDDEN") {
					setSourcePermissionDenied(true);
					setSourceError("当前账号无权核验规划来源，请联系计划负责人或管理员授权");
				} else if (state === "UNAVAILABLE") {
					setSourceError("当前计划已有来源，但尚未确认、已失效或版本需要刷新，请先完善来源盘点");
				}
				return inventory;
			} catch (error) {
				if (requestId !== sourceRequestRef.current) return null;
				const currentSources =
					(form.getFieldValue("sources") as ModelSpecDraft["sources"] | undefined) ?? existingSources;
				setSourceOptions(withPinnedExistingSources([], currentSources));
				const denied = modelSpecSourcePermissionDenied(error);
				setSourcePermissionDenied(denied);
				setSourceError(
					denied
						? "当前账号无权核验规划来源，请联系计划负责人或管理员授权"
						: "规划来源暂时无法核验；已保存来源仅供诊断，当前输入不会被清空",
				);
				return null;
			} finally {
				if (requestId === sourceRequestRef.current) setSourceLoading(false);
			}
		},
		[form],
	);
	const load = useCallback(async () => {
		const requestId = ++loadRequestRef.current;
		setLoading(true);
		setSaving(false);
		setLoadError("");
		setSaveError("");
		setConflict(null);
		setStatusChanged(false);
		setStageGates([]);
		setGateError("");
		setAvailableModels([]);
		setDependencyMetadataLoaded(false);
		setReferenceTargets({});
		setReferenceMetadataLoaded(false);
		setReferenceResolutionFailed(false);
		const referenceRequestId = ++referenceRequestRef.current;
		gateRequestRef.current += 1;
		setSourceOptions([]);
		setSourceError("");
		setSourcePermissionDenied(false);
		setSourceInventoryOpen(false);
		physicalRequestRef.current += 1;
		setPhysicalTimeline(null);
		setPhysicalLoading(false);
		setPhysicalError("");
		try {
			const [detail, gates] = await Promise.all([
				getModelSpec(modelSpecId),
				getModelSpecStageGates(modelSpecId).catch(() => null),
			]);
			if (requestId !== loadRequestRef.current) return;
			setModel(detail);
			if (detail.planId) notifyResolvedContext({ modelSpecId: detail.id, planId: detail.planId });
			if (gates) setStageGates(gates);
			else setGateError("暂时无法读取服务端门禁结果，不影响继续编辑草稿");
			setWriteDenied(false);
			if (detail.compatibilityMode === "CANONICAL") {
				form.resetFields();
				form.setFieldsValue(modelSpecDraftFromView(detail));
			}
			const [list, referenceResolution] = await Promise.all([
				listModelSpecs().catch(() => null),
				resolveModelSpecReferenceTargets(detail),
			]);
			if (requestId !== loadRequestRef.current || referenceRequestId !== referenceRequestRef.current) return;
			setReferenceTargets(referenceResolution.targets);
			setReferenceResolutionFailed(referenceResolution.failed);
			setReferenceMetadataLoaded(true);
			if (list) {
				const candidates = (Array.isArray(list) ? list : []).filter(
					(candidate): candidate is CanonicalModelSpecView =>
						isCanonicalModelSpecReferenceTarget(candidate) && candidate.id !== modelSpecId,
				);
				setAvailableModels(candidates);
				setDependencyMetadataLoaded(true);
			} else {
				setAvailableModels([]);
				setDependencyMetadataLoaded(false);
			}
		} catch (error) {
			if (requestId !== loadRequestRef.current) return;
			setModel(null);
			setLoadError(modelSpecErrorMessage(error));
		} finally {
			if (requestId === loadRequestRef.current) setLoading(false);
		}
	}, [form, modelSpecId, notifyResolvedContext]);
	useEffect(() => {
		void load();
		return () => {
			loadRequestRef.current += 1;
			referenceRequestRef.current += 1;
			sourceRequestRef.current += 1;
			gateRequestRef.current += 1;
			physicalRequestRef.current += 1;
			releaseRetryRequestRef.current += 1;
		};
	}, [load]);

	useEffect(() => {
		if (!canonicalModel || activeStage !== "implementation") return;
		const currentSources = (form.getFieldValue("sources") as ModelSpecDraft["sources"] | undefined) || [];
		void loadSources(canonicalModel.planId, currentSources);
	}, [activeStage, canonicalModel, form, loadSources]);

	useEffect(() => {
		if (activeStage !== "logical") void loadPhysicalTimeline();
	}, [activeStage, loadPhysicalTimeline]);

	const refreshReferenceTargetsAfterSave = async (
		updated: CanonicalModelSpecView,
		pageRequestId: number,
	): Promise<boolean> => {
		const referenceRequestId = ++referenceRequestRef.current;
		setReferenceTargets({});
		setReferenceMetadataLoaded(false);
		setReferenceResolutionFailed(false);
		const referenceResolution = await resolveModelSpecReferenceTargets(updated);
		if (pageRequestId !== loadRequestRef.current || referenceRequestId !== referenceRequestRef.current) return false;
		setReferenceTargets(referenceResolution.targets);
		setReferenceResolutionFailed(referenceResolution.failed);
		setReferenceMetadataLoaded(true);
		return true;
	};

	const selectableModels = useMemo(() => {
		if (!canonicalModel) return [];
		return availableModels.filter(
			(candidate) => candidate.planId === canonicalModel.planId || candidate.status === "PUBLISHED",
		);
	}, [availableModels, canonicalModel]);
	const upstreamOptions = useMemo<ModelSpecSelectOption[]>(
		() =>
			selectableModels.map((candidate) => {
				const allowed = canonicalModel
					? isModelSpecReferenceTargetAllowed(canonicalModel, candidate, "DEPENDENCY")
					: false;
				return {
					value: candidate.id,
					label: `${candidate.name} · ${MODEL_TYPE_LABELS[candidate.modelType]} · ${candidate.layer} · r${
						candidate.revision
					}${allowed ? "" : "（不符合当前模型依赖）"}`,
					disabled: !allowed,
					revision: candidate.revision,
					checksum: candidate.checksum,
				};
			}),
		[canonicalModel, selectableModels],
	);
	const implementationUpstreamOptions = useMemo<ModelSpecSelectOption[]>(
		() =>
			selectableModels.map((candidate) => {
				const allowed =
					canonicalModel?.modelType === "DIMENSION" ||
					Boolean(canonicalModel && isModelSpecReferenceTargetAllowed(canonicalModel, candidate, "DEPENDENCY"));
				return {
					value: candidate.id,
					label: `${candidate.name} · ${MODEL_TYPE_LABELS[candidate.modelType]} · ${candidate.layer} · r${
						candidate.revision
					}${allowed ? "" : "（不符合当前模型实现输入）"}`,
					disabled: !allowed,
					revision: candidate.revision,
					checksum: candidate.checksum,
				};
			}),
		[canonicalModel, selectableModels],
	);
	const dimensionOptions = useMemo<ModelSpecSelectOption[]>(
		() =>
			selectableModels
				.filter((candidate) =>
					canonicalModel ? isModelSpecReferenceTargetAllowed(canonicalModel, candidate, "DIMENSION") : false,
				)
				.map((candidate) => ({
					value: candidate.id,
					label: `${candidate.name} · DIMENSION · DWD · r${candidate.revision}`,
					revision: candidate.revision,
				})),
		[canonicalModel, selectableModels],
	);
	const save = async (override?: ModelSpecCasToken) => {
		if (!canonicalModel || !canEdit) return;
		const pageRequestId = loadRequestRef.current;
		setSaveError("");
		try {
			await form.validateFields();
			const values = form.getFieldsValue(true);
			const command = buildModelSpecUpdateCommand(values, selectableModels);
			const issues = validateModelSpecUpdate(command);
			if (issues.length > 0) {
				const fields = (form.getFieldValue("fields") as ModelSpecDraft["fields"] | undefined) || [];
				form.setFields(
					issues.map((issue) => ({
						name: modelSpecIssueFieldPath(issue.field, fields),
						errors: [modelSpecIssueMessage(issue.code)],
					})),
				);
				setSaveError("请补齐标红字段后再保存");
				return;
			}
			setSaving(true);
			const updated = await updateModelSpec(
				override || { id: canonicalModel.id, revision: canonicalModel.revision, checksum: canonicalModel.checksum },
				command,
			);
			if (pageRequestId !== loadRequestRef.current) return;
			setModel(updated);
			form.resetFields();
			form.setFieldsValue(modelSpecDraftFromView(updated));
			if (!(await refreshReferenceTargetsAfterSave(updated, pageRequestId))) return;
			void loadStageGates();
			setConflict(null);
			setStatusChanged(false);
			setSaveError("");
		} catch (error) {
			if (handleModelSpecFormValidationError(error, form, setSaveError)) return;
			if (pageRequestId !== loadRequestRef.current) return;
			const latest = modelSpecRevisionConflict(error);
			if (latest) setConflict(latest);
			if ((error as { response?: { status?: number } })?.response?.status === 403) setWriteDenied(true);
			if (isModelSpecStatusReadonly(error)) {
				setStatusChanged(true);
				setSaveError("");
			} else {
				const serverIssues = modelSpecServerIssues(error);
				if (serverIssues.length > 0) {
					const fields = (form.getFieldValue("fields") as ModelSpecDraft["fields"] | undefined) || [];
					const errorsByField = new Map<string, { name: ModelSpecIssueFormPath; errors: string[] }>();
					serverIssues.forEach((issue) => {
						const name = modelSpecIssueFieldPath(issue.field, fields);
						const key = JSON.stringify(name);
						const entry = errorsByField.get(key) || { name, errors: [] };
						entry.errors.push(modelSpecIssueMessage(issue.code));
						errorsByField.set(key, entry);
					});
					form.setFields(
						Array.from(errorsByField.values(), ({ name, errors }) => ({
							name,
							errors: Array.from(new Set(errors)),
						})),
					);
				}
				setSaveError(modelSpecErrorMessage(error));
			}
		} finally {
			if (pageRequestId === loadRequestRef.current) setSaving(false);
		}
	};

	const retryConflict = () => {
		if (!canonicalModel || !conflict) return;
		void save({ id: canonicalModel.id, revision: conflict.currentRevision, checksum: conflict.currentChecksum });
	};

	const onSaveStandardBindings = async (standardBindings: ModelSpecStandardBinding[]) => {
		if (!canonicalModel || !canEdit) return false;
		const pageRequestId = loadRequestRef.current;
		setSaveError("");
		try {
			const command = buildModelSpecUpdateCommand(
				{ ...modelSpecDraftFromView(canonicalModel), standardBindings },
				selectableModels,
			);
			const issues = validateModelSpecUpdate(command);
			if (issues.length > 0) {
				setSaveError(issues.map((issue) => modelSpecIssueMessage(issue.code)).join("；"));
				return false;
			}
			setSaving(true);
			const updated = await updateModelSpec(
				{ id: canonicalModel.id, revision: canonicalModel.revision, checksum: canonicalModel.checksum },
				command,
			);
			if (pageRequestId !== loadRequestRef.current) return false;
			setModel(updated);
			form.resetFields();
			form.setFieldsValue(modelSpecDraftFromView(updated));
			if (!(await refreshReferenceTargetsAfterSave(updated, pageRequestId))) return false;
			setConflict(null);
			setStatusChanged(false);
			void loadStageGates();
			return true;
		} catch (error) {
			if (pageRequestId !== loadRequestRef.current) return false;
			const latest = modelSpecRevisionConflict(error);
			if (latest) setConflict(latest);
			if ((error as { response?: { status?: number } })?.response?.status === 403) setWriteDenied(true);
			if (isModelSpecStatusReadonly(error)) {
				setStatusChanged(true);
			} else {
				setSaveError(modelSpecErrorMessage(error));
			}
			return false;
		} finally {
			if (pageRequestId === loadRequestRef.current) setSaving(false);
		}
	};

	const discardAndReload = () => {
		Modal.confirm({
			title: "加载最新版本？",
			content: "当前未保存输入将被最新版本替换。",
			okText: "加载最新版本",
			cancelText: "继续编辑",
			onOk: () => load(),
		});
	};

	const retryReleaseRegistration = useCallback(
		async (releaseId: string) => {
			const requestId = ++releaseRetryRequestRef.current;
			const pageRequestId = loadRequestRef.current;
			setPhysicalError("");
			try {
				await retryModelReleaseRegistration(modelSpecId, releaseId);
				if (requestId !== releaseRetryRequestRef.current || pageRequestId !== loadRequestRef.current) {
					return;
				}
				await loadPhysicalTimeline();
			} catch {
				if (requestId !== releaseRetryRequestRef.current || pageRequestId !== loadRequestRef.current) {
					return;
				}
				setPhysicalError("发布登记重试失败；服务端状态未被本地覆盖，请稍后重试。");
				throw new Error("MODEL_RELEASE_REGISTRATION_RETRY_FAILED");
			}
		},
		[loadPhysicalTimeline, modelSpecId],
	);

	if (loading) {
		return (
			<div className="flex min-h-[360px] items-center justify-center">
				<Spin tip="正在加载模型" />
			</div>
		);
	}

	if (!model) {
		return (
			<div className="p-4">
				<Alert
					type="error"
					showIcon
					message={loadError || "模型不可访问"}
					action={
						<Space>
							<Button size="small" onClick={() => returnToCatalog()}>
								{embedded ? "返回逻辑模型" : "返回模型中心"}
							</Button>
							<Button size="small" onClick={() => void load()}>
								<RefreshCw size={14} />
								重试
							</Button>
						</Space>
					}
				/>
			</div>
		);
	}

	const planOptions: ModelSpecSelectOption[] = model.planId ? [{ value: model.planId, label: "当前建设计划" }] : [];
	const domainOptions: ModelSpecSelectOption[] = model.domainId
		? [{ value: model.domainId, label: labelByKey[model.domainId] || "当前业务分类" }]
		: [];
	const stageProjection = canonicalModel
		? getModelSpecDetailStageProjection({
				stage: activeStage,
				logicalDirty,
				designedReady: stageGates.find((gate) => gate.stage === "DESIGNED")?.status === "READY",
				implementationConfigured: implementationState.configured,
				implementationDirty: implementationState.dirty,
				implementationValidated: implementationState.validated,
				canEdit,
				lifecycleStatus: canonicalModel.status,
			})
		: null;
	const runPrimaryAction = async () => {
		if (!stageProjection || !canonicalModel || stageProjection.primaryAction.disabled) return;
		if (stageProjection.primaryAction.recoveryStage !== activeStage) {
			changeStage(canonicalModel, stageProjection.primaryAction.recoveryStage);
			return;
		}
		if (stageProjection.primaryAction.label === "保存逻辑设计") await save();
		else if (stageProjection.primaryAction.label === "配置数据实现") await implementationActionRef.current?.save();
		else if (stageProjection.primaryAction.label === "验证实现") await implementationActionRef.current?.validate();
		else document.getElementById("model-spec-delivery-intents")?.scrollIntoView({ behavior: "smooth", block: "start" });
	};
	const primaryActionContext = {
		saving,
		advancedImplementationReady,
		implementationRecoveryMessage,
		onAction: runPrimaryAction,
	};

	return (
		<div className={embedded ? "p-3" : "p-4"} data-testid="model-spec-detail-page">
			<ModelSpecDetailHeader
				model={model}
				canonicalModel={canonicalModel}
				canEdit={canEdit}
				primaryAction={activeStage === "logical" ? stageProjection?.primaryAction : undefined}
				primaryActionContext={primaryActionContext}
				backLabel={embedded ? "返回逻辑模型" : undefined}
				onBack={() => returnToCatalog(model)}
				onReload={load}
			/>
			<ModelSpecDetailNotices
				model={model}
				canonicalModel={canonicalModel}
				roleAllowsEdit={roleAllowsEdit}
				writeDenied={writeDenied}
				statusAllowsEdit={statusAllowsEdit}
				statusChanged={statusChanged}
				dependencyContractMismatch={dependencyContractMismatch}
				referenceResolutionFailed={referenceResolutionFailed}
				saveError={saveError}
				conflict={conflict}
				onReload={load}
				onRetryConflict={retryConflict}
				onDiscardAndReload={discardAndReload}
			/>

			{canonicalModel ? (
				<ModelSpecBlockerPanel
					gates={stageGates}
					loading={gateLoading}
					error={gateError}
					onReload={() => void loadStageGates()}
				/>
			) : null}
			{canonicalModel ? (
				<ModelSpecEditorCanvas
					activeStage={activeStage}
					implementationDisabled={stageGates.find((gate) => gate.stage === "DESIGNED")?.status !== "READY"}
					onStageChange={(stage) => changeStage(canonicalModel, stage)}
					drawerAction={activeStage !== "logical" ? stageProjection?.primaryAction : undefined}
					primaryActionContext={primaryActionContext}
					logical={
						<Form form={form} layout="vertical" requiredMark={false} disabled={saving}>
							<ModelSpecLogicalDesignStage
								form={form}
								model={canonicalModel}
								planOptions={planOptions}
								domainOptions={domainOptions}
								upstreamOptions={upstreamOptions}
								dimensionOptions={dimensionOptions}
								upstreamValidationAvailable={dependencyMetadataLoaded}
								readOnly={!canEdit}
								saving={saving}
								persistedFieldNames={canonicalModel.fields.map((field) => field.name)}
								onSaveStandardBindings={onSaveStandardBindings}
							/>
						</Form>
					}
					implementation={
						<>
							<ModelSpecImplementationMigrationPanel
								model={canonicalModel}
								canMaintain={canEdit}
								onMigrated={() => void loadPhysicalTimeline()}
							/>
							<ModelSpecImplementationStage
								ref={implementationActionRef}
								model={canonicalModel}
								expected={{
									id: canonicalModel.id,
									revision: canonicalModel.revision,
									checksum: canonicalModel.checksum,
								}}
								implementation={physicalTimeline?.implementation || null}
								implementationLoading={physicalLoading}
								sourceOptions={sourceOptions}
								upstreamOptions={implementationUpstreamOptions}
								sourceLoading={sourceLoading}
								sourceError={sourceError}
								sourcePermissionDenied={sourcePermissionDenied}
								readOnly={!canEdit}
								deliveryReady={stageGates.find((gate) => gate.stage === "IMPLEMENTATION_READY")?.status === "READY"}
								onReloadSources={() => {
									const currentSources = (form.getFieldValue("sources") as ModelSpecDraft["sources"] | undefined) || [];
									void loadSources(canonicalModel.planId, currentSources);
								}}
								onManageSources={canEdit ? () => setSourceInventoryOpen(true) : undefined}
								onStateChange={setImplementationState}
								onImplementationSaved={(implementation) => {
									if (
										implementation.modelSpecId !== canonicalModel.id ||
										implementation.revision !== canonicalModel.revision ||
										implementation.modelChecksum !== canonicalModel.checksum
									)
										return;
									setPhysicalTimeline((current) => ({
										implementation,
										artifacts: current?.artifacts || [],
										events: current?.events || [],
									}));
									void loadStageGates();
								}}
								advancedEntryDisabled={!advancedImplementationReady}
								onOpenAdvanced={() => navigate(implementationPath)}
							/>
						</>
					}
					physical={
						<ModelSpecPhysicalAssetStage
							model={canonicalModel}
							timeline={physicalTimeline}
							loading={physicalLoading}
							error={physicalError}
							onRetry={() => void loadPhysicalTimeline()}
							onRetryReleaseRegistration={retryReleaseRegistration}
						/>
					}
				/>
			) : (
				<Card>
					<Descriptions column={1} size="small" bordered>
						<Descriptions.Item label="模型名称">{model.name}</Descriptions.Item>
						<Descriptions.Item label="每行含义">{model.grain?.statement || "未记录"}</Descriptions.Item>
						<Descriptions.Item label="粒度键">{model.grain?.keys.join("、") || "未记录"}</Descriptions.Item>
						<Descriptions.Item label="来源">
							{model.sourceRefs.length > 0 ? model.sourceRefs.map((source) => source.ref).join("、") : "未记录"}
						</Descriptions.Item>
					</Descriptions>
				</Card>
			)}
			{canonicalModel ? (
				<ModelSpecSourceInventoryModal
					open={sourceInventoryOpen}
					planId={canonicalModel.planId}
					roleAllowsPlanMaintenance={roleAllowsEdit}
					onClose={() => setSourceInventoryOpen(false)}
					onSaved={async () => {
						const currentSources = (form.getFieldValue("sources") as ModelSpecDraft["sources"] | undefined) || [];
						return loadSources(canonicalModel.planId, currentSources);
					}}
				/>
			) : null}
		</div>
	);
}
