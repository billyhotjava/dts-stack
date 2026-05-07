import type { NodeFormProps } from "./types";
import { useNodeForm } from "./use-node-form";

export function SinkForm({ nodeId }: NodeFormProps) {
	const { config, updateConfig } = useNodeForm(nodeId);
	return (
		<>
			<label className="workflow-panel-field">
				<span>目标数据集</span>
				<input
					value={String(config.targetDatasetName ?? "")}
					onChange={(event) => updateConfig({ targetDatasetName: event.target.value })}
					placeholder="选择或输入目标数据集"
				/>
			</label>
			<label className="workflow-panel-field">
				<span>写入模式</span>
				<select
					value={String(config.mode ?? "append")}
					onChange={(event) => updateConfig({ mode: event.target.value })}
				>
					<option value="append">追加</option>
					<option value="overwrite">覆盖</option>
					<option value="upsert">增量更新</option>
				</select>
			</label>
		</>
	);
}
