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
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { listModelSpecs } from "@/api/modelSpecApi";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
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
	{ type: "source", label: "创建贴源表", group: "逻辑模型", layer: "贴源层", icon: Database },
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
		dimension: "new_dimension",
		source: "ods_new_source",
		"dimension-table": "dim_new_dimension",
		fact: "fct_new_detail",
		aggregate: "agg_new_summary",
		application: "app_new_dataset",
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

export function DimensionalModelingWorkspace({ route }: WorkspacePageProps) {
	const navigate = useNavigate();
	const [searchParams, setSearchParams] = useSearchParams();
	const view = route.view === "reverse" ? "reverse" : "workbench";
	const [models, setModels] = useState<ModelSpecView[]>([]);
	const [loading, setLoading] = useState(false);
	const [loadError, setLoadError] = useState<string | null>(null);
	const [layer, setLayer] = useState("公共层");
	const [domain, setDomain] = useState("全部数据域");
	const [query, setQuery] = useState("");
	const [creationOpen, setCreationOpen] = useState(false);
	const [draftSelection, setDraftSelection] = useState<ModelSelection | null>(null);
	const [expandedDomains, setExpandedDomains] = useState<Set<string>>(new Set());
	const selectedModelId = searchParams.get("modelSpecId");

	const refreshModels = useCallback(async () => {
		setLoading(true);
		setLoadError(null);
		try {
			const result = await listModelSpecs();
			setModels(result);
			setExpandedDomains(new Set(result.map((model) => model.domainId || "未归属数据域")));
		} catch {
			setLoadError("MODEL_SPEC_CATALOG_READ_FAILED");
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		if (view === "workbench") void refreshModels();
	}, [refreshModels, view]);

	useEffect(() => {
		if (view === "workbench" && !selectedModelId && models[0]) {
			setSearchParams({ modelSpecId: models[0].id }, { replace: true });
		}
	}, [models, selectedModelId, setSearchParams, view]);

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
	const selectedModel = models.find((model) => model.id === selectedModelId) || null;

	const selectModel = (model: ModelSpecView) => {
		setDraftSelection(null);
		setSearchParams({ modelSpecId: model.id });
	};

	const createModel = (type: ModelObjectType) => {
		setDraftSelection(toDraftSelection(type, domain));
		setCreationOpen(false);
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
						onClick={() => navigate(dataModelingPath("dimensions", "workbench"))}
						type="button"
					>
						<Boxes aria-hidden="true" size={17} />
						<span>维度建模</span>
					</button>
					<button
						className={view === "reverse" ? "is-active" : ""}
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
										onClick={() => setCreationOpen((current) => !current)}
										type="button"
									>
										<Plus aria-hidden="true" size={17} />
									</button>
									<button
										aria-label="导入 dbt ZIP"
										className="dm-icon-button"
										onClick={() => navigate(`${dataModelingPath("dimensions", "reverse")}?source=dbt`)}
										type="button"
									>
										<FileDown aria-hidden="true" size={16} />
									</button>
									<button
										aria-label="刷新目录"
										className="dm-icon-button"
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
													<button key={entry.type} onClick={() => createModel(entry.type)} type="button">
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
								<ActionButton kind="quiet" onClick={() => setQuery("")}>
									清除筛选
								</ActionButton>
							</footer>
						</aside>
						{draftSelection ? (
							<ModelingEditor selection={draftSelection} />
						) : selectedModel ? (
							<ModelDetailWorkbench key={`${selectedModel.id}-${selectedModel.revision}`} model={selectedModel} />
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
