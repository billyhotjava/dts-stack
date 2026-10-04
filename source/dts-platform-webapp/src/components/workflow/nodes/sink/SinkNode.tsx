import { BaseNode } from "../_base/BaseNode";
import { getNodeConfig, getWorkflowBlock, stringValue, type WorkflowNodeProps } from "../utils";

const MODE_LABEL: Record<string, string> = {
	append: "追加",
	overwrite: "覆盖",
	upsert: "增量更新",
};

export function SinkNode({ id, data, selected, dragging }: WorkflowNodeProps) {
	const block = getWorkflowBlock("sink");
	const config = getNodeConfig(data);
	const targetName = stringValue(config.targetDatasetName || data.targetDatasetName);
	const mode = stringValue(config.mode, "append");

	return (
		<BaseNode nodeId={id} block={block} data={data} selected={selected} dragging={dragging} outputs={[]}>
			<div className="wf-node-summary">
				<span className={targetName ? "wf-node-summary-block" : "wf-node-placeholder"}>
					{targetName || "未选择目标"}
				</span>
				<span className={`wf-node-tag wf-node-mode-${mode}`}>{MODE_LABEL[mode] ?? mode}</span>
			</div>
		</BaseNode>
	);
}
