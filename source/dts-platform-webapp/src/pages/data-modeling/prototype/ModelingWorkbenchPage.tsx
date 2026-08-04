import {
	ChevronDown,
	ChevronRight,
	DatabaseZap,
	Eye,
	FileDown,
	GitBranch,
	Import,
	Link2,
	ListChecks,
	ListFilter,
	Plus,
	RefreshCw,
	Save,
	Settings2,
	ShieldCheck,
	Trash2,
	Upload,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { getModelRepresentation } from "@/api/modelRepresentationApi";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelRepresentationView } from "@/features/modeling/contracts/modelRepresentationContract";
import type { ModelSpecField, ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { useUserInfo } from "@/store/userStore";
import { dataModelingPath } from "../navigation";
import type { DataModelingRoute } from "../types";
import { ModelWorkbenchDialog, type WorkbenchDialog } from "./ModelWorkbenchDialog";
import { Button, PageHeader, RequestState, Status, Toast, useTransientMessage } from "./PrototypePrimitives";
import {
	emptyModelDraft,
	loadCurrentDimensionDefinitions,
	loadModelWorkbenchContext,
	MODEL_KIND_CONFIG,
	type ModelCreateKind,
	type ModelDraft,
	type ModelWorkbenchContext,
	modelDraftFromView,
	saveModelDraft,
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

const ownerIdOf = (userInfo: unknown) => {
	if (!userInfo || typeof userInfo !== "object") return "";
	const value = (userInfo as Record<string, unknown>).id;
	return value == null ? "" : String(value).trim();
};

export function ModelingWorkbenchPage({ route }: { route: DataModelingRoute }) {
	const navigate = useNavigate();
	const canMaintain = useDataModelingMenuGrant();
	const userInfo = useUserInfo();
	const requestEpoch = useRef(0);
	const [searchParams, setSearchParams] = useSearchParams();
	const requestedModelId = searchParams.get("modelSpecId") || "";
	const [context, setContext] = useState<ModelWorkbenchContext | null>(null);
	const [draft, setDraft] = useState<ModelDraft | null>(null);
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
	const draftBase = draft?.base || null;
	const draftCreateKind = draft?.createKind || null;
	const draftDimensionDefinitionId = draft?.dimensionDefinitionId || "";
	const draftDomainId = draft?.domainId || "";

	const load = useCallback(
		async (preferredModelId?: string) => {
			const epoch = ++requestEpoch.current;
			setLoading(true);
			setFailure(null);
			try {
				const next = await loadModelWorkbenchContext();
				if (requestEpoch.current !== epoch) return;
				setContext(next);
				const targetId = preferredModelId || requestedModelId;
				const target = next.models.find((model) => model.id === targetId) || next.models[0] || null;
				const nextDraft = target ? modelDraftFromView(target) : null;
				setDraft(nextDraft);
				setFieldRowIds(nextDraft?.fields.map(() => crypto.randomUUID()) || []);
			} catch (error) {
				if (requestEpoch.current !== epoch) return;
				setContext(null);
				setDraft(null);
				setFieldRowIds([]);
				setFailure(normalizeModelingRequestFailure(error, "模型目录读取失败。"));
			} finally {
				if (requestEpoch.current === epoch) setLoading(false);
			}
		},
		[requestedModelId],
	);

	useEffect(() => {
		void load();
		return () => {
			requestEpoch.current += 1;
		};
	}, [load]);

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
					setDraft((current) => (current ? { ...current, dimensionDefinitionId: items[0].id } : current));
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

	const domainNames = useMemo(() => new Map((context?.domains || []).map((item) => [item.code, item.name])), [context]);
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
	const visibleModels = useMemo(() => {
		const activeLayers = layerTabs.find((item) => item.label === layer)?.layers || [];
		const normalized = query.trim().toLowerCase();
		return (context?.models || []).filter(
			(model) =>
				activeLayers.includes(model.layer) &&
				(!domain || model.domainId === domain) &&
				(!normalized ||
					`${model.name}${model.implementationPolicy?.physicalName || ""}`.toLowerCase().includes(normalized)),
		);
	}, [context, domain, layer, query]);
	const groups = useMemo(() => {
		const result = new Map<string, ModelSpecView[]>();
		for (const model of visibleModels) {
			const label = domainNames.get(model.domainId || "") || model.domainId || "未归属数据域";
			result.set(label, [...(result.get(label) || []), model]);
		}
		return Array.from(result.entries());
	}, [domainNames, visibleModels]);

	const chooseModel = (model: ModelSpecView) => {
		const nextDraft = modelDraftFromView(model);
		setDraft(nextDraft);
		setFieldRowIds(nextDraft.fields.map(() => crypto.randomUUID()));
		setCreateOpen(false);
		const next = new URLSearchParams(searchParams);
		next.set("modelSpecId", model.id);
		setSearchParams(next, { replace: true });
	};

	const createModel = (kind: ModelCreateKind) => {
		if (!context) return;
		const next = emptyModelDraft(kind, context);
		if (draft?.planId) next.planId = draft.planId;
		if (draft?.domainId) next.domainId = draft.domainId;
		setDraft(next);
		setFieldRowIds(next.fields.map(() => crypto.randomUUID()));
		setCreateOpen(false);
		const params = new URLSearchParams(searchParams);
		params.delete("modelSpecId");
		setSearchParams(params, { replace: true });
	};

	const save = async () => {
		if (!draft || !context || !canMaintain) return;
		setSaving(true);
		setFailure(null);
		try {
			const saved = await saveModelDraft(draft, {
				ownerId: ownerIdOf(userInfo),
				dimensionDefinitions,
			});
			const savedDraft = modelDraftFromView(saved);
			setDraft(savedDraft);
			setFieldRowIds(savedDraft.fields.map(() => crypto.randomUUID()));
			const next = new URLSearchParams(searchParams);
			next.set("modelSpecId", saved.id);
			setSearchParams(next, { replace: true });
			show(`模型草稿已保存：r${saved.revision}`);
			await load(saved.id);
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "模型保存失败。"));
		} finally {
			setSaving(false);
		}
	};

	const updateField = (index: number, patch: Partial<ModelSpecField>) =>
		setDraft((current) => {
			if (!current) return current;
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
	const addField = () => {
		setFieldRowIds((current) => [...current, crypto.randomUUID()]);
		setDraft((current) =>
			current
				? {
						...current,
						fields: [
							...current.fields,
							{
								name: "",
								displayName: "",
								dataType: "STRING",
								nullable: true,
								role: "ATTRIBUTE",
								dimensionAttributeCode: null,
							},
						],
					}
				: current,
		);
	};
	const deleteField = (index: number) => {
		setFieldRowIds((current) => current.filter((_, row) => row !== index));
		setDraft((current) => {
			if (!current) return current;
			const fieldName = current.fields[index]?.name;
			return {
				...current,
				fields: current.fields.filter((_, row) => row !== index),
				standardBindings: fieldName
					? current.standardBindings.filter((binding) => binding.fieldName !== fieldName)
					: current.standardBindings,
			};
		});
	};
	const selectedModel = draft?.base || null;
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
									disabled={!canMaintain}
									onClick={() => setCreateOpen((open) => !open)}
									title={canMaintain ? "新建模型" : "当前账号无建模维护权限"}
									type="button"
								>
									<Plus size={16} />
								</button>
								<button
									aria-label="导入"
									onClick={() => navigate(dataModelingPath("dimensions", "reverse"))}
									type="button"
								>
									<Import size={16} />
								</button>
								<button aria-label="刷新" disabled={loading} onClick={() => void load(selectedModel?.id)} type="button">
									<RefreshCw size={16} />
								</button>
							</div>
						</header>
						<div className="dmx-layer-tabs">
							{layerTabs.map((item) => (
								<button
									className={layer === item.label ? "active" : ""}
									key={item.label}
									onClick={() => setLayer(item.label)}
									type="button"
								>
									{item.label}
								</button>
							))}
						</div>
						<div className="dmx-object-filters">
							<select aria-label="筛选数据域" onChange={(event) => setDomain(event.target.value)} value={domain}>
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
								<RequestState description="当前分层或筛选条件下没有模型。" kind="empty" title="暂无模型" />
							) : null}
						</div>
						{createOpen ? (
							<div className="dmx-create-menu">
								<strong>概念模型</strong>
								<button onClick={() => createModel("dimension")} type="button">
									创建维度
								</button>
								<strong>逻辑模型</strong>
								<button disabled title="当前 ModelSpec 契约不拥有 ODS/STG 贴源对象" type="button">
									创建贴源表（尚未接入）
								</button>
								{(["dimension-table", "fact", "summary", "application"] as ModelCreateKind[]).map((kind) => (
									<button key={kind} onClick={() => createModel(kind)} type="button">
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
							{selectedModel ? (
								<Status tone={selectedModel.status === "PUBLISHED" ? "success" : "warning"}>
									{selectedModel.status} · r{selectedModel.revision}
								</Status>
							) : null}
						</div>
						{draft ? (
							<>
								{selectedModel ? (
									<div className="dmx-model-context">
										<span>模型 r{selectedModel.revision}</span>
										<span title={selectedModel.checksum}>模型校验和 {selectedModel.checksum.slice(0, 12)}</span>
										<span>
											实现{" "}
											{representation?.implementationRevision ? `r${representation.implementationRevision}` : "尚无"}
										</span>
										<span title={representation?.implementationChecksum || undefined}>
											实现校验和 {representation?.implementationChecksum?.slice(0, 12) || "—"}
										</span>
										<span>所有权 {representation?.ownershipMode || selectedModel.implementationMode}</span>
										<span>可视化 {representation?.visualizationCapability || "读取中"}</span>
										<span>漂移 {representation?.driftStatus || "—"}</span>
									</div>
								) : null}
								{representationFailure ? <div className="dmx-capability-note">{representationFailure}</div> : null}
								<div className="dmx-editor-toolbar">
									<button
										className="primary"
										disabled={!canMaintain || saving || selectedModel?.compatibilityMode === "LEGACY_READONLY"}
										onClick={() => void save()}
										title={canMaintain ? "保存模型草稿" : "当前账号无建模维护权限"}
										type="button"
									>
										<Save size={15} />
										{saving ? "保存中…" : "保存"}
									</button>
									<button disabled={!selectedModel} onClick={() => setDialog("gates")} type="button">
										<ListChecks size={15} />
										提交检查
									</button>
									<button onClick={() => void load(selectedModel?.id)} type="button">
										<RefreshCw size={15} />
										刷新
									</button>
									<button disabled={!selectedModel} onClick={() => setDialog("association")} type="button">
										<Link2 size={15} />
										关联关系
									</button>
									<button
										disabled={!selectedModel || !canMaintain}
										onClick={() => setDialog("publish")}
										title={canMaintain ? undefined : "当前账号无发布与物化权限"}
										type="button"
									>
										<Upload size={15} />
										发布与物化
									</button>
									<button disabled={!selectedModel} onClick={() => setDialog("logs")} type="button">
										<FileDown size={15} />
										日志
									</button>
									<button disabled={!selectedModel} onClick={() => setDialog("quality")} type="button">
										<ShieldCheck size={15} />
										质量门禁
									</button>
									<button
										disabled={!selectedModel || !canMaintain}
										onClick={() => setDialog("advanced")}
										title={canMaintain ? undefined : "当前账号无高级实现维护权限"}
										type="button"
									>
										<Settings2 size={15} />
										高级 dbt
									</button>
									<button disabled={!selectedModel} onClick={() => setDialog("preview")} type="button">
										<DatabaseZap size={15} />
										物理预览
									</button>
									<button disabled title="尚无模型导出服务端契约" type="button">
										<Import size={15} />
										导出
									</button>
								</div>
								{failure ? (
									<div className="dmx-inline-error" role="alert">
										{failure.message}
									</div>
								) : null}
								<ModelEditor
									dimensionDefinitionFailure={dimensionDefinitionFailure}
									dimensionDefinitions={dimensionDefinitions}
									domains={context.domains}
									draft={draft}
									fieldRowIds={fieldRowIds}
									onAddField={addField}
									onChange={setDraft}
									onDeleteField={deleteField}
									onStandardChange={(index, value) =>
										setDraft((current) => {
											if (!current) return current;
											const fieldName = current.fields[index]?.name || "";
											const remaining = current.standardBindings.filter((binding) => binding.fieldName !== fieldName);
											if (!value || !fieldName) return { ...current, standardBindings: remaining };
											const [standardElementId, version] = value.split("@");
											return {
												...current,
												standardBindings: [
													...remaining,
													{ fieldName, standardElementId, standardElementVersion: Number(version) },
												],
											};
										})
									}
									onUpdateField={updateField}
									readOnly={!canMaintain || selectedModel?.compatibilityMode === "LEGACY_READONLY"}
									standards={context.standards}
								/>
							</>
						) : (
							<RequestState description="请从目录选择模型，或新建一个模型草稿。" kind="empty" title="请选择模型" />
						)}
					</section>
					<aside className="dmx-record-rail">
						<button disabled={!selectedModel} onClick={() => setDialog("versions")} type="button">
							<GitBranch size={16} />
							版本管理
						</button>
						<button disabled={!selectedModel} onClick={() => setDialog("releases")} type="button">
							<FileDown size={16} />
							发布记录
						</button>
					</aside>
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
			<Toast message={message} />
		</main>
	);
}

function ModelEditor({
	draft,
	onChange,
	domains,
	dimensionDefinitionFailure,
	dimensionDefinitions,
	standards,
	fieldRowIds,
	readOnly,
	onAddField,
	onUpdateField,
	onDeleteField,
	onStandardChange,
}: {
	draft: ModelDraft;
	onChange: (draft: ModelDraft) => void;
	domains: ModelWorkbenchContext["domains"];
	dimensionDefinitionFailure: string;
	dimensionDefinitions: DimensionDefinitionView[];
	standards: ModelWorkbenchContext["standards"];
	fieldRowIds: string[];
	readOnly: boolean;
	onAddField: () => void;
	onUpdateField: (index: number, patch: Partial<ModelSpecField>) => void;
	onDeleteField: (index: number) => void;
	onStandardChange: (index: number, value: string) => void;
}) {
	const config = MODEL_KIND_CONFIG[draft.createKind];
	const patch = (next: Partial<ModelDraft>) => onChange({ ...draft, ...next });
	return (
		<fieldset className="dmx-editor-fieldset dmx-editor-scroll" disabled={readOnly}>
			<section className="dmx-editor-panel">
				<h3>基本信息</h3>
				<div className="dmx-form-grid">
					<label>
						<span className="required">数据域</span>
						<select
							disabled={Boolean(draft.base)}
							onChange={(event) => patch({ domainId: event.target.value })}
							value={draft.domainId}
						>
							<option value="">请选择数据域</option>
							{domains.map((item) => (
								<option key={item.code} value={item.code}>
									{item.name} · {item.code}
								</option>
							))}
							{draft.base && !domains.some((item) => item.code === draft.domainId) ? (
								<option value={draft.domainId}>{draft.domainId}</option>
							) : null}
						</select>
					</label>
					<label>
						<span>模型类型</span>
						<input disabled value={config.label} />
					</label>
					<label>
						<span>目标分层</span>
						<input disabled value={config.layer} />
					</label>
					<label>
						<span className="required">模型名称</span>
						<input onChange={(event) => patch({ name: event.target.value })} value={draft.name} />
					</label>
					<label>
						<span>物理表名</span>
						<input
							onChange={(event) => patch({ physicalName: event.target.value })}
							placeholder="由命名策略校验"
							value={draft.physicalName}
						/>
					</label>
					<label className="dmx-form-field--wide">
						<span>业务定义</span>
						<textarea onChange={(event) => patch({ description: event.target.value })} value={draft.description} />
					</label>
					<label className="dmx-form-field--wide">
						<span className="required">模型粒度</span>
						<input
							onChange={(event) => patch({ grainStatement: event.target.value })}
							placeholder="例如：一个预算科目一行"
							value={draft.grainStatement}
						/>
					</label>
					<label>
						<span>物化方式</span>
						<select onChange={(event) => patch({ materialization: event.target.value })} value={draft.materialization}>
							<option value="table">table</option>
							<option value="incremental">incremental</option>
							<option value="view">view</option>
							<option value="ephemeral">ephemeral</option>
						</select>
					</label>
					<label>
						<span>加载策略</span>
						<select
							onChange={(event) => patch({ loadStrategy: event.target.value as ModelDraft["loadStrategy"] })}
							value={draft.loadStrategy}
						>
							<option value="FULL">全量</option>
							<option value="INCREMENTAL">增量</option>
							<option value="SNAPSHOT">快照</option>
						</select>
					</label>
					<label className="dmx-form-field--wide">
						<span>分区字段</span>
						<input
							onChange={(event) => patch({ partitionFields: event.target.value })}
							placeholder="多个字段用逗号分隔"
							value={draft.partitionFields}
						/>
					</label>
					{config.modelType === "DIMENSION" ? (
						<>
							<label>
								<span>SCD 策略</span>
								<select
									onChange={(event) => patch({ scdType: event.target.value as ModelDraft["scdType"] })}
									value={draft.scdType}
								>
									<option value="NONE">不保留历史</option>
									<option value="TYPE1">SCD Type 1</option>
									<option value="TYPE2">SCD Type 2</option>
								</select>
							</label>
							<label>
								<span>复用范围</span>
								<select
									onChange={(event) => patch({ reuseScope: event.target.value as ModelDraft["reuseScope"] })}
									value={draft.reuseScope}
								>
									<option value="DOMAIN">同一业务分类</option>
									<option value="TENANT">当前租户</option>
								</select>
							</label>
							{!draft.base && draft.createKind === "dimension-table" ? (
								<label className="dmx-form-field--wide">
									<span className="required">维度</span>
									<select
										onChange={(event) => patch({ dimensionDefinitionId: event.target.value })}
										value={draft.dimensionDefinitionId}
									>
										<option value="">请选择维度</option>
										{dimensionDefinitions.map((item) => (
											<option key={item.id} value={item.id}>
												{item.name} · {item.systemCode} · r{item.revision}
											</option>
										))}
									</select>
									{dimensionDefinitionFailure ? (
										<small className="dmx-inline-error">{dimensionDefinitionFailure}</small>
									) : !dimensionDefinitions.length ? (
										<small>当前数据域暂无维度，请先创建维度。</small>
									) : null}
								</label>
							) : null}
						</>
					) : null}
				</div>
			</section>
			<section className="dmx-editor-panel">
				<h3>字段管理</h3>
				<div className="dmx-table-tools">
					<Button onClick={onAddField}>
						<Plus size={14} />
						插入 1 行
					</Button>
					<Button className="right" disabled title="字段列显示偏好将在统一用户偏好服务接入后开放">
						<Eye size={14} />
						字段显示设置
					</Button>
				</div>
				<FieldTable
					bindings={draft.standardBindings}
					fieldRowIds={fieldRowIds}
					fields={draft.fields}
					onDelete={onDeleteField}
					onStandardChange={onStandardChange}
					onUpdate={onUpdateField}
					standards={standards}
				/>
			</section>
		</fieldset>
	);
}

function FieldTable({
	fields,
	bindings,
	standards,
	fieldRowIds,
	onUpdate,
	onDelete,
	onStandardChange,
}: {
	fields: ModelSpecField[];
	bindings: ModelDraft["standardBindings"];
	standards: ModelWorkbenchContext["standards"];
	fieldRowIds: string[];
	onUpdate: (index: number, patch: Partial<ModelSpecField>) => void;
	onDelete: (index: number) => void;
	onStandardChange: (index: number, value: string) => void;
}) {
	return (
		<div className="dmx-field-table-wrap">
			<table className="dmx-field-table">
				<thead>
					<tr>
						<th>序号</th>
						<th>字段名称</th>
						<th>数据类型</th>
						<th>业务名称</th>
						<th>字段作用</th>
						<th>字段标准</th>
						<th>允许为空</th>
						<th>维度属性编码</th>
						<th>安全等级</th>
						<th>操作</th>
					</tr>
				</thead>
				<tbody>
					{fields.length ? (
						fields.map((field, index) => {
							const binding = bindings.find((item) => item.fieldName === field.name);
							const standardValue = binding ? `${binding.standardElementId}@${binding.standardElementVersion}` : "";
							return (
								<tr key={fieldRowIds[index]}>
									<td>{index + 1}</td>
									<td>
										<input onChange={(event) => onUpdate(index, { name: event.target.value })} value={field.name} />
									</td>
									<td>
										<input
											onChange={(event) => onUpdate(index, { dataType: event.target.value })}
											value={field.dataType}
										/>
									</td>
									<td>
										<input
											onChange={(event) => onUpdate(index, { displayName: event.target.value })}
											value={field.displayName || ""}
										/>
									</td>
									<td>
										<select
											onChange={(event) => onUpdate(index, { role: event.target.value as ModelSpecField["role"] })}
											value={field.role}
										>
											<option value="KEY">键（KEY）</option>
											<option value="ATTRIBUTE">属性</option>
											<option value="TIME">时间</option>
											<option value="MEASURE">度量</option>
										</select>
									</td>
									<td>
										<select
											disabled={!field.name.trim()}
											onChange={(event) => onStandardChange(index, event.target.value)}
											value={standardValue}
										>
											<option value="">不绑定</option>
											{standards.map((standard) => (
												<option key={`${standard.id}@${standard.version}`} value={`${standard.id}@${standard.version}`}>
													{standard.name} · {standard.code} · v{standard.version}
												</option>
											))}
										</select>
									</td>
									<td>
										<input
											checked={field.nullable}
											onChange={(event) => onUpdate(index, { nullable: event.target.checked })}
											type="checkbox"
										/>
									</td>
									<td>
										<input
											onChange={(event) => onUpdate(index, { dimensionAttributeCode: event.target.value })}
											placeholder="可选"
											value={field.dimensionAttributeCode || ""}
										/>
									</td>
									<td>
										<input
											onChange={(event) => onUpdate(index, { securityLevel: event.target.value })}
											placeholder="可选"
											value={field.securityLevel || ""}
										/>
									</td>
									<td>
										<button aria-label={`删除字段 ${index + 1}`} onClick={() => onDelete(index)} type="button">
											<Trash2 size={14} />
											删除
										</button>
									</td>
								</tr>
							);
						})
					) : (
						<tr>
							<td colSpan={10}>
								<RequestState description="点击插入按钮新增字段。" kind="empty" title="暂无字段" />
							</td>
						</tr>
					)}
				</tbody>
			</table>
		</div>
	);
}
