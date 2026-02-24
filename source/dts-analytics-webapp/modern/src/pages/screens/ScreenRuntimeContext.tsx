import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import type { ScreenGlobalVariable } from './types';

export type RuntimeEventKind = 'variable' | 'filter' | 'interaction' | 'drill-down' | 'drill-up' | 'jump';

export interface RuntimeVariableEvent {
    id: number;
    at: string;
    kind: RuntimeEventKind;
    key: string;
    value: string;
    source?: string;
    meta?: string;
}

interface ScreenRuntimeContextValue {
    definitions: ScreenGlobalVariable[];
    values: Record<string, string>;
    getEvents: () => RuntimeVariableEvent[];
    setVariable: (key: string, value: string, source?: string) => void;
    trackEvent: (event: Omit<RuntimeVariableEvent, 'id' | 'at'>) => void;
}

const emptyValue: ScreenRuntimeContextValue = {
    definitions: [],
    values: {},
    getEvents: () => [],
    setVariable: () => {
        // no-op for unwrapped usage
    },
    trackEvent: () => {
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

function inferEventKindBySource(source?: string): RuntimeEventKind {
    const normalized = String(source || '').trim().toLowerCase();
    if (!normalized) return 'variable';
    if (normalized.startsWith('filter-') || normalized.startsWith('map-')) return 'filter';
    if (normalized.startsWith('interaction:')) return 'interaction';
    if (normalized.startsWith('drill:')) return 'drill-down';
    return 'variable';
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

    // Phase 1.5: use ref for events so context consumers don't re-render on every event
    const eventsRef = useRef(events);
    eventsRef.current = events;

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
        getEvents: () => eventsRef.current,
        trackEvent: (event) => {
            const safeKey = (event.key || '').trim() || '__event__';
            setEvents((prev) => {
                const next: RuntimeVariableEvent = {
                    id: prev.length > 0 ? prev[0].id + 1 : 1,
                    at: new Date().toISOString(),
                    kind: event.kind,
                    key: safeKey,
                    value: String(event.value ?? ''),
                    source: event.source?.trim() || undefined,
                    meta: event.meta?.trim() || undefined,
                };
                return [next, ...prev].slice(0, 100);
            });
        },
        setVariable: (key: string, value: string, source?: string) => {
            const safeKey = (key || '').trim();
            if (!safeKey) return;
            setValues((prev) => ({ ...prev, [safeKey]: value }));
            setEvents((prev) => {
                const next: RuntimeVariableEvent = {
                    id: prev.length > 0 ? prev[0].id + 1 : 1,
                    at: new Date().toISOString(),
                    kind: inferEventKindBySource(source),
                    key: safeKey,
                    value: String(value ?? ''),
                    source: source?.trim() || undefined,
                };
                return [next, ...prev].slice(0, 100);
            });
        },
    }), [normalizedDefinitions, values]); // events removed from deps

    return <ScreenRuntimeContext.Provider value={contextValue}>{children}</ScreenRuntimeContext.Provider>;
}

export function useScreenRuntime() {
    return useContext(ScreenRuntimeContext);
}
