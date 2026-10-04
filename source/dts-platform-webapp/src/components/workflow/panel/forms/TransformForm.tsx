import type { NodeFormProps } from "./types";
import { useNodeForm } from "./use-node-form";

export function TransformForm({ nodeId }: NodeFormProps) {
	const { config, updateConfig } = useNodeForm(nodeId);
	return (
		<>
			<label className="workflow-panel-field">
				<span>转换语言</span>
				<select
					value={String(config.language ?? "sql")}
					onChange={(event) => updateConfig({ language: event.target.value })}
				>
					<option value="sql">SQL</option>
					<option value="python">Python</option>
					<option value="spark">Spark</option>
				</select>
			</label>
			<label className="workflow-panel-field">
				<span>转换脚本</span>
				<textarea
					value={String(config.code ?? "")}
					onChange={(event) => updateConfig({ code: event.target.value })}
					rows={8}
				/>
			</label>
			<label className="workflow-panel-checkbox">
				<input
					type="checkbox"
					checked={Boolean(config.hasExternalDeps)}
					onChange={(event) => updateConfig({ hasExternalDeps: event.target.checked })}
				/>
				<span>包含外部依赖</span>
			</label>
		</>
	);
}
