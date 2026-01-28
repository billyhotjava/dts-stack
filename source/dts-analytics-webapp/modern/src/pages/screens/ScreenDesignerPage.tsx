import { useEffect } from 'react';
import { DndProvider } from 'react-dnd';
import { HTML5Backend } from 'react-dnd-html5-backend';
import { ScreenProvider, useScreen } from './ScreenContext';
import {
    ComponentLibraryPanel,
    CanvasToolbar,
    DesignerCanvas,
    PropertyPanel,
    LayerPanel,
} from './components';
import './ScreenDesigner.css';

function ScreenDesignerContent() {
    const { undo, redo, deleteComponents, state } = useScreen();
    const { selectedIds } = state;

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
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [undo, redo, deleteComponents, selectedIds]);

    return (
        <div className="screen-designer">
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
