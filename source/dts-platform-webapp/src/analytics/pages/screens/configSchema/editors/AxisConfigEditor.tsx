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

const LabelRow: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
    <span style={{ fontSize: 12 }}>{label}</span>
    <div style={{ maxWidth: '60%' }}>{children}</div>
  </div>
);

const AxisConfigEditor: React.FC<AxisConfigEditorProps> = ({ value = {}, onChange, label = '坐标轴' }) => {
  const update = (patch: Partial<AxisConfig>) => onChange({ ...value, ...patch });

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
            <Slider
              value={typeof value.labelRotate === 'number' ? value.labelRotate : 0}
              // 拖动时立即 commit，确保序列化后不会出现 undefined vs 0 混用。
              onChange={(v) => update({ labelRotate: typeof v === 'number' ? v : 0 })}
              min={-90}
              max={90}
              step={5}
            />
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

  return <Collapse size="small" items={items} defaultActiveKey={['axis']} />;
};

export default AxisConfigEditor;
