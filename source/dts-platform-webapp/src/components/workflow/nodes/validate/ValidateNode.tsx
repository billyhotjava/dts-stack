import { BaseNode } from "../_base/BaseNode";
import { getNodeConfig, getWorkflowBlock, stringValue, type WorkflowNodeProps } from "../utils";

const RULE_TYPE_LABEL: Record<string, string> = {
	complete: "完整性",
	range: "范围",
	regex: "正则",
	custom: "自定义",
};

interface ValidateRule {
	type?: string;
}

export function summarizeRuleTypes(rules: ValidateRule[]): string {
	if (rules.length === 0) return "未配置规则";
	const counts: Record<string, number> = {};
	for (const rule of rules) {
		const type = stringValue(rule.type, "custom");
		counts[type] = (counts[type] ?? 0) + 1;
	}
	return Object.keys(counts)
		.map((type) => `${RULE_TYPE_LABEL[type] ?? type}×${counts[type]}`)
		.join(" · ");
}

export function ValidateNode({ id, data, selected, dragging }: WorkflowNodeProps) {
	const block = getWorkflowBlock("validate");
	const config = getNodeConfig(data);
	const rules = Array.isArray(config.rules) ? (config.rules as ValidateRule[]) : [];

	return (
		<BaseNode
			nodeId={id}
			block={block}
			data={data}
			selected={selected}
			dragging={dragging}
			outputs={[
				{ id: "pass", label: "通过", color: "#10b981" },
				{ id: "fail", label: "失败", color: "#ef4444" },
			]}
		>
			<div className="wf-node-summary">
				<span>{rules.length} 条规则</span>
				<span className="wf-node-muted">{summarizeRuleTypes(rules)}</span>
			</div>
		</BaseNode>
	);
}
