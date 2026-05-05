import type { ReactNode } from "react";
import { Icon } from "@/components/icon";
import type { WorkflowNodeData, WorkflowNodeKind } from "../store/types";

/**
 * BlockDef — 节点库里每一个可拖出的"积木"。
 * 单一数据源（DRY）：BlockSelectorPanel + BlockSelectorPopover 共用。
 * 不抄 Dify 的 LLM/Tool 等领域无关节点；F5 P0 再接入 iteration/loop。
 */
export type BlockCategory = "basic" | "source" | "transform" | "validate" | "sink" | "advanced";

export interface BlockDef {
	kind: WorkflowNodeKind;
	label: string;
	category: BlockCategory;
	icon: ReactNode;
	color: string;
	description: string;
	defaultData: Omit<WorkflowNodeData, "kind" | "title">;
}

export const CATEGORY_LABEL: Record<BlockCategory, string> = {
	basic: "基础",
	source: "数据源",
	transform: "转换",
	validate: "校验",
	sink: "写入",
	advanced: "高级",
};

export const CATEGORY_ORDER: ReadonlyArray<BlockCategory> = [
	"basic",
	"source",
	"transform",
	"validate",
	"sink",
	"advanced",
];

const iconStyle = { width: 18, height: 18 };

export const BLOCKS: ReadonlyArray<BlockDef> = [
	{
		kind: "start",
		category: "basic",
		label: "开始",
		color: "#10b981",
		icon: <Icon icon="lucide:play-circle" style={iconStyle} aria-hidden="true" />,
		description: "工作流入口节点（手动 / 定时 / Webhook）",
		defaultData: { config: { trigger: "manual" } },
	},
	{
		kind: "end",
		category: "basic",
		label: "结束",
		color: "#475569",
		icon: <Icon icon="lucide:flag" style={iconStyle} aria-hidden="true" />,
		description: "工作流出口节点",
		defaultData: { config: {} },
	},
	{
		kind: "source",
		category: "source",
		label: "数据源",
		color: "#3b82f6",
		icon: <Icon icon="lucide:database" style={iconStyle} aria-hidden="true" />,
		description: "从 Catalog 拉取数据集",
		defaultData: { config: { datasetId: "" } },
	},
	{
		kind: "transform",
		category: "transform",
		label: "转换",
		color: "#a855f7",
		icon: <Icon icon="lucide:wand-2" style={iconStyle} aria-hidden="true" />,
		description: "SQL / 脚本数据转换",
		defaultData: { config: { language: "sql", code: "" } },
	},
	{
		kind: "validate",
		category: "validate",
		label: "校验",
		color: "#f59e0b",
		icon: <Icon icon="lucide:shield-check" style={iconStyle} aria-hidden="true" />,
		description: "数据质量校验规则",
		defaultData: { config: { rules: [] } },
	},
	{
		kind: "sink",
		category: "sink",
		label: "写入",
		color: "#ef4444",
		icon: <Icon icon="lucide:save" style={iconStyle} aria-hidden="true" />,
		description: "写入目标数据源（append / overwrite / merge）",
		defaultData: { config: { mode: "append" } },
	},
	{
		kind: "iteration",
		category: "advanced",
		label: "迭代",
		color: "#0f766e",
		icon: <Icon icon="lucide:repeat-2" style={iconStyle} aria-hidden="true" />,
		description: "对数组逐项执行一段子流程",
		defaultData: {
			config: {
				inputArray: "$.tables",
				itemAlias: "item",
				parallel: false,
				maxParallel: 1,
				children: [],
				childEdges: [],
			},
		},
	},
	{
		kind: "loop",
		category: "advanced",
		label: "循环",
		color: "#7c3aed",
		icon: <Icon icon="lucide:refresh-cw" style={iconStyle} aria-hidden="true" />,
		description: "重复执行子流程直到退出条件成立",
		defaultData: {
			config: {
				exitCondition: "!$.hasMore",
				maxIterations: 1000,
				iterationDelay: 0,
				retryOnError: false,
				children: [],
				childEdges: [],
			},
		},
	},
	{
		kind: "note",
		category: "advanced",
		label: "便签",
		color: "#f59e0b",
		icon: <Icon icon="lucide:sticky-note" style={iconStyle} aria-hidden="true" />,
		description: "画布标注，不参与执行",
		defaultData: { config: { content: "双击编辑便签", color: "#fef3c7", width: 220, height: 130 } },
	},
];

/** F5 P0 节点集；T01/T02 暂不暴露，留给子流程实现时接入 */
export const ADVANCED_BLOCKS: ReadonlyArray<BlockDef> = [];

export interface FilterOptions {
	keyword?: string;
}

export function filterBlocks(blocks: ReadonlyArray<BlockDef>, opts: FilterOptions): BlockDef[] {
	const kw = (opts.keyword ?? "").trim().toLowerCase();
	if (!kw) return [...blocks];
	return blocks.filter((b) => {
		return b.label.toLowerCase().includes(kw) || b.description.toLowerCase().includes(kw) || b.kind.includes(kw);
	});
}

export function groupByCategory(blocks: ReadonlyArray<BlockDef>): Record<BlockCategory, BlockDef[]> {
	const initial: Record<BlockCategory, BlockDef[]> = {
		basic: [],
		source: [],
		transform: [],
		validate: [],
		sink: [],
		advanced: [],
	};
	for (const block of blocks) {
		initial[block.category].push(block);
	}
	return initial;
}

export const BLOCK_DRAG_MIME = "application/x-workflow-block";

export function serializeBlockForDrag(block: BlockDef): string {
	return JSON.stringify({ kind: block.kind, label: block.label, color: block.color, defaultData: block.defaultData });
}

export interface DraggedBlockPayload {
	kind: WorkflowNodeKind;
	label: string;
	color: string;
	defaultData: Omit<WorkflowNodeData, "kind" | "title">;
}

export function parseDraggedBlock(raw: string | null | undefined): DraggedBlockPayload | null {
	if (!raw) return null;
	try {
		const parsed = JSON.parse(raw) as DraggedBlockPayload;
		if (!parsed || typeof parsed.kind !== "string" || typeof parsed.label !== "string") {
			return null;
		}
		return parsed;
	} catch {
		return null;
	}
}
