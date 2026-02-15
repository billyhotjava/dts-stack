import { useEffect, useMemo, useState } from 'react';
import { analyticsApi, HttpError, type ScreenEditLock } from '../../../api/analyticsApi';
import { Modal } from '../../../ui/Modal/Modal';

interface ScreenEditLockPanelProps {
    open: boolean;
    screenId?: string | number;
    lock?: ScreenEditLock | null;
    onClose: () => void;
    onChange?: (next: ScreenEditLock | null) => void;
}

export function ScreenEditLockPanel({ open, screenId, lock, onClose, onChange }: ScreenEditLockPanelProps) {
    const [loading, setLoading] = useState(false);
    const [working, setWorking] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [localLock, setLocalLock] = useState<ScreenEditLock | null>(lock ?? null);

    useEffect(() => {
        setLocalLock(lock ?? null);
    }, [lock]);

    const statusText = useMemo(() => {
        if (!localLock?.active) return '未加锁';
        if (localLock.mine) return '当前会话持有';
        const owner = String(localLock.ownerName || localLock.ownerId || '其他用户');
        return `被 ${owner} 占用`;
    }, [localLock]);

    const publishLock = (next: ScreenEditLock | null) => {
        setLocalLock(next);
        onChange?.(next);
    };

    const refresh = async () => {
        if (!screenId) return;
        setLoading(true);
        setError(null);
        try {
            const next = await analyticsApi.getScreenEditLock(screenId);
            publishLock(next);
        } catch (e) {
            setError(e instanceof Error ? e.message : '读取编辑锁失败');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (!open || !screenId) return;
        refresh();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open, screenId]);

    const handleAcquire = async () => {
        if (!screenId || working) return;
        setWorking(true);
        setError(null);
        try {
            const next = await analyticsApi.acquireScreenEditLock(screenId, { ttlSeconds: 120 });
            publishLock(next);
        } catch (e) {
            if (e instanceof HttpError) {
                try {
                    const payload = JSON.parse(e.bodyText) as { lock?: ScreenEditLock; message?: string };
                    if (payload?.lock) {
                        publishLock(payload.lock);
                    }
                    setError(payload?.message || e.message);
                } catch {
                    setError(e.message);
                }
            } else {
                setError(e instanceof Error ? e.message : '申请编辑锁失败');
            }
        } finally {
            setWorking(false);
        }
    };

    const handleForceTakeover = async () => {
        if (!screenId || working) return;
        if (!window.confirm('确认强制接管该编辑锁吗？仅建议在对方离线或误占锁时使用。')) {
            return;
        }
        setWorking(true);
        setError(null);
        try {
            const next = await analyticsApi.acquireScreenEditLock(screenId, {
                ttlSeconds: 120,
                forceTakeover: true,
            });
            publishLock(next);
        } catch (e) {
            if (e instanceof HttpError) {
                try {
                    const payload = JSON.parse(e.bodyText) as { lock?: ScreenEditLock; message?: string };
                    if (payload?.lock) {
                        publishLock(payload.lock);
                    }
                    setError(payload?.message || e.message);
                } catch {
                    setError(e.message);
                }
            } else {
                setError(e instanceof Error ? e.message : '强制接管失败');
            }
        } finally {
            setWorking(false);
        }
    };

    const handleRelease = async () => {
        if (!screenId || working) return;
        setWorking(true);
        setError(null);
        try {
            const next = await analyticsApi.releaseScreenEditLock(screenId);
            publishLock(next);
        } catch (e) {
            setError(e instanceof Error ? e.message : '释放编辑锁失败');
        } finally {
            setWorking(false);
        }
    };

    return (
        <Modal isOpen={open} onClose={onClose} title="编辑锁" size="lg">
            <div style={{ fontSize: 12, opacity: 0.8, marginBottom: 10 }}>
                用于避免多人同时改同一大屏导致覆盖。持有者可保存/发布，其他用户会收到冲突提示。
            </div>

            <div style={{
                border: '1px solid var(--color-border)',
                borderRadius: 8,
                padding: 12,
                marginBottom: 12,
            }}>
                <div style={{ fontSize: 12, opacity: 0.75, marginBottom: 8 }}>当前状态</div>
                <div style={{ fontWeight: 600, marginBottom: 6 }}>{statusText}</div>
                <div style={{ fontSize: 12, opacity: 0.75 }}>Owner: {String(localLock?.ownerName || localLock?.ownerId || '-')}</div>
                <div style={{ fontSize: 12, opacity: 0.75 }}>过期时间: {String(localLock?.expireAt || '-')}</div>
                <div style={{ fontSize: 12, opacity: 0.75 }}>剩余TTL: {String(localLock?.ttlSeconds ?? 0)}s</div>
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

            <div style={{ display: 'flex', gap: 8 }}>
                <button type="button" className="header-btn" disabled={loading} onClick={refresh}>
                    {loading ? '刷新中...' : '刷新状态'}
                </button>
                <button type="button" className="header-btn" disabled={working || !!localLock?.mine} onClick={handleAcquire}>
                    {working ? '处理中...' : '申请编辑锁'}
                </button>
                <button
                    type="button"
                    className="header-btn"
                    disabled={working || !localLock?.active || !!localLock?.mine}
                    onClick={handleForceTakeover}
                    title="需要 MANAGE 权限"
                >
                    强制接管
                </button>
                <button type="button" className="header-btn" disabled={working || !localLock?.mine} onClick={handleRelease}>
                    释放编辑锁
                </button>
            </div>
        </Modal>
    );
}
