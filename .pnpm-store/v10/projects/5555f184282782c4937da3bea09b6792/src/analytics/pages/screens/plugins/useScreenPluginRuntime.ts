import { useEffect, useState } from 'react';
import { installBuiltinPluginAdapters } from './builtinPluginAdapters';
import { loadScreenPluginManifests } from './manifestLoader';

let loaded = false;
let loadingPromise: Promise<void> | null = null;

function ensureLoaded(): Promise<void> {
    if (loaded) {
        return Promise.resolve();
    }
    if (loadingPromise) {
        return loadingPromise;
    }
    loadingPromise = loadScreenPluginManifests()
        .then((manifests) => {
            installBuiltinPluginAdapters(manifests);
            loaded = true;
        })
        .catch((error) => {
            // Plugin loading must not break runtime.
            console.warn('[screen-plugin] failed to load manifests:', error);
        })
        .finally(() => {
            loadingPromise = null;
        });
    return loadingPromise;
}

export function useScreenPluginRuntime(): number {
    const [version, setVersion] = useState(0);
    useEffect(() => {
        let active = true;
        ensureLoaded().then(() => {
            if (active) {
                setVersion((prev) => prev + 1);
            }
        });
        return () => {
            active = false;
        };
    }, []);
    return version;
}
