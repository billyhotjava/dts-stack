import { useReactFlow } from "@xyflow/react";
import { useEffect, useRef } from "react";
import { type ClipboardPayload, copySelection, deleteSelection, pasteClipboard, selectAll } from "../actions";
import type { WorkflowEdge, WorkflowNode } from "../store/types";
import { useWorkflowStore } from "../store/workflow-store";
import { isCommandKey, isEditableTarget } from "./platform";

export interface UseWorkflowShortcutsOptions {
	enabled?: boolean;
	onSave?: () => void;
}

export function useWorkflowShortcuts(options: UseWorkflowShortcutsOptions = {}) {
	const enabled = options.enabled ?? true;
	const onSaveRef = useRef(options.onSave);
	const clipboardRef = useRef<ClipboardPayload | null>(null);
	const { fitView, zoomIn, zoomOut } = useReactFlow<WorkflowNode, WorkflowEdge>();

	useEffect(() => {
		onSaveRef.current = options.onSave;
	}, [options.onSave]);

	useEffect(() => {
		if (!enabled) return;
		const onKeyDown = (event: KeyboardEvent) => {
			if (isEditableTarget(event.target)) return;
			const command = isCommandKey(event);
			const key = event.key.toLowerCase();

			if (command && key === "c") {
				clipboardRef.current = copySelection(useWorkflowStore.getState());
				event.preventDefault();
				return;
			}
			if (command && key === "v") {
				pasteClipboard(clipboardRef.current);
				event.preventDefault();
				return;
			}
			if (command && key === "x") {
				clipboardRef.current = copySelection(useWorkflowStore.getState());
				deleteSelection();
				event.preventDefault();
				return;
			}
			if (key === "delete" || key === "backspace") {
				deleteSelection();
				event.preventDefault();
				return;
			}
			if (command && key === "a") {
				selectAll();
				event.preventDefault();
				return;
			}
			if (command && key === "z" && !event.shiftKey) {
				useWorkflowStore.getState().undo();
				event.preventDefault();
				return;
			}
			if (command && key === "z" && event.shiftKey) {
				useWorkflowStore.getState().redo();
				event.preventDefault();
				return;
			}
			if (command && key === "s") {
				onSaveRef.current?.();
				event.preventDefault();
				return;
			}
			if (command && key === "0") {
				void fitView();
				event.preventDefault();
				return;
			}
			if (command && (key === "=" || key === "+")) {
				void zoomIn();
				event.preventDefault();
				return;
			}
			if (command && key === "-") {
				void zoomOut();
				event.preventDefault();
			}
		};

		window.addEventListener("keydown", onKeyDown);
		return () => window.removeEventListener("keydown", onKeyDown);
	}, [enabled, fitView, zoomIn, zoomOut]);
}
