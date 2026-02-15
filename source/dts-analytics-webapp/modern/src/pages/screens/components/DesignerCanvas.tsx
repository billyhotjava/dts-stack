import { useCallback, useRef } from 'react';
import { useDrop } from 'react-dnd';
import { useScreen } from '../ScreenContext';
import { CanvasComponent } from './CanvasComponent';
import type { ComponentItem, ScreenComponent } from '../types';

function generateId(): string {
    return `comp_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
}

export function DesignerCanvas() {
    const { state, addComponent, selectComponents, snapGuides } = useScreen();
    const { config, selectedIds, zoom, showGrid } = state;
    const canvasRef = useRef<HTMLDivElement>(null);

    const [{ isOver }, drop] = useDrop(() => ({
        accept: 'COMPONENT',
        drop: (item: ComponentItem, monitor) => {
            const offset = monitor.getClientOffset();
            const canvasRect = canvasRef.current?.getBoundingClientRect();

            if (offset && canvasRect) {
                // Calculate position relative to canvas, accounting for zoom
                const scale = zoom / 100;
                const x = Math.round((offset.x - canvasRect.left) / scale);
                const y = Math.round((offset.y - canvasRect.top) / scale);

                const newComponent: ScreenComponent = {
                    id: generateId(),
                    type: item.type,
                    name: item.name,
                    x: Math.max(0, x - item.defaultWidth / 2),
                    y: Math.max(0, y - item.defaultHeight / 2),
                    width: item.defaultWidth,
                    height: item.defaultHeight,
                    zIndex: config.components.length + 1,
                    locked: false,
                    visible: true,
                    config: { ...item.defaultConfig },
                };

                addComponent(newComponent);
            }
        },
        collect: (monitor) => ({
            isOver: monitor.isOver(),
        }),
    }), [zoom, config.components.length, addComponent]);

    const handleCanvasClick = useCallback((e: React.MouseEvent) => {
        // Deselect all when clicking on empty canvas area
        if (e.target === e.currentTarget || (e.target as HTMLElement).classList.contains('canvas-grid')) {
            selectComponents([]);
        }
    }, [selectComponents]);

    const scale = zoom / 100;

    return (
        <div className="canvas-container">
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
                        backgroundImage: config.backgroundImage ? `url(${config.backgroundImage})` : undefined,
                        backgroundSize: 'cover',
                        backgroundPosition: 'center',
                        transform: `scale(${scale})`,
                        transformOrigin: 'top left',
                    }}
                    onClick={handleCanvasClick}
                >
                    {showGrid && <div className="canvas-grid" />}

                    {config.components
                        .filter((comp) => comp.visible)
                        .sort((a, b) => a.zIndex - b.zIndex)
                        .map((component) => (
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
