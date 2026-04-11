import React from 'react';
import { ColorPicker, Input, InputNumber, Radio, Select, Slider, Switch } from 'antd';

import type { ConfigField } from '../types';

// Complex types that fall back to JSON textarea in v1
const JSON_FALLBACK_TYPES = new Set([
  'tooltip-config',
  'series-config',
  'mark-line',
  'map-region',
  'border-box-style',
  'decoration-style',
  'conditional-color',
  'key-value-list',
  'field-group',
  'json',
]);

export interface FieldEditorProps {
  field: ConfigField;
  value: unknown;
  onChange: (value: unknown) => void;
  themeDefault?: string;
}

const FieldEditor: React.FC<FieldEditorProps> = ({ field, value, onChange, themeDefault }) => {
  const placeholder = field.placeholder ?? (themeDefault ? `主题默认: ${themeDefault}` : undefined);

  switch (field.type) {
    case 'text':
      return (
        <Input
          size="small"
          value={value as string}
          onChange={(e) => onChange(e.target.value)}
          allowClear
          placeholder={placeholder}
        />
      );

    case 'textarea':
      return (
        <Input.TextArea
          size="small"
          rows={3}
          value={value as string}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
        />
      );

    case 'number':
      return (
        <InputNumber
          size="small"
          style={{ width: '100%' }}
          value={value as number}
          onChange={(v) => onChange(v)}
          min={field.min}
          max={field.max}
          step={field.step}
          placeholder={placeholder}
        />
      );

    case 'slider':
      return (
        <Slider
          value={value as number}
          onChange={(v) => onChange(v)}
          min={field.min}
          max={field.max}
          step={field.step}
        />
      );

    case 'color':
      return (
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <ColorPicker
            size="small"
            value={value as string}
            onChange={(_, hex) => onChange(hex)}
            allowClear
            onClear={() => onChange(undefined)}
          />
          <Input
            size="small"
            value={value as string}
            onChange={(e) => onChange(e.target.value)}
            placeholder={placeholder ?? '#000000'}
            style={{ flex: 1 }}
          />
        </div>
      );

    case 'boolean':
      return <Switch size="small" checked={!!value} onChange={(v) => onChange(v)} />;

    case 'select':
      return (
        <Select
          size="small"
          style={{ width: '100%' }}
          value={value as string}
          onChange={(v) => onChange(v)}
          allowClear
          placeholder={placeholder}
          options={field.options?.map((o) => ({ label: o.label, value: o.value }))}
        />
      );

    case 'radio':
      return (
        <Radio.Group size="small" value={value} onChange={(e) => onChange(e.target.value)}>
          {field.options?.map((o) => (
            <Radio.Button key={String(o.value)} value={o.value}>
              {o.label}
            </Radio.Button>
          ))}
        </Radio.Group>
      );

    case 'image-url':
      return (
        <Input
          size="small"
          value={value as string}
          onChange={(e) => onChange(e.target.value)}
          placeholder="图片 URL"
          allowClear
        />
      );

    case 'gradient':
    case 'icon-select':
    case 'font-family':
      return (
        <Input
          size="small"
          value={value as string}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
        />
      );

    default:
      // Complex types with JSON fallback
      if (JSON_FALLBACK_TYPES.has(field.type)) {
        return (
          <Input.TextArea
            size="small"
            rows={4}
            style={{ fontFamily: 'monospace' }}
            value={typeof value === 'string' ? value : JSON.stringify(value, null, 2)}
            onChange={(e) => {
              const raw = e.target.value;
              try {
                onChange(JSON.parse(raw));
              } catch {
                onChange(raw);
              }
            }}
            placeholder={placeholder}
          />
        );
      }

      // Default fallback
      return (
        <Input
          size="small"
          value={value as string}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
        />
      );
  }
};

export default FieldEditor;
