import { useCallback, useEffect, useMemo, useRef, useState, type MouseEvent, type MutableRefObject } from 'react';
import { useDrop } from 'react-dnd';
import apiClient from '@/api/apiClient';
import { useScreen } from '../ScreenContext';
import { generateId } from '../ScreenContext';
import { CanvasComponent } from './CanvasComponent';
import type { ComponentItem, ScreenComponent } from '../types';
import { buildComponentMap, isComponentEffectivelyVisible } from '../componentHierarchy';
import { applyChartPresetDefaults, isChartComponentType } from '../chartPresets';
import { safeCssBackgroundUrl } from '../sanitize';
import { applyThemeCssVariables } from '../themes/screenCssVariables';
import { resolveScreenTheme, getThemeTokens, applyThemeToComponents } from '../screenThemes';

type ContextMenuState = {
    x: number;
    y: number;
    componentId?: string;
} | null;

type ScreenFontAsset = {
    fontFamily: string;
    url: string;
    format?: string;
};

type ScreenFontResponse = ScreenFontAsset[] | { data?: ScreenFontAsset[] };

function resolveScreenFontAssets(response: ScreenFontResponse): ScreenFontAsset[] {
    const items = Array.isArray(response) ? response : response.data;
    if (!Array.isArray(items)) return [];
    return items.filter((item) => (
        item
        && typeof item.fontFamily === 'string'
        && typeof item.url === 'string'
    ));
}

export function DesignerCanvas() {
    const { state, addComponent, selectComponents, snapGuides, dispatch, deleteComponents, copyComponents, pasteComponents, duplicateSelected, undo, redo, clipboard, editorReadonly } = useScreen();
    const { config, selectedIds, zoom, showGrid } = state;
    const containerRef = useRef<HTMLDivElement>(null);
    const canvasRef = useRef<HTMLDivElement>(null);

    // Apply theme CSS Variables to canvas so components pick up theme changes
    const editorTheme = resolveScreenTheme(config.theme, config.backgroundColor);
    const editorThemeTokens = getThemeTokens(editorTheme, config.customTheme);
    // 画布底色优先使用用户自定义 (config.backgroundColor)；未设置/空时跟主题走
    // 以避免"浅色主题 + 深色画布"视觉错位。
    const resolvedCanvasBackground = (typeof config.backgroundColor === 'string' && config.backgroundColor.trim().length > 0)
        ? config.backgroundColor
        : editorThemeTokens.canvasBackground;
    useEffect(() => {
        if (canvasRef.current) applyThemeCssVariables(canvasRef.current, editorTheme, config.customTheme);
    }, [editorTheme, config.customTheme]);

    // Inject @font-face for uploaded custom fonts so they're available everywhere
    useEffect(() => {
        const styleId = 'screen-custom-fonts';
        apiClient.get<ScreenFontResponse>({ url: '/infra/screen-fonts' })
            .then(res => {
                const fonts = resolveScreenFontAssets(res);
                if (!Array.isArray(fonts) || fonts.length === 0) return;
                let el = document.getElementById(styleId) as HTMLStyleElement | null;
                if (!el) { el = document.createElement('style'); el.id = styleId; document.head.appendChild(el); }
                const formatMap: Record<string, string> = { ttf: 'truetype', otf: 'opentype', woff: 'woff', woff2: 'woff2' };
                el.textContent = fonts.map((f) =>
                    `@font-face { font-family: "${f.fontFamily}"; src: url("${f.url}") format("${formatMap[f.format] || 'truetype'}"); font-display: swap; }`
                ).join('\n');
            })
            .catch(() => {});
        return () => { document.getElementById(styleId)?.remove(); };
    }, []);
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
        const resizeObserver = typeof ResizeObserver !== 'undefined'
            ? new ResizeObserver(onResize)
            : null;
        if (node && resizeObserver) {
            resizeObserver.observe(node);
        }
        window.addEventListener('resize', onResize);
        return () => {
            window.removeEventListener('resize', onResize);
            resizeObserver?.disconnect();
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
            if (editorReadonly) {
                return;
            }
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

                const rawConfig = isChartComponentType(item.type)
                    ? applyChartPresetDefaults(
                        { ...item.defaultConfig },
                        item.defaultWidth <= 360 || item.defaultHeight <= 260 ? 'compact' : 'business',
                    )
                    : { ...item.defaultConfig };

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
                    config: rawConfig,
                    parentContainerId: targetContainer?.id,
                };

                // Apply current theme colors to the new component so text/lines
                // are visible on the dark canvas (e.g., white text, light axes)
                const [themed] = applyThemeToComponents([newComponent], config.theme, 'safe');
                addComponent(themed);
            }
        },
        collect: (monitor) => ({
            isOver: monitor.isOver(),
        }),
    }), [zoom, fitScale, addComponent, editorReadonly]);

    const handleCanvasClick = useCallback((e: MouseEvent<HTMLDivElement>) => {
        // Deselect all when clicking on empty canvas area
        if (e.target === e.currentTarget || (e.target as HTMLElement).classList.contains('canvas-grid')) {
            selectComponents([]);
        }
    }, [selectComponents]);

    // Right-click context menu
    const [ctxMenu, setCtxMenu] = useState<ContextMenuState>(null);
    const closeMenu = useCallback(() => setCtxMenu(null), []);

    useEffect(() => {
        if (ctxMenu) {
            const handler = () => setCtxMenu(null);
            window.addEventListener('click', handler);
            window.addEventListener('scroll', handler, true);
            return () => {
                window.removeEventListener('click', handler);
                window.removeEventListener('scroll', handler, true);
            };
        }
    }, [ctxMenu]);

    const handleContextMenu = useCallback((e: MouseEvent<HTMLDivElement>) => {
        e.preventDefault();
        e.stopPropagation();
        // Find which component was right-clicked (walk up from target)
        let node = e.target as HTMLElement | null;
        let componentId: string | undefined;
        while (node && node !== e.currentTarget) {
            if (node.dataset?.componentId) {
                componentId = node.dataset.componentId;
                break;
            }
            node = node.parentElement;
        }
        if (componentId && !selectedIds.includes(componentId)) {
            selectComponents([componentId]);
        }
        setCtxMenu({ x: e.clientX, y: e.clientY, componentId });
    }, [selectComponents, selectedIds]);

    // Phase 1.4: useMemo for visible sorted components
    const visibleSortedComponents = useMemo(() => {
        const componentMap = buildComponentMap(config.components);
        return config.components
            .filter((comp) => comp.visible && isComponentEffectivelyVisible(comp, componentMap))
            .sort((a, b) => a.zIndex - b.zIndex);
    }, [config.components]);

    const scale = Math.max(0.1, (zoom / 100) * fitScale);

    // Ruler tick marks
    const rulerStep = scale >= 0.5 ? 100 : scale >= 0.25 ? 200 : 400;
    const hTicks = useMemo(() => {
        const ticks: number[] = [];
        for (let x = 0; x <= config.width; x += rulerStep) ticks.push(x);
        return ticks;
    }, [config.width, rulerStep]);
    const vTicks = useMemo(() => {
        const ticks: number[] = [];
        for (let y = 0; y <= config.height; y += rulerStep) ticks.push(y);
        return ticks;
    }, [config.height, rulerStep]);

    return (
        <div
            data-testid="analytics-screen-canvas-shell"
            className="flex-1 min-h-0 overflow-auto grid place-items-center p-10 relative"
            ref={containerRef}
            style={{ background: 'var(--color-surface)' }}
        >
            {editorReadonly && (
                <div
                    data-testid="analytics-screen-canvas-readonly-banner"
                    className="absolute top-3 left-1/2 -translate-x-1/2 z-20 rounded-md border px-3 py-1.5 text-xs"
                    style={{
                        color: '#fbbf24',
                        borderColor: 'rgba(251,191,36,0.38)',
                        background: 'rgba(24,24,27,0.88)',
                        boxShadow: '0 10px 24px rgba(0,0,0,0.24)',
                    }}
                >
                    只读模式：当前大屏由其他用户编辑，画布操作已禁用。
                </div>
            )}
            {/* Horizontal ruler */}
            <div className="canvas-ruler canvas-ruler--h shrink-0 relative overflow-hidden h-[22px]" style={{ paddingLeft: 30, background: 'var(--color-surface)', borderBottom: '1px solid var(--color-border)' }}>
                <div style={{ position: 'relative', width: config.width * scale, height: '100%', overflow: 'hidden' }}>
                    {hTicks.map(x => (
                        <span key={x} className="canvas-ruler-tick absolute text-[9px] pointer-events-none whitespace-nowrap bottom-0.5" style={{ left: x * scale, color: 'var(--color-text-tertiary)' }}>{x}</span>
                    ))}
                </div>
            </div>
            <div className="flex flex-1 min-h-0">
                {/* Vertical ruler */}
                <div className="canvas-ruler canvas-ruler--v shrink-0 relative overflow-hidden w-[30px]" style={{ background: 'var(--color-surface)', borderRight: '1px solid var(--color-border)' }}>
                    <div style={{ position: 'relative', height: config.height * scale, width: '100%', overflow: 'hidden' }}>
                        {vTicks.map(y => (
                            <span key={y} className="canvas-ruler-tick absolute text-[9px] pointer-events-none whitespace-nowrap left-0.5" style={{ top: y * scale, writingMode: 'vertical-lr', textOrientation: 'mixed', color: 'var(--color-text-tertiary)' }}>{y}</span>
                        ))}
                    </div>
                </div>
            <div className="flex-1 overflow-auto relative">
            <div
                className="relative shadow-[0_4px_20px_rgba(0,0,0,0.5)] origin-center"
                style={{
                    width: config.width * scale,
                    height: config.height * scale,
                }}
            >
                <div
                    ref={(node) => {
                        drop(node);
                        (canvasRef as MutableRefObject<HTMLDivElement | null>).current = node;
                    }}
                    data-testid="analytics-screen-canvas"
                    data-scale={scale}
                    aria-readonly={editorReadonly}
                    className="relative overflow-hidden"
                    style={{
                        width: config.width,
                        height: config.height,
                        backgroundColor: resolvedCanvasBackground,
                        backgroundImage: safeCssBackgroundUrl(config.backgroundImage),
                        backgroundSize: 'cover',
                        backgroundPosition: 'center',
                        backgroundRepeat: 'no-repeat',
                        transform: `scale(${scale})`,
                        transformOrigin: 'top left',
                    }}
                    onClick={handleCanvasClick}
                    onContextMenu={handleContextMenu}
                >
                    {showGrid && <div className="canvas-grid absolute inset-0 pointer-events-none" />}

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
                                left: x - 1,
                                top: 0,
                                width: 2,
                                height: config.height,
                                background: 'rgba(14, 165, 233, 0.9)',
                                boxShadow: '0 0 4px rgba(14,165,233,0.5)',
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
                                top: y - 1,
                                width: config.width,
                                height: 2,
                                background: 'rgba(14, 165, 233, 0.9)',
                                boxShadow: '0 0 4px rgba(14,165,233,0.5)',
                                pointerEvents: 'none',
                                zIndex: 9999,
                            }}
                        />
                    ))}

                    {isOver && (
                        <div
                            className="absolute inset-0 pointer-events-none"
                            style={{
                                backgroundColor: 'rgba(99, 102, 241, 0.1)',
                                border: '2px dashed var(--color-primary)',
                            }}
                        />
                    )}
                </div>
            </div>

            </div>{/* end canvas-scroll-area */}
            </div>{/* end flex row (ruler + canvas) */}

            {/* Right-click context menu */}
            {ctxMenu && (
                <div
                    className="canvas-context-menu"
                    style={{ left: ctxMenu.x, top: ctxMenu.y }}
                    onClick={(e) => e.stopPropagation()}
                >
                    {ctxMenu.componentId && selectedIds.length > 0 ? (
                        <>
                            <button type="button" className="ctx-menu-item" onClick={() => { duplicateSelected(); closeMenu(); }} disabled={editorReadonly}>
                                复制组件
                            </button>
                            <button type="button" className="ctx-menu-item" onClick={() => { copyComponents(); closeMenu(); }}>
                                拷贝 (Ctrl+C)
                            </button>
                            <button type="button" className="ctx-menu-item" onClick={() => { deleteComponents(selectedIds); closeMenu(); }} disabled={editorReadonly}>
                                删除
                            </button>
                            <div className="ctx-menu-divider" />
                            <button type="button" className="ctx-menu-item" disabled={editorReadonly} onClick={() => {
                                selectedIds.forEach(id => {
                                    const c = config.components.find(c => c.id === id);
                                    if (c) dispatch({ type: 'REORDER_LAYER', payload: { id, direction: 'top' } });
                                });
                                closeMenu();
                            }}>
                                置顶
                            </button>
                            <button type="button" className="ctx-menu-item" disabled={editorReadonly} onClick={() => {
                                selectedIds.forEach(id => {
                                    dispatch({ type: 'REORDER_LAYER', payload: { id, direction: 'bottom' } });
                                });
                                closeMenu();
                            }}>
                                置底
                            </button>
                            <div className="ctx-menu-divider" />
                            <button type="button" className="ctx-menu-item" disabled={editorReadonly} onClick={() => {
                                selectedIds.forEach(id => {
                                    const c = config.components.find(c => c.id === id);
                                    if (c) {
                                        dispatch({ type: 'UPDATE_COMPONENT', payload: { id, updates: { locked: !c.locked } } });
                                    }
                                });
                                closeMenu();
                            }}>
                                {config.components.find(c => c.id === selectedIds[0])?.locked ? '解锁' : '锁定'}
                            </button>
                            <button type="button" className="ctx-menu-item" disabled={editorReadonly} onClick={() => {
                                selectedIds.forEach(id => {
                                    const c = config.components.find(c => c.id === id);
                                    if (c) {
                                        dispatch({ type: 'UPDATE_COMPONENT', payload: { id, updates: { visible: !c.visible } } });
                                    }
                                });
                                closeMenu();
                            }}>
                                {config.components.find(c => c.id === selectedIds[0])?.visible ? '隐藏' : '显示'}
                            </button>
                        </>
                    ) : (
                        <>
                            <button type="button" className="ctx-menu-item" onClick={() => { pasteComponents(); closeMenu(); }} disabled={!clipboard?.length || editorReadonly}>
                                粘贴 (Ctrl+V)
                            </button>
                            <div className="ctx-menu-divider" />
                            <button type="button" className="ctx-menu-item" onClick={() => { undo(); closeMenu(); }} disabled={editorReadonly}>
                                撤销 (Ctrl+Z)
                            </button>
                            <button type="button" className="ctx-menu-item" onClick={() => { redo(); closeMenu(); }} disabled={editorReadonly}>
                                重做 (Ctrl+Y)
                            </button>
                            <div className="ctx-menu-divider" />
                            <button type="button" className="ctx-menu-item" onClick={() => { selectComponents(config.components.map(c => c.id)); closeMenu(); }}>
                                全选
                            </button>
                        </>
                    )}
                </div>
            )}
        </div>
    );
}
