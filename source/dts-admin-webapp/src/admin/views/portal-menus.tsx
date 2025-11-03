import { Fragment, useCallback, useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import type { AdminRoleDetail, PortalMenuCollection, PortalMenuItem } from "@/admin/types";
import { isReservedBusinessRoleName } from "@/constants/keycloak-roles";
import { setPortalMenus } from "@/store/portalMenuStore";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Text } from "@/ui/typography";
import { Button } from "@/ui/button";
import { Badge } from "@/ui/badge";
import { Input } from "@/ui/input";
import { Checkbox } from "@/ui/checkbox";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { toast } from "sonner";

export default function PortalMenusView() {
	const queryClient = useQueryClient();
	const { data: portalMenus, isLoading } = useQuery<PortalMenuCollection>({
		queryKey: ["admin", "portal-menus"],
		queryFn: () => adminApi.getPortalMenus(),
	});
	const { data: rolesData, isLoading: rolesLoading } = useQuery<AdminRoleDetail[]>({
		queryKey: ["admin", "roles"],
		queryFn: () => adminApi.getAdminRoles(),
	});

	const treeMenus = useMemo(
		() => portalMenus?.allMenus ?? portalMenus?.menus ?? [],
		[portalMenus],
	);
	const activeMenus = useMemo(() => portalMenus?.menus ?? [], [portalMenus]);
	const roleOptions = useMemo(() => buildRoleOptions(rolesData ?? []), [rolesData]);
	const roleLabelMap = useMemo(() => {
		const map = new Map<string, string>();
		roleOptions.forEach((option) => {
			map.set(option.authority, option.label);
		});
		return map;
	}, [roleOptions]);
	const resolveRoleLabel = useCallback(
		(authority: string) => {
			const normalized = toRoleAuthority(authority);
			return roleLabelMap.get(normalized) ?? humanizeRoleAuthority(normalized);
		},
		[roleLabelMap],
	);

	const [pending, setPending] = useState<Record<number, boolean>>({});
	const [resetting, setResetting] = useState(false);
	const [expanded, setExpanded] = useState<Set<number>>(new Set());
	const [keyword, setKeyword] = useState("");
	const [editTarget, setEditTarget] = useState<PortalMenuItem | null>(null);

	const filteredTreeMenus = useMemo(() => {
		const trimmed = keyword.trim();
		if (!trimmed) return treeMenus;
		return filterMenusByKeyword(treeMenus, trimmed);
	}, [keyword, treeMenus]);

	const menuStats = useMemo(() => {
		let total = 0;
		let disabled = 0;
		const walk = (items: PortalMenuItem[] = []) => {
			items.forEach((item) => {
				if (!item) return;
				const children = Array.isArray(item.children) ? item.children : [];
				if (children.length) {
					walk(children);
				} else {
					total += 1;
					if (item.deleted) disabled += 1;
				}
			});
		};
		walk(treeMenus);
		const active = total - disabled;
		return { total, active, disabled };
	}, [treeMenus]);

	useEffect(() => {
		if (!isLoading) {
			setPortalMenus(activeMenus, treeMenus);
		}
	}, [activeMenus, treeMenus, isLoading]);

	// 默认展开第一层
	useEffect(() => {
		const next = new Set<number>();
		const roots = Array.isArray(treeMenus) ? treeMenus : [];
		for (const item of roots) {
			if (item?.id != null && item.children && item.children.length > 0) {
				next.add(item.id);
			}
		}
		setExpanded(next);
	}, [treeMenus]);

	useEffect(() => {
		if (!keyword.trim()) return;
		setExpanded(collectFolderIds(filteredTreeMenus));
	}, [keyword, filteredTreeMenus]);

	const refresh = () => queryClient.invalidateQueries({ queryKey: ["admin", "portal-menus"] });

	const updateCache = (next: PortalMenuCollection) => {
		queryClient.setQueryData(["admin", "portal-menus"], next);
	};
	const handleOpenRolesDialog = (menu: PortalMenuItem) => {
		setEditTarget(menu);
	};
	const handleCloseRolesDialog = () => setEditTarget(null);
	const editBusy = editTarget?.id != null ? Boolean(pending[editTarget.id]) : false;

	const markPending = (id: number, value: boolean) => {
		setPending((prev) => {
			const next = { ...prev };
			if (value) {
				next[id] = true;
			} else {
				delete next[id];
			}
			return next;
		});
	};

	const handleSubmitRoles = async (menuId: number, roles: string[]) => {
		markPending(menuId, true);
		try {
			const payload = { allowedRoles: roles };
			const result = await adminApi.updatePortalMenu(menuId, payload);
			if (result && typeof result === "object" && (result as any).menus) {
				updateCache(result as PortalMenuCollection);
			} else {
				await refresh();
			}
			toast.success("菜单可见角色已提交审批");
			return true;
		} catch (error: any) {
			toast.error(error?.message || "更新失败，请稍后重试");
			await refresh();
			return false;
		} finally {
			markPending(menuId, false);
		}
	};

	const handleToggle = async (id: number, currentlyDeleted: boolean) => {
		markPending(id, true);
		try {
			if (currentlyDeleted) {
				const result = await adminApi.updatePortalMenu(id, { deleted: false });
				// When审批开启，后端返回的是变更请求而非菜单集合；此时不要污染缓存，直接刷新
				if (result && typeof result === "object" && (result as any).menus) {
					updateCache(result as any);
				} else {
					await refresh();
				}
				// 审批模式下，启用动作也会生成“变更申请”
				toast.success("菜单已提交启用申请，等待授权管理员审批");
			} else {
				const result = await adminApi.deletePortalMenu(id);
				if (result && typeof result === "object" && (result as any).menus) {
					updateCache(result as any);
				} else {
					await refresh();
				}
				// 当启用审批流时，禁用动作会生成“变更申请”而非立即生效
				toast.success("菜单已提交禁用申请，等待授权管理员审批");
			}
		} catch (error: any) {
			toast.error(error?.message || "操作失败，请稍后重试");
			await refresh();
		} finally {
			markPending(id, false);
		}
	};

	const handleReset = async () => {
		setResetting(true);
		try {
			const result = await adminApi.resetPortalMenus();
			updateCache(result);
			toast.success("默认菜单已恢复");
		} catch (error: any) {
			toast.error(error?.message || "恢复失败，请稍后再试");
			await refresh();
		} finally {
			setResetting(false);
		}
	};

	return (
		<div className="space-y-6">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<Text variant="body1" className="text-lg font-semibold">
					菜单管理
				</Text>
				<Button variant="secondary" onClick={handleReset} disabled={resetting}>
					{resetting ? "恢复中.." : "恢复默认菜单"}
				</Button>
			</div>

			<div className="grid gap-3 sm:grid-cols-3">
				<Card>
					<CardContent className="flex flex-col gap-1 px-4 py-3">
						<Text variant="body3" className="text-muted-foreground">
							菜单总数
						</Text>
						<span className="text-2xl font-semibold">{menuStats.total}</span>
					</CardContent>
				</Card>
				<Card>
					<CardContent className="flex flex-col gap-1 px-4 py-3">
						<Text variant="body3" className="text-muted-foreground">
							已启用
						</Text>
						<span className="text-2xl font-semibold text-emerald-600">{menuStats.active}</span>
					</CardContent>
				</Card>
				<Card>
					<CardContent className="flex flex-col gap-1 px-4 py-3">
						<Text variant="body3" className="text-muted-foreground">
							已禁用
						</Text>
						<span className="text-2xl font-semibold text-red-500">{menuStats.disabled}</span>
					</CardContent>
				</Card>
			</div>

			<Card>
				<CardHeader className="pb-2">
					<div className="flex items-center gap-2">
						<CardTitle className="mr-2">菜单编辑器</CardTitle>
						<div className="ml-auto flex items-center gap-2">
							<Input
								value={keyword}
								onChange={(event) => setKeyword(event.target.value)}
								placeholder="按名称搜索菜单"
								className="w-56"
							/>
							{keyword ? (
								<Button size="sm" variant="ghost" onClick={() => setKeyword("")}>
									清除
								</Button>
							) : null}
							<Button size="sm" variant="outline" onClick={() => setExpanded(collectFolderIds(filteredTreeMenus))}>
								全部展开
							</Button>
							<Button size="sm" variant="outline" onClick={() => setExpanded(new Set())}>
								全部折叠
							</Button>
						</div>
					</div>
					<Text variant="body3" className="text-muted-foreground">
						说明：父节点仅用于分组，不提供启用/禁用按钮；叶子节点可切换状态
					</Text>
					<Text variant="body3" color="warning" className="mt-1">
						提示：禁用此项会触发菜单管理审批，请谨慎处理
					</Text>
				</CardHeader>
				<CardContent>
					{isLoading ? (
						<Text variant="body3">加载中..</Text>
					) : filteredTreeMenus.length === 0 ? (
						<Text variant="body3">暂无菜单数据</Text>
					) : (
						<div className="overflow-x-auto rounded-lg border">
							<table className="min-w-full table-auto border-collapse text-sm">
								<thead className="bg-muted/40 text-left text-xs text-muted-foreground">
									<tr>
										<th className="px-3 py-2 font-medium">菜单名称</th>
										<th className="px-3 py-2 font-medium">完整路径</th>
										<th className="px-3 py-2 font-medium">状态</th>
										<th className="px-3 py-2 font-medium">关联角色</th>
										<th className="px-3 py-2 font-medium text-right">操作</th>
									</tr>
								</thead>
								<tbody>
									{filteredTreeMenus.map((item) => (
										<MenuRow
											key={item.id}
											item={item}
											level={0}
											pathNames={[]}
											expanded={expanded}
											setExpanded={setExpanded}
											pending={pending}
											onToggle={handleToggle}
											keyword={keyword}
											onEditRoles={handleOpenRolesDialog}
											resolveRoleLabel={resolveRoleLabel}
											rolesLoading={rolesLoading}
										/>
									))}
								</tbody>
							</table>
						</div>
					)}
				</CardContent>
			</Card>
			<MenuRoleDialog
				menu={editTarget}
				onClose={handleCloseRolesDialog}
				onSubmit={handleSubmitRoles}
				roleOptions={roleOptions}
				resolveRoleLabel={resolveRoleLabel}
				rolesLoading={rolesLoading}
				busy={editBusy}
			/>
		</div>
	);
}

type MenuRowProps = {
	item: PortalMenuItem;
	level: number;
	pathNames: string[];
	expanded: Set<number>;
	setExpanded: (next: Set<number>) => void;
	pending: Record<number, boolean>;
	onToggle: (id: number, currentlyDeleted: boolean) => void;
	keyword: string;
	onEditRoles: (menu: PortalMenuItem) => void;
	resolveRoleLabel: (authority: string) => string;
	rolesLoading: boolean;
};

function MenuRow({
	item,
	level,
	pathNames,
	expanded,
	setExpanded,
	pending,
	onToggle,
	keyword,
	onEditRoles,
	resolveRoleLabel,
	rolesLoading,
}: MenuRowProps) {
	const id = item.id as number | undefined;
	if (id == null) return null;
	const baseName = item.displayName ?? item.name ?? String(id);
	const name = id != null ? `id${id}-${baseName}` : baseName;
	const children = Array.isArray(item.children) ? item.children : [];
	const isFolder = children.length > 0;
	const isExpanded = isFolder && expanded.has(id);
	const busy = pending[id];
	const isDeleted = Boolean(item.deleted);
	const allowedAuthorities = isFolder ? [] : normalizeAllowedRoles(item.allowedRoles);
	const previewRoles = allowedAuthorities.slice(0, 4);
	const remainingRoles = allowedAuthorities.length - previewRoles.length;
	const fullPath = [...pathNames, name];
	const fullPathLabel = `/${fullPath.join("/")}`;

	const toggleExpand = () => {
		if (!isFolder) return;
		const next = new Set(expanded);
		if (next.has(id)) next.delete(id);
		else next.add(id);
		setExpanded(next);
	};

	return (
		<Fragment>
			<tr className="border-b last:border-none align-top hover:bg-accent/5">
				<td className="px-3 py-2 align-top">
					<div className="flex items-start gap-2">
						<span style={{ width: level * 16 }} className="shrink-0" />
						<button
							type="button"
							aria-label="toggle"
							className="h-6 w-6 shrink-0 rounded-md border text-xs"
							onClick={toggleExpand}
							disabled={!isFolder}
							title={isFolder ? "折叠/展开" : "无子节点"}
						>
							{isFolder ? (isExpanded ? "▾" : "▸") : "·"}
						</button>
						<div className="min-w-0 flex-1 truncate font-semibold">{highlightKeyword(name, keyword)}</div>
					</div>
				</td>
				<td className="px-3 py-2 align-top text-xs text-muted-foreground break-all">{fullPathLabel}</td>
				<td className="px-3 py-2 align-top">
					{isFolder ? (
						<Badge variant="outline">目录</Badge>
					) : (
						<Badge
							variant="outline"
							className={isDeleted ? "border-red-200 text-red-600" : "border-emerald-200 text-emerald-600"}
						>
							{isDeleted ? "已禁用" : "已启用"}
						</Badge>
					)}
				</td>
				<td className="px-3 py-2 align-top">
					{isFolder ? (
						<span className="text-xs text-muted-foreground">--</span>
					) : previewRoles.length > 0 ? (
						<div className="flex flex-wrap gap-1">
							{previewRoles.map((authority) => (
								<Badge key={authority} variant="secondary" className="border">
									{resolveRoleLabel(authority)}
								</Badge>
							))}
							{remainingRoles > 0 ? (
								<Badge variant="outline" className="border-dashed text-muted-foreground">
									+{remainingRoles}
								</Badge>
							) : null}
						</div>
					) : (
						<Badge variant="outline" className="border-dashed text-muted-foreground">
							未绑定角色
						</Badge>
					)}
				</td>
				<td className="px-3 py-2 align-top text-right">
					{isFolder ? (
						<span className="text-xs text-muted-foreground">--</span>
					) : (
						<div className="flex flex-wrap justify-end gap-2">
							<Button size="sm" variant="outline" onClick={() => onToggle(id, isDeleted)} disabled={busy}>
								{isDeleted ? "启用菜单" : "禁用菜单"}
							</Button>
							<Button size="sm" variant="outline" onClick={() => onEditRoles(item)} disabled={rolesLoading || busy}>
								配置角色
							</Button>
						</div>
					)}
				</td>
			</tr>

			{isFolder && isExpanded
				? children.map((c) => (
						<MenuRow
							key={c.id}
							item={c}
							level={level + 1}
							pathNames={[...pathNames, name]}
							expanded={expanded}
							setExpanded={setExpanded}
							pending={pending}
							onToggle={onToggle}
							keyword={keyword}
							onEditRoles={onEditRoles}
							resolveRoleLabel={resolveRoleLabel}
							rolesLoading={rolesLoading}
						/>
				  ))
				: null}
		</Fragment>
	);
}

interface MenuRoleDialogProps {
	menu: PortalMenuItem | null;
	onClose: () => void;
	onSubmit: (menuId: number, roles: string[]) => Promise<boolean>;
	roleOptions: RoleOption[];
	resolveRoleLabel: (authority: string) => string;
	rolesLoading: boolean;
	busy: boolean;
}

function MenuRoleDialog({
	menu,
	onClose,
	onSubmit,
	roleOptions,
	resolveRoleLabel,
	rolesLoading,
	busy,
}: MenuRoleDialogProps) {
	const [selected, setSelected] = useState<Set<string>>(new Set());
	const [filter, setFilter] = useState("");
	const [saving, setSaving] = useState(false);
	const open = Boolean(menu);

	useEffect(() => {
		if (!menu) {
			setSelected(new Set());
			setFilter("");
			setSaving(false);
			return;
		}
		setSelected(new Set(normalizeAllowedRoles(menu.allowedRoles)));
		setFilter("");
		setSaving(false);
	}, [menu]);

	const mergedOptions = useMemo(() => {
		const map = new Map<string, RoleOption>();
		roleOptions.forEach((option) => {
			map.set(option.authority, option);
		});
		selected.forEach((authority) => {
			if (!map.has(authority)) {
				map.set(authority, {
					authority,
					label: resolveRoleLabel(authority),
				});
			}
		});
		return Array.from(map.values()).sort((a, b) => a.label.localeCompare(b.label, "zh-CN"));
	}, [roleOptions, selected, resolveRoleLabel]);

	const filteredOptions = useMemo(() => {
		const keyword = filter.trim().toLowerCase();
		if (!keyword) {
			return mergedOptions;
		}
		return mergedOptions.filter((option) => {
			const normalizedLabel = option.label.toLowerCase();
			const normalizedAuthority = option.authority.toLowerCase();
			return normalizedLabel.includes(keyword) || normalizedAuthority.includes(keyword);
		});
	}, [mergedOptions, filter]);

	const handleToggle = (authority: string, checked: boolean) => {
		setSelected((prev) => {
			const next = new Set(prev);
			if (checked) {
				next.add(authority);
			} else {
				next.delete(authority);
			}
			return next;
		});
	};

	const handleSave = async () => {
		if (!menu?.id) {
			return;
		}
		setSaving(true);
		const ok = await onSubmit(menu.id, Array.from(selected));
		setSaving(false);
		if (ok) {
			onClose();
		}
	};

	const selectedCount = selected.size;
	const disabled = rolesLoading || busy || saving;
	const menuLabel = menu?.displayName || menu?.name || menu?.path || (menu?.id ? `菜单 #${menu.id}` : "菜单");

	return (
		<Dialog open={open} onOpenChange={(next) => (!next ? onClose() : null)}>
			<DialogContent className="max-w-xl">
				<DialogHeader>
					<DialogTitle>配置菜单可见角色</DialogTitle>
					{menu ? (
						<Text variant="body3" className="text-muted-foreground">
							{menuLabel}
							{menu?.path ? <span className="ml-2 text-xs text-muted-foreground">({menu.path})</span> : null}
						</Text>
					) : null}
				</DialogHeader>
				<div className="space-y-3 text-sm">
					<Input
						value={filter}
						onChange={(event) => setFilter(event.target.value)}
						placeholder="搜索角色名称或编码"
						disabled={rolesLoading}
					/>
					<div className="rounded-md border">
						{rolesLoading && mergedOptions.length === 0 ? (
							<Text variant="body3" className="px-3 py-4 text-muted-foreground">
								角色列表加载中…
							</Text>
						) : filteredOptions.length === 0 ? (
							<Text variant="body3" className="px-3 py-4 text-muted-foreground">
								未找到匹配的角色。
							</Text>
						) : (
							<div className="max-h-72 space-y-2 overflow-y-auto px-2 py-3">
								{filteredOptions.map((option) => {
									const authority = option.authority;
									const checked = selected.has(authority);
									return (
										<label
											key={authority}
											className="flex cursor-pointer items-center gap-3 rounded-md px-2 py-1 hover:bg-accent/40"
										>
											<Checkbox
												checked={checked}
												onCheckedChange={(value) => handleToggle(authority, value === true)}
												disabled={disabled}
											/>
											<div className="flex flex-1 flex-col">
												<span className="text-sm font-medium">{option.label}</span>
												<span className="text-xs text-muted-foreground">{authority}</span>
											</div>
										</label>
									);
								})}
							</div>
						)}
					</div>
					<div className="flex items-center justify-between text-xs text-muted-foreground">
						<span>已选择 {selectedCount} 个角色</span>
						{selectedCount > 0 ? (
							<Button size="sm" variant="ghost" onClick={() => setSelected(new Set())} disabled={disabled}>
								清空选择
							</Button>
						) : null}
					</div>
				</div>
				<DialogFooter className="flex justify-end gap-2">
					<Button variant="outline" onClick={onClose} disabled={saving}>
						取消
					</Button>
					<Button onClick={handleSave} disabled={disabled}>
						{saving ? "提交中…" : "保存"}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}

type RoleOption = {
	authority: string;
	label: string;
	scope?: string;
	source?: string;
};

function buildRoleOptions(roles: AdminRoleDetail[]): RoleOption[] {
	const map = new Map<string, RoleOption>();
	roles.forEach((role) => {
		const authority = toRoleAuthority(role.roleId || role.code || role.name);
		if (!authority) {
			return;
		}
		if (isReservedBusinessRoleName(authority)) {
			return;
		}
		const label = role.displayName || role.description || role.name || humanizeRoleAuthority(authority);
		if (!map.has(authority)) {
			map.set(authority, {
				authority,
				label,
				scope: role.scope,
				source: role.source,
			});
		}
	});
	return Array.from(map.values()).sort((a, b) => a.label.localeCompare(b.label, "zh-CN"));
}

function normalizeAllowedRoles(raw?: string[]): string[] {
	if (!Array.isArray(raw)) {
		return [];
	}
	const set = new Set<string>();
	raw.forEach((value) => {
		const authority = toRoleAuthority(value);
		if (authority && !isReservedBusinessRoleName(authority)) {
			set.add(authority);
		}
	});
	return Array.from(set);
}

function toRoleAuthority(value: string | null | undefined): string {
	if (!value) {
		return "";
	}
	let normalized = value.trim();
	if (!normalized) {
		return "";
	}
	if (normalized.startsWith("ROLE_")) {
		normalized = normalized.substring(5);
	} else if (normalized.startsWith("ROLE-")) {
		normalized = normalized.substring(5);
	}
	normalized = normalized.toUpperCase().replace(/[^A-Z0-9_]/g, "_").replace(/_+/g, "_");
	if (!normalized) {
		return "";
	}
	return `ROLE_${normalized}`;
}

function humanizeRoleAuthority(authority: string): string {
	if (!authority) {
		return "未命名角色";
	}
	const core = authority.startsWith("ROLE_") ? authority.substring(5) : authority;
	return core.replace(/_/g, "·");
}

function highlightKeyword(text: string, keyword: string) {
	if (!keyword) return text;
	const lower = text.toLowerCase();
	const target = keyword.toLowerCase();
	const index = lower.indexOf(target);
	if (index === -1) return text;
	const before = text.slice(0, index);
	const match = text.slice(index, index + keyword.length);
	const after = text.slice(index + keyword.length);
	return (
		<span>
			{before}
			<span className="text-primary font-semibold">{match}</span>
			{after}
		</span>
	);
}

function filterMenusByKeyword(items: PortalMenuItem[], keyword: string): PortalMenuItem[] {
	if (!keyword) return items;
	const lower = keyword.toLowerCase();
	const walk = (nodes: PortalMenuItem[] = [], trail: string[] = []): PortalMenuItem[] => {
		const result: PortalMenuItem[] = [];
		nodes.forEach((node) => {
			if (!node) return;
			const label = (node.displayName || node.name || "").toString();
			const idName = `id${node.id ?? ""}-${label}`;
			const children = Array.isArray(node.children) ? node.children : [];
			const filteredChildren = walk(children, [...trail, label]);
			const match = label.toLowerCase().includes(lower) || idName.toLowerCase().includes(lower);
			if (match || filteredChildren.length > 0) {
				result.push({ ...node, children: filteredChildren });
			}
		});
		return result;
	};
	return walk(items);
}

function collectFolderIds(items: PortalMenuItem[]): Set<number> {
	const out = new Set<number>();
	const walk = (list: PortalMenuItem[]) => {
		for (const it of list) {
			if (it?.id == null) continue;
			if (it.children && it.children.length > 0) {
				out.add(it.id as number);
				walk(it.children);
			}
		}
	};
	walk(items ?? []);
	return out;
}
