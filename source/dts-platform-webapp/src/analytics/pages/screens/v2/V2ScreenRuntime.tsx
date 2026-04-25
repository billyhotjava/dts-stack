import { useEffect, useMemo, useState } from 'react';

import { SharedStoreProvider } from '../hooks/useSharedStore';
import { useScreenCarousel } from '../hooks/useScreenCarousel';
import { resolveDeviceModeByViewport, type DeviceMode } from '../deviceMode';
import { ScreenRuntimeProvider, type ScreenRuntimeMeta } from '../ScreenRuntimeContext';
import { GlobalVariablePanel } from '../components/GlobalVariablePanel';
import { RuntimeActionPanel } from '../components/RuntimeActionPanel';
import { ResponsiveScreenLayout } from './ResponsiveScreenLayout';
import type { ComponentV2, ScreenConfigV2 } from './types';
import '../ScreenRuntimeShell.css';

interface V2ScreenRuntimeProps {
    screen: ScreenConfigV2;
    runtimeMeta?: ScreenRuntimeMeta;
    urlVariableOverrides?: Record<string, string>;
    showVariablePanel?: boolean;
    dataTestId?: string;
}

function isVisibleForDevice(component: ComponentV2, deviceMode: DeviceMode): boolean {
    const visibility = component.visibleByDevice;
    if (!visibility) return true;
    if (deviceMode === 'tablet') return visibility.tablet !== false;
    if (deviceMode === 'mobile') return visibility.mobile !== false;
    return visibility.pc !== false;
}

export function V2ScreenRuntime({
    screen,
    runtimeMeta,
    urlVariableOverrides,
    showVariablePanel = true,
    dataTestId,
}: V2ScreenRuntimeProps) {
    const [deviceMode, setDeviceMode] = useState<DeviceMode>(() => resolveDeviceModeByViewport(window.innerWidth));

    useEffect(() => {
        const updateDeviceMode = () => {
            setDeviceMode(resolveDeviceModeByViewport(window.innerWidth));
        };
        updateDeviceMode();
        window.addEventListener('resize', updateDeviceMode);
        return () => window.removeEventListener('resize', updateDeviceMode);
    }, []);

    const effectiveDefinitions = useMemo(() => {
        const definitions = screen.globalVariables ?? [];
        if (!urlVariableOverrides || Object.keys(urlVariableOverrides).length === 0) {
            return definitions;
        }
        return definitions.map((definition) => {
            const override = urlVariableOverrides[definition.key];
            return override === undefined
                ? definition
                : { ...definition, defaultValue: override };
        });
    }, [screen.globalVariables, urlVariableOverrides]);

    const carousel = useScreenCarousel(screen.pages, screen.components, screen.carouselConfig);

    const visibleComponents = useMemo(
        () => carousel.currentPageComponents.filter(
            (component) => component.visible !== false && isVisibleForDevice(component, deviceMode),
        ),
        [carousel.currentPageComponents, deviceMode],
    );

    const backgroundColor = carousel.currentPageBgColor ?? screen.backgroundColor;
    const backgroundImage = carousel.currentPageBgImage ?? screen.backgroundImage;

    return (
        <ScreenRuntimeProvider definitions={effectiveDefinitions} runtimeMeta={runtimeMeta}>
            <SharedStoreProvider>
                <div className="fixed inset-0 overflow-hidden p-0 box-border" data-testid={dataTestId}>
                    <ResponsiveScreenLayout
                        screen={screen}
                        components={visibleComponents}
                        backgroundColor={backgroundColor}
                        backgroundImage={backgroundImage}
                        theme={screen.theme}
                    />

                    {showVariablePanel ? (
                        <div className="fixed top-6 right-6 z-[11000] max-w-[360px]">
                            <GlobalVariablePanel />
                        </div>
                    ) : null}

                    {carousel.pageCount > 1 ? (
                        <div
                            className="fixed bottom-[18px] left-1/2 z-[10990] flex items-center gap-2.5 rounded-full backdrop-blur-[16px]"
                            style={{
                                minHeight: 50,
                                padding: '8px 12px',
                                border: '1px solid rgba(148, 163, 184, 0.22)',
                                background: 'rgba(255, 255, 255, 0.9)',
                                boxShadow: '0 20px 40px rgba(15, 23, 42, 0.16)',
                                transform: 'translateX(-50%)',
                            }}
                        >
                            <button type="button" onClick={carousel.prevPage} className="runtime-control-btn screen-runtime__pager-nav">&#8249;</button>
                            {Array.from({ length: carousel.pageCount }, (_, index) => (
                                <button
                                    key={index}
                                    type="button"
                                    onClick={() => carousel.goToPage(index)}
                                    className={`runtime-control-btn screen-runtime__pager-dot ${index === carousel.pageIndex ? 'is-active' : ''}`}
                                    title={`第 ${index + 1} 页`}
                                />
                            ))}
                            <button type="button" onClick={carousel.nextPage} className="runtime-control-btn screen-runtime__pager-nav">&#8250;</button>
                            <button
                                type="button"
                                onClick={carousel.togglePlay}
                                className="runtime-control-btn screen-runtime__pager-play"
                                title={carousel.isPlaying ? '暂停自动轮播' : '开始自动轮播'}
                            >
                                <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
                                    {carousel.isPlaying
                                        ? <><rect x="3" y="2" width="4" height="12" rx="1" /><rect x="9" y="2" width="4" height="12" rx="1" /></>
                                        : <path d="M4 2l10 6-10 6V2z" />
                                    }
                                </svg>
                            </button>
                        </div>
                    ) : null}

                    <RuntimeActionPanel />
                </div>
            </SharedStoreProvider>
        </ScreenRuntimeProvider>
    );
}
