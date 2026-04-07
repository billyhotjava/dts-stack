import { useEffect, useMemo, useState } from 'react';
import { analyticsApi, HttpError, type ScreenEditLock } from '../../../api/analyticsApi';
import { Modal } from 'antd';

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
		<Modal open={open} onCancel={onClose} title="编辑锁" width={720}>
			<div className="text-xs opacity-80 mb-2.5">
				用于避免多人同时改同一大屏导致覆盖。持有者可保存/发布，其他用户会收到冲突提示。
			</div>

			<div className="border border-border-default rounded-lg p-3 mb-3">
				<div className="text-xs opacity-75 mb-2">当前状态</div>
				<div className="font-semibold mb-1.5">{statusText}</div>
				<div className="text-xs opacity-75">Owner: {String(localLock?.ownerName || localLock?.ownerId || '-')}</div>
				<div className="text-xs opacity-75">过期时间: {String(localLock?.expireAt || '-')}</div>
				<div className="text-xs opacity-75">剩余TTL: {String(localLock?.ttlSeconds ?? 0)}s</div>
			</div>

			{error && (
				<div className="border border-error bg-error/10 text-error rounded-lg p-2.5 mb-2.5 text-xs whitespace-pre-wrap">
					{error}
				</div>
			)}

			<div className="flex gap-2">
				<button
					type="button"
					className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-3.5 text-xs hover:border-brand/30 hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
					disabled={loading}
					onClick={refresh}
				>
					{loading ? '刷新中...' : '刷新状态'}
				</button>
				<button
					type="button"
					className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-3.5 text-xs hover:border-brand/30 hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
					disabled={working || !!localLock?.mine}
					onClick={handleAcquire}
				>
					{working ? '处理中...' : '申请编辑锁'}
				</button>
				<button
					type="button"
					className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-3.5 text-xs hover:border-brand/30 hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
					disabled={working || !localLock?.active || !!localLock?.mine}
					onClick={handleForceTakeover}
					title="需要 MANAGE 权限"
				>
					强制接管
				</button>
				<button
					type="button"
					className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-3.5 text-xs hover:border-brand/30 hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
					disabled={working || !localLock?.mine}
					onClick={handleRelease}
				>
					释放编辑锁
				</button>
			</div>
		</Modal>
	);
}
