import React, { useEffect, useRef, useState } from 'react';
import { Button, ColorPicker, Input, InputNumber, message, Radio, Select, Slider, Switch } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import apiClient from '../../../../../api/apiClient';
import {
    getScreenImageUploadErrorMessage,
    SCREEN_IMAGE_UPLOAD_LIMIT_BYTES,
    SCREEN_IMAGE_UPLOAD_LIMIT_LABEL,
} from '../../utils/screenImageUpload';

import type { ConfigField } from '../types';

// 颜色合法性：接受 hex (#rgb / #rrggbb / #rrggbbaa)、rgb()/rgba()、
// hsl()/hsla()、CSS 变量（var(--xxx)）以及 CSS 命名色（通过 transparent/currentColor 等不做穷举校验）。
// 非法输入应让用户保留原值，而不是静默下发到 ECharts 导致渲染异常。
const COLOR_HEX_RE = /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/;
const COLOR_FUNC_RE = /^(rgb|rgba|hsl|hsla)\s*\([^)]*\)\s*$/i;
const COLOR_VAR_RE = /^var\s*\(\s*--[\w-]+\s*(?:,[^)]*)?\)\s*$/;
const COLOR_KEYWORDS = new Set(['transparent', 'currentcolor', 'inherit', 'initial', 'unset']);

function isValidColor(text: string): boolean {
    const v = text.trim();
    if (!v) return false;
    if (COLOR_HEX_RE.test(v)) return true;
    if (COLOR_FUNC_RE.test(v)) return true;
    if (COLOR_VAR_RE.test(v)) return true;
    if (COLOR_KEYWORDS.has(v.toLowerCase())) return true;
    return false;
}

// ---------------------------------------------------------------------------
// 子组件: 图片 URL / 字体上传
// 之前实现在 FieldEditor 的 switch case 里直接使用 useRef / useState,
// 如果 field.type 在不同渲染之间切换会违反 React Hooks 规则, 造成 Hook 顺序错乱。
// 拆成独立组件后, Hook 仅在该组件内部使用, 安全合规。
// ---------------------------------------------------------------------------

interface ColorTextFieldProps {
    value: string | undefined;
    onChange: (v: unknown) => void;
    placeholder?: string;
}

/**
 * 颜色输入框 + ColorPicker。
 * 输入过程中允许任意字符（否则用户没法正常编辑 hex），仅在失焦/回车时做合法性校验。
 * 非法输入会回滚到上一次有效值，避免把 `#xyz` 写进 config 导致 ECharts 报错或渲染异常。
 */
const ColorTextField: React.FC<ColorTextFieldProps> = ({ value, onChange, placeholder }) => {
    const [draft, setDraft] = useState<string>(value ?? '');

    useEffect(() => {
        setDraft(value ?? '');
    }, [value]);

    const commit = () => {
        const next = draft.trim();
        if (next.length === 0) {
            onChange(undefined);
            return;
        }
        if (isValidColor(next)) {
            onChange(next);
        } else {
            message.warning('颜色格式无效，已恢复上一次有效值');
            setDraft(value ?? '');
        }
    };

    return (
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
            <ColorPicker
                size="small"
                value={value}
                onChange={(_, hex) => onChange(hex)}
                allowClear
                onClear={() => onChange(undefined)}
                showText={false}
            />
            <Input
                size="small"
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                onBlur={commit}
                onPressEnter={commit}
                placeholder={placeholder}
                style={{ flex: 1 }}
                allowClear
            />
        </div>
    );
};

interface ImageUrlFieldProps {
    value: unknown;
    onChange: (v: unknown) => void;
}

const ImageUrlField: React.FC<ImageUrlFieldProps> = ({ value, onChange }) => {
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [uploading, setUploading] = useState(false);

    const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        if (file.size > SCREEN_IMAGE_UPLOAD_LIMIT_BYTES) {
            message.error(`文件大小不能超过 ${SCREEN_IMAGE_UPLOAD_LIMIT_LABEL}`);
            if (fileInputRef.current) fileInputRef.current.value = '';
            return;
        }
        const formData = new FormData();
        formData.append('file', file);
        setUploading(true);
        try {
            const res = await apiClient.post<{ data: { url: string } }>({
                url: '/infra/screen-images/upload',
                data: formData,
                _skipErrorToast: true,
            } as any);
            const url = (res as any)?.data?.url ?? (res as any)?.url;
            if (url) { onChange(url); message.success('上传成功'); }
            else { message.error('上传返回格式异常'); }
        } catch (err: unknown) { message.error(getScreenImageUploadErrorMessage(err)); }
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
            <Button
                size="small"
                icon={<UploadOutlined />}
                loading={uploading}
                onClick={() => fileInputRef.current?.click()}
            >
                上传
            </Button>
            <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                style={{ display: 'none' }}
                onChange={handleUpload}
            />
        </div>
    );
};

interface FontFamilyFieldProps {
    value: unknown;
    onChange: (v: unknown) => void;
    placeholder?: string;
}

const PRESET_FONTS = [
    '微软雅黑', '宋体', '黑体', '楷体', '仿宋',
    'PingFang SC', 'Noto Sans SC',
    'Arial', 'Helvetica', 'Times New Roman', 'Georgia',
];

const FontFamilyField: React.FC<FontFamilyFieldProps> = ({ value, onChange }) => {
    const fontInputRef = useRef<HTMLInputElement>(null);
    const [fontUploading, setFontUploading] = useState(false);
    const [uploadedFonts, setUploadedFonts] = useState<Array<{ fontFamily: string; url: string; format: string }>>([]);

    useEffect(() => {
        let cancelled = false;
        // 注意: 之前这里写成 apiClient.post({ ..., method: 'GET' }) —— apiClient.post 内部强制 method=POST,
        // 会导致后端返回 405。用 apiClient.get 才是正确 GET 请求。
        apiClient.get<any>({ url: '/infra/screen-fonts' })
            .then((res: any) => {
                if (cancelled) return;
                const list = res?.data ?? res ?? [];
                if (Array.isArray(list)) setUploadedFonts(list);
            })
            .catch(() => { /* 忽略字体列表加载错误 */ });
        return () => { cancelled = true; };
    }, []);

    const handleFontUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        if (file.size > 20 * 1024 * 1024) { message.error('字体文件不能超过 20MB'); return; }
        const formData = new FormData();
        formData.append('file', file);
        setFontUploading(true);
        try {
            const res = await apiClient.post<any>({ url: '/infra/screen-fonts/upload', data: formData });
            const d = (res as any)?.data ?? res;
            if (d?.fontFamily) {
                onChange(d.fontFamily);
                setUploadedFonts((prev) => [...prev, {
                    fontFamily: d.fontFamily,
                    url: d.url,
                    format: d.filename?.split('.').pop() || 'ttf',
                }]);
                message.success(`字体 "${d.fontFamily}" 上传成功`);
            } else {
                message.error('上传返回格式异常');
            }
        } catch (err: any) {
            message.error(err?.message || '上传失败');
        } finally {
            setFontUploading(false);
            if (fontInputRef.current) fontInputRef.current.value = '';
        }
    };

    const options = [
        ...PRESET_FONTS.map((f) => ({ label: f, value: f })),
        ...uploadedFonts.map((f) => ({ label: `📦 ${f.fontFamily}`, value: f.fontFamily })),
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
            <Button
                size="small"
                icon={<UploadOutlined />}
                loading={fontUploading}
                onClick={() => fontInputRef.current?.click()}
            >
                上传
            </Button>
            <input
                ref={fontInputRef}
                type="file"
                accept=".ttf,.otf,.woff,.woff2"
                style={{ display: 'none' }}
                onChange={handleFontUpload}
            />
        </div>
    );
};

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

function toInputString(value: unknown): string {
  if (typeof value === 'string') return value;
  if (value == null) return '';
  return String(value);
}

const FieldEditor: React.FC<FieldEditorProps> = ({ field, value, onChange, themeDefault }) => {
  const placeholder = field.placeholder ?? (themeDefault ? `主题默认: ${themeDefault}` : undefined);

  switch (field.type) {
    case 'text':
      return (
        <Input
          size="small"
          value={toInputString(value)}
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
          value={toInputString(value)}
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
          // Ant Design 清空数字框时回调 null，外层配置统一用 undefined 表示"未设置"，
          // 避免 config 里混入 null 而导致后续 ECharts option 出现 { fontSize: null } 之类的异常。
          onChange={(v) => onChange(v ?? undefined)}
          min={field.min}
          max={field.max}
          step={field.step}
          placeholder={placeholder}
        />
      );

    case 'slider': {
      const sliderMin = typeof field.min === 'number' ? field.min : 0;
      const sliderValue = typeof value === 'number' ? value : sliderMin;
      return (
        <Slider
          value={sliderValue}
          onChange={(v) => onChange(v)}
          min={field.min}
          max={field.max}
          step={field.step}
        />
      );
    }

    case 'color': {
      const colorValue = typeof value === 'string' && value.length > 0 ? value : undefined;
      return (
        <ColorTextField
          value={colorValue}
          onChange={onChange}
          placeholder={placeholder ?? '#000000'}
        />
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

    case 'image-url':
      return <ImageUrlField value={value} onChange={onChange} />;

    case 'gradient':
    case 'icon-select':
      return (
        <Input
          size="small"
          value={toInputString(value)}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
        />
      );

    case 'font-family':
      return <FontFamilyField value={value} onChange={onChange} />;

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
          value={toInputString(value)}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
        />
      );
  }
};

export default FieldEditor;
