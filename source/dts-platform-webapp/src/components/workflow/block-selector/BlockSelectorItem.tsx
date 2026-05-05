import type { DragEvent } from "react";
import { BLOCK_DRAG_MIME, type BlockDef, serializeBlockForDrag } from "./blocks.config";

export interface BlockSelectorItemProps {
	block: BlockDef;
	collapsed?: boolean;
	onDragStart?: (block: BlockDef) => void;
	onDragEnd?: (block: BlockDef) => void;
}

export function BlockSelectorItem({ block, collapsed = false, onDragStart, onDragEnd }: BlockSelectorItemProps) {
	const handleDragStart = (event: DragEvent<HTMLButtonElement>) => {
		event.dataTransfer.setData(BLOCK_DRAG_MIME, serializeBlockForDrag(block));
		event.dataTransfer.effectAllowed = "move";
		if (event.dataTransfer.setDragImage && typeof document !== "undefined") {
			const ghost = document.createElement("div");
			ghost.className = "candidate-node-ghost";
			ghost.style.borderLeftColor = block.color;
			ghost.textContent = block.label;
			document.body.appendChild(ghost);
			event.dataTransfer.setDragImage(ghost, 56, 24);
			window.setTimeout(() => {
				if (ghost.parentNode) {
					ghost.parentNode.removeChild(ghost);
				}
			}, 0);
		}
		onDragStart?.(block);
	};

	const handleDragEnd = () => {
		onDragEnd?.(block);
	};

	return (
		<button
			type="button"
			className="block-selector-item"
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
		</button>
	);
}
