import type { ScreenComponent } from '../../types';
import { SectionToggle } from './SectionToggle';

interface ComponentAppearanceSectionOptions {
    selectedComponent: ScreenComponent;
    handleConfigChange: (key: string, value: unknown) => void;
    isSectionCollapsed: (sectionKey: string) => boolean;
    toggleSection: (sectionKey: string) => void;
}

export function renderComponentAppearanceConfig({
    selectedComponent,
    handleConfigChange,
    isSectionCollapsed,
    toggleSection,
}: ComponentAppearanceSectionOptions) {
    const config = selectedComponent.config;
    const isCollapsed = isSectionCollapsed('component-appearance');

    return (
        <div className="property-section py-3 border-b border-border-default">
            <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                <SectionToggle collapsed={isCollapsed} label="组件外观" onToggle={() => toggleSection('component-appearance')} />
            </div>
            {!isCollapsed ? (
                <>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">背景色</label>
                        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                            <input
                                type="color"
                                className="property-color-input w-8 h-7 border border-border-default rounded cursor-pointer p-0"
                                value={String(config.componentBgColor || 'transparent') === 'transparent' ? '#000000' : String(config.componentBgColor || '#000000')}
                                onChange={(e) => handleConfigChange('componentBgColor', e.target.value)}
                            />
                            <input
                                type="text"
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                style={{ flex: 1 }}
                                value={String(config.componentBgColor || '')}
                                onChange={(e) => handleConfigChange('componentBgColor', e.target.value)}
                                placeholder="transparent"
                            />
                            {config.componentBgColor ? (
                                <button
                                    type="button"
                                    className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
                                    onClick={() => handleConfigChange('componentBgColor', '')}
                                >
                                    清除
                                </button>
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
                                value={Number(config.componentBgOpacity ?? 100)}
                                onChange={(e) => handleConfigChange('componentBgOpacity', Number(e.target.value))}
                                style={{ flex: 1 }}
                            />
                            <span style={{ fontSize: 11, minWidth: 32, textAlign: 'right' }}>{Number(config.componentBgOpacity ?? 100)}%</span>
                        </div>
                    </div>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">圆角</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            value={Number(config.componentBorderRadius ?? 0)}
                            onChange={(e) => handleConfigChange('componentBorderRadius', Math.max(0, Number(e.target.value) || 0))}
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
                                value={Number(config.componentBorderWidth ?? 0)}
                                onChange={(e) => handleConfigChange('componentBorderWidth', Math.max(0, Number(e.target.value) || 0))}
                                min={0}
                                max={20}
                                placeholder="0"
                            />
                            <input
                                type="color"
                                className="property-color-input w-8 h-7 border border-border-default rounded cursor-pointer p-0"
                                value={String(config.componentBorderColor || '#ffffff')}
                                onChange={(e) => handleConfigChange('componentBorderColor', e.target.value)}
                            />
                            <select
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                style={{ flex: 1 }}
                                value={String(config.componentBorderStyle || 'solid')}
                                onChange={(e) => handleConfigChange('componentBorderStyle', e.target.value)}
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
                            value={Number(config.componentPadding ?? 0)}
                            onChange={(e) => handleConfigChange('componentPadding', Math.max(0, Number(e.target.value) || 0))}
                            min={0}
                            max={100}
                            placeholder="0"
                        />
                    </div>
                </>
            ) : null}
        </div>
    );
}
