import { Import, ListFilter, Plus, RefreshCw, Table2 } from "lucide-react";
import type { CatalogDomain } from "@/api/services/catalogDomainService";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import type { WorkbenchCatalogGroup } from "./modelWorkbenchPresentation";
import type { ModelCreateKind } from "./services/modelWorkbenchService";
import { WorkbenchCatalogTree, WorkbenchCreateMenu } from "./WorkbenchCatalogWidgets";

const layerLabels = ["贴源层", "公共层", "应用层"];

export type ModelWorkbenchCatalogPanelProps = {
	canMaintain: boolean;
	categoryRoots: CatalogDomain[];
	createCategory: string;
	createOpen: boolean;
	createQuery: string;
	domain: string;
	domainOpen: Record<string, boolean>;
	effectiveView: "domain" | "category";
	emptyMessage: string;
	groups: WorkbenchCatalogGroup[];
	layer: string;
	loading: boolean;
	modelDomainOptions: Array<{ id: string; name: string }>;
	query: string;
	saving: boolean;
	selectedDimensionId: string;
	selectedModelId: string;
	onChooseDimension: (dimension: DimensionDefinitionView) => void;
	onChooseModel: (model: ModelSpecView) => void;
	onCloneDimension: (dimension: DimensionDefinitionView) => void;
	onCreate: (kind: ModelCreateKind) => void;
	onCreateCategoryChange: (value: string) => void;
	onCreateQueryChange: (value: string) => void;
	onDomainChange: (value: string) => void;
	onGoToGraph: (model: ModelSpecView) => void;
	onGoToGraphDimension: (dimension: DimensionDefinitionView) => void;
	onImport: () => void;
	onLayerChange: (value: string) => void;
	onList: () => void;
	onQueryChange: (value: string) => void;
	onRefresh: () => void;
	onRemoveDimension: (dimension: DimensionDefinitionView) => void;
	onRemoveModel: (model: ModelSpecView) => void;
	onToggleCreate: () => void;
	onToggleDomain: (key: string) => void;
	onViewChange: (view: "domain" | "category") => void;
};

export function ModelWorkbenchCatalogPanel(props: ModelWorkbenchCatalogPanelProps) {
	return (
		<aside className="dmx-object-panel">
			<header>
				<h2>模型目录</h2>
				<div className="dmx-iconbar">
					<button
						aria-label="新建"
						disabled={props.saving || !props.canMaintain}
						onClick={props.onToggleCreate}
						title={props.canMaintain ? "新建模型" : "当前账号无建模维护权限"}
						type="button"
					>
						<Plus size={16} />
					</button>
					<button
						aria-label="列表管理"
						disabled={props.saving}
						onClick={props.onList}
						title="切换到模型列表管理"
						type="button"
					>
						<Table2 size={16} />
					</button>
					<button aria-label="导入" disabled={props.saving} onClick={props.onImport} type="button">
						<Import size={16} />
					</button>
					<button aria-label="刷新" disabled={props.saving || props.loading} onClick={props.onRefresh} type="button">
						<RefreshCw size={16} />
					</button>
				</div>
			</header>
			<div className="dmx-layer-tabs">
				{layerLabels.map((item) => (
					<button
						className={props.layer === item ? "active" : ""}
						disabled={props.saving}
						key={item}
						onClick={() => props.onLayerChange(item)}
						type="button"
					>
						{item}
					</button>
				))}
			</div>
			{props.layer === "公共层" ? (
				<fieldset className="dmx-view-switch" aria-label="管理视角">
					<button
						className={props.effectiveView === "domain" ? "active" : ""}
						disabled={props.saving}
						onClick={() => props.onViewChange("domain")}
						type="button"
					>
						数据域视角
					</button>
					<button
						className={props.effectiveView === "category" ? "active" : ""}
						disabled={props.saving}
						onClick={() => props.onViewChange("category")}
						type="button"
					>
						业务分类视角
					</button>
				</fieldset>
			) : null}
			<div className="dmx-object-filters">
				<select
					aria-label={props.effectiveView === "domain" ? "筛选数据域" : "筛选业务分类"}
					disabled={props.saving}
					onChange={(event) => props.onDomainChange(event.target.value)}
					value={props.domain}
				>
					<option value="">{props.effectiveView === "domain" ? "全部数据域" : "全部业务分类"}</option>
					{props.modelDomainOptions.map((item) => (
						<option key={item.id} value={item.id}>
							{item.name}
						</option>
					))}
				</select>
				<div>
					<ListFilter size={14} />
					<input
						aria-label="搜索模型"
						disabled={props.saving}
						onChange={(event) => props.onQueryChange(event.target.value)}
						placeholder="搜索模型"
						value={props.query}
					/>
				</div>
			</div>
			<WorkbenchCatalogTree
				domainOpen={props.domainOpen}
				emptyMessage={props.emptyMessage}
				groups={props.groups}
				onChooseDimension={props.onChooseDimension}
				onChooseModel={props.onChooseModel}
				onCloneDimension={props.onCloneDimension}
				onGoToGraphDimension={props.onGoToGraphDimension}
				onGoToGraph={props.onGoToGraph}
				onRemoveModel={props.onRemoveModel}
				onRemoveDimension={props.onRemoveDimension}
				onToggleDomain={props.onToggleDomain}
				saving={props.saving}
				selectedDimensionId={props.selectedDimensionId}
				selectedModelId={props.selectedModelId}
			/>
			{props.createOpen ? (
				<WorkbenchCreateMenu
					category={props.createCategory}
					categoryRoots={props.categoryRoots}
					onCategoryChange={props.onCreateCategoryChange}
					onCreate={props.onCreate}
					onQueryChange={props.onCreateQueryChange}
					query={props.createQuery}
					saving={props.saving}
				/>
			) : null}
		</aside>
	);
}
