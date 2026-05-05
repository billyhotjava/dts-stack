import { BaseNode } from "../_base/BaseNode";
import { getNodeConfig, getWorkflowBlock, numberValue, stringValue, type WorkflowNodeProps } from "../utils";

function childCount(value: unknown): number {
	return Array.isArray(value) ? value.length : 0;
}

export function LoopNode({ id, data, selected, dragging }: WorkflowNodeProps) {
	const block = getWorkflowBlock("loop");
	const config = getNodeConfig(data);
	const exitCondition = stringValue(config.exitCondition, "!$.hasMore");
	const maxIterations = numberValue(config.maxIterations) ?? 1000;
	const delay = numberValue(config.iterationDelay) ?? 0;

	return (
		<BaseNode nodeId={id} block={block} data={data} selected={selected} dragging={dragging}>
			<div className="wf-node-summary">
				<span className="wf-node-summary-block">
					直到 <code>{exitCondition}</code>
				</span>
				<span className="wf-node-muted">
					最多 {maxIterations.toLocaleString()} 轮 · 间隔 {delay}ms · {childCount(config.children)} 个子节点
				</span>
			</div>
		</BaseNode>
	);
}
