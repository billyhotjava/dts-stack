import { Button, Table } from "antd";
import { EditOutlined, EyeOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { KeycloakUser } from "#/keycloak";
import { adminApi } from "@/admin/api/adminApi";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Text } from "@/ui/typography";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/ui/tooltip";
import { toast } from "sonner";
import { useRouter } from "@/routes/hooks";
import UserModal from "./user-management.modal";

type UserSnapshotRow = {
  id?: number;
  keycloakId?: string;
  username?: string;
  fullName?: string;
  email?: string;
  phone?: string;
  deptCode?: string;
  deptName?: string;
  personSecurityLevel?: string;
  realmRoles?: string[];
  groupPaths?: string[];
  enabled?: boolean;
  mdmEnabled?: number;
  lastSyncAt?: string;
};

type RoleChip = { code: string; label: string };

function normalizeRoleCode(value?: string): string {
  if (!value) return "";
  let upper = String(value).trim().toUpperCase();
  if (!upper) return "";
  if (upper.startsWith("ROLE_")) {
    upper = upper.slice(5);
  } else if (upper.startsWith("ROLE-")) {
    upper = upper.slice(5);
  }
  return upper.replace(/[^A-Z0-9_]/g, "_").replace(/_+/g, "_");
}

function leafOfGroupPath(path?: string): string {
  if (!path) return "";
  const idx = path.lastIndexOf("/");
  return idx >= 0 && idx + 1 < path.length ? path.substring(idx + 1) : path;
}

function toPersonnelLevelZh(raw?: string): string {
  const v = (raw || "").toString().trim();
  if (!v) return "";
  if (/^\d+$/.test(v)) {
    if (v === "0") return "一般";
    if (v === "1") return "重要";
    return "核心";
  }
  const upper = v.toUpperCase();
  if (upper === "CORE") return "核心";
  if (upper === "IMPORTANT") return "重要";
  if (upper === "GENERAL") return "一般";
  if (upper === "NON_SECRET" || upper === "NONE_SECRET") return "一般";
  return v;
}

function toKeycloakUser(row: UserSnapshotRow): KeycloakUser {
  return {
    id: row.keycloakId || row.username || "",
    username: row.username || "",
    email: row.email,
    enabled: Boolean(row.enabled),
    firstName: row.fullName || undefined,
    fullName: row.fullName || undefined,
  };
}

function normalizeUsersPage(page: unknown): { items: UserSnapshotRow[]; total: number } {
  if (!page || typeof page !== "object") {
    return { items: [], total: 0 };
  }
  const obj = page as Record<string, unknown>;
  const items = Array.isArray(obj.content) ? (obj.content as UserSnapshotRow[]).filter(Boolean) : [];
  const total = typeof obj.totalElements === "number" ? obj.totalElements : items.length;
  return { items, total };
}

export default function UserManagementView() {
  const { push } = useRouter();
  const [list, setList] = useState<UserSnapshotRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pagination, setPagination] = useState<{ current: number; pageSize: number }>({
    current: 1,
    pageSize: 20,
  });
  const [roleDisplayNameMap, setRoleDisplayNameMap] = useState<Record<string, string>>({
    SYSADMIN: "系统管理员",
    SYS_ADMIN: "系统管理员",
    AUTHADMIN: "授权管理员",
    AUTH_ADMIN: "授权管理员",
    OPADMIN: "运维管理员",
    AUDITADMIN: "安全审计员",
    SECURITY_AUDITOR: "安全审计员",
  });
  const [modalState, setModalState] = useState<{
    open: boolean;
    mode: "create" | "edit";
    target?: KeycloakUser;
  }>({ open: false, mode: "create" });

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await adminApi.getAdminUsers({
        page: Math.max(0, pagination.current - 1),
        size: pagination.pageSize,
        keyword: keyword.trim() ? keyword.trim() : undefined,
      });
      const { items, total: nextTotal } = normalizeUsersPage(page);
      setList(items);
      setTotal(nextTotal);
    } catch (e: any) {
      toast.error(e?.message || "加载用户失败");
    } finally {
      setLoading(false);
    }
  }, [keyword, pagination.current, pagination.pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    (async () => {
      try {
        const roles = await adminApi.getAdminRoles();
        const map: Record<string, string> = {};
        const register = (key?: string, value?: string) => {
          if (!key || !value) return;
          const upper = String(key).trim().toUpperCase();
          if (!upper) return;
          const label = String(value);
          if (!map[upper]) map[upper] = label;
          const canonical = normalizeRoleCode(upper);
          if (canonical && !map[canonical]) map[canonical] = label;
          if (upper.startsWith("ROLE_")) {
            const without = upper.slice(5);
            if (without && !map[without]) map[without] = label;
          }
        };
        for (const r of roles || []) {
          const display = (r as any).displayName || (r as any).name || "";
          const keys = [(r as any).name, (r as any).code, (r as any).roleId, (r as any).legacyName];
          keys.forEach((k) => register(k, display));
        }
        if (Object.keys(map).length > 0) {
          setRoleDisplayNameMap(map);
        }
      } catch {
        /* ignore */
      }
    })();
  }, []);

  const getRoleChips = useCallback(
    (record: UserSnapshotRow): RoleChip[] => {
      const raw = Array.isArray(record.realmRoles) ? record.realmRoles : [];
      const seen = new Set<string>();
      const roles: RoleChip[] = [];
      for (const name of raw) {
        const normalized = normalizeRoleCode(name);
        if (!normalized || seen.has(normalized)) continue;
        seen.add(normalized);
        const label = roleDisplayNameMap[normalized] || roleDisplayNameMap[`ROLE_${normalized}`] || normalized;
        roles.push({ code: normalized, label });
      }
      return roles;
    },
    [roleDisplayNameMap],
  );

  const renderRolePreview = useCallback((roles: RoleChip[]) => {
    if (!roles.length) return <span className="text-muted-foreground">--</span>;
    const preview = roles.slice(0, 2);
    const remaining = roles.slice(2);
    return (
      <div className="flex items-center gap-1 overflow-hidden whitespace-nowrap">
        {preview.map((role) => (
          <Badge key={role.code} variant="outline" className="max-w-[120px] truncate">
            {role.label}
          </Badge>
        ))}
        {remaining.length > 0 ? (
          <Tooltip>
            <TooltipTrigger asChild>
              <Badge variant="secondary">+{remaining.length}</Badge>
            </TooltipTrigger>
            <TooltipContent className="max-w-xs">
              <div className="flex flex-wrap gap-1">
                {remaining.map((role) => (
                  <Badge key={role.code} variant="outline" className="max-w-[140px] truncate">
                    {role.label}
                  </Badge>
                ))}
              </div>
            </TooltipContent>
          </Tooltip>
        ) : null}
      </div>
    );
  }, []);

  const expandedRowRender = useCallback(
    (record: UserSnapshotRow) => {
      const roles = getRoleChips(record);
      const department =
        record.deptName ||
        (Array.isArray(record.groupPaths) && record.groupPaths.length ? leafOfGroupPath(record.groupPaths[0]) : "");
      const securityLevel = toPersonnelLevelZh(record.personSecurityLevel);

      return (
        <div className="grid gap-4 border-t border-muted pt-4 text-sm md:grid-cols-3">
          <div className="space-y-2">
            <Text variant="body3" className="text-muted-foreground">
              基础信息
            </Text>
            <div className="space-y-1">
              <div>
                <span className="text-muted-foreground">用户名：</span>
                <span>{record.username || "-"}</span>
              </div>
              <div>
                <span className="text-muted-foreground">姓名：</span>
                <span>{record.fullName || "-"}</span>
              </div>
              <div>
                <span className="text-muted-foreground">邮箱：</span>
                <span>{record.email || "-"}</span>
              </div>
              <div>
                <span className="text-muted-foreground">部门：</span>
                <span>{department || "-"}</span>
              </div>
            </div>
          </div>
          <div className="space-y-2">
            <Text variant="body3" className="text-muted-foreground">
              联系方式
            </Text>
            <div className="space-y-1">
              <div>
                <span className="text-muted-foreground">电话：</span>
                <span>{record.phone || "-"}</span>
              </div>
              <div>
                <span className="text-muted-foreground">人员密级：</span>
                <span>{securityLevel || "-"}</span>
              </div>
              <div>
                <span className="text-muted-foreground">账号状态：</span>
                <span className={record.enabled ? "text-emerald-600" : "text-red-600"}>{record.enabled ? "可用" : "禁用"}</span>
              </div>
              <div>
                <span className="text-muted-foreground">院级状态：</span>
                <span className={record.mdmEnabled === 0 ? "text-red-600" : "text-emerald-600"}>{record.mdmEnabled === 0 ? "禁用" : "可用"}</span>
              </div>
            </div>
          </div>
          <div className="space-y-2">
            <Text variant="body3" className="text-muted-foreground">
              角色
            </Text>
            <div className="flex flex-wrap gap-1">
              {roles.length ? (
                roles.map((role) => (
                  <Badge key={role.code} variant="outline" className="max-w-[160px] truncate">
                    {role.label}
                  </Badge>
                ))
              ) : (
                <span className="text-muted-foreground">未分配角色</span>
              )}
            </div>
            {record.lastSyncAt ? (
              <div className="rounded-md bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
                最后同步：{new Date(record.lastSyncAt).toLocaleString()}
              </div>
            ) : null}
          </div>
        </div>
      );
    },
    [getRoleChips],
  );

  const columns: ColumnsType<UserSnapshotRow> = useMemo(
    () => [
      {
        title: "用户名",
        dataIndex: "username",
        key: "username",
        width: 200,
        ellipsis: true,
        onCell: () => ({ style: { verticalAlign: "middle" } }),
      },
      {
        title: "姓名",
        dataIndex: "fullName",
        key: "fullName",
        width: 160,
        ellipsis: true,
        onCell: () => ({ style: { verticalAlign: "middle" } }),
        render: (val?: string) => (val ? val : <span className="text-muted-foreground">-</span>),
      },
      {
        title: "邮箱",
        dataIndex: "email",
        key: "email",
        width: 220,
        ellipsis: true,
        onCell: () => ({ style: { verticalAlign: "middle" } }),
      },
      {
        title: "所属部门",
        key: "department",
        width: 200,
        ellipsis: true,
        onCell: () => ({ style: { verticalAlign: "middle" } }),
        render: (_, record) => {
          const deptName = record.deptName?.trim();
          if (deptName) return deptName;
          const path = Array.isArray(record.groupPaths) && record.groupPaths.length ? record.groupPaths[0] : "";
          const leaf = leafOfGroupPath(path);
          return leaf ? leaf : <span className="text-muted-foreground">-</span>;
        },
      },
      {
        title: "人员密级",
        key: "personSecurityLevel",
        width: 140,
        onCell: () => ({ style: { verticalAlign: "middle" } }),
        render: (_, record) => {
          const zh = toPersonnelLevelZh(record.personSecurityLevel);
          return zh || <span className="text-muted-foreground">-</span>;
        },
      },
      {
        title: "角色",
        key: "roles",
        onCell: () => ({ style: { verticalAlign: "middle" } }),
        render: (_, record) => renderRolePreview(getRoleChips(record)),
      },
      {
        title: "账号状态",
        dataIndex: "enabled",
        key: "enabled",
        width: 120,
        onCell: () => ({ style: { verticalAlign: "middle" } }),
        render: (val?: boolean) => (
          <div className="flex items-center gap-2">
            <span className={val ? "h-2 w-2 rounded-full bg-emerald-500" : "h-2 w-2 rounded-full bg-red-500"} />
            <span className={val ? "text-emerald-600" : "text-red-600"}>{val ? "可用" : "禁用"}</span>
          </div>
        ),
      },
      {
        title: <span className="whitespace-nowrap">院级状态</span>,
        key: "mdmEnabled",
        width: 140,
        onCell: () => ({ style: { verticalAlign: "middle" } }),
        render: (_, record) => {
          const value = record.mdmEnabled;
          if (value === 0) return <Badge variant="destructive">禁用</Badge>;
          if (value === 1) return <Badge variant="secondary">可用</Badge>;
          return <span className="text-muted-foreground">--</span>;
        },
      },
      {
        title: "操作",
        key: "actions",
        width: 180,
        fixed: "right" as const,
        align: "right" as const,
        onCell: () => ({ style: { verticalAlign: "middle" } }),
        render: (_, record) => (
          <div className="flex items-center gap-2 justify-end">
            <Button size="small" type="default" icon={<EditOutlined />} onClick={() => setModalState({ open: true, mode: "edit", target: toKeycloakUser(record) })}>
              编辑
            </Button>
            <Button
              size="small"
              type="text"
              icon={<EyeOutlined />}
              onClick={() => {
                const id = record.keycloakId || record.username;
                if (!id) return;
                push(`/admin/users/${id}`);
              }}
            >
              详情
            </Button>
          </div>
        ),
      },
    ],
    [getRoleChips, push, renderRolePreview],
  );

  return (
    <TooltipProvider>
      <div className="mx-auto w-full max-w-[1400px] px-6 py-6 space-y-6">
        <div className="flex items-center justify-center gap-2 rounded-md border border-dashed border-red-200 bg-red-50 px-4 py-3 text-center text-sm font-medium text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200">
          <Icon icon="mdi:star" className="h-5 w-5 text-red-500" />
          <span className="text-center">非密模块禁止处理涉密数据</span>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <Text variant="body1" className="text-lg font-semibold">
            用户管理
          </Text>
          <div className="ml-auto flex items-center gap-2">
            <Input
              placeholder="按用户名搜索"
              value={keywordInput}
              onChange={(e) => setKeywordInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  const nextKeyword = keywordInput.trim();
                  if (nextKeyword.length === 1) {
                    toast.warning("关键字至少输入 2 个字符");
                    return;
                  }
                  setKeyword(nextKeyword.length >= 2 ? nextKeyword : "");
                  setPagination((prev) => ({ ...prev, current: 1 }));
                }
              }}
              className="w-[240px]"
            />
            <Button
              type="default"
              onClick={() => {
                const nextKeyword = keywordInput.trim();
                if (nextKeyword.length === 1) {
                  toast.warning("关键字至少输入 2 个字符");
                  return;
                }
                setKeyword(nextKeyword.length >= 2 ? nextKeyword : "");
                setPagination((prev) => ({ ...prev, current: 1 }));
              }}
            >
              <Icon icon="solar:magnifer-linear" className="mr-1 h-4 w-4" />
              搜索
            </Button>
            <Button type="primary" onClick={() => setModalState({ open: true, mode: "create" })}>
              <Icon icon="solar:add-circle-bold" className="mr-1 h-4 w-4" />
              新建用户
            </Button>
          </div>
        </div>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle>用户列表</CardTitle>
          </CardHeader>
          <CardContent>
            <Table
              rowKey={(r) => String(r.keycloakId || r.username || r.id)}
              columns={columns}
              dataSource={list}
              loading={loading}
              pagination={{
                current: pagination.current,
                pageSize: pagination.pageSize,
                onChange: (page, pageSize) => setPagination({ current: page, pageSize }),
                showSizeChanger: true,
                pageSizeOptions: [10, 20, 50, 100, 200],
                showQuickJumper: true,
                showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
                total,
              }}
              size="small"
              className="user-management-table text-sm"
              rowClassName={() => "text-sm"}
              tableLayout="fixed"
              scroll={{ x: 1500 }}
              expandable={{
                expandedRowRender,
                expandRowByClick: true,
                columnWidth: 48,
                fixed: false,
              }}
            />
          </CardContent>
        </Card>

        <UserModal
          open={modalState.open}
          mode={modalState.mode}
          user={modalState.target}
          onCancel={() => setModalState((s) => ({ ...s, open: false, target: undefined }))}
          onSuccess={() => {
            setModalState((s) => ({ ...s, open: false, target: undefined }));
            load();
          }}
        />
      </div>
    </TooltipProvider>
  );
}
