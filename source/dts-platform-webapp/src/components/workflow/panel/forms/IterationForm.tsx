import type { NodeFormProps } from "./types";
import { useNodeForm } from "./use-node-form";

function numberPatch(value: string, fallback: number, min: number, max: number): number {
	const parsed = Number(value);
	if (!Number.isFinite(parsed)) return fallback;
	return Math.max(min, Math.min(max, parsed));
}

export function IterationForm({ nodeId }: NodeFormProps) {
	const { config, updateConfig } = useNodeForm(nodeId);
	return (
		<>
			<label className="workflow-panel-field">
				<span>输入数组表达式</span>
				<input
					value={String(config.inputArray ?? "$.tables")}
					onChange={(event) => updateConfig({ inputArray: event.target.value })}
				/>
			</label>
			<label className="workflow-panel-field">
				<span>元素别名</span>
				<input
					value={String(config.itemAlias ?? "item")}
					onChange={(event) => updateConfig({ itemAlias: event.target.value })}
				/>
			</label>
			<label className="workflow-panel-checkbox">
				<input
					type="checkbox"
					checked={Boolean(config.parallel)}
					onChange={(event) => updateConfig({ parallel: event.target.checked })}
				/>
				<span>并发执行</span>
			</label>
			<label className="workflow-panel-field">
				<span>最大并发</span>
				<input
					type="number"
					min="1"
					max="100"
					value={String(config.maxParallel ?? 1)}
					onChange={(event) => updateConfig({ maxParallel: numberPatch(event.target.value, 1, 1, 100) })}
				/>
			</label>
		</>
	);
}
