import { useMemo, useState } from 'react';
import { useScreen } from '../ScreenContext';

const ZOOM_OPTIONS = [50, 75, 100, 125, 150, 200];

const ARRANGE_OPTIONS = [
    { value: 'group', label: '组合' },
    { value: 'ungroup', label: '解组' },
    { value: 'align-left', label: '左对齐' },
    { value: 'align-h-center', label: '水平居中' },
    { value: 'align-right', label: '右对齐' },
    { value: 'align-top', label: '顶对齐' },
    { value: 'align-v-center', label: '垂直居中' },
    { value: 'align-bottom', label: '底对齐' },
    { value: 'distribute-horizontal', label: '水平分布' },
    { value: 'distribute-vertical', label: '垂直分布' },
] as const;

type ArrangeAction = typeof ARRANGE_OPTIONS[number]['value'];

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
    } = useScreen();
    const { selectedIds, zoom } = state;
    const [arrangeAction, setArrangeAction] = useState<ArrangeAction>('align-left');
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

    const executeArrangeAction = () => {
        if (arrangeAction === 'group') {
            if (!canGroup) return;
            groupSelected();
            return;
        }
        if (arrangeAction === 'ungroup') {
            if (!canUngroup) return;
            ungroupSelected();
            return;
        }
        if (arrangeAction === 'align-left') {
            if (!canAlign) return;
            alignSelected('left');
            return;
        }
        if (arrangeAction === 'align-h-center') {
            if (!canAlign) return;
            alignSelected('h-center');
            return;
        }
        if (arrangeAction === 'align-right') {
            if (!canAlign) return;
            alignSelected('right');
            return;
        }
        if (arrangeAction === 'align-top') {
            if (!canAlign) return;
            alignSelected('top');
            return;
        }
        if (arrangeAction === 'align-v-center') {
            if (!canAlign) return;
            alignSelected('v-center');
            return;
        }
        if (arrangeAction === 'align-bottom') {
            if (!canAlign) return;
            alignSelected('bottom');
            return;
        }
        if (arrangeAction === 'distribute-horizontal') {
            if (!canDistribute) return;
            distributeSelected('horizontal');
            return;
        }
        if (arrangeAction === 'distribute-vertical') {
            if (!canDistribute) return;
            distributeSelected('vertical');
        }
    };

    const canExecuteArrange = (() => {
        if (arrangeAction === 'group') return canGroup;
        if (arrangeAction === 'ungroup') return canUngroup;
        if (arrangeAction.startsWith('distribute')) return canDistribute;
        return canAlign;
    })();

    return (
        <div className="canvas-toolbar">
            {/* Undo/Redo */}
            <div className="toolbar-group">
                <button
                    className="toolbar-btn"
                    onClick={undo}
                    disabled={!canUndo}
                    title="撤销 (Ctrl+Z)"
                >
                    撤销
                </button>
                <button
                    className="toolbar-btn"
                    onClick={redo}
                    disabled={!canRedo}
                    title="重做 (Ctrl+Y)"
                >
                    重做
                </button>
            </div>

            {/* Align / distribute */}
            <div className="toolbar-group">
                <select
                    className="zoom-select"
                    value={arrangeAction}
                    onChange={(e) => setArrangeAction(e.target.value as ArrangeAction)}
                    title="排列动作"
                >
                    {ARRANGE_OPTIONS.map((item) => (
                        <option key={item.value} value={item.value}>
                            {item.label}
                        </option>
                    ))}
                </select>
                <button
                    className="toolbar-btn"
                    onClick={executeArrangeAction}
                    disabled={!canExecuteArrange}
                    title={canExecuteArrange ? '执行排列动作' : '请先选择足够的组件'}
                >
                    执行
                </button>
            </div>

            {/* Zoom */}
            <div className="toolbar-group">
                <button className="toolbar-btn" onClick={() => handleZoomStep(-25)} title="缩小 25%">-</button>
                <select
                    className="zoom-select"
                    value={zoom}
                    onChange={handleZoomChange}
                >
                    {zoomSelectOptions.map((z) => (
                        <option key={z} value={z}>
                            {z}%
                        </option>
                    ))}
                </select>
                <button className="toolbar-btn" onClick={() => handleZoomStep(25)} title="放大 25%">+</button>
            </div>

            <div className="toolbar-group toolbar-group--status">
                <div className="toolbar-info-pill">
                    <span className="toolbar-info-pill__label">画布</span>
                    <span className="toolbar-info-pill__value">{state.config.width} × {state.config.height}</span>
                </div>
                <div className="toolbar-info-pill">
                    <span className="toolbar-info-pill__label">选中</span>
                    <span className="toolbar-info-pill__value">{selectedIds.length}</span>
                </div>
            </div>

        </div>
    );
}
