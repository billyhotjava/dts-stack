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
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/ui/tooltip";
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
  upper = upper.replace(/[^A-Z0-9_]/g, "_").replace(/_+/g, "_");
  return upper;
}

function collectRoleCodes(user: KeycloakUser): string[] {
  const names = new Set<string>();
  const push = (value?: string) => {
    if (!value) return;
    const raw = String(value).trim();
    if (!raw) return;
    const lower = raw.toLowerCase();
    if (GLOBAL_CONFIG.hideDefaultRoles && lower.startsWith("default-roles-")) return;
    if (GLOBAL_CONFIG.hideBuiltinRoles && (lower === "offline_access" || lower === "uma_authorization")) return;
    const code = normalizeRoleCode(raw);
    if (code) names.add(code);
  };
  (user.roles || []).forEach(push);
  (user.realmRoles || []).forEach(push);
  if (user.clientRoles) {
    (Object.values(user.clientRoles) as string[][]).forEach((arr: string[]) => arr?.forEach(push));
  }
  return Array.from(names).filter(Boolean);
}

function resolvefullName(user: KeycloakUser): string {
  const n = (
    user.fullName ||
    user.firstName ||
    user.lastName ||
    (Array.isArray(user.attributes?.fullName) ? user.attributes?.fullName?.[0] : undefined) ||
    ""
  ).toString().trim();
  return n;
}

const hasOwn = Object.prototype.hasOwnProperty;

export default function UserManagementView() {
  const { push } = useRouter();
  const [list, setList] = useState<KeycloakUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchValue, setSearchValue] = useState("");
  const [rolesMap, setRolesMap] = useState<Record<string, string[]>>({});
  const [roleDisplayNameMap, setRoleDisplayNameMap] = useState<Record<string, string>>({
    SYSADMIN: "系统管理员",
    AUTHADMIN: "授权管理员",
    OPADMIN: "运维管理员",
    AUDITADMIN: "安全审计员",
  });
  const [assignmentRoleMap, setAssignmentRoleMap] = useState<Record<string, { code: string; label: string }[]>>({});
  const [orgIndexById, setOrgIndexById] = useState<Record<string, OrganizationNode>>({});
  const [modalState, setModalState] = useState<{
    open: boolean;
    mode: "create" | "edit";
    target?: KeycloakUser;
  }>({ open: false, mode: "create" });
  // no external toggling action here; enable/停用统一放入编辑弹窗

  const loadRoleAssignments = useCallback(async () => {
    try {
      const assignments = await adminApi.getRoleAssignments();
      const roleNameLookup: Record<string, string> = { ...roleDisplayNameMap };
      const map: Record<string, { code: string; label: string }[]> = {};
      const labelMap: Record<string, string> = {};
      (assignments || []).forEach((item: any) => {
        const username = (item?.username || "").toString().trim().toLowerCase();
        const roleRaw = (item?.role || "").toString();
        const displayNameRaw = (item?.displayName || "").toString();
        const code = normalizeRoleCode(roleRaw);
        if (!username || !code) return;
        const normalizedKey = code;
        const canonicalKey = `ROLE_${code}`;
        const resolvedDisplay = roleNameLookup[normalizedKey] || roleNameLookup[canonicalKey] || displayNameRaw.trim() || roleRaw.trim() || code;
        if (!map[username]) map[username] = [];
        if (!map[username].some((entry) => entry.code === code)) {
          map[username].push({ code, label: resolvedDisplay });
        }
        labelMap[code] = resolvedDisplay;
        labelMap[`ROLE_${code}`] = resolvedDisplay;
      });
      setAssignmentRoleMap(map);
      if (Object.keys(labelMap).length) {
        setRoleDisplayNameMap((prev) => {
          const next = { ...prev };
          Object.entries(labelMap).forEach(([key, value]) => {
            if (!next[key]) {
              next[key] = value;
            }
          });
          return next;
        });
      }
    } catch (error) {
      console.error("Failed to load role assignments", error);
      setAssignmentRoleMap({});
    }
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = searchValue.trim()
        ? await KeycloakUserService.searchUsers(searchValue.trim())
        : await KeycloakUserService.getAllUsers({ first: 0, max: 100 });
      setList(data || []);
      setRolesMap({});
      await loadRoleAssignments();
    } catch (e: any) {
      toast.error(e?.message || "加载用户失败");
    } finally {
      setLoading(false);
    }
  }, [searchValue, loadRoleAssignments]);

  useEffect(() => {
    load();
  }, [load]);

  const toggleEnabled = useCallback(async () => {}, []);

  const getRoleInfo = useCallback(
    (record: KeycloakUser) => {
      const key = String(record.id || record.username);
      const loaded = hasOwn.call(rolesMap, key);
      const usernameKey = (record.username || "").trim().toLowerCase();
      const assigned = assignmentRoleMap[usernameKey] ?? [];
      const fetched = loaded ? rolesMap[key] ?? [] : [];
      const fallback = collectRoleCodes(record);
      const seen = new Set<string>();
      const roles: RoleChip[] = [];
      const push = (code?: string, label?: string) => {
        const normalized = normalizeRoleCode(code);
        if (!normalized || seen.has(normalized)) return;
        seen.add(normalized);
        const resolvedLabel =
          roleDisplayNameMap[normalized] ||
          roleDisplayNameMap[`ROLE_${normalized}`] ||
          label ||
          normalized;
        roles.push({ code: normalized, label: resolvedLabel });
      };
      assigned.forEach((entry) => push(entry.code, entry.label));
      fetched.forEach((code) => push(code));
      fallback.forEach((code) => push(code));
      return { key, loaded, roles };
    },
    [rolesMap, assignmentRoleMap, roleDisplayNameMap]
  );

  const renderRolePreview = useCallback(
    (roles: RoleChip[]) => {
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
    },
    []
  );

  const expandedRowRender = useCallback(
    (record: KeycloakUser) => {
      const { roles } = getRoleInfo(record);
      const attrs = record.attributes || {};
      const phone = getSingleAttr(attrs, "phone") || getSingleAttr(attrs, "mobile") || getSingleAttr(attrs, "telephone");
      const title = getSingleAttr(attrs, "title") || getSingleAttr(attrs, "position");
      const remark = getSingleAttr(attrs, "remark") || getSingleAttr(attrs, "description");
      const department = getSingleAttr(attrs, "department") || getSingleAttr(attrs, "dept_name");
      const securityLevel = toPersonnelLevelZh(
        getSingleAttr(attrs, "personnel_security_level") ||
          getSingleAttr(attrs, "person_security_level") ||
          getSingleAttr(attrs, "personnel_level")
      );

      return (
        <div className="grid gap-4 border-t border-muted pt-4 text-sm md:grid-cols-3">
          <div className="space-y-2">
            <Text variant="body3" className="text-muted-foreground">
              基础信息
            </Text>
            <div className="space-y-1">
              <div>
                <span className="text-muted-foreground">用户名：</span>
                <span>{record.username}</span>
              </div>
              <div>
                <span className="text-muted-foreground">姓名：</span>
                <span>{resolvefullName(record) || "-"}</span>
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
                <span>{phone || "-"}</span>
              </div>
              <div>
                <span className="text-muted-foreground">职务：</span>
                <span>{title || "-"}</span>
              </div>
              <div>
                <span className="text-muted-foreground">人员密级：</span>
                <span>{securityLevel || "-"}</span>
              </div>
              <div>
                <span className="text-muted-foreground">状态：</span>
                <span className={record.enabled ? "text-emerald-600" : "text-red-600"}>
                  {record.enabled ? "已启用" : "已停用"}
                </span>
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
            {remark ? (
              <div className="rounded-md bg-muted/40 px-3 py-2 text-xs text-muted-foreground">备注：{remark}</div>
            ) : null}
          </div>
        </div>
      );
    },
    [getRoleInfo]
  );

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
          const display = (r as any).nameZh || (r as any).displayName || (r as any).name || "";
          const keys = [(r as any).name, (r as any).code, (r as any).roleId, (r as any).legacyName];
          keys.forEach((k) => register(k, display));
        }
        setRoleDisplayNameMap(map);
      } catch {
        /* ignore */
      }
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
          const key = String(u.id || u.username || idx);
          if (rolesMap[key] !== undefined) continue;
          const userId = String(u.id || "").trim();
          if (!userId) {
            setRolesMap((prev) => ({ ...prev, [key]: [] }));
            continue;
          }
          try {
            const roles: KeycloakRole[] = await KeycloakUserService.getUserRoles(userId);
            const filtered = (roles || []).filter((r) => {
              const name = (r?.name || "").toString();
              if (!name) return false;
              if (GLOBAL_CONFIG.hideDefaultRoles && name.toLowerCase().startsWith("default-roles-")) return false;
              if (GLOBAL_CONFIG.hideBuiltinRoles && isKeycloakBuiltInRole(r)) return false;
              return true;
            });
            const names = filtered
              .map((r) => normalizeRoleCode(r?.name))
              .filter((code) => Boolean(code));
            setRolesMap((prev) => ({ ...prev, [key]: names }));
          } catch (error) {
            console.error("Failed to load Keycloak roles", error);
            setRolesMap((prev) => ({ ...prev, [key]: [] }));
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
          const name = resolvefullName(record);
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
          const info = getRoleInfo(record);
          if (!info.loaded && info.roles.length === 0) {
            return <span className="text-muted-foreground">加载中…</span>;
          }
          return renderRolePreview(info.roles);
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
    [toggleEnabled, orgIndexById, getRoleInfo, renderRolePreview],
  );

  return (
    <TooltipProvider>
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
            expandable={{
              expandedRowRender,
              expandRowByClick: true,
              columnWidth: 48,
            }}
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
    </TooltipProvider>
  );
}
