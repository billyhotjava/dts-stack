import { useEffect, useMemo, useState } from 'react';
import { analyticsApi, type ScreenHealthReport, type ScreenHealthStats } from '../../../api/analyticsApi';
import { Modal } from '../../../ui/Modal/Modal';

interface ScreenHealthPanelProps {
    open: boolean;
    screenId?: string | number;
    onClose: () => void;
}

type BrowserCheck = {
    name: string;
    version?: number;
    status: 'pass' | 'warn' | 'fail' | 'unknown';
    message: string;
};

function detectBrowserCheck(): BrowserCheck {
    if (typeof navigator === 'undefined') {
        return { name: 'Unknown', status: 'unknown', message: '无法识别浏览器环境' };
    }
    const ua = navigator.userAgent || '';
    const edge = /Edg\/(\d+)/.exec(ua);
    const chrome = /Chrome\/(\d+)/.exec(ua);

    if (edge) {
        const version = Number(edge[1]);
        if (Number.isFinite(version) && version >= 95) {
            return { name: 'Edge', version, status: version >= 109 ? 'pass' : 'warn', message: version >= 109 ? '兼容性良好' : '满足最低基线，建议升级到 109+' };
        }
        return { name: 'Edge', version, status: 'fail', message: '低于 Chrome 95 兼容基线' };
    }

    if (chrome) {
        const version = Number(chrome[1]);
        if (!Number.isFinite(version)) {
            return { name: 'Chrome', status: 'unknown', message: '无法识别 Chrome 版本' };
        }
        if (version >= 109) {
            return { name: 'Chrome', version, status: 'pass', message: '兼容性良好' };
        }
        if (version >= 95) {
            return { name: 'Chrome', version, status: 'warn', message: '满足最低基线，建议升级到 109+' };
        }
        return { name: 'Chrome', version, status: 'fail', message: '低于 Chrome 95 兼容基线' };
    }

    return { name: 'Other', status: 'warn', message: '非 Chrome/Edge 浏览器，建议回归验证核心功能' };
}

function StatusPill({ status }: { status: BrowserCheck['status'] }) {
    const color = status === 'pass'
        ? '#22c55e'
        : status === 'warn'
            ? '#f59e0b'
            : status === 'fail'
                ? '#ef4444'
                : '#94a3b8';
    return (
        <span style={{
            display: 'inline-block',
            padding: '2px 8px',
            borderRadius: 999,
            fontSize: 11,
            fontWeight: 600,
            background: `${color}20`,
            color,
            border: `1px solid ${color}66`,
        }}>
            {status.toUpperCase()}
        </span>
    );
}

function MetricRow({ label, value }: { label: string; value: string | number }) {
    return (
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, padding: '3px 0' }}>
            <span style={{ opacity: 0.8 }}>{label}</span>
            <span style={{ fontWeight: 600 }}>{value}</span>
        </div>
    );
}

function StatsCard({ title, stats }: { title: string; stats?: ScreenHealthStats }) {
    if (!stats) {
        return (
            <div style={{ border: '1px solid var(--color-border)', borderRadius: 10, padding: 12 }}>
                <div style={{ fontWeight: 600, marginBottom: 6 }}>{title}</div>
                <div style={{ fontSize: 12, opacity: 0.75 }}>暂无数据</div>
            </div>
        );
    }
    return (
        <div style={{ border: '1px solid var(--color-border)', borderRadius: 10, padding: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                <div style={{ fontWeight: 600 }}>{title}</div>
                <StatusPill status={stats.pass ? 'pass' : 'warn'} />
            </div>
            <MetricRow label="组件数" value={stats.componentCount ?? 0} />
            <MetricRow label="数据绑定组件" value={stats.dataBoundComponentCount ?? 0} />
            <MetricRow label="可刷新组件" value={stats.refreshableComponentCount ?? 0} />
            <MetricRow label="交互组件" value={stats.interactiveComponentCount ?? 0} />
            <MetricRow label="重组件" value={stats.heavyComponentCount ?? 0} />
            <MetricRow label="可预热数据库源" value={stats.warmupEligibleDatabaseSources ?? 0} />
            <MetricRow label="类型数" value={stats.uniqueComponentTypes ?? 0} />
            <MetricRow label="复杂度分" value={stats.estimatedComplexity ?? 0} />
            <div style={{ marginTop: 8, fontSize: 12, opacity: 0.9, lineHeight: 1.6 }}>
                {(stats.recommendations || []).map((item, idx) => (
                    <div key={`${item}-${idx}`}>- {item}</div>
                ))}
            </div>
        </div>
    );
}

export function ScreenHealthPanel({ open, screenId, onClose }: ScreenHealthPanelProps) {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [report, setReport] = useState<ScreenHealthReport | null>(null);
    const [benchmarkRunning, setBenchmarkRunning] = useState(false);
    const [benchmark, setBenchmark] = useState<{
        frameAvgMs: number;
        frameP95Ms: number;
        sampleCount: number;
        pass: boolean;
    } | null>(null);
    const browserCheck = useMemo(() => detectBrowserCheck(), []);

    const reload = async () => {
        if (!screenId) return;
        setLoading(true);
        setError(null);
        try {
            const data = await analyticsApi.getScreenHealth(screenId);
            setReport(data);
        } catch (e) {
            setReport(null);
            setError(e instanceof Error ? e.message : '加载体检报告失败');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (!open || !screenId) return;
        reload();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open, screenId]);

    const runClientBenchmark = async () => {
        if (benchmarkRunning) return;
        setBenchmarkRunning(true);
        try {
            const frameDurations: number[] = [];
            let last = performance.now();
            await new Promise<void>((resolve) => {
                const loop = (count: number) => {
                    requestAnimationFrame((now) => {
                        frameDurations.push(now - last);
                        last = now;
                        if (count >= 60) {
                            resolve();
                            return;
                        }
                        loop(count + 1);
                    });
                };
                loop(1);
            });
            const sorted = [...frameDurations].sort((a, b) => a - b);
            const p95Index = Math.min(sorted.length - 1, Math.floor(sorted.length * 0.95));
            const frameP95Ms = sorted[p95Index] || 0;
            const frameAvgMs = frameDurations.length > 0
                ? frameDurations.reduce((sum, item) => sum + item, 0) / frameDurations.length
                : 0;
            const pass = frameP95Ms <= 50;
            setBenchmark({
                frameAvgMs: Number(frameAvgMs.toFixed(2)),
                frameP95Ms: Number(frameP95Ms.toFixed(2)),
                sampleCount: frameDurations.length,
                pass,
            });
        } finally {
            setBenchmarkRunning(false);
        }
    };

    return (
        <Modal isOpen={open} onClose={onClose} title="兼容与性能体检" size="xl">
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10, alignItems: 'center' }}>
                <div style={{ fontSize: 12, opacity: 0.8 }}>
                    requestId: {report?.requestId || '-'} | baseline: {report?.baselineTargetComponents || 100} 组件
                </div>
                <button type="button" className="header-btn" onClick={reload} disabled={loading || !screenId}>
                    {loading ? '刷新中...' : '刷新体检'}
                </button>
            </div>

            <div style={{ border: '1px solid var(--color-border)', borderRadius: 10, padding: 12, marginBottom: 12 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                    <div style={{ fontWeight: 600 }}>浏览器兼容基线（Chrome 95+）</div>
                    <StatusPill status={browserCheck.status} />
                </div>
                <div style={{ fontSize: 12, opacity: 0.85 }}>
                    当前: {browserCheck.name}{browserCheck.version ? ` ${browserCheck.version}` : ''}，{browserCheck.message}
                </div>
            </div>

            {error && (
                <div style={{
                    border: '1px solid #ef4444',
                    background: 'rgba(239,68,68,0.08)',
                    color: '#ef4444',
                    borderRadius: 8,
                    padding: 10,
                    marginBottom: 12,
                    fontSize: 12,
                    whiteSpace: 'pre-wrap',
                }}>
                    {error}
                </div>
            )}

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                <StatsCard title="草稿态" stats={report?.draft} />
                <StatsCard title="发布态" stats={report?.published} />
            </div>

            <div style={{ border: '1px solid var(--color-border)', borderRadius: 10, padding: 12, marginTop: 12 }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                    <div style={{ fontWeight: 600 }}>本地浏览器帧稳定性</div>
                    <button type="button" className="header-btn" disabled={benchmarkRunning} onClick={runClientBenchmark}>
                        {benchmarkRunning ? '测量中...' : '运行测量'}
                    </button>
                </div>
                {benchmark ? (
                    <div style={{ fontSize: 12, lineHeight: 1.8 }}>
                        <div>样本数: <b>{benchmark.sampleCount}</b></div>
                        <div>平均帧时延: <b>{benchmark.frameAvgMs} ms</b></div>
                        <div>帧时延 P95: <b>{benchmark.frameP95Ms} ms</b></div>
                        <div>判定: <StatusPill status={benchmark.pass ? 'pass' : 'warn'} /></div>
                    </div>
                ) : (
                    <div style={{ fontSize: 12, opacity: 0.75 }}>点击“运行测量”获取当前浏览器的真实帧稳定性样本，不再构造模拟组件数据。</div>
                )}
            </div>
        </Modal>
    );
}
