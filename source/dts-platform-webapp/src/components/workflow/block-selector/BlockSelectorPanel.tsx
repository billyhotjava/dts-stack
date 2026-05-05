import { useMemo, useState } from "react";
import { BlockSelectorItem } from "./BlockSelectorItem";
import {
	BLOCKS,
	type BlockCategory,
	type BlockDef,
	CATEGORY_LABEL,
	CATEGORY_ORDER,
	filterBlocks,
	groupByCategory,
} from "./blocks.config";

export interface BlockSelectorPanelProps {
	blocks?: ReadonlyArray<BlockDef>;
	defaultCollapsed?: boolean;
	onBlockDragStart?: (block: BlockDef) => void;
	onBlockDragEnd?: (block: BlockDef) => void;
}

export function BlockSelectorPanel({
	blocks = BLOCKS,
	defaultCollapsed = false,
	onBlockDragStart,
	onBlockDragEnd,
}: BlockSelectorPanelProps) {
	const [search, setSearch] = useState("");
	const [collapsed, setCollapsed] = useState(defaultCollapsed);

	const grouped = useMemo(() => {
		const filtered = filterBlocks(blocks, { keyword: search });
		return groupByCategory(filtered);
	}, [blocks, search]);

	const visibleCategories = CATEGORY_ORDER.filter((cat) => grouped[cat].length > 0);

	return (
		<aside className="block-selector-panel" data-collapsed={collapsed ? "true" : "false"} aria-label="节点库">
			<header className="block-selector-panel__header">
				{collapsed ? null : (
					<input
						type="search"
						className="block-selector-panel__search"
						value={search}
						onChange={(e) => setSearch(e.target.value)}
						placeholder="搜索节点..."
						aria-label="搜索节点"
					/>
				)}
				<button
					type="button"
					className="block-selector-panel__toggle"
					onClick={() => setCollapsed((prev) => !prev)}
					aria-label={collapsed ? "展开节点库" : "折叠节点库"}
					title={collapsed ? "展开" : "折叠"}
				>
					{collapsed ? "›" : "‹"}
				</button>
			</header>
			<div className="block-selector-panel__body">
				{visibleCategories.length === 0 ? (
					<p className="block-selector-panel__empty">未找到匹配节点</p>
				) : (
					visibleCategories.map((cat: BlockCategory) => (
						<section key={cat} className="block-selector-panel__group" aria-label={CATEGORY_LABEL[cat]}>
							{collapsed ? null : <h4 className="block-selector-panel__group-title">{CATEGORY_LABEL[cat]}</h4>}
							{grouped[cat].map((block) => (
								<BlockSelectorItem
									key={block.kind}
									block={block}
									collapsed={collapsed}
									onDragStart={onBlockDragStart}
									onDragEnd={onBlockDragEnd}
								/>
							))}
						</section>
					))
				)}
			</div>
		</aside>
	);
}
