import { useEffect, useMemo, useRef, useState } from 'react';
import { useScreen } from '../ScreenContext';
import type { ScreenTheme } from '../types';
import { applyThemeToComponents, getThemeTokens, type ThemeComponentApplyMode } from '../screenThemes';
import { LinkageGraphPanel } from './LinkageGraphPanel';

const ZOOM_OPTIONS = [50, 75, 100, 125, 150, 200];
const THEME_PACK_SCHEMA = 'dts.screen-theme-pack';
const THEME_PACK_VERSION = 1;

const THEME_OPTIONS: { value: ScreenTheme | ''; label: string }[] = [
    { value: '', label: '经典深蓝' },
    { value: 'titanium', label: '钛合金灰' },
    { value: 'glacier', label: '冰川白' },
];

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
const BATCH_ACTION_OPTIONS = [
    { value: 'duplicate', label: '复制一份' },
    { value: 'copy', label: '复制' },
    { value: 'paste', label: '粘贴' },
    { value: 'bring-top', label: '置顶' },
    { value: 'send-bottom', label: '置底' },
    { value: 'show', label: '显示选中' },
    { value: 'hide', label: '隐藏选中' },
    { value: 'unlock', label: '解锁选中' },
    { value: 'lock', label: '锁定选中' },
    { value: 'delete', label: '删除选中' },
] as const;
type BatchAction = typeof BATCH_ACTION_OPTIONS[number]['value'];

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
        updateSelectedComponents,
        copyComponents,
        pasteComponents,
        clipboard,
    } = useScreen();
    const { selectedIds, zoom, showGrid } = state;
    const themeInputRef = useRef<HTMLInputElement | null>(null);
    const [themeApplyMode, setThemeApplyMode] = useState<ThemeComponentApplyMode>('force');
    const [arrangeAction, setArrangeAction] = useState<ArrangeAction>('align-left');
    const [batchAction, setBatchAction] = useState<BatchAction>('duplicate');
    const [showMoreTools, setShowMoreTools] = useState(false);
    const [showLinkageGraph, setShowLinkageGraph] = useState(false);
    const moreToolsRef = useRef<HTMLDivElement | null>(null);
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
    const handleZoomReset = () => {
        dispatch({ type: 'SET_ZOOM', payload: 100 });
    };
    const handleZoomFit = () => {
        const canvasContainer = document.querySelector('.canvas-container') as HTMLElement | null;
        const availableWidth = canvasContainer
            ? Math.max(320, canvasContainer.clientWidth - 24)
            : Math.max(480, (window.visualViewport?.width ?? window.innerWidth) - 420);
        const availableHeight = canvasContainer
            ? Math.max(240, canvasContainer.clientHeight - 24)
            : Math.max(260, (window.visualViewport?.height ?? window.innerHeight) - 160);
        const canvasWidth = Number(state.config.width) || 1920;
        const canvasHeight = Number(state.config.height) || 1080;
        const fitScale = Math.min(availableWidth / canvasWidth, availableHeight / canvasHeight);
        const fitPercent = clampZoom(Math.floor(fitScale * 100));
        dispatch({ type: 'SET_ZOOM', payload: fitPercent });
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

    const executeBatchAction = () => {
        if (batchAction === 'copy') {
            if (selectedIds.length > 0) copyComponents();
            return;
        }
        if (batchAction === 'paste') {
            if (clipboard.length > 0) pasteComponents();
            return;
        }
        if (batchAction === 'duplicate') {
            if (selectedIds.length === 0) return;
            copyComponents();
            setTimeout(() => pasteComponents(), 0);
            return;
        }
        if (batchAction === 'bring-top') {
            if (selectedIds.length === 0) return;
            const selected = state.config.components
                .filter((item) => selectedIds.includes(item.id))
                .sort((a, b) => a.zIndex - b.zIndex);
            for (const item of selected) {
                dispatch({ type: 'REORDER_LAYER', payload: { id: item.id, direction: 'top' } });
            }
            return;
        }
        if (batchAction === 'send-bottom') {
            if (selectedIds.length === 0) return;
            const selected = state.config.components
                .filter((item) => selectedIds.includes(item.id))
                .sort((a, b) => b.zIndex - a.zIndex);
            for (const item of selected) {
                dispatch({ type: 'REORDER_LAYER', payload: { id: item.id, direction: 'bottom' } });
            }
            return;
        }
        if (batchAction === 'show') {
            if (selectedIds.length > 0) updateSelectedComponents({ visible: true });
            return;
        }
        if (batchAction === 'hide') {
            if (selectedIds.length > 0) updateSelectedComponents({ visible: false });
            return;
        }
        if (batchAction === 'unlock') {
            if (selectedIds.length > 0) updateSelectedComponents({ locked: false });
            return;
        }
        if (batchAction === 'lock') {
            if (selectedIds.length > 0) updateSelectedComponents({ locked: true });
            return;
        }
        if (batchAction === 'delete') {
            handleDelete();
        }
    };

    const canExecuteBatch = (() => {
        if (batchAction === 'paste') return clipboard.length > 0;
        if (batchAction === 'copy' || batchAction === 'duplicate') return selectedIds.length > 0;
        return selectedIds.length > 0;
    })();

    const handleShortcutHelp = () => {
        alert([
            '快捷键说明',
            '',
            'Ctrl/Cmd + Z：撤销',
            'Ctrl/Cmd + Y 或 Ctrl/Cmd + Shift + Z：重做',
            'Ctrl/Cmd + C / V：复制 / 粘贴',
            'Ctrl/Cmd + D：复制一份',
            'Ctrl/Cmd + A：全选组件',
            'Ctrl/Cmd + \\：聚焦模式切换',
            'Ctrl/Cmd + Alt + 1 / 2：显示/隐藏左栏与右栏',
            'Ctrl/Cmd + 1 / 2：切换属性/图层面板',
            'Ctrl/Cmd + K：命令面板',
            'Ctrl/Cmd + Shift + P：预览大屏',
            'Delete / Backspace：删除选中',
            '方向键：微调 1px',
            'Shift + 方向键：快速移动 10px',
            'Ctrl/Cmd + 鼠标滚轮：连续缩放',
            'Ctrl/Cmd + = / -：缩放 ±25%',
            'Ctrl/Cmd + 0：恢复 100%',
        ].join('\n'));
    };

    useEffect(() => {
        if (!showMoreTools) {
            return;
        }
        const handlePointerDown = (event: MouseEvent) => {
            const node = moreToolsRef.current;
            if (!node) return;
            if (!node.contains(event.target as Node)) {
                setShowMoreTools(false);
            }
        };
        const handleEscape = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                setShowMoreTools(false);
            }
        };
        window.addEventListener('mousedown', handlePointerDown);
        window.addEventListener('keydown', handleEscape);
        return () => {
            window.removeEventListener('mousedown', handlePointerDown);
            window.removeEventListener('keydown', handleEscape);
        };
    }, [showMoreTools]);

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

            {/* More tools */}
            <div className="toolbar-group">
                <div className="toolbar-menu" ref={moreToolsRef}>
                    <button
                        className={`toolbar-btn toolbar-menu-trigger ${showMoreTools ? 'active' : ''}`}
                        onClick={() => setShowMoreTools((prev) => !prev)}
                        title="更多低频工具"
                    >
                        更多工具
                    </button>
                    {showMoreTools ? (
                        <div className="toolbar-menu-panel">
                            <div className="toolbar-menu-section">
                                <div className="toolbar-menu-section-title">批量动作</div>
                                <select
                                    className="zoom-select"
                                    value={batchAction}
                                    onChange={(e) => setBatchAction(e.target.value as BatchAction)}
                                    title="批量动作"
                                >
                                    {BATCH_ACTION_OPTIONS.map((item) => (
                                        <option key={item.value} value={item.value}>
                                            {item.label}
                                        </option>
                                    ))}
                                </select>
                                <button
                                    className="toolbar-btn"
                                    onClick={executeBatchAction}
                                    disabled={!canExecuteBatch}
                                    title={canExecuteBatch ? '执行批量动作' : '请先选择组件或复制内容'}
                                >
                                    执行动作
                                </button>
                            </div>
                            <div className="toolbar-menu-section">
                                <div className="toolbar-menu-section-title">视图与主题</div>
                                <button
                                    className="toolbar-btn"
                                    onClick={handleZoomReset}
                                    title="缩放重置为 100%"
                                >
                                    缩放100%
                                </button>
                                <button
                                    className="toolbar-btn"
                                    onClick={handleZoomFit}
                                    title="按当前窗口自动适配缩放"
                                >
                                    缩放适配
                                </button>
                                <button
                                    className={`toolbar-btn ${showGrid ? 'active' : ''}`}
                                    onClick={() => dispatch({ type: 'TOGGLE_GRID' })}
                                    title="显示/隐藏网格"
                                >
                                    {showGrid ? '隐藏网格' : '显示网格'}
                                </button>
                                <select
                                    className="zoom-select"
                                    value={state.config.theme || ''}
                                    onChange={handleThemeChange}
                                    title="切换主题"
                                >
                                    {THEME_OPTIONS.map((opt) => (
                                        <option key={opt.value} value={opt.value}>
                                            {opt.label}
                                        </option>
                                    ))}
                                </select>
                            </div>
                            <div className="toolbar-menu-section">
                                <div className="toolbar-menu-section-title">主题工具</div>
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
                                    应用样式
                                </button>
                                <button
                                    className="toolbar-btn"
                                    onClick={handleExportThemePack}
                                    title="导出主题包"
                                >
                                    导出主题
                                </button>
                                <button
                                    className="toolbar-btn"
                                    onClick={handleImportThemePackClick}
                                    title="导入主题包"
                                >
                                    导入主题
                                </button>
                            </div>
                            <div className="toolbar-menu-section">
                                <div className="toolbar-menu-section-title">联动</div>
                                <button
                                    className="toolbar-btn"
                                    onClick={() => { setShowMoreTools(false); setShowLinkageGraph(prev => !prev); }}
                                    title="查看组件联动关系图"
                                >
                                    联动关系图
                                </button>
                            </div>
                            <div className="toolbar-menu-section">
                                <div className="toolbar-menu-section-title">帮助</div>
                                <button
                                    className="toolbar-btn"
                                    onClick={handleShortcutHelp}
                                    title="查看快捷键"
                                >
                                    快捷键
                                </button>
                            </div>
                            <div className="toolbar-menu-section">
                                <div className="toolbar-menu-section-title">画布信息</div>
                                <div className="toolbar-label">
                                    {state.config.width} × {state.config.height}
                                </div>
                            </div>
                        </div>
                    ) : null}
                </div>
                <input
                    ref={themeInputRef}
                    type="file"
                    accept="application/json,.json"
                    style={{ display: 'none' }}
                    onChange={handleThemePackFileChange}
                />
            </div>

            {showLinkageGraph && (
                <LinkageGraphPanel
                    config={state.config}
                    selectedIds={state.selectedIds}
                    onClose={() => setShowLinkageGraph(false)}
                />
            )}
        </div>
    );
}
