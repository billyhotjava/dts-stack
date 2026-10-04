import { BaseNode } from "../_base/BaseNode";
import { getNodeConfig, getWorkflowBlock, numberValue, stringValue, type WorkflowNodeProps } from "../utils";

function childCount(value: unknown): number {
	return Array.isArray(value) ? value.length : 0;
}

export function IterationNode({ id, data, selected, dragging }: WorkflowNodeProps) {
	const block = getWorkflowBlock("iteration");
	const config = getNodeConfig(data);
	const inputArray = stringValue(config.inputArray, "$.tables");
	const itemAlias = stringValue(config.itemAlias, "item");
	const maxParallel = numberValue(config.maxParallel);
	const parallel = Boolean(config.parallel);

	return (
		<BaseNode nodeId={id} block={block} data={data} selected={selected} dragging={dragging}>
			<div className="wf-node-summary">
				<span className="wf-node-summary-block">
					迭代 <code>{inputArray}</code>
				</span>
				<span className="wf-node-muted">
					别名 {itemAlias} · {parallel ? `并发 ${maxParallel ?? 1}` : "串行"} · {childCount(config.children)} 个子节点
				</span>
			</div>
		</BaseNode>
	);
}
