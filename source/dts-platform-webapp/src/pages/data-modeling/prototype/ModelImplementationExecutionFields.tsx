import type { ModelImplementationCapabilities } from "@/features/modeling/contracts/modelImplementationContract";
import { materializationLabel } from "@/utils/customerDisplayLabels";
import { ModelPartitionFieldSelector } from "./ModelPartitionFieldSelector";
import type { ModelSpecDraft } from "./services/modelWorkbenchService";

type Props = {
	capabilities: ModelImplementationCapabilities;
	draft: ModelSpecDraft;
	dimensionMode?: boolean;
	partitionError?: string;
	onChange: (patch: Partial<ModelSpecDraft>) => void;
};

const combinations = (capabilities: ModelImplementationCapabilities) =>
	capabilities.loadStrategies.flatMap((loadStrategy) =>
		(capabilities.materializationsByLoadStrategy[loadStrategy] || []).map((materialization) => ({
			loadStrategy,
			materialization,
		})),
	);

export function ModelImplementationExecutionFields({
	capabilities,
	draft,
	dimensionMode = false,
	partitionError,
	onChange,
}: Props) {
	const availableCombinations = combinations(capabilities);
	const supported = availableCombinations.some(
		(item) => item.loadStrategy === draft.loadStrategy && item.materialization === draft.materialization,
	);
	if (dimensionMode) {
		const currentValue = `${draft.loadStrategy}:${draft.materialization}`;
		return (
			<label>
				<span>存储策略</span>
				<select
					aria-label="存储策略"
					onChange={(event) => {
						const selected = availableCombinations.find(
							(item) => `${item.loadStrategy}:${item.materialization}` === event.target.value,
						);
						if (selected) onChange(selected);
					}}
					value={currentValue}
				>
					{!supported ? (
						<option value={currentValue}>
							已保存但不可执行：{draft.loadStrategy || "未设置"} / {draft.materialization}
						</option>
					) : null}
					{availableCombinations.map((item) => (
						<option
							key={`${item.loadStrategy}:${item.materialization}`}
							value={`${item.loadStrategy}:${item.materialization}`}
						>
							{materializationLabel(item.materialization)} · {item.loadStrategy === "FULL" ? "全量" : "增量"}
						</option>
					))}
				</select>
				{!supported ? <small role="alert">当前执行目标不支持已保存策略，请明确选择可用策略后再执行。</small> : null}
			</label>
		);
	}

	const materializations = capabilities.materializationsByLoadStrategy[draft.loadStrategy] || [];
	return (
		<>
			<label>
				<span>存储方式</span>
				<select onChange={(event) => onChange({ materialization: event.target.value })} value={draft.materialization}>
					{!materializations.includes(draft.materialization) ? (
						<option value={draft.materialization}>已保存：{draft.materialization || "未设置"}</option>
					) : null}
					{materializations.map((materialization) => (
						<option key={materialization} value={materialization}>
							{materializationLabel(materialization)}
						</option>
					))}
				</select>
			</label>
			<label>
				<span>加载策略</span>
				<select
					onChange={(event) => {
						const loadStrategy = event.target.value as ModelSpecDraft["loadStrategy"];
						const allowed = capabilities.materializationsByLoadStrategy[loadStrategy] || [];
						const materialization = allowed.includes(draft.materialization) ? draft.materialization : allowed[0];
						if (!materialization) return;
						onChange({
							loadStrategy,
							materialization,
						});
					}}
					value={draft.loadStrategy}
				>
					{!capabilities.loadStrategies.includes(draft.loadStrategy) ? (
						<option value={draft.loadStrategy}>已保存但不可执行：{draft.loadStrategy || "未设置"}</option>
					) : null}
					{capabilities.loadStrategies.map((loadStrategy) => (
						<option key={loadStrategy} value={loadStrategy}>
							{loadStrategy === "FULL" ? "全量" : loadStrategy === "INCREMENTAL" ? "增量" : "快照"}
						</option>
					))}
				</select>
			</label>
			{!supported ? <p role="alert">当前执行目标不支持已保存的加载与构建组合，请重新选择。</p> : null}
			{capabilities.partitionFieldsSupported ? (
				<ModelPartitionFieldSelector
					error={partitionError}
					fields={draft.fields}
					onChange={(partitionFields) => onChange({ partitionFields })}
					value={draft.partitionFields}
				/>
			) : (
				<label className="dmx-workbench-editor__wide-field">
					<span>分区字段</span>
					<small>当前 {capabilities.adapter} 执行目标不支持分区配置，无需填写。</small>
					{draft.partitionFields ? (
						<>
							<small role="alert">已保存分区：{draft.partitionFields}。执行前请清除。</small>
							<button type="button" onClick={() => onChange({ partitionFields: "" })}>
								清空分区配置
							</button>
						</>
					) : null}
				</label>
			)}
		</>
	);
}
