import { useEffect, useState } from 'react';
import { Link2 } from 'lucide-react';
import { useScreen } from '../../ScreenContext';
import type { ScreenCustomTheme } from '../../types';
import { getRendererPlugin } from '../../plugins/registry';
import { readComponentPluginMeta, resolveRuntimePluginId } from '../../plugins/runtime';
import { useScreenPluginRuntime } from '../../plugins/useScreenPluginRuntime';
import { analyticsApi } from '../../../../api/analyticsApi';
import { applyThemeToComponents, getThemeTokens } from '../../screenThemes';
import { FontFamilyField } from '../../configSchema/editors/FieldEditor';

// Extracted modules (F4-Step3 split)
import {
    PROPERTY_SECTION_COLLAPSE_KEY,
    resolveExplainCardId,
} from './helpers';
import { renderActionConfig, renderInteractionConfig } from './BehaviorConfigSection';
import { renderDrillDownConfig } from './DrillDownConfigSection';
import { renderDataSourceConfig } from './DataSourceConfigSection';
import { renderPluginSchemaFields } from './PluginSchemaFieldsSection';
import { renderAnimationConfig } from './AnimationConfigSection';
import { renderOtherConfig } from './OtherConfigSection';
import { renderComponentAppearanceConfig } from './ComponentAppearanceSection';
import { renderPositionSizeConfig } from './PositionSizeSection';
import { renderComponentConfigSection } from './ComponentConfigSection';
import { renderFieldMappingConfig } from './FieldMappingSection';
import { renderExplainConfig } from './ExplainConfigSection';
import { ChartAnnotationConfig } from './ChartAnnotationConfig';
import { BackgroundImageRow } from './BackgroundImageRow';
import { ColorPickerInput } from './ColorPickerInput';
import { SectionToggle } from './SectionToggle';
import { THEME_OPTIONS } from '../screenHeader/helpers';
import type { ExplainState } from './types';
import {
    readCollapsedSections,
    writeCollapsedSections,
} from './propertyPanelPersistence';

export type PropertyPanelTab = 'style' | 'data' | 'interaction' | 'advanced';

const CUSTOM_THEME_FIELDS: Array<{ key: keyof ScreenCustomTheme; label: string; fallback: string }> = [
    { key: 'primaryColor', label: '主色', fallback: '#409eff' },
    { key: 'backgroundColor', label: '背景色', fallback: '#1e1f26' },
    { key: 'textPrimary', label: '主文字', fallback: '#e2e8f0' },
    { key: 'textSecondary', label: '副文字', fallback: '#94a3b8' },
    { key: 'borderColor', label: '边框', fallback: '#1e293b' },
    { key: 'cardBackground', label: '卡片背景', fallback: '#1a2332' },
];

// ── PropertyPanel ────────────────────────────────────────────────────

export function PropertyPanel({ activeTab = 'style' }: { activeTab?: PropertyPanelTab }) {
    const {
        state,
        updateComponent,
        updateConfig,
        updateSelectedComponents,
        alignSelected,
        distributeSelected,
        groupSelected,
        ungroupSelected,
        editorReadonly,
    } = useScreen();
    const { config, selectedIds } = state;
    useScreenPluginRuntime();
    const [explainState, setExplainState] = useState<ExplainState | null>(null);
    const [collapsedSections, setCollapsedSections] = useState<string[]>([]);

    const selectedComponents = config.components.filter((c) => selectedIds.includes(c.id));
    const selectedComponent = selectedIds.length === 1
        ? config.components.find((c) => c.id === selectedIds[0])
        : null;

    useEffect(() => {
        setExplainState(null);
    }, [selectedComponent?.id]);
    useEffect(() => {
        setCollapsedSections(readCollapsedSections(
            typeof window === 'undefined' ? null : window.localStorage,
            PROPERTY_SECTION_COLLAPSE_KEY,
            [],
        ));
    }, []);
    useEffect(() => {
        writeCollapsedSections(
            typeof window === 'undefined' ? null : window.localStorage,
            PROPERTY_SECTION_COLLAPSE_KEY,
            collapsedSections,
        );
    }, [collapsedSections]);

    if (selectedComponents.length === 0) {
        const customTheme = config.customTheme;
        const handleCustomThemeChange = (key: keyof ScreenCustomTheme, value: string) => {
            const nextCustomTheme = { ...(customTheme || {}), [key]: value };
            updateConfig({
                customTheme: nextCustomTheme,
                ...(key === 'backgroundColor' ? { backgroundColor: value } : {}),
                ...(config.theme === 'brand-custom'
                    ? { components: applyThemeToComponents(config.components, config.theme, 'force', nextCustomTheme) }
                    : {}),
            } as Partial<typeof config>);
        };
        const handleGlobalFontChange = (raw: unknown) => {
            const value = typeof raw === 'string' ? raw.trim() : '';
            updateConfig({ fontFamily: value || undefined });
        };
        const isCustom = config.theme === 'brand-custom';
        return (
            <div className="flex flex-col h-full" aria-readonly={editorReadonly}>
                <div className="property-panel-header px-4 py-3 border-b border-border-default">
                    <h3>画布设置</h3>
                    <p className="text-xs text-text-muted mt-1">全局主题与画布属性</p>
                    {editorReadonly && (
                        <div
                            data-testid="analytics-screen-property-readonly-note"
                            className="mt-2 rounded border px-2 py-1.5 text-[11px]"
                            style={{
                                color: '#fbbf24',
                                borderColor: 'rgba(251,191,36,0.28)',
                                background: 'rgba(251,191,36,0.08)',
                            }}
                        >
                            只读模式：画布设置仅供查看。
                        </div>
                    )}
                </div>
                <div className="property-panel-content flex-1 overflow-y-auto px-4 py-2">
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2">主题</div>
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">主题方案</label>
                            <select
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                value={config.theme || 'legacy-dark'}
                                onChange={(e) => {
                                    const theme = e.target.value as typeof config.theme;
                                    const tokens = getThemeTokens(theme, theme === 'brand-custom' ? config.customTheme : undefined);
                                    updateConfig({
                                        theme,
                                        backgroundColor: tokens.canvasBackground,
                                        components: applyThemeToComponents(
                                            config.components,
                                            theme,
                                            'force',
                                            theme === 'brand-custom' ? config.customTheme : undefined,
                                        ),
                                    });
                                }}
                            >
                                {THEME_OPTIONS.map((option) => (
                                    <option key={option.value} value={option.value}>{option.label}</option>
                                ))}
                            </select>
                        </div>
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">全局字体</label>
                            <div className="flex-1 min-w-0">
                                <FontFamilyField
                                    value={config.fontFamily || undefined}
                                    onChange={handleGlobalFontChange}
                                />
                            </div>
                        </div>
                        <div className="property-row flex items-center mb-1">
                            <label className="property-label w-20 text-xs text-text-secondary">
                                密级
                                {!config.classification && (
                                    <span style={{ color: '#f59e0b', marginLeft: 4 }} title="未设置密级">⚠</span>
                                )}
                            </label>
                            <select
                                className="property-input flex-1 px-2.5 py-1.5 border rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                style={
                                    !config.classification
                                        ? { borderColor: '#f59e0b', background: 'rgba(245,158,11,0.08)' }
                                        : undefined
                                }
                                value={config.classification || ''}
                                onChange={(e) => {
                                    const next = e.target.value as typeof config.classification | '';
                                    updateConfig({ classification: next || undefined });
                                }}
                            >
                                <option value="">请选择密级</option>
                                <option value="PUBLIC">公开</option>
                                <option value="INTERNAL">内部</option>
                                <option value="SECRET">秘密</option>
                                <option value="CONFIDENTIAL">机密</option>
                            </select>
                        </div>
                        {/* Sprint-24 F3：未设密级时给一段说明，提醒补登；选完即隐藏，不打扰已设密级的大屏。 */}
                        <div
                            className="mb-3"
                            style={{
                                fontSize: 13,
                                color: !config.classification ? '#b45309' : 'var(--color-text-tertiary, #6b7280)',
                                paddingLeft: 80,
                                lineHeight: 1.5,
                            }}
                        >
                            {!config.classification
                                ? '未设置密级时本大屏对所有登录用户可见。请尽快补登，保存时也会按此密级管控可见范围。'
                                : '密级决定哪些人员可访问本大屏，可随时调整。'}
                        </div>
                        {isCustom && (
                            <>
                                <div style={{ fontSize: 13, color: 'var(--color-text-tertiary)', margin: '8px 0 4px' }}>自定义主题颜色</div>
                                {CUSTOM_THEME_FIELDS.map(({ key, label, fallback }) => (
                                    <div className="property-row flex items-center mb-3" key={key}>
                                        <label className="property-label w-20 text-xs text-text-secondary">{label}</label>
                                        <ColorPickerInput
                                            value={customTheme?.[key] || fallback}
                                            fallback={fallback}
                                            onChange={(value) => handleCustomThemeChange(key, value)}
                                            ariaLabel={label}
                                        />
                                    </div>
                                ))}
                            </>
                        )}
                    </div>
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2">画布</div>
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">宽度</label>
                            <input
                                type="number"
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                value={config.width || 1920}
                                onChange={(e) => updateConfig({ width: Math.max(320, Number(e.target.value) || 1920) })}
                            />
                        </div>
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">高度</label>
                            <input
                                type="number"
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                value={config.height || 1080}
                                onChange={(e) => updateConfig({ height: Math.max(240, Number(e.target.value) || 1080) })}
                            />
                        </div>
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">背景色</label>
                            <ColorPickerInput
                                value={config.backgroundColor || '#1e1f26'}
                                fallback="#1e1f26"
                                onChange={(value) => updateConfig({ backgroundColor: value })}
                                ariaLabel="背景色"
                            />
                        </div>
                        <BackgroundImageRow
                            value={config.backgroundImage || ''}
                            onChange={(url) => updateConfig({ backgroundImage: url })}
                        />
                    </div>
                    <div className="flex flex-col items-center justify-center py-8 text-center" style={{ padding: '20px 0' }}>
                        <div className="text-xs text-text-muted text-center py-8">点击画布中的组件进行选择编辑</div>
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
        const primarySelected = selectedComponents[0];
        return (
            <div className="flex flex-col h-full" aria-readonly={editorReadonly}>
                <div className="property-panel-header px-4 py-3 border-b border-border-default">
                    <h3>批量属性 ({total})</h3>
                    <p className="text-xs text-text-muted mt-1">
                        统一处理 {primarySelected?.type || 'selected'} 组件。
                        {grouped > 0 ? ` 当前包含 ${grouped} 个已编组组件。` : ''}
                    </p>
                    {editorReadonly && (
                        <div
                            data-testid="analytics-screen-property-readonly-note"
                            className="mt-2 rounded border px-2 py-1.5 text-[11px]"
                            style={{
                                color: '#fbbf24',
                                borderColor: 'rgba(251,191,36,0.28)',
                                background: 'rgba(251,191,36,0.08)',
                            }}
                        >
                            只读模式：批量编辑已被保护，当前面板仅用于查看。
                        </div>
                    )}
                </div>
                <div className="property-panel-content flex-1 overflow-y-auto px-4 py-2">
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2">批量设置</div>
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">宽度</label>
                            <input
                                type="number"
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                min={50}
                                onChange={(e) => updateSelectedComponents({ width: Math.max(50, Number(e.target.value) || 50) })}
                                placeholder="统一宽度"
                            />
                        </div>
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">高度</label>
                            <input
                                type="number"
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                min={50}
                                onChange={(e) => updateSelectedComponents({ height: Math.max(50, Number(e.target.value) || 50) })}
                                placeholder="统一高度"
                            />
                        </div>
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">锁定</label>
                            <select
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                value={allLocked ? 'locked' : 'unlocked'}
                                onChange={(e) => updateSelectedComponents({ locked: e.target.value === 'locked' })}
                            >
                                <option value="locked">全部锁定</option>
                                <option value="unlocked">全部解锁</option>
                            </select>
                        </div>
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">可见</label>
                            <select
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                value={allVisible ? 'visible' : 'hidden'}
                                onChange={(e) => updateSelectedComponents({ visible: e.target.value === 'visible' })}
                            >
                                <option value="visible">全部可见</option>
                                <option value="hidden">全部隐藏</option>
                            </select>
                        </div>
                    </div>
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2">批量动作</div>
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 6 }}>
                            <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => alignSelected('left')} disabled={total < 2}>左对齐</button>
                            <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => alignSelected('right')} disabled={total < 2}>右对齐</button>
                            <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => alignSelected('top')} disabled={total < 2}>顶对齐</button>
                            <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => alignSelected('bottom')} disabled={total < 2}>底对齐</button>
                            <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => distributeSelected('horizontal')} disabled={total < 3}>水平分布</button>
                            <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => distributeSelected('vertical')} disabled={total < 3}>垂直分布</button>
                            <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={groupSelected} disabled={total < 2}>组合</button>
                            <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={ungroupSelected} disabled={total < 1}>解组</button>
                        </div>
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 6, marginTop: 6 }}>
                            <button
                                type="button"
                                className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
                                disabled={!primarySelected}
                                onClick={() => {
                                    if (!primarySelected) return;
                                    updateSelectedComponents({ width: primarySelected.width });
                                }}
                                title="将选中组件宽度统一为首个选中组件宽度"
                            >
                                同步首项宽度
                            </button>
                            <button
                                type="button"
                                className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
                                disabled={!primarySelected}
                                onClick={() => {
                                    if (!primarySelected) return;
                                    updateSelectedComponents({ height: primarySelected.height });
                                }}
                                title="将选中组件高度统一为首个选中组件高度"
                            >
                                同步首项高度
                            </button>
                        </div>
                    </div>
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2">选择概览</div>
                        <div style={{ fontSize: 13, opacity: 0.8, lineHeight: 1.7 }}>
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
        // 用函数式 updater：reducer 会用 store 里最新的 prev，而不是渲染时闭包里的快照。
        // 避免连续多次写入（或快速切换组件）时后一次覆盖前一次的其他字段。
        updateComponent(selectedComponent.id, (prev) => ({
            config: { ...(prev.config as Record<string, unknown>), [key]: value },
        }));
    };

    const isSectionCollapsedStored = (sectionKey: string) => collapsedSections.includes(sectionKey);
    const isSectionCollapsed = (sectionKey: string) => isSectionCollapsedStored(sectionKey);
    const toggleSection = (sectionKey: string) => {
        setCollapsedSections((prev) => {
            if (prev.includes(sectionKey)) {
                return prev.filter((item) => item !== sectionKey);
            }
            return [...prev, sectionKey];
        });
    };
    const shouldRenderSection = (_sectionKey: string, ..._aliases: string[]) => true;

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
    const drillDownContent = shouldRenderSection('drill-down', '下钻', 'drill')
        ? renderDrillDownConfig(selectedComponent, updateComponent, config.globalVariables ?? [], { embedded: true })
        : null;
    const interactionContent = shouldRenderSection('interaction', '联动', '交互', 'interaction', 'jump')
        ? renderInteractionConfig(selectedComponent, config.globalVariables ?? [], updateComponent, { embedded: true })
        : null;
    const actionContent = shouldRenderSection('actions', '动作', '面板', '意图', '跳转')
        ? renderActionConfig(selectedComponent, updateComponent, config.pages ?? [], { embedded: true })
        : null;

    const isStyleTab = activeTab === 'style';
    const isDataTab = activeTab === 'data';
    const isInteractionTab = activeTab === 'interaction';
    const isAdvancedTab = activeTab === 'advanced';
    const TAB_LABELS: Record<PropertyPanelTab, string> = { style: '样式', data: '数据', interaction: '交互', advanced: '高级' };

    return (
        <div className="property-panel" aria-readonly={editorReadonly}>
            <div className="property-panel-header border-b border-border-default">
                <h3>{selectedComponent.name}</h3>
                <p className="text-xs text-text-muted mt-1">
                    {selectedComponent.type} · {selectedComponent.width} × {selectedComponent.height} · {TAB_LABELS[activeTab]}
                </p>
                {editorReadonly && (
                    <div
                        data-testid="analytics-screen-property-readonly-note"
                        className="mt-2 rounded border px-2 py-1.5 text-[11px]"
                        style={{
                            color: '#fbbf24',
                            borderColor: 'rgba(251,191,36,0.28)',
                            background: 'rgba(251,191,36,0.08)',
                        }}
                    >
                        只读模式：属性编辑已被保护，当前面板仅用于查看。
                    </div>
                )}
            </div>
            <div className="property-panel-content flex-1 overflow-y-auto">
                {/* Component Appearance */}
                {isStyleTab && shouldRenderSection('component-appearance', '外观', '背景', '圆角', '边框', '透明') && renderComponentAppearanceConfig({
                    selectedComponent,
                    handleConfigChange,
                    isSectionCollapsed,
                    toggleSection,
                })}

                {/* Position & Size */}
                {isStyleTab && shouldRenderSection('position-size', '位置', '尺寸', 'x', 'y', '宽', '高') && renderPositionSizeConfig({
                    selectedComponent,
                    handleChange,
                    isSectionCollapsed,
                    toggleSection,
                })}

                {/* Component-specific config (plugin) */}
                {isStyleTab && runtimePlugin?.propertySchema?.fields?.length && shouldRenderSection('plugin-config', '插件', 'plugin', runtimePlugin.name) ? (
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                            <SectionToggle
                                collapsed={isSectionCollapsed('plugin-config')}
                                label={`插件配置 (${runtimePlugin.name})`}
                                onToggle={() => toggleSection('plugin-config')}
                            />
                        </div>
                        {!isSectionCollapsed('plugin-config')
                            ? renderPluginSchemaFields(selectedComponent, runtimePlugin.propertySchema.fields, handleConfigChange)
                            : null}
                    </div>
                ) : null}

                {isStyleTab && shouldRenderSection('component-config', '组件', '样式', '图表', '外观') && renderComponentConfigSection({
                    selectedComponent,
                    theme: config.theme,
                    customTheme: config.customTheme,
                    updateComponent,
                    isSectionCollapsed,
                    toggleSection,
                    /* A7: 样式 Tab 隐藏 advanced 分组,这些字段下沉到"高级" Tab */
                    hideGroups: ['advanced'],
                })}

                {/* Data Source */}
                {isDataTab && shouldRenderSection('data-source', '数据', 'sql', 'card', 'api', 'dataset', 'metric') && (
                    <div className="property-section border-b border-border-default">
                        <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide flex items-center justify-between cursor-pointer select-none">
                            <SectionToggle
                                collapsed={isSectionCollapsed('data-source')}
                                label="数据源"
                                onToggle={() => toggleSection('data-source')}
                            />
                        </div>
                        {!isSectionCollapsed('data-source')
                            ? renderDataSourceConfig(selectedComponent, updateComponent, config.globalVariables ?? [])
                            : null}
                    </div>
                )}

                {/* Field Mapping */}
                {isDataTab && shouldRenderSection('data-source', '字段映射', 'field', 'mapping') && renderFieldMappingConfig({
                    selectedComponent,
                    handleConfigChange,
                    isSectionCollapsed,
                    toggleSection,
                })}

                {isDataTab && shouldRenderSection('explain', '解释', 'explain') && renderExplainConfig({
                    canExplain,
                    explainCardId,
                    explainState,
                    handleExplain,
                    isSectionCollapsed,
                    toggleSection,
                })}

                {/* Chart annotations (markLine / markArea / conditionalColors) */}
                {isStyleTab && shouldRenderSection('component-config', '标注', '辅助线', 'markLine', 'threshold') && (selectedComponent.type === 'line-chart' || selectedComponent.type === 'bar-chart' || selectedComponent.type === 'scatter-chart' || selectedComponent.type === 'combo-chart' || selectedComponent.type === 'waterfall-chart') && (
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                            <SectionToggle
                                collapsed={isSectionCollapsed('annotations')}
                                label="标注 / 阈值线"
                                onToggle={() => toggleSection('annotations')}
                            />
                        </div>
                        {!isSectionCollapsed('annotations') && (
                            <ChartAnnotationConfig
                                component={selectedComponent}
                                onChange={handleConfigChange}
                            />
                        )}
                    </div>
                )}

                {/* Drill-down config */}
                {isInteractionTab && drillDownContent ? (
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                            <SectionToggle
                                collapsed={isSectionCollapsed('drill-down')}
                                label="下钻配置"
                                onToggle={() => toggleSection('drill-down')}
                            />
                        </div>
                        {!isSectionCollapsed('drill-down') ? drillDownContent : null}
                    </div>
                ) : null}

                {isInteractionTab && interactionContent ? (
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                            <SectionToggle
                                collapsed={isSectionCollapsed('interaction')}
                                label="联动配置"
                                onToggle={() => toggleSection('interaction')}
                            />
                        </div>
                        {!isSectionCollapsed('interaction') ? interactionContent : null}
                    </div>
                ) : null}

                {isInteractionTab && actionContent ? (
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                            <SectionToggle
                                collapsed={isSectionCollapsed('actions')}
                                label="动作入口"
                                onToggle={() => toggleSection('actions')}
                            />
                        </div>
                        {!isSectionCollapsed('actions') ? actionContent : null}
                    </div>
                ) : null}

                {/* Visibility & Lock & Name */}
                {isAdvancedTab && shouldRenderSection('other', '其他', '名称', '容器', '锁定', '可见') && renderOtherConfig({
                    selectedComponent,
                    components: config.components,
                    updateComponent,
                    updateConfig,
                    handleConfigChange,
                    isSectionCollapsed,
                    toggleSection,
                })}

                {isInteractionTab && !drillDownContent && !interactionContent && !actionContent && (
                    <div className="flex flex-col items-center justify-center text-xs text-text-muted" style={{ minHeight: 220, padding: 24 }}>
                        <Link2 size={28} strokeWidth={1.6} className="opacity-30 mb-2" aria-hidden="true" />
                        <div className="text-xs text-text-muted">当前组件暂无交互配置</div>
                        <div className="text-xs text-text-muted text-center mt-2">图表类组件支持下钻、联动和动作配置</div>
                    </div>
                )}

                {/* Filler hint card — fills the blank space below sparse sections in 交互 tab */}
                {isInteractionTab && (drillDownContent || interactionContent || actionContent) && (
                    <div
                        style={{
                            flex: 1,
                            minHeight: 80,
                            display: 'flex',
                            flexDirection: 'column',
                            justifyContent: 'flex-end',
                            padding: '16px 4px 8px',
                        }}
                    >
                        <div
                            style={{
                                border: '1px dashed rgba(148,163,184,0.32)',
                                borderRadius: 8,
                                padding: 12,
                                background: 'rgba(248,250,252,0.55)',
                                fontSize: 13,
                                color: 'var(--color-text-secondary)',
                                lineHeight: 1.6,
                            }}
                        >
                            <div style={{ fontWeight: 600, marginBottom: 6, color: 'var(--color-text-primary)' }}>💡 交互配置说明</div>
                            <div>· <b>动作入口</b>：点击触发跳转、写变量、打开面板等</div>
                            <div>· <b>页面跳转</b>：选「选择大屏」可挑选已发布大屏，自动生成 screen-ref 链接</div>
                            <div>· <b>预览生效</b>：动作仅在预览/发布模式下响应点击</div>
                            <div>· <b>支持类型</b>：图表 / 表格 / 形状 / 标题 / 数字卡 / Markdown</div>
                        </div>
                    </div>
                )}
                {isAdvancedTab && renderAnimationConfig({
                    selectedComponent,
                    components: config.components,
                    updateConfig,
                    handleConfigChange,
                    isSectionCollapsed,
                    toggleSection,
                })}

                {/* A7: 高级 Tab 渲染组件 schema 中 group='advanced' 的字段(如标记线/提示框/散点数据 等低频字段) */}
                {isAdvancedTab && renderComponentConfigSection({
                    selectedComponent,
                    theme: config.theme,
                    customTheme: config.customTheme,
                    updateComponent,
                    isSectionCollapsed,
                    toggleSection,
                    onlyGroups: ['advanced'],
                })}
            </div>
        </div>
    );
}
