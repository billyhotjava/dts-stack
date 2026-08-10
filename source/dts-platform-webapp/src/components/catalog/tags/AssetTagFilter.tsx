import { Button, Select, Spin } from "antd";
import { useEffect, useMemo, useState } from "react";
import {
	type CatalogTagCategoryDto,
	type CatalogTagDto,
	listAllEnabledCatalogTags,
	listTagCategories,
} from "@/api/catalogTagsApi";

type AssetTagFilterProps = {
	value: string[];
	onChange: (tagIds: string[]) => void;
	disabled?: boolean;
	className?: string;
};

function flattenCategories(categories: CatalogTagCategoryDto[]): CatalogTagCategoryDto[] {
	const result: CatalogTagCategoryDto[] = [];
	for (const category of categories) {
		result.push(category);
		result.push(...flattenCategories(category.children || []));
	}
	return result;
}

export function AssetTagFilter({ value, onChange, disabled = false, className }: AssetTagFilterProps) {
	const [categories, setCategories] = useState<CatalogTagCategoryDto[]>([]);
	const [tags, setTags] = useState<CatalogTagDto[]>([]);
	const [loading, setLoading] = useState(true);
	const [loadError, setLoadError] = useState("");

	useEffect(() => {
		let active = true;
		setLoading(true);
		setLoadError("");
		Promise.all([listTagCategories(), listAllEnabledCatalogTags()])
			.then(([categoryRows, availableTags]) => {
				if (!active) return;
				setCategories(categoryRows || []);
				setTags((availableTags || []).filter((tag) => tag.enabled));
			})
			.catch((error: unknown) => {
				if (!active) return;
				setCategories([]);
				setTags([]);
				setLoadError(error instanceof Error && error.message ? error.message : "业务数据标签暂时无法加载");
			})
			.finally(() => {
				if (active) setLoading(false);
			});
		return () => {
			active = false;
		};
	}, []);

	const options = useMemo(() => {
		const categoryRows = flattenCategories(categories);
		const categoryNameById = new Map(categoryRows.map((category) => [category.id, category.name]));
		const grouped = new Map<string, CatalogTagDto[]>();
		for (const tag of tags) {
			const categoryName = categoryNameById.get(tag.categoryId) || "其他业务标签";
			const rows = grouped.get(categoryName) || [];
			rows.push(tag);
			grouped.set(categoryName, rows);
		}
		return Array.from(grouped.entries()).map(([label, rows]) => ({
			label,
			options: rows
				.slice()
				.sort((left, right) => left.name.localeCompare(right.name, "zh-CN"))
				.map((tag) => ({ value: tag.id, label: tag.name })),
		}));
	}, [categories, tags]);

	return (
		<div className={className}>
			{/* 与筛选区其它字段共用同一套标签排版（h-5/leading-5），保证同行控件顶部对齐 */}
			<div className="mb-1 block h-5 text-xs text-slate-500 leading-5">同时包含所选标签</div>
			<div className="flex min-w-0 items-center gap-2">
				<Spin spinning={loading} size="small" wrapperClassName="min-w-0 flex-1">
					<Select
						mode="multiple"
						aria-label="按业务数据标签筛选"
						value={value}
						options={options}
						onChange={(next) => onChange(next)}
						disabled={disabled || loading}
						placeholder="选择业务数据标签"
						optionFilterProp="label"
						showSearch
						maxTagCount={2}
						className="w-full min-w-[220px]"
						notFoundContent={loadError ? "标签目录不可用" : "暂无可用业务标签"}
					/>
				</Spin>
				<Button
					type="link"
					size="small"
					aria-label="清除标签筛选"
					disabled={disabled || value.length === 0}
					onClick={() => onChange([])}
				>
					清除
				</Button>
			</div>
			{loadError ? <output className="mt-1 text-xs text-amber-700">{loadError}</output> : null}
		</div>
	);
}
