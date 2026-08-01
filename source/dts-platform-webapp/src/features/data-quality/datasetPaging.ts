type DatasetPage<T> = {
	content?: T[];
	total?: number;
	totalElements?: number;
	totalPages?: number;
};

export async function collectDatasetPages<T extends { id?: unknown }>(
	fetchPage: (page: number, size: number) => Promise<DatasetPage<T> | T[]>,
): Promise<T[]> {
	const pageSize = 200;
	const byId = new Map<string, T>();
	let received = 0;

	for (let page = 0; page < 1000; page += 1) {
		const response = await fetchPage(page, pageSize);
		const isPlainList = Array.isArray(response);
		const rows = isPlainList ? response : Array.isArray(response.content) ? response.content : [];
		if (!rows.length) break;

		const knownDatasetCount = byId.size;
		rows.forEach((row, index) => {
			const key = row.id == null ? `__row_${page}_${index}` : String(row.id);
			if (!byId.has(key)) byId.set(key, row);
		});
		received += rows.length;

		if (byId.size === knownDatasetCount) break;
		if (isPlainList) break;
		const hasPaginationTotal =
			Number.isFinite(response.totalPages) ||
			Number.isFinite(response.total) ||
			Number.isFinite(response.totalElements);
		if (Number.isFinite(response.totalPages) && page + 1 >= Number(response.totalPages)) break;
		if (Number.isFinite(response.total) && received >= Number(response.total)) break;
		if (Number.isFinite(response.totalElements) && received >= Number(response.totalElements)) break;
		if (!hasPaginationTotal && rows.length < pageSize) break;
	}

	return Array.from(byId.values());
}
