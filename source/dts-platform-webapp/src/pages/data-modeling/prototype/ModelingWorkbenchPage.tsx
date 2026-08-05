import { ChevronDown, ChevronRight, FileDown, GitBranch, Import, ListFilter, Plus, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { type BlockerFunction, useBlocker, useNavigate, useSearchParams } from "react-router";
import { getModelRepresentation } from "@/api/modelRepresentationApi";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelRepresentationView } from "@/features/modeling/contracts/modelRepresentationContract";
import type { ModelSpecField, ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { useUserInfo } from "@/store/userStore";
import { dataModelingPath } from "../navigation";
import type { DataModelingRoute } from "../types";

import { ConceptDimensionRecordDialog } from "./ConceptDimensionRecordDialog";
import { isBlankModelField } from "./ModelFieldEditorTable";
import { ModelingWorkbenchEditor } from "./ModelingWorkbenchEditor";
import { ModelWorkbenchDialog, type WorkbenchDialog } from "./ModelWorkbenchDialog";
import { modelDraftFingerprint } from "./modelWorkbenchPresentation";
import { PageHeader, RequestState, Status, Toast, useTransientMessage } from "./PrototypePrimitives";
import {
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
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";
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

	const domainNames = useMemo(() => new Map((context?.domains || []).map((item) => [item.id, item.name])), [context]);
	const modelDomainOptions = useMemo(
		() =>
			Array.from(
				new Set(
					(context?.models || [])
						.map((model) => model.domainId)
						.filter((domainId): domainId is string => Boolean(domainId)),
				),
			).map((domainId) => ({
				id: domainId,
				name: domainNames.get(domainId) || domainId,
			})),
		[context?.models, domainNames],
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
	const groups = useMemo(() => {
		const result = new Map<string, ModelSpecView[]>();
		for (const model of visibleModels) {
			const label = domainNames.get(model.domainId || "") || model.domainId || "未归属数据域";
			result.set(label, [...(result.get(label) || []), model]);
		}
		return Array.from(result.entries());
	}, [domainNames, visibleModels]);
	const catalogEmptyMessage = !(context?.models.length || 0)
		? "当前数据域尚无模型。"
		: !filteredModels.length
			? "当前分层或数据域下暂无模型。"
			: !visibleModels.length
				? "没有符合搜索条件的模型。"
				: "";

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
		replaceDraft(next);
		setCreateOpen(false);
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
				show(`维度草稿已保存：${saved.systemCode} · r${saved.revision}`);
			} catch (error) {
				setFailure(normalizeModelingRequestFailure(error, "维度保存失败。"));
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
	const confirmConceptVersion = async () => {
		if (savingRef.current || !conceptDraft?.definitionBase || !canMaintain) return;
		setFailure(null);
		savingRef.current = true;
		setSaving(true);
		try {
			const confirmed = await confirmDimensionDefinitionDraft(conceptDraft);
			replaceDraft(confirmed);
			show(`维度版本已确认：${confirmed.definitionBase?.systemCode} · r${confirmed.definitionBase?.revision}`);
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "维度版本确认失败。"));
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
							{groups.map(([group, models]) => {
								const open = domainOpen[group] !== false;
								return (
									<div key={group}>
										<button
											className="dmx-tree-domain"
											disabled={saving}
											onClick={() => setDomainOpen((current) => ({ ...current, [group]: !open }))}
											type="button"
										>
											{open ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
											<span>🌐</span>
											<strong>{group}</strong>
											<em>({models.length})</em>
										</button>
										{open
											? models.map((model) => (
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
							{!groups.length ? <RequestState description={catalogEmptyMessage} kind="empty" title="暂无模型" /> : null}
						</div>
						{createOpen ? (
							<div className="dmx-create-menu">
								<strong>概念模型</strong>
								<button disabled={saving} onClick={() => createModel("dimension")} type="button">
									创建维度
								</button>
								<strong>逻辑模型</strong>
								<button disabled title="当前 ModelSpec 契约不拥有 ODS/STG 贴源对象" type="button">
									创建贴源表（尚未接入）
								</button>
								{(["dimension-table", "fact", "summary", "application"] as ModelCreateKind[]).map((kind) => (
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
								<Status tone="warning">
									{draft.definitionBase.status} · r{draft.definitionBase.revision}
								</Status>
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
					{draft ? (
						<aside className="dmx-record-rail">
							<button
								disabled={saving || !(selectedModel || conceptDraft?.definitionBase)}
								onClick={() => setDialog("versions")}
								type="button"
							>
								<GitBranch size={16} />
								版本管理
							</button>
							<button
								disabled={saving || !(selectedModel || conceptDraft?.definitionBase)}
								onClick={() => setDialog("releases")}
								type="button"
							>
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
