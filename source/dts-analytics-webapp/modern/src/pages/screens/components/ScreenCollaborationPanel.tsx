import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { analyticsApi, type ScreenComment } from '../../../api/analyticsApi';
import type { ScreenComponent } from '../types';
import { Modal } from '../../../ui/Modal/Modal';

interface ScreenCollaborationPanelProps {
    open: boolean;
    screenId?: string | number;
    components: ScreenComponent[];
    selectedIds?: string[];
    onClose: () => void;
}

export function ScreenCollaborationPanel({
    open,
    screenId,
    components,
    selectedIds,
    onClose,
}: ScreenCollaborationPanelProps) {
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [limit, setLimit] = useState(200);
    const [rows, setRows] = useState<ScreenComment[]>([]);
    const [message, setMessage] = useState('');
    const [componentId, setComponentId] = useState<string>('');
    const [baselineUpdatedAt, setBaselineUpdatedAt] = useState<string>('');
    const [latestUpdatedAt, setLatestUpdatedAt] = useState<string>('');
    const [driftWarning, setDriftWarning] = useState(false);
    const baselineUpdatedAtRef = useRef<string>('');

    const componentOptions = useMemo(() => {
        return components.map((item) => ({
            id: item.id,
            label: `${item.name || item.type || item.id} (${item.id})`,
        }));
    }, [components]);

    const componentLabelMap = useMemo(() => {
        const out = new Map<string, string>();
        for (const item of components) {
            out.set(item.id, item.name || item.type || item.id);
        }
        return out;
    }, [components]);

    const openCount = useMemo(
        () => rows.filter((item) => (item.status || 'open') !== 'resolved').length,
        [rows],
    );

    const refreshDriftHint = async (resetBaseline = false) => {
        if (!screenId) return;
        try {
            const detail = await analyticsApi.getScreen(screenId, { mode: 'draft', fallbackDraft: true });
            const nextUpdatedAt = String(detail.updatedAt || '');
            if (resetBaseline || baselineUpdatedAtRef.current.length === 0) {
                baselineUpdatedAtRef.current = nextUpdatedAt;
                setBaselineUpdatedAt(nextUpdatedAt);
                setLatestUpdatedAt(nextUpdatedAt);
                setDriftWarning(false);
                return;
            }
            setLatestUpdatedAt(nextUpdatedAt);
            setDriftWarning(
                nextUpdatedAt.length > 0
                && baselineUpdatedAtRef.current.length > 0
                && nextUpdatedAt !== baselineUpdatedAtRef.current,
            );
        } catch {
            // Keep comments workflow available even if drift hint API fails.
        }
    };

    const loadRows = async (targetLimit: number) => {
        if (!screenId) return;
        setLoading(true);
        setError(null);
        try {
            const data = await analyticsApi.listScreenComments(screenId, targetLimit);
            setRows(Array.isArray(data) ? data : []);
        } catch (e) {
            setRows([]);
            setError(e instanceof Error ? e.message : '加载评论失败');
        } finally {
            setLoading(false);
        }
        await refreshDriftHint(false);
    };

    useEffect(() => {
        if (!open || !screenId) return;
        baselineUpdatedAtRef.current = '';
        setBaselineUpdatedAt('');
        setLatestUpdatedAt('');
        setDriftWarning(false);
        refreshDriftHint(true);
        loadRows(limit);
        const selectedId = Array.isArray(selectedIds) && selectedIds.length > 0 ? selectedIds[0] : '';
        setComponentId(selectedId || '');
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open, screenId]);

    return (
        <Modal isOpen={open} onClose={onClose} title="协作批注中心" size="xl">
            <div style={{ fontSize: 12, opacity: 0.8, marginBottom: 10 }}>
                轻协作模式：支持对大屏或指定组件添加评论，按状态跟踪“待处理/已解决”。
            </div>
            {driftWarning && (
                <div style={{
                    border: '1px solid #f59e0b',
                    background: 'rgba(245,158,11,0.12)',
                    color: '#f59e0b',
                    borderRadius: 8,
                    padding: 10,
                    marginBottom: 10,
                    fontSize: 12,
                    lineHeight: 1.5,
                }}>
                    检测到草稿版本已变化，可能存在多人并行修改。<br />
                    基线时间: {baselineUpdatedAt || '-'}<br />
                    当前时间: {latestUpdatedAt || '-'}
                </div>
            )}

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(120px, 1fr))', gap: 8, marginBottom: 12 }}>
                <MetricCell title="评论总数" value={String(rows.length)} />
                <MetricCell title="待处理" value={String(openCount)} />
                <MetricCell title="已解决" value={String(rows.length - openCount)} />
            </div>

            <div style={{ border: '1px solid var(--color-border)', borderRadius: 8, padding: 10, marginBottom: 12 }}>
                <div style={{ fontWeight: 600, marginBottom: 8 }}>新增评论</div>
                <div style={{ display: 'grid', gridTemplateColumns: '240px 1fr auto', gap: 8, alignItems: 'start' }}>
                    <select
                        className="property-input"
                        value={componentId}
                        onChange={(e) => setComponentId(e.target.value)}
                    >
                        <option value="">-- 整个大屏 --</option>
                        {componentOptions.map((item) => (
                            <option key={item.id} value={item.id}>{item.label}</option>
                        ))}
                    </select>

                    <textarea
                        className="property-input"
                        value={message}
                        onChange={(e) => setMessage(e.target.value)}
                        placeholder="输入批注内容，例如：该组件口径需与生产日报一致，建议补充单位和更新时间。"
                        rows={3}
                        style={{ resize: 'vertical', minHeight: 72 }}
                    />

                    <button
                        type="button"
                        className="header-btn"
                        disabled={saving || !screenId || message.trim().length === 0}
                        onClick={async () => {
                            if (!screenId) return;
                            const text = message.trim();
                            if (!text) return;
                            setSaving(true);
                            setError(null);
                            try {
                                const created = await analyticsApi.createScreenComment(screenId, {
                                    message: text,
                                    componentId: componentId || null,
                                });
                                setRows((prev) => [created, ...prev]);
                                setMessage('');
                                await refreshDriftHint(false);
                            } catch (e) {
                                setError(e instanceof Error ? e.message : '创建评论失败');
                            } finally {
                                setSaving(false);
                            }
                        }}
                    >
                        {saving ? '提交中...' : '添加评论'}
                    </button>
                </div>
            </div>

            <div style={{ display: 'flex', gap: 8, marginBottom: 10, alignItems: 'center' }}>
                <span style={{ fontSize: 12, opacity: 0.8 }}>最大行数</span>
                <input
                    type="number"
                    min={1}
                    max={500}
                    className="property-input"
                    value={limit}
                    onChange={(e) => {
                        const n = Number(e.target.value);
                        setLimit(Number.isFinite(n) ? Math.max(1, Math.min(500, n)) : 200);
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
                            <th style={thStyle}>状态</th>
                            <th style={thStyle}>评论</th>
                            <th style={thStyle}>组件</th>
                            <th style={thStyle}>创建时间</th>
                            <th style={thStyle}>创建人</th>
                            <th style={thStyle}>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        {rows.map((row) => {
                            const resolved = (row.status || 'open') === 'resolved';
                            const targetId = row.componentId || '';
                            const targetName = targetId ? (componentLabelMap.get(targetId) || targetId) : '全屏';
                            return (
                                <tr key={String(row.id)}>
                                    <td style={tdStyle}>
                                        <StatusBadge resolved={resolved} />
                                    </td>
                                    <td style={tdStyle}>{String(row.message || '-')}</td>
                                    <td style={tdStyle}>{targetName}</td>
                                    <td style={tdStyle}>{String(row.createdAt || '-')}</td>
                                    <td style={tdStyle}>{String(row.createdBy ?? '-')}</td>
                                    <td style={tdStyle}>
                                        {resolved ? (
                                            <button
                                                type="button"
                                                className="header-btn"
                                                onClick={async () => {
                                                    if (!screenId) return;
                                                    setError(null);
                                                    try {
                                                        const updated = await analyticsApi.reopenScreenComment(screenId, row.id);
                                                        setRows((prev) => prev.map((item) => item.id === row.id ? updated : item));
                                                        await refreshDriftHint(false);
                                                    } catch (e) {
                                                        setError(e instanceof Error ? e.message : '重新打开失败');
                                                    }
                                                }}
                                            >
                                                重新打开
                                            </button>
                                        ) : (
                                            <button
                                                type="button"
                                                className="header-btn"
                                                onClick={async () => {
                                                    if (!screenId) return;
                                                    const note = (window.prompt('处理备注（可选）', '') || '').trim();
                                                    setError(null);
                                                    try {
                                                        const updated = await analyticsApi.resolveScreenComment(screenId, row.id, { note });
                                                        setRows((prev) => prev.map((item) => item.id === row.id ? updated : item));
                                                        await refreshDriftHint(false);
                                                    } catch (e) {
                                                        setError(e instanceof Error ? e.message : '标记已解决失败');
                                                    }
                                                }}
                                            >
                                                标记已解决
                                            </button>
                                        )}
                                    </td>
                                </tr>
                            );
                        })}
                        {rows.length === 0 && !loading && (
                            <tr>
                                <td style={tdStyle} colSpan={6}>暂无评论</td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>
        </Modal>
    );
}

function StatusBadge({ resolved }: { resolved: boolean }) {
    return (
        <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            minWidth: 56,
            padding: '2px 8px',
            borderRadius: 999,
            border: `1px solid ${resolved ? '#16a34a' : '#f59e0b'}`,
            background: resolved ? 'rgba(22,163,74,0.1)' : 'rgba(245,158,11,0.12)',
            color: resolved ? '#16a34a' : '#f59e0b',
            fontSize: 11,
        }}>
            {resolved ? '已解决' : '待处理'}
        </span>
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
