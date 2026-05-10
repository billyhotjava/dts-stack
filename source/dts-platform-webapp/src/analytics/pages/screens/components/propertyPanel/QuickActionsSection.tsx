import { ArrowDown, ArrowLeft, ArrowRight, ArrowUp } from 'lucide-react';
import type { ChartPreset } from '../../chartPresets';
import { CHART_COMPONENT_TYPES } from '../../chartPresets';
import type { ScreenComponent } from '../../types';
import { SectionToggle } from './SectionToggle';
import type { LayoutClipboardPayload, StyleClipboardPayload } from './types';

export type QuickActionMode = 'core' | 'layout' | 'nudge' | 'clipboard' | 'all';
export type CanvasAlignMode = 'left' | 'right' | 'top' | 'bottom' | 'h-center' | 'v-center';

interface QuickActionsSectionOptions {
    selectedComponent: ScreenComponent;
    quickActionMode: QuickActionMode;
    setQuickActionMode: (mode: QuickActionMode) => void;
    styleClipboard: StyleClipboardPayload | null;
    layoutClipboard: LayoutClipboardPayload | null;
    duplicateCurrentComponent: () => void;
    deleteCurrentComponent: () => void;
    alignToCanvas: (mode: CanvasAlignMode) => void;
    nudgePosition: (dx: number, dy: number) => void;
    nudgeSize: (dw: number, dh: number) => void;
    copyCurrentStyle: () => void;
    applyCopiedStyle: () => void;
    copyLayoutSnapshot: () => void;
    pasteLayoutSnapshot: () => void;
    copyConfigJson: () => Promise<void>;
    pasteConfigJson: () => void;
    clearStyleClipboard: () => void;
    clearLayoutClipboard: () => void;
    applyChartPreset: (preset: ChartPreset) => void;
    isSectionCollapsed: (sectionKey: string) => boolean;
    toggleSection: (sectionKey: string) => void;
}

const buttonClassName = 'property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed';

export function renderQuickActionsConfig({
    selectedComponent,
    quickActionMode,
    setQuickActionMode,
    styleClipboard,
    layoutClipboard,
    duplicateCurrentComponent,
    deleteCurrentComponent,
    alignToCanvas,
    nudgePosition,
    nudgeSize,
    copyCurrentStyle,
    applyCopiedStyle,
    copyLayoutSnapshot,
    pasteLayoutSnapshot,
    copyConfigJson,
    pasteConfigJson,
    clearStyleClipboard,
    clearLayoutClipboard,
    applyChartPreset,
    isSectionCollapsed,
    toggleSection,
}: QuickActionsSectionOptions) {
    const isCollapsed = isSectionCollapsed('quick-actions');
    const showGroup = (group: Exclude<QuickActionMode, 'all'>) => (
        quickActionMode === 'all' || quickActionMode === group
    );
    const filterButtonStyle = (mode: QuickActionMode) => (
        quickActionMode === mode
            ? { borderColor: 'var(--color-primary)', background: 'var(--color-primary-light)' }
            : undefined
    );

    return (
        <div className="property-section py-3 border-b border-border-default">
            <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                <SectionToggle collapsed={isCollapsed} label="快捷操作" onToggle={() => toggleSection('quick-actions')} />
            </div>
            {!isCollapsed ? (
                <>
                    <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 6 }}>
                        <button type="button" className={buttonClassName} style={filterButtonStyle('core')} onClick={() => setQuickActionMode('core')}>常用</button>
                        <button type="button" className={buttonClassName} style={filterButtonStyle('layout')} onClick={() => setQuickActionMode('layout')}>布局</button>
                        <button type="button" className={buttonClassName} style={filterButtonStyle('nudge')} onClick={() => setQuickActionMode('nudge')}>微调</button>
                        <button type="button" className={buttonClassName} style={filterButtonStyle('clipboard')} onClick={() => setQuickActionMode('clipboard')}>剪贴板</button>
                        <button type="button" className={buttonClassName} style={filterButtonStyle('all')} onClick={() => setQuickActionMode('all')}>全部</button>
                    </div>
                    {showGroup('core') ? (
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 6 }}>
                            <button type="button" className={buttonClassName} onClick={duplicateCurrentComponent}>复制组件</button>
                            <button type="button" className={buttonClassName} onClick={deleteCurrentComponent}>删除组件</button>
                            <button type="button" className={buttonClassName} onClick={() => alignToCanvas('h-center')}>水平居中</button>
                            <button type="button" className={buttonClassName} onClick={() => alignToCanvas('v-center')}>垂直居中</button>
                        </div>
                    ) : null}
                    {showGroup('layout') ? (
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 6, marginTop: 6 }}>
                            <button type="button" className={buttonClassName} onClick={() => alignToCanvas('left')}>贴左</button>
                            <button type="button" className={buttonClassName} onClick={() => alignToCanvas('right')}>贴右</button>
                            <button type="button" className={buttonClassName} onClick={() => alignToCanvas('top')}>贴上</button>
                            <button type="button" className={buttonClassName} onClick={() => alignToCanvas('bottom')}>贴下</button>
                        </div>
                    ) : null}
                    {showGroup('nudge') ? (
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 6, marginTop: 6 }}>
                            <button type="button" className={buttonClassName} onClick={() => nudgePosition(-1, 0)} title="X -1"><ArrowLeft size={13} aria-hidden="true" />1</button>
                            <button type="button" className={buttonClassName} onClick={() => nudgePosition(1, 0)} title="X +1"><ArrowRight size={13} aria-hidden="true" />1</button>
                            <button type="button" className={buttonClassName} onClick={() => nudgePosition(0, -1)} title="Y -1"><ArrowUp size={13} aria-hidden="true" />1</button>
                            <button type="button" className={buttonClassName} onClick={() => nudgePosition(0, 1)} title="Y +1"><ArrowDown size={13} aria-hidden="true" />1</button>
                            <button type="button" className={buttonClassName} onClick={() => nudgePosition(-10, 0)} title="X -10"><ArrowLeft size={13} aria-hidden="true" />10</button>
                            <button type="button" className={buttonClassName} onClick={() => nudgePosition(10, 0)} title="X +10"><ArrowRight size={13} aria-hidden="true" />10</button>
                            <button type="button" className={buttonClassName} onClick={() => nudgePosition(0, -10)} title="Y -10"><ArrowUp size={13} aria-hidden="true" />10</button>
                            <button type="button" className={buttonClassName} onClick={() => nudgePosition(0, 10)} title="Y +10"><ArrowDown size={13} aria-hidden="true" />10</button>
                            <button type="button" className={buttonClassName} onClick={() => nudgeSize(-10, 0)} title="宽度 -10">宽-10</button>
                            <button type="button" className={buttonClassName} onClick={() => nudgeSize(10, 0)} title="宽度 +10">宽+10</button>
                            <button type="button" className={buttonClassName} onClick={() => nudgeSize(0, -10)} title="高度 -10">高-10</button>
                            <button type="button" className={buttonClassName} onClick={() => nudgeSize(0, 10)} title="高度 +10">高+10</button>
                        </div>
                    ) : null}
                    {showGroup('clipboard') ? (
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 6, marginTop: 6 }}>
                            <button type="button" className={buttonClassName} onClick={copyCurrentStyle}>复制样式</button>
                            <button type="button" className={buttonClassName} onClick={applyCopiedStyle}>粘贴样式</button>
                            <button type="button" className={buttonClassName} onClick={copyLayoutSnapshot}>复制布局</button>
                            <button type="button" className={buttonClassName} onClick={pasteLayoutSnapshot}>粘贴布局</button>
                            <button type="button" className={buttonClassName} onClick={() => { void copyConfigJson(); }}>复制配置JSON</button>
                            <button type="button" className={buttonClassName} onClick={pasteConfigJson}>粘贴配置JSON</button>
                            <button type="button" className={buttonClassName} onClick={clearStyleClipboard}>清空样式板</button>
                            <button type="button" className={buttonClassName} onClick={clearLayoutClipboard}>清空布局板</button>
                        </div>
                    ) : null}
                    {CHART_COMPONENT_TYPES.has(selectedComponent.type) ? (
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 6, marginTop: 6 }}>
                            <button type="button" className={buttonClassName} onClick={() => applyChartPreset('business')} title="适合白底商务大屏">商务预设</button>
                            <button type="button" className={buttonClassName} onClick={() => applyChartPreset('compact')} title="适合小尺寸组件">紧凑预设</button>
                            <button type="button" className={buttonClassName} onClick={() => applyChartPreset('clear')} title="恢复默认可读策略">恢复预设</button>
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
    );
}
