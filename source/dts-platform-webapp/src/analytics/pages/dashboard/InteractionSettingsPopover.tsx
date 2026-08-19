import { Button, Checkbox, Popover, Select, Space, Typography } from "antd";
import { useState } from "react";
import type { DashboardCard } from "../../api/analyticsApi";

const { Text } = Typography;

export const CROSS_FILTER_ENABLED = "dts.cross_filter.enabled";
export const CROSS_FILTER_TARGETS = "dts.cross_filter.target_card_ids";

type Props = {
	currentDashcardId: number;
	dashcards: DashboardCard[];
	settings: Record<string, unknown>;
	onSave: (settings: Record<string, unknown>) => void;
};

export function InteractionSettingsPopover({ currentDashcardId, dashcards, settings, onSave }: Props) {
	const [open, setOpen] = useState(false);
	const [enabled, setEnabled] = useState(false);
	const [targetCardIds, setTargetCardIds] = useState<number[]>([]);

	const handleOpenChange = (nextOpen: boolean) => {
		if (nextOpen) {
			setEnabled(settings[CROSS_FILTER_ENABLED] === true);
			setTargetCardIds(
				Array.isArray(settings[CROSS_FILTER_TARGETS])
					? settings[CROSS_FILTER_TARGETS].filter((value): value is number => typeof value === "number")
					: [],
			);
		}
		setOpen(nextOpen);
	};

	const options = dashcards
		.filter((item) => item.id !== currentDashcardId)
		.map((item) => ({
			value: item.id,
			label: (item.card && typeof item.card.name === "string" && item.card.name) || `组件 ${item.id}`,
		}));

	const content = (
		<Space direction="vertical" size={10} style={{ width: 280 }}>
			<Checkbox checked={enabled} onChange={(event) => setEnabled(event.target.checked)}>
				点击图表后筛选其他组件
			</Checkbox>
			<Text type="secondary">只有配置为目标的组件会收到联动条件，来源组件不会自筛。</Text>
			<Select
				mode="multiple"
				allowClear
				value={targetCardIds}
				options={options}
				disabled={!enabled}
				placeholder="选择联动目标组件"
				onChange={setTargetCardIds}
				style={{ width: "100%" }}
			/>
			<Button
				type="primary"
				block
				disabled={enabled && targetCardIds.length === 0}
				onClick={() => {
					onSave({
						...settings,
						[CROSS_FILTER_ENABLED]: enabled,
						[CROSS_FILTER_TARGETS]: enabled ? targetCardIds : [],
					});
					setOpen(false);
				}}
			>
				保存联动配置
			</Button>
		</Space>
	);

	return (
		<Popover title="图表联动" content={content} trigger="click" open={open} onOpenChange={handleOpenChange}>
			<Button type="text" size="small">联动</Button>
		</Popover>
	);
}
