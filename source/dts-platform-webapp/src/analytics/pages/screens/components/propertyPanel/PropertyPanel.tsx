// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import { useCallback, useEffect, useState } from 'react';
import { Link2 } from 'lucide-react';
import { message } from 'antd';
import { toast } from 'sonner';
import { useScreen } from '../../ScreenContext';
import type {
    ChartMarkArea,
    ChartMarkLine,
    ScreenComponent,
    ScreenGlobalVariable,
    SeriesConditionalColor,
} from '../../types';
import { DRILLABLE_TYPES } from '../../types';
import { getRendererPlugin } from '../../plugins/registry';
import { readComponentPluginMeta, resolveRuntimePluginId } from '../../plugins/runtime';
import { useScreenPluginRuntime } from '../../plugins/useScreenPluginRuntime';
import { analyticsApi, type ScreenListItem } from '../../../../api/analyticsApi';
import { writeTextToClipboard } from '../../../../hooks/clipboard';
import {
    applyChartPresetConfig,
    isChartComponentType,
    type ChartPreset,
} from '../../chartPresets';
import { PROVINCE_PRESETS } from '../../renderers/shared/geoJsonCache';
import { COLOR_SCHEMES, recommendColorSchemes, type ColorScheme } from '../../colorSchemes';
import { getThemeTokens } from '../../screenThemes';

// Extracted modules (F4-Step3 split)
import {
    DEFAULT_SERIES_COLORS,
    LAYOUT_CLIPBOARD_KEY,
    PROPERTY_FOCUS_SECTION_KEYS,
    PROPERTY_PANEL_DENSITY_KEY,
    PROPERTY_SECTION_COLLAPSE_KEY,
    PROPERTY_SECTION_ESSENTIAL_COLLAPSED,
    PROPERTY_SECTION_KEYS,
    STYLE_CLIPBOARD_KEY,
    applyLegendHeuristicLayout,
    buildScreenJumpUrl,
    buildStyleClipboardPayload,
    deepMergeConfig,
    extractScreenIdFromJumpUrl,
    isVisualConfigKey,
    renderChartTitleLayoutRows,
    resolveExplainCardId,
    resolveLegendHeuristicLayout,
} from './helpers';
import { renderActionConfig, renderDrillDownConfig, renderInteractionConfig } from './BehaviorConfigSection';
import { CardSourceColumnBindingsEditor } from './CardSourceColumnBindingsEditor';
import { renderDataSourceConfig } from './DataSourceConfigSection';
import { renderPluginSchemaFields } from './PluginSchemaFieldsSection';
import { renderAnimationConfig } from './AnimationConfigSection';
import { renderOtherConfig } from './OtherConfigSection';
import { renderComponentAppearanceConfig } from './ComponentAppearanceSection';
import { renderPositionSizeConfig } from './PositionSizeSection';
import { renderComponentConfigSection } from './ComponentConfigSection';
import { renderFieldMappingConfig } from './FieldMappingSection';
import { renderExplainConfig } from './ExplainConfigSection';
import { renderQuickActionsConfig, type CanvasAlignMode, type QuickActionMode } from './QuickActionsSection';
import { ScrollBoardConfig } from './ScrollBoardConfig';
import { ChartAnnotationConfig } from './ChartAnnotationConfig';
import { TableConfig } from './TableConfig';
import { BackgroundImageRow } from './BackgroundImageRow';
import { SectionToggle } from './SectionToggle';
import { THEME_OPTIONS } from '../screenHeader/helpers';
import type {
    ColumnEntry,
    ExplainState,
    LayoutClipboardPayload,
    StyleClipboardPayload,
} from './types';

export type PropertyPanelTab = 'style' | 'data' | 'interaction' | 'advanced';


// ── PropertyPanel ────────────────────────────────────────────────────

export function PropertyPanel({ activeTab = 'style' }: { activeTab?: PropertyPanelTab }) {
    const {
        state,
        updateComponent,
        updateConfig,
        updateSelectedComponents,
        deleteComponents,
        alignSelected,
        distributeSelected,
        groupSelected,
        ungroupSelected,
        editorReadonly,
    } = useScreen();
    const { config, selectedIds } = state;
    useScreenPluginRuntime();
    const [explainState, setExplainState] = useState<ExplainState | null>(null);
    const [panelFilter, setPanelFilter] = useState('');
    const [styleClipboard, setStyleClipboard] = useState<StyleClipboardPayload | null>(null);
    const [layoutClipboard, setLayoutClipboard] = useState<LayoutClipboardPayload | null>(null);
    const [quickActionMode, setQuickActionMode] = useState<QuickActionMode>('core');
    const [panelDensity, setPanelDensity] = useState<'focus' | 'full'>(() => {
        if (typeof window === 'undefined') {
            return 'focus';
        }
        try {
            const raw = window.localStorage.getItem(PROPERTY_PANEL_DENSITY_KEY);
            return raw === 'full' ? 'full' : 'focus';
        } catch {
            return 'focus';
        }
    });
    const [collapsedSections, setCollapsedSections] = useState<string[]>([]);

    const selectedComponents = config.components.filter((c) => selectedIds.includes(c.id));
    const selectedComponent = selectedIds.length === 1
        ? config.components.find((c) => c.id === selectedIds[0])
        : null;

    useEffect(() => {
        setExplainState(null);
    }, [selectedComponent?.id]);
    useEffect(() => {
        if (typeof window === 'undefined') return;
        try {
            window.localStorage.setItem(PROPERTY_PANEL_DENSITY_KEY, panelDensity);
        } catch {
            // ignore storage failure
        }
    }, [panelDensity]);
    useEffect(() => {
        try {
            const raw = localStorage.getItem(PROPERTY_SECTION_COLLAPSE_KEY);
            if (!raw) {
                setCollapsedSections([...PROPERTY_SECTION_ESSENTIAL_COLLAPSED]);
                return;
            }
            const parsed = JSON.parse(raw) as unknown;
            if (!Array.isArray(parsed)) {
                setCollapsedSections([...PROPERTY_SECTION_ESSENTIAL_COLLAPSED]);
                return;
            }
            setCollapsedSections(parsed.filter((item) => typeof item === 'string'));
        } catch {
            setCollapsedSections([...PROPERTY_SECTION_ESSENTIAL_COLLAPSED]);
        }
    }, []);
    useEffect(() => {
        try {
            localStorage.setItem(PROPERTY_SECTION_COLLAPSE_KEY, JSON.stringify(collapsedSections));
        } catch {
            // ignore storage failure
        }
    }, [collapsedSections]);

    useEffect(() => {
        try {
            const raw = sessionStorage.getItem(STYLE_CLIPBOARD_KEY);
            if (!raw) return;
            const parsed = JSON.parse(raw) as StyleClipboardPayload;
            if (!parsed || typeof parsed !== 'object' || !parsed.type || !parsed.config) return;
            setStyleClipboard(parsed);
        } catch {
            // ignore invalid cache
        }
    }, []);
    useEffect(() => {
        try {
            const raw = sessionStorage.getItem(LAYOUT_CLIPBOARD_KEY);
            if (!raw) return;
            const parsed = JSON.parse(raw) as LayoutClipboardPayload;
            if (!parsed || typeof parsed !== 'object') return;
            if (!Number.isFinite(parsed.x) || !Number.isFinite(parsed.y)) return;
            if (!Number.isFinite(parsed.width) || !Number.isFinite(parsed.height)) return;
            setLayoutClipboard(parsed);
        } catch {
            // ignore invalid cache
        }
    }, []);

    const persistStyleClipboard = (payload: StyleClipboardPayload | null) => {
        setStyleClipboard(payload);
        try {
            if (!payload) {
                sessionStorage.removeItem(STYLE_CLIPBOARD_KEY);
                return;
            }
            sessionStorage.setItem(STYLE_CLIPBOARD_KEY, JSON.stringify(payload));
        } catch {
            // ignore storage failure
        }
    };
    const persistLayoutClipboard = (payload: LayoutClipboardPayload | null) => {
        setLayoutClipboard(payload);
        try {
            if (!payload) {
                sessionStorage.removeItem(LAYOUT_CLIPBOARD_KEY);
                return;
            }
            sessionStorage.setItem(LAYOUT_CLIPBOARD_KEY, JSON.stringify(payload));
        } catch {
            // ignore storage failure
        }
    };

    if (selectedComponents.length === 0) {
        const customTheme = config.customTheme;
        const handleCustomThemeChange = (key: string, value: string) => {
            updateConfig({
                customTheme: { ...(customTheme || {}), [key]: value },
                ...(key === 'backgroundColor' ? { backgroundColor: value } : {}),
            } as Partial<typeof config>);
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
                                    updateConfig({ theme, backgroundColor: tokens.canvasBackground });
                                }}
                            >
                                {THEME_OPTIONS.map((option) => (
                                    <option key={option.value} value={option.value}>{option.label}</option>
                                ))}
                            </select>
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
                                fontSize: 11,
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
                                <div style={{ fontSize: 11, color: 'var(--color-text-tertiary)', margin: '8px 0 4px' }}>自定义主题颜色</div>
                                {[
                                    ['primaryColor', '主色', '#409eff'],
                                    ['backgroundColor', '背景色', '#1e1f26'],
                                    ['textPrimary', '主文字', '#e2e8f0'],
                                    ['textSecondary', '副文字', '#94a3b8'],
                                    ['borderColor', '边框', '#1e293b'],
                                    ['cardBackground', '卡片背景', '#1a2332'],
                                ].map(([key, label, fallback]) => (
                                    <div className="property-row flex items-center mb-3" key={key}>
                                        <label className="property-label w-20 text-xs text-text-secondary">{label}</label>
                                        <input
                                            type="color"
                                            className="property-color-input w-8 h-7 border border-border-default rounded cursor-pointer p-0"
                                            value={customTheme?.[key] || fallback}
                                            onChange={(e) => handleCustomThemeChange(key, e.target.value)}
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
                            <input
                                type="color"
                                className="property-color-input w-8 h-7 border border-border-default rounded cursor-pointer p-0"
                                value={config.backgroundColor || '#1e1f26'}
                                onChange={(e) => updateConfig({ backgroundColor: e.target.value })}
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
        // 用函数式 updater：reducer 会用 store 里最新的 prev，而不是渲染时闭包里的快照。
        // 避免连续多次写入（或快速切换组件）时后一次覆盖前一次的其他字段。
        updateComponent(selectedComponent.id, (prev) => ({
            config: { ...(prev.config as Record<string, unknown>), [key]: value },
        }));
    };

    const canvasWidth = Number(config.width) || 1920;
    const canvasHeight = Number(config.height) || 1080;
    const normalizedPanelFilter = panelFilter.trim().toLowerCase();
    const sectionVisible = (...aliases: string[]) => {
        if (!normalizedPanelFilter) return true;
        return aliases.some((item) => item.toLowerCase().includes(normalizedPanelFilter));
    };
    const isSectionCollapsedStored = (sectionKey: string) => collapsedSections.includes(sectionKey);
    const isSectionCollapsed = (sectionKey: string) => (
        normalizedPanelFilter ? false : isSectionCollapsedStored(sectionKey)
    );
    const toggleSection = (sectionKey: string) => {
        setCollapsedSections((prev) => {
            if (prev.includes(sectionKey)) {
                return prev.filter((item) => item !== sectionKey);
            }
            return [...prev, sectionKey];
        });
    };
    const collapseAllSections = () => {
        setCollapsedSections([...PROPERTY_SECTION_KEYS]);
    };
    const expandAllSections = () => {
        setCollapsedSections([]);
    };
    const collapseToEssential = () => {
        setCollapsedSections([...PROPERTY_SECTION_ESSENTIAL_COLLAPSED]);
    };
    const applyPanelPreset = (
        preset: '' | '位置' | '组件' | '数据' | '联动' | '下钻' | '解释' | '其他' | '常用',
    ) => {
        if (!preset) {
            setPanelFilter('');
            return;
        }
        if (preset === '常用') {
            setPanelFilter('');
            setPanelDensity('focus');
            collapseToEssential();
            return;
        }
        setPanelFilter(preset);
        setPanelDensity('full');
    };
    const shouldRenderSection = (sectionKey: string, ...aliases: string[]) => {
        if (!sectionVisible(...aliases)) {
            return false;
        }
        if (panelDensity === 'full') {
            return true;
        }
        if (normalizedPanelFilter) {
            return true;
        }
        return PROPERTY_FOCUS_SECTION_KEYS.has(sectionKey);
    };
    const alignToCanvas = (mode: CanvasAlignMode) => {
        if (mode === 'left') {
            handleChange('x', 0);
            return;
        }
        if (mode === 'right') {
            handleChange('x', Math.max(0, canvasWidth - selectedComponent.width));
            return;
        }
        if (mode === 'top') {
            handleChange('y', 0);
            return;
        }
        if (mode === 'bottom') {
            handleChange('y', Math.max(0, canvasHeight - selectedComponent.height));
            return;
        }
        if (mode === 'h-center') {
            handleChange('x', Math.max(0, Math.round((canvasWidth - selectedComponent.width) / 2)));
            return;
        }
        handleChange('y', Math.max(0, Math.round((canvasHeight - selectedComponent.height) / 2)));
    };

    const duplicateCurrentComponent = () => {
        const nextId = `comp_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;
        const maxZ = config.components.length > 0
            ? Math.max(...config.components.map((item) => item.zIndex))
            : 0;
        const maxX = Math.max(0, canvasWidth - selectedComponent.width);
        const maxY = Math.max(0, canvasHeight - selectedComponent.height);
        const clone: ScreenComponent = {
            ...selectedComponent,
            id: nextId,
            x: Math.min(maxX, selectedComponent.x + 20),
            y: Math.min(maxY, selectedComponent.y + 20),
            zIndex: maxZ + 1,
            name: `${selectedComponent.name}-副本`,
        };
        updateConfig({ components: [...config.components, clone] });
    };

    const copyCurrentStyle = () => {
        const payload = buildStyleClipboardPayload(selectedComponent);
        persistStyleClipboard(payload);
        message.success(`已复制样式（${Object.keys(payload.config).length} 个外观字段）`);
    };

    const applyCopiedStyle = () => {
        if (!styleClipboard) {
            message.warning('样式剪贴板为空，请先复制一个组件样式');
            return;
        }
        if (styleClipboard.type !== selectedComponent.type) {
            const confirmed = window.confirm(
                `样式来源类型为「${styleClipboard.type}」，当前为「${selectedComponent.type}」。\n继续应用可能只部分生效，是否继续？`
            );
            if (!confirmed) return;
        }
        updateComponent(selectedComponent.id, {
            width: Math.max(50, Number(styleClipboard.width) || selectedComponent.width),
            height: Math.max(50, Number(styleClipboard.height) || selectedComponent.height),
            // 用 deepMerge：如 style / xAxis / legend 等嵌套对象，只想覆盖其中一部分子字段时，
            // 避免把剪贴板里没有的子字段直接清零。
            config: deepMergeConfig(
                selectedComponent.config as Record<string, unknown>,
                styleClipboard.config as Record<string, unknown>,
            ),
        });
    };

    const copyLayoutSnapshot = () => {
        persistLayoutClipboard({
            x: selectedComponent.x,
            y: selectedComponent.y,
            width: selectedComponent.width,
            height: selectedComponent.height,
            copiedAt: new Date().toISOString(),
        });
        message.success('布局已复制（位置 + 尺寸）');
    };

    const pasteLayoutSnapshot = () => {
        if (!layoutClipboard) {
            message.warning('布局剪贴板为空，请先复制布局');
            return;
        }
        const nextWidth = Math.max(50, Math.round(layoutClipboard.width));
        const nextHeight = Math.max(50, Math.round(layoutClipboard.height));
        const maxX = Math.max(0, canvasWidth - nextWidth);
        const maxY = Math.max(0, canvasHeight - nextHeight);
        updateComponent(selectedComponent.id, {
            x: Math.min(maxX, Math.max(0, Math.round(layoutClipboard.x))),
            y: Math.min(maxY, Math.max(0, Math.round(layoutClipboard.y))),
            width: nextWidth,
            height: nextHeight,
        });
    };

    const nudgePosition = (dx: number, dy: number) => {
        const maxX = Math.max(0, canvasWidth - selectedComponent.width);
        const maxY = Math.max(0, canvasHeight - selectedComponent.height);
        updateComponent(selectedComponent.id, {
            x: Math.min(maxX, Math.max(0, selectedComponent.x + dx)),
            y: Math.min(maxY, Math.max(0, selectedComponent.y + dy)),
        });
    };

    const nudgeSize = (dw: number, dh: number) => {
        const nextWidth = Math.min(canvasWidth, Math.max(50, selectedComponent.width + dw));
        const nextHeight = Math.min(canvasHeight, Math.max(50, selectedComponent.height + dh));
        const maxX = Math.max(0, canvasWidth - nextWidth);
        const maxY = Math.max(0, canvasHeight - nextHeight);
        updateComponent(selectedComponent.id, {
            width: nextWidth,
            height: nextHeight,
            x: Math.min(maxX, Math.max(0, selectedComponent.x)),
            y: Math.min(maxY, Math.max(0, selectedComponent.y)),
        });
    };
    const applyChartPreset = (preset: ChartPreset) => {
        if (!isChartComponentType(selectedComponent.type)) {
            message.warning('当前组件不是图表类型，无法应用图表预设');
            return;
        }
        updateComponent(selectedComponent.id, {
            config: applyChartPresetConfig(selectedComponent.config, preset),
        });
    };

    const copyConfigJson = async () => {
        const text = JSON.stringify(selectedComponent.config || {}, null, 2);
        const copied = await writeTextToClipboard(text);
        if (copied) { message.success('组件配置JSON已复制'); } else { message.warning('复制失败，请重试'); }
    };

    const pasteConfigJson = () => {
        const current = JSON.stringify(selectedComponent.config || {}, null, 2);
        const input = window.prompt('粘贴组件配置 JSON（将覆盖当前组件配置）', current);
        if (input == null) return;
        try {
            const parsed = JSON.parse(input);
            if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
                toast.error('配置必须是 JSON 对象');
                return;
            }
            updateComponent(selectedComponent.id, { config: parsed as Record<string, unknown> });
            message.success('组件配置已更新');
        } catch {
            toast.error('JSON 格式错误，请检查后重试');
        }
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
    const drillDownContent = shouldRenderSection('drill-down', '下钻', 'drill')
        ? renderDrillDownConfig(selectedComponent, updateComponent, { embedded: true })
        : null;
    const interactionContent = shouldRenderSection('interaction', '联动', '交互', 'interaction', 'jump')
        ? renderInteractionConfig(selectedComponent, config.globalVariables ?? [], updateComponent, { embedded: true })
        : null;
    const actionContent = shouldRenderSection('actions', '动作', '面板', '意图', '跳转')
        ? renderActionConfig(selectedComponent, updateComponent, { embedded: true })
        : null;

    const isStyleTab = activeTab === 'style';
    const isDataTab = activeTab === 'data';
    const isInteractionTab = activeTab === 'interaction';
    const isAdvancedTab = activeTab === 'advanced';
    const TAB_LABELS: Record<PropertyPanelTab, string> = { style: '样式', data: '数据', interaction: '交互', advanced: '高级' };

    return (
        <div className={`property-panel property-panel--${panelDensity}`} aria-readonly={editorReadonly}>
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
                {isStyleTab && <div className="property-section py-3 border-b border-border-default">
                    <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                        <SectionToggle
                            collapsed={isSectionCollapsed('quick-filter')}
                            label="快速定位"
                            onToggle={() => toggleSection('quick-filter')}
                        />
                    </div>
                    {!isSectionCollapsed('quick-filter') ? (
                        <>
                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">筛选</label>
                                <input
                                    type="text"
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                    value={panelFilter}
                                    onChange={(e) => setPanelFilter(e.target.value)}
                                    placeholder="输入：位置/样式/数据/联动/可见..."
                                />
                            </div>
                            <div className="property-quick-filter-row flex flex-wrap items-center gap-1.5">
                                <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => applyPanelPreset('')}>清空</button>
                                <select
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                    style={{ maxWidth: 160, padding: '4px 8px' }}
                                    defaultValue=""
                                    onChange={(event) => {
                                        const next = event.target.value as '' | '位置' | '组件' | '数据' | '联动' | '下钻' | '解释' | '其他' | '常用';
                                        applyPanelPreset(next);
                                        event.currentTarget.value = '';
                                    }}
                                >
                                    <option value="">快速定位到...</option>
                                    <option value="位置">位置与尺寸</option>
                                    <option value="组件">组件配置</option>
                                    <option value="数据">数据源</option>
                                    <option value="联动">联动配置</option>
                                    <option value="下钻">下钻配置</option>
                                    <option value="解释">解释</option>
                                    <option value="其他">其他</option>
                                    <option value="常用">常用视图</option>
                                </select>
                                <button
                                    type="button"
                                    className={`property-btn-small ${panelDensity === 'focus' ? 'is-active' : ''}`}
                                    onClick={() => setPanelDensity('focus')}
                                >
                                    高频
                                </button>
                                <button
                                    type="button"
                                    className={`property-btn-small ${panelDensity === 'full' ? 'is-active' : ''}`}
                                    onClick={() => setPanelDensity('full')}
                                >
                                    全部
                                </button>
                            </div>
                            <div className="property-quick-filter-row flex flex-wrap items-center gap-1.5">
                                <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={collapseToEssential}>常用视图</button>
                                <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={expandAllSections}>全部展开</button>
                                <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={collapseAllSections}>全部收起</button>
                            </div>
                        </>
                    ) : null}
                </div>}

                {isStyleTab && shouldRenderSection('quick-actions', '快捷', '操作', '样式', '复制', '对齐') && renderQuickActionsConfig({
                    selectedComponent,
                    quickActionMode,
                    setQuickActionMode,
                    styleClipboard,
                    layoutClipboard,
                    duplicateCurrentComponent,
                    deleteCurrentComponent: () => deleteComponents([selectedComponent.id]),
                    alignToCanvas,
                    nudgePosition,
                    nudgeSize,
                    copyCurrentStyle,
                    applyCopiedStyle,
                    copyLayoutSnapshot,
                    pasteLayoutSnapshot,
                    copyConfigJson,
                    pasteConfigJson,
                    clearStyleClipboard: () => persistStyleClipboard(null),
                    clearLayoutClipboard: () => persistLayoutClipboard(null),
                    applyChartPreset,
                    isSectionCollapsed,
                    toggleSection,
                })}

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
                    updateComponent,
                    isSectionCollapsed,
                    toggleSection,
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
                                fontSize: 11,
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
            </div>
        </div>
    );
}
