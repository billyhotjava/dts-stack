import { analyticsApi, type ScreenPluginManifest } from '../../../api/analyticsApi';

let manifestCache: ScreenPluginManifest[] | null = null;
let loadingPromise: Promise<ScreenPluginManifest[]> | null = null;

function normalizeManifests(input: unknown): ScreenPluginManifest[] {
    if (!Array.isArray(input)) {
        return [];
    }
    return input as ScreenPluginManifest[];
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
            const manifests = normalizeManifests(data);
            manifestCache = manifests;
            return manifests;
        })
        .finally(() => {
            loadingPromise = null;
        });
    return loadingPromise;
}

export function clearScreenPluginManifestCache(): void {
    manifestCache = null;
}

