import { useEffect, useMemo, useState } from 'react';
import { analyticsApi, type DatabaseListItem, type DatasetCachePolicy, type DatasetCacheStats } from '../../../api/analyticsApi';
import { Modal } from '../../../ui/Modal/Modal';

interface CacheObservabilityPanelProps {
    open: boolean;
    onClose: () => void;
}

export function CacheObservabilityPanel({ open, onClose }: CacheObservabilityPanelProps) {
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [stats, setStats] = useState<DatasetCacheStats | null>(null);
    const [databases, setDatabases] = useState<DatabaseListItem[]>([]);
    const [selectedDbId, setSelectedDbId] = useState<number>(0);
    const [policy, setPolicy] = useState<DatasetCachePolicy | null>(null);
    const [error, setError] = useState<string | null>(null);

    const hitRateText = useMemo(() => {
        const hitRate = stats?.hit_rate;
        if (typeof hitRate !== 'number' || Number.isNaN(hitRate)) return '-';
        return `${(hitRate * 100).toFixed(2)}%`;
    }, [stats]);

    const reloadStats = async () => {
        try {
            const s = await analyticsApi.getDatasetCacheStats();
            setStats(s);
        } catch (e) {
            setError(e instanceof Error ? e.message : '读取缓存统计失败');
        }
    };

    const reloadPolicy = async (databaseId: number) => {
        if (!databaseId || databaseId <= 0) {
            setPolicy(null);
            return;
        }
        try {
            const p = await analyticsApi.getDatasetCachePolicy(databaseId);
            setPolicy(p);
        } catch (e) {
            setError(e instanceof Error ? e.message : '读取缓存策略失败');
            setPolicy(null);
        }
    };

    useEffect(() => {
        if (!open) return;

        let cancelled = false;
        const bootstrap = async () => {
            setLoading(true);
            setError(null);
            try {
                const [s, dbResp] = await Promise.all([
                    analyticsApi.getDatasetCacheStats(),
                    analyticsApi.listDatabases(),
                ]);
                if (cancelled) return;
                const dbs = dbResp?.data ?? [];
                setStats(s);
                setDatabases(dbs);
                if (dbs.length > 0) {
                    const firstId = Number(dbs[0].id || 0);
                    setSelectedDbId(firstId);
                    await reloadPolicy(firstId);
                }
            } catch (e) {
                if (!cancelled) {
                    setError(e instanceof Error ? e.message : '加载缓存观测数据失败');
                }
            } finally {
                if (!cancelled) setLoading(false);
            }
        };

        bootstrap();
        return () => {
            cancelled = true;
        };
    }, [open]);

    useEffect(() => {
        if (!open) return;
        reloadPolicy(selectedDbId);
    }, [selectedDbId, open]);

    return (
        <Modal isOpen={open} onClose={onClose} title="缓存命中率观测" size="xl">
            {loading && <div style={{ fontSize: 12, opacity: 0.8, marginBottom: 10 }}>加载中...</div>}
            {error && (
                <div style={{
                    border: '1px solid #ef4444',
                    background: 'rgba(239,68,68,0.08)',
                    color: '#ef4444',
                    borderRadius: 8,
                    padding: 10,
                    marginBottom: 12,
                    fontSize: 12,
                    lineHeight: 1.5,
                    whiteSpace: 'pre-wrap',
                }}>
                    {error}
                </div>
            )}

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, minmax(120px, 1fr))', gap: 8, marginBottom: 14 }}>
                <MetricCard title="缓存条目" value={String(stats?.size ?? '-')} />
                <MetricCard title="命中次数" value={String(stats?.hit_count ?? '-')} />
                <MetricCard title="未命中次数" value={String(stats?.miss_count ?? '-')} />
                <MetricCard title="命中率" value={hitRateText} />
                <MetricCard title="驱逐次数" value={String(stats?.eviction_count ?? '-')} />
            </div>

            <div style={{ display: 'flex', gap: 8, marginBottom: 14 }}>
                <button type="button" className="header-btn" onClick={reloadStats}>刷新统计</button>
            </div>

            <div style={{ borderTop: '1px solid var(--color-border)', paddingTop: 12 }}>
                <div style={{ fontWeight: 600, marginBottom: 8 }}>数据库缓存策略</div>

                <div style={{ display: 'grid', gridTemplateColumns: '220px 140px 200px 1fr', gap: 8, alignItems: 'center', marginBottom: 10 }}>
                    <select
                        className="property-input"
                        value={selectedDbId || 0}
                        onChange={(e) => setSelectedDbId(Number(e.target.value))}
                    >
                        <option value={0}>-- 选择数据库 --</option>
                        {databases.map((db) => (
                            <option key={db.id} value={db.id}>
                                #{db.id} {db.name || '(未命名)'}{db.engine ? ` (${db.engine})` : ''}
                            </option>
                        ))}
                    </select>

                    <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12 }}>
                        <input
                            type="checkbox"
                            checked={policy?.enabled ?? true}
                            onChange={(e) => setPolicy((prev) => ({ ...(prev || {}), enabled: e.target.checked }))}
                        />
                        启用缓存
                    </label>

                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <span style={{ fontSize: 12, opacity: 0.8 }}>TTL(秒)</span>
                        <input
                            type="number"
                            className="property-input"
                            min={1}
                            max={86400}
                            value={policy?.ttlSeconds ?? 300}
                            onChange={(e) => {
                                const n = Number(e.target.value);
                                setPolicy((prev) => ({ ...(prev || {}), ttlSeconds: Number.isFinite(n) ? n : 300 }));
                            }}
                        />
                    </div>

                    <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12 }}>
                        <input
                            type="checkbox"
                            checked={policy?.cacheNativeQueries ?? true}
                            onChange={(e) => setPolicy((prev) => ({ ...(prev || {}), cacheNativeQueries: e.target.checked }))}
                        />
                        缓存 Native Query
                    </label>
                </div>

                <div style={{ display: 'flex', gap: 8 }}>
                    <button
                        type="button"
                        className="header-btn"
                        disabled={saving || !selectedDbId}
                        onClick={async () => {
                            if (!selectedDbId) return;
                            setSaving(true);
                            setError(null);
                            try {
                                const next = await analyticsApi.setDatasetCachePolicy(selectedDbId, {
                                    enabled: policy?.enabled ?? true,
                                    ttlSeconds: policy?.ttlSeconds ?? 300,
                                    cacheNativeQueries: policy?.cacheNativeQueries ?? true,
                                });
                                setPolicy(next);
                            } catch (e) {
                                setError(e instanceof Error ? e.message : '保存缓存策略失败');
                            } finally {
                                setSaving(false);
                            }
                        }}
                    >
                        {saving ? '保存中...' : '保存策略'}
                    </button>
                    <button
                        type="button"
                        className="header-btn"
                        disabled={!selectedDbId}
                        onClick={() => reloadPolicy(selectedDbId)}
                    >
                        重新加载策略
                    </button>
                </div>
            </div>
        </Modal>
    );
}

function MetricCard({ title, value }: { title: string; value: string }) {
    return (
        <div style={{
            border: '1px solid var(--color-border)',
            borderRadius: 8,
            padding: 10,
            background: 'rgba(255,255,255,0.02)',
        }}>
            <div style={{ fontSize: 12, opacity: 0.75, marginBottom: 6 }}>{title}</div>
            <div style={{ fontSize: 18, fontWeight: 600 }}>{value}</div>
        </div>
    );
}
