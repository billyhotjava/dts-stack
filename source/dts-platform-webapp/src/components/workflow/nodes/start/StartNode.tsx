import { BaseNode } from "../_base/BaseNode";
import { getNodeConfig, getWorkflowBlock, stringValue, type WorkflowNodeProps } from "../utils";

const TRIGGER_LABEL: Record<string, string> = {
	manual: "手动",
	cron: "Cron 定时",
	event: "事件触发",
};

export function StartNode({ id, data, selected, dragging }: WorkflowNodeProps) {
	const block = getWorkflowBlock("start");
	const config = getNodeConfig(data);
	const trigger = stringValue(config.trigger, "manual");

	return (
		<BaseNode
			nodeId={id}
			block={block}
			data={data}
			selected={selected}
			dragging={dragging}
			inputs={[]}
			outputs={[{ id: "out" }]}
		>
			<div className="wf-node-summary">
				<span className="wf-node-muted">触发方式</span>
				<span className="wf-node-tag">{TRIGGER_LABEL[trigger] ?? trigger}</span>
			</div>
		</BaseNode>
	);
}
