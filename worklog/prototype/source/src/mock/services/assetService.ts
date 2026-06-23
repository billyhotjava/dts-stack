import type { Result } from "@/types/api";
import type { AssetGrant, Dataset, DataProduct, DatasetLineage, QualityRule } from "@/types/asset";
import { ok } from "../client";
import { db } from "../db";

/**
 * 资产服务 —— 资产归口部门。
 * 目录/数据产品/血缘/质量/授权均按部门过滤。
 */
export const assetService = {
	listDatasets(departmentId: string, keyword?: string): Promise<Result<Dataset[]>> {
		const kw = (keyword ?? "").trim().toLowerCase();
		const list = db.datasets
			.filter((d) => d.departmentId === departmentId)
			.filter((d) => !kw || d.name.toLowerCase().includes(kw) || (d.description ?? "").toLowerCase().includes(kw));
		return ok(list);
	},
	getDataset(id: string): Promise<Result<Dataset | null>> {
		return ok(db.datasets.find((d) => d.id === id) ?? null);
	},
	listDataProducts(departmentId: string): Promise<Result<DataProduct[]>> {
		return ok(db.dataProducts.filter((p) => p.departmentId === departmentId));
	},
	getLineage(datasetId: string): Promise<Result<DatasetLineage>> {
		return ok(db.lineage[datasetId] ?? { nodes: [], edges: [] });
	},
	listQualityRules(datasetId: string): Promise<Result<QualityRule[]>> {
		return ok(db.qualityRules.filter((r) => r.datasetId === datasetId));
	},
	listQualityByDepartment(departmentId: string): Promise<Result<QualityRule[]>> {
		const dsIds = new Set(db.datasets.filter((d) => d.departmentId === departmentId).map((d) => d.id));
		return ok(db.qualityRules.filter((r) => dsIds.has(r.datasetId)));
	},
	listGrants(datasetId: string): Promise<Result<AssetGrant[]>> {
		return ok(db.assetGrants.filter((g) => g.datasetId === datasetId));
	},
};
