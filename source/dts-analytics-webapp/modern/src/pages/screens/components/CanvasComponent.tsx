import { useCallback, useState, useRef } from 'react';
import { useScreen } from '../ScreenContext';
import { ComponentRenderer } from './ComponentRenderer';
import type { ScreenComponent } from '../types';

interface CanvasComponentProps {
    component: ScreenComponent;
    isSelected: boolean;
}

type ResizeDirection = 'nw' | 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w';

export function CanvasComponent({ component, isSelected }: CanvasComponentProps) {
    const { dispatch, selectComponents } = useScreen();
    const [isDragging, setIsDragging] = useState(false);
    const [isResizing, setIsResizing] = useState(false);
    const startPos = useRef({ x: 0, y: 0 });
    const startSize = useRef({ width: 0, height: 0 });
    const startCompPos = useRef({ x: 0, y: 0 });
    const resizeDirection = useRef<ResizeDirection | null>(null);

    const handleMouseDown = useCallback((e: React.MouseEvent) => {
        if (component.locked) return;
        e.stopPropagation();

        selectComponents([component.id]);
        setIsDragging(true);
        startPos.current = { x: e.clientX, y: e.clientY };
        startCompPos.current = { x: component.x, y: component.y };

        const handleMouseMove = (moveEvent: MouseEvent) => {
            const deltaX = moveEvent.clientX - startPos.current.x;
            const deltaY = moveEvent.clientY - startPos.current.y;

            dispatch({
                type: 'MOVE_COMPONENT',
                payload: {
                    id: component.id,
                    x: Math.max(0, startCompPos.current.x + deltaX),
                    y: Math.max(0, startCompPos.current.y + deltaY),
                },
            });
        };

        const handleMouseUp = () => {
            setIsDragging(false);
            document.removeEventListener('mousemove', handleMouseMove);
            document.removeEventListener('mouseup', handleMouseUp);
        };

        document.addEventListener('mousemove', handleMouseMove);
        document.addEventListener('mouseup', handleMouseUp);
    }, [component.id, component.locked, component.x, component.y, dispatch, selectComponents]);

    const handleResizeStart = useCallback((e: React.MouseEvent, direction: ResizeDirection) => {
        if (component.locked) return;
        e.stopPropagation();

        setIsResizing(true);
        resizeDirection.current = direction;
        startPos.current = { x: e.clientX, y: e.clientY };
        startSize.current = { width: component.width, height: component.height };
        startCompPos.current = { x: component.x, y: component.y };

        const handleMouseMove = (moveEvent: MouseEvent) => {
            const deltaX = moveEvent.clientX - startPos.current.x;
            const deltaY = moveEvent.clientY - startPos.current.y;
            const dir = resizeDirection.current;

            let newWidth = startSize.current.width;
            let newHeight = startSize.current.height;
            let newX = startCompPos.current.x;
            let newY = startCompPos.current.y;

            if (dir?.includes('e')) {
                newWidth = Math.max(50, startSize.current.width + deltaX);
            }
            if (dir?.includes('w')) {
                const widthDelta = Math.min(deltaX, startSize.current.width - 50);
                newWidth = startSize.current.width - widthDelta;
                newX = startCompPos.current.x + widthDelta;
            }
            if (dir?.includes('s')) {
                newHeight = Math.max(50, startSize.current.height + deltaY);
            }
            if (dir?.includes('n')) {
                const heightDelta = Math.min(deltaY, startSize.current.height - 50);
                newHeight = startSize.current.height - heightDelta;
                newY = startCompPos.current.y + heightDelta;
            }

            dispatch({
                type: 'RESIZE_COMPONENT',
                payload: { id: component.id, width: newWidth, height: newHeight },
            });

            if (dir?.includes('w') || dir?.includes('n')) {
                dispatch({
                    type: 'MOVE_COMPONENT',
                    payload: { id: component.id, x: newX, y: newY },
                });
            }
        };

        const handleMouseUp = () => {
            setIsResizing(false);
            resizeDirection.current = null;
            document.removeEventListener('mousemove', handleMouseMove);
            document.removeEventListener('mouseup', handleMouseUp);
        };

        document.addEventListener('mousemove', handleMouseMove);
        document.addEventListener('mouseup', handleMouseUp);
    }, [component.id, component.locked, component.width, component.height, component.x, component.y, dispatch]);

    const resizeHandles: ResizeDirection[] = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'];

    return (
        <div
            className={`canvas-component ${isSelected ? 'selected' : ''} ${component.locked ? 'locked' : ''}`}
            style={{
                left: component.x,
                top: component.y,
                width: component.width,
                height: component.height,
                zIndex: component.zIndex,
                cursor: isDragging ? 'grabbing' : isResizing ? 'default' : 'move',
            }}
            onMouseDown={handleMouseDown}
        >
            <ComponentRenderer component={component} />

            {isSelected && !component.locked && (
                <>
                    {resizeHandles.map((dir) => (
                        <div
                            key={dir}
                            className={`resize-handle ${dir}`}
                            onMouseDown={(e) => handleResizeStart(e, dir)}
                        />
                    ))}
                </>
            )}
        </div>
    );
}
