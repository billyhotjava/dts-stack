import { BaseNode } from "../_base/BaseNode";
import { getNodeConfig, getWorkflowBlock, numberValue, stringValue, type WorkflowNodeProps } from "../utils";

export function SourceNode({ id, data, selected, dragging }: WorkflowNodeProps) {
	const block = getWorkflowBlock("source");
	const config = getNodeConfig(data);
	const datasetName = stringValue(config.datasetName || data.datasetName);
	const rowCount = numberValue(config.rowCount ?? data.rowCount);

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
				{datasetName ? (
					<>
						<span className="wf-node-summary-block">{datasetName}</span>
						<span className="wf-node-muted">约 {rowCount === null ? "?" : rowCount.toLocaleString()} 行</span>
					</>
				) : (
					<span className="wf-node-placeholder">未选择数据集</span>
				)}
			</div>
		</BaseNode>
	);
}
