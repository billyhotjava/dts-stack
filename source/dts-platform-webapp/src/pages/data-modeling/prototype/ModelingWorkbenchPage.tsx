import { FileDown, GitBranch } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { type BlockerFunction, useBlocker, useNavigate, useSearchParams } from "react-router";
import { getModelRepresentation } from "@/api/modelRepresentationApi";
import { getModelLifecycle } from "@/api/modelSpecApi";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelRepresentationView } from "@/features/modeling/contracts/modelRepresentationContract";
import type { ModelSpecField, ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { useUserInfo } from "@/store/userStore";
import { dataModelingPath } from "../navigation";
import type { DataModelingRoute } from "../types";

import { ConceptDimensionRecordDialog } from "./ConceptDimensionRecordDialog";
import { isBlankModelField } from "./ModelFieldEditorTable";
import { ModelingWorkbenchEditor } from "./ModelingWorkbenchEditor";
import { ModelPublishDialog } from "./ModelPublishDialog";
import { ModelWorkbenchCatalogList } from "./ModelWorkbenchCatalogList";
import { ModelWorkbenchCatalogPanel } from "./ModelWorkbenchCatalogPanel";
import { ModelWorkbenchDialog, type WorkbenchDialog } from "./ModelWorkbenchDialog";
import {
	buildWorkbenchCatalogGroups,
	modelDraftFingerprint,
	workbenchCatalogEmptyMessage,
} from "./modelWorkbenchPresentation";
import { PageHeader, RequestState, Status, Toast, useTransientMessage } from "./PrototypePrimitives";
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
	type ModelDraftValidationErrors,
	type ModelWorkbenchContext,
	modelDraftFromView,
	prepareModelDraftForSave,
	saveDimensionDefinitionDraft,
	saveModelDraft,
	validateConceptDimensionDraftInput,
	validateModelDraftInput,
} from "./services/modelWorkbenchService";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";
import { useCatalogActions } from "./useCatalogActions";
import { useConceptDimensionWorkflow } from "./useConceptDimensionWorkflow";
import { useDataModelingMenuGrant } from "./useDataModelingMenuGrant";
import { resolveWorkbenchEditorAccess } from "./workbenchEditorAccess";

const layerTabs = [
	{ label: "贴源层", layers: ["ODS", "STG"] },
	{ label: "公共层", layers: ["DWD", "DWS"] },
	{ label: "应用层", layers: ["ADS"] },
];

const DISCARD_PROMPT = "当前模型有未保存修改，确认放弃吗？";

const ownerIdOf = (userInfo: unknown) => {
	if (!userInfo || typeof userInfo !== "object") return "";
	const value = (userInfo as Record<string, unknown>).id;
	return value == null ? "" : String(value).trim();
};

export function resolveRequestedModelSelection<T extends { id: string }>(
	models: readonly T[],
	requestedModelId: string,
) {
	const requestedModel = requestedModelId ? models.find((model) => model.id === requestedModelId) : undefined;
	const selectedModel = requestedModel || models[0] || null;
	return {
		selectedModel,
		normalizedModelId: requestedModelId && !requestedModel ? selectedModel?.id || "" : requestedModelId,
	};
}

export function shouldBlockWorkbenchNavigation(dirty: boolean, currentPathname: string, nextPathname: string): boolean {
	return dirty && currentPathname !== nextPathname;
}

export function resolveWorkbenchCreateAction(hasDraft: boolean, createMenuOpen: boolean) {
	return !hasDraft && !createMenuOpen ? "CREATE_DIMENSION_TABLE" : "TOGGLE_CREATE_MENU";
}

export function ModelingWorkbenchPage({ route }: { route: DataModelingRoute }) {
	const navigate = useNavigate();
	const canMaintain = useDataModelingMenuGrant();
	const userInfo = useUserInfo();
	const requestEpoch = useRef(0);
	const savingRef = useRef(false);
	const [searchParams, setSearchParams] = useSearchParams();
	const requestedModelId = searchParams.get("modelSpecId") || "";
	const requestedDimensionId = searchParams.get("dimensionDefinitionId") || "";
	const requestedDialog = searchParams.get("open") || "";
	const requestedModelIdRef = useRef(requestedModelId);
	const requestedDimensionIdRef = useRef(requestedDimensionId);
	const searchParamsRef = useRef(searchParams);
	requestedModelIdRef.current = requestedModelId;
	requestedDimensionIdRef.current = requestedDimensionId;
	searchParamsRef.current = searchParams;
	const syncWorkbenchUrl = useCallback(
		(mutate: (params: URLSearchParams) => void) => {
			const normalized = new URLSearchParams(searchParamsRef.current);
			mutate(normalized);
			if (normalized.toString() === searchParamsRef.current.toString()) return;
			searchParamsRef.current = normalized;
			setSearchParams(normalized, { replace: true });
		},
		[setSearchParams],
	);
	const [context, setContext] = useState<ModelWorkbenchContext | null>(null);
	const [draft, setDraft] = useState<ModelDraft | null>(null);
	const [cleanFingerprint, setCleanFingerprint] = useState<string | null>(null);
	const [validationErrors, setValidationErrors] = useState<ModelDraftValidationErrors>({});
	const [fieldRowIds, setFieldRowIds] = useState<string[]>([]);
	const [dimensionDefinitions, setDimensionDefinitions] = useState<DimensionDefinitionView[]>([]);
	const [dimensionDefinitionFailure, setDimensionDefinitionFailure] = useState("");
	const [layer, setLayer] = useState("公共层");
	const [domain, setDomain] = useState("");
	const [query, setQuery] = useState("");
	const [createOpen, setCreateOpen] = useState(false);
	const [createCategory, setCreateCategory] = useState("");
	const [createQuery, setCreateQuery] = useState("");
	const [viewMode, setViewMode] = useState<"domain" | "category">("domain");
	const [catalogMode, setCatalogMode] = useState<"tree" | "list">("list");
	const [selectedModelId, setSelectedModelId] = useState("");
	const [selectedDimensionId, setSelectedDimensionId] = useState("");
	const [dialog, setDialog] = useState<WorkbenchDialog>(null);
	const [batchMaterializationModels, setBatchMaterializationModels] = useState<ModelSpecView[]>([]);
	const [materializationRefreshKey, setMaterializationRefreshKey] = useState(0);
	const [domainOpen, setDomainOpen] = useState<Record<string, boolean>>({});
	const [loading, setLoading] = useState(true);
	const [editorLoading, setEditorLoading] = useState(false);
	const [saving, setSaving] = useState(false);
	const [failure, setFailure] = useState<{ kind: "permission" | "request"; message: string } | null>(null);
	const [representation, setRepresentation] = useState<ModelRepresentationView | null>(null);
	const [representationFailure, setRepresentationFailure] = useState("");
	const { message, show } = useTransientMessage();
	const draftBase = draft && isModelSpecDraft(draft) ? draft.base : null;
	const conceptDraft = draft && isConceptDimensionDraft(draft) ? draft : null;
	const draftCreateKind = draft?.createKind || null;
	const draftDimensionDefinitionId = draft && isDimensionTableDraft(draft) ? draft.dimensionDefinitionId : "";
	const draftDomainId = draft?.domainId || "";
	const editorAccess = useMemo(() => resolveWorkbenchEditorAccess(canMaintain, draft), [canMaintain, draft]);
	const dirty = draft !== null && cleanFingerprint !== null && modelDraftFingerprint(draft) !== cleanFingerprint;
	const blocker = useBlocker(
		useCallback<BlockerFunction>(
			({ currentLocation, nextLocation }) =>
				shouldBlockWorkbenchNavigation(dirty, currentLocation.pathname, nextLocation.pathname),
			[dirty],
		),
	);
	const confirmDiscard = useCallback(() => !dirty || window.confirm(DISCARD_PROMPT), [dirty]);
	const replaceDraft = useCallback((nextDraft: ModelDraft | null) => {
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
			setEditorLoading(false);
			setFailure(null);
			try {
				const next = await loadModelWorkbenchContext();
				if (requestEpoch.current !== epoch) return;
				setContext(next);
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
						return;
					}
					requestedDimensionIdRef.current = "";
					syncWorkbenchUrl((params) => params.delete("dimensionDefinitionId"));
				}
				const targetId = preferredModelId ?? requestedModelIdRef.current;
				const { selectedModel, normalizedModelId } = resolveRequestedModelSelection(next.models, targetId);
				setSelectedModelId(selectedModel?.id || "");
				setSelectedDimensionId("");
				if (selectedModel) {
					try {
						const nextDraft = await loadModelWorkbenchDraft(selectedModel);
						if (requestEpoch.current !== epoch) return;
						replaceDraft(nextDraft);
					} catch (error) {
						if (requestEpoch.current !== epoch) return;
						replaceDraft(modelDraftFromView(selectedModel));
						setFailure(normalizeModelingRequestFailure(error, "模型实现信息读取失败。"));
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
			} catch (error) {
				if (requestEpoch.current !== epoch) return;
				setContext(null);
				setSelectedModelId("");
				setSelectedDimensionId("");
				replaceDraft(null);
				setFailure(normalizeModelingRequestFailure(error, "模型目录读取失败。"));
			} finally {
				if (requestEpoch.current === epoch) setLoading(false);
			}
		},
		[replaceDraft, syncWorkbenchUrl],
	);

	useEffect(() => {
		void load();
		return () => {
			requestEpoch.current += 1;
		};
	}, [load]);

	useEffect(() => {
		if (!dirty) return;
		const handleBeforeUnload = (event: BeforeUnloadEvent) => {
			event.preventDefault();
			event.returnValue = "";
		};
		window.addEventListener("beforeunload", handleBeforeUnload);
		return () => window.removeEventListener("beforeunload", handleBeforeUnload);
	}, [dirty]);

	useEffect(() => {
		if (blocker.state !== "blocked") return;
		if (window.confirm(DISCARD_PROMPT)) blocker.proceed();
		else blocker.reset();
	}, [blocker]);

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

	// DataWorks 工作台左侧在分层下展示完整数据域树：数据域节点来自数仓规划台账，
	// 而不是从“有模型的域”反推，保证空域也可见。
	const dataDomains = useMemo(() => {
		const all = context?.domains || [];
		const children = all.filter((item) => Boolean(item.parentCode));
		return children.length ? children : all;
	}, [context?.domains]);
	const categoryRoots = useMemo(() => (context?.domains || []).filter((item) => !item.parentCode), [context?.domains]);
	const domainByCode = useMemo(
		() => new Map((context?.domains || []).map((item) => [item.code, item])),
		[context?.domains],
	);
	// DataWorks 建模视角：公共层支持 数据域/业务分类 两种视角；贴源层、应用层仅业务分类视角。
	const effectiveView: "domain" | "category" = layer === "公共层" ? viewMode : "category";
	const modelDomainOptions = useMemo(
		() =>
			(effectiveView === "domain" ? dataDomains : categoryRoots).map((item) => ({
				id: item.id,
				name: item.name,
			})),
		[categoryRoots, dataDomains, effectiveView],
	);
	const filteredModels = useMemo(() => {
		const activeLayers = layerTabs.find((item) => item.label === layer)?.layers || [];
		return (context?.models || []).filter(
			(model) => activeLayers.includes(model.layer) && (!domain || model.domainId === domain),
		);
	}, [context, domain, layer]);
	const visibleModels = useMemo(() => {
		const normalized = query.trim().toLowerCase();
		return normalized
			? filteredModels.filter((model) =>
					`${model.name}${model.implementationPolicy?.physicalName || ""}`.toLowerCase().includes(normalized),
				)
			: filteredModels;
	}, [filteredModels, query]);
	// 概念维度属于公共层（维度层），只在公共层目录展示；贴源层/应用层不出现维度。
	const visibleDimensions = useMemo(
		() => (layer === "公共层" ? context?.dimensions || [] : []),
		[context?.dimensions, layer],
	);
	const groups = useMemo(() => {
		return buildWorkbenchCatalogGroups({
			dataDomains,
			categoryRoots,
			domainByCode,
			effectiveView,
			visibleModels,
			visibleDimensions,
		});
	}, [categoryRoots, dataDomains, domainByCode, effectiveView, visibleDimensions, visibleModels]);
	const catalogEmptyMessage = workbenchCatalogEmptyMessage({
		effectiveView,
		dataDomains,
		categoryRoots,
		hasAnyModels: Boolean(context?.models.length || context?.dimensions.length),
		hasLayerModels: filteredModels.length > 0 || visibleDimensions.length > 0,
		layer,
	});

	const chooseModel = async (model: ModelSpecView) => {
		if (savingRef.current || !confirmDiscard()) return;
		const epoch = ++requestEpoch.current;
		setCreateOpen(false);
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
				setFailure(normalizeModelingRequestFailure(error, "模型实现信息读取失败。"));
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
		setCreateOpen(false);
		setDialog(null);
		requestedModelIdRef.current = "";
		requestedDimensionIdRef.current = definition.id;
		syncWorkbenchUrl((params) => {
			params.set("dimensionDefinitionId", definition.id);
			params.delete("modelSpecId");
		});
	};

	const createModel = (kind: ModelCreateKind) => {
		if (savingRef.current || !context || !confirmDiscard()) return;
		const next = emptyModelDraft(kind, context);
		if (draft?.domainId) next.domainId = draft.domainId;
		else if (createCategory) next.domainId = createCategory;
		else if (domain) next.domainId = domain;
		replaceDraft(next);
		setSelectedModelId("");
		setSelectedDimensionId("");
		setCreateOpen(false);
		setDialog(null);
		syncWorkbenchUrl((params) => {
			params.delete("modelSpecId");
			params.delete("dimensionDefinitionId");
		});
	};

	const save = async () => {
		if (savingRef.current || !draft || !context || !canMaintain) return;
		setFailure(null);
		if (isConceptDimensionDraft(draft)) {
			const nextValidationErrors = validateConceptDimensionDraftInput(draft);
			setValidationErrors(nextValidationErrors);
			if (Object.keys(nextValidationErrors).length) return;
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
			} catch (error) {
				const failure = normalizeModelingRequestFailure(error, "维度保存失败。");
				if (failure.code === "DIMENSION_DEFINITION_NAME_CONFLICT") {
					const recovered = await recoverConflictingDimensionDefinition(draft, failure);
					if (recovered) {
						setSaving(false);
						savingRef.current = false;
						return;
					}
				}
				setFailure(failure);
			} finally {
				savingRef.current = false;
				setSaving(false);
			}
			return;
		}
		const preparedDraft = prepareModelDraftForSave(draft, dimensionDefinitions);
		const nextValidationErrors = validateModelDraftInput(preparedDraft);
		setValidationErrors(nextValidationErrors);
		if (Object.keys(nextValidationErrors).length) return;
		savingRef.current = true;
		setSaving(true);
		try {
			const saved = await saveModelDraft(preparedDraft, {
				ownerId: ownerIdOf(userInfo),
				dimensionDefinitions,
				models: context.models,
			});
			const savedDraft = modelDraftFromView(saved.model, saved.implementation);
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
			});
			show(`模型草稿已保存：r${saved.model.revision}`);
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "模型保存失败。"));
		} finally {
			savingRef.current = false;
			setSaving(false);
		}
	};
	const updateField = (index: number, patch: Partial<ModelSpecField>) => {
		if (savingRef.current) return;
		setDraft((current) => {
			if (!current || !isModelSpecDraft(current)) return current;
			const oldName = current.fields[index]?.name || "";
			const nextName = patch.name == null ? oldName : patch.name;
			return {
				...current,
				fields: current.fields.map((field, row) => (row === index ? { ...field, ...patch } : field)),
				standardBindings:
					oldName === nextName
						? current.standardBindings
						: current.standardBindings.map((binding) =>
								binding.fieldName === oldName ? { ...binding, fieldName: nextName } : binding,
							),
			};
		});
	};
	const addFields = (count: number) => {
		if (savingRef.current || !draft || !isModelSpecDraft(draft)) return;
		const additionCount = Number.isFinite(count) ? Math.max(1, Math.min(20, Math.floor(count))) : 1;
		const nextFields = Array.from({ length: additionCount }, () => ({
			name: "",
			displayName: "",
			dataType: "STRING",
			nullable: true,
			role: "ATTRIBUTE" as const,
			dimensionAttributeCode: null,
		}));
		const nextRowIds = nextFields.map(() => crypto.randomUUID());
		setDraft((current) =>
			current && isModelSpecDraft(current) ? { ...current, fields: [...current.fields, ...nextFields] } : current,
		);
		setFieldRowIds((current) => [...current, ...nextRowIds]);
	};
	const removeBlankFields = () => {
		if (savingRef.current || !draft || !isModelSpecDraft(draft)) return;
		const keepIndexes = draft.fields
			.map((field, index) => (isBlankModelField(field) ? -1 : index))
			.filter((index) => index >= 0);
		const fields = keepIndexes.map((index) => draft.fields[index]);
		const fieldNames = new Set(fields.map((field) => field.name));
		setDraft({
			...draft,
			fields,
			standardBindings: draft.standardBindings.filter((binding) => fieldNames.has(binding.fieldName)),
		});
		setFieldRowIds((current) => keepIndexes.map((index) => current[index] || crypto.randomUUID()));
	};
	const deleteField = (index: number) => {
		if (savingRef.current) return;
		setFieldRowIds((current) => current.filter((_, row) => row !== index));
		setDraft((current) => {
			if (!current || !isModelSpecDraft(current)) return current;
			const fieldName = current.fields[index]?.name;
			return {
				...current,
				fields: current.fields.filter((_, row) => row !== index),
				standardBindings: current.standardBindings.filter((binding) => binding.fieldName !== fieldName),
			};
		});
	};
	const updateStandardBinding = (index: number, value: string) => {
		if (savingRef.current) return;
		setDraft((current) => {
			if (!current || !isModelSpecDraft(current)) return current;
			const fieldName = current.fields[index]?.name || "";
			const remaining = current.standardBindings.filter((binding) => binding.fieldName !== fieldName);
			if (!value || !fieldName) return { ...current, standardBindings: remaining };
			const [standardElementId, version] = value.split("@");
			return {
				...current,
				standardBindings: [...remaining, { fieldName, standardElementId, standardElementVersion: Number(version) }],
			};
		});
	};
	const selectedModel = draft && isModelSpecDraft(draft) ? draft.base : null;
	useEffect(() => {
		if (requestedDialog !== "advanced" || !requestedModelId || selectedModel?.id !== requestedModelId) return;
		setDialog("advanced");
		syncWorkbenchUrl((params) => params.delete("open"));
	}, [requestedDialog, requestedModelId, selectedModel?.id, syncWorkbenchUrl]);
	const refresh = () => {
		if (!savingRef.current && confirmDiscard()) void load(selectedModelId || undefined);
	};
	const navigateToReverseModeling = () => {
		if (!savingRef.current) navigate(dataModelingPath("dimensions", "reverse"));
	};
	const { goToGraph, removeModel, goToDimensionGraph, cloneDimension, removeDimension } = useCatalogActions({
		canMaintain,
		confirmDiscard,
		navigate,
		ownerId: ownerIdOf(userInfo),
		reload: () => load(),
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
	useEffect(() => {
		if (!selectedModel) {
			setRepresentation(null);
			setRepresentationFailure("");
			return;
		}
		let active = true;
		setRepresentation(null);
		setRepresentationFailure("");
		void getModelLifecycle(selectedModel.id)
			.then(({ implementation }) =>
				getModelRepresentation(selectedModel.id, {
					modelRevision: selectedModel.revision,
					implementationRevision: implementation?.implementationRevision,
					representationScope: "BUSINESS",
				}),
			)
			.then((value) => {
				if (active) setRepresentation(value);
			})
			.catch((error) => {
				if (active) setRepresentationFailure(normalizeModelingRequestFailure(error, "统一模型表示读取失败。").message);
			});
		return () => {
			active = false;
		};
	}, [selectedModel]);

	return (
		<main className="dmx-workbench-page">
			<PageHeader description={route.description} title="维度建模" trail="数据建模 / 维度建模" />
			{loading ? (
				<RequestState description="正在读取模型目录、数据域和标准。" kind="loading" title="正在加载模型工作台" />
			) : failure?.kind === "permission" ? (
				<RequestState description={failure.message} kind="permission" title="无权访问模型工作台" />
			) : context && catalogMode === "list" ? (
				<ModelWorkbenchCatalogList
					canMaintain={canMaintain}
					dimensions={context.dimensions}
					domains={context.domains}
					key={`model-list:${materializationRefreshKey}`}
					models={context.models}
					onBack={() => {
						if (!confirmDiscard()) return;
						replaceDraft(null);
						setSelectedModelId("");
						setSelectedDimensionId("");
						setCatalogMode("tree");
						syncWorkbenchUrl((params) => {
							params.delete("modelSpecId");
							params.delete("dimensionDefinitionId");
						});
					}}
					onChooseDimension={(item) => {
						setCatalogMode("tree");
						chooseDimension(item);
					}}
					onChooseModel={(item) => {
						setCatalogMode("tree");
						void chooseModel(item);
					}}
					onMaterialize={setBatchMaterializationModels}
				/>
			) : context ? (
				<div className={`dmx-model-workbench${draft ? " dmx-model-workbench--editor-only" : ""}`}>
					{draft ? null : (
						<ModelWorkbenchCatalogPanel
							canMaintain={canMaintain}
							categoryRoots={categoryRoots}
							createCategory={createCategory}
							createOpen={createOpen}
							createQuery={createQuery}
							domain={domain}
							domainOpen={domainOpen}
							effectiveView={effectiveView}
							emptyMessage={catalogEmptyMessage}
							groups={groups}
							layer={layer}
							loading={loading}
							modelDomainOptions={modelDomainOptions}
							onChooseDimension={chooseDimension}
							onChooseModel={(item) => void chooseModel(item)}
							onCloneDimension={(item) => void cloneDimension(item)}
							onCreate={createModel}
							onCreateCategoryChange={setCreateCategory}
							onCreateQueryChange={setCreateQuery}
							onDomainChange={setDomain}
							onGoToGraph={goToGraph}
							onGoToGraphDimension={goToDimensionGraph}
							onImport={navigateToReverseModeling}
							onLayerChange={(value) => {
								setDomain("");
								setLayer(value);
							}}
							onList={() => setCatalogMode("list")}
							onQueryChange={setQuery}
							onRefresh={refresh}
							onRemoveDimension={(item) => void removeDimension(item)}
							onRemoveModel={(item) => void removeModel(item)}
							onToggleCreate={() => {
								setCreateCategory("");
								setCreateQuery("");
								if (resolveWorkbenchCreateAction(Boolean(draft), createOpen) === "CREATE_DIMENSION_TABLE") {
									createModel("dimension-table");
									return;
								}
								setCreateOpen((open) => !open);
							}}
							onToggleDomain={(key) => setDomainOpen((current) => ({ ...current, [key]: current[key] === false }))}
							onViewChange={(value) => {
								setDomain("");
								setViewMode(value);
							}}
							query={query}
							saving={saving}
							selectedDimensionId={selectedDimensionId}
							selectedModelId={selectedModelId}
						/>
					)}
					<section className="dmx-model-editor">
						<div className="dmx-editor-tab">
							{draft ? (
								<button className="dmx-table-action" onClick={() => setCatalogMode("list")} type="button">
									返回模型列表
								</button>
							) : null}
							<span>▤</span>
							<strong>
								{draft?.name || (draft ? `新建${MODEL_KIND_CONFIG[draft.createKind].label}` : "模型编辑器")}
							</strong>
							{draft && isConceptDimensionDraft(draft) && draft.definitionBase ? (
								<Status tone="warning">{draft.definitionBase.status}</Status>
							) : selectedModel ? (
								<Status tone={selectedModel.status === "PUBLISHED" ? "success" : "warning"}>
									{selectedModel.status} · r{selectedModel.revision}
								</Status>
							) : null}
						</div>
						{editorLoading ? (
							<RequestState description="正在读取所选模型的版本与实现信息。" kind="loading" title="正在打开模型" />
						) : draft ? (
							<ModelingWorkbenchEditor
								canMaintain={canMaintain}
								context={context}
								currentOwnerId={ownerIdOf(userInfo)}
								dimensionDefinitionFailure={dimensionDefinitionFailure}
								dimensionDefinitions={dimensionDefinitions}
								dirty={dirty}
								draft={draft}
								editorAccessMessage={editorAccess.message}
								failureMessage={failure?.message || ""}
								fieldRowIds={fieldRowIds}
								materializationRefreshKey={materializationRefreshKey}
								onAddFields={addFields}
								onChange={(nextDraft) => {
									if (savingRef.current) return;
									setDraft(nextDraft);
									setValidationErrors({});
								}}
								onDeleteField={deleteField}
								onConfirmDimension={() => void confirmConceptVersion()}
								onDialog={(nextDialog) => {
									if (!savingRef.current) setDialog(nextDialog);
								}}
								onRefresh={refresh}
								onRemoveBlankFields={removeBlankFields}
								onSave={() => void save()}
								onStandardChange={updateStandardBinding}
								onUpdateField={updateField}
								readOnly={editorAccess.readOnly}
								representation={representation}
								representationFailure={representationFailure}
								saving={saving}
								selectedModel={selectedModel}
								validationErrors={validationErrors}
							/>
						) : (
							<RequestState description="请从目录选择模型，或新建一个模型草稿。" kind="empty" title="请选择模型" />
						)}
					</section>
					{selectedModel?.modelType === "FACT" ? (
						<aside className="dmx-record-rail">
							<button disabled={saving || !selectedModel} onClick={() => setDialog("versions")} type="button">
								<GitBranch size={16} />
								版本管理
							</button>
							<button disabled={saving || !selectedModel} onClick={() => setDialog("releases")} type="button">
								<FileDown size={16} />
								发布记录
							</button>
						</aside>
					) : null}
				</div>
			) : (
				<RequestState
					description={failure?.message || "服务端未返回模型工作台上下文。"}
					kind="error"
					onRetry={() => void load()}
					title="模型工作台加载失败"
				/>
			)}
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
