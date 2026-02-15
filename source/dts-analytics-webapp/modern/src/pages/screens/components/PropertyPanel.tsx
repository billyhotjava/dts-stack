import { useScreen } from '../ScreenContext';
import type { CardParameterBinding, ComponentInteractionMapping, ComponentType, DataSourceConfig, DrillLevel, QuerySourceType, ScreenComponent, ScreenGlobalVariable } from '../types';
import { DRILLABLE_TYPES } from '../types';
import { CardIdPicker } from './CardIdPicker';
import { MetricBindingEditor } from './MetricBindingEditor';
import { CardParamBindingsEditor } from './CardParamBindingsEditor';
import { DatabaseIdPicker } from './DatabaseIdPicker';
import { getRendererPlugin } from '../plugins/registry';
import { readComponentPluginMeta, resolveRuntimePluginId } from '../plugins/runtime';
import { useScreenPluginRuntime } from '../plugins/useScreenPluginRuntime';
import type { PropertySchemaField } from '../plugins/types';

const DEFAULT_SERIES_COLORS = [
    '#3b82f6',
    '#22c55e',
    '#f59e0b',
    '#ef4444',
    '#a855f7',
    '#06b6d4',
];

export function PropertyPanel() {
    const { state, updateComponent, updateSelectedComponents } = useScreen();
    const { config, selectedIds } = state;
    useScreenPluginRuntime();

    const selectedComponents = config.components.filter((c) => selectedIds.includes(c.id));
    const selectedComponent = selectedIds.length === 1
        ? config.components.find((c) => c.id === selectedIds[0])
        : null;

    if (selectedComponents.length === 0) {
        return (
            <div className="property-panel">
                <div className="property-panel-header">
                    <h3>属性</h3>
                </div>
                <div className="property-panel-content">
                    <div className="empty-state">
                        <div className="empty-state-icon">🎨</div>
                        <div className="empty-state-text">选择组件以编辑属性</div>
                        <div className="empty-state-hint">点击画布中的组件进行选择</div>
                    </div>
                </div>
            </div>
        );
    }

    if (!selectedComponent) {
        const total = selectedComponents.length;
        const allLocked = selectedComponents.every((item) => item.locked);
        const allVisible = selectedComponents.every((item) => item.visible);
        const grouped = selectedComponents.filter((item) => Boolean(item.groupId)).length;
        return (
            <div className="property-panel">
                <div className="property-panel-header">
                    <h3>批量属性 ({total})</h3>
                </div>
                <div className="property-panel-content">
                    <div className="property-section">
                        <div className="property-section-title">批量设置</div>
                        <div className="property-row">
                            <label className="property-label">宽度</label>
                            <input
                                type="number"
                                className="property-input"
                                min={50}
                                onChange={(e) => updateSelectedComponents({ width: Math.max(50, Number(e.target.value) || 50) })}
                                placeholder="统一宽度"
                            />
                        </div>
                        <div className="property-row">
                            <label className="property-label">高度</label>
                            <input
                                type="number"
                                className="property-input"
                                min={50}
                                onChange={(e) => updateSelectedComponents({ height: Math.max(50, Number(e.target.value) || 50) })}
                                placeholder="统一高度"
                            />
                        </div>
                        <div className="property-row">
                            <label className="property-label">锁定</label>
                            <select
                                className="property-input"
                                value={allLocked ? 'locked' : 'unlocked'}
                                onChange={(e) => updateSelectedComponents({ locked: e.target.value === 'locked' })}
                            >
                                <option value="locked">全部锁定</option>
                                <option value="unlocked">全部解锁</option>
                            </select>
                        </div>
                        <div className="property-row">
                            <label className="property-label">可见</label>
                            <select
                                className="property-input"
                                value={allVisible ? 'visible' : 'hidden'}
                                onChange={(e) => updateSelectedComponents({ visible: e.target.value === 'visible' })}
                            >
                                <option value="visible">全部可见</option>
                                <option value="hidden">全部隐藏</option>
                            </select>
                        </div>
                    </div>
                    <div className="property-section">
                        <div className="property-section-title">选择概览</div>
                        <div style={{ fontSize: 12, opacity: 0.8, lineHeight: 1.7 }}>
                            已选组件: {total}<br />
                            已分组组件: {grouped}<br />
                            类型数: {new Set(selectedComponents.map((item) => item.type)).size}
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    const handleChange = (key: string, value: unknown) => {
        updateComponent(selectedComponent.id, { [key]: value });
    };

    const handleConfigChange = (key: string, value: unknown) => {
        updateComponent(selectedComponent.id, {
            config: { ...selectedComponent.config, [key]: value },
        });
    };

    const pluginMeta = readComponentPluginMeta(selectedComponent.config);
    const runtimePlugin = pluginMeta ? getRendererPlugin(resolveRuntimePluginId(pluginMeta)) : undefined;

    return (
        <div className="property-panel">
            <div className="property-panel-header">
                <h3>属性 - {selectedComponent.name}</h3>
            </div>
            <div className="property-panel-content">
                {/* Position & Size */}
                <div className="property-section">
                    <div className="property-section-title">位置与尺寸</div>

                    <div className="property-row">
                        <label className="property-label">X</label>
                        <input
                            type="number"
                            className="property-input"
                            value={selectedComponent.x}
                            onChange={(e) => handleChange('x', Number(e.target.value))}
                        />
                    </div>

                    <div className="property-row">
                        <label className="property-label">Y</label>
                        <input
                            type="number"
                            className="property-input"
                            value={selectedComponent.y}
                            onChange={(e) => handleChange('y', Number(e.target.value))}
                        />
                    </div>

                    <div className="property-row">
                        <label className="property-label">宽度</label>
                        <input
                            type="number"
                            className="property-input"
                            value={selectedComponent.width}
                            onChange={(e) => handleChange('width', Number(e.target.value))}
                        />
                    </div>

                    <div className="property-row">
                        <label className="property-label">高度</label>
                        <input
                            type="number"
                            className="property-input"
                            value={selectedComponent.height}
                            onChange={(e) => handleChange('height', Number(e.target.value))}
                        />
                    </div>
                </div>

                {/* Component-specific config */}
                {runtimePlugin?.propertySchema?.fields?.length ? (
                    <div className="property-section">
                        <div className="property-section-title">插件配置 ({runtimePlugin.name})</div>
                        {renderPluginSchemaFields(selectedComponent, runtimePlugin.propertySchema.fields, handleConfigChange)}
                    </div>
                ) : null}

                <div className="property-section">
                    <div className="property-section-title">组件配置</div>

                    {renderComponentConfig(selectedComponent, handleConfigChange)}
                </div>

                {/* Data Source */}
                <div className="property-section">
                    <div className="property-section-title">数据源</div>
                    {renderDataSourceConfig(selectedComponent, updateComponent, config.globalVariables ?? [])}
                </div>

                {/* Drill-down config */}
                {renderDrillDownConfig(selectedComponent, updateComponent)}

                {renderInteractionConfig(selectedComponent, config.globalVariables ?? [], updateComponent)}

                {/* Visibility & Lock */}
                <div className="property-section">
                    <div className="property-section-title">其他</div>

                    <div className="property-row">
                        <label className="property-label">名称</label>
                        <input
                            type="text"
                            className="property-input"
                            value={selectedComponent.name}
                            onChange={(e) => handleChange('name', e.target.value)}
                        />
                    </div>

                    {selectedComponent.type !== 'container' && (
                        <div className="property-row">
                            <label className="property-label">所属容器</label>
                            <select
                                className="property-input"
                                value={selectedComponent.parentContainerId || ''}
                                onChange={(e) => {
                                    const parentId = e.target.value || undefined;
                                    if (!parentId) {
                                        updateComponent(selectedComponent.id, { parentContainerId: undefined });
                                        return;
                                    }
                                    const parent = config.components.find((item) => item.id === parentId && item.type === 'container');
                                    if (!parent) {
                                        updateComponent(selectedComponent.id, { parentContainerId: undefined });
                                        return;
                                    }
                                    const maxX = parent.x + Math.max(0, parent.width - selectedComponent.width);
                                    const maxY = parent.y + Math.max(0, parent.height - selectedComponent.height);
                                    const nextX = Math.max(parent.x, Math.min(selectedComponent.x, maxX));
                                    const nextY = Math.max(parent.y, Math.min(selectedComponent.y, maxY));
                                    updateComponent(selectedComponent.id, {
                                        parentContainerId: parentId,
                                        x: nextX,
                                        y: nextY,
                                    });
                                }}
                            >
                                <option value="">-- 无 --</option>
                                {config.components
                                    .filter((item) => item.type === 'container' && item.id !== selectedComponent.id)
                                    .map((item) => (
                                        <option key={item.id} value={item.id}>
                                            {item.name} ({item.id})
                                        </option>
                                    ))}
                            </select>
                        </div>
                    )}

                    {selectedComponent.type === 'container' && (
                        <div className="property-row">
                            <label className="property-label">子组件数</label>
                            <div className="property-input" style={{ display: 'flex', alignItems: 'center' }}>
                                {config.components.filter((item) => item.parentContainerId === selectedComponent.id).length}
                            </div>
                        </div>
                    )}

                    <div className="property-row">
                        <label className="property-label">锁定</label>
                        <input
                            type="checkbox"
                            checked={selectedComponent.locked}
                            onChange={(e) => handleChange('locked', e.target.checked)}
                        />
                    </div>

                    <div className="property-row">
                        <label className="property-label">可见</label>
                        <input
                            type="checkbox"
                            checked={selectedComponent.visible}
                            onChange={(e) => handleChange('visible', e.target.checked)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">多端可见</label>
                        <div style={{ display: 'flex', gap: 8 }}>
                            {(['pc', 'tablet', 'mobile'] as const).map((device) => {
                                const current = Array.isArray(selectedComponent.config.visibleOn)
                                    ? selectedComponent.config.visibleOn as string[]
                                    : ['pc', 'tablet', 'mobile'];
                                const checked = current.includes(device);
                                const label = device === 'pc' ? 'PC' : device === 'tablet' ? '平板' : '手机';
                                return (
                                    <label key={device} style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12 }}>
                                        <input
                                            type="checkbox"
                                            checked={checked}
                                            onChange={(e) => {
                                                const base = Array.isArray(selectedComponent.config.visibleOn)
                                                    ? selectedComponent.config.visibleOn as string[]
                                                    : ['pc', 'tablet', 'mobile'];
                                                const next = e.target.checked
                                                    ? Array.from(new Set([...base, device]))
                                                    : base.filter((item) => item !== device);
                                                handleConfigChange('visibleOn', next);
                                            }}
                                        />
                                        {label}
                                    </label>
                                );
                            })}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

function renderPluginSchemaFields(
    component: ScreenComponent,
    fields: PropertySchemaField[],
    onChange: (key: string, value: unknown) => void,
) {
    if (!Array.isArray(fields) || fields.length === 0) {
        return null;
    }
    return (
        <>
            {fields.map((field) => {
                const key = String(field?.key || '').trim();
                if (!key) return null;
                const label = field?.label || key;
                const value = component.config[key] ?? field?.defaultValue;
                if (field.type === 'boolean') {
                    return (
                        <div className="property-row" key={key}>
                            <label className="property-label">{label}</label>
                            <input
                                type="checkbox"
                                checked={Boolean(value)}
                                onChange={(e) => onChange(key, e.target.checked)}
                            />
                        </div>
                    );
                }
                if (field.type === 'number') {
                    return (
                        <div className="property-row" key={key}>
                            <label className="property-label">{label}</label>
                            <input
                                type="number"
                                className="property-input"
                                value={Number(value ?? 0)}
                                onChange={(e) => onChange(key, Number(e.target.value))}
                            />
                        </div>
                    );
                }
                if (field.type === 'color') {
                    const fallback = typeof value === 'string' && value ? value : '#3b82f6';
                    return (
                        <div className="property-row" key={key}>
                            <label className="property-label">{label}</label>
                            <input
                                type="color"
                                className="property-color-input"
                                value={fallback}
                                onChange={(e) => onChange(key, e.target.value)}
                            />
                        </div>
                    );
                }
                if (field.type === 'array' || field.type === 'json') {
                    const isArray = field.type === 'array';
                    const snapshot = JSON.stringify(
                        value ?? (isArray ? [] : {}),
                        null,
                        2,
                    );
                    return (
                        <div className="property-row" key={key}>
                            <label className="property-label">{label}</label>
                            <div style={{ flex: 1 }}>
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => {
                                        const input = window.prompt(`${label} (${isArray ? 'JSON数组' : 'JSON对象'})`, snapshot);
                                        if (input == null) return;
                                        try {
                                            const parsed = JSON.parse(input);
                                            if (isArray && !Array.isArray(parsed)) {
                                                alert(`${label} 需要是 JSON 数组`);
                                                return;
                                            }
                                            if (!isArray && (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed))) {
                                                alert(`${label} 需要是 JSON 对象`);
                                                return;
                                            }
                                            onChange(key, parsed);
                                        } catch {
                                            alert(`${label} JSON 格式错误`);
                                        }
                                    }}
                                >
                                    编辑JSON
                                </button>
                                <pre style={{
                                    margin: '6px 0 0',
                                    maxHeight: 120,
                                    overflow: 'auto',
                                    fontSize: 11,
                                    opacity: 0.8,
                                    whiteSpace: 'pre-wrap',
                                    wordBreak: 'break-all',
                                }}
                                >
                                    {snapshot}
                                </pre>
                            </div>
                        </div>
                    );
                }
                return (
                    <div className="property-row" key={key}>
                        <label className="property-label">{label}</label>
                        <input
                            type="text"
                            className="property-input"
                            value={String(value ?? '')}
                            onChange={(e) => onChange(key, e.target.value)}
                        />
                    </div>
                );
            })}
        </>
    );
}

function renderComponentConfig(
    component: ScreenComponent,
    onChange: (key: string, value: unknown) => void
) {
    const { type, config } = component;
    const configuredSeriesColors = Array.isArray(config.seriesColors)
        ? (config.seriesColors as string[]).map((item) => String(item))
        : [];

    const setSeriesColor = (index: number, color: string) => {
        const next = [...configuredSeriesColors];
        next[index] = color;
        onChange('seriesColors', next);
    };

    const renderSeriesColorRows = (labels: string[]) => {
        if (labels.length === 0) return null;
        return (
            <>
                <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                    系列配色
                </div>
                {labels.map((label, idx) => (
                    <div className="property-row" key={`${label}-${idx}`}>
                        <label className="property-label">{label || `系列${idx + 1}`}</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={configuredSeriesColors[idx] || DEFAULT_SERIES_COLORS[idx % DEFAULT_SERIES_COLORS.length]}
                            onChange={(e) => setSeriesColor(idx, e.target.value)}
                        />
                    </div>
                ))}
            </>
        );
    };

    switch (type) {
        case 'line-chart':
        case 'bar-chart':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.title as string}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">标题字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={36}
                            value={(config.titleFontSize as number) || 14}
                            onChange={(e) => onChange('titleFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">坐标轴字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={28}
                            value={(config.axisFontSize as number) || 12}
                            onChange={(e) => onChange('axisFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">图例字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={28}
                            value={(config.legendFontSize as number) || 12}
                            onChange={(e) => onChange('legendFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">图例位置</label>
                        <select
                            className="property-input"
                            value={(config.legendPosition as string) || 'top'}
                            onChange={(e) => onChange('legendPosition', e.target.value)}
                        >
                            <option value="top">顶部</option>
                            <option value="bottom">底部</option>
                            <option value="left">左侧</option>
                            <option value="right">右侧</option>
                        </select>
                    </div>
                    {renderSeriesColorRows(
                        ((config.series as Array<{ name?: string }> | undefined) || [])
                            .map((item, idx) => (item?.name || '').trim() || `系列${idx + 1}`),
                    )}
                </>
            );

        case 'pie-chart':
        case 'gauge-chart':
        case 'radar-chart':
        case 'funnel-chart':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.title as string}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">标题字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={36}
                            value={(config.titleFontSize as number) || 14}
                            onChange={(e) => onChange('titleFontSize', Number(e.target.value))}
                        />
                    </div>
                    {type !== 'gauge-chart' && (
                        <>
                            <div className="property-row">
                                <label className="property-label">图例字号</label>
                                <input
                                    type="number"
                                    className="property-input"
                                    min={10}
                                    max={28}
                                    value={(config.legendFontSize as number) || 12}
                                    onChange={(e) => onChange('legendFontSize', Number(e.target.value))}
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">图例位置</label>
                                <select
                                    className="property-input"
                                    value={(config.legendPosition as string) || 'top'}
                                    onChange={(e) => onChange('legendPosition', e.target.value)}
                                >
                                    <option value="top">顶部</option>
                                    <option value="bottom">底部</option>
                                    <option value="left">左侧</option>
                                    <option value="right">右侧</option>
                                </select>
                            </div>
                            {renderSeriesColorRows(
                                ((config.data as Array<{ name?: string }> | undefined) || [])
                                    .map((item, idx) => (item?.name || '').trim() || `系列${idx + 1}`),
                            )}
                        </>
                    )}
                    {type === 'gauge-chart' && (
                        <div className="property-row">
                            <label className="property-label">值</label>
                            <input
                                type="number"
                                className="property-input"
                                value={config.value as number}
                                onChange={(e) => onChange('value', Number(e.target.value))}
                            />
                        </div>
                    )}
                </>
            );

        case 'number-card':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.title as string}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">数值</label>
                        <input
                            type="number"
                            className="property-input"
                            value={config.value as number}
                            onChange={(e) => onChange('value', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">前缀</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.prefix as string}
                            onChange={(e) => onChange('prefix', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">标题字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={36}
                            value={(config.titleFontSize as number) || 12}
                            onChange={(e) => onChange('titleFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">数值字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={16}
                            max={72}
                            value={(config.valueFontSize as number) || 32}
                            onChange={(e) => onChange('valueFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">标题颜色</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.titleColor as string) || '#ffffff'}
                            onChange={(e) => onChange('titleColor', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">数值颜色</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.valueColor as string) || '#ffffff'}
                            onChange={(e) => onChange('valueColor', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">背景色</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.backgroundColor as string) || '#1a1a2e'}
                            onChange={(e) => onChange('backgroundColor', e.target.value)}
                        />
                    </div>
                </>
            );

        case 'title':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">文本</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.text as string}
                            onChange={(e) => onChange('text', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">字号</label>
                        <input
                            type="number"
                            className="property-input"
                            value={config.fontSize as number}
                            onChange={(e) => onChange('fontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">颜色</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={config.color as string}
                            onChange={(e) => onChange('color', e.target.value)}
                        />
                    </div>
                </>
            );

        case 'markdown-text':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">Markdown</label>
                        <textarea
                            className="property-input"
                            rows={8}
                            value={(config.markdown as string) || ''}
                            onChange={(e) => onChange('markdown', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={36}
                            value={(config.fontSize as number) || 14}
                            onChange={(e) => onChange('fontSize', Number(e.target.value))}
                        />
                    </div>
                </>
            );

        case 'datetime':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">格式</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.format as string}
                            onChange={(e) => onChange('format', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">字号</label>
                        <input
                            type="number"
                            className="property-input"
                            value={config.fontSize as number}
                            onChange={(e) => onChange('fontSize', Number(e.target.value))}
                        />
                    </div>
                </>
            );

        case 'countdown':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.title as string) || '倒计时'}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">目标时间</label>
                        <input
                            type="datetime-local"
                            className="property-input"
                            value={String(config.targetTime || '').replace('Z', '').slice(0, 16)}
                            onChange={(e) => onChange('targetTime', new Date(e.target.value).toISOString())}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">显示天数</label>
                        <input
                            type="checkbox"
                            checked={config.showDays !== false}
                            onChange={(e) => onChange('showDays', e.target.checked)}
                        />
                    </div>
                </>
            );

        case 'marquee':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">文本</label>
                        <textarea
                            className="property-input"
                            rows={4}
                            value={(config.text as string) || ''}
                            onChange={(e) => onChange('text', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">速度(秒)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={120}
                            value={(config.speed as number) || 40}
                            onChange={(e) => onChange('speed', Number(e.target.value))}
                        />
                    </div>
                </>
            );

        case 'progress-bar':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">值 (%)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={100}
                            value={config.value as number}
                            onChange={(e) => onChange('value', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">显示标签</label>
                        <input
                            type="checkbox"
                            checked={config.showLabel as boolean}
                            onChange={(e) => onChange('showLabel', e.target.checked)}
                        />
                    </div>
                </>
            );

        case 'filter-input':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.label as string) || '筛选'}
                            onChange={(e) => onChange('label', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">变量Key</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.variableKey as string) || ''}
                            onChange={(e) => onChange('variableKey', e.target.value)}
                            placeholder="keyword"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">占位</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.placeholder as string) || ''}
                            onChange={(e) => onChange('placeholder', e.target.value)}
                            placeholder="请输入关键词"
                        />
                    </div>
                </>
            );

        case 'filter-select': {
            const options = Array.isArray(config.options)
                ? (config.options as Array<string | { label?: string; value?: string }>)
                : [];
            const optionText = options
                .map((item) => (typeof item === 'string' ? item : `${item.value || ''}|${item.label || ''}`))
                .join('\n');
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.label as string) || '筛选'}
                            onChange={(e) => onChange('label', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">变量Key</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.variableKey as string) || ''}
                            onChange={(e) => onChange('variableKey', e.target.value)}
                            placeholder="region"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">选项(每行1个)</label>
                        <textarea
                            className="property-input"
                            rows={5}
                            value={optionText}
                            onChange={(e) => {
                                const lines = e.target.value
                                    .split('\n')
                                    .map((line) => line.trim())
                                    .filter((line) => line.length > 0);
                                onChange('options', lines);
                            }}
                            placeholder={'华北\n华东\n华南'}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">占位</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.placeholder as string) || ''}
                            onChange={(e) => onChange('placeholder', e.target.value)}
                            placeholder="请选择"
                        />
                    </div>
                </>
            );
        }

        case 'filter-date-range':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.label as string) || '日期区间'}
                            onChange={(e) => onChange('label', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">开始变量</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.startKey as string) || ''}
                            onChange={(e) => onChange('startKey', e.target.value)}
                            placeholder="startDate"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">结束变量</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.endKey as string) || ''}
                            onChange={(e) => onChange('endKey', e.target.value)}
                            placeholder="endDate"
                        />
                    </div>
                </>
            );

        case 'image':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">图片URL</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.src as string}
                            onChange={(e) => onChange('src', e.target.value)}
                            placeholder="输入图片地址"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">填充方式</label>
                        <select
                            className="property-input"
                            value={config.fit as string}
                            onChange={(e) => onChange('fit', e.target.value)}
                        >
                            <option value="cover">覆盖</option>
                            <option value="contain">包含</option>
                            <option value="fill">拉伸</option>
                        </select>
                    </div>
                </>
            );

        case 'video':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">视频URL</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.src as string}
                            onChange={(e) => onChange('src', e.target.value)}
                            placeholder="输入视频地址"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">自动播放</label>
                        <input
                            type="checkbox"
                            checked={config.autoplay as boolean}
                            onChange={(e) => onChange('autoplay', e.target.checked)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">循环</label>
                        <input
                            type="checkbox"
                            checked={config.loop as boolean}
                            onChange={(e) => onChange('loop', e.target.checked)}
                        />
                    </div>
                </>
            );

        case 'iframe':
            return (
                <div className="property-row">
                    <label className="property-label">URL</label>
                    <input
                        type="text"
                        className="property-input"
                        value={config.src as string}
                        onChange={(e) => onChange('src', e.target.value)}
                        placeholder="输入网页地址"
                    />
                </div>
            );

        case 'border-box':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">边框类型</label>
                        <select
                            className="property-input"
                            value={config.boxType as number}
                            onChange={(e) => onChange('boxType', Number(e.target.value))}
                        >
                            {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13].map((n) => (
                                <option key={n} value={n}>边框 {n}</option>
                            ))}
                        </select>
                    </div>
                </>
            );

        case 'decoration':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">装饰类型</label>
                        <select
                            className="property-input"
                            value={config.decorationType as number}
                            onChange={(e) => onChange('decorationType', Number(e.target.value))}
                        >
                            {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map((n) => (
                                <option key={n} value={n}>装饰 {n}</option>
                            ))}
                        </select>
                    </div>
                </>
            );

        case 'water-level':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">值 (%)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={100}
                            value={config.value as number}
                            onChange={(e) => onChange('value', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">形状</label>
                        <select
                            className="property-input"
                            value={config.shape as string}
                            onChange={(e) => onChange('shape', e.target.value)}
                        >
                            <option value="round">圆形</option>
                            <option value="rect">矩形</option>
                            <option value="roundRect">圆角矩形</option>
                        </select>
                    </div>
                </>
            );

        case 'digital-flop':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">数值</label>
                        <input
                            type="number"
                            className="property-input"
                            value={(config.number as number[])?.[0] || 0}
                            onChange={(e) => onChange('number', [Number(e.target.value)])}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">字号</label>
                        <input
                            type="number"
                            className="property-input"
                            value={(config.style as { fontSize?: number })?.fontSize || 30}
                            onChange={(e) => onChange('style', {
                                ...(config.style as object),
                                fontSize: Number(e.target.value),
                            })}
                        />
                    </div>
                </>
            );

        case 'percent-pond':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">值 (%)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={100}
                            value={config.value as number}
                            onChange={(e) => onChange('value', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">边框宽度</label>
                        <input
                            type="number"
                            className="property-input"
                            min={1}
                            max={10}
                            value={config.borderWidth as number}
                            onChange={(e) => onChange('borderWidth', Number(e.target.value))}
                        />
                    </div>
                </>
            );

        case 'scatter-chart':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.title as string}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">标题字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={36}
                            value={(config.titleFontSize as number) || 14}
                            onChange={(e) => onChange('titleFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">坐标轴字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={28}
                            value={(config.axisFontSize as number) || 12}
                            onChange={(e) => onChange('axisFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">图例字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={28}
                            value={(config.legendFontSize as number) || 12}
                            onChange={(e) => onChange('legendFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">图例位置</label>
                        <select
                            className="property-input"
                            value={(config.legendPosition as string) || 'top'}
                            onChange={(e) => onChange('legendPosition', e.target.value)}
                        >
                            <option value="top">顶部</option>
                            <option value="bottom">底部</option>
                            <option value="left">左侧</option>
                            <option value="right">右侧</option>
                        </select>
                    </div>
                    {renderSeriesColorRows(['散点系列'])}
                </>
            );

        case 'map-chart':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.title as string) || '区域地图'}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">地图范围</label>
                        <select
                            className="property-input"
                            value={(config.mapScope as string) || 'china'}
                            onChange={(e) => onChange('mapScope', e.target.value)}
                        >
                            <option value="china">中国</option>
                            <option value="world">世界</option>
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">区域变量Key</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.regionVariableKey as string) || ''}
                            onChange={(e) => onChange('regionVariableKey', e.target.value)}
                            placeholder="region"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">区域编码变量Key</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.regionCodeVariableKey as string) || ''}
                            onChange={(e) => onChange('regionCodeVariableKey', e.target.value)}
                            placeholder="region_code"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">优先使用内置底图</label>
                        <input
                            type="checkbox"
                            checked={config.usePresetGeoJson !== false}
                            onChange={(e) => onChange('usePresetGeoJson', e.target.checked)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">GeoJSON URL(可选)</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.geoJsonUrl as string) || ''}
                            onChange={(e) => onChange('geoJsonUrl', e.target.value)}
                            placeholder="https://.../map.geojson"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">启用下钻</label>
                        <input
                            type="checkbox"
                            checked={config.enableRegionDrill !== false}
                            onChange={(e) => onChange('enableRegionDrill', e.target.checked)}
                        />
                    </div>
                </>
            );

        case 'scroll-board':
            return <ScrollBoardConfig component={component} onChange={onChange} />;

        case 'table':
            return <TableConfig component={component} onChange={onChange} />;

        case 'scroll-ranking':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">行数</label>
                        <input
                            type="number"
                            className="property-input"
                            min={1}
                            max={20}
                            value={config.rowNum as number}
                            onChange={(e) => onChange('rowNum', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">等待时间(ms)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={500}
                            max={10000}
                            step={500}
                            value={config.waitTime as number || 2000}
                            onChange={(e) => onChange('waitTime', Number(e.target.value))}
                        />
                    </div>
                </>
            );

        case 'shape':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">形状</label>
                        <select
                            className="property-input"
                            value={(config.shapeType as string) || 'rect'}
                            onChange={(e) => onChange('shapeType', e.target.value)}
                        >
                            <option value="rect">矩形</option>
                            <option value="circle">圆形</option>
                            <option value="line">线条</option>
                            <option value="arrow">箭头</option>
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">填充色</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.fillColor as string) || '#3b82f6'}
                            onChange={(e) => onChange('fillColor', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">边框色</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.borderColor as string) || '#60a5fa'}
                            onChange={(e) => onChange('borderColor', e.target.value)}
                        />
                    </div>
                </>
            );

        case 'container':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.title as string) || '容器'}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">内边距</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={80}
                            value={(config.padding as number) || 12}
                            onChange={(e) => onChange('padding', Number(e.target.value))}
                        />
                    </div>
                </>
            );

        default:
            return (
                <div className="empty-state-hint">
                    暂无可配置项
                </div>
            );
    }
}

function renderDataSourceConfig(
    component: ScreenComponent,
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void,
    globalVariables: ScreenGlobalVariable[],
) {
    const ds = component.dataSource as DataSourceConfig | undefined;
    const dsType = resolveDataSourceType(ds);
    const sqlConfig = resolveSqlConfig(ds);

    const cardBindings: CardParameterBinding[] = ds?.type === 'card' ? (ds.cardConfig?.parameterBindings ?? []) : [];
    const sqlBindings: CardParameterBinding[] = dsType === 'sql' ? (sqlConfig?.parameterBindings ?? []) : [];
    const variableOptions = (globalVariables ?? []).map((item) => ({ key: item.key, label: item.label || item.key }));

    const updateCardBindings = (bindings: CardParameterBinding[]) => {
        setDataSource({
            type: 'card',
            sourceType: 'card',
            cardConfig: {
                ...(ds?.type === 'card' ? ds.cardConfig : {}),
                cardId: ds?.type === 'card' ? (ds.cardConfig?.cardId ?? 0) : 0,
                parameterBindings: bindings,
            },
        });
    };

    const updateSqlBindings = (bindings: CardParameterBinding[]) => {
        const base = resolveSqlConfig(ds);
        setDataSource({
            type: 'sql',
            sourceType: 'sql',
            refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
            sqlConfig: {
                ...(base ?? { query: '' }),
                query: base?.query ?? '',
                databaseId: base?.databaseId,
                connectionId: base?.connectionId,
                queryTimeoutSeconds: base?.queryTimeoutSeconds,
                maxRows: base?.maxRows,
                parameterBindings: bindings,
            },
        });
    };

    const setDataSource = (newDs: DataSourceConfig | undefined) => {
        updateComponent(component.id, { dataSource: newDs });
    };

    const setType = (nextType: string) => {
        if (nextType === 'static') {
            setDataSource(undefined);
            return;
        }
        if (nextType === 'card') {
            setDataSource({
                type: 'card',
                sourceType: 'card',
                cardConfig: {
                    cardId: ds?.type === 'card' ? (ds.cardConfig?.cardId ?? 0) : 0,
                    refreshInterval: ds?.type === 'card' ? ds.cardConfig?.refreshInterval : undefined,
                    metricId: ds?.type === 'card' ? ds.cardConfig?.metricId : undefined,
                    metricVersion: ds?.type === 'card' ? ds.cardConfig?.metricVersion : undefined,
                    parameterBindings: ds?.type === 'card' ? (ds.cardConfig?.parameterBindings ?? []) : [],
                },
            });
            return;
        }
        if (nextType === 'api') {
            setDataSource({
                type: 'api',
                sourceType: 'api',
                refreshInterval: ds?.type === 'api' ? ds.refreshInterval : undefined,
                apiConfig: {
                    url: ds?.type === 'api' ? (ds.apiConfig?.url ?? '') : '',
                    method: ds?.type === 'api' ? (ds.apiConfig?.method ?? 'GET') : 'GET',
                    body: ds?.type === 'api' ? ds.apiConfig?.body : undefined,
                },
            });
            return;
        }
        if (nextType === 'sql') {
            const base = resolveSqlConfig(ds);
            setDataSource({
                type: 'sql',
                sourceType: 'sql',
                refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
                sqlConfig: {
                    databaseId: base?.databaseId,
                    connectionId: base?.connectionId,
                    query: base?.query ?? 'select 1',
                    queryTimeoutSeconds: base?.queryTimeoutSeconds,
                    maxRows: base?.maxRows,
                    parameterBindings: base?.parameterBindings ?? [],
                },
            });
            return;
        }
        if (nextType === 'dataset') {
            setDataSource({
                type: 'dataset',
                sourceType: 'dataset',
                refreshInterval: dsType === 'dataset' ? ds?.refreshInterval : undefined,
                datasetConfig: dsType === 'dataset'
                    ? ds?.datasetConfig
                    : { queryBody: { database: 0, type: 'query', query: {} } },
            });
        }
    };

    return (
        <>
            <div className="property-row">
                <label className="property-label">类型</label>
                <select
                    className="property-input"
                    value={dsType}
                    onChange={(e) => setType(e.target.value)}
                >
                    <option value="static">静态数据</option>
                    <option value="card">Card 查询</option>
                    <option value="api">HTTP API</option>
                    <option value="sql">SQL 模式</option>
                    <option value="dataset">Dataset 模式</option>
                </select>
            </div>

            {dsType === 'card' && (
                <>
                    <div className="property-row">
                        <label className="property-label">Card</label>
                        <CardIdPicker
                            value={ds?.type === 'card' ? (ds.cardConfig?.cardId ?? 0) : 0}
                            onChange={(cardId) => {
                                setDataSource({
                                    type: 'card',
                                    sourceType: 'card',
                                    cardConfig: {
                                        ...(ds?.type === 'card' ? ds.cardConfig : {}),
                                        cardId,
                                    },
                                });
                            }}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            step={10}
                            value={ds?.type === 'card' ? (ds.cardConfig?.refreshInterval ?? 0) : 0}
                            onChange={(e) => {
                                const val = Number(e.target.value);
                                setDataSource({
                                    type: 'card',
                                    sourceType: 'card',
                                    cardConfig: {
                                        ...(ds?.type === 'card' ? ds.cardConfig : {}),
                                        cardId: ds?.type === 'card' ? (ds.cardConfig?.cardId ?? 0) : 0,
                                        refreshInterval: val > 0 ? val : undefined,
                                    },
                                });
                            }}
                            placeholder="0=不刷新"
                        />
                    </div>
                    <MetricBindingEditor
                        metricId={ds?.type === 'card' ? ds.cardConfig?.metricId : undefined}
                        metricVersion={ds?.type === 'card' ? ds.cardConfig?.metricVersion : undefined}
                        onMetricIdChange={(metricId) => {
                            setDataSource({
                                type: 'card',
                                sourceType: 'card',
                                cardConfig: {
                                    ...(ds?.type === 'card' ? ds.cardConfig : {}),
                                    cardId: ds?.type === 'card' ? (ds.cardConfig?.cardId ?? 0) : 0,
                                    refreshInterval: ds?.type === 'card' ? ds.cardConfig?.refreshInterval : undefined,
                                    metricId,
                                    metricVersion: metricId ? (ds?.type === 'card' ? ds.cardConfig?.metricVersion : undefined) : undefined,
                                },
                            });
                        }}
                        onMetricVersionChange={(metricVersion) => {
                            setDataSource({
                                type: 'card',
                                sourceType: 'card',
                                cardConfig: {
                                    ...(ds?.type === 'card' ? ds.cardConfig : {}),
                                    cardId: ds?.type === 'card' ? (ds.cardConfig?.cardId ?? 0) : 0,
                                    refreshInterval: ds?.type === 'card' ? ds.cardConfig?.refreshInterval : undefined,
                                    metricId: ds?.type === 'card' ? ds.cardConfig?.metricId : undefined,
                                    metricVersion,
                                },
                            });
                        }}
                    />
                    <CardParamBindingsEditor
                        bindings={cardBindings}
                        globalVariables={globalVariables}
                        onChange={updateCardBindings}
                    />
                </>
            )}

            {dsType === 'api' && (
                <>
                    <div className="property-row">
                        <label className="property-label">URL</label>
                        <input
                            type="text"
                            className="property-input"
                            value={ds?.type === 'api' ? (ds.apiConfig?.url ?? '') : ''}
                            onChange={(e) => {
                                setDataSource({
                                    type: 'api',
                                    sourceType: 'api',
                                    refreshInterval: ds?.type === 'api' ? ds.refreshInterval : undefined,
                                    apiConfig: {
                                        ...(ds?.type === 'api' ? ds.apiConfig : { method: 'GET' as const }),
                                        url: e.target.value,
                                        method: ds?.type === 'api' ? (ds.apiConfig?.method ?? 'GET') : 'GET',
                                    },
                                });
                            }}
                            placeholder="/analytics/api/card/1/query 或 https://..."
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">方法</label>
                        <select
                            className="property-input"
                            value={ds?.type === 'api' ? (ds.apiConfig?.method ?? 'GET') : 'GET'}
                            onChange={(e) => {
                                const method = (e.target.value as 'GET' | 'POST') || 'GET';
                                setDataSource({
                                    type: 'api',
                                    sourceType: 'api',
                                    refreshInterval: ds?.type === 'api' ? ds.refreshInterval : undefined,
                                    apiConfig: {
                                        ...(ds?.type === 'api' ? ds.apiConfig : {}),
                                        url: ds?.type === 'api' ? (ds.apiConfig?.url ?? '') : '',
                                        method,
                                    },
                                });
                            }}
                        >
                            <option value="GET">GET</option>
                            <option value="POST">POST</option>
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">Body</label>
                        <textarea
                            className="property-input"
                            rows={4}
                            value={ds?.type === 'api' ? (ds.apiConfig?.body ?? '') : ''}
                            onChange={(e) => {
                                setDataSource({
                                    type: 'api',
                                    sourceType: 'api',
                                    refreshInterval: ds?.type === 'api' ? ds.refreshInterval : undefined,
                                    apiConfig: {
                                        ...(ds?.type === 'api' ? ds.apiConfig : {}),
                                        url: ds?.type === 'api' ? (ds.apiConfig?.url ?? '') : '',
                                        method: ds?.type === 'api' ? (ds.apiConfig?.method ?? 'GET') : 'GET',
                                        body: e.target.value,
                                    },
                                });
                            }}
                            placeholder='{"parameters":[]}'
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            step={10}
                            value={ds?.type === 'api' ? (ds.refreshInterval ?? 0) : 0}
                            onChange={(e) => {
                                const val = Number(e.target.value);
                                setDataSource({
                                    type: 'api',
                                    sourceType: 'api',
                                    refreshInterval: val > 0 ? val : undefined,
                                    apiConfig: ds?.type === 'api'
                                        ? {
                                            ...(ds.apiConfig ?? { method: 'GET' as const, url: '' }),
                                            method: ds.apiConfig?.method ?? 'GET',
                                            url: ds.apiConfig?.url ?? '',
                                        }
                                        : { method: 'GET', url: '' },
                                });
                            }}
                            placeholder="0=不刷新"
                        />
                    </div>
                </>
            )}

            {dsType === 'sql' && (
                <>
                    <div className="property-row">
                        <label className="property-label">数据库</label>
                        <DatabaseIdPicker
                            value={sqlConfig?.databaseId ?? 0}
                            onChange={(databaseId) => {
                                const base = resolveSqlConfig(ds);
                                setDataSource({
                                    type: 'sql',
                                    sourceType: 'sql',
                                    refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
                                    sqlConfig: {
                                        ...(base ?? { query: '' }),
                                        databaseId: databaseId > 0 ? databaseId : undefined,
                                        query: base?.query ?? '',
                                    },
                                });
                            }}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">数据库ID(手工)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={1}
                            value={sqlConfig?.databaseId ?? 0}
                            onChange={(e) => {
                                const n = Number(e.target.value);
                                const base = resolveSqlConfig(ds);
                                setDataSource({
                                    type: 'sql',
                                    sourceType: 'sql',
                                    refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
                                    sqlConfig: {
                                        ...(base ?? { query: '' }),
                                        databaseId: Number.isFinite(n) && n > 0 ? n : undefined,
                                        query: base?.query ?? '',
                                    },
                                });
                            }}
                            placeholder="用于离线环境或未同步数据库列表"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">SQL</label>
                        <textarea
                            className="property-input"
                            rows={6}
                            value={sqlConfig?.query ?? ''}
                            onChange={(e) => {
                                const base = resolveSqlConfig(ds);
                                setDataSource({
                                    type: 'sql',
                                    sourceType: 'sql',
                                    refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
                                    sqlConfig: {
                                        ...(base ?? { query: '' }),
                                        databaseId: base?.databaseId,
                                        query: e.target.value,
                                    },
                                });
                            }}
                            placeholder="select * from public.table where day = {{day}} limit 200"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">最大行数</label>
                        <input
                            type="number"
                            className="property-input"
                            min={1}
                            step={100}
                            value={sqlConfig?.maxRows ?? 2000}
                            onChange={(e) => {
                                const n = Number(e.target.value);
                                const base = resolveSqlConfig(ds);
                                setDataSource({
                                    type: 'sql',
                                    sourceType: 'sql',
                                    refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
                                    sqlConfig: {
                                        ...(base ?? { query: '' }),
                                        query: base?.query ?? '',
                                        maxRows: Number.isFinite(n) && n > 0 ? n : undefined,
                                    },
                                });
                            }}
                            placeholder="默认2000"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">超时(秒)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={1}
                            step={5}
                            value={sqlConfig?.queryTimeoutSeconds ?? 60}
                            onChange={(e) => {
                                const n = Number(e.target.value);
                                const base = resolveSqlConfig(ds);
                                setDataSource({
                                    type: 'sql',
                                    sourceType: 'sql',
                                    refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
                                    sqlConfig: {
                                        ...(base ?? { query: '' }),
                                        query: base?.query ?? '',
                                        queryTimeoutSeconds: Number.isFinite(n) && n > 0 ? n : undefined,
                                    },
                                });
                            }}
                            placeholder="默认60"
                        />
                    </div>
                    <CardParamBindingsEditor
                        bindings={sqlBindings}
                        globalVariables={globalVariables}
                        onChange={updateSqlBindings}
                    />
                    <div className="property-row">
                        <label className="property-label">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            step={10}
                            value={dsType === 'sql' ? (ds?.refreshInterval ?? 0) : 0}
                            onChange={(e) => {
                                const val = Number(e.target.value);
                                const base = resolveSqlConfig(ds);
                                setDataSource({
                                    type: 'sql',
                                    sourceType: 'sql',
                                    refreshInterval: val > 0 ? val : undefined,
                                    sqlConfig: base
                                        ? {
                                            ...base,
                                            query: base.query ?? '',
                                        }
                                        : { query: '' },
                                });
                            }}
                            placeholder="0=不刷新"
                        />
                    </div>
                </>
            )}

            {dsType === 'dataset' && (
                <>
                    <div className="property-row">
                        <label className="property-label">QueryBody(JSON)</label>
                        <textarea
                            className="property-input"
                            rows={8}
                            value={safeJsonStringify(ds?.datasetConfig?.queryBody)}
                            onChange={(e) => {
                                const parsed = safeJsonParse(e.target.value);
                                setDataSource({
                                    type: 'dataset',
                                    sourceType: 'dataset',
                                    refreshInterval: dsType === 'dataset' ? ds?.refreshInterval : undefined,
                                    datasetConfig: { queryBody: parsed ?? {} },
                                });
                            }}
                            placeholder='{"database":1,"type":"native","native":{"query":"select 1"}}'
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            step={10}
                            value={dsType === 'dataset' ? (ds?.refreshInterval ?? 0) : 0}
                            onChange={(e) => {
                                const val = Number(e.target.value);
                                setDataSource({
                                    type: 'dataset',
                                    sourceType: 'dataset',
                                    refreshInterval: val > 0 ? val : undefined,
                                    datasetConfig: ds?.datasetConfig ?? { queryBody: {} },
                                });
                            }}
                            placeholder="0=不刷新"
                        />
                    </div>
                </>
            )}
        </>
    );
}

function resolveDataSourceType(ds?: DataSourceConfig): 'static' | QuerySourceType {
    const type = ((ds?.sourceType ?? ds?.type) || 'static').toLowerCase();
    if (type === 'database' || type === 'sql') return 'sql';
    if (type === 'card' || type === 'api' || type === 'dataset' || type === 'metric') {
        return type;
    }
    return 'static';
}

function resolveSqlConfig(ds?: DataSourceConfig): DataSourceConfig['sqlConfig'] | DataSourceConfig['databaseConfig'] | undefined {
    if (!ds) return undefined;
    return ds.sqlConfig ?? ds.databaseConfig;
}

function safeJsonParse(text: string): Record<string, unknown> | null {
    const raw = (text || '').trim();
    if (!raw) return {};
    try {
        const parsed = JSON.parse(raw);
        return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
            ? (parsed as Record<string, unknown>)
            : null;
    } catch {
        return null;
    }
}

function safeJsonStringify(value: unknown): string {
    if (!value || typeof value !== 'object') return '{}';
    try {
        return JSON.stringify(value, null, 2);
    } catch {
        return '{}';
    }
}


const INTERACTION_COMPONENT_TYPES = new Set<ComponentType>([
    'line-chart',
    'bar-chart',
    'pie-chart',
    'scatter-chart',
    'radar-chart',
    'funnel-chart',
]);

function renderInteractionConfig(
    component: ScreenComponent,
    globalVariables: ScreenGlobalVariable[],
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void,
) {
    if (!INTERACTION_COMPONENT_TYPES.has(component.type)) {
        return null;
    }

    const interaction = component.interaction ?? { enabled: false, mappings: [] as ComponentInteractionMapping[] };
    const mappings = interaction.mappings ?? [];

    const setInteraction = (next: typeof interaction) => {
        updateComponent(component.id, { interaction: next });
    };

    const updateMapping = (index: number, patch: Partial<ComponentInteractionMapping>) => {
        const next = [...mappings];
        next[index] = { ...next[index], ...patch };
        setInteraction({ ...interaction, mappings: next });
    };

    return (
        <div className="property-section">
            <div className="property-section-title">联动配置</div>

            <div className="property-row">
                <label className="property-label">启用点击联动</label>
                <input
                    type="checkbox"
                    checked={interaction.enabled ?? false}
                    onChange={(e) => setInteraction({ ...interaction, enabled: e.target.checked })}
                />
            </div>

            {interaction.enabled && (
                <>
                    {globalVariables.length === 0 && (
                        <div style={{ fontSize: 11, color: '#888', marginBottom: 8 }}>
                            请先在顶部“变量”里创建全局变量。
                        </div>
                    )}

                    {mappings.map((mapping, index) => (
                        <div
                            key={`interaction-${index}`}
                            style={{
                                border: '1px solid rgba(255,255,255,0.06)',
                                borderRadius: 4,
                                padding: 8,
                                marginBottom: 8,
                            }}
                        >
                            <div className="property-row">
                                <label className="property-label">目标变量</label>
                                <select
                                    className="property-input"
                                    value={mapping.variableKey || ''}
                                    onChange={(e) => updateMapping(index, { variableKey: e.target.value })}
                                >
                                    <option value="">-- 请选择 --</option>
                                    {globalVariables.map((item) => (
                                        <option key={item.key} value={item.key}>
                                            {item.label || item.key} ({item.key})
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <div className="property-row">
                                <label className="property-label">取值路径</label>
                                <select
                                    className="property-input"
                                    value={mapping.sourcePath || 'name'}
                                    onChange={(e) => updateMapping(index, { sourcePath: e.target.value })}
                                >
                                    <option value="name">name</option>
                                    <option value="seriesName">seriesName</option>
                                    <option value="value">value</option>
                                    <option value="data.name">data.name</option>
                                </select>
                            </div>

                            <button
                                className="property-input"
                                onClick={() => setInteraction({ ...interaction, mappings: mappings.filter((_, i) => i !== index) })}
                                style={{ width: '100%', cursor: 'pointer', textAlign: 'center', color: '#ef4444' }}
                            >
                                删除联动规则
                            </button>
                        </div>
                    ))}

                    <button
                        className="property-input"
                        onClick={() => setInteraction({
                            ...interaction,
                            mappings: [...mappings, { variableKey: globalVariables[0]?.key ?? '', sourcePath: 'name' }],
                        })}
                        style={{ width: '100%', cursor: 'pointer', textAlign: 'center', color: '#6366f1' }}
                    >
                        + 添加联动规则
                    </button>
                </>
            )}
        </div>
    );
}
function renderDrillDownConfig(
    component: ScreenComponent,
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void,
) {
    const { type, dataSource, drillDown } = component;
    const cardId = dataSource?.type === 'card' ? dataSource.cardConfig?.cardId : undefined;

    // Only show for drillable chart types with a valid card data source
    if (!DRILLABLE_TYPES.has(type) || dataSource?.type !== 'card' || !cardId || cardId <= 0) {
        return null;
    }

    const enabled = drillDown?.enabled ?? false;
    const levels = drillDown?.levels ?? [];

    const setDrillDown = (updates: Partial<typeof drillDown>) => {
        updateComponent(component.id, {
            drillDown: { enabled, levels, ...drillDown, ...updates },
        });
    };

    const updateLevel = (index: number, field: keyof DrillLevel, value: string | number) => {
        const newLevels = [...levels];
        newLevels[index] = { ...newLevels[index], [field]: value };
        setDrillDown({ levels: newLevels });
    };

    const removeLevel = (index: number) => {
        setDrillDown({ levels: levels.filter((_, i) => i !== index) });
    };

    const addLevel = () => {
        setDrillDown({ levels: [...levels, { cardId: 0, paramName: '', label: '' }] });
    };

    return (
        <div className="property-section">
            <div className="property-section-title">下钻配置</div>

            <div className="property-row">
                <label className="property-label">启用下钻</label>
                <input
                    type="checkbox"
                    checked={enabled}
                    onChange={(e) => setDrillDown({ enabled: e.target.checked })}
                />
            </div>

            {enabled && (
                <>
                    {levels.map((level, i) => (
                        <div key={i} style={{
                            border: '1px solid rgba(255,255,255,0.1)',
                            borderRadius: 4,
                            padding: 8,
                            marginBottom: 8,
                        }}>
                            <div style={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                marginBottom: 4,
                                fontSize: 11,
                                color: '#888',
                            }}>
                                <span>层级 {i + 1}</span>
                                <button
                                    className="property-btn-small"
                                    onClick={() => removeLevel(i)}
                                    style={{
                                        background: 'none', border: 'none',
                                        color: '#ef4444', cursor: 'pointer', fontSize: 11,
                                    }}
                                >
                                    删除
                                </button>
                            </div>
                            <div className="property-row">
                                <label className="property-label">Card</label>
                                <CardIdPicker
                                    value={level.cardId || 0}
                                    onChange={(cardId) => updateLevel(i, 'cardId', cardId)}
                                    placeholder="-- 下钻目标 --"
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">参数名</label>
                                <input
                                    type="text"
                                    className="property-input"
                                    value={level.paramName}
                                    onChange={(e) => updateLevel(i, 'paramName', e.target.value)}
                                    placeholder="如: region"
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">标签</label>
                                <input
                                    type="text"
                                    className="property-input"
                                    value={level.label}
                                    onChange={(e) => updateLevel(i, 'label', e.target.value)}
                                    placeholder="如: 地区"
                                />
                            </div>
                        </div>
                    ))}

                    <button
                        className="property-input"
                        onClick={addLevel}
                        style={{
                            width: '100%', cursor: 'pointer',
                            textAlign: 'center', color: '#6366f1',
                        }}
                    >
                        + 添加下钻层级
                    </button>
                </>
            )}
        </div>
    );
}

/** Column config entry for scroll-board */
interface ColumnEntry {
    source: string;
    alias?: string;
    align?: 'left' | 'center' | 'right';
    width?: number;
    formatter?: 'auto' | 'string' | 'number' | 'percent' | 'date';
}

/** scroll-board 专用属性面板，支持动态列选择 */
function ScrollBoardConfig({ component, onChange }: {
    component: ScreenComponent;
    onChange: (key: string, value: unknown) => void;
}) {
    const { config, dataSource } = component;

    // Read _sourceColumns persisted by ComponentRenderer (no separate API call)
    const sourceCols = config._sourceColumns as Array<{ name: string; displayName: string }> ?? [];
    const columns = config.columns as ColumnEntry[] | undefined;
    const hasCardSource = dataSource?.type === 'card' && !!dataSource.cardConfig?.cardId;

    // Static fallback: use config.header when no card data source
    const staticHeaders = config.header as string[] || [];
    const columnAlias = config.columnAlias as Record<string, string> || {};

    // Helper: initialize columns config from source columns (all selected)
    const initColumns = (): ColumnEntry[] =>
        sourceCols.map(c => ({ source: c.name }));

    const handleToggleColumn = (colName: string, selected: boolean) => {
        const current = columns ?? initColumns();
        if (selected) {
            // Add column back at its original source position
            const originalIdx = sourceCols.findIndex(c => c.name === colName);
            const newCols = [...current];
            let insertIdx = newCols.length;
            for (let i = 0; i < newCols.length; i++) {
                const idx = sourceCols.findIndex(c => c.name === newCols[i].source);
                if (idx > originalIdx) { insertIdx = i; break; }
            }
            newCols.splice(insertIdx, 0, { source: colName });
            onChange('columns', newCols);
        } else {
            onChange('columns', current.filter(c => c.source !== colName));
        }
    };

    const handleAliasChange = (colName: string, alias: string) => {
        const current = columns ?? initColumns();
        onChange('columns', current.map(c => {
            if (c.source !== colName) return c;
            if (alias) return { ...c, alias };
            const { alias: _a, ...rest } = c;
            return rest;
        }));
    };

    const handleColumnPatch = (
        colName: string,
        patch: Partial<Pick<ColumnEntry, 'align' | 'width' | 'formatter'>>,
    ) => {
        const current = columns ?? initColumns();
        onChange('columns', current.map((c) => {
            if (c.source !== colName) return c;
            return { ...c, ...patch };
        }));
    };

    return (
        <>
            <div className="property-row">
                <label className="property-label">行数</label>
                <input
                    type="number"
                    className="property-input"
                    min={1}
                    max={20}
                    value={config.rowNum as number}
                    onChange={(e) => onChange('rowNum', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">等待时间(ms)</label>
                <input
                    type="number"
                    className="property-input"
                    min={500}
                    max={10000}
                    step={500}
                    value={config.waitTime as number || 2000}
                    onChange={(e) => onChange('waitTime', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">表头颜色</label>
                <input
                    type="color"
                    className="property-color-input"
                    value={(config.headerColor as string) || '#ffffff'}
                    onChange={(e) => onChange('headerColor', e.target.value)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">表头背景</label>
                <input
                    type="color"
                    className="property-color-input"
                    value={(config.headerBGC as string) || '#003366'}
                    onChange={(e) => onChange('headerBGC', e.target.value)}
                />
            </div>

            {/* Card 数据源: 等待列加载 */}
            {hasCardSource && sourceCols.length === 0 && (
                <div style={{ fontSize: 11, color: '#888', marginTop: 8, padding: '4px 0' }}>
                    等待数据源加载列信息…
                </div>
            )}

            {/* Card 数据源: 动态列选择 */}
            {hasCardSource && sourceCols.length > 0 && (
                <>
                    <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                        显示列 (来自数据源)
                    </div>
                    {sourceCols.map(col => {
                        const colConfig = columns?.find(c => c.source === col.name);
                        const isSelected = columns ? !!colConfig : true;
                        const displayName = col.displayName || col.name;
                        return (
                            <div key={col.name} style={{
                                border: '1px solid rgba(255,255,255,0.06)',
                                borderRadius: 4,
                                padding: '4px 6px',
                                marginBottom: 4,
                            }}>
                                <div className="property-row" style={{ marginBottom: isSelected ? 4 : 0 }}>
                                    <label className="property-label" style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                                        <input
                                            type="checkbox"
                                            checked={isSelected}
                                            onChange={(e) => handleToggleColumn(col.name, e.target.checked)}
                                        />
                                        <span title={col.name}>{displayName}</span>
                                    </label>
                                </div>
                                {isSelected && (
                                    <>
                                        <div className="property-row">
                                            <label className="property-label">表头标题</label>
                                            <input
                                                type="text"
                                                className="property-input"
                                                placeholder={displayName}
                                                value={colConfig?.alias || ''}
                                                onChange={(e) => handleAliasChange(col.name, e.target.value)}
                                            />
                                        </div>
                                        <div className="property-row">
                                            <label className="property-label">对齐</label>
                                            <select
                                                className="property-input"
                                                value={(colConfig?.align as string) || 'center'}
                                                onChange={(e) => handleColumnPatch(col.name, { align: e.target.value as ColumnEntry['align'] })}
                                            >
                                                <option value="left">左</option>
                                                <option value="center">中</option>
                                                <option value="right">右</option>
                                            </select>
                                        </div>
                                        <div className="property-row">
                                            <label className="property-label">列宽(%)</label>
                                            <input
                                                type="number"
                                                className="property-input"
                                                min={5}
                                                max={100}
                                                value={typeof colConfig?.width === 'number' ? colConfig.width : ''}
                                                placeholder="自动"
                                                onChange={(e) => {
                                                    const raw = e.target.value.trim();
                                                    if (!raw) {
                                                        handleColumnPatch(col.name, { width: undefined });
                                                        return;
                                                    }
                                                    const width = Number(raw);
                                                    handleColumnPatch(col.name, {
                                                        width: Number.isFinite(width) ? Math.max(5, Math.min(100, width)) : undefined,
                                                    });
                                                }}
                                            />
                                        </div>
                                        <div className="property-row">
                                            <label className="property-label">格式化</label>
                                            <select
                                                className="property-input"
                                                value={(colConfig?.formatter as string) || 'auto'}
                                                onChange={(e) => handleColumnPatch(col.name, { formatter: e.target.value as ColumnEntry['formatter'] })}
                                            >
                                                <option value="auto">自动</option>
                                                <option value="string">文本</option>
                                                <option value="number">数字</option>
                                                <option value="percent">百分比</option>
                                                <option value="date">日期时间</option>
                                            </select>
                                        </div>
                                    </>
                                )}
                            </div>
                        );
                    })}
                </>
            )}

            {/* 静态数据源: 按索引的表头别名 (保持向后兼容) */}
            {!hasCardSource && staticHeaders.length > 0 && (
                <>
                    <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                        表头别名
                    </div>
                    {staticHeaders.map((h, i) => (
                        <div className="property-row" key={i}>
                            <label className="property-label" title={h}>列{i + 1}</label>
                            <input
                                type="text"
                                className="property-input"
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

/** table 专用属性面板，支持动态列选择和样式配置 */
function TableConfig({ component, onChange }: {
    component: ScreenComponent;
    onChange: (key: string, value: unknown) => void;
}) {
    const { config, dataSource } = component;

    const sourceCols = config._sourceColumns as Array<{ name: string; displayName: string }> ?? [];
    const columns = config.columns as ColumnEntry[] | undefined;
    const hasCardSource = dataSource?.type === 'card' && !!dataSource.cardConfig?.cardId;

    const staticHeaders = config.header as string[] || [];
    const columnAlias = config.columnAlias as Record<string, string> || {};

    const initColumns = (): ColumnEntry[] =>
        sourceCols.map(c => ({ source: c.name }));

    const handleToggleColumn = (colName: string, selected: boolean) => {
        const current = columns ?? initColumns();
        if (selected) {
            const originalIdx = sourceCols.findIndex(c => c.name === colName);
            const newCols = [...current];
            let insertIdx = newCols.length;
            for (let i = 0; i < newCols.length; i++) {
                const idx = sourceCols.findIndex(c => c.name === newCols[i].source);
                if (idx > originalIdx) {
                    insertIdx = i;
                    break;
                }
            }
            newCols.splice(insertIdx, 0, { source: colName });
            onChange('columns', newCols);
        } else {
            onChange('columns', current.filter(c => c.source !== colName));
        }
    };

    const handleAliasChange = (colName: string, alias: string) => {
        const current = columns ?? initColumns();
        onChange('columns', current.map(c => {
            if (c.source !== colName) return c;
            if (alias) return { ...c, alias };
            const { alias: _a, ...rest } = c;
            return rest;
        }));
    };

    const handleColumnPatch = (
        colName: string,
        patch: Partial<Pick<ColumnEntry, 'align' | 'width' | 'formatter'>>,
    ) => {
        const current = columns ?? initColumns();
        onChange('columns', current.map((c) => {
            if (c.source !== colName) return c;
            return { ...c, ...patch };
        }));
    };

    return (
        <>
            <div className="property-row">
                <label className="property-label">字号</label>
                <input
                    type="number"
                    className="property-input"
                    min={10}
                    max={24}
                    value={(config.fontSize as number) || 13}
                    onChange={(e) => onChange('fontSize', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">表头颜色</label>
                <input
                    type="color"
                    className="property-color-input"
                    value={(config.headerColor as string) || '#e5e7eb'}
                    onChange={(e) => onChange('headerColor', e.target.value)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">表头背景</label>
                <input
                    type="color"
                    className="property-color-input"
                    value={(config.headerBackground as string) || '#64748b'}
                    onChange={(e) => onChange('headerBackground', e.target.value)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">正文颜色</label>
                <input
                    type="color"
                    className="property-color-input"
                    value={(config.bodyColor as string) || '#d1d5db'}
                    onChange={(e) => onChange('bodyColor', e.target.value)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">边框颜色</label>
                <input
                    type="color"
                    className="property-color-input"
                    value={(config.borderColor as string) || '#94a3b8'}
                    onChange={(e) => onChange('borderColor', e.target.value)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">启用排序</label>
                <input
                    type="checkbox"
                    checked={config.enableSort !== false}
                    onChange={(e) => onChange('enableSort', e.target.checked)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">分页</label>
                <input
                    type="checkbox"
                    checked={config.enablePagination === true}
                    onChange={(e) => onChange('enablePagination', e.target.checked)}
                />
            </div>
            {config.enablePagination === true && (
                <div className="property-row">
                    <label className="property-label">每页条数</label>
                    <input
                        type="number"
                        className="property-input"
                        min={1}
                        max={200}
                        value={(config.pageSize as number) || 10}
                        onChange={(e) => onChange('pageSize', Number(e.target.value))}
                    />
                </div>
            )}
            <div className="property-row">
                <label className="property-label">冻结表头</label>
                <input
                    type="checkbox"
                    checked={config.freezeHeader !== false}
                    onChange={(e) => onChange('freezeHeader', e.target.checked)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">条件格式(JSON)</label>
                <textarea
                    className="property-input"
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
                            alert('条件格式 JSON 解析失败');
                        }
                    }}
                    placeholder='[{"columnIndex":0,"operator":">","value":100,"color":"#ef4444"}]'
                />
            </div>

            {hasCardSource && sourceCols.length === 0 && (
                <div style={{ fontSize: 11, color: '#888', marginTop: 8, padding: '4px 0' }}>
                    等待数据源加载列信息…
                </div>
            )}

            {hasCardSource && sourceCols.length > 0 && (
                <>
                    <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                        字段绑定 (来自数据源)
                    </div>
                    {sourceCols.map(col => {
                        const colConfig = columns?.find(c => c.source === col.name);
                        const isSelected = columns ? !!colConfig : true;
                        const displayName = col.displayName || col.name;
                        return (
                            <div key={col.name} style={{
                                border: '1px solid rgba(255,255,255,0.06)',
                                borderRadius: 4,
                                padding: '4px 6px',
                                marginBottom: 4,
                            }}>
                                <div className="property-row" style={{ marginBottom: isSelected ? 4 : 0 }}>
                                    <label className="property-label" style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                                        <input
                                            type="checkbox"
                                            checked={isSelected}
                                            onChange={(e) => handleToggleColumn(col.name, e.target.checked)}
                                        />
                                        <span title={col.name}>{displayName}</span>
                                    </label>
                                </div>
                                {isSelected && (
                                    <>
                                        <div className="property-row">
                                            <label className="property-label">表头标题</label>
                                            <input
                                                type="text"
                                                className="property-input"
                                                placeholder={displayName}
                                                value={colConfig?.alias || ''}
                                                onChange={(e) => handleAliasChange(col.name, e.target.value)}
                                            />
                                        </div>
                                        <div className="property-row">
                                            <label className="property-label">对齐</label>
                                            <select
                                                className="property-input"
                                                value={(colConfig?.align as string) || 'left'}
                                                onChange={(e) => handleColumnPatch(col.name, { align: e.target.value as ColumnEntry['align'] })}
                                            >
                                                <option value="left">左</option>
                                                <option value="center">中</option>
                                                <option value="right">右</option>
                                            </select>
                                        </div>
                                        <div className="property-row">
                                            <label className="property-label">列宽(%)</label>
                                            <input
                                                type="number"
                                                className="property-input"
                                                min={5}
                                                max={100}
                                                value={typeof colConfig?.width === 'number' ? colConfig.width : ''}
                                                placeholder="自动"
                                                onChange={(e) => {
                                                    const raw = e.target.value.trim();
                                                    if (!raw) {
                                                        handleColumnPatch(col.name, { width: undefined });
                                                        return;
                                                    }
                                                    const width = Number(raw);
                                                    handleColumnPatch(col.name, {
                                                        width: Number.isFinite(width) ? Math.max(5, Math.min(100, width)) : undefined,
                                                    });
                                                }}
                                            />
                                        </div>
                                        <div className="property-row">
                                            <label className="property-label">格式化</label>
                                            <select
                                                className="property-input"
                                                value={(colConfig?.formatter as string) || 'auto'}
                                                onChange={(e) => handleColumnPatch(col.name, { formatter: e.target.value as ColumnEntry['formatter'] })}
                                            >
                                                <option value="auto">自动</option>
                                                <option value="string">文本</option>
                                                <option value="number">数字</option>
                                                <option value="percent">百分比</option>
                                                <option value="date">日期时间</option>
                                            </select>
                                        </div>
                                    </>
                                )}
                            </div>
                        );
                    })}
                </>
            )}

            {!hasCardSource && staticHeaders.length > 0 && (
                <>
                    <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                        表头别名
                    </div>
                    {staticHeaders.map((h, i) => (
                        <div className="property-row" key={i}>
                            <label className="property-label" title={h}>列{i + 1}</label>
                            <input
                                type="text"
                                className="property-input"
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
