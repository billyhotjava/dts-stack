import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { analyticsApi, type ScreenAuditEntry } from '../../../api/analyticsApi';
import { Modal } from '../../../ui/Modal/Modal';

interface ScreenAuditPanelProps {
    open: boolean;
    screenId?: string | number;
    onClose: () => void;
}

export function ScreenAuditPanel({ open, screenId, onClose }: ScreenAuditPanelProps) {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [limit, setLimit] = useState(200);
    const [rows, setRows] = useState<ScreenAuditEntry[]>([]);
    const [actionFilter, setActionFilter] = useState<string>('all');
    const [keyword, setKeyword] = useState('');
    const [onlyExportActions, setOnlyExportActions] = useState(false);
    const [autoRefresh, setAutoRefresh] = useState(true);
    const [refreshSeconds, setRefreshSeconds] = useState(20);

    const loadRows = async (targetLimit: number) => {
        if (!screenId) return;
        setLoading(true);
        setError(null);
        try {
            const data = await analyticsApi.getScreenAuditLogs(screenId, targetLimit);
            setRows(Array.isArray(data) ? data : []);
        } catch (e) {
            setRows([]);
            setError(e instanceof Error ? e.message : '加载审计日志失败');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (!open || !screenId) return;
        loadRows(limit);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open, screenId]);

    useEffect(() => {
        if (!open || !screenId || !autoRefresh || loading) {
            return;
        }
        const seconds = Number.isFinite(refreshSeconds) ? Math.max(5, Math.min(120, Math.floor(refreshSeconds))) : 20;
        const timer = window.setInterval(() => {
            void loadRows(limit);
        }, seconds * 1000);
        return () => window.clearInterval(timer);
    }, [autoRefresh, limit, loading, open, refreshSeconds, screenId]);

    const actionOptions = useMemo(() => {
        const set = new Set<string>();
        for (const row of rows) {
            const action = String(row.action || '').trim();
            if (action) set.add(action);
        }
        return Array.from(set).sort((a, b) => a.localeCompare(b, 'zh-CN'));
    }, [rows]);

    const filteredRows = useMemo(() => {
        const kw = keyword.trim().toLowerCase();
        return rows.filter((row) => {
            const action = String(row.action || '').trim();
            if (onlyExportActions && !action.startsWith('screen.export')) {
                return false;
            }
            if (actionFilter !== 'all' && action !== actionFilter) {
                return false;
            }
            if (!kw) return true;
            const actor = String(row.actorId ?? '');
            const req = String(row.requestId ?? '');
            const created = String(row.createdAt ?? '');
            const haystack = `${action} ${actor} ${req} ${created}`.toLowerCase();
            return haystack.includes(kw);
        });
    }, [actionFilter, keyword, onlyExportActions, rows]);

    const exportActionCount = useMemo(
        () => rows.filter((row) => String(row.action || '').startsWith('screen.export')).length,
        [rows],
    );

    return (
        <Modal isOpen={open} onClose={onClose} title="操作审计链路" size="xl">
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(120px, 1fr))', gap: 8, marginBottom: 10 }}>
                <MetricCell title="审计总数" value={String(rows.length)} />
                <MetricCell title="筛选后" value={String(filteredRows.length)} />
                <MetricCell title="导出事件" value={String(exportActionCount)} />
            </div>
            <div style={{ display: 'flex', gap: 8, marginBottom: 10, alignItems: 'center' }}>
                <span style={{ fontSize: 12, opacity: 0.8 }}>最大行数</span>
                <input
                    type="number"
                    min={1}
                    max={1000}
                    className="property-input"
                    value={limit}
                    onChange={(e) => {
                        const n = Number(e.target.value);
                        setLimit(Number.isFinite(n) ? Math.max(1, Math.min(1000, n)) : 200);
                    }}
                    style={{ width: 140 }}
                />
                <button type="button" className="header-btn" disabled={!screenId || loading} onClick={() => loadRows(limit)}>
                    {loading ? '刷新中...' : '刷新'}
                </button>
                <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, opacity: 0.9 }}>
                    <input
                        type="checkbox"
                        checked={autoRefresh}
                        onChange={(e) => setAutoRefresh(e.target.checked)}
                    />
                    自动刷新
                </label>
                <input
                    type="number"
                    min={5}
                    max={120}
                    className="property-input"
                    value={refreshSeconds}
                    onChange={(e) => {
                        const n = Number(e.target.value);
                        setRefreshSeconds(Number.isFinite(n) ? Math.max(5, Math.min(120, n)) : 20);
                    }}
                    style={{ width: 100 }}
                    title="自动刷新间隔(秒)"
                    disabled={!autoRefresh}
                />
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '220px 1fr auto', gap: 8, alignItems: 'center', marginBottom: 10 }}>
                <select
                    className="property-input"
                    value={actionFilter}
                    onChange={(e) => setActionFilter(e.target.value)}
                >
                    <option value="all">全部动作</option>
                    {actionOptions.map((item) => (
                        <option key={item} value={item}>{item}</option>
                    ))}
                </select>
                <input
                    type="text"
                    className="property-input"
                    value={keyword}
                    onChange={(e) => setKeyword(e.target.value)}
                    placeholder="搜索 action / actor / requestId / 时间"
                />
                <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12 }}>
                    <input
                        type="checkbox"
                        checked={onlyExportActions}
                        onChange={(e) => setOnlyExportActions(e.target.checked)}
                    />
                    仅导出事件
                </label>
            </div>

            {error && (
                <div style={{
                    border: '1px solid #ef4444',
                    background: 'rgba(239,68,68,0.08)',
                    color: '#ef4444',
                    borderRadius: 8,
                    padding: 10,
                    marginBottom: 10,
                    fontSize: 12,
                    whiteSpace: 'pre-wrap',
                }}>
                    {error}
                </div>
            )}

            <div style={{
                border: '1px solid var(--color-border)',
                borderRadius: 8,
                maxHeight: 360,
                overflow: 'auto',
            }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
                    <thead>
                        <tr style={{ position: 'sticky', top: 0, background: 'var(--color-surface)' }}>
                            <th style={thStyle}>时间</th>
                            <th style={thStyle}>操作者</th>
                            <th style={thStyle}>动作</th>
                            <th style={thStyle}>RequestId</th>
                        </tr>
                    </thead>
                    <tbody>
                        {filteredRows.map((row) => (
                            <tr key={String(row.id)}>
                                <td style={tdStyle}>{String(row.createdAt ?? '-')}</td>
                                <td style={tdStyle}>{String(row.actorId ?? '-')}</td>
                                <td style={tdStyle}>{String(row.action ?? '-')}</td>
                                <td style={tdStyle}>{String(row.requestId ?? '-')}</td>
                            </tr>
                        ))}
                        {filteredRows.length === 0 && !loading && (
                            <tr>
                                <td style={tdStyle} colSpan={4}>暂无数据</td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>
        </Modal>
    );
}

const thStyle: CSSProperties = {
    textAlign: 'left',
    padding: '8px 10px',
    borderBottom: '1px solid var(--color-border)',
    fontWeight: 600,
};

const tdStyle: CSSProperties = {
    textAlign: 'left',
    padding: '8px 10px',
    borderBottom: '1px solid var(--color-border)',
    verticalAlign: 'top',
};

function MetricCell({ title, value }: { title: string; value: string }) {
    return (
        <div style={{
            border: '1px solid var(--color-border)',
            borderRadius: 8,
            padding: 10,
            background: 'rgba(255,255,255,0.02)',
        }}>
            <div style={{ fontSize: 12, opacity: 0.75, marginBottom: 6 }}>{title}</div>
            <div style={{ fontSize: 16, fontWeight: 600 }}>{value}</div>
        </div>
    );
}
