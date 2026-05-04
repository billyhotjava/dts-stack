import { Panel } from "@xyflow/react";
import { ZoomControls } from "./zoom-controls";
import { FitViewButton } from "./fit-view-button";
import { ScreenshotButton } from "./screenshot-button";
import { UndoRedoButtons } from "./undo-redo-buttons";

/**
 * Operator — 画布右下角工具栏。通过 ReactFlow `<Panel>` 锚定。
 * 子按钮均通过 useReactFlow() 联动；样式在 styles/operator.css。
 */
export function Operator() {
	return (
		<Panel position="bottom-right" className="workflow-operator" data-testid="workflow-operator">
			<div className="workflow-operator__bar" role="toolbar" aria-label="画布操作">
				<ZoomControls />
				<span className="workflow-operator__divider" aria-hidden="true" />
				<FitViewButton />
				<span className="workflow-operator__divider" aria-hidden="true" />
				<ScreenshotButton />
				<UndoRedoButtons />
			</div>
		</Panel>
	);
}
