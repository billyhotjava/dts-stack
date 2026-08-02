const OPERATION_PARAM = "dmOperationId";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

export type DimensionModelOperationUrl = {
	operationId: string;
	searchParams: URLSearchParams;
	changed: boolean;
};

export const readDimensionModelOperationId = (searchParams: URLSearchParams): string | null => {
	const current = searchParams.get(OPERATION_PARAM)?.trim() || "";
	return UUID.test(current) ? current : null;
};

export const ensureDimensionModelOperationId = (
	searchParams: URLSearchParams,
	operationIdFactory: () => string = () => crypto.randomUUID(),
): DimensionModelOperationUrl => {
	const current = readDimensionModelOperationId(searchParams);
	if (current) {
		return { operationId: current, searchParams, changed: false };
	}
	const next = new URLSearchParams(searchParams);
	const operationId = operationIdFactory();
	if (!UUID.test(operationId)) throw new Error("DIMENSION_MODEL_OPERATION_ID_INVALID");
	next.set(OPERATION_PARAM, operationId);
	return { operationId, searchParams: next, changed: true };
};

export const clearDimensionModelOperationId = (searchParams: URLSearchParams, operationId: string) => {
	if (searchParams.get(OPERATION_PARAM) !== operationId) return { searchParams, changed: false };
	const next = new URLSearchParams(searchParams);
	next.delete(OPERATION_PARAM);
	return { searchParams: next, changed: true };
};
