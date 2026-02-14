import { useEffect, useMemo, useState } from 'react';
import { analyticsApi, type Metric } from '../../../api/analyticsApi';

interface MetricBindingEditorProps {
    metricId?: number;
    metricVersion?: string;
    onMetricIdChange: (metricId?: number) => void;
    onMetricVersionChange: (metricVersion?: string) => void;
}

let cachedMetrics: Metric[] | null = null;
let metricsFetchPromise: Promise<Metric[]> | null = null;

function loadMetrics(): Promise<Metric[]> {
    if (cachedMetrics) {
        return Promise.resolve(cachedMetrics);
    }
    if (metricsFetchPromise) {
        return metricsFetchPromise;
    }

    metricsFetchPromise = analyticsApi.listMetrics()
        .then((items) => {
            cachedMetrics = (items ?? []).filter((item) => !item.archived);
            return cachedMetrics;
        })
        .catch(() => {
            metricsFetchPromise = null;
            return [] as Metric[];
        });

    return metricsFetchPromise;
}

function extractVersions(metric?: Metric): string[] {
    if (!metric) {
        return [];
    }

    const values = new Set<string>();

    const definition = metric.definition as Record<string, unknown> | null | undefined;
    const version = definition?.version;
    if (typeof version === 'string' && version.trim().length > 0) {
        values.add(version.trim());
    }

    const versions = definition?.versions;
    if (Array.isArray(versions)) {
        for (const item of versions) {
            if (typeof item === 'string' && item.trim().length > 0) {
                values.add(item.trim());
                continue;
            }
            if (item && typeof item === 'object') {
                const obj = item as Record<string, unknown>;
                const v = obj.version ?? obj.versionNo ?? obj.name;
                if (typeof v === 'string' && v.trim().length > 0) {
                    values.add(v.trim());
                }
            }
        }
    }

    return Array.from(values);
}

export function MetricBindingEditor({
    metricId,
    metricVersion,
    onMetricIdChange,
    onMetricVersionChange,
}: MetricBindingEditorProps) {
    const [metrics, setMetrics] = useState<Metric[]>(cachedMetrics ?? []);
    const [loading, setLoading] = useState(!cachedMetrics);
    const [traceVersions, setTraceVersions] = useState<string[]>([]);

    useEffect(() => {
        if (cachedMetrics) {
            setMetrics(cachedMetrics);
            setLoading(false);
            return;
        }

        let cancelled = false;
        setLoading(true);
        loadMetrics().then((items) => {
            if (!cancelled) {
                setMetrics(items);
                setLoading(false);
            }
        });

        return () => {
            cancelled = true;
        };
    }, []);

    useEffect(() => {
        if (!metricId || metricId <= 0) {
            setTraceVersions([]);
            return;
        }

        let cancelled = false;
        analyticsApi
            .listMetricVersions(metricId)
            .then((items) => {
                if (cancelled) {
                    return;
                }
                setTraceVersions(
                    (items ?? [])
                        .map((item) => (typeof item === 'string' ? item.trim() : ''))
                        .filter((item) => item.length > 0),
                );
            })
            .catch(() => {
                if (!cancelled) {
                    setTraceVersions([]);
                }
            });

        return () => {
            cancelled = true;
        };
    }, [metricId]);

    const selectedMetric = useMemo(
        () => metrics.find((item) => Number(item.id) === Number(metricId)),
        [metrics, metricId],
    );
    const versionOptions = useMemo(() => {
        const values = new Set<string>();
        for (const item of extractVersions(selectedMetric)) {
            values.add(item);
        }
        for (const item of traceVersions) {
            values.add(item);
        }
        return Array.from(values);
    }, [selectedMetric, traceVersions]);
    const datalistId = `metric-version-options-${metricId ?? 0}`;

    return (
        <>
            <div className="property-row">
                <label className="property-label">指标</label>
                <select
                    className="property-input"
                    value={metricId ?? 0}
                    onChange={(e) => {
                        const nextMetricId = Number(e.target.value);
                        if (nextMetricId > 0) {
                            onMetricIdChange(nextMetricId);
                        } else {
                            onMetricIdChange(undefined);
                            onMetricVersionChange(undefined);
                        }
                    }}
                    style={metricId && metricId > 0 ? undefined : { color: '#888' }}
                >
                    <option value={0}>
                        {loading ? '加载指标中...' : '-- 不绑定指标 --'}
                    </option>
                    {metrics.map((item) => (
                        <option key={item.id} value={Number(item.id)}>
                            #{item.id} {item.name || '(未命名指标)'}
                        </option>
                    ))}
                </select>
            </div>

            {metricId && metricId > 0 && (
                <>
                    <div className="property-row">
                        <label className="property-label">口径版本</label>
                        <input
                            type="text"
                            className="property-input"
                            list={datalistId}
                            value={metricVersion ?? ''}
                            placeholder={versionOptions.length > 0 ? '选择或输入版本' : '如: v2026.02'}
                            onChange={(e) => {
                                const nextVersion = e.target.value.trim();
                                onMetricVersionChange(nextVersion.length > 0 ? nextVersion : undefined);
                            }}
                        />
                        {versionOptions.length > 0 && (
                            <datalist id={datalistId}>
                                {versionOptions.map((item) => (
                                    <option key={item} value={item} />
                                ))}
                            </datalist>
                        )}
                    </div>
                    <div style={{ fontSize: 11, color: '#888', marginTop: -4 }}>
                        已绑定指标将进入查询追溯链路，用于口径一致性审计。
                    </div>
                </>
            )}
        </>
    );
}
