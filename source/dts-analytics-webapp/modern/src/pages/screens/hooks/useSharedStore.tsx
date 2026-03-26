/**
 * useSharedStore — Enables components to share computed results.
 *
 * A component can declare `exports: { key: "totalProjects", path: "data.total" }`
 * in its config. After data loads, the value is written to the shared store.
 * Other components can reference it via `{{ shared.totalProjects }}`.
 *
 * This avoids redundant API calls when multiple components need the same metric.
 */
import { createContext, useContext, useState, useCallback, useMemo, type ReactNode } from 'react';

interface SharedStoreContextValue {
    /** Current shared values */
    values: Record<string, unknown>;
    /** Set a shared value (called by a component after data loads) */
    setValue: (key: string, value: unknown) => void;
    /** Get a shared value */
    getValue: (key: string) => unknown;
}

const SharedStoreContext = createContext<SharedStoreContextValue>({
    values: {},
    setValue: () => {},
    getValue: () => undefined,
});

export function SharedStoreProvider({ children }: { children: ReactNode }) {
    const [values, setValues] = useState<Record<string, unknown>>({});

    const setValue = useCallback((key: string, value: unknown) => {
        const safeKey = (key || '').trim();
        if (!safeKey) return;
        setValues((prev) => ({ ...prev, [safeKey]: value }));
    }, []);

    const getValue = useCallback((key: string) => {
        return values[(key || '').trim()];
    }, [values]);

    const ctx = useMemo<SharedStoreContextValue>(
        () => ({ values, setValue, getValue }),
        [values, setValue, getValue],
    );

    return (
        <SharedStoreContext.Provider value={ctx}>
            {children}
        </SharedStoreContext.Provider>
    );
}

export function useSharedStore() {
    return useContext(SharedStoreContext);
}
