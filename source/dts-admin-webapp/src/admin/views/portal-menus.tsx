import { Fragment, useCallback, useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import type { AdminRoleDetail, PortalMenuCollection, PortalMenuItem } from "@/admin/types";
import { isReservedBusinessRoleName } from "@/constants/keycloak-roles";
import { setPortalMenus } from "@/store/portalMenuStore";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Text } from "@/ui/typography";
import { Button } from "antd";
import { Badge } from "@/ui/badge";
import { Input } from "@/ui/input";
import { Checkbox } from "@/ui/checkbox";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
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
	const [disableTarget, setDisableTarget] = useState<{ menu: PortalMenuItem; roleCodes: string[] } | null>(null);
	const [customEditTarget, setCustomEditTarget] = useState<PortalMenuItem | null>(null);
	const [customEditDraft, setCustomEditDraft] = useState<{ title: string; url: string }>({ title: "", url: "" });
	const [customEditBusy, setCustomEditBusy] = useState(false);
	const [customDeleteTarget, setCustomDeleteTarget] = useState<PortalMenuItem | null>(null);
	const [customDeleteBusy, setCustomDeleteBusy] = useState(false);
	const [quickAddOpen, setQuickAddOpen] = useState(false);
	const [quickAddDraft, setQuickAddDraft] = useState<{ title: string; url: string; parentId: string }>({
		title: "",
		url: "",
		parentId: "",
	});
	const [quickAddBusy, setQuickAddBusy] = useState(false);

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

	const menuIndex = useMemo(() => buildMenuIndex(treeMenus), [treeMenus]);
	const parentOptions = useMemo(() => buildParentSelectOptions(treeMenus), [treeMenus]);
	const defaultQuickAddParentId = useMemo(
		() => resolvePortalSectionId(treeMenus, "visualization"),
		[treeMenus],
	);

	const handleOpenQuickAdd = () => {
		setQuickAddDraft({
			title: "",
			url: "",
			parentId: defaultQuickAddParentId != null ? String(defaultQuickAddParentId) : "",
		});
		setQuickAddOpen(true);
	};

	const handleOpenCustomEdit = (menu: PortalMenuItem) => {
		if (!menu?.id) return;
		if (!isCustomPortalMenu(menu)) {
			toast.error("系统内置菜单不支持编辑");
			return;
		}
		const meta = parseMetadata(menu.metadata) ?? {};
		const title = String(meta?.title ?? menu.displayName ?? menu.name ?? "").trim();
		const url = String(meta?.externalLink ?? meta?.url ?? "").trim();
		setCustomEditTarget(menu);
		setCustomEditDraft({ title, url });
	};

	const handleCloseCustomEdit = () => {
		if (customEditBusy) return;
		setCustomEditTarget(null);
		setCustomEditDraft({ title: "", url: "" });
	};

	const isValidLink = (value: string) => /^https?:\/\//i.test(value) || value.startsWith("/");

	const handleSubmitCustomEdit = async () => {
		const menu = customEditTarget;
		if (!menu?.id) return;
		const title = customEditDraft.title.trim();
		const url = customEditDraft.url.trim();
		if (!title) {
			toast.error("请填写菜单名称");
			return;
		}
		if (!isValidLink(url)) {
			toast.error("请填写以 http(s):// 或 / 开头的链接地址");
			return;
		}
		setCustomEditBusy(true);
		try {
			const meta = parseMetadata(menu.metadata) ?? {};
			const nextMeta = { ...meta, title, externalLink: url };
			const result = await adminApi.updatePortalMenu(menu.id, { metadata: JSON.stringify(nextMeta) } as any);
			if (result && typeof result === "object" && (result as any).menus) {
				updateCache(result as PortalMenuCollection);
			} else {
				await refresh();
			}
			toast.success("菜单已更新");
			handleCloseCustomEdit();
		} catch (error: any) {
			toast.error(error?.message || "更新失败，请稍后重试");
			await refresh();
		} finally {
			setCustomEditBusy(false);
		}
	};

	const handleOpenCustomDelete = (menu: PortalMenuItem) => {
		if (!menu?.id) return;
		if (!isCustomPortalMenu(menu)) {
			toast.error("系统内置菜单不允许删除，只能禁用");
			return;
		}
		setCustomDeleteTarget(menu);
	};

	const handleCancelCustomDelete = () => {
		if (customDeleteBusy) return;
		setCustomDeleteTarget(null);
	};

	const handleConfirmCustomDelete = async () => {
		const menu = customDeleteTarget;
		if (!menu?.id) return;
		setCustomDeleteBusy(true);
		try {
			const result = await adminApi.deletePortalMenuHard(menu.id);
			if (result && typeof result === "object" && (result as any).menus) {
				updateCache(result as PortalMenuCollection);
			} else {
				await refresh();
			}
			toast.success("菜单删除请求已提交（如启用审批流）");
		} catch (error: any) {
			toast.error(error?.message || "删除失败，请稍后重试");
			await refresh();
		} finally {
			setCustomDeleteBusy(false);
			setCustomDeleteTarget(null);
		}
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

	const processToggle = async (menu: PortalMenuItem, currentlyDeleted: boolean) => {
		const id = menu.id;
		if (id == null) return;
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

	const handleToggle = (menu: PortalMenuItem) => {
		const id = menu.id;
		if (id == null) return;
		if (!menu.deleted) {
			const roleCodes = normalizeAllowedRoles(menu.allowedRoles);
			setDisableTarget({ menu, roleCodes });
			return;
		}
		void processToggle(menu, true);
	};

	const handleCancelDisable = () => setDisableTarget(null);

	const handleConfirmDisable = async () => {
		if (!disableTarget) return;
		try {
			await processToggle(disableTarget.menu, false);
		} finally {
			setDisableTarget(null);
		}
	};

	const disableBusy = disableTarget?.menu.id != null ? Boolean(pending[disableTarget.menu.id]) : false;

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

	const handleSubmitQuickAdd = async () => {
		const title = quickAddDraft.title.trim();
		const url = quickAddDraft.url.trim();
		if (!title) {
			toast.error("请填写菜单名称");
			return;
		}
		if (!isValidLink(url)) {
			toast.error("请填写以 http(s):// 或 / 开头的链接地址");
			return;
		}

		const parentIdRaw = quickAddDraft.parentId.trim();
		if (!parentIdRaw) {
			toast.error("请选择父节点");
			return;
		}
		const parentId = Number.parseInt(parentIdRaw, 10);
		if (!Number.isFinite(parentId)) {
			toast.error("父节点不合法，请重新选择");
			return;
		}
		const parentNode = menuIndex.get(parentId);
		if (!parentNode) {
			toast.error("未找到父节点，请刷新后重试");
			return;
		}

		const usedFullPaths = collectFullPaths(treeMenus);
		const segment = buildUniqueSegment(title, usedFullPaths, parentNode.fullPath || "");
		const meta: Record<string, any> = { title, externalLink: url };
		if (parentNode.sectionKey) {
			meta.sectionKey = parentNode.sectionKey;
		}
		const nameSuffix = `${Date.now().toString(36)}-${segment}`;

		setQuickAddBusy(true);
		try {
			const defaultRoles = [
				"ROLE_EMPLOYEE",
				"ROLE_DEPT_DATA_VIEWER",
				"ROLE_DEPT_DATA_DEV",
				"ROLE_DEPT_DATA_OWNER",
				"ROLE_DEPT_LEADER",
				"ROLE_INST_DATA_VIEWER",
				"ROLE_INST_DATA_DEV",
				"ROLE_INST_DATA_OWNER",
				"ROLE_INST_LEADER",
				"ROLE_OP_ADMIN",
			];
			const payload: PortalMenuItem = {
				name: `custom.link.${nameSuffix}`,
				path: segment,
				parentId,
				component: "/pages/sys/others/link/external-link",
				icon: "solar:link-bold-duotone",
				sortOrder: 999,
				metadata: JSON.stringify(meta),
				// Default: align with “报表与大屏”可见角色；可在创建后用“配置角色”精确收敛
				allowedRoles: defaultRoles,
			};
			const result = await adminApi.createPortalMenu(payload);
			if (result && typeof result === "object" && (result as any).menus) {
				updateCache(result as PortalMenuCollection);
			} else {
				await refresh();
			}
			toast.success("菜单已提交新增审批（如启用审批流）");
			setQuickAddOpen(false);
			setQuickAddDraft({ title: "", url: "", parentId: "" });
		} catch (error: any) {
			toast.error(error?.message || "添加失败，请稍后重试");
			await refresh();
		} finally {
			setQuickAddBusy(false);
		}
	};

	return (
		<div className="space-y-6">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<Text variant="body1" className="text-lg font-semibold">
					菜单管理
				</Text>
				<div className="flex items-center gap-2">
					<Button type="default" onClick={handleOpenQuickAdd}>
						添加菜单
					</Button>
					<Button type="default" onClick={handleReset} disabled={resetting}>
						{resetting ? "恢复中.." : "恢复默认菜单"}
					</Button>
				</div>
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
								<Button size="small" type="text" onClick={() => setKeyword("")}>
									清除
								</Button>
							) : null}
							<Button size="small" type="default" onClick={() => setExpanded(collectFolderIds(filteredTreeMenus))}>
								全部展开
							</Button>
							<Button size="small" type="default" onClick={() => setExpanded(new Set())}>
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
											onEditCustom={handleOpenCustomEdit}
											onDeleteCustom={handleOpenCustomDelete}
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
			<DisableMenuDialog
				target={disableTarget}
				onCancel={handleCancelDisable}
				onConfirm={handleConfirmDisable}
				resolveRoleLabel={resolveRoleLabel}
				busy={disableBusy}
			/>
			<CustomEditMenuDialog
				menu={customEditTarget}
				title={customEditDraft.title}
				url={customEditDraft.url}
				onChangeTitle={(value) => setCustomEditDraft((prev) => ({ ...prev, title: value }))}
				onChangeUrl={(value) => setCustomEditDraft((prev) => ({ ...prev, url: value }))}
				onClose={handleCloseCustomEdit}
				onSubmit={handleSubmitCustomEdit}
				busy={customEditBusy}
			/>
			<CustomDeleteMenuDialog
				menu={customDeleteTarget}
				onCancel={handleCancelCustomDelete}
				onConfirm={handleConfirmCustomDelete}
				busy={customDeleteBusy}
			/>
			<QuickAddMenuDialog
				open={quickAddOpen}
				onClose={() => (quickAddBusy ? null : setQuickAddOpen(false))}
				title={quickAddDraft.title}
				url={quickAddDraft.url}
				parentId={quickAddDraft.parentId}
				parentOptions={parentOptions}
				onChangeTitle={(value) => setQuickAddDraft((prev) => ({ ...prev, title: value }))}
				onChangeUrl={(value) => setQuickAddDraft((prev) => ({ ...prev, url: value }))}
				onChangeParentId={(value) => setQuickAddDraft((prev) => ({ ...prev, parentId: value }))}
				onSubmit={handleSubmitQuickAdd}
				busy={quickAddBusy}
			/>
		</div>
	);
}

function QuickAddMenuDialog(props: {
	open: boolean;
	onClose: () => void;
	title: string;
	url: string;
	parentId: string;
	parentOptions: { value: string; label: string; disabled?: boolean }[];
	onChangeTitle: (value: string) => void;
	onChangeUrl: (value: string) => void;
	onChangeParentId: (value: string) => void;
	onSubmit: () => void;
	busy: boolean;
}) {
	return (
		<Dialog open={props.open} onOpenChange={(next) => (next ? null : props.onClose())}>
			<DialogContent className="sm:max-w-lg">
				<DialogHeader>
					<DialogTitle>添加菜单</DialogTitle>
				</DialogHeader>
				<div className="space-y-3">
					<div className="space-y-1.5">
						<Text variant="body3" className="text-muted-foreground">
							父节点
						</Text>
						<Select value={props.parentId} onValueChange={props.onChangeParentId}>
							<SelectTrigger className="w-full">
								<SelectValue placeholder="请选择父节点（用于分组）" />
							</SelectTrigger>
							<SelectContent>
								{props.parentOptions.map((option) => (
									<SelectItem key={option.value} value={option.value} disabled={option.disabled}>
										{option.label}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
					</div>
					<div className="space-y-1.5">
						<Text variant="body3" className="text-muted-foreground">
							菜单名称
						</Text>
						<Input
							value={props.title}
							onChange={(event) => props.onChangeTitle(event.target.value)}
							placeholder="例如：河图驾驶舱"
						/>
					</div>
					<div className="space-y-1.5">
						<Text variant="body3" className="text-muted-foreground">
							跳转链接（http(s):// 或 /）
						</Text>
						<Input
							value={props.url}
							onChange={(event) => props.onChangeUrl(event.target.value)}
							placeholder="例如：/screen/share/index.html#/TJ... 或 https://bi.xxx.com/screen/share/index.html#/TJ..."
						/>
					</div>
					<Text variant="body3" className="text-muted-foreground">
						说明：创建后可在列表中点击“配置角色”调整可见范围；如启用审批流，需要授权管理员审批后生效。
					</Text>
				</div>
				<DialogFooter>
					<Button type="default" onClick={props.onClose} disabled={props.busy}>
						取消
					</Button>
					<Button type="primary" onClick={props.onSubmit} disabled={props.busy}>
						{props.busy ? "提交中.." : "提交"}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}

type MenuRowProps = {
	item: PortalMenuItem;
	level: number;
	pathNames: string[];
	expanded: Set<number>;
	setExpanded: (next: Set<number>) => void;
	pending: Record<number, boolean>;
	onToggle: (menu: PortalMenuItem) => void;
	onEditCustom: (menu: PortalMenuItem) => void;
	onDeleteCustom: (menu: PortalMenuItem) => void;
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
	onEditCustom,
	onDeleteCustom,
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
	const isCustom = isCustomPortalMenu(item);
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
							<Button size="small" type="default" onClick={() => onToggle(item)} disabled={busy}>
								{isDeleted ? "启用菜单" : "禁用菜单"}
							</Button>
							<Button size="small" type="default" onClick={() => onEditRoles(item)} disabled={rolesLoading || busy}>
								配置角色
							</Button>
							{isCustom ? (
								<>
									<Button size="small" type="default" onClick={() => onEditCustom(item)} disabled={busy}>
										编辑
									</Button>
									<Button size="small" danger type="primary" onClick={() => onDeleteCustom(item)} disabled={busy}>
										删除
									</Button>
								</>
							) : null}
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
							onEditCustom={onEditCustom}
							onDeleteCustom={onDeleteCustom}
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

function CustomEditMenuDialog(props: {
	menu: PortalMenuItem | null;
	title: string;
	url: string;
	onChangeTitle: (value: string) => void;
	onChangeUrl: (value: string) => void;
	onClose: () => void;
	onSubmit: () => void;
	busy: boolean;
}) {
	const open = Boolean(props.menu);
	const label = props.menu?.displayName || props.menu?.name || (props.menu?.id ? `菜单 #${props.menu.id}` : "菜单");
	return (
		<Dialog open={open} onOpenChange={(next) => (!next ? props.onClose() : null)}>
			<DialogContent className="sm:max-w-lg">
				<DialogHeader>
					<DialogTitle>编辑菜单</DialogTitle>
					<Text variant="body3" className="text-muted-foreground">
						{label}
					</Text>
				</DialogHeader>
				<div className="space-y-3">
					<div className="space-y-1.5">
						<Text variant="body3" className="text-muted-foreground">
							菜单名称
						</Text>
						<Input
							value={props.title}
							onChange={(event) => props.onChangeTitle(event.target.value)}
							placeholder="例如：河图驾驶舱"
							disabled={props.busy}
						/>
					</div>
					<div className="space-y-1.5">
						<Text variant="body3" className="text-muted-foreground">
							跳转链接（http(s):// 或 /）
						</Text>
						<Input
							value={props.url}
							onChange={(event) => props.onChangeUrl(event.target.value)}
							placeholder="例如：/screen/share/index.html#/TJ... 或 https://bi.xxx.com/..."
							disabled={props.busy}
						/>
					</div>
				</div>
				<DialogFooter>
					<Button type="default" onClick={props.onClose} disabled={props.busy}>
						取消
					</Button>
					<Button type="primary" onClick={props.onSubmit} disabled={props.busy}>
						{props.busy ? "保存中.." : "保存"}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}

function CustomDeleteMenuDialog(props: {
	menu: PortalMenuItem | null;
	onCancel: () => void;
	onConfirm: () => void;
	busy: boolean;
}) {
	const open = Boolean(props.menu);
	const label = props.menu?.displayName || props.menu?.name || (props.menu?.id ? `菜单 #${props.menu.id}` : "菜单");
	return (
		<Dialog open={open} onOpenChange={(next) => (!next ? props.onCancel() : null)}>
			<DialogContent className="sm:max-w-lg">
				<DialogHeader>
					<DialogTitle>删除菜单</DialogTitle>
				</DialogHeader>
				<div className="space-y-2">
					<Text variant="body3" className="text-muted-foreground">
						将永久删除此菜单：{label}
					</Text>
					<Text variant="body3" color="warning">
						提示：若启用审批流，该操作会提交删除审批；审批通过后才会真正删除。
					</Text>
				</div>
				<DialogFooter>
					<Button type="default" onClick={props.onCancel} disabled={props.busy}>
						取消
					</Button>
					<Button danger type="primary" onClick={props.onConfirm} disabled={props.busy}>
						{props.busy ? "删除中.." : "确认删除"}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
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
							<Button size="small" type="text" onClick={() => setSelected(new Set())} disabled={disabled}>
								清空选择
							</Button>
						) : null}
					</div>
				</div>
				<DialogFooter className="flex justify-end gap-2">
					<Button type="default" onClick={onClose} disabled={saving}>
						取消
					</Button>
					<Button type="primary" onClick={handleSave} disabled={disabled}>
						{saving ? "提交中…" : "保存"}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}

type DisableMenuDialogProps = {
	target: { menu: PortalMenuItem; roleCodes: string[] } | null;
	onCancel: () => void;
	onConfirm: () => void | Promise<void>;
	resolveRoleLabel: (authority: string) => string;
	busy: boolean;
};

function DisableMenuDialog({ target, onCancel, onConfirm, resolveRoleLabel, busy }: DisableMenuDialogProps) {
	const open = Boolean(target);
	const menu = target?.menu ?? null;
	const menuLabel = menu?.displayName || menu?.name || menu?.path || (menu?.id ? `菜单 #${menu.id}` : "菜单");
	const menuPath = menu?.path;

	const roleBadges = useMemo(() => {
		if (!target) {
			return [];
		}
		const codes = Array.from(
			new Set(
				target.roleCodes
					.map((code) => toRoleAuthority(code))
					.filter((authority) => Boolean(authority))
			)
		);
		return codes.map((authority) => ({
			code: authority,
			label: resolveRoleLabel(authority),
		}));
	}, [target, resolveRoleLabel]);

	return (
		<Dialog open={open} onOpenChange={(next) => (!next ? onCancel() : null)}>
			<DialogContent className="max-w-md">
				<DialogHeader>
					<DialogTitle>确认禁用菜单</DialogTitle>
					{menu ? (
						<Text variant="body3" className="text-muted-foreground">
							{menuLabel}
							{menuPath ? <span className="ml-2 text-xs text-muted-foreground">({menuPath})</span> : null}
						</Text>
					) : null}
				</DialogHeader>
				<div className="space-y-3 text-sm">
					<Text variant="body3" className="text-muted-foreground">
						禁用后，该菜单将无法访问，并会清除所有已绑定的角色可见权限。确认要提交禁用申请吗？
					</Text>
					<div className="space-y-2">
						<Text variant="body3" className="font-medium">
							将被移除的角色
						</Text>
						{roleBadges.length > 0 ? (
							<div className="flex flex-wrap gap-2">
								{roleBadges.map((item) => (
									<Badge key={item.code} variant="secondary" className="border">
										<span className="mr-1">{item.label}</span>
										<span className="text-xs text-muted-foreground">({item.code})</span>
									</Badge>
								))}
							</div>
						) : (
							<Text variant="body3" className="text-xs text-muted-foreground">
								当前菜单未绑定角色，但禁用后仍会清空角色关联。
							</Text>
						)}
					</div>
				</div>
				<DialogFooter className="flex justify-end gap-2">
					<Button htmlType="button" type="default" onClick={onCancel} disabled={busy}>
						取消
					</Button>
					<Button htmlType="button" danger type="primary" onClick={() => onConfirm()} disabled={busy}>
						{busy ? "提交中..." : "确认禁用"}
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

function parseMetadata(raw?: string): Record<string, any> | null {
	if (!raw) return null;
	try {
		return JSON.parse(raw) as Record<string, any>;
	} catch {
		return null;
	}
}

function isCustomPortalMenu(menu: PortalMenuItem | null | undefined): boolean {
	if (!menu) return false;
	const name = String(menu.name || "").trim().toLowerCase();
	return name.startsWith("custom.");
}

type MenuIndexEntry = {
	id: number;
	fullPath: string;
	sectionKey: string | null;
	deleted: boolean;
};

function buildMenuIndex(items: PortalMenuItem[]): Map<number, MenuIndexEntry> {
	const out = new Map<number, MenuIndexEntry>();
	const walk = (nodes: PortalMenuItem[], parentPath: string, inheritedSectionKey: string | null) => {
		for (const node of nodes) {
			if (!node || node.id == null) continue;
			const segment = (node.path || "").toString().replace(/^\/+|\/+$/g, "");
			const fullPath = segment ? (parentPath ? `${parentPath}/${segment}` : `/${segment}`) : parentPath || "";
			const meta = parseMetadata(node.metadata);
			const sectionKey =
				typeof meta?.sectionKey === "string" && meta.sectionKey.trim() ? meta.sectionKey.trim() : inheritedSectionKey;

			out.set(node.id as number, {
				id: node.id as number,
				fullPath,
				sectionKey,
				deleted: Boolean(node.deleted),
			});

			if (Array.isArray(node.children) && node.children.length > 0) {
				walk(node.children, fullPath, sectionKey);
			}
		}
	};
	walk(items ?? [], "", null);
	return out;
}

function buildParentSelectOptions(items: PortalMenuItem[]): { value: string; label: string; disabled?: boolean }[] {
	const out: { value: string; label: string; disabled?: boolean }[] = [];
	const walk = (nodes: PortalMenuItem[], depth: number) => {
		for (const node of nodes) {
			if (!node || node.id == null) continue;
			const meta = parseMetadata(node.metadata);
			const baseName = node.displayName ?? (meta?.title as string | undefined) ?? node.name ?? String(node.id);
			const prefix = depth > 0 ? `${"—".repeat(Math.min(depth, 6))} ` : "";
			out.push({
				value: String(node.id),
				label: `${prefix}${baseName}`,
				disabled: Boolean(node.deleted),
			});
			if (Array.isArray(node.children) && node.children.length > 0) {
				walk(node.children, depth + 1);
			}
		}
	};
	walk(items ?? [], 0);
	return out;
}

function resolvePortalSectionId(items: PortalMenuItem[], sectionKey: string): number | null {
	const normalized = sectionKey.trim().toLowerCase();
	const stack = Array.isArray(items) ? [...items] : [];
	while (stack.length) {
		const node = stack.pop();
		if (!node || node.id == null) continue;
		const meta = parseMetadata(node.metadata);
		const metaSection = typeof meta?.sectionKey === "string" ? meta.sectionKey.trim().toLowerCase() : "";
		if (!node.parentId && metaSection === normalized) {
			return node.id;
		}
		if (Array.isArray(node.children)) {
			for (const child of node.children) {
				stack.push(child);
			}
		}
	}
	return null;
}

function collectFullPaths(items: PortalMenuItem[]): Set<string> {
	const out = new Set<string>();
	const walk = (nodes: PortalMenuItem[], parentPath: string) => {
		for (const node of nodes) {
			if (!node) continue;
			const segment = (node.path || "").toString().replace(/^\/+|\/+$/g, "");
			const current = segment ? (parentPath ? `${parentPath}/${segment}` : `/${segment}`) : parentPath || "";
			if (current) out.add(current);
			if (Array.isArray(node.children) && node.children.length > 0) {
				walk(node.children, current);
			}
		}
	};
	walk(items ?? [], "");
	return out;
}

function slugifySegment(input: string): string {
	const raw = (input || "").trim().toLowerCase();
	const ascii = raw.replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
	return ascii;
}

function buildUniqueSegment(title: string, usedFullPaths: Set<string>, parentPrefix: string): string {
	const baseSlug = slugifySegment(title);
	const base = baseSlug ? `link-${baseSlug}` : `link-${Date.now().toString(36)}`;
	let candidate = base;
	let i = 1;
	while (true) {
		const fullPath = `${parentPrefix}/${candidate}`.replace(/\/{2,}/g, "/");
		if (!usedFullPaths.has(fullPath)) {
			return candidate;
		}
		i += 1;
		candidate = `${base}-${i}`;
	}
}
