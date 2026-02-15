import { useEffect, useState, type CSSProperties } from 'react';
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

    return (
        <Modal isOpen={open} onClose={onClose} title="操作审计链路" size="xl">
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
                        {rows.map((row) => (
                            <tr key={String(row.id)}>
                                <td style={tdStyle}>{String(row.createdAt ?? '-')}</td>
                                <td style={tdStyle}>{String(row.actorId ?? '-')}</td>
                                <td style={tdStyle}>{String(row.action ?? '-')}</td>
                                <td style={tdStyle}>{String(row.requestId ?? '-')}</td>
                            </tr>
                        ))}
                        {rows.length === 0 && !loading && (
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

