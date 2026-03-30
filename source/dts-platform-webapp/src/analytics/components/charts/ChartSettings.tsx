// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import { ReactNode, useState } from 'react';
import { Input, Select, Switch, Checkbox, Tabs } from 'antd';
import './ChartComponents.css';

export interface ChartSettingsData {
	// Display settings
	title?: string;
	subtitle?: string;
	showLegend?: boolean;
	legendPosition?: 'top' | 'bottom' | 'left' | 'right';

	// Axis settings
	xAxisLabel?: string;
	yAxisLabel?: string;
	showXAxis?: boolean;
	showYAxis?: boolean;
	showGridLines?: boolean;

	// Style settings
	colorPalette?: string;
	smooth?: boolean;
	stacked?: boolean;
	showDataLabels?: boolean;

	// Goal line
	goalValue?: number;
	goalLabel?: string;
}

export interface ChartSettingsProps {
	settings: ChartSettingsData;
	onChange: (settings: ChartSettingsData) => void;
	chartType?: string;
	className?: string;
}

export function ChartSettings({
	settings,
	onChange,
	chartType = 'line',
	className = '',
}: ChartSettingsProps) {
	const updateSetting = <K extends keyof ChartSettingsData>(
		key: K,
		value: ChartSettingsData[K]
	) => {
		onChange({ ...settings, [key]: value });
	};

	return (
		<div className={`p-4 bg-surface-card border border-border-default rounded-md ${className}`}>
			<Tabs
				defaultActiveKey="display"
				items={[
					{
						key: 'display',
						label: 'Display',
						children: (
							<div className="flex flex-col gap-4 py-4 first:pt-0">
								<div>
									<label className="block mb-1 font-medium">Title</label>
									<Input
										value={settings.title || ''}
										onChange={(e) => updateSetting('title', e.target.value)}
										placeholder="Chart title"
									/>
								</div>
								<div>
									<label className="block mb-1 font-medium">Subtitle</label>
									<Input
										value={settings.subtitle || ''}
										onChange={(e) => updateSetting('subtitle', e.target.value)}
										placeholder="Optional subtitle"
									/>
								</div>
								<div className="flex items-center gap-4">
									<Switch
										checked={settings.showLegend ?? true}
										onChange={(checked) => updateSetting('showLegend', checked)}
									/>
									<span className="ml-2">Show Legend</span>
								</div>
								{settings.showLegend && (
									<div>
										<label className="block mb-1 font-medium">Legend Position</label>
										<Select
											value={settings.legendPosition || 'bottom'}
											onChange={(value) => updateSetting('legendPosition', value as ChartSettingsData['legendPosition'])}
											options={[
												{ value: 'top', label: 'Top' },
												{ value: 'bottom', label: 'Bottom' },
												{ value: 'left', label: 'Left' },
												{ value: 'right', label: 'Right' },
											]}
											style={{ width: "100%" }}
										/>
									</div>
								)}
							</div>
						),
					},
					{
						key: 'axes',
						label: 'Axes',
						children: (
							<div className="flex flex-col gap-4 py-4 first:pt-0">
								<div>
									<label className="block mb-1 font-medium">X-Axis Label</label>
									<Input
										value={settings.xAxisLabel || ''}
										onChange={(e) => updateSetting('xAxisLabel', e.target.value)}
										placeholder="X-axis label"
									/>
								</div>
								<div>
									<label className="block mb-1 font-medium">Y-Axis Label</label>
									<Input
										value={settings.yAxisLabel || ''}
										onChange={(e) => updateSetting('yAxisLabel', e.target.value)}
										placeholder="Y-axis label"
									/>
								</div>
								<div className="flex items-center gap-4">
									<Checkbox
										checked={settings.showXAxis ?? true}
										onChange={(e) => updateSetting('showXAxis', e.target.checked)}
									>
										Show X-Axis
									</Checkbox>
								</div>
								<div className="flex items-center gap-4">
									<Checkbox
										checked={settings.showYAxis ?? true}
										onChange={(e) => updateSetting('showYAxis', e.target.checked)}
									>
										Show Y-Axis
									</Checkbox>
								</div>
								<div className="flex items-center gap-4">
									<Checkbox
										checked={settings.showGridLines ?? true}
										onChange={(e) => updateSetting('showGridLines', e.target.checked)}
									>
										Show Grid Lines
									</Checkbox>
								</div>
							</div>
						),
					},
					{
						key: 'style',
						label: 'Style',
						children: (
							<>
								<div className="flex flex-col gap-4 py-4 first:pt-0">
									<div>
										<label className="block mb-1 font-medium">Color Palette</label>
										<Select
											value={settings.colorPalette || 'default'}
											onChange={(value) => updateSetting('colorPalette', value)}
											options={[
												{ value: 'default', label: 'Default' },
												{ value: 'pastel', label: 'Pastel' },
												{ value: 'categorical', label: 'Categorical' },
											]}
											style={{ width: "100%" }}
										/>
									</div>
									{(chartType === 'line' || chartType === 'area') && (
										<div className="flex items-center gap-4">
											<Switch
												checked={settings.smooth ?? false}
												onChange={(checked) => updateSetting('smooth', checked)}
											/>
											<span className="ml-2">Smooth Lines</span>
										</div>
									)}
									{(chartType === 'bar' || chartType === 'area') && (
										<div className="flex items-center gap-4">
											<Switch
												checked={settings.stacked ?? false}
												onChange={(checked) => updateSetting('stacked', checked)}
											/>
											<span className="ml-2">Stacked</span>
										</div>
									)}
									<div className="flex items-center gap-4">
										<Switch
											checked={settings.showDataLabels ?? false}
											onChange={(checked) => updateSetting('showDataLabels', checked)}
										/>
										<span className="ml-2">Show Data Labels</span>
									</div>
								</div>

								{/* Goal Line Settings */}
								<div className="flex flex-col gap-4 py-4">
									<h4 className="text-sm font-semibold text-text-secondary uppercase tracking-wide m-0 mb-2">Goal Line</h4>
									<div>
										<label className="block mb-1 font-medium">Goal Value</label>
										<Input
											type="number"
											value={settings.goalValue?.toString() || ''}
											onChange={(e) => updateSetting('goalValue', e.target.value ? Number(e.target.value) : undefined)}
											placeholder="Enter goal value"
										/>
									</div>
									{settings.goalValue !== undefined && (
										<div>
											<label className="block mb-1 font-medium">Goal Label</label>
											<Input
												value={settings.goalLabel || ''}
												onChange={(e) => updateSetting('goalLabel', e.target.value)}
												placeholder="Goal label"
											/>
										</div>
									)}
								</div>
							</>
						),
					},
				]}
			/>
		</div>
	);
}

// Chart Settings Panel (with expand/collapse)
export interface ChartSettingsPanelProps {
	settings: ChartSettingsData;
	onChange: (settings: ChartSettingsData) => void;
	chartType?: string;
	isOpen?: boolean;
	onToggle?: () => void;
}

export function ChartSettingsPanel({
	settings,
	onChange,
	chartType,
	isOpen = false,
	onToggle,
}: ChartSettingsPanelProps) {
	return (
		<div className="relative">
			<button
				type="button"
				className={`flex items-center gap-1 px-3 py-2 border rounded-sm font-[inherit] text-sm cursor-pointer transition-all duration-150 ${isOpen ? 'bg-surface-muted border-brand text-brand' : 'bg-surface-card border-border-default text-text-secondary hover:border-border-strong hover:text-text-primary'}`}
				onClick={onToggle}
				aria-expanded={isOpen}
			>
				<svg
					width="16"
					height="16"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					strokeWidth="2"
					strokeLinecap="round"
					strokeLinejoin="round"
				>
					<circle cx="12" cy="12" r="3" />
					<path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
				</svg>
				<span>Settings</span>
			</button>
			{isOpen && (
				<div className="absolute top-full right-0 z-50 mt-1 min-w-[320px] max-h-[480px] overflow-y-auto shadow-lg animate-[chart-settings-slide-in_0.15s_ease-out]">
					<ChartSettings
						settings={settings}
						onChange={onChange}
						chartType={chartType}
					/>
				</div>
			)}
		</div>
	);
}
