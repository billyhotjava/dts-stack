import { Select } from "antd";

export type TimeRange = "MONTH" | "QUARTER" | "YEAR";

export const TIME_RANGE_OPTIONS: ReadonlyArray<{ value: TimeRange; label: string }> = [
	{ value: "MONTH", label: "本月" },
	{ value: "QUARTER", label: "本季" },
	{ value: "YEAR", label: "本年" },
];

export function timeRangeLabel(range: TimeRange): string {
	return TIME_RANGE_OPTIONS.find((o) => o.value === range)?.label ?? "本期";
}

export interface TimeRangeSelectProps {
	value: TimeRange;
	onChange: (value: TimeRange) => void;
}

export function TimeRangeSelect({ value, onChange }: TimeRangeSelectProps) {
	return (
		<Select<TimeRange>
			value={value}
			onChange={onChange}
			options={TIME_RANGE_OPTIONS.map((opt) => ({ value: opt.value, label: opt.label }))}
			style={{ minWidth: 120 }}
		/>
	);
}

export default TimeRangeSelect;
