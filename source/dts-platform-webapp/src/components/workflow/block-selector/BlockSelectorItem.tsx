import type { DragEvent } from "react";
import { BLOCK_DRAG_MIME, serializeBlockForDrag, type BlockDef } from "./blocks.config";

export interface BlockSelectorItemProps {
	block: BlockDef;
	collapsed?: boolean;
	onDragStart?: (block: BlockDef) => void;
	onDragEnd?: (block: BlockDef) => void;
}

export function BlockSelectorItem({ block, collapsed = false, onDragStart, onDragEnd }: BlockSelectorItemProps) {
	const handleDragStart = (event: DragEvent<HTMLDivElement>) => {
		event.dataTransfer.setData(BLOCK_DRAG_MIME, serializeBlockForDrag(block));
		event.dataTransfer.effectAllowed = "move";
		onDragStart?.(block);
	};

	const handleDragEnd = () => {
		onDragEnd?.(block);
	};

	return (
		<div
			className="block-selector-item"
			role="button"
			tabIndex={0}
			draggable
			onDragStart={handleDragStart}
			onDragEnd={handleDragEnd}
			aria-label={`节点 ${block.label}：${block.description}`}
			data-kind={block.kind}
			data-testid={`block-${block.kind}`}
			style={{ borderLeftColor: block.color }}
			title={block.description}
		>
			<span className="block-selector-item__icon" style={{ color: block.color }} aria-hidden="true">
				{block.icon}
			</span>
			{collapsed ? null : (
				<span className="block-selector-item__body">
					<span className="block-selector-item__label">{block.label}</span>
					<span className="block-selector-item__desc">{block.description}</span>
				</span>
			)}
		</div>
	);
}
