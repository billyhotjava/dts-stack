import type { NodeFormProps } from "./types";
import { useNodeForm } from "./use-node-form";

function formatRules(value: unknown): string {
	return JSON.stringify(Array.isArray(value) ? value : [], null, 2);
}

export function ValidateForm({ nodeId }: NodeFormProps) {
	const { config, updateConfig } = useNodeForm(nodeId);
	return (
		<label className="workflow-panel-field">
			<span>校验规则 JSON</span>
			<textarea
				value={formatRules(config.rules)}
				onChange={(event) => {
					try {
						const parsed = JSON.parse(event.target.value);
						updateConfig({ rules: Array.isArray(parsed) ? parsed : [] });
					} catch {
						updateConfig({ rulesText: event.target.value });
					}
				}}
				rows={10}
			/>
		</label>
	);
}
