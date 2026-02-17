import type { ScreenPluginManifest } from '../../../api/analyticsApi';
import { buildPluginRuntimeId, registerRendererPlugin } from './registry';
import type { RendererPlugin } from './types';

type AdapterFactory = (pluginId: string, componentId: string, version?: string) => RendererPlugin;
type CustomPluginFactory = AdapterFactory | ((pluginId?: string, componentId?: string, version?: string) => RendererPlugin);
type CustomPluginModule = {
    createPlugin?: CustomPluginFactory;
    default?: CustomPluginFactory;
    [key: string]: unknown;
};

const ADAPTERS: Record<string, AdapterFactory> = {
    'demo-stat-pack:kpi-card-pro': createKpiCardProPlugin,
    'demo-stat-pack:compact-trend': createCompactTrendPlugin,
    'demo-stat-pack:table-matrix': createTableMatrixPlugin,
};

const customModules = import.meta.glob('./custom/*.tsx', { eager: true }) as Record<string, CustomPluginModule>;
const CUSTOM_ADAPTERS: Record<string, AdapterFactory> = buildCustomAdapters(customModules);

export function installBuiltinPluginAdapters(manifests: ScreenPluginManifest[]): void {
    for (const plugin of manifests || []) {
        const pluginId = String(plugin?.id ?? '').trim();
        if (!pluginId) continue;
        const version = String(plugin?.version ?? '').trim() || undefined;
        const components = Array.isArray(plugin.components) ? plugin.components : [];
        for (const component of components) {
            const componentId = String(component?.id ?? '').trim();
            if (!componentId) continue;
            const adapterKey = `${pluginId}:${componentId}`;
            const factory = ADAPTERS[adapterKey] ?? CUSTOM_ADAPTERS[adapterKey];
            if (!factory) continue;
            registerRendererPlugin(factory(pluginId, componentId, version));
        }
    }
}

function buildCustomAdapters(modules: Record<string, CustomPluginModule>): Record<string, AdapterFactory> {
    const out: Record<string, AdapterFactory> = {};
    for (const [filePath, mod] of Object.entries(modules)) {
        const key = parseAdapterKeyFromPath(filePath);
        if (!key) continue;
        const customFactory = resolveCustomFactory(mod);
        if (!customFactory) continue;
        out[key] = (pluginId: string, componentId: string, version?: string) => customFactory(pluginId, componentId, version);
    }
    return out;
}

function parseAdapterKeyFromPath(filePath: string): string {
    const filename = filePath.split('/').pop() || '';
    const stem = filename.endsWith('.tsx') ? filename.slice(0, -4) : filename;
    const split = stem.split('__');
    if (split.length !== 2) {
        return '';
    }
    const pluginId = String(split[0] || '').trim();
    const componentId = String(split[1] || '').trim();
    if (!pluginId || !componentId) {
        return '';
    }
    return `${pluginId}:${componentId}`;
}

function resolveCustomFactory(mod: CustomPluginModule): CustomPluginFactory | null {
    if (typeof mod.createPlugin === 'function') {
        return mod.createPlugin;
    }
    if (typeof mod.default === 'function') {
        return mod.default;
    }
    for (const value of Object.values(mod)) {
        if (typeof value === 'function') {
            return value as CustomPluginFactory;
        }
    }
    return null;
}

function createKpiCardProPlugin(pluginId: string, componentId: string, version?: string): RendererPlugin {
    const id = buildPluginRuntimeId(pluginId, componentId, version);
    return {
        id,
        name: 'KPI Card Pro',
        version: version || '1.0.0',
        baseType: 'number-card',
        dataContract: {
            version: '1.0',
            kind: 'kv',
            description: '期望 value/title/prefix/suffix',
        },
        propertySchema: {
            version: '1.0',
            fields: [
                { key: 'title', label: '标题', type: 'string' },
                { key: 'value', label: '值', type: 'number' },
                { key: 'prefix', label: '前缀', type: 'string' },
                { key: 'suffix', label: '后缀', type: 'string' },
            ],
        },
        render: ({ config }) => {
            const title = String(config.title ?? 'KPI');
            const value = Number(config.value ?? 0);
            const prefix = String(config.prefix ?? '');
            const suffix = String(config.suffix ?? '');
            const bg = String(config.backgroundColor ?? 'linear-gradient(135deg, rgba(8,47,73,0.9) 0%, rgba(14,116,144,0.35) 100%)');
            const titleColor = String(config.titleColor ?? '#93c5fd');
            const valueColor = String(config.valueColor ?? '#f8fafc');
            const valueFontSize = Number(config.valueFontSize ?? 34);
            return (
                <div style={{
                    width: '100%',
                    height: '100%',
                    borderRadius: 14,
                    border: '1px solid rgba(56,189,248,0.4)',
                    boxSizing: 'border-box',
                    padding: '14px 16px',
                    background: bg,
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                }}>
                    <div style={{ fontSize: 12, color: titleColor, letterSpacing: 0.5 }}>{title}</div>
                    <div style={{ fontSize: valueFontSize, color: valueColor, fontWeight: 700 }}>
                        {prefix}{value.toLocaleString('zh-CN')}{suffix}
                    </div>
                </div>
            );
        },
    };
}

function createCompactTrendPlugin(pluginId: string, componentId: string, version?: string): RendererPlugin {
    const id = buildPluginRuntimeId(pluginId, componentId, version);
    return {
        id,
        name: 'Compact Trend',
        version: version || '1.0.0',
        baseType: 'line-chart',
        dataContract: {
            version: '1.0',
            kind: 'series',
            description: '期望 xAxisData + series[0].data',
        },
        propertySchema: {
            version: '1.0',
            fields: [
                { key: 'title', label: '标题', type: 'string' },
                { key: 'xAxisData', label: 'X轴', type: 'array' },
                { key: 'series', label: '序列', type: 'array' },
            ],
        },
        render: ({ config }) => {
            const title = String(config.title ?? '趋势');
            const labels = Array.isArray(config.xAxisData) ? config.xAxisData.map((v) => String(v)) : [];
            const firstSeries = Array.isArray(config.series) && config.series.length > 0 ? (config.series[0] as Record<string, unknown>) : {};
            const data = Array.isArray(firstSeries.data) ? firstSeries.data.map((v) => Number(v) || 0) : [];
            const max = data.length > 0 ? Math.max(...data, 1) : 1;
            const points = data.map((v, idx) => `${(idx / Math.max(data.length - 1, 1)) * 100},${100 - (v / max) * 100}`).join(' ');
            const current = data.length > 0 ? data[data.length - 1] : 0;
            return (
                <div style={{
                    width: '100%',
                    height: '100%',
                    boxSizing: 'border-box',
                    padding: 10,
                    borderRadius: 10,
                    border: '1px solid rgba(16,185,129,0.35)',
                    background: 'rgba(2,44,34,0.3)',
                    display: 'grid',
                    gridTemplateRows: 'auto 1fr auto',
                    gap: 8,
                }}>
                    <div style={{ fontSize: 12, color: '#a7f3d0' }}>{title}</div>
                    <svg viewBox="0 0 100 100" preserveAspectRatio="none" style={{ width: '100%', height: '100%' }}>
                        <polyline
                            fill="none"
                            stroke="#34d399"
                            strokeWidth="2"
                            points={points || '0,100 100,0'}
                        />
                    </svg>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: '#d1fae5' }}>
                        <span>{labels[0] || '-'}</span>
                        <span>当前 {current.toLocaleString('zh-CN')}</span>
                        <span>{labels[labels.length - 1] || '-'}</span>
                    </div>
                </div>
            );
        },
    };
}

function createTableMatrixPlugin(pluginId: string, componentId: string, version?: string): RendererPlugin {
    const id = buildPluginRuntimeId(pluginId, componentId, version);
    return {
        id,
        name: 'Table Matrix',
        version: version || '1.0.0',
        baseType: 'table',
        dataContract: {
            version: '1.0',
            kind: 'table',
            description: '期望 header + data',
        },
        propertySchema: {
            version: '1.0',
            fields: [
                { key: 'header', label: '表头', type: 'array' },
                { key: 'data', label: '数据', type: 'array' },
            ],
        },
        render: ({ config }) => {
            const headers = Array.isArray(config.header) ? config.header.map((v) => String(v)) : [];
            const rows = Array.isArray(config.data) ? config.data as unknown[][] : [];
            return (
                <div style={{ width: '100%', height: '100%', overflow: 'auto' }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
                        <thead>
                            <tr>
                                {headers.map((header, idx) => (
                                    <th key={idx} style={{ textAlign: 'left', borderBottom: '1px solid rgba(148,163,184,0.4)', padding: '6px 8px' }}>
                                        {header}
                                    </th>
                                ))}
                            </tr>
                        </thead>
                        <tbody>
                            {rows.map((row, rowIdx) => (
                                <tr key={rowIdx}>
                                    {headers.map((_, colIdx) => (
                                        <td key={colIdx} style={{ borderBottom: '1px solid rgba(148,163,184,0.2)', padding: '6px 8px' }}>
                                            {String(row[colIdx] ?? '')}
                                        </td>
                                    ))}
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            );
        },
    };
}
