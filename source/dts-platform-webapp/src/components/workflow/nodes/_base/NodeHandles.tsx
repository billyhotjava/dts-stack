import { Handle, Position } from "@xyflow/react";
import type { HandleDef } from "./types";

function resolveTop(index: number, total: number): string {
	if (total <= 1) return "50%";
	return `${((index + 1) * 100) / (total + 1)}%`;
}

export function NodeHandles({ inputs, outputs }: { inputs: HandleDef[]; outputs: HandleDef[] }) {
	return (
		<>
			{inputs.map((handle, index) => (
				<Handle
					key={`in-${handle.id}`}
					id={handle.id}
					type="target"
					position={Position.Left}
					className="wf-node-handle wf-node-handle-in"
					style={{ top: resolveTop(index, inputs.length), borderColor: handle.color }}
				/>
			))}
			{outputs.map((handle, index) => (
				<div
					key={`out-wrap-${handle.id}`}
					className="wf-node-output"
					style={{ top: resolveTop(index, outputs.length), color: handle.color }}
				>
					{handle.label ? <span className="wf-node-output-label">{handle.label}</span> : null}
					<Handle
						id={handle.id}
						type="source"
						position={Position.Right}
						className="wf-node-handle wf-node-handle-out"
						style={{ borderColor: handle.color, backgroundColor: handle.color }}
					/>
				</div>
			))}
		</>
	);
}
