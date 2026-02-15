import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { analyticsApi } from '../../../api/analyticsApi';
import type { CardParameterBinding, DataSourceConfig, CardData } from '../types';
import { runWithRetry, scheduleQueryTask } from './queryScheduler';

interface CardDataSourceResult {
    data: CardData | null;
    loading: boolean;
    error: string | null;
}

const CACHE_TTL_MS = 5000;
const DEFAULT_CARD_TIMEOUT_MS = 30000;
const DEFAULT_DATASET_TIMEOUT_MS = 30000;
const DEFAULT_API_TIMEOUT_MS = 20000;
const cacheStore = new Map<string, { expiresAt: number; data: CardData }>();
const inflightStore = new Map<string, Promise<CardData>>();

function toCardData(payload: unknown): CardData {
    if (payload && typeof payload === 'object' && !Array.isArray(payload)) {
        const obj = payload as Record<string, unknown>;
        const rows = obj.rows;
        const cols = obj.cols;
        if (Array.isArray(rows) && Array.isArray(cols)) {
            return {
                rows: rows as unknown[][],
                cols: (cols as Array<Record<string, unknown>>).map((c, idx) => ({
                    name: String(c.name ?? `col_${idx + 1}`),
                    display_name: String(c.display_name ?? c.name ?? `列${idx + 1}`),
                    base_type: String(c.base_type ?? 'type/Text'),
                })),
            };
        }
        if (obj.data && typeof obj.data === 'object') {
            return toCardData(obj.data);
        }
    }

    if (Array.isArray(payload) && payload.length > 0 && typeof payload[0] === 'object' && !Array.isArray(payload[0])) {
        const objects = payload as Array<Record<string, unknown>>;
        const keySet = new Set<string>();
        for (const row of objects) {
            Object.keys(row || {}).forEach((k) => keySet.add(k));
        }
        const keys = Array.from(keySet);
        return {
            rows: objects.map((row) => keys.map((k) => row?.[k] ?? null)),
            cols: keys.map((k) => ({ name: k, display_name: k, base_type: 'type/Text' })),
        };
    }

    if (Array.isArray(payload) && payload.length > 0 && Array.isArray(payload[0])) {
        const rows = payload as unknown[][];
        const width = rows[0]?.length ?? 0;
        return {
            rows,
            cols: Array.from({ length: width }, (_, i) => ({
                name: `col_${i + 1}`,
                display_name: `列${i + 1}`,
                base_type: 'type/Text',
            })),
        };
    }

    if (Array.isArray(payload) && payload.length === 0) {
        return { rows: [], cols: [] };
    }

    throw new Error('数据源返回格式不支持，请返回 rows/cols 或数组结构');
}

function buildApiUrl(baseUrl: string, params?: Record<string, string>): string {
    const url = new URL(baseUrl, window.location.origin);
    if (params) {
        for (const [k, v] of Object.entries(params)) {
            url.searchParams.set(k, v);
        }
    }
    return url.toString();
}

function parseDatabaseId(dataSource?: DataSourceConfig): number | null {
    if (!dataSource) return null;
    const sourceType = resolveSourceType(dataSource);
    if (sourceType !== 'sql') return null;
    const sqlConfig = resolveSqlConfig(dataSource);
    const raw = sqlConfig?.databaseId ?? sqlConfig?.connectionId;
    const n = Number(raw);
    if (!Number.isFinite(n) || n <= 0) return null;
    return n;
}

function resolveSourceType(dataSource?: DataSourceConfig): 'static' | 'card' | 'api' | 'sql' | 'dataset' | 'metric' {
    if (!dataSource) return 'static';
    const sourceType = ((dataSource.sourceType ?? dataSource.type) || '').toLowerCase();
    if (!sourceType || sourceType === 'static') return 'static';
    if (sourceType === 'database' || sourceType === 'sql') return 'sql';
    if (sourceType === 'card' || sourceType === 'api' || sourceType === 'dataset' || sourceType === 'metric') {
        return sourceType;
    }
    return 'static';
}

function resolveSqlConfig(dataSource?: DataSourceConfig): DataSourceConfig['sqlConfig'] | DataSourceConfig['databaseConfig'] | undefined {
    if (!dataSource) return undefined;
    return dataSource.sqlConfig ?? dataSource.databaseConfig;
}

function normalizeParameterBindings(bindings?: CardParameterBinding[]): CardParameterBinding[] {
    if (!Array.isArray(bindings)) return [];
    return bindings
        .map((item) => ({
            name: String(item?.name ?? '').trim(),
            variableKey: item?.variableKey ? String(item.variableKey).trim() : undefined,
            value: item?.value == null ? undefined : String(item.value),
        }))
        .filter((item) => item.name.length > 0);
}

function mergeBindingsWithRuntime(
    bindings: CardParameterBinding[] | undefined,
    runtimeParams: Array<{ name: string; value: string }> | undefined,
): Array<{ name: string; value: string }> {
    const merged = new Map<string, string>();
    for (const item of runtimeParams ?? []) {
        const key = String(item?.name ?? '').trim();
        if (!key) continue;
        merged.set(key, String(item?.value ?? ''));
    }
    for (const item of normalizeParameterBindings(bindings)) {
        const key = item.name;
        if (merged.has(key)) continue;
        const value = item.value ?? '';
        merged.set(key, String(value));
    }
    return Array.from(merged.entries()).map(([name, value]) => ({ name, value }));
}

function getCacheKey(
    sourceType: 'static' | 'card' | 'api' | 'sql' | 'dataset' | 'metric',
    dataSource: DataSourceConfig | undefined,
    cardId: number | undefined,
    databaseId: number | null,
    paramsKey: string,
    contextKey: string,
): string | null {
    if (!dataSource || !sourceType || sourceType === 'static') return null;

    if (sourceType === 'card') {
        if (!cardId || cardId <= 0) return null;
        return `card:${cardId}:params:${paramsKey}:ctx:${contextKey}`;
    }

    if (sourceType === 'sql') {
        if (!databaseId || databaseId <= 0) return null;
        const query = (resolveSqlConfig(dataSource)?.query ?? '').trim();
        if (!query) return null;
        const timeout = resolveSqlConfig(dataSource)?.queryTimeoutSeconds ?? '';
        const maxRows = resolveSqlConfig(dataSource)?.maxRows ?? '';
        return `db:${databaseId}:sql:${query}:params:${paramsKey}:ctx:${contextKey}:timeout:${timeout}:max:${maxRows}`;
    }

    if (sourceType === 'dataset') {
        const queryBody = dataSource.datasetConfig?.queryBody;
        if (!queryBody || typeof queryBody !== 'object') return null;
        return `dataset:${JSON.stringify(queryBody)}:params:${paramsKey}:ctx:${contextKey}`;
    }

    if (sourceType === 'api') {
        const cfg = dataSource.apiConfig;
        if (!cfg?.url?.trim()) return null;
        return [
            'api',
            cfg.method ?? 'GET',
            cfg.url,
            JSON.stringify(cfg.params ?? {}),
            JSON.stringify(cfg.headers ?? {}),
            cfg.body ?? '',
        ].join(':');
    }

    return null;
}

function resolveQueryTimeoutMs(
    sourceType: 'static' | 'card' | 'api' | 'sql' | 'dataset' | 'metric',
    dataSource?: DataSourceConfig,
): number {
    if (sourceType === 'sql') {
        const sqlConfig = resolveSqlConfig(dataSource);
        const timeoutSeconds = Number(sqlConfig?.queryTimeoutSeconds ?? 0);
        if (Number.isFinite(timeoutSeconds) && timeoutSeconds > 0) {
            // SQL 已有后端 query_timeout，这里只加前端保护超时。
            return Math.min(Math.max(Math.round(timeoutSeconds * 1000 + 2000), 5000), 180000);
        }
        return DEFAULT_CARD_TIMEOUT_MS;
    }
    if (sourceType === 'dataset') {
        return DEFAULT_DATASET_TIMEOUT_MS;
    }
    if (sourceType === 'api') {
        return DEFAULT_API_TIMEOUT_MS;
    }
    return DEFAULT_CARD_TIMEOUT_MS;
}

function getCached(key: string | null): CardData | null {
    if (!key) return null;
    const hit = cacheStore.get(key);
    if (!hit) return null;
    if (hit.expiresAt <= Date.now()) {
        cacheStore.delete(key);
        return null;
    }
    return hit.data;
}

function setCached(key: string | null, data: CardData): void {
    if (!key) return;
    cacheStore.set(key, { expiresAt: Date.now() + CACHE_TTL_MS, data });
}

async function dedupe(key: string | null, fetcher: () => Promise<CardData>): Promise<CardData> {
    if (!key) return await fetcher();
    const inflight = inflightStore.get(key);
    if (inflight) return await inflight;
    const p = fetcher();
    inflightStore.set(key, p);
    try {
        return await p;
    } finally {
        inflightStore.delete(key);
    }
}

export function useCardDataSource(
    dataSource?: DataSourceConfig,
    overrideCardId?: number,
    queryParameters?: Array<{ name: string; value: string }>,
    queryContext?: Record<string, unknown>,
): CardDataSourceResult {
    const [data, setData] = useState<CardData | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
    const requestSeqRef = useRef(0);

    const sourceType = resolveSourceType(dataSource);
    const baseCardId = sourceType === 'card' ? dataSource?.cardConfig?.cardId : undefined;
    const cardId = overrideCardId ?? baseCardId;
    const databaseId = parseDatabaseId(dataSource);
    const refreshInterval = sourceType === 'card'
        ? (dataSource?.cardConfig?.refreshInterval ?? dataSource?.refreshInterval)
        : dataSource?.refreshInterval;

    const paramsKey = useMemo(() => JSON.stringify(queryParameters ?? null), [queryParameters]);
    const contextKey = useMemo(() => JSON.stringify(queryContext ?? null), [queryContext]);
    const cacheKey = useMemo(
        () => getCacheKey(sourceType, dataSource, cardId, databaseId, paramsKey, contextKey),
        [sourceType, dataSource, cardId, databaseId, paramsKey, contextKey],
    );
    const queryTimeoutMs = useMemo(
        () => resolveQueryTimeoutMs(sourceType, dataSource),
        [sourceType, dataSource],
    );

    const fetchData = useCallback(async () => {
        const requestSeq = requestSeqRef.current + 1;
        requestSeqRef.current = requestSeq;

        if (!dataSource || sourceType === 'static') {
            if (requestSeqRef.current === requestSeq) {
                setData(null);
                setError(null);
                setLoading(false);
            }
            return;
        }

        const cached = getCached(cacheKey);
        if (cached) {
            if (requestSeqRef.current === requestSeq) {
                setData(cached);
                setError(null);
                setLoading(false);
            }
            return;
        }

        setLoading(true);
        setError(null);

        try {
            const next = await dedupe(cacheKey, async () => (
                await scheduleQueryTask(
                    async () => await runWithRetry(async () => {
                        if (sourceType === 'card') {
                            if (!cardId || cardId <= 0) {
                                throw new Error('Card 数据源未选择有效 Card');
                            }
                            const requestBody: Record<string, unknown> = {};
                            const params: Array<{ name: string; value: string }> | undefined =
                                paramsKey !== 'null' ? JSON.parse(paramsKey) : undefined;
                            const context: Record<string, unknown> | undefined =
                                contextKey !== 'null' ? JSON.parse(contextKey) : undefined;

                            if (params?.length) {
                                requestBody.parameters = params;
                            }
                            const metricId = dataSource.cardConfig?.metricId;
                            const metricVersion = dataSource.cardConfig?.metricVersion?.trim();
                            if ((metricId ?? 0) > 0 || (metricVersion && metricVersion.length > 0)) {
                                requestBody.semantic = {
                                    metricId: (metricId ?? 0) > 0 ? metricId : undefined,
                                    metricVersion: metricVersion && metricVersion.length > 0 ? metricVersion : undefined,
                                };
                            }
                            if (context && Object.keys(context).length > 0) {
                                requestBody.queryContext = context;
                            }

                            const result = await analyticsApi.queryCard(cardId, requestBody);
                            if (result.error) {
                                throw new Error(String(result.error));
                            }
                            return toCardData(result.data ?? result);
                        }

                        if (sourceType === 'api') {
                            const cfg = dataSource.apiConfig;
                            if (!cfg?.url?.trim()) {
                                throw new Error('API 数据源未配置 URL');
                            }
                            const method = cfg.method || 'GET';
                            const headers = new Headers(cfg.headers ?? {});
                            if (method === 'POST' && !headers.has('content-type')) {
                                headers.set('content-type', 'application/json');
                            }
                            const response = await fetch(buildApiUrl(cfg.url, cfg.params), {
                                method,
                                headers,
                                credentials: 'include',
                                body: method === 'POST' ? (cfg.body ?? '') : undefined,
                            });
                            if (!response.ok) {
                                const text = await response.text().catch(() => '');
                                throw new Error(`API 请求失败: HTTP ${response.status} ${response.statusText} ${text}`.trim());
                            }
                            const ct = response.headers.get('content-type') || '';
                            const payload = ct.includes('application/json')
                                ? await response.json()
                                : await response.text();
                            return toCardData(payload);
                        }

                        if (sourceType === 'sql') {
                            if (!databaseId || databaseId <= 0) {
                                throw new Error('数据库数据源未配置有效 databaseId');
                            }
                            const sqlConfig = resolveSqlConfig(dataSource);
                            const query = sqlConfig?.query ?? '';
                            if (!query.trim()) {
                                throw new Error('数据库数据源未配置 SQL');
                            }
                            const mergedParams = mergeBindingsWithRuntime(sqlConfig?.parameterBindings, paramsKey !== 'null' ? JSON.parse(paramsKey) : undefined);
                            const timeout = Number(sqlConfig?.queryTimeoutSeconds ?? 0);
                            const maxRows = Number(sqlConfig?.maxRows ?? 0);
                            const result = await analyticsApi.runDatasetQuery({
                                database: databaseId,
                                type: 'native',
                                native: { query },
                                parameters: mergedParams,
                                ...(Number.isFinite(timeout) && timeout > 0 ? { query_timeout: timeout } : {}),
                                ...(Number.isFinite(maxRows) && maxRows > 0 ? { constraints: { 'max-results': maxRows } } : {}),
                                ...(contextKey !== 'null' ? { queryContext: JSON.parse(contextKey) } : {}),
                            });
                            if (result.error) {
                                throw new Error(String(result.error));
                            }
                            return toCardData(result.data ?? result);
                        }

                        if (sourceType === 'dataset') {
                            const queryBody = dataSource.datasetConfig?.queryBody;
                            if (!queryBody || typeof queryBody !== 'object') {
                                throw new Error('Dataset 数据源未配置 queryBody');
                            }
                            const body = { ...(queryBody as Record<string, unknown>) };
                            if (contextKey !== 'null') {
                                body.queryContext = JSON.parse(contextKey);
                            }
                            const result = await analyticsApi.runDatasetQuery(body);
                            if (result.error) {
                                throw new Error(String(result.error));
                            }
                            return toCardData(result.data ?? result);
                        }

                        throw new Error(`暂不支持的数据源类型: ${String(sourceType)}`);
                    }, {
                        maxRetries: sourceType === 'api' ? 2 : 1,
                    }),
                    {
                        timeoutMs: queryTimeoutMs,
                    },
                )
            ));

            setCached(cacheKey, next);
            if (requestSeqRef.current === requestSeq) {
                setData(next);
            }
        } catch (e) {
            if (requestSeqRef.current === requestSeq) {
                setData(null);
                setError(e instanceof Error ? e.message : '数据源查询失败');
            }
        } finally {
            if (requestSeqRef.current === requestSeq) {
                setLoading(false);
            }
        }
    }, [cacheKey, cardId, contextKey, dataSource, databaseId, paramsKey, queryTimeoutMs, sourceType]);

    useEffect(() => {
        if (intervalRef.current) {
            clearInterval(intervalRef.current);
            intervalRef.current = null;
        }

        fetchData();

        if (refreshInterval && refreshInterval > 0 && sourceType !== 'static') {
            intervalRef.current = setInterval(() => {
                fetchData();
            }, refreshInterval * 1000);
        }

        return () => {
            if (intervalRef.current) {
                clearInterval(intervalRef.current);
                intervalRef.current = null;
            }
        };
    }, [dataSource, fetchData, refreshInterval, sourceType]);

    return { data, loading, error };
}
