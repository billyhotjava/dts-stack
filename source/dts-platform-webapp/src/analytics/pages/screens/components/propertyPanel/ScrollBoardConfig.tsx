import type { DataSourceConfig, ScreenComponent } from '../../types';
import { CardSourceColumnBindingsEditor } from './CardSourceColumnBindingsEditor';
import { ColorPickerInput } from './ColorPickerInput';
import { resolveDataSourceType } from './helpers';
import type { ColumnEntry } from './types';

/** scroll-board 专用属性面板，支持动态列选择 */
export function ScrollBoardConfig({ component, onChange }: {
    component: ScreenComponent;
    onChange: (key: string, value: unknown) => void;
}) {
    const { config, dataSource } = component;

    // Read _sourceColumns persisted by ComponentRenderer (no separate API call)
    const sourceCols = config._sourceColumns as Array<{ name: string; displayName: string }> ?? [];
    const columns = config.columns as ColumnEntry[] | undefined;
    const hasDynamicSource = resolveDataSourceType(dataSource as DataSourceConfig | undefined) !== 'static';

    // Static fallback: use config.header when no card data source
    const staticHeaders = config.header as string[] || [];
    const columnAlias = config.columnAlias as Record<string, string> || {};

    return (
        <>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">行数</label>
                <input
                    type="number"
                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                    min={1}
                    max={20}
                    value={config.rowNum as number}
                    onChange={(e) => onChange('rowNum', Number(e.target.value))}
                />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">等待时间(ms)</label>
                <input
                    type="number"
                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                    min={500}
                    max={10000}
                    step={500}
                    value={config.waitTime as number || 2000}
                    onChange={(e) => onChange('waitTime', Number(e.target.value))}
                />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">表头颜色</label>
                <ColorPickerInput
                    value={(config.headerColor as string) || '#ffffff'}
                    fallback="#ffffff"
                    onChange={(value) => onChange('headerColor', value)}
                    ariaLabel="表头颜色"
                />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">表头背景</label>
                <ColorPickerInput
                    value={(config.headerBGC as string) || '#003366'}
                    fallback="#003366"
                    onChange={(value) => onChange('headerBGC', value)}
                    ariaLabel="表头背景"
                />
            </div>

            {/* Card 数据源: 等待列加载 */}
            {hasDynamicSource && sourceCols.length === 0 && (
                <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginTop: 8, padding: '4px 0' }}>
                    等待数据源加载列信息…
                </div>
            )}

            {/* Card 数据源: 动态列选择 */}
            {hasDynamicSource && sourceCols.length > 0 && (
                <CardSourceColumnBindingsEditor
                    title="显示列 (来自数据源)"
                    sourceCols={sourceCols}
                    columns={columns}
                    defaultAlign="center"
                    onColumnsChange={(value) => onChange('columns', value)}
                />
            )}

            {/* 静态数据源: 按索引的表头别名 (保持向后兼容) */}
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
        </>
    );
}
