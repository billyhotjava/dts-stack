import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { ScreenGlobalVariable } from './types';

export interface RuntimeVariableEvent {
    id: number;
    at: string;
    key: string;
    value: string;
    source?: string;
}

interface ScreenRuntimeContextValue {
    definitions: ScreenGlobalVariable[];
    values: Record<string, string>;
    events: RuntimeVariableEvent[];
    setVariable: (key: string, value: string, source?: string) => void;
}

const emptyValue: ScreenRuntimeContextValue = {
    definitions: [],
    values: {},
    events: [],
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
    const [events, setEvents] = useState<RuntimeVariableEvent[]>([]);

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
        events,
        setVariable: (key: string, value: string, source?: string) => {
            const safeKey = (key || '').trim();
            if (!safeKey) return;
            setValues((prev) => ({ ...prev, [safeKey]: value }));
            setEvents((prev) => {
                const next: RuntimeVariableEvent = {
                    id: prev.length > 0 ? prev[0].id + 1 : 1,
                    at: new Date().toISOString(),
                    key: safeKey,
                    value: String(value ?? ''),
                    source: source?.trim() || undefined,
                };
                return [next, ...prev].slice(0, 100);
            });
        },
    }), [events, normalizedDefinitions, values]);

    return <ScreenRuntimeContext.Provider value={contextValue}>{children}</ScreenRuntimeContext.Provider>;
}

export function useScreenRuntime() {
    return useContext(ScreenRuntimeContext);
}
