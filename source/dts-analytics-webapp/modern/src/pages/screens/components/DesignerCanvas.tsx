import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useDrop } from 'react-dnd';
import { useScreen } from '../ScreenContext';
import { generateId } from '../ScreenContext';
import { CanvasComponent } from './CanvasComponent';
import type { ComponentItem, ScreenComponent } from '../types';
import { buildComponentMap, isComponentEffectivelyVisible } from '../componentHierarchy';
import { applyChartPresetDefaults, isChartComponentType } from '../chartPresets';
import { safeCssBackgroundUrl } from '../sanitize';

export function DesignerCanvas() {
    const { state, addComponent, selectComponents, snapGuides, dispatch } = useScreen();
    const { config, selectedIds, zoom, showGrid } = state;
    const containerRef = useRef<HTMLDivElement>(null);
    const canvasRef = useRef<HTMLDivElement>(null);
    const [fitScale, setFitScale] = useState(1);

    // Phase 4.4: resize debounce with requestAnimationFrame
    useEffect(() => {
        let rafId = 0;
        const updateFitScale = () => {
            const node = containerRef.current;
            if (!node) {
                return;
            }
            const availableWidth = Math.max(node.clientWidth - 24, 320);
            const availableHeight = Math.max(node.clientHeight - 24, 240);
            const baseWidth = Math.max(config.width || 1920, 1);
            const baseHeight = Math.max(config.height || 1080, 1);
            const next = Math.max(0.1, Math.min(1, availableWidth / baseWidth, availableHeight / baseHeight));
            setFitScale(next);
        };
        const onResize = () => {
            cancelAnimationFrame(rafId);
            rafId = requestAnimationFrame(updateFitScale);
        };

        updateFitScale();
        window.addEventListener('resize', onResize);
        return () => {
            window.removeEventListener('resize', onResize);
            cancelAnimationFrame(rafId);
        };
    }, [config.width, config.height]);

    useEffect(() => {
        const node = containerRef.current;
        if (!node) return;
        const clampZoom = (value: number) => Math.min(300, Math.max(25, Math.round(value)));
        const handleWheel = (event: WheelEvent) => {
            if (!event.ctrlKey && !event.metaKey) {
                return;
            }
            event.preventDefault();
            const step = event.deltaY > 0 ? -5 : 5;
            const next = clampZoom((Number(state.zoom) || 100) + step);
            dispatch({ type: 'SET_ZOOM', payload: next });
        };
        node.addEventListener('wheel', handleWheel, { passive: false });
        return () => node.removeEventListener('wheel', handleWheel);
    }, [dispatch, state.zoom]);

    // Phase 4.1: use ref to hold components, so useDrop doesn't re-register on every state change
    const componentsRef = useRef(config.components);
    componentsRef.current = config.components;

    const [{ isOver }, drop] = useDrop(() => ({
        accept: 'COMPONENT',
        drop: (item: ComponentItem, monitor) => {
            const offset = monitor.getClientOffset();
            const canvasRect = canvasRef.current?.getBoundingClientRect();

            if (offset && canvasRect) {
                // Calculate position relative to canvas, accounting for zoom
                const scale = Math.max(0.1, (zoom / 100) * fitScale);
                const x = Math.round((offset.x - canvasRect.left) / scale);
                const y = Math.round((offset.y - canvasRect.top) / scale);
                const dropX = x - item.defaultWidth / 2;
                const dropY = y - item.defaultHeight / 2;

                const currentComponents = componentsRef.current;
                const visibilityMap = buildComponentMap(currentComponents);
                const targetContainer = [...currentComponents]
                    .filter((comp) => comp.visible && comp.type === 'container' && isComponentEffectivelyVisible(comp, visibilityMap))
                    .sort((a, b) => b.zIndex - a.zIndex)
                    .find((container) => (
                        x >= container.x
                        && x <= container.x + container.width
                        && y >= container.y
                        && y <= container.y + container.height
                    ));

                const boundedX = targetContainer
                    ? Math.max(
                        targetContainer.x,
                        Math.min(dropX, targetContainer.x + Math.max(0, targetContainer.width - item.defaultWidth)),
                    )
                    : Math.max(0, dropX);
                const boundedY = targetContainer
                    ? Math.max(
                        targetContainer.y,
                        Math.min(dropY, targetContainer.y + Math.max(0, targetContainer.height - item.defaultHeight)),
                    )
                    : Math.max(0, dropY);

                const newComponent: ScreenComponent = {
                    id: generateId(),
                    type: item.type,
                    name: item.name,
                    x: Math.round(boundedX),
                    y: Math.round(boundedY),
                    width: item.defaultWidth,
                    height: item.defaultHeight,
                    zIndex: currentComponents.length + 1,
                    locked: false,
                    visible: true,
                    config: isChartComponentType(item.type)
                        ? applyChartPresetDefaults(
                            { ...item.defaultConfig },
                            item.defaultWidth <= 360 || item.defaultHeight <= 260 ? 'compact' : 'business',
                        )
                        : { ...item.defaultConfig },
                    parentContainerId: targetContainer?.id,
                };

                addComponent(newComponent);
            }
        },
        collect: (monitor) => ({
            isOver: monitor.isOver(),
        }),
    }), [zoom, fitScale, addComponent]);

    const handleCanvasClick = useCallback((e: React.MouseEvent) => {
        // Deselect all when clicking on empty canvas area
        if (e.target === e.currentTarget || (e.target as HTMLElement).classList.contains('canvas-grid')) {
            selectComponents([]);
        }
    }, [selectComponents]);

    // Phase 1.4: useMemo for visible sorted components
    const visibleSortedComponents = useMemo(() => {
        const componentMap = buildComponentMap(config.components);
        return config.components
            .filter((comp) => comp.visible && isComponentEffectivelyVisible(comp, componentMap))
            .sort((a, b) => a.zIndex - b.zIndex);
    }, [config.components]);

    const scale = Math.max(0.1, (zoom / 100) * fitScale);

    return (
        <div className="canvas-container" ref={containerRef}>
            <div
                className="canvas-wrapper"
                style={{
                    width: config.width * scale,
                    height: config.height * scale,
                }}
            >
                <div
                    ref={(node) => {
                        drop(node);
                        (canvasRef as React.MutableRefObject<HTMLDivElement | null>).current = node;
                    }}
                    className="canvas"
                    style={{
                        width: config.width,
                        height: config.height,
                        backgroundColor: config.backgroundColor,
                        backgroundImage: safeCssBackgroundUrl(config.backgroundImage),
                        backgroundSize: 'cover',
                        backgroundPosition: 'center',
                        transform: `scale(${scale})`,
                        transformOrigin: 'top left',
                    }}
                    onClick={handleCanvasClick}
                >
                    {showGrid && <div className="canvas-grid" />}

                    {visibleSortedComponents.map((component) => (
                        <CanvasComponent
                            key={component.id}
                            component={component}
                            isSelected={selectedIds.includes(component.id)}
                            theme={config.theme}
                        />
                    ))}

                    {snapGuides.x.map((x, idx) => (
                        <div
                            key={`snap-x-${idx}`}
                            style={{
                                position: 'absolute',
                                left: x,
                                top: 0,
                                width: 1,
                                height: config.height,
                                background: 'rgba(14, 165, 233, 0.9)',
                                boxShadow: '0 0 0 1px rgba(14,165,233,0.2)',
                                pointerEvents: 'none',
                                zIndex: 9999,
                            }}
                        />
                    ))}
                    {snapGuides.y.map((y, idx) => (
                        <div
                            key={`snap-y-${idx}`}
                            style={{
                                position: 'absolute',
                                left: 0,
                                top: y,
                                width: config.width,
                                height: 1,
                                background: 'rgba(14, 165, 233, 0.9)',
                                boxShadow: '0 0 0 1px rgba(14,165,233,0.2)',
                                pointerEvents: 'none',
                                zIndex: 9999,
                            }}
                        />
                    ))}

                    {isOver && (
                        <div
                            style={{
                                position: 'absolute',
                                inset: 0,
                                backgroundColor: 'rgba(99, 102, 241, 0.1)',
                                border: '2px dashed var(--color-primary)',
                                pointerEvents: 'none',
                            }}
                        />
                    )}
                </div>
            </div>
        </div>
    );
}
