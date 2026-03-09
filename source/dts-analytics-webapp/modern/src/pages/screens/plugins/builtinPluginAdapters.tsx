import type { ScreenPluginManifest } from '../../../api/analyticsApi';
import { registerRendererPlugin } from './registry';
import type { RendererPlugin } from './types';

type AdapterFactory = (pluginId: string, componentId: string, version?: string) => RendererPlugin;
type CustomPluginFactory = AdapterFactory | ((pluginId?: string, componentId?: string, version?: string) => RendererPlugin);
type CustomPluginModule = {
    createPlugin?: CustomPluginFactory;
    default?: CustomPluginFactory;
    [key: string]: unknown;
};

const customModules = import.meta.glob('./custom/*.tsx', { eager: true }) as Record<string, CustomPluginModule>;
const CUSTOM_ADAPTERS: Record<string, AdapterFactory> = buildCustomAdapters(customModules);

export function installBuiltinPluginAdapters(manifests: ScreenPluginManifest[]): void {
    for (const plugin of manifests || []) {
        const pluginId = String(plugin?.id ?? '').trim();
        if (!pluginId) continue;
        const version = String(plugin?.version ?? '').trim() || undefined;
        const components = Array.isArray(plugin.components) ? plugin.components : [];
        for (const component of components) {
            const componentId = String(component?.id ?? '').trim();
            if (!componentId) continue;
            const adapterKey = `${pluginId}:${componentId}`;
            const factory = CUSTOM_ADAPTERS[adapterKey];
            if (!factory) continue;
            registerRendererPlugin(factory(pluginId, componentId, version));
        }
    }
}

function buildCustomAdapters(modules: Record<string, CustomPluginModule>): Record<string, AdapterFactory> {
    const out: Record<string, AdapterFactory> = {};
    for (const [filePath, mod] of Object.entries(modules)) {
        const key = parseAdapterKeyFromPath(filePath);
        if (!key) continue;
        const customFactory = resolveCustomFactory(mod);
        if (!customFactory) continue;
        out[key] = (pluginId: string, componentId: string, version?: string) => customFactory(pluginId, componentId, version);
    }
    return out;
}

function parseAdapterKeyFromPath(filePath: string): string {
    const filename = filePath.split('/').pop() || '';
    const stem = filename.endsWith('.tsx') ? filename.slice(0, -4) : filename;
    const split = stem.split('__');
    if (split.length !== 2) {
        return '';
    }
    const pluginId = String(split[0] || '').trim();
    const componentId = String(split[1] || '').trim();
    if (!pluginId || !componentId) {
        return '';
    }
    return `${pluginId}:${componentId}`;
}

function resolveCustomFactory(mod: CustomPluginModule): CustomPluginFactory | null {
    if (typeof mod.createPlugin === 'function') {
        return mod.createPlugin;
    }
    if (typeof mod.default === 'function') {
        return mod.default;
    }
    for (const value of Object.values(mod)) {
        if (typeof value === 'function') {
            return value as CustomPluginFactory;
        }
    }
    return null;
}
