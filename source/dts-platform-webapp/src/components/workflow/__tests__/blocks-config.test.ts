// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import {
	BLOCKS,
	CATEGORY_ORDER,
	filterBlocks,
	groupByCategory,
	parseDraggedBlock,
	serializeBlockForDrag,
} from "../block-selector/blocks.config";
import { createWorkflowNodeFromBlock, resetWorkflowNodeFactoryForTest } from "../block-selector/create-node";

function requirePayload<T>(payload: T | null): T {
	if (!payload) {
		throw new Error("expected drag payload to parse");
	}
	return payload;
}

describe("blocks.config — filterBlocks", () => {
	it("returns a copy when keyword empty", () => {
		const result = filterBlocks(BLOCKS, { keyword: "" });
		expect(result).toHaveLength(BLOCKS.length);
		expect(result).not.toBe(BLOCKS);
	});

	it("matches by label (case-insensitive)", () => {
		const result = filterBlocks(BLOCKS, { keyword: "数据" });
		expect(result.some((b) => b.kind === "source")).toBe(true);
		expect(result.every((b) => b.label.includes("数据") || b.description.includes("数据") || b.kind === "source")).toBe(
			true,
		);
	});

	it("matches by kind", () => {
		const result = filterBlocks(BLOCKS, { keyword: "transform" });
		expect(result.map((b) => b.kind)).toContain("transform");
	});

	it("returns empty array when no match", () => {
		const result = filterBlocks(BLOCKS, { keyword: "doesnotexist" });
		expect(result).toEqual([]);
	});

	it("trims whitespace and ignores case", () => {
		const result = filterBlocks(BLOCKS, { keyword: "  TRANSFORM  " });
		expect(result.some((b) => b.kind === "transform")).toBe(true);
		expect(result.length).toBe(1);
	});
});

describe("blocks.config — groupByCategory", () => {
	it("buckets blocks into 6 fixed categories", () => {
		const grouped = groupByCategory(BLOCKS);
		for (const cat of CATEGORY_ORDER) {
			expect(grouped[cat]).toBeDefined();
		}
		expect(grouped.basic.length).toBeGreaterThanOrEqual(2); // start + end
		expect(grouped.source.length).toBe(1);
		expect(grouped.transform.length).toBe(1);
		expect(grouped.validate.length).toBe(1);
		expect(grouped.sink.length).toBe(1);
		expect(grouped.advanced.map((block) => block.kind)).toEqual(["iteration", "loop", "note"]);
	});

	it("preserves source order within a category", () => {
		const grouped = groupByCategory(BLOCKS);
		expect(grouped.basic[0].kind).toBe("start");
		expect(grouped.basic[1].kind).toBe("end");
	});
});

describe("blocks.config — drag serialization round-trip", () => {
	it("serializes a block to JSON containing kind/label/color/defaultData", () => {
		const block = BLOCKS[0];
		const raw = serializeBlockForDrag(block);
		const parsed = JSON.parse(raw);
		expect(parsed.kind).toBe(block.kind);
		expect(parsed.label).toBe(block.label);
		expect(parsed.color).toBe(block.color);
		expect(parsed.defaultData).toEqual(block.defaultData);
	});

	it("parseDraggedBlock returns null for invalid input", () => {
		expect(parseDraggedBlock(null)).toBeNull();
		expect(parseDraggedBlock("")).toBeNull();
		expect(parseDraggedBlock("not json")).toBeNull();
		expect(parseDraggedBlock(JSON.stringify({ kind: 123 }))).toBeNull();
	});

	it("round-trip yields equivalent payload", () => {
		const block = BLOCKS[2];
		const restored = parseDraggedBlock(serializeBlockForDrag(block));
		expect(restored?.kind).toBe(block.kind);
		expect(restored?.defaultData).toEqual(block.defaultData);
	});
});

describe("createWorkflowNodeFromBlock", () => {
	it("creates a workflow node from a dragged block payload", () => {
		resetWorkflowNodeFactoryForTest();
		const block = BLOCKS[2];
		const payload = requirePayload(parseDraggedBlock(serializeBlockForDrag(block)));

		const node = createWorkflowNodeFromBlock(payload, { x: 120, y: 80 });

		expect(node.id).toMatch(/^node-source-/);
		expect(node.type).toBe("source");
		expect(node.position).toEqual({ x: 120, y: 80 });
		expect(node.data.kind).toBe("source");
		expect(node.data.title).toBe("数据源");
		expect(node.data.config).toEqual({ datasetId: "" });
	});

	it("generates stable unique ids within one session", () => {
		resetWorkflowNodeFactoryForTest();
		const payload = requirePayload(parseDraggedBlock(serializeBlockForDrag(BLOCKS[0])));
		const first = createWorkflowNodeFromBlock(payload, { x: 0, y: 0 });
		const second = createWorkflowNodeFromBlock(payload, { x: 0, y: 0 });

		expect(first.id).not.toBe(second.id);
		expect(first.id.endsWith("-1")).toBe(true);
		expect(second.id.endsWith("-2")).toBe(true);
	});
});
