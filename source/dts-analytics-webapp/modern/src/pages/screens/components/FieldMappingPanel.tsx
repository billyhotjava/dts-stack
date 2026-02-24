import { useCallback, useMemo, useState } from 'react';
import type { ComponentType, FieldMapping, FieldMappingAggregation } from '../types';
import {
    type MappingSlot,
    columnTypeIcon,
    detectStaleFields,
    getMappingSlots,
    isMappable,
} from '../hooks/fieldMappingTransform';

interface SourceColumn {
    name: string;
    displayName: string;
    baseType?: string;
}

interface FieldMappingPanelProps {
    componentType: ComponentType;
    sourceColumns: SourceColumn[];
    mapping: FieldMapping;
    onChange: (mapping: FieldMapping) => void;
}

/**
 * Visual field mapping panel — users click source columns to assign them to chart slots.
 * Uses click-to-assign instead of drag-and-drop for simplicity and Chrome 95 compatibility.
 */
export function FieldMappingPanel({
    componentType,
    sourceColumns,
    mapping,
    onChange,
}: FieldMappingPanelProps) {
    const slots = useMemo(() => getMappingSlots(componentType), [componentType]);
    const [activeSlot, setActiveSlot] = useState<keyof FieldMapping | null>(null);

    const staleFields = useMemo(
        () => detectStaleFields(mapping, sourceColumns),
        [mapping, sourceColumns],
    );

    const handleColumnClick = useCallback((colName: string) => {
        if (!activeSlot) return;
        const slot = slots.find(s => s.key === activeSlot);
        if (!slot) return;

        const next = { ...mapping };
        if (slot.key === 'measures' && slot.multi) {
            const current = next.measures ?? [];
            if (current.includes(colName)) {
                next.measures = current.filter(m => m !== colName);
            } else {
                next.measures = [...current, colName];
            }
        } else if (slot.key === 'measures') {
            next.measures = [colName];
        } else {
            (next as Record<string, unknown>)[slot.key] = colName;
        }
        onChange(next);
    }, [activeSlot, mapping, onChange, slots]);

    const handleClearSlot = useCallback((slotKey: keyof FieldMapping) => {
        const next = { ...mapping };
        if (slotKey === 'measures') {
            next.measures = [];
        } else {
            delete (next as Record<string, unknown>)[slotKey];
        }
        onChange(next);
    }, [mapping, onChange]);

    const handleAggregationChange = useCallback((agg: FieldMappingAggregation) => {
        onChange({ ...mapping, aggregation: agg });
    }, [mapping, onChange]);

    const handleSortChange = useCallback((field: string, order: 'asc' | 'desc') => {
        onChange({ ...mapping, sortField: field || undefined, sortOrder: order });
    }, [mapping, onChange]);

    const getSlotValue = (slot: MappingSlot): string[] => {
        if (slot.key === 'measures') return mapping.measures ?? [];
        const val = mapping[slot.key];
        return val ? [String(val)] : [];
    };

    const isColumnAssigned = (colName: string): boolean => {
        if (mapping.dimension === colName) return true;
        if (mapping.measures?.includes(colName)) return true;
        if (mapping.groupBy === colName) return true;
        if (mapping.sizeField === colName) return true;
        return false;
    };

    if (!isMappable(componentType) || sourceColumns.length === 0) return null;

    return (
        <div className="field-mapping-panel">
            {/* Stale field warnings */}
            {staleFields.length > 0 && (
                <div className="field-mapping-warning">
                    ⚠ 字段已失效: {staleFields.join(', ')}
                </div>
            )}

            <div className="field-mapping-layout">
                {/* Left: source columns */}
                <div className="field-mapping-source">
                    <div className="field-mapping-section-label">数据字段</div>
                    {sourceColumns.map((col) => {
                        const assigned = isColumnAssigned(col.name);
                        const stale = staleFields.includes(col.name);
                        return (
                            <div
                                key={col.name}
                                className={`field-mapping-col ${assigned ? 'assigned' : ''} ${activeSlot ? 'clickable' : ''} ${stale ? 'stale' : ''}`}
                                onClick={() => handleColumnClick(col.name)}
                                title={`${col.displayName} (${col.baseType || 'unknown'})`}
                            >
                                <span className="field-mapping-col-icon">{columnTypeIcon(col.baseType || '')}</span>
                                <span className="field-mapping-col-name">{col.displayName || col.name}</span>
                            </div>
                        );
                    })}
                </div>

                {/* Right: target slots */}
                <div className="field-mapping-target">
                    <div className="field-mapping-section-label">图表映射</div>
                    {slots.map((slot) => {
                        const values = getSlotValue(slot);
                        const isActive = activeSlot === slot.key;
                        return (
                            <div
                                key={slot.key}
                                className={`field-mapping-slot ${isActive ? 'active' : ''} ${values.length > 0 ? 'filled' : ''}`}
                                onClick={() => setActiveSlot(isActive ? null : slot.key)}
                            >
                                <div className="field-mapping-slot-header">
                                    <span className="field-mapping-slot-label">
                                        {slot.label}
                                        {slot.required && <span className="field-mapping-required">*</span>}
                                    </span>
                                    {values.length > 0 && (
                                        <button
                                            type="button"
                                            className="field-mapping-slot-clear"
                                            onClick={(e) => { e.stopPropagation(); handleClearSlot(slot.key); }}
                                            title="清除"
                                        >
                                            ×
                                        </button>
                                    )}
                                </div>
                                <div className="field-mapping-slot-values">
                                    {values.length > 0
                                        ? values.map(v => (
                                            <span key={v} className={`field-mapping-chip ${staleFields.includes(v) ? 'stale' : ''}`}>
                                                {sourceColumns.find(c => c.name === v)?.displayName || v}
                                            </span>
                                        ))
                                        : <span className="field-mapping-slot-empty">
                                            {isActive ? '点击左侧字段分配' : '点击此处激活'}
                                        </span>
                                    }
                                </div>
                            </div>
                        );
                    })}
                </div>
            </div>

            {/* Aggregation & sort controls */}
            <div className="field-mapping-options">
                <div className="field-mapping-option-row">
                    <label>聚合</label>
                    <select
                        value={mapping.aggregation ?? 'sum'}
                        onChange={(e) => handleAggregationChange(e.target.value as FieldMappingAggregation)}
                    >
                        <option value="sum">求和</option>
                        <option value="count">计数</option>
                        <option value="avg">平均值</option>
                        <option value="min">最小值</option>
                        <option value="max">最大值</option>
                    </select>
                </div>
                <div className="field-mapping-option-row">
                    <label>排序</label>
                    <select
                        value={mapping.sortField ?? ''}
                        onChange={(e) => handleSortChange(e.target.value, mapping.sortOrder ?? 'asc')}
                    >
                        <option value="">无</option>
                        {sourceColumns.map(col => (
                            <option key={col.name} value={col.name}>{col.displayName || col.name}</option>
                        ))}
                    </select>
                    {mapping.sortField && (
                        <select
                            value={mapping.sortOrder ?? 'asc'}
                            onChange={(e) => handleSortChange(mapping.sortField ?? '', e.target.value as 'asc' | 'desc')}
                        >
                            <option value="asc">升序</option>
                            <option value="desc">降序</option>
                        </select>
                    )}
                </div>
            </div>

            <style>{`
                .field-mapping-panel {
                    margin-top: 4px;
                }
                .field-mapping-warning {
                    background: rgba(239, 68, 68, 0.15);
                    color: #f87171;
                    padding: 6px 10px;
                    border-radius: 6px;
                    font-size: 11px;
                    margin-bottom: 8px;
                }
                .field-mapping-layout {
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 8px;
                }
                .field-mapping-section-label {
                    font-size: 11px;
                    color: #94a3b8;
                    margin-bottom: 6px;
                    font-weight: 600;
                }
                .field-mapping-source, .field-mapping-target {
                    display: flex;
                    flex-direction: column;
                    gap: 3px;
                }
                .field-mapping-col {
                    display: flex;
                    align-items: center;
                    gap: 6px;
                    padding: 5px 8px;
                    background: rgba(15, 23, 42, 0.6);
                    border: 1px solid rgba(148, 163, 184, 0.15);
                    border-radius: 5px;
                    font-size: 12px;
                    color: #cbd5e1;
                    cursor: default;
                    transition: all 0.15s;
                }
                .field-mapping-col.clickable {
                    cursor: pointer;
                }
                .field-mapping-col.clickable:hover {
                    border-color: #00d4ff;
                    background: rgba(0, 212, 255, 0.08);
                }
                .field-mapping-col.assigned {
                    border-color: rgba(0, 212, 255, 0.4);
                    color: #7dd3fc;
                }
                .field-mapping-col.stale {
                    border-color: rgba(239, 68, 68, 0.4);
                    color: #f87171;
                }
                .field-mapping-col-icon {
                    font-size: 10px;
                    min-width: 16px;
                    text-align: center;
                    opacity: 0.7;
                }
                .field-mapping-col-name {
                    overflow: hidden;
                    text-overflow: ellipsis;
                    white-space: nowrap;
                }
                .field-mapping-slot {
                    padding: 6px 8px;
                    background: rgba(15, 23, 42, 0.4);
                    border: 1px dashed rgba(148, 163, 184, 0.2);
                    border-radius: 5px;
                    cursor: pointer;
                    transition: all 0.15s;
                }
                .field-mapping-slot:hover,
                .field-mapping-slot.active {
                    border-color: #00d4ff;
                    border-style: solid;
                }
                .field-mapping-slot.active {
                    background: rgba(0, 212, 255, 0.06);
                }
                .field-mapping-slot.filled {
                    border-style: solid;
                    border-color: rgba(0, 212, 255, 0.3);
                }
                .field-mapping-slot-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    margin-bottom: 4px;
                }
                .field-mapping-slot-label {
                    font-size: 11px;
                    color: #94a3b8;
                    font-weight: 500;
                }
                .field-mapping-required {
                    color: #f87171;
                    margin-left: 2px;
                }
                .field-mapping-slot-clear {
                    background: none;
                    border: none;
                    color: #64748b;
                    font-size: 14px;
                    cursor: pointer;
                    padding: 0 2px;
                    line-height: 1;
                }
                .field-mapping-slot-clear:hover {
                    color: #f87171;
                }
                .field-mapping-slot-values {
                    display: flex;
                    flex-wrap: wrap;
                    gap: 4px;
                    min-height: 22px;
                    align-items: center;
                }
                .field-mapping-chip {
                    display: inline-block;
                    padding: 2px 8px;
                    background: rgba(0, 212, 255, 0.15);
                    color: #7dd3fc;
                    border-radius: 10px;
                    font-size: 11px;
                }
                .field-mapping-chip.stale {
                    background: rgba(239, 68, 68, 0.15);
                    color: #f87171;
                    text-decoration: line-through;
                }
                .field-mapping-slot-empty {
                    font-size: 11px;
                    color: #475569;
                    font-style: italic;
                }
                .field-mapping-options {
                    display: flex;
                    gap: 12px;
                    margin-top: 8px;
                    flex-wrap: wrap;
                }
                .field-mapping-option-row {
                    display: flex;
                    align-items: center;
                    gap: 4px;
                }
                .field-mapping-option-row label {
                    font-size: 11px;
                    color: #94a3b8;
                }
                .field-mapping-option-row select {
                    background: rgba(15, 23, 42, 0.8);
                    border: 1px solid rgba(148, 163, 184, 0.2);
                    border-radius: 4px;
                    padding: 3px 6px;
                    color: #cbd5e1;
                    font-size: 11px;
                }
            `}</style>
        </div>
    );
}

export { isMappable };
