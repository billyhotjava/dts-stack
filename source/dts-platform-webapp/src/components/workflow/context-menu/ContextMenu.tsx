import { useReactFlow } from "@xyflow/react";
import { useEffect } from "react";
import {
	addNoteAt,
	alignSelection,
	type ClipboardPayload,
	copySelection,
	deleteSelection,
	pasteClipboard,
	renameSelectedNode,
	selectAll,
} from "../actions";
import type { WorkflowEdge, WorkflowNode } from "../store/types";
import { useWorkflowStore } from "../store/workflow-store";
import "./styles.css";

interface MenuItem {
	key: string;
	label: string;
	disabled?: boolean;
	onClick: () => void;
}

let menuClipboard: ClipboardPayload | null = null;

function menuPosition(x: number, y: number): { left: number; top: number } {
	if (typeof window === "undefined") return { left: x, top: y };
	return {
		left: Math.min(x, Math.max(8, window.innerWidth - 220)),
		top: Math.min(y, Math.max(8, window.innerHeight - 320)),
	};
}

function openSelectedNodePanel() {
	const state = useWorkflowStore.getState();
	if (state.selectedNodeId) {
		state.setPanelOpen(true);
	}
}

function promptRename() {
	const nodeId = useWorkflowStore.getState().selectedNodeId;
	const node = useWorkflowStore.getState().nodes.find((item) => item.id === nodeId);
	const nextTitle = window.prompt("节点名称", node?.data.title ?? "");
	if (nextTitle !== null) {
		renameSelectedNode(nextTitle);
	}
}

function menuItems(fitView: () => void): MenuItem[] {
	const state = useWorkflowStore.getState();
	const menu = state.contextMenu;
	if (!menu) return [];
	if (menu.type === "pane") {
		return [
			{
				key: "paste",
				label: "粘贴",
				disabled: !menuClipboard,
				onClick: () => pasteClipboard(menuClipboard, menu.flowPosition),
			},
			{ key: "select-all", label: "全选", disabled: state.nodes.length === 0, onClick: selectAll },
			{ key: "fit-view", label: "适配视图", onClick: fitView },
			{ key: "add-note", label: "添加便签", onClick: () => menu.flowPosition && addNoteAt(menu.flowPosition) },
		];
	}
	if (menu.type === "edge") {
		return [{ key: "delete", label: "删除连线", onClick: deleteSelection }];
	}
	if (menu.type === "multi") {
		return [
			{
				key: "copy",
				label: "复制",
				onClick: () => {
					menuClipboard = copySelection(useWorkflowStore.getState());
				},
			},
			{ key: "delete", label: "删除", onClick: deleteSelection },
			{ key: "align-left", label: "左对齐", onClick: () => alignSelection("left") },
			{ key: "align-top", label: "顶对齐", onClick: () => alignSelection("top") },
			{ key: "align-h", label: "水平居中", onClick: () => alignSelection("horizontal-center") },
			{ key: "align-v", label: "垂直居中", onClick: () => alignSelection("vertical-center") },
		];
	}
	return [
		{
			key: "copy",
			label: "复制",
			onClick: () => {
				menuClipboard = copySelection(useWorkflowStore.getState());
			},
		},
		{
			key: "cut",
			label: "剪切",
			onClick: () => {
				menuClipboard = copySelection(useWorkflowStore.getState());
				deleteSelection();
			},
		},
		{ key: "delete", label: "删除", onClick: deleteSelection },
		{ key: "rename", label: "重命名", onClick: promptRename },
		{ key: "details", label: "查看详情", onClick: openSelectedNodePanel },
		{ key: "add-note", label: "添加便签", onClick: () => menu.flowPosition && addNoteAt(menu.flowPosition) },
	];
}

export function WorkflowContextMenu() {
	const menu = useWorkflowStore((state) => state.contextMenu);
	const setContextMenu = useWorkflowStore((state) => state.setContextMenu);
	const reactFlow = useReactFlow<WorkflowNode, WorkflowEdge>();

	useEffect(() => {
		if (!menu) return;
		const close = () => setContextMenu(null);
		const handleKeyDown = (event: KeyboardEvent) => {
			if (event.key === "Escape") close();
		};
		window.addEventListener("keydown", handleKeyDown);
		window.addEventListener("click", close);
		return () => {
			window.removeEventListener("keydown", handleKeyDown);
			window.removeEventListener("click", close);
		};
	}, [menu, setContextMenu]);

	if (!menu) return null;
	const position = menuPosition(menu.x, menu.y);
	return (
		<div className="workflow-context-menu" style={position} role="menu" aria-label="画布上下文菜单">
			{menuItems(() => {
				void reactFlow.fitView();
			}).map((item) => (
				<div key={item.key} role="none">
					<button
						type="button"
						role="menuitem"
						disabled={item.disabled}
						onClick={(event) => {
							event.stopPropagation();
							if (!item.disabled) {
								item.onClick();
								setContextMenu(null);
							}
						}}
					>
						{item.label}
					</button>
				</div>
			))}
		</div>
	);
}
