import { useEffect, useMemo, useState } from 'react';
import { analyticsApi, type ScreenPublicLinkPolicy } from '../../../api/analyticsApi';
import { writeTextToClipboard } from '../../../hooks/clipboard';
import { Modal } from '../../../ui/Modal/Modal';

interface ScreenSharePolicyPanelProps {
    open: boolean;
    screenId?: string | number;
    onClose: () => void;
}

function toIsoLocalInput(value?: string | null): string {
    if (!value) return '';
    try {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return '';
        const pad = (n: number) => String(n).padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    } catch {
        return '';
    }
}

function fromIsoLocalInput(value: string): string | null {
    const text = value.trim();
    if (!text) return null;
    try {
        const date = new Date(text);
        if (Number.isNaN(date.getTime())) return null;
        return date.toISOString();
    } catch {
        return null;
    }
}

export function ScreenSharePolicyPanel({ open, screenId, onClose }: ScreenSharePolicyPanelProps) {
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [policy, setPolicy] = useState<ScreenPublicLinkPolicy | null>(null);
    const [expireAtInput, setExpireAtInput] = useState('');
    const [password, setPassword] = useState('');
    const [clearPassword, setClearPassword] = useState(false);
    const [ipAllowlist, setIpAllowlist] = useState('');
    const [disabled, setDisabled] = useState(false);

    const shareUrl = useMemo(() => {
        const uuid = policy?.uuid;
        if (!uuid) return '';
        return `${window.location.origin}/analytics/public/screen/${uuid}`;
    }, [policy?.uuid]);

    const loadPolicy = async () => {
        if (!screenId) return;
        setLoading(true);
        setError(null);
        try {
            const next = await analyticsApi.createScreenPublicLink(screenId, {});
            setPolicy(next || {});
            setExpireAtInput(toIsoLocalInput(next?.expireAt));
            setIpAllowlist(next?.ipAllowlist || '');
            setDisabled(Boolean(next?.disabled));
            setPassword('');
            setClearPassword(false);
        } catch (e) {
            setPolicy(null);
            setError(e instanceof Error ? e.message : '创建/加载分享链接失败');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (!open || !screenId) return;
        loadPolicy();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open, screenId]);

    return (
        <Modal isOpen={open} onClose={onClose} title="分享策略" size="lg">
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
                    whiteSpace: 'pre-wrap',
                }}>
                    {error}
                </div>
            )}

            <div style={{ display: 'grid', gap: 10 }}>
                <div className="property-row">
                    <label className="property-label">分享地址</label>
                    <input className="property-input" value={shareUrl} readOnly />
                </div>
                <div className="property-row">
                    <label className="property-label">过期时间</label>
                    <input
                        type="datetime-local"
                        className="property-input"
                        value={expireAtInput}
                        onChange={(e) => setExpireAtInput(e.target.value)}
                    />
                </div>
                <div className="property-row">
                    <label className="property-label">口令</label>
                    <input
                        type="password"
                        className="property-input"
                        placeholder={policy?.hasPassword ? '已设置口令；输入新值可覆盖' : '留空表示不设置'}
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                    />
                </div>
                <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12 }}>
                    <input
                        type="checkbox"
                        checked={clearPassword}
                        onChange={(e) => setClearPassword(e.target.checked)}
                    />
                    清空已有口令
                </label>
                <div className="property-row">
                    <label className="property-label">IP白名单</label>
                    <textarea
                        className="property-input"
                        rows={3}
                        placeholder="多个IP可用逗号或换行分隔"
                        value={ipAllowlist}
                        onChange={(e) => setIpAllowlist(e.target.value)}
                    />
                </div>
                <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12 }}>
                    <input
                        type="checkbox"
                        checked={disabled}
                        onChange={(e) => setDisabled(e.target.checked)}
                    />
                    禁用该分享链接
                </label>
            </div>

            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
                <button
                    type="button"
                    className="header-btn"
                    disabled={!screenId || saving}
                    onClick={async () => {
                        if (!screenId) return;
                        setSaving(true);
                        setError(null);
                        try {
                            const body: Record<string, unknown> = {
                                expireAt: fromIsoLocalInput(expireAtInput),
                                ipAllowlist: ipAllowlist.trim() || null,
                                disabled,
                            };
                            if (clearPassword) {
                                body.password = '';
                            } else if (password.trim().length > 0) {
                                body.password = password.trim();
                            }
                            const next = await analyticsApi.updateScreenPublicLinkPolicy(screenId, body);
                            setPolicy(next);
                            setPassword('');
                            setClearPassword(false);
                        } catch (e) {
                            setError(e instanceof Error ? e.message : '保存分享策略失败');
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
                    disabled={!shareUrl}
                    onClick={async () => {
                        if (!shareUrl) return;
                        const copied = await writeTextToClipboard(shareUrl);
                        alert(copied ? '分享链接已复制到剪贴板' : `复制失败，请手工复制：\n${shareUrl}`);
                    }}
                >
                    复制链接
                </button>
                <button
                    type="button"
                    className="header-btn"
                    disabled={!screenId}
                    onClick={async () => {
                        if (!screenId) return;
                        if (!window.confirm('确认删除该分享链接吗？')) return;
                        setError(null);
                        try {
                            await analyticsApi.deleteScreenPublicLink(screenId);
                            setPolicy(null);
                        } catch (e) {
                            setError(e instanceof Error ? e.message : '删除分享链接失败');
                        }
                    }}
                >
                    删除链接
                </button>
            </div>
        </Modal>
    );
}
