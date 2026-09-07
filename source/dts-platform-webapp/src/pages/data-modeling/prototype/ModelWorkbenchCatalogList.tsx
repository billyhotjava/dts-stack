import { Space } from "antd";
import { Search } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { getModelDeliveryStatus, type ModelDeliveryStatus } from "@/api/modelDeliveryStatusApi";
import {
	listModelWorkbenchCatalogPage,
	type ModelWorkbenchCatalogEntry,
	type ModelWorkbenchCatalogPage,
} from "@/api/modelSpecApi";
import type { CatalogDomain } from "@/api/services/catalogDomainService";
import { actionColumn, type CompactColumns, CompactTable } from "@/components/table";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelSpecLayer, ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { useRouter } from "@/routes/hooks";
import { statusLabel } from "@/utils/customerDisplayLabels";
import { ModelDeliveryStatusCell, resolveModelDeliveryCell } from "./ModelDeliveryStatusCell";
import { ModelWorkbenchCreateMenu } from "./ModelWorkbenchCreateMenu";
import { Button, RequestState, Status } from "./PrototypePrimitives";
import type { ModelCreateKind } from "./services/modelWorkbenchService";
import "./modeling-workbench.css";

const MODEL_TYPE_LABEL: Record<string, string> = {
	SOURCE: "贴源表",
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
	dimension: DimensionDefinitionView | null;
	model: ModelSpecView | null;
	open: (() => void) | null;
};

export type ModelWorkbenchCatalogListProps = {
	busy: boolean;
	canMaintain: boolean;
	dimensions: DimensionDefinitionView[];
	domains: CatalogDomain[];
	failureMessage: string;
	models: ModelSpecView[];
	onArchiveModel: (model: ModelSpecView) => void;
	onCloneDimension: (dimension: DimensionDefinitionView) => void;
	onChooseDimension: (dimension: DimensionDefinitionView) => void;
	onChooseModel: (model: ModelSpecView) => void;
	onCreate: (kind: ModelCreateKind, categoryId: string) => void;
	onGoToGraphDimension: (dimension: DimensionDefinitionView) => void;
	onGoToGraphModel: (model: ModelSpecView) => void;
	onImport: () => void;
	onMaterialize: (models: ModelSpecView[]) => void;
	onRefresh: () => void;
	onRemoveDimension: (dimension: DimensionDefinitionView) => void;
	onRemoveModel: (model: ModelSpecView) => void;
};

export function ModelWorkbenchCatalogList({
	busy,
	canMaintain,
	dimensions,
	domains,
	failureMessage,
	models,
	onArchiveModel,
	onCloneDimension,
	onChooseDimension,
	onChooseModel,
	onCreate,
	onGoToGraphDimension,
	onGoToGraphModel,
	onImport,
	onMaterialize,
	onRefresh,
	onRemoveDimension,
	onRemoveModel,
}: ModelWorkbenchCatalogListProps) {
	const router = useRouter();
	const [createOpen, setCreateOpen] = useState(false);
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
	const [deliveryByModel, setDeliveryByModel] = useState<Map<string, ModelDeliveryStatus>>(() => new Map());
	const [deliveryLoading, setDeliveryLoading] = useState(false);
	const [deliveryFailure, setDeliveryFailure] = useState(false);
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
		() =>
			Array.from(
				new Set([...dimensions.map((item) => item.status), ...models.map((item) => item.status), "ARCHIVED"]),
			).sort(),
		[dimensions, models],
	);
	const normalized = query.trim().toLowerCase();
	const categoryRoots = useMemo(() => domains.filter((domain) => !domain.parentCode), [domains]);

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
				editable: canMaintain && dimension.status !== "RETIRED",
				dimension,
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
				dimension: null,
				model,
				open: () => onChooseModel(model),
			})),
		],
		[canMaintain, dimensions, models, onChooseDimension, onChooseModel],
	);

	const filteredLocalRows = useMemo(
		() =>
			allLocalRows.filter((row) => {
				if (!statusFilter && row.status === "ARCHIVED") return false;
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
							((dimension && dimension.status !== "RETIRED") ||
								(model && model.status === "DRAFT" && model.compatibilityMode === "CANONICAL")),
					),
					dimension,
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
	const pageModelIdsKey = pageRows
		.map((row) => (row.model ? `${row.model.id}:${row.model.revision}` : ""))
		.filter(Boolean)
		.join(",");
	useEffect(() => {
		const modelSpecIds = pageModelIdsKey
			.split(",")
			.map((item) => item.split(":")[0])
			.filter(Boolean);
		if (!modelSpecIds.length) {
			setDeliveryByModel(new Map());
			setDeliveryLoading(false);
			setDeliveryFailure(false);
			return;
		}
		let active = true;
		setDeliveryLoading(true);
		setDeliveryFailure(false);
		void Promise.all(modelSpecIds.map(async (id) => [id, await getModelDeliveryStatus(id)] as const))
			.then((results) => {
				if (active) setDeliveryByModel(new Map(results));
			})
			.catch(() => {
				if (active) {
					setDeliveryByModel(new Map());
					setDeliveryFailure(true);
				}
			})
			.finally(() => {
				if (active) setDeliveryLoading(false);
			});
		return () => {
			active = false;
		};
	}, [pageModelIdsKey]);

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
					model.status !== "ARCHIVED" &&
					model.compatibilityMode === "CANONICAL" &&
					(!selectedPlanId || selectedPlanId === model.planId),
			),
		);
	const allPageModelsSelected =
		pageSelectableModels.length > 0 && pageSelectableModels.every((model) => selectedIds.has(model.id));

	useEffect(() => {
		if (page > pageCount) setPage(pageCount);
	}, [page, pageCount]);

	const toggleModel = useCallback(
		(model: ModelSpecView) => {
			setSelectedIds((current) => {
				const next = new Set(current);
				if (next.has(model.id)) next.delete(model.id);
				else if ((!selectedPlanId || selectedPlanId === model.planId) && next.size < 100) next.add(model.id);
				return next;
			});
		},
		[selectedPlanId],
	);
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

	const catalogColumns = useMemo<CompactColumns<CatalogRow>>(
		() => [
			{
				title: "选择",
				key: "select",
				width: 60,
				align: "center",
				render: (_, row) => {
					const model = row.model;
					if (!model) return <span className="dmx-table-muted">—</span>;
					const selected = selectedIds.has(model.id);
					const selectable = Boolean(
						canMaintain &&
							model.compatibilityMode === "CANONICAL" &&
							(!selectedPlanId || selectedPlanId === model.planId),
					);
					return (
						<input
							aria-label={`选择 ${row.name}`}
							checked={selected}
							disabled={!selectable || (!selected && selectedIds.size >= 100)}
							onChange={() => toggleModel(model)}
							type="checkbox"
						/>
					);
				},
			},
			{ title: "对象名称", dataIndex: "name" },
			{ title: "编码 / 表名", dataIndex: "code" },
			{ title: "对象类型", dataIndex: "type" },
			{
				title: "数据域",
				dataIndex: "domainId",
				render: (value: string) => domainById.get(value) || "未归属",
			},
			{
				title: "状态",
				dataIndex: "status",
				render: (value: string) => <Status tone={value === "DRAFT" ? "warning" : "info"}>{statusLabel(value)}</Status>,
			},
			{
				title: "版本",
				dataIndex: "revision",
				render: (value: number) => `r${value}`,
			},
			{
				title: "物化状态",
				key: "materialization",
				render: (_, row) =>
					row.model ? (
						<ModelDeliveryStatusCell
							model={row.model}
							status={deliveryByModel.get(row.model.id)}
							kind="materialization"
							loading={deliveryLoading}
							failed={deliveryFailure}
						/>
					) : (
						"不适用"
					),
			},
			{
				title: "目录登记",
				key: "catalog-registration",
				render: (_, row) => {
					if (!row.model) return "不适用";
					const delivery = deliveryByModel.get(row.model.id);
					const assetId = resolveModelDeliveryCell(row.model, delivery, "catalog").assetId;
					return (
						<Space size={4}>
							<ModelDeliveryStatusCell
								model={row.model}
								status={delivery}
								kind="catalog"
								loading={deliveryLoading}
								failed={deliveryFailure}
							/>
							{assetId ? (
								<Button onClick={() => router.push(`/catalog/datasets/${encodeURIComponent(assetId)}`)} type="text">
									查看资产
								</Button>
							) : null}
						</Space>
					);
				},
			},
			{
				title: "分析准备",
				key: "analysis-preparation",
				render: (_, row) =>
					row.model ? (
						<ModelDeliveryStatusCell
							model={row.model}
							status={deliveryByModel.get(row.model.id)}
							kind="analysis"
							loading={deliveryLoading}
							failed={deliveryFailure}
						/>
					) : (
						"不适用"
					),
			},
			actionColumn<CatalogRow>(
				(row) => [
					{
						key: "open",
						label: row.open ? (row.editable ? "编辑" : "查看") : "详情未加载",
						disabled: busy || !row.open,
						onClick: row.open || undefined,
					},
					{
						key: "model-graph",
						label: "关系图",
						hidden: !row.model,
						disabled: busy,
						onClick: () => onGoToGraphModel(row.model as ModelSpecView),
					},
					{
						key: "materialize",
						label: row.model && deliveryByModel.has(row.model.id) ? "物化历史 / 再次物化" : "物化详情",
						hidden: !row.model,
						disabled: busy || !canMaintain || row.model?.status === "ARCHIVED",
						onClick: () => onMaterialize([row.model as ModelSpecView]),
					},
					{
						key: "model-remove",
						label: "删除",
						hidden: !row.model || row.model.status !== "DRAFT",
						disabled: busy || !canMaintain || row.model?.compatibilityMode !== "CANONICAL",
						onClick: () => onRemoveModel(row.model as ModelSpecView),
					},
					{
						key: "model-archive",
						label: "归档",
						hidden: !row.model || row.model.status === "DRAFT" || row.model.status === "ARCHIVED",
						disabled: busy || !canMaintain || row.model?.compatibilityMode !== "CANONICAL",
						onClick: () => onArchiveModel(row.model as ModelSpecView),
					},
					{
						key: "dimension-graph",
						label: "关系图",
						hidden: !!row.model || !row.dimension,
						disabled: busy,
						onClick: () => onGoToGraphDimension(row.dimension as DimensionDefinitionView),
					},
					{
						key: "dimension-clone",
						label: "克隆",
						hidden: !!row.model || !row.dimension,
						disabled: busy || !canMaintain,
						onClick: () => onCloneDimension(row.dimension as DimensionDefinitionView),
					},
					{
						key: "dimension-remove",
						label: row.dimension?.status === "DRAFT" ? "删除" : "退役",
						hidden: !!row.model || !row.dimension,
						disabled: busy || !canMaintain,
						onClick: () => onRemoveDimension(row.dimension as DimensionDefinitionView),
					},
				],
				{ width: 250, fixed: false },
			),
		],
		[
			busy,
			canMaintain,
			domainById,
			deliveryByModel,
			deliveryFailure,
			deliveryLoading,
			onCloneDimension,
			onGoToGraphDimension,
			onGoToGraphModel,
			onMaterialize,
			onArchiveModel,
			onRemoveDimension,
			onRemoveModel,
			router,
			selectedIds,
			selectedPlanId,
			toggleModel,
		],
	);

	return (
		<section className="dmx-model-list-workspace" aria-label="模型列表管理">
			<header>
				<div>
					<h2>模型列表</h2>
					<p>通过筛选和分页选择模型；详情进入同一编辑器，勾选只用于批量交付。</p>
				</div>
				<div className="dmx-model-list-header-actions">
					<Button disabled={busy || catalogLoading} onClick={onRefresh}>
						刷新
					</Button>
					<Button disabled={busy} onClick={onImport}>
						逆向建模
					</Button>
					<Button
						disabled={busy || !canMaintain}
						onClick={() => setCreateOpen((open) => !open)}
						primary
						title={canMaintain ? "新建模型" : "当前账号无建模维护权限"}
					>
						新建模型
					</Button>
				</div>
			</header>
			{createOpen ? (
				<ModelWorkbenchCreateMenu
					categoryRoots={categoryRoots}
					onCreate={(kind, categoryId) => {
						setCreateOpen(false);
						onCreate(kind, categoryId);
					}}
					saving={busy}
				/>
			) : null}
			{failureMessage ? <output className="dmx-inline-error">{failureMessage}</output> : null}
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
					{selectedModels.length ? <Button onClick={() => setSelectedIds(new Set())}>清空已选</Button> : null}
					<Button
						disabled={!canMaintain || !selectedModels.length}
						onClick={() => onMaterialize(selectedModels)}
						primary
					>
						生成物化候选（{selectedModels.length}）
					</Button>
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
					<option value="">在用状态（不含已归档）</option>
					{statusOptions.map((status) => (
						<option key={status} value={status}>
							{statusLabel(status)}
						</option>
					))}
				</select>
				<Button disabled={!pageSelectableModels.length} onClick={toggleCurrentPage}>
					{allPageModelsSelected ? "取消选择当前页" : "选择当前页"}
				</Button>
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
				<CompactTable<CatalogRow>
					className="dmx-model-list-table"
					columns={catalogColumns}
					dataSource={pageRows}
					pagination={{
						current: currentPage,
						pageSize: PAGE_SIZE,
						total: totalElements,
						showSizeChanger: false,
						onChange: (next) => setPage(next),
					}}
					rowClassName={(row) => (row.model && selectedIds.has(row.model.id) ? "selected" : "")}
					rowKey={(row) => `${row.objectType}-${row.id}`}
				/>
			) : (
				<RequestState description="请调整搜索关键词后重试。" kind="empty" title="没有匹配模型" />
			)}
		</section>
	);
}
