import { useState, useEffect, useRef, useCallback } from 'react';
import { analyticsApi } from '../../../api/analyticsApi';
import type { DataSourceConfig, CardData } from '../types';

interface CardDataSourceResult {
    data: CardData | null;
    loading: boolean;
    error: string | null;
}

export function useCardDataSource(dataSource?: DataSourceConfig): CardDataSourceResult {
    const [data, setData] = useState<CardData | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

    const cardId = dataSource?.type === 'card' ? dataSource.cardConfig?.cardId : undefined;
    const refreshInterval = dataSource?.type === 'card'
        ? (dataSource.cardConfig?.refreshInterval ?? dataSource.refreshInterval)
        : undefined;

    const fetchData = useCallback(async (id: number) => {
        setLoading(true);
        setError(null);
        try {
            const result = await analyticsApi.queryCard(id);
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

        fetchData(cardId);

        if (refreshInterval && refreshInterval > 0) {
            intervalRef.current = setInterval(() => fetchData(cardId), refreshInterval * 1000);
        }

        return () => {
            if (intervalRef.current) {
                clearInterval(intervalRef.current);
                intervalRef.current = null;
            }
        };
    }, [cardId, refreshInterval, fetchData]);

    return { data, loading, error };
}
