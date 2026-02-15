import { useEffect, useState } from 'react';
import { analyticsApi, type ScreenAclEntry } from '../../../api/analyticsApi';
import { Modal } from '../../../ui/Modal/Modal';

interface ScreenAclPanelProps {
    open: boolean;
    screenId?: string | number;
    onClose: () => void;
}

const SUBJECT_TYPES: ScreenAclEntry['subjectType'][] = ['USER', 'ROLE'];
const PERMS: ScreenAclEntry['perm'][] = ['READ', 'EDIT', 'PUBLISH', 'MANAGE'];

function normalizeEntry(row: Partial<ScreenAclEntry>): ScreenAclEntry {
    return {
        subjectType: row.subjectType === 'ROLE' ? 'ROLE' : 'USER',
        subjectId: String(row.subjectId || '').trim(),
        perm: (PERMS.includes(row.perm as ScreenAclEntry['perm']) ? row.perm : 'READ') as ScreenAclEntry['perm'],
        id: row.id,
        screenId: row.screenId,
        creatorId: row.creatorId,
        createdAt: row.createdAt,
        updatedAt: row.updatedAt,
    };
}

export function ScreenAclPanel({ open, screenId, onClose }: ScreenAclPanelProps) {
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [rows, setRows] = useState<ScreenAclEntry[]>([]);

    useEffect(() => {
        if (!open || !screenId) return;
        let cancelled = false;
        const load = async () => {
            setLoading(true);
            setError(null);
            try {
                const acl = await analyticsApi.getScreenAcl(screenId);
                if (cancelled) return;
                setRows((acl || []).map(normalizeEntry));
            } catch (e) {
                if (!cancelled) {
                    setError(e instanceof Error ? e.message : '加载权限失败');
                    setRows([]);
                }
            } finally {
                if (!cancelled) setLoading(false);
            }
        };
        load();
        return () => {
            cancelled = true;
        };
    }, [open, screenId]);

    const updateAt = (index: number, patch: Partial<ScreenAclEntry>) => {
        setRows((prev) => prev.map((row, i) => (i === index ? normalizeEntry({ ...row, ...patch }) : row)));
    };

    const removeAt = (index: number) => {
        setRows((prev) => prev.filter((_, i) => i !== index));
    };

    return (
        <Modal isOpen={open} onClose={onClose} title="权限管理" size="xl">
            {!screenId && <div style={{ fontSize: 12, opacity: 0.8 }}>请先保存大屏后再配置权限。</div>}
            {loading && <div style={{ fontSize: 12, opacity: 0.8, marginBottom: 8 }}>加载中...</div>}
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

            <div style={{ display: 'grid', gridTemplateColumns: '120px 1fr 160px 68px', gap: 8, marginBottom: 8, fontSize: 12 }}>
                <div>主体类型</div>
                <div>主体标识</div>
                <div>权限</div>
                <div />
            </div>

            {rows.map((row, idx) => (
                <div
                    key={`${row.subjectType}-${row.subjectId}-${row.perm}-${idx}`}
                    style={{ display: 'grid', gridTemplateColumns: '120px 1fr 160px 68px', gap: 8, marginBottom: 8 }}
                >
                    <select
                        className="property-input"
                        value={row.subjectType}
                        onChange={(e) => updateAt(idx, { subjectType: e.target.value as ScreenAclEntry['subjectType'] })}
                    >
                        {SUBJECT_TYPES.map((type) => (
                            <option key={type} value={type}>{type}</option>
                        ))}
                    </select>
                    <input
                        className="property-input"
                        value={row.subjectId}
                        placeholder={row.subjectType === 'ROLE' ? 'ROLE_ANALYST' : '10001'}
                        onChange={(e) => updateAt(idx, { subjectId: e.target.value })}
                    />
                    <select
                        className="property-input"
                        value={row.perm}
                        onChange={(e) => updateAt(idx, { perm: e.target.value as ScreenAclEntry['perm'] })}
                    >
                        {PERMS.map((perm) => (
                            <option key={perm} value={perm}>{perm}</option>
                        ))}
                    </select>
                    <button type="button" className="header-btn" onClick={() => removeAt(idx)}>-</button>
                </div>
            ))}

            <div style={{ display: 'flex', gap: 8 }}>
                <button
                    type="button"
                    className="header-btn"
                    onClick={() => setRows((prev) => [...prev, normalizeEntry({ subjectType: 'USER', subjectId: '', perm: 'READ' })])}
                    disabled={!screenId}
                >
                    + 添加
                </button>
                <button
                    type="button"
                    className="header-btn"
                    disabled={!screenId || saving}
                    onClick={async () => {
                        if (!screenId) return;
                        setSaving(true);
                        setError(null);
                        try {
                            const payload = rows
                                .map(normalizeEntry)
                                .filter((item) => item.subjectId.trim().length > 0);
                            const updated = await analyticsApi.updateScreenAcl(screenId, { entries: payload });
                            setRows((updated || []).map(normalizeEntry));
                        } catch (e) {
                            setError(e instanceof Error ? e.message : '保存权限失败');
                        } finally {
                            setSaving(false);
                        }
                    }}
                >
                    {saving ? '保存中...' : '保存权限'}
                </button>
            </div>
        </Modal>
    );
}
