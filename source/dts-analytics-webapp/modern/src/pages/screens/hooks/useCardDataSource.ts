import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { analyticsApi } from '../../../api/analyticsApi';
import type { DataSourceConfig, CardData } from '../types';

interface CardDataSourceResult {
    data: CardData | null;
    loading: boolean;
    error: string | null;
}

export function useCardDataSource(
    dataSource?: DataSourceConfig,
    overrideCardId?: number,
    queryParameters?: Array<{ name: string; value: string }>,
): CardDataSourceResult {
    const [data, setData] = useState<CardData | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

    const baseCardId = dataSource?.type === 'card' ? dataSource.cardConfig?.cardId : undefined;
    const cardId = overrideCardId ?? baseCardId;
    const refreshInterval = dataSource?.type === 'card'
        ? (dataSource.cardConfig?.refreshInterval ?? dataSource.refreshInterval)
        : undefined;

    // Stable serialization to avoid infinite re-renders
    const paramsKey = useMemo(() => JSON.stringify(queryParameters ?? null), [queryParameters]);

    const fetchData = useCallback(async (id: number, params?: Array<{ name: string; value: string }>) => {
        setLoading(true);
        setError(null);
        try {
            const body = params?.length ? { parameters: params } : {};
            const result = await analyticsApi.queryCard(id, body);
            if (result.data?.rows && result.data?.cols) {
                setData({
                    rows: result.data.rows as unknown[][],
                    cols: result.data.cols as CardData['cols'],
                });
            } else if (result.error) {
                setError(String(result.error));
            }
        } catch (e) {
            setError(e instanceof Error ? e.message : 'Card 查询失败');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (intervalRef.current) {
            clearInterval(intervalRef.current);
            intervalRef.current = null;
        }

        if (!cardId || cardId <= 0) {
            setData(null);
            setError(null);
            return;
        }

        // Clear stale data from previous card before fetching new one
        setData(null);
        setError(null);

        const params: Array<{ name: string; value: string }> | undefined =
            paramsKey !== 'null' ? JSON.parse(paramsKey) : undefined;

        fetchData(cardId, params);

        if (refreshInterval && refreshInterval > 0) {
            intervalRef.current = setInterval(() => fetchData(cardId, params), refreshInterval * 1000);
        }

        return () => {
            if (intervalRef.current) {
                clearInterval(intervalRef.current);
                intervalRef.current = null;
            }
        };
    }, [cardId, refreshInterval, fetchData, paramsKey]);

    return { data, loading, error };
}
