import { ArrowLeft, Search } from "lucide-react";
import { useMemo, useState } from "react";
import type { CatalogDomain } from "@/api/services/catalogDomainService";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
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
};

export function ModelWorkbenchCatalogList({
	canMaintain,
	dimensions,
	domains,
	models,
	onBack,
	onChooseDimension,
	onChooseModel,
}: ModelWorkbenchCatalogListProps) {
	const [query, setQuery] = useState("");
	const domainById = useMemo(() => new Map(domains.map((domain) => [domain.id, domain.name])), [domains]);
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
				<span>共 {rows.length} 条</span>
			</div>
			{rows.length ? (
				<div className="dmx-table-scroll dmx-model-list-table">
					<table className="dmx-table">
						<thead>
							<tr>
								<th>对象名称</th>
								<th>编码 / 表名</th>
								<th>对象类型</th>
								<th>数据域</th>
								<th>状态</th>
								<th>版本</th>
								<th>操作</th>
							</tr>
						</thead>
						<tbody>
							{rows.map((row) => (
								<tr key={`${row.type}-${row.id}`}>
									<td>{row.name}</td>
									<td>{row.code}</td>
									<td>{row.type}</td>
									<td>{domainById.get(row.domainId) || "未归属"}</td>
									<td>
										<Status tone={row.status === "DRAFT" ? "warning" : "info"}>{row.status}</Status>
									</td>
									<td>r{row.revision}</td>
									<td>
										<button className="dmx-table-action" onClick={row.open} type="button">
											{row.editable ? "编辑" : "查看"}
										</button>
									</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			) : (
				<RequestState description="请调整搜索关键词后重试。" kind="empty" title="没有匹配模型" />
			)}
		</section>
	);
}
