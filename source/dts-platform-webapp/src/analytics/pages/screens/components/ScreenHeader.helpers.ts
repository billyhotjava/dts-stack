import type { ScreenConfig } from '../types';

export function buildExploreSessionSteps(config: ScreenConfig): Array<Record<string, unknown>> {
    const now = new Date().toISOString();
    const componentOutline = [...(config.components ?? [])]
        .sort((a, b) => (a.zIndex || 0) - (b.zIndex || 0))
        .slice(0, 20)
        .map((item) => ({
            id: item.id,
            type: item.type,
            name: item.name,
            visible: item.visible !== false,
            dataSourceType: item.dataSource?.sourceType ?? item.dataSource?.type ?? 'static',
        }));
    return [
        {
            at: now,
            title: '大屏快照',
            type: 'screen_snapshot',
            params: {
                screenId: config.id || null,
                screenName: config.name || null,
                width: config.width,
                height: config.height,
                theme: config.theme || null,
                componentCount: config.components?.length ?? 0,
                globalVariableCount: config.globalVariables?.length ?? 0,
            },
        },
        {
            at: now,
            title: '关键组件概览',
            type: 'component_outline',
            params: {
                components: componentOutline,
            },
        },
    ];
}
