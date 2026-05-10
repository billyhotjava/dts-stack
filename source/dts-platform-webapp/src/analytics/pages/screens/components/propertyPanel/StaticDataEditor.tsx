import { useState } from 'react';
import { Plus, X } from 'lucide-react';
import type { ScreenComponent } from '../../types';

// Default templates per component type
const STATIC_DATA_TEMPLATES: Record<string, { headers: string[]; rows: string[][] }> = {
    'bar-chart': { headers: ['月份', '销量', '成本'], rows: [['一月', '120', '80'], ['二月', '200', '150'], ['三月', '150', '100']] },
    'line-chart': { headers: ['月份', '指标A', '指标B'], rows: [['一月', '42', '30'], ['二月', '55', '48'], ['三月', '62', '51']] },
    'pie-chart': { headers: ['名称', '数值'], rows: [['类别A', '35'], ['类别B', '25'], ['类别C', '20'], ['类别D', '15']] },
    'table': { headers: ['编号', '名称', '状态'], rows: [['001', '项目A', '进行中'], ['002', '项目B', '已完成']] },
    'scroll-board': { headers: ['项目', '进度', '负责人'], rows: [['项目A', '80%', '张三'], ['项目B', '60%', '李四']] },
    'number-card': { headers: ['label', 'value'], rows: [['总数', '128']] },
    'gauge-chart': { headers: ['label', 'value'], rows: [['完成率', '78']] },
    'radar-chart': { headers: ['维度', '系列A', '系列B'], rows: [['进度', '80', '70'], ['质量', '90', '60'], ['成本', '70', '85']] },
    'funnel-chart': { headers: ['阶段', '数量'], rows: [['线索', '100'], ['商机', '60'], ['成交', '30']] },
    'scatter-chart': { headers: ['X', 'Y'], rows: [['10', '20'], ['30', '50'], ['50', '40'], ['70', '80']] },
};

const STATIC_DATA_HINTS: Record<string, string> = {
    'bar-chart': '第1列=X轴标签，其余列=数值系列',
    'line-chart': '第1列=X轴标签，其余列=数值系列',
    'pie-chart': '第1列=名称，第2列=数值',
    'table': '所有列直接展示为表格',
    'number-card': '第1列=标签，第2列=数值',
    'gauge-chart': '第1列=标签，第2列=数值(0-100)',
    'radar-chart': '第1列=维度名，其余列=各系列数值',
    'scatter-chart': '第1列=X值，第2列=Y值',
};

export function StaticDataEditor({ component, updateComponent }: {
    component: ScreenComponent;
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void;
}) {
    const config = component.config;
    const componentType = component.type;
    const pluginMeta = config.__plugin as { pluginId?: string; componentId?: string } | undefined;
    const isFinanceSummaryTable = pluginMeta?.pluginId === 'finance-kit' && pluginMeta?.componentId === 'summary-table';
    const [mode, setMode] = useState<'table' | 'json'>('table');

    const defaultTemplate = STATIC_DATA_TEMPLATES[componentType] || { headers: ['列1', '列2', '列3'], rows: [['', '', ''], ['', '', '']] };

    // Parse existing data into header + rows
    const [headers, setHeaders] = useState<string[]>(() => {
        const h = config.header as string[] | undefined;
        if (Array.isArray(h) && h.length > 0) return h.map(String);
        if (isFinanceSummaryTable) {
            const financeHeaders = config.headers as string[] | undefined;
            if (Array.isArray(financeHeaders) && financeHeaders.length > 0) return financeHeaders.map(String);
        }
        return defaultTemplate.headers;
    });
    const [rows, setRows] = useState<string[][]>(() => {
        const d = config.data as string[][] | undefined;
        if (Array.isArray(d) && d.length > 0) return d.map((r) => Array.isArray(r) ? r.map(String) : []);
        if (isFinanceSummaryTable) {
            const financeRows = config.rows as Array<Record<string, unknown>> | undefined;
            if (Array.isArray(financeRows) && financeRows.length > 0) {
                return financeRows.map((row) => Object.values(row).map((cell) => String(cell ?? '')));
            }
        }
        return defaultTemplate.rows;
    });
    const [jsonText, setJsonText] = useState('');
    const [jsonError, setJsonError] = useState<string | null>(null);

    // Sync to component config
    const applyTableData = (nextHeaders: string[], nextRows: string[][]) => {
        updateComponent(component.id, {
            config: {
                ...config,
                header: nextHeaders,
                data: nextRows,
                ...(isFinanceSummaryTable
                    ? {
                        headers: nextHeaders,
                        headerSourceMode: 'manual',
                    }
                    : {}),
            },
        });
    };

    const updateCell = (rowIdx: number, colIdx: number, value: string) => {
        const next = rows.map((r, ri) => ri === rowIdx ? r.map((c, ci) => ci === colIdx ? value : c) : [...r]);
        setRows(next);
        applyTableData(headers, next);
    };

    const updateHeader = (colIdx: number, value: string) => {
        const next = headers.map((h, i) => i === colIdx ? value : h);
        setHeaders(next);
        applyTableData(next, rows);
    };

    const addRow = () => {
        const next = [...rows, headers.map(() => '')];
        setRows(next);
        applyTableData(headers, next);
    };

    const deleteRow = (idx: number) => {
        if (rows.length <= 1) return;
        const next = rows.filter((_, i) => i !== idx);
        setRows(next);
        applyTableData(headers, next);
    };

    const addColumn = () => {
        const nextH = [...headers, '新列'];
        const nextR = rows.map((r) => [...r, '']);
        setHeaders(nextH);
        setRows(nextR);
        applyTableData(nextH, nextR);
    };

    const deleteColumn = (idx: number) => {
        if (headers.length <= 1) return;
        const nextH = headers.filter((_, i) => i !== idx);
        const nextR = rows.map((r) => r.filter((_, i) => i !== idx));
        setHeaders(nextH);
        setRows(nextR);
        applyTableData(nextH, nextR);
    };

    const switchToJson = () => {
        setJsonText(JSON.stringify([headers, ...rows], null, 2));
        setJsonError(null);
        setMode('json');
    };

    const applyJson = () => {
        try {
            const parsed: unknown = JSON.parse(jsonText);
            if (!Array.isArray(parsed) || parsed.length < 1 || !Array.isArray(parsed[0])) {
                throw new Error('需要至少一行（表头）');
            }
            const h = parsed[0].map(String);
            const d = parsed.slice(1).map((r: unknown) => {
                const row = Array.isArray(r) ? r.map(String) : [];
                while (row.length < h.length) row.push('');
                return row.slice(0, h.length);
            });
            setHeaders(h);
            setRows(d.length > 0 ? d : [h.map(() => '')]);
            applyTableData(h, d.length > 0 ? d : [h.map(() => '')]);
            setJsonError(null);
            setMode('table');
        } catch (e) {
            setJsonError(e instanceof Error ? e.message : 'JSON 错误');
        }
    };

    return (
        <>
            <div className="text-xs text-text-secondary mb-1" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 6 }}>
                <span>静态数据</span>
                <span style={{ display: 'flex', gap: 8 }}>
                    <button type="button" style={{ background: 'none', border: 'none', color: 'var(--color-text-secondary)', cursor: 'pointer', fontSize: 11 }}
                        onClick={() => { setHeaders(defaultTemplate.headers); setRows(defaultTemplate.rows); applyTableData(defaultTemplate.headers, defaultTemplate.rows); }}
                        title="重置为当前组件类型的示例数据"
                    >重置模板</button>
                    <button type="button" style={{ background: 'none', border: 'none', color: 'var(--color-primary, #509EE3)', cursor: 'pointer', fontSize: 11 }}
                        onClick={() => mode === 'table' ? switchToJson() : setMode('table')}
                    >{mode === 'table' ? 'JSON' : '表格'}</button>
                </span>
            </div>

            {mode === 'table' ? (
                <div
                    className="border border-border-default rounded bg-surface-card"
                    style={{ overflow: 'auto', maxHeight: 320 }}
                >
                    <table
                        className="bg-surface-card"
                        style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12, color: 'var(--color-text-primary, #e2e8f0)' }}
                    >
                        <thead>
                            <tr>
                                <th className="bg-surface-card" style={{ width: 28, padding: '4px 2px', borderBottom: '1px solid var(--color-border, rgba(255,255,255,0.1))', fontSize: 10, color: 'var(--color-text-tertiary)' }}>#</th>
                                {headers.map((h, ci) => (
                                    <th key={ci} className="bg-surface-card" style={{ padding: 0, borderBottom: '1px solid var(--color-border, rgba(255,255,255,0.1))', position: 'relative' }}>
                                        <input
                                            type="text"
                                            value={h}
                                            onChange={(e) => updateHeader(ci, e.target.value)}
                                            style={{ width: '100%', border: 'none', background: 'transparent', padding: '6px 8px', fontSize: 12, fontWeight: 600, outline: 'none', boxSizing: 'border-box', color: 'inherit' }}
                                        />
                                        {headers.length > 1 && (
                                            <button type="button" onClick={() => deleteColumn(ci)}
                                                style={{ position: 'absolute', top: 0, right: 2, background: 'none', border: 'none', color: 'var(--color-text-tertiary)', cursor: 'pointer', lineHeight: 1, display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}
                                                title="删除列" aria-label="删除列">
                                                <X size={11} aria-hidden="true" />
                                            </button>
                                        )}
                                    </th>
                                ))}
                                <th className="bg-surface-card" style={{ width: 28, padding: 0, borderBottom: '1px solid var(--color-border, rgba(255,255,255,0.1))' }}>
                                    <button type="button" onClick={addColumn}
                                        style={{ background: 'none', border: 'none', color: 'var(--color-primary, #509EE3)', cursor: 'pointer', padding: '2px 6px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}
                                        title="添加列" aria-label="添加列">
                                        <Plus size={14} aria-hidden="true" />
                                    </button>
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            {rows.map((row, ri) => (
                                <tr key={ri}>
                                    <td style={{ padding: '2px 4px', textAlign: 'center', fontSize: 10, color: 'var(--color-text-tertiary)', borderBottom: '1px solid var(--color-border, rgba(255,255,255,0.1))', userSelect: 'none' }}>
                                        {ri + 1}
                                    </td>
                                    {row.slice(0, headers.length).map((cell, ci) => (
                                        <td key={ci} style={{ padding: 0, borderBottom: '1px solid var(--color-border, rgba(255,255,255,0.1))' }}>
                                            <input
                                                type="text"
                                                value={cell}
                                                onChange={(e) => updateCell(ri, ci, e.target.value)}
                                                style={{ width: '100%', border: 'none', background: 'transparent', padding: '5px 8px', fontSize: 12, outline: 'none', boxSizing: 'border-box', color: 'inherit' }}
                                            />
                                        </td>
                                    ))}
                                    <td style={{ padding: 0, textAlign: 'center', borderBottom: '1px solid var(--color-border, rgba(255,255,255,0.1))' }}>
                                        {rows.length > 1 && (
                                            <button type="button" onClick={() => deleteRow(ri)}
                                                style={{ background: 'none', border: 'none', color: 'var(--color-text-tertiary)', cursor: 'pointer', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}
                                                title="删除行" aria-label="删除行">
                                                <X size={12} aria-hidden="true" />
                                            </button>
                                        )}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                    <div className="bg-surface-card" style={{ padding: '4px 8px', borderTop: '1px solid var(--color-border, rgba(255,255,255,0.1))' }}>
                        <button type="button" onClick={addRow}
                            className="bg-surface-card"
                            style={{ border: '1px dashed var(--color-border, rgba(255,255,255,0.15))', borderRadius: 4, padding: '3px 12px', cursor: 'pointer', fontSize: 11, color: 'var(--color-primary, #509EE3)', width: '100%', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
                        >
                            <Plus size={13} aria-hidden="true" />
                            添加行
                        </button>
                    </div>
                </div>
            ) : (
                <div className="border border-border-default rounded bg-surface-card p-2">
                    <textarea
                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                        style={{ width: '100%', height: 200, fontFamily: 'monospace', fontSize: 11, resize: 'vertical', color: 'var(--color-text-primary, #e2e8f0)' }}
                        value={jsonText}
                        onChange={(e) => { setJsonText(e.target.value); setJsonError(null); }}
                        spellCheck={false}
                    />
                    {jsonError && <div style={{ color: '#ef4444', fontSize: 11, marginTop: 4 }}>{jsonError}</div>}
                    <button type="button" className="property-btn-small inline-flex items-center justify-center px-3 py-1.5 min-h-8 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer hover:border-brand hover:bg-brand/10" style={{ marginTop: 6, width: '100%' }} onClick={applyJson}>
                        应用 JSON
                    </button>
                </div>
            )}

            <div style={{ fontSize: 10, color: 'var(--color-text-secondary)', padding: '4px 0', lineHeight: 1.4 }}>
                {headers.length} 列 × {rows.length} 行
                {STATIC_DATA_HINTS[componentType] && (
                    <span style={{ display: 'block', marginTop: 2, color: '#60a5fa' }}>
                        格式：{STATIC_DATA_HINTS[componentType]}
                    </span>
                )}
            </div>
        </>
    );
}
