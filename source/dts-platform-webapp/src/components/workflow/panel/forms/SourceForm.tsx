import type { NodeFormProps } from "./types";
import { useNodeForm } from "./use-node-form";

export function SourceForm({ nodeId }: NodeFormProps) {
	const { config, updateConfig } = useNodeForm(nodeId);
	return (
		<>
			<label className="workflow-panel-field">
				<span>数据集名称</span>
				<input
					value={String(config.datasetName ?? "")}
					onChange={(event) => updateConfig({ datasetName: event.target.value })}
					placeholder="选择或输入数据集"
				/>
			</label>
			<label className="workflow-panel-field">
				<span>估算行数</span>
				<input
					type="number"
					value={String(config.rowCount ?? "")}
					onChange={(event) => updateConfig({ rowCount: event.target.value ? Number(event.target.value) : undefined })}
					placeholder="0"
				/>
			</label>
		</>
	);
}
