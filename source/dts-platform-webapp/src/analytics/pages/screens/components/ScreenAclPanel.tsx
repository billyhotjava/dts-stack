import { useEffect, useState, useCallback, useMemo } from 'react';
import { analyticsApi, type ScreenAclEntry, type PlatformUser, type PlatformRole } from '../../../api/analyticsApi';
import { Modal } from 'antd';

interface ScreenAclPanelProps {
	open: boolean;
	screenId?: string | number;
	onClose: () => void;
	isOwner?: boolean;
}

const GRANTEE_TYPE_LABELS: Record<string, string> = { USER: '用户', ROLE: '角色' };
const PERM_LABELS: Record<string, string> = { OWNER: '拥有者', MANAGE: '管理者', READ: '查看者' };

function toBackendPermission(perm: 'MANAGE' | 'READ'): string {
	return perm === 'MANAGE' ? 'MANAGER' : 'VIEWER';
}

const PAGE_SIZE = 10;

export function ScreenAclPanel({ open, screenId, onClose, isOwner = false }: ScreenAclPanelProps) {
	// ── Existing grants ──
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [forbidden, setForbidden] = useState(false);
	const [rows, setRows] = useState<ScreenAclEntry[]>([]);

	// ── Add grant form ──
	const [granteeType, setGranteeType] = useState<'USER' | 'ROLE'>('USER');
	const [addPerm, setAddPerm] = useState<'MANAGE' | 'READ'>('READ');
	const [searchQuery, setSearchQuery] = useState('');
	const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
	const [adding, setAdding] = useState(false);

	// ── Candidate data sources ──
	const [platformUsers, setPlatformUsers] = useState<PlatformUser[]>([]);
	const [usersLoading, setUsersLoading] = useState(false);
	const [roles, setRoles] = useState<PlatformRole[]>([]);
	const [rolesLoading, setRolesLoading] = useState(false);

	// ── Pagination ──
	const [currentPage, setCurrentPage] = useState(1);

	// ── Load existing grants ──
	const loadGrants = useCallback(async () => {
		if (!screenId) return;
		setLoading(true);
		setError(null);
		setForbidden(false);
		try {
			const acl = await analyticsApi.getScreenAcl(screenId);
			setRows(acl || []);
		} catch (e: unknown) {
			const status = (e as { status?: number }).status;
			if (status === 403) {
				setForbidden(true);
			} else {
				setError(e instanceof Error ? e.message : '加载权限失败');
			}
			setRows([]);
		} finally {
			setLoading(false);
		}
	}, [screenId]);

	useEffect(() => {
		if (!open || !screenId) return;
		loadGrants();
	}, [open, screenId, loadGrants]);

	// ── Load platform users ──
	const loadUsers = useCallback(async (keyword: string) => {
		setUsersLoading(true);
		try {
			const result = await analyticsApi.listPlatformUsers(keyword || undefined);
			setPlatformUsers(result || []);
		} catch {
			setPlatformUsers([]);
		} finally {
			setUsersLoading(false);
		}
	}, []);

	// ── Load roles ──
	const loadRoles = useCallback(async () => {
		setRolesLoading(true);
		try {
			const result = await analyticsApi.listPlatformRoles();
			setRoles(result || []);
		} catch {
			setRoles([]);
		} finally {
			setRolesLoading(false);
		}
	}, []);

	// ── Trigger load on type switch or open ──
	useEffect(() => {
		if (!open) return;
		setSearchQuery('');
		setSelectedIds(new Set());
		setCurrentPage(1);
		if (granteeType === 'USER') {
			loadUsers('');
		} else {
			loadRoles();
		}
	}, [open, granteeType, loadUsers, loadRoles]);

	// ── Debounced user search ──
	useEffect(() => {
		if (!open || granteeType !== 'USER') return;
		const timer = setTimeout(() => {
			loadUsers(searchQuery);
		}, 300);
		return () => clearTimeout(timer);
	}, [searchQuery, open, granteeType, loadUsers]);

	// ── Filtered roles (frontend filter) ──
	const filteredRoles = useMemo(() => {
		if (!searchQuery.trim()) return roles;
		const kw = searchQuery.trim().toLowerCase();
		return roles.filter(
			(r) =>
				(r.name || '').toLowerCase().includes(kw) ||
				(r.description || '').toLowerCase().includes(kw),
		);
	}, [roles, searchQuery]);

	// ── Build username→displayName map for existing grants ──
	const userDisplayMap = useMemo(() => {
		const map = new Map<string, PlatformUser>();
		for (const u of platformUsers) {
			map.set(u.username.toLowerCase(), u);
		}
		return map;
	}, [platformUsers]);

	const resolveGrantLabel = (row: ScreenAclEntry): string => {
		if (row.subjectType === 'USER') {
			const u = userDisplayMap.get((row.subjectId || '').toLowerCase());
			if (u) {
				const parts = [u.username];
				if (u.displayName && u.displayName !== u.username) parts.push(u.displayName);
				return parts.join(' / ');
			}
		}
		return row.subjectId;
	};

	const candidateList: (PlatformUser | PlatformRole)[] = granteeType === 'USER' ? platformUsers : filteredRoles;
	const totalItems = candidateList.length;
	const totalPages = Math.max(1, Math.ceil(totalItems / PAGE_SIZE));
	const safePage = Math.min(currentPage, totalPages);
	const pagedItems = candidateList.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE);

	// Reset page when search changes
	useEffect(() => {
		setCurrentPage(1);
	}, [searchQuery, granteeType]);

	// ── Checkbox helpers ──
	const getCandidateId = (item: PlatformUser | PlatformRole): string => {
		if (granteeType === 'USER') {
			return (item as PlatformUser).username;
		}
		return (item as PlatformRole).name;
	};

	const toggleSelect = (id: string) => {
		setSelectedIds((prev) => {
			const next = new Set(prev);
			if (next.has(id)) next.delete(id);
			else next.add(id);
			return next;
		});
	};

	const isAllPageSelected = pagedItems.length > 0 && pagedItems.every((item) => selectedIds.has(getCandidateId(item)));

	const togglePageAll = () => {
		setSelectedIds((prev) => {
			const next = new Set(prev);
			const pageIds = pagedItems.map(getCandidateId);
			if (isAllPageSelected) {
				pageIds.forEach((id) => next.delete(id));
			} else {
				pageIds.forEach((id) => next.add(id));
			}
			return next;
		});
	};

	// ── Add selected grants ──
	const handleAddSelected = async () => {
		if (!screenId || selectedIds.size === 0) return;
		setAdding(true);
		setError(null);
		try {
			const permission = toBackendPermission(addPerm);
			for (const granteeId of selectedIds) {
				await analyticsApi.addScreenGrant(screenId, {
					granteeType,
					granteeId,
					permission,
				});
			}
			setSelectedIds(new Set());
			await loadGrants();
		} catch (e) {
			setError(e instanceof Error ? e.message : '添加权限失败');
		} finally {
			setAdding(false);
		}
	};

	// ── Revoke grant ──
	const handleRevoke = async (grantId: string | number | undefined) => {
		if (!screenId || grantId == null) return;
		if (!confirm('确定要移除这条权限吗？')) return;
		setError(null);
		try {
			await analyticsApi.revokeScreenGrant(screenId, grantId);
			await loadGrants();
		} catch (e) {
			setError(e instanceof Error ? e.message : '移除权限失败');
		}
	};

	const assignablePerms: ('MANAGE' | 'READ')[] = isOwner ? ['MANAGE', 'READ'] : ['READ'];

	const cellCls = 'px-3 py-2 text-xs text-text-primary';
	const headerCls = 'text-left font-medium px-3 py-2 text-xs text-text-secondary bg-surface-secondary';

	return (
		<Modal
			open={open}
			onCancel={onClose}
			title="权限管理"
			footer={null}
			width={780}
			styles={{ body: { maxHeight: '72vh', overflowY: 'auto' } }}
		>
			{!screenId && <div className="text-xs opacity-80">请先保存大屏后再配置权限。</div>}
			{forbidden && (
				<div className="border border-warning bg-warning/10 text-text-primary rounded-lg px-4 py-6 mb-3 text-center">
					<div className="text-sm font-medium mb-1">无权管理此大屏的权限</div>
					<div className="text-xs text-text-secondary">只有大屏的创建者才能查看和管理权限配置。</div>
					<button
						type="button"
						className="mt-4 px-4 py-1.5 rounded-md border border-border-default bg-surface-card text-text-primary text-xs cursor-pointer hover:border-brand hover:bg-brand/10"
						onClick={onClose}
					>
						关闭
					</button>
				</div>
			)}
			{error && !forbidden && (
				<div className="border border-error bg-error/10 text-error rounded-lg p-2.5 mb-3 text-xs whitespace-pre-wrap">
					{error}
				</div>
			)}

			{/* ── Section 1: Existing Grants ── */}
			{!forbidden && <div className="mb-5">
				<div className="text-sm font-medium text-text-primary mb-2">已有权限</div>
				{loading ? (
					<div className="text-xs text-text-muted py-4 text-center">加载中...</div>
				) : rows.length === 0 ? (
					<div className="text-xs text-text-muted py-4 text-center border border-border-default rounded-lg">暂无权限记录</div>
				) : (
					<div className="rounded-lg border border-border-default overflow-hidden">
						<table className="w-full border-collapse">
							<thead>
								<tr>
									<th className={headerCls}>类型</th>
									<th className={headerCls}>标识</th>
									<th className={headerCls}>权限</th>
									<th className={`${headerCls} text-right`}>操作</th>
								</tr>
							</thead>
							<tbody>
								{rows.map((row, idx) => (
									<tr key={row.id ?? idx} className="border-t border-border-default">
										<td className={cellCls}>{GRANTEE_TYPE_LABELS[row.subjectType] || row.subjectType}</td>
										<td className={cellCls}>{resolveGrantLabel(row)}</td>
										<td className={cellCls}>
											<span className={row.perm === 'OWNER' ? 'font-semibold text-brand' : ''}>
												{PERM_LABELS[row.perm] || row.perm}
											</span>
										</td>
										<td className={`${cellCls} text-right`}>
											{row.perm !== 'OWNER' && row.id != null ? (
												<button
													type="button"
													className="px-2 py-0.5 rounded border border-border-default bg-surface-card text-xs text-error cursor-pointer hover:border-error hover:bg-error/10"
													onClick={() => handleRevoke(row.id)}
												>
													移除
												</button>
											) : null}
										</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				)}
			</div>}

			{/* ── Section 2: Add Grant ── */}
			{screenId && !forbidden && (
				<div className="border-t border-border-default pt-4">
					<div className="text-sm font-medium text-text-primary mb-3">添加权限</div>

					{/* Type & perm selectors */}
					<div className="flex items-center gap-3 mb-3 flex-wrap">
						<div className="flex items-center gap-1.5">
							<span className="text-xs text-text-secondary">授权类型</span>
							<select
								className="px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
								value={granteeType}
								onChange={(e) => {
									setGranteeType(e.target.value as 'USER' | 'ROLE');
									setSelectedIds(new Set());
								}}
							>
								<option value="USER">用户</option>
								<option value="ROLE">角色</option>
							</select>
						</div>
						<div className="flex items-center gap-1.5">
							<span className="text-xs text-text-secondary">权限</span>
							<select
								className="px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
								value={addPerm}
								onChange={(e) => setAddPerm(e.target.value as 'MANAGE' | 'READ')}
							>
								{assignablePerms.map((p) => (
									<option key={p} value={p}>{PERM_LABELS[p]}</option>
								))}
							</select>
						</div>
						<div className="flex-1 min-w-[180px]">
							<input
								className="w-full px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
								value={searchQuery}
								onChange={(e) => setSearchQuery(e.target.value)}
								placeholder={granteeType === 'USER' ? '搜索用户名或姓名...' : '搜索角色名称...'}
							/>
						</div>
					</div>

					{/* Candidate table */}
					<div className="rounded-lg border border-border-default overflow-hidden mb-3">
						<table className="w-full border-collapse">
							<thead>
								<tr>
									<th className={`${headerCls} w-10`}>
										<input
											type="checkbox"
											checked={isAllPageSelected}
											onChange={togglePageAll}
											className="cursor-pointer"
										/>
									</th>
									{granteeType === 'USER' ? (
										<>
											<th className={headerCls}>用户名</th>
											<th className={headerCls}>姓名</th>
											<th className={headerCls}>部门</th>
										</>
									) : (
										<>
											<th className={headerCls}>角色名称</th>
											<th className={headerCls}>描述</th>
											<th className={headerCls}>来源</th>
										</>
									)}
								</tr>
							</thead>
							<tbody>
								{(usersLoading || rolesLoading) ? (
									<tr>
										<td colSpan={4} className="text-center text-xs text-text-muted py-6">加载中...</td>
									</tr>
								) : pagedItems.length === 0 ? (
									<tr>
										<td colSpan={4} className="text-center text-xs text-text-muted py-6">
											{searchQuery ? '无匹配结果' : '暂无数据'}
										</td>
									</tr>
								) : granteeType === 'USER' ? (
									(pagedItems as PlatformUser[]).map((user) => {
										const uid = user.username;
										return (
											<tr
												key={uid}
												className={`border-t border-border-default cursor-pointer transition-colors duration-100 ${selectedIds.has(uid) ? 'bg-brand/5' : 'hover:bg-surface-secondary/50'}`}
												onClick={() => toggleSelect(uid)}
											>
												<td className={cellCls}>
													<input
														type="checkbox"
														checked={selectedIds.has(uid)}
														onChange={() => toggleSelect(uid)}
														className="cursor-pointer"
													/>
												</td>
												<td className={cellCls}>{user.username}</td>
												<td className={cellCls}>{user.displayName || '-'}</td>
												<td className={`${cellCls} text-text-secondary`}>{user.deptName || user.deptCode || '-'}</td>
											</tr>
										);
									})
								) : (
									(pagedItems as PlatformRole[]).map((role) => {
										const rid = role.name;
										const sourceLabels: Record<string, string> = { builtin: '内置', custom: '自定义', assignment: '分配' };
										return (
											<tr
												key={rid}
												className={`border-t border-border-default cursor-pointer transition-colors duration-100 ${selectedIds.has(rid) ? 'bg-brand/5' : 'hover:bg-surface-secondary/50'}`}
												onClick={() => toggleSelect(rid)}
											>
												<td className={cellCls}>
													<input
														type="checkbox"
														checked={selectedIds.has(rid)}
														onChange={() => toggleSelect(rid)}
														className="cursor-pointer"
													/>
												</td>
												<td className={`${cellCls} font-medium`}>{role.name}</td>
												<td className={`${cellCls} text-text-secondary`}>{role.description || '-'}</td>
												<td className={cellCls}>{sourceLabels[role.source || ''] || role.source || '-'}</td>
											</tr>
										);
									})
								)}
							</tbody>
						</table>
					</div>

					{/* Pagination & action */}
					<div className="flex items-center justify-between flex-wrap gap-2">
						<div className="text-xs text-text-muted">
							共 {totalItems} 条
							{selectedIds.size > 0 && (
								<span className="ml-2 text-brand font-medium">已选 {selectedIds.size} 项</span>
							)}
						</div>
						<div className="flex items-center gap-1.5">
							{totalPages > 1 && (
								<div className="flex items-center gap-1 mr-3">
									<button
										type="button"
										className="px-2 py-1 rounded border border-border-default bg-surface-card text-xs cursor-pointer hover:border-brand disabled:opacity-40 disabled:cursor-not-allowed"
										disabled={safePage <= 1}
										onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
									>
										上一页
									</button>
									<span className="text-xs text-text-secondary px-1.5">{safePage} / {totalPages}</span>
									<button
										type="button"
										className="px-2 py-1 rounded border border-border-default bg-surface-card text-xs cursor-pointer hover:border-brand disabled:opacity-40 disabled:cursor-not-allowed"
										disabled={safePage >= totalPages}
										onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
									>
										下一页
									</button>
								</div>
							)}
							<button
								type="button"
								className="px-3 py-1.5 rounded-md border border-brand bg-brand text-white text-xs cursor-pointer hover:opacity-85 disabled:opacity-40 disabled:cursor-not-allowed"
								onClick={handleAddSelected}
								disabled={adding || selectedIds.size === 0}
							>
								{adding ? '添加中...' : `添加选中的 ${selectedIds.size} 项`}
							</button>
							<button
								type="button"
								className="px-3 py-1.5 rounded-md border border-border-default bg-surface-card text-text-primary text-xs cursor-pointer hover:border-brand hover:bg-brand/10"
								onClick={onClose}
							>
								关闭
							</button>
						</div>
					</div>
				</div>
			)}
		</Modal>
	);
}
