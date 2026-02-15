import { useEffect, useState, useCallback, useMemo } from 'react';
import { useParams } from 'react-router';
import { analyticsApi } from '../../api/analyticsApi';
import { ComponentRenderer } from './components/ComponentRenderer';
import type { ScreenConfig, ScreenTheme } from './types';
import { resolveScreenTheme } from './screenThemes';
import { normalizeScreenConfig } from './specV2';

const PREVIEW_BATCH_SIZE = 20;
type DeviceMode = 'pc' | 'tablet' | 'mobile';

function isVisibleForDevice(component: { config?: Record<string, unknown> }, device: DeviceMode): boolean {
    const raw = component?.config?.visibleOn;
    if (!Array.isArray(raw) || raw.length === 0) {
        return true;
    }
    return raw.includes(device);
}

export default function ScreenPreviewPage() {
    const { id } = useParams<{ id: string }>();
    const [screen, setScreen] = useState<ScreenConfig | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [scale, setScale] = useState(1);
    const [deviceMode, setDeviceMode] = useState<DeviceMode>('pc');
    const [visibleCount, setVisibleCount] = useState(PREVIEW_BATCH_SIZE);

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
        const vw = window.innerWidth;
        const vh = window.innerHeight;
        const nextMode: DeviceMode = vw <= 768 ? 'mobile' : (vw <= 1200 ? 'tablet' : 'pc');
        setDeviceMode(nextMode);
        const sx = vw / (screen.width || 1920);
        const sy = vh / (screen.height || 1080);
        setScale(Math.min(sx, sy));
    }, [screen]);

    useEffect(() => {
        computeScale();
        window.addEventListener('resize', computeScale);
        return () => window.removeEventListener('resize', computeScale);
    }, [computeScale]);

    const components = useMemo(() => screen?.components || [], [screen]);

    const visibleSortedComponents = useMemo(
        () => components
            .filter(c => c.visible && isVisibleForDevice(c, deviceMode))
            .sort((a, b) => a.zIndex - b.zIndex),
        [components, deviceMode],
    );

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

    return (
        <div
            style={{
                position: 'fixed',
                inset: 0,
                background: outerBg,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                overflow: 'hidden',
            }}
        >
            <div
                style={{
                    width: screen.width || 1920,
                    height: screen.height || 1080,
                    backgroundColor: screen.backgroundColor || '#0d1b2a',
                    backgroundImage: screen.backgroundImage ? `url(${screen.backgroundImage})` : undefined,
                    backgroundSize: 'cover',
                    backgroundPosition: 'center',
                    position: 'relative',
                    overflow: 'hidden',
                    transform: `scale(${scale})`,
                    transformOrigin: 'center center',
                }}
            >
                {visibleSortedComponents
                    .slice(0, visibleCount)
                    .map((component) => (
                        <div
                            key={component.id}
                            style={{
                                position: 'absolute',
                                left: component.x,
                                top: component.y,
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
    );
}
