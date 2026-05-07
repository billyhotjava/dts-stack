import type { NodeFormProps } from "./types";
import { useNodeForm } from "./use-node-form";

export function EndForm({ nodeId }: NodeFormProps) {
	const { config, updateConfig } = useNodeForm(nodeId);
	return (
		<label className="workflow-panel-field">
			<span>结束策略</span>
			<select
				value={String(config.strategy ?? "success")}
				onChange={(event) => updateConfig({ strategy: event.target.value })}
			>
				<option value="success">成功通知</option>
				<option value="rollback">失败回滚</option>
				<option value="silent">静默结束</option>
			</select>
		</label>
	);
}
