import { useEffect } from 'react';
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
        loadConfig,
        clipboard,
        state,
    } = useScreen();
    const { selectedIds } = state;

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
        const handleKeyDown = (e: KeyboardEvent) => {
            // Ignore if typing in input
            if ((e.target as HTMLElement).tagName === 'INPUT' ||
                (e.target as HTMLElement).tagName === 'TEXTAREA') {
                return;
            }

            // Ctrl+Z: Undo
            if (e.ctrlKey && e.key === 'z' && !e.shiftKey) {
                e.preventDefault();
                undo();
            }
            // Ctrl+Y or Ctrl+Shift+Z: Redo
            if ((e.ctrlKey && e.key === 'y') || (e.ctrlKey && e.shiftKey && e.key === 'z')) {
                e.preventDefault();
                redo();
            }
            // Delete/Backspace: Delete selected
            if ((e.key === 'Delete' || e.key === 'Backspace') && selectedIds.length > 0) {
                e.preventDefault();
                deleteComponents(selectedIds);
            }
            // Ctrl+C: Copy
            if (e.ctrlKey && e.key === 'c' && selectedIds.length > 0) {
                e.preventDefault();
                copyComponents();
            }
            // Ctrl+V: Paste
            if (e.ctrlKey && e.key === 'v' && clipboard.length > 0) {
                e.preventDefault();
                pasteComponents();
            }
            // Ctrl+D: Duplicate (copy + paste in one action)
            if (e.ctrlKey && e.key === 'd' && selectedIds.length > 0) {
                e.preventDefault();
                copyComponents();
                // Paste after a tick to ensure clipboard is updated
                setTimeout(() => pasteComponents(), 0);
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [undo, redo, deleteComponents, copyComponents, pasteComponents, selectedIds, clipboard]);

    return (
        <ScreenRuntimeProvider definitions={state.config.globalVariables}>
            <div className="screen-designer">
                {/* Top: Header */}
                <ScreenHeader />

                <div className="screen-designer-body">
                    {/* Left: Component Library */}
                    <ComponentLibraryPanel />

                    {/* Center: Canvas */}
                    <div className="canvas-area">
                        <CanvasToolbar />
                        <DesignerCanvas />
                    </div>

                    {/* Right: Property Panel + Layer Panel */}
                    <div style={{ display: 'flex', flexDirection: 'column', width: 300, overflow: 'hidden' }}>
                        <div style={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
                            <PropertyPanel />
                        </div>
                        <div style={{ maxHeight: '40%', overflow: 'auto' }}>
                            <LayerPanel />
                        </div>
                    </div>
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
