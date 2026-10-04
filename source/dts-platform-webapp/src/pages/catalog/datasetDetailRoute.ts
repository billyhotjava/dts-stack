const DATASET_DETAIL_PATH = /^\/catalog\/datasets\/([^/]+)\/?$/;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function resolveDatasetDetailId(routeId: string | undefined, pathname: string): string {
	const normalizedRouteId = routeId?.trim();
	if (normalizedRouteId) return UUID_PATTERN.test(normalizedRouteId) ? normalizedRouteId : "";

	const encodedId = DATASET_DETAIL_PATH.exec(pathname)?.[1];
	if (!encodedId) return "";
	try {
		const decodedId = decodeURIComponent(encodedId);
		return UUID_PATTERN.test(decodedId) ? decodedId : "";
	} catch {
		return "";
	}
}
