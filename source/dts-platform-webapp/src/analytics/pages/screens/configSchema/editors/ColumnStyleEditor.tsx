import React from 'react';
import { Button, Collapse, Input, InputNumber, Radio, Switch } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';

export interface ColumnConfig {
  key?: string;
  label?: string;
  width?: number;
  align?: 'left' | 'center' | 'right';
  frozen?: boolean;
  sortable?: boolean;
}

export interface ColumnStyleEditorProps {
  value?: ColumnConfig[];
  onChange: (v: ColumnConfig[]) => void;
}

const LabelRow: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
    <span style={{ fontSize: 12 }}>{label}</span>
    <div style={{ maxWidth: '60%' }}>{children}</div>
  </div>
);

const ColumnStyleEditor: React.FC<ColumnStyleEditorProps> = ({ value = [], onChange }) => {
  const updateAt = (idx: number, patch: Partial<ColumnConfig>) => {
    const next = [...value];
    next[idx] = { ...next[idx], ...patch };
    onChange(next);
  };

  const removeAt = (idx: number) => {
    onChange(value.filter((_, i) => i !== idx));
  };

  const add = () => {
    onChange([...value, {}]);
  };

  const items = value.map((col, idx) => ({
    key: String(idx),
    label: col.label || col.key || `列 ${idx + 1}`,
    extra: (
      <Button
        size="small"
        type="text"
        icon={<DeleteOutlined />}
        danger
        onClick={(e) => {
          e.stopPropagation();
          removeAt(idx);
        }}
      />
    ),
    children: (
      <div>
        <LabelRow label="字段 Key">
          <Input
            size="small"
            value={col.key}
            onChange={(e) => updateAt(idx, { key: e.target.value })}
          />
        </LabelRow>
        <LabelRow label="标签">
          <Input
            size="small"
            value={col.label}
            onChange={(e) => updateAt(idx, { label: e.target.value })}
          />
        </LabelRow>
        <LabelRow label="宽度">
          <InputNumber
            size="small"
            value={col.width}
            onChange={(v) => updateAt(idx, { width: v ?? undefined })}
            min={40}
            max={600}
            style={{ width: '100%' }}
          />
        </LabelRow>
        <LabelRow label="对齐">
          <Radio.Group
            size="small"
            value={col.align}
            onChange={(e) => updateAt(idx, { align: e.target.value })}
          >
            <Radio.Button value="left">左</Radio.Button>
            <Radio.Button value="center">中</Radio.Button>
            <Radio.Button value="right">右</Radio.Button>
          </Radio.Group>
        </LabelRow>
        <LabelRow label="冻结">
          <Switch size="small" checked={!!col.frozen} onChange={(v) => updateAt(idx, { frozen: v })} />
        </LabelRow>
        <LabelRow label="可排序">
          <Switch size="small" checked={!!col.sortable} onChange={(v) => updateAt(idx, { sortable: v })} />
        </LabelRow>
      </div>
    ),
  }));

  return (
    <div>
      <Collapse size="small" items={items} />
      <Button
        size="small"
        type="dashed"
        icon={<PlusOutlined />}
        onClick={add}
        block
        style={{ marginTop: 4 }}
      >
        添加列
      </Button>
    </div>
  );
};

export default ColumnStyleEditor;
