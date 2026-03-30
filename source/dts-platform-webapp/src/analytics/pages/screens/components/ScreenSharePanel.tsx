import { useEffect, useState, useCallback, useRef } from 'react';
import { analyticsApi, type ScreenAclEntry, type UserSearchItem } from '../../../api/analyticsApi';
import { writeTextToClipboard } from '../../../hooks/clipboard';
import { resolveRouteHref } from '../../../helpers/resolveAnalyticsUrl';
import { Modal, Input, Select, Button, Tag, message } from 'antd';

interface ScreenSharePanelProps {
	open: boolean;
	screenId?: string | number;
	onClose: () => void;
	isOwner?: boolean;
}

const PERM_LABELS: Record<string, string> = {
	OWNER: '拥有者',
	MANAGE: '管理者',
	READ: '查看者',
};

const PERM_COLORS: Record<string, string> = {
	OWNER: 'gold',
	MANAGE: 'blue',
	READ: 'green',
};

export function ScreenSharePanel({ open, screenId, onClose, isOwner = false }: ScreenSharePanelProps) {
	const [loading, setLoading] = useState(false);
	const [saving, setSaving] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [entries, setEntries] = useState<ScreenAclEntry[]>([]);
	const [userNameMap, setUserNameMap] = useState<Record<string, string>>({});

	// User search state
	const [searchQuery, setSearchQuery] = useState('');
	const [searchResults, setSearchResults] = useState<UserSearchItem[]>([]);
	const [searching, setSearching] = useState(false);
	const [selectedUserIds, setSelectedUserIds] = useState<Set<string>>(new Set());
	const [addPerm, setAddPerm] = useState<'READ' | 'MANAGE'>('READ');
	const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

	// Share link state
	const [shareUrl, setShareUrl] = useState<string | null>(null);
	const [linkLoading, setLinkLoading] = useState(false);

	// Resolve user IDs to display names
	const resolveUserNames = useCallback(async (aclEntries: ScreenAclEntry[]) => {
		const userIds = [
			...new Set(
				aclEntries
					.filter((e) => e.subjectType === 'USER')
					.map((e) => String(e.subjectId))
					.filter((id) => id.trim().length > 0),
			),
		];
		if (userIds.length === 0) return;

		const nameMap: Record<string, string> = {};
		await Promise.all(
			userIds.map(async (id) => {
				try {
					const user = await analyticsApi.getUser(id);
					const name =
						user.common_name ||
						[user.first_name, user.last_name].filter(Boolean).join(' ').trim() ||
						user.email;
					if (name) nameMap[id] = name;
				} catch {
					// Resolution failed — will fall back to "用户 {id}"
				}
			}),
		);
		setUserNameMap((prev) => ({ ...prev, ...nameMap }));
	}, []);

	// Load ACL entries
	const loadAcl = useCallback(async () => {
		if (!screenId) return;
		setLoading(true);
		setError(null);
		try {
			const acl = await analyticsApi.getScreenAcl(screenId);
			const normalized = (acl || []).map(normalizeEntry);
			setEntries(normalized);
			resolveUserNames(normalized);
		} catch (e) {
			setError(e instanceof Error ? e.message : '加载权限失败');
			setEntries([]);
		} finally {
			setLoading(false);
		}
	}, [screenId, resolveUserNames]);

	useEffect(() => {
		if (!open || !screenId) return;
		loadAcl();
		setSearchQuery('');
		setSearchResults([]);
		setSelectedUserIds(new Set());
		setShareUrl(null);
	}, [open, screenId, loadAcl]);

	// Debounced user search
	useEffect(() => {
		if (searchTimerRef.current) {
			clearTimeout(searchTimerRef.current);
		}
		if (!searchQuery.trim()) {
			setSearchResults([]);
			return;
		}
		searchTimerRef.current = setTimeout(async () => {
			setSearching(true);
			try {
				const results = await analyticsApi.searchUsers(searchQuery.trim());
				setSearchResults(results || []);
			} catch {
				setSearchResults([]);
			} finally {
				setSearching(false);
			}
		}, 300);
		return () => {
			if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
		};
	}, [searchQuery]);

	// IDs already in the ACL
	const existingUserIds = new Set(
		entries
			.filter((e) => e.subjectType === 'USER')
			.map((e) => String(e.subjectId)),
	);

	// Filter search results to exclude users already in ACL
	const filteredResults = searchResults.filter(
		(u) => !existingUserIds.has(String(u.id)),
	);

	const toggleUser = (userId: string) => {
		setSelectedUserIds((prev) => {
			const next = new Set(prev);
			if (next.has(userId)) {
				next.delete(userId);
			} else {
				next.add(userId);
			}
			return next;
		});
	};

	const reloadEntries = async () => {
		if (!screenId) return;
		try {
			const acl = await analyticsApi.getScreenAcl(screenId);
			setEntries((acl || []).map(normalizeEntry));
		} catch { /* ignore reload failure */ }
	};

	const handleAddSelected = async () => {
		if (!screenId || selectedUserIds.size === 0) return;
		setSaving(true);
		setError(null);
		try {
			const backendPerm = addPerm === 'MANAGE' ? 'EDIT' : 'READ';
			for (const uid of selectedUserIds) {
				await analyticsApi.addScreenGrant(screenId, {
					granteeType: 'USER',
					granteeId: uid,
					permission: backendPerm,
				});
			}
			await reloadEntries();
			setSelectedUserIds(new Set());
			setSearchQuery('');
			setSearchResults([]);
			message.success(`已添加 ${selectedUserIds.size} 位用户`);
		} catch (e) {
			setError(e instanceof Error ? e.message : '添加用户失败');
		} finally {
			setSaving(false);
		}
	};

	const handleRemoveEntry = async (index: number) => {
		if (!screenId) return;
		const target = entries[index];
		if (!target || target.perm === 'OWNER' || target.id == null) return;

		setSaving(true);
		setError(null);
		try {
			await analyticsApi.revokeScreenGrant(screenId, target.id);
			await reloadEntries();
		} catch (e) {
			setError(e instanceof Error ? e.message : '移除用户失败');
		} finally {
			setSaving(false);
		}
	};

	const handleCopyLink = async () => {
		if (!screenId) return;
		setLinkLoading(true);
		try {
			const { uuid } = await analyticsApi.createScreenPublicLink(screenId, {});
			if (!uuid) {
				message.error('未获取到分享链接，请先发布后重试');
				return;
			}
			const url = resolveRouteHref(`/bi/public/screen/${uuid}`);
			setShareUrl(url);
			const copied = await writeTextToClipboard(url);
			if (copied) {
				message.success('分享链接已复制到剪贴板');
			} else {
				message.info('复制失败，请手工复制链接');
			}
		} catch {
			message.error('创建分享链接失败，请先发布版本');
		} finally {
			setLinkLoading(false);
		}
	};

	const ownerEntries = entries.filter((e) => e.perm === 'OWNER');
	const nonOwnerEntries = entries.filter((e) => e.perm !== 'OWNER');

	const availablePerms: Array<{ value: 'READ' | 'MANAGE'; label: string }> = isOwner
		? [
			{ value: 'READ', label: '查看者' },
			{ value: 'MANAGE', label: '管理者' },
		]
		: [{ value: 'READ', label: '查看者' }];

	return (
		<Modal
			open={open}
			onCancel={onClose}
			title="分享大屏"
			width={640}
			footer={null}
			destroyOnClose
		>
			{!screenId && (
				<div style={{ fontSize: 12, opacity: 0.8, marginBottom: 12 }}>
					请先保存大屏后再配置分享。
				</div>
			)}
			{error && (
				<div style={{
					border: '1px solid var(--color-error, #ef4444)',
					background: 'rgba(239,68,68,0.08)',
					color: 'var(--color-error, #ef4444)',
					borderRadius: 8,
					padding: 10,
					marginBottom: 12,
					fontSize: 12,
					whiteSpace: 'pre-wrap',
				}}>
					{error}
				</div>
			)}

			{/* Current sharing status */}
			<div style={{ marginBottom: 16 }}>
				<div style={{
					fontSize: 13,
					fontWeight: 600,
					marginBottom: 8,
					color: 'var(--color-text-primary, #e5e7eb)',
				}}>
					当前共享
				</div>
				{loading ? (
					<div style={{ fontSize: 12, opacity: 0.6 }}>加载中...</div>
				) : entries.length === 0 ? (
					<div style={{ fontSize: 12, opacity: 0.6 }}>暂无共享用户</div>
				) : (
					<div style={{
						border: '1px solid var(--color-border, rgba(255,255,255,0.1))',
						borderRadius: 8,
						overflow: 'hidden',
					}}>
						{ownerEntries.map((entry, idx) => (
							<div
								key={`owner-${entry.subjectId}-${idx}`}
								style={{
									display: 'flex',
									alignItems: 'center',
									justifyContent: 'space-between',
									padding: '8px 12px',
									borderBottom: '1px solid var(--color-border, rgba(255,255,255,0.06))',
									background: 'var(--color-surface-raised, rgba(255,255,255,0.03))',
								}}
							>
								<div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
									<span style={{
										display: 'inline-flex',
										alignItems: 'center',
										justifyContent: 'center',
										width: 24,
										height: 24,
										borderRadius: '50%',
										background: 'var(--color-primary, #3b82f6)',
										color: '#fff',
										fontSize: 11,
										fontWeight: 600,
									}}>
										{(userNameMap[String(entry.subjectId)] || entry.subjectId || '?').charAt(0).toUpperCase()}
									</span>
									<span style={{ fontSize: 13, color: 'var(--color-text-primary, #e5e7eb)' }}>
										{entry.subjectType === 'USER'
											? (userNameMap[String(entry.subjectId)] || `用户 ${entry.subjectId}`)
											: `角色 ${entry.subjectId}`}
									</span>
								</div>
								<Tag color={PERM_COLORS.OWNER} style={{ margin: 0 }}>
									{PERM_LABELS.OWNER}
								</Tag>
							</div>
						))}
						{nonOwnerEntries.map((entry, idx) => {
							const globalIdx = entries.indexOf(entry);
							return (
								<div
									key={`entry-${entry.subjectType}-${entry.subjectId}-${idx}`}
									style={{
										display: 'flex',
										alignItems: 'center',
										justifyContent: 'space-between',
										padding: '8px 12px',
										borderBottom: idx < nonOwnerEntries.length - 1
											? '1px solid var(--color-border, rgba(255,255,255,0.06))'
											: 'none',
									}}
								>
									<div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
										<span style={{
											display: 'inline-flex',
											alignItems: 'center',
											justifyContent: 'center',
											width: 24,
											height: 24,
											borderRadius: '50%',
											background: 'var(--color-surface-hover, rgba(255,255,255,0.08))',
											color: 'var(--color-text-secondary, #9ca3af)',
											fontSize: 11,
											fontWeight: 600,
										}}>
											{(userNameMap[String(entry.subjectId)] || entry.subjectId || '?').charAt(0).toUpperCase()}
										</span>
										<span style={{ fontSize: 13, color: 'var(--color-text-primary, #e5e7eb)' }}>
											{entry.subjectType === 'USER'
											? (userNameMap[String(entry.subjectId)] || `用户 ${entry.subjectId}`)
											: `角色 ${entry.subjectId}`}
										</span>
									</div>
									<div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
										<Tag color={PERM_COLORS[entry.perm] || 'default'} style={{ margin: 0 }}>
											{PERM_LABELS[entry.perm] || entry.perm}
										</Tag>
										<Button
											type="text"
											size="small"
											danger
											disabled={saving}
											onClick={() => handleRemoveEntry(globalIdx)}
											style={{ fontSize: 12, padding: '0 4px' }}
										>
											移除
										</Button>
									</div>
								</div>
							);
						})}
					</div>
				)}
			</div>

			{/* Add users section */}
			{screenId && (
				<div style={{ marginBottom: 16 }}>
					<div style={{
						fontSize: 13,
						fontWeight: 600,
						marginBottom: 8,
						color: 'var(--color-text-primary, #e5e7eb)',
					}}>
						添加分享
					</div>
					<Input
						placeholder="搜索用户名或邮箱..."
						value={searchQuery}
						onChange={(e) => setSearchQuery(e.target.value)}
						allowClear
						style={{ marginBottom: 8 }}
					/>
					{searching && (
						<div style={{ fontSize: 12, opacity: 0.6, marginBottom: 4 }}>搜索中...</div>
					)}
					{filteredResults.length > 0 && (
						<div style={{
							border: '1px solid var(--color-border, rgba(255,255,255,0.1))',
							borderRadius: 8,
							maxHeight: 200,
							overflowY: 'auto',
							marginBottom: 8,
						}}>
							{filteredResults.map((user) => {
								const uid = String(user.id);
								const checked = selectedUserIds.has(uid);
								return (
									<div
										key={uid}
										onClick={() => toggleUser(uid)}
										style={{
											display: 'flex',
											alignItems: 'center',
											gap: 8,
											padding: '6px 12px',
											cursor: 'pointer',
											borderBottom: '1px solid var(--color-border, rgba(255,255,255,0.04))',
											background: checked
												? 'var(--color-primary-light, rgba(59,130,246,0.1))'
												: 'transparent',
											transition: 'background 0.15s',
										}}
									>
										<input
											type="checkbox"
											checked={checked}
											onChange={() => toggleUser(uid)}
											style={{ cursor: 'pointer' }}
										/>
										<span style={{
											fontSize: 13,
											color: 'var(--color-text-primary, #e5e7eb)',
											flex: 1,
										}}>
											{user.email || uid}
										</span>
										{user.common_name && (
											<span style={{
												fontSize: 12,
												color: 'var(--color-text-secondary, #9ca3af)',
											}}>
												{user.common_name}
											</span>
										)}
									</div>
								);
							})}
						</div>
					)}
					{searchQuery.trim() && !searching && filteredResults.length === 0 && searchResults.length === 0 && (
						<div style={{ fontSize: 12, opacity: 0.6, marginBottom: 8 }}>
							未找到匹配的用户
						</div>
					)}
					<div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
						<span style={{ fontSize: 12, color: 'var(--color-text-secondary, #9ca3af)' }}>
							权限:
						</span>
						<Select
							value={addPerm}
							onChange={(val) => setAddPerm(val)}
							size="small"
							style={{ width: 100 }}
							options={availablePerms}
						/>
						<Button
							type="primary"
							size="small"
							disabled={selectedUserIds.size === 0 || saving}
							loading={saving}
							onClick={handleAddSelected}
						>
							添加选中用户 {selectedUserIds.size > 0 ? `(${selectedUserIds.size})` : ''}
						</Button>
					</div>
				</div>
			)}

			{/* Share link section */}
			{screenId && (
				<>
					<div style={{
						borderTop: '1px solid var(--color-border, rgba(255,255,255,0.1))',
						paddingTop: 12,
					}}>
						<div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
							<span style={{
								fontSize: 13,
								fontWeight: 600,
								color: 'var(--color-text-primary, #e5e7eb)',
							}}>
								分享链接
							</span>
							<Button
								size="small"
								loading={linkLoading}
								onClick={handleCopyLink}
							>
								复制链接
							</Button>
						</div>
						{shareUrl && (
							<Input
								value={shareUrl}
								readOnly
								size="small"
								style={{ marginBottom: 4 }}
							/>
						)}
						<div style={{
							fontSize: 11,
							color: 'var(--color-text-tertiary, #6b7280)',
						}}>
							仅已授权用户登录后可访问；公开链接需先发布版本
						</div>
					</div>
				</>
			)}
		</Modal>
	);
}

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
