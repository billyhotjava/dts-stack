import { useEffect, useState, useCallback, useMemo, useRef } from 'react';
import { useParams } from 'react-router';
import { analyticsApi } from '../../api/analyticsApi';
import { ComponentRenderer } from './components/ComponentRenderer';
import { DeviceModeSwitcher } from './components/DeviceModeSwitcher';
import { PreviewScaleControl } from './components/PreviewScaleControl';
import { ScreenRuntimeProvider } from './ScreenRuntimeContext';
import type { ScreenConfig, ScreenTheme, CarouselConfig } from './types';
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

const PREVIEW_BATCH_SIZE = 20;

export default function ScreenPreviewPage() {
    const { id } = useParams<{ id: string }>();
    const [screen, setScreen] = useState<ScreenConfig | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [scale, setScale] = useState(1);
    const [autoScale, setAutoScale] = useState(1);
    const [manualScale, setManualScale] = useState<number | null>(null);
    const [deviceMode, setDeviceMode] = useState<DeviceMode>('pc');
    const [forcedDeviceMode, setForcedDeviceMode] = useState<DeviceMode | null>(null);
    const [visibleCount, setVisibleCount] = useState(PREVIEW_BATCH_SIZE);
    const scrollContainerRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
        setForcedDeviceMode(parseForcedDeviceModeFromWindow());
    }, []);

    useEffect(() => {
        if (!id) {
            setError('未找到大屏ID');
            setLoading(false);
            return;
        }

        analyticsApi.getScreen(id, { mode: 'published', fallbackDraft: true })
            .then((data) => {
                const normalized = normalizeScreenConfig(data, { id: data.id });
                if (normalized.warnings.length > 0) {
                    console.warn('[screen-spec-v2] normalized with warnings:', normalized.warnings);
                }
                setScreen(normalized.config);
                setLoading(false);
            })
            .catch((err) => {
                console.error('Failed to load screen:', err);
                setError('加载大屏失败');
                setLoading(false);
            });
    }, [id]);

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

    // Multi-page carousel support
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
    }, [screen, visibleSortedComponents]);

    useEffect(() => {
        if (!visibleSortedComponents.length) {
            setVisibleCount(PREVIEW_BATCH_SIZE);
            return;
        }

        setVisibleCount(Math.min(PREVIEW_BATCH_SIZE, visibleSortedComponents.length));

        if (visibleSortedComponents.length <= PREVIEW_BATCH_SIZE) {
            return;
        }

        let cancelled = false;
        const loadNextBatch = () => {
            if (cancelled) return;
            setVisibleCount((prev) => {
                const next = Math.min(prev + PREVIEW_BATCH_SIZE, visibleSortedComponents.length);
                return next;
            });
        };

        const timer = window.setInterval(() => {
            if (cancelled) return;
            setVisibleCount((prev) => {
                if (prev >= visibleSortedComponents.length) {
                    window.clearInterval(timer);
                    return prev;
                }
                return Math.min(prev + PREVIEW_BATCH_SIZE, visibleSortedComponents.length);
            });
        }, 30);

        requestAnimationFrame(loadNextBatch);

        return () => {
            cancelled = true;
            window.clearInterval(timer);
        };
    }, [visibleSortedComponents]);

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

    // ── Early returns MUST be after all hooks ──
    if (loading) {
        return (
            <div style={{
                position: 'fixed', inset: 0, display: 'flex',
                alignItems: 'center', justifyContent: 'center',
                background: '#000', color: '#fff', fontSize: 16,
            }}>
                <span>加载中...</span>
            </div>
        );
    }

    if (error || !screen) {
        return (
            <div style={{
                position: 'fixed', inset: 0, display: 'flex',
                alignItems: 'center', justifyContent: 'center',
                background: '#000', color: '#fff', fontSize: 16,
            }}>
                <span>{error || '未找到大屏'}</span>
            </div>
        );
    }

    const rawTheme = (screen as { theme?: string }).theme as ScreenTheme | undefined;
    const screenTheme = resolveScreenTheme(rawTheme, screen.backgroundColor);
    const outerBg = screenTheme === 'glacier' ? '#e5e7eb' : '#000';
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
        <ScreenRuntimeProvider definitions={screen.globalVariables ?? []}>
        <div
            style={{
                position: 'fixed',
                inset: 0,
                background: outerBg,
                padding: 12,
                boxSizing: 'border-box',
            }}
        >
            <div ref={scrollContainerRef} style={{ width: '100%', height: '100%', overflowX: 'auto', overflowY: 'auto' }}>
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
                <div
                    style={{
                        minWidth: '100%',
                        minHeight: '100%',
                        display: 'flex',
                        alignItems: 'flex-start',
                        justifyContent: 'flex-start',
                        padding: 8,
                        boxSizing: 'border-box',
                    }}
                >
                    <div
                        style={{
                            position: 'relative',
                            width: stageWidth,
                            height: stageHeight,
                            flex: '0 0 auto',
                        }}
                    >
                        <div
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
                            }}
                        >
                            {visibleSortedComponents
                                .slice(0, visibleCount)
                                .map((component) => (
                                    <div
                                        key={component.id}
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

                            {visibleCount < visibleSortedComponents.length && (
                                <div
                                    style={{
                                        position: 'absolute',
                                        right: 12,
                                        bottom: 12,
                                        background: 'rgba(0,0,0,0.55)',
                                        color: '#fff',
                                        fontSize: 12,
                                        padding: '4px 8px',
                                        borderRadius: 6,
                                        zIndex: 9999,
                                    }}
                                >
                                    组件加载中 {visibleCount}/{visibleSortedComponents.length}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
                <DeviceModeSwitcher
                    position="fixed"
                    deviceMode={deviceMode}
                    forcedDeviceMode={forcedDeviceMode}
                    onSetForcedMode={setForcedMode}
                />
                {/* Carousel page indicator */}
                {carousel.pageCount > 1 && (
                    <div style={{
                        position: 'fixed',
                        bottom: 16,
                        left: '50%',
                        transform: 'translateX(-50%)',
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                        padding: '6px 14px',
                        background: 'rgba(0,0,0,0.5)',
                        borderRadius: 20,
                        zIndex: 9999,
                    }}>
                        <button
                            type="button"
                            onClick={carousel.prevPage}
                            style={{
                                background: 'none', border: 'none', color: '#fff',
                                cursor: 'pointer', fontSize: 14, padding: '0 4px', opacity: 0.7,
                            }}
                        >
                            ‹
                        </button>
                        {Array.from({ length: carousel.pageCount }, (_, i) => (
                            <button
                                key={i}
                                type="button"
                                onClick={() => carousel.goToPage(i)}
                                style={{
                                    width: i === carousel.pageIndex ? 20 : 8,
                                    height: 8,
                                    borderRadius: 4,
                                    border: 'none',
                                    background: i === carousel.pageIndex ? '#3b82f6' : 'rgba(255,255,255,0.4)',
                                    cursor: 'pointer',
                                    padding: 0,
                                    transition: 'width 0.3s, background 0.3s',
                                }}
                                title={`第 ${i + 1} 页`}
                            />
                        ))}
                        <button
                            type="button"
                            onClick={carousel.nextPage}
                            style={{
                                background: 'none', border: 'none', color: '#fff',
                                cursor: 'pointer', fontSize: 14, padding: '0 4px', opacity: 0.7,
                            }}
                        >
                            ›
                        </button>
                    </div>
                )}
            </div>
        </div>
        </ScreenRuntimeProvider>
    );
}
