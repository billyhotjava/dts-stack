import { buildPluginRuntimeId } from './registry';

export interface ComponentPluginMeta {
    pluginId: string;
    componentId: string;
    version?: string;
}

export function readComponentPluginMeta(config: Record<string, unknown> | undefined): ComponentPluginMeta | null {
    if (!config || typeof config !== 'object') return null;
    const raw = config.__plugin;
    if (!raw || typeof raw !== 'object') return null;
    const row = raw as Record<string, unknown>;
    const pluginId = String(row.pluginId ?? '').trim();
    const componentId = String(row.componentId ?? '').trim();
    const version = String(row.version ?? '').trim();
    if (!pluginId || !componentId) return null;
    return {
        pluginId,
        componentId,
        version: version || undefined,
    };
}

export function resolveRuntimePluginId(meta: ComponentPluginMeta | null): string {
    if (!meta) return '';
    return buildPluginRuntimeId(meta.pluginId, meta.componentId, meta.version);
}

