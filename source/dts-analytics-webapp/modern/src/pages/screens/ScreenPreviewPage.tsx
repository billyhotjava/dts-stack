import { useEffect, useState } from 'react';
import { useParams } from 'react-router';
import { analyticsApi, ScreenDetail } from '../../api/analyticsApi';
import { ComponentRenderer } from './components/ComponentRenderer';
import type { ScreenComponent, ComponentType } from './types';

export default function ScreenPreviewPage() {
    const { id } = useParams<{ id: string }>();
    const [screen, setScreen] = useState<ScreenDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!id) {
            setError('未找到大屏ID');
            setLoading(false);
            return;
        }

        analyticsApi.getScreen(id)
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

    if (loading) {
        return (
            <div className="screen-preview-loading">
                <div className="loading-spinner" />
                <span>加载中...</span>
            </div>
        );
    }

    if (error) {
        return (
            <div className="screen-preview-error">
                <span>❌ {error}</span>
            </div>
        );
    }

    if (!screen) {
        return (
            <div className="screen-preview-error">
                <span>未找到大屏</span>
            </div>
        );
    }

    const components: ScreenComponent[] = (screen.components || []).map(c => ({
        ...c,
        type: c.type as ComponentType,
    }));

    return (
        <div
            className="screen-preview"
            style={{
                width: screen.width,
                height: screen.height,
                backgroundColor: screen.backgroundColor || '#0d1b2a',
                backgroundImage: screen.backgroundImage ? `url(${screen.backgroundImage})` : undefined,
                backgroundSize: 'cover',
                backgroundPosition: 'center',
                position: 'relative',
                overflow: 'hidden',
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
                        <ComponentRenderer component={component} />
                    </div>
                ))}
        </div>
    );
}
