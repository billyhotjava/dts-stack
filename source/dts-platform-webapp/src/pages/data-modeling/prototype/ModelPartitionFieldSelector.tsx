import type { ChangeEvent } from "react";
import type { ModelSpecField } from "@/features/modeling/contracts/modelSpecV2Contract";
import { parsePartitionFields } from "./services/modelWorkbenchService";

type ModelPartitionFieldSelectorProps = {
	fields: ModelSpecField[];
	value: string;
	error?: string;
	onChange: (value: string) => void;
};

const fieldOptionLabel = (field: ModelSpecField): string => {
	const name = field.name.trim();
	const displayName = field.displayName?.trim();
	return `${displayName || name}（${name} · ${field.dataType}）`;
};

export function ModelPartitionFieldSelector({
	fields,
	value,
	error,
	onChange,
}: ModelPartitionFieldSelectorProps) {
	const selectedNames = parsePartitionFields(value);
	const fieldsByName = new Map(
		fields.filter((field) => field.name.trim()).map((field) => [field.name.trim(), field] as const),
	);
	const availableFields = Array.from(fieldsByName.values()).filter(
		(field) => !selectedNames.includes(field.name.trim()),
	);

	const addField = (event: ChangeEvent<HTMLSelectElement>) => {
		const name = event.target.value;
		if (!name || selectedNames.includes(name)) return;
		onChange([...selectedNames, name].join(","));
	};
	const removeField = (name: string) => {
		onChange(selectedNames.filter((selectedName) => selectedName !== name).join(","));
	};

	return (
		<div className="dmx-workbench-editor__wide-field dmx-partition-field-selector">
			<label className="dmx-partition-field-selector__control">
				<span>分区字段</span>
				<select aria-label="分区字段" disabled={!availableFields.length} onChange={addField} value="">
					<option value="">
						{fieldsByName.size === 0
							? "请先在字段管理中新增字段"
							: availableFields.length === 0
								? "已选择全部字段"
								: "请选择字段（可多选）"}
					</option>
					{availableFields.map((field) => (
						<option key={field.name.trim()} value={field.name.trim()}>
							{fieldOptionLabel(field)}
						</option>
					))}
				</select>
			</label>
			<small className="dmx-partition-field-selector__hint">可留空；如需分区，请从当前模型字段中逐项选择。</small>
			{selectedNames.length ? (
				<div aria-label="已选分区字段" className="dmx-partition-field-selector__selected" role="list">
					{selectedNames.map((name) => {
						const field = fieldsByName.get(name);
						return (
							<span
								className={`dmx-partition-field-selector__tag${field ? "" : " dmx-partition-field-selector__tag--stale"}`}
								key={name}
								role="listitem"
							>
								<span>{field ? fieldOptionLabel(field) : `已失效字段（${name}）`}</span>
								<button aria-label={`移除分区字段 ${name}`} onClick={() => removeField(name)} type="button">
									×
								</button>
							</span>
						);
					})}
				</div>
			) : (
				<small className="dmx-partition-field-selector__empty">未选择分区字段</small>
			)}
			{error ? (
				<small className="dmx-workbench-editor__validation" role="alert">
					{error}
				</small>
			) : null}
		</div>
	);
}
