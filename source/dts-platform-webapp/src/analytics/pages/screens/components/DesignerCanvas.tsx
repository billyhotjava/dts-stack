import { useCallback, useEffect, useMemo, useRef, useState, type MouseEvent, type MutableRefObject } from 'react';
import { useDrop } from 'react-dnd';
import { useScreen } from '../ScreenContext';
import { generateId } from '../ScreenContext';
import { CanvasComponent } from './CanvasComponent';
import type { ComponentItem, ScreenComponent } from '../types';
import { buildComponentMap, isComponentEffectivelyVisible } from '../componentHierarchy';
import { applyChartPresetDefaults, isChartComponentType } from '../chartPresets';
import { safeCssBackgroundUrl } from '../sanitize';
import { applyThemeCssVariables } from '../themes/screenCssVariables';
import { resolveScreenTheme, getThemeTokens, applyThemeToComponents } from '../screenThemes';
import { resolveScreenFontFamily } from '../screenTypography';
import { useScreenFontFaces } from '../hooks/useScreenFontFaces';

type ContextMenuState = {
    x: number;
    y: number;
    componentId?: string;
} | null;

export function DesignerCanvas() {
    const { state, addComponent, selectComponents, snapGuides, dispatch, deleteComponents, copyComponents, pasteComponents, duplicateSelected, undo, redo, clipboard, editorReadonly } = useScreen();
    const { config, selectedIds, zoom, showGrid } = state;
    const containerRef = useRef<HTMLDivElement>(null);
    const canvasRef = useRef<HTMLDivElement>(null);

    // Apply theme CSS Variables to canvas so components pick up theme changes
    const editorTheme = resolveScreenTheme(config.theme, config.backgroundColor);
    const editorThemeTokens = getThemeTokens(editorTheme, config.customTheme);
    const screenFontFamily = resolveScreenFontFamily(config.fontFamily ?? config.customTheme?.fontFamily);
    // 画布底色优先使用用户自定义 (config.backgroundColor)；未设置/空时跟主题走
    // 以避免"浅色主题 + 深色画布"视觉错位。
    const resolvedCanvasBackground = (typeof config.backgroundColor === 'string' && config.backgroundColor.trim().length > 0)
        ? config.backgroundColor
        : editorThemeTokens.canvasBackground;
    useEffect(() => {
        if (!canvasRef.current) return;
        applyThemeCssVariables(canvasRef.current, editorTheme, config.customTheme);
        canvasRef.current.style.setProperty('--screen-font-family', screenFontFamily);
    }, [editorTheme, config.customTheme, screenFontFamily]);
    useScreenFontFaces();
    const [fitScale, setFitScale] = useState(1);

    // Phase 4.4 + UI 抖动修复:
    // - resize debounce with requestAnimationFrame
    // - 阈值过滤(避免滚动条 ±16px 抖动反复触发 fitScale)
    // - scale 量化到 3 位小数,防止浮点精度引起的"无意义微更新"
    // - 修改属性(颜色/标题等)时,组件重渲可能让 ResizeObserver 偶发触发,
    //   仅当尺寸/容器变化超过 2% 时才认为是真实 resize
    useEffect(() => {
        let rafId = 0;
        const containerNode = containerRef.current;
        const quantize = (v: number) => Math.round(v * 1000) / 1000;
        const updateFitScale = () => {
            if (!containerNode) {
                return;
            }
            const availableWidth = Math.max(containerNode.clientWidth - 24, 320);
            const availableHeight = Math.max(containerNode.clientHeight - 24, 240);
            const baseWidth = Math.max(config.width || 1920, 1);
            const baseHeight = Math.max(config.height || 1080, 1);
            const raw = Math.max(0.1, Math.min(1, availableWidth / baseWidth, availableHeight / baseHeight));
            const next = quantize(raw);
            // 阈值过滤: 仅在变化 ≥ 2% 时才更新(避免滚动条/属性面板 layout 抖动连锁反应)
            setFitScale((prev) => (Math.abs(next - prev) >= 0.02 ? next : prev));
        };
        const onResize = () => {
            cancelAnimationFrame(rafId);
            rafId = requestAnimationFrame(updateFitScale);
        };

        updateFitScale();
        const resizeObserver = typeof ResizeObserver !== 'undefined'
            ? new ResizeObserver(onResize)
            : null;
        if (containerNode && resizeObserver) {
            resizeObserver.observe(containerNode);
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
                // 量化到 3 位小数,确保 scale 在 fitScale 与 zoom 不变时严格相等,
    // 否则 React 每次重渲都会产生新的浮点 width/height,触发不必要的 layout
    const scale = Math.round(Math.max(0.1, (zoom / 100) * fitScale) * 1000) / 1000;
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
                addComponent({ ...newComponent, config: themed?.config ?? newComponent.config });
            }
        },
        collect: (monitor) => ({
            isOver: monitor.isOver(),
        }),
    }), [zoom, fitScale, addComponent, editorReadonly]);

    // scale 提前声明 — 供框选/坐标换算使用
    // 用同样的量化方式保证与 line ~354 处的 scale 严格相等
    const _designerScale = Math.round(Math.max(0.1, (zoom / 100) * fitScale) * 1000) / 1000;

    const handleCanvasClick = useCallback((e: MouseEvent<HTMLDivElement>) => {
        // Deselect all when clicking on empty canvas area
        if (e.target === e.currentTarget || (e.target as HTMLElement).classList.contains('canvas-grid')) {
            selectComponents([]);
        }
    }, [selectComponents]);

    // ============================================================
    // Shift + 拖框选(marquee selection)
    // - 在画布空白区按住 Shift 并拖动 → 进入框选模式
    // - 框选过程显示半透明蓝色矩形选区
    // - 抬起时将选区命中的组件加入 selectedIds(累加,不覆盖)
    // - 不按 Shift 拖空白 → 走原 onClick 清空选区流程,不框选
    // ============================================================
    const [marquee, setMarquee] = useState<{ x1: number; y1: number; x2: number; y2: number } | null>(null);
    const marqueeStartRef = useRef<{ x: number; y: number; baseSelectedIds: string[] } | null>(null);

    const handleCanvasPointerDown = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
        if (editorReadonly) return;
        if (!e.isPrimary || e.button !== 0) return;
        // 仅在按 Shift + 鼠标按下在空白区(画布自身或网格)时进入框选
        if (!e.shiftKey) return;
        const targetEl = e.target as HTMLElement;
        const isEmptyArea = targetEl === e.currentTarget || targetEl.classList.contains('canvas-grid');
        if (!isEmptyArea) return;

        const rect = canvasRef.current?.getBoundingClientRect();
        if (!rect) return;
        // 画布有 transform: scale(scale),把屏幕像素转换成 design 像素
        const designX = (e.clientX - rect.left) / _designerScale;
        const designY = (e.clientY - rect.top) / _designerScale;

        e.preventDefault();
        e.stopPropagation();
        marqueeStartRef.current = { x: designX, y: designY, baseSelectedIds: [...selectedIds] };
        setMarquee({ x1: designX, y1: designY, x2: designX, y2: designY });
    }, [editorReadonly, _designerScale, selectedIds]);

    useEffect(() => {
        if (!marquee || !marqueeStartRef.current) return;
        const onMove = (ev: PointerEvent) => {
            const rect = canvasRef.current?.getBoundingClientRect();
            if (!rect) return;
            const designX = (ev.clientX - rect.left) / _designerScale;
            const designY = (ev.clientY - rect.top) / _designerScale;
            const start = marqueeStartRef.current!;
            setMarquee({ x1: start.x, y1: start.y, x2: designX, y2: designY });
        };
        const onUp = () => {
            if (!marqueeStartRef.current) return;
            const m = { ...marquee! };
            const left = Math.min(m.x1, m.x2);
            const right = Math.max(m.x1, m.x2);
            const top = Math.min(m.y1, m.y2);
            const bottom = Math.max(m.y1, m.y2);
            const isClick = (right - left < 4) && (bottom - top < 4);
            if (!isClick) {
                // 选取所有与选区相交的可见组件
                const hit = config.components
                    .filter((c) => c.visible !== false && !c.locked)
                    .filter((c) => {
                        const cLeft = c.x;
                        const cRight = c.x + c.width;
                        const cTop = c.y;
                        const cBottom = c.y + c.height;
                        // 相交判断(任何重叠就算命中)
                        return cLeft < right && cRight > left && cTop < bottom && cBottom > top;
                    })
                    .map((c) => c.id);
                if (hit.length > 0) {
                    // Shift + 框选 = 累加到当前选区(去重)
                    const base = marqueeStartRef.current.baseSelectedIds;
                    const merged = Array.from(new Set([...base, ...hit]));
                    selectComponents(merged);
                }
            }
            marqueeStartRef.current = null;
            setMarquee(null);
        };
        window.addEventListener('pointermove', onMove);
        window.addEventListener('pointerup', onUp);
        window.addEventListener('pointercancel', onUp);
        return () => {
            window.removeEventListener('pointermove', onMove);
            window.removeEventListener('pointerup', onUp);
            window.removeEventListener('pointercancel', onUp);
        };
    }, [marquee, _designerScale, config.components, selectComponents]);

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

    // 量化到 3 位小数,确保 scale 在 fitScale 与 zoom 不变时严格相等,
    // 否则 React 每次重渲都会产生新的浮点 width/height,触发不必要的 layout
    const scale = Math.round(Math.max(0.1, (zoom / 100) * fitScale) * 1000) / 1000;

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
            {/*
              使用 overflow:scroll 而非 auto,让滚动条永久占位,避免出现/消失反复触发 ResizeObserver
              缩放反馈循环 (用户体验上"按 + 抖动"的根因之一)
            */}
            <div className="flex-1 relative" style={{ overflow: 'scroll', scrollbarGutter: 'stable' }}>
            <div
                className="relative shadow-[0_4px_20px_rgba(0,0,0,0.5)] origin-center"
                style={{
                    width: config.width * scale,
                    height: config.height * scale,
                    // ❗ 不要在 width/height 上加 CSS transition:
                    // 修改组件属性(颜色/标题等)会触发父组件重渲,scale 可能因 fitScale
                    // 浮点精度微调而产生 sub-pixel 变化,加 transition 会让画布持续闪烁。
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
                        // 同上: 不在 transform 上加 transition,避免编辑属性时画布抖动
                    }}
                    onClick={handleCanvasClick}
                    onContextMenu={handleContextMenu}
                    onPointerDown={handleCanvasPointerDown}
                >
                    {showGrid && <div className="canvas-grid absolute inset-0 pointer-events-none" />}

                    {visibleSortedComponents.map((component) => (
                        <CanvasComponent
                            key={component.id}
                            component={component}
                            isSelected={selectedIds.includes(component.id)}
                            theme={config.theme}
                            customTheme={config.customTheme}
                            fontFamily={screenFontFamily}
                        />
                    ))}

                    {/* Shift + 拖框选 矩形指示器(design 坐标系) */}
                    {marquee ? (
                        <div
                            data-testid="marquee-selection"
                            aria-hidden="true"
                            style={{
                                position: 'absolute',
                                left: Math.min(marquee.x1, marquee.x2),
                                top: Math.min(marquee.y1, marquee.y2),
                                width: Math.abs(marquee.x2 - marquee.x1),
                                height: Math.abs(marquee.y2 - marquee.y1),
                                background: 'rgba(74, 158, 255, 0.12)',
                                border: '1px dashed rgba(74, 158, 255, 0.85)',
                                pointerEvents: 'none',
                                zIndex: 9999,
                            }}
                        />
                    ) : null}

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
