/**
 * useEChartsLoader — Lazy-loads ECharts, echarts-wordcloud, echarts-gl, and DataView modules.
 * Extracted from ComponentRenderer to reduce its line count.
 */
import { useEffect, useState } from 'react';
import type { ReactEChartsComponent } from '../renderers/types';
import { resolvePresetMapUrl, fetchGeoJsonWithCache } from '../renderers/shared/geoJsonCache';

const ECHART_COMPONENT_TYPES = new Set([
    'line-chart', 'bar-chart', 'pie-chart', 'gauge-chart', 'gantt-chart',
    'radar-chart', 'funnel-chart', 'scatter-chart', 'map-chart', 'combo-chart',
    'wordcloud-chart', 'treemap-chart', 'sunburst-chart', 'waterfall-chart',
    'globe-chart', 'bar3d-chart', 'scatter3d-chart',
]);

const ECHART_3D_TYPES = new Set(['globe-chart', 'bar3d-chart', 'scatter3d-chart']);

export { ECHART_COMPONENT_TYPES, ECHART_3D_TYPES };

export function isWebGLSupported(): boolean {
    try {
        const canvas = document.createElement('canvas');
        return !!(canvas.getContext('webgl') || canvas.getContext('webgl2'));
    } catch {
        return false;
    }
}

export interface EChartsLoaderResult {
    EChartsComponent: ReactEChartsComponent | null;
    registerMapFn: ((mapName: string, geoJson: unknown) => boolean) | null;
    hasMapFn: ((mapName: string) => boolean) | null;
    mapReadyVersion: number;
}

export function useEChartsLoader(
    type: string,
    config: Record<string, unknown>,
): EChartsLoaderResult {
    const needsECharts = ECHART_COMPONENT_TYPES.has(type);

    const [EChartsComponent, setEChartsComponent] = useState<ReactEChartsComponent | null>(null);
    const [registerMapFn, setRegisterMapFn] = useState<((mapName: string, geoJson: unknown) => boolean) | null>(null);
    const [hasMapFn, setHasMapFn] = useState<((mapName: string) => boolean) | null>(null);
    const [mapReadyVersion, setMapReadyVersion] = useState(0);

    useEffect(() => {
        if (!needsECharts || EChartsComponent) return;
        let cancelled = false;
        const imports: Promise<unknown>[] = [
            import('../../../components/charts/EChartsRuntime'),
        ];
        if (type === 'wordcloud-chart') {
            imports.push(import('echarts-wordcloud'));
        }
        if (ECHART_3D_TYPES.has(type)) {
            const glPkg = 'echarts-gl';
            imports.push(import(/* @vite-ignore */ glPkg).catch(() => null));
        }
        Promise.all(imports).then(([echartsModule]) => {
            const mod = echartsModule as typeof import('../../../components/charts/EChartsRuntime');
            if (!cancelled) {
                setEChartsComponent(() => mod.default as ReactEChartsComponent);
                if (typeof mod.registerEChartsMap === 'function') {
                    setRegisterMapFn(() => mod.registerEChartsMap);
                }
                if (typeof mod.hasEChartsMap === 'function') {
                    setHasMapFn(() => mod.hasEChartsMap);
                }
            }
        });
        return () => { cancelled = true; };
    }, [needsECharts, EChartsComponent, type]);

    useEffect(() => {
        if (type !== 'map-chart' || !registerMapFn) return;
        const mapName = String(config.mapName || config.mapScope || 'dts-map').trim();
        if (!mapName) return;
        const geoJson = config.geoJson;
        const geoJsonUrlRaw = typeof config.geoJsonUrl === 'string' ? config.geoJsonUrl.trim() : '';
        const presetAllowed = config.usePresetGeoJson !== false;
        const presetUrl = presetAllowed ? resolvePresetMapUrl(String(config.mapScope || 'china')) : undefined;
        const geoJsonUrl = geoJsonUrlRaw || presetUrl || '';
        let cancelled = false;
        if (geoJson && typeof geoJson === 'object') {
            if (registerMapFn(mapName, geoJson)) {
                setMapReadyVersion((v) => v + 1);
            }
            return;
        }
        if (!geoJsonUrl) return;
        fetchGeoJsonWithCache(geoJsonUrl).then((loaded) => {
            if (cancelled || !loaded || typeof loaded !== 'object') return;
            if (registerMapFn(mapName, loaded)) {
                setMapReadyVersion((v) => v + 1);
            }
        });
        return () => { cancelled = true; };
    }, [config, registerMapFn, type]);

    return {
        EChartsComponent,
        registerMapFn,
        hasMapFn,
        mapReadyVersion,
    };
}
