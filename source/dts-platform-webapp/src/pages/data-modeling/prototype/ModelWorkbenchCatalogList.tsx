import { ArrowLeft, Search } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { getModelMaterializationStatuses, type ModelMaterializationStatus } from "@/api/modelSpecApi";
import type { CatalogDomain } from "@/api/services/catalogDomainService";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { resolveMaterializationPresentation } from "./ModelMaterializationStatus";
import { RequestState, Status } from "./PrototypePrimitives";
import "./modeling-workbench.css";

const MODEL_TYPE_LABEL: Record<string, string> = {
	DIMENSION: "维度表",
	FACT: "明细表",
	SUMMARY: "汇总表",
	APPLICATION: "应用表",
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
	const [selectedIds, setSelectedIds] = useState<Set<string>>(() => new Set());
	const [materializationByModel, setMaterializationByModel] = useState<Map<string, ModelMaterializationStatus>>(
		() => new Map(),
	);
	const domainById = useMemo(() => new Map(domains.map((domain) => [domain.id, domain.name])), [domains]);
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
	const normalized = query.trim().toLowerCase();
	const rows = useMemo(
		() =>
			[
				...dimensions.map((dimension) => ({
					id: dimension.id,
					name: dimension.name,
					code: dimension.systemCode,
					type: "维度",
					domainId: dimension.domainId,
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
					domainId: model.domainId || "",
					status: model.status,
					revision: model.revision,
					editable: canMaintain && model.status === "DRAFT" && model.compatibilityMode === "CANONICAL",
					model,
					open: () => onChooseModel(model),
				})),
			].filter((row) => {
				if (!normalized) return true;
				return [row.name, row.code, row.type, row.status, domainById.get(row.domainId) || ""]
					.join(" ")
					.toLowerCase()
					.includes(normalized);
			}),
		[canMaintain, dimensions, domainById, models, normalized, onChooseDimension, onChooseModel],
	);
	const selectedModels = models.filter((model) => selectedIds.has(model.id));
	const selectedPlanId = selectedModels[0]?.planId || "";
	const toggleModel = (model: ModelSpecView) => {
		setSelectedIds((current) => {
			const next = new Set(current);
			if (next.has(model.id)) next.delete(model.id);
			else if ((!selectedPlanId || selectedPlanId === model.planId) && next.size < 100) next.add(model.id);
			return next;
		});
	};

	return (
		<section className="dmx-model-list-workspace" aria-label="模型列表管理">
			<header>
				<div>
					<h2>模型列表</h2>
					<p>用于跨目录搜索和管理；进入对象后仍在同一模型工作台编辑。</p>
				</div>
				<button onClick={onBack} type="button">
					<ArrowLeft size={15} />
					返回目录工作台
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
						共 {rows.length} 条，已选 {selectedModels.length} 个模型
					</span>
					<button
						className="primary"
						disabled={!canMaintain || !selectedModels.length}
						onClick={() => onMaterialize(selectedModels)}
						type="button"
					>
						物化所选（{selectedModels.length}）
					</button>
				</div>
			</div>
			{rows.length ? (
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
							{rows.map((row) => {
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
									<tr className={selected ? "selected" : ""} key={`${row.type}-${row.id}`}>
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
											<button className="dmx-table-action" onClick={row.open} type="button">
												{row.editable ? "编辑" : "查看"}
											</button>
										</td>
									</tr>
								);
							})}
						</tbody>
					</table>
				</div>
			) : (
				<RequestState description="请调整搜索关键词后重试。" kind="empty" title="没有匹配模型" />
			)}
		</section>
	);
}
