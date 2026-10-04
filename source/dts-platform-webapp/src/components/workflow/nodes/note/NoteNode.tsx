import { useEffect, useRef, useState } from "react";
import { useWorkflowStore } from "../../store/workflow-store";
import { getNodeConfig, stringValue, type WorkflowNodeProps } from "../utils";

const DEFAULT_COLOR = "#fef3c7";

function numberConfig(value: unknown, fallback: number): number {
	return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

export function NoteNode({ id, data, selected, dragging }: WorkflowNodeProps) {
	const [editing, setEditing] = useState(false);
	const editorRef = useRef<HTMLTextAreaElement | null>(null);
	const updateNode = useWorkflowStore((s) => s.updateNode);
	const config = getNodeConfig(data);
	const content = stringValue(config.content, "双击编辑便签");
	const color = stringValue(config.color, DEFAULT_COLOR);
	const width = numberConfig(config.width, 220);
	const height = numberConfig(config.height, 130);

	const updateConfig = (patch: Record<string, unknown>) => {
		updateNode(id, {
			data: {
				kind: data.kind,
				title: data.title,
				config: {
					...config,
					...patch,
				},
			},
		});
	};

	const className = [
		"wf-note-node",
		selected ? "wf-note-node-selected" : "",
		dragging || data.isDragging ? "wf-note-node-dragging" : "",
	]
		.filter(Boolean)
		.join(" ");

	useEffect(() => {
		if (editing) {
			editorRef.current?.focus();
		}
	}, [editing]);

	return (
		<div
			className={className}
			style={{ backgroundColor: color, width, minHeight: height }}
			role="note"
			aria-label="便签节点"
			onDoubleClick={() => setEditing(true)}
		>
			{editing ? (
				<textarea
					ref={editorRef}
					className="wf-note-editor"
					value={content}
					onChange={(event) => updateConfig({ content: event.target.value })}
					onBlur={() => setEditing(false)}
				/>
			) : (
				<div className="wf-note-content">{content}</div>
			)}
		</div>
	);
}
