import React, { useState } from "react";
import { Button, Checkbox, Input, Popover } from "antd";
import { SettingOutlined } from "@ant-design/icons";
import { t, type Locale } from "../../i18n";
import type { DashboardParameter } from "./DashboardFilterBar";

export interface ParameterMapping {
	parameter_id: string;
	card_id: number;
	target: unknown;
}

export interface ParameterMappingPopoverProps {
	parameters: DashboardParameter[];
	currentMappings: ParameterMapping[];
	cardId: number;
	locale: Locale;
	onSave: (mappings: ParameterMapping[]) => void;
}

export function ParameterMappingPopover({
	parameters,
	currentMappings,
	cardId,
	locale,
	onSave,
}: ParameterMappingPopoverProps) {
	const [open, setOpen] = useState(false);
	const [draft, setDraft] = useState<Record<string, { checked: boolean; targetVar: string }>>({});

	const handleOpenChange = (visible: boolean) => {
		if (visible) {
			// Initialize draft from currentMappings
			const init: Record<string, { checked: boolean; targetVar: string }> = {};
			for (const p of parameters) {
				const existing = currentMappings.find((m) => m.parameter_id === p.id && m.card_id === cardId);
				if (existing) {
					const target = existing.target;
					let targetVar = "";
					if (Array.isArray(target) && target.length >= 2 && Array.isArray(target[1]) && target[1].length >= 2) {
						targetVar = String(target[1][1] ?? "");
					}
					init[p.id] = { checked: true, targetVar };
				} else {
					init[p.id] = { checked: false, targetVar: p.slug || p.name || p.id };
				}
			}
			setDraft(init);
		}
		setOpen(visible);
	};

	const handleSave = () => {
		const mappings: ParameterMapping[] = [];
		for (const p of parameters) {
			const d = draft[p.id];
			if (d?.checked && d.targetVar.trim()) {
				mappings.push({
					parameter_id: p.id,
					card_id: cardId,
					target: ["variable", ["template-tag", d.targetVar.trim()]],
				});
			}
		}
		onSave(mappings);
		setOpen(false);
	};

	if (parameters.length === 0) return null;

	const content = (
		<div className="w-64 space-y-3">
			{parameters.map((p) => {
				const d = draft[p.id] ?? { checked: false, targetVar: "" };
				return (
					<div key={p.id} className="space-y-1">
						<Checkbox
							checked={d.checked}
							onChange={(e) =>
								setDraft((prev) => ({
									...prev,
									[p.id]: { ...d, checked: e.target.checked },
								}))
							}
						>
							<span className="text-sm">{p.name || p.slug || p.id}</span>
						</Checkbox>
						{d.checked && (
							<div className="pl-6">
								<Input
									size="small"
									value={d.targetVar}
									onChange={(e) =>
										setDraft((prev) => ({
											...prev,
											[p.id]: { ...d, targetVar: e.target.value },
										}))
									}
									placeholder={t(locale, "dashboards.paramTarget")}
								/>
							</div>
						)}
					</div>
				);
			})}
			<div className="flex justify-end pt-2 border-t border-border-default">
				<Button size="small" type="primary" onClick={handleSave}>
					{t(locale, "dashboards.save")}
				</Button>
			</div>
		</div>
	);

	return (
		<Popover
			content={content}
			title={t(locale, "dashboards.paramMapping")}
			trigger="click"
			open={open}
			onOpenChange={handleOpenChange}
			placement="bottomLeft"
		>
			<Button type="text" size="small" icon={<SettingOutlined />} title={t(locale, "dashboards.paramMapping")} />
		</Popover>
	);
}
