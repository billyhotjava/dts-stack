// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import React, { useMemo } from "react";
import { Button, DatePicker, Input, Select, Tag } from "antd";
import { PlusOutlined, CloseOutlined, FilterOutlined } from "@ant-design/icons";
import { t, type Locale } from "../../i18n";

const { RangePicker } = DatePicker;

export interface DashboardParameter {
	id: string;
	name?: string;
	slug?: string;
	type?: string;
}

export interface DashboardFilterBarProps {
	parameters: DashboardParameter[];
	paramValues: Record<string, string>;
	paramOptions?: Record<string, string[]>;
	onParamChange: (paramId: string, value: string) => void;
	isEditing: boolean;
	locale: Locale;
	onAddParam?: () => void;
	onRemoveParam?: (paramId: string) => void;
}

export function DashboardFilterBar({
	parameters,
	paramValues,
	paramOptions,
	onParamChange,
	isEditing,
	locale,
	onAddParam,
	onRemoveParam,
}: DashboardFilterBarProps) {
	if (parameters.length === 0 && !isEditing) return null;

	return (
		<div className="flex items-center gap-3 flex-wrap px-4 py-2 bg-gray-50 dark:bg-[#22262b] border border-border-default rounded-md mb-4">
			<span className="text-gray-500 text-sm flex items-center gap-1 shrink-0">
				<FilterOutlined />
				{t(locale, "filter.title")}
			</span>

			{parameters.map((p) => {
				const paramId = p.id;
				const label = p.name || p.slug || p.id;

				if (isEditing) {
					return (
						<Tag
							key={paramId}
							closable
							onClose={() => onRemoveParam?.(paramId)}
							className="flex items-center gap-1"
						>
							<span className="text-xs font-medium">{label}</span>
							<span className="text-[10px] text-gray-400">({p.type || "category"})</span>
						</Tag>
					);
				}

				// Render appropriate control based on type
				const type = p.type || "category";
				const options = paramOptions?.[paramId] ?? [];
				const currentValue = paramValues[paramId] ?? "";

				if (type === "date") {
					return (
						<div key={paramId} className="flex flex-col gap-0.5">
							<span className="text-xs text-gray-500">{label}</span>
							<RangePicker
								size="small"
								onChange={(_dates, dateStrings) => {
									const value = dateStrings.filter(Boolean).join("~");
									onParamChange(paramId, value);
								}}
								style={{ minWidth: 220 }}
							/>
						</div>
					);
				}

				if (type === "category" || type === "string/=") {
					return (
						<div key={paramId} className="flex flex-col gap-0.5" style={{ minWidth: 160 }}>
							<span className="text-xs text-gray-500">{label}</span>
							<Select
								size="small"
								value={currentValue}
								onChange={(v) => onParamChange(paramId, v)}
								options={[
									{ value: "", label: t(locale, "filter.all") },
									...options.map((o) => ({ value: String(o), label: String(o) })),
								]}
								style={{ width: "100%" }}
							/>
						</div>
					);
				}

				// Fallback: string input
				return (
					<div key={paramId} className="flex flex-col gap-0.5" style={{ minWidth: 160 }}>
						<span className="text-xs text-gray-500">{label}</span>
						<Input
							size="small"
							value={currentValue}
							onChange={(e) => onParamChange(paramId, e.target.value)}
							placeholder={label}
						/>
					</div>
				);
			})}

			{isEditing && (
				<Button
					type="dashed"
					size="small"
					icon={<PlusOutlined />}
					onClick={onAddParam}
				>
					{t(locale, "dashboards.addFilter")}
				</Button>
			)}

			{!isEditing && parameters.length > 0 && Object.values(paramValues).some((v) => v) && (
				<Button
					type="text"
					size="small"
					onClick={() => {
						for (const p of parameters) {
							onParamChange(p.id, "");
						}
					}}
				>
					{t(locale, "filter.clear")}
				</Button>
			)}
		</div>
	);
}
