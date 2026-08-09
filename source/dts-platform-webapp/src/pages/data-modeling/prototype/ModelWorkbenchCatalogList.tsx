import { ArrowLeft, Search } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import {
	getModelMaterializationStatuses,
	listModelWorkbenchCatalogPage,
	type ModelMaterializationStatus,
	type ModelWorkbenchCatalogEntry,
	type ModelWorkbenchCatalogPage,
} from "@/api/modelSpecApi";
import type { CatalogDomain } from "@/api/services/catalogDomainService";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelSpecLayer, ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { resolveMaterializationPresentation } from "./ModelMaterializationStatus";
import { RequestState, Status } from "./PrototypePrimitives";
import "./modeling-workbench.css";

const MODEL_TYPE_LABEL: Record<string, string> = {
	DIMENSION_DEFINITION: "维度",
	DIMENSION: "维度表",
	FACT: "明细表",
	SUMMARY: "汇总表",
	APPLICATION: "应用表",
};

const PAGE_SIZE = 10;

type CatalogRow = {
	id: string;
	name: string;
	code: string;
	type: string;
	objectType: ModelWorkbenchCatalogEntry["objectType"];
	planId: string;
	domainId: string;
	layer: ModelSpecLayer | null;
	status: string;
	revision: number;
	editable: boolean;
	model: ModelSpecView | null;
	open: (() => void) | null;
};

export type ModelWorkbenchCatalogListProps = {
	canMaintain: boolean;
	dimensions: DimensionDefinitionView[];
	domains: CatalogDomain[];
	models: ModelSpecView[];
	onBack: () => void;
	onChooseDimension: (dimension: DimensionDefinitionView) => void;
	onChooseModel: (model: ModelSpecView) => void;
	onMaterialize: (models: ModelSpecView[]) => void;
};

export function ModelWorkbenchCatalogList({
	canMaintain,
	dimensions,
	domains,
	models,
	onBack,
	onChooseDimension,
	onChooseModel,
	onMaterialize,
}: ModelWorkbenchCatalogListProps) {
	const [query, setQuery] = useState("");
	const [planFilter, setPlanFilter] = useState("");
	const [domainFilter, setDomainFilter] = useState("");
	const [typeFilter, setTypeFilter] = useState<ModelWorkbenchCatalogEntry["objectType"] | "">("");
	const [layerFilter, setLayerFilter] = useState<ModelSpecLayer | "">("");
	const [statusFilter, setStatusFilter] = useState("");
	const [page, setPage] = useState(1);
	const [catalogPage, setCatalogPage] = useState<ModelWorkbenchCatalogPage | null>(null);
	const [catalogLoading, setCatalogLoading] = useState(true);
	const [compatibilityFallback, setCompatibilityFallback] = useState(false);
	const [selectedIds, setSelectedIds] = useState<Set<string>>(() => new Set());
	const [materializationByModel, setMaterializationByModel] = useState<Map<string, ModelMaterializationStatus>>(
		() => new Map(),
	);
	const domainById = useMemo(() => new Map(domains.map((domain) => [domain.id, domain.name])), [domains]);
	const modelById = useMemo(() => new Map(models.map((model) => [model.id, model])), [models]);
	const dimensionById = useMemo(() => new Map(dimensions.map((dimension) => [dimension.id, dimension])), [dimensions]);
	const planOptions = useMemo(
		() =>
			Array.from(
				new Set(models.map((model) => model.planId).filter((planId): planId is string => Boolean(planId))),
			).sort(),
		[models],
	);
	const statusOptions = useMemo(
		() => Array.from(new Set([...dimensions.map((item) => item.status), ...models.map((item) => item.status)])).sort(),
		[dimensions, models],
	);
	const normalized = query.trim().toLowerCase();

	useEffect(() => {
		let active = true;
		const byPlan = new Map<string, string[]>();
		models.forEach((model) => {
			if (!model.planId) return;
			byPlan.set(model.planId, [...(byPlan.get(model.planId) || []), model.id]);
		});
		const requests = Array.from(byPlan.entries()).flatMap(([planId, ids]) => {
			const chunks: Promise<ModelMaterializationStatus[]>[] = [];
			for (let offset = 0; offset < ids.length; offset += 100)
				chunks.push(getModelMaterializationStatuses(planId, ids.slice(offset, offset + 100)));
			return chunks;
		});
		void Promise.all(requests)
			.then((groups) => {
				if (active) setMaterializationByModel(new Map(groups.flat().map((item) => [item.modelSpecId, item])));
			})
			.catch(() => {
				if (active) setMaterializationByModel(new Map());
			});
		return () => {
			active = false;
		};
	}, [models]);

	useEffect(() => {
		const available = new Set(models.map((model) => model.id));
		setSelectedIds((current) => new Set(Array.from(current).filter((id) => available.has(id))));
	}, [models]);

	const allLocalRows = useMemo<CatalogRow[]>(
		() => [
			...dimensions.map((dimension) => ({
				id: dimension.id,
				name: dimension.name,
				code: dimension.systemCode,
				type: MODEL_TYPE_LABEL.DIMENSION_DEFINITION,
				objectType: "DIMENSION_DEFINITION" as const,
				planId: "",
				domainId: dimension.domainId,
				layer: null,
				status: dimension.status,
				revision: dimension.revision,
				editable: canMaintain && dimension.status === "DRAFT",
				model: null,
				open: () => onChooseDimension(dimension),
			})),
			...models.map((model) => ({
				id: model.id,
				name: model.name,
				code: model.implementationPolicy?.physicalName || "—",
				type: MODEL_TYPE_LABEL[model.modelType] || model.modelType,
				objectType: model.modelType,
				planId: model.planId || "",
				domainId: model.domainId || "",
				layer: model.layer,
				status: model.status,
				revision: model.revision,
				editable: canMaintain && model.status === "DRAFT" && model.compatibilityMode === "CANONICAL",
				model,
				open: () => onChooseModel(model),
			})),
		],
		[canMaintain, dimensions, models, onChooseDimension, onChooseModel],
	);

	const filteredLocalRows = useMemo(
		() =>
			allLocalRows.filter((row) => {
				if (planFilter && row.planId !== planFilter) return false;
				if (domainFilter && row.domainId !== domainFilter) return false;
				if (typeFilter && row.objectType !== typeFilter) return false;
				if (layerFilter && row.layer !== layerFilter) return false;
				if (statusFilter && row.status !== statusFilter) return false;
				if (!normalized) return true;
				return [row.name, row.code, row.type, row.status, domainById.get(row.domainId) || ""]
					.join(" ")
					.toLowerCase()
					.includes(normalized);
			}),
		[allLocalRows, domainById, domainFilter, layerFilter, normalized, planFilter, statusFilter, typeFilter],
	);

	// biome-ignore lint/correctness/useExhaustiveDependencies: each filter change intentionally resets server paging.
	useEffect(() => setPage(1), [domainFilter, layerFilter, normalized, planFilter, statusFilter, typeFilter]);

	useEffect(() => {
		let active = true;
		setCatalogLoading(true);
		void listModelWorkbenchCatalogPage({
			page: page - 1,
			size: PAGE_SIZE,
			query: query.trim() || undefined,
			planId: planFilter || undefined,
			domainId: domainFilter || undefined,
			objectType: typeFilter || undefined,
			layer: layerFilter || undefined,
			status: statusFilter || undefined,
		})
			.then((result) => {
				if (!active) return;
				setCatalogPage(result);
				setCompatibilityFallback(false);
			})
			.catch(() => {
				if (!active) return;
				setCatalogPage(null);
				setCompatibilityFallback(true);
			})
			.finally(() => {
				if (active) setCatalogLoading(false);
			});
		return () => {
			active = false;
		};
	}, [domainFilter, layerFilter, page, planFilter, query, statusFilter, typeFilter]);

	const serverRows = useMemo<CatalogRow[]>(
		() =>
			(catalogPage?.content || []).map((entry) => {
				const model = entry.kind === "MODEL_SPEC" ? modelById.get(entry.id) || null : null;
				const dimension = entry.kind === "DIMENSION_DEFINITION" ? dimensionById.get(entry.id) || null : null;
				return {
					id: entry.id,
					name: entry.name,
					code: entry.code,
					type: MODEL_TYPE_LABEL[entry.objectType] || entry.objectType,
					objectType: entry.objectType,
					planId: entry.planId || "",
					domainId: entry.domainId,
					layer: entry.layer,
					status: entry.status,
					revision: entry.revision,
					editable: Boolean(
						canMaintain &&
							((dimension && dimension.status === "DRAFT") ||
								(model && model.status === "DRAFT" && model.compatibilityMode === "CANONICAL")),
					),
					model,
					open: dimension ? () => onChooseDimension(dimension) : model ? () => onChooseModel(model) : null,
				};
			}),
		[catalogPage, canMaintain, dimensionById, modelById, onChooseDimension, onChooseModel],
	);
	const localPageCount = Math.ceil(filteredLocalRows.length / PAGE_SIZE);
	const pageRows = compatibilityFallback
		? filteredLocalRows.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)
		: serverRows;
	const totalElements = compatibilityFallback ? filteredLocalRows.length : catalogPage?.totalElements || 0;
	const pageCount = Math.max(1, compatibilityFallback ? localPageCount : catalogPage?.totalPages || 0);
	const currentPage = Math.min(page, pageCount);
	const selectedModels = models.filter((model) => selectedIds.has(model.id));
	const selectedPlanId = selectedModels[0]?.planId || "";
	const pageSelectableModels = pageRows
		.map((row) => row.model)
		.filter((model): model is ModelSpecView =>
			Boolean(
				model &&
					canMaintain &&
					model.compatibilityMode === "CANONICAL" &&
					(!selectedPlanId || selectedPlanId === model.planId),
			),
		);
	const allPageModelsSelected =
		pageSelectableModels.length > 0 && pageSelectableModels.every((model) => selectedIds.has(model.id));

	useEffect(() => {
		if (page > pageCount) setPage(pageCount);
	}, [page, pageCount]);

	const toggleModel = (model: ModelSpecView) => {
		setSelectedIds((current) => {
			const next = new Set(current);
			if (next.has(model.id)) next.delete(model.id);
			else if ((!selectedPlanId || selectedPlanId === model.planId) && next.size < 100) next.add(model.id);
			return next;
		});
	};
	const toggleCurrentPage = () => {
		setSelectedIds((current) => {
			const next = new Set(current);
			if (allPageModelsSelected) pageSelectableModels.forEach((model) => next.delete(model.id));
			else {
				for (const model of pageSelectableModels) {
					if (next.size >= 100) break;
					next.add(model.id);
				}
			}
			return next;
		});
	};

	return (
		<section className="dmx-model-list-workspace" aria-label="模型列表管理">
			<header>
				<div>
					<h2>模型列表</h2>
					<p>通过筛选和分页选择模型；详情进入同一编辑器，勾选只用于批量交付。</p>
				</div>
				<button onClick={onBack} type="button">
					<ArrowLeft size={15} />
					进入目录编辑器
				</button>
			</header>
			<div className="dmx-model-list-toolbar">
				<label>
					<Search size={15} />
					<input
						aria-label="搜索模型列表"
						onChange={(event) => setQuery(event.target.value)}
						placeholder="搜索名称、编码、类型、状态或数据域"
						value={query}
					/>
				</label>
				<div className="dmx-model-list-toolbar__actions">
					<span>
						共 {totalElements} 条，每页 {PAGE_SIZE} 条；已选 {selectedModels.length} 个模型
					</span>
					{selectedModels.length ? (
						<button onClick={() => setSelectedIds(new Set())} type="button">
							清空已选
						</button>
					) : null}
					<button
						className="primary"
						disabled={!canMaintain || !selectedModels.length}
						onClick={() => onMaterialize(selectedModels)}
						type="button"
					>
						生成物化候选（{selectedModels.length}）
					</button>
				</div>
			</div>
			<fieldset className="dmx-model-list-filters" aria-label="模型组合筛选">
				<select aria-label="按规划筛选" onChange={(event) => setPlanFilter(event.target.value)} value={planFilter}>
					<option value="">全部规划</option>
					{planOptions.map((planId) => (
						<option key={planId} value={planId}>
							规划 {planId.slice(0, 8)}
						</option>
					))}
				</select>
				<select
					aria-label="按数据域筛选"
					onChange={(event) => setDomainFilter(event.target.value)}
					value={domainFilter}
				>
					<option value="">全部数据域</option>
					{domains.map((domain) => (
						<option key={domain.id} value={domain.id}>
							{domain.name}
						</option>
					))}
				</select>
				<select
					aria-label="按对象类型筛选"
					onChange={(event) => setTypeFilter(event.target.value as ModelWorkbenchCatalogEntry["objectType"] | "")}
					value={typeFilter}
				>
					<option value="">全部对象类型</option>
					{Object.entries(MODEL_TYPE_LABEL).map(([value, label]) => (
						<option key={value} value={value}>
							{label}
						</option>
					))}
				</select>
				<select
					aria-label="按数仓分层筛选"
					onChange={(event) => setLayerFilter(event.target.value as ModelSpecLayer | "")}
					value={layerFilter}
				>
					<option value="">全部分层</option>
					{["ODS", "STG", "DWD", "DWS", "ADS"].map((layer) => (
						<option key={layer} value={layer}>
							{layer}
						</option>
					))}
				</select>
				<select aria-label="按状态筛选" onChange={(event) => setStatusFilter(event.target.value)} value={statusFilter}>
					<option value="">全部状态</option>
					{statusOptions.map((status) => (
						<option key={status} value={status}>
							{status}
						</option>
					))}
				</select>
				<button disabled={!pageSelectableModels.length} onClick={toggleCurrentPage} type="button">
					{allPageModelsSelected ? "取消选择当前页" : "选择当前页"}
				</button>
			</fieldset>
			{compatibilityFallback ? (
				<output className="dmx-model-list-feedback">
					分页服务暂不可用，当前显示本页已加载记录；刷新后可重试服务端分页。
				</output>
			) : null}
			{selectedModels.length ? (
				<div className="dmx-model-list-selection" aria-live="polite">
					<strong>已选清单：</strong>
					<span>{selectedModels.map((model) => model.name).join("、")}</span>
				</div>
			) : null}
			{catalogLoading && !compatibilityFallback ? (
				<RequestState description="正在按当前条件读取模型。" kind="loading" title="加载模型列表" />
			) : pageRows.length ? (
				<>
					<div className="dmx-table-scroll dmx-model-list-table">
						<table className="dmx-table">
							<thead>
								<tr>
									<th>选择</th>
									<th>对象名称</th>
									<th>编码 / 表名</th>
									<th>对象类型</th>
									<th>数据域</th>
									<th>状态</th>
									<th>版本</th>
									<th>物化状态</th>
									<th>操作</th>
								</tr>
							</thead>
							<tbody>
								{pageRows.map((row) => {
									const selected = Boolean(row.model && selectedIds.has(row.model.id));
									const selectable = Boolean(
										row.model &&
											canMaintain &&
											row.model.compatibilityMode === "CANONICAL" &&
											(!selectedPlanId || selectedPlanId === row.model.planId),
									);
									const materialization = row.model
										? resolveMaterializationPresentation(
												materializationByModel.get(row.model.id) || null,
												row.model.revision,
											)
										: null;
									return (
										<tr className={selected ? "selected" : ""} key={`${row.objectType}-${row.id}`}>
											<td>
												{row.model ? (
													<input
														aria-label={`选择 ${row.name}`}
														checked={selected}
														disabled={!selectable || (!selected && selectedIds.size >= 100)}
														onChange={() => toggleModel(row.model as ModelSpecView)}
														type="checkbox"
													/>
												) : (
													"—"
												)}
											</td>
											<td>{row.name}</td>
											<td>{row.code}</td>
											<td>{row.type}</td>
											<td>{domainById.get(row.domainId) || "未归属"}</td>
											<td>
												<Status tone={row.status === "DRAFT" ? "warning" : "info"}>{row.status}</Status>
											</td>
											<td>r{row.revision}</td>
											<td>
												{materialization ? (
													<Status tone={materialization.tone}>{materialization.label}</Status>
												) : (
													"不适用"
												)}
											</td>
											<td>
												<div className="dmx-row-actions">
													<button
														className="dmx-table-action"
														disabled={!row.open}
														onClick={row.open || undefined}
														type="button"
													>
														{row.open ? (row.editable ? "编辑" : "查看") : "详情未加载"}
													</button>
													{row.model ? (
														<button
															className="dmx-table-action"
															onClick={() => onMaterialize([row.model as ModelSpecView])}
															type="button"
														>
															{materialization ? "物化历史 / 再次物化" : "物化详情"}
														</button>
													) : null}
												</div>
											</td>
										</tr>
									);
								})}
							</tbody>
						</table>
					</div>
					<nav aria-label="模型列表分页" className="dmx-pagination dmx-model-list-pagination">
						<button
							disabled={currentPage <= 1}
							onClick={() => setPage((value) => Math.max(1, value - 1))}
							type="button"
						>
							上一页
						</button>
						<span>
							第 {currentPage} / {pageCount} 页
						</span>
						<button
							disabled={currentPage >= pageCount}
							onClick={() => setPage((value) => Math.min(pageCount, value + 1))}
							type="button"
						>
							下一页
						</button>
					</nav>
				</>
			) : (
				<RequestState description="请调整搜索关键词后重试。" kind="empty" title="没有匹配模型" />
			)}
		</section>
	);
}
