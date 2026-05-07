import { useCallback } from "react";
import { useReactFlow, useViewport } from "@xyflow/react";

const ZOOM_STEP = 0.2;

export function ZoomControls() {
	const { zoomIn, zoomOut } = useReactFlow();
	const { zoom } = useViewport();
	const percent = Math.round(zoom * 100);

	const onZoomIn = useCallback(() => {
		zoomIn({ duration: 200 });
	}, [zoomIn]);

	const onZoomOut = useCallback(() => {
		zoomOut({ duration: 200 });
	}, [zoomOut]);

	return (
		<div className="workflow-operator__group" role="group" aria-label="缩放">
			<button
				type="button"
				className="workflow-operator__btn"
				onClick={onZoomOut}
				aria-label="缩小"
				title={`缩小（当前 ${percent}%）`}
			>
				−
			</button>
			<span
				className="workflow-operator__zoom-display"
				aria-live="polite"
				aria-atomic="true"
				title={`当前缩放 ${percent}%（步长 ${Math.round(ZOOM_STEP * 100)}%）`}
			>
				{percent}%
			</span>
			<button
				type="button"
				className="workflow-operator__btn"
				onClick={onZoomIn}
				aria-label="放大"
				title={`放大（当前 ${percent}%）`}
			>
				+
			</button>
		</div>
	);
}
