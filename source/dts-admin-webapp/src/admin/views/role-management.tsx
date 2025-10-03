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
import { Textarea } from "@/ui/textarea";
import { toast } from "sonner";

const OPERATION_LABELS: Record<DataOperation, string> = {
    read: "读取",
    write: "编辑",
    export: "导出",
};

const SCOPE_LABELS: Record<"DEPARTMENT" | "INSTITUTE", string> = {
    DEPARTMENT: "部门",
    INSTITUTE: "全院共享区",
};

const SOURCE_LABELS: Record<string, string> = {
    builtin: "预置",
    keycloak: "Keycloak",
    custom: "自定义",
};

const RESERVED_ROLE_CODES = new Set(["SYSADMIN", "AUTHADMIN", "AUDITADMIN", "OPADMIN"]);

interface RoleRow {
    key: string;
    authority: string;
    displayName: string;
    canonical: string;
    description?: string | null;
    scope?: "DEPARTMENT" | "INSTITUTE";
    operations: DataOperation[];
    menuIds: number[];
    menuLabels: string[];
    assignments: AdminRoleAssignment[];
    source?: string;
}

interface MenuOption {
    id: number;
    label: string;
    depth: number;
    rawRoles: string[];
    canonicalRoles: string[];
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

        roles.forEach((role) => {
            const canonical = canonicalRole(role.name);
            if (!canonical || RESERVED_ROLE_CODES.has(canonical)) {
                return;
            }
            const menuIds = Array.from(new Set(roleMenuIndex.get(canonical) ?? [])).sort((a, b) => a - b);
            const entry: RoleRow = {
                key: role.id?.toString() ?? role.name,
                authority: role.name,
                displayName: role.name,
                canonical,
                description: role.description,
                scope: role.scope ?? undefined,
                operations: role.operations ?? [],
                menuIds,
                menuLabels: menuIds.map((id) => menuLabelMap.get(id) ?? `菜单 ${id}`),
                assignments: assignmentMap.get(canonical) ?? [],
                source: role.source ?? undefined,
            };
            map.set(canonical, entry);
        });

        extra.forEach((role) => {
            const canonical = canonicalRole(role.name);
            if (!canonical || RESERVED_ROLE_CODES.has(canonical)) {
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
                map.set(canonical, {
                    key: `custom-${role.id}`,
                    authority: role.name,
                    displayName: role.name,
                    canonical,
                    description: role.description,
                    scope: role.scope,
                    operations: role.operations ?? [],
                    menuIds,
                    menuLabels: menuIds.map((id) => menuLabelMap.get(id) ?? `菜单 ${id}`),
                    assignments: assignmentMap.get(canonical) ?? [],
                    source: "custom",
                });
            }
        });

        map.forEach((entry, canonical) => {
            entry.assignments = assignmentMap.get(canonical) ?? [];
            if (!entry.source) {
                entry.source = "keycloak";
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
                width: 220,
                render: (_value, record) => (
                    <div className="flex flex-col gap-1">
                        <span className="font-medium">{record.displayName}</span>
                        {record.description ? (
                            <Text variant="body3" className="text-muted-foreground">
                                {record.description}
                            </Text>
                        ) : null}
                        <div className="flex flex-wrap gap-2 text-xs">
                            <Badge variant="outline">{record.authority}</Badge>
                            <Badge variant={record.source === "custom" ? "secondary" : "outline"}>
                                {SOURCE_LABELS[record.source ?? "keycloak"] ?? record.source ?? "Keycloak"}
                            </Badge>
                        </div>
                    </div>
                ),
            },
            {
                title: "作用域",
                dataIndex: "scope",
                key: "scope",
                width: 140,
                render: (scope?: "DEPARTMENT" | "INSTITUTE") =>
                    scope ? SCOPE_LABELS[scope] : <Text variant="body3" className="text-muted-foreground">未设置</Text>,
            },
            {
                title: "操作权限",
                dataIndex: "operations",
                key: "operations",
                width: 200,
                render: (operations: DataOperation[]) =>
                    operations.length ? (
                        <div className="flex flex-wrap gap-1">
                            {operations.map((operation) => (
                                <Badge key={operation} variant="outline">
                                    {OPERATION_LABELS[operation]}
                                </Badge>
                            ))}
                        </div>
                    ) : (
                        <Text variant="body3" className="text-muted-foreground">未配置</Text>
                    ),
            },
            {
                title: "绑定菜单",
                dataIndex: "menuLabels",
                key: "menus",
                width: 260,
                render: (menus: string[]) =>
                    menus.length ? (
                        <div className="flex flex-wrap gap-1">
                            {menus.slice(0, 4).map((menu) => (
                                <Badge key={menu} variant="outline">
                                    {menu}
                                </Badge>
                            ))}
                            {menus.length > 4 ? (
                                <Badge variant="secondary">+{menus.length - 4}</Badge>
                            ) : null}
                        </div>
                    ) : (
                        <Text variant="body3" className="text-muted-foreground">未绑定</Text>
                    ),
            },
            {
                title: "成员",
                key: "members",
                render: (_value, record) =>
                    record.assignments.length ? (
                        <div className="flex flex-wrap gap-1">
                            {record.assignments.slice(0, 4).map((assignment) => (
                                <Badge key={assignment.id} variant="outline">
                                    {assignment.displayName || assignment.username}
                                </Badge>
                            ))}
                            {record.assignments.length > 4 ? (
                                <Badge variant="secondary">+{record.assignments.length - 4}</Badge>
                            ) : null}
                        </div>
                    ) : (
                        <Text variant="body3" className="text-muted-foreground">未绑定用户</Text>
                    ),
            },
            {
                title: "操作",
                key: "actions",
                fixed: "right",
                width: 220,
                render: (_value, record) => {
                    const immutable = record.source === "builtin";
                    return (
                        <div className="flex flex-wrap gap-2">
                            <Button size="sm" variant="outline" onClick={() => setMemberTarget(record)}>
                                成员
                            </Button>
                            <Button
                                size="sm"
                                variant="outline"
                                disabled={immutable}
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

    return (
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
                            pagination={false}
                            scroll={{ x: 1100 }}
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
            />
            <UpdateRoleDialog
                target={editTarget}
                onClose={() => setEditTarget(null)}
                onSubmitted={handleUpdateSubmitted}
                menuOptions={menuOptions}
                menuRoleMap={menuRoleMap}
            />
            <DeleteRoleDialog
                target={deleteTarget}
                onClose={() => setDeleteTarget(null)}
                onSubmitted={handleDeleteSubmitted}
                menuRoleMap={menuRoleMap}
            />
            <MembersDialog target={memberTarget} onClose={() => setMemberTarget(null)} />
        </div>
    );
}

interface CreateRoleDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onSubmitted: () => void;
    menuOptions: MenuOption[];
    menuRoleMap: Map<number, MenuOption>;
}

function CreateRoleDialog({ open, onOpenChange, onSubmitted, menuOptions, menuRoleMap }: CreateRoleDialogProps) {
    const [name, setName] = useState("");
    const [scope, setScope] = useState<"DEPARTMENT" | "INSTITUTE">("DEPARTMENT");
    const [operations, setOperations] = useState<Set<DataOperation>>(new Set(["read", "write"]));
    const [allowDesensitize, setAllowDesensitize] = useState(true);
    const [description, setDescription] = useState("");
    const [reason, setReason] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [selectedMenus, setSelectedMenus] = useState<Set<number>>(new Set());

    const resetState = useCallback(() => {
        setName("");
        setScope("DEPARTMENT");
        setOperations(new Set(["read", "write"]));
        setAllowDesensitize(true);
        setDescription("");
        setReason("");
        setSelectedMenus(new Set());
        setSubmitting(false);
    }, []);

    const toggleOperation = (operation: DataOperation, checked: boolean) => {
        setOperations((prev) => {
            const next = new Set(prev);
            if (checked) {
                next.add(operation);
            } else {
                next.delete(operation);
            }
            return next;
        });
    };

    const toggleMenu = (id: number, checked: boolean) => {
        setSelectedMenus((prev) => {
            const next = new Set(prev);
            if (checked) {
                next.add(id);
            } else {
                next.delete(id);
            }
            return next;
        });
    };

    const handleSubmit = async () => {
        const trimmedName = name.trim();
        if (!trimmedName) {
            toast.error("请输入角色名称");
            return;
        }
        if (operations.size === 0) {
            toast.error("至少选择一个操作权限");
            return;
        }
        setSubmitting(true);
        try {
            const trimmedReason = reason.trim() || undefined;
            const payload = {
                name: trimmedName.toUpperCase(),
                scope,
                operations: Array.from(operations),
                allowDesensitizeJson: allowDesensitize,
                description: description.trim() || undefined,
                reason: trimmedReason,
            };
            await adminApi.createCustomRole(payload);
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
                    <div className="space-y-2">
                        <label htmlFor="role-name" className="font-medium">
                            角色名称
                        </label>
                        <Input
                            id="role-name"
                            placeholder="如：DEPT_OWNER"
                            value={name}
                            onChange={(event) => setName(event.target.value)}
                        />
                        <Text variant="body3" className="text-muted-foreground">
                            建议使用大写英文与下划线，审批通过后会同步至 Keycloak。
                        </Text>
                    </div>
                    <div className="grid gap-4 md:grid-cols-2">
                        <div className="space-y-2">
                            <label className="font-medium" htmlFor="role-scope">
                                作用域
                            </label>
                            <Select value={scope} onValueChange={(value) => setScope(value as "DEPARTMENT" | "INSTITUTE")}> 
                                <SelectTrigger id="role-scope">
                                    <SelectValue placeholder="请选择作用域" />
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="DEPARTMENT">部门</SelectItem>
                                    <SelectItem value="INSTITUTE">全院共享区</SelectItem>
                                </SelectContent>
                            </Select>
                        </div>
                        <div className="space-y-2">
                            <span className="font-medium">脱敏策略</span>
                            <label className="flex items-center gap-2 text-sm">
                                <Checkbox checked={allowDesensitize} onCheckedChange={(value) => setAllowDesensitize(value === true)} />
                                需脱敏 JSON 输出
                            </label>
                        </div>
                    </div>
                    <div className="space-y-2">
                        <span className="font-medium">操作权限</span>
                        <div className="flex flex-wrap gap-4">
                            {(Object.keys(OPERATION_LABELS) as DataOperation[]).map((operation) => (
                                <label key={operation} className="flex items-center gap-2 text-sm">
                                    <Checkbox
                                        checked={operations.has(operation)}
                                        onCheckedChange={(value) => toggleOperation(operation, value === true)}
                                    />
                                    {OPERATION_LABELS[operation]}
                                </label>
                            ))}
                        </div>
                    </div>
                    <div className="space-y-2">
                        <span className="font-medium">访问菜单</span>
                        {menuOptions.length === 0 ? (
                            <Text variant="body3" className="text-muted-foreground">
                                暂无可绑定的菜单，请稍后再试。
                            </Text>
                        ) : (
                            <div className="max-h-60 space-y-1 overflow-y-auto rounded-md border p-3">
                                {menuOptions.map((option) => (
                                    <label
                                        key={option.id}
                                        className="flex items-center gap-2 text-sm"
                                        style={{ paddingLeft: option.depth * 16 }}
                                    >
                                        <Checkbox
                                            checked={selectedMenus.has(option.id)}
                                            onCheckedChange={(value) => toggleMenu(option.id, value === true)}
                                        />
                                        {option.label}
                                    </label>
                                ))}
                            </div>
                        )}
                        <Text variant="body3" className="text-muted-foreground">
                            审批通过后，所选菜单将自动纳入该角色的可访问范围。
                        </Text>
                    </div>
                    <div className="space-y-2">
                        <label htmlFor="role-description" className="font-medium">
                            说明（可选）
                        </label>
                        <Textarea
                            id="role-description"
                            rows={3}
                            placeholder="补充角色用途，便于审批记录"
                            value={description}
                            onChange={(event) => setDescription(event.target.value)}
                        />
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
}

function UpdateRoleDialog({ target, onClose, onSubmitted, menuOptions, menuRoleMap }: UpdateRoleDialogProps) {
    const [scope, setScope] = useState<"DEPARTMENT" | "INSTITUTE">("DEPARTMENT");
    const [operations, setOperations] = useState<Set<DataOperation>>(new Set());
    const [description, setDescription] = useState("");
    const [reason, setReason] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [selectedMenus, setSelectedMenus] = useState<Set<number>>(new Set());

    useEffect(() => {
        if (!target) {
            return;
        }
        setScope(target.scope ?? "DEPARTMENT");
        setOperations(new Set(target.operations.length ? target.operations : ["read"]));
        setDescription(target.description ?? "");
        setReason("");
        setSelectedMenus(new Set(target.menuIds));
        setSubmitting(false);
    }, [target]);

    const toggleOperation = (operation: DataOperation, checked: boolean) => {
        setOperations((prev) => {
            const next = new Set(prev);
            if (checked) {
                next.add(operation);
            } else {
                next.delete(operation);
            }
            return next;
        });
    };

    const toggleMenu = (id: number, checked: boolean) => {
        setSelectedMenus((prev) => {
            const next = new Set(prev);
            if (checked) {
                next.add(id);
            } else {
                next.delete(id);
            }
            return next;
        });
    };

    const handleSubmit = async () => {
        if (!target) {
            return;
        }
        if (operations.size === 0) {
            toast.error("至少选择一个操作权限");
            return;
        }
        const trimmedReason = reason.trim() || undefined;
        const desiredMenus = new Set(selectedMenus);
        const originalMenus = new Set(target.menuIds);
        const menuChanged = !setsEqual(desiredMenus, originalMenus);
        const scopeChanged = (target.scope ?? "DEPARTMENT") !== scope;
        const descriptionChanged = (target.description ?? "") !== description.trim();
        const operationsChanged = !setsEqual(new Set(target.operations), operations);

        if (!menuChanged && !scopeChanged && !descriptionChanged && !operationsChanged) {
            toast.info("未检测到变更，无需提交审批");
            return;
        }
        setSubmitting(true);
        try {
            if (scopeChanged || descriptionChanged || operationsChanged) {
                const payload = {
                    resourceType: "ROLE",
                    action: "UPDATE",
                    resourceId: target.authority,
                    payloadJson: JSON.stringify({
                        name: target.authority,
                        scope,
                        operations: Array.from(operations),
                        description: description.trim() || undefined,
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
                                        <SelectItem value="INSTITUTE">全院共享区</SelectItem>
                                    </SelectContent>
                                </Select>
                            </div>
                            <div className="space-y-2">
                                <span className="font-medium">操作权限</span>
                                <div className="flex flex-wrap gap-3">
                                    {(Object.keys(OPERATION_LABELS) as DataOperation[]).map((operation) => (
                                        <label key={operation} className="flex items-center gap-2 text-sm">
                                            <Checkbox
                                                checked={operations.has(operation)}
                                                onCheckedChange={(value) => toggleOperation(operation, value === true)}
                                            />
                                            {OPERATION_LABELS[operation]}
                                        </label>
                                    ))}
                                </div>
                            </div>
                        </div>
                        <div className="space-y-2">
                            <label htmlFor="edit-description" className="font-medium">
                                说明（可选）
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
                                    {menuOptions.map((option) => (
                                        <label
                                            key={option.id}
                                            className="flex items-center gap-2 text-sm"
                                            style={{ paddingLeft: option.depth * 16 }}
                                        >
                                            <Checkbox
                                                checked={selectedMenus.has(option.id)}
                                                onCheckedChange={(value) => toggleMenu(option.id, value === true)}
                                            />
                                            {option.label}
                                        </label>
                                    ))}
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

    useEffect(() => {
        setReason("");
        setSubmitting(false);
    }, [target]);

    const handleSubmit = async () => {
        if (!target) {
            return;
        }
        setSubmitting(true);
        try {
            const trimmedReason = reason.trim() || undefined;
            const payload = {
                resourceType: "ROLE",
                action: "DELETE",
                resourceId: target.authority,
                payloadJson: JSON.stringify({ name: target.authority }),
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
                        <Text variant="body3">
                            即将提交删除角色 <span className="font-semibold">{target.authority}</span> 的审批请求。该操作将在审批通过后由授权管理员执行。
                        </Text>
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
                    <Button variant="destructive" onClick={handleSubmit} disabled={submitting || !target}>
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
    return (
        <Dialog open={Boolean(target)} onOpenChange={(open) => (!open ? onClose() : null)}>
            <DialogContent className="max-w-lg">
                <DialogHeader>
                    <DialogTitle>角色成员</DialogTitle>
                    {target ? (
                        <Text variant="body3" className="text-muted-foreground">
                            角色 {target.displayName} 当前共 {target.assignments.length} 名成员。
                        </Text>
                    ) : null}
                </DialogHeader>
                <div className="max-h-[360px] space-y-3 overflow-y-auto">
                    {target && target.assignments.length === 0 ? (
                        <Text variant="body3" className="text-muted-foreground">
                            尚未绑定用户，可通过“新增角色授权”审批流程维护成员。
                        </Text>
                    ) : null}
                    {target?.assignments.map((assignment) => (
                        <div key={assignment.id} className="rounded-lg border px-4 py-3 text-sm">
                            <div className="flex flex-wrap items-center justify-between gap-2">
                                <div className="flex flex-col">
                                    <span className="font-medium">{assignment.displayName || assignment.username}</span>
                                    <Text variant="body3" className="text-muted-foreground">
                                        {assignment.username}
                                    </Text>
                                </div>
                                <Badge variant="outline">{assignment.scopeOrgName || "全院共享区"}</Badge>
                            </div>
                            <div className="mt-2 flex flex-wrap gap-2">
                                <Badge variant="secondary">{assignment.userSecurityLevel}</Badge>
                                {assignment.operations.map((operation) => (
                                    <Badge key={`${assignment.id}-${operation}`} variant="outline">
                                        {OPERATION_LABELS[operation]}
                                    </Badge>
                                ))}
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

function buildMenuCatalog(collection: PortalMenuCollection | undefined): {
    options: MenuOption[];
    roleToMenuIds: Map<string, number[]>;
    menuRoleMap: Map<number, MenuOption>;
} {
    const options: MenuOption[] = [];
    const roleToMenuIds = new Map<string, number[]>();
    const menuRoleMap = new Map<number, MenuOption>();

    const visit = (items: PortalMenuItem[] | undefined, depth: number, ancestors: string[]) => {
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
                };
                options.push(option);
                menuRoleMap.set(option.id, option);
                canonicalRoles.forEach((role) => {
                    const list = roleToMenuIds.get(role) ?? [];
                    list.push(option.id);
                    roleToMenuIds.set(role, list);
                });
            }

            if (item.children?.length) {
                visit(item.children, depth + 1, chain);
            }
        });
    };

    visit(collection?.allMenus ?? collection?.menus, 0, []);

    roleToMenuIds.forEach((ids, role) => {
        const unique = Array.from(new Set(ids));
        unique.sort((a, b) => a - b);
        roleToMenuIds.set(role, unique);
    });

    return { options, roleToMenuIds, menuRoleMap };
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
    const tasks: Promise<void>[] = [];

    affectedIds.forEach((menuId) => {
        const option = menuRoleMap.get(menuId);
        if (!option) {
            return;
        }
        const existingRoles = new Set(option.rawRoles);
        const shouldHave = desiredMenuIds.has(menuId);
        const currentlyHas = existingRoles.has(authority);
        if (shouldHave === currentlyHas) {
            return;
        }
        if (shouldHave) {
            existingRoles.add(authority);
        } else {
            existingRoles.delete(authority);
        }
        const allowedRoles = Array.from(existingRoles).sort();
        tasks.push(
            (async () => {
                const payload = {
                    resourceType: "PORTAL_MENU",
                    action: "UPDATE",
                    resourceId: String(menuId),
                    payloadJson: JSON.stringify({ allowedRoles }),
                    reason,
                };
                const change = await adminApi.createChangeRequest(payload);
                await adminApi.submitChangeRequest(change.id);
                option.rawRoles = allowedRoles;
                option.canonicalRoles = allowedRoles.map(canonicalRole).filter((role) => role.length > 0);
            })()
        );
    });

    if (tasks.length === 0) {
        return;
    }
    await Promise.all(tasks);
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
