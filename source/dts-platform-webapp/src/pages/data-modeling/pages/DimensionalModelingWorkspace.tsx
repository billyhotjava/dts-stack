import {
	Boxes,
	ChevronDown,
	ChevronRight,
	Database,
	FileDown,
	FileUp,
	Grid2X2,
	Layers3,
	Plus,
	RefreshCw,
	Search,
	Table2,
	WandSparkles,
} from "lucide-react";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { ModelingEditor, type ModelObjectType, type ModelSelection } from "../components/ModelingEditor";
import { ReverseModelingWizard } from "../components/ReverseModelingWizard";
import { ActionButton, UiStageNotice, WorkspacePage } from "../components/WorkspacePage";
import { dataModelingPath } from "../navigation";
import type { WorkspacePageProps } from "../types";
import "./modeling-metrics.css";
import "./modeling-metrics-extended.css";

type CatalogNode = ModelSelection & { status: "草稿" | "已发布" };

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

const DOMAIN_CATALOG: Array<{ name: string; nodes: CatalogNode[] }> = [
	{
		name: "财务域",
		nodes: [
			{
				type: "dimension-table",
				code: "dim_fin_date",
				name: "财务日期维度表",
				layer: "公共层 / 维度层",
				domain: "财务域",
				status: "已发布",
			},
			{
				type: "dimension-table",
				code: "dim_budget_account",
				name: "预算科目维度表",
				layer: "公共层 / 维度层",
				domain: "财务域",
				status: "草稿",
			},
			{
				type: "fact",
				code: "fct_budget_execution",
				name: "预算执行明细表",
				layer: "公共层 / 明细层",
				domain: "财务域",
				status: "已发布",
			},
			{
				type: "aggregate",
				code: "agg_budget_monthly",
				name: "月度预算执行汇总表",
				layer: "公共层 / 汇总层",
				domain: "财务域",
				status: "草稿",
			},
			{
				type: "application",
				code: "app_budget_dashboard",
				name: "预算驾驶舱应用表",
				layer: "应用层",
				domain: "财务域",
				status: "草稿",
			},
		],
	},
	{
		name: "项目域",
		nodes: [
			{
				type: "dimension-table",
				code: "dim_project",
				name: "项目维度表",
				layer: "公共层 / 维度层",
				domain: "项目域",
				status: "已发布",
			},
			{
				type: "dimension-table",
				code: "dim_department",
				name: "责任部门维度表",
				layer: "公共层 / 维度层",
				domain: "项目域",
				status: "已发布",
			},
			{
				type: "fact",
				code: "fct_project_milestone",
				name: "项目节点明细表",
				layer: "公共层 / 明细层",
				domain: "项目域",
				status: "草稿",
			},
			{
				type: "aggregate",
				code: "agg_project_weekly",
				name: "项目周进展汇总表",
				layer: "公共层 / 汇总层",
				domain: "项目域",
				status: "草稿",
			},
		],
	},
];

const initialSelection = DOMAIN_CATALOG[0].nodes[1];

const layerMatches = (node: CatalogNode, layer: string) => {
	if (layer === "贴源层") return node.type === "source";
	if (layer === "应用层") return node.type === "application";
	return node.type !== "source" && node.type !== "application";
};

export function DimensionalModelingWorkspace({ route }: WorkspacePageProps) {
	const navigate = useNavigate();
	const view = route.view === "reverse" ? "reverse" : "workbench";
	const [selection, setSelection] = useState<ModelSelection>(initialSelection);
	const [layer, setLayer] = useState("公共层");
	const [domain, setDomain] = useState("全部数据域");
	const [query, setQuery] = useState("");
	const [creationOpen, setCreationOpen] = useState(false);
	const [expandedDomains, setExpandedDomains] = useState(() => new Set(DOMAIN_CATALOG.map((item) => item.name)));

	const catalog = useMemo(
		() =>
			DOMAIN_CATALOG.map((group) => ({
				...group,
				nodes: group.nodes.filter(
					(node) =>
						(domain === "全部数据域" || node.domain === domain) &&
						layerMatches(node, layer) &&
						(!query || node.code.toLowerCase().includes(query.toLowerCase()) || node.name.includes(query)),
				),
			})).filter((group) => (domain === "全部数据域" || group.name === domain) && group.nodes.length > 0),
		[domain, layer, query],
	);

	const createModel = (type: ModelObjectType) => {
		const entry = CREATION_ENTRIES.find((item) => item.type === type);
		const codeByType: Record<ModelObjectType, string> = {
			dimension: "new_dimension",
			source: "ods_new_source",
			"dimension-table": "dim_new_dimension",
			fact: "fct_new_detail",
			aggregate: "agg_new_summary",
			application: "app_new_dataset",
		};
		setSelection({
			type,
			code: codeByType[type],
			name: entry?.label.replace("创建", "新建") ?? "新建模型",
			layer: entry?.layer ?? "公共层",
			domain: domain === "全部数据域" ? "财务域" : domain,
			isNew: true,
		});
		setLayer(type === "source" ? "贴源层" : type === "application" ? "应用层" : "公共层");
		setCreationOpen(false);
	};

	return (
		<WorkspacePage
			description="沿用原型的对象目录与编辑器结构，在同一工作区完成模型定义、字段设计和交付预览。"
			eyebrow="DataWorks 风格工作台"
			title={view === "reverse" ? "逆向建模" : "维度建模"}
		>
			<UiStageNotice />
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
								<strong>维度建模</strong>
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
									<button aria-label="导入模型" className="dm-icon-button" disabled title="后台阶段接入" type="button">
										<FileDown aria-hidden="true" size={16} />
									</button>
									<button aria-label="导出模型" className="dm-icon-button" disabled title="后台阶段接入" type="button">
										<FileUp aria-hidden="true" size={16} />
									</button>
									<button aria-label="刷新目录" className="dm-icon-button" disabled title="后台阶段接入" type="button">
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
									<option>财务域</option>
									<option>项目域</option>
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
								{catalog.length === 0 ? (
									<div className="dm-object-tree__empty">当前筛选条件下暂无模型</div>
								) : (
									catalog.map((group) => {
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
													<span>{group.nodes.length}</span>
												</button>
												{expanded
													? group.nodes.map((node) => (
															<button
																className={`dm-tree-node ${selection.code === node.code ? "is-active" : ""}`}
																key={node.code}
																onClick={() => setSelection(node)}
																title={node.name}
																type="button"
															>
																<Table2 aria-hidden="true" size={13} />
																<span>
																	<strong>{node.code}</strong>
																	<small>{node.name}</small>
																</span>
																<i className={node.status === "已发布" ? "is-published" : ""} />
															</button>
														))
													: null}
											</div>
										);
									})
								)}
							</div>
							<footer>
								<span>目录对象 {DOMAIN_CATALOG.reduce((total, group) => total + group.nodes.length, 0)}</span>
								<ActionButton kind="quiet" onClick={() => setQuery("")}>
									清除筛选
								</ActionButton>
							</footer>
						</aside>
						<ModelingEditor selection={selection} />
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
