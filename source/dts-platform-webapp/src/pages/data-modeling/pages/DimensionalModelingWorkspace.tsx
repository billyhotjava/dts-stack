import {
	Boxes,
	ChevronDown,
	ChevronRight,
	Database,
	FileDown,
	Grid2X2,
	Layers3,
	Plus,
	RefreshCw,
	Search,
	Table2,
	WandSparkles,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { getDimensionDefinition, getDimensionDefinitionRevision } from "@/api/dimensionDefinitionApi";
import {
	type CreateDimensionModelCommand,
	getDimensionModelOperation,
	getModelSpecRevision,
	listModelSpecs,
} from "@/api/modelSpecApi";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { CanonicalModelSpecView, ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import {
	clearDimensionModelDraft,
	readDimensionModelDraft,
} from "@/features/modeling/operations/dimensionModelDraftSession";
import {
	clearDimensionModelOperationId,
	ensureDimensionModelOperationId,
	readDimensionModelOperationId,
} from "@/features/modeling/operations/dimensionModelOperationUrl";
import { useUserInfo } from "@/store/userStore";
import { DimensionDefinitionTargetView } from "../components/DimensionDefinitionTargetView";
import { ModelingEditor, type ModelObjectType, type ModelSelection } from "../components/ModelingEditor";
import { ModelDetailWorkbench } from "../components/model-detail/ModelDetailWorkbench";
import { ModelRepresentationState } from "../components/model-detail/ModelRepresentationState";
import { ReverseModelingWizard } from "../components/ReverseModelingWizard";
import { ActionButton, WorkspacePage } from "../components/WorkspacePage";
import { dataModelingPath } from "../navigation";
import type { WorkspacePageProps } from "../types";
import "./modeling-metrics.css";
import "./modeling-dialogs.css";
import "./modeling-metrics-extended.css";

const CREATION_ENTRIES: Array<{
	type: ModelObjectType;
	label: string;
	group: string;
	layer: string;
	icon: typeof Boxes;
}> = [
	{ type: "dimension", label: "创建维度", group: "概念模型", layer: "公共层 / 维度层", icon: Boxes },
	{ type: "dimension-table", label: "创建维度表", group: "逻辑模型", layer: "公共层 / 维度层", icon: Table2 },
	{ type: "fact", label: "创建明细表", group: "逻辑模型", layer: "公共层 / 明细层", icon: Table2 },
	{ type: "aggregate", label: "创建汇总表", group: "逻辑模型", layer: "公共层 / 汇总层", icon: Layers3 },
	{ type: "application", label: "创建应用表", group: "逻辑模型", layer: "应用层", icon: Grid2X2 },
];

const layerLabel = (model: ModelSpecView) => {
	if (model.layer === "ODS" || model.layer === "STG") return "贴源层";
	if (model.layer === "ADS") return "应用层";
	return "公共层";
};

const modelCode = (model: ModelSpecView) =>
	model.implementationPolicy?.physicalName ||
	model.variantCode ||
	`${model.modelType.toLowerCase()}_${model.id.slice(0, 8)}`;

const toDraftSelection = (type: ModelObjectType, domain: string): ModelSelection => {
	const entry = CREATION_ENTRIES.find((item) => item.type === type);
	const codeByType: Record<ModelObjectType, string> = {
		dimension: "",
		"dimension-table": "",
		fact: "",
		aggregate: "",
		application: "",
	};
	return {
		type,
		code: codeByType[type],
		name: entry?.label.replace("创建", "新建") || "新建模型",
		layer: entry?.layer || "公共层",
		domain: domain === "全部数据域" ? "待选择" : domain,
		isNew: true,
	};
};

type OperationRecoveryState = "IDLE" | "LOADING" | "DRAFT" | "ERROR" | "CONFLICT";

const operationStatus = (error: unknown) => (error as { response?: { status?: number } } | null)?.response?.status || 0;

export function DimensionalModelingWorkspace({ route }: WorkspacePageProps) {
	const navigate = useNavigate();
	const [searchParams, setSearchParams] = useSearchParams();
	const userInfo = useUserInfo();
	const view = route.view === "reverse" ? "reverse" : "workbench";
	const [models, setModels] = useState<ModelSpecView[]>([]);
	const [loading, setLoading] = useState(false);
	const [loadError, setLoadError] = useState<string | null>(null);
	const [targetLoading, setTargetLoading] = useState(false);
	const [targetError, setTargetError] = useState<string | null>(null);
	const [historicalModel, setHistoricalModel] = useState<ModelSpecView | null>(null);
	const [dimensionDefinitionTarget, setDimensionDefinitionTarget] = useState<DimensionDefinitionView | null>(null);
	const [layer, setLayer] = useState("公共层");
	const [domain, setDomain] = useState("全部数据域");
	const [query, setQuery] = useState("");
	const [creationOpen, setCreationOpen] = useState(false);
	const [draftSelection, setDraftSelection] = useState<ModelSelection | null>(null);
	const [editingModel, setEditingModel] = useState<CanonicalModelSpecView | null>(null);
	const [pendingDimensionCommand, setPendingDimensionCommand] = useState<CreateDimensionModelCommand | null>(null);
	const [editorBusy, setEditorBusy] = useState(false);
	const [operationRecovery, setOperationRecovery] = useState<OperationRecoveryState>("IDLE");
	const [operationRecoveryError, setOperationRecoveryError] = useState<string | null>(null);
	const [operationRecoveryNonce, setOperationRecoveryNonce] = useState(0);
	const [expandedDomains, setExpandedDomains] = useState<Set<string>>(new Set());
	const operationAbortRef = useRef<AbortController | null>(null);
	const locallyCreatedOperationRef = useRef<string | null>(null);
	const catalogRequestEpochRef = useRef(0);
	const editorUrlModelIdRef = useRef<string | null>(null);
	const selectedModelId = searchParams.get("modelSpecId");
	const dimensionDefinitionId = searchParams.get("dimensionDefinitionId");
	const revisionRequested = searchParams.has("revision");
	const revisionCandidate = Number(searchParams.get("revision"));
	const requestedRevision = Number.isInteger(revisionCandidate) && revisionCandidate > 0 ? revisionCandidate : null;
	const createRequested = searchParams.get("create") === "1";
	const rawOperationId = searchParams.get("dmOperationId");
	const dimensionOperationId = readDimensionModelOperationId(searchParams);
	const currentNavigationRef = useRef({ selectedModelId, dimensionOperationId });
	currentNavigationRef.current = { selectedModelId, dimensionOperationId };
	const dimensionActorScope = [userInfo.id, userInfo.username, userInfo.email].filter(Boolean).join("|");
	const workspaceNavigationLocked = editorBusy || Boolean(rawOperationId);

	const refreshModels = useCallback(async () => {
		const requestEpoch = ++catalogRequestEpochRef.current;
		setLoading(true);
		setLoadError(null);
		try {
			const result = await listModelSpecs();
			if (catalogRequestEpochRef.current !== requestEpoch) return;
			setModels(result);
			setExpandedDomains(new Set(result.map((model) => model.domainId || "未归属数据域")));
		} catch {
			if (catalogRequestEpochRef.current === requestEpoch) setLoadError("MODEL_SPEC_CATALOG_READ_FAILED");
		} finally {
			if (catalogRequestEpochRef.current === requestEpoch) setLoading(false);
		}
	}, []);

	useEffect(() => {
		if (view === "workbench") void refreshModels();
	}, [refreshModels, view]);

	useEffect(() => {
		void operationRecoveryNonce;
		operationAbortRef.current?.abort();
		if (view !== "workbench" || !rawOperationId) {
			locallyCreatedOperationRef.current = null;
			setOperationRecovery("IDLE");
			setOperationRecoveryError(null);
			return;
		}
		if (!dimensionOperationId) {
			setOperationRecovery("ERROR");
			setOperationRecoveryError("DIMENSION_MODEL_OPERATION_ID_INVALID");
			return;
		}
		if (locallyCreatedOperationRef.current === dimensionOperationId) {
			setOperationRecovery("DRAFT");
			setOperationRecoveryError(null);
			return;
		}
		const controller = new AbortController();
		operationAbortRef.current = controller;
		setOperationRecovery("LOADING");
		setOperationRecoveryError(null);
		void getDimensionModelOperation(dimensionOperationId, controller.signal)
			.then((operation) => {
				if (controller.signal.aborted) return;
				if (
					operation.operationId !== dimensionOperationId ||
					operation.replayed !== true ||
					operation.modelSpecRevision.revision !== 2 ||
					operation.dimensionDefinitionRevision.revision < 1 ||
					operation.modelSpecRevision.id !== operation.currentModelSpec.id ||
					operation.currentModelSpec.contractVersion !== 2
				) {
					setOperationRecovery("ERROR");
					setOperationRecoveryError("DIMENSION_MODEL_OPERATION_RESPONSE_INVALID");
					return;
				}
				const currentModel = operation.currentModelSpec;
				setModels((current) => [currentModel, ...current.filter((model) => model.id !== currentModel.id)]);
				setHistoricalModel(null);
				setDimensionDefinitionTarget(null);
				setDraftSelection(null);
				setEditingModel(null);
				setPendingDimensionCommand(null);
				editorUrlModelIdRef.current = null;
				setEditorBusy(false);
				setOperationRecovery("IDLE");
				const cleared = clearDimensionModelOperationId(searchParams, dimensionOperationId);
				const acknowledged = new URLSearchParams(cleared.searchParams);
				acknowledged.delete("create");
				acknowledged.delete("dimensionDefinitionId");
				acknowledged.delete("revision");
				acknowledged.set("modelSpecId", currentModel.id);
				setSearchParams(acknowledged, { replace: true });
				clearDimensionModelDraft(dimensionOperationId);
				void refreshModels();
			})
			.catch(async (cause) => {
				if (controller.signal.aborted) return;
				const status = operationStatus(cause);
				if (status === 404) {
					if (!dimensionActorScope) {
						setOperationRecovery("ERROR");
						setOperationRecoveryError("DIMENSION_MODEL_ACTOR_SCOPE_REQUIRED");
						return;
					}
					let restored: Awaited<ReturnType<typeof readDimensionModelDraft>>;
					try {
						restored = await readDimensionModelDraft(dimensionOperationId, dimensionActorScope);
					} catch {
						setOperationRecovery("ERROR");
						setOperationRecoveryError("DIMENSION_MODEL_DRAFT_STORAGE_UNAVAILABLE");
						return;
					}
					if (controller.signal.aborted) return;
					if (restored.kind === "FOUND") {
						const command = restored.command;
						editorUrlModelIdRef.current = selectedModelId;
						setPendingDimensionCommand(command);
						setEditingModel(null);
						setDraftSelection(
							toDraftSelection(
								command.definitionBinding.mode === "CREATE" ? "dimension" : "dimension-table",
								command.modelSpec.domainId,
							),
						);
						setCreationOpen(false);
						setOperationRecovery("DRAFT");
						return;
					}
					setOperationRecovery("ERROR");
					setOperationRecoveryError(`DIMENSION_MODEL_RECOVERY_${restored.kind}`);
					return;
				}
				if (status === 409) {
					setOperationRecovery("CONFLICT");
					setOperationRecoveryError("DIMENSION_MODEL_OPERATION_CONFLICT");
					return;
				}
				setOperationRecovery("ERROR");
				setOperationRecoveryError("DIMENSION_MODEL_OPERATION_READ_FAILED");
			});
		return () => controller.abort();
	}, [
		dimensionActorScope,
		dimensionOperationId,
		operationRecoveryNonce,
		rawOperationId,
		refreshModels,
		searchParams,
		selectedModelId,
		setSearchParams,
		view,
	]);

	useEffect(() => {
		if (!draftSelection || selectedModelId === editorUrlModelIdRef.current) return;
		setDraftSelection(null);
		setEditingModel(null);
		setPendingDimensionCommand(null);
		setEditorBusy(false);
	}, [draftSelection, selectedModelId]);

	useEffect(() => {
		let active = true;
		setHistoricalModel(null);
		setDimensionDefinitionTarget(null);
		setTargetError(null);
		if (view !== "workbench" || rawOperationId || (!selectedModelId && !dimensionDefinitionId)) {
			setTargetLoading(false);
			return () => {
				active = false;
			};
		}
		if (revisionRequested && !requestedRevision) {
			setTargetError("REVISION_PARAMETER_INVALID");
			setTargetLoading(false);
			return () => {
				active = false;
			};
		}
		if (!dimensionDefinitionId && !revisionRequested) {
			setTargetLoading(false);
			return () => {
				active = false;
			};
		}
		setTargetLoading(true);
		const request = dimensionDefinitionId
			? requestedRevision
				? getDimensionDefinitionRevision(dimensionDefinitionId, requestedRevision)
				: getDimensionDefinition(dimensionDefinitionId)
			: getModelSpecRevision(selectedModelId as string, requestedRevision as number);
		void request
			.then((target) => {
				if (!active) return;
				if (dimensionDefinitionId) setDimensionDefinitionTarget(target as DimensionDefinitionView);
				else setHistoricalModel(target as ModelSpecView);
			})
			.catch(() => {
				if (active) setTargetError("FIXED_REVISION_READ_FAILED_OR_FORBIDDEN");
			})
			.finally(() => {
				if (active) setTargetLoading(false);
			});
		return () => {
			active = false;
		};
	}, [dimensionDefinitionId, rawOperationId, requestedRevision, revisionRequested, selectedModelId, view]);

	useEffect(() => {
		if (view !== "workbench" || rawOperationId || operationRecovery !== "IDLE" || !createRequested) return;
		const nextSearchParams = new URLSearchParams(searchParams);
		nextSearchParams.delete("create");
		if (selectedModelId || dimensionDefinitionId) {
			setSearchParams(nextSearchParams, { replace: true });
			return;
		}
		setDraftSelection(null);
		setEditingModel(null);
		setCreationOpen(true);
		setSearchParams(nextSearchParams, { replace: true });
	}, [
		createRequested,
		dimensionDefinitionId,
		operationRecovery,
		rawOperationId,
		searchParams,
		selectedModelId,
		setSearchParams,
		view,
	]);

	useEffect(() => {
		if (
			view === "workbench" &&
			!rawOperationId &&
			operationRecovery === "IDLE" &&
			!createRequested &&
			!creationOpen &&
			!draftSelection &&
			!selectedModelId &&
			!dimensionDefinitionId &&
			models[0]
		) {
			setSearchParams({ modelSpecId: models[0].id }, { replace: true });
		}
	}, [
		createRequested,
		creationOpen,
		dimensionDefinitionId,
		draftSelection,
		models,
		operationRecovery,
		rawOperationId,
		selectedModelId,
		setSearchParams,
		view,
	]);

	const domains = useMemo(
		() => Array.from(new Set(models.map((model) => model.domainId || "未归属数据域"))).sort(),
		[models],
	);
	const catalog = useMemo(() => {
		const filtered = models.filter((model) => {
			const normalizedQuery = query.trim().toLowerCase();
			const modelDomain = model.domainId || "未归属数据域";
			return (
				(domain === "全部数据域" || domain === modelDomain) &&
				layerLabel(model) === layer &&
				(!normalizedQuery ||
					modelCode(model).toLowerCase().includes(normalizedQuery) ||
					model.name.toLowerCase().includes(normalizedQuery))
			);
		});
		return Array.from(new Set(filtered.map((model) => model.domainId || "未归属数据域"))).map((name) => ({
			name,
			models: filtered.filter((model) => (model.domainId || "未归属数据域") === name),
		}));
	}, [domain, layer, models, query]);
	const latestSelectedModel = models.find((model) => model.id === selectedModelId) || null;
	const selectedModel =
		historicalModel?.id === selectedModelId && historicalModel.revision === requestedRevision
			? historicalModel
			: latestSelectedModel;

	const selectModel = (model: ModelSpecView) => {
		if (workspaceNavigationLocked) return;
		editorUrlModelIdRef.current = null;
		setDraftSelection(null);
		setEditingModel(null);
		setPendingDimensionCommand(null);
		setSearchParams({ modelSpecId: model.id });
	};

	const createModel = (type: ModelObjectType) => {
		if (workspaceNavigationLocked) return;
		if (type === "dimension" || type === "dimension-table") {
			const operation = ensureDimensionModelOperationId(searchParams);
			locallyCreatedOperationRef.current = operation.operationId;
			const next = new URLSearchParams(operation.searchParams);
			next.delete("modelSpecId");
			next.delete("dimensionDefinitionId");
			next.delete("revision");
			next.delete("create");
			editorUrlModelIdRef.current = null;
			setSearchParams(next, { replace: true });
		} else if (rawOperationId) {
			return;
		} else {
			editorUrlModelIdRef.current = selectedModelId;
		}
		setEditingModel(null);
		setPendingDimensionCommand(null);
		setDraftSelection(toDraftSelection(type, domain));
		setCreationOpen(false);
	};

	const editModel = (model: ModelSpecView) => {
		if (workspaceNavigationLocked) return;
		if (model.compatibilityMode !== "CANONICAL" || model.contractVersion !== 2) return;
		const type: ModelObjectType =
			model.modelType === "DIMENSION"
				? "dimension-table"
				: model.modelType === "FACT"
					? "fact"
					: model.modelType === "SUMMARY"
						? "aggregate"
						: "application";
		editorUrlModelIdRef.current = model.id;
		setEditingModel(model);
		setPendingDimensionCommand(null);
		setDraftSelection({
			type,
			code: modelCode(model),
			name: model.name,
			layer: layerLabel(model),
			domain: model.domainId,
		});
	};

	const handleSaved = async (saved: CanonicalModelSpecView, completedOperationId?: string) => {
		const currentNavigation = currentNavigationRef.current;
		if (currentNavigation.selectedModelId !== editorUrlModelIdRef.current) {
			throw new Error("MODEL_SPEC_STALE_NAVIGATION_RESULT");
		}
		if (completedOperationId && currentNavigation.dimensionOperationId !== completedOperationId) {
			throw new Error("DIMENSION_MODEL_OPERATION_ACK_MISMATCH");
		}
		setModels((current) => [saved, ...current.filter((model) => model.id !== saved.id)]);
		setDraftSelection(null);
		setEditingModel(null);
		setPendingDimensionCommand(null);
		editorUrlModelIdRef.current = null;
		setEditorBusy(false);
		if (completedOperationId) {
			locallyCreatedOperationRef.current = null;
			const cleared = clearDimensionModelOperationId(searchParams, completedOperationId);
			const next = new URLSearchParams(cleared.searchParams);
			next.delete("create");
			next.set("modelSpecId", saved.id);
			setSearchParams(next, { replace: true });
			clearDimensionModelDraft(completedOperationId);
		} else {
			setSearchParams({ modelSpecId: saved.id });
		}
		await refreshModels();
	};

	const cancelEditor = () => {
		if (editorBusy) return;
		editorUrlModelIdRef.current = null;
		setDraftSelection(null);
		setEditingModel(null);
		setPendingDimensionCommand(null);
		if (rawOperationId) {
			locallyCreatedOperationRef.current = null;
			const next = dimensionOperationId
				? clearDimensionModelOperationId(searchParams, dimensionOperationId).searchParams
				: new URLSearchParams(searchParams);
			if (!dimensionOperationId) next.delete("dmOperationId");
			setSearchParams(next, { replace: true });
			if (dimensionOperationId) clearDimensionModelDraft(dimensionOperationId);
		}
	};

	return (
		<WorkspacePage
			description="在同一固定修订上下文中维护业务模型、显式高级 dbt 实现和物理资产证据。"
			eyebrow="统一模型工作台"
			title={view === "reverse" ? "逆向建模" : "维度建模"}
		>
			<div className="dm-model-workspace">
				<nav aria-label="建模方式" className="dm-model-module-rail">
					<button
						className={view === "workbench" ? "is-active" : ""}
						disabled={workspaceNavigationLocked}
						onClick={() => navigate(dataModelingPath("dimensions", "workbench"))}
						type="button"
					>
						<Boxes aria-hidden="true" size={17} />
						<span>维度建模</span>
					</button>
					<button
						className={view === "reverse" ? "is-active" : ""}
						disabled={workspaceNavigationLocked}
						onClick={() => navigate(dataModelingPath("dimensions", "reverse"))}
						type="button"
					>
						<WandSparkles aria-hidden="true" size={17} />
						<span>逆向建模</span>
					</button>
				</nav>

				{view === "workbench" ? (
					<>
						<aside className="dm-model-catalog">
							<header>
								<strong>模型目录</strong>
								<div>
									<button
										aria-expanded={creationOpen}
										aria-label="新建模型"
										className="dm-icon-button"
										disabled={workspaceNavigationLocked}
										onClick={() => setCreationOpen((current) => !current)}
										type="button"
									>
										<Plus aria-hidden="true" size={17} />
									</button>
									<button
										aria-label="导入 dbt ZIP"
										className="dm-icon-button"
										disabled={workspaceNavigationLocked}
										onClick={() => navigate(`${dataModelingPath("dimensions", "reverse")}?source=dbt`)}
										type="button"
									>
										<FileDown aria-hidden="true" size={16} />
									</button>
									<button
										aria-label="刷新目录"
										className="dm-icon-button"
										disabled={workspaceNavigationLocked}
										onClick={() => void refreshModels()}
										type="button"
									>
										<RefreshCw aria-hidden="true" size={16} />
									</button>
								</div>
							</header>
							{creationOpen ? (
								<div className="dm-create-popover">
									{["概念模型", "逻辑模型"].map((group) => (
										<div key={group}>
											<small>{group}</small>
											{CREATION_ENTRIES.filter((entry) => entry.group === group).map((entry) => {
												const Icon = entry.icon;
												return (
													<button
														disabled={workspaceNavigationLocked}
														key={entry.type}
														onClick={() => createModel(entry.type)}
														type="button"
													>
														<Icon aria-hidden="true" size={15} />
														<span>
															<strong>{entry.label}</strong>
															<small>{entry.layer}</small>
														</span>
													</button>
												);
											})}
										</div>
									))}
								</div>
							) : null}
							<div className="dm-layer-tabs">
								{["贴源层", "公共层", "应用层"].map((item) => (
									<button
										className={layer === item ? "is-active" : ""}
										disabled={workspaceNavigationLocked}
										key={item}
										onClick={() => setLayer(item)}
										type="button"
									>
										{item}
									</button>
								))}
							</div>
							<div className="dm-catalog-filter">
								<select
									aria-label="选择数据域"
									className="dm-select"
									disabled={workspaceNavigationLocked}
									onChange={(event) => setDomain(event.target.value)}
									value={domain}
								>
									<option>全部数据域</option>
									{domains.map((item) => (
										<option key={item}>{item}</option>
									))}
								</select>
								<label>
									<Search aria-hidden="true" size={14} />
									<input
										aria-label="搜索模型"
										disabled={workspaceNavigationLocked}
										onChange={(event) => setQuery(event.target.value)}
										placeholder="搜索模型编码或名称"
										type="search"
										value={query}
									/>
								</label>
							</div>
							<div className="dm-object-tree">
								{loading ? <ModelRepresentationState state="loading" /> : null}
								{!loading && loadError ? <ModelRepresentationState message={loadError} state="error" /> : null}
								{!loading && !loadError && catalog.length === 0 ? <ModelRepresentationState state="empty" /> : null}
								{!loading && !loadError
									? catalog.map((group) => {
											const expanded = expandedDomains.has(group.name);
											return (
												<div className="dm-tree-group" key={group.name}>
													<button
														className="dm-tree-group__title"
														disabled={workspaceNavigationLocked}
														onClick={() =>
															setExpandedDomains((current) => {
																const next = new Set(current);
																if (expanded) next.delete(group.name);
																else next.add(group.name);
																return next;
															})
														}
														type="button"
													>
														{expanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
														<Database size={14} />
														<strong>{group.name}</strong>
														<span>{group.models.length}</span>
													</button>
													{expanded
														? group.models.map((model) => (
																<button
																	className={`dm-tree-node ${selectedModelId === model.id ? "is-active" : ""}`}
																	disabled={workspaceNavigationLocked}
																	key={model.id}
																	onClick={() => selectModel(model)}
																	title={model.name}
																	type="button"
																>
																	<Table2 aria-hidden="true" size={13} />
																	<span>
																		<strong>{modelCode(model)}</strong>
																		<small>{model.name}</small>
																	</span>
																	<i className={model.status === "PUBLISHED" ? "is-published" : ""} />
																</button>
															))
														: null}
												</div>
											);
										})
									: null}
							</div>
							<footer>
								<span>目录对象 {models.length}</span>
								<ActionButton disabled={workspaceNavigationLocked} kind="quiet" onClick={() => setQuery("")}>
									清除筛选
								</ActionButton>
							</footer>
						</aside>
						{operationRecovery === "LOADING" ? (
							<ModelRepresentationState state="loading" />
						) : operationRecovery === "ERROR" || operationRecovery === "CONFLICT" ? (
							<section className="dm-model-editor">
								<div className="dm-pending-callout" role="alert">
									{operationRecoveryError || "DIMENSION_MODEL_OPERATION_READ_FAILED"}
								</div>
								<ActionButton onClick={() => setOperationRecoveryNonce((current) => current + 1)}>
									重试恢复
								</ActionButton>
								<ActionButton onClick={cancelEditor}>放弃本次恢复</ActionButton>
							</section>
						) : draftSelection ? (
							<ModelingEditor
								dimensionActorScope={dimensionActorScope}
								dimensionOperationId={dimensionOperationId}
								initialDimensionCommand={pendingDimensionCommand}
								key={`${draftSelection.type}:${draftSelection.code}:${editingModel?.id || dimensionOperationId || "new"}:${selectedModelId || "none"}`}
								model={editingModel}
								onBusyChange={setEditorBusy}
								onCancel={cancelEditor}
								onSaved={handleSaved}
								selection={draftSelection}
							/>
						) : dimensionDefinitionId ? (
							<DimensionDefinitionTargetView
								definition={dimensionDefinitionTarget}
								error={targetError}
								loading={targetLoading}
							/>
						) : targetLoading ? (
							<ModelRepresentationState state="loading" />
						) : targetError ? (
							<ModelRepresentationState message={targetError} state="error" />
						) : selectedModel ? (
							<ModelDetailWorkbench
								key={`${selectedModel.id}-${selectedModel.revision}`}
								model={selectedModel}
								onEdit={
									latestSelectedModel?.revision === selectedModel.revision && !revisionRequested
										? () => editModel(selectedModel)
										: undefined
								}
							/>
						) : (
							<ModelRepresentationState state="empty" />
						)}
					</>
				) : (
					<div className="dm-reverse-workspace">
						<ReverseModelingWizard />
					</div>
				)}
			</div>
		</WorkspacePage>
	);
}
