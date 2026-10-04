import React from 'react';
import { ColorPicker, InputNumber, Select, Switch } from 'antd';

export interface LegendConfig {
  show?: boolean;
  position?: 'top' | 'bottom' | 'left' | 'right';
  fontSize?: number;
  color?: string;
  /** Space between legend and chart body (px). Controls visual gap. */
  reserveSize?: number;
  /** Gap between individual legend items (px). */
  itemGap?: number;
}

export interface LegendConfigEditorProps {
  value?: LegendConfig;
  onChange: (v: LegendConfig) => void;
}

const POSITION_OPTIONS = [
  { label: '顶部', value: 'top' },
  { label: '底部', value: 'bottom' },
  { label: '左侧', value: 'left' },
  { label: '右侧', value: 'right' },
];

const LabelRow: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
    <span style={{ fontSize: 12 }}>{label}</span>
    <div style={{ maxWidth: '60%' }}>{children}</div>
  </div>
);

const LegendConfigEditor: React.FC<LegendConfigEditorProps> = ({ value = {}, onChange }) => {
  const update = (patch: Partial<LegendConfig>) => onChange({ ...value, ...patch });

  return (
    <div>
      <LabelRow label="显示">
        <Switch size="small" checked={!!value.show} onChange={(v) => update({ show: v })} />
      </LabelRow>
      <LabelRow label="位置">
        <Select
          size="small"
          style={{ width: '100%' }}
          value={value.position}
          onChange={(v) => update({ position: v })}
          options={POSITION_OPTIONS}
          placeholder="自动（跟随图表）"
          allowClear
        />
      </LabelRow>
      <LabelRow label="字号">
        <InputNumber
          size="small"
          value={value.fontSize}
          onChange={(v) => update({ fontSize: v ?? undefined })}
          min={8}
          max={24}
          style={{ width: '100%' }}
        />
      </LabelRow>
      <LabelRow label="颜色">
        <ColorPicker
          size="small"
          value={value.color}
          onChange={(_, hex) => update({ color: hex })}
        />
      </LabelRow>
      <LabelRow label="与图形间距">
        <InputNumber
          size="small"
          value={value.reserveSize}
          onChange={(v) => update({ reserveSize: v ?? undefined })}
          min={0}
          max={200}
          step={2}
          style={{ width: '100%' }}
          placeholder="自动"
        />
      </LabelRow>
      <LabelRow label="条目间距">
        <InputNumber
          size="small"
          value={value.itemGap}
          onChange={(v) => update({ itemGap: v ?? undefined })}
          min={0}
          max={80}
          style={{ width: '100%' }}
          placeholder="12"
        />
      </LabelRow>
    </div>
  );
};

export default LegendConfigEditor;
