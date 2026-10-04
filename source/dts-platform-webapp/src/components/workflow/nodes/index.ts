import type { NodeTypes } from "@xyflow/react";
import { EndNode } from "./end/EndNode";
import { IterationNode } from "./iteration/IterationNode";
import { LoopNode } from "./loop/LoopNode";
import { NoteNode } from "./note/NoteNode";
import { SinkNode } from "./sink/SinkNode";
import { SourceNode } from "./source/SourceNode";
import { StartNode } from "./start/StartNode";
import { TransformNode } from "./transform/TransformNode";
import { ValidateNode } from "./validate/ValidateNode";
import "./_base/styles.css";

export const workflowNodeTypes: NodeTypes = {
	start: StartNode,
	source: SourceNode,
	transform: TransformNode,
	validate: ValidateNode,
	sink: SinkNode,
	end: EndNode,
	iteration: IterationNode,
	loop: LoopNode,
	note: NoteNode,
};

export { BaseNode } from "./_base/BaseNode";
export type { BaseNodeProps, HandleDef, WorkflowNodeStatus } from "./_base/types";
export { EndNode } from "./end/EndNode";
export { IterationNode } from "./iteration/IterationNode";
export { LoopNode } from "./loop/LoopNode";
export { NoteNode } from "./note/NoteNode";
export { SinkNode } from "./sink/SinkNode";
export { SourceNode } from "./source/SourceNode";
export { StartNode } from "./start/StartNode";
export { TransformNode } from "./transform/TransformNode";
export { summarizeRuleTypes, ValidateNode } from "./validate/ValidateNode";
