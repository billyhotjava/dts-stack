import { useCallback, useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { adminApi } from "@/admin/api/adminApi";
import type {
    AdminCustomRole,
    AdminRoleAssignment,
    AdminRoleDetail,
    DataOperation,
    PortalMenuCollection,
    PortalMenuItem,
} from "@/admin/types";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Checkbox } from "@/ui/checkbox";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Text } from "@/ui/typography";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/ui/tooltip";
import { Textarea } from "@/ui/textarea";
import { toast } from "sonner";
import { GLOBAL_CONFIG } from "@/global-config";
import { isKeycloakBuiltInRole } from "@/constants/keycloak-roles";

// NOTE: Any previously declared constants used only in commented UI
// blocks have been removed to satisfy TypeScript noUnusedLocals during
// production builds.

// Hide internal/administrative roles from role management UI.
// Use canonical codes (strip ROLE_ and underscores) for matching.
const RESERVED_ROLE_CODES = new Set([
    "SYSADMIN",
    "AUTHADMIN",
    "OPADMIN",
    // Auditor (canonical: ROLE_SECURITY_AUDITOR). Keep code aliases for backwards compatibility in filtering only.
    "AUDITORADMIN",
    "SECURITYAUDITOR",
]);

interface RoleRow {
    id?: number;
    key: string;
    authority: string;
    displayName: string;
    canonical: string;
    code?: string;
    nameZh?: string;
    nameEn?: string;
    zone?: "DEPT" | "INST";
    description?: string | null;
    scope?: "DEPARTMENT" | "INSTITUTE";
    operations: DataOperation[];
    canManage?: boolean;
    menuIds: number[];
    menuLabels: string[];
    assignments: AdminRoleAssignment[];
    source?: string;
    kcMemberCount?: number;
}

interface MenuOption {
    id: number;
    label: string;
    depth: number;
    rawRoles: string[];
    canonicalRoles: string[];
    deleted: boolean;
}

export default function RoleManagementView() {
    const queryClient = useQueryClient();
    const {
        data: rolesData,
        isLoading: rolesLoading,
        isError: rolesError,
    } = useQuery<AdminRoleDetail[]>({
        queryKey: ["admin", "roles"],
        queryFn: adminApi.getAdminRoles,
    });
    const {
        data: customRoles,
        isLoading: customLoading,
        isError: customError,
    } = useQuery<AdminCustomRole[]>({
        queryKey: ["admin", "custom-roles"],
        queryFn: adminApi.getCustomRoles,
    });
    const {
        data: assignments,
        isLoading: assignmentLoading,
        isError: assignmentError,
    } = useQuery<AdminRoleAssignment[]>({
        queryKey: ["admin", "role-assignments"],
        queryFn: adminApi.getRoleAssignments,
    });
    const {
        data: portalMenus,
        isLoading: menuLoading,
        isError: menuError,
    } = useQuery<PortalMenuCollection>({
        queryKey: ["admin", "portal-menus"],
        queryFn: adminApi.getPortalMenus,
    });

    const [createOpen, setCreateOpen] = useState(false);
    const [editTarget, setEditTarget] = useState<RoleRow | null>(null);
    const [deleteTarget, setDeleteTarget] = useState<RoleRow | null>(null);
    const [memberTarget, setMemberTarget] = useState<RoleRow | null>(null);

    const isLoading = rolesLoading || customLoading || assignmentLoading || menuLoading;
    const hasError = rolesError || customError || assignmentError || menuError;

    const menuCatalog = useMemo(() => buildMenuCatalog(portalMenus), [portalMenus]);
    const menuOptions = menuCatalog.options;
    const menuRoleMap = menuCatalog.menuRoleMap;
    const roleMenuIndex = menuCatalog.roleToMenuIds;
    const menuParentMap = menuCatalog.parentMap;
    const menuChildrenMap = menuCatalog.childrenMap;
    const menuLabelMap = useMemo(() => {
        const map = new Map<number, string>();
        menuOptions.forEach((option) => {
            map.set(option.id, option.label);
        });
        return map;
    }, [menuOptions]);

    const roleRows = useMemo(() => {
        const map = new Map<string, RoleRow>();
        const roles = rolesData ?? [];
        const extra = customRoles ?? [];
        const assignmentMap = groupAssignments(assignments);

        const hideDefaultRoles = GLOBAL_CONFIG.hideDefaultRoles;
        const hideBuiltinRoles = GLOBAL_CONFIG.hideBuiltinRoles;
        const isDefaultRoles = (name?: string) => {
            if (!name) return false;
            const lower = name.trim().toLowerCase();
            return lower.startsWith("default-roles-");
        };

        roles.forEach((role) => {
            const authorityCode = role.roleId || role.code || role.name;
            const canonical = canonicalRole(authorityCode);
            if (!canonical || RESERVED_ROLE_CODES.has(canonical)) {
                return;
            }
            if (hideDefaultRoles && isDefaultRoles(role.name)) {
                return;
            }
            if (hideBuiltinRoles && isKeycloakBuiltInRole({ name: role.name } as any)) {
                return;
            }
            const menuIds = Array.from(new Set(roleMenuIndex.get(canonical) ?? [])).sort((a, b) => a - b);
            const entry: RoleRow = {
                id: role.id,
                key: role.id?.toString() ?? authorityCode,
                authority: authorityCode,
                displayName: (role as any).nameZh || role.name,
                canonical,
                code: role.roleId || (role as any).code || canonical,
                nameZh: (role as any).nameZh,
                nameEn: (role as any).nameEn,
                zone: (role as any).zone,
                description: role.description,
                scope: role.scope ?? undefined,
                operations: role.operations ?? [],
                canManage: (role as any).canManage ?? canonical.endsWith("_OWNER"),
                menuIds,
                menuLabels: menuIds.map((id) => menuLabelMap.get(id) ?? `菜单 ${id}`),
                assignments: assignmentMap.get(canonical) ?? [],
                source: role.source ?? undefined,
                kcMemberCount: role.kcMemberCount ?? role.memberCount ?? 0,
            };
            map.set(canonical, entry);
        });

        extra.forEach((role) => {
            const canonical = canonicalRole(role.name);
            if (!canonical || RESERVED_ROLE_CODES.has(canonical)) {
                return;
            }
            if (hideDefaultRoles && isDefaultRoles(role.name)) {
                return;
            }
            if (hideBuiltinRoles && isKeycloakBuiltInRole({ name: role.name } as any)) {
                return;
            }
            const existing = map.get(canonical);
            if (existing) {
                existing.description = existing.description ?? role.description;
                existing.scope = existing.scope ?? role.scope;
                if (!existing.operations.length && role.operations?.length) {
                    existing.operations = role.operations;
                }
                existing.source = existing.source ?? "custom";
                if (!existing.menuIds.length) {
                    const menuIds = Array.from(new Set(roleMenuIndex.get(canonical) ?? [])).sort((a, b) => a - b);
                    existing.menuIds = menuIds;
                    existing.menuLabels = menuIds.map((id) => menuLabelMap.get(id) ?? `菜单 ${id}`);
                }
            } else {
                const menuIds = Array.from(new Set(roleMenuIndex.get(canonical) ?? [])).sort((a, b) => a - b);
                const matchedRole = (rolesData || []).find(
                    (r) => toRoleName(r.name) === canonical || toRoleName(r.code || "") === canonical
                );
                map.set(canonical, {
                    key: `custom-${role.id}`,
                    authority: role.name,
                    displayName: role.name,
                    canonical,
                    description: role.description,
                    scope: role.scope,
                    operations: role.operations ?? [],
                    canManage: canonical.endsWith("_OWNER"),
                    menuIds,
                    menuLabels: menuIds.map((id) => menuLabelMap.get(id) ?? `菜单 ${id}`),
                    assignments: assignmentMap.get(canonical) ?? [],
                    source: "custom",
                    kcMemberCount: matchedRole?.kcMemberCount ?? matchedRole?.memberCount ?? 0,
                });
            }
        });

        map.forEach((entry, canonical) => {
            entry.assignments = assignmentMap.get(canonical) ?? [];
            if (!entry.source) {
                entry.source = "服务端";
            }
            if (!entry.operations.length) {
                entry.operations = ["read"];
            }
            if (!entry.menuIds.length) {
                const menuIds = Array.from(new Set(roleMenuIndex.get(canonical) ?? [])).sort((a, b) => a - b);
                entry.menuIds = menuIds;
                entry.menuLabels = menuIds.map((id) => menuLabelMap.get(id) ?? `菜单 ${id}`);
            }
        });

        return Array.from(map.values()).sort((a, b) => a.displayName.localeCompare(b.displayName, "zh-CN"));
    }, [rolesData, customRoles, assignments, roleMenuIndex, menuLabelMap]);

    const handleCreateSubmitted = useCallback(() => {
        queryClient.invalidateQueries({ queryKey: ["admin", "roles"] });
        queryClient.invalidateQueries({ queryKey: ["admin", "custom-roles"] });
        queryClient.invalidateQueries({ queryKey: ["admin", "portal-menus"] });
    }, [queryClient]);

    const handleUpdateSubmitted = useCallback(() => {
        queryClient.invalidateQueries({ queryKey: ["admin", "roles"] });
        queryClient.invalidateQueries({ queryKey: ["admin", "portal-menus"] });
    }, [queryClient]);

    const handleDeleteSubmitted = useCallback(() => {
        queryClient.invalidateQueries({ queryKey: ["admin", "roles"] });
        queryClient.invalidateQueries({ queryKey: ["admin", "portal-menus"] });
    }, [queryClient]);

    const columns = useMemo<ColumnsType<RoleRow>>(() => {
        return [
            {
                title: "角色",
                dataIndex: "displayName",
                key: "name",
                width: 240,
                onCell: () => ({ style: { verticalAlign: "middle" } }),
                render: (_value, record) => {
                    const code = record.code || record.canonical;
                    const en = record.nameEn || "";
                    return (
                        <div title={en || undefined} className="flex min-w-0 flex-col">
                            <span className="truncate font-medium">{code}</span>
                            {/* <span className="truncate text-xs text-muted-foreground">{code}</span> */}
                        </div>
                    );
                },
            },
            {
                title: "角色名称",
                dataIndex: "displayName",
                key: "displayNameZh",
                width: 200,
                onCell: () => ({ style: { verticalAlign: "middle" } }),
                render: (_value, record) => {
                    const zh = (record.nameZh || record.displayName || "").trim();
                    const fallback = record.description ?? "";
                    const label = zh || fallback;
                    return label ? (
                        <span className="truncate block max-w-full" title={label}>
                            {label}
                        </span>
                    ) : (
                        <Text variant="body3" className="text-muted-foreground">
                            未填写
                        </Text>
                    );
                },
            },
            {
                title: "可见上限",
                key: "visibilityMax",
                width: 160,
                onCell: () => ({ style: { verticalAlign: "middle" } }),
                render: () => <Text variant="body3" className="text-muted-foreground">随人员密级</Text>,
            },
            {
                title: "描述",
                dataIndex: "description",
                key: "description",
                width: 260,
                onCell: () => ({ style: { verticalAlign: "middle" } }),
                render: (value: string | null | undefined) => {
                    const v = (value ?? "").trim();
                    return v ? (
                        <span className="truncate block max-w-full" title={v}>{v}</span>
                    ) : (
                        <Text variant="body3" className="text-muted-foreground">未填写</Text>
                    );
                },
            },
            // 隐藏操作权限列，角色默认仅具备读取权限
			{
				title: "绑定菜单",
				dataIndex: "menuLabels",
				key: "menus",
				width: 220,
				onCell: () => ({ style: { verticalAlign: "middle" } }),
				render: (menus: string[]) => {
					const list = menus ?? [];
					if (!list.length) {
						return <Text variant="body3" className="text-muted-foreground">未绑定</Text>;
					}
					const preview = list.slice(0, 3);
					const remaining = list.slice(3);
					return (
						<div className="flex items-center gap-1 overflow-hidden whitespace-nowrap">
							{preview.map((menu) => (
								<Badge key={menu} variant="outline" className="max-w-[140px] truncate">
									{menu}
								</Badge>
							))}
							{remaining.length > 0 ? (
								<Tooltip>
									<TooltipTrigger asChild>
										<Badge variant="secondary">+{remaining.length}</Badge>
									</TooltipTrigger>
									<TooltipContent className="max-w-sm">
										<div className="max-h-48 space-y-1 overflow-auto pr-1 text-xs">
											{remaining.map((menu) => (
												<div key={menu} className="truncate">{menu}</div>
											))}
										</div>
									</TooltipContent>
								</Tooltip>
							) : null}
						</div>
					);
				},
			},
			{
				title: "成员",
				key: "members",
				width: 220,
				onCell: () => ({ style: { verticalAlign: "middle" } }),
				render: (_value, record) => {
					const total = Math.max(record.kcMemberCount ?? 0, record.assignments.length);
					const preview = record.assignments.slice(0, 2);
					const remaining = record.assignments.slice(2);
					return (
						<div className="flex items-center gap-2 overflow-hidden whitespace-nowrap">
							<Badge variant="secondary">{total} 人</Badge>
							{preview.map((assignment) => (
								<Badge key={assignment.id} variant="outline" className="max-w-[140px] truncate">
									{assignment.displayName || assignment.username}
								</Badge>
							))}
							{remaining.length > 0 ? (
								<Tooltip>
									<TooltipTrigger asChild>
										<Badge variant="outline">+{remaining.length}</Badge>
									</TooltipTrigger>
									<TooltipContent className="max-w-sm">
										<div className="max-h-48 space-y-1 overflow-auto pr-1 text-xs">
											{remaining.map((assignment) => (
												<div key={assignment.id} className="truncate">
													{assignment.displayName || assignment.username}
												</div>
											))}
										</div>
									</TooltipContent>
								</Tooltip>
							) : null}
						</div>
					);
				},
			},
            {
                title: "操作",
                key: "actions",
                width: 200,
                fixed: "right",
                align: "right" as const,
                onCell: () => ({ style: { verticalAlign: "middle" } }),
                render: (_value, record) => {
                    const immutable = record.source === "builtin";
                    return (
                        <div className="flex flex-wrap gap-2 justify-end">
                            <Button size="sm" variant="outline" onClick={() => setMemberTarget(record)}>
                                成员
                            </Button>
                            <Button
                                size="sm"
                                variant="outline"
                                onClick={() => setEditTarget(record)}
                            >
                                编辑
                            </Button>
                            <Button
                                size="sm"
                                variant="destructive"
                                disabled={immutable}
                                onClick={() => setDeleteTarget(record)}
                            >
                                删除
                            </Button>
                        </div>
                    );
                },
            },
        ];
    }, []);

    const expandedRowRender = useCallback(
        (record: RoleRow) => {
            const members = record.assignments || [];
            return (
                <div className="grid gap-4 border-t border-muted pt-4 text-sm md:grid-cols-3">
                    <div className="space-y-2">
                        <Text variant="body3" className="text-muted-foreground">
                            角色信息
                        </Text>
                        <div className="space-y-1">
                            <div>编码：{record.code || record.canonical}</div>
                            <div>描述：{record.description || "未填写"}</div>
                            <div>操作权限：系统默认（读取 / 写入 / 导出）</div>
                        </div>
                    </div>
                    <div className="space-y-2">
                        <Text variant="body3" className="text-muted-foreground">
                            绑定菜单
                        </Text>
                        <div className="flex flex-wrap gap-1">
                            {record.menuLabels?.length ? (
                                record.menuLabels.map((menu) => (
                                    <Badge key={menu} variant="outline" className="max-w-[160px] truncate">
                                        {menu}
                                    </Badge>
                                ))
                            ) : (
                                <span className="text-muted-foreground">未绑定</span>
                            )}
                        </div>
                    </div>
                    <div className="space-y-2">
                        <Text variant="body3" className="text-muted-foreground">
                            成员列表
                        </Text>
                        <div className="flex flex-wrap gap-1">
                            {members.length ? (
                                members.map((member) => (
                                    <Badge key={member.id} variant="outline" className="max-w-[160px] truncate">
                                        {member.displayName || member.username}
                                    </Badge>
                                ))
                            ) : (
                                <span className="text-muted-foreground">暂无成员</span>
                            )}
                        </div>
                    </div>
                </div>
            );
        },
        []
    );

    return (
        <TooltipProvider>
            <div className="mx-auto w-full max-w-[1200px] space-y-6">
            <Card>
                <CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                    <div className="space-y-1">
                        <CardTitle>角色列表</CardTitle>
                        <Text variant="body3" className="text-muted-foreground">
                            展示可管理的角色、默认菜单绑定与当前成员，所有调整将通过审批流生效。
                        </Text>
                    </div>
                    <Button onClick={() => setCreateOpen(true)}>新增角色</Button>
                </CardHeader>
                <CardContent>
                    {isLoading ? (
                        <Text variant="body3" className="text-muted-foreground">数据加载中…</Text>
                    ) : hasError ? (
                        <Text variant="body3" className="text-destructive">加载角色数据失败，请稍后重试。</Text>
                    ) : roleRows.length === 0 ? (
                        <Text variant="body3" className="text-muted-foreground">暂无可显示的角色。</Text>
                    ) : (
                        <Table<RoleRow>
                            rowKey="key"
                            columns={columns}
                            dataSource={roleRows}
                            pagination={{
                                pageSize: 10,
                                showSizeChanger: true,
                                pageSizeOptions: [10, 20, 50, 100],
                                showQuickJumper: true,
                                showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
                            }}
                            size="small"
                            className="text-sm"
                            rowClassName={() => "text-sm"}
                            tableLayout="fixed"
                            scroll={{ x: 1400 }}
                            expandable={{
                                expandedRowRender,
                                expandRowByClick: true,
                                columnWidth: 48,
                            }}
                        />
                    )}
                </CardContent>
            </Card>

            <CreateRoleDialog
                open={createOpen}
                onOpenChange={setCreateOpen}
                onSubmitted={handleCreateSubmitted}
                menuOptions={menuOptions}
                menuRoleMap={menuRoleMap}
                menuParentMap={menuParentMap}
                menuChildrenMap={menuChildrenMap}
            />
            <UpdateRoleDialog
                target={editTarget}
                onClose={() => setEditTarget(null)}
                onSubmitted={handleUpdateSubmitted}
                menuOptions={menuOptions}
                menuRoleMap={menuRoleMap}
                menuParentMap={menuParentMap}
                menuChildrenMap={menuChildrenMap}
            />
            <DeleteRoleDialog
                target={deleteTarget}
                onClose={() => setDeleteTarget(null)}
                onSubmitted={handleDeleteSubmitted}
                menuRoleMap={menuRoleMap}
            />
            <MembersDialog target={memberTarget} onClose={() => setMemberTarget(null)} />
            </div>
        </TooltipProvider>
    );
}

interface CreateRoleDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onSubmitted: () => void;
    menuOptions: MenuOption[];
    menuRoleMap: Map<number, MenuOption>;
    menuParentMap: Map<number, number | null>;
    menuChildrenMap: Map<number, number[]>;
}

function CreateRoleDialog({ open, onOpenChange, onSubmitted, menuOptions, menuRoleMap, menuParentMap, menuChildrenMap }: CreateRoleDialogProps) {
    const [name, setName] = useState("");
    const [displayName, setDisplayName] = useState("");
    const [allowDesensitize, setAllowDesensitize] = useState(true);
    const [description, setDescription] = useState("");
    const [reason, setReason] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [selectedMenus, setSelectedMenus] = useState<Set<number>>(new Set());

    const resetState = useCallback(() => {
        setName("");
        setDisplayName("");
        setAllowDesensitize(true);
        setDescription("");
        setReason("");
        setSelectedMenus(new Set());
        setSubmitting(false);
    }, []);

    const getDescendants = (startId: number): number[] => {
        const acc: number[] = [];
        const stack: number[] = [startId];
        const visited = new Set<number>();
        while (stack.length) {
            const cur = stack.pop()!;
            if (visited.has(cur)) continue;
            visited.add(cur);
            const children = menuChildrenMap.get(cur) || [];
            for (const c of children) {
                acc.push(c);
                stack.push(c);
            }
        }
        return acc;
    };

    const getAncestors = (startId: number): number[] => {
        const list: number[] = [];
        let cur: number | null | undefined = menuParentMap.get(startId) ?? null;
        while (cur != null) {
            list.push(cur);
            cur = menuParentMap.get(cur) ?? null;
        }
        return list;
    };

    const toggleMenu = (id: number, checked: boolean) => {
        setSelectedMenus((prev) => {
            const next = new Set(prev);
            const descendants = getDescendants(id);
            if (checked) {
                const selfOpt = menuRoleMap.get(id);
                if (!selfOpt?.deleted) {
                    next.add(id);
                }
                descendants.forEach((d) => {
                    const opt = menuRoleMap.get(d);
                    if (!opt?.deleted) next.add(d);
                });
            } else {
                next.delete(id);
                descendants.forEach((d) => next.delete(d));
                // if no child selected for any ancestor, uncheck that ancestor
                const ancestors = getAncestors(id);
                for (const p of ancestors) {
                    const desc = getDescendants(p);
                    const anySelected = desc.some((d) => next.has(d));
                    if (!anySelected) next.delete(p);
                }
            }
            return next;
        });
    };

    const handleSubmit = async () => {
        const trimmedName = name.trim();
        if (!trimmedName) {
            toast.error("请输入角色 ID");
            return;
        }
        const trimmedDisplayName = displayName.trim();
        if (!trimmedDisplayName) {
            toast.error("请输入角色名称");
            return;
        }
        const trimmedDescription = description.trim();
        if (!trimmedDescription) {
            toast.error("请输入说明");
            return;
        }
        setSubmitting(true);
        try {
            const trimmedReason = reason.trim() || undefined;
            const payload = {
                name: trimmedName.toUpperCase(),
                scope: "DEPARTMENT" as const,
                operations: ["read"] as DataOperation[],
                allowDesensitizeJson: allowDesensitize,
                description: trimmedDescription,
                titleCn: trimmedDisplayName,
                nameZh: trimmedDisplayName,
                displayName: trimmedDisplayName,
                reason: trimmedReason,
            };
            const change = await adminApi.createCustomRole(payload);
            if (change?.id != null) {
                await adminApi.submitChangeRequest(change.id);
            }
            if (selectedMenus.size > 0) {
                try {
                    await submitMenuChangeRequests({
                        authority: toRoleAuthority(trimmedName),
                        desiredMenuIds: new Set(selectedMenus),
                        originalMenuIds: new Set(),
                        menuRoleMap,
                        reason: trimmedReason,
                    });
                } catch (error: any) {
                    toast.error(error?.message ?? "菜单绑定申请提交失败，请在审批中心确认状态");
                }
            }
            toast.success("角色创建申请已提交审批");
            onSubmitted();
            onOpenChange(false);
            resetState();
        } catch (error: any) {
            toast.error(error?.message ?? "提交失败，请稍后重试");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Dialog
            open={open}
            onOpenChange={(next) => {
                if (!next) {
                    onOpenChange(false);
                    resetState();
                } else {
                    onOpenChange(true);
                }
            }}
        >
            <DialogContent className="max-w-xl">
                <DialogHeader>
                    <DialogTitle>新增角色</DialogTitle>
                </DialogHeader>
                <div className="space-y-4 text-sm">
                    <div className="grid gap-4 md:grid-cols-2">
                        <div className="space-y-2">
                            <label htmlFor="role-code" className="font-medium">
                                角色 ID
                            </label>
                            <Input
                                id="role-code"
                                placeholder="如：DEPT_DATA_DEV"
                                value={name}
                                onChange={(event) => setName(event.target.value)}
                            />
                            <Text variant="body3" className="text-muted-foreground">
                                建议使用大写英文与下划线
                            </Text>
                        </div>
                        <div className="space-y-2">
                            <label htmlFor="role-display-name" className="font-medium">
                                角色名称
                            </label>
                            <Input
                                id="role-display-name"
                                placeholder="如：部门数据开发员"
                                value={displayName}
                                onChange={(event) => setDisplayName(event.target.value)}
                            />
                            <Text variant="body3" className="text-muted-foreground">
                                展示给平台和审批流程的名称
                            </Text>
                        </div>
                    </div>
                    <div className="space-y-2">
                        <label htmlFor="role-description" className="font-medium">
                            描述
                        </label>
                        <Textarea
                            id="role-description"
                            rows={2}
                            placeholder="补充角色用途，便于审批记录"
                            value={description}
                            onChange={(event) => setDescription(event.target.value)}
                        />
                    </div>
                    <div className="space-y-2">
                        <span className="font-medium">操作权限</span>
                        <Text variant="body3" className="text-muted-foreground">
                            系统默认启用读取 / 写入 / 导出，无需额外勾选
                        </Text>
                    </div>
                    <div className="space-y-2">
                        <span className="font-medium">访问菜单</span>
                        {menuOptions.length === 0 ? (
                            <Text variant="body3" className="text-muted-foreground">
                                暂无可绑定的菜单，请稍后再试。
                            </Text>
                        ) : (
                            <div className="max-h-60 space-y-1 overflow-y-auto rounded-md border p-3">
                                {menuOptions.map((option) => {
                                    const disabled = Boolean(option.deleted);
                                    return (
                                        <label
                                            key={option.id}
                                            className={`flex items-center gap-2 text-sm ${disabled ? "text-muted-foreground cursor-not-allowed" : ""}`}
                                            style={{ paddingLeft: option.depth * 16 }}
                                        >
                                            <Checkbox
                                                checked={selectedMenus.has(option.id)}
                                                onCheckedChange={(value) => toggleMenu(option.id, value === true)}
                                                disabled={disabled}
                                            />
                                            <span>{option.label}</span>
                                        </label>
                                    );
                                })}
                            </div>
                        )}
                        <Text variant="body3" className="text-muted-foreground">
                            审批通过后，所选菜单将自动纳入该角色的可访问范围。
                        </Text>
                    </div>
                    
                    <div className="space-y-2">
                        <label htmlFor="role-reason" className="font-medium">
                            审批备注（可选）
                        </label>
                        <Textarea
                            id="role-reason"
                            rows={2}
                            placeholder="填写申请背景或审批说明"
                            value={reason}
                            onChange={(event) => setReason(event.target.value)}
                        />
                    </div>
                </div>
                <DialogFooter className="flex justify-end gap-2">
                    <Button variant="outline" onClick={() => onOpenChange(false)} disabled={submitting}>
                        取消
                    </Button>
                    <Button onClick={handleSubmit} disabled={submitting}>
                        提交审批
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

interface UpdateRoleDialogProps {
    target: RoleRow | null;
    onClose: () => void;
    onSubmitted: () => void;
    menuOptions: MenuOption[];
    menuRoleMap: Map<number, MenuOption>;
    menuParentMap: Map<number, number | null>;
    menuChildrenMap: Map<number, number[]>;
}

function UpdateRoleDialog({ target, onClose, onSubmitted, menuOptions, menuRoleMap, menuParentMap, menuChildrenMap }: UpdateRoleDialogProps) {
    const [scope, setScope] = useState<"DEPARTMENT" | "INSTITUTE">("DEPARTMENT");
    const [description, setDescription] = useState("");
    const [reason, setReason] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [selectedMenus, setSelectedMenus] = useState<Set<number>>(new Set());

    useEffect(() => {
        if (!target) {
            return;
        }
        setScope(target.scope ?? "DEPARTMENT");
        setDescription(target.description ?? "");
        setReason("");
        setSelectedMenus(new Set(target.menuIds));
        setSubmitting(false);
    }, [target]);

    const getDescendants = (startId: number): number[] => {
        const acc: number[] = [];
        const stack: number[] = [startId];
        const visited = new Set<number>();
        while (stack.length) {
            const cur = stack.pop()!;
            if (visited.has(cur)) continue;
            visited.add(cur);
            const children = menuChildrenMap.get(cur) || [];
            for (const c of children) {
                acc.push(c);
                stack.push(c);
            }
        }
        return acc;
    };

    const getAncestors = (startId: number): number[] => {
        const list: number[] = [];
        let cur: number | null | undefined = menuParentMap.get(startId) ?? null;
        while (cur != null) {
            list.push(cur);
            cur = menuParentMap.get(cur) ?? null;
        }
        return list;
    };

    const toggleMenu = (id: number, checked: boolean) => {
        setSelectedMenus((prev) => {
            const next = new Set(prev);
            const descendants = getDescendants(id);
            if (checked) {
                const selfOpt = menuRoleMap.get(id);
                if (!selfOpt?.deleted) {
                    next.add(id);
                }
                descendants.forEach((d) => {
                    const opt = menuRoleMap.get(d);
                    if (!opt?.deleted) next.add(d);
                });
            } else {
                next.delete(id);
                descendants.forEach((d) => next.delete(d));
                const ancestors = getAncestors(id);
                for (const p of ancestors) {
                    const desc = getDescendants(p);
                    const anySelected = desc.some((d) => next.has(d));
                    if (!anySelected) next.delete(p);
                }
            }
            return next;
        });
    };

    const handleSubmit = async () => {
        if (!target) {
            return;
        }
        const trimmedReason = reason.trim() || undefined;
        const desiredMenus = new Set(selectedMenus);
        const originalMenus = new Set(target.menuIds);
        const menuChanged = !setsEqual(desiredMenus, originalMenus);
        const scopeChanged = (target.scope ?? "DEPARTMENT") !== scope;
        const descriptionChanged = (target.description ?? "") !== description.trim();
        const nextOperations = target.operations && target.operations.length ? target.operations : ["read"];

        if (!menuChanged && !scopeChanged && !descriptionChanged) {
            toast.info("未检测到变更，无需提交审批");
            return;
        }
        setSubmitting(true);
        try {
            if (scopeChanged || descriptionChanged) {
                const roleName = toRoleName(target.authority);
                const resourceIdentifier = roleName || target.authority;
                const payload = {
                    resourceType: "ROLE",
                    action: "UPDATE",
                    resourceId: resourceIdentifier,
                    payloadJson: JSON.stringify({
                        id: target.id,
                        name: roleName,
                        scope,
                        operations: nextOperations,
                        description: description.trim() || undefined,
                    }),
                    diffJson: JSON.stringify({
                        before: {
                            id: target.id ?? null,
                            name: roleName,
                            scope: target.scope ?? null,
                            operations: target.operations ?? [],
                            description: target.description ?? null,
                        },
                        after: {
                            id: target.id ?? null,
                            name: roleName,
                            scope,
                            operations: nextOperations,
                            description: description.trim() || null,
                        },
                    }),
                    reason: trimmedReason,
                };
                const change = await adminApi.createChangeRequest(payload);
                await adminApi.submitChangeRequest(change.id);
            }
            if (menuChanged) {
                await submitMenuChangeRequests({
                    authority: toRoleAuthority(target.authority),
                    desiredMenuIds: desiredMenus,
                    originalMenuIds: originalMenus,
                    menuRoleMap,
                    reason: trimmedReason,
                });
            }
            toast.success("更新申请已提交审批");
            onSubmitted();
            onClose();
        } catch (error: any) {
            toast.error(error?.message ?? "提交失败，请稍后重试");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Dialog open={Boolean(target)} onOpenChange={(open) => (!open ? onClose() : null)}>
            <DialogContent className="max-w-xl">
                <DialogHeader>
                    <DialogTitle>编辑角色</DialogTitle>
                </DialogHeader>
                {target ? (
                    <div className="space-y-4 text-sm">
                        <div className="space-y-1">
                            <Text variant="body3" className="font-medium">
                                角色标识
                            </Text>
                            <Text variant="body3" className="text-muted-foreground">
                                {target.authority}
                            </Text>
                        </div>
                        <div className="grid gap-4 md:grid-cols-2">
                            <div className="space-y-2">
                                <label className="font-medium" htmlFor="edit-scope">
                                    作用域
                                </label>
                                <Select value={scope} onValueChange={(value) => setScope(value as "DEPARTMENT" | "INSTITUTE")}> 
                                    <SelectTrigger id="edit-scope">
                                        <SelectValue placeholder="请选择作用域" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="DEPARTMENT">部门</SelectItem>
                                        <SelectItem value="INSTITUTE">全所共享区</SelectItem>
                                    </SelectContent>
                                </Select>
                            </div>
                            {/* <div className="space-y-2">
                                <span className="font-medium">操作权限</span>
                                <div className="text-sm text-muted-foreground">
                                    {target.operations?.length
                                        ? target.operations.map((op) => OPERATION_LABELS[op]).join("、")
                                        : OPERATION_LABELS.read}
                                </div>
                            </div> */}
                        </div>
                        <div className="space-y-2">
                            <label htmlFor="edit-description" className="font-medium">
                                描述
                            </label>
                            <Textarea
                                id="edit-description"
                                rows={3}
                                placeholder="更新角色说明"
                                value={description}
                                onChange={(event) => setDescription(event.target.value)}
                            />
                        </div>
                        <div className="space-y-2">
                            <label htmlFor="edit-reason" className="font-medium">
                                审批备注（可选）
                            </label>
                            <Textarea
                                id="edit-reason"
                                rows={2}
                                placeholder="补充审批说明"
                                value={reason}
                                onChange={(event) => setReason(event.target.value)}
                            />
                        </div>
                        <div className="space-y-2">
                            <span className="font-medium">绑定菜单</span>
                            {menuOptions.length === 0 ? (
                                <Text variant="body3" className="text-muted-foreground">
                                    暂无可配置的菜单。
                                </Text>
                            ) : (
                                <div className="max-h-60 space-y-1 overflow-y-auto rounded-md border p-3">
                                    {menuOptions.map((option) => {
                                        const disabled = Boolean(option.deleted);
                                        return (
                                            <label
                                                key={option.id}
                                                className={`flex items-center gap-2 text-sm ${disabled ? "text-muted-foreground cursor-not-allowed" : ""}`}
                                                style={{ paddingLeft: option.depth * 16 }}
                                            >
                                                <Checkbox
                                                    checked={selectedMenus.has(option.id)}
                                                    onCheckedChange={(value) => toggleMenu(option.id, value === true)}
                                                    disabled={disabled}
                                                />
                                                <span>{option.label}</span>
                                            </label>
                                        );
                                    })}
                                </div>
                            )}
                            <Text variant="body3" className="text-muted-foreground">
                                提交审批后，菜单可见性会按最新配置下发。
                            </Text>
                        </div>
                    </div>
                ) : null}
                <DialogFooter className="flex justify-end gap-2">
                    <Button variant="outline" onClick={onClose} disabled={submitting}>
                        取消
                    </Button>
                    <Button onClick={handleSubmit} disabled={submitting || !target}>
                        提交审批
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

interface DeleteRoleDialogProps {
    target: RoleRow | null;
    onClose: () => void;
    onSubmitted: () => void;
    menuRoleMap: Map<number, MenuOption>;
}

function DeleteRoleDialog({ target, onClose, onSubmitted, menuRoleMap }: DeleteRoleDialogProps) {
    const [reason, setReason] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [checking, setChecking] = useState(false);
    const [precheck, setPrecheck] = useState<Record<string, any> | null>(null);

    useEffect(() => {
        setReason("");
        setSubmitting(false);
        setPrecheck(null);
        setChecking(false);
    }, [target]);

    useEffect(() => {
        let cancelled = false;
        async function load() {
            const name = target?.authority ? toRoleName(target.authority) : "";
            if (!name) return;
            setChecking(true);
            try {
                const data = await adminApi.getRolePreDeleteCheck(name);
                if (!cancelled) setPrecheck(data || null);
            } catch (e) {
                if (!cancelled) setPrecheck(null);
            } finally {
                if (!cancelled) setChecking(false);
            }
        }
        load();
        return () => {
            cancelled = true;
        };
    }, [target?.authority]);

    const handleSubmit = async () => {
        if (!target) {
            return;
        }
        setSubmitting(true);
        try {
            const trimmedReason = reason.trim() || undefined;
            const roleName = toRoleName(target.authority);
            const payload = {
                resourceType: "ROLE",
                action: "DELETE",
                resourceId: roleName,
                payloadJson: JSON.stringify({ id: target.id, name: roleName }),
                reason: trimmedReason,
            };
            const change = await adminApi.createChangeRequest(payload);
            await adminApi.submitChangeRequest(change.id);
            if (target.menuIds.length) {
                await submitMenuChangeRequests({
                    authority: toRoleAuthority(target.authority),
                    desiredMenuIds: new Set(),
                    originalMenuIds: new Set(target.menuIds),
                    menuRoleMap,
                    reason: trimmedReason,
                });
            }
            toast.success("删除申请已提交审批");
            onSubmitted();
            onClose();
        } catch (error: any) {
            toast.error(error?.message ?? "提交失败，请稍后重试");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Dialog open={Boolean(target)} onOpenChange={(open) => (!open ? onClose() : null)}>
            <DialogContent className="max-w-lg">
                <DialogHeader>
                    <DialogTitle>删除角色</DialogTitle>
                </DialogHeader>
                {target ? (
                    <div className="space-y-4 text-sm">
                        <div className="space-y-1">
                            <Text variant="body3">
                                即将提交删除角色 <span className="font-semibold">{target.authority}</span> 的审批请求。该操作将在审批通过后由授权管理员执行。
                            </Text>
                            {checking ? (
                                <Text variant="body3" className="text-muted-foreground">预检中…</Text>
                            ) : (
                                <div className="flex flex-wrap items-center gap-2 text-muted-foreground">
                                    <Badge variant={precheck?.reserved ? "destructive" : "secondary"}>
                                        {precheck?.reserved ? "内置角色（不可删除）" : "可删除"}
                                    </Badge>
                                    <Badge variant="secondary">Keycloak成员 {precheck?.kcMemberCount ?? 0}</Badge>
                                    <Badge variant="secondary">菜单绑定 {precheck?.menuBindings ?? 0}</Badge>
                                    {typeof precheck?.assignments === 'number' ? (
                                        <Badge variant="secondary">业务授权 {precheck?.assignments}</Badge>
                                    ) : null}
                                </div>
                            )}
                        </div>
                        <div className="space-y-2">
                            <label htmlFor="delete-reason" className="font-medium">
                                审批备注（可选）
                            </label>
                            <Textarea
                                id="delete-reason"
                                rows={2}
                                placeholder="说明删除原因，便于审批通过"
                                value={reason}
                                onChange={(event) => setReason(event.target.value)}
                            />
                        </div>
                    </div>
                ) : null}
                <DialogFooter className="flex justify-end gap-2">
                    <Button variant="outline" onClick={onClose} disabled={submitting}>
                        取消
                    </Button>
                    <Button variant="destructive" onClick={handleSubmit} disabled={submitting || !target || precheck?.reserved === true}>
                        提交审批
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

interface MembersDialogProps {
    target: RoleRow | null;
    onClose: () => void;
}

function MembersDialog({ target, onClose }: MembersDialogProps) {
    const [kcMembers, setKcMembers] = useState<Array<{ id: string; username: string; fullName?: string }>>([]);
    const [loading, setLoading] = useState(false);
    useEffect(() => {
        let cancelled = false;
        async function load() {
            if (!target) return;
            setLoading(true);
            try {
                // Fetch Keycloak realm role members on demand
                const authority = target.authority || target.displayName;
                if (authority) {
                    const users = await (await import("@/api/services/keycloakService")).default.role.getRoleUsers(authority);
                    if (!cancelled) setKcMembers(users);
                }
            } catch (e) {
                console.warn("Failed to load role users from Keycloak", e);
                if (!cancelled) setKcMembers([]);
            } finally {
                if (!cancelled) setLoading(false);
            }
        }
        load();
        return () => {
            cancelled = true;
        };
    }, [target?.authority, target?.displayName]);

    // Merge AdminRoleAssignments (data assignments) and Keycloak realm role members
    const merged: Array<{ id?: number; username: string; displayName?: string; source: string }> = useMemo(() => {
        const list: Array<{ id?: number; username: string; displayName?: string; source: string }> = [];
        const seen = new Set<string>();
        (target?.assignments ?? []).forEach((a) => {
            list.push({ id: a.id, username: a.username, displayName: a.displayName, source: "assignment" });
            seen.add(a.username);
        });
        kcMembers.forEach((u) => {
            const uname = u.username || u.id;
            if (!seen.has(uname)) {
                list.push({ username: uname, displayName: u.fullName || u.username, source: "keycloak" });
            }
        });
        return list;
    }, [target?.assignments, kcMembers]);

    const total = merged.length;
    return (
        <Dialog open={Boolean(target)} onOpenChange={(open) => (!open ? onClose() : null)}>
            <DialogContent className="max-w-lg">
                <DialogHeader>
                    <DialogTitle>角色成员</DialogTitle>
                    {target ? (
                        <Text variant="body3" className="text-muted-foreground">
                            角色 {target.displayName} 当前共 {total}{loading ? "…" : ""} 名成员。
                        </Text>
                    ) : null}
                </DialogHeader>
                <div className="max-h-[360px] space-y-3 overflow-y-auto">
                    {target && !loading && total === 0 ? (
                        <Text variant="body3" className="text-muted-foreground">
                            尚未绑定用户。可通过“新增角色授权”或为用户分配该系统角色维护成员。
                        </Text>
                    ) : null}
                    {loading ? (
                        <Text variant="body3" className="text-muted-foreground">加载中…</Text>
                    ) : null}
                    {merged.map((m, idx) => (
                        <div key={`${m.source}-${m.id ?? m.username}-${idx}`} className="rounded-lg border px-4 py-3 text-sm">
                            <div className="flex flex-wrap items-center justify-between gap-2">
                                <div className="flex flex-col">
                                    <span className="font-medium">{m.displayName || m.username}</span>
                                    <Text variant="body3" className="text-muted-foreground">{m.username}</Text>
                                </div>
                                <Badge variant={m.source === "assignment" ? "outline" : "secondary"}>
                                    {m.source === "assignment" ? "授权记录" : (GLOBAL_CONFIG.hideKeycloakBranding ? "系统角色" : "Keycloak 角色")}
                                </Badge>
                            </div>
                        </div>
                    ))}
                </div>
                <DialogFooter>
                    <Button variant="outline" onClick={onClose}>
                        关闭
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

function canonicalRole(value: string | null | undefined): string {
    if (!value) {
        return "";
    }
    return value.trim().toUpperCase().replace(/^ROLE[_-]?/, "").replace(/_/g, "");
}

function toRoleName(value: string | null | undefined): string {
    if (!value) return "";
    let upper = value.trim().toUpperCase();
    if (upper.startsWith("ROLE_")) {
        upper = upper.substring(5);
    } else if (upper.startsWith("ROLE-")) {
        upper = upper.substring(5);
    }
    // keep underscores; normalize other non-word to underscore and collapse
    upper = upper.replace(/[^A-Z0-9_]/g, "_").replace(/_+/g, "_");
    return upper;
}

function buildMenuCatalog(collection: PortalMenuCollection | undefined): {
    options: MenuOption[];
    roleToMenuIds: Map<string, number[]>;
    menuRoleMap: Map<number, MenuOption>;
    parentMap: Map<number, number | null>;
    childrenMap: Map<number, number[]>;
} {
    const options: MenuOption[] = [];
    const roleToMenuIds = new Map<string, number[]>();
    const menuRoleMap = new Map<number, MenuOption>();
    const parentMap = new Map<number, number | null>();
    const childrenMap = new Map<number, number[]>();

    const ensureChild = (parentId: number | null, childId: number) => {
        if (parentId == null) return;
        const list = childrenMap.get(parentId) ?? [];
        list.push(childId);
        childrenMap.set(parentId, list);
    };

    const isFoundation = (item: PortalMenuItem): boolean => {
        try {
            if (!item) return false;
            if (item.id === 2670) return true;
            const p = (item.path || "").toLowerCase();
            if (p === "/foundation" || p.startsWith("/foundation/")) return true;
            const m = (item.metadata || "").toLowerCase();
            if (m.includes('"sectionkey":"foundation"')) return true;
        } catch {}
        return false;
    };

    const visit = (items: PortalMenuItem[] | undefined, depth: number, ancestors: string[], parentId: number | null) => {
        if (!items) {
            return;
        }
        items.forEach((item) => {
            const labelText = item.displayName || item.name || item.path || `菜单 ${item.id ?? ""}`;
            const chain = [...ancestors, labelText];
            const normalizedRoles = (item.allowedRoles ?? [])
                .map((role) => toRoleAuthority(role))
                .filter((role) => role.length > 0);
            const canonicalRoles = normalizedRoles.map(canonicalRole).filter((role) => role.length > 0);

            if (item.id != null) {
                const option: MenuOption = {
                    id: item.id,
                    label: chain.join(" / "),
                    depth,
                    rawRoles: normalizedRoles,
                    canonicalRoles,
                    deleted: Boolean(item.deleted) || isFoundation(item),
                };
                options.push(option);
                menuRoleMap.set(option.id, option);
                parentMap.set(option.id, parentId);
                ensureChild(parentId, option.id);
                canonicalRoles.forEach((role) => {
                    const list = roleToMenuIds.get(role) ?? [];
                    list.push(option.id);
                    roleToMenuIds.set(role, list);
                });
            }

            if (item.children?.length) {
                visit(item.children, depth + 1, chain, item.id ?? parentId);
            }
        });
    };

    visit(collection?.allMenus ?? collection?.menus, 0, [], null);

    roleToMenuIds.forEach((ids, role) => {
        const unique = Array.from(new Set(ids));
        unique.sort((a, b) => a - b);
        roleToMenuIds.set(role, unique);
    });

    // Normalize children lists
    Array.from(childrenMap.keys()).forEach((pid) => {
        const arr = childrenMap.get(pid) ?? [];
        const unique = Array.from(new Set(arr));
        unique.sort((a, b) => a - b);
        childrenMap.set(pid, unique);
    });

    return { options, roleToMenuIds, menuRoleMap, parentMap, childrenMap };
}

async function submitMenuChangeRequests(params: {
    authority: string;
    desiredMenuIds: Set<number>;
    originalMenuIds: Set<number>;
    menuRoleMap: Map<number, MenuOption>;
    reason?: string;
}) {
    const { authority, desiredMenuIds, originalMenuIds, menuRoleMap, reason } = params;
    const affectedIds = new Set<number>();
    desiredMenuIds.forEach((id) => affectedIds.add(id));
    originalMenuIds.forEach((id) => affectedIds.add(id));

    // Build batched updates: only include items where the role binding actually changes
    const updates: { id: number; allowedRoles: string[]; _beforeAllowedRoles: string[] }[] = [];
    affectedIds.forEach((menuId) => {
        const option = menuRoleMap.get(menuId);
        if (!option) return;
        // Skip disabled menus: cannot be assigned/unassigned while disabled
        if (option.deleted) return;
        const beforeAllowedRoles = option.rawRoles;
        const existingRoles = new Set(beforeAllowedRoles);
        const shouldHave = desiredMenuIds.has(menuId);
        const currentlyHas = existingRoles.has(authority);
        if (shouldHave === currentlyHas) return;
        if (shouldHave) existingRoles.add(authority);
        else existingRoles.delete(authority);
        const allowedRoles = Array.from(existingRoles).sort();
        updates.push({ id: menuId, allowedRoles, _beforeAllowedRoles: beforeAllowedRoles });
    });

    if (updates.length === 0) return;

    const payload = {
        resourceType: "PORTAL_MENU",
        action: "BATCH_UPDATE",
        resourceId: undefined,
        payloadJson: JSON.stringify({ updates: updates.map(({ id, allowedRoles }) => ({ id, allowedRoles })) }),
        diffJson: JSON.stringify({
            items: updates.map((u) => ({ id: u.id, before: { allowedRoles: u._beforeAllowedRoles }, after: { allowedRoles: u.allowedRoles } })),
        }),
        category: "PORTAL_MENU",
        reason,
    } as const;

    const change = await adminApi.createChangeRequest(payload as any);
    await adminApi.submitChangeRequest(change.id);

    // Optimistically update local cache for immediate feedback
    updates.forEach(({ id, allowedRoles }) => {
        const option = menuRoleMap.get(id);
        if (!option) return;
        option.rawRoles = allowedRoles;
        option.canonicalRoles = allowedRoles.map(canonicalRole).filter((role) => role.length > 0);
    });
}

function setsEqual<T>(a: Set<T>, b: Set<T>): boolean {
    if (a.size !== b.size) {
        return false;
    }
    for (const item of a) {
        if (!b.has(item)) {
            return false;
        }
    }
    return true;
}

function toRoleAuthority(value: string | null | undefined): string {
    if (!value) {
        return "";
    }
    let normalized = value.trim().toUpperCase();
    if (normalized.startsWith("ROLE_")) {
        normalized = normalized.substring(5);
    } else if (normalized.startsWith("ROLE-")) {
        normalized = normalized.substring(5);
    }
    normalized = normalized.replace(/[^A-Z0-9_]/g, "_").replace(/_+/g, "_");
    if (!normalized) {
        return "";
    }
    return `ROLE_${normalized}`;
}

function groupAssignments(assignments: AdminRoleAssignment[] | undefined): Map<string, AdminRoleAssignment[]> {
    const map = new Map<string, AdminRoleAssignment[]>();
    if (!assignments) {
        return map;
    }
    assignments.forEach((assignment) => {
        const canonical = canonicalRole(assignment.role);
        if (!canonical) {
            return;
        }
        if (!map.has(canonical)) {
            map.set(canonical, []);
        }
        map.get(canonical)!.push(assignment);
    });
    map.forEach((list) => {
        list.sort((a, b) => {
            const left = a.displayName || a.username;
            const right = b.displayName || b.username;
            return left.localeCompare(right, "zh-CN");
        });
    });
    return map;
}
