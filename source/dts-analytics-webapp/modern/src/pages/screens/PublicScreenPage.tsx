import { useEffect, useState, useCallback } from 'react';
import { useParams } from 'react-router';
import { analyticsApi } from '../../api/analyticsApi';
import { ComponentRenderer } from './components/ComponentRenderer';
import { DeviceModeSwitcher } from './components/DeviceModeSwitcher';
import { GlobalVariablePanel } from './components/GlobalVariablePanel';
import { ScreenRuntimeProvider } from './ScreenRuntimeContext';
import type { ScreenConfig, ScreenTheme } from './types';
import { resolveScreenTheme } from './screenThemes';
import { normalizeScreenConfig } from './specV2';
import { buildComponentMap, isComponentEffectivelyVisible } from './componentHierarchy';
import {
    isVisibleForDevice,
    parseForcedDeviceModeFromWindow,
    resolveDeviceModeByViewport,
    syncDeviceModeToWindowUrl,
    type DeviceMode,
} from './deviceMode';

export default function PublicScreenPage() {
    const { uuid } = useParams<{ uuid: string }>();
    const [screen, setScreen] = useState<ScreenConfig | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [scale, setScale] = useState(1);
    const [deviceMode, setDeviceMode] = useState<DeviceMode>('pc');
    const [forcedDeviceMode, setForcedDeviceMode] = useState<DeviceMode | null>(null);

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
        setScale(Math.max(0.1, Math.min(sx, sy, 1)));
    }, [forcedDeviceMode, screen]);

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

    const rawTheme = screen.theme as ScreenTheme | undefined;
    const screenTheme = resolveScreenTheme(rawTheme, screen.backgroundColor);
    const globalVariables = screen.globalVariables ?? [];
    const components = screen.components || [];
    const componentMap = buildComponentMap(components);

    const outerBg = screenTheme === 'glacier' ? '#e5e7eb' : '#000';
    const screenWidth = screen.width || 1920;
    const screenHeight = screen.height || 1080;
    const stageWidth = Math.max(1, screenWidth * scale);
    const stageHeight = Math.max(1, screenHeight * scale);

    const setForcedMode = (mode: DeviceMode | null) => {
        setForcedDeviceMode(mode);
        syncDeviceModeToWindowUrl(mode);
    };

    return (
        <ScreenRuntimeProvider definitions={globalVariables}>
            <div
                style={{
                    position: 'fixed',
                    inset: 0,
                    background: outerBg,
                    padding: 12,
                    boxSizing: 'border-box',
                }}
            >
                <div style={{ width: '100%', height: '100%', overflow: 'auto' }}>
                    <div
                        style={{
                            minWidth: '100%',
                            minHeight: '100%',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
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
                                    backgroundColor: screen.backgroundColor || '#0d1b2a',
                                    backgroundImage: screen.backgroundImage ? `url(${screen.backgroundImage})` : undefined,
                                    backgroundSize: 'cover',
                                    backgroundPosition: 'center',
                                    position: 'relative',
                                    overflow: 'hidden',
                                    transform: `scale(${scale})`,
                                    transformOrigin: 'top left',
                                }}
                            >
                                {components
                                    .filter((c) => c.visible && isVisibleForDevice(c, deviceMode) && isComponentEffectivelyVisible(c, componentMap))
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
                        </div>
                    </div>
                </div>
                <GlobalVariablePanel />
                <DeviceModeSwitcher
                    position="fixed"
                    deviceMode={deviceMode}
                    forcedDeviceMode={forcedDeviceMode}
                    onSetForcedMode={setForcedMode}
                />
            </div>
        </ScreenRuntimeProvider>
    );
}
