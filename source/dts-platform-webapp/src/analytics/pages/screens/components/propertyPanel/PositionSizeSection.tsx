import type { ScreenComponent } from '../../types';

interface PositionSizeSectionOptions {
    selectedComponent: ScreenComponent;
    handleChange: (key: string, value: unknown) => void;
    isSectionCollapsed: (sectionKey: string) => boolean;
    toggleSection: (sectionKey: string) => void;
}

export function renderPositionSizeConfig({
    selectedComponent,
    handleChange,
    isSectionCollapsed,
    toggleSection,
}: PositionSizeSectionOptions) {
    const isCollapsed = isSectionCollapsed('position-size');

    return (
        <div className="property-section py-3 border-b border-border-default">
            <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                <button
                    type="button"
                    className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                    onClick={() => toggleSection('position-size')}
                >
                    {isCollapsed ? '▸' : '▾'} 位置与尺寸
                </button>
            </div>
            {!isCollapsed ? (
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
    );
}
