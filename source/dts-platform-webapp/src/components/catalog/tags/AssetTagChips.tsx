import type { CatalogTagDto } from "@/api/catalogTagsApi";

export type DisplayCatalogTag = CatalogTagDto & {
	categoryName?: string;
};

type AssetTagChipsProps = {
	tags: DisplayCatalogTag[];
	removable?: boolean;
	onRemove?: (tag: DisplayCatalogTag) => void;
	disabled?: boolean;
	variant?: "inline" | "panel";
};

const TAG_COLORS = new Set(["#1677ff", "#13c2c2", "#52c41a", "#faad14", "#fa541c", "#722ed1", "#eb2f96", "#8c8c8c"]);

function safeTagColor(color?: string | null): string {
	const normalized = String(color || "")
		.trim()
		.toLowerCase();
	return TAG_COLORS.has(normalized) ? normalized : "#8c8c8c";
}

export function AssetTagChips({
	tags,
	removable = false,
	onRemove,
	disabled = false,
	variant = "inline",
}: AssetTagChipsProps) {
	if (!tags.length) return null;
	return (
		<ul
			aria-label="业务数据标签"
			className={
				variant === "panel" ? "flex list-none flex-wrap gap-2 p-0" : "mt-1 flex min-w-0 list-none flex-wrap gap-1 p-0"
			}
		>
			{tags.map((tag) => (
				<li
					key={tag.id}
					data-business-tag="true"
					title={tag.categoryName ? `业务数据标签｜分类：${tag.categoryName}` : "业务数据标签"}
					className="inline-flex max-w-full items-center gap-1 rounded border border-slate-200 border-l-[3px] bg-slate-50 px-1.5 py-0.5 text-xs leading-5 text-slate-700"
					style={{ borderLeftColor: safeTagColor(tag.color) }}
				>
					<span className="truncate">{tag.name}</span>
					{removable && onRemove ? (
						<button
							type="button"
							aria-label={`移除业务标签 ${tag.name}`}
							title={`移除业务标签“${tag.name}”`}
							disabled={disabled}
							onClick={() => onRemove(tag)}
							className="inline-flex h-4 w-4 shrink-0 items-center justify-center rounded text-slate-500 hover:bg-slate-200 hover:text-slate-800 disabled:cursor-not-allowed disabled:opacity-40"
						>
							×
						</button>
					) : null}
				</li>
			))}
		</ul>
	);
}
