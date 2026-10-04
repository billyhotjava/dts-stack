import {
	BLOCKS,
	type BlockCategory,
	type BlockDef,
	CATEGORY_LABEL,
	CATEGORY_ORDER,
	filterBlocks,
	groupByCategory,
} from "./blocks.config";

export interface BlockSelectorPopoverProps {
	blocks?: ReadonlyArray<BlockDef>;
	keyword?: string;
	onSelect: (block: BlockDef) => void;
}

export function BlockSelectorPopover({ blocks = BLOCKS, keyword = "", onSelect }: BlockSelectorPopoverProps) {
	const grouped = groupByCategory(filterBlocks(blocks, { keyword }));
	const visibleCategories = CATEGORY_ORDER.filter((cat) => grouped[cat].length > 0);

	return (
		<div className="block-selector-popover" role="menu" aria-label="选择下一个节点">
			{visibleCategories.map((cat: BlockCategory) => (
				<section key={cat} className="block-selector-popover__group" aria-label={CATEGORY_LABEL[cat]}>
					<h5 className="block-selector-popover__title">{CATEGORY_LABEL[cat]}</h5>
					{grouped[cat].map((block) => (
						<button
							key={block.kind}
							type="button"
							className="block-selector-popover__item"
							onClick={() => onSelect(block)}
							role="menuitem"
						>
							<span className="block-selector-popover__icon" style={{ color: block.color }} aria-hidden="true">
								{block.icon}
							</span>
							<span className="block-selector-popover__label">{block.label}</span>
						</button>
					))}
				</section>
			))}
		</div>
	);
}
