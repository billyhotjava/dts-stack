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

const customModules = loadBuiltinCustomModules();
const CUSTOM_ADAPTERS: Record<string, AdapterFactory> = buildCustomAdapters(customModules);

export function installBuiltinPluginAdapters(manifests: ScreenPluginManifest[]): void {
    installBuiltinPluginAdaptersFromModules(manifests, customModules);
}

export function ensureBuiltinPluginAdapter(
    pluginId: string,
    componentId: string,
    version?: string,
): boolean {
    return ensureBuiltinPluginAdapterFromModules(pluginId, componentId, version, customModules);
}

export function installBuiltinPluginAdaptersFromModules(
    manifests: ScreenPluginManifest[],
    modules: Record<string, CustomPluginModule>,
): void {
    const adapters = buildCustomAdapters(modules);
    for (const plugin of manifests || []) {
        const pluginId = String(plugin?.id ?? '').trim();
        if (!pluginId) continue;
        const version = String(plugin?.version ?? '').trim() || undefined;
        const components = Array.isArray(plugin.components) ? plugin.components : [];
        for (const component of components) {
            const componentId = String(component?.id ?? '').trim();
            if (!componentId) continue;
            const adapterKey = `${pluginId}:${componentId}`;
            const factory = adapters[adapterKey];
            if (!factory) continue;
            registerRendererPlugin(factory(pluginId, componentId, version));
        }
    }
}

export function ensureBuiltinPluginAdapterFromModules(
    pluginId: string,
    componentId: string,
    version: string | undefined,
    modules: Record<string, CustomPluginModule>,
): boolean {
    const pid = String(pluginId || '').trim();
    const cid = String(componentId || '').trim();
    if (!pid || !cid) {
        return false;
    }
    const adapters = buildCustomAdapters(modules);
    const adapterKey = `${pid}:${cid}`;
    const factory = adapters[adapterKey];
    if (!factory) {
        return false;
    }
    registerRendererPlugin(factory(pid, cid, version));
    return true;
}

function loadBuiltinCustomModules(): Record<string, CustomPluginModule> {
    const meta = import.meta as ImportMeta & {
        glob?: (pattern: string, options: { eager: true }) => Record<string, CustomPluginModule>;
    };
    if (typeof meta.glob !== 'function') {
        return {};
    }
    return meta.glob('./custom/*.tsx', { eager: true }) as Record<string, CustomPluginModule>;
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
