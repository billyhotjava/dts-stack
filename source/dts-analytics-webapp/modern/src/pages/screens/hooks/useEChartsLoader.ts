/**
 * useEChartsLoader — Lazy-loads ECharts, echarts-wordcloud, echarts-gl, and DataView modules.
 * Extracted from ComponentRenderer to reduce its line count.
 */
import { useEffect, useMemo, useState, type ComponentType } from 'react';
import type { ReactEChartsComponent, DataViewModule } from '../renderers/types';
import { resolvePresetMapUrl, fetchGeoJsonWithCache } from '../renderers/shared/geoJsonCache';

const ECHART_COMPONENT_TYPES = new Set([
    'line-chart', 'bar-chart', 'pie-chart', 'gauge-chart', 'gantt-chart',
    'radar-chart', 'funnel-chart', 'scatter-chart', 'map-chart', 'combo-chart',
    'wordcloud-chart', 'treemap-chart', 'sunburst-chart', 'waterfall-chart',
    'globe-chart', 'bar3d-chart', 'scatter3d-chart',
]);

const ECHART_3D_TYPES = new Set(['globe-chart', 'bar3d-chart', 'scatter3d-chart']);

const DATAV_COMPONENT_TYPES = new Set([
    'border-box', 'decoration', 'scroll-board', 'scroll-ranking',
    'water-level', 'digital-flop',
]);

export { ECHART_COMPONENT_TYPES, ECHART_3D_TYPES, DATAV_COMPONENT_TYPES };

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
    dataViewModule: DataViewModule | null;
    borderBoxComponents: Record<number, ComponentType<{ children?: React.ReactNode; color?: string[] }>> | null;
    decorationComponents: Record<number, ComponentType<{ color?: string[]; style?: React.CSSProperties }>> | null;
    mapReadyVersion: number;
}

export function useEChartsLoader(
    type: string,
    config: Record<string, unknown>,
): EChartsLoaderResult {
    const needsECharts = ECHART_COMPONENT_TYPES.has(type);
    const needsDataV = DATAV_COMPONENT_TYPES.has(type);

    const [EChartsComponent, setEChartsComponent] = useState<ReactEChartsComponent | null>(null);
    const [registerMapFn, setRegisterMapFn] = useState<((mapName: string, geoJson: unknown) => boolean) | null>(null);
    const [hasMapFn, setHasMapFn] = useState<((mapName: string) => boolean) | null>(null);
    const [dataViewModule, setDataViewModule] = useState<DataViewModule | null>(null);
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

    useEffect(() => {
        if (!needsDataV || dataViewModule) return;
        let cancelled = false;
        import('@jiaminghi/data-view-react').then((mod) => {
            if (!cancelled) setDataViewModule(mod);
        });
        return () => { cancelled = true; };
    }, [needsDataV, dataViewModule]);

    const borderBoxComponents = useMemo(() => {
        if (!dataViewModule) return null;
        return {
            1: dataViewModule.BorderBox1, 2: dataViewModule.BorderBox2,
            3: dataViewModule.BorderBox3, 4: dataViewModule.BorderBox4,
            5: dataViewModule.BorderBox5, 6: dataViewModule.BorderBox6,
            7: dataViewModule.BorderBox7, 8: dataViewModule.BorderBox8,
            9: dataViewModule.BorderBox9, 10: dataViewModule.BorderBox10,
            11: dataViewModule.BorderBox11, 12: dataViewModule.BorderBox12,
            13: dataViewModule.BorderBox13,
        } as Record<number, ComponentType<{ children?: React.ReactNode; color?: string[] }>>;
    }, [dataViewModule]);

    const decorationComponents = useMemo(() => {
        if (!dataViewModule) return null;
        return {
            1: dataViewModule.Decoration1, 2: dataViewModule.Decoration2,
            3: dataViewModule.Decoration3, 4: dataViewModule.Decoration4,
            5: dataViewModule.Decoration5, 6: dataViewModule.Decoration6,
            7: dataViewModule.Decoration7, 8: dataViewModule.Decoration8,
            9: dataViewModule.Decoration9, 10: dataViewModule.Decoration10,
            11: dataViewModule.Decoration11, 12: dataViewModule.Decoration12,
        } as Record<number, ComponentType<{ color?: string[]; style?: React.CSSProperties }>>;
    }, [dataViewModule]);

    return {
        EChartsComponent,
        registerMapFn,
        hasMapFn,
        dataViewModule,
        borderBoxComponents,
        decorationComponents,
        mapReadyVersion,
    };
}
