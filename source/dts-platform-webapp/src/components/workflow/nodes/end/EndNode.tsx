import { BaseNode } from "../_base/BaseNode";
import { getNodeConfig, getWorkflowBlock, stringValue, type WorkflowNodeProps } from "../utils";

const STRATEGY_LABEL: Record<string, string> = {
	success: "成功通知",
	rollback: "失败回滚",
	silent: "静默结束",
};

export function EndNode({ id, data, selected, dragging }: WorkflowNodeProps) {
	const block = getWorkflowBlock("end");
	const config = getNodeConfig(data);
	const strategy = stringValue(config.strategy, "success");

	return (
		<BaseNode nodeId={id} block={block} data={data} selected={selected} dragging={dragging} outputs={[]}>
			<div className="wf-node-summary">
				<span className={`wf-node-tag wf-node-strategy-${strategy}`}>{STRATEGY_LABEL[strategy] ?? strategy}</span>
			</div>
		</BaseNode>
	);
}
