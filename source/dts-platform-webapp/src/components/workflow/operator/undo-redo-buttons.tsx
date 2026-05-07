import { useWorkflowStore } from "../store/workflow-store";

export function UndoRedoButtons() {
	const canUndo = useWorkflowStore((state) => state.canUndo);
	const canRedo = useWorkflowStore((state) => state.canRedo);
	const undo = useWorkflowStore((state) => state.undo);
	const redo = useWorkflowStore((state) => state.redo);

	return (
		<div className="workflow-operator__group">
			<button
				type="button"
				className="workflow-operator__btn"
				disabled={!canUndo}
				aria-disabled={!canUndo}
				aria-label="撤销"
				title="撤销"
				onClick={undo}
			>
				↶
			</button>
			<button
				type="button"
				className="workflow-operator__btn"
				disabled={!canRedo}
				aria-disabled={!canRedo}
				aria-label="重做"
				title="重做"
				onClick={redo}
			>
				↷
			</button>
		</div>
	);
}
