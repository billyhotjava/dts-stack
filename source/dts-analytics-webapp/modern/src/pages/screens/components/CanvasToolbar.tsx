import { useScreen } from '../ScreenContext';
import type { ScreenTheme } from '../types';
import { getThemeTokens } from '../screenThemes';

const ZOOM_OPTIONS = [50, 75, 100, 125, 150, 200];

const THEME_OPTIONS: { value: ScreenTheme | ''; label: string }[] = [
    { value: '', label: '经典深蓝' },
    { value: 'titanium', label: '钛合金灰' },
    { value: 'glacier', label: '冰川白' },
];

export function CanvasToolbar() {
    const { state, dispatch, undo, redo, canUndo, canRedo, deleteComponents, updateConfig } = useScreen();
    const { selectedIds, zoom, showGrid } = state;

    const handleZoomChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
        dispatch({ type: 'SET_ZOOM', payload: Number(e.target.value) });
    };

    const handleThemeChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
        const value = e.target.value as ScreenTheme | '';
        const theme = value || undefined;
        const tokens = getThemeTokens(theme);
        updateConfig({ theme, backgroundColor: tokens.canvasBackground });
    };

    const handleDelete = () => {
        if (selectedIds.length > 0) {
            deleteComponents(selectedIds);
        }
    };

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
                    ↩️
                </button>
                <button
                    className="toolbar-btn"
                    onClick={redo}
                    disabled={!canRedo}
                    title="重做 (Ctrl+Y)"
                >
                    ↪️
                </button>
            </div>

            {/* Edit operations */}
            <div className="toolbar-group">
                <button
                    className="toolbar-btn"
                    onClick={handleDelete}
                    disabled={selectedIds.length === 0}
                    title="删除选中组件"
                >
                    🗑️
                </button>
            </div>

            {/* View options */}
            <div className="toolbar-group">
                <button
                    className={`toolbar-btn ${showGrid ? 'active' : ''}`}
                    onClick={() => dispatch({ type: 'TOGGLE_GRID' })}
                    title="显示/隐藏网格"
                >
                    #
                </button>
            </div>

            {/* Zoom */}
            <div className="toolbar-group">
                <span className="toolbar-label">缩放:</span>
                <select
                    className="zoom-select"
                    value={zoom}
                    onChange={handleZoomChange}
                >
                    {ZOOM_OPTIONS.map((z) => (
                        <option key={z} value={z}>
                            {z}%
                        </option>
                    ))}
                </select>
            </div>

            {/* Theme */}
            <div className="toolbar-group">
                <span className="toolbar-label">主题:</span>
                <select
                    className="zoom-select"
                    value={state.config.theme || ''}
                    onChange={handleThemeChange}
                >
                    {THEME_OPTIONS.map((opt) => (
                        <option key={opt.value} value={opt.value}>
                            {opt.label}
                        </option>
                    ))}
                </select>
            </div>

            {/* Screen info */}
            <div className="toolbar-group" style={{ marginLeft: 'auto', borderRight: 'none' }}>
                <span className="toolbar-label">
                    画布: {state.config.width} × {state.config.height}
                </span>
            </div>
        </div>
    );
}
