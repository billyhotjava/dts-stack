// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import type {
    ChartMarkArea,
    ChartMarkLine,
    ScreenComponent,
    SeriesConditionalColor,
} from '../../types';

/** Chart annotation config: markLines, markAreas, conditionalColors */
export function ChartAnnotationConfig({ component, onChange }: {
    component: ScreenComponent;
    onChange: (key: string, value: unknown) => void;
}) {
    const { config } = component;
    const markLines = (config.markLines as ChartMarkLine[]) ?? [];
    const markAreas = (config.markAreas as ChartMarkArea[]) ?? [];
    const conditionalColors = (config.conditionalColors as SeriesConditionalColor[]) ?? [];

    const addMarkLine = () => {
        if (markLines.length >= 5) return;
        onChange('markLines', [...markLines, { type: 'value' as const, value: 0, name: '', color: '#ff6b6b', lineStyle: 'dashed' as const, axis: 'y' as const }]);
    };
    const updateMarkLine = (idx: number, patch: Partial<ChartMarkLine>) => {
        onChange('markLines', markLines.map((ml, i) => i === idx ? { ...ml, ...patch } : ml));
    };
    const removeMarkLine = (idx: number) => {
        onChange('markLines', markLines.filter((_, i) => i !== idx));
    };
    const addMarkArea = () => {
        if (markAreas.length >= 3) return;
        onChange('markAreas', [...markAreas, { from: 0, to: 100, name: '', color: 'rgba(255,107,107,0.15)', axis: 'y' as const }]);
    };
    const updateMarkArea = (idx: number, patch: Partial<ChartMarkArea>) => {
        onChange('markAreas', markAreas.map((ma, i) => i === idx ? { ...ma, ...patch } : ma));
    };
    const removeMarkArea = (idx: number) => {
        onChange('markAreas', markAreas.filter((_, i) => i !== idx));
    };
    const addConditionalColor = () => {
        if (conditionalColors.length >= 5) return;
        onChange('conditionalColors', [...conditionalColors, { operator: '>' as const, value: 0, color: '#ef4444' }]);
    };
    const updateConditionalColor = (idx: number, patch: Partial<SeriesConditionalColor>) => {
        onChange('conditionalColors', conditionalColors.map((cc, i) => i === idx ? { ...cc, ...patch } : cc));
    };
    const removeConditionalColor = (idx: number) => {
        onChange('conditionalColors', conditionalColors.filter((_, i) => i !== idx));
    };

    return (
        <div style={{ display: 'grid', gap: 8 }}>
            <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', fontWeight: 600 }}>辅助线 ({markLines.length}/5)</div>
            {markLines.map((ml, idx) => (
                <div key={idx} style={{ display: 'grid', gridTemplateColumns: 'auto 1fr 60px 60px auto', gap: 4, alignItems: 'center' }}>
                    <select className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" style={{ fontSize: 11, padding: '3px 4px' }} value={ml.type}
                        onChange={(e) => updateMarkLine(idx, { type: e.target.value as ChartMarkLine['type'] })}>
                        <option value="value">固定值</option><option value="average">平均</option>
                        <option value="min">最小</option><option value="max">最大</option>
                    </select>
                    {ml.type === 'value' ? (
                        <input type="number" className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" style={{ fontSize: 11, padding: '3px 4px' }} value={ml.value ?? 0}
                            onChange={(e) => updateMarkLine(idx, { value: Number(e.target.value) })} />
                    ) : <span />}
                    <input type="text" className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" style={{ fontSize: 11, padding: '3px 4px' }} placeholder="标签" value={ml.name ?? ''} onChange={(e) => updateMarkLine(idx, { name: e.target.value })} />
                    <input type="color" className="property-color-input w-8 h-7 border border-border-default rounded cursor-pointer p-0" value={ml.color ?? '#ff6b6b'} onChange={(e) => updateMarkLine(idx, { color: e.target.value })} />
                    <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" style={{ padding: '2px 6px', fontSize: 11 }} onClick={() => removeMarkLine(idx)}>×</button>
                </div>
            ))}
            {markLines.length < 5 && (
                <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={addMarkLine} style={{ fontSize: 11, justifySelf: 'start' }}>+ 辅助线</button>
            )}

            <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', fontWeight: 600, marginTop: 4 }}>标记区域 ({markAreas.length}/3)</div>
            {markAreas.map((ma, idx) => (
                <div key={idx} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 60px 60px auto', gap: 4, alignItems: 'center' }}>
                    <input type="number" className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" style={{ fontSize: 11, padding: '3px 4px' }} placeholder="起始" value={ma.from} onChange={(e) => updateMarkArea(idx, { from: Number(e.target.value) })} />
                    <input type="number" className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" style={{ fontSize: 11, padding: '3px 4px' }} placeholder="结束" value={ma.to} onChange={(e) => updateMarkArea(idx, { to: Number(e.target.value) })} />
                    <input type="text" className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" style={{ fontSize: 11, padding: '3px 4px' }} placeholder="标签" value={ma.name ?? ''} onChange={(e) => updateMarkArea(idx, { name: e.target.value })} />
                    <input type="color" className="property-color-input w-8 h-7 border border-border-default rounded cursor-pointer p-0" value={ma.color?.startsWith('rgba') ? '#ff6b6b' : (ma.color ?? '#ff6b6b')}
                        onChange={(e) => { const h = e.target.value; const r = parseInt(h.slice(1, 3), 16); const g = parseInt(h.slice(3, 5), 16); const b = parseInt(h.slice(5, 7), 16); updateMarkArea(idx, { color: `rgba(${r},${g},${b},0.15)` }); }} />
                    <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" style={{ padding: '2px 6px', fontSize: 11 }} onClick={() => removeMarkArea(idx)}>×</button>
                </div>
            ))}
            {markAreas.length < 3 && (
                <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={addMarkArea} style={{ fontSize: 11, justifySelf: 'start' }}>+ 标记区域</button>
            )}

            <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', fontWeight: 600, marginTop: 4 }}>条件着色 ({conditionalColors.length}/5)</div>
            {conditionalColors.map((cc, idx) => (
                <div key={idx} style={{ display: 'grid', gridTemplateColumns: 'auto 60px 60px 40px auto', gap: 4, alignItems: 'center' }}>
                    <select className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" style={{ fontSize: 11, padding: '3px 4px' }} value={cc.operator}
                        onChange={(e) => updateConditionalColor(idx, { operator: e.target.value as SeriesConditionalColor['operator'] })}>
                        <option value=">">{'>'}</option><option value=">=">{'>='}</option><option value="<">{'<'}</option>
                        <option value="<=">{'<='}</option><option value="==">{'=='}</option><option value="between">区间</option>
                    </select>
                    <input type="number" className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" style={{ fontSize: 11, padding: '3px 4px' }} value={cc.value}
                        onChange={(e) => updateConditionalColor(idx, { value: Number(e.target.value) })} />
                    {cc.operator === 'between' ? (
                        <input type="number" className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" style={{ fontSize: 11, padding: '3px 4px' }} placeholder="上限" value={cc.valueTo ?? 0}
                            onChange={(e) => updateConditionalColor(idx, { valueTo: Number(e.target.value) })} />
                    ) : <span />}
                    <input type="color" className="property-color-input w-8 h-7 border border-border-default rounded cursor-pointer p-0" value={cc.color} onChange={(e) => updateConditionalColor(idx, { color: e.target.value })} />
                    <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" style={{ padding: '2px 6px', fontSize: 11 }} onClick={() => removeConditionalColor(idx)}>×</button>
                </div>
            ))}
            {conditionalColors.length < 5 && (
                <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={addConditionalColor} style={{ fontSize: 11, justifySelf: 'start' }}>+ 条件着色</button>
            )}
        </div>
    );
}
