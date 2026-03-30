import { useEffect, useState } from 'react';
import { analyticsApi, type ScreenAclEntry } from '../../../api/analyticsApi';
import { Modal } from 'antd';

interface ScreenAclPanelProps {
	open: boolean;
	screenId?: string | number;
	onClose: () => void;
	isOwner?: boolean;
}

const SUBJECT_TYPES: ScreenAclEntry['subjectType'][] = ['USER', 'ROLE'];
const ASSIGNABLE_PERMS: ScreenAclEntry['perm'][] = ['MANAGE', 'READ'];
const PERM_LABELS: Record<string, string> = {
	OWNER: '拥有者',
	MANAGE: '管理者',
	READ: '查看者',
};

function normalizeEntry(row: Partial<ScreenAclEntry>): ScreenAclEntry {
	const validPerms: ScreenAclEntry['perm'][] = ['READ', 'MANAGE', 'OWNER'];
	return {
		subjectType: row.subjectType === 'ROLE' ? 'ROLE' : 'USER',
		subjectId: String(row.subjectId || '').trim(),
		perm: (validPerms.includes(row.perm as ScreenAclEntry['perm']) ? row.perm : 'READ') as ScreenAclEntry['perm'],
		id: row.id,
		screenId: row.screenId,
		creatorId: row.creatorId,
		createdAt: row.createdAt,
		updatedAt: row.updatedAt,
	};
}

export function ScreenAclPanel({ open, screenId, onClose, isOwner = false }: ScreenAclPanelProps) {
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

	// Determine which perms the current user can assign
	const availablePerms: ScreenAclEntry['perm'][] = isOwner ? ASSIGNABLE_PERMS : ['READ'];

	return (
		<Modal open={open} onCancel={onClose} title="权限管理" width={960}>
			{!screenId && <div className="text-xs opacity-80">请先保存大屏后再配置权限。</div>}
			{loading && <div className="text-xs opacity-80 mb-2">加载中...</div>}
			{error && (
				<div className="border border-error bg-error/10 text-error rounded-lg p-2.5 mb-3 text-xs whitespace-pre-wrap">
					{error}
				</div>
			)}

			<div className="grid gap-2 mb-2 text-xs" style={{ gridTemplateColumns: '120px 1fr 160px 68px' }}>
				<div>主体类型</div>
				<div>主体标识</div>
				<div>权限</div>
				<div />
			</div>

			{rows.map((row, idx) => {
				// OWNER rows are read-only
				if (row.perm === 'OWNER') {
					return (
						<div
							key={`${row.subjectType}-${row.subjectId}-${row.perm}-${idx}`}
							className="grid gap-2 mb-2"
							style={{ gridTemplateColumns: '120px 1fr 160px 68px' }}
						>
							<span className="text-xs text-text-muted px-2.5 py-1.5">{row.subjectType}</span>
							<span className="text-xs text-text-primary px-2.5 py-1.5">{row.subjectId}</span>
							<span className="text-xs font-semibold text-brand px-2.5 py-1.5">{PERM_LABELS.OWNER}</span>
							<span />
						</div>
					);
				}

				return (
					<div
						key={`${row.subjectType}-${row.subjectId}-${row.perm}-${idx}`}
						className="grid gap-2 mb-2"
						style={{ gridTemplateColumns: '120px 1fr 160px 68px' }}
					>
						<select
							className="flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
							value={row.subjectType}
							onChange={(e) => updateAt(idx, { subjectType: e.target.value as ScreenAclEntry['subjectType'] })}
						>
							{SUBJECT_TYPES.map((type) => (
								<option key={type} value={type}>{type}</option>
							))}
						</select>
						<input
							className="flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
							value={row.subjectId}
							placeholder={row.subjectType === 'ROLE' ? 'ROLE_ANALYST' : '10001'}
							onChange={(e) => updateAt(idx, { subjectId: e.target.value })}
						/>
						<select
							className="flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
							value={row.perm}
							onChange={(e) => updateAt(idx, { perm: e.target.value as ScreenAclEntry['perm'] })}
						>
							{availablePerms.map((perm) => (
								<option key={perm} value={perm}>{PERM_LABELS[perm] || perm}</option>
							))}
						</select>
						<button
							type="button"
							className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-3.5 text-xs hover:border-brand/30 hover:bg-brand/10"
							onClick={() => removeAt(idx)}
						>
							-
						</button>
					</div>
				);
			})}

			<div className="flex gap-2">
				<button
					type="button"
					className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-3.5 text-xs hover:border-brand/30 hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
					onClick={() => setRows((prev) => [...prev, normalizeEntry({ subjectType: 'USER', subjectId: '', perm: 'READ' })])}
					disabled={!screenId}
				>
					+ 添加
				</button>
				<button
					type="button"
					className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-3.5 text-xs hover:border-brand/30 hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
					disabled={!screenId || saving}
					onClick={async () => {
						if (!screenId) return;
						setSaving(true);
						setError(null);
						try {
							const payload = rows
								.filter((item) => item.perm !== 'OWNER')
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
