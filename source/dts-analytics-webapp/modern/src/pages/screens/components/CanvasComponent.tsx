import { useCallback, useState, useRef } from 'react';
import { useScreen } from '../ScreenContext';
import { ComponentRenderer } from './ComponentRenderer';
import type { ScreenComponent, ScreenTheme } from '../types';

interface CanvasComponentProps {
    component: ScreenComponent;
    isSelected: boolean;
    theme?: ScreenTheme;
}

type ResizeDirection = 'nw' | 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w';
const SNAP_TOLERANCE = 5;

function findSnapOffset(points: number[], candidates: number[]): { offset: number; guide: number } | null {
    let best: { offset: number; guide: number } | null = null;
    for (const point of points) {
        for (const candidate of candidates) {
            const offset = candidate - point;
            const distance = Math.abs(offset);
            if (distance > SNAP_TOLERANCE) {
                continue;
            }
            if (!best || distance < Math.abs(best.offset)) {
                best = { offset, guide: candidate };
            }
        }
    }
    return best;
}

export function CanvasComponent({ component, isSelected, theme }: CanvasComponentProps) {
    const { state, dispatch, selectComponents, updateComponent, snapshotTransform, setSnapGuides, clearSnapGuides } = useScreen();
    const { config, selectedIds } = state;
    const [isDragging, setIsDragging] = useState(false);
    const [isResizing, setIsResizing] = useState(false);
    const startPos = useRef({ x: 0, y: 0 });
    const startSize = useRef({ width: 0, height: 0 });
    const startCompPos = useRef({ x: 0, y: 0 });
    const resizeDirection = useRef<ResizeDirection | null>(null);

    const handleMouseDown = useCallback((e: React.MouseEvent) => {
        if (component.locked) return;
        e.stopPropagation();

        const groupedIds = component.groupId
            ? config.components.filter((item) => item.groupId === component.groupId).map((item) => item.id)
            : [];

        const moveIds = groupedIds.length > 0
            ? groupedIds
            : (selectedIds.includes(component.id) ? selectedIds : [component.id]);

        selectComponents(moveIds);
        setIsDragging(true);
        startPos.current = { x: e.clientX, y: e.clientY };
        startCompPos.current = { x: component.x, y: component.y };
        const startPositions = new Map(
            moveIds.map((id) => {
                const target = config.components.find((item) => item.id === id);
                return [id, { x: target?.x ?? 0, y: target?.y ?? 0 }] as const;
            }),
        );

        const handleMouseMove = (moveEvent: MouseEvent) => {
            const deltaX = moveEvent.clientX - startPos.current.x;
            const deltaY = moveEvent.clientY - startPos.current.y;

            if (moveIds.length > 1) {
                clearSnapGuides();
                dispatch({
                    type: 'MOVE_COMPONENTS',
                    payload: moveIds.map((id) => {
                        const start = startPositions.get(id) ?? { x: 0, y: 0 };
                        return {
                            id,
                            x: Math.max(0, start.x + deltaX),
                            y: Math.max(0, start.y + deltaY),
                        };
                    }),
                });
                return;
            }

            const tentativeX = Math.max(0, startCompPos.current.x + deltaX);
            const tentativeY = Math.max(0, startCompPos.current.y + deltaY);
            const otherComponents = config.components.filter((item) => item.id !== component.id);
            const xCandidates = [
                0,
                config.width / 2,
                config.width,
                ...otherComponents.flatMap((item) => [item.x, item.x + item.width / 2, item.x + item.width]),
            ];
            const yCandidates = [
                0,
                config.height / 2,
                config.height,
                ...otherComponents.flatMap((item) => [item.y, item.y + item.height / 2, item.y + item.height]),
            ];
            const xSnap = findSnapOffset(
                [tentativeX, tentativeX + component.width / 2, tentativeX + component.width],
                xCandidates,
            );
            const ySnap = findSnapOffset(
                [tentativeY, tentativeY + component.height / 2, tentativeY + component.height],
                yCandidates,
            );
            const nextX = Math.max(0, tentativeX + (xSnap?.offset ?? 0));
            const nextY = Math.max(0, tentativeY + (ySnap?.offset ?? 0));
            setSnapGuides({
                x: xSnap ? [xSnap.guide] : [],
                y: ySnap ? [ySnap.guide] : [],
            });

            dispatch({
                type: 'MOVE_COMPONENT',
                payload: {
                    id: component.id,
                    x: nextX,
                    y: nextY,
                },
            });
        };

        const handleMouseUp = () => {
            setIsDragging(false);
            document.removeEventListener('mousemove', handleMouseMove);
            document.removeEventListener('mouseup', handleMouseUp);
            clearSnapGuides();
            snapshotTransform();
        };

        document.addEventListener('mousemove', handleMouseMove);
        document.addEventListener('mouseup', handleMouseUp);
    }, [clearSnapGuides, component.groupId, component.height, component.id, component.locked, component.width, component.x, component.y, config.components, config.height, config.width, dispatch, selectedIds, selectComponents, setSnapGuides, snapshotTransform]);

    const handleResizeStart = useCallback((e: React.MouseEvent, direction: ResizeDirection) => {
        if (component.locked) return;
        e.stopPropagation();

        const groupedIds = component.groupId
            ? config.components.filter((item) => item.groupId === component.groupId).map((item) => item.id)
            : [];
        const resizeIds = groupedIds.length > 1 ? groupedIds : [component.id];
        if (resizeIds.length > 1) {
            selectComponents(resizeIds);
        }

        setIsResizing(true);
        resizeDirection.current = direction;
        startPos.current = { x: e.clientX, y: e.clientY };
        startSize.current = { width: component.width, height: component.height };
        startCompPos.current = { x: component.x, y: component.y };
        const startRects = new Map(
            resizeIds.map((id) => {
                const target = config.components.find((item) => item.id === id);
                return [
                    id,
                    {
                        x: target?.x ?? 0,
                        y: target?.y ?? 0,
                        width: Math.max(1, target?.width ?? 1),
                        height: Math.max(1, target?.height ?? 1),
                    },
                ] as const;
            }),
        );

        const handleMouseMove = (moveEvent: MouseEvent) => {
            const deltaX = moveEvent.clientX - startPos.current.x;
            const deltaY = moveEvent.clientY - startPos.current.y;
            const dir = resizeDirection.current;

            if (resizeIds.length > 1) {
                const rects = resizeIds
                    .map((id) => ({ id, ...(startRects.get(id) ?? { x: 0, y: 0, width: 1, height: 1 }) }));
                const left0 = Math.min(...rects.map((item) => item.x));
                const top0 = Math.min(...rects.map((item) => item.y));
                const right0 = Math.max(...rects.map((item) => item.x + item.width));
                const bottom0 = Math.max(...rects.map((item) => item.y + item.height));

                let left = left0;
                let top = top0;
                let right = right0;
                let bottom = bottom0;

                if (dir?.includes('e')) right = Math.max(left + 50, right0 + deltaX);
                if (dir?.includes('w')) left = Math.min(right - 50, left0 + deltaX);
                if (dir?.includes('s')) bottom = Math.max(top + 50, bottom0 + deltaY);
                if (dir?.includes('n')) top = Math.min(bottom - 50, top0 + deltaY);

                const baseWidth = Math.max(1, right0 - left0);
                const baseHeight = Math.max(1, bottom0 - top0);
                const scaleX = (right - left) / baseWidth;
                const scaleY = (bottom - top) / baseHeight;

                dispatch({
                    type: 'TRANSFORM_COMPONENTS',
                    payload: rects.map((item) => ({
                        id: item.id,
                        x: Math.round(left + (item.x - left0) * scaleX),
                        y: Math.round(top + (item.y - top0) * scaleY),
                        width: Math.max(20, Math.round(item.width * scaleX)),
                        height: Math.max(20, Math.round(item.height * scaleY)),
                    })),
                });
                return;
            }

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
            clearSnapGuides();
            snapshotTransform();
        };

        document.addEventListener('mousemove', handleMouseMove);
        document.addEventListener('mouseup', handleMouseUp);
    }, [clearSnapGuides, component.groupId, component.id, component.locked, component.width, component.height, component.x, component.y, config.components, dispatch, selectComponents, snapshotTransform]);

    // Stable ref for config to avoid recreating callback on every config change
    const configRef = useRef(component.config);
    configRef.current = component.config;

    const handleConfigMeta = useCallback((meta: Record<string, unknown>) => {
        updateComponent(component.id, {
            config: { ...configRef.current, ...meta },
        });
    }, [component.id, updateComponent]);

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
            <ComponentRenderer component={component} mode="designer" theme={theme} onConfigMeta={handleConfigMeta} />

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
