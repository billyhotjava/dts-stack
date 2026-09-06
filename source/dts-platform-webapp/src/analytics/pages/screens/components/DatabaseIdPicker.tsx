import { useCallback, useEffect, useMemo, useState } from 'react';
import { analyticsApi, type PlatformSourceWithDbId } from '../../../api/analyticsApi';

interface DatabaseIdPickerProps {
    value: number;
    onChange: (databaseId: number) => void;
    placeholder?: string;
}

let cachedSources: PlatformSourceWithDbId[] | null = null;
let fetchPromise: Promise<PlatformSourceWithDbId[]> | null = null;

function loadPlatformSources(): Promise<PlatformSourceWithDbId[]> {
    if (cachedSources) return Promise.resolve(cachedSources);
    if (fetchPromise) return fetchPromise;

    fetchPromise = analyticsApi.listPlatformSources()
        .then((list) => {
            const filtered = (Array.isArray(list) ? list : []).filter((s) => s?.platformId != null);
            cachedSources = filtered.length > 0 ? filtered : null;
            return filtered;
        })
        .finally(() => {
            fetchPromise = null;
        });

    return fetchPromise;
}

export function DatabaseIdPicker({ value, onChange, placeholder }: DatabaseIdPickerProps) {
    const [sources, setSources] = useState<PlatformSourceWithDbId[]>(cachedSources ?? []);
    const [loading, setLoading] = useState(!cachedSources);
    const [registering, setRegistering] = useState(false);
    const [error, setError] = useState('');
    const [reloadKey, setReloadKey] = useState(0);

    useEffect(() => {
        if (cachedSources) {
            setSources(cachedSources);
            setLoading(false);
            return;
        }
        let cancelled = false;
        setLoading(true);
        setError('');
        loadPlatformSources().then((list) => {
            if (!cancelled) {
                setSources(list);
                if (list.length === 0) setError('无可用数据源');
            }
        }).catch(() => {
            if (!cancelled) setError('数据源加载失败，请重试');
        }).finally(() => {
            if (!cancelled) setLoading(false);
        });
        return () => { cancelled = true; };
    }, [reloadKey]);

    const handleChange = useCallback(async (e: React.ChangeEvent<HTMLSelectElement>) => {
        const selected = e.target.value;
        // 已经是 analytics DB ID（数字）
        const numVal = Number(selected);
        if (Number.isFinite(numVal) && numVal > 0) {
            onChange(numVal);
            return;
        }
        // 是 platform data source ID（UUID），需要按需注册
        if (!selected || selected === '0') {
            onChange(0);
            return;
        }
        setRegistering(true);
        setError('');
        try {
            const result = await analyticsApi.ensureFromPlatform(selected);
            const dbId = (result as any)?.id;
            if (typeof dbId === 'number' && dbId > 0) {
                // 更新缓存中的 analyticsDbId
                const updated = sources.map((s) =>
                    s.platformId === selected ? { ...s, analyticsDbId: dbId } : s
                );
                cachedSources = updated;
                setSources(updated);
                onChange(dbId);
            } else {
                setError('注册数据源失败');
            }
        } catch (ex: any) {
            setError(ex?.message || '注册数据源失败');
        } finally {
            setRegistering(false);
        }
    }, [sources, onChange]);

    // 当前值是否在列表中
    const hasCurrentInList = useMemo(
        () => (value > 0 ? sources.some((s) => s.analyticsDbId === value) : true),
        [sources, value],
    );

    // 下拉选项值：已注册的用 analyticsDbId，未注册的用 platformId
    const optionValue = (s: PlatformSourceWithDbId) =>
        s.analyticsDbId != null && s.analyticsDbId > 0 ? String(s.analyticsDbId) : s.platformId;

    const statusText = loading
        ? '加载中...'
        : registering
            ? '注册数据源中...'
            : error
                ? `⚠ ${error}`
                : (placeholder ?? '-- 选择数据库 --');

    return (
        <>
        <select
            className="property-input"
            value={value > 0 ? String(value) : '0'}
            onChange={handleChange}
            disabled={loading || registering}
            style={value > 0 ? undefined : { color: '#888' }}
        >
            <option value="0">{statusText}</option>
            {!hasCurrentInList && value > 0 && (
                <option value={String(value)}>#{value} (手工输入)</option>
            )}
            {sources.map((s) => (
                <option key={s.platformId} value={optionValue(s)}>
                    {s.name || '(未命名)'}{s.type ? ` (${s.type})` : ''}
                    {s.analyticsDbId ? ` #${s.analyticsDbId}` : ''}
                </option>
            ))}
        </select>
        {error && !loading && !registering && (
            <button type="button" className="property-input" onClick={() => {
                invalidateDatabaseCache();
                setReloadKey((key) => key + 1);
            }}>重新加载数据源</button>
        )}
        </>
    );
}

export function invalidateDatabaseCache() {
    cachedSources = null;
    fetchPromise = null;
}
