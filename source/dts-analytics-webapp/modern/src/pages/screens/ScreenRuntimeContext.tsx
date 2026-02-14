import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { ScreenGlobalVariable } from './types';

interface ScreenRuntimeContextValue {
    definitions: ScreenGlobalVariable[];
    values: Record<string, string>;
    setVariable: (key: string, value: string) => void;
}

const emptyValue: ScreenRuntimeContextValue = {
    definitions: [],
    values: {},
    setVariable: () => {
        // no-op for unwrapped usage
    },
};

const ScreenRuntimeContext = createContext<ScreenRuntimeContextValue>(emptyValue);

function normalizeDefinitions(definitions: ScreenGlobalVariable[] | undefined): ScreenGlobalVariable[] {
    if (!Array.isArray(definitions)) return [];
    const dedup = new Map<string, ScreenGlobalVariable>();
    for (const item of definitions) {
        const key = typeof item?.key === 'string' ? item.key.trim() : '';
        if (!key) continue;
        dedup.set(key, {
            key,
            label: (item.label || key).trim(),
            type: item.type === 'number' || item.type === 'date' ? item.type : 'string',
            defaultValue: item.defaultValue ?? '',
            description: item.description,
        });
    }
    return Array.from(dedup.values());
}

export function ScreenRuntimeProvider({
    definitions,
    children,
}: {
    definitions?: ScreenGlobalVariable[];
    children: ReactNode;
}) {
    const normalizedDefinitions = useMemo(() => normalizeDefinitions(definitions), [definitions]);
    const [values, setValues] = useState<Record<string, string>>({});

    useEffect(() => {
        setValues((prev) => {
            const next: Record<string, string> = {};
            for (const def of normalizedDefinitions) {
                const prevValue = prev[def.key];
                next[def.key] = prevValue ?? (def.defaultValue ?? '');
            }
            return next;
        });
    }, [normalizedDefinitions]);

    const contextValue = useMemo<ScreenRuntimeContextValue>(() => ({
        definitions: normalizedDefinitions,
        values,
        setVariable: (key: string, value: string) => {
            const safeKey = (key || '').trim();
            if (!safeKey) return;
            setValues((prev) => ({ ...prev, [safeKey]: value }));
        },
    }), [normalizedDefinitions, values]);

    return <ScreenRuntimeContext.Provider value={contextValue}>{children}</ScreenRuntimeContext.Provider>;
}

export function useScreenRuntime() {
    return useContext(ScreenRuntimeContext);
}
