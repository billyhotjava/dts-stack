import { useEffect, useMemo, useState } from 'react';
import { analyticsApi, type DatabaseListItem } from '../../../api/analyticsApi';

interface DatabaseIdPickerProps {
    value: number;
    onChange: (databaseId: number) => void;
    placeholder?: string;
}

let cachedDatabases: DatabaseListItem[] | null = null;
let fetchPromise: Promise<DatabaseListItem[]> | null = null;

function loadDatabases(): Promise<DatabaseListItem[]> {
    if (cachedDatabases) return Promise.resolve(cachedDatabases);
    if (fetchPromise) return fetchPromise;

    fetchPromise = analyticsApi.listDatabases()
        .then((resp) => {
            const list = (resp?.data ?? []).filter((d) => d?.id != null);
            cachedDatabases = list;
            return list;
        })
        .catch(() => {
            fetchPromise = null;
            return [] as DatabaseListItem[];
        });

    return fetchPromise;
}

export function DatabaseIdPicker({ value, onChange, placeholder }: DatabaseIdPickerProps) {
    const [databases, setDatabases] = useState<DatabaseListItem[]>(cachedDatabases ?? []);
    const [loading, setLoading] = useState(!cachedDatabases);

    useEffect(() => {
        if (cachedDatabases) {
            setDatabases(cachedDatabases);
            setLoading(false);
            return;
        }

        let cancelled = false;
        setLoading(true);
        loadDatabases().then((list) => {
            if (!cancelled) {
                setDatabases(list);
                setLoading(false);
            }
        });
        return () => { cancelled = true; };
    }, []);

    const hasCurrentInList = useMemo(
        () => (value > 0 ? databases.some((d) => d.id === value) : true),
        [databases, value],
    );

    return (
        <select
            className="property-input"
            value={value || 0}
            onChange={(e) => onChange(Number(e.target.value))}
            style={value > 0 ? undefined : { color: '#888' }}
        >
            <option value={0}>
                {loading ? '加载中...' : (placeholder ?? '-- 选择数据库 --')}
            </option>
            {!hasCurrentInList && value > 0 && (
                <option value={value}>#{value} (手工输入)</option>
            )}
            {databases.map((db) => (
                <option key={db.id} value={db.id}>
                    #{db.id} {db.name || '(未命名数据库)'}{db.engine ? ` (${db.engine})` : ''}
                </option>
            ))}
        </select>
    );
}

export function invalidateDatabaseCache() {
    cachedDatabases = null;
    fetchPromise = null;
}
