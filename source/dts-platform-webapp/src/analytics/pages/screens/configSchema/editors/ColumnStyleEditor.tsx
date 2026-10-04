import React, { useMemo, useRef } from 'react';
import { Button, Collapse, Input, InputNumber, Radio, Select, Switch, Tooltip, message } from 'antd';
import { DeleteOutlined, PlusOutlined, SyncOutlined } from '@ant-design/icons';

export interface ColumnConfig {
  source?: string;
  key?: string;
  alias?: string;
  label?: string;
  width?: number;
  widthUnit?: 'px' | 'percent' | '%';
  align?: 'left' | 'center' | 'right';
  headerAlign?: 'left' | 'center' | 'right';
  wrap?: boolean;
  formatter?: 'auto' | 'string' | 'number' | 'percent' | 'date';
  frozen?: boolean;
  sortable?: boolean;
}

/**
 * 从数据源派生的列元数据 — 由 SQL 执行后回写到 component.config._sourceColumns。
 * 这里只是 ColumnStyleEditor 消费的子集形状。
 */
export interface SourceColumnMeta {
  name: string;
  displayName?: string;
  baseType?: string;
}

export interface ColumnStyleEditorProps {
  value?: ColumnConfig[];
  onChange: (v: ColumnConfig[]) => void;
  /**
   * 数据源派生的列(SQL alias / 字段名)。
   * 提供时,字段 Key 改为下拉,并支持"从数据源同步列"一键填充。
   */
  sourceColumns?: SourceColumnMeta[];
}

const LabelRow: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
    <span style={{ fontSize: 12, flex: '0 0 72px', color: 'var(--color-text-secondary)' }}>{label}</span>
    <div style={{ flex: 1, minWidth: 0 }}>{children}</div>
  </div>
);

function resolveEditableColumnKey(col: ColumnConfig): string | undefined {
  return col.key || col.source;
}

function resolveEditableColumnLabel(col: ColumnConfig): string | undefined {
  return col.label || col.alias;
}

const ColumnStyleEditor: React.FC<ColumnStyleEditorProps> = ({ value = [], onChange, sourceColumns }) => {
  // 为每个列生成一个稳定的内部 uid,只在内部使用,不污染数据。
  const uidsRef = useRef<string[]>([]);
  const uids = useMemo(() => {
    const cur = uidsRef.current;
    if (cur.length < value.length) {
      while (cur.length < value.length) {
        cur.push(`col-${Math.random().toString(36).slice(2, 10)}`);
      }
    } else if (cur.length > value.length) {
      cur.length = value.length;
    }
    return cur.slice();
  }, [value.length]);

  const hasSource = Array.isArray(sourceColumns) && sourceColumns.length > 0;
  const sourceMap = useMemo(() => {
    const map = new Map<string, SourceColumnMeta>();
    (sourceColumns || []).forEach((s) => map.set(s.name, s));
    return map;
  }, [sourceColumns]);

  const updateAt = (idx: number, patch: Partial<ColumnConfig>) => {
    const next = [...value];
    next[idx] = { ...next[idx], ...patch };
    onChange(next);
  };

  const updateColumnKey = (idx: number, key: string) => {
    const oldKey = resolveEditableColumnKey(value[idx] ?? {});
    const oldSource = oldKey ? sourceMap.get(oldKey) : undefined;
    const nextSource = sourceMap.get(key);
    const currentLabel = resolveEditableColumnLabel(value[idx] ?? {});
    const shouldSyncLabel = !currentLabel
      || currentLabel === oldKey
      || currentLabel === oldSource?.displayName;
    const syncedLabel = nextSource?.displayName || key || undefined;
    updateAt(idx, {
      key,
      source: key,
      ...(shouldSyncLabel ? {
        label: syncedLabel,
        alias: syncedLabel,
      } : {}),
    });
  };

  const updateColumnLabel = (idx: number, label: string) => {
    updateAt(idx, {
      label: label || undefined,
      alias: label || undefined,
    });
  };

  const removeAt = (idx: number) => {
    uidsRef.current.splice(idx, 1);
    onChange(value.filter((_, i) => i !== idx));
  };

  const add = () => {
    if (hasSource) {
      // 自动选第一个未绑定的源字段
      const usedKeys = new Set(value.map((v) => v.key).filter(Boolean) as string[]);
      value.forEach((v) => {
        if (v.source) usedKeys.add(v.source);
      });
      const next = sourceColumns!.find((s) => !usedKeys.has(s.name));
      if (next) {
        onChange([...value, {
          key: next.name,
          source: next.name,
          label: next.displayName || next.name,
          alias: next.displayName || next.name,
          sortable: true,
        }]);
        return;
      }
    }
    onChange([...value, { sortable: true }]);
  };

  const syncFromSource = () => {
    if (!hasSource) return;
    // 保留已有列的 width/align/frozen/sortable/label 等用户配置,
    // 仅按 sourceColumns 重建顺序与 key,补齐缺失的列。
    const byKey = new Map<string, ColumnConfig>();
    value.forEach((c) => {
      const key = resolveEditableColumnKey(c);
      if (key) byKey.set(key, c);
    });
    const next: ColumnConfig[] = sourceColumns!.map((s) => {
      const exist = byKey.get(s.name);
      if (exist) {
        return {
          ...exist,
          key: s.name,
          source: s.name,
          label: resolveEditableColumnLabel(exist) || s.displayName || s.name,
          alias: resolveEditableColumnLabel(exist) || s.displayName || s.name,
        };
      }
      return {
        key: s.name,
        source: s.name,
        label: s.displayName || s.name,
        alias: s.displayName || s.name,
        sortable: true,
      };
    });
    // 重置 uid (列顺序变更时避免 Collapse 状态错位)
    uidsRef.current = next.map(() => `col-${Math.random().toString(36).slice(2, 10)}`);
    onChange(next);
    message.success(`已从数据源同步 ${next.length} 列`);
  };

  const items = value.map((col, idx) => {
    const columnKey = resolveEditableColumnKey(col);
    const columnLabel = resolveEditableColumnLabel(col);
    const sourceMeta = columnKey ? sourceMap.get(columnKey) : undefined;
    const isBroken = hasSource && columnKey && !sourceMeta;
    return ({
      key: uids[idx] ?? `col-${idx}`,
      label: columnLabel || sourceMeta?.displayName || columnKey || `列 ${idx + 1}`,
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
          <LabelRow label="数据字段">
            {hasSource ? (
              <Select
                size="small"
                value={columnKey}
                onChange={(v) => updateColumnKey(idx, v)}
                style={{ width: '100%' }}
                placeholder="选择数据源字段"
                showSearch
                optionFilterProp="label"
                status={isBroken ? 'error' : undefined}
                options={[
                  ...(isBroken ? [{ label: `${columnKey} (已失效)`, value: columnKey as string }] : []),
                  ...sourceColumns!.map((s) => ({
                    label: s.displayName && s.displayName !== s.name ? `${s.displayName} (${s.name})` : s.name,
                    value: s.name,
                  })),
                ]}
              />
            ) : (
              <Input
                size="small"
                value={columnKey}
                onChange={(e) => updateColumnKey(idx, e.target.value)}
                placeholder="字段标识"
              />
            )}
          </LabelRow>
          <LabelRow label="显示标题">
            <Input
              size="small"
              value={columnLabel}
              onChange={(e) => updateColumnLabel(idx, e.target.value)}
              placeholder={sourceMeta?.displayName || columnKey || '列标题'}
            />
          </LabelRow>
          <LabelRow label="宽度(px)">
            <InputNumber
              size="small"
              value={col.width}
              onChange={(v) => updateAt(idx, { width: v ?? undefined, widthUnit: 'px' })}
              min={40}
              max={600}
              style={{ width: '100%' }}
              placeholder="自适应"
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
            <Switch size="small" checked={col.sortable !== false} onChange={(v) => updateAt(idx, { sortable: v })} />
          </LabelRow>
          <LabelRow label="自动换行">
            <Switch size="small" checked={!!col.wrap} onChange={(v) => updateAt(idx, { wrap: v })} />
          </LabelRow>
          <LabelRow label="格式化">
            <Select
              size="small"
              value={col.formatter || 'auto'}
              onChange={(v) => updateAt(idx, { formatter: v })}
              style={{ width: '100%' }}
              options={[
                { label: '自动', value: 'auto' },
                { label: '文本', value: 'string' },
                { label: '数字', value: 'number' },
                { label: '百分比', value: 'percent' },
                { label: '日期时间', value: 'date' },
              ]}
            />
          </LabelRow>
        </div>
      ),
    });
  });

  return (
    <div>
      {hasSource ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6, fontSize: 11, color: 'var(--color-text-secondary)' }}>
          <span style={{ flex: 1 }}>已识别 {sourceColumns!.length} 个数据源字段</span>
          <Tooltip title="按当前 SQL 列重建列配置(保留已配置的宽度/对齐等)">
            <Button size="small" type="dashed" icon={<SyncOutlined />} onClick={syncFromSource}>
              从数据源同步
            </Button>
          </Tooltip>
        </div>
      ) : null}
      <Collapse
        size="small"
        ghost
        bordered={false}
        items={items}
        className="schema-config-collapse"
      />
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
