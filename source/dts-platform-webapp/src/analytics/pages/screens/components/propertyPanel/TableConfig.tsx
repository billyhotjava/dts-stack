// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import { toast } from 'sonner';
import type { DataSourceConfig, ScreenComponent } from '../../types';
import { CardSourceColumnBindingsEditor } from './CardSourceColumnBindingsEditor';
import { resolveDataSourceType } from './helpers';
import type { ColumnEntry } from './types';

/** table 专用属性面板，支持动态列选择和样式配置 */
export function TableConfig({ component, onChange }: {
    component: ScreenComponent;
    onChange: (key: string, value: unknown) => void;
}) {
    const { config, dataSource } = component;

    const sourceCols = config._sourceColumns as Array<{ name: string; displayName: string }> ?? [];
    const columns = config.columns as ColumnEntry[] | undefined;
    const hasDynamicSource = resolveDataSourceType(dataSource as DataSourceConfig | undefined) !== 'static';

    const staticHeaders = config.header as string[] || [];
    const columnAlias = config.columnAlias as Record<string, string> || {};

    return (
        <>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">字号</label>
                <input
                    type="number"
                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                    min={10}
                    max={24}
                    value={(config.fontSize as number) || 13}
                    onChange={(e) => onChange('fontSize', Number(e.target.value))}
                />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">表头字号</label>
                <input
                    type="number"
                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                    min={10}
                    max={36}
                    value={(config.headerFontSize as number) || (config.fontSize as number) || 13}
                    onChange={(e) => onChange('headerFontSize', Number(e.target.value))}
                />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">表头对齐</label>
                <select
                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                    value={(config.headerAlign as string) || ''}
                    onChange={(e) => onChange('headerAlign', e.target.value || undefined)}
                >
                    <option value="">跟随列对齐</option>
                    <option value="left">左对齐</option>
                    <option value="center">居中</option>
                    <option value="right">右对齐</option>
                </select>
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">表头颜色</label>
                <input
                    type="color"
                    className="property-color-input w-8 h-7 border border-border-default rounded cursor-pointer p-0"
                    value={(config.headerColor as string) || '#e5e7eb'}
                    onChange={(e) => onChange('headerColor', e.target.value)}
                />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">表头背景</label>
                <input
                    type="color"
                    className="property-color-input w-8 h-7 border border-border-default rounded cursor-pointer p-0"
                    value={(config.headerBackground as string) || '#64748b'}
                    onChange={(e) => onChange('headerBackground', e.target.value)}
                />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">正文颜色</label>
                <input
                    type="color"
                    className="property-color-input w-8 h-7 border border-border-default rounded cursor-pointer p-0"
                    value={(config.bodyColor as string) || '#d1d5db'}
                    onChange={(e) => onChange('bodyColor', e.target.value)}
                />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">边框颜色</label>
                <input
                    type="color"
                    className="property-color-input w-8 h-7 border border-border-default rounded cursor-pointer p-0"
                    value={(config.borderColor as string) || '#94a3b8'}
                    onChange={(e) => onChange('borderColor', e.target.value)}
                />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">启用排序</label>
                <input
                    type="checkbox"
                    checked={config.enableSort !== false}
                    onChange={(e) => onChange('enableSort', e.target.checked)}
                />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">分页</label>
                <input
                    type="checkbox"
                    checked={config.enablePagination === true}
                    onChange={(e) => onChange('enablePagination', e.target.checked)}
                />
            </div>
            {config.enablePagination === true && (
                <div className="property-row flex items-center mb-3">
                    <label className="property-label w-20 text-xs text-text-secondary">每页条数</label>
                    <input
                        type="number"
                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                        min={1}
                        max={200}
                        value={(config.pageSize as number) || 10}
                        onChange={(e) => onChange('pageSize', Number(e.target.value))}
                    />
                </div>
            )}
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">冻结表头</label>
                <input
                    type="checkbox"
                    checked={config.freezeHeader !== false}
                    onChange={(e) => onChange('freezeHeader', e.target.checked)}
                />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">冻结首列</label>
                <input
                    type="checkbox"
                    checked={config.freezeFirstColumn === true}
                    onChange={(e) => onChange('freezeFirstColumn', e.target.checked)}
                />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">条件格式(JSON)</label>
                <textarea
                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                    rows={4}
                    defaultValue={JSON.stringify((config.conditionalRules as unknown[]) || [], null, 2)}
                    onBlur={(e) => {
                        const raw = e.target.value.trim();
                        if (!raw) {
                            onChange('conditionalRules', []);
                            return;
                        }
                        try {
                            const parsed = JSON.parse(raw);
                            onChange('conditionalRules', Array.isArray(parsed) ? parsed : []);
                        } catch {
                            toast.error('条件格式 JSON 解析失败');
                        }
                    }}
                    placeholder='[{"columnKey":"amount","operator":">","value":100,"color":"#ef4444"}]'
                />
            </div>
            <div style={{ fontSize: 11, opacity: 0.75, marginTop: -2, marginBottom: 8, lineHeight: 1.5 }}>
                支持按 `columnIndex`、`columnKey` 或 `columnTitle` 匹配列；建议优先使用 `columnKey` 以避免字段重排错位。
            </div>

            {hasDynamicSource && sourceCols.length === 0 && (
                <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginTop: 8, padding: '4px 0' }}>
                    等待数据源加载列信息…
                </div>
            )}

            {hasDynamicSource && sourceCols.length > 0 && (
                <CardSourceColumnBindingsEditor
                    title="字段绑定 (来自数据源)"
                    sourceCols={sourceCols}
                    columns={columns}
                    defaultAlign="left"
                    onColumnsChange={(value) => onChange('columns', value)}
                />
            )}

            {!hasDynamicSource && staticHeaders.length > 0 && (
                <>
                    <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginTop: 8, marginBottom: 4 }}>
                        表头别名
                    </div>
                    {staticHeaders.map((h, i) => (
                        <div className="property-row flex items-center mb-3" key={i}>
                            <label className="property-label w-20 text-xs text-text-secondary" title={h}>列{i + 1}</label>
                            <input
                                type="text"
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                placeholder={h}
                                value={columnAlias[String(i)] || ''}
                                onChange={(e) => {
                                    const newAlias = { ...columnAlias };
                                    if (e.target.value) {
                                        newAlias[String(i)] = e.target.value;
                                    } else {
                                        delete newAlias[String(i)];
                                    }
                                    onChange('columnAlias', newAlias);
                                }}
                            />
                        </div>
                    ))}
                </>
            )}

            {/* Table 样式增强 */}
            <div className="text-xs text-text-secondary mb-1">样式</div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">表头背景</label>
                <input type="color" className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" value={(config.headerBackground as string) || '#112238'} onChange={(e) => onChange('headerBackground', e.target.value)} />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">行背景</label>
                <input type="color" className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" value={(config.bodyBackground as string) || '#0d1b2d'} onChange={(e) => onChange('bodyBackground', e.target.value)} />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">交替行背景</label>
                <input type="color" className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" value={(config.oddRowBackground as string) || '#10233a'} onChange={(e) => onChange('oddRowBackground', e.target.value)} />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">文字颜色</label>
                <input type="color" className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" value={(config.bodyColor as string) || '#c8ddf5'} onChange={(e) => onChange('bodyColor', e.target.value)} />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">边框颜色</label>
                <input type="color" className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" value={(config.borderColor as string) || '#1e3a5f'} onChange={(e) => onChange('borderColor', e.target.value)} />
            </div>
        </>
    );
}
