import type { ComponentType } from '../types';

// ---------------------------------------------------------------------------
// FieldType — all supported property-panel editor types
// ---------------------------------------------------------------------------

export type FieldType =
  // Basic editors
  | 'text'
  | 'textarea'
  | 'number'
  | 'slider'
  | 'color'
  | 'gradient'
  | 'boolean'
  | 'select'
  | 'radio'
  | 'icon-select'
  | 'font-family'
  | 'json'
  | 'image-url'
  // Complex editors (v1 implemented)
  | 'color-array'
  | 'axis-config'
  | 'legend-config'
  | 'column-style'
  // Complex editors (v1 JSON fallback)
  | 'tooltip-config'
  | 'series-config'
  | 'mark-line'
  | 'map-region'
  | 'border-box-style'
  | 'decoration-style'
  | 'conditional-color'
  | 'key-value-list'
  | 'field-group';

// ---------------------------------------------------------------------------
// ConfigFieldOption — used by select / radio fields
// ---------------------------------------------------------------------------

export interface ConfigFieldOption {
  label: string;
  value: string | number | boolean;
}

// ---------------------------------------------------------------------------
// ConfigField — a single editable property in the panel
// ---------------------------------------------------------------------------

export interface ConfigField {
  /** Config field path, e.g. 'titleFontSize' or 'style.fill' */
  key: string;
  /** Chinese display label */
  label: string;
  /** Editor widget type */
  type: FieldType;
  /** Group key — references ConfigGroup.key */
  group?: string;
  /** Default value when the field is unset */
  defaultValue?: unknown;
  /** Minimum value (number / slider) */
  min?: number;
  /** Maximum value (number / slider) */
  max?: number;
  /** Step increment (number / slider) */
  step?: number;
  /** Options for select / radio */
  options?: ConfigFieldOption[];
  /** Input placeholder text */
  placeholder?: string;
  /** 数字字段后缀单位(如 px/%/秒);未提供时按 key 名自动推断 */
  unit?: string;
  /** Conditional display predicate */
  showIf?: (config: Record<string, unknown>) => boolean;
  /** Maps to a theme token for auto-patching */
  themeTokenKey?: string;
}

// ---------------------------------------------------------------------------
// ConfigGroup — collapsible section in the property panel
// ---------------------------------------------------------------------------

export interface ConfigGroup {
  key: string;
  label: string;
  defaultOpen?: boolean;
}

// ---------------------------------------------------------------------------
// ComponentConfigSchema — full schema for one component type
// ---------------------------------------------------------------------------

export interface ComponentConfigSchema {
  type: ComponentType;
  groups?: ConfigGroup[];
  fields: ConfigField[];
}

// ---------------------------------------------------------------------------
// STANDARD_GROUPS — reusable group definitions shared across schemas
// ---------------------------------------------------------------------------

export const STANDARD_GROUPS: Record<string, ConfigGroup> = {
  content:    { key: 'content',    label: '内容',   defaultOpen: true },
  typography: { key: 'typography', label: '文字',   defaultOpen: true },
  appearance: { key: 'appearance', label: '外观',   defaultOpen: true },
  layout:     { key: 'layout',    label: '布局',   defaultOpen: false },
  behavior:   { key: 'behavior',  label: '行为',   defaultOpen: false },
  chart:      { key: 'chart',     label: '图表',   defaultOpen: true },
  header:     { key: 'header',    label: '表头样式', defaultOpen: true },
  body:       { key: 'body',      label: '数据行', defaultOpen: true },
  column:     { key: 'column',    label: '列配置', defaultOpen: false },
  pagination: { key: 'pagination', label: '分页',  defaultOpen: false },
  advanced:   { key: 'advanced',  label: '高级',   defaultOpen: false },
};
