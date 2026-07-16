export const CONFORMED_DIMENSION_VERSION = 1 as const;
export const BUS_MATRIX_STORAGE_KEY = "dts.conformed-dimension-bus-matrix.v1";
export const CONFORMED_DIMENSION_REFERENCE_KEY = "dts.conformed-dimension-reference.v1";

export type ConformedDimension = {
	version: typeof CONFORMED_DIMENSION_VERSION;
	dimensionId: string;
	name: string;
	sourceModel?: string;
	domainIds: string[];
};

export type BusMatrix = {
	version: typeof CONFORMED_DIMENSION_VERSION;
	domainId: string;
	links: Record<string, string[]>;
	updatedAt: string;
};

export type ConformedDimensionReference = {
	version: typeof CONFORMED_DIMENSION_VERSION;
	domainId: string;
	processId: string;
	dimensionId: string;
	name: string;
	sourceModel?: string;
	savedAt: string;
};

export type ConformedDimensionStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

export const CONFORMED_DIMENSION_SEEDS: ConformedDimension[] = [
	{ version: 1, dimensionId: "completion-status", name: "完成情况", sourceModel: "dim_completion_status_v2", domainIds: ["*"] },
	{ version: 1, dimensionId: "node-type", name: "节点类型", sourceModel: "dim_node_type_v2", domainIds: ["*"] },
	{ version: 1, dimensionId: "risk-level", name: "风险等级", sourceModel: "dim_risk_level_v2", domainIds: ["*"] },
	{ version: 1, dimensionId: "quality-zero-status", name: "质量归零状态", sourceModel: "dim_quality_zero_status_v2", domainIds: ["*"] },
	{ version: 1, dimensionId: "quality-reason", name: "质量原因分类", sourceModel: "dim_quality_reason_v2", domainIds: ["*"] },
	{ version: 1, dimensionId: "technical-change-type", name: "技术状态更改类别", sourceModel: "dim_technical_change_type_v2", domainIds: ["*"] },
	{ version: 1, dimensionId: "signing-status", name: "签署状态", sourceModel: "dim_signing_status_v2", domainIds: ["*"] },
	{ version: 1, dimensionId: "risk-category", name: "风险分类", sourceModel: "dim_risk_category_v2", domainIds: ["*"] },
];

const defaultStorage = (): ConformedDimensionStorage | undefined => {
	try {
		return typeof window === "undefined" ? undefined : window.sessionStorage;
	} catch {
		return undefined;
	}
};

const emptyMatrix = (domainId: string): BusMatrix => ({
	version: CONFORMED_DIMENSION_VERSION,
	domainId,
	links: {},
	updatedAt: new Date().toISOString(),
});

export const dimensionsForDomain = (domainId: string): ConformedDimension[] =>
	CONFORMED_DIMENSION_SEEDS.filter((item) => item.domainIds.includes("*") || item.domainIds.includes(domainId));

/**
 * Returns the conformed dimensions already registered for a business process.
 * An empty result is intentional: callers can render the full catalog as the
 * next registration action without pretending that a dimension is reusable.
 */
export const recommendDimensionsForProcess = (
	domainId: string,
	processId: string | undefined,
	matrix: BusMatrix = loadBusMatrix(domainId),
): ConformedDimension[] => {
	if (!processId?.trim()) return [];
	const registered = new Set(matrix.links[processId] || []);
	return dimensionsForDomain(domainId).filter((dimension) => registered.has(dimension.dimensionId));
};

export const buildConformedDimensionReference = (
	domainId: string,
	processId: string,
	dimension: ConformedDimension,
	now = new Date().toISOString(),
): ConformedDimensionReference => ({
	version: CONFORMED_DIMENSION_VERSION,
	domainId,
	processId,
	dimensionId: dimension.dimensionId,
	name: dimension.name,
	sourceModel: dimension.sourceModel,
	savedAt: now,
});

export const saveConformedDimensionReference = (
	reference: ConformedDimensionReference,
	storage: ConformedDimensionStorage | undefined = defaultStorage(),
): ConformedDimensionReference => {
	try {
		storage?.setItem(`${CONFORMED_DIMENSION_REFERENCE_KEY}:${reference.domainId}:${reference.processId}`, JSON.stringify(reference));
	} catch {
		// session storage can be unavailable; callers still receive the metadata.
	}
	return reference;
};

export const loadBusMatrix = (
	domainId: string,
	storage: ConformedDimensionStorage | undefined = defaultStorage(),
): BusMatrix => {
	if (!storage) return emptyMatrix(domainId);
	try {
		const raw = storage.getItem(`${BUS_MATRIX_STORAGE_KEY}:${domainId}`);
		if (!raw) return emptyMatrix(domainId);
		const parsed = JSON.parse(raw) as BusMatrix;
		if (parsed.version !== CONFORMED_DIMENSION_VERSION || parsed.domainId !== domainId || !parsed.links) return emptyMatrix(domainId);
		return parsed;
	} catch {
		return emptyMatrix(domainId);
	}
};

export const toggleBusMatrixLink = (
	domainId: string,
	processId: string,
	dimensionId: string,
	storage: ConformedDimensionStorage | undefined = defaultStorage(),
): BusMatrix => {
	const current = loadBusMatrix(domainId, storage);
	const selected = new Set(current.links[processId] || []);
	if (selected.has(dimensionId)) selected.delete(dimensionId);
	else selected.add(dimensionId);
	const links = { ...current.links };
	if (selected.size) links[processId] = [...selected].sort();
	else delete links[processId];
	const next = { ...current, links, updatedAt: new Date().toISOString() };
	try {
		storage?.setItem(`${BUS_MATRIX_STORAGE_KEY}:${domainId}`, JSON.stringify(next));
	} catch {
		// session storage can be unavailable; callers still receive the computed state.
	}
	return next;
};
