import { useCallback, useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { Link } from "react-router";
import { adminApi } from "@/admin/api/adminApi";
import type { AdminCustomRole, AdminRoleDetail, ChangeRequest, CreateCustomRolePayload, PortalMenuCollection, PortalMenuItem } from "@/admin/types";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Text } from "@/ui/typography";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/ui/tooltip";
import { Textarea } from "@/ui/textarea";
import { Alert, AlertDescription, AlertTitle } from "@/ui/alert";
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
    zone?: "DEPT" | "INST";
    description?: string | null;
    scope?: "DEPARTMENT" | "INSTITUTE";
    canManage?: boolean;
    menuIds: number[];
    menuLabels: string[];
    source?: string;
    memberCount?: number;
}

interface MenuOption {
    id: number;
    label: string;
    depth: number;
    rawRoles: string[];
    canonicalRoles: string[];
    deleted: boolean;
    disabledReason?: string;
    path?: string;
}

export default function RoleManagementView() {
    const queryClient = useQueryClient();
    const {
        data: rolesData,
        isLoading: rolesLoading,
        isError: rolesError,
    } = useQuery<AdminRoleDetail[]>({
        queryKey: ["admin", "roles"],
        queryFn: () => adminApi.getAdminRoles(),
    });
    const {
        data: customRoles,
        isLoading: customLoading,
        isError: customError,
    } = useQuery<AdminCustomRole[]>({
        queryKey: ["admin", "custom-roles"],
        queryFn: () => adminApi.getCustomRoles(),
    });
    const {
        data: portalMenus,
        isLoading: menuLoading,
        isError: menuError,
    } = useQuery<PortalMenuCollection>({
        queryKey: ["admin", "portal-menus"],
        queryFn: () => adminApi.getPortalMenus(),
    });

    const [createOpen, setCreateOpen] = useState(false);
    const [deleteTarget, setDeleteTarget] = useState<RoleRow | null>(null);

    const isLoading = rolesLoading || customLoading || menuLoading;
    const hasError = rolesError || customError || menuError;

    const menuCatalog = useMemo(() => buildMenuCatalog(portalMenus), [portalMenus]);
    const menuOptions = menuCatalog.options;
    const roleMenuIndex = menuCatalog.roleToMenuIds;
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
            const resolvedScope =
                role.scope ??
                ((role as any).zone === "INST"
                    ? "INSTITUTE"
                    : (role as any).zone === "DEPT"
                        ? "DEPARTMENT"
                        : undefined);
            const entry: RoleRow = {
                id: role.id,
                key: role.id?.toString() ?? authorityCode,
                authority: authorityCode,
                displayName: role.displayName || role.name,
                canonical,
                code: role.roleId || (role as any).code || canonical,
                zone: (role as any).zone,
                description: role.description,
                scope: resolvedScope ?? undefined,
                canManage: (role as any).canManage ?? canonical.endsWith("_OWNER"),
                menuIds,
                menuLabels: menuIds.map((id) => menuLabelMap.get(id) ?? `菜单 ${id}`),
                source: role.source ?? undefined,
                memberCount: role.memberCount ?? 0,
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
            const normalizedScope =
                role.scope ??
                ((role as any).zone === "INST"
                    ? "INSTITUTE"
                    : (role as any).zone === "DEPT"
                        ? "DEPARTMENT"
                        : undefined);
            const existing = map.get(canonical);
            const displayLabel = (role.displayName || role.name || "").toString().trim() || canonical;
            if (existing) {
                existing.description = existing.description ?? role.description;
                existing.scope = existing.scope ?? normalizedScope;
                existing.source = existing.source ?? "custom";
                if (!existing.displayName?.trim()) {
                    existing.displayName = displayLabel;
                }
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
                    displayName: displayLabel,
                    canonical,
                    description: role.description,
                    scope: normalizedScope ?? undefined,
                    canManage: canonical.endsWith("_OWNER"),
                    menuIds,
                    menuLabels: menuIds.map((id) => menuLabelMap.get(id) ?? `菜单 ${id}`),
                    source: "custom",
                    memberCount: matchedRole?.memberCount ?? 0,
                });
            }
        });

        map.forEach((entry, canonical) => {
            if (!entry.source) {
                entry.source = "服务端";
            }
            if (!entry.scope && entry.zone) {
                entry.scope = entry.zone === "INST" ? "INSTITUTE" : entry.zone === "DEPT" ? "DEPARTMENT" : undefined;
            }
            if (!entry.menuIds.length) {
                const menuIds = Array.from(new Set(roleMenuIndex.get(canonical) ?? [])).sort((a, b) => a - b);
                entry.menuIds = menuIds;
                entry.menuLabels = menuIds.map((id) => menuLabelMap.get(id) ?? `菜单 ${id}`);
            }
        });

        return Array.from(map.values()).sort((a, b) => a.displayName.localeCompare(b.displayName, "zh-CN"));
    }, [rolesData, customRoles, roleMenuIndex, menuLabelMap]);

    const handleCreateSubmitted = useCallback(() => {
        queryClient.invalidateQueries({ queryKey: ["admin", "roles"] });
        queryClient.invalidateQueries({ queryKey: ["admin", "custom-roles"] });
        queryClient.invalidateQueries({ queryKey: ["admin", "portal-menus"] });
    }, [queryClient]);

    const handleDeleteSubmitted = useCallback(() => {
        queryClient.invalidateQueries({ queryKey: ["admin", "roles"] });
        queryClient.invalidateQueries({ queryKey: ["admin", "portal-menus"] });
        queryClient.invalidateQueries({ queryKey: ["admin", "role-change-pending"] });
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
                    const tooltip = record.displayName || record.description || code;
                    return (
                        <div title={tooltip || undefined} className="flex min-w-0 flex-col">
                            <span className="truncate font-medium">{code}</span>
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
                    const label = (record.displayName || record.description || "").trim();
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
                title: "所属域",
                dataIndex: "scope",
                key: "scope",
                width: 160,
                onCell: () => ({ style: { verticalAlign: "middle" } }),
                render: (_value, record) => {
                    const scope = record.scope ?? (record.zone === "INST" ? "INSTITUTE" : record.zone === "DEPT" ? "DEPARTMENT" : undefined);
                    const label =
                        scope === "INSTITUTE"
                            ? "全所共享域"
                            : scope === "DEPARTMENT"
                                ? "部门域"
                                : "未配置";
                    return <span className="truncate block max-w-full">{label}</span>;
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
                    const total = record.memberCount ?? 0;
                    return (
                        <div className="flex items-center gap-2 overflow-hidden whitespace-nowrap">
                            <Badge variant="secondary">{total} 人</Badge>
                            <Text variant="body3" className="text-muted-foreground">
                                详情页可管理成员
                            </Text>
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
                    const roleSlug = encodeURIComponent(toRoleName(record.authority));
                    return (
                        <div className="flex flex-wrap gap-2 justify-end">
                            <Button size="sm" variant="outline" asChild>
                                <Link to={`/admin/roles/${roleSlug}`}>详情</Link>
                            </Button>
                            <Button size="sm" variant="outline" asChild>
                                <Link to={`/admin/roles/${roleSlug}/edit`}>编辑</Link>
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
            return (
                <div className="grid gap-4 border-t border-muted pt-4 text-sm md:grid-cols-3">
                    <div className="space-y-2">
                        <Text variant="body3" className="text-muted-foreground">
                            角色信息
                        </Text>
                        <div className="space-y-1">
                            <div>编码：{record.code || record.canonical}</div>
                            <div>描述：{record.description || "未填写"}</div>
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
                            成员管理
                        </Text>
                        <div className="rounded-md bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
                            成员请在“详情/编辑”页面通过审批流程管理，可按部门筛选人员。
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
                <div className="flex items-center justify-center gap-2 rounded-md border border-dashed border-red-200 bg-red-50 px-4 py-3 text-center text-sm font-medium text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200">
                    <Icon icon="mdi:star" className="h-5 w-5 text-red-500" />
                    <span className="text-center">非密模块禁止处理涉密数据</span>
                </div>
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
                                scroll={{ x: 1500 }}
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
            />
            <DeleteRoleDialog
                target={deleteTarget}
                onClose={() => setDeleteTarget(null)}
                onSubmitted={handleDeleteSubmitted}
            />
            </div>
        </TooltipProvider>
    );
}

interface CreateRoleDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onSubmitted: () => void;
}

function CreateRoleDialog({ open, onOpenChange, onSubmitted }: CreateRoleDialogProps) {
    const [name, setName] = useState("");
    const [displayName, setDisplayName] = useState("");
    const [scope, setScope] = useState<"DEPARTMENT" | "INSTITUTE" | undefined>(undefined);
    const [description, setDescription] = useState("");
    const [reason, setReason] = useState("");
    const [submitting, setSubmitting] = useState(false);

    const resetState = useCallback(() => {
        setName("");
        setDisplayName("");
        setScope(undefined);
        setDescription("");
        setReason("");
        setSubmitting(false);
    }, []);

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
        if (!scope) {
            toast.error("请选择所属域");
            return;
        }
        setSubmitting(true);
        try {
            const trimmedReason = reason.trim();
            const payload: CreateCustomRolePayload = {
                name: trimmedName.toUpperCase(),
                scope,
                description: trimmedDescription,
                displayName: trimmedDisplayName,
            };
            if (trimmedReason) {
                payload.reason = trimmedReason;
            }
            const change = await adminApi.createCustomRole(payload);
            if (change?.id != null) {
                await adminApi.submitChangeRequest(change.id);
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
                    <div className="flex items-center justify-center gap-2 rounded-md border border-dashed border-red-200 bg-red-50 px-4 py-3 text-center text-sm font-medium text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200">
                        <Icon icon="mdi:star" className="h-5 w-5 text-red-500" />
                        <span className="text-center">非密模块禁止处理涉密数据</span>
                    </div>
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
                        <div className="space-y-2 md:col-span-2">
                            <label htmlFor="role-scope" className="font-medium">
                                所属域
                            </label>
                            <Select value={scope} onValueChange={(value) => setScope(value as "DEPARTMENT" | "INSTITUTE")}>
                                <SelectTrigger id="role-scope">
                                    <SelectValue placeholder="请选择所属域" />
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="DEPARTMENT">部门域</SelectItem>
                                    <SelectItem value="INSTITUTE">全所共享域</SelectItem>
                                </SelectContent>
                            </Select>
                            <Text variant="body3" className="text-muted-foreground">
                                决定角色在审批与菜单绑定中的组织范围。
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
                        <span className="font-medium">菜单可见性</span>
                        <Text variant="body3" className="text-muted-foreground">
                            角色与菜单的绑定请在“菜单管理”模块中维护；角色审批通过后，可由菜单管理统一分配可见菜单。
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

interface DeleteRoleDialogProps {
    target: RoleRow | null;
    onClose: () => void;
    onSubmitted: () => void;
}

function DeleteRoleDialog({ target, onClose, onSubmitted }: DeleteRoleDialogProps) {
    const [reason, setReason] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [checking, setChecking] = useState(false);
    const [precheck, setPrecheck] = useState<Record<string, any> | null>(null);
    const queryClient = useQueryClient();
    const canonical = useMemo(() => canonicalRole(target?.authority ?? ""), [target?.authority]);

    const { data: pendingChangeList } = useQuery({
        queryKey: ["admin", "role-change-pending", canonical],
        enabled: canonical.length > 0,
        queryFn: async () => {
            const list = await adminApi.getChangeRequests({ status: "PENDING", type: "ROLE" });
            return Array.isArray(list) ? list : [];
        },
        staleTime: 15_000,
        refetchOnWindowFocus: false,
    });

    const pendingChange = useMemo<ChangeRequest | null>(() => {
        if (!pendingChangeList?.length || !canonical) {
            return null;
        }
        for (const change of pendingChangeList) {
            if (resolveChangeRoleId(change) === canonical) {
                return change;
            }
        }
        return null;
    }, [pendingChangeList, canonical]);
    const hasPendingChange = Boolean(pendingChange);

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
        if (pendingChange) {
            const applicant = pendingChange.requestedByDisplayName || pendingChange.requestedBy || "当前用户";
            const submittedAt = formatDateTime(pendingChange.requestedAt);
            const tip = submittedAt
                ? `角色已有待审批的变更（#${pendingChange.id}，${applicant} 于 ${submittedAt} 提交），请等待审批完成后再试。`
                : `角色已有待审批的变更（#${pendingChange.id}，申请人：${applicant}），请等待审批完成后再试。`;
            toast.error(tip);
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
            toast.success("删除申请已提交审批");
            await queryClient.invalidateQueries({ queryKey: ["admin", "role-change-pending"] });
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
                        {hasPendingChange ? (
                            <Alert className="border-amber-300 bg-amber-50 text-amber-900">
                                <AlertTitle>已有待审批的角色变更</AlertTitle>
                                <AlertDescription className="space-y-2">
                                    <p>
                                        角色 <strong>{target.authority}</strong> 已提交审批（单号 #{pendingChange!.id}，申请人{" "}
                                        {pendingChange!.requestedByDisplayName || pendingChange!.requestedBy || "未知"}
                                        {pendingChange!.requestedAt ? `，提交时间 ${formatDateTime(pendingChange!.requestedAt)}` : ""}）。审批完成前无法再次发起删除。
                                    </p>
                                </AlertDescription>
                            </Alert>
                        ) : null}
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
                                    <Badge variant="secondary">角色成员 {precheck?.memberCount ?? 0}</Badge>
                                    <Badge variant="secondary">菜单绑定 {precheck?.menuBindings ?? 0}</Badge>
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
                    <Button
                        variant="destructive"
                        onClick={handleSubmit}
                        disabled={submitting || !target || precheck?.reserved === true || hasPendingChange}
                    >
                        提交审批
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
    const trimmed = value.trim();
    if (!trimmed || trimmed.toLowerCase() === "null" || trimmed.toLowerCase() === "undefined") {
        return "";
    }
    return trimmed.toUpperCase().replace(/^ROLE[_-]?/, "").replace(/_/g, "");
}

function resolveChangeRoleId(change: ChangeRequest | undefined | null): string {
    if (!change) return "";
    const directSource =
        typeof change.resourceId === "string"
            ? change.resourceId
            : typeof change.resourceId === "number"
                ? String(change.resourceId)
                : "";
    const direct = canonicalRole(directSource);
    if (direct && /[A-Z]/.test(direct)) {
        return direct;
    }
    const payload = safeParseJson(change.payloadJson);
    const payloadName =
        payload && typeof payload === "object" ? canonicalRole((payload as any).name || (payload as any).role) : "";
    if (payloadName) return payloadName;
    const updated = change.updatedValue;
    if (updated && typeof updated === "object") {
        const candidate = canonicalRole((updated as any).name || (updated as any).role);
        if (candidate) return candidate;
    }
    const original = change.originalValue;
    if (original && typeof original === "object") {
        const candidate = canonicalRole((original as any).name || (original as any).role);
        if (candidate) return candidate;
    }
    return "";
}

function safeParseJson<T = any>(value: unknown): T | null {
    if (!value) return null;
    if (typeof value === "object") {
        return value as T;
    }
    if (typeof value !== "string") {
        return null;
    }
    try {
        return JSON.parse(value) as T;
    } catch {
        return null;
    }
}

function formatDateTime(value: string | undefined | null): string {
    if (!value) return "";
    try {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value;
        }
        return date.toLocaleString();
    } catch {
        return value ?? "";
    }
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

    const normalizePath = (value?: string | null): string => {
        if (!value) return "";
        return value.trim().toLowerCase().replace(/^\/+/, "");
    };

    const containsDataAssetKeyword = (text: string | undefined): boolean => {
        if (!text) return false;
        const normalized = text.toLowerCase();
        return normalized.includes("数据资产") || normalized.includes("data asset");
    };

    const isDataAssetMenu = (item: PortalMenuItem, ancestorLabels: string[]): boolean => {
        const path = normalizePath(item.path);
        if (path === "catalog" || path.startsWith("catalog/")) {
            return true;
        }
        if (containsDataAssetKeyword(item.displayName) || containsDataAssetKeyword(item.name)) {
            return true;
        }
        if (ancestorLabels.some((label) => containsDataAssetKeyword(label))) {
            return true;
        }
        const metadata = (item.metadata || "").toLowerCase();
        if (metadata.includes('"sectionkey":"catalog"') || metadata.includes('"section":"catalog"')) {
            return true;
        }
        return false;
    };

    const visit = (
        items: PortalMenuItem[] | undefined,
        depth: number,
        ancestors: string[],
        parentId: number | null,
        ancestorDisabled: boolean,
    ) => {
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

            // Flag the entire “数据资产” section (and descendants) as non-assignable for custom roles
            const disableForCatalog = ancestorDisabled || isDataAssetMenu(item, ancestors);

            if (item.id != null) {
                const option: MenuOption = {
                    id: item.id,
                    label: chain.join(" / "),
                    depth,
                    rawRoles: normalizedRoles,
                    canonicalRoles,
                    deleted: Boolean(item.deleted) || isFoundation(item) || disableForCatalog,
                    disabledReason: disableForCatalog ? "数据资产模块暂不对自定义角色开放" : undefined,
                    path: item.path ?? undefined,
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
                visit(item.children, depth + 1, chain, item.id ?? parentId, disableForCatalog);
            }
        });
    };

    visit(collection?.allMenus ?? collection?.menus, 0, [], null, false);

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
