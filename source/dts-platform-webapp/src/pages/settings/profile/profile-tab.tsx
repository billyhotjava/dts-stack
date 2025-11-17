import { useEffect, useMemo, useState } from "react";
import type { KeycloakUser } from "#/keycloak";
import { useUserInfo } from "@/store/userStore";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Text } from "@/ui/typography";
import { resolveProfileName } from "./profile-utils";
import deptService from "@/api/services/deptService";

type AttributeMap = Record<string, string[]> | undefined;

type ProfileTabProps = {
  detail?: KeycloakUser | null;
  pickAttributeValue: (attributes: AttributeMap, keys: string[]) => string;
};

export const ROLE_LABEL_MAP: Record<string, string> = {
  SYSADMIN: "系统管理员",
  AUTHADMIN: "授权管理员",
  AUDITADMIN: "安全审计员",
  DEPT_DATA_OWNER: "部门数据管理员",
  DEPT_DATA_DEV: "部门数据开发员",
  DEPT_DATA_VIEWER: "部门数据查看员",
  INST_DATA_OWNER: "研究所数据管理员",
  INST_DATA_DEV: "研究所数据开发员",
  INST_DATA_VIEWER: "研究所数据查看员",
  DEPT_LEADER: "部门领导",
  INST_LEADER: "研究所领导",
  EMPLOYEE: "普通员工",
  DEPT_OWNER: "部门数据管理员",
  DEPT_EDITOR: "部门数据开发员",
  DEPT_VIEWER: "部门数据查看员",
  INST_OWNER: "研究所数据管理员",
  INST_EDITOR: "研究所数据开发员",
  INST_VIEWER: "研究所数据查看员",
  DATA_STEWARD: "数据管家",
  DATA_ANALYST: "数据分析员",
};

export const USERNAME_FALLBACK_NAME: Record<string, string> = {
  sysadmin: "系统管理员",
  authadmin: "授权管理员",
  auditadmin: "安全审计员",
  opadmin: "运维管理员",
};

export function resolveRoleLabels(roles: unknown): string[] {
  if (!Array.isArray(roles) || roles.length === 0) {
    return [];
  }

  const hiddenPrefixes = ["ROLE_DEFAULT", "DEFAULT-ROLES", "ROLE_UMA_AUTHORIZATION", "ROLE_OFFLINE_ACCESS"];
  const hiddenEquals = new Set(["offline_access", "uma_authorization"]);

  return roles
    .map((role) => {
      if (typeof role === "string") {
        const trimmed = role.trim();
        if (!trimmed) return undefined;
        const upper = trimmed.toUpperCase();
        const lower = trimmed.toLowerCase();
        if (hiddenPrefixes.some((prefix) => upper.startsWith(prefix))) return undefined;
        if (hiddenEquals.has(lower)) return undefined;
        return (
          ROLE_LABEL_MAP[upper] ??
          ROLE_LABEL_MAP[upper.replace(/^ROLE_/, "")] ??
          trimmed
        );
      }

      if (role && typeof role === "object") {
        const maybeRole = role as { code?: string; name?: string };
        const key = maybeRole.code || maybeRole.name;
        if (key) {
          const trimmed = key.trim();
          const upper = trimmed.toUpperCase();
          const lower = trimmed.toLowerCase();
          if (hiddenPrefixes.some((prefix) => upper.startsWith(prefix))) return undefined;
          if (hiddenEquals.has(lower)) return undefined;
          return (
            ROLE_LABEL_MAP[upper] ??
            ROLE_LABEL_MAP[upper.replace(/^ROLE_/, "")] ??
            trimmed
          );
        }
      }

      return undefined;
    })
    .filter((item): item is string => Boolean(item));
}

export default function ProfileTab({ detail, pickAttributeValue }: ProfileTabProps) {
  const { fullName, firstName, username, email, roles, enabled, id, attributes, department } = useUserInfo();
  const detailAttributes = detail?.attributes as AttributeMap;
  const storeAttributes = attributes as AttributeMap;
  const [deptLabelMap, setDeptLabelMap] = useState<Map<string, string>>(new Map());

  const resolvedUsername = detail?.username || username || "-";
  const resolvedUsernameDisplay = detail?.username || username || "-";
  const normalizedUsernameLower = typeof resolvedUsername === "string" ? resolvedUsername.trim().toLowerCase() : "";
  const fallbackName = normalizedUsernameLower ? USERNAME_FALLBACK_NAME[normalizedUsernameLower] ?? "" : "";

  const resolvedName = resolveProfileName({
    detail,
    storeFullName: fullName,
    storeFirstName: firstName,
    username: resolvedUsername,
    fallbackName,
    attributeSources: [detailAttributes, storeAttributes],
    pickAttributeValue,
  });

  const resolvedEmail = detail?.email || email || "-";
  const roleLabels = useMemo(() => {
    const source = detail?.realmRoles && detail.realmRoles.length ? detail.realmRoles : roles;
    const labels = resolveRoleLabels(source);
    if (labels.length > 0) {
      return Array.from(new Set(labels));
    }
    return [];
  }, [detail?.realmRoles, roles]);

  const accountStatus = detail?.enabled ?? enabled;
  const accountId = detail?.id || id || "-";

  const departmentCandidates = useMemo(() => {
    const pick = (source: AttributeMap, keys: string[]): string => {
      const value = pickAttributeValue(source, keys);
      return typeof value === "string" ? value.trim() : "";
    };

    const nameCandidates = [
      pick(detailAttributes, ["departmentName", "deptName", "department_label", "departmentDisplay"]),
      pick(storeAttributes, ["departmentName", "deptName", "department_label", "departmentDisplay"]),
    ].filter((item) => item);

    const codeCandidates = [
      pick(detailAttributes, ["dept_code", "deptCode", "departmentCode", "department"]),
      pick(storeAttributes, ["dept_code", "deptCode", "departmentCode", "department"]),
      typeof department === "string" ? department.trim() : "",
    ].filter((item) => item);

    return { names: nameCandidates, codes: codeCandidates };
  }, [detailAttributes, storeAttributes, department, pickAttributeValue]);

  useEffect(() => {
    const missingCodes = departmentCandidates.codes.filter((code) => code && !deptLabelMap.has(code));
    if (!missingCodes.length) {
      return;
    }
    let cancelled = false;
    (async () => {
      const updates = new Map<string, string>();
      for (const code of missingCodes) {
        try {
          const list = await deptService.listDepartments(code);
          const matched = list.find((item) => String(item.code) === code);
          if (matched) {
            updates.set(code, matched.nameZh || matched.nameEn || String(matched.code));
          }
        } catch (error) {
          console.warn("Failed to resolve department name for", code, error);
        }
      }
      if (!cancelled && updates.size) {
        setDeptLabelMap((prev) => {
          const next = new Map(prev);
          updates.forEach((label, code) => next.set(code, label));
          return next;
        });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [departmentCandidates.codes, deptLabelMap]);

  const resolvedDepartment = useMemo(() => {
    const nameCandidate = departmentCandidates.names.find((item) => item && item.trim());
    if (nameCandidate) {
      return nameCandidate.trim();
    }
    const codeCandidate = departmentCandidates.codes.find((item) => item && item.trim());
    if (codeCandidate) {
      return deptLabelMap.get(codeCandidate) ?? codeCandidate;
    }
    return "-";
  }, [departmentCandidates, deptLabelMap]);

  const basicInfo = [
    { label: "姓名", value: resolvedName || "-", key: "name" },
    { label: "用户名", value: resolvedUsernameDisplay || "-", key: "username" },
    { label: "邮箱", value: resolvedEmail || "-", key: "email" },
    { label: "所属部门", value: resolvedDepartment, key: "department" },
    { label: "角色", value: roleLabels.length ? roleLabels.join("、") : "-", key: "roles" },
    { label: "账号状态", value: accountStatus === false ? "已停用" : "正常", key: "status" },
    { label: "账号标识", value: accountId || "-", key: "id" },
  ];

  return (
    <Card>
      <CardHeader className="space-y-1">
        <CardTitle>基本信息</CardTitle>
        <Text variant="body3" className="text-muted-foreground">
          当前登录账号的基础资料和状态
        </Text>
      </CardHeader>
      <CardContent>
        <div className="grid gap-3 sm:grid-cols-2">
          {basicInfo.map((item) => {
            const displayValue = typeof item.value === "string" && item.value.trim() ? item.value : "-";
            return (
              <div
                key={item.key}
                className="flex items-baseline gap-2 rounded-md border border-border/60 bg-muted/40 px-3 py-2"
              >
                <Text variant="body3" className="text-muted-foreground whitespace-nowrap">
                  {`${item.label}：`}
                </Text>
                <Text variant="body2" className="font-medium text-foreground break-all">
                  {displayValue}
                </Text>
              </div>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
}

export { resolveProfileName } from "./profile-utils";
