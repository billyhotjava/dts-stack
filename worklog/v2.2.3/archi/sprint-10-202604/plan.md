# 大屏编辑器 Phase 1 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 Schema 驱动架构替换 PropertyPanel 的硬编码 switch/case，修复全部 47 个组件配置面板；修复主题切换只改背景色的 bug；发布结果改为弹窗整合链接+分享+权限。

**Architecture:** 新建 `configSchema/` 目录，定义 ConfigField 类型系统和通用渲染器 SchemaConfigRenderer，为每个组件类型声明配置 schema。PropertyPanel 的「组件配置」section 从 switch/case（1746 行）替换为单行 `<SchemaConfigRenderer>`。主题切换通过 schema 中的 themeTokenKey 自动 patch 所有组件颜色。发布弹窗提取 ScreenGrantManager 复用授权逻辑。

**Tech Stack:** React 18, TypeScript, Ant Design (Collapse/Input/InputNumber/Select/Radio/Switch/ColorPicker/Slider), ECharts

**Base path:** `source/dts-platform-webapp/src/analytics/pages/screens/`

---

## Task 1: Schema 类型定义

**Files:**
- Create: `configSchema/types.ts`

- [ ] **Step 1: Create the ConfigField type system**

```ts
// configSchema/types.ts
import type { ComponentType } from '../types';

export type FieldType =
  | 'text' | 'textarea' | 'number' | 'slider' | 'color' | 'gradient'
  | 'boolean' | 'select' | 'radio' | 'icon-select' | 'font-family'
  | 'json' | 'image-url'
  | 'color-array' | 'axis-config' | 'legend-config' | 'column-style'
  | 'tooltip-config' | 'series-config' | 'mark-line'
  | 'map-region' | 'border-box-style' | 'decoration-style'
  | 'conditional-color' | 'key-value-list' | 'field-group';

export type ConfigFieldOption = { label: string; value: string | number | boolean };

export type ConfigField = {
  key: string;
  label: string;
  type: FieldType;
  group?: string;
  defaultValue?: unknown;
  min?: number;
  max?: number;
  step?: number;
  options?: ConfigFieldOption[];
  placeholder?: string;
  showIf?: (config: Record<string, unknown>) => boolean;
  themeTokenKey?: string;
};

export type ConfigGroup = {
  key: string;
  label: string;
  defaultOpen?: boolean;
};

export type ComponentConfigSchema = {
  type: ComponentType;
  groups?: ConfigGroup[];
  fields: ConfigField[];
};

// Standard groups reusable across schemas
export const STANDARD_GROUPS: Record<string, ConfigGroup> = {
  content:     { key: 'content',    label: '内容',     defaultOpen: true },
  typography:  { key: 'typography', label: '文字',     defaultOpen: true },
  appearance:  { key: 'appearance', label: '外观',     defaultOpen: true },
  layout:      { key: 'layout',    label: '布局',     defaultOpen: false },
  behavior:    { key: 'behavior',  label: '行为',     defaultOpen: false },
  chart:       { key: 'chart',     label: '图表',     defaultOpen: true },
  header:      { key: 'header',    label: '表头样式', defaultOpen: true },
  body:        { key: 'body',      label: '数据行',   defaultOpen: true },
  column:      { key: 'column',    label: '列配置',   defaultOpen: false },
  pagination:  { key: 'pagination',label: '分页',     defaultOpen: false },
  advanced:    { key: 'advanced',  label: '高级',     defaultOpen: false },
};
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit 2>&1 | grep -E 'configSchema|error TS' | head -20`
Expected: No errors related to configSchema

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/configSchema/types.ts
git commit -m "feat(screen-designer): add ConfigField type system for schema-driven property panel"
```

---

## Task 2: FieldEditor 基础字段编辑器

**Files:**
- Create: `configSchema/editors/FieldEditor.tsx`

- [ ] **Step 1: Create FieldEditor component**

This component routes a `ConfigField` to the appropriate Ant Design input widget. For complex types not yet implemented (tooltip-config, series-config, etc.), fall back to JSON editor.

```tsx
// configSchema/editors/FieldEditor.tsx
import { Input, InputNumber, Select, Radio, Slider, Switch, ColorPicker } from 'antd';
import type { ConfigField } from '../types';

// JSON fallback types — will get dedicated editors in future iterations
const JSON_FALLBACK_TYPES = new Set([
  'tooltip-config', 'series-config', 'mark-line', 'map-region',
  'border-box-style', 'decoration-style', 'conditional-color',
  'key-value-list', 'field-group', 'json',
]);

type FieldEditorProps = {
  field: ConfigField;
  value: unknown;
  onChange: (value: unknown) => void;
  themeDefault?: string;
};

export function FieldEditor({ field, value, onChange, themeDefault }: FieldEditorProps) {
  const placeholder = field.placeholder
    || (themeDefault ? `主题默认: ${themeDefault}` : undefined);

  if (JSON_FALLBACK_TYPES.has(field.type)) {
    return (
      <Input.TextArea
        rows={4}
        value={typeof value === 'string' ? value : JSON.stringify(value ?? '', null, 2)}
        placeholder={placeholder || 'JSON'}
        onChange={(e) => {
          try { onChange(JSON.parse(e.target.value)); }
          catch { onChange(e.target.value); }
        }}
        style={{ fontFamily: 'monospace', fontSize: 12 }}
      />
    );
  }

  switch (field.type) {
    case 'text':
      return <Input value={value as string ?? ''} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} allowClear />;

    case 'textarea':
      return <Input.TextArea rows={3} value={value as string ?? ''} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />;

    case 'number':
      return <InputNumber value={value as number} min={field.min} max={field.max} step={field.step ?? 1} placeholder={placeholder} onChange={(v) => onChange(v)} style={{ width: '100%' }} />;

    case 'slider':
      return <Slider value={value as number ?? field.min ?? 0} min={field.min ?? 0} max={field.max ?? 100} step={field.step ?? 1} onChange={(v) => onChange(v)} />;

    case 'color':
      return (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <ColorPicker
            value={value as string || undefined}
            onChange={(_, hex) => onChange(hex)}
            allowClear
            onClear={() => onChange(undefined)}
          />
          <Input
            value={value as string ?? ''}
            placeholder={placeholder}
            onChange={(e) => onChange(e.target.value || undefined)}
            style={{ flex: 1 }}
            allowClear
          />
        </div>
      );

    case 'boolean':
      return <Switch checked={!!value} onChange={(v) => onChange(v)} />;

    case 'select':
      return (
        <Select
          value={value as string | number | undefined}
          onChange={(v) => onChange(v)}
          options={field.options}
          placeholder={placeholder}
          allowClear
          style={{ width: '100%' }}
        />
      );

    case 'radio':
      return (
        <Radio.Group value={value} onChange={(e) => onChange(e.target.value)}>
          {(field.options ?? []).map((opt) => (
            <Radio.Button key={String(opt.value)} value={opt.value}>{opt.label}</Radio.Button>
          ))}
        </Radio.Group>
      );

    case 'image-url':
      return <Input value={value as string ?? ''} placeholder={placeholder || '图片 URL'} onChange={(e) => onChange(e.target.value)} allowClear />;

    case 'gradient':
    case 'icon-select':
    case 'font-family':
      // Simplified first-version: text input
      return <Input value={value as string ?? ''} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} allowClear />;

    default:
      return <Input value={String(value ?? '')} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />;
  }
}
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit 2>&1 | grep 'error TS' | head -10`

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/configSchema/editors/FieldEditor.tsx
git commit -m "feat(screen-designer): add FieldEditor base component for schema field rendering"
```

---

## Task 3: 4 个复合编辑器

**Files:**
- Create: `configSchema/editors/ColorArrayEditor.tsx`
- Create: `configSchema/editors/AxisConfigEditor.tsx`
- Create: `configSchema/editors/LegendConfigEditor.tsx`
- Create: `configSchema/editors/ColumnStyleEditor.tsx`

- [ ] **Step 1: ColorArrayEditor**

```tsx
// configSchema/editors/ColorArrayEditor.tsx
import { Button, ColorPicker } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';

type Props = { value: string[]; onChange: (v: string[]) => void };

export function ColorArrayEditor({ value = [], onChange }: Props) {
  const colors = Array.isArray(value) ? value : [];
  const update = (idx: number, hex: string) => { const next = [...colors]; next[idx] = hex; onChange(next); };
  const add = () => onChange([...colors, '#409eff']);
  const remove = (idx: number) => onChange(colors.filter((_, i) => i !== idx));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      {colors.map((c, i) => (
        <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <ColorPicker value={c} onChange={(_, hex) => update(i, hex)} size="small" />
          <span style={{ flex: 1, fontSize: 12, fontFamily: 'monospace' }}>{c}</span>
          <Button type="text" size="small" icon={<DeleteOutlined />} onClick={() => remove(i)} danger />
        </div>
      ))}
      <Button type="dashed" size="small" icon={<PlusOutlined />} onClick={add}>添加颜色</Button>
    </div>
  );
}
```

- [ ] **Step 2: AxisConfigEditor**

```tsx
// configSchema/editors/AxisConfigEditor.tsx
import { Collapse, InputNumber, Switch, ColorPicker, Slider } from 'antd';

type AxisConfig = {
  show?: boolean;
  labelFontSize?: number;
  labelColor?: string;
  labelRotate?: number;
  splitLineShow?: boolean;
  splitLineColor?: string;
};

type Props = { value?: AxisConfig; onChange: (v: AxisConfig) => void; label?: string };

export function AxisConfigEditor({ value = {}, onChange, label }: Props) {
  const patch = (key: keyof AxisConfig, v: unknown) => onChange({ ...value, [key]: v });
  return (
    <Collapse size="small" items={[{
      key: '1',
      label: label || '坐标轴',
      children: (
        <div style={{ display: 'grid', gap: 8 }}>
          <LabelRow label="显示"><Switch size="small" checked={value.show !== false} onChange={(v) => patch('show', v)} /></LabelRow>
          <LabelRow label="标签字号"><InputNumber size="small" value={value.labelFontSize ?? 12} min={8} max={24} onChange={(v) => patch('labelFontSize', v)} style={{ width: '100%' }} /></LabelRow>
          <LabelRow label="标签颜色"><ColorPicker size="small" value={value.labelColor} onChange={(_, hex) => patch('labelColor', hex)} allowClear /></LabelRow>
          <LabelRow label="标签旋转"><Slider value={value.labelRotate ?? 0} min={-90} max={90} step={5} onChange={(v) => patch('labelRotate', v)} /></LabelRow>
          <LabelRow label="分隔线"><Switch size="small" checked={value.splitLineShow !== false} onChange={(v) => patch('splitLineShow', v)} /></LabelRow>
          <LabelRow label="分隔线颜色"><ColorPicker size="small" value={value.splitLineColor} onChange={(_, hex) => patch('splitLineColor', hex)} allowClear /></LabelRow>
        </div>
      ),
    }]} />
  );
}

function LabelRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
      <span style={{ fontSize: 12 }}>{label}</span>
      <div style={{ maxWidth: '60%' }}>{children}</div>
    </div>
  );
}
```

- [ ] **Step 3: LegendConfigEditor**

```tsx
// configSchema/editors/LegendConfigEditor.tsx
import { InputNumber, Select, Switch, ColorPicker } from 'antd';

type LegendConfig = {
  show?: boolean;
  position?: 'top' | 'bottom' | 'left' | 'right';
  fontSize?: number;
  color?: string;
};

type Props = { value?: LegendConfig; onChange: (v: LegendConfig) => void };

export function LegendConfigEditor({ value = {}, onChange }: Props) {
  const patch = (key: keyof LegendConfig, v: unknown) => onChange({ ...value, [key]: v });
  return (
    <div style={{ display: 'grid', gap: 8 }}>
      <LabelRow label="显示图例"><Switch size="small" checked={value.show !== false} onChange={(v) => patch('show', v)} /></LabelRow>
      <LabelRow label="位置">
        <Select size="small" value={value.position ?? 'top'} onChange={(v) => patch('position', v)} style={{ width: '100%' }}
          options={[{ label: '顶部', value: 'top' }, { label: '底部', value: 'bottom' }, { label: '左侧', value: 'left' }, { label: '右侧', value: 'right' }]}
        />
      </LabelRow>
      <LabelRow label="字号"><InputNumber size="small" value={value.fontSize ?? 12} min={8} max={24} onChange={(v) => patch('fontSize', v)} style={{ width: '100%' }} /></LabelRow>
      <LabelRow label="颜色"><ColorPicker size="small" value={value.color} onChange={(_, hex) => patch('color', hex)} allowClear /></LabelRow>
    </div>
  );
}

function LabelRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
      <span style={{ fontSize: 12 }}>{label}</span>
      <div style={{ maxWidth: '60%' }}>{children}</div>
    </div>
  );
}
```

- [ ] **Step 4: ColumnStyleEditor**

```tsx
// configSchema/editors/ColumnStyleEditor.tsx
import { Button, Collapse, Input, InputNumber, Radio, Switch, ColorPicker } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';

type ColumnConfig = {
  key?: string;
  label?: string;
  width?: number;
  align?: 'left' | 'center' | 'right';
  frozen?: boolean;
  sortable?: boolean;
  format?: string;
  conditionColor?: string;
  conditionThreshold?: number;
};

type Props = { value?: ColumnConfig[]; onChange: (v: ColumnConfig[]) => void };

export function ColumnStyleEditor({ value = [], onChange }: Props) {
  const columns = Array.isArray(value) ? value : [];
  const patch = (idx: number, key: keyof ColumnConfig, v: unknown) => {
    const next = columns.map((c, i) => i === idx ? { ...c, [key]: v } : c);
    onChange(next);
  };
  const add = () => onChange([...columns, { key: '', label: '', width: 120, align: 'left' }]);
  const remove = (idx: number) => onChange(columns.filter((_, i) => i !== idx));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <Collapse size="small" items={columns.map((col, idx) => ({
        key: idx,
        label: col.label || col.key || `列 ${idx + 1}`,
        extra: <Button type="text" size="small" icon={<DeleteOutlined />} onClick={(e) => { e.stopPropagation(); remove(idx); }} danger />,
        children: (
          <div style={{ display: 'grid', gap: 6 }}>
            <LabelRow label="字段 key"><Input size="small" value={col.key ?? ''} onChange={(e) => patch(idx, 'key', e.target.value)} /></LabelRow>
            <LabelRow label="标签"><Input size="small" value={col.label ?? ''} onChange={(e) => patch(idx, 'label', e.target.value)} /></LabelRow>
            <LabelRow label="宽度"><InputNumber size="small" value={col.width} min={40} max={600} onChange={(v) => patch(idx, 'width', v)} style={{ width: '100%' }} /></LabelRow>
            <LabelRow label="对齐">
              <Radio.Group size="small" value={col.align ?? 'left'} onChange={(e) => patch(idx, 'align', e.target.value)}>
                <Radio.Button value="left">左</Radio.Button>
                <Radio.Button value="center">中</Radio.Button>
                <Radio.Button value="right">右</Radio.Button>
              </Radio.Group>
            </LabelRow>
            <LabelRow label="冻结"><Switch size="small" checked={!!col.frozen} onChange={(v) => patch(idx, 'frozen', v)} /></LabelRow>
            <LabelRow label="可排序"><Switch size="small" checked={!!col.sortable} onChange={(v) => patch(idx, 'sortable', v)} /></LabelRow>
          </div>
        ),
      }))} />
      <Button type="dashed" size="small" icon={<PlusOutlined />} onClick={add} block>添加列</Button>
    </div>
  );
}

function LabelRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
      <span style={{ fontSize: 12 }}>{label}</span>
      <div style={{ maxWidth: '60%' }}>{children}</div>
    </div>
  );
}
```

- [ ] **Step 5: Verify TypeScript compiles**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit 2>&1 | grep 'error TS' | head -10`

- [ ] **Step 6: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/configSchema/editors/
git commit -m "feat(screen-designer): add 4 complex editors — ColorArray, Axis, Legend, ColumnStyle"
```

---

## Task 4: SchemaConfigRenderer 通用渲染器

**Files:**
- Create: `configSchema/editors/SchemaConfigRenderer.tsx`

- [ ] **Step 1: Create SchemaConfigRenderer**

This is the core orchestrator that reads a ComponentConfigSchema and renders all fields grouped into collapsible sections.

```tsx
// configSchema/editors/SchemaConfigRenderer.tsx
import { useMemo } from 'react';
import { Collapse } from 'antd';
import type { ComponentConfigSchema, ConfigField, ConfigGroup } from '../types';
import { STANDARD_GROUPS } from '../types';
import { FieldEditor } from './FieldEditor';
import { ColorArrayEditor } from './ColorArrayEditor';
import { AxisConfigEditor } from './AxisConfigEditor';
import { LegendConfigEditor } from './LegendConfigEditor';
import { ColumnStyleEditor } from './ColumnStyleEditor';
import type { ScreenThemeTokens } from '../../screenThemes';
import { getThemeTokens } from '../../screenThemes';

type Props = {
  schema: ComponentConfigSchema;
  config: Record<string, unknown>;
  onChange: (key: string, value: unknown) => void;
  theme?: string;
};

function resolveNestedValue(obj: Record<string, unknown>, path: string): unknown {
  return path.split('.').reduce<unknown>((acc, key) => (acc && typeof acc === 'object' ? (acc as Record<string, unknown>)[key] : undefined), obj);
}

function resolveTokenValue(tokens: ScreenThemeTokens, tokenKey: string): string | undefined {
  // Support dotted paths like 'scrollBoard.headerBGC'
  const parts = tokenKey.split('.');
  let val: unknown = tokens;
  for (const p of parts) {
    if (val && typeof val === 'object') val = (val as Record<string, unknown>)[p];
    else return undefined;
  }
  return typeof val === 'string' ? val : undefined;
}

export function SchemaConfigRenderer({ schema, config, onChange, theme }: Props) {
  const tokens = useMemo(() => getThemeTokens(theme as any), [theme]);

  const groupedFields = useMemo(() => {
    const groups = schema.groups ?? Object.values(STANDARD_GROUPS);
    const grouped = new Map<string, ConfigField[]>();

    // Initialize group order
    for (const g of groups) grouped.set(g.key, []);

    // Assign fields to groups
    for (const field of schema.fields) {
      const gk = field.group || 'content';
      if (!grouped.has(gk)) grouped.set(gk, []);
      grouped.get(gk)!.push(field);
    }

    // Build collapse items (skip empty groups)
    const groupMap = new Map<string, ConfigGroup>();
    for (const g of groups) groupMap.set(g.key, g);
    // Also include standard groups for any fields that reference them
    for (const [k, g] of Object.entries(STANDARD_GROUPS)) {
      if (!groupMap.has(k)) groupMap.set(k, g);
    }

    return Array.from(grouped.entries())
      .filter(([, fields]) => fields.length > 0)
      .map(([key, fields]) => ({
        group: groupMap.get(key) || { key, label: key, defaultOpen: false },
        fields,
      }));
  }, [schema]);

  const defaultActiveKeys = useMemo(
    () => groupedFields.filter((g) => g.group.defaultOpen !== false).map((g) => g.group.key),
    [groupedFields],
  );

  const renderField = (field: ConfigField) => {
    // Check showIf condition
    if (field.showIf && !field.showIf(config)) return null;

    const value = resolveNestedValue(config, field.key);
    const themeDefault = field.themeTokenKey ? resolveTokenValue(tokens, field.themeTokenKey) : undefined;

    // Route to complex editors
    switch (field.type) {
      case 'color-array':
        return (
          <FieldRow key={field.key} label={field.label}>
            <ColorArrayEditor value={(value as string[]) ?? []} onChange={(v) => onChange(field.key, v)} />
          </FieldRow>
        );
      case 'axis-config':
        return (
          <div key={field.key}>
            <AxisConfigEditor value={value as any} onChange={(v) => onChange(field.key, v)} label={field.label} />
          </div>
        );
      case 'legend-config':
        return (
          <FieldRow key={field.key} label={field.label}>
            <LegendConfigEditor value={value as any} onChange={(v) => onChange(field.key, v)} />
          </FieldRow>
        );
      case 'column-style':
        return (
          <FieldRow key={field.key} label={field.label}>
            <ColumnStyleEditor value={value as any} onChange={(v) => onChange(field.key, v)} />
          </FieldRow>
        );
      default:
        return (
          <FieldRow key={field.key} label={field.label}>
            <FieldEditor field={field} value={value} onChange={(v) => onChange(field.key, v)} themeDefault={themeDefault} />
          </FieldRow>
        );
    }
  };

  return (
    <Collapse
      size="small"
      defaultActiveKey={defaultActiveKeys}
      items={groupedFields.map(({ group, fields }) => ({
        key: group.key,
        label: group.label,
        children: <div style={{ display: 'grid', gap: 8 }}>{fields.map(renderField)}</div>,
      }))}
    />
  );
}

function FieldRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
      <span style={{ width: 80, flexShrink: 0, fontSize: 12, lineHeight: '32px', color: 'var(--color-text-secondary, rgba(255,255,255,0.55))' }}>
        {label}
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>{children}</div>
    </div>
  );
}
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit 2>&1 | grep 'error TS' | head -10`

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/configSchema/editors/SchemaConfigRenderer.tsx
git commit -m "feat(screen-designer): add SchemaConfigRenderer — generic schema-to-UI orchestrator"
```

---

## Task 5: 全部 47 个组件的 Schema 定义

**Files:**
- Create: `configSchema/schemas/common.ts`
- Create: `configSchema/schemas/basic.ts`
- Create: `configSchema/schemas/enterprise.ts`
- Create: `configSchema/schemas/table.ts`
- Create: `configSchema/schemas/charts.ts`
- Create: `configSchema/schemas/datav.ts`
- Create: `configSchema/schemas/filters.ts`
- Create: `configSchema/schemas/three-d.ts`
- Create: `configSchema/schemas/index.ts`

**Key principle:** 每个组件的 schema 必须覆盖该组件渲染器中 `config.*` 或 `c.*` 实际读取的全部字段。参考渲染器文件：
- `renderers/BasicRenderer.tsx` — title, number-card, stat-card, section-panel, divider, markdown-text, richtext, datetime, countdown, marquee, carousel, tab-switcher, progress-bar, shape, container, image, video, iframe
- `renderers/EChartsRenderer.tsx` — 所有 chart 类型
- `renderers/DataVRenderer.tsx` — border-box, decoration, scroll-board, scroll-ranking, water-level, digital-flop, percent-pond
- `renderers/TableRenderer.tsx` — table
- `renderers/FilterRenderer.tsx` — filter-input, filter-select, filter-date-range

- [ ] **Step 1: common.ts — ECharts 共享字段**

```ts
// configSchema/schemas/common.ts
import type { ConfigField } from '../types';

/** Shared fields for ALL ECharts chart types */
export const ECHARTS_COMMON_FIELDS: ConfigField[] = [
  { key: 'title',             label: '标题',     type: 'text',        group: 'content' },
  { key: 'titleColor',        label: '标题颜色', type: 'color',       group: 'content',  themeTokenKey: 'textPrimary' },
  { key: 'titleFontSize',     label: '标题字号', type: 'number',      group: 'content',  min: 10, max: 36, defaultValue: 14 },
  { key: 'legend',            label: '图例',     type: 'legend-config', group: 'chart' },
  { key: 'seriesColors',      label: '系列颜色', type: 'color-array', group: 'chart',    themeTokenKey: 'echarts.colorPalette' },
  { key: 'tooltip',           label: '提示框',   type: 'json',        group: 'advanced' },
  { key: 'markLines',         label: '标记线',   type: 'json',        group: 'advanced' },
  { key: 'animation',         label: '动画',     type: 'boolean',     group: 'chart',    defaultValue: true },
  { key: 'animationDuration', label: '动画时长', type: 'number',      group: 'chart',    min: 0, max: 5000, step: 100, showIf: (c) => c.animation !== false },
];

/** Additional fields for charts with X/Y axes (line, bar, scatter, combo, waterfall) */
export const AXIS_CHART_FIELDS: ConfigField[] = [
  { key: 'xAxis', label: 'X 轴', type: 'axis-config', group: 'chart' },
  { key: 'yAxis', label: 'Y 轴', type: 'axis-config', group: 'chart' },
];
```

- [ ] **Step 2: basic.ts — 11 个基础/文本/媒体组件**

Read `BasicRenderer.tsx` 逐个组件检查 `c.*` 引用，为每个组件定义完整 schema。必须包含审计报告中列出的全部缺失字段。示例：

```ts
// configSchema/schemas/basic.ts
import type { ComponentConfigSchema } from '../types';
import { STANDARD_GROUPS } from '../types';

const g = STANDARD_GROUPS;

export const basicSchemas: ComponentConfigSchema[] = [
  {
    type: 'number-card',
    groups: [g.content, g.typography, g.appearance],
    fields: [
      { key: 'title',           label: '标题',   type: 'text',   group: 'content' },
      { key: 'value',           label: '数值',   type: 'text',   group: 'content' },
      { key: 'prefix',          label: '前缀',   type: 'text',   group: 'content' },
      { key: 'suffix',          label: '后缀',   type: 'text',   group: 'content' },  // FIX: was missing
      { key: 'titleFontSize',   label: '标题字号', type: 'number', group: 'typography', min: 10, max: 72, defaultValue: 14 },
      { key: 'valueFontSize',   label: '数值字号', type: 'number', group: 'typography', min: 10, max: 200, defaultValue: 32 },
      { key: 'titleColor',      label: '标题颜色', type: 'color',  group: 'typography', themeTokenKey: 'numberCard.titleColor' },
      { key: 'valueColor',      label: '数值颜色', type: 'color',  group: 'typography', themeTokenKey: 'numberCard.valueColor' },
      { key: 'backgroundColor', label: '背景色',   type: 'color',  group: 'appearance', themeTokenKey: 'cardBackground' },
    ],
  },
  {
    type: 'title',
    groups: [g.content, g.typography],
    fields: [
      { key: 'text',       label: '标题文本', type: 'text',   group: 'content' },
      { key: 'fontSize',   label: '字号',     type: 'number', group: 'typography', min: 10, max: 120, defaultValue: 24 },
      { key: 'fontWeight',  label: '粗细',    type: 'select', group: 'typography', options: [{ label: '正常', value: 'normal' }, { label: '加粗', value: 'bold' }, { label: '细', value: '300' }] },
      { key: 'textAlign',   label: '对齐',    type: 'radio',  group: 'typography', options: [{ label: '左', value: 'left' }, { label: '中', value: 'center' }, { label: '右', value: 'right' }] },
      { key: 'color',       label: '颜色',    type: 'color',  group: 'typography', themeTokenKey: 'textPrimary' },
    ],
  },
  // ... remaining 9 basic components: markdown-text, richtext, datetime, countdown, marquee, carousel,
  //     tab-switcher, progress-bar, image, video, iframe
  // Each MUST be defined by reading BasicRenderer.tsx for that component's c.* references
  // and including ALL fields from the audit report's "missing fields" table.
];
```

**实现者须对照 BasicRenderer.tsx 逐个组件完成**，确保：
- `markdown-text`: color, fontSize, lineHeight
- `richtext`: (已有 content json 编辑，保留)
- `datetime`: fontSize, color, format
- `countdown`: targetDate, label, digitFontSize, labelFontSize, accentColor, color
- `marquee`: content, speed, direction, color, fontSize, backgroundColor
- `carousel`: items/interval/autoPlay, color, titleColor, backgroundColor, fontSize, borderRadius
- `tab-switcher`: tabs(json), activeColor, inactiveColor, fontSize
- `progress-bar`: value, trackHeight, trackColor, fillColor, showLabel, labelColor
- `image`: src, objectFit, borderRadius
- `video`: src, autoPlay, loop, controls, muted, poster
- `iframe`: src, borderWidth

- [ ] **Step 3: enterprise.ts — 5 个企业组件（全部审计 CRITICAL）**

```ts
// configSchema/schemas/enterprise.ts — stat-card, section-panel, divider, shape, container
// CRITICAL: stat-card / section-panel / divider 之前完全没有配置面板！
import type { ComponentConfigSchema } from '../types';
import { STANDARD_GROUPS } from '../types';

const g = STANDARD_GROUPS;

export const enterpriseSchemas: ComponentConfigSchema[] = [
  {
    type: 'stat-card',
    groups: [g.content, g.typography, g.appearance],
    fields: [
      { key: 'title',           label: '标题',       type: 'text',    group: 'content' },
      { key: 'value',           label: '数值',       type: 'text',    group: 'content' },
      { key: 'suffix',          label: '后缀',       type: 'text',    group: 'content' },
      { key: 'trend',           label: '趋势',       type: 'select',  group: 'content', options: [{ label: '上升', value: 'up' }, { label: '下降', value: 'down' }, { label: '持平', value: 'flat' }, { label: '无', value: '' }] },
      { key: 'trendValue',      label: '趋势值',     type: 'text',    group: 'content' },
      { key: 'icon',            label: '图标',       type: 'text',    group: 'content', placeholder: 'emoji 或 icon name' },
      { key: 'titleColor',      label: '标题颜色',   type: 'color',   group: 'typography', themeTokenKey: 'textSecondary' },
      { key: 'valueColor',      label: '数值颜色',   type: 'color',   group: 'typography', themeTokenKey: 'textPrimary' },
      { key: 'accentColor',     label: '强调色',     type: 'color',   group: 'appearance', themeTokenKey: 'accentColor' },
      { key: 'backgroundColor', label: '背景色',     type: 'color',   group: 'appearance', themeTokenKey: 'cardBackground' },
      { key: 'showAccentBar',   label: '强调条',     type: 'boolean', group: 'appearance', defaultValue: true },
      { key: 'borderRadius',    label: '圆角',       type: 'number',  group: 'appearance', min: 0, max: 24 },
      { key: 'shadow',          label: '阴影',       type: 'boolean', group: 'appearance' },
    ],
  },
  {
    type: 'section-panel',
    groups: [g.content, g.typography, g.appearance, g.layout],
    fields: [
      { key: 'title',            label: '标题',       type: 'text',    group: 'content' },
      { key: 'titleIcon',        label: '标题图标',   type: 'text',    group: 'content' },
      { key: 'showHeader',       label: '显示标题栏', type: 'boolean', group: 'layout',  defaultValue: true },
      { key: 'headerHeight',     label: '标题栏高度', type: 'number',  group: 'layout',  min: 24, max: 80 },
      { key: 'padding',          label: '内边距',     type: 'number',  group: 'layout',  min: 0, max: 40 },
      { key: 'titleAlign',       label: '标题对齐',   type: 'radio',   group: 'typography', options: [{ label: '左', value: 'left' }, { label: '中', value: 'center' }] },
      { key: 'titleColor',       label: '标题颜色',   type: 'color',   group: 'typography', themeTokenKey: 'textPrimary' },
      { key: 'headerBackground', label: '标题栏背景', type: 'color',   group: 'appearance' },
      { key: 'backgroundColor',  label: '背景色',     type: 'color',   group: 'appearance', themeTokenKey: 'cardBackground' },
      { key: 'borderStyle',      label: '边框样式',   type: 'select',  group: 'appearance', options: [{ label: '实线', value: 'solid' }, { label: '虚线', value: 'dashed' }, { label: '无', value: 'none' }] },
      { key: 'borderWidth',      label: '边框宽度',   type: 'number',  group: 'appearance', min: 0, max: 4 },
      { key: 'borderColor',      label: '边框颜色',   type: 'color',   group: 'appearance', themeTokenKey: 'cardBorder' },
      { key: 'borderRadius',     label: '圆角',       type: 'number',  group: 'appearance', min: 0, max: 24 },
      { key: 'backdropBlur',     label: '模糊',       type: 'number',  group: 'appearance', min: 0, max: 20 },
      { key: 'shadow',           label: '阴影',       type: 'boolean', group: 'appearance' },
    ],
  },
  {
    type: 'divider',
    groups: [g.appearance],
    fields: [
      { key: 'direction',      label: '方向',   type: 'radio',  group: 'appearance', options: [{ label: '水平', value: 'horizontal' }, { label: '垂直', value: 'vertical' }], defaultValue: 'horizontal' },
      { key: 'lineStyle',      label: '线型',   type: 'select', group: 'appearance', options: [{ label: '实线', value: 'solid' }, { label: '虚线', value: 'dashed' }, { label: '点线', value: 'dotted' }, { label: '渐变', value: 'gradient' }] },
      { key: 'lineColor',      label: '线颜色', type: 'color',  group: 'appearance', themeTokenKey: 'textMuted' },
      { key: 'lineWidth',      label: '线宽',   type: 'number', group: 'appearance', min: 1, max: 8, defaultValue: 1 },
      { key: 'gradientColors', label: '渐变色', type: 'color-array', group: 'appearance', showIf: (c) => c.lineStyle === 'gradient' },
    ],
  },
  // shape and container — implement by reading BasicRenderer.tsx c.* references
  // shape: shapeType, backgroundColor, borderColor, borderWidth, radius, opacity
  // container: title, padding, backgroundColor, borderColor, borderWidth, radius, titleColor
];
```

- [ ] **Step 4: table.ts**

定义 table schema，必须包含审计中列出的全部字段：headerBGC, headerColor, headerFontSize, headerFontWeight, headerAlign, rowBGC, rowAltBGC, rowColor, rowFontSize, rowHeight, hoverHighlight, stripedRows, rowBorderColor, columns(column-style), showPagination, pageSize, borderColor, borderWidth, borderRadius, scrollBarVisible。

- [ ] **Step 5: charts.ts — 14 个 ECharts 图表**

每个图表 schema = `[...ECHARTS_COMMON_FIELDS, ...（该类型需要 AXIS_CHART_FIELDS 就加）, ...专有字段]`。

专有字段参考 EChartsRenderer.tsx 中的 c.* 引用：
- `line-chart`: smooth, areaStyle, stack
- `bar-chart`: barDirection, stack, barWidth, barRadius
- `pie-chart`: roseType, innerRadius, labelPosition
- `gauge-chart`: min, max, splitNumber
- `scatter-chart`: (AXIS_CHART_FIELDS 即可)
- `radar-chart`: radarShape(circle/polygon)
- `funnel-chart`: funnelAlign, sort, gap
- `map-chart`: mapRegion(json), mapStyle
- `combo-chart`: (AXIS_CHART_FIELDS + seriesConfig json)
- `treemap-chart`: (ECHARTS_COMMON_FIELDS 即可)
- `sunburst-chart`: (ECHARTS_COMMON_FIELDS 即可)
- `wordcloud-chart`: shape, fontSizeRange(json)
- `waterfall-chart`: (AXIS_CHART_FIELDS)
- `gantt-chart`: renderMode, sideTextColor, tasks(json)

- [ ] **Step 6: datav.ts — 8 个 DataV 组件**

border-box, decoration, scroll-board, scroll-ranking, water-level, digital-flop, percent-pond, flyline-chart。参考 DataVRenderer.tsx 和 BasicRenderer.tsx (scroll-board 也部分在 TableRenderer.tsx)。

- [ ] **Step 7: filters.ts — 3 个筛选器**

filter-input, filter-select, filter-date-range。共享字段：label, variableKey, placeholder, labelColor, inputTextColor, inputBorderColor, inputBackground。各自独有字段参考 FilterRenderer.tsx。

- [ ] **Step 8: three-d.ts — 3 个 3D 图表**

globe-chart, bar3d-chart, scatter3d-chart。读 EChartsRenderer.tsx 中对应 case。

- [ ] **Step 9: index.ts — 汇总导出**

```ts
// configSchema/schemas/index.ts
import type { ComponentType } from '../../types';
import type { ComponentConfigSchema } from '../types';
import { basicSchemas } from './basic';
import { enterpriseSchemas } from './enterprise';
import { tableSchemas } from './table';
import { chartSchemas } from './charts';
import { datavSchemas } from './datav';
import { filterSchemas } from './filters';
import { threeDSchemas } from './three-d';

const ALL_SCHEMAS: ComponentConfigSchema[] = [
  ...basicSchemas,
  ...enterpriseSchemas,
  ...tableSchemas,
  ...chartSchemas,
  ...datavSchemas,
  ...filterSchemas,
  ...threeDSchemas,
];

export const COMPONENT_CONFIG_SCHEMAS: Partial<Record<ComponentType, ComponentConfigSchema>> =
  Object.fromEntries(ALL_SCHEMAS.map((s) => [s.type, s]));

export { ALL_SCHEMAS };
```

- [ ] **Step 10: Verify TypeScript compiles**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit 2>&1 | grep 'error TS' | head -20`

- [ ] **Step 11: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/configSchema/schemas/
git commit -m "feat(screen-designer): define config schemas for all 47 component types"
```

---

## Task 6: PropertyPanel 集成 SchemaConfigRenderer

**Files:**
- Modify: `components/PropertyPanel.tsx:1009,2054-2088,2855-5121`

- [ ] **Step 1: Import SchemaConfigRenderer and COMPONENT_CONFIG_SCHEMAS**

At top of PropertyPanel.tsx, add:
```ts
import { SchemaConfigRenderer } from '../configSchema/editors/SchemaConfigRenderer';
import { COMPONENT_CONFIG_SCHEMAS } from '../configSchema/schemas';
```

- [ ] **Step 2: Replace the 组件配置 section**

Replace the entire `renderComponentConfig` function (lines 2855-5121) and the Quick/Advanced toggle logic (lines 2054-2088) with:

```tsx
// Replace lines 2054-2088 (the toggle + dispatch block) with:
{(() => {
  const schema = selectedComponent ? COMPONENT_CONFIG_SCHEMAS[selectedComponent.type] : undefined;
  if (!schema) {
    return <div className="text-xs text-center py-4 opacity-60">暂无可配置项</div>;
  }
  return (
    <SchemaConfigRenderer
      schema={schema}
      config={(selectedComponent?.config as Record<string, unknown>) ?? {}}
      onChange={(key, value) => {
        if (!selectedComponent) return;
        const nextConfig = { ...(selectedComponent.config as Record<string, unknown>), [key]: value };
        updateComponentConfig(selectedComponent.id, nextConfig);
      }}
      theme={config.theme}
    />
  );
})()}
```

- [ ] **Step 3: Delete the old renderComponentConfig function (lines 2855-5121) and renderQuickChartConfig (line 338+)**

Remove: `function renderComponentConfig(...)` and `function renderQuickChartConfig(...)` and the `componentConfigMode` state (line 1009) and localStorage key (line 66).

- [ ] **Step 4: Delete the configMode toggle buttons (lines 2054-2079)**

Remove the 简洁/专业 toggle buttons and their surrounding container.

- [ ] **Step 5: Verify TypeScript compiles and app renders**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit 2>&1 | grep 'error TS' | head -20`

- [ ] **Step 6: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/components/PropertyPanel.tsx
git commit -m "feat(screen-designer): replace PropertyPanel switch/case with SchemaConfigRenderer

Removes ~1700 lines of hardcoded component config rendering.
All 47 component types now driven by declarative schemas."
```

---

## Task 7: 主题系统修复

**Files:**
- Modify: `screenThemes.ts:521-635`
- Modify: `components/ScreenHeader.tsx:344-349`

- [ ] **Step 1: Rewrite applyThemeToComponents to be schema-driven**

Replace `patchComponentConfig` (lines 521-623) and `applyThemeToComponents` (lines 625-635) in `screenThemes.ts`:

```ts
import { COMPONENT_CONFIG_SCHEMAS } from './configSchema/schemas';

/**
 * Apply theme tokens to all component configs via schema themeTokenKey mappings.
 * @param mode 'force' = overwrite all; 'safe' = only fill empty values
 */
export function applyThemeToComponents(
  components: ScreenComponent[],
  theme: ScreenTheme | string | undefined,
  mode: 'force' | 'safe' = 'force',
): ScreenComponent[] {
  const tokens = getThemeTokens(theme as ScreenTheme);
  return components.map((comp) => {
    const schema = COMPONENT_CONFIG_SCHEMAS[comp.type as ComponentType];
    if (!schema) return comp;
    const patched = { ...(comp.config || {}) } as Record<string, unknown>;
    for (const field of schema.fields) {
      if (!field.themeTokenKey) continue;
      const tokenValue = resolveTokenValue(tokens, field.themeTokenKey);
      if (tokenValue === undefined) continue;
      if (mode === 'force' || patched[field.key] === undefined || patched[field.key] === null || patched[field.key] === '') {
        patched[field.key] = tokenValue;
      }
    }
    return { ...comp, config: patched };
  });
}

function resolveTokenValue(tokens: ScreenThemeTokens, tokenKey: string): string | undefined {
  const parts = tokenKey.split('.');
  let val: unknown = tokens;
  for (const p of parts) {
    if (val && typeof val === 'object') val = (val as Record<string, unknown>)[p];
    else return undefined;
  }
  return typeof val === 'string' ? val : undefined;
}
```

Delete the old `patchComponentConfig` function.

- [ ] **Step 2: Update handleToolbarThemeChange in ScreenHeader.tsx (lines 344-349)**

```ts
const handleToolbarThemeChange = useCallback((e: any) => {
    const value = e.target.value as ScreenTheme | '';
    const theme = value || undefined;
    const tokens = getThemeTokens(theme);
    const updatedComponents = applyThemeToComponents(config.components || [], theme, 'force');
    updateConfig({ theme, backgroundColor: tokens.canvasBackground, components: updatedComponents });
}, [config.components, updateConfig]);
```

Add import: `import { applyThemeToComponents } from '../screenThemes';`

- [ ] **Step 3: Remove the standalone "应用样式" button** if it exists in ScreenHeader (search for it and delete).

- [ ] **Step 4: Verify TypeScript compiles**

- [ ] **Step 5: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/screenThemes.ts \
      source/dts-platform-webapp/src/analytics/pages/screens/components/ScreenHeader.tsx
git commit -m "fix(screen-designer): theme switch now applies colors to all components via schema themeTokenKey"
```

---

## Task 8: ScreenGrantManager 提取

**Files:**
- Create: `components/ScreenGrantManager.tsx`
- Modify: `components/ScreenAclPanel.tsx`

- [ ] **Step 1: Extract ScreenGrantManager from ScreenAclPanel**

Read `ScreenAclPanel.tsx` (513 lines). Extract the core content (grant table + add form + handlers) into `ScreenGrantManager.tsx` as a non-Modal component. Keep the same internal logic, just remove the `<Modal>` wrapper.

```tsx
// components/ScreenGrantManager.tsx
// Props: { screenId?: string | number; isOwner?: boolean }
// Contains: grant list table, add grant form (type/perm selector + candidate search + add), revoke handler
// NO Modal wrapping — this is a pure content component
```

- [ ] **Step 2: Simplify ScreenAclPanel to wrap ScreenGrantManager**

```tsx
// components/ScreenAclPanel.tsx
import { Modal } from 'antd';
import { ScreenGrantManager } from './ScreenGrantManager';

type Props = { open: boolean; screenId?: string | number; onClose: () => void; isOwner?: boolean };

export function ScreenAclPanel({ open, screenId, onClose, isOwner }: Props) {
  return (
    <Modal open={open} onCancel={onClose} title="权限管理" footer={null} width={780}
      styles={{ body: { maxHeight: '72vh', overflowY: 'auto' } }}>
      <ScreenGrantManager screenId={screenId} isOwner={isOwner} />
    </Modal>
  );
}
```

- [ ] **Step 3: Verify TypeScript compiles and ACL panel still works**

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/components/ScreenGrantManager.tsx \
      source/dts-platform-webapp/src/analytics/pages/screens/components/ScreenAclPanel.tsx
git commit -m "refactor(screen-designer): extract ScreenGrantManager for reuse in PublishResultModal"
```

---

## Task 9: PublishResultModal + ScreenHeader 清理

**Files:**
- Create: `components/PublishResultModal.tsx`
- Modify: `components/ScreenHeader.tsx:445-446,950-997,999-1029,2302-2367`

- [ ] **Step 1: Create PublishResultModal**

```tsx
// components/PublishResultModal.tsx
import { Modal, Button, Input, message } from 'antd';
import { CopyOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { ScreenGrantManager } from './ScreenGrantManager';

type PublishInfo = {
  screenId: string | number;
  versionNo: number | string;
  previewUrl: string;
  publicUrl?: string;
  warmupText?: string;
};

type Props = {
  open: boolean;
  onClose: () => void;
  publishInfo: PublishInfo | null;
  isOwner?: boolean;
};

export function PublishResultModal({ open, onClose, publishInfo, isOwner }: Props) {
  if (!publishInfo) return null;

  const copyUrl = (url: string) => {
    navigator.clipboard.writeText(url).then(() => message.success('已复制'));
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title={null}
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button onClick={onClose}>关闭</Button>
        </div>
      }
      width={720}
      styles={{ body: { maxHeight: '76vh', overflowY: 'auto' } }}
    >
      <div style={{ display: 'grid', gap: 16 }}>
        {/* 发布信息 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '12px 16px', borderRadius: 8, background: 'rgba(34,197,94,0.08)', border: '1px solid rgba(34,197,94,0.2)' }}>
          <CheckCircleOutlined style={{ color: '#22c55e', fontSize: 20 }} />
          <div>
            <div style={{ fontWeight: 600 }}>已发布 v{publishInfo.versionNo}（大屏 #{publishInfo.screenId}）</div>
            {publishInfo.warmupText && <div style={{ fontSize: 12, opacity: 0.7, marginTop: 2 }}>{publishInfo.warmupText}</div>}
          </div>
        </div>

        {/* 链接地址 */}
        <div>
          <div style={{ fontWeight: 500, marginBottom: 8 }}>链接地址</div>
          <div style={{ display: 'grid', gap: 8 }}>
            <LinkRow label="预览链接" url={publishInfo.previewUrl} onCopy={copyUrl} />
            {publishInfo.publicUrl && <LinkRow label="公开链接" url={publishInfo.publicUrl} onCopy={copyUrl} />}
          </div>
        </div>

        {/* 分享与权限 */}
        <div>
          <div style={{ fontWeight: 500, marginBottom: 8 }}>分享与权限</div>
          <ScreenGrantManager screenId={publishInfo.screenId} isOwner={isOwner} />
        </div>
      </div>
    </Modal>
  );
}

function LinkRow({ label, url, onCopy }: { label: string; url: string; onCopy: (url: string) => void }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 12px', borderRadius: 6, background: 'var(--color-bg-secondary, rgba(255,255,255,0.04))' }}>
      <span style={{ fontSize: 12, opacity: 0.6, width: 60, flexShrink: 0 }}>{label}</span>
      <a href={url} target="_blank" rel="noopener noreferrer" style={{ flex: 1, fontSize: 12, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{url}</a>
      <Button size="small" icon={<CopyOutlined />} onClick={() => onCopy(url)}>复制</Button>
    </div>
  );
}
```

- [ ] **Step 2: Update ScreenHeader.tsx**

1. Add import: `import { PublishResultModal } from './PublishResultModal';`
2. Replace `publishNotice` state (line 445-446) with:
   ```ts
   const [publishModalOpen, setPublishModalOpen] = useState(false);
   const [publishInfo, setPublishInfo] = useState<{ screenId: string | number; versionNo: number | string; previewUrl: string; publicUrl?: string; warmupText?: string } | null>(null);
   ```
3. In `handlePublish` (lines 950-997), replace `setPublishNotice(...)` with:
   ```ts
   setPublishInfo({ screenId, versionNo: version.versionNo, previewUrl, publicUrl, warmupText });
   setPublishModalOpen(true);
   ```
4. Delete the inline publish notice panel (lines 2302-2367).
5. Delete `publishNoticeDismissed` state and its usage.
6. Delete the auto-hydration logic (lines 999-1029) or change it to set `publishInfo` without auto-opening the modal.
7. Add the modal render near other modals:
   ```tsx
   <PublishResultModal open={publishModalOpen} onClose={() => setPublishModalOpen(false)} publishInfo={publishInfo} isOwner={permissions.isOwner} />
   ```

- [ ] **Step 3: Verify TypeScript compiles**

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/components/PublishResultModal.tsx \
      source/dts-platform-webapp/src/analytics/pages/screens/components/ScreenHeader.tsx
git commit -m "feat(screen-designer): replace inline publish panel with PublishResultModal

Integrates link sharing + permission management (ScreenGrantManager) into
a clean modal dialog. Removes the old green inline bar."
```

---

## Task 10: 最终验证

- [ ] **Step 1: Full TypeScript check**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit 2>&1 | grep 'error TS' | wc -l`
Expected: 0

- [ ] **Step 2: Verify no unused imports or dead code**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit 2>&1 | grep 'unused\|declared but' | head -20`
Fix any warnings.

- [ ] **Step 3: Manual verification checklist**

- [ ] Open screen designer, select a `number-card` component → see "后缀" field in 组件配置
- [ ] Select a `stat-card` → see full config panel (title/value/suffix/trend/accentColor/...)
- [ ] Select a `section-panel` → see full config panel
- [ ] Select a `divider` → see direction/lineStyle/lineColor/lineWidth fields
- [ ] Select a `line-chart` → see 图例/系列颜色/X轴/Y轴 editors
- [ ] Select a `table` → see 表头样式/数据行/列配置 sections
- [ ] Switch theme from glacier to titanium → all component colors update immediately
- [ ] Click 发布 → see modal with links + 分享与权限 section
- [ ] Click 复制 on preview link → clipboard works

- [ ] **Step 4: Final commit**

```bash
git commit --allow-empty -m "chore(screen-designer): Phase 1 complete — schema-driven config + theme fix + publish modal"
```
