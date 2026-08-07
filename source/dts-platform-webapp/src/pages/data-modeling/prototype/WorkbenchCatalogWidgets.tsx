import { ChevronDown, ChevronRight, Copy, GitBranch, Search, Trash2 } from "lucide-react";
import type { CatalogDomain } from "@/api/services/catalogDomainService";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import type { WorkbenchCatalogGroup } from "./modelWorkbenchPresentation";
import { RequestState } from "./PrototypePrimitives";
import type { ModelCreateKind } from "./services/modelWorkbenchService";

const MODEL_TYPE_LABEL: Record<string, string> = {
	DIMENSION: "维度表",
	FACT: "明细表",
	SUMMARY: "汇总表",
	APPLICATION: "应用表",
};

const LOGICAL_CREATE_ENTRIES: Array<{
	kind: ModelCreateKind | null;
	label: string;
	disabled: boolean;
	title?: string;
}> = [
	{
		kind: null,
		label: "创建贴源表（尚未接入）",
		disabled: true,
		title: "当前 ModelSpec 契约不拥有 ODS/STG 贴源对象",
	},
	{ kind: "dimension-table", label: "创建维度表", disabled: false },
	{ kind: "fact", label: "创建明细表", disabled: false },
	{ kind: "summary", label: "创建汇总表", disabled: false },
	{ kind: "application", label: "创建应用表", disabled: false },
];

export type WorkbenchCreateMenuProps = {
	categoryRoots: CatalogDomain[];
	category: string;
	query: string;
	saving: boolean;
	onCategoryChange: (value: string) => void;
	onQueryChange: (value: string) => void;
	onCreate: (kind: ModelCreateKind) => void;
};

export function WorkbenchCreateMenu({
	categoryRoots,
	category,
	query,
	saving,
	onCategoryChange,
	onQueryChange,
	onCreate,
}: WorkbenchCreateMenuProps) {
	const normalized = query.trim().toLowerCase();
	const visible = (label: string) => !normalized || label.toLowerCase().includes(normalized);
	return (
		<div className="dmx-create-menu">
			<div className="dmx-create-menu-head">
				<select
					aria-label="请选择业务分类"
					onChange={(event) => onCategoryChange(event.target.value)}
					value={category}
				>
					<option value="">请选择业务分类</option>
					{categoryRoots.map((item) => (
						<option key={item.id} value={item.id}>
							{item.name}
						</option>
					))}
				</select>
				<div>
					<Search size={13} />
					<input
						aria-label="搜索模型类型"
						onChange={(event) => onQueryChange(event.target.value)}
						placeholder="搜索"
						value={query}
					/>
				</div>
			</div>
			<strong>概念模型</strong>
			{visible("创建维度") ? (
				<button disabled={saving} onClick={() => onCreate("dimension")} type="button">
					创建维度
				</button>
			) : null}
			<strong>逻辑模型</strong>
			{LOGICAL_CREATE_ENTRIES.filter((entry) => visible(entry.label)).map((entry) =>
				entry.disabled ? (
					<button disabled key={entry.label} title={entry.title} type="button">
						{entry.label}
					</button>
				) : (
					<button
						disabled={saving}
						key={entry.label}
						onClick={() => entry.kind && onCreate(entry.kind)}
						type="button"
					>
						{entry.label}
					</button>
				),
			)}
		</div>
	);
}

export type WorkbenchModelRowProps = {
	model: ModelSpecView;
	selected: boolean;
	saving: boolean;
	onChoose: (model: ModelSpecView) => void;
	onGoToGraph: (model: ModelSpecView) => void;
	onRemove: (model: ModelSpecView) => void;
};

export function WorkbenchModelRow({
	model,
	selected,
	saving,
	onChoose,
	onGoToGraph,
	onRemove,
}: WorkbenchModelRowProps) {
	return (
		<div
			className={`dmx-tree-model${selected ? " active" : ""}`}
			onClick={() => onChoose(model)}
			onKeyDown={(event) => {
				if (event.key === "Enter" || event.key === " ") {
					event.preventDefault();
					onChoose(model);
				}
			}}
			role="button"
			tabIndex={0}
		>
			<span>▦</span>
			<span className="dmx-tree-model-copy">
				<b>{model.implementationPolicy?.physicalName || model.name}</b>
				<small>
					{model.name} · {MODEL_TYPE_LABEL[model.modelType]}
				</small>
			</span>
			<span className="dmx-tree-model-actions">
				<button
					aria-label={`前往关系图：${model.name}`}
					disabled={saving}
					onClick={(event) => {
						event.stopPropagation();
						onGoToGraph(model);
					}}
					title="前往关系图"
					type="button"
				>
					<GitBranch size={13} />
				</button>
				<button
					aria-label={`删除：${model.name}`}
					disabled={saving || model.compatibilityMode !== "CANONICAL"}
					onClick={(event) => {
						event.stopPropagation();
						onRemove(model);
					}}
					title={model.compatibilityMode === "CANONICAL" ? "删除" : "历史只读模型不能删除"}
					type="button"
				>
					<Trash2 size={13} />
				</button>
			</span>
		</div>
	);
}

export type WorkbenchDimensionRowProps = {
	dimension: DimensionDefinitionView;
	selected: boolean;
	saving: boolean;
	onChoose: (dimension: DimensionDefinitionView) => void;
	onGoToGraph: (dimension: DimensionDefinitionView) => void;
	onClone: (dimension: DimensionDefinitionView) => void;
	onRemove: (dimension: DimensionDefinitionView) => void;
};

export function WorkbenchDimensionRow({
	dimension,
	selected,
	saving,
	onChoose,
	onGoToGraph,
	onClone,
	onRemove,
}: WorkbenchDimensionRowProps) {
	return (
		<div
			className={`dmx-tree-model${selected ? " active" : ""}`}
			onClick={() => onChoose(dimension)}
			onKeyDown={(event) => {
				if (event.key === "Enter" || event.key === " ") {
					event.preventDefault();
					onChoose(dimension);
				}
			}}
			role="button"
			tabIndex={0}
		>
			<span>◈</span>
			<span className="dmx-tree-model-copy">
				<b>{dimension.name}</b>
				<small>
					{dimension.systemCode} · 维度
				</small>
			</span>
			<span className="dmx-tree-model-actions">
				<button
					aria-label={`前往关系图：${dimension.name}`}
					disabled={saving}
					onClick={(event) => {
						event.stopPropagation();
						onGoToGraph(dimension);
					}}
					title="前往关系图"
					type="button"
				>
					<GitBranch size={13} />
				</button>
				<button
					aria-label={`克隆：${dimension.name}`}
					disabled={saving}
					onClick={(event) => {
						event.stopPropagation();
						onClone(dimension);
					}}
					title="克隆"
					type="button"
				>
					<Copy size={13} />
				</button>
				<button
					aria-label={`删除：${dimension.name}`}
					disabled={saving}
					onClick={(event) => {
						event.stopPropagation();
						onRemove(dimension);
					}}
					title={dimension.status === "DRAFT" ? "删除" : "退役"}
					type="button"
				>
					<Trash2 size={13} />
				</button>
			</span>
		</div>
	);
}

export type WorkbenchCatalogTreeProps = {
	groups: WorkbenchCatalogGroup[];
	domainOpen: Record<string, boolean>;
	saving: boolean;
	selectedModelId: string;
	selectedDimensionId: string;
	emptyMessage: string;
	onToggleDomain: (key: string) => void;
	onChooseModel: (model: ModelSpecView) => void;
	onChooseDimension: (dimension: DimensionDefinitionView) => void;
	onGoToGraphDimension: (dimension: DimensionDefinitionView) => void;
	onCloneDimension: (dimension: DimensionDefinitionView) => void;
	onRemoveDimension: (dimension: DimensionDefinitionView) => void;
	onGoToGraph: (model: ModelSpecView) => void;
	onRemoveModel: (model: ModelSpecView) => void;
};

export function WorkbenchCatalogTree({
	groups,
	domainOpen,
	saving,
	selectedModelId,
	selectedDimensionId,
	emptyMessage,
	onToggleDomain,
	onChooseModel,
	onChooseDimension,
	onGoToGraphDimension,
	onCloneDimension,
	onRemoveDimension,
	onGoToGraph,
	onRemoveModel,
}: WorkbenchCatalogTreeProps) {
	return (
		<div className="dmx-object-tree">
			{groups.map((group) => {
				const open = domainOpen[group.key] !== false;
				return (
					<div key={group.key}>
						<button
							className="dmx-tree-domain"
							disabled={saving}
							onClick={() => onToggleDomain(group.key)}
							type="button"
						>
							{open ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
							<span>{group.kind === "category" ? "🗂" : group.kind === "domain" ? "🌐" : "▦"}</span>
							<strong>{group.label}</strong>
							<em>({group.models.length + group.dimensions.length})</em>
						</button>
						{open
							? [
									...group.models.map((model) => (
										<WorkbenchModelRow
											key={model.id}
											model={model}
											onChoose={onChooseModel}
											onGoToGraph={onGoToGraph}
											onRemove={onRemoveModel}
											saving={saving}
											selected={selectedModelId === model.id}
										/>
									)),
									...group.dimensions.map((dimension) => (
										<WorkbenchDimensionRow
											dimension={dimension}
											key={dimension.id}
											onChoose={onChooseDimension}
											onClone={onCloneDimension}
											onGoToGraph={onGoToGraphDimension}
											onRemove={onRemoveDimension}
											saving={saving}
											selected={selectedDimensionId === dimension.id}
										/>
									)),
								]
							: null}
					</div>
				);
			})}
			{!groups.length ? (
				<RequestState description={emptyMessage} kind="empty" title="暂无模型" />
			) : null}
		</div>
	);
}
