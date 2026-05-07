import type { NodeFormProps } from "./types";
import { useNodeForm } from "./use-node-form";

export function StartForm({ nodeId }: NodeFormProps) {
	const { config, updateConfig } = useNodeForm(nodeId);
	return (
		<label className="workflow-panel-field">
			<span>触发方式</span>
			<select
				value={String(config.trigger ?? "manual")}
				onChange={(event) => updateConfig({ trigger: event.target.value })}
			>
				<option value="manual">手动</option>
				<option value="cron">Cron 定时</option>
				<option value="event">事件触发</option>
			</select>
		</label>
	);
}
