import type { NodeFormProps } from "./types";
import { useNodeForm } from "./use-node-form";

function numberPatch(value: string, fallback: number, min: number, max: number): number {
	const parsed = Number(value);
	if (!Number.isFinite(parsed)) return fallback;
	return Math.max(min, Math.min(max, parsed));
}

export function LoopForm({ nodeId }: NodeFormProps) {
	const { config, updateConfig } = useNodeForm(nodeId);
	return (
		<>
			<label className="workflow-panel-field">
				<span>退出条件</span>
				<input
					value={String(config.exitCondition ?? "!$.hasMore")}
					onChange={(event) => updateConfig({ exitCondition: event.target.value })}
				/>
			</label>
			<label className="workflow-panel-field">
				<span>最大轮次</span>
				<input
					type="number"
					min="1"
					max="100000"
					value={String(config.maxIterations ?? 1000)}
					onChange={(event) => updateConfig({ maxIterations: numberPatch(event.target.value, 1000, 1, 100000) })}
				/>
			</label>
			<label className="workflow-panel-field">
				<span>每轮间隔 ms</span>
				<input
					type="number"
					min="0"
					max="3600000"
					value={String(config.iterationDelay ?? 0)}
					onChange={(event) => updateConfig({ iterationDelay: numberPatch(event.target.value, 0, 0, 3600000) })}
				/>
			</label>
			<label className="workflow-panel-checkbox">
				<input
					type="checkbox"
					checked={Boolean(config.retryOnError)}
					onChange={(event) => updateConfig({ retryOnError: event.target.checked })}
				/>
				<span>失败后继续重试</span>
			</label>
		</>
	);
}
