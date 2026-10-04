import type { RendererPlugin } from './types';

const pluginRegistry = new Map<string, RendererPlugin>();

export function buildPluginRuntimeId(pluginId: string, componentId: string, version?: string): string {
    const pid = (pluginId || '').trim();
    const cid = (componentId || '').trim();
    const ver = (version || '').trim();
    if (!pid || !cid) return '';
    return ver ? `${pid}:${cid}@${ver}` : `${pid}:${cid}`;
}

export function registerRendererPlugin(plugin: RendererPlugin): void {
    const id = (plugin?.id || '').trim();
    if (!id) return;
    pluginRegistry.set(id, plugin);
}

export function unregisterRendererPlugin(id: string): void {
    const key = (id || '').trim();
    if (!key) return;
    pluginRegistry.delete(key);
}

export function getRendererPlugin(id: string): RendererPlugin | undefined {
    const key = (id || '').trim();
    if (!key) return undefined;
    return pluginRegistry.get(key);
}

export function listRendererPlugins(): RendererPlugin[] {
    return Array.from(pluginRegistry.values());
}

