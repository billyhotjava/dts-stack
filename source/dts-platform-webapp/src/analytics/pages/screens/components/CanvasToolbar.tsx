import { useMemo } from 'react';
import {
    AlignCenterHorizontal,
    AlignCenterVertical,
    AlignEndHorizontal,
    AlignEndVertical,
    AlignHorizontalSpaceAround,
    AlignStartHorizontal,
    AlignStartVertical,
    AlignVerticalSpaceAround,
    Grid3x3,
    Group,
    Minus,
    Paintbrush,
    Plus,
    Redo2,
    Undo2,
    Ungroup,
} from 'lucide-react';
import { useScreen } from '../ScreenContext';

const ZOOM_OPTIONS = [50, 75, 100, 125, 150, 200];
const ICON_SIZE = 14;

export function CanvasToolbar() {
    const {
        state,
        dispatch,
        undo,
        redo,
        canUndo,
        canRedo,
        alignSelected,
        distributeSelected,
        groupSelected,
        ungroupSelected,
        formatSource,
        pickFormatSource,
        applyFormatToSelected,
    } = useScreen();
    const { selectedIds, zoom, showGrid } = state;
    const canAlign = selectedIds.length >= 2;
    const canDistribute = selectedIds.length >= 3;
    const canGroup = selectedIds.length >= 2;
    const canUngroup = selectedIds.length >= 1;
    const zoomSelectOptions = useMemo(() => {
        const current = Math.round(Number(zoom) || 100);
        const set = new Set(ZOOM_OPTIONS);
        if (!set.has(current)) {
            set.add(current);
        }
        return Array.from(set).sort((a, b) => a - b);
    }, [zoom]);

    const handleZoomChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
        dispatch({ type: 'SET_ZOOM', payload: Number(e.target.value) });
    };
    const clampZoom = (value: number) => Math.min(300, Math.max(25, Math.round(value)));
    const handleZoomStep = (delta: number) => {
        const next = clampZoom((Number(zoom) || 100) + delta);
        dispatch({ type: 'SET_ZOOM', payload: next });
    };

    return (
        <div className="min-h-[44px] bg-[var(--color-surface-secondary)] border-b border-[var(--color-border)] flex items-center px-2 gap-1 overflow-hidden shrink-0">
            {/* History */}
            <div className="flex items-center gap-0.5 px-1 shrink-0" title="历史操作">
                <button className="toolbar-btn" onClick={undo} disabled={!canUndo} title="撤销 (Ctrl+Z)">
                    <Undo2 size={ICON_SIZE} />
                </button>
                <button className="toolbar-btn" onClick={redo} disabled={!canRedo} title="重做 (Ctrl+Y)">
                    <Redo2 size={ICON_SIZE} />
                </button>
            </div>

            <div className="w-px h-6 bg-[var(--color-border)] mx-0.5 shrink-0" />

            {/* Alignment */}
            <div className="flex items-center gap-0.5 px-1 shrink-0" title="对齐">
                <button className="toolbar-btn toolbar-btn--icon" onClick={() => alignSelected('left')} disabled={!canAlign} title="左对齐">
                    <AlignStartVertical size={ICON_SIZE} />
                </button>
                <button className="toolbar-btn toolbar-btn--icon" onClick={() => alignSelected('h-center')} disabled={!canAlign} title="水平居中">
                    <AlignCenterVertical size={ICON_SIZE} />
                </button>
                <button className="toolbar-btn toolbar-btn--icon" onClick={() => alignSelected('right')} disabled={!canAlign} title="右对齐">
                    <AlignEndVertical size={ICON_SIZE} />
                </button>
                <button className="toolbar-btn toolbar-btn--icon" onClick={() => alignSelected('top')} disabled={!canAlign} title="顶对齐">
                    <AlignStartHorizontal size={ICON_SIZE} />
                </button>
                <button className="toolbar-btn toolbar-btn--icon" onClick={() => alignSelected('v-center')} disabled={!canAlign} title="垂直居中">
                    <AlignCenterHorizontal size={ICON_SIZE} />
                </button>
                <button className="toolbar-btn toolbar-btn--icon" onClick={() => alignSelected('bottom')} disabled={!canAlign} title="底对齐">
                    <AlignEndHorizontal size={ICON_SIZE} />
                </button>
            </div>

            <div className="w-px h-6 bg-[var(--color-border)] mx-0.5 shrink-0" />

            {/* Distribution & Grouping */}
            <div className="flex items-center gap-0.5 px-1 shrink-0" title="分布与编组">
                <button className="toolbar-btn" onClick={() => distributeSelected('horizontal')} disabled={!canDistribute} title="水平等距分布 (3+)">
                    <AlignHorizontalSpaceAround size={ICON_SIZE} />
                </button>
                <button className="toolbar-btn" onClick={() => distributeSelected('vertical')} disabled={!canDistribute} title="垂直等距分布 (3+)">
                    <AlignVerticalSpaceAround size={ICON_SIZE} />
                </button>
                <button className="toolbar-btn" onClick={groupSelected} disabled={!canGroup} title="编组 (2+)">
                    <Group size={ICON_SIZE} />
                </button>
                <button className="toolbar-btn" onClick={ungroupSelected} disabled={!canUngroup} title="解组">
                    <Ungroup size={ICON_SIZE} />
                </button>
            </div>

            <div className="w-px h-6 bg-[var(--color-border)] mx-0.5 shrink-0" />

            {/* Format painter */}
            <div className="flex items-center gap-0.5 px-1 shrink-0" title="格式刷">
                <button
                    className={`toolbar-btn ${formatSource ? 'toolbar-btn--active' : ''}`}
                    onClick={pickFormatSource}
                    disabled={selectedIds.length !== 1}
                    title="拾取样式：选中一个组件后点击此按钮"
                >
                    <Paintbrush size={ICON_SIZE} />
                </button>
                <button
                    className="toolbar-btn"
                    onClick={applyFormatToSelected}
                    disabled={!formatSource || selectedIds.length === 0}
                    title="应用样式：选中目标组件后点击此按钮"
                >
                    <Paintbrush size={ICON_SIZE} />
                </button>
            </div>

            <div className="w-px h-6 bg-[var(--color-border)] mx-0.5 shrink-0" />

            {/* View controls */}
            <div className="flex items-center gap-0.5 px-1 shrink-0" title="视图控制">
                <button
                    className={`toolbar-btn ${showGrid ? 'toolbar-btn--active' : ''}`}
                    onClick={() => dispatch({ type: 'TOGGLE_GRID' })}
                    title="网格 (G)"
                >
                    <Grid3x3 size={ICON_SIZE} />
                </button>
            </div>

            {/* Spacer */}
            <div className="flex-1" />

            {/* Zoom */}
            <div className="flex items-center gap-0.5 px-1 shrink-0">
                <button className="toolbar-btn" onClick={() => handleZoomStep(-25)} title="缩小 25%">
                    <Minus size={ICON_SIZE} />
                </button>
                <select className="zoom-select" value={zoom} onChange={handleZoomChange}>
                    {zoomSelectOptions.map((z) => (
                        <option key={z} value={z}>{z}%</option>
                    ))}
                </select>
                <button className="toolbar-btn" onClick={() => handleZoomStep(25)} title="放大 25%">
                    <Plus size={ICON_SIZE} />
                </button>
            </div>

            <div className="w-px h-6 bg-[var(--color-border)] mx-0.5 shrink-0" />

            {/* Status - compact */}
            <div className="flex items-center gap-1 px-1 shrink min-w-0">
                <span className="text-[11px] text-[var(--color-text-tertiary)] whitespace-nowrap">{state.config.width}×{state.config.height}</span>
                {selectedIds.length > 0 && <span className="text-[11px] text-[var(--color-text-tertiary)] whitespace-nowrap">选{selectedIds.length}</span>}
            </div>
        </div>
    );
}
