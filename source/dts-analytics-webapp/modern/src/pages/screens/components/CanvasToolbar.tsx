import { useRef, useState } from 'react';
import { useScreen } from '../ScreenContext';
import type { ScreenTheme } from '../types';
import { applyThemeToComponents, getThemeTokens, type ThemeComponentApplyMode } from '../screenThemes';

const ZOOM_OPTIONS = [50, 75, 100, 125, 150, 200];
const THEME_PACK_SCHEMA = 'dts.screen-theme-pack';
const THEME_PACK_VERSION = 1;

const THEME_OPTIONS: { value: ScreenTheme | ''; label: string }[] = [
    { value: '', label: '经典深蓝' },
    { value: 'titanium', label: '钛合金灰' },
    { value: 'glacier', label: '冰川白' },
];

type ThemePackPayload = {
    schema?: string;
    version?: number;
    name?: string;
    theme?: string;
    backgroundColor?: string;
    backgroundImage?: string | null;
    applyToComponents?: boolean;
    componentStyleMode?: ThemeComponentApplyMode;
    exportedAt?: string;
};

function normalizeTheme(theme?: string): ScreenTheme | undefined {
    if (theme === 'legacy-dark' || theme === 'titanium' || theme === 'glacier') {
        return theme;
    }
    return undefined;
}

export function CanvasToolbar() {
    const {
        state,
        dispatch,
        undo,
        redo,
        canUndo,
        canRedo,
        deleteComponents,
        updateConfig,
        alignSelected,
        distributeSelected,
        groupSelected,
        ungroupSelected,
    } = useScreen();
    const { selectedIds, zoom, showGrid } = state;
    const themeInputRef = useRef<HTMLInputElement | null>(null);
    const [themeApplyMode, setThemeApplyMode] = useState<ThemeComponentApplyMode>('force');
    const canAlign = selectedIds.length >= 2;
    const canDistribute = selectedIds.length >= 3;
    const canGroup = selectedIds.length >= 2;
    const canUngroup = selectedIds.length >= 1;

    const handleZoomChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
        dispatch({ type: 'SET_ZOOM', payload: Number(e.target.value) });
    };

    const handleThemeChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
        const value = e.target.value as ScreenTheme | '';
        const theme = value || undefined;
        const tokens = getThemeTokens(theme);
        updateConfig({ theme, backgroundColor: tokens.canvasBackground });
    };

    const applyThemeToAllComponents = (mode: ThemeComponentApplyMode) => {
        const nextTheme = state.config.theme;
        const nextComponents = applyThemeToComponents(state.config.components, nextTheme, mode);
        updateConfig({ components: nextComponents });
    };

    const handleDelete = () => {
        if (selectedIds.length > 0) {
            deleteComponents(selectedIds);
        }
    };

    const handleExportThemePack = () => {
        const payload: ThemePackPayload = {
            schema: THEME_PACK_SCHEMA,
            version: THEME_PACK_VERSION,
            name: state.config.name,
            theme: state.config.theme || 'legacy-dark',
            backgroundColor: state.config.backgroundColor,
            backgroundImage: state.config.backgroundImage || null,
            applyToComponents: true,
            componentStyleMode: themeApplyMode,
            exportedAt: new Date().toISOString(),
        };

        const json = JSON.stringify(payload, null, 2);
        const blob = new Blob([json], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        const dateTag = new Date().toISOString().slice(0, 10);
        a.href = url;
        a.download = `theme-pack-${dateTag}.json`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
    };

    const handleImportThemePackClick = () => {
        themeInputRef.current?.click();
    };

    const handleThemePackFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        event.target.value = '';
        if (!file) {
            return;
        }

        try {
            const content = await file.text();
            const raw = JSON.parse(content) as ThemePackPayload;
            if (!raw || typeof raw !== 'object') {
                alert('主题包格式不正确');
                return;
            }
            if (raw.schema && raw.schema !== THEME_PACK_SCHEMA) {
                alert('主题包 schema 不匹配');
                return;
            }

            const nextTheme = normalizeTheme(raw.theme) || state.config.theme;
            const fallbackBackground = getThemeTokens(nextTheme).canvasBackground;
            const nextBackground = typeof raw.backgroundColor === 'string' && raw.backgroundColor.trim().length > 0
                ? raw.backgroundColor.trim()
                : fallbackBackground;

            updateConfig({
                theme: nextTheme,
                backgroundColor: nextBackground,
                backgroundImage: typeof raw.backgroundImage === 'string' && raw.backgroundImage.trim().length > 0
                    ? raw.backgroundImage.trim()
                    : undefined,
            });
            const importMode = raw.componentStyleMode === 'safe' ? 'safe' : 'force';
            const shouldApply = raw.applyToComponents !== false;
            if (shouldApply) {
                const confirmed = window.confirm(
                    `主题包已导入，是否批量应用组件样式？\n策略：${importMode === 'force' ? '强制覆盖' : '仅补缺省'}`
                );
                if (confirmed) {
                    const nextComponents = applyThemeToComponents(state.config.components, nextTheme, importMode);
                    updateConfig({
                        theme: nextTheme,
                        backgroundColor: nextBackground,
                        backgroundImage: typeof raw.backgroundImage === 'string' && raw.backgroundImage.trim().length > 0
                            ? raw.backgroundImage.trim()
                            : undefined,
                        components: nextComponents,
                    });
                }
            }
            alert('主题包导入成功');
        } catch (error) {
            console.error('Failed to import theme pack:', error);
            alert('主题包导入失败，请检查 JSON 内容');
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

            {/* Align / distribute */}
            <div className="toolbar-group">
                <button className="toolbar-btn" onClick={groupSelected} disabled={!canGroup} title="组合">🧩</button>
                <button className="toolbar-btn" onClick={ungroupSelected} disabled={!canUngroup} title="取消组合">🧱</button>
                <button className="toolbar-btn" onClick={() => alignSelected('left')} disabled={!canAlign} title="左对齐">⟸</button>
                <button className="toolbar-btn" onClick={() => alignSelected('h-center')} disabled={!canAlign} title="水平居中">↔︎</button>
                <button className="toolbar-btn" onClick={() => alignSelected('right')} disabled={!canAlign} title="右对齐">⟹</button>
                <button className="toolbar-btn" onClick={() => alignSelected('top')} disabled={!canAlign} title="顶对齐">⟰</button>
                <button className="toolbar-btn" onClick={() => alignSelected('v-center')} disabled={!canAlign} title="垂直居中">↕︎</button>
                <button className="toolbar-btn" onClick={() => alignSelected('bottom')} disabled={!canAlign} title="底对齐">⟱</button>
                <button className="toolbar-btn" onClick={() => distributeSelected('horizontal')} disabled={!canDistribute} title="水平分布">⇆</button>
                <button className="toolbar-btn" onClick={() => distributeSelected('vertical')} disabled={!canDistribute} title="垂直分布">⇅</button>
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

            {/* Theme pack */}
            <div className="toolbar-group">
                <select
                    className="zoom-select"
                    value={themeApplyMode}
                    onChange={(e) => setThemeApplyMode(e.target.value === 'safe' ? 'safe' : 'force')}
                    title="组件样式应用策略"
                >
                    <option value="force">强制覆盖</option>
                    <option value="safe">仅补缺省</option>
                </select>
                <button
                    className="toolbar-btn"
                    onClick={() => applyThemeToAllComponents(themeApplyMode)}
                    title="按当前主题批量刷新组件样式"
                >
                    刷组件样式
                </button>
                <button
                    className="toolbar-btn"
                    onClick={handleExportThemePack}
                    title="导出主题包"
                >
                    ⬇️主题包
                </button>
                <button
                    className="toolbar-btn"
                    onClick={handleImportThemePackClick}
                    title="导入主题包"
                >
                    ⬆️主题包
                </button>
                <input
                    ref={themeInputRef}
                    type="file"
                    accept="application/json,.json"
                    style={{ display: 'none' }}
                    onChange={handleThemePackFileChange}
                />
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
