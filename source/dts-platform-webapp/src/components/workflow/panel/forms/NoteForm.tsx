import type { NodeFormProps } from "./types";
import { useNodeForm } from "./use-node-form";

const NOTE_COLORS = ["#fef3c7", "#dbeafe", "#fce7f3", "#dcfce7", "#f3e8ff"];

function numberValue(value: unknown, fallback: number): string {
	return typeof value === "number" && Number.isFinite(value) ? String(value) : String(fallback);
}

function boundedNumber(value: string, fallback: number, min: number, max: number): number {
	const parsed = Number(value);
	if (!Number.isFinite(parsed)) return fallback;
	return Math.max(min, Math.min(max, parsed));
}

export function NoteForm({ nodeId }: NodeFormProps) {
	const { config, updateConfig } = useNodeForm(nodeId);
	return (
		<>
			<label className="workflow-panel-field">
				<span>便签内容</span>
				<textarea
					value={String(config.content ?? "")}
					onChange={(event) => updateConfig({ content: event.target.value })}
					rows={6}
				/>
			</label>
			<label className="workflow-panel-field">
				<span>颜色</span>
				<select
					value={String(config.color ?? NOTE_COLORS[0])}
					onChange={(event) => updateConfig({ color: event.target.value })}
				>
					{NOTE_COLORS.map((color) => (
						<option key={color} value={color}>
							{color}
						</option>
					))}
				</select>
			</label>
			<label className="workflow-panel-field">
				<span>宽度</span>
				<input
					type="number"
					min="160"
					max="520"
					value={numberValue(config.width, 220)}
					onChange={(event) => updateConfig({ width: boundedNumber(event.target.value, 220, 160, 520) })}
				/>
			</label>
			<label className="workflow-panel-field">
				<span>高度</span>
				<input
					type="number"
					min="100"
					max="360"
					value={numberValue(config.height, 130)}
					onChange={(event) => updateConfig({ height: boundedNumber(event.target.value, 130, 100, 360) })}
				/>
			</label>
		</>
	);
}
