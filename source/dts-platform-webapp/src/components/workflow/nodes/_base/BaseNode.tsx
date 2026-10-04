import { NodeHandles } from "./NodeHandles";
import { NodeHeader } from "./NodeHeader";
import { PlusHandle } from "./PlusHandle";
import type { BaseNodeProps, HandleDef, WorkflowNodeStatus } from "./types";

const DEFAULT_INPUTS: HandleDef[] = [{ id: "in" }];
const DEFAULT_OUTPUTS: HandleDef[] = [{ id: "out" }];

function asString(value: unknown): string | undefined {
	return typeof value === "string" ? value : undefined;
}

function resolveStatus(data: Record<string, unknown> | undefined): WorkflowNodeStatus {
	const status = asString(data?.status);
	if (status === "running" || status === "success" || status === "error") return status;
	return data?.error ? "error" : "idle";
}

export function BaseNode({
	nodeId,
	block,
	selected = false,
	dragging = false,
	data,
	inputs = DEFAULT_INPUTS,
	outputs = DEFAULT_OUTPUTS,
	children,
}: BaseNodeProps) {
	const hasError = Boolean(data?.error);
	const className = [
		"wf-node",
		`wf-node-${block.category}`,
		selected ? "wf-node-selected" : "",
		dragging || data?.isDragging ? "wf-node-dragging" : "",
		hasError ? "wf-node-error" : "",
	]
		.filter(Boolean)
		.join(" ");

	return (
		// biome-ignore lint/a11y/useSemanticElements: React Flow nodes contain handles and nested controls; a native button would be invalid here.
		<div
			className={className}
			role="button"
			tabIndex={0}
			aria-label={`${block.label}节点：${asString(data?.title) ?? block.description}`}
			data-node-id={nodeId}
			data-node-kind={block.kind}
		>
			<NodeHandles inputs={inputs} outputs={outputs} />
			<NodeHeader
				block={block}
				title={asString(data?.title)}
				status={resolveStatus(data)}
				classification={asString(data?.classification)}
			/>
			<div className="wf-node-body">{children}</div>
			{hasError ? <div className="wf-node-error-message">{asString(data?.error) ?? "节点配置异常"}</div> : null}
			{outputs.length > 0 ? <PlusHandle nodeId={nodeId} outputId={outputs[0].id} /> : null}
		</div>
	);
}
