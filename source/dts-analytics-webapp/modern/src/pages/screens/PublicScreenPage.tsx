import { useEffect, useState, useCallback } from 'react';
import { useParams } from 'react-router';
import { analyticsApi, PublicScreenDetail } from '../../api/analyticsApi';
import { ComponentRenderer } from './components/ComponentRenderer';
import { GlobalVariablePanel } from './components/GlobalVariablePanel';
import { ScreenRuntimeProvider } from './ScreenRuntimeContext';
import type { ScreenComponent, ComponentType, ScreenTheme, ScreenGlobalVariable } from './types';
import { resolveScreenTheme } from './screenThemes';

function toGlobalVariables(input: unknown): ScreenGlobalVariable[] {
    if (!Array.isArray(input)) return [];
    return input
        .map((item) => {
            if (!item || typeof item !== 'object') return null;
            const row = item as Record<string, unknown>;
            const key = typeof row.key === 'string' ? row.key.trim() : '';
            if (!key) return null;
            return {
                key,
                label: typeof row.label === 'string' ? row.label : key,
                type: row.type === 'number' || row.type === 'date' ? row.type : 'string',
                defaultValue: typeof row.defaultValue === 'string' ? row.defaultValue : '',
                description: typeof row.description === 'string' ? row.description : undefined,
            } as ScreenGlobalVariable;
        })
        .filter((x): x is ScreenGlobalVariable => x !== null);
}

export default function PublicScreenPage() {
    const { uuid } = useParams<{ uuid: string }>();
    const [screen, setScreen] = useState<PublicScreenDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [scale, setScale] = useState(1);

    useEffect(() => {
        if (!uuid) {
            setError('未找到大屏链接');
            setLoading(false);
            return;
        }

        analyticsApi.getPublicScreen(uuid)
            .then((data) => {
                setScreen(data);
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
    const globalVariables = toGlobalVariables((screen as Record<string, unknown>).globalVariables);

    const components: ScreenComponent[] = (screen.components || []).map(c => ({
        ...c,
        type: c.type as ComponentType,
        dataSource: c.dataSource as import('./types').DataSourceConfig | undefined,
        interaction: c.interaction as import('./types').ComponentInteractionConfig | undefined,
    }));

    const outerBg = screenTheme === 'glacier' ? '#e5e7eb' : '#000';

    return (
        <ScreenRuntimeProvider definitions={globalVariables}>
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
                    {components
                        .filter(c => c.visible)
                        .sort((a, b) => a.zIndex - b.zIndex)
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
                </div>
                <GlobalVariablePanel />
            </div>
        </ScreenRuntimeProvider>
    );
}
