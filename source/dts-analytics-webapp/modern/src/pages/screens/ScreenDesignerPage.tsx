import { useEffect, useState, useCallback, useMemo } from 'react';
import { DndProvider } from 'react-dnd';
import { HTML5Backend } from 'react-dnd-html5-backend';
import { useNavigate, useParams } from 'react-router';
import { ScreenProvider, useScreen } from './ScreenContext';
import { ScreenRuntimeProvider } from './ScreenRuntimeContext';
import { analyticsApi } from '../../api/analyticsApi';
import { resolveScreenTheme } from './screenThemes';
import { normalizeScreenConfig } from './specV2';
import {
    ComponentLibraryPanel,
    CanvasToolbar,
    DesignerCanvas,
    PropertyPanel,
    LayerPanel,
    ScreenHeader,
} from './components';
import { PageManagerPanel } from './components/PageManagerPanel';
import type { ScreenPage } from './types';
import './ScreenDesigner.css';

function ScreenDesignerContent() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const {
        undo,
        redo,
        deleteComponents,
        copyComponents,
        pasteComponents,
        duplicateSelected,
        loadConfig,
        clipboard,
        state,
        dispatch,
        selectComponents,
        updateConfig,
    } = useScreen();
    const { selectedIds } = state;
    const { config } = state;
    const [rightPanelTab, setRightPanelTab] = useState<'property' | 'layer'>(() => {
        const raw = typeof window !== 'undefined'
            ? window.localStorage.getItem('dts.analytics.screenDesigner.rightPanelTab')
            : null;
        return raw === 'layer' ? 'layer' : 'property';
    });
    const [focusMode, setFocusMode] = useState<boolean>(() => {
        const raw = typeof window !== 'undefined'
            ? window.localStorage.getItem('dts.analytics.screenDesigner.focusMode')
            : null;
        return raw === 'true';
    });
    const [showLibraryPanel, setShowLibraryPanel] = useState<boolean>(() => {
        const raw = typeof window !== 'undefined'
            ? window.localStorage.getItem('dts.analytics.screenDesigner.showLibraryPanel')
            : null;
        return raw !== 'false';
    });
    const [showInspectorPanel, setShowInspectorPanel] = useState<boolean>(() => {
        const raw = typeof window !== 'undefined'
            ? window.localStorage.getItem('dts.analytics.screenDesigner.showInspectorPanel')
            : null;
        return raw !== 'false';
    });

    useEffect(() => {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem('dts.analytics.screenDesigner.rightPanelTab', rightPanelTab);
    }, [rightPanelTab]);
    useEffect(() => {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem('dts.analytics.screenDesigner.focusMode', focusMode ? 'true' : 'false');
    }, [focusMode]);
    useEffect(() => {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem('dts.analytics.screenDesigner.showLibraryPanel', showLibraryPanel ? 'true' : 'false');
    }, [showLibraryPanel]);
    useEffect(() => {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem('dts.analytics.screenDesigner.showInspectorPanel', showInspectorPanel ? 'true' : 'false');
    }, [showInspectorPanel]);

    // --- Multi-page management ---
    const [currentPageIndex, setCurrentPageIndex] = useState(0);
    const pages: ScreenPage[] = useMemo(() => {
        if (config.pages && config.pages.length > 0) return config.pages;
        // Single-page fallback: wrap top-level components
        return [{
            id: '__default__',
            name: '页面 1',
            components: config.components || [],
        }];
    }, [config.pages, config.components]);

    const hasMultiPages = (config.pages?.length ?? 0) > 1;

    const handleAddPage = useCallback(() => {
        const newPage: ScreenPage = {
            id: `page_${crypto.randomUUID().replace(/-/g, '').slice(0, 12)}`,
            name: `页面 ${pages.length + 1}`,
            components: [],
        };
        const updatedPages = [...pages, newPage];
        updateConfig({ pages: updatedPages });
        setCurrentPageIndex(updatedPages.length - 1);
    }, [pages, updateConfig]);

    const handleDeletePage = useCallback((index: number) => {
        if (pages.length <= 1) return;
        const updatedPages = pages.filter((_, i) => i !== index);
        updateConfig({ pages: updatedPages });
        if (currentPageIndex >= updatedPages.length) {
            setCurrentPageIndex(Math.max(0, updatedPages.length - 1));
        }
    }, [pages, updateConfig, currentPageIndex]);

    const handleDuplicatePage = useCallback((index: number) => {
        const source = pages[index];
        if (!source) return;
        const newPage: ScreenPage = {
            ...source,
            id: `page_${crypto.randomUUID().replace(/-/g, '').slice(0, 12)}`,
            name: `${source.name} (副本)`,
            components: source.components.map(c => ({
                ...c,
                id: `comp_${crypto.randomUUID().replace(/-/g, '').slice(0, 12)}`,
            })),
        };
        const updatedPages = [...pages];
        updatedPages.splice(index + 1, 0, newPage);
        updateConfig({ pages: updatedPages });
        setCurrentPageIndex(index + 1);
    }, [pages, updateConfig]);

    const handleRenamePage = useCallback((index: number, name: string) => {
        const updatedPages = pages.map((p, i) => i === index ? { ...p, name } : p);
        updateConfig({ pages: updatedPages });
    }, [pages, updateConfig]);

    const handleMovePage = useCallback((fromIndex: number, toIndex: number) => {
        if (fromIndex === toIndex) return;
        const updatedPages = [...pages];
        const [moved] = updatedPages.splice(fromIndex, 1);
        updatedPages.splice(toIndex, 0, moved);
        updateConfig({ pages: updatedPages });
        if (currentPageIndex === fromIndex) {
            setCurrentPageIndex(toIndex);
        }
    }, [pages, updateConfig, currentPageIndex]);

    // Load existing screen if editing
    useEffect(() => {
        if (id) {
            analyticsApi.getScreen(id, { mode: 'draft' })
                .then((screen) => {
                    if (screen.canEdit === false) {
                        alert('当前账号没有该大屏的编辑权限');
                        navigate('/screens', { replace: true });
                        return;
                    }
                    const normalized = normalizeScreenConfig(screen, { id: screen.id });
                    if (normalized.warnings.length > 0) {
                        console.warn('[screen-spec-v2] normalized with warnings:', normalized.warnings);
                    }
                    const backgroundColor = normalized.config.backgroundColor || '#0d1b2a';
                    const resolvedTheme = resolveScreenTheme(
                        normalized.config.theme,
                        backgroundColor,
                    );
                    loadConfig({ ...normalized.config, theme: resolvedTheme });
                })
                .catch((error) => {
                    console.error('Failed to load screen:', error);
                });
        }
    }, [id, loadConfig, navigate]);

    // Keyboard shortcuts
    useEffect(() => {
        const isTypingTarget = (target: EventTarget | null): boolean => {
            const node = target as HTMLElement | null;
            if (!node) return false;
            const tag = node.tagName;
            if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
            return node.isContentEditable;
        };
        const handleKeyDown = (e: KeyboardEvent) => {
            const hotkey = e.ctrlKey || e.metaKey;
            // Ignore if typing in form/editor context
            if (isTypingTarget(e.target)) {
                return;
            }

            // Ctrl/Cmd+Z: Undo
            if (hotkey && e.key.toLowerCase() === 'z' && !e.shiftKey) {
                e.preventDefault();
                undo();
            }
            // Ctrl/Cmd+Y or Ctrl/Cmd+Shift+Z: Redo
            if ((hotkey && e.key.toLowerCase() === 'y') || (hotkey && e.shiftKey && e.key.toLowerCase() === 'z')) {
                e.preventDefault();
                redo();
            }
            // Delete/Backspace: Delete selected
            if ((e.key === 'Delete' || e.key === 'Backspace') && selectedIds.length > 0) {
                e.preventDefault();
                deleteComponents(selectedIds);
            }
            // Ctrl/Cmd+C: Copy
            if (hotkey && e.key.toLowerCase() === 'c' && selectedIds.length > 0) {
                e.preventDefault();
                copyComponents();
            }
            // Ctrl/Cmd+V: Paste
            if (hotkey && e.key.toLowerCase() === 'v' && clipboard.length > 0) {
                e.preventDefault();
                pasteComponents();
            }
            // Ctrl/Cmd+D: Duplicate (atomic action in reducer, no race condition)
            if (hotkey && e.key.toLowerCase() === 'd' && selectedIds.length > 0) {
                e.preventDefault();
                duplicateSelected();
            }
            // Ctrl/Cmd+A: Select all components
            if (hotkey && e.key.toLowerCase() === 'a') {
                e.preventDefault();
                selectComponents(state.config.components.map((item) => item.id));
            }
            // Ctrl/Cmd + = / - / 0 : Zoom control
            if (hotkey && (e.key === '=' || e.key === '+')) {
                e.preventDefault();
                const next = Math.min(300, Math.max(25, (Number(state.zoom) || 100) + 25));
                dispatch({ type: 'SET_ZOOM', payload: next });
            }
            if (hotkey && e.key === '-') {
                e.preventDefault();
                const next = Math.min(300, Math.max(25, (Number(state.zoom) || 100) - 25));
                dispatch({ type: 'SET_ZOOM', payload: next });
            }
            if (hotkey && e.key === '0') {
                e.preventDefault();
                dispatch({ type: 'SET_ZOOM', payload: 100 });
            }
            // Ctrl/Cmd+\ : Toggle focus mode
            if (hotkey && e.code === 'Backslash') {
                e.preventDefault();
                setFocusMode((prev) => !prev);
            }
            // Ctrl/Cmd+Alt+1/2 : toggle left/right panel visibility
            if (hotkey && e.altKey && e.key === '1') {
                e.preventDefault();
                setShowLibraryPanel((prev) => !prev);
                return;
            }
            if (hotkey && e.altKey && e.key === '2') {
                e.preventDefault();
                setShowInspectorPanel((prev) => !prev);
                return;
            }
            // Ctrl/Cmd+1/2 : switch right panel tab
            if (hotkey && !e.altKey && e.key === '1') {
                e.preventDefault();
                setRightPanelTab('property');
                return;
            }
            if (hotkey && !e.altKey && e.key === '2') {
                e.preventDefault();
                setRightPanelTab('layer');
                return;
            }
            // Arrow keys: nudge selected components (Shift = 10px)
            if ((e.key === 'ArrowUp' || e.key === 'ArrowDown' || e.key === 'ArrowLeft' || e.key === 'ArrowRight') && selectedIds.length > 0) {
                e.preventDefault();
                const step = e.shiftKey ? 10 : 1;
                const dx = e.key === 'ArrowLeft' ? -step : (e.key === 'ArrowRight' ? step : 0);
                const dy = e.key === 'ArrowUp' ? -step : (e.key === 'ArrowDown' ? step : 0);
                if (dx === 0 && dy === 0) {
                    return;
                }
                const nextPositions: Array<{ id: string; x: number; y: number }> = [];
                const canvasWidth = Number(state.config.width) || 1920;
                const canvasHeight = Number(state.config.height) || 1080;
                for (const item of state.config.components) {
                    if (!selectedIds.includes(item.id)) continue;
                    const maxX = Math.max(0, canvasWidth - item.width);
                    const maxY = Math.max(0, canvasHeight - item.height);
                    const x = Math.min(maxX, Math.max(0, item.x + dx));
                    const y = Math.min(maxY, Math.max(0, item.y + dy));
                    nextPositions.push({ id: item.id, x, y });
                }
                if (nextPositions.length > 0) {
                    dispatch({ type: 'MOVE_COMPONENTS', payload: nextPositions });
                    dispatch({ type: 'SNAPSHOT' });
                }
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [undo, redo, deleteComponents, copyComponents, pasteComponents, duplicateSelected, selectedIds, clipboard, dispatch, selectComponents, state.config.components, state.config.height, state.config.width, state.zoom]);

    return (
        <ScreenRuntimeProvider definitions={state.config.globalVariables}>
            <div
                data-testid="analytics-screen-designer"
                className={`screen-designer ${focusMode ? 'is-focus-mode' : ''}`}
            >
                <ScreenHeader
                    focusMode={focusMode}
                    onToggleFocusMode={() => setFocusMode((prev) => !prev)}
                    showLibraryPanel={showLibraryPanel}
                    onToggleLibraryPanel={() => setShowLibraryPanel((prev) => !prev)}
                    showInspectorPanel={showInspectorPanel}
                    onToggleInspectorPanel={() => setShowInspectorPanel((prev) => !prev)}
                />

                <div className="screen-designer-body">
                    {!focusMode && showLibraryPanel ? (
                        <div className="designer-side-rail designer-side-rail--library">
                            <ComponentLibraryPanel />
                        </div>
                    ) : null}

                    <div className="canvas-area">
                        <CanvasToolbar />
                        <DesignerCanvas />
                        {(hasMultiPages || pages.length > 0) && (
                            <PageManagerPanel
                                pages={pages}
                                currentPageIndex={currentPageIndex}
                                onSwitchPage={setCurrentPageIndex}
                                onAddPage={handleAddPage}
                                onDeletePage={handleDeletePage}
                                onDuplicatePage={handleDuplicatePage}
                                onRenamePage={handleRenamePage}
                                onMovePage={handleMovePage}
                            />
                        )}
                    </div>

                    {!focusMode && showInspectorPanel ? (
                        <div className="designer-side-rail designer-side-rail--inspector">
                            <div className="designer-right-panel">
                                <div className="designer-right-panel-tabs">
                                    <button
                                        type="button"
                                        className={`designer-right-panel-tab ${rightPanelTab === 'property' ? 'active' : ''}`}
                                        onClick={() => setRightPanelTab('property')}
                                        title="组件属性配置"
                                    >
                                        属性
                                    </button>
                                    <button
                                        type="button"
                                        className={`designer-right-panel-tab ${rightPanelTab === 'layer' ? 'active' : ''}`}
                                        onClick={() => setRightPanelTab('layer')}
                                        title="图层管理"
                                    >
                                        图层
                                    </button>
                                </div>
                                <div className="designer-right-panel-content">
                                    {rightPanelTab === 'property' ? <PropertyPanel /> : <LayerPanel />}
                                </div>
                            </div>
                        </div>
                    ) : null}
                </div>
            </div>
        </ScreenRuntimeProvider>
    );
}

export default function ScreenDesignerPage() {
    return (
        <DndProvider backend={HTML5Backend}>
            <ScreenProvider>
                <ScreenDesignerContent />
            </ScreenProvider>
        </DndProvider>
    );
}
