import type { DragEvent } from "react";
import { useEffect, useState } from "react";
import { BlockSelectorPopover } from "../../block-selector/BlockSelectorPopover";
import { BLOCK_DRAG_MIME, type BlockDef, parseDraggedBlock } from "../../block-selector/blocks.config";
import { createWorkflowNodeFromBlock } from "../../block-selector/create-node";
import { DEFAULT_EDGE_TYPE } from "../../custom-edge";
import { useWorkflowStore } from "../../store/workflow-store";

function blockToPayload(block: BlockDef) {
	return {
		kind: block.kind,
		label: block.label,
		color: block.color,
		defaultData: block.defaultData,
	};
}

export function PlusHandle({ nodeId, outputId }: { nodeId: string; outputId: string }) {
	const [open, setOpen] = useState(false);
	const nodes = useWorkflowStore((s) => s.nodes);
	const addNode = useWorkflowStore((s) => s.addNode);
	const addEdge = useWorkflowStore((s) => s.addEdge);
	const sourceNode = nodes.find((node) => node.id === nodeId);

	useEffect(() => {
		if (!open) return;
		const handleKeyDown = (event: KeyboardEvent) => {
			if (event.key === "Escape") {
				setOpen(false);
			}
		};
		document.addEventListener("keydown", handleKeyDown);
		return () => document.removeEventListener("keydown", handleKeyDown);
	}, [open]);

	const addNextNode = (block: ReturnType<typeof blockToPayload>) => {
		const position = {
			x: (sourceNode?.position.x ?? 0) + 280,
			y: sourceNode?.position.y ?? 0,
		};
		const newNode = createWorkflowNodeFromBlock(block, position);
		addNode(newNode);
		addEdge({
			id: `edge-${nodeId}-${newNode.id}-${Date.now()}`,
			type: DEFAULT_EDGE_TYPE,
			source: nodeId,
			sourceHandle: outputId,
			target: newNode.id,
			targetHandle: "in",
		});
		setOpen(false);
	};

	const handleDragOver = (event: DragEvent<HTMLButtonElement>) => {
		event.preventDefault();
		event.dataTransfer.dropEffect = "move";
	};

	const handleDrop = (event: DragEvent<HTMLButtonElement>) => {
		const block = parseDraggedBlock(event.dataTransfer.getData(BLOCK_DRAG_MIME));
		if (!block) return;
		event.preventDefault();
		event.stopPropagation();
		addNextNode(block);
	};

	return (
		<div className="wf-node-plus-wrap">
			<button
				type="button"
				className="wf-node-plus"
				aria-label="添加下一个节点"
				onClick={() => setOpen((prev) => !prev)}
				onDragOver={handleDragOver}
				onDrop={handleDrop}
			>
				+
			</button>
			{open ? (
				<div className="wf-node-plus-popover">
					<BlockSelectorPopover onSelect={(block) => addNextNode(blockToPayload(block))} />
				</div>
			) : null}
		</div>
	);
}
