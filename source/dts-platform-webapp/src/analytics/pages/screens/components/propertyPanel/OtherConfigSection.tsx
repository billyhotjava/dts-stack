import { message } from 'antd';
import { toast } from 'sonner';
import { wouldCreateParentCycle } from '../../componentHierarchy';
import type { ComponentType, ScreenComponent, ScreenConfig } from '../../types';
import {
    parseVisibilityMatchValues,
    resolveTabSwitcherOptionValues,
    serializeVisibilityMatchValues,
} from './helpers';
import { SectionToggle } from './SectionToggle';

interface OtherConfigSectionOptions {
    selectedComponent: ScreenComponent;
    components: ScreenComponent[];
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void;
    updateConfig: (updates: Partial<ScreenConfig>) => void;
    handleConfigChange: (key: string, value: unknown) => void;
    isSectionCollapsed: (sectionKey: string) => boolean;
    toggleSection: (sectionKey: string) => void;
}

const TAB_VISIBILITY_TARGET_TYPES = new Set<ComponentType>([
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

function applyTabVisibilityRules(
    selectedComponent: ScreenComponent,
    components: ScreenComponent[],
    updateConfig: (updates: Partial<ScreenConfig>) => void,
) {
    if (selectedComponent.type !== 'tab-switcher') return;
    const variableKey = String(selectedComponent.config.variableKey || 'tabKey').trim() || 'tabKey';
    const optionValues = resolveTabSwitcherOptionValues(selectedComponent.config.options);
    if (optionValues.length === 0) {
        message.warning('请先在 Tab 组件中配置可用选项');
        return;
    }

    let assigned = 0;
    let index = 0;
    const nextComponents = components.map((item) => {
        if (item.id === selectedComponent.id || !TAB_VISIBILITY_TARGET_TYPES.has(item.type)) {
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
        message.warning('当前画布没有可绑定 Tab 显隐规则的图表/表格组件');
        return;
    }
    updateConfig({ components: nextComponents });
    message.success(`已应用 Tab 显隐规则到 ${assigned} 个组件`);
}

function clearTabVisibilityRules(
    selectedComponent: ScreenComponent,
    components: ScreenComponent[],
    updateConfig: (updates: Partial<ScreenConfig>) => void,
) {
    if (selectedComponent.type !== 'tab-switcher') return;
    const variableKey = String(selectedComponent.config.variableKey || 'tabKey').trim() || 'tabKey';
    let cleared = 0;
    const nextComponents = components.map((item) => {
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
        message.info('未找到可清理的 Tab 显隐规则');
        return;
    }
    updateConfig({ components: nextComponents });
    message.success(`已清理 ${cleared} 个组件的 Tab 显隐规则`);
}

export function renderOtherConfig({
    selectedComponent,
    components,
    updateComponent,
    updateConfig,
    handleConfigChange,
    isSectionCollapsed,
    toggleSection,
}: OtherConfigSectionOptions) {
    const isCollapsed = isSectionCollapsed('other');

    return (
        <div className="property-section py-3 border-b border-border-default">
            <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                <SectionToggle collapsed={isCollapsed} label="其他" onToggle={() => toggleSection('other')} />
            </div>
            {!isCollapsed ? (
                <>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">名称</label>
                        <input
                            type="text"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            value={selectedComponent.name}
                            onChange={(e) => updateComponent(selectedComponent.id, { name: e.target.value })}
                        />
                    </div>

                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">所属容器</label>
                        <select
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            value={selectedComponent.parentContainerId || ''}
                            onChange={(e) => {
                                const parentId = e.target.value || undefined;
                                if (!parentId) {
                                    updateComponent(selectedComponent.id, { parentContainerId: undefined });
                                    return;
                                }
                                const parent = components.find((item) => item.id === parentId && item.type === 'container');
                                if (!parent) {
                                    updateComponent(selectedComponent.id, { parentContainerId: undefined });
                                    return;
                                }
                                if (wouldCreateParentCycle(components, selectedComponent.id, parentId)) {
                                    toast.error('该容器绑定会形成循环引用，请选择其他容器');
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
                            {components
                                .filter((item) => (
                                    item.type === 'container'
                                    && item.id !== selectedComponent.id
                                    && !wouldCreateParentCycle(components, selectedComponent.id, item.id)
                                ))
                                .map((item) => (
                                    <option key={item.id} value={item.id}>
                                        {item.name} ({item.id})
                                    </option>
                                ))}
                        </select>
                    </div>

                    {selectedComponent.type === 'container' && (
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">子组件数</label>
                            <div className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" style={{ display: 'flex', alignItems: 'center' }}>
                                {components.filter((item) => item.parentContainerId === selectedComponent.id).length}
                            </div>
                        </div>
                    )}
                    {selectedComponent.type === 'tab-switcher' && (
                        <div className="property-row flex items-center mb-3" style={{ alignItems: 'flex-start' }}>
                            <label className="property-label w-20 text-xs text-text-secondary">Tab联动</label>
                            <div style={{ display: 'grid', gap: 6, width: '100%' }}>
                                <button
                                    type="button"
                                    className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
                                    onClick={() => applyTabVisibilityRules(selectedComponent, components, updateConfig)}
                                >
                                    一键应用显隐规则
                                </button>
                                <button
                                    type="button"
                                    className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
                                    onClick={() => clearTabVisibilityRules(selectedComponent, components, updateConfig)}
                                >
                                    清理显隐规则
                                </button>
                                <div style={{ fontSize: 13, color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
                                    规则会按 Tab 选项顺序分配到图表/表格组件。
                                </div>
                            </div>
                        </div>
                    )}

                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">锁定</label>
                        <input
                            type="checkbox"
                            checked={selectedComponent.locked}
                            onChange={(e) => updateComponent(selectedComponent.id, { locked: e.target.checked })}
                        />
                    </div>

                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">可见</label>
                        <input
                            type="checkbox"
                            checked={selectedComponent.visible}
                            onChange={(e) => updateComponent(selectedComponent.id, { visible: e.target.checked })}
                        />
                    </div>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">多端可见</label>
                        <div style={{ display: 'flex', gap: 8 }}>
                            {(['pc', 'tablet', 'mobile'] as const).map((device) => {
                                const current = Array.isArray(selectedComponent.config.visibleOn)
                                    ? selectedComponent.config.visibleOn as string[]
                                    : ['pc', 'tablet', 'mobile'];
                                const checked = current.includes(device);
                                const label = device === 'pc' ? 'PC' : device === 'tablet' ? '平板' : '手机';
                                return (
                                    <label key={device} style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 13 }}>
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
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">变量可见条件</label>
                        <input
                            type="checkbox"
                            checked={selectedComponent.config.visibilityRuleEnabled === true}
                            onChange={(e) => handleConfigChange('visibilityRuleEnabled', e.target.checked)}
                        />
                    </div>
                    {selectedComponent.config.visibilityRuleEnabled === true && (
                        <>
                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">变量Key</label>
                                <input
                                    type="text"
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                    value={String(selectedComponent.config.visibilityVariableKey ?? '')}
                                    onChange={(e) => handleConfigChange('visibilityVariableKey', e.target.value)}
                                    placeholder="tabKey"
                                />
                            </div>
                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">匹配模式</label>
                                <select
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
                                    <div className="property-row flex items-center mb-3">
                                        <label className="property-label w-20 text-xs text-text-secondary">匹配值</label>
                                        <textarea
                                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                            rows={4}
                                            value={serializeVisibilityMatchValues(selectedComponent.config.visibilityMatchValues)}
                                            onChange={(e) => handleConfigChange('visibilityMatchValues', parseVisibilityMatchValues(e.target.value))}
                                            placeholder={'每行一个值，例如：\noverview\nline'}
                                        />
                                    </div>
                                );
                            })()}
                            <div style={{ fontSize: 13, color: 'var(--color-text-secondary)', marginTop: -2 }}>
                                仅在预览/公开/导出模式生效，设计器中始终可见便于编辑。
                            </div>
                        </>
                    )}
                </>
            ) : null}
        </div>
    );
}
