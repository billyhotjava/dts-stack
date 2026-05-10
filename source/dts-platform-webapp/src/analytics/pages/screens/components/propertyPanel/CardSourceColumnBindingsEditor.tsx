import { message } from 'antd';
import { ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-react';
import type { ColumnEntry, SourceColumnOption } from './types';

export function CardSourceColumnBindingsEditor({
    title,
    sourceCols,
    columns,
    defaultAlign,
    onColumnsChange,
}: {
    title: string;
    sourceCols: SourceColumnOption[];
    columns: ColumnEntry[] | undefined;
    defaultAlign: NonNullable<ColumnEntry['align']>;
    onColumnsChange: (value: ColumnEntry[] | undefined) => void;
}) {
    const fallbackColumns: ColumnEntry[] = sourceCols.map((item) => ({ source: item.name }));
    const effectiveColumns = columns ?? fallbackColumns;
    const usedSourceSet = new Set(effectiveColumns.map((item) => item.source));
    const unboundSources = sourceCols.filter((item) => !usedSourceSet.has(item.name));

    const updateColumn = (index: number, patch: Partial<ColumnEntry>) => {
        const next = effectiveColumns.map((item, i) => (i === index ? { ...item, ...patch } : item));
        onColumnsChange(next);
    };

    const handleSourceChange = (index: number, nextSource: string) => {
        const duplicate = effectiveColumns.some((item, i) => i !== index && item.source === nextSource);
        if (duplicate) {
            message.warning('该字段已被绑定，请选择其他字段');
            return;
        }
        updateColumn(index, { source: nextSource });
    };

    const handleMove = (index: number, direction: -1 | 1) => {
        const target = index + direction;
        if (target < 0 || target >= effectiveColumns.length) return;
        const next = [...effectiveColumns];
        const [current] = next.splice(index, 1);
        next.splice(target, 0, current);
        onColumnsChange(next);
    };

    const handleRemove = (index: number) => {
        onColumnsChange(effectiveColumns.filter((_, i) => i !== index));
    };

    return (
        <>
            <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginTop: 8, marginBottom: 4 }}>
                {title}
            </div>
            <div style={{ display: 'flex', gap: 6, marginBottom: 8 }}>
                <button
                    type="button"
                    className="header-btn inline-flex items-center gap-1.5 min-h-8 rounded-md border border-border-default bg-surface-card text-text-primary px-3 text-xs cursor-pointer hover:border-brand hover:bg-brand/10 disabled:opacity-40 disabled:cursor-not-allowed"
                    onClick={() => {
                        if (!unboundSources[0]) return;
                        onColumnsChange([...effectiveColumns, { source: unboundSources[0].name, align: defaultAlign }]);
                    }}
                    disabled={unboundSources.length === 0}
                    title="追加一个未绑定字段"
                >
                    <Plus size={13} aria-hidden="true" />
                    添加列
                </button>
                <button
                    type="button"
                    className="header-btn inline-flex items-center gap-1.5 min-h-8 rounded-md border border-border-default bg-surface-card text-text-primary px-3 text-xs cursor-pointer hover:border-brand hover:bg-brand/10 disabled:opacity-40 disabled:cursor-not-allowed"
                    onClick={() => onColumnsChange(undefined)}
                    title="恢复默认映射（按数据源原始字段）"
                >
                    恢复默认
                </button>
                <button
                    type="button"
                    className="header-btn inline-flex items-center gap-1.5 min-h-8 rounded-md border border-border-default bg-surface-card text-text-primary px-3 text-xs cursor-pointer hover:border-brand hover:bg-brand/10 disabled:opacity-40 disabled:cursor-not-allowed"
                    onClick={() => onColumnsChange([])}
                    disabled={effectiveColumns.length === 0}
                    title="清空当前映射"
                >
                    清空
                </button>
            </div>
            {effectiveColumns.length === 0 && (
                <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginTop: 4, marginBottom: 8 }}>
                    当前无字段绑定，请点击“添加列”。
                </div>
            )}
            {effectiveColumns.map((entry, index) => {
                const sourceMeta = sourceCols.find((item) => item.name === entry.source);
                return (
                    <div key={`${entry.source}-${index}`} style={{
                        border: '1px solid rgba(255,255,255,0.06)',
                        borderRadius: 4,
                        padding: '6px',
                        marginBottom: 6,
                    }}>
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                            <span style={{ fontSize: 11, color: 'var(--color-text-secondary)' }}>
                                列 {index + 1}{sourceMeta ? '' : ' (失效字段)'}
                            </span>
                            <div style={{ display: 'flex', gap: 4 }}>
                                <button
                                    type="button"
                                    className="header-btn inline-flex items-center gap-1.5 min-h-8 rounded-md border border-border-default bg-surface-card text-text-primary px-3 text-xs cursor-pointer hover:border-brand hover:bg-brand/10 disabled:opacity-40 disabled:cursor-not-allowed"
                                    onClick={() => handleMove(index, -1)}
                                    disabled={index === 0}
                                    title="上移"
                                >
                                    <ArrowUp size={13} aria-hidden="true" />
                                </button>
                                <button
                                    type="button"
                                    className="header-btn inline-flex items-center gap-1.5 min-h-8 rounded-md border border-border-default bg-surface-card text-text-primary px-3 text-xs cursor-pointer hover:border-brand hover:bg-brand/10 disabled:opacity-40 disabled:cursor-not-allowed"
                                    onClick={() => handleMove(index, 1)}
                                    disabled={index >= effectiveColumns.length - 1}
                                    title="下移"
                                >
                                    <ArrowDown size={13} aria-hidden="true" />
                                </button>
                                <button
                                    type="button"
                                    className="header-btn inline-flex items-center gap-1.5 min-h-8 rounded-md border border-border-default bg-surface-card text-text-primary px-3 text-xs cursor-pointer hover:border-brand hover:bg-brand/10 disabled:opacity-40 disabled:cursor-not-allowed"
                                    onClick={() => handleRemove(index)}
                                    title="删除该列"
                                >
                                    <Trash2 size={13} aria-hidden="true" />
                                    删除
                                </button>
                            </div>
                        </div>
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">绑定字段</label>
                            <select
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                value={entry.source}
                                onChange={(e) => handleSourceChange(index, e.target.value)}
                            >
                                {!sourceMeta && (
                                    <option value={entry.source}>{entry.source} (失效字段)</option>
                                )}
                                {sourceCols.map((item) => (
                                    <option key={item.name} value={item.name}>
                                        {(item.displayName || item.name)} ({item.name})
                                    </option>
                                ))}
                            </select>
                        </div>
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">表头标题</label>
                            <input
                                type="text"
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                placeholder={sourceMeta?.displayName || entry.source}
                                value={entry.alias || ''}
                                onChange={(e) => {
                                    const nextAlias = e.target.value;
                                    if (nextAlias) {
                                        updateColumn(index, { alias: nextAlias });
                                        return;
                                    }
                                    const next = effectiveColumns.map((item, i) => {
                                        if (i !== index) return item;
                                        const { alias: _alias, ...rest } = item;
                                        return rest;
                                    });
                                    onColumnsChange(next);
                                }}
                            />
                        </div>
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">对齐</label>
                            <select
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                value={(entry.align as string) || defaultAlign}
                                onChange={(e) => updateColumn(index, { align: e.target.value as ColumnEntry['align'] })}
                            >
                                <option value="left">左</option>
                                <option value="center">中</option>
                                <option value="right">右</option>
                            </select>
                        </div>
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">自动换行</label>
                            <input
                                type="checkbox"
                                checked={entry.wrap === true}
                                onChange={(e) => updateColumn(index, { wrap: e.target.checked })}
                            />
                        </div>
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">列宽(%)</label>
                            <input
                                type="number"
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                min={5}
                                max={100}
                                value={typeof entry.width === 'number' ? entry.width : ''}
                                placeholder="自动"
                                onChange={(e) => {
                                    const raw = e.target.value.trim();
                                    if (!raw) {
                                        updateColumn(index, { width: undefined });
                                        return;
                                    }
                                    const parsed = Number(raw);
                                    updateColumn(index, {
                                        width: Number.isFinite(parsed) ? Math.max(5, Math.min(100, parsed)) : undefined,
                                    });
                                }}
                            />
                        </div>
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">格式化</label>
                            <select
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                value={(entry.formatter as string) || 'auto'}
                                onChange={(e) => updateColumn(index, { formatter: e.target.value as ColumnEntry['formatter'] })}
                            >
                                <option value="auto">自动</option>
                                <option value="string">文本</option>
                                <option value="number">数字</option>
                                <option value="percent">百分比</option>
                                <option value="date">日期时间</option>
                            </select>
                        </div>
                    </div>
                );
            })}
        </>
    );
}
