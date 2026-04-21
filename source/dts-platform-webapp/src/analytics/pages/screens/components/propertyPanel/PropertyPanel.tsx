// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import { useCallback, useEffect, useRef, useState } from 'react';
import { message } from 'antd';
import apiClient from '@/api/apiClient';
import { toast } from 'sonner';
import { useScreen } from '../../ScreenContext';
import SchemaConfigRenderer from '../../configSchema/editors/SchemaConfigRenderer';
import { COMPONENT_CONFIG_SCHEMAS } from '../../configSchema/schemas';
import type {
    CardParameterBinding,
    ChartMarkArea,
    ChartMarkLine,
    ComponentInteractionMapping,
    ComponentType,
    DataSourceConfig,
    DrillLevel,
    QuerySourceType,
    ScreenComponent,
    ScreenComponentAction,
    ScreenGlobalVariable,
    SeriesConditionalColor,
} from '../../types';
import { DRILLABLE_TYPES } from '../../types';
import { CardIdPicker } from '../CardIdPicker';
import { MetricBindingEditor } from '../MetricBindingEditor';
import { CardParamBindingsEditor } from '../CardParamBindingsEditor';
import { DatabaseIdPicker } from '../DatabaseIdPicker';
import { getRendererPlugin } from '../../plugins/registry';
import { readComponentPluginMeta, resolveRuntimePluginId } from '../../plugins/runtime';
import { useScreenPluginRuntime } from '../../plugins/useScreenPluginRuntime';
import type { PropertySchemaField } from '../../plugins/types';
import { wouldCreateParentCycle } from '../../componentHierarchy';
import { analyticsApi, type ExplainabilityResponse, type ScreenListItem } from '../../../../api/analyticsApi';
import { writeTextToClipboard } from '../../../../hooks/clipboard';
import {
    CHART_COMPONENT_TYPES,
    applyChartPresetConfig,
    isChartComponentType,
    type ChartPreset,
} from '../../chartPresets';
import { PROVINCE_PRESETS } from '../../renderers/shared/geoJsonCache';
import { FieldMappingPanel, isMappable } from '../FieldMappingPanel';
import type { FieldMapping } from '../../types';
import { COLOR_SCHEMES, recommendColorSchemes, type ColorScheme } from '../../colorSchemes';

// Extracted modules (F4-Step3 split)
import {
    ACTION_COMPONENT_TYPES,
    DRILL_CONFIGURABLE_TYPES,
    DEFAULT_SERIES_COLORS,
    INTERACTION_COMPONENT_TYPES,
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
    extractSqlTemplateParameterNames,
    getActionSourcePathCandidates,
    isVisualConfigKey,
    parseVisibilityMatchValues,
    renderChartTitleLayoutRows,
    resolveDataSourceType,
    resolveExplainCardId,
    resolveLegendHeuristicLayout,
    resolveSqlConfig,
    resolveTabSwitcherOptionValues,
    safeJsonParse,
    safeJsonStringify,
    serializeVisibilityMatchValues,
    setByPath,
} from './helpers';
import { ScreenJumpPicker } from './ScreenJumpPicker';
import { CardSourceColumnBindingsEditor } from './CardSourceColumnBindingsEditor';
import { ScrollBoardConfig } from './ScrollBoardConfig';
import { ChartAnnotationConfig } from './ChartAnnotationConfig';
import { TableConfig } from './TableConfig';
import { StaticDataEditor } from './StaticDataEditor';
import type {
    ColumnEntry,
    ExplainState,
    LayoutClipboardPayload,
    StyleClipboardPayload,
} from './types';

export type PropertyPanelTab = 'style' | 'data' | 'interaction' | 'advanced';


// ── BackgroundImageRow (screen-level background image upload) ────────
function BackgroundImageRow({ value, onChange }: { value: string; onChange: (url: string) => void }) {
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [uploading, setUploading] = useState(false);

    const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        if (file.size > 10 * 1024 * 1024) { message.error('文件大小不能超过 10MB'); return; }
        const formData = new FormData();
        formData.append('file', file);
        setUploading(true);
        try {
            const res = await apiClient.post<{ data: { url: string } }>({ url: '/infra/screen-images/upload', data: formData });
            const url = (res as any)?.data?.url ?? (res as any)?.url;
            if (url) { onChange(url); message.success('上传成功'); }
            else { message.error('上传返回格式异常'); }
        } catch (err: any) { message.error(err?.message || '上传失败'); }
        finally { setUploading(false); if (fileInputRef.current) fileInputRef.current.value = ''; }
    };

    return (
        <div className="property-row flex items-center mb-3">
            <label className="property-label w-20 text-xs text-text-secondary">背景图</label>
            <div style={{ display: 'flex', gap: 4, alignItems: 'center', flex: 1 }}>
                <input
                    type="text"
                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                    value={value}
                    onChange={(e) => onChange(e.target.value)}
                    placeholder="图片 URL 或点击上传"
                />
                <button
                    type="button"
                    className="px-2 py-1.5 text-xs border border-border-default rounded bg-surface-card text-text-primary hover:bg-surface-hover"
                    disabled={uploading}
                    onClick={() => fileInputRef.current?.click()}
                >
                    {uploading ? '...' : '上传'}
                </button>
                {value && (
                    <button
                        type="button"
                        className="px-1.5 py-1.5 text-xs border border-border-default rounded bg-surface-card text-text-primary hover:bg-surface-hover"
                        title="清除背景图"
                        onClick={() => onChange('')}
                    >
                        ✕
                    </button>
                )}
                <input ref={fileInputRef} type="file" accept="image/*" style={{ display: 'none' }}
                    onChange={handleUpload} />
            </div>
        </div>
    );
}

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
    } = useScreen();
    const { config, selectedIds } = state;
    useScreenPluginRuntime();
    const [explainState, setExplainState] = useState<ExplainState | null>(null);
    const [panelFilter, setPanelFilter] = useState('');
    const [styleClipboard, setStyleClipboard] = useState<StyleClipboardPayload | null>(null);
    const [layoutClipboard, setLayoutClipboard] = useState<LayoutClipboardPayload | null>(null);
    const [quickActionMode, setQuickActionMode] = useState<'core' | 'layout' | 'nudge' | 'clipboard' | 'all'>('core');
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
        const customTheme = (config as unknown as Record<string, unknown>).customTheme as Record<string, string> | undefined;
        const handleCustomThemeChange = (key: string, value: string) => {
            updateConfig({ customTheme: { ...(customTheme || {}), [key]: value } } as Partial<typeof config>);
        };
        const isCustom = config.theme === 'brand-custom';
        return (
            <div className="flex flex-col h-full">
                <div className="property-panel-header px-4 py-3 border-b border-border-default">
                    <h3>画布设置</h3>
                    <p className="text-xs text-text-muted mt-1">全局主题与画布属性</p>
                </div>
                <div className="property-panel-content flex-1 overflow-y-auto px-4 py-2">
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2">主题</div>
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">主题方案</label>
                            <select
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                value={config.theme || 'legacy-dark'}
                                onChange={(e) => updateConfig({ theme: e.target.value as typeof config.theme })}
                            >
                                <option value="legacy-dark">经典暗色</option>
                                <option value="titanium">钛金属</option>
                                <option value="glacier">冰川</option>
                                <option value="light-business">商务浅色</option>
                                <option value="dark-command">指挥暗色</option>
                                <option value="brand-custom">自定义</option>
                            </select>
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
            <div className="flex flex-col h-full">
                <div className="property-panel-header px-4 py-3 border-b border-border-default">
                    <h3>批量属性 ({total})</h3>
                    <p className="text-xs text-text-muted mt-1">
                        统一处理 {primarySelected?.type || 'selected'} 组件。
                        {grouped > 0 ? ` 当前包含 ${grouped} 个已编组组件。` : ''}
                    </p>
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
    const showQuickActionGroup = (group: 'core' | 'layout' | 'nudge' | 'clipboard') => (
        quickActionMode === 'all' || quickActionMode === group
    );
    const getQuickActionFilterButtonStyle = (mode: 'core' | 'layout' | 'nudge' | 'clipboard' | 'all') => (
        quickActionMode === mode
            ? { borderColor: 'var(--color-primary)', background: 'var(--color-primary-light)' }
            : undefined
    );

    const alignToCanvas = (mode: 'left' | 'right' | 'top' | 'bottom' | 'h-center' | 'v-center') => {
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

    const applyTabVisibilityRules = () => {
        if (selectedComponent.type !== 'tab-switcher') return;
        const variableKey = String(selectedComponent.config.variableKey || 'tabKey').trim() || 'tabKey';
        const optionValues = resolveTabSwitcherOptionValues(selectedComponent.config.options);
        if (optionValues.length === 0) {
            message.warning('请先在 Tab 组件中配置可用选项');
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
            message.warning('当前画布没有可绑定 Tab 显隐规则的图表/表格组件');
            return;
        }
        updateConfig({ components: nextComponents });
        message.success(`已应用 Tab 显隐规则到 ${assigned} 个组件`);
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
            message.info('未找到可清理的 Tab 显隐规则');
            return;
        }
        updateConfig({ components: nextComponents });
        message.success(`已清理 ${cleared} 个组件的 Tab 显隐规则`);
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
        <div className={`property-panel property-panel--${panelDensity}`}>
            <div className="property-panel-header border-b border-border-default">
                <h3>{selectedComponent.name}</h3>
                <p className="text-xs text-text-muted mt-1">
                    {selectedComponent.type} · {selectedComponent.width} × {selectedComponent.height} · {TAB_LABELS[activeTab]}
                </p>
            </div>
            <div className="property-panel-content flex-1 overflow-y-auto">
                {isStyleTab && <div className="property-section py-3 border-b border-border-default">
                    <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                        <button
                            type="button"
                            className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                            onClick={() => toggleSection('quick-filter')}
                        >
                            {isSectionCollapsed('quick-filter') ? '▸' : '▾'} 快速定位
                        </button>
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

                {isStyleTab && shouldRenderSection('quick-actions', '快捷', '操作', '样式', '复制', '对齐') && (
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                            <button
                                type="button"
                                className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                                onClick={() => toggleSection('quick-actions')}
                            >
                                {isSectionCollapsed('quick-actions') ? '▸' : '▾'} 快捷操作
                            </button>
                        </div>
                        {!isSectionCollapsed('quick-actions') ? (
                            <>
                                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 6 }}>
                                    <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" style={getQuickActionFilterButtonStyle('core')} onClick={() => setQuickActionMode('core')}>常用</button>
                                    <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" style={getQuickActionFilterButtonStyle('layout')} onClick={() => setQuickActionMode('layout')}>布局</button>
                                    <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" style={getQuickActionFilterButtonStyle('nudge')} onClick={() => setQuickActionMode('nudge')}>微调</button>
                                    <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" style={getQuickActionFilterButtonStyle('clipboard')} onClick={() => setQuickActionMode('clipboard')}>剪贴板</button>
                                    <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" style={getQuickActionFilterButtonStyle('all')} onClick={() => setQuickActionMode('all')}>全部</button>
                                </div>
                                {showQuickActionGroup('core') ? (
                                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 6 }}>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={duplicateCurrentComponent}>复制组件</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => deleteComponents([selectedComponent.id])}>删除组件</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => alignToCanvas('h-center')}>水平居中</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => alignToCanvas('v-center')}>垂直居中</button>
                                    </div>
                                ) : null}
                                {showQuickActionGroup('layout') ? (
                                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 6, marginTop: 6 }}>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => alignToCanvas('left')}>贴左</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => alignToCanvas('right')}>贴右</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => alignToCanvas('top')}>贴上</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => alignToCanvas('bottom')}>贴下</button>
                                    </div>
                                ) : null}
                                {showQuickActionGroup('nudge') ? (
                                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 6, marginTop: 6 }}>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => nudgePosition(-1, 0)} title="X -1">←1</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => nudgePosition(1, 0)} title="X +1">→1</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => nudgePosition(0, -1)} title="Y -1">↑1</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => nudgePosition(0, 1)} title="Y +1">↓1</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => nudgePosition(-10, 0)} title="X -10">←10</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => nudgePosition(10, 0)} title="X +10">→10</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => nudgePosition(0, -10)} title="Y -10">↑10</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => nudgePosition(0, 10)} title="Y +10">↓10</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => nudgeSize(-10, 0)} title="宽度 -10">宽-10</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => nudgeSize(10, 0)} title="宽度 +10">宽+10</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => nudgeSize(0, -10)} title="高度 -10">高-10</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => nudgeSize(0, 10)} title="高度 +10">高+10</button>
                                    </div>
                                ) : null}
                                {showQuickActionGroup('clipboard') ? (
                                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 6, marginTop: 6 }}>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={copyCurrentStyle}>复制样式</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={applyCopiedStyle}>粘贴样式</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={copyLayoutSnapshot}>复制布局</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={pasteLayoutSnapshot}>粘贴布局</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => { void copyConfigJson(); }}>复制配置JSON</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={pasteConfigJson}>粘贴配置JSON</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => persistStyleClipboard(null)}>清空样式板</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => persistLayoutClipboard(null)}>清空布局板</button>
                                    </div>
                                ) : null}
                                {CHART_COMPONENT_TYPES.has(selectedComponent.type) ? (
                                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 6, marginTop: 6 }}>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => applyChartPreset('business')} title="适合白底商务大屏">商务预设</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => applyChartPreset('compact')} title="适合小尺寸组件">紧凑预设</button>
                                        <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => applyChartPreset('clear')} title="恢复默认可读策略">恢复预设</button>
                                    </div>
                                ) : null}
                                <div style={{ marginTop: 6, fontSize: 11, color: 'var(--color-text-secondary)', lineHeight: 1.45 }}>
                                    {styleClipboard
                                        ? `样式剪贴板：${styleClipboard.type}（${Object.keys(styleClipboard.config || {}).length} 字段）`
                                        : '样式剪贴板为空，可先在任意组件点击“复制样式”。'}
                                    <br />
                                    {layoutClipboard
                                        ? `布局剪贴板：${layoutClipboard.width}×${layoutClipboard.height} @ (${layoutClipboard.x}, ${layoutClipboard.y})`
                                        : '布局剪贴板为空，可复制当前组件布局。'}
                                </div>
                            </>
                        ) : null}
                    </div>
                )}

                {/* Component Appearance */}
                {isStyleTab && shouldRenderSection('component-appearance', '外观', '背景', '圆角', '边框', '透明') && (() => {
                    const cc = selectedComponent.config;
                    const setCC = handleConfigChange;
                    return (
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                            <button
                                type="button"
                                className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                                onClick={() => toggleSection('component-appearance')}
                            >
                                {isSectionCollapsed('component-appearance') ? '▸' : '▾'} 组件外观
                            </button>
                        </div>
                        {!isSectionCollapsed('component-appearance') ? (
                            <>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">背景色</label>
                                    <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                                        <input
                                            type="color"
                                            className="property-color-input w-8 h-7 border border-border-default rounded cursor-pointer p-0"
                                            value={String(cc.componentBgColor || 'transparent') === 'transparent' ? '#000000' : String(cc.componentBgColor || '#000000')}
                                            onChange={(e) => setCC('componentBgColor', e.target.value)}
                                        />
                                        <input
                                            type="text"
                                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                            style={{ flex: 1 }}
                                            value={String(cc.componentBgColor || '')}
                                            onChange={(e) => setCC('componentBgColor', e.target.value)}
                                            placeholder="transparent"
                                        />
                                        {cc.componentBgColor ? (
                                            <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={() => setCC('componentBgColor', '')}>清除</button>
                                        ) : null}
                                    </div>
                                </div>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">背景透明度</label>
                                    <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                                        <input
                                            type="range"
                                            min={0}
                                            max={100}
                                            step={1}
                                            value={Number(cc.componentBgOpacity ?? 100)}
                                            onChange={(e) => setCC('componentBgOpacity', Number(e.target.value))}
                                            style={{ flex: 1 }}
                                        />
                                        <span style={{ fontSize: 11, minWidth: 32, textAlign: 'right' }}>{Number(cc.componentBgOpacity ?? 100)}%</span>
                                    </div>
                                </div>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">圆角</label>
                                    <input
                                        type="number"
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        value={Number(cc.componentBorderRadius ?? 0)}
                                        onChange={(e) => setCC('componentBorderRadius', Math.max(0, Number(e.target.value) || 0))}
                                        min={0}
                                        max={100}
                                        placeholder="0"
                                    />
                                </div>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">边框</label>
                                    <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                                        <input
                                            type="number"
                                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                            style={{ width: 50 }}
                                            value={Number(cc.componentBorderWidth ?? 0)}
                                            onChange={(e) => setCC('componentBorderWidth', Math.max(0, Number(e.target.value) || 0))}
                                            min={0}
                                            max={20}
                                            placeholder="0"
                                        />
                                        <input
                                            type="color"
                                            className="property-color-input w-8 h-7 border border-border-default rounded cursor-pointer p-0"
                                            value={String(cc.componentBorderColor || '#ffffff')}
                                            onChange={(e) => setCC('componentBorderColor', e.target.value)}
                                        />
                                        <select
                                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                            style={{ flex: 1 }}
                                            value={String(cc.componentBorderStyle || 'solid')}
                                            onChange={(e) => setCC('componentBorderStyle', e.target.value)}
                                        >
                                            <option value="solid">实线</option>
                                            <option value="dashed">虚线</option>
                                            <option value="dotted">点线</option>
                                        </select>
                                    </div>
                                </div>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">内边距</label>
                                    <input
                                        type="number"
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        value={Number(cc.componentPadding ?? 0)}
                                        onChange={(e) => setCC('componentPadding', Math.max(0, Number(e.target.value) || 0))}
                                        min={0}
                                        max={100}
                                        placeholder="0"
                                    />
                                </div>
                            </>
                        ) : null}
                    </div>
                    );
                })()}

                {/* Position & Size */}
                {isStyleTab && shouldRenderSection('position-size', '位置', '尺寸', 'x', 'y', '宽', '高') && (
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                            <button
                                type="button"
                                className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                                onClick={() => toggleSection('position-size')}
                            >
                                {isSectionCollapsed('position-size') ? '▸' : '▾'} 位置与尺寸
                            </button>
                        </div>
                        {!isSectionCollapsed('position-size') ? (
                            <>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">X</label>
                                    <input
                                        type="number"
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        value={selectedComponent.x}
                                        onChange={(e) => handleChange('x', Number(e.target.value))}
                                    />
                                </div>

                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">Y</label>
                                    <input
                                        type="number"
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        value={selectedComponent.y}
                                        onChange={(e) => handleChange('y', Number(e.target.value))}
                                    />
                                </div>

                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">宽度</label>
                                    <input
                                        type="number"
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        value={selectedComponent.width}
                                        onChange={(e) => handleChange('width', Number(e.target.value))}
                                    />
                                </div>

                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">高度</label>
                                    <input
                                        type="number"
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        value={selectedComponent.height}
                                        onChange={(e) => handleChange('height', Number(e.target.value))}
                                    />
                                </div>
                            </>
                        ) : null}
                    </div>
                )}

                {/* Component-specific config (plugin) */}
                {isStyleTab && runtimePlugin?.propertySchema?.fields?.length && shouldRenderSection('plugin-config', '插件', 'plugin', runtimePlugin.name) ? (
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                            <button
                                type="button"
                                className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                                onClick={() => toggleSection('plugin-config')}
                            >
                                {isSectionCollapsed('plugin-config') ? '▸' : '▾'} 插件配置 ({runtimePlugin.name})
                            </button>
                        </div>
                        {!isSectionCollapsed('plugin-config')
                            ? renderPluginSchemaFields(selectedComponent, runtimePlugin.propertySchema.fields, handleConfigChange)
                            : null}
                    </div>
                ) : null}

                {isStyleTab && shouldRenderSection('component-config', '组件', '样式', '图表', '外观') && (
                    <div className="property-section py-3 border-b border-border-default">
                        <div
                            className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none"
                            style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}
                        >
                            <button
                                type="button"
                                className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                                onClick={() => toggleSection('component-config')}
                            >
                                {isSectionCollapsed('component-config') ? '▸' : '▾'} 组件配置
                            </button>
                        </div>

                        {!isSectionCollapsed('component-config') && (() => {
                            if (!selectedComponent) return null;
                            const schema = COMPONENT_CONFIG_SCHEMAS[selectedComponent.type];
                            if (!schema) {
                                return <div className="text-xs text-center py-4 opacity-60">暂无可配置项</div>;
                            }
                            return (
                                <SchemaConfigRenderer
                                    schema={schema}
                                    config={(selectedComponent.config as Record<string, unknown>) ?? {}}
                                    onChange={(key, value) => {
                                        // 读取侧（SchemaConfigRenderer.resolveNestedValue）按 "a.b.c" 逐层访问，
                                        // 写入侧必须对称，否则 "style.fontSize" 会被存成扁平键，导致刷新后读不回。
                                        // 用函数式 updater 避免闭包陈旧值覆盖最新 store 状态。
                                        updateComponent(selectedComponent.id, (prev) => ({
                                            config: setByPath(
                                                prev.config as Record<string, unknown>,
                                                key,
                                                value,
                                            ),
                                        }));
                                    }}
                                    theme={config.theme}
                                />
                            );
                        })()}
                    </div>
                )}

                {/* Data Source */}
                {isDataTab && shouldRenderSection('data-source', '数据', 'sql', 'card', 'api', 'dataset', 'metric') && (
                    <div className="property-section border-b border-border-default">
                        <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide flex items-center justify-between cursor-pointer select-none">
                            <button
                                type="button"
                                className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                                onClick={() => toggleSection('data-source')}
                            >
                                {isSectionCollapsed('data-source') ? '▸' : '▾'} 数据源
                            </button>
                        </div>
                        {!isSectionCollapsed('data-source')
                            ? renderDataSourceConfig(selectedComponent, updateComponent, config.globalVariables ?? [])
                            : null}
                    </div>
                )}

                {/* Field Mapping */}
                {isDataTab && shouldRenderSection('data-source', '字段映射', 'field', 'mapping') && isMappable(selectedComponent.type) && (() => {
                    const fmSourceCols = selectedComponent.config._sourceColumns as Array<{ name: string; displayName: string; baseType?: string }> ?? [];
                    const hasFmSource = resolveDataSourceType(selectedComponent.dataSource as DataSourceConfig | undefined) !== 'static';
                    if (!hasFmSource || fmSourceCols.length === 0) return null;
                    const currentMapping = (selectedComponent.config._fieldMapping as FieldMapping) ?? {};
                    const useFieldMapping = selectedComponent.config._useFieldMapping !== false;
                    return (
                        <div className="property-section py-3 border-b border-border-default">
                            <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                                <button
                                    type="button"
                                    className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                                    onClick={() => toggleSection('field-mapping')}
                                >
                                    {isSectionCollapsed('field-mapping') ? '▸' : '▾'} 字段映射
                                </button>
                                <label style={{ fontSize: 11, color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: 4, marginLeft: 'auto' }}>
                                    <input
                                        type="checkbox"
                                        checked={useFieldMapping}
                                        onChange={(e) => {
                                            handleConfigChange('_useFieldMapping', e.target.checked);
                                        }}
                                    />
                                    启用
                                </label>
                            </div>
                            {!isSectionCollapsed('field-mapping') && useFieldMapping ? (
                                <FieldMappingPanel
                                    componentType={selectedComponent.type}
                                    sourceColumns={fmSourceCols}
                                    mapping={currentMapping}
                                    onChange={(newMapping) => handleConfigChange('_fieldMapping', newMapping)}
                                />
                            ) : !isSectionCollapsed('field-mapping') ? (
                                <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', padding: '4px 0' }}>
                                    字段映射已关闭，使用高级模式直接编辑 config。
                                </div>
                            ) : null}
                        </div>
                    );
                })()}

                {isDataTab && shouldRenderSection('explain', '解释', 'explain') && (
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                            <button
                                type="button"
                                className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                                onClick={() => toggleSection('explain')}
                            >
                                {isSectionCollapsed('explain') ? '▸' : '▾'} 解释
                            </button>
                        </div>
                        {!isSectionCollapsed('explain') ? (
                            canExplain ? (
                                <div style={{ display: 'grid', gap: 8 }}>
                                    <button
                                        type="button"
                                        className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
                                        onClick={() => { void handleExplain(); }}
                                    >
                                        解释当前组件
                                    </button>
                                    <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', lineHeight: 1.45 }}>
                                        解释来源 CardId: {explainCardId}
                                    </div>
                                    {explainState?.state === 'loading' ? (
                                        <div style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>解释生成中...</div>
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
                                                className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
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
                                <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', lineHeight: 1.45 }}>
                                    当前组件未绑定可解释的 Card 数据源。
                                </div>
                            )
                        ) : null}
                    </div>
                )}

                {/* Chart annotations (markLine / markArea / conditionalColors) */}
                {isStyleTab && shouldRenderSection('component-config', '标注', '辅助线', 'markLine', 'threshold') && (selectedComponent.type === 'line-chart' || selectedComponent.type === 'bar-chart' || selectedComponent.type === 'scatter-chart' || selectedComponent.type === 'combo-chart' || selectedComponent.type === 'waterfall-chart') && (
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                            <button
                                type="button"
                                className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                                onClick={() => toggleSection('annotations')}
                            >
                                {isSectionCollapsed('annotations') ? '▸' : '▾'} 标注 / 阈值线
                            </button>
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
                            <button
                                type="button"
                                className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                                onClick={() => toggleSection('drill-down')}
                            >
                                {isSectionCollapsed('drill-down') ? '▸' : '▾'} 下钻配置
                            </button>
                        </div>
                        {!isSectionCollapsed('drill-down') ? drillDownContent : null}
                    </div>
                ) : null}

                {isInteractionTab && interactionContent ? (
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                            <button
                                type="button"
                                className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                                onClick={() => toggleSection('interaction')}
                            >
                                {isSectionCollapsed('interaction') ? '▸' : '▾'} 联动配置
                            </button>
                        </div>
                        {!isSectionCollapsed('interaction') ? interactionContent : null}
                    </div>
                ) : null}

                {isInteractionTab && actionContent ? (
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                            <button
                                type="button"
                                className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                                onClick={() => toggleSection('actions')}
                            >
                                {isSectionCollapsed('actions') ? '▸' : '▾'} 动作入口
                            </button>
                        </div>
                        {!isSectionCollapsed('actions') ? actionContent : null}
                    </div>
                ) : null}

                {/* Visibility & Lock & Name */}
                {isAdvancedTab && shouldRenderSection('other', '其他', '名称', '容器', '锁定', '可见') && (
                    <div className="property-section py-3 border-b border-border-default">
                        <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                            <button
                                type="button"
                                className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                                onClick={() => toggleSection('other')}
                            >
                                {isSectionCollapsed('other') ? '▸' : '▾'} 其他
                            </button>
                        </div>
                        {!isSectionCollapsed('other') ? (
                            <>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">名称</label>
                                    <input
                                        type="text"
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        value={selectedComponent.name}
                                        onChange={(e) => handleChange('name', e.target.value)}
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
                                            const parent = config.components.find((item) => item.id === parentId && item.type === 'container');
                                            if (!parent) {
                                                updateComponent(selectedComponent.id, { parentContainerId: undefined });
                                                return;
                                            }
                                            if (wouldCreateParentCycle(config.components, selectedComponent.id, parentId)) {
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
                                    <div className="property-row flex items-center mb-3">
                                        <label className="property-label w-20 text-xs text-text-secondary">子组件数</label>
                                        <div className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" style={{ display: 'flex', alignItems: 'center' }}>
                                            {config.components.filter((item) => item.parentContainerId === selectedComponent.id).length}
                                        </div>
                                    </div>
                                )}
                                {selectedComponent.type === 'tab-switcher' && (
                                    <div className="property-row flex items-center mb-3" style={{ alignItems: 'flex-start' }}>
                                        <label className="property-label w-20 text-xs text-text-secondary">Tab联动</label>
                                        <div style={{ display: 'grid', gap: 6, width: '100%' }}>
                                            <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={applyTabVisibilityRules}>
                                                一键应用显隐规则
                                            </button>
                                            <button type="button" className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed" onClick={clearTabVisibilityRules}>
                                                清理显隐规则
                                            </button>
                                            <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
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
                                        onChange={(e) => handleChange('locked', e.target.checked)}
                                    />
                                </div>

                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">可见</label>
                                    <input
                                        type="checkbox"
                                        checked={selectedComponent.visible}
                                        onChange={(e) => handleChange('visible', e.target.checked)}
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
                                        <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginTop: -2 }}>
                                            仅在预览/公开/导出模式生效，设计器中始终可见便于编辑。
                                        </div>
                                    </>
                                )}
                            </>
                        ) : null}
                    </div>
                )}

                {isInteractionTab && !drillDownContent && !interactionContent && !actionContent && (
                    <div className="flex flex-col items-center justify-center text-xs text-text-muted" style={{ minHeight: 220, padding: 24 }}>
                        <div className="text-3xl opacity-30 mb-2">🔗</div>
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
                {isAdvancedTab && (
                    <div className="property-section py-3 border-b border-border-default" style={{ marginTop: 8 }}>
                        <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                            <button
                                type="button"
                                className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                                onClick={() => toggleSection('animation')}
                            >
                                {isSectionCollapsed('animation') ? '▸' : '▾'} 入场动画
                            </button>
                        </div>
                        {!isSectionCollapsed('animation') && (
                            <>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">动画类型</label>
                                    <select
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        value={String(selectedComponent.config.animationType ?? 'none')}
                                        onChange={(e) => handleConfigChange('animationType', e.target.value)}
                                    >
                                        <option value="none">无</option>
                                        <option value="fadeIn">淡入</option>
                                        <option value="slideUp">上滑进入</option>
                                        <option value="slideDown">下滑进入</option>
                                        <option value="slideLeft">左滑进入</option>
                                        <option value="slideRight">右滑进入</option>
                                        <option value="zoomIn">缩放进入</option>
                                        <option value="bounceIn">弹性进入</option>
                                        <option value="rotateIn">旋转进入</option>
                                    </select>
                                </div>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">时长(ms)</label>
                                    <input
                                        type="number"
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        min={100}
                                        max={5000}
                                        step={100}
                                        value={Number(selectedComponent.config.animationDuration ?? 600)}
                                        onChange={(e) => handleConfigChange('animationDuration', Number(e.target.value))}
                                    />
                                </div>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">延迟(ms)</label>
                                    <input
                                        type="number"
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        min={0}
                                        max={10000}
                                        step={100}
                                        value={Number(selectedComponent.config.animationDelay ?? 0)}
                                        onChange={(e) => handleConfigChange('animationDelay', Number(e.target.value))}
                                    />
                                </div>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">缓动函数</label>
                                    <select
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        value={String(selectedComponent.config.animationEasing ?? 'ease')}
                                        onChange={(e) => handleConfigChange('animationEasing', e.target.value)}
                                    >
                                        <option value="ease">ease</option>
                                        <option value="linear">linear</option>
                                        <option value="ease-in">ease-in</option>
                                        <option value="ease-out">ease-out</option>
                                        <option value="ease-in-out">ease-in-out</option>
                                        <option value="cubic-bezier(0.34,1.56,0.64,1)">弹性</option>
                                    </select>
                                </div>
                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6, marginTop: 8 }}>
                                    <button
                                        type="button"
                                        className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
                                        title="按位置自动设置递增延迟（从上到下、从左到右）"
                                        onClick={() => {
                                            const sorted = [...config.components]
                                                .filter(c => c.visible && String(c.config.animationType ?? 'none') !== 'none')
                                                .sort((a, b) => a.y !== b.y ? a.y - b.y : a.x - b.x);
                                            const nextComponents = config.components.map(c => {
                                                const idx = sorted.findIndex(s => s.id === c.id);
                                                if (idx >= 0) return { ...c, config: { ...c.config, animationDelay: idx * 150 } };
                                                return c;
                                            });
                                            updateConfig({ components: nextComponents });
                                        }}
                                    >
                                        自动编排延迟
                                    </button>
                                    <button
                                        type="button"
                                        className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
                                        onClick={() => {
                                            const nextComponents = config.components.map(c => {
                                                if (String(c.config.animationType ?? 'none') !== 'none') {
                                                    return { ...c, config: { ...c.config, animationDelay: 0 } };
                                                }
                                                return c;
                                            });
                                            updateConfig({ components: nextComponents });
                                        }}
                                    >
                                        清除所有延迟
                                    </button>
                                </div>
                                <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginTop: 4, lineHeight: 1.45 }}>
                                    入场动画仅在预览和运行时生效，设计器中不播放。
                                </div>
                            </>
                        )}
                    </div>
                )}
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
                // 不用 `??`：否则用户显式设的 0 / '' / false 会被 defaultValue 覆盖。
                const hasExplicitValue = Object.prototype.hasOwnProperty.call(component.config, key)
                    && component.config[key] !== undefined;
                const value = hasExplicitValue ? component.config[key] : field?.defaultValue;
                const description = String(field?.description || '').trim();
                const descriptionNode = description ? (
                    <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginTop: 4, lineHeight: 1.45 }}>
                        {description}
                    </div>
                ) : null;
                if (field.type === 'boolean') {
                    return (
                        <div className="property-row flex items-center mb-3" key={key}>
                            <label className="property-label w-20 text-xs text-text-secondary">{label}</label>
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
                        <div className="property-row flex items-center mb-3" key={key}>
                            <label className="property-label w-20 text-xs text-text-secondary">{label}</label>
                            <div style={{ flex: 1 }}>
                                <input
                                    type="number"
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
                        <div className="property-row flex items-center mb-3" key={key}>
                            <label className="property-label w-20 text-xs text-text-secondary">{label}</label>
                            <div style={{ flex: 1 }}>
                                <select
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
                        <div className="property-row flex items-center mb-3" key={key}>
                            <label className="property-label w-20 text-xs text-text-secondary">{label}</label>
                            <div style={{ flex: 1 }}>
                                <input
                                    type="color"
                                    className="property-color-input w-8 h-7 border border-border-default rounded cursor-pointer p-0"
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
                        <div className="property-row flex items-center mb-3" key={key}>
                            <label className="property-label w-20 text-xs text-text-secondary">{label}</label>
                            <div style={{ flex: 1 }}>
                                <button
                                    type="button"
                                    className="header-btn inline-flex items-center gap-1.5 min-h-8 rounded-md border border-border-default bg-surface-card text-text-primary px-3 text-xs cursor-pointer hover:border-brand hover:bg-brand/10 disabled:opacity-40 disabled:cursor-not-allowed"
                                    onClick={() => {
                                        const input = window.prompt(`${label} (${isArray ? 'JSON数组' : 'JSON对象'})`, snapshot);
                                        if (input == null) return;
                                        try {
                                            const parsed = JSON.parse(input);
                                            if (isArray && !Array.isArray(parsed)) {
                                                toast.error(`${label} 需要是 JSON 数组`);
                                                return;
                                            }
                                            if (!isArray && (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed))) {
                                                toast.error(`${label} 需要是 JSON 对象`);
                                                return;
                                            }
                                            onChange(key, parsed);
                                        } catch {
                                            toast.error(`${label} JSON 格式错误`);
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
                    <div className="property-row flex items-center mb-3" key={key}>
                        <label className="property-label w-20 text-xs text-text-secondary">{label}</label>
                        <div style={{ flex: 1 }}>
                            <input
                                type="text"
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">类型</label>
                <select
                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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

            {dsType === 'static' && (
                <StaticDataEditor key={component.id} component={component} updateComponent={updateComponent} />
            )}

            {dsType === 'card' && (
                <>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">Card</label>
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
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">URL</label>
                        <input
                            type="text"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
                            placeholder="/bi/api/card/1/query 或 https://..."
                        />
                    </div>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">方法</label>
                        <select
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">Body</label>
                        <textarea
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">数据库</label>
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
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">数据库ID(手工)</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">SQL</label>
                        <textarea
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">参数提取</label>
                        <button
                            type="button"
                            className="property-btn-small inline-flex items-center justify-center px-3 py-1.5 min-h-8 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
                            onClick={() => {
                                const names = extractSqlTemplateParameterNames(sqlConfig?.query ?? '');
                                if (names.length === 0) {
                                    message.info('未识别到 SQL 参数，占位符示例：{{day}} 或 ${day}');
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
                    <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginTop: -2 }}>
                        自动识别 &#123;&#123;param&#125;&#125; / $&#123;param&#125; 占位符并生成参数绑定。
                    </div>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">最大行数</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">超时(秒)</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">QueryBody(JSON)</label>
                        <textarea
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">Card</label>
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
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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


function renderInteractionConfig(
    component: ScreenComponent,
    globalVariables: ScreenGlobalVariable[],
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void,
    options?: { embedded?: boolean },
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
    const sourcePathCandidates = (() => {
        const t = component.type;
        if (t === 'pie-chart' || t === 'funnel-chart') return ['name', 'value', 'percent', 'data.name'];
        if (t === 'map-chart') return ['name', 'data.name', 'data.value', 'value'];
        if (t === 'table' || t === 'scroll-board') return ['row[0]', 'row[1]', 'row[2]', 'name', 'value'];
        if (t === 'scatter-chart') return ['name', 'value', 'data[0]', 'data[1]', 'seriesName'];
        if (t === 'treemap-chart' || t === 'sunburst-chart') return ['name', 'value', 'data.name', 'treePathInfo'];
        if (t === 'radar-chart') return ['name', 'seriesName', 'value', 'data.name'];
        return ['name', 'seriesName', 'value', 'data.name', 'data.value', 'data.code'];
    })();

    const setInteraction = (next: typeof interaction) => {
        updateComponent(component.id, { interaction: next });
    };

    const updateMapping = (index: number, patch: Partial<ComponentInteractionMapping>) => {
        const next = [...mappings];
        next[index] = { ...next[index], ...patch };
        setInteraction({ ...interaction, mappings: next });
    };

    const content = (
        <>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">启用点击联动</label>
                <input
                    type="checkbox"
                    checked={interaction.enabled ?? false}
                    onChange={(e) => setInteraction({ ...interaction, enabled: e.target.checked })}
                />
            </div>

            {interaction.enabled && (
                <>
                    {globalVariables.length === 0 && (
                        <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginBottom: 8 }}>
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
                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">目标变量</label>
                                <select
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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

                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">取值路径</label>
                                <input
                                    list={`interaction-source-path-${index}`}
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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

                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">值转换</label>
                                <select
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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

                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">默认值</label>
                                <input
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                    value={mapping.fallbackValue || ''}
                                    onChange={(e) => updateMapping(index, { fallbackValue: e.target.value })}
                                    placeholder="取值为空时写入该值"
                                />
                            </div>

                            <button
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                onClick={() => setInteraction({ ...interaction, mappings: mappings.filter((_, i) => i !== index) })}
                                style={{ width: '100%', cursor: 'pointer', textAlign: 'center', color: '#ef4444' }}
                            >
                                删除联动规则
                            </button>
                        </div>
                    ))}

                    <button
                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
                    <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginTop: 6, lineHeight: 1.5 }}>
                        支持自定义路径，例如 <code>data.code</code>；可对值做数值/大小写转换，并设置空值回退。
                    </div>

                    <div className="property-row flex items-center mb-3" style={{ marginTop: 10 }}>
                        <label className="property-label w-20 text-xs text-text-secondary">启用点击跳转</label>
                        <input
                            type="checkbox"
                            checked={interaction.jumpEnabled === true}
                            onChange={(e) => setInteraction({ ...interaction, jumpEnabled: e.target.checked })}
                        />
                    </div>

                    {interaction.jumpEnabled === true && (
                        <>
                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">跳转链接模板</label>
                                <input
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                    value={interaction.jumpUrlTemplate || ''}
                                    onChange={(e) => setInteraction({ ...interaction, jumpUrlTemplate: e.target.value })}
                                    placeholder="https://host/path?name={{name}}&value={{value}}"
                                />
                            </div>
                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">打开方式</label>
                                <select
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
                            <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginTop: 4 }}>
                                支持占位符: {'{{name}} / {{seriesName}} / {{value}} / {{data.name}}'}
                            </div>
                        </>
                    )}
                </>
            )}
        </>
    );

    if (options?.embedded) {
        return content;
    }
    return (
        <div className="property-section py-3 border-b border-border-default">
            <div className="property-section-title text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2">联动配置</div>
            {content}
        </div>
    );
}

function renderActionConfig(
    component: ScreenComponent,
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void,
    options?: { embedded?: boolean },
) {
    if (!ACTION_COMPONENT_TYPES.has(component.type)) {
        return null;
    }

    const actions = component.actions ?? [];
    const sourcePathCandidates = getActionSourcePathCandidates(component.type);

    const setActions = (next: ScreenComponentAction[]) => {
        updateComponent(component.id, { actions: next });
    };

    const updateAction = (index: number, patch: Partial<ScreenComponentAction>) => {
        const next = [...actions];
        next[index] = { ...next[index], ...patch };
        setActions(next);
    };

    const updateMappings = (index: number, nextMappings: ComponentInteractionMapping[]) => {
        updateAction(index, { mappings: nextMappings });
    };

    const content = (
        <>
            {actions.length === 0 ? (
                <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginBottom: 8 }}>
                    当前组件还没有动作入口。适合配置详情面板、跳转、变量写入或意图事件。
                </div>
            ) : null}

            {actions.map((action, index) => {
                const actionType = action.type || 'set-variable';
                const mappings = action.mappings ?? [];
                const showMappings = actionType === 'set-variable' || actionType === 'jump-url' || actionType === 'emit-intent';
                return (
                    <div
                        key={`action-${index}`}
                        className="border border-border-default rounded-[10px] p-2.5 mb-2.5 bg-surface-muted/40"
                    >
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">动作标题</label>
                            <input
                                type="text"
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                value={action.label || ''}
                                onChange={(e) => updateAction(index, { label: e.target.value })}
                                placeholder="查看详情 / 发起协调 / 跳转周报"
                            />
                        </div>

                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">动作类型</label>
                            <select
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                value={actionType}
                                onChange={(e) => updateAction(index, { type: e.target.value as ScreenComponentAction['type'] })}
                            >
                                <option value="set-variable">写入变量</option>
                                <option value="drill-down">下钻</option>
                                <option value="drill-up">上卷返回</option>
                                <option value="jump-url">页面跳转</option>
                                <option value="open-panel">打开详情面板</option>
                                <option value="emit-intent">发出意图事件</option>
                            </select>
                        </div>

                        {showMappings ? (
                            <>
                                {mappings.map((mapping, mappingIndex) => (
                                    <div
                                        key={`action-${index}-mapping-${mappingIndex}`}
                                        className="border border-dashed border-border-default rounded-lg p-2 mb-2"
                                    >
                                        <div className="property-row flex items-center mb-3">
                                            <label className="property-label w-20 text-xs text-text-secondary">目标变量</label>
                                            <input
                                                type="text"
                                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                                value={mapping.variableKey || ''}
                                                onChange={(e) => {
                                                    const next = [...mappings];
                                                    next[mappingIndex] = { ...next[mappingIndex], variableKey: e.target.value };
                                                    updateMappings(index, next);
                                                }}
                                                placeholder="projectId"
                                            />
                                        </div>
                                        <div className="property-row flex items-center mb-3">
                                            <label className="property-label w-20 text-xs text-text-secondary">取值路径</label>
                                            <input
                                                list={`action-source-path-${index}-${mappingIndex}`}
                                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                                value={mapping.sourcePath || ''}
                                                onChange={(e) => {
                                                    const next = [...mappings];
                                                    next[mappingIndex] = { ...next[mappingIndex], sourcePath: e.target.value };
                                                    updateMappings(index, next);
                                                }}
                                                placeholder="name / row[0] / data.owner"
                                            />
                                            <datalist id={`action-source-path-${index}-${mappingIndex}`}>
                                                {sourcePathCandidates.map((item) => (
                                                    <option key={item} value={item} />
                                                ))}
                                            </datalist>
                                        </div>
                                        <div className="property-row flex items-center mb-3">
                                            <label className="property-label w-20 text-xs text-text-secondary">值转换</label>
                                            <select
                                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                                value={String(mapping.transform || 'raw')}
                                                onChange={(e) => {
                                                    const next = [...mappings];
                                                    next[mappingIndex] = { ...next[mappingIndex], transform: e.target.value as ComponentInteractionMapping['transform'] };
                                                    updateMappings(index, next);
                                                }}
                                            >
                                                <option value="raw">原值</option>
                                                <option value="string">字符串</option>
                                                <option value="number">数值</option>
                                                <option value="lowercase">转小写</option>
                                                <option value="uppercase">转大写</option>
                                            </select>
                                        </div>
                                        <button
                                            type="button"
                                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                            onClick={() => updateMappings(index, mappings.filter((_, i) => i !== mappingIndex))}
                                            style={{ width: '100%', textAlign: 'center', cursor: 'pointer', color: '#ef4444' }}
                                        >
                                            删除映射
                                        </button>
                                    </div>
                                ))}

                                <button
                                    type="button"
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                    onClick={() => updateMappings(index, [
                                        ...mappings,
                                        { variableKey: '', sourcePath: 'name', transform: 'raw', fallbackValue: '' },
                                    ])}
                                    style={{ width: '100%', textAlign: 'center', cursor: 'pointer', color: '#2563eb' }}
                                >
                                    + 添加变量映射
                                </button>
                            </>
                        ) : null}

                        {actionType === 'jump-url' ? (
                            <>
                                <ScreenJumpPicker
                                    value={action.jumpUrlTemplate || ''}
                                    onChange={(url) => updateAction(index, { jumpUrlTemplate: url })}
                                />
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">打开方式</label>
                                    <select
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        value={action.jumpOpenMode || 'new-tab'}
                                        onChange={(e) => updateAction(index, { jumpOpenMode: e.target.value === 'self' ? 'self' : 'new-tab' })}
                                    >
                                        <option value="new-tab">新窗口</option>
                                        <option value="self">当前窗口</option>
                                    </select>
                                </div>
                            </>
                        ) : null}

                        {actionType === 'open-panel' ? (
                            <>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">面板标题模板</label>
                                    <input
                                        type="text"
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        value={action.panelTitle || ''}
                                        onChange={(e) => updateAction(index, { panelTitle: e.target.value })}
                                        placeholder="项目 {{name}}"
                                    />
                                </div>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">面板内容模板</label>
                                    <textarea
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        rows={4}
                                        value={action.panelBodyTemplate || ''}
                                        onChange={(e) => updateAction(index, { panelBodyTemplate: e.target.value })}
                                        placeholder={'负责人：{{责任人}}\n状态：{{状态}}\n建议：发起协调'}
                                    />
                                </div>
                            </>
                        ) : null}

                        {actionType === 'emit-intent' ? (
                            <>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">意图名称</label>
                                    <input
                                        type="text"
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        value={action.intentName || ''}
                                        onChange={(e) => updateAction(index, { intentName: e.target.value })}
                                        placeholder="project.follow-up"
                                    />
                                </div>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">意图负载模板</label>
                                    <textarea
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        rows={4}
                                        value={action.intentPayloadTemplate || ''}
                                        onChange={(e) => updateAction(index, { intentPayloadTemplate: e.target.value })}
                                        placeholder={'{"project":"{{name}}","owner":"{{责任人}}"}'}
                                    />
                                </div>
                            </>
                        ) : null}

                        {(actionType === 'drill-down' || actionType === 'drill-up') ? (
                            <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginTop: 6 }}>
                                {actionType === 'drill-down'
                                    ? '运行态会复用当前组件的下钻链路，并使用点击值推进到下一层。'
                                    : '运行态会从当前钻取层级返回上一层。'}
                            </div>
                        ) : null}

                        <button
                            type="button"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            onClick={() => setActions(actions.filter((_, i) => i !== index))}
                            style={{ width: '100%', textAlign: 'center', cursor: 'pointer', color: '#ef4444', marginTop: 8 }}
                        >
                            删除动作
                        </button>
                    </div>
                );
            })}

            <button
                type="button"
                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                onClick={() => setActions([
                    ...actions,
                    { type: 'open-panel', label: '查看详情', panelTitle: '{{name}}', panelBodyTemplate: '' },
                ])}
                style={{ width: '100%', textAlign: 'center', cursor: 'pointer', color: '#2563eb' }}
            >
                + 添加动作入口
            </button>
        </>
    );

    if (options?.embedded) {
        return content;
    }
    return (
        <div className="property-section py-3 border-b border-border-default">
            <div className="property-section-title text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2">动作入口</div>
            {content}
        </div>
    );
}

function renderDrillDownConfig(
    component: ScreenComponent,
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void,
    options?: { embedded?: boolean },
) {
    const { type, dataSource, drillDown } = component;
    const cardId = dataSource?.type === 'card' ? dataSource.cardConfig?.cardId : undefined;

    // Show for drill-capable components backed by a valid card data source.
    if (!DRILL_CONFIGURABLE_TYPES.has(type) || dataSource?.type !== 'card' || !cardId || cardId <= 0) {
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

    const content = (
        <>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">启用下钻</label>
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
                                color: 'var(--color-text-secondary)',
                            }}>
                                <span>层级 {i + 1}</span>
                                <button
                                    className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
                                    onClick={() => removeLevel(i)}
                                    style={{
                                        background: 'none', border: 'none',
                                        color: '#ef4444', cursor: 'pointer', fontSize: 11,
                                    }}
                                >
                                    删除
                                </button>
                            </div>
                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">Card</label>
                                <CardIdPicker
                                    value={level.cardId || 0}
                                    onChange={(cardId) => updateLevel(i, 'cardId', cardId)}
                                    placeholder="-- 下钻目标 --"
                                />
                            </div>
                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">参数名</label>
                                <input
                                    type="text"
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                    value={level.paramName}
                                    onChange={(e) => updateLevel(i, 'paramName', e.target.value)}
                                    placeholder="如: region"
                                />
                            </div>
                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">标签</label>
                                <input
                                    type="text"
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                    value={level.label}
                                    onChange={(e) => updateLevel(i, 'label', e.target.value)}
                                    placeholder="如: 地区"
                                />
                            </div>
                        </div>
                    ))}

                    <button
                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
        </>
    );

    if (options?.embedded) {
        return content;
    }
    return (
        <div className="property-section py-3 border-b border-border-default">
            <div className="property-section-title text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2">下钻配置</div>
            {content}
        </div>
    );
}

