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
    <div className={`chart-settings ${className}`}>
      <Tabs
        defaultActiveKey="display"
        items={[
          {
            key: 'display',
            label: 'Display',
            children: (
              <div className="chart-settings__section">
                <div>
                  <label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>Title</label>
                  <Input
                    value={settings.title || ''}
                    onChange={(e) => updateSetting('title', e.target.value)}
                    placeholder="Chart title"
                  />
                </div>
                <div>
                  <label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>Subtitle</label>
                  <Input
                    value={settings.subtitle || ''}
                    onChange={(e) => updateSetting('subtitle', e.target.value)}
                    placeholder="Optional subtitle"
                  />
                </div>
                <div className="chart-settings__row">
                  <Switch
                    checked={settings.showLegend ?? true}
                    onChange={(checked) => updateSetting('showLegend', checked)}
                  />
                  <span style={{ marginLeft: 8 }}>Show Legend</span>
                </div>
                {settings.showLegend && (
                  <div>
                    <label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>Legend Position</label>
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
              <div className="chart-settings__section">
                <div>
                  <label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>X-Axis Label</label>
                  <Input
                    value={settings.xAxisLabel || ''}
                    onChange={(e) => updateSetting('xAxisLabel', e.target.value)}
                    placeholder="X-axis label"
                  />
                </div>
                <div>
                  <label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>Y-Axis Label</label>
                  <Input
                    value={settings.yAxisLabel || ''}
                    onChange={(e) => updateSetting('yAxisLabel', e.target.value)}
                    placeholder="Y-axis label"
                  />
                </div>
                <div className="chart-settings__row">
                  <Checkbox
                    checked={settings.showXAxis ?? true}
                    onChange={(e) => updateSetting('showXAxis', e.target.checked)}
                  >
                    Show X-Axis
                  </Checkbox>
                </div>
                <div className="chart-settings__row">
                  <Checkbox
                    checked={settings.showYAxis ?? true}
                    onChange={(e) => updateSetting('showYAxis', e.target.checked)}
                  >
                    Show Y-Axis
                  </Checkbox>
                </div>
                <div className="chart-settings__row">
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
                <div className="chart-settings__section">
                  <div>
                    <label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>Color Palette</label>
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
                    <div className="chart-settings__row">
                      <Switch
                        checked={settings.smooth ?? false}
                        onChange={(checked) => updateSetting('smooth', checked)}
                      />
                      <span style={{ marginLeft: 8 }}>Smooth Lines</span>
                    </div>
                  )}
                  {(chartType === 'bar' || chartType === 'area') && (
                    <div className="chart-settings__row">
                      <Switch
                        checked={settings.stacked ?? false}
                        onChange={(checked) => updateSetting('stacked', checked)}
                      />
                      <span style={{ marginLeft: 8 }}>Stacked</span>
                    </div>
                  )}
                  <div className="chart-settings__row">
                    <Switch
                      checked={settings.showDataLabels ?? false}
                      onChange={(checked) => updateSetting('showDataLabels', checked)}
                    />
                    <span style={{ marginLeft: 8 }}>Show Data Labels</span>
                  </div>
                </div>

                {/* Goal Line Settings */}
                <div className="chart-settings__section">
                  <h4 className="chart-settings__section-title">Goal Line</h4>
                  <div>
                    <label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>Goal Value</label>
                    <Input
                      type="number"
                      value={settings.goalValue?.toString() || ''}
                      onChange={(e) => updateSetting('goalValue', e.target.value ? Number(e.target.value) : undefined)}
                      placeholder="Enter goal value"
                    />
                  </div>
                  {settings.goalValue !== undefined && (
                    <div>
                      <label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>Goal Label</label>
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
