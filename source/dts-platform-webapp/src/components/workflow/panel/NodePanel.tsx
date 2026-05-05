import { useEffect, useState } from "react";
import { BLOCKS } from "../block-selector";
import { useWorkflowStore } from "../store/workflow-store";
import { NODE_FORMS } from "./forms";
import { PanelHeader } from "./PanelHeader";
import "./styles.css";

export function NodePanel() {
	const [locked, setLocked] = useState(false);
	const selectedNodeId = useWorkflowStore((s) => s.selectedNodeId);
	const node = useWorkflowStore((s) => s.nodes.find((item) => item.id === selectedNodeId));
	const panelOpen = useWorkflowStore((s) => s.panelOpen);
	const setPanelOpen = useWorkflowStore((s) => s.setPanelOpen);
	const setSelectedNodeId = useWorkflowStore((s) => s.setSelectedNodeId);
	const open = panelOpen && Boolean(node);

	useEffect(() => {
		if (!open) return;
		const handleKeyDown = (event: KeyboardEvent) => {
			if (event.key === "Escape" && !locked) {
				setPanelOpen(false);
				setSelectedNodeId(null);
			}
		};
		window.addEventListener("keydown", handleKeyDown);
		return () => window.removeEventListener("keydown", handleKeyDown);
	}, [locked, open, setPanelOpen, setSelectedNodeId]);

	if (!open || !node) return null;

	const block = BLOCKS.find((item) => item.kind === node.data.kind);
	const FormComponent = NODE_FORMS[node.data.kind];
	if (!block || !FormComponent) return null;

	const close = () => {
		if (locked) return;
		setPanelOpen(false);
		setSelectedNodeId(null);
	};

	return (
		<aside className="workflow-panel" aria-label="节点配置面板">
			<PanelHeader block={block} locked={locked} onLockToggle={() => setLocked((prev) => !prev)} onClose={close} />
			<div className="workflow-panel-body">
				<FormComponent nodeId={node.id} />
			</div>
		</aside>
	);
}
