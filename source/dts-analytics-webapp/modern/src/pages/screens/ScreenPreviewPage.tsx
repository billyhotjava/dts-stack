import { useEffect, useState, useCallback, useMemo } from 'react';
import { useParams } from 'react-router';
import { analyticsApi, ScreenDetail } from '../../api/analyticsApi';
import { ComponentRenderer } from './components/ComponentRenderer';
import type { ScreenComponent, ComponentType, ScreenTheme } from './types';
import { resolveScreenTheme } from './screenThemes';

const PREVIEW_BATCH_SIZE = 20;

export default function ScreenPreviewPage() {
    const { id } = useParams<{ id: string }>();
    const [screen, setScreen] = useState<ScreenDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [scale, setScale] = useState(1);
    const [visibleCount, setVisibleCount] = useState(PREVIEW_BATCH_SIZE);

    useEffect(() => {
        if (!id) {
            setError('未找到大屏ID');
            setLoading(false);
            return;
        }

        analyticsApi.getScreen(id, { mode: 'published', fallbackDraft: true })
            .then((data) => {
                setScreen(data);
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
        const sx = vw / screen.width;
        const sy = vh / screen.height;
        setScale(Math.min(sx, sy));
    }, [screen]);

    useEffect(() => {
        computeScale();
        window.addEventListener('resize', computeScale);
        return () => window.removeEventListener('resize', computeScale);
    }, [computeScale]);

    const components: ScreenComponent[] = useMemo(() => {
        if (!screen) return [];
        return (screen.components || []).map(c => ({
            ...c,
            type: c.type as ComponentType,
            dataSource: c.dataSource as import('./types').DataSourceConfig | undefined,
        }));
    }, [screen]);

    const visibleSortedComponents = useMemo(
        () => components.filter(c => c.visible).sort((a, b) => a.zIndex - b.zIndex),
        [components],
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
                    width: screen.width,
                    height: screen.height,
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
