import { analyticsApi, type ScreenPluginManifest } from '../../../api/analyticsApi';

let manifestCache: ScreenPluginManifest[] | null = null;
let loadingPromise: Promise<ScreenPluginManifest[]> | null = null;

type JsonModuleLike = {
    default?: unknown;
};

const localManifestModules = import.meta.glob('./custom/*.manifest.json', { eager: true }) as Record<string, JsonModuleLike>;

const ID_PATTERN = /^[a-z][a-z0-9-]{1,63}$/;
const SEMVER_PATTERN = /^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$/;

function normalizeManifests(input: unknown, sourceLabel: string): ScreenPluginManifest[] {
    if (!Array.isArray(input)) {
        return [];
    }
    return input
        .map((item) => normalizeManifestEntry(item, sourceLabel))
        .filter((item): item is ScreenPluginManifest => !!item);
}

function normalizeManifestEntry(input: unknown, sourceLabel: string): ScreenPluginManifest | null {
    if (!input || typeof input !== 'object') {
        return null;
    }
    const row = input as ScreenPluginManifest;
    const id = String(row.id ?? '').trim();
    if (!id) {
        console.warn('[screen-plugin] skip manifest without id:', sourceLabel);
        return null;
    }
    if (!ID_PATTERN.test(id)) {
        console.warn('[screen-plugin] skip manifest with invalid id:', id, sourceLabel);
        return null;
    }
    const version = String(row.version ?? '').trim();
    if (version && !SEMVER_PATTERN.test(version)) {
        console.warn('[screen-plugin] plugin version is not semver:', id, version, sourceLabel);
    }

    const normalizedComponents = normalizePluginComponents(id, row.components, sourceLabel);
    const normalizedDataSources = normalizePluginDataSources(id, row.dataSources, sourceLabel);
    return {
        ...row,
        id,
        version: version || row.version,
        components: normalizedComponents,
        dataSources: normalizedDataSources,
    };
}

function normalizePluginComponents(
    pluginId: string,
    components: ScreenPluginManifest['components'],
    sourceLabel: string,
): ScreenPluginManifest['components'] {
    if (!Array.isArray(components)) {
        return [];
    }
    const dedupe = new Set<string>();
    const out: NonNullable<ScreenPluginManifest['components']> = [];
    for (const item of components) {
        if (!item || typeof item !== 'object') {
            continue;
        }
        const componentId = String(item.id ?? '').trim();
        if (!componentId) {
            console.warn('[screen-plugin] skip component without id:', pluginId, sourceLabel);
            continue;
        }
        if (!ID_PATTERN.test(componentId)) {
            console.warn('[screen-plugin] skip component with invalid id:', pluginId, componentId, sourceLabel);
            continue;
        }
        if (dedupe.has(componentId)) {
            console.warn('[screen-plugin] duplicated component id in manifest, keep first:', pluginId, componentId, sourceLabel);
            continue;
        }
        dedupe.add(componentId);
        out.push({
            ...item,
            id: componentId,
        });
    }
    return out;
}

function normalizePluginDataSources(
    pluginId: string,
    dataSources: ScreenPluginManifest['dataSources'],
    sourceLabel: string,
): ScreenPluginManifest['dataSources'] {
    if (!Array.isArray(dataSources)) {
        return [];
    }
    const dedupe = new Set<string>();
    const out: NonNullable<ScreenPluginManifest['dataSources']> = [];
    for (const item of dataSources) {
        if (!item || typeof item !== 'object') continue;
        const id = String(item.id ?? '').trim();
        if (!id) {
            console.warn('[screen-plugin] skip dataSource without id:', pluginId, sourceLabel);
            continue;
        }
        if (!ID_PATTERN.test(id)) {
            console.warn('[screen-plugin] skip dataSource with invalid id:', pluginId, id, sourceLabel);
            continue;
        }
        if (dedupe.has(id)) {
            console.warn('[screen-plugin] duplicated dataSource id in manifest, keep first:', pluginId, id, sourceLabel);
            continue;
        }
        dedupe.add(id);
        out.push({
            ...item,
            id,
        });
    }
    return out;
}

function normalizeLocalManifestEntries(input: unknown, sourceLabel: string): ScreenPluginManifest[] {
    if (!input) {
        return [];
    }
    if (Array.isArray(input)) {
        return input
            .map((item) => normalizeManifestEntry(item, sourceLabel))
            .filter((item): item is ScreenPluginManifest => !!item);
    }
    if (typeof input === 'object') {
        const row = input as Record<string, unknown>;
        const bundled = Array.isArray(row.plugins) ? row.plugins : null;
        if (bundled) {
            return bundled
                .map((item) => normalizeManifestEntry(item, sourceLabel))
                .filter((item): item is ScreenPluginManifest => !!item);
        }
    }
    const single = normalizeManifestEntry(input, sourceLabel);
    return single ? [single] : [];
}

function loadLocalManifests(): ScreenPluginManifest[] {
    const out: ScreenPluginManifest[] = [];
    for (const [path, mod] of Object.entries(localManifestModules)) {
        const items = normalizeLocalManifestEntries(mod?.default ?? mod, path);
        if (items.length === 0) {
            console.warn('[screen-plugin] skip invalid local manifest:', path);
            continue;
        }
        for (const item of items) {
            out.push(item);
        }
    }
    return out;
}

function mergeManifests(remote: ScreenPluginManifest[], local: ScreenPluginManifest[]): ScreenPluginManifest[] {
    if (local.length === 0) {
        return remote;
    }
    const merged = new Map<string, ScreenPluginManifest>();

    for (const item of remote) {
        const id = String(item?.id ?? '').trim();
        if (!id) continue;
        merged.set(id, item);
    }

    for (const item of local) {
        const id = String(item?.id ?? '').trim();
        if (!id) continue;
        const existing = merged.get(id);
        if (!existing) {
            merged.set(id, item);
            continue;
        }
        const existingComponents = Array.isArray(existing.components) ? existing.components : [];
        const localComponents = Array.isArray(item.components) ? item.components : [];
        const componentMap = new Map<string, unknown>();
        for (const comp of existingComponents) {
            const compId = String((comp as { id?: string } | undefined)?.id ?? '').trim();
            if (compId) componentMap.set(compId, comp);
        }
        for (const comp of localComponents) {
            const compId = String((comp as { id?: string } | undefined)?.id ?? '').trim();
            if (!compId) continue;
            // Local manifest should override remote component definition during development.
            componentMap.set(compId, comp);
        }
        merged.set(id, {
            ...existing,
            ...item,
            components: Array.from(componentMap.values()) as ScreenPluginManifest['components'],
        });
    }

    return Array.from(merged.values());
}

export async function loadScreenPluginManifests(force = false): Promise<ScreenPluginManifest[]> {
    if (!force && manifestCache) {
        return manifestCache;
    }
    if (!force && loadingPromise) {
        return loadingPromise;
    }

    loadingPromise = analyticsApi.listScreenPlugins()
        .then((data) => {
            const remoteManifests = normalizeManifests(data, 'remote:/analytics/api/screen-plugins');
            const localManifests = loadLocalManifests();
            const manifests = mergeManifests(remoteManifests, localManifests);
            manifestCache = manifests;
            return manifests;
        })
        .catch((error) => {
            const localManifests = loadLocalManifests();
            if (localManifests.length > 0) {
                console.warn('[screen-plugin] failed to load remote manifests, fallback to local manifests:', error);
                manifestCache = localManifests;
                return localManifests;
            }
            throw error;
        })
        .finally(() => {
            loadingPromise = null;
        });
    return loadingPromise;
}

export function clearScreenPluginManifestCache(): void {
    manifestCache = null;
}
