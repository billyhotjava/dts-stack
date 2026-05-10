import type { ScreenComponent } from '../../types';
import { SectionToggle } from './SectionToggle';

interface PositionSizeSectionOptions {
    selectedComponent: ScreenComponent;
    handleChange: (key: string, value: unknown) => void;
    isSectionCollapsed: (sectionKey: string) => boolean;
    toggleSection: (sectionKey: string) => void;
}

function parseNumberInput(value: string, fallback: number, min?: number): number {
    const n = Number(value);
    if (!Number.isFinite(n)) {
        return fallback;
    }
    return typeof min === 'number' ? Math.max(min, n) : n;
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
                <SectionToggle collapsed={isCollapsed} label="位置与尺寸" onToggle={() => toggleSection('position-size')} />
            </div>
            {!isCollapsed ? (
                <>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">X</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            value={selectedComponent.x}
                            onChange={(e) => handleChange('x', parseNumberInput(e.target.value, selectedComponent.x))}
                        />
                    </div>

                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">Y</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            value={selectedComponent.y}
                            onChange={(e) => handleChange('y', parseNumberInput(e.target.value, selectedComponent.y))}
                        />
                    </div>

                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">宽度</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            min={1}
                            value={selectedComponent.width}
                            onChange={(e) => handleChange('width', parseNumberInput(e.target.value, selectedComponent.width, 1))}
                        />
                    </div>

                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">高度</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            min={1}
                            value={selectedComponent.height}
                            onChange={(e) => handleChange('height', parseNumberInput(e.target.value, selectedComponent.height, 1))}
                        />
                    </div>
                </>
            ) : null}
        </div>
    );
}
