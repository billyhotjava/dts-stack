import { useEffect, useState, useCallback, useMemo, useRef } from 'react';
import { useParams } from 'react-router';
import { analyticsApi } from '../../api/analyticsApi';
import { ComponentRenderer } from './components/ComponentRenderer';
import { DeviceModeSwitcher } from './components/DeviceModeSwitcher';
import { GlobalVariablePanel } from './components/GlobalVariablePanel';
import { PreviewScaleControl } from './components/PreviewScaleControl';
import { RuntimeActionPanel } from './components/RuntimeActionPanel';
import { ScreenRuntimeProvider } from './ScreenRuntimeContext';
import type { ScreenConfig, ScreenTheme } from './types';
import { resolveScreenTheme } from './screenThemes';
import { normalizeScreenConfig } from './specV2';
import { buildComponentMap, isComponentEffectivelyVisible } from './componentHierarchy';
import { safeCssBackgroundUrl } from './sanitize';
import { useScreenCarousel } from './hooks/useScreenCarousel';
import {
    isVisibleForDevice,
    parseForcedDeviceModeFromWindow,
    resolveDeviceModeByViewport,
    syncDeviceModeToWindowUrl,
    type DeviceMode,
} from './deviceMode';
import './ScreenRuntimeShell.css';

export default function PublicScreenPage() {
    const { uuid } = useParams<{ uuid: string }>();
    const [screen, setScreen] = useState<ScreenConfig | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [scale, setScale] = useState(1);
    const [autoScale, setAutoScale] = useState(1);
    const [manualScale, setManualScale] = useState<number | null>(null);
    const [deviceMode, setDeviceMode] = useState<DeviceMode>('pc');
    const [forcedDeviceMode, setForcedDeviceMode] = useState<DeviceMode | null>(null);
    const [fabOpen, setFabOpen] = useState(false);
    const scrollContainerRef = useRef<HTMLDivElement | null>(null);
    const fabRef = useRef<HTMLDivElement | null>(null);

    // Embed mode: ?embed=1 hides all controls; ?hideControls=1 hides only zoom controls
    const searchParams = useMemo(() => new URLSearchParams(window.location.search), []);
    const isEmbedMode = searchParams.get('embed') === '1';
    const hideControls = isEmbedMode || searchParams.get('hideControls') === '1';

    // URL parameter passthrough: ?var_{key}=value → globalVariable
    const urlVariableOverrides = useMemo(() => {
        const overrides: Record<string, string> = {};
        for (const [k, v] of searchParams) {
            if (k.startsWith('var_')) {
                overrides[k.slice(4)] = v;
            }
        }
        return overrides;
    }, [searchParams]);

    useEffect(() => {
        setForcedDeviceMode(parseForcedDeviceModeFromWindow());
    }, []);

    useEffect(() => {
        if (!uuid) {
            setError('未找到大屏链接');
            setLoading(false);
            return;
        }

        analyticsApi.getPublicScreen(uuid)
            .then((data) => {
                const normalized = normalizeScreenConfig(data, { id: data.id });
                if (normalized.warnings.length > 0) {
                    console.warn('[screen-spec-v2] normalized with warnings:', normalized.warnings);
                }
                setScreen(normalized.config);
                setLoading(false);
            })
            .catch((err) => {
                console.error('Failed to load public screen:', err);
                setError('加载大屏失败');
                setLoading(false);
            });
    }, [uuid]);

    const computeScale = useCallback(() => {
        if (!screen) return;
        const viewport = window.visualViewport;
        const vw = viewport?.width ?? window.innerWidth;
        const vh = viewport?.height ?? window.innerHeight;
        const nextMode: DeviceMode = forcedDeviceMode || resolveDeviceModeByViewport(vw);
        setDeviceMode(nextMode);
        const safeWidth = Math.max(vw - 24, 320);
        const safeHeight = Math.max(vh - 64, 240);
        const sx = safeWidth / (screen.width || 1920);
        const sy = safeHeight / (screen.height || 1080);
        const nextAutoScale = Math.max(0.1, Math.min(sx, sy, 1));
        setAutoScale(nextAutoScale);
        if (manualScale === null) {
            setScale(nextAutoScale);
        }
    }, [forcedDeviceMode, manualScale, screen]);

    useEffect(() => {
        computeScale();
        window.addEventListener('resize', computeScale);
        return () => window.removeEventListener('resize', computeScale);
    }, [computeScale]);

    // Multi-page carousel support — must be called before early returns
    const carousel = useScreenCarousel(screen?.pages, screen?.components || [], screen?.carouselConfig);
    const components = carousel.currentPageComponents;

    const visibleSortedComponents = useMemo(
        () => {
            const componentMap = buildComponentMap(components);
            return components
                .filter((c) => c.visible && isVisibleForDevice(c, deviceMode) && isComponentEffectivelyVisible(c, componentMap))
                .sort((a, b) => a.zIndex - b.zIndex);
        },
        [components, deviceMode],
    );

    const contentBounds = useMemo(() => {
        const baseWidth = Math.max(1, screen?.width || 1920);
        const baseHeight = Math.max(1, screen?.height || 1080);
        let minLeft = 0;
        let minTop = 0;
        let maxRight = baseWidth;
        let maxBottom = baseHeight;
        for (const component of visibleSortedComponents) {
            const left = Number(component.x) || 0;
            const top = Number(component.y) || 0;
            const right = left + Math.max(0, Number(component.width) || 0);
            const bottom = top + Math.max(0, Number(component.height) || 0);
            if (left < minLeft) minLeft = left;
            if (top < minTop) minTop = top;
            if (right > maxRight) maxRight = right;
            if (bottom > maxBottom) maxBottom = bottom;
        }
        return {
            minLeft,
            minTop,
            width: Math.max(1, maxRight - minLeft),
            height: Math.max(1, maxBottom - minTop),
        };
    }, [screen?.height, screen?.width, visibleSortedComponents]);

    const clampScale = (value: number) => Math.max(0.2, Math.min(2, value));
    const setFitScale = useCallback(() => {
        setManualScale(null);
        setScale(autoScale);
    }, [autoScale]);
    const setAbsoluteScale = useCallback((value: number) => {
        const next = clampScale(value);
        setManualScale(next);
        setScale(next);
    }, []);
    const adjustScale = useCallback((delta: number) => {
        const base = manualScale === null ? autoScale : manualScale;
        setAbsoluteScale(base + delta);
    }, [autoScale, manualScale, setAbsoluteScale]);

    useEffect(() => {
        const node = scrollContainerRef.current;
        if (!node) return;
        const handleWheel = (event: WheelEvent) => {
            if (!event.ctrlKey && !event.metaKey) {
                return;
            }
            event.preventDefault();
            const direction = event.deltaY > 0 ? -1 : 1;
            adjustScale(direction * 0.05);
        };
        node.addEventListener('wheel', handleWheel, { passive: false });
        return () => {
            node.removeEventListener('wheel', handleWheel);
        };
    }, [adjustScale]);

    useEffect(() => {
        const isTypingTarget = (target: EventTarget | null): boolean => {
            const node = target as HTMLElement | null;
            if (!node) return false;
            const tag = node.tagName;
            if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
            return node.isContentEditable;
        };
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.ctrlKey || event.metaKey || event.altKey) return;
            if (isTypingTarget(event.target)) return;
            const key = event.key;
            if (key === '0') {
                event.preventDefault();
                setAbsoluteScale(1);
                return;
            }
            if (key.toLowerCase() === 'f') {
                event.preventDefault();
                setFitScale();
                return;
            }
            if (key === '+' || key === '=' || key === 'NumpadAdd') {
                event.preventDefault();
                adjustScale(0.1);
                return;
            }
            if (key === '-' || key === '_' || key === 'NumpadSubtract') {
                event.preventDefault();
                adjustScale(-0.1);
            }
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [adjustScale, setAbsoluteScale, setFitScale]);

    // Close FAB panel on click outside
    useEffect(() => {
        if (!fabOpen) return;
        const handleClick = (e: MouseEvent) => {
            if (fabRef.current && !fabRef.current.contains(e.target as Node)) {
                setFabOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClick);
        return () => document.removeEventListener('mousedown', handleClick);
    }, [fabOpen]);

    const globalVariables = screen?.globalVariables ?? [];
    // Apply URL variable overrides to global variable definitions
    const effectiveGlobalVars = useMemo(() => {
        if (Object.keys(urlVariableOverrides).length === 0) return globalVariables;
        return globalVariables.map(gv => {
            const override = urlVariableOverrides[gv.key];
            return override !== undefined ? { ...gv, defaultValue: override } : gv;
        });
    }, [globalVariables, urlVariableOverrides]);

    // ── Early returns MUST be after all hooks ──
    if (loading) {
        return (
            <div className="screen-runtime screen-runtime--fullscreen screen-runtime--dark">
                <div className="screen-runtime__feedback">
                    <div className="screen-runtime__feedback-card">
                        <h1>正在加载公开大屏</h1>
                        <p>正在准备公开访问所需的画布和运行态参数。</p>
                    </div>
                </div>
            </div>
        );
    }

    if (error || !screen) {
        return (
            <div className="screen-runtime screen-runtime--fullscreen screen-runtime--dark">
                <div className="screen-runtime__feedback">
                    <div className="screen-runtime__feedback-card">
                        <h1>公开链接不可用</h1>
                        <p>{error || '未找到大屏'}</p>
                    </div>
                </div>
            </div>
        );
    }

    const rawTheme = screen.theme as ScreenTheme | undefined;
    const screenTheme = resolveScreenTheme(rawTheme, screen.backgroundColor);
    const carouselTransition = screen.carouselConfig?.transition ?? 'fade';
    const carouselDuration = screen.carouselConfig?.transitionDuration ?? 800;
    const screenWidth = contentBounds.width;
    const screenHeight = contentBounds.height;
    const stageWidth = Math.max(1, screenWidth * scale);
    const stageHeight = Math.max(1, screenHeight * scale);
    const scalePercent = Math.round(scale * 100);

    const setForcedMode = (mode: DeviceMode | null) => {
        setForcedDeviceMode(mode);
        syncDeviceModeToWindowUrl(mode);
    };

    return (
        <ScreenRuntimeProvider definitions={effectiveGlobalVars}>
            <div className={`screen-runtime screen-runtime--fullscreen ${screenTheme === 'glacier' ? 'screen-runtime--light' : 'screen-runtime--dark'} ${isEmbedMode ? 'screen-runtime--embed' : ''}`}>
                <div ref={scrollContainerRef} className="screen-runtime__scroll">
                    <div className="screen-runtime__viewport">
                        <div className="screen-runtime__stage" style={{ width: stageWidth, height: stageHeight }}>
                            <div className="screen-runtime__canvas-shell">
                            <div
                                className={`screen-runtime__canvas ${carousel.transitioning ? 'is-transitioning' : ''}`}
                                style={{
                                    width: screenWidth,
                                    height: screenHeight,
                                    backgroundColor: carousel.currentPageBgColor || screen.backgroundColor || '#0d1b2a',
                                    backgroundImage: safeCssBackgroundUrl(carousel.currentPageBgImage || screen.backgroundImage),
                                    backgroundSize: 'cover',
                                    backgroundPosition: 'center',
                                    position: 'relative',
                                    overflow: 'hidden',
                                    transform: `scale(${scale})`,
                                    transformOrigin: 'top left',
                                    transition: carousel.transitioning
                                        ? `opacity ${carouselDuration}ms ease, transform ${carouselDuration}ms ease`
                                        : 'none',
                                    ...(carousel.transitioning && carouselTransition === 'fade'
                                        ? { animation: `carousel-fade-in ${carouselDuration}ms ease` }
                                        : {}),
                                }}
                                onMouseEnter={carousel.isCarouselActive ? carousel.pause : undefined}
                                onMouseLeave={carousel.isCarouselActive ? carousel.resume : undefined}
                            >
                                {visibleSortedComponents
                                    .map((component) => (
                                        <div
                                            key={component.id}
                                            data-component-id={component.id}
                                            data-component-name={component.name}
                                            data-component-type={component.type}
                                            style={{
                                                position: 'absolute',
                                                left: component.x - contentBounds.minLeft,
                                                top: component.y - contentBounds.minTop,
                                                width: component.width,
                                                height: component.height,
                                                zIndex: component.zIndex,
                                            }}
                                        >
                                            <ComponentRenderer component={component} mode="preview" theme={screenTheme} />
                                        </div>
                                    ))}
                            </div>
                            </div>
                        </div>
                    </div>
                    {/* Carousel page indicator */}
                    {carousel.pageCount > 1 && (
                        <div className="screen-runtime__pager">
                            <button type="button" onClick={carousel.prevPage} className="runtime-control-btn screen-runtime__pager-nav">‹</button>
                            {Array.from({ length: carousel.pageCount }, (_, i) => (
                                <button
                                    key={i}
                                    type="button"
                                    onClick={() => carousel.goToPage(i)}
                                    className={`runtime-control-btn screen-runtime__pager-dot ${i === carousel.pageIndex ? 'is-active' : ''}`}
                                    title={`第 ${i + 1} 页`}
                                />
                            ))}
                            <button type="button" onClick={carousel.nextPage} className="runtime-control-btn screen-runtime__pager-nav">›</button>
                        </div>
                    )}
                    <RuntimeActionPanel />
                </div>

                {/* Floating controls FAB */}
                {!isEmbedMode && (
                    <div ref={fabRef} className="preview-fab">
                        <button
                            type="button"
                            className="preview-fab__trigger"
                            onClick={() => setFabOpen((prev) => !prev)}
                            title="控制面板"
                        >
                            ⚙
                        </button>
                        {fabOpen && (
                            <div className="preview-fab__panel">
                                <div className="preview-fab__section">
                                    <div className="preview-fab__section-title">屏幕信息</div>
                                    <div className="preview-fab__info-name">{screen.name || '未命名大屏'}</div>
                                    <div className="preview-fab__info-badges">
                                        <span className="screen-runtime__badge is-info">公开访问</span>
                                        <span className="screen-runtime__badge">{screenWidth} × {screenHeight}</span>
                                        <span className="screen-runtime__badge">{visibleSortedComponents.length} 组件</span>
                                        {carousel.pageCount > 1 ? (
                                            <span className="screen-runtime__badge">{carousel.pageIndex + 1}/{carousel.pageCount} 页</span>
                                        ) : null}
                                        {Object.keys(urlVariableOverrides).length > 0 ? (
                                            <span className="screen-runtime__badge is-warning">URL 参数覆盖 {Object.keys(urlVariableOverrides).length}</span>
                                        ) : null}
                                    </div>
                                </div>
                                <div className="preview-fab__divider" />
                                <div className="preview-fab__section">
                                    <div className="preview-fab__section-title">缩放</div>
                                    {!hideControls && (
                                        <PreviewScaleControl
                                            scalePercent={scalePercent}
                                            onFit={setFitScale}
                                            onReset100={() => setAbsoluteScale(1)}
                                            onZoomOut={() => adjustScale(-0.1)}
                                            onZoomIn={() => adjustScale(0.1)}
                                            onSetScalePercent={(percent) => {
                                                const safePercent = Number.isFinite(percent) ? Math.max(20, Math.min(200, Math.round(percent))) : 100;
                                                setAbsoluteScale(safePercent / 100);
                                            }}
                                        />
                                    )}
                                </div>
                                <div className="preview-fab__divider" />
                                <div className="preview-fab__section">
                                    <div className="preview-fab__section-title">设备模式</div>
                                    <DeviceModeSwitcher
                                        position="inline"
                                        deviceMode={deviceMode}
                                        forcedDeviceMode={forcedDeviceMode}
                                        onSetForcedMode={setForcedMode}
                                    />
                                </div>
                                {globalVariables.length > 0 && (
                                    <>
                                        <div className="preview-fab__divider" />
                                        <div className="preview-fab__section">
                                            <div className="preview-fab__section-title">运行时变量</div>
                                            <GlobalVariablePanel />
                                        </div>
                                    </>
                                )}
                            </div>
                        )}
                    </div>
                )}
            </div>
        </ScreenRuntimeProvider>
    );
}
