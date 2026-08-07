import { ChevronDown, ChevronRight, FileDown, GitBranch, Import, ListFilter, Plus, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { type BlockerFunction, useBlocker, useNavigate, useSearchParams } from "react-router";
import { listDimensionDefinitions } from "@/api/dimensionDefinitionApi";
import { getModelRepresentation } from "@/api/modelRepresentationApi";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelRepresentationView } from "@/features/modeling/contracts/modelRepresentationContract";
import type { ModelSpecField, ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { useUserInfo } from "@/store/userStore";
import { dataModelingPath } from "../navigation";
import type { DataModelingRoute } from "../types";

import { isBlankModelField } from "./ModelFieldEditorTable";
import { ConceptDimensionRecordDialog } from "./ConceptDimensionRecordDialog";
import { ModelingWorkbenchEditor } from "./ModelingWorkbenchEditor";
import { ModelWorkbenchDialog, type WorkbenchDialog } from "./ModelWorkbenchDialog";
import { modelDraftFingerprint } from "./modelWorkbenchPresentation";
import { PageHeader, RequestState, Status, Toast, useTransientMessage } from "./PrototypePrimitives";
import {
	type ConceptDimensionDraft,
	conceptDimensionDraftFromView,
	confirmDimensionDefinitionDraft,
	emptyModelDraft,
	isConceptDimensionDraft,
	isDimensionTableDraft,
	isModelSpecDraft,
	loadCurrentDimensionDefinitions,
	loadModelWorkbenchContext,
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
import { type ModelingRequestFailure, normalizeModelingRequestFailure } from "./services/planningProjectionService";
import { useDataModelingMenuGrant } from "./useDataModelingMenuGrant";

const layerTabs = [
	{ label: "贴源层", layers: ["ODS", "STG"] },
	{ label: "公共层", layers: ["DWD", "DWS"] },
	{ label: "应用层", layers: ["ADS"] },
];

const modelTypeLabel: Record<string, string> = {
	DIMENSION: "维度表",
	FACT: "明细表",
	SUMMARY: "汇总表",
	APPLICATION: "应用表",
};

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

export function ModelingWorkbenchPage({ route }: { route: DataModelingRoute }) {
	const navigate = useNavigate();
	const canMaintain = useDataModelingMenuGrant();
	const userInfo = useUserInfo();
	const requestEpoch = useRef(0);
	const savingRef = useRef(false);
	const [searchParams, setSearchParams] = useSearchParams();
	const requestedModelId = searchParams.get("modelSpecId") || "";
	const requestedModelIdRef = useRef(requestedModelId);
	const searchParamsRef = useRef(searchParams);
	requestedModelIdRef.current = requestedModelId;
	searchParamsRef.current = searchParams;
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
	const [viewMode, setViewMode] = useState<"domain" | "category">("domain");
	const [dialog, setDialog] = useState<WorkbenchDialog>(null);
	const [domainOpen, setDomainOpen] = useState<Record<string, boolean>>({});
	const [loading, setLoading] = useState(true);
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
	const dirty = draft !== null && cleanFingerprint !== null && modelDraftFingerprint(draft) !== cleanFingerprint;
	const blocker = useBlocker(
		useCallback<BlockerFunction>(
			({ currentLocation, nextLocation }) =>
				shouldBlockWorkbenchNavigation(dirty, currentLocation.pathname, nextLocation.pathname),
			[dirty],
		),
	);
	const confirmDiscard = useCallback(() => !dirty || window.confirm(DISCARD_PROMPT), [dirty]);
	const confirmConceptVersion = async () => {
		if (savingRef.current || !conceptDraft?.definitionBase || !canMaintain) return;
		setFailure(null);
		savingRef.current = true;
		setSaving(true);
		try {
			const confirmed = await confirmDimensionDefinitionDraft(conceptDraft);
			replaceDraft(confirmed);
			show(`维度定义已确认：${confirmed.definitionBase?.systemCode}`);
		} catch (error) {
			const failure = normalizeModelingRequestFailure(error, "维度定义确认失败。");
			if (failure.code === "DIMENSION_DEFINITION_ATTRIBUTES_REQUIRED_FOR_CONFIRMATION") {
				setFailure({
					...failure,
					message: "确认前请至少添加一个维度属性，并将其中一个属性设为主键。",
				});
			} else {
				setFailure(failure);
			}
		} finally {
			savingRef.current = false;
			setSaving(false);
		}
	};
	const recoverConflictingDimensionDefinition = async (
		draft: ConceptDimensionDraft,
		failure: ModelingRequestFailure,
	): Promise<boolean> => {
		const conflictMessage = "该数据域下已存在同名维度，请更换名称，或直接使用已有维度。";
		try {
			const definitions = await listDimensionDefinitions({
				domainId: draft.domainId,
				offset: 0,
				limit: 100,
			});
			const match = definitions.find((item) => item.name.trim() === draft.name.trim());
			if (!match) {
				setFailure({ ...failure, message: conflictMessage });
				return false;
			}
			replaceDraft(conceptDimensionDraftFromView(match));
			setFailure(null);
			show(`该数据域下已存在同名维度，已为你打开：${match.systemCode}`);
			return true;
		} catch {
			setFailure({ ...failure, message: conflictMessage });
			return false;
		}
	};
	const replaceDraft = useCallback((nextDraft: ModelDraft | null) => {
		setDraft(nextDraft);
		setCleanFingerprint(nextDraft ? modelDraftFingerprint(nextDraft) : null);
		setFieldRowIds(nextDraft && isModelSpecDraft(nextDraft) ? nextDraft.fields.map(() => crypto.randomUUID()) : []);
		setValidationErrors({});
	}, []);

	const load = useCallback(
		async (preferredModelId?: string) => {
			const epoch = ++requestEpoch.current;
			setLoading(true);
			setFailure(null);
			try {
				const next = await loadModelWorkbenchContext();
				if (requestEpoch.current !== epoch) return;
				setContext(next);
				const targetId = preferredModelId ?? requestedModelIdRef.current;
				const { selectedModel, normalizedModelId } = resolveRequestedModelSelection(next.models, targetId);
				replaceDraft(selectedModel ? modelDraftFromView(selectedModel) : null);
				if (normalizedModelId !== requestedModelIdRef.current) {
					const normalized = new URLSearchParams(searchParamsRef.current);
					if (normalizedModelId) normalized.set("modelSpecId", normalizedModelId);
					else normalized.delete("modelSpecId");
					requestedModelIdRef.current = normalizedModelId;
					searchParamsRef.current = normalized;
					setSearchParams(normalized, { replace: true });
				}
			} catch (error) {
				if (requestEpoch.current !== epoch) return;
				setContext(null);
				replaceDraft(null);
				setFailure(normalizeModelingRequestFailure(error, "模型目录读取失败。"));
			} finally {
				if (requestEpoch.current === epoch) setLoading(false);
			}
		},
		[replaceDraft, setSearchParams],
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
		const activeModelId = draftBase?.id || "";
		if (!context || !requestedModelId || requestedModelId === activeModelId) return;
		if (savingRef.current || !confirmDiscard()) {
			const restored = new URLSearchParams(searchParams);
			if (activeModelId) restored.set("modelSpecId", activeModelId);
			else restored.delete("modelSpecId");
			setSearchParams(restored, { replace: true });
			return;
		}
		void load(requestedModelId);
	}, [confirmDiscard, context, draftBase, load, requestedModelId, searchParams, setSearchParams]);

	useEffect(() => {
		if (draftCreateKind !== "dimension-table" || !draftDomainId) {
			setDimensionDefinitions([]);
			setDimensionDefinitionFailure("");
			return;
		}
		let active = true;
		setDimensionDefinitionFailure("");
		void loadCurrentDimensionDefinitions(draftDomainId)
			.then((items) => {
				if (!active) return;
				setDimensionDefinitions(items);
				if (!draftBase && draftCreateKind === "dimension-table" && !draftDimensionDefinitionId && items[0]) {
					setDraft((current) =>
						current && isDimensionTableDraft(current) ? { ...current, dimensionDefinitionId: items[0].id } : current,
					);
				}
			})
			.catch((error) => {
				if (active) {
					setDimensionDefinitions([]);
					setDimensionDefinitionFailure(normalizeModelingRequestFailure(error, "维度目录读取失败。").message);
				}
			});
		return () => {
			active = false;
		};
	}, [draftBase, draftCreateKind, draftDimensionDefinitionId, draftDomainId]);

	// DataWorks 工作台左侧在分层下展示完整数据域树：数据域节点来自数仓规划台账，
	// 而不是从“有模型的域”反推，保证空域也可见。
	const dataDomains = useMemo(() => {
		const all = context?.domains || [];
		const children = all.filter((item) => Boolean(item.parentCode));
		return children.length ? children : all;
	}, [context?.domains]);
	const categoryRoots = useMemo(
		() => (context?.domains || []).filter((item) => !item.parentCode),
		[context?.domains],
	);
	const domainByCode = useMemo(
		() => new Map((context?.domains || []).map((item) => [item.code, item])),
		[context?.domains],
	);
	// DataWorks 建模视角：公共层支持 数据域/业务分类 两种视角；贴源层、应用层仅业务分类视角。
	const effectiveView: "domain" | "category" = layer === "公共层" ? viewMode : "category";
	const modelDomainOptions = useMemo(
		() => dataDomains.map((item) => ({ id: item.id, name: item.name })),
		[dataDomains],
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
	type ViewGroup = {
		key: string;
		label: string;
		kind: "category" | "domain" | "unassigned";
		models: ModelSpecView[];
	};
	const groups = useMemo(() => {
		const byKey = new Map<string, ViewGroup>();
		const ensure = (key: string, label: string, kind: ViewGroup["kind"]): ViewGroup => {
			const existing = byKey.get(key);
			if (existing) return existing;
			const created: ViewGroup = { key, label, kind, models: [] };
			byKey.set(key, created);
			return created;
		};
		if (effectiveView === "domain") {
			for (const domain of dataDomains) ensure(domain.id, domain.name, "domain");
		}
		for (const root of categoryRoots) ensure(root.id, root.name, "category");
		for (const model of visibleModels) {
			if (effectiveView === "domain") {
				const domain = dataDomains.find((item) => item.id === model.domainId);
				if (domain) {
					ensure(domain.id, domain.name, "domain").models.push(model);
					continue;
				}
				const root = categoryRoots.find((item) => item.id === model.domainId);
				if (root) {
					ensure(root.id, root.name, "category").models.push(model);
					continue;
				}
			} else {
				const root = categoryRoots.find((item) => item.id === model.domainId);
				if (root) {
					ensure(root.id, root.name, "category").models.push(model);
					continue;
				}
				const domain = dataDomains.find((item) => item.id === model.domainId);
				const parent = domain?.parentCode ? domainByCode.get(domain.parentCode) : undefined;
				if (parent) {
					ensure(parent.id, parent.name, "category").models.push(model);
					continue;
				}
			}
			ensure("__unassigned__", "未归属数据域", "unassigned").models.push(model);
		}
		return Array.from(byKey.values()).filter((group) => group.models.length > 0);
	}, [categoryRoots, dataDomains, domainByCode, effectiveView, visibleModels]);
	const catalogEmptyMessage = (() => {
		if (effectiveView === "domain") {
			if (!dataDomains.length) return "当前没有数据域，请先在数仓规划中创建数据域。";
		} else if (!categoryRoots.length) {
			return "当前没有业务分类，请先在数仓规划中创建业务分类。";
		}
		if (!(context?.models.length || 0)) return "当前尚无模型。";
		if (!filteredModels.length)
			return `当前“${layer}”暂无模型；切换分层查看其他模型，或在右侧新建模型。`;
		return "没有符合搜索条件的模型。";
	})();

	const chooseModel = (model: ModelSpecView) => {
		if (savingRef.current || !confirmDiscard()) return;
		const nextDraft = modelDraftFromView(model);
		replaceDraft(nextDraft);
		setCreateOpen(false);
		const next = new URLSearchParams(searchParams);
		next.set("modelSpecId", model.id);
		setSearchParams(next, { replace: true });
	};

	const createModel = (kind: ModelCreateKind) => {
		if (savingRef.current || !context || !confirmDiscard()) return;
		const next = emptyModelDraft(kind, context);
		if (draft?.domainId) next.domainId = draft.domainId;
		else if (domain) next.domainId = domain;
		replaceDraft(next);
		setCreateOpen(false);
		setDialog(null);
		const params = new URLSearchParams(searchParams);
		params.delete("modelSpecId");
		setSearchParams(params, { replace: true });
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
			});
			const savedDraft = modelDraftFromView(saved);
			replaceDraft(savedDraft);
			const next = new URLSearchParams(searchParams);
			next.set("modelSpecId", saved.id);
			setSearchParams(next, { replace: true });
			show(`模型草稿已保存：r${saved.revision}`);
			await load(saved.id);
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
	const refresh = () => {
		if (!savingRef.current && confirmDiscard()) void load(selectedModel?.id);
	};
	const navigateToReverseModeling = () => {
		if (!savingRef.current) navigate(dataModelingPath("dimensions", "reverse"));
	};
	useEffect(() => {
		if (!selectedModel) {
			setRepresentation(null);
			setRepresentationFailure("");
			return;
		}
		let active = true;
		setRepresentation(null);
		setRepresentationFailure("");
		void getModelRepresentation(selectedModel.id, {
			modelRevision: selectedModel.revision,
			representationScope: "BUSINESS",
		})
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
			) : context ? (
				<div className="dmx-model-workbench">
					<aside className="dmx-object-panel">
						<header>
							<h2>模型目录</h2>
							<div className="dmx-iconbar">
								<button
									aria-label="新建"
									disabled={saving || !canMaintain}
									onClick={() => setCreateOpen((open) => !open)}
									title={canMaintain ? "新建模型" : "当前账号无建模维护权限"}
									type="button"
								>
									<Plus size={16} />
								</button>
								<button aria-label="导入" disabled={saving} onClick={navigateToReverseModeling} type="button">
									<Import size={16} />
								</button>
								<button aria-label="刷新" disabled={saving || loading} onClick={refresh} type="button">
									<RefreshCw size={16} />
								</button>
							</div>
						</header>
						<div className="dmx-layer-tabs">
							{layerTabs.map((item) => (
								<button
									className={layer === item.label ? "active" : ""}
									disabled={saving}
									key={item.label}
									onClick={() => setLayer(item.label)}
									type="button"
								>
									{item.label}
								</button>
							))}
						</div>
						{layer === "公共层" ? (
							<div className="dmx-view-switch" role="group" aria-label="管理视角">
								<button
									className={effectiveView === "domain" ? "active" : ""}
									disabled={saving}
									onClick={() => setViewMode("domain")}
									type="button"
								>
									数据域视角
								</button>
								<button
									className={effectiveView === "category" ? "active" : ""}
									disabled={saving}
									onClick={() => setViewMode("category")}
									type="button"
								>
									业务分类视角
								</button>
							</div>
						) : null}
						<div className="dmx-object-filters">
							<select
								aria-label="筛选数据域"
								disabled={saving}
								onChange={(event) => setDomain(event.target.value)}
								value={domain}
							>
								<option value="">全部数据域</option>
								{modelDomainOptions.map((item) => (
									<option key={item.id} value={item.id}>
										{item.name}
									</option>
								))}
							</select>
							<div>
								<ListFilter size={14} />
								<input
									aria-label="搜索模型"
									disabled={saving}
									onChange={(event) => setQuery(event.target.value)}
									placeholder="搜索模型"
									value={query}
								/>
							</div>
						</div>
						<div className="dmx-object-tree">
							{groups.map((group) => {
								const open = domainOpen[group.key] !== false;
								return (
									<div key={group.key}>
										<button
											className="dmx-tree-domain"
											disabled={saving}
											onClick={() => setDomainOpen((current) => ({ ...current, [group.key]: !open }))}
											type="button"
										>
											{open ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
											<span>{group.kind === "category" ? "🗂" : group.kind === "domain" ? "🌐" : "▦"}</span>
											<strong>{group.label}</strong>
											<em>({group.models.length})</em>
										</button>
										{open
											? group.models.map((model) => (
													<button
														className={`dmx-tree-model${selectedModel?.id === model.id ? " active" : ""}`}
														disabled={saving}
														key={model.id}
														onClick={() => chooseModel(model)}
														type="button"
													>
														<span>▦</span>
														<span className="dmx-tree-model-copy">
															<b>{model.implementationPolicy?.physicalName || model.name}</b>
															<small>
																{model.name} · {modelTypeLabel[model.modelType]}
															</small>
														</span>
													</button>
												))
											: null}
									</div>
								);
							})}
							{!groups.length ? (
								<RequestState description={catalogEmptyMessage} kind="empty" title="暂无模型" />
							) : null}
						</div>
						{createOpen ? (
							<div className="dmx-create-menu">
								<strong>创建模型</strong>
								<button disabled={saving} onClick={() => createModel("dimension")} type="button">
									创建维度
								</button>
								<button disabled={saving} onClick={() => createModel("dimension-table")} type="button">
									创建维度表
								</button>
								<button disabled title="当前 ModelSpec 契约不拥有 ODS/STG 贴源对象" type="button">
									创建贴源表（尚未接入）
								</button>
								{(["fact", "summary", "application"] as ModelCreateKind[]).map((kind) => (
									<button disabled={saving} key={kind} onClick={() => createModel(kind)} type="button">
										创建{MODEL_KIND_CONFIG[kind].label}
									</button>
								))}
							</div>
						) : null}
					</aside>
					<section className="dmx-model-editor">
						<div className="dmx-editor-tab">
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
						{draft ? (
							<ModelingWorkbenchEditor
								canMaintain={canMaintain}
								context={context}
								currentOwnerId={ownerIdOf(userInfo)}
								dimensionDefinitionFailure={dimensionDefinitionFailure}
								dimensionDefinitions={dimensionDefinitions}
								dirty={dirty}
								draft={draft}
								failureMessage={failure?.message || ""}
								fieldRowIds={fieldRowIds}
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
								readOnly={!canMaintain || selectedModel?.compatibilityMode === "LEGACY_READONLY"}
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
				onClose={() => setDialog(null)}
			/>
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
