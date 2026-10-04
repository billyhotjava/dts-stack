import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { analyticsApi, fetchWithPlatformAuth, HttpError } from '../../../api/analyticsApi';
import { resolveAnalyticsErrorCodeMessage } from '../../../api/errorCodeMessages';
import type { CardParameterBinding, DataSourceConfig, CardData } from '../types';
import { buildApiRuntimeRequest, resolveApiRuntimePayload } from '../apiDataSourceRuntime';
import { runWithRetry, scheduleQueryTask } from './queryScheduler';
import { normalizeTabularData } from './tabularDataAdapter';

interface CardDataSourceResult {
    data: CardData | null;
    loading: boolean;
    error: string | null;
}

const CACHE_TTL_MS = 5000;
const CACHE_MAX_ENTRIES = 200;
const CACHE_CLEANUP_INTERVAL_MS = 30000;
const DEFAULT_CARD_TIMEOUT_MS = 30000;
const DEFAULT_DATASET_TIMEOUT_MS = 30000;
const DEFAULT_API_TIMEOUT_MS = 20000;
const cacheStore = new Map<string, { expiresAt: number; data: CardData }>();
const inflightStore = new Map<string, Promise<CardData>>();

// Periodic cache cleanup to prevent memory leaks
if (typeof window !== 'undefined') {
    setInterval(() => {
        const now = Date.now();
        for (const [key, entry] of cacheStore) {
            if (entry.expiresAt <= now) cacheStore.delete(key);
        }
        // LRU eviction if cache exceeds max entries
        if (cacheStore.size > CACHE_MAX_ENTRIES) {
            const entries = Array.from(cacheStore.entries()).sort((a, b) => a[1].expiresAt - b[1].expiresAt);
            const toRemove = entries.slice(0, cacheStore.size - CACHE_MAX_ENTRIES);
            for (const [key] of toRemove) cacheStore.delete(key);
        }
    }, CACHE_CLEANUP_INTERVAL_MS);
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

function resolveMetricConfig(dataSource?: DataSourceConfig): DataSourceConfig['metricConfig'] | undefined {
    if (!dataSource) return undefined;
    return dataSource.metricConfig;
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

function resolveDatasetParameterName(parameter: unknown): string | undefined {
    if (!parameter || typeof parameter !== 'object' || Array.isArray(parameter)) return undefined;
    const row = parameter as Record<string, unknown>;
    const directName = String(row.name ?? row.slug ?? '').trim();
    if (directName) return directName;
    const target = row.target;
    if (!Array.isArray(target)) return undefined;
    const templateTag = target[1];
    if (!Array.isArray(templateTag) || templateTag[0] !== 'template-tag') return undefined;
    const targetName = String(templateTag[1] ?? '').trim();
    return targetName || undefined;
}

export function buildDatasetRuntimeRequest(
    queryBody: Record<string, unknown>,
    runtimeParams?: Array<{ name: string; value: string }>,
    queryContext?: Record<string, unknown>,
): Record<string, unknown> {
    const body = { ...queryBody };
    const runtimeValues = new Map<string, string>();
    for (const item of runtimeParams ?? []) {
        const name = String(item?.name ?? '').trim();
        if (name) runtimeValues.set(name, String(item?.value ?? ''));
    }

    if (runtimeValues.size > 0) {
        const existing = queryBody.parameters;
        if (Array.isArray(existing)) {
            const matched = new Set<string>();
            const parameters = existing.map((parameter) => {
                const name = resolveDatasetParameterName(parameter);
                if (!name || !runtimeValues.has(name) || !parameter || typeof parameter !== 'object' || Array.isArray(parameter)) {
                    return parameter;
                }
                matched.add(name);
                return { ...(parameter as Record<string, unknown>), value: runtimeValues.get(name) };
            });
            for (const [name, value] of runtimeValues) {
                if (!matched.has(name)) parameters.push({ name, value });
            }
            body.parameters = parameters;
        } else if (existing && typeof existing === 'object') {
            body.parameters = { ...(existing as Record<string, unknown>), ...Object.fromEntries(runtimeValues) };
        } else {
            body.parameters = Array.from(runtimeValues, ([name, value]) => ({ name, value }));
        }
    }

    if (queryContext && Object.keys(queryContext).length > 0) {
        body.queryContext = queryContext;
    }
    return body;
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

    if (sourceType === 'metric') {
        if (!cardId || cardId <= 0) return null;
        const metricConfig = resolveMetricConfig(dataSource);
        const metricId = Number(metricConfig?.metricId ?? 0);
        const metricVersion = String(metricConfig?.metricVersion ?? '').trim();
        return `metric:card:${cardId}:metric:${metricId > 0 ? metricId : ''}:version:${metricVersion}:params:${paramsKey}:ctx:${contextKey}`;
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
            cfg.responsePath ?? '',
            paramsKey,
            contextKey,
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

function resolveDataSourceErrorMessage(error: unknown): string {
    if (error instanceof HttpError) {
        if (error.bodyText) {
            try {
                const payload = JSON.parse(error.bodyText) as {
                    message?: unknown;
                    error?: unknown;
                    code?: unknown;
                    errors?: Record<string, unknown>;
                };
                const code = typeof payload.code === 'string' && payload.code.trim()
                    ? payload.code.trim()
                    : undefined;
                const codeHint = resolveAnalyticsErrorCodeMessage(code);
                const message = typeof payload.message === 'string' && payload.message.trim()
                    ? payload.message.trim()
                    : typeof payload.error === 'string' && payload.error.trim()
                        ? payload.error.trim()
                        : undefined;
                if (message) {
                    if (codeHint) {
                        return code ? `${codeHint} (${code})：${message}` : `${codeHint}：${message}`;
                    }
                    return code ? `${message} (${code})` : message;
                }
                if (payload.errors && typeof payload.errors === 'object') {
                    const values = Object.values(payload.errors)
                        .map((item) => String(item ?? '').trim())
                        .filter(Boolean);
                    if (values.length > 0) {
                        if (codeHint) {
                            const detail = values.join('; ');
                            return code ? `${codeHint} (${code})：${detail}` : `${codeHint}：${detail}`;
                        }
                        return code ? `${values.join('; ')} (${code})` : values.join('; ');
                    }
                }
                if (codeHint) {
                    return code ? `${codeHint} (${code})` : codeHint;
                }
            } catch {
                // Keep default error message below
            }
        }
        const codeHint = resolveAnalyticsErrorCodeMessage(error.code);
        if (codeHint) {
            const codeTag = error.code ? ` (${error.code})` : '';
            const requestTag = error.requestId ? ` [requestId=${error.requestId}]` : '';
            return `${codeHint}${codeTag}${requestTag}`;
        }
        return error.message || `HTTP ${error.status}`;
    }
    if (error instanceof Error && error.message) {
        return error.message;
    }
    return '数据源查询失败';
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
    const baseCardId = sourceType === 'card'
        ? dataSource?.cardConfig?.cardId
        : sourceType === 'metric'
            ? dataSource?.metricConfig?.cardId
            : undefined;
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

        if (sourceType === 'sql') {
            const sqlConfig = resolveSqlConfig(dataSource);
            const available = new Set(mergeBindingsWithRuntime(
                sqlConfig?.parameterBindings, paramsKey !== 'null' ? JSON.parse(paramsKey) : undefined,
            ).filter((item) => item.value.trim().length > 0).map((item) => item.name));
            const required = Array.from((sqlConfig?.query ?? '').replace(/\[\[[\s\S]*?\]\]/g, '').matchAll(/\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*}}/g), (match) => match[1]);
            const missing = required.filter((name) => !available.has(name));
            if (missing.length > 0) {
                setData(null);
                setLoading(false);
                setError(`等待查询参数：${Array.from(new Set(missing)).join('、')}`);
                return;
            }
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
                            return normalizeTabularData(result.data ?? result);
                        }

                        if (sourceType === 'metric') {
                            if (!cardId || cardId <= 0) {
                                throw new Error('Metric 数据源未选择有效 Card');
                            }
                            const requestBody: Record<string, unknown> = {};
                            const params: Array<{ name: string; value: string }> | undefined =
                                paramsKey !== 'null' ? JSON.parse(paramsKey) : undefined;
                            const context: Record<string, unknown> | undefined =
                                contextKey !== 'null' ? JSON.parse(contextKey) : undefined;
                            const metricConfig = resolveMetricConfig(dataSource);
                            const mergedParams = mergeBindingsWithRuntime(metricConfig?.parameterBindings, params);
                            if (mergedParams.length > 0) {
                                requestBody.parameters = mergedParams;
                            }
                            const metricId = metricConfig?.metricId;
                            const metricVersion = metricConfig?.metricVersion?.trim();
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
                            return normalizeTabularData(result.data ?? result);
                        }

                        if (sourceType === 'api') {
                            const cfg = dataSource.apiConfig;
                            if (!cfg?.url?.trim()) {
                                throw new Error('API 数据源未配置 URL');
                            }
                            const request = buildApiRuntimeRequest(cfg, {
                                queryParameters: paramsKey !== 'null' ? JSON.parse(paramsKey) : undefined,
                                queryContext: contextKey !== 'null' ? JSON.parse(contextKey) : undefined,
                            });
                            const headers = new Headers(request.headers);
                            const method = request.method || 'GET';
                            if (method === 'POST' && !headers.has('content-type')) {
                                headers.set('content-type', 'application/json');
                            }
                            const response = await fetchWithPlatformAuth(buildApiUrl(request.url, request.params), {
                                method,
                                headers,
                                body: method === 'POST' ? request.body : undefined,
                            });
                            if (!response.ok) {
                                const text = await response.text().catch(() => '');
                                throw new Error(`API 请求失败: HTTP ${response.status} ${response.statusText} ${text}`.trim());
                            }
                            const ct = response.headers.get('content-type') || '';
                            const payload = ct.includes('application/json')
                                ? await response.json()
                                : await response.text();
                            return normalizeTabularData(resolveApiRuntimePayload(payload, cfg.responsePath));
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
                            return normalizeTabularData(result.data ?? result);
                        }

                        if (sourceType === 'dataset') {
                            const queryBody = dataSource.datasetConfig?.queryBody;
                            if (!queryBody || typeof queryBody !== 'object') {
                                throw new Error('Dataset 数据源未配置 queryBody');
                            }
                            const body = buildDatasetRuntimeRequest(
                                queryBody as Record<string, unknown>,
                                paramsKey !== 'null' ? JSON.parse(paramsKey) : undefined,
                                contextKey !== 'null' ? JSON.parse(contextKey) : undefined,
                            );
                            const result = await analyticsApi.runDatasetQuery(body);
                            if (result.error) {
                                throw new Error(String(result.error));
                            }
                            return normalizeTabularData(result.data ?? result);
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
                setError(resolveDataSourceErrorMessage(e));
            }
        } finally {
            if (requestSeqRef.current === requestSeq) {
                setLoading(false);
            }
        }
    }, [cacheKey, cardId, contextKey, dataSource, databaseId, paramsKey, queryTimeoutMs, sourceType]);

    // Pause polling when the tab is hidden (e.g. user opened drill page in a new tab).
    // Without this, background tabs keep firing API requests → 401 refresh races →
    // ERR_INSUFFICIENT_RESOURCES storm that exhausts CPU and memory.
    const [tabVisible, setTabVisible] = useState(() => typeof document !== 'undefined' ? document.visibilityState === 'visible' : true);

    useEffect(() => {
        const onVisibility = () => setTabVisible(document.visibilityState === 'visible');
        document.addEventListener('visibilitychange', onVisibility);
        return () => document.removeEventListener('visibilitychange', onVisibility);
    }, []);

    useEffect(() => {
        if (intervalRef.current) {
            clearInterval(intervalRef.current);
            intervalRef.current = null;
        }

        // Only fetch when the tab is visible
        if (tabVisible) {
            fetchData();
        }

        if (tabVisible && refreshInterval && refreshInterval > 0 && sourceType !== 'static') {
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
    }, [dataSource, fetchData, refreshInterval, sourceType, tabVisible]);

    return { data, loading, error };
}
