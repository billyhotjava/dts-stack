import { ReactNode, useState } from 'react';
import { Button } from '../../ui/Button/Button';
import { Input } from '../../ui/Input/Input';
import { NativeSelect } from '../../ui/Input/Select';
import { Toggle, Checkbox } from '../../ui/Input/Checkbox';
import { Tabs, TabList, Tab, TabPanels, TabPanel } from '../../ui/Tabs/Tabs';
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
    <div className={`chart-settings ${className}`}>
      <Tabs defaultValue="display" variant="underline">
        <TabList>
          <Tab value="display">Display</Tab>
          <Tab value="axes">Axes</Tab>
          <Tab value="style">Style</Tab>
        </TabList>

        <TabPanels>
          {/* Display Tab */}
          <TabPanel value="display">
            <div className="chart-settings__section">
              <Input
                label="Title"
                value={settings.title || ''}
                onChange={(e) => updateSetting('title', e.target.value)}
                placeholder="Chart title"
              />
              <Input
                label="Subtitle"
                value={settings.subtitle || ''}
                onChange={(e) => updateSetting('subtitle', e.target.value)}
                placeholder="Optional subtitle"
              />
              <div className="chart-settings__row">
                <Toggle
                  label="Show Legend"
                  checked={settings.showLegend ?? true}
                  onChange={(e) => updateSetting('showLegend', e.target.checked)}
                />
              </div>
              {settings.showLegend && (
                <NativeSelect
                  label="Legend Position"
                  value={settings.legendPosition || 'bottom'}
                  onChange={(e) => updateSetting('legendPosition', e.target.value as ChartSettingsData['legendPosition'])}
                  options={[
                    { value: 'top', label: 'Top' },
                    { value: 'bottom', label: 'Bottom' },
                    { value: 'left', label: 'Left' },
                    { value: 'right', label: 'Right' },
                  ]}
                />
              )}
            </div>
          </TabPanel>

          {/* Axes Tab */}
          <TabPanel value="axes">
            <div className="chart-settings__section">
              <Input
                label="X-Axis Label"
                value={settings.xAxisLabel || ''}
                onChange={(e) => updateSetting('xAxisLabel', e.target.value)}
                placeholder="X-axis label"
              />
              <Input
                label="Y-Axis Label"
                value={settings.yAxisLabel || ''}
                onChange={(e) => updateSetting('yAxisLabel', e.target.value)}
                placeholder="Y-axis label"
              />
              <div className="chart-settings__row">
                <Checkbox
                  label="Show X-Axis"
                  checked={settings.showXAxis ?? true}
                  onChange={(e) => updateSetting('showXAxis', e.target.checked)}
                />
              </div>
              <div className="chart-settings__row">
                <Checkbox
                  label="Show Y-Axis"
                  checked={settings.showYAxis ?? true}
                  onChange={(e) => updateSetting('showYAxis', e.target.checked)}
                />
              </div>
              <div className="chart-settings__row">
                <Checkbox
                  label="Show Grid Lines"
                  checked={settings.showGridLines ?? true}
                  onChange={(e) => updateSetting('showGridLines', e.target.checked)}
                />
              </div>
            </div>
          </TabPanel>

          {/* Style Tab */}
          <TabPanel value="style">
            <div className="chart-settings__section">
              <NativeSelect
                label="Color Palette"
                value={settings.colorPalette || 'default'}
                onChange={(e) => updateSetting('colorPalette', e.target.value)}
                options={[
                  { value: 'default', label: 'Default' },
                  { value: 'pastel', label: 'Pastel' },
                  { value: 'categorical', label: 'Categorical' },
                ]}
              />
              {(chartType === 'line' || chartType === 'area') && (
                <div className="chart-settings__row">
                  <Toggle
                    label="Smooth Lines"
                    checked={settings.smooth ?? false}
                    onChange={(e) => updateSetting('smooth', e.target.checked)}
                  />
                </div>
              )}
              {(chartType === 'bar' || chartType === 'area') && (
                <div className="chart-settings__row">
                  <Toggle
                    label="Stacked"
                    checked={settings.stacked ?? false}
                    onChange={(e) => updateSetting('stacked', e.target.checked)}
                  />
                </div>
              )}
              <div className="chart-settings__row">
                <Toggle
                  label="Show Data Labels"
                  checked={settings.showDataLabels ?? false}
                  onChange={(e) => updateSetting('showDataLabels', e.target.checked)}
                />
              </div>
            </div>

            {/* Goal Line Settings */}
            <div className="chart-settings__section">
              <h4 className="chart-settings__section-title">Goal Line</h4>
              <Input
                label="Goal Value"
                type="number"
                value={settings.goalValue?.toString() || ''}
                onChange={(e) => updateSetting('goalValue', e.target.value ? Number(e.target.value) : undefined)}
                placeholder="Enter goal value"
              />
              {settings.goalValue !== undefined && (
                <Input
                  label="Goal Label"
                  value={settings.goalLabel || ''}
                  onChange={(e) => updateSetting('goalLabel', e.target.value)}
                  placeholder="Goal label"
                />
              )}
            </div>
          </TabPanel>
        </TabPanels>
      </Tabs>
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
    <div className={`chart-settings-panel ${isOpen ? 'chart-settings-panel--open' : ''}`}>
      <button
        type="button"
        className="chart-settings-panel__toggle"
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
        <div className="chart-settings-panel__content">
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
