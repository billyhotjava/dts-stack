import { BaseNode } from "../_base/BaseNode";
import { getNodeConfig, getWorkflowBlock, stringValue, type WorkflowNodeProps } from "../utils";

function countLines(code: string): number {
	if (!code) return 0;
	return code.split("\n").length;
}

export function TransformNode({ id, data, selected, dragging }: WorkflowNodeProps) {
	const block = getWorkflowBlock("transform");
	const config = getNodeConfig(data);
	const language = stringValue(config.language, "sql");
	const code = stringValue(config.code);
	const hasExternalDeps = Boolean(config.hasExternalDeps || data.hasExternalDeps);

	return (
		<BaseNode nodeId={id} block={block} data={data} selected={selected} dragging={dragging}>
			<div className="wf-node-summary">
				<span className="wf-node-tag">{language.toUpperCase()}</span>
				<span className="wf-node-muted">{countLines(code)} 行</span>
				{hasExternalDeps ? <span className="wf-node-tag wf-node-tag-warn">含外部依赖</span> : null}
			</div>
		</BaseNode>
	);
}
