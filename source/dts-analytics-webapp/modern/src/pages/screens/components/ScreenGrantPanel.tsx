import { useEffect, useState, useCallback, useRef } from 'react';
import { analyticsApi, type ScreenGrantEntry, type UserSearchItem, type OrgNode, type RoleSummary } from '../../../api/analyticsApi';
import { Modal, Input, Select, Button, Tag, Radio, message, Spin } from 'antd';

interface ScreenGrantPanelProps {
	open: boolean;
	screenId?: string | number;
	onClose: () => void;
	isOwner?: boolean;
	ownerName?: string;
	ownerDept?: string;
	classification?: string;
}

const PERM_LABELS: Record<string, string> = {
	EDIT: '可编辑',
	READ: '只读',
};

const PERM_COLORS: Record<string, string> = {
	EDIT: 'blue',
	READ: 'green',
};

const GRANTEE_ICONS: Record<string, string> = {
	USER: '👤',
	DEPT: '🏢',
	ROLE: '🎭',
};

const GRANTEE_LABELS: Record<string, string> = {
	USER: '用户',
	DEPT: '部门',
	ROLE: '角色',
};

export function ScreenGrantPanel({ open, screenId, onClose, isOwner = false, ownerName, ownerDept, classification }: ScreenGrantPanelProps) {
	const [loading, setLoading] = useState(false);
	const [grants, setGrants] = useState<ScreenGrantEntry[]>([]);
	const [error, setError] = useState<string | null>(null);

	// Add grant state
	const [granteeType, setGranteeType] = useState<'USER' | 'DEPT' | 'ROLE'>('USER');
	const [permission, setPermission] = useState<'READ' | 'EDIT'>('READ');
	const [adding, setAdding] = useState(false);

	// User search
	const [searchQuery, setSearchQuery] = useState('');
	const [searchResults, setSearchResults] = useState<UserSearchItem[]>([]);
	const [searching, setSearching] = useState(false);
	const [selectedGranteeId, setSelectedGranteeId] = useState<string>('');
	const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

	// Org/Role lists
	const [orgs, setOrgs] = useState<OrgNode[]>([]);
	const [roles, setRoles] = useState<RoleSummary[]>([]);

	const loadGrants = useCallback(async () => {
		if (!screenId) return;
		setLoading(true);
		setError(null);
		try {
			const data = await analyticsApi.listScreenGrants(screenId);
			setGrants(Array.isArray(data) ? data : []);
		} catch (e: any) {
			setError(e?.message || '加载授权列表失败');
		} finally {
			setLoading(false);
		}
	}, [screenId]);

	useEffect(() => {
		if (open && screenId) {
			loadGrants();
			// Load org/role lists
			analyticsApi.listOrgs().then(r => {
				const data = r && typeof r === 'object' && 'data' in r ? (r as any).data : r;
				setOrgs(Array.isArray(data) ? flattenOrgs(data) : []);
			}).catch(() => {});
			analyticsApi.listRoles().then(r => {
				const data = r && typeof r === 'object' && 'data' in r ? (r as any).data : r;
				setRoles(Array.isArray(data) ? data : []);
			}).catch(() => {});
		}
	}, [open, screenId, loadGrants]);

	// User search with debounce
	useEffect(() => {
		if (granteeType !== 'USER' || searchQuery.trim().length < 1) {
			setSearchResults([]);
			return;
		}
		if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
		searchTimerRef.current = setTimeout(async () => {
			setSearching(true);
			try {
				const results = await analyticsApi.searchUsers(searchQuery.trim());
				setSearchResults(Array.isArray(results) ? results : []);
			} catch {
				setSearchResults([]);
			} finally {
				setSearching(false);
			}
		}, 300);
		return () => { if (searchTimerRef.current) clearTimeout(searchTimerRef.current); };
	}, [searchQuery, granteeType]);

	const handleAdd = async () => {
		if (!screenId || !selectedGranteeId) return;
		setAdding(true);
		try {
			await analyticsApi.addScreenGrant(screenId, {
				granteeType,
				granteeId: selectedGranteeId,
				permission,
			});
			message.success('授权成功');
			setSelectedGranteeId('');
			setSearchQuery('');
			setSearchResults([]);
			await loadGrants();
		} catch (e: any) {
			message.error(e?.message || '授权失败');
		} finally {
			setAdding(false);
		}
	};

	const handleRevoke = async (grantId: number) => {
		if (!screenId) return;
		try {
			await analyticsApi.revokeScreenGrant(screenId, grantId);
			message.success('已撤销');
			await loadGrants();
		} catch (e: any) {
			message.error(e?.message || '撤销失败');
		}
	};

	const handlePermChange = async (grant: ScreenGrantEntry, newPerm: string) => {
		if (!screenId) return;
		try {
			await analyticsApi.revokeScreenGrant(screenId, grant.id);
			await analyticsApi.addScreenGrant(screenId, {
				granteeType: grant.granteeType,
				granteeId: grant.granteeId,
				permission: newPerm,
			});
			await loadGrants();
		} catch (e: any) {
			message.error(e?.message || '修改权限失败');
		}
	};

	// Reset state on close
	const handleClose = () => {
		setSearchQuery('');
		setSearchResults([]);
		setSelectedGranteeId('');
		setGranteeType('USER');
		setPermission('READ');
		onClose();
	};

	return (
		<Modal
			title="分享大屏"
			open={open}
			onCancel={handleClose}
			footer={null}
			width={560}
			destroyOnClose
		>
			{/* Owner info */}
			<div style={{ marginBottom: 16 }}>
				<div style={{ fontWeight: 600, marginBottom: 8 }}>拥有者</div>
				<div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 12px', background: 'var(--color-bg-secondary, #f5f5f5)', borderRadius: 6 }}>
					<span style={{ width: 32, height: 32, borderRadius: '50%', background: 'var(--color-brand, #1677ff)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 600 }}>
						{(ownerName || 'O').charAt(0).toUpperCase()}
					</span>
					<span style={{ flex: 1 }}>{ownerName || '未知'}{ownerDept ? ` (${ownerDept})` : ''}</span>
					<Tag color="gold">拥有者</Tag>
				</div>
			</div>

			{/* Current grants */}
			<div style={{ marginBottom: 16 }}>
				<div style={{ fontWeight: 600, marginBottom: 8 }}>当前授权</div>
				{loading ? (
					<div style={{ textAlign: 'center', padding: 16 }}><Spin /></div>
				) : error ? (
					<div style={{ color: 'var(--color-error, red)', padding: 8 }}>{error}</div>
				) : grants.length === 0 ? (
					<div style={{ color: 'var(--color-text-secondary, #999)', padding: 8, textAlign: 'center' }}>暂无授权</div>
				) : (
					<div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
						{grants.map((g) => (
							<div key={g.id} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 12px', border: '1px solid var(--color-border, #e8e8e8)', borderRadius: 6 }}>
								<span>{GRANTEE_ICONS[g.granteeType] || '👤'}</span>
								<span style={{ flex: 1 }}>{g.granteeId}</span>
								<Select
									size="small"
									value={g.permission}
									onChange={(val) => handlePermChange(g, val)}
									style={{ width: 90 }}
									options={[
										{ value: 'READ', label: '只读' },
										{ value: 'EDIT', label: '可编辑' },
									]}
								/>
								<Button size="small" danger type="text" onClick={() => handleRevoke(g.id)}>
									撤销
								</Button>
							</div>
						))}
					</div>
				)}
			</div>

			{/* Add grant */}
			{isOwner && (
				<div style={{ borderTop: '1px solid var(--color-border, #e8e8e8)', paddingTop: 16 }}>
					<div style={{ fontWeight: 600, marginBottom: 8 }}>添加授权</div>
					<div style={{ marginBottom: 8 }}>
						<Radio.Group value={granteeType} onChange={(e) => { setGranteeType(e.target.value); setSelectedGranteeId(''); setSearchQuery(''); }}>
							<Radio.Button value="USER">用户</Radio.Button>
							<Radio.Button value="DEPT">部门</Radio.Button>
							<Radio.Button value="ROLE">角色</Radio.Button>
						</Radio.Group>
					</div>

					{/* Search/select by type */}
					{granteeType === 'USER' && (
						<>
							<Input
								placeholder="搜索用户..."
								value={searchQuery}
								onChange={(e) => setSearchQuery(e.target.value)}
								style={{ marginBottom: 8 }}
								allowClear
							/>
							{searching && <div style={{ textAlign: 'center', padding: 4 }}><Spin size="small" /></div>}
							{searchResults.length > 0 && (
								<div style={{ maxHeight: 160, overflow: 'auto', border: '1px solid var(--color-border, #e8e8e8)', borderRadius: 4, marginBottom: 8 }}>
									{searchResults.map((u) => {
										const uid = u.email ? u.email.split('@')[0] : String(u.id);
										const selected = selectedGranteeId === uid;
										return (
											<div
												key={u.id}
												onClick={() => setSelectedGranteeId(selected ? '' : uid)}
												style={{
													padding: '6px 12px',
													cursor: 'pointer',
													background: selected ? 'var(--color-brand-light, #e6f7ff)' : 'transparent',
													display: 'flex', justifyContent: 'space-between',
												}}
											>
												<span>{u.common_name || u.email}</span>
												<span style={{ color: 'var(--color-text-secondary, #999)' }}>{uid}</span>
											</div>
										);
									})}
								</div>
							)}
						</>
					)}

					{granteeType === 'DEPT' && (
						<Select
							style={{ width: '100%', marginBottom: 8 }}
							placeholder="选择部门"
							value={selectedGranteeId || undefined}
							onChange={(v) => setSelectedGranteeId(v)}
							showSearch
							optionFilterProp="label"
							options={orgs.map((o) => ({ value: o.code || o.id, label: o.name }))}
						/>
					)}

					{granteeType === 'ROLE' && (
						<Select
							style={{ width: '100%', marginBottom: 8 }}
							placeholder="选择角色"
							value={selectedGranteeId || undefined}
							onChange={(v) => setSelectedGranteeId(v)}
							showSearch
							optionFilterProp="label"
							options={roles.map((r) => ({ value: r.code || r.name, label: r.name }))}
						/>
					)}

					<div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
						<span>权限:</span>
						<Radio.Group value={permission} onChange={(e) => setPermission(e.target.value)}>
							<Radio.Button value="READ">只读</Radio.Button>
							<Radio.Button value="EDIT">可编辑</Radio.Button>
						</Radio.Group>
						<Button type="primary" disabled={!selectedGranteeId} loading={adding} onClick={handleAdd}>
							添加
						</Button>
					</div>
				</div>
			)}

			{/* Classification display */}
			{classification && (
				<div style={{ borderTop: '1px solid var(--color-border, #e8e8e8)', paddingTop: 12, marginTop: 16, color: 'var(--color-text-secondary, #999)', fontSize: 12 }}>
					密级: {classification === 'PUBLIC' ? '公开' : classification === 'INTERNAL' ? '内部' : classification === 'SECRET' ? '秘密' : classification === 'CONFIDENTIAL' ? '机密' : classification}
				</div>
			)}

			{/* Future: approval settings */}
			<div style={{ borderTop: '1px solid var(--color-border, #e8e8e8)', paddingTop: 12, marginTop: 16, color: 'var(--color-text-tertiary, #bbb)', fontSize: 12 }}>
				审批设置（暂未开放）
			</div>
		</Modal>
	);
}

function flattenOrgs(nodes: OrgNode[]): OrgNode[] {
	const result: OrgNode[] = [];
	const stack = [...nodes];
	while (stack.length > 0) {
		const node = stack.pop()!;
		result.push(node);
		if (node.children) {
			stack.push(...node.children);
		}
	}
	return result;
}
