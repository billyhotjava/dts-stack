const PAGE_SIZE = 10;
const MAX_PAGES = 100;

type IndicatorPage<T> = {
	content?: T[];
	totalPages?: number;
};

export async function loadAllIndicatorPages<T>(
	fetchPage: (page: number, size: number) => Promise<IndicatorPage<T> | T[]>,
): Promise<T[]> {
	const items: T[] = [];
	for (let page = 0; page < MAX_PAGES; page += 1) {
		const result = await fetchPage(page, PAGE_SIZE);
		if (Array.isArray(result)) return result;
		const content = Array.isArray(result?.content) ? result.content : [];
		items.push(...content);
		const totalPages = Number(result?.totalPages);
		if (Number.isFinite(totalPages) ? page + 1 >= totalPages : content.length < PAGE_SIZE) break;
	}
	return items;
}
