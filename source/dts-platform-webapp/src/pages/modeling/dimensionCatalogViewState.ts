export type DimensionCatalogEmptyState = {
	totalCount: number;
	visibleCount: number;
	search: string;
	canEdit: boolean;
};

export const dimensionCatalogEmptyText = ({
	totalCount,
	visibleCount,
	search,
	canEdit,
}: DimensionCatalogEmptyState): string => {
	if (totalCount > 0 && visibleCount === 0 && search.trim()) return "未找到匹配维度，请调整搜索条件";
	if (canEdit) return "还没有维度，点击“登记维度”开始";
	return "还没有可浏览的维度";
};
