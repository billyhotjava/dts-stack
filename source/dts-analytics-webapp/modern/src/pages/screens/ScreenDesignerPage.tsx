import { useEffect, useState, useCallback, useMemo } from 'react';
import { DndProvider } from 'react-dnd';
import { HTML5Backend } from 'react-dnd-html5-backend';
import { useLocation, useNavigate, useParams } from 'react-router';
import { ScreenProvider, useScreen } from './ScreenContext';
import { ScreenRuntimeProvider } from './ScreenRuntimeContext';
import { analyticsApi } from '../../api/analyticsApi';
import { resolveScreenTheme } from './screenThemes';
import { normalizeScreenConfig } from './specV2';
import { commitScreenPageDraft, materializeScreenPage, resolveScreenPages, switchScreenPage } from './screenPageState';
import {
    resolveInitialFocusMode,
    resolveInitialRightPanelTab,
    resolveInitialSidePanelVisibility,
} from './screenDesignerLayoutState';
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
    const location = useLocation();
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
    const [rightPanelTab, setRightPanelTab] = useState<'property' | 'layer'>(() => resolveInitialRightPanelTab(
        typeof window !== 'undefined' ? window.localStorage : undefined,
    ));
    const [focusMode, setFocusMode] = useState<boolean>(() => resolveInitialFocusMode(
        typeof window !== 'undefined' ? window.localStorage : undefined,
    ));
    const initialSidePanels = resolveInitialSidePanelVisibility(
        typeof window !== 'undefined' ? window.localStorage : undefined,
    );
    const [showLibraryPanel, setShowLibraryPanel] = useState<boolean>(initialSidePanels.showLibraryPanel);
    const [showInspectorPanel, setShowInspectorPanel] = useState<boolean>(initialSidePanels.showInspectorPanel);
    const [hasLoadedInitialState, setHasLoadedInitialState] = useState(false);

    useEffect(() => {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem('dts.analytics.screenDesigner.rightPanelTab', rightPanelTab);
    }, [rightPanelTab]);
    useEffect(() => {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem('dts.analytics.screenDesigner.focusMode', focusMode ? 'true' : 'false');
    }, [focusMode]);
    // --- Multi-page management ---
    const [currentPageIndex, setCurrentPageIndex] = useState(0);
    const pages: ScreenPage[] = useMemo(() => {
        return resolveScreenPages(config);
    }, [config]);

    const hasMultiPages = (config.pages?.length ?? 0) > 1;

    const handleAddPage = useCallback(() => {
        const committed = commitScreenPageDraft(config, currentPageIndex);
        const newPage: ScreenPage = {
            id: `page_${crypto.randomUUID().replace(/-/g, '').slice(0, 12)}`,
            name: `页面 ${pages.length + 1}`,
            components: [],
        };
        const updatedPages = [...resolveScreenPages(committed), newPage];
        const nextIndex = updatedPages.length - 1;
        updateConfig(materializeScreenPage({
            ...committed,
            pages: updatedPages,
        }, nextIndex));
        selectComponents([]);
        setCurrentPageIndex(nextIndex);
    }, [config, currentPageIndex, pages.length, selectComponents, updateConfig]);

    const handleDeletePage = useCallback((index: number) => {
        if (pages.length <= 1) return;
        const committed = commitScreenPageDraft(config, currentPageIndex);
        const updatedPages = resolveScreenPages(committed).filter((_, i) => i !== index);
        const nextIndex = currentPageIndex === index
            ? Math.max(0, Math.min(index, updatedPages.length - 1))
            : (currentPageIndex > index ? currentPageIndex - 1 : currentPageIndex);
        updateConfig(materializeScreenPage({
            ...committed,
            pages: updatedPages,
        }, nextIndex));
        selectComponents([]);
        setCurrentPageIndex(nextIndex);
    }, [config, currentPageIndex, pages.length, selectComponents, updateConfig]);

    const handleDuplicatePage = useCallback((index: number) => {
        const committed = commitScreenPageDraft(config, currentPageIndex);
        const resolvedPages = resolveScreenPages(committed);
        const source = resolvedPages[index];
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
        const updatedPages = [...resolvedPages];
        updatedPages.splice(index + 1, 0, newPage);
        const nextIndex = index + 1;
        updateConfig(materializeScreenPage({
            ...committed,
            pages: updatedPages,
        }, nextIndex));
        selectComponents([]);
        setCurrentPageIndex(nextIndex);
    }, [config, currentPageIndex, selectComponents, updateConfig]);

    const handleRenamePage = useCallback((index: number, name: string) => {
        const committed = commitScreenPageDraft(config, currentPageIndex);
        const updatedPages = resolveScreenPages(committed).map((p, i) => i === index ? { ...p, name } : p);
        updateConfig({ pages: updatedPages });
    }, [config, currentPageIndex, updateConfig]);

    const handleMovePage = useCallback((fromIndex: number, toIndex: number) => {
        if (fromIndex === toIndex) return;
        const committed = commitScreenPageDraft(config, currentPageIndex);
        const updatedPages = [...resolveScreenPages(committed)];
        const [moved] = updatedPages.splice(fromIndex, 1);
        updatedPages.splice(toIndex, 0, moved);
        updateConfig({ pages: updatedPages });
        if (currentPageIndex === fromIndex) {
            setCurrentPageIndex(toIndex);
        } else if (currentPageIndex > fromIndex && currentPageIndex <= toIndex) {
            setCurrentPageIndex(currentPageIndex - 1);
        } else if (currentPageIndex < fromIndex && currentPageIndex >= toIndex) {
            setCurrentPageIndex(currentPageIndex + 1);
        }
    }, [config, currentPageIndex, updateConfig]);

    const handleSwitchPage = useCallback((nextIndex: number) => {
        if (nextIndex === currentPageIndex) {
            return;
        }
        updateConfig(switchScreenPage(config, currentPageIndex, nextIndex));
        selectComponents([]);
        setCurrentPageIndex(nextIndex);
    }, [config, currentPageIndex, selectComponents, updateConfig]);

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
                    setCurrentPageIndex(0);
                    loadConfig(materializeScreenPage({ ...normalized.config, theme: resolvedTheme }, 0));
                })
                .catch((error) => {
                    console.error('Failed to load screen:', error);
                });
        }
    }, [id, loadConfig, navigate]);

    useEffect(() => {
        if (id || hasLoadedInitialState) {
            return;
        }
        const initialConfig = (location.state as { initialConfig?: Record<string, unknown> } | null)?.initialConfig;
        if (!initialConfig || typeof initialConfig !== 'object') {
            return;
        }
        const normalized = normalizeScreenConfig(initialConfig, { id: '' });
        if (normalized.warnings.length > 0) {
            console.warn('[screen-spec-v2] initial template config normalized with warnings:', normalized.warnings);
        }
        const backgroundColor = normalized.config.backgroundColor || '#0d1b2a';
        const resolvedTheme = resolveScreenTheme(
            normalized.config.theme,
            backgroundColor,
        );
        setCurrentPageIndex(0);
        loadConfig(materializeScreenPage({ ...normalized.config, theme: resolvedTheme }, 0));
        setHasLoadedInitialState(true);
    }, [hasLoadedInitialState, id, loadConfig, location.state]);

    useEffect(() => {
        if (pages.length === 0 || currentPageIndex < pages.length) {
            return;
        }
        const nextIndex = Math.max(0, pages.length - 1);
        setCurrentPageIndex(nextIndex);
        const nextConfig = materializeScreenPage(config, nextIndex);
        if (nextConfig !== config) {
            updateConfig(nextConfig);
        }
    }, [config, currentPageIndex, pages.length, updateConfig]);

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
                    currentPageIndex={currentPageIndex}
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
                                onSwitchPage={handleSwitchPage}
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
