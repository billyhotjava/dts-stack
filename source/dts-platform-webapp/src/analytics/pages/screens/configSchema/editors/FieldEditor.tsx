import React, { useRef, useState } from 'react';
import { Button, ColorPicker, Input, InputNumber, message, Radio, Select, Slider, Switch } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import apiClient from '../../../../../api/apiClient';

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

    case 'color': {
      const colorValue = typeof value === 'string' && value.length > 0 ? value : undefined;
      return (
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <ColorPicker
            size="small"
            value={colorValue}
            onChange={(_, hex) => onChange(hex)}
            allowClear
            onClear={() => onChange(undefined)}
            showText={false}
          />
          <Input
            size="small"
            value={colorValue ?? ''}
            onChange={(e) => {
              const next = e.target.value.trim();
              onChange(next.length === 0 ? undefined : next);
            }}
            placeholder={placeholder ?? '#000000'}
            style={{ flex: 1 }}
            allowClear
          />
        </div>
      );
    }

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

    case 'image-url': {
      const fileInputRef = useRef<HTMLInputElement>(null);
      const [uploading, setUploading] = useState(false);
      const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        if (file.size > 10 * 1024 * 1024) { message.error('文件大小不能超过 10MB'); return; }
        const formData = new FormData();
        formData.append('file', file);
        setUploading(true);
        try {
          const res = await apiClient.post<{ data: { url: string } }>({ url: '/api/infra/screen-images/upload', data: formData });
          const url = (res as any)?.data?.url ?? (res as any)?.url;
          if (url) { onChange(url); message.success('上传成功'); }
          else { message.error('上传返回格式异常'); }
        } catch (err: any) { message.error(err?.message || '上传失败'); }
        finally { setUploading(false); if (fileInputRef.current) fileInputRef.current.value = ''; }
      };
      return (
        <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
          <Input
            size="small"
            value={value as string}
            onChange={(e) => onChange(e.target.value)}
            placeholder="图片 URL 或点击上传"
            allowClear
            style={{ flex: 1 }}
          />
          <Button size="small" icon={<UploadOutlined />} loading={uploading}
            onClick={() => fileInputRef.current?.click()}>
            上传
          </Button>
          <input ref={fileInputRef} type="file" accept="image/*" style={{ display: 'none' }}
            onChange={handleUpload} />
        </div>
      );
    }

    case 'gradient':
    case 'icon-select':
      return (
        <Input
          size="small"
          value={value as string}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
        />
      );

    case 'font-family': {
      const fontInputRef = useRef<HTMLInputElement>(null);
      const [fontUploading, setFontUploading] = useState(false);
      const [uploadedFonts, setUploadedFonts] = useState<Array<{ fontFamily: string; url: string; format: string }>>([]);
      const fontsLoaded = useRef(false);
      if (!fontsLoaded.current) {
        fontsLoaded.current = true;
        apiClient.post<any>({ url: '/api/infra/screen-fonts', method: 'GET' } as any)
          .catch(() => fetch('/api/infra/screen-fonts').then(r => r.json()))
          .then((res: any) => {
            const list = res?.data ?? res ?? [];
            if (Array.isArray(list)) setUploadedFonts(list);
          }).catch(() => {});
      }
      const PRESET_FONTS = [
        '微软雅黑', '宋体', '黑体', '楷体', '仿宋',
        'PingFang SC', 'Noto Sans SC',
        'Arial', 'Helvetica', 'Times New Roman', 'Georgia',
      ];
      const handleFontUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        if (file.size > 20 * 1024 * 1024) { message.error('字体文件不能超过 20MB'); return; }
        const formData = new FormData();
        formData.append('file', file);
        setFontUploading(true);
        try {
          const res = await apiClient.post<any>({ url: '/api/infra/screen-fonts/upload', data: formData });
          const d = (res as any)?.data ?? res;
          if (d?.fontFamily) {
            onChange(d.fontFamily);
            setUploadedFonts(prev => [...prev, { fontFamily: d.fontFamily, url: d.url, format: d.filename?.split('.').pop() || 'ttf' }]);
            message.success(`字体 "${d.fontFamily}" 上传成功`);
          } else { message.error('上传返回格式异常'); }
        } catch (err: any) { message.error(err?.message || '上传失败'); }
        finally { setFontUploading(false); if (fontInputRef.current) fontInputRef.current.value = ''; }
      };
      const options = [
        ...PRESET_FONTS.map(f => ({ label: f, value: f })),
        ...uploadedFonts.map(f => ({ label: `📦 ${f.fontFamily}`, value: f.fontFamily })),
      ];
      return (
        <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
          <Select
            size="small"
            showSearch
            value={(value as string) || undefined}
            onChange={(v) => onChange(v)}
            placeholder="选择字体"
            options={options}
            style={{ flex: 1 }}
            allowClear
          />
          <Button size="small" icon={<UploadOutlined />} loading={fontUploading}
            onClick={() => fontInputRef.current?.click()}>
            上传
          </Button>
          <input ref={fontInputRef} type="file" accept=".ttf,.otf,.woff,.woff2" style={{ display: 'none' }}
            onChange={handleFontUpload} />
        </div>
      );
    }

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
