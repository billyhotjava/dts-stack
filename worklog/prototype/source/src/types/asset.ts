/** 资产分层。 */
export type AssetLayer = "ODS" | "DWD" | "ADS";
export type AssetStatus = "published" | "draft";

export interface DatasetColumn {
	name: string;
	type: string;
	comment?: string;
}

/** 数据集 —— 资产归口部门（departmentId）。 */
export interface Dataset {
	id: string;
	departmentId: string;
	name: string;
	layer: AssetLayer;
	kind: "table" | "view";
	status: AssetStatus;
	rowCount?: number;
	/** 质量分 0–100 */
	qualityScore?: number;
	owner?: string;
	updatedAt?: string;
	description?: string;
	columns: DatasetColumn[];
}

/** 数据产品 —— 一组数据集对外打包。 */
export interface DataProduct {
	id: string;
	departmentId: string;
	name: string;
	status: AssetStatus;
	datasetIds: string[];
	description?: string;
}

export type LineageKind = "source" | "model" | "dataset" | "metric";

export interface LineageNode {
	id: string;
	label: string;
	kind: LineageKind;
	x: number;
	y: number;
}
export interface LineageEdge {
	source: string;
	target: string;
}
export interface DatasetLineage {
	nodes: LineageNode[];
	edges: LineageEdge[];
}

/** 质量规则与结果。 */
export interface QualityRule {
	id: string;
	datasetId: string;
	name: string;
	dimension: "完整性" | "唯一性" | "有效性" | "及时性";
	status: "pass" | "fail" | "warn";
	lastRun?: string;
}

/** 资产授权（归口部门把资产授权给其它部门）。 */
export interface AssetGrant {
	id: string;
	datasetId: string;
	granteeDept: string;
	level: "read" | "write";
	grantedAt: string;
}
