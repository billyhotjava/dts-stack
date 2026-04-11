import React from 'react';
import { Button, ColorPicker, Input } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';

export interface ColorArrayEditorProps {
  value: string[];
  onChange: (v: string[]) => void;
}

const LabelRow: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
    <span style={{ fontSize: 12 }}>{label}</span>
    <div style={{ maxWidth: '60%' }}>{children}</div>
  </div>
);

const ColorArrayEditor: React.FC<ColorArrayEditorProps> = ({ value = [], onChange }) => {
  const updateAt = (idx: number, color: string) => {
    const next = [...value];
    next[idx] = color;
    onChange(next);
  };

  const removeAt = (idx: number) => {
    onChange(value.filter((_, i) => i !== idx));
  };

  const add = () => {
    onChange([...value, '#409eff']);
  };

  return (
    <div>
      {value.map((color, idx) => (
        <LabelRow key={idx} label={`颜色 ${idx + 1}`}>
          <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
            <ColorPicker
              size="small"
              value={color}
              onChange={(_, hex) => updateAt(idx, hex)}
            />
            <Input
              size="small"
              value={color}
              onChange={(e) => updateAt(idx, e.target.value)}
              style={{ width: 90 }}
            />
            <Button
              size="small"
              type="text"
              icon={<DeleteOutlined />}
              onClick={() => removeAt(idx)}
              danger
            />
          </div>
        </LabelRow>
      ))}
      <Button
        size="small"
        type="dashed"
        icon={<PlusOutlined />}
        onClick={add}
        block
        style={{ marginTop: 4 }}
      >
        添加颜色
      </Button>
    </div>
  );
};

export default ColorArrayEditor;
