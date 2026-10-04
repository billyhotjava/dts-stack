export const CONFORMED_DIMENSION_VERSION = 1 as const;
export const BUS_MATRIX_STORAGE_KEY = "dts.conformed-dimension-bus-matrix.v1";
export const CONFORMED_DIMENSION_REFERENCE_KEY = "dts.conformed-dimension-reference.v1";

export type ConformedDimension = {
	version: typeof CONFORMED_DIMENSION_VERSION;
	dimensionId: string;
	name: string;
	sourceModel?: string;
	domainIds: string[];
	sourceType?: string;
	sourceId?: string;
	sourceVersion?: string;
	confirmed: boolean;
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

/**
 * Returns the conformed dimensions already registered for a business process.
 * An empty result is intentional: callers can render the full catalog as the
 * next registration action without pretending that a dimension is reusable.
 */
export const recommendDimensionsForProcess = (
	processId: string | undefined,
	catalog: ConformedDimension[],
	matrix: BusMatrix,
): ConformedDimension[] => {
	if (!processId?.trim()) return [];
	const registered = new Set(matrix.links[processId] || []);
	return catalog.filter((dimension) => dimension.confirmed === true && registered.has(dimension.dimensionId));
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
		storage?.setItem(
			`${CONFORMED_DIMENSION_REFERENCE_KEY}:${reference.domainId}:${reference.processId}`,
			JSON.stringify(reference),
		);
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
		if (parsed.version !== CONFORMED_DIMENSION_VERSION || parsed.domainId !== domainId || !parsed.links)
			return emptyMatrix(domainId);
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
