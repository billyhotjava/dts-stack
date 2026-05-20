import { useCallback, useState, useRef } from 'react';
import { useScreen } from '../ScreenContext';
import { ComponentRenderer } from './ComponentRenderer';
import type { ScreenComponent, ScreenCustomTheme, ScreenTheme } from '../types';
import { collectContainerSubtreeIds } from '../componentHierarchy';
import { resolveComponentAppearanceStyle } from '../componentAppearance';
import { resolveInteractionScale, resolveScaledPointerDelta } from '../canvasInteraction';

interface CanvasComponentProps {
    component: ScreenComponent;
    isSelected: boolean;
    theme?: ScreenTheme;
    customTheme?: ScreenCustomTheme;
    fontFamily?: string;
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

function clampToBounds(value: number, min: number, max: number): number {
    if (!Number.isFinite(min) || !Number.isFinite(max)) {
        return value;
    }
    if (max < min) {
        return min;
    }
    return Math.max(min, Math.min(max, value));
}

function clampGroupDelta(
    components: ScreenComponent[],
    moveIds: string[],
    startPositions: Map<string, { x: number; y: number }>,
    deltaX: number,
    deltaY: number,
    canvasWidth: number,
    canvasHeight: number,
): { dx: number; dy: number } {
    const moveSet = new Set(moveIds);
    const compMap = new Map(components.map((item) => [item.id, item]));
    let minDx = Number.NEGATIVE_INFINITY;
    let maxDx = Number.POSITIVE_INFINITY;
    let minDy = Number.NEGATIVE_INFINITY;
    let maxDy = Number.POSITIVE_INFINITY;

    for (const id of moveIds) {
        const comp = compMap.get(id);
        const start = startPositions.get(id);
        if (!comp || !start) continue;

        minDx = Math.max(minDx, -start.x);
        minDy = Math.max(minDy, -start.y);
        maxDx = Math.min(maxDx, canvasWidth - (start.x + comp.width));
        maxDy = Math.min(maxDy, canvasHeight - (start.y + comp.height));

        if (comp.parentContainerId && !moveSet.has(comp.parentContainerId)) {
            const parent = compMap.get(comp.parentContainerId);
            if (parent) {
                minDx = Math.max(minDx, parent.x - start.x);
                minDy = Math.max(minDy, parent.y - start.y);
                maxDx = Math.min(maxDx, parent.x + parent.width - (start.x + comp.width));
                maxDy = Math.min(maxDy, parent.y + parent.height - (start.y + comp.height));
            }
        }
    }

    const dx = clampToBounds(deltaX, minDx, maxDx);
    const dy = clampToBounds(deltaY, minDy, maxDy);
    return { dx, dy };
}

export function CanvasComponent({ component, isSelected, theme, customTheme, fontFamily }: CanvasComponentProps) {
    const { state, dispatch, selectComponents, updateComponent, snapshotTransform, setSnapGuides, clearSnapGuides, editorReadonly } = useScreen();
    const { config, selectedIds } = state;
    const [isDragging, setIsDragging] = useState(false);
    const [isResizing, setIsResizing] = useState(false);
    const startPos = useRef({ x: 0, y: 0 });
    const startSize = useRef({ width: 0, height: 0 });
    const startCompPos = useRef({ x: 0, y: 0 });
    const resizeDirection = useRef<ResizeDirection | null>(null);
    const interactionScale = useRef(1);
    const activePointerId = useRef<number | null>(null);
    const activePointerTarget = useRef<HTMLElement | null>(null);

    const endPointerCapture = useCallback(() => {
        const target = activePointerTarget.current;
        const pointerId = activePointerId.current;
        if (target && pointerId !== null && target.hasPointerCapture?.(pointerId)) {
            target.releasePointerCapture(pointerId);
        }
        activePointerId.current = null;
        activePointerTarget.current = null;
    }, []);

    const handlePointerDown = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
        if (component.locked || editorReadonly) return;
        if (!e.isPrimary || activePointerId.current !== null) return;

        // Shift + 点击 = 切换选中(加入/移除多选),不开始拖拽
        // 与 Figma / Sketch / draw.io 行为一致
        const isShiftClick = e.shiftKey && !component.groupId;
        if (isShiftClick) {
            e.stopPropagation();
            e.preventDefault();
            const nextIds = selectedIds.includes(component.id)
                ? selectedIds.filter((id) => id !== component.id)
                : [...selectedIds, component.id];
            selectComponents(nextIds);
            return;
        }

        e.stopPropagation();
        e.preventDefault();

        const target = e.currentTarget;
        activePointerId.current = e.pointerId;
        activePointerTarget.current = target;
        target.setPointerCapture?.(e.pointerId);
        interactionScale.current = resolveInteractionScale(target, component.width);

        const groupedIds = component.groupId
            ? config.components.filter((item) => item.groupId === component.groupId).map((item) => item.id)
            : [];

        // 多选拖拽: 如果当前组件已在多选区,保持所有选中一起拖
        // 否则单选这个组件
        const seedIds = groupedIds.length > 0
            ? groupedIds
            : (selectedIds.includes(component.id) && selectedIds.length > 1
                ? selectedIds
                : [component.id]);
        const moveIds = collectContainerSubtreeIds(config.components, seedIds);

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

        const handlePointerMove = (moveEvent: PointerEvent) => {
            if (moveEvent.pointerId !== activePointerId.current) return;
            moveEvent.preventDefault();
            const delta = resolveScaledPointerDelta(
                startPos.current,
                { x: moveEvent.clientX, y: moveEvent.clientY },
                interactionScale.current,
            );
            const deltaX = delta.x;
            const deltaY = delta.y;

            if (moveIds.length > 1) {
                clearSnapGuides();
                const bounded = clampGroupDelta(
                    config.components,
                    moveIds,
                    startPositions,
                    deltaX,
                    deltaY,
                    config.width,
                    config.height,
                );
                dispatch({
                    type: 'MOVE_COMPONENTS',
                    payload: moveIds.map((id) => {
                        const start = startPositions.get(id) ?? { x: 0, y: 0 };
                        return {
                            id,
                            x: Math.max(0, start.x + bounded.dx),
                            y: Math.max(0, start.y + bounded.dy),
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
            let nextX = Math.max(0, tentativeX + (xSnap?.offset ?? 0));
            let nextY = Math.max(0, tentativeY + (ySnap?.offset ?? 0));
            if (component.parentContainerId) {
                const parent = config.components.find((item) => item.id === component.parentContainerId);
                if (parent) {
                    const maxX = parent.x + Math.max(0, parent.width - component.width);
                    const maxY = parent.y + Math.max(0, parent.height - component.height);
                    nextX = clampToBounds(nextX, parent.x, maxX);
                    nextY = clampToBounds(nextY, parent.y, maxY);
                }
            }
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

        const handlePointerEnd = (event: PointerEvent) => {
            if (event.pointerId !== activePointerId.current) return;
            setIsDragging(false);
            document.removeEventListener('pointermove', handlePointerMove);
            document.removeEventListener('pointerup', handlePointerEnd);
            document.removeEventListener('pointercancel', handlePointerEnd);
            clearSnapGuides();
            endPointerCapture();
            snapshotTransform();
        };

        document.addEventListener('pointermove', handlePointerMove, { passive: false });
        document.addEventListener('pointerup', handlePointerEnd);
        document.addEventListener('pointercancel', handlePointerEnd);
    }, [activePointerId, clearSnapGuides, component.groupId, component.height, component.id, component.locked, component.width, component.x, component.y, config.components, config.height, config.width, dispatch, editorReadonly, endPointerCapture, selectedIds, selectComponents, setSnapGuides, snapshotTransform]);

    const handleResizeStart = useCallback((e: React.PointerEvent<HTMLDivElement>, direction: ResizeDirection) => {
        if (component.locked || editorReadonly) return;
        if (!e.isPrimary || activePointerId.current !== null) return;
        e.stopPropagation();
        e.preventDefault();

        const handleElement = e.currentTarget;
        const target = handleElement.parentElement;
        activePointerId.current = e.pointerId;
        activePointerTarget.current = handleElement;
        handleElement.setPointerCapture?.(e.pointerId);
        interactionScale.current = resolveInteractionScale(target, component.width);

        const groupedIds = component.groupId
            ? config.components.filter((item) => item.groupId === component.groupId).map((item) => item.id)
            : [];
        const seedIds = groupedIds.length > 1 ? groupedIds : [component.id];
        const resizeIds = collectContainerSubtreeIds(config.components, seedIds);
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

        const handlePointerMove = (moveEvent: PointerEvent) => {
            if (moveEvent.pointerId !== activePointerId.current) return;
            moveEvent.preventDefault();
            const delta = resolveScaledPointerDelta(
                startPos.current,
                { x: moveEvent.clientX, y: moveEvent.clientY },
                interactionScale.current,
            );
            const deltaX = delta.x;
            const deltaY = delta.y;
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

            if (component.parentContainerId) {
                const parent = config.components.find((item) => item.id === component.parentContainerId);
                if (parent) {
                    const minX = parent.x;
                    const minY = parent.y;
                    const maxX = parent.x + parent.width;
                    const maxY = parent.y + parent.height;
                    newX = clampToBounds(newX, minX, maxX - 20);
                    newY = clampToBounds(newY, minY, maxY - 20);
                    const maxWidth = Math.max(20, maxX - newX);
                    const maxHeight = Math.max(20, maxY - newY);
                    newWidth = clampToBounds(newWidth, 20, maxWidth);
                    newHeight = clampToBounds(newHeight, 20, maxHeight);
                }
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

        const handlePointerEnd = (event: PointerEvent) => {
            if (event.pointerId !== activePointerId.current) return;
            setIsResizing(false);
            resizeDirection.current = null;
            document.removeEventListener('pointermove', handlePointerMove);
            document.removeEventListener('pointerup', handlePointerEnd);
            document.removeEventListener('pointercancel', handlePointerEnd);
            clearSnapGuides();
            endPointerCapture();
            snapshotTransform();
        };

        document.addEventListener('pointermove', handlePointerMove, { passive: false });
        document.addEventListener('pointerup', handlePointerEnd);
        document.addEventListener('pointercancel', handlePointerEnd);
    }, [clearSnapGuides, component.groupId, component.id, component.locked, component.width, component.height, component.x, component.y, config.components, dispatch, editorReadonly, endPointerCapture, selectComponents, snapshotTransform]);

    // Stable ref for config to avoid recreating callback on every config change
    const configRef = useRef(component.config);
    configRef.current = component.config;

    const handleConfigMeta = useCallback((meta: Record<string, unknown>) => {
        updateComponent(component.id, {
            config: { ...configRef.current, ...meta },
        });
    }, [component.id, updateComponent]);

    const handleKeyDown = useCallback((event: React.KeyboardEvent<HTMLDivElement>) => {
        if (event.key !== 'Enter' && event.key !== ' ') {
            return;
        }
        event.preventDefault();
        event.stopPropagation();
        selectComponents([component.id]);
    }, [component.id, selectComponents]);

    const resizeHandles: ResizeDirection[] = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'];

    return (
        <div
            data-component-id={component.id}
            data-testid={`analytics-screen-component-${component.id}`}
            role="button"
            tabIndex={0}
            aria-label={`${component.name || component.type} 组件`}
            aria-selected={isSelected}
            aria-disabled={component.locked || editorReadonly}
            className={`absolute select-none ${isSelected ? 'outline-2 outline-[var(--color-primary)] outline-offset-2' : ''} ${component.locked || editorReadonly ? 'cursor-not-allowed' : ''}`}
            style={{
                left: component.x,
                top: component.y,
                width: component.width,
                height: component.height,
                zIndex: component.zIndex,
                touchAction: 'none',
                fontFamily,
                cursor: isDragging ? 'grabbing' : isResizing ? 'default' : (component.locked || editorReadonly ? 'not-allowed' : 'move'),
                ...resolveComponentAppearanceStyle(component.config),
            }}
            onPointerDown={handlePointerDown}
            onKeyDown={handleKeyDown}
        >
            <ComponentRenderer
                component={component}
                mode="designer"
                theme={theme}
                customTheme={customTheme}
                fontFamily={fontFamily}
                onConfigMeta={handleConfigMeta}
            />

            {isSelected && !component.locked && !editorReadonly && (
                <>
                    {resizeHandles.map((dir) => (
                        <div
                            key={dir}
                            className={`resize-handle ${dir}`}
                            onPointerDown={(e) => handleResizeStart(e, dir)}
                            style={{ touchAction: 'none' }}
                        />
                    ))}
                </>
            )}
        </div>
    );
}
