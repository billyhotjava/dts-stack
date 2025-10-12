import { useCallback, useEffect, useMemo, useState } from "react";
import type { ColumnsType } from "antd/es/table";
import { Table } from "antd";
import type { KeycloakRole, KeycloakUser } from "#/keycloak";
import { KeycloakUserService } from "@/api/services/keycloakService";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Text } from "@/ui/typography";
import { Icon } from "@/components/icon";
import UserModal from "./user-management.modal";
import { toast } from "sonner";
import { useRouter } from "@/routes/hooks";
import type { OrganizationNode } from "@/admin/types";
import { adminApi } from "@/admin/api/adminApi";
import { isKeycloakBuiltInRole } from "@/constants/keycloak-roles";
import { GLOBAL_CONFIG } from "@/global-config";

function getSingleAttr(attrs: Record<string, string[]> | undefined, key: string): string {
  const values = attrs?.[key];
  if (!values || values.length === 0) return "";
  const nonEmpty = values.find((v) => v && v.trim());
  return (nonEmpty ?? values[0] ?? "").toString().trim();
}

function toPersonnelLevelZh(raw?: string): string {
  const v = (raw || "").toString().trim();
  if (!v) return "";
  const upper = v.toUpperCase();
  if (upper === "CORE") return "核心";
  if (upper === "IMPORTANT") return "重要";
  if (upper === "GENERAL") return "一般";
  if (upper === "NON_SECRET") return "非密";
  return v;
}

// (deduped) helpers defined once above

function collectRoleNames(user: KeycloakUser): string[] {
  const names = new Set<string>();
  // 1) backend may return a flat roles[] summary
  (user.roles || []).forEach((r: string) => r && names.add(r));
  // 2) realm roles (preferred when available)
  (user.realmRoles || []).forEach((r: string) => r && names.add(r));
  // 3) client roles (flatten)
  if (user.clientRoles) {
    (Object.values(user.clientRoles) as string[][]).forEach((arr: string[]) => arr?.forEach((r: string) => r && names.add(r)));
  }
  // Hide built-in/default technical roles from list display
  const raw = Array.from(names).map((n) => String(n || "").trim());
  const filtered = raw.filter((n) => {
    if (!n) return false;
    if (GLOBAL_CONFIG.hideDefaultRoles && n.toLowerCase().startsWith("default-roles-")) return false;
    const lower = n.toLowerCase();
    if (GLOBAL_CONFIG.hideBuiltinRoles && (lower === "offline_access" || lower === "uma_authorization" || lower.startsWith("realm-management"))) return false;
    return true;
  });
  return filtered;
}

function resolveFullName(user: KeycloakUser): string {
  const n = (
    user.fullName ||
    user.firstName ||
    user.lastName ||
    (Array.isArray(user.attributes?.fullname) ? user.attributes?.fullname?.[0] : undefined) ||
    ""
  ).toString().trim();
  return n;
}

export default function UserManagementView() {
  const { push } = useRouter();
  const [list, setList] = useState<KeycloakUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchValue, setSearchValue] = useState("");
  const [rolesMap, setRolesMap] = useState<Record<string, string[]>>({});
  const [roleDisplayNameMap, setRoleDisplayNameMap] = useState<Record<string, string>>({});
  const [orgIndexById, setOrgIndexById] = useState<Record<string, OrganizationNode>>({});
  const [modalState, setModalState] = useState<{
    open: boolean;
    mode: "create" | "edit";
    target?: KeycloakUser;
  }>({ open: false, mode: "create" });
  // no external toggling action here; enable/停用统一放入编辑弹窗

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = searchValue.trim()
        ? await KeycloakUserService.searchUsers(searchValue.trim())
        : await KeycloakUserService.getAllUsers({ first: 0, max: 100 });
      setList(data || []);
      setRolesMap({});
    } catch (e: any) {
      toast.error(e?.message || "加载用户失败");
    } finally {
      setLoading(false);
    }
  }, [searchValue]);

  useEffect(() => {
    load();
  }, [load]);

  const toggleEnabled = useCallback(async () => {}, []);

  // Build organization index once for dept_code -> name mapping
  useEffect(() => {
    (async () => {
      try {
        if (Object.keys(orgIndexById).length > 0) return;
        const tree = await adminApi.getOrganizations();
        const index: Record<string, OrganizationNode> = {};
        const visit = (nodes?: OrganizationNode[]) => {
          if (!nodes) return;
          for (const n of nodes) {
            index[String(n.id)] = n;
            if (n.children && n.children.length) visit(n.children);
          }
        };
        visit(tree);
        setOrgIndexById(index);
      } catch {
        // ignore errors
      }
    })();
  }, [orgIndexById]);

  // Load admin role catalog for Chinese display names
  useEffect(() => {
    (async () => {
      try {
        const roles = await adminApi.getAdminRoles();
        const map: Record<string, string> = {};
        for (const r of roles || []) {
          const display = (r as any).nameZh || (r as any).displayName || (r as any).name || "";
          const keys = [ (r as any).name, (r as any).code, (r as any).roleId, (r as any).legacyName ];
          for (const k of keys) {
            const key = (k || "").toString().trim().toUpperCase();
            if (key && display) map[key] = String(display);
          }
        }
        setRoleDisplayNameMap(map);
      } catch { /* ignore */ }
    })();
  }, []);

  // Background fetch for user roles to fill the roles column
  useEffect(() => {
    (async () => {
      const entries = list || [];
      if (!entries.length) return;
      const limit = 5; // simple concurrency cap
      let i = 0;
      async function worker() {
        while (i < entries.length) {
          const idx = i++;
          const u = entries[idx];
          const id = String(u.id || u.username || idx);
          if (rolesMap[id] !== undefined) continue;
          try {
            const key = String(u.id || u.username || idx);
            const roles: KeycloakRole[] = await KeycloakUserService.getUserRoles(String(u.id || u.username || ""));
            const filtered = (roles || []).filter((r) => {
              const name = (r?.name || "").toString();
              if (!name) return false;
              if (GLOBAL_CONFIG.hideDefaultRoles && name.toLowerCase().startsWith("default-roles-")) return false;
              // Only hide Keycloak built-in roles in the list view. Do NOT hide
              // reserved business roles (e.g. SYSADMIN/OPADMIN) from display here,
              // otherwise the “角色”列会被清空。
              if (GLOBAL_CONFIG.hideBuiltinRoles && isKeycloakBuiltInRole(r)) return false;
              return true;
            });
            const names = filtered.map((r) => (r?.name || "").toString().trim().toUpperCase());
            setRolesMap((prev) => ({ ...prev, [key]: names }));
          } catch {
            setRolesMap((prev) => ({ ...prev, [id]: [] }));
          }
        }
      }
  await Promise.all(Array.from({ length: Math.min(limit, entries.length) }, () => worker()));
    })();
  }, [list]);


  const columns: ColumnsType<KeycloakUser> = useMemo(
    () => [
      { title: "用户名", dataIndex: "username", key: "username", width: 200, ellipsis: true, onCell: () => ({ style: { verticalAlign: "middle" } }) },
      {
        title: "姓名",
        key: "fullName",
        width: 160,
        ellipsis: true,
        onCell: () => ({ style: { verticalAlign: "middle" } }),
        render: (_, record) => {
          const name = resolveFullName(record);
          return name ? name : <span className="text-muted-foreground">-</span>;
        },
      },
      { title: "邮箱", dataIndex: "email", key: "email", width: 220, ellipsis: true, onCell: () => ({ style: { verticalAlign: "middle" } }) },
      {
        title: "所属部门",
        key: "department",
        width: 200,
        ellipsis: true,
        onCell: () => ({ style: { verticalAlign: "middle" } }),
        render: (_, record) => {
          // 1) department attribute
          const dept = getSingleAttr(record.attributes, "department");
          if (dept) return dept;
          // 2) group path leaf (when included in summary)
          const gp = Array.isArray((record as any).groups) && (record as any).groups.length ? (record as any).groups[0] : "";
          const idx = gp.lastIndexOf("/");
          const leaf = idx >= 0 && idx + 1 < gp.length ? gp.substring(idx + 1) : gp;
          if (leaf) return leaf;
          // 3) map dept_code via organizations index
          const dc = getSingleAttr(record.attributes, "dept_code");
          if (dc && orgIndexById[dc]) return orgIndexById[dc].name || dc;
          return <span className="text-muted-foreground">-</span>;
        },
      },
      {
        title: "人员密级",
        key: "personnel_level",
        width: 140,
        onCell: () => ({ style: { verticalAlign: "middle" } }),
        render: (_, record) => {
          const primary = getSingleAttr(record.attributes, "personnel_security_level");
          const fallback = getSingleAttr(record.attributes, "person_security_level") || getSingleAttr(record.attributes, "person_level");
          const zh = toPersonnelLevelZh(primary || fallback);
          return zh || <span className="text-muted-foreground">-</span>;
        },
      },
      {
        title: "角色",
        key: "roles",
        onCell: () => ({ style: { verticalAlign: "middle" } }),
        render: (_, record) => {
          const id = String(record.id || record.username);
          const names = rolesMap[id] ?? collectRoleNames(record).map((n) => n.toUpperCase());
          if (names === undefined) return <span className="text-muted-foreground">加载中…</span>;
          const roles = names || [];
          return roles.length ? (
            <div className="flex flex-wrap gap-1">
              {roles.slice(0, 5).map((r) => (
                <Badge key={r} variant="outline">{roleDisplayNameMap[r] || r}</Badge>
              ))}
              {roles.length > 5 ? <Badge variant="secondary">+{roles.length - 5}</Badge> : null}
            </div>
          ) : (
            <span className="text-muted-foreground">--</span>
          );
        },
      },
      {
        title: "状态",
        dataIndex: "enabled",
        key: "enabled",
        width: 120,
        onCell: () => ({ style: { verticalAlign: "middle" } }),
        render: (val?: boolean) => (
          <div className="flex items-center gap-2">
            <span className={val ? "h-2 w-2 rounded-full bg-emerald-500" : "h-2 w-2 rounded-full bg-red-500"} />
            <span className={val ? "text-emerald-600" : "text-red-600"}>{val ? "已启用" : "已停用"}</span>
          </div>
        ),
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
            <Button
              size="sm"
              variant="outline"
              onClick={() => setModalState({ open: true, mode: "edit", target: record })}
            >
              编辑
            </Button>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => {
                const id = record.id || record.username;
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
    [toggleEnabled, rolesMap, orgIndexById],
  );

  return (
    <div className="mx-auto w-full max-w-[1400px] px-6 py-6 space-y-6">
      {/* 页面标题与操作区：老布局形态 */}
      <div className="flex flex-wrap items-center gap-3">
        <Text variant="body1" className="text-lg font-semibold">
          用户管理
        </Text>
        <div className="ml-auto flex items-center gap-2">
          <Input
            placeholder="按用户名搜索"
            value={searchValue}
            onChange={(e) => setSearchValue(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") load();
            }}
            className="w-[240px]"
          />
          <Button variant="outline" onClick={load}>
            <Icon icon="solar:magnifer-linear" className="mr-1 h-4 w-4" />
            搜索
          </Button>
          <Button onClick={() => setModalState({ open: true, mode: "create" })}>
            <Icon icon="solar:add-circle-bold" className="mr-1 h-4 w-4" />
            新建用户
          </Button>
        </div>
      </div>

      {/* 列表区：保持老的“表格主体”布局，但采用新样式卡片包裹 */}
      <Card>
        <CardHeader className="pb-2">
          <CardTitle>用户列表</CardTitle>
        </CardHeader>
        <CardContent>
          <Table
            rowKey={(r) => String(r.id ?? r.username)}
            columns={columns}
            dataSource={list}
            loading={loading}
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
          />
        </CardContent>
      </Card>

      {/* 创建/编辑弹窗：复用旧页面Modal但使用新样式组件 */}
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
  );
}
