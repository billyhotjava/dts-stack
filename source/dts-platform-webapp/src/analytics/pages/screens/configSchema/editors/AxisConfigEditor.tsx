import React from 'react';
import { Collapse, ColorPicker, InputNumber, Slider, Switch } from 'antd';

export interface AxisConfig {
  show?: boolean;
  labelFontSize?: number;
  labelColor?: string;
  labelRotate?: number;
  splitLineShow?: boolean;
  splitLineColor?: string;
}

export interface AxisConfigEditorProps {
  value?: AxisConfig;
  onChange: (v: AxisConfig) => void;
  label?: string;
}

const ROTATE_MIN = -90;
const ROTATE_MAX = 90;

const LabelRow: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
    <span style={{ fontSize: 12 }}>{label}</span>
    <div style={{ width: '60%', minWidth: 0, display: 'flex', justifyContent: 'flex-end' }}>{children}</div>
  </div>
);

const AxisConfigEditor: React.FC<AxisConfigEditorProps> = ({ value = {}, onChange, label = '坐标轴' }) => {
  const update = (patch: Partial<AxisConfig>) => onChange({ ...value, ...patch });
  const labelRotate = typeof value.labelRotate === 'number' ? value.labelRotate : 0;
  const updateLabelRotate = (v: number | null) => update({ labelRotate: typeof v === 'number' ? v : 0 });

  const items = [
    {
      key: 'axis',
      label,
      children: (
        <div>
          <LabelRow label="显示">
            <Switch size="small" checked={!!value.show} onChange={(v) => update({ show: v })} />
          </LabelRow>
          <LabelRow label="字号">
            <InputNumber
              size="small"
              value={value.labelFontSize}
              onChange={(v) => update({ labelFontSize: v ?? undefined })}
              min={8}
              max={24}
              style={{ width: '100%' }}
            />
          </LabelRow>
          <LabelRow label="颜色">
            <ColorPicker
              size="small"
              value={value.labelColor}
              onChange={(_, hex) => update({ labelColor: hex })}
            />
          </LabelRow>
          <LabelRow label="旋转角度">
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, width: '100%' }}>
              <Slider
                value={labelRotate}
                onChange={(v) => updateLabelRotate(typeof v === 'number' ? v : 0)}
                min={ROTATE_MIN}
                max={ROTATE_MAX}
                step={1}
                style={{ flex: 1, minWidth: 0 }}
              />
              <InputNumber
                size="small"
                value={labelRotate}
                onChange={updateLabelRotate}
                min={ROTATE_MIN}
                max={ROTATE_MAX}
                step={1}
                style={{ width: 64 }}
              />
            </div>
          </LabelRow>
          <LabelRow label="分割线">
            <Switch size="small" checked={!!value.splitLineShow} onChange={(v) => update({ splitLineShow: v })} />
          </LabelRow>
          <LabelRow label="分割线颜色">
            <ColorPicker
              size="small"
              value={value.splitLineColor}
              onChange={(_, hex) => update({ splitLineColor: hex })}
            />
          </LabelRow>
        </div>
      ),
    },
  ];

  return (
    <Collapse
      size="small"
      ghost
      bordered={false}
      items={items}
      defaultActiveKey={['axis']}
      className="schema-config-collapse"
    />
  );
};

export default AxisConfigEditor;
