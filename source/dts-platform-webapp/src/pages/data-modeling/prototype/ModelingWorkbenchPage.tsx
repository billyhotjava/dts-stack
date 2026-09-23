import { Alert, Button as AntButton, Select, Space } from "antd";
import { normalizeModelWizardStep } from "@/api/modelDeliveryStatusApi";
import { selectedModelingDepartment, selectModelingDepartment } from "@/api/modelingAccessApi";
import type { UnsavedEditorHandle } from "@/pages/catalog/CatalogDatasetGovernanceSummaryEditor";
import { confirmSimilarModel } from "./confirmSimilarModel";
import { ModelAccessDrawer } from "./ModelAccessDrawer";
import { ModelWizardFrame } from "./ModelWizardFrame";
import { ModelWorkbenchNavigationGuard } from "./ModelWorkbenchNavigationGuard";
import { resolveRequestedModelSelection } from "./modelingWorkbenchNavigation";
import { useModelDeliveryStatus } from "./useModelDeliveryStatus";
import { useModelDraftFields } from "./useModelDraftFields";
import { useModelAccess, useModelingAuthorization } from "./useModelingAccess";

export { resolveRequestedModelSelection, shouldBlockWorkbenchNavigation } from "./modelingWorkbenchNavigation";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { useUserInfo } from "@/store/userStore";
import { statusLabel } from "@/utils/customerDisplayLabels";
import { dataModelingPath } from "../navigation";
import type { DataModelingRoute } from "../types";
import { AdvancedDbtWorkspace } from "./AdvancedDbtWorkspace";
import { ConceptDimensionRecordDialog } from "./ConceptDimensionRecordDialog";
import { ModelingWorkbenchEditor } from "./ModelingWorkbenchEditor";
import { ModelPublishDialog } from "./ModelPublishDialog";
import { ModelWorkbenchCatalogList } from "./ModelWorkbenchCatalogList";
import { ModelWorkbenchDialog, type WorkbenchDialog } from "./ModelWorkbenchDialog";
import { type ModelingWorkbenchView, normalizeWorkbenchView } from "./modelingWorkbenchMode";
import { modelDraftFingerprint } from "./modelWorkbenchPresentation";
import { Button, PageHeader, RequestState, Status, Toast, useTransientMessage } from "./PrototypePrimitives";
import {
	saveExistingModelDefinition,
	saveModelDefinitionDraft,
	validateModelDefinitionInput,
} from "./services/modelDefinitionCreation";
import {
	conceptDimensionDraftFromView,
	emptyModelDraft,
	isConceptDimensionDraft,
	isDimensionTableDraft,
	isModelSpecDraft,
	loadModelWorkbenchContext,
	loadModelWorkbenchDraft,
	MODEL_KIND_CONFIG,
	type ModelCreateKind,
	type ModelDraft,
	ModelDraftPartialSaveError,
	type ModelDraftValidationErrors,
	type ModelWorkbenchCatalogContext,
	type ModelWorkbenchContext,
	modelDraftFromView,
	modelDraftNeedsImplementationRecovery,
	normalizeModelDraftImplementation,
	prepareModelDraftForSave,
	reconcileModelDraftSources,
	saveDimensionDefinitionDraft,
	validateConceptDimensionDraftInput,
	validateModelDraftInput,
} from "./services/modelWorkbenchService";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";
import { useCatalogActions } from "./useCatalogActions";
import { useConceptDimensionWorkflow } from "./useConceptDimensionWorkflow";
import { useDataModelingMenuGrant } from "./useDataModelingMenuGrant";
import { useModelAuthoringSession } from "./useModelAuthoringSession";
import { resolveWorkbenchEditorAccess } from "./workbenchEditorAccess";

const DISCARD_PROMPT = "当前工作区有未保存修改，确认放弃吗？";

const ownerIdOf = (userInfo: unknown) => {
	if (!userInfo || typeof userInfo !== "object") return "";
	const value = (userInfo as Record<string, unknown>).id;
	return value == null ? "" : String(value).trim();
};

export function ModelingWorkbenchPage({ route }: { route: DataModelingRoute }) {
	const authorization = useModelingAuthorization();
	const [department, setDepartment] = useState(selectedModelingDepartment() || "");
	const current = authorization.data;
	const validSelection = current?.departments.some((item) => item.code === department);
	const selected = current?.canSelectDepartment ? (validSelection ? department : "") : current?.departmentCode || "";
	if (authorization.isPending)
		return <RequestState kind="loading" title="正在核验建模权限" description="正在读取当前用户和部门信息。" />;
	if (authorization.isError || !current?.canModel)
		return (
			<RequestState
				kind="permission"
				title="无法进入数据建模"
				description="请确认账号有效并具有建模角色；若刚完成权限调整，请重新登录。"
			/>
		);
	return (
		<>
			<Space wrap style={{ marginBottom: 12 }}>
				<span>部门公共层：</span>
				{current.canSelectDepartment ? (
					<Select
						aria-label="建模所属部门"
						placeholder="请选择部门"
						value={selected || undefined}
						style={{ width: 240 }}
						options={current.departments.map((item) => ({ value: item.code, label: item.name }))}
						onChange={(value) => {
							if (selected && !window.confirm("切换部门将关闭当前编辑器，请先确认修改已保存。是否继续？")) return;
							selectModelingDepartment(value);
							setDepartment(value);
						}}
					/>
				) : (
					<strong>
						{current.departments.find((item) => item.code === selected)?.name || selected || "尚未分配部门"}
					</strong>
				)}
				<AntButton disabled title="跨部门引用与共享审批尚未开放">
					申请跨部门引用
				</AntButton>
			</Space>
			{selected ? (
				<ModelingWorkbenchBody key={selected} route={route} />
			) : (
				<Alert type="info" showIcon message="请选择所属部门后继续；部门公共层将在首次保存时创建。" />
			)}
		</>
	);
}

function ModelingWorkbenchBody({ route }: { route: DataModelingRoute }) {
	const navigate = useNavigate();
	const canModel = useDataModelingMenuGrant();
	const userInfo = useUserInfo();
	const requestEpoch = useRef(0);
	const savingRef = useRef(false);
	const [searchParams, setSearchParams] = useSearchParams();
	const setSearchParamsRef = useRef(setSearchParams);
	setSearchParamsRef.current = setSearchParams;
	const requestedModelId = searchParams.get("modelSpecId") || "";
	const requestedDimensionId = searchParams.get("dimensionDefinitionId") || "";
	const { view: requestedView, legacyAdvanced } = normalizeWorkbenchView(searchParams);
	const requestedModelIdRef = useRef(requestedModelId);
	const requestedDimensionIdRef = useRef(requestedDimensionId);
	const searchParamsRef = useRef(searchParams);
	requestedModelIdRef.current = requestedModelId;
	requestedDimensionIdRef.current = requestedDimensionId;
	searchParamsRef.current = searchParams;
	const syncWorkbenchUrl = useCallback((mutate: (params: URLSearchParams) => void) => {
		const normalized = new URLSearchParams(searchParamsRef.current);
		mutate(normalized);
		if (normalized.toString() === searchParamsRef.current.toString()) return;
		searchParamsRef.current = normalized;
		// The router setter changes with the query; keep the initial loader stable across wizard navigation.
		setSearchParamsRef.current(normalized, { replace: true });
	}, []);
	const [context, setContext] = useState<ModelWorkbenchContext | null>(null);
	const [catalogContext, setCatalogContext] = useState<ModelWorkbenchCatalogContext | null>(null);
	const [draft, setDraft] = useState<ModelDraft | null>(null);
	const cleanDraftRef = useRef<ModelDraft | null>(null);
	const [cleanFingerprint, setCleanFingerprint] = useState<string | null>(null);
	const [validationErrors, setValidationErrors] = useState<ModelDraftValidationErrors>({});
	const [fieldRowIds, setFieldRowIds] = useState<string[]>([]);
	const [dimensionDefinitions, setDimensionDefinitions] = useState<DimensionDefinitionView[]>([]);
	const [dimensionDefinitionFailure, setDimensionDefinitionFailure] = useState("");
	const [selectedModelId, setSelectedModelId] = useState("");
	const modelAccess = useModelAccess(selectedModelId ? [selectedModelId] : []);
	const selectedAccess = modelAccess.isError ? undefined : modelAccess.data?.[selectedModelId];
	const canMaintain = canModel && (!selectedModelId || selectedAccess?.canEdit === true);
	const [sharingOpen, setSharingOpen] = useState(false);
	const [selectedDimensionId, setSelectedDimensionId] = useState("");
	const [dialog, setDialog] = useState<WorkbenchDialog>(null);
	const [batchMaterializationModels, setBatchMaterializationModels] = useState<ModelSpecView[]>([]);
	const [materializationRefreshKey, setMaterializationRefreshKey] = useState(0);
	const [loading, setLoading] = useState(true);
	const [contextReady, setContextReady] = useState(false);
	const [editorLoading, setEditorLoading] = useState(false);
	const [saving, setSaving] = useState(false);
	const [failure, setFailure] = useState<{ kind: "permission" | "request"; message: string } | null>(null);
	const { message, show } = useTransientMessage();
	const draftBase = draft && isModelSpecDraft(draft) ? draft.base : null;
	const selectedModel = draftBase;
	const delivery = useModelDeliveryStatus(
		selectedModel,
		searchParams.get("environment") || "",
		searchParams.get("candidateId") || "",
		materializationRefreshKey,
	);
	const wizardStep =
		normalizeModelWizardStep(searchParams.get("step")) || delivery.data?.recommendedStep || "definition";
	const conceptDraft = draft && isConceptDimensionDraft(draft) ? draft : null;
	const draftCreateKind = draft?.createKind || null;
	const draftDimensionDefinitionId = draft && isDimensionTableDraft(draft) ? draft.dimensionDefinitionId : "";
	const draftDomainId = draft?.domainId || "";
	const editorAccess = useMemo(() => resolveWorkbenchEditorAccess(canMaintain, draft), [canMaintain, draft]);
	const dirty = draft !== null && cleanFingerprint !== null && modelDraftFingerprint(draft) !== cleanFingerprint;
	const saveNeeded = dirty || modelDraftNeedsImplementationRecovery(draft, context?.implementationCapabilities);
	const replaceDraft = useCallback((nextDraft: ModelDraft | null) => {
		cleanDraftRef.current = nextDraft;
		setDraft(nextDraft);
		setCleanFingerprint(nextDraft ? modelDraftFingerprint(nextDraft) : null);
		setFieldRowIds(nextDraft && isModelSpecDraft(nextDraft) ? nextDraft.fields.map(() => crypto.randomUUID()) : []);
		setValidationErrors({});
	}, []);
	const { confirmConceptVersion, recoverConflictingDimensionDefinition } = useConceptDimensionWorkflow({
		canMaintain,
		conceptDraft,
		draftBase,
		draftCreateKind,
		draftDimensionDefinitionId,
		draftDomainId,
		replaceDraft,
		savingRef,
		setDimensionDefinitionFailure,
		setDimensionDefinitions,
		setDraft,
		setFailure,
		setSaving,
		show,
	});

	const load = useCallback(
		async (preferredModelId?: string) => {
			const epoch = ++requestEpoch.current;
			setLoading(true);
			setContextReady(false);
			setEditorLoading(false);
			setFailure(null);
			try {
				const next = await loadModelWorkbenchContext((catalog) => {
					if (requestEpoch.current === epoch) setCatalogContext(catalog);
				});
				if (requestEpoch.current !== epoch) return null;
				setContext(next);
				setContextReady(true);
				if (!preferredModelId && requestedDimensionIdRef.current) {
					const dimension = next.dimensions.find((item) => item.id === requestedDimensionIdRef.current);
					if (dimension) {
						replaceDraft(conceptDimensionDraftFromView(dimension));
						setSelectedModelId("");
						setSelectedDimensionId(dimension.id);
						requestedModelIdRef.current = "";
						requestedDimensionIdRef.current = dimension.id;
						syncWorkbenchUrl((params) => {
							params.set("dimensionDefinitionId", dimension.id);
							params.delete("modelSpecId");
						});
						return null;
					}
					requestedDimensionIdRef.current = "";
					syncWorkbenchUrl((params) => params.delete("dimensionDefinitionId"));
				}
				const targetId = preferredModelId ?? requestedModelIdRef.current;
				if (!targetId) {
					setSelectedModelId("");
					setSelectedDimensionId("");
					replaceDraft(null);
					return null;
				}
				const { selectedModel, normalizedModelId } = resolveRequestedModelSelection(next.models, targetId);
				setSelectedModelId(selectedModel?.id || "");
				setSelectedDimensionId("");
				if (selectedModel) {
					try {
						const nextDraft = await loadModelWorkbenchDraft(selectedModel);
						if (requestEpoch.current !== epoch) return null;
						replaceDraft(nextDraft);
					} catch (error) {
						if (requestEpoch.current !== epoch) return null;
						replaceDraft(modelDraftFromView(selectedModel));
						setFailure(normalizeModelingRequestFailure(error, "模型代码信息读取失败。"));
					}
				} else replaceDraft(null);
				if (normalizedModelId !== requestedModelIdRef.current) {
					requestedModelIdRef.current = normalizedModelId;
					requestedDimensionIdRef.current = "";
					syncWorkbenchUrl((params) => {
						if (normalizedModelId) params.set("modelSpecId", normalizedModelId);
						else params.delete("modelSpecId");
						params.delete("dimensionDefinitionId");
					});
				}
				return selectedModel || null;
			} catch (error) {
				if (requestEpoch.current !== epoch) return null;
				setContext(null);
				setSelectedModelId("");
				setSelectedDimensionId("");
				replaceDraft(null);
				setFailure(normalizeModelingRequestFailure(error, "模型列表读取失败。"));
				return null;
			} finally {
				if (requestEpoch.current === epoch) setLoading(false);
			}
		},
		[replaceDraft, syncWorkbenchUrl],
	);
	const authoring = useModelAuthoringSession({
		canMaintain,
		context,
		dimensionDefinitions,
		dirty,
		draft,
		loadWorkbench: async (preferredModelId) => {
			await load(preferredModelId);
		},
		ownerId: ownerIdOf(userInfo),
		replaceDraft,
		selectedModel,
		selectedModelId,
		setContext,
		setValidationErrors,
		show,
	});
	const {
		busy: authoringBusy,
		changeFiles: setAuthoringFiles,
		codeDirty: authoringCodeDirty,
		commit: authoringCommit,
		commitDraft: commitAuthoring,
		conflict: authoringConflict,
		context: authoringContext,
		create: createAuthoring,
		failure: authoringFailure,
		files: authoringFiles,
		focusNode: authoringFocusNode,
		invalidateValidation,
		reload: reloadAuthoring,
		save: saveAuthoring,
		setFocusNode: setAuthoringFocusNode,
		validate: validateAuthoring,
		validation: authoringValidation,
	} = authoring;
	const [assetGuard, setAssetGuard] = useState<UnsavedEditorHandle | null>(null);
	const unsavedChanges = dirty || authoringCodeDirty || Boolean(assetGuard?.dirty);
	// S10DC-108: once the user confirmed here, the route guard must not ask again for the same navigation.
	const discardConfirmedRef = useRef(false);
	const confirmDiscard = useCallback(() => {
		if (!unsavedChanges) return true;
		const confirmed = window.confirm(DISCARD_PROMPT);
		discardConfirmedRef.current = confirmed;
		return confirmed;
	}, [unsavedChanges]);
	useEffect(() => {
		if (!unsavedChanges) discardConfirmedRef.current = false;
	}, [unsavedChanges]);

	// biome-ignore lint/correctness/useExhaustiveDependencies: load reads the current route IDs through refs and must rerun on navigation.
	useEffect(() => {
		void load();
		return () => {
			requestEpoch.current += 1;
		};
	}, [load, requestedModelId, requestedDimensionId]);

	useEffect(() => {
		const activeModelId = selectedModelId;
		if (!context || !requestedModelId || requestedModelId === activeModelId) return;
		if (savingRef.current || !confirmDiscard()) {
			const restored = new URLSearchParams(searchParams);
			if (activeModelId) restored.set("modelSpecId", activeModelId);
			else restored.delete("modelSpecId");
			setSearchParams(restored, { replace: true });
			return;
		}
		void load(requestedModelId);
	}, [confirmDiscard, context, load, requestedModelId, searchParams, selectedModelId, setSearchParams]);

	const chooseModel = async (model: ModelSpecView) => {
		if (savingRef.current || !confirmDiscard()) return;
		const epoch = ++requestEpoch.current;
		setSelectedModelId(model.id);
		setSelectedDimensionId("");
		setEditorLoading(true);
		setFailure(null);
		requestedModelIdRef.current = model.id;
		requestedDimensionIdRef.current = "";
		syncWorkbenchUrl((params) => {
			params.set("modelSpecId", model.id);
			params.delete("dimensionDefinitionId");
		});
		try {
			const nextDraft = await loadModelWorkbenchDraft(model);
			if (requestEpoch.current === epoch) replaceDraft(nextDraft);
		} catch (error) {
			if (requestEpoch.current === epoch) {
				replaceDraft(modelDraftFromView(model));
				setFailure(normalizeModelingRequestFailure(error, "模型代码信息读取失败。"));
			}
		} finally {
			if (requestEpoch.current === epoch) setEditorLoading(false);
		}
	};
	const chooseDimension = (definition: DimensionDefinitionView) => {
		if (savingRef.current || !confirmDiscard()) return;
		requestEpoch.current += 1;
		replaceDraft(conceptDimensionDraftFromView(definition));
		setEditorLoading(false);
		setSelectedModelId("");
		setSelectedDimensionId(definition.id);
		setDialog(null);
		requestedModelIdRef.current = "";
		requestedDimensionIdRef.current = definition.id;
		syncWorkbenchUrl((params) => {
			params.set("dimensionDefinitionId", definition.id);
			params.delete("modelSpecId");
		});
	};

	const createModel = (kind: ModelCreateKind, categoryId = "") => {
		if (savingRef.current || !context || !confirmDiscard()) return;
		const next = emptyModelDraft(kind, context);
		if (categoryId) {
			const category = context.domains.find((item) => item.id === categoryId && !item.parentCode);
			const domains = category ? context.domains.filter((item) => item.parentCode === category.code) : [];
			next.domainId = domains.length === 1 ? domains[0].id : "";
		}
		replaceDraft(next);
		setSelectedModelId("");
		setSelectedDimensionId("");
		setDialog(null);
		syncWorkbenchUrl((params) => {
			params.delete("modelSpecId");
			params.delete("dimensionDefinitionId");
			params.set("step", "definition");
			params.set("view", "visual");
		});
	};

	const save = async (advance = true): Promise<boolean> => {
		if (savingRef.current || !draft || !context || !canMaintain) return false;
		setFailure(null);
		if (isConceptDimensionDraft(draft)) {
			const nextValidationErrors = validateConceptDimensionDraftInput(draft);
			setValidationErrors(nextValidationErrors);
			if (Object.keys(nextValidationErrors).length) return false;
			savingRef.current = true;
			setSaving(true);
			try {
				const saved = await saveDimensionDefinitionDraft(draft, ownerIdOf(userInfo));
				replaceDraft(conceptDimensionDraftFromView(saved));
				setContext((current) =>
					current
						? { ...current, dimensions: [...current.dimensions.filter((item) => item.id !== saved.id), saved] }
						: current,
				);
				setSelectedModelId("");
				setSelectedDimensionId(saved.id);
				requestedModelIdRef.current = "";
				requestedDimensionIdRef.current = saved.id;
				syncWorkbenchUrl((params) => {
					params.set("dimensionDefinitionId", saved.id);
					params.delete("modelSpecId");
				});
				show(`维度草稿已保存：${saved.systemCode}`);
				return true;
			} catch (error) {
				const failure = normalizeModelingRequestFailure(error, "维度保存失败。");
				if (failure.code === "DIMENSION_DEFINITION_NAME_CONFLICT") {
					const recovered = await recoverConflictingDimensionDefinition(draft, failure);
					if (recovered) {
						setSaving(false);
						savingRef.current = false;
						return true;
					}
				}
				setFailure(failure);
			} finally {
				savingRef.current = false;
				setSaving(false);
			}
			return false;
		}
		const preparedDraft = normalizeModelDraftImplementation(
			prepareModelDraftForSave(draft, dimensionDefinitions),
			context.implementationCapabilities,
		);
		const nextValidationErrors =
			wizardStep === "definition" || !preparedDraft.base
				? validateModelDefinitionInput(preparedDraft)
				: validateModelDraftInput(preparedDraft, context.implementationCapabilities);
		setValidationErrors(nextValidationErrors);
		if (Object.keys(nextValidationErrors).length) return false;
		if (wizardStep !== "definition" && preparedDraft.base) {
			return await authoring.save("VISUAL");
		}
		savingRef.current = true;
		setSaving(true);
		try {
			if (!(await confirmSimilarModel(preparedDraft))) return false;
			const persistDraft = !preparedDraft.base ? saveModelDefinitionDraft : saveExistingModelDefinition;
			const saved = await persistDraft(preparedDraft, {
				ownerId: ownerIdOf(userInfo),
				dimensionDefinitions,
				models: context.models,
				implementationCapabilities: context.implementationCapabilities,
			});
			const projectedDraft = modelDraftFromView(saved.model, saved.implementation);
			const savedDraft =
				saved.model.implementationMode === "DBT_MANAGED" && !saved.implementation
					? { ...projectedDraft, physicalName: preparedDraft.physicalName }
					: projectedDraft;
			replaceDraft(savedDraft);
			setContext((current) =>
				current
					? { ...current, models: [...current.models.filter((item) => item.id !== saved.model.id), saved.model] }
					: current,
			);
			setSelectedModelId(saved.model.id);
			setSelectedDimensionId("");
			requestedModelIdRef.current = saved.model.id;
			requestedDimensionIdRef.current = "";
			syncWorkbenchUrl((params) => {
				params.set("modelSpecId", saved.model.id);
				params.delete("dimensionDefinitionId");
				if (advance && (wizardStep === "definition" || !preparedDraft.base)) {
					params.set("step", "implementation");
					params.set("view", "visual");
				}
			});
			show(`模型草稿已保存：r${saved.model.revision}`);
			setMaterializationRefreshKey((value) => value + 1);
			return true;
		} catch (error) {
			if (error instanceof ModelDraftPartialSaveError) {
				const recovered = {
					...preparedDraft,
					base: error.savedModel,
					implementationIdempotencyKey: crypto.randomUUID(),
				};
				replaceDraft(recovered);
				setContext((current) =>
					current
						? {
								...current,
								models: [...current.models.filter((item) => item.id !== error.savedModel.id), error.savedModel],
							}
						: current,
				);
			}
			setFailure(
				normalizeModelingRequestFailure(
					error instanceof ModelDraftPartialSaveError ? error.failure : error,
					"模型保存失败。",
				),
			);
			return false;
		} finally {
			savingRef.current = false;
			setSaving(false);
		}
	};
	const { updateField, addFields, removeBlankFields, deleteField, updateStandardBinding } = useModelDraftFields(
		draft,
		savingRef,
		setDraft,
		setFieldRowIds,
	);
	useEffect(() => {
		if (!legacyAdvanced || !requestedModelId || selectedModel?.id !== requestedModelId) return;
		syncWorkbenchUrl((params) => {
			params.set("view", "code");
			params.delete("open");
		});
	}, [legacyAdvanced, requestedModelId, selectedModel?.id, syncWorkbenchUrl]);
	const setWorkbenchView = async (view: ModelingWorkbenchView) => {
		if (view === requestedView || savingRef.current || authoringBusy) return;
		savingRef.current = true;
		try {
			if ((dirty || authoringCodeDirty) && !(await authoring.save(requestedView === "code" ? "CODE" : "VISUAL")))
				return;
			syncWorkbenchUrl((params) => {
				params.set("view", view);
				params.delete("open");
			});
		} finally {
			savingRef.current = false;
		}
	};
	const refresh = async () => {
		if (savingRef.current || !confirmDiscard()) return;
		const refreshedModel = await load(selectedModelId || undefined);
		if (
			refreshedModel &&
			selectedModelId &&
			refreshedModel.revision === selectedModel?.revision &&
			refreshedModel.checksum === selectedModel?.checksum
		) {
			await reloadAuthoring(selectedModelId);
		}
	};
	const navigateToReverseModeling = () => {
		if (!savingRef.current) navigate(dataModelingPath("dimensions", "reverse"));
	};
	const returnToList = () => {
		if (savingRef.current || !confirmDiscard()) return;
		requestEpoch.current += 1;
		setEditorLoading(false);
		replaceDraft(null);
		setSelectedModelId("");
		setSelectedDimensionId("");
		setDialog(null);
		setFailure(null);
		requestedModelIdRef.current = "";
		requestedDimensionIdRef.current = "";
		syncWorkbenchUrl((params) => {
			params.delete("modelSpecId");
			params.delete("dimensionDefinitionId");
			params.delete("open");
			params.delete("view");
			params.delete("step");
		});
	};
	const { goToGraph, removeModel, archiveModel, goToDimensionGraph, cloneDimension, removeDimension } =
		useCatalogActions({
			canMaintain,
			confirmDiscard,
			navigate,
			ownerId: ownerIdOf(userInfo),
			reload: async () => {
				await load();
			},
			replaceDraft,
			requestedDimensionIdRef,
			requestedModelIdRef,
			savingRef,
			selectedDimensionId,
			selectedModelId,
			setFailure,
			setSaving,
			show,
			syncWorkbenchUrl,
		});
	const listContext = contextReady ? context : catalogContext;
	const showEarlyList = Boolean(listContext && !draft && !editorLoading && !requestedModelId && !requestedDimensionId);
	return (
		<main className="dmx-workbench-page">
			<PageHeader description={route.description} title="维度建模" trail="数据建模 / 维度建模" />
			{selectedModel?.modelType === "APPLICATION" && (
				<Space wrap style={{ marginBottom: 12 }}>
					<span>负责人：{selectedAccess?.ownerName || "暂无显示名称"}</span>
					<AntButton onClick={() => setSharingOpen(true)}>编辑共享</AntButton>
				</Space>
			)}
			{selectedModel?.modelType === "APPLICATION" && (
				<ModelAccessDrawer
					modelId={selectedModel.id}
					access={selectedAccess}
					open={sharingOpen}
					onClose={() => setSharingOpen(false)}
				/>
			)}
			{loading && !showEarlyList ? (
				<RequestState description="正在读取模型列表和数据域。" kind="loading" title="正在加载模型工作台" />
			) : failure?.kind === "permission" ? (
				<RequestState description={failure.message} kind="permission" title="无权访问模型工作台" />
			) : listContext && !draft && !editorLoading ? (
				<ModelWorkbenchCatalogList
					busy={saving}
					scopePlanId={context?.planId || null}
					detailsReady={contextReady}
					canMaintain={canMaintain}
					dimensions={listContext.dimensions}
					domains={listContext.domains}
					failureMessage={failure?.message || ""}
					key={`model-list:${materializationRefreshKey}`}
					models={listContext.models}
					onArchiveModel={(item) => void archiveModel(item)}
					onCloneDimension={(item) => void cloneDimension(item)}
					onChooseDimension={chooseDimension}
					onChooseModel={(item) => void chooseModel(item)}
					onCreate={createModel}
					onGoToGraphDimension={goToDimensionGraph}
					onGoToGraphModel={goToGraph}
					onImport={navigateToReverseModeling}
					onMaterialize={setBatchMaterializationModels}
					onRefresh={refresh}
					onRemoveDimension={(item) => void removeDimension(item)}
					onRemoveModel={(item) => void removeModel(item)}
				/>
			) : context && contextReady ? (
				<div className="dmx-model-workbench dmx-model-workbench--editor-only">
					<section className="dmx-model-editor">
						<div className="dmx-editor-tab">
							<span>▤</span>
							<strong>
								{draft?.name || (draft ? `新建${MODEL_KIND_CONFIG[draft.createKind].label}` : "模型编辑器")}
							</strong>
							{draft && isConceptDimensionDraft(draft) && draft.definitionBase ? (
								<Status tone="warning">{statusLabel(draft.definitionBase.status)}</Status>
							) : selectedModel ? (
								<Status tone={selectedModel.status === "PUBLISHED" ? "success" : "warning"}>
									{statusLabel(selectedModel.status)} · r{selectedModel.revision}
								</Status>
							) : null}
							<Button className="dmx-editor-tab__back dmx-table-action" onClick={returnToList} type="link">
								返回模型列表
							</Button>
						</div>
						<ModelWizardFrame
							model={selectedModel}
							enabled={!conceptDraft}
							delivery={delivery.data}
							loading={delivery.loading}
							failure={delivery.failure}
							onRefresh={() => setMaterializationRefreshKey((value) => value + 1)}
							canMaintain={canMaintain}
							onBack={returnToList}
							dirty={dirty || authoringCodeDirty}
							onAssetGuardChange={setAssetGuard}
							commandsBlocked={unsavedChanges}
						>
							{editorLoading ? (
								<RequestState description="正在读取所选模型的版本与加工信息。" kind="loading" title="正在打开模型" />
							) : wizardStep === "implementation" && requestedView === "code" && selectedModel ? (
								<AdvancedDbtWorkspace
									busy={authoringBusy}
									canMaintain={canMaintain}
									commit={authoringCommit}
									conflict={authoringConflict}
									context={authoringContext}
									dirty={authoringCodeDirty || dirty}
									failure={authoringFailure}
									files={authoringFiles}
									initialFocusNode={authoringFocusNode}
									initialTargetPhysicalName={draft && isModelSpecDraft(draft) ? draft.physicalName : ""}
									model={selectedModel}
									onBack={() => setWorkbenchView("visual")}
									onCommit={() => void commitAuthoring()}
									onSubmit={() =>
										void authoring.submitImplementation("CODE").then((ok) => {
											if (ok) {
												setMaterializationRefreshKey((value) => value + 1);
												syncWorkbenchUrl((params) => params.set("step", "verification"));
											}
										})
									}
									onNext={() => syncWorkbenchUrl((params) => params.set("step", "verification"))}
									onPrevious={() => syncWorkbenchUrl((params) => params.set("step", "definition"))}
									onCreate={(targetPhysicalName) => void createAuthoring(targetPhysicalName)}
									onFilesChange={setAuthoringFiles}
									onSave={() => void saveAuthoring("CODE")}
									onValidate={() => void validateAuthoring("CODE")}
									validation={authoringValidation}
								/>
							) : draft ? (
								<ModelingWorkbenchEditor
									definitionOnly={wizardStep === "definition" && isModelSpecDraft(draft)}
									authoringBusy={authoringBusy}
									authoringConflict={authoringConflict}
									authoringContext={authoringContext}
									authoringFailure={authoringFailure}
									authoringValidation={authoringValidation}
									canMaintain={canMaintain}
									context={context}
									currentOwnerId={ownerIdOf(userInfo)}
									dimensionDefinitionFailure={dimensionDefinitionFailure}
									dimensionDefinitions={dimensionDefinitions}
									dirty={saveNeeded || authoringCodeDirty}
									draft={draft}
									editorAccessMessage={editorAccess.message}
									failureMessage={failure?.message || ""}
									fieldRowIds={fieldRowIds}
									materializationRefreshKey={materializationRefreshKey}
									onViewChange={setWorkbenchView}
									onAddFields={addFields}
									onChange={(nextDraft) => {
										if (savingRef.current) return;
										setDraft(
											isModelSpecDraft(nextDraft)
												? { ...nextDraft, implementationIdempotencyKey: crypto.randomUUID() }
												: nextDraft,
										);
										setValidationErrors({});
										invalidateValidation();
									}}
									onDeleteField={deleteField}
									onConfirmDimension={() => void confirmConceptVersion()}
									onCommitAuthoring={() => void commitAuthoring()}
									onDialog={(nextDialog) => {
										if (!savingRef.current) setDialog(nextDialog);
									}}
									onRefresh={refresh}
									onForkPublished={() => void createAuthoring()}
									onOpenRawNode={(node) => {
										setAuthoringFocusNode(node);
										setWorkbenchView("code");
									}}
									onRemoveBlankFields={removeBlankFields}
									onSave={() => void save()}
									onStash={() => void save(false)}
									onSubmitImplementation={() =>
										void authoring.submitImplementation("VISUAL").then((ok) => {
											if (ok) {
												setMaterializationRefreshKey((value) => value + 1);
												syncWorkbenchUrl((params) => params.set("step", "verification"));
											}
										})
									}
									onNext={() => syncWorkbenchUrl((params) => params.set("step", "verification"))}
									onPrevious={() => syncWorkbenchUrl((params) => params.set("step", "definition"))}
									onStandardChange={updateStandardBinding}
									onSourcesChanged={(sources, sourcePlanId) => {
										if (savingRef.current) return;
										setContext((current) =>
											current ? { ...current, planId: sourcePlanId || current.planId, sources } : current,
										);
										setDraft((current) => {
											if (!current || !isModelSpecDraft(current)) return current;
											const next = reconcileModelDraftSources(current, sourcePlanId, sources);
											if (next.planId === current.planId && next.sourceRefs === current.sourceRefs) return current;
											return { ...next, implementationIdempotencyKey: crypto.randomUUID() };
										});
										setValidationErrors({});
										invalidateValidation();
									}}
									onUpdateField={updateField}
									onValidateAuthoring={() => void validateAuthoring("VISUAL")}
									readOnly={
										editorAccess.readOnly ||
										Boolean(
											selectedModel &&
												(!delivery.data ||
													delivery.data.wizard.find((page) => page.key === wizardStep)?.canEdit === false),
										)
									}
									saving={saving}
									selectedModel={selectedModel}
									validationErrors={validationErrors}
									view={requestedView}
								/>
							) : (
								<RequestState description="未能打开模型，请返回模型列表重试。" kind="error" title="模型打开失败" />
							)}
						</ModelWizardFrame>
					</section>
				</div>
			) : (
				<RequestState
					description={failure?.message || "服务端未返回模型工作台数据。"}
					kind="error"
					onRetry={() => void load()}
					title="模型工作台加载失败"
				/>
			)}
			<ModelWorkbenchNavigationGuard
				dirty={unsavedChanges}
				savingRef={savingRef}
				discardConfirmedRef={discardConfirmedRef}
				onSave={() =>
					assetGuard?.dirty ? assetGuard.save() : requestedView === "code" ? saveAuthoring("CODE") : save(false)
				}
				onDiscard={() => {
					assetGuard?.discard();
					replaceDraft(cleanDraftRef.current);
					void reloadAuthoring();
				}}
			/>

			<ModelWorkbenchDialog
				canMaintain={canMaintain}
				dialog={dialog}
				model={selectedModel}
				onClose={() => {
					if (dialog === "publish") setMaterializationRefreshKey((current) => current + 1);
					setDialog(null);
				}}
			/>
			{batchMaterializationModels.length ? (
				<ModelPublishDialog
					canMaintain={canMaintain}
					models={batchMaterializationModels}
					onClose={() => {
						setBatchMaterializationModels([]);
						setMaterializationRefreshKey((current) => current + 1);
					}}
				/>
			) : null}
			<ConceptDimensionRecordDialog
				canMaintain={canMaintain}
				dialog={conceptDraft && (dialog === "versions" || dialog === "releases") ? dialog : null}
				draft={conceptDraft}
				onClose={() => setDialog(null)}
				onConfirm={() => void confirmConceptVersion()}
				saving={saving}
			/>
			<Toast message={message} />
		</main>
	);
}
