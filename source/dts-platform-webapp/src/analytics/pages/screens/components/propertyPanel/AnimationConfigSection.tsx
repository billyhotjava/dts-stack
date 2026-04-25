import type { ScreenComponent, ScreenConfig } from '../../types';

interface AnimationConfigSectionOptions {
    selectedComponent: ScreenComponent;
    components: ScreenComponent[];
    updateConfig: (updates: Partial<ScreenConfig>) => void;
    handleConfigChange: (key: string, value: unknown) => void;
    isSectionCollapsed: (sectionKey: string) => boolean;
    toggleSection: (sectionKey: string) => void;
}

export function renderAnimationConfig({
    selectedComponent,
    components,
    updateConfig,
    handleConfigChange,
    isSectionCollapsed,
    toggleSection,
}: AnimationConfigSectionOptions) {
    const isCollapsed = isSectionCollapsed('animation');

    return (
        <div className="property-section py-3 border-b border-border-default" style={{ marginTop: 8 }}>
            <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                <button
                    type="button"
                    className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                    onClick={() => toggleSection('animation')}
                >
                    {isCollapsed ? '▸' : '▾'} 入场动画
                </button>
            </div>
            {!isCollapsed && (
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
                                const sorted = [...components]
                                    .filter((component) => component.visible && String(component.config.animationType ?? 'none') !== 'none')
                                    .sort((a, b) => a.y !== b.y ? a.y - b.y : a.x - b.x);
                                const nextComponents = components.map((component) => {
                                    const idx = sorted.findIndex((sortedComponent) => sortedComponent.id === component.id);
                                    if (idx >= 0) {
                                        return {
                                            ...component,
                                            config: { ...component.config, animationDelay: idx * 150 },
                                        };
                                    }
                                    return component;
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
                                const nextComponents = components.map((component) => {
                                    if (String(component.config.animationType ?? 'none') !== 'none') {
                                        return {
                                            ...component,
                                            config: { ...component.config, animationDelay: 0 },
                                        };
                                    }
                                    return component;
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
    );
}
