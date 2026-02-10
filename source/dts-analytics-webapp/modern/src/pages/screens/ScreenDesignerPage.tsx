import { useEffect } from 'react';
import { DndProvider } from 'react-dnd';
import { HTML5Backend } from 'react-dnd-html5-backend';
import { useParams } from 'react-router';
import { ScreenProvider, useScreen } from './ScreenContext';
import { analyticsApi } from '../../api/analyticsApi';
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
    const {
        undo,
        redo,
        deleteComponents,
        copyComponents,
        pasteComponents,
        loadConfig,
        clipboard,
        state
    } = useScreen();
    const { selectedIds } = state;

    // Load existing screen if editing
    useEffect(() => {
        if (id) {
            analyticsApi.getScreen(id)
                .then((screen) => {
                    loadConfig({
                        id: String(screen.id),
                        name: screen.name || '未命名大屏',
                        description: screen.description || '',
                        width: screen.width || 1920,
                        height: screen.height || 1080,
                        backgroundColor: screen.backgroundColor || '#0d1b2a',
                        backgroundImage: screen.backgroundImage || undefined,
                        components: (screen.components || []).map(c => ({
                            ...c,
                            type: c.type as import('./types').ComponentType,
                            dataSource: c.dataSource as import('./types').DataSourceConfig | undefined,
                        })),
                    });
                })
                .catch((error) => {
                    console.error('Failed to load screen:', error);
                });
        }
    }, [id, loadConfig]);

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
                <div style={{ display: 'flex', flexDirection: 'column', width: 300 }}>
                    <PropertyPanel />
                    <LayerPanel />
                </div>
            </div>
        </div>
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
