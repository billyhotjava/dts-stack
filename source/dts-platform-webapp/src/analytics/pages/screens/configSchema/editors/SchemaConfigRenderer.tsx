import React, { useMemo } from 'react';
import { Collapse } from 'antd';

import type { ComponentConfigSchema, ConfigField, ConfigGroup } from '../types';
import { STANDARD_GROUPS } from '../types';
import type { ScreenThemeTokens } from '../../screenThemes';
import { getThemeTokens } from '../../screenThemes';
import type { ScreenTheme } from '../../types';

import FieldEditor from './FieldEditor';
import ColorArrayEditor from './ColorArrayEditor';
import AxisConfigEditor from './AxisConfigEditor';
import LegendConfigEditor from './LegendConfigEditor';
import ColumnStyleEditor from './ColumnStyleEditor';

import type { AxisConfig } from './AxisConfigEditor';
import type { LegendConfig } from './LegendConfigEditor';
import type { ColumnConfig } from './ColumnStyleEditor';

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

export interface SchemaConfigRendererProps {
  schema: ComponentConfigSchema;
  config: Record<string, unknown>;
  onChange: (key: string, value: unknown) => void;
  theme?: string;
  /** 仅渲染指定 group(白名单)。与 hideGroups 互斥。 */
  onlyGroups?: string[];
  /** 排除指定 group(黑名单)。 */
  hideGroups?: string[];
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function resolveNestedValue(obj: Record<string, unknown>, path: string): unknown {
  const parts = path.split('.');
  let cur: unknown = obj;
  for (const p of parts) {
    if (cur == null || typeof cur !== 'object') return undefined;
    cur = (cur as Record<string, unknown>)[p];
  }
  return cur;
}

function resolveTokenValue(tokens: ScreenThemeTokens | undefined, tokenKey: string | undefined): string | undefined {
  if (!tokens || !tokenKey) return undefined;
  const parts = tokenKey.split('.');
  let cur: unknown = tokens;
  for (const p of parts) {
    if (cur == null || typeof cur !== 'object') return undefined;
    cur = (cur as Record<string, unknown>)[p];
  }
  return typeof cur === 'string' ? cur : undefined;
}

// ---------------------------------------------------------------------------
// FieldRow — label + content wrapper for simple fields
// ---------------------------------------------------------------------------

const FieldRow: React.FC<{ label: string; children: React.ReactNode; fullWidth?: boolean }> = ({
  label,
  children,
  fullWidth = false,
}) => {
  if (fullWidth) {
    return (
      <div style={{ display: 'grid', gap: 6, marginBottom: 10 }}>
        <span
          style={{
            fontSize: 12,
            color: 'var(--color-text-secondary)',
            lineHeight: '18px',
          }}
        >
          {label}
        </span>
        <div style={{ width: '100%', minWidth: 0 }}>{children}</div>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', alignItems: 'flex-start', marginBottom: 8 }}>
      <span
        style={{
          width: 80,
          flexShrink: 0,
          fontSize: 12,
          color: 'var(--color-text-secondary)',
          lineHeight: '28px',
        }}
      >
        {label}
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>{children}</div>
    </div>
  );
};

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

const SchemaConfigRenderer: React.FC<SchemaConfigRendererProps> = ({ schema, config, onChange, theme, onlyGroups, hideGroups }) => {
  const tokens = useMemo(() => getThemeTokens(theme as ScreenTheme), [theme]);

  const groupFilter = useMemo(() => {
    if (onlyGroups && onlyGroups.length > 0) {
      const allow = new Set(onlyGroups);
      return (g: string) => allow.has(g);
    }
    if (hideGroups && hideGroups.length > 0) {
      const deny = new Set(hideGroups);
      return (g: string) => !deny.has(g);
    }
    return () => true;
  }, [onlyGroups, hideGroups]);

  // Group fields
  const groupOrder: ConfigGroup[] = useMemo(() => {
    let baseGroups: ConfigGroup[];
    if (schema.groups && schema.groups.length > 0) {
      baseGroups = schema.groups;
    } else {
      // Derive from STANDARD_GROUPS in insertion order
      const groupKeys = new Set<string>();
      for (const f of schema.fields) {
        groupKeys.add(f.group ?? 'content');
      }
      const result: ConfigGroup[] = [];
      for (const key of Object.keys(STANDARD_GROUPS)) {
        if (groupKeys.has(key)) result.push(STANDARD_GROUPS[key]);
      }
      for (const key of groupKeys) {
        if (!STANDARD_GROUPS[key]) result.push({ key, label: key, defaultOpen: false });
      }
      baseGroups = result;
    }
    return baseGroups.filter((g) => groupFilter(g.key));
  }, [schema, groupFilter]);

  const fieldsByGroup = useMemo(() => {
    const map = new Map<string, ConfigField[]>();
    for (const f of schema.fields) {
      const gk = f.group ?? 'content';
      if (!map.has(gk)) map.set(gk, []);
      map.get(gk)!.push(f);
    }
    return map;
  }, [schema]);

  const renderField = (field: ConfigField) => {
    // showIf check
    if (field.showIf && !field.showIf(config)) return null;

    const val = resolveNestedValue(config, field.key);
    const themeDefault = resolveTokenValue(tokens, field.themeTokenKey);

    // Complex editors
    if (field.type === 'axis-config') {
      return (
        <AxisConfigEditor
          key={field.key}
          value={val as AxisConfig | undefined}
          onChange={(v) => onChange(field.key, v)}
          label={field.label}
        />
      );
    }

    if (field.type === 'color-array') {
      return (
        <FieldRow key={field.key} label={field.label}>
          <ColorArrayEditor
            value={(val as string[]) ?? []}
            onChange={(v) => onChange(field.key, v)}
          />
        </FieldRow>
      );
    }

    if (field.type === 'legend-config') {
      return (
        <FieldRow key={field.key} label={field.label}>
          <LegendConfigEditor
            value={val as LegendConfig | undefined}
            onChange={(v) => onChange(field.key, v)}
          />
        </FieldRow>
      );
    }

    if (field.type === 'column-style') {
      // 从组件 config 上提取 SQL 数据源派生的列(由 ComponentRenderer 执行 SQL 后回写)。
      // 当存在 _sourceColumns 时,ColumnStyleEditor 自动启用"字段下拉" + "一键同步列"。
      const sourceColumns = (config as Record<string, unknown>)?._sourceColumns as
        | Array<{ name: string; displayName?: string; baseType?: string }>
        | undefined;
      return (
        <FieldRow key={field.key} label={field.label}>
          <ColumnStyleEditor
            value={val as ColumnConfig[] | undefined}
            onChange={(v) => onChange(field.key, v)}
            sourceColumns={sourceColumns}
          />
        </FieldRow>
      );
    }

    // Default — FieldEditor
    return (
      <FieldRow key={field.key} label={field.label} fullWidth={field.type === 'image-url'}>
        <FieldEditor
          field={field}
          value={val}
          onChange={(v) => onChange(field.key, v)}
          themeDefault={themeDefault}
        />
      </FieldRow>
    );
  };

  // Build collapse items
  const collapseItems = useMemo(() => {
    const items: { key: string; label: string; children: React.ReactNode }[] = [];
    for (const group of groupOrder) {
      const fields = fieldsByGroup.get(group.key);
      if (!fields || fields.length === 0) continue;
      items.push({
        key: group.key,
        label: group.label,
        children: <div>{fields.map(renderField)}</div>,
      });
    }
    return items;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupOrder, fieldsByGroup, config, tokens]);

  const defaultActiveKey = useMemo(
    () => groupOrder.filter((g) => g.defaultOpen !== false).map((g) => g.key),
    [groupOrder],
  );

  return <Collapse size="small" items={collapseItems} defaultActiveKey={defaultActiveKey} />;
};

export default SchemaConfigRenderer;
