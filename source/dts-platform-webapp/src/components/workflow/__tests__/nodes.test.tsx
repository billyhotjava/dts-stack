// @vitest-environment jsdom
import { ReactFlowProvider } from "@xyflow/react";
import type { ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import { BLOCK_DRAG_MIME, BLOCKS, serializeBlockForDrag } from "../block-selector";
import { BaseNode } from "../nodes";
import { EndNode } from "../nodes/end/EndNode";
import { IterationNode } from "../nodes/iteration/IterationNode";
import { LoopNode } from "../nodes/loop/LoopNode";
import { NoteNode } from "../nodes/note/NoteNode";
import { SinkNode } from "../nodes/sink/SinkNode";
import { SourceNode } from "../nodes/source/SourceNode";
import { StartNode } from "../nodes/start/StartNode";
import { TransformNode } from "../nodes/transform/TransformNode";
import { summarizeRuleTypes, ValidateNode } from "../nodes/validate/ValidateNode";
import type { WorkflowNodeData } from "../store/types";
import { resetWorkflowStoreForTest, useWorkflowStore } from "../store/workflow-store";

let root: Root | null = null;
let host: HTMLDivElement | null = null;

function render(node: ReactNode) {
	host = document.createElement("div");
	document.body.appendChild(host);
	act(() => {
		root = createRoot(host as HTMLDivElement);
		root.render(<ReactFlowProvider>{node}</ReactFlowProvider>);
	});
	return host as HTMLDivElement;
}

function setReactTextareaValue(textarea: HTMLTextAreaElement, value: string) {
	const setter = Object.getOwnPropertyDescriptor<HTMLTextAreaElement>(HTMLTextAreaElement.prototype, "value")?.set;
	act(() => {
		setter?.call(textarea, value);
		textarea.dispatchEvent(new Event("input", { bubbles: true }));
	});
}

afterEach(() => {
	if (root && host) {
		const currentRoot = root;
		act(() => currentRoot.unmount());
		document.body.removeChild(host);
	}
	root = null;
	host = null;
	resetWorkflowStoreForTest();
});

function nodeData(kind: WorkflowNodeData["kind"], config: Record<string, unknown> = {}): WorkflowNodeData {
	const block = BLOCKS.find((item) => item.kind === kind);
	return {
		kind,
		title: block?.label ?? kind,
		config,
	};
}

const basePosition = { x: 0, y: 0 };

describe("BaseNode", () => {
	it("renders selected/dragging/error states", () => {
		const block = BLOCKS[0];
		const container = render(
			<BaseNode
				nodeId="n1"
				block={block}
				selected
				dragging
				data={{ title: "开始节点", error: "配置缺失", classification: "internal" }}
				inputs={[]}
			/>,
		);
		const node = container.querySelector(".wf-node");
		expect(node?.className).toContain("wf-node-selected");
		expect(node?.className).toContain("wf-node-dragging");
		expect(node?.className).toContain("wf-node-error");
		expect(container.textContent).toContain("配置缺失");
		expect(container.textContent).toContain("内部");
	});

	it("opens block selector popover and creates a connected next node", () => {
		resetWorkflowStoreForTest();
		useWorkflowStore.getState().addNode({
			id: "n1",
			type: "start",
			position: { x: 10, y: 20 },
			data: nodeData("start"),
		});
		const block = BLOCKS[0];
		const container = render(
			<BaseNode nodeId="n1" block={block} data={nodeData("start")} inputs={[]} outputs={[{ id: "out" }]} />,
		);
		const plus = container.querySelector<HTMLButtonElement>(".wf-node-plus");
		expect(plus).not.toBeNull();
		act(() => plus?.click());
		const transform = Array.from(container.querySelectorAll<HTMLButtonElement>(".block-selector-popover__item")).find(
			(item) => item.textContent?.includes("转换"),
		);
		expect(transform).not.toBeUndefined();
		act(() => transform?.click());

		const state = useWorkflowStore.getState();
		expect(state.nodes.some((node) => node.type === "transform" && node.position.x === 290)).toBe(true);
		expect(state.edges.some((edge) => edge.source === "n1" && edge.sourceHandle === "out")).toBe(true);
	});

	it("drops a dragged block onto plus handle and auto-connects it", () => {
		resetWorkflowStoreForTest();
		useWorkflowStore.getState().addNode({
			id: "n1",
			type: "start",
			position: { x: 0, y: 0 },
			data: nodeData("start"),
		});
		const container = render(
			<BaseNode nodeId="n1" block={BLOCKS[0]} data={nodeData("start")} inputs={[]} outputs={[{ id: "out" }]} />,
		);
		const plus = container.querySelector<HTMLButtonElement>(".wf-node-plus");
		expect(plus).not.toBeNull();
		const event = new Event("drop", { bubbles: true }) as unknown as DragEvent;
		Object.defineProperty(event, "dataTransfer", {
			value: {
				getData: (type: string) => (type === BLOCK_DRAG_MIME ? serializeBlockForDrag(BLOCKS[2]) : ""),
				dropEffect: "",
			},
		});

		act(() => plus?.dispatchEvent(event));

		const state = useWorkflowStore.getState();
		expect(state.nodes.some((node) => node.type === "source")).toBe(true);
		expect(state.edges.some((edge) => edge.source === "n1" && edge.targetHandle === "in")).toBe(true);
	});
});

describe("workflow ETL nodes", () => {
	it("renders StartNode trigger summary", () => {
		const container = render(
			<StartNode
				id="start-1"
				data={nodeData("start", { trigger: "cron" })}
				selected={false}
				dragging={false}
				positionAbsolute={basePosition}
			/>,
		);
		expect(container.textContent).toContain("开始");
		expect(container.textContent).toContain("Cron 定时");
	});

	it("renders SourceNode empty and configured summaries", () => {
		const empty = render(
			<SourceNode
				id="source-empty"
				data={nodeData("source")}
				selected={false}
				dragging={false}
				positionAbsolute={basePosition}
			/>,
		);
		expect(empty.textContent).toContain("未选择数据集");
		act(() => root?.unmount());
		document.body.removeChild(host as HTMLDivElement);
		root = null;
		host = null;

		const configured = render(
			<SourceNode
				id="source-1"
				data={nodeData("source", { datasetName: "订单明细", rowCount: 1200 })}
				selected={false}
				dragging={false}
				positionAbsolute={basePosition}
			/>,
		);
		expect(configured.textContent).toContain("订单明细");
		expect(configured.textContent).toContain("1,200");
	});

	it("renders TransformNode language, line count, and dependency warning", () => {
		const container = render(
			<TransformNode
				id="transform-1"
				data={nodeData("transform", { language: "python", code: "a\nb\nc", hasExternalDeps: true })}
				selected={false}
				dragging={false}
				positionAbsolute={basePosition}
			/>,
		);
		expect(container.textContent).toContain("PYTHON");
		expect(container.textContent).toContain("3 行");
		expect(container.textContent).toContain("含外部依赖");
	});

	it("summarizes ValidateNode rules and renders two output handles", () => {
		expect(summarizeRuleTypes([{ type: "complete" }, { type: "range" }, { type: "range" }])).toBe("完整性×1 · 范围×2");
		const container = render(
			<ValidateNode
				id="validate-1"
				data={nodeData("validate", { rules: [{ type: "complete" }, { type: "regex" }] })}
				selected={false}
				dragging={false}
				positionAbsolute={basePosition}
			/>,
		);
		expect(container.textContent).toContain("2 条规则");
		expect(container.textContent).toContain("完整性×1");
		expect(container.querySelectorAll(".wf-node-handle-out")).toHaveLength(2);
	});

	it("renders SinkNode mode and EndNode strategy", () => {
		const sink = render(
			<SinkNode
				id="sink-1"
				data={nodeData("sink", { targetDatasetName: "清洗结果", mode: "overwrite" })}
				selected={false}
				dragging={false}
				positionAbsolute={basePosition}
			/>,
		);
		expect(sink.textContent).toContain("清洗结果");
		expect(sink.textContent).toContain("覆盖");
		act(() => root?.unmount());
		document.body.removeChild(host as HTMLDivElement);
		root = null;
		host = null;

		const end = render(
			<EndNode
				id="end-1"
				data={nodeData("end", { strategy: "rollback" })}
				selected={false}
				dragging={false}
				positionAbsolute={basePosition}
			/>,
		);
		expect(end.textContent).toContain("失败回滚");
	});

	it("renders NoteNode without handles and syncs inline edits", () => {
		resetWorkflowStoreForTest();
		useWorkflowStore.getState().addNode({
			id: "note-1",
			type: "note",
			position: { x: 0, y: 0 },
			data: nodeData("note", { content: "处理前确认口径", color: "#dbeafe", width: 240, height: 150 }),
		});
		const container = render(
			<NoteNode
				id="note-1"
				data={nodeData("note", { content: "处理前确认口径", color: "#dbeafe", width: 240, height: 150 })}
				selected={false}
				dragging={false}
				positionAbsolute={basePosition}
			/>,
		);
		const note = container.querySelector<HTMLElement>(".wf-note-node");
		expect(note).not.toBeNull();
		expect(note?.style.width).toBe("240px");
		expect(note?.textContent).toContain("处理前确认口径");
		expect(container.querySelector(".wf-node-handle")).toBeNull();

		act(() => note?.dispatchEvent(new MouseEvent("dblclick", { bubbles: true })));
		const editor = container.querySelector<HTMLTextAreaElement>(".wf-note-editor");
		expect(editor).not.toBeNull();
		if (!editor) throw new Error("note editor should render");
		setReactTextareaValue(editor, "已确认口径");

		const node = useWorkflowStore.getState().nodes[0];
		expect(node.data.config?.content).toBe("已确认口径");
	});

	it("renders IterationNode and LoopNode summaries", () => {
		const iteration = render(
			<IterationNode
				id="iteration-1"
				data={nodeData("iteration", {
					inputArray: "$.upstream.tables",
					itemAlias: "table",
					parallel: true,
					maxParallel: 4,
					children: [{ id: "child-source" }],
				})}
				selected={false}
				dragging={false}
				positionAbsolute={basePosition}
			/>,
		);
		expect(iteration.textContent).toContain("$.upstream.tables");
		expect(iteration.textContent).toContain("并发 4");
		expect(iteration.textContent).toContain("1 个子节点");
		act(() => root?.unmount());
		document.body.removeChild(host as HTMLDivElement);
		root = null;
		host = null;

		const loop = render(
			<LoopNode
				id="loop-1"
				data={nodeData("loop", { exitCondition: "!$.hasMore", maxIterations: 12, iterationDelay: 300 })}
				selected={false}
				dragging={false}
				positionAbsolute={basePosition}
			/>,
		);
		expect(loop.textContent).toContain("!$.hasMore");
		expect(loop.textContent).toContain("最多 12 轮");
		expect(loop.textContent).toContain("间隔 300ms");
	});
});
