import { useEffect, useState } from 'react';
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
import { wouldCreateParentCycle } from '../componentHierarchy';
import { analyticsApi, type ExplainabilityResponse } from '../../../api/analyticsApi';
import { writeTextToClipboard } from '../../../hooks/clipboard';

type ExplainState =
    | { state: 'loading' }
    | { state: 'loaded'; value: ExplainabilityResponse }
    | { state: 'error'; error: unknown };

const DEFAULT_SERIES_COLORS = [
    '#3b82f6',
    '#22c55e',
    '#f59e0b',
    '#ef4444',
    '#a855f7',
    '#06b6d4',
];

function serializeVisibilityMatchValues(raw: unknown): string {
    if (Array.isArray(raw)) {
        return raw.map((item) => String(item ?? '').trim()).filter((item) => item.length > 0).join('\n');
    }
    const text = String(raw ?? '').trim();
    if (!text) return '';
    return text
        .split(/[\n,，]/g)
        .map((item) => item.trim())
        .filter((item) => item.length > 0)
        .join('\n');
}

function parseVisibilityMatchValues(text: string): string[] {
    return text
        .split(/[\n,，]/g)
        .map((item) => item.trim())
        .filter((item) => item.length > 0)
        .slice(0, 200);
}

function extractSqlTemplateParameterNames(sql: string): string[] {
    const text = String(sql ?? '');
    if (!text.trim()) {
        return [];
    }
    const names: string[] = [];
    const seen = new Set<string>();
    const patterns = [
        /\{\{\s*([a-zA-Z][a-zA-Z0-9_-]{0,63})\s*\}\}/g,
        /\$\{\s*([a-zA-Z][a-zA-Z0-9_-]{0,63})\s*\}/g,
    ];
    for (const pattern of patterns) {
        let match: RegExpExecArray | null = null;
        // eslint-disable-next-line no-cond-assign
        while ((match = pattern.exec(text)) !== null) {
            const name = String(match[1] ?? '').trim();
            if (!name || seen.has(name)) {
                continue;
            }
            seen.add(name);
            names.push(name);
        }
    }
    return names.slice(0, 200);
}

function resolveTabSwitcherOptionValues(raw: unknown): string[] {
    if (!Array.isArray(raw)) {
        return [];
    }
    const out: string[] = [];
    for (const item of raw) {
        if (typeof item === 'string') {
            const text = item.trim();
            if (text) out.push(text);
            continue;
        }
        if (!item || typeof item !== 'object') {
            continue;
        }
        const row = item as Record<string, unknown>;
        const value = String(row.value ?? '').trim();
        if (value) {
            out.push(value);
        }
    }
    return out;
}

export function PropertyPanel() {
    const { state, updateComponent, updateConfig, updateSelectedComponents } = useScreen();
    const { config, selectedIds } = state;
    useScreenPluginRuntime();
    const [explainState, setExplainState] = useState<ExplainState | null>(null);

    const selectedComponents = config.components.filter((c) => selectedIds.includes(c.id));
    const selectedComponent = selectedIds.length === 1
        ? config.components.find((c) => c.id === selectedIds[0])
        : null;

    useEffect(() => {
        setExplainState(null);
    }, [selectedComponent?.id]);

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

    const applyTabVisibilityRules = () => {
        if (selectedComponent.type !== 'tab-switcher') return;
        const variableKey = String(selectedComponent.config.variableKey || 'tabKey').trim() || 'tabKey';
        const optionValues = resolveTabSwitcherOptionValues(selectedComponent.config.options);
        if (optionValues.length === 0) {
            alert('请先在 Tab 组件中配置可用选项');
            return;
        }
        const targetTypes = new Set<ComponentType>([
            'line-chart',
            'bar-chart',
            'pie-chart',
            'map-chart',
            'table',
            'scroll-board',
            'scroll-ranking',
            'funnel-chart',
            'scatter-chart',
            'radar-chart',
            'gauge-chart',
        ]);
        let assigned = 0;
        let index = 0;
        const nextComponents = config.components.map((item) => {
            if (item.id === selectedComponent.id || !targetTypes.has(item.type)) {
                return item;
            }
            const match = optionValues[index % optionValues.length];
            index += 1;
            assigned += 1;
            return {
                ...item,
                config: {
                    ...item.config,
                    visibilityRuleEnabled: true,
                    visibilityVariableKey: variableKey,
                    visibilityMatchMode: 'equals',
                    visibilityMatchValues: [match],
                },
            };
        });
        if (assigned <= 0) {
            alert('当前画布没有可绑定 Tab 显隐规则的图表/表格组件');
            return;
        }
        updateConfig({ components: nextComponents });
        alert(`已应用 Tab 显隐规则到 ${assigned} 个组件`);
    };

    const clearTabVisibilityRules = () => {
        if (selectedComponent.type !== 'tab-switcher') return;
        const variableKey = String(selectedComponent.config.variableKey || 'tabKey').trim() || 'tabKey';
        let cleared = 0;
        const nextComponents = config.components.map((item) => {
            if (item.id === selectedComponent.id) {
                return item;
            }
            const currentVarKey = String((item.config as Record<string, unknown>).visibilityVariableKey ?? '').trim();
            if (currentVarKey !== variableKey) {
                return item;
            }
            const raw = item.config as Record<string, unknown>;
            const {
                visibilityRuleEnabled: _visibilityRuleEnabled,
                visibilityVariableKey: _visibilityVariableKey,
                visibilityMatchMode: _visibilityMatchMode,
                visibilityMatchValues: _visibilityMatchValues,
                visibilityMatchValue: _visibilityMatchValue,
                ...rest
            } = raw;
            cleared += 1;
            return { ...item, config: rest };
        });
        if (cleared <= 0) {
            alert('未找到可清理的 Tab 显隐规则');
            return;
        }
        updateConfig({ components: nextComponents });
        alert(`已清理 ${cleared} 个组件的 Tab 显隐规则`);
    };

    const pluginMeta = readComponentPluginMeta(selectedComponent.config);
    const runtimePlugin = pluginMeta ? getRendererPlugin(resolveRuntimePluginId(pluginMeta)) : undefined;
    const explainCardId = resolveExplainCardId(selectedComponent);
    const canExplain = Number.isFinite(explainCardId) && (explainCardId ?? 0) > 0;

    const handleExplain = async () => {
        if (!canExplain || !explainCardId) {
            return;
        }
        setExplainState({ state: 'loading' });
        try {
            const value = await analyticsApi.explainCard(explainCardId, { componentId: selectedComponent.id });
            setExplainState({ state: 'loaded', value });
        } catch (error) {
            setExplainState({ state: 'error', error });
        }
    };

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

                <div className="property-section">
                    <div className="property-section-title">解释</div>
                    {canExplain ? (
                        <div style={{ display: 'grid', gap: 8 }}>
                            <button
                                type="button"
                                className="property-btn-small"
                                onClick={() => { void handleExplain(); }}
                            >
                                解释当前组件
                            </button>
                            <div style={{ fontSize: 11, color: '#94a3b8', lineHeight: 1.45 }}>
                                解释来源 CardId: {explainCardId}
                            </div>
                            {explainState?.state === 'loading' ? (
                                <div style={{ fontSize: 12, color: '#94a3b8' }}>解释生成中...</div>
                            ) : null}
                            {explainState?.state === 'error' ? (
                                <div style={{ fontSize: 12, color: '#ef4444' }}>
                                    解释失败：{explainState.error instanceof Error ? explainState.error.message : 'unknown error'}
                                </div>
                            ) : null}
                            {explainState?.state === 'loaded' ? (
                                <>
                                    <button
                                        type="button"
                                        className="property-btn-small"
                                        onClick={() => {
                                            const text = explainState.value.copyJson ?? JSON.stringify(explainState.value.explainCard ?? {}, null, 2);
                                            void writeTextToClipboard(text);
                                        }}
                                    >
                                        复制解释JSON
                                    </button>
                                    <pre
                                        style={{
                                            margin: 0,
                                            padding: 8,
                                            borderRadius: 8,
                                            background: 'rgba(15,23,42,0.6)',
                                            whiteSpace: 'pre-wrap',
                                            wordBreak: 'break-word',
                                            fontSize: 11,
                                            maxHeight: 240,
                                            overflow: 'auto',
                                        }}
                                    >
                                        {JSON.stringify(explainState.value.explainCard ?? {}, null, 2)}
                                    </pre>
                                </>
                            ) : null}
                        </div>
                    ) : (
                        <div style={{ fontSize: 11, color: '#94a3b8', lineHeight: 1.45 }}>
                            当前组件未绑定可解释的 Card 数据源。
                        </div>
                    )}
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
                                if (wouldCreateParentCycle(config.components, selectedComponent.id, parentId)) {
                                    alert('该容器绑定会形成循环引用，请选择其他容器');
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
                                .filter((item) => (
                                    item.type === 'container'
                                    && item.id !== selectedComponent.id
                                    && !wouldCreateParentCycle(config.components, selectedComponent.id, item.id)
                                ))
                                .map((item) => (
                                    <option key={item.id} value={item.id}>
                                        {item.name} ({item.id})
                                    </option>
                                ))}
                        </select>
                    </div>

                    {selectedComponent.type === 'container' && (
                        <div className="property-row">
                            <label className="property-label">子组件数</label>
                            <div className="property-input" style={{ display: 'flex', alignItems: 'center' }}>
                                {config.components.filter((item) => item.parentContainerId === selectedComponent.id).length}
                            </div>
                        </div>
                    )}
                    {selectedComponent.type === 'tab-switcher' && (
                        <div className="property-row" style={{ alignItems: 'flex-start' }}>
                            <label className="property-label">Tab联动</label>
                            <div style={{ display: 'grid', gap: 6, width: '100%' }}>
                                <button type="button" className="property-btn-small" onClick={applyTabVisibilityRules}>
                                    一键应用显隐规则
                                </button>
                                <button type="button" className="property-btn-small" onClick={clearTabVisibilityRules}>
                                    清理显隐规则
                                </button>
                                <div style={{ fontSize: 11, color: '#94a3b8', lineHeight: 1.5 }}>
                                    规则会按 Tab 选项顺序分配到图表/表格组件。
                                </div>
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
                    <div className="property-row">
                        <label className="property-label">变量可见条件</label>
                        <input
                            type="checkbox"
                            checked={selectedComponent.config.visibilityRuleEnabled === true}
                            onChange={(e) => handleConfigChange('visibilityRuleEnabled', e.target.checked)}
                        />
                    </div>
                    {selectedComponent.config.visibilityRuleEnabled === true && (
                        <>
                            <div className="property-row">
                                <label className="property-label">变量Key</label>
                                <input
                                    type="text"
                                    className="property-input"
                                    value={String(selectedComponent.config.visibilityVariableKey ?? '')}
                                    onChange={(e) => handleConfigChange('visibilityVariableKey', e.target.value)}
                                    placeholder="tabKey"
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">匹配模式</label>
                                <select
                                    className="property-input"
                                    value={String(selectedComponent.config.visibilityMatchMode ?? 'equals')}
                                    onChange={(e) => handleConfigChange('visibilityMatchMode', e.target.value)}
                                >
                                    <option value="equals">等于任一值</option>
                                    <option value="not-equals">不等于任一值</option>
                                    <option value="contains">包含任一值</option>
                                    <option value="not-contains">不包含任一值</option>
                                    <option value="starts-with">前缀匹配任一值</option>
                                    <option value="ends-with">后缀匹配任一值</option>
                                    <option value="empty">为空</option>
                                    <option value="not-empty">非空</option>
                                </select>
                            </div>
                            {(() => {
                                const mode = String(selectedComponent.config.visibilityMatchMode ?? 'equals');
                                if (mode === 'empty' || mode === 'not-empty') {
                                    return null;
                                }
                                return (
                                    <div className="property-row">
                                        <label className="property-label">匹配值</label>
                                        <textarea
                                            className="property-input"
                                            rows={4}
                                            value={serializeVisibilityMatchValues(selectedComponent.config.visibilityMatchValues)}
                                            onChange={(e) => handleConfigChange('visibilityMatchValues', parseVisibilityMatchValues(e.target.value))}
                                            placeholder={'每行一个值，例如：\noverview\nline'}
                                        />
                                    </div>
                                );
                            })()}
                            <div style={{ fontSize: 11, color: '#94a3b8', marginTop: -2 }}>
                                仅在预览/公开/导出模式生效，设计器中始终可见便于编辑。
                            </div>
                        </>
                    )}
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
                const description = String(field?.description || '').trim();
                const descriptionNode = description ? (
                    <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 4, lineHeight: 1.45 }}>
                        {description}
                    </div>
                ) : null;
                if (field.type === 'boolean') {
                    return (
                        <div className="property-row" key={key}>
                            <label className="property-label">{label}</label>
                            <div style={{ flex: 1 }}>
                                <input
                                    type="checkbox"
                                    checked={Boolean(value)}
                                    onChange={(e) => onChange(key, e.target.checked)}
                                />
                                {descriptionNode}
                            </div>
                        </div>
                    );
                }
                if (field.type === 'number') {
                    const min = Number.isFinite(field.min) ? Number(field.min) : undefined;
                    const max = Number.isFinite(field.max) ? Number(field.max) : undefined;
                    const step = Number.isFinite(field.step) ? Number(field.step) : undefined;
                    return (
                        <div className="property-row" key={key}>
                            <label className="property-label">{label}</label>
                            <div style={{ flex: 1 }}>
                                <input
                                    type="number"
                                    className="property-input"
                                    min={min}
                                    max={max}
                                    step={step}
                                    value={Number(value ?? 0)}
                                    onChange={(e) => onChange(key, Number(e.target.value))}
                                />
                                {descriptionNode}
                            </div>
                        </div>
                    );
                }
                if (field.type === 'select') {
                    const options = Array.isArray(field.options)
                        ? field.options
                            .map((item) => {
                                if (!item || typeof item !== 'object') return null;
                                const labelText = String(item.label ?? '').trim();
                                const rawValue = (item as { value?: unknown }).value;
                                if (!labelText) return null;
                                if (
                                    typeof rawValue !== 'string'
                                    && typeof rawValue !== 'number'
                                    && typeof rawValue !== 'boolean'
                                ) {
                                    return null;
                                }
                                return {
                                    label: labelText,
                                    value: rawValue,
                                };
                            })
                            .filter((item): item is { label: string; value: string | number | boolean } => !!item)
                        : [];
                    const selectedIndex = options.findIndex((item) => String(item.value) === String(value));
                    return (
                        <div className="property-row" key={key}>
                            <label className="property-label">{label}</label>
                            <div style={{ flex: 1 }}>
                                <select
                                    className="property-input"
                                    value={selectedIndex >= 0 ? String(selectedIndex) : ''}
                                    onChange={(e) => {
                                        const nextIdx = Number(e.target.value);
                                        if (!Number.isFinite(nextIdx) || nextIdx < 0 || nextIdx >= options.length) {
                                            return;
                                        }
                                        onChange(key, options[nextIdx].value);
                                    }}
                                >
                                    {selectedIndex < 0 && (
                                        <option value="">-- 请选择 --</option>
                                    )}
                                    {options.map((item, idx) => (
                                        <option key={`${item.label}-${idx}`} value={String(idx)}>
                                            {item.label}
                                        </option>
                                    ))}
                                </select>
                                {descriptionNode}
                            </div>
                        </div>
                    );
                }
                if (field.type === 'color') {
                    const fallback = typeof value === 'string' && value ? value : '#3b82f6';
                    return (
                        <div className="property-row" key={key}>
                            <label className="property-label">{label}</label>
                            <div style={{ flex: 1 }}>
                                <input
                                    type="color"
                                    className="property-color-input"
                                    value={fallback}
                                    onChange={(e) => onChange(key, e.target.value)}
                                />
                                {descriptionNode}
                            </div>
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
                                {descriptionNode}
                            </div>
                        </div>
                    );
                }
                return (
                    <div className="property-row" key={key}>
                        <label className="property-label">{label}</label>
                        <div style={{ flex: 1 }}>
                            <input
                                type="text"
                                className="property-input"
                                value={String(value ?? '')}
                                placeholder={String(field?.placeholder || '')}
                                onChange={(e) => onChange(key, e.target.value)}
                            />
                            {descriptionNode}
                        </div>
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

    const renderLegendLayoutRows = () => (
        <>
            <div className="property-row">
                <label className="property-label">图例方向</label>
                <select
                    className="property-input"
                    value={(config.legendOrient as string) || 'auto'}
                    onChange={(e) => onChange('legendOrient', e.target.value)}
                >
                    <option value="auto">自动</option>
                    <option value="horizontal">横向</option>
                    <option value="vertical">纵向</option>
                </select>
            </div>
            <div className="property-row">
                <label className="property-label">图例间距</label>
                <input
                    type="number"
                    className="property-input"
                    min={0}
                    max={60}
                    value={(config.legendItemGap as number) || 12}
                    onChange={(e) => onChange('legendItemGap', Number(e.target.value))}
                />
            </div>
        </>
    );

    const renderChartPaddingRows = () => (
        <>
            <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                图形留白(像素)
            </div>
            <div className="property-row">
                <label className="property-label">上留白</label>
                <input
                    type="number"
                    className="property-input"
                    min={0}
                    max={300}
                    value={(config.chartPaddingTop as number) || 0}
                    onChange={(e) => onChange('chartPaddingTop', Number(e.target.value))}
                    placeholder="0=自动"
                />
            </div>
            <div className="property-row">
                <label className="property-label">右留白</label>
                <input
                    type="number"
                    className="property-input"
                    min={0}
                    max={300}
                    value={(config.chartPaddingRight as number) || 0}
                    onChange={(e) => onChange('chartPaddingRight', Number(e.target.value))}
                    placeholder="0=自动"
                />
            </div>
            <div className="property-row">
                <label className="property-label">下留白</label>
                <input
                    type="number"
                    className="property-input"
                    min={0}
                    max={300}
                    value={(config.chartPaddingBottom as number) || 0}
                    onChange={(e) => onChange('chartPaddingBottom', Number(e.target.value))}
                    placeholder="0=自动"
                />
            </div>
            <div className="property-row">
                <label className="property-label">左留白</label>
                <input
                    type="number"
                    className="property-input"
                    min={0}
                    max={300}
                    value={(config.chartPaddingLeft as number) || 0}
                    onChange={(e) => onChange('chartPaddingLeft', Number(e.target.value))}
                    placeholder="0=自动"
                />
            </div>
        </>
    );

    const renderChartOffsetRows = () => (
        <>
            <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                图形位置微调
            </div>
            <div className="property-row">
                <label className="property-label">水平偏移</label>
                <input
                    type="number"
                    className="property-input"
                    min={-400}
                    max={400}
                    value={(config.chartOffsetX as number) || 0}
                    onChange={(e) => onChange('chartOffsetX', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">垂直偏移</label>
                <input
                    type="number"
                    className="property-input"
                    min={-400}
                    max={400}
                    value={(config.chartOffsetY as number) || 0}
                    onChange={(e) => onChange('chartOffsetY', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">图形缩放(%)</label>
                <input
                    type="number"
                    className="property-input"
                    min={40}
                    max={180}
                    value={(config.chartScalePercent as number) || 100}
                    onChange={(e) => onChange('chartScalePercent', Number(e.target.value))}
                />
            </div>
        </>
    );

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
                    {renderLegendLayoutRows()}
                    {renderChartPaddingRows()}
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
                            {renderLegendLayoutRows()}
                            {renderChartPaddingRows()}
                            {renderChartOffsetRows()}
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
                            onChange={(e) => {
                                const raw = String(e.target.value || '').trim();
                                if (!raw) {
                                    onChange('targetTime', '');
                                    return;
                                }
                                const parsed = Date.parse(raw);
                                if (Number.isFinite(parsed)) {
                                    onChange('targetTime', new Date(parsed).toISOString());
                                }
                            }}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">目标时间变量</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.targetVariableKey as string) || ''}
                            onChange={(e) => onChange('targetVariableKey', e.target.value)}
                            placeholder="releaseDeadline"
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

        case 'carousel':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.title as string) || '轮播卡片'}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">内容来源</label>
                        <select
                            className="property-input"
                            value={(config.itemSourceMode as string) || 'auto'}
                            onChange={(e) => onChange('itemSourceMode', e.target.value)}
                        >
                            <option value="auto">自动（优先数据）</option>
                            <option value="manual">手工内容</option>
                            <option value="data">数据内容</option>
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">轮播内容</label>
                        <textarea
                            className="property-input"
                            rows={6}
                            value={Array.isArray(config.items) ? config.items.map((item) => String(item ?? '')).join('\n') : String(config.items ?? '')}
                            onChange={(e) => {
                                const items = e.target.value
                                    .split(/\r?\n/g)
                                    .map((item) => item.trim())
                                    .filter((item) => item.length > 0)
                                    .slice(0, 200);
                                onChange('items', items);
                            }}
                            placeholder="每行一条，例如：\n设备在线率 99.2%\n昨日告警 6 条"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">轮播间隔(秒)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={1}
                            max={120}
                            value={(config.intervalSeconds as number) || 4}
                            onChange={(e) => onChange('intervalSeconds', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">自动轮播</label>
                        <input
                            type="checkbox"
                            checked={config.autoPlay !== false}
                            onChange={(e) => onChange('autoPlay', e.target.checked)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">数据内容列</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.dataItemField as string) || ''}
                            onChange={(e) => onChange('dataItemField', e.target.value)}
                            placeholder="列名/显示名/序号(1开始)"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">数据行上限</label>
                        <input
                            type="number"
                            className="property-input"
                            min={1}
                            max={500}
                            value={(config.dataItemMax as number) || 50}
                            onChange={(e) => onChange('dataItemMax', Number(e.target.value))}
                            placeholder="数据源接入时生效"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">显示切换按钮</label>
                        <input
                            type="checkbox"
                            checked={config.showControls !== false}
                            onChange={(e) => onChange('showControls', e.target.checked)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">悬停暂停</label>
                        <input
                            type="checkbox"
                            checked={config.pauseOnHover !== false}
                            onChange={(e) => onChange('pauseOnHover', e.target.checked)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">显示指示点</label>
                        <input
                            type="checkbox"
                            checked={config.showDots !== false}
                            onChange={(e) => onChange('showDots', e.target.checked)}
                        />
                    </div>
                </>
            );

        case 'tab-switcher':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.label as string) || '维度切换'}
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
                            placeholder="tabKey"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">选项来源</label>
                        <select
                            className="property-input"
                            value={(config.optionSourceMode as string) || 'manual'}
                            onChange={(e) => onChange('optionSourceMode', e.target.value === 'data' ? 'data' : 'manual')}
                        >
                            <option value="manual">手工配置</option>
                            <option value="data">数据源首列</option>
                        </select>
                    </div>
                    {(String(config.optionSourceMode || 'manual') !== 'data') ? (
                        <div className="property-row">
                            <label className="property-label">选项</label>
                            <textarea
                                className="property-input"
                                rows={6}
                                value={Array.isArray(config.options)
                                    ? config.options.map((item) => {
                                        if (item && typeof item === 'object') {
                                            const row = item as Record<string, unknown>;
                                            const label = String(row.label ?? '').trim();
                                            const value = String(row.value ?? '').trim();
                                            return label && value ? `${label}:${value}` : (label || value);
                                        }
                                        return String(item ?? '');
                                    }).join('\n')
                                    : String(config.options ?? '')
                                }
                                onChange={(e) => {
                                    const lines = e.target.value
                                        .split(/\r?\n/g)
                                        .map((line) => line.trim())
                                        .filter((line) => line.length > 0)
                                        .slice(0, 300);
                                    const next = lines.map((line) => {
                                        const idx = line.indexOf(':');
                                        if (idx < 0) {
                                            return { label: line, value: line };
                                        }
                                        const label = line.slice(0, idx).trim();
                                        const value = line.slice(idx + 1).trim();
                                        const safeValue = value || label;
                                        return { label: label || safeValue, value: safeValue };
                                    });
                                    onChange('options', next);
                                }}
                                placeholder={'每行一个选项，可写 label:value\n例如：\n总览:overview\n产线:line'}
                            />
                        </div>
                    ) : (
                        <>
                            <div className="property-row">
                                <label className="property-label">标签列</label>
                                <input
                                    type="text"
                                    className="property-input"
                                    value={(config.dataOptionLabelField as string) || ''}
                                    onChange={(e) => onChange('dataOptionLabelField', e.target.value)}
                                    placeholder="列名/显示名/序号(1开始)"
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">值列</label>
                                <input
                                    type="text"
                                    className="property-input"
                                    value={(config.dataOptionValueField as string) || ''}
                                    onChange={(e) => onChange('dataOptionValueField', e.target.value)}
                                    placeholder="列名/显示名/序号(1开始)"
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">最大选项数</label>
                                <input
                                    type="number"
                                    className="property-input"
                                    min={1}
                                    max={500}
                                    value={(config.dataOptionMax as number) || 100}
                                    onChange={(e) => onChange('dataOptionMax', Number(e.target.value))}
                                />
                            </div>
                        </>
                    )}
                    <div className="property-row">
                        <label className="property-label">默认值</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.defaultValue as string) || ''}
                            onChange={(e) => onChange('defaultValue', e.target.value)}
                            placeholder="首次加载时写入变量"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">紧凑模式</label>
                        <input
                            type="checkbox"
                            checked={config.compact === true}
                            onChange={(e) => onChange('compact', e.target.checked)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">激活背景</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.activeBackgroundColor as string) || '#38bdf8'}
                            onChange={(e) => onChange('activeBackgroundColor', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">激活文字</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.activeTextColor as string) || '#0f172a'}
                            onChange={(e) => onChange('activeTextColor', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">未激活背景</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.inactiveBackgroundColor as string) || '#1e293b'}
                            onChange={(e) => onChange('inactiveBackgroundColor', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">未激活文字</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.inactiveTextColor as string) || '#94a3b8'}
                            onChange={(e) => onChange('inactiveTextColor', e.target.value)}
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
                    <div className="property-row">
                        <label className="property-label">防抖(ms)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={5000}
                            step={50}
                            value={Number(config.debounceMs as number) > 0 ? Number(config.debounceMs as number) : 0}
                            onChange={(e) => {
                                const n = Number(e.target.value);
                                onChange('debounceMs', Number.isFinite(n) && n > 0 ? Math.max(50, Math.min(5000, Math.round(n))) : 0);
                            }}
                            placeholder="0=不防抖"
                        />
                    </div>
                </>
            );

        case 'filter-select': {
            const options = Array.isArray(config.options)
                ? (config.options as Array<string | { label?: string; value?: string }>)
                : [];
            const optionSourceMode = String(config.optionSourceMode || 'manual') === 'data' ? 'data' : 'manual';
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
                        <label className="property-label">选项来源</label>
                        <select
                            className="property-input"
                            value={optionSourceMode}
                            onChange={(e) => onChange('optionSourceMode', e.target.value === 'data' ? 'data' : 'manual')}
                        >
                            <option value="manual">手工配置</option>
                            <option value="data">来自数据源</option>
                        </select>
                    </div>
                    {optionSourceMode === 'manual' ? (
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
                    ) : (
                        <>
                            <div className="property-row">
                                <label className="property-label">值字段</label>
                                <input
                                    type="text"
                                    className="property-input"
                                    value={(config.dataOptionValueField as string) || ''}
                                    onChange={(e) => onChange('dataOptionValueField', e.target.value)}
                                    placeholder="默认第1列"
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">标签字段</label>
                                <input
                                    type="text"
                                    className="property-input"
                                    value={(config.dataOptionLabelField as string) || ''}
                                    onChange={(e) => onChange('dataOptionLabelField', e.target.value)}
                                    placeholder="默认与值字段相同"
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">最大选项数</label>
                                <input
                                    type="number"
                                    className="property-input"
                                    min={1}
                                    max={2000}
                                    value={Number(config.dataOptionMax as number) > 0 ? Number(config.dataOptionMax as number) : 200}
                                    onChange={(e) => {
                                        const n = Number(e.target.value);
                                        onChange('dataOptionMax', Number.isFinite(n) ? Math.max(1, Math.min(2000, n)) : 200);
                                    }}
                                />
                            </div>
                        </>
                    )}
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
                    {renderLegendLayoutRows()}
                    {renderChartPaddingRows()}
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
    const metricBindings: CardParameterBinding[] = dsType === 'metric' ? (ds?.metricConfig?.parameterBindings ?? []) : [];
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

    const updateMetricBindings = (bindings: CardParameterBinding[]) => {
        const currentMetricConfig = dsType === 'metric' ? ds?.metricConfig : undefined;
        setDataSource({
            type: 'metric',
            sourceType: 'metric',
            refreshInterval: dsType === 'metric' ? ds?.refreshInterval : undefined,
            metricConfig: {
                ...(currentMetricConfig ?? {}),
                cardId: currentMetricConfig?.cardId ?? 0,
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
            return;
        }
        if (nextType === 'metric') {
            setDataSource({
                type: 'metric',
                sourceType: 'metric',
                refreshInterval: dsType === 'metric' ? ds?.refreshInterval : undefined,
                metricConfig: {
                    cardId: dsType === 'metric' ? (ds?.metricConfig?.cardId ?? 0) : 0,
                    metricId: dsType === 'metric' ? ds?.metricConfig?.metricId : undefined,
                    metricVersion: dsType === 'metric' ? ds?.metricConfig?.metricVersion : undefined,
                    parameterBindings: dsType === 'metric' ? (ds?.metricConfig?.parameterBindings ?? []) : [],
                },
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
                    <option value="metric">Metric 语义模式</option>
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
                        <label className="property-label">参数提取</label>
                        <button
                            type="button"
                            className="header-btn"
                            onClick={() => {
                                const names = extractSqlTemplateParameterNames(sqlConfig?.query ?? '');
                                if (names.length === 0) {
                                    alert('未识别到 SQL 参数，占位符示例：{{day}} 或 ${day}');
                                    return;
                                }
                                const previous = new Map(
                                    (sqlBindings ?? []).map((item) => [String(item.name ?? '').trim(), item]),
                                );
                                const nextBindings: CardParameterBinding[] = names.map((name) => {
                                    const exists = previous.get(name);
                                    if (!exists) {
                                        return {
                                            name,
                                            variableKey: '',
                                            value: '',
                                        };
                                    }
                                    return {
                                        name,
                                        variableKey: exists.variableKey ?? '',
                                        value: exists.value ?? '',
                                    };
                                });
                                updateSqlBindings(nextBindings);
                            }}
                        >
                            从 SQL 提取参数
                        </button>
                    </div>
                    <div style={{ fontSize: 11, color: '#94a3b8', marginTop: -2 }}>
                        自动识别 &#123;&#123;param&#125;&#125; / $&#123;param&#125; 占位符并生成参数绑定。
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

            {dsType === 'metric' && (
                <>
                    <div className="property-row">
                        <label className="property-label">Card</label>
                        <CardIdPicker
                            value={dsType === 'metric' ? (ds?.metricConfig?.cardId ?? 0) : 0}
                            onChange={(cardId) => {
                                const currentMetricConfig = dsType === 'metric' ? ds?.metricConfig : undefined;
                                setDataSource({
                                    type: 'metric',
                                    sourceType: 'metric',
                                    refreshInterval: dsType === 'metric' ? ds?.refreshInterval : undefined,
                                    metricConfig: {
                                        ...(currentMetricConfig ?? {}),
                                        cardId,
                                    },
                                });
                            }}
                        />
                    </div>
                    <MetricBindingEditor
                        metricId={dsType === 'metric' ? ds?.metricConfig?.metricId : undefined}
                        metricVersion={dsType === 'metric' ? ds?.metricConfig?.metricVersion : undefined}
                        onMetricIdChange={(metricId) => {
                            const currentMetricConfig = dsType === 'metric' ? ds?.metricConfig : undefined;
                            setDataSource({
                                type: 'metric',
                                sourceType: 'metric',
                                refreshInterval: dsType === 'metric' ? ds?.refreshInterval : undefined,
                                metricConfig: {
                                    ...(currentMetricConfig ?? {}),
                                    cardId: currentMetricConfig?.cardId ?? 0,
                                    metricId,
                                    metricVersion: metricId ? currentMetricConfig?.metricVersion : undefined,
                                },
                            });
                        }}
                        onMetricVersionChange={(metricVersion) => {
                            const currentMetricConfig = dsType === 'metric' ? ds?.metricConfig : undefined;
                            setDataSource({
                                type: 'metric',
                                sourceType: 'metric',
                                refreshInterval: dsType === 'metric' ? ds?.refreshInterval : undefined,
                                metricConfig: {
                                    ...(currentMetricConfig ?? {}),
                                    cardId: currentMetricConfig?.cardId ?? 0,
                                    metricId: currentMetricConfig?.metricId,
                                    metricVersion,
                                },
                            });
                        }}
                    />
                    <CardParamBindingsEditor
                        bindings={metricBindings}
                        globalVariables={globalVariables}
                        onChange={updateMetricBindings}
                    />
                    <div className="property-row">
                        <label className="property-label">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            step={10}
                            value={dsType === 'metric' ? (ds?.refreshInterval ?? 0) : 0}
                            onChange={(e) => {
                                const val = Number(e.target.value);
                                const currentMetricConfig = dsType === 'metric' ? ds?.metricConfig : undefined;
                                setDataSource({
                                    type: 'metric',
                                    sourceType: 'metric',
                                    refreshInterval: val > 0 ? val : undefined,
                                    metricConfig: {
                                        ...(currentMetricConfig ?? {}),
                                        cardId: currentMetricConfig?.cardId ?? 0,
                                    },
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

function resolveExplainCardId(component: ScreenComponent): number | undefined {
    const ds = component.dataSource as DataSourceConfig | undefined;
    const dsType = resolveDataSourceType(ds);
    if (dsType === 'card') {
        const id = Number(ds?.cardConfig?.cardId ?? 0);
        return Number.isFinite(id) && id > 0 ? id : undefined;
    }
    if (dsType === 'metric') {
        const id = Number(ds?.metricConfig?.cardId ?? 0);
        return Number.isFinite(id) && id > 0 ? id : undefined;
    }
    return undefined;
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

    const interaction = component.interaction ?? {
        enabled: false,
        mappings: [] as ComponentInteractionMapping[],
        jumpEnabled: false,
        jumpUrlTemplate: '',
        jumpOpenMode: 'new-tab' as const,
    };
    const mappings = interaction.mappings ?? [];
    const sourcePathCandidates = ['name', 'seriesName', 'value', 'data.name', 'data.value', 'data.code'];

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
                                <input
                                    list={`interaction-source-path-${index}`}
                                    className="property-input"
                                    value={mapping.sourcePath || 'name'}
                                    onChange={(e) => updateMapping(index, { sourcePath: e.target.value })}
                                    placeholder="name / data.name / value"
                                />
                                <datalist id={`interaction-source-path-${index}`}>
                                    {sourcePathCandidates.map((item) => (
                                        <option key={item} value={item} />
                                    ))}
                                </datalist>
                            </div>

                            <div className="property-row">
                                <label className="property-label">值转换</label>
                                <select
                                    className="property-input"
                                    value={String(mapping.transform || 'raw')}
                                    onChange={(e) => updateMapping(index, { transform: e.target.value as ComponentInteractionMapping['transform'] })}
                                >
                                    <option value="raw">原值</option>
                                    <option value="string">字符串</option>
                                    <option value="number">数值</option>
                                    <option value="lowercase">转小写</option>
                                    <option value="uppercase">转大写</option>
                                </select>
                            </div>

                            <div className="property-row">
                                <label className="property-label">默认值</label>
                                <input
                                    className="property-input"
                                    value={mapping.fallbackValue || ''}
                                    onChange={(e) => updateMapping(index, { fallbackValue: e.target.value })}
                                    placeholder="取值为空时写入该值"
                                />
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
                            mappings: [...mappings, {
                                variableKey: globalVariables[0]?.key ?? '',
                                sourcePath: 'name',
                                transform: 'raw',
                                fallbackValue: '',
                            }],
                        })}
                        style={{ width: '100%', cursor: 'pointer', textAlign: 'center', color: '#6366f1' }}
                    >
                        + 添加联动规则
                    </button>
                    <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 6, lineHeight: 1.5 }}>
                        支持自定义路径，例如 <code>data.code</code>；可对值做数值/大小写转换，并设置空值回退。
                    </div>

                    <div className="property-row" style={{ marginTop: 10 }}>
                        <label className="property-label">启用点击跳转</label>
                        <input
                            type="checkbox"
                            checked={interaction.jumpEnabled === true}
                            onChange={(e) => setInteraction({ ...interaction, jumpEnabled: e.target.checked })}
                        />
                    </div>

                    {interaction.jumpEnabled === true && (
                        <>
                            <div className="property-row">
                                <label className="property-label">跳转链接模板</label>
                                <input
                                    className="property-input"
                                    value={interaction.jumpUrlTemplate || ''}
                                    onChange={(e) => setInteraction({ ...interaction, jumpUrlTemplate: e.target.value })}
                                    placeholder="https://host/path?name={{name}}&value={{value}}"
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">打开方式</label>
                                <select
                                    className="property-input"
                                    value={interaction.jumpOpenMode || 'new-tab'}
                                    onChange={(e) => setInteraction({
                                        ...interaction,
                                        jumpOpenMode: e.target.value === 'self' ? 'self' : 'new-tab',
                                    })}
                                >
                                    <option value="new-tab">新窗口</option>
                                    <option value="self">当前窗口</option>
                                </select>
                            </div>
                            <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 4 }}>
                                支持占位符: {'{{name}} / {{seriesName}} / {{value}} / {{data.name}}'}
                            </div>
                        </>
                    )}
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
    wrap?: boolean;
    formatter?: 'auto' | 'string' | 'number' | 'percent' | 'date';
}

interface SourceColumnOption {
    name: string;
    displayName: string;
}

function CardSourceColumnBindingsEditor({
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
    const fallbackColumns = sourceCols.map((item) => ({ source: item.name } as ColumnEntry));
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
            alert('该字段已被绑定，请选择其他字段');
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
            <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                {title}
            </div>
            <div style={{ display: 'flex', gap: 6, marginBottom: 8 }}>
                <button
                    type="button"
                    className="header-btn"
                    onClick={() => {
                        if (!unboundSources[0]) return;
                        onColumnsChange([...effectiveColumns, { source: unboundSources[0].name, align: defaultAlign }]);
                    }}
                    disabled={unboundSources.length === 0}
                    title="追加一个未绑定字段"
                >
                    + 添加列
                </button>
                <button
                    type="button"
                    className="header-btn"
                    onClick={() => onColumnsChange(undefined)}
                    title="恢复默认映射（按数据源原始字段）"
                >
                    恢复默认
                </button>
                <button
                    type="button"
                    className="header-btn"
                    onClick={() => onColumnsChange([])}
                    disabled={effectiveColumns.length === 0}
                    title="清空当前映射"
                >
                    清空
                </button>
            </div>
            {effectiveColumns.length === 0 && (
                <div style={{ fontSize: 11, color: '#888', marginTop: 4, marginBottom: 8 }}>
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
                            <span style={{ fontSize: 11, color: '#94a3b8' }}>
                                列 {index + 1}{sourceMeta ? '' : ' (失效字段)'}
                            </span>
                            <div style={{ display: 'flex', gap: 4 }}>
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => handleMove(index, -1)}
                                    disabled={index === 0}
                                    title="上移"
                                >
                                    ↑
                                </button>
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => handleMove(index, 1)}
                                    disabled={index >= effectiveColumns.length - 1}
                                    title="下移"
                                >
                                    ↓
                                </button>
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => handleRemove(index)}
                                    title="删除该列"
                                >
                                    删除
                                </button>
                            </div>
                        </div>
                        <div className="property-row">
                            <label className="property-label">绑定字段</label>
                            <select
                                className="property-input"
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
                        <div className="property-row">
                            <label className="property-label">表头标题</label>
                            <input
                                type="text"
                                className="property-input"
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
                        <div className="property-row">
                            <label className="property-label">对齐</label>
                            <select
                                className="property-input"
                                value={(entry.align as string) || defaultAlign}
                                onChange={(e) => updateColumn(index, { align: e.target.value as ColumnEntry['align'] })}
                            >
                                <option value="left">左</option>
                                <option value="center">中</option>
                                <option value="right">右</option>
                            </select>
                        </div>
                        <div className="property-row">
                            <label className="property-label">自动换行</label>
                            <input
                                type="checkbox"
                                checked={entry.wrap === true}
                                onChange={(e) => updateColumn(index, { wrap: e.target.checked })}
                            />
                        </div>
                        <div className="property-row">
                            <label className="property-label">列宽(%)</label>
                            <input
                                type="number"
                                className="property-input"
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
                        <div className="property-row">
                            <label className="property-label">格式化</label>
                            <select
                                className="property-input"
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

/** scroll-board 专用属性面板，支持动态列选择 */
function ScrollBoardConfig({ component, onChange }: {
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
            {hasDynamicSource && sourceCols.length === 0 && (
                <div style={{ fontSize: 11, color: '#888', marginTop: 8, padding: '4px 0' }}>
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
    const hasDynamicSource = resolveDataSourceType(dataSource as DataSourceConfig | undefined) !== 'static';

    const staticHeaders = config.header as string[] || [];
    const columnAlias = config.columnAlias as Record<string, string> || {};

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
                <label className="property-label">冻结首列</label>
                <input
                    type="checkbox"
                    checked={config.freezeFirstColumn === true}
                    onChange={(e) => onChange('freezeFirstColumn', e.target.checked)}
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
                    placeholder='[{"columnKey":"amount","operator":">","value":100,"color":"#ef4444"}]'
                />
            </div>
            <div style={{ fontSize: 11, opacity: 0.75, marginTop: -2, marginBottom: 8, lineHeight: 1.5 }}>
                支持按 `columnIndex`、`columnKey` 或 `columnTitle` 匹配列；建议优先使用 `columnKey` 以避免字段重排错位。
            </div>

            {hasDynamicSource && sourceCols.length === 0 && (
                <div style={{ fontSize: 11, color: '#888', marginTop: 8, padding: '4px 0' }}>
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
