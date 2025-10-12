import { useContext, useEffect, useMemo, useState } from "react";
import type { ReactElement } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { adminApi } from "@/admin/api/adminApi";
import type { AdminUser, ChangeRequest } from "@/admin/types";
import { AdminSessionContext } from "@/admin/lib/session-context";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Text } from "@/ui/typography";
import { toast } from "sonner";
import { KeycloakApprovalService } from "@/api/services/approvalService";
import { KeycloakUserService } from "@/api/services/keycloakService";
import { useUserInfo } from "@/store/userStore";
import { PERSON_SECURITY_LEVELS } from "@/constants/governance";

type TaskCategory = "user" | "role";

const CATEGORY_LABELS: Record<TaskCategory, string> = {
	user: "用户管理",
	role: "角色管理",
};

const USER_RESOURCE_TYPES = new Set(["USER"]);
// Include menu visibility changes in Role category so "绑定菜单" edits appear in approvals
const ROLE_RESOURCE_TYPES = new Set(["ROLE", "CUSTOM_ROLE", "ROLE_ASSIGNMENT", "PORTAL_MENU"]);
// 仅保留用户/角色类审批

const ACTION_LABELS: Record<string, string> = {
	CREATE: "新增",
	UPDATE: "更新",
	DELETE: "删除",
	ENABLE: "启用",
	DISABLE: "禁用",
};

const STATUS_LABELS: Record<string, string> = {
	PENDING: "待审批",
	PROCESSING: "处理中",
	ON_HOLD: "待定",
	APPROVED: "已通过",
	APPLIED: "已应用",
	REJECTED: "已驳回",
	FAILED: "失败",
	DRAFT: "草稿",
};

const STATUS_BADGE: Record<string, "outline" | "secondary" | "destructive"> = {
	PENDING: "outline",
	PROCESSING: "outline",
	ON_HOLD: "outline",
	APPROVED: "secondary",
	APPLIED: "secondary",
	REJECTED: "destructive",
	FAILED: "destructive",
	DRAFT: "outline",
};

type DecisionStatus =
  | "PENDING"
  | "PROCESSING"
  | "ON_HOLD"
  | "APPROVED"
  | "APPLIED"
  | "REJECTED"
  | "FAILED";

interface DecisionRecord {
	status: DecisionStatus;
	decidedAt: string | null;
	decidedBy: string | null;
}

type AugmentedChangeRequest = ChangeRequest & {
	effectiveStatus: DecisionStatus;
	effectiveDecidedAt: string | null;
	effectiveDecidedBy: string | null;
	override?: DecisionRecord;
};

const CATEGORY_ORDER: TaskCategory[] = ["user", "role"];

function resolveCategory(resourceType: string | null | undefined): TaskCategory | null {
	if (!resourceType) {
		return null;
	}
	const normalized = resourceType.trim().toUpperCase();
	if (USER_RESOURCE_TYPES.has(normalized)) {
		return "user";
	}
	if (ROLE_RESOURCE_TYPES.has(normalized)) {
		return "role";
	}
    // 其余类型不再纳入审批列表
    return null;
}

function parseJson(value?: string | null): unknown {
	if (!value) return null;
	try {
		return JSON.parse(value);
	} catch (error) {
		console.warn("Failed to parse change request payload", error, value);
		return null;
	}
}

function asRecord(value: unknown): Record<string, unknown> | null {
	if (value && typeof value === "object" && !Array.isArray(value)) {
		return value as Record<string, unknown>;
	}
	return null;
}

function getStringField(source: Record<string, unknown> | null, key: string): string | null {
	if (!source) return null;
	const raw = source[key];
	return typeof raw === "string" && raw.trim().length > 0 ? raw : null;
}

function resolveOperatorDisplayName(username: string | null | undefined, map: Record<string, string>): string {
	const key = (username || "").trim();
	if (!key) return "-";
	return map[key] || map[key.toLowerCase()] || key;
}

type DiffFormatContext = {
    roleDisplay?: Record<string, string>;
    userDisplay?: Record<string, string>;
};

function resolveTarget(request: ChangeRequest, ctx?: DiffFormatContext): string {
	const payload = asRecord(parseJson(request.payloadJson));
	const diff = asRecord(parseJson(request.diffJson));
	const after = diff && asRecord(diff.after);
	const candidates = [
		request.resourceId,
		getStringField(payload, "username"),
		getStringField(payload, "name"),
		getStringField(after, "username"),
		getStringField(after, "name"),
	];
    const target = candidates.find((item) => typeof item === "string" && item.trim().length > 0);
    const raw = target ? String(target) : "-";
    if (ctx?.userDisplay) {
        const mapped = ctx.userDisplay[raw] || ctx.userDisplay[raw.toLowerCase?.() || raw];
        if (mapped) return mapped;
    }
    return raw;
}

function summarizeDetails(request: ChangeRequest): string {
    const payload = asRecord(parseJson(request.payloadJson));
    const diff = asRecord(parseJson(request.diffJson));
    // Prefer diff summary for batch updates
    if (diff && Array.isArray((diff as any).items) && ((diff as any).items as any[]).length > 0) {
        const items = ((diff as any).items as any[]).slice(0, 3);
        const parts: string[] = [];
        for (const it of items) {
            const id = it?.id ?? "?";
            const before = asRecord(it?.before) || {};
            const after = asRecord(it?.after) || {};
            const keys = Array.from(new Set([...Object.keys(before), ...Object.keys(after)]));
            const changed = keys.filter((k) => JSON.stringify((before as any)[k]) !== JSON.stringify((after as any)[k]));
            if (changed.length === 0) continue;
            const detail = changed
                .slice(0, 2)
                .map((k) => `${k}: ${fmtValue((before as any)[k])} → ${fmtValue((after as any)[k])}`)
                .join("，");
            parts.push(`菜单${id}: ${detail}`);
        }
        const extra = ((diff as any).items as any[]).length - items.length;
        return parts.length ? parts.join("；") + (extra > 0 ? `（等${extra}项）` : "") : "—";
    }
    if (payload) {
        const entries = Object.entries(payload).filter(([, value]) => value != null);
        if (entries.length === 0) return "—";
        return entries
            .slice(0, 3)
            .map(([key, value]) => `${key}: ${typeof value === "string" ? value : JSON.stringify(value)}`)
            .join("；");
    }
    if (diff) {
        const after = asRecord(diff.after);
        if (after) {
            return summarizeDetails({ ...request, payloadJson: JSON.stringify(after) });
        }
        return JSON.stringify(diff);
    }
    return "—";
}

// Removed unused summarize helpers (summarizeDiffPairs/summarizeDiffSide) to satisfy TS noUnusedLocals

// 与 summarizeDiffSide 类似，但返回 JSX，并在“变更后”用红色高亮变动值
function renderDiffSide(request: ChangeRequest, side: "before" | "after", ctx?: DiffFormatContext): ReactElement {
    const diff = asRecord(parseJson(request.diffJson));
    const containerClass = "flex flex-col gap-1 text-xs leading-5";
    const mutedClass = "text-xs text-muted-foreground";
    if (!diff) return <span className={mutedClass}>—</span>;

    const renderLine = (label: string, value: unknown, key: string, prefix?: string | null, idx?: number) => (
        <div key={`${prefix ?? ""}${key}-${idx ?? 0}`} className="whitespace-pre-wrap">
            {prefix ? <span className="text-muted-foreground">{prefix} · </span> : null}
            <span className="text-muted-foreground">{label}：</span>
            <span className={side === "after" ? "text-destructive" : undefined}>{formatFriendlyValue(key, value, ctx)}</span>
        </div>
    );

    const collectChangedKeys = (
        before: Record<string, unknown>,
        after: Record<string, unknown>,
        changeList?: any[],
    ) => {
        if (Array.isArray(changeList) && changeList.length > 0) {
            return changeList
                .map((item) => {
                    const field = String(item?.field ?? "");
                    if (!field || shouldIgnoreField(field)) return null;
                    return {
                        field,
                        value: side === "after" ? item?.after : item?.before,
                    };
                })
                .filter((entry): entry is { field: string; value: unknown } => entry !== null);
        }
        const keys = Array.from(new Set([...Object.keys(before), ...Object.keys(after)]));
        return keys
            .filter(
                (key) =>
                    !shouldIgnoreField(key) && JSON.stringify(before[key]) !== JSON.stringify(after[key]),
            )
            .map((field) => ({
                field,
                value: (side === "after" ? after : before)[field],
            }));
    };

    // 批量
    if (Array.isArray((diff as any).items)) {
        const items = (diff as any).items as any[];
        const lines: ReactElement[] = [];
        items.forEach((item, itemIndex) => {
            const before = asRecord(item?.before) || {};
            const after = asRecord(item?.after) || {};
            const changes = collectChangedKeys(before, after, Array.isArray(item?.changes) ? item.changes : undefined);
            const itemLabel = resolveBatchItemLabel(item, before, after, itemIndex);
            changes.forEach(({ field, value }, idx) => {
                lines.push(renderLine(labelOf(field), value, field, itemLabel, idx));
            });
        });
        if (lines.length === 0) {
            return <span className={mutedClass}>—</span>;
        }
        return <div className={containerClass}>{lines}</div>;
    }

    const before = asRecord(diff.before) || {};
    const after = asRecord(diff.after) || {};
    const changes = collectChangedKeys(before, after, Array.isArray((diff as any).changes) ? (diff as any).changes : undefined);

    if (changes.length === 0) {
        return <span className={mutedClass}>—</span>;
    }

    return (
        <div className={containerClass}>
            {changes.map(({ field, value }, idx) => renderLine(labelOf(field), value, field, null, idx))}
        </div>
    );
}

function resolveBatchItemLabel(
    item: Record<string, unknown> | null,
    before: Record<string, unknown>,
    after: Record<string, unknown>,
    index: number,
): string {
    const candidates = [
        item?.label,
        item?.name,
        item?.displayName,
        item?.title,
        after?.name,
        after?.displayName,
        after?.title,
        before?.name,
        before?.displayName,
        before?.title,
    ];
    for (const candidate of candidates) {
        if (typeof candidate === "string" && candidate.trim().length > 0) {
            return candidate.trim();
        }
    }
    return `第${index + 1}项`;
}

function fmtValue(v: unknown): string {
    if (v == null) return "—";
    if (Array.isArray(v)) return `[${v.map((x) => (typeof x === "string" ? x : JSON.stringify(x))).join(", ")}]`;
    if (typeof v === "string") return v.replaceAll("7f3868a1-9c8c-4122-b7e4-7f921a40c019", "***");
    return JSON.stringify(v);
}

// 字段中文名映射
const FIELD_LABELS: Record<string, string> = {
    // 通用
    username: "用户名",
    name: "名称",
    displayName: "显示名称",
    fullName: "姓名",
    description: "描述",
    reason: "审批备注",
    operations: "操作权限",
    action: "操作",
    actionDisplay: "操作",
    allowRoles: "允许角色",
    allowedPermissions: "允许权限",
    allowedRoles: "允许角色",
    enabled: "是否启用",
    email: "邮箱",
    mobile: "手机号",
    phone: "联系电话",
    groupPaths: "所属组织",
    orgPath: "所属组织",
    orgPaths: "所属组织",
    orgName: "组织名称",
    orgId: "组织标识",
    securityLevel: "安全级别",
    personSecurityLevel: "人员密级",
    "attributes.person_level": "人员密级",
    "attributes.person_security_level": "人员密级",
    "attributes.data_levels": "数据密级",
    "attributes.fullname": "姓名",
    maxDataLevel: "最大数据密级",
    maxDataLevels: "最大数据密级",
    dataOperations: "数据操作",
    // 数据级别 / 作用域
    dataLevel: "数据密级",
    dataLevels: "数据密级",
    scope: "作用域",
    shareScope: "共享范围",
    // 角色/菜单
    roles: "角色",
    resultRoles: "角色",
    realmRoles: "角色",
    clientRoles: "客户端角色",
    menuIds: "菜单绑定",
    menuBindings: "菜单绑定",
    // Keycloak 相关
    keycloakId: "Keycloak ID",
};

const DATA_LEVEL_LABELS: Record<string, string> = {
    DATA_PUBLIC: "公开",
    DATA_INTERNAL: "内部",
    DATA_SECRET: "秘密",
    DATA_TOP_SECRET: "机密",
};

// 人员密级（人员安全等级）中文映射
const PERSON_LEVEL_LABELS: Record<string, string> = Object.fromEntries(
    PERSON_SECURITY_LEVELS.map((it) => [String(it.value).toUpperCase(), it.label]),
);

const SCOPE_LABELS: Record<string, string> = {
    DEPARTMENT: "部门",
    INSTITUTE: "研究所共享区",
};

const SHARE_SCOPE_LABELS: Record<string, string> = {
    SHARE_INST: "所内共享",
    PUBLIC_INST: "所内公开",
};

const OP_LABELS: Record<string, string> = { read: "读取", write: "写入", export: "导出" };

const IGNORED_FIELD_PREFIXES = new Set(["attributes", "target"]);
const IGNORED_EXACT_FIELDS = new Set(["keycloakId"]);

function shouldIgnoreField(key: string): boolean {
    const prefix = key.includes(".") ? key.split(".")[0] : key;
    if (IGNORED_FIELD_PREFIXES.has(prefix)) return true;
    const lower = key.toLowerCase();
    if (IGNORED_EXACT_FIELDS.has(key) || lower === "keycloakid") return true;
    return false;
}

function labelOf(key: string): string {
    if (FIELD_LABELS[key]) {
        return FIELD_LABELS[key];
    }
    if (key.includes(".")) {
        const tail = key.substring(key.lastIndexOf(".") + 1);
        if (FIELD_LABELS[tail]) {
            return FIELD_LABELS[tail];
        }
    }
    return key;
}

function mapArray(v: unknown, mapper: (x: any) => string): string {
    const arr = Array.isArray(v) ? v : v == null ? [] : [v];
    if (arr.length === 0) return "—";
    return arr.map(mapper).join("，");
}

// 将值转换为更友好的中文显示
function isPersonLevelKey(key: string): boolean {
    const k = String(key || "").toLowerCase();
    // 支持多种键名：personSecurityLevel / person_security_level / person_level / personnel_security_level
    return (
        k.endsWith("personsecuritylevel") ||
        k.endsWith("person_level") ||
        k.endsWith("person_security_level") ||
        k.endsWith("personlevel") ||
        k.endsWith("personnel_security_level") ||
        k.endsWith("personnelsecuritylevel")
    );
}

function formatFriendlyValue(key: string, value: unknown, ctx?: DiffFormatContext): string {
    if (value == null || value === "") return "—";
    // 人员密级优先处理，兼容 attributes.person_level 等多种写法
    if (isPersonLevelKey(key)) {
        return mapArray(value, (x) => {
            const raw = String(x || "");
            const mapped = PERSON_LEVEL_LABELS[raw.toUpperCase()];
            return mapped || raw;
        });
    }
    switch (key) {
        case "dataLevel":
        case "dataLevels":
            return mapArray(value, (x) => DATA_LEVEL_LABELS[String(x)] || String(x));
        case "operations":
            return mapArray(value, (x) => OP_LABELS[String(x)] || String(x));
        case "role":
        case "roles":
        case "resultRoles":
        case "realmRoles":
        case "clientRoles":
        case "allowRoles":
            return mapArray(value, (x) => {
                const raw = String(x || "");
                const upper = raw.toUpperCase();
                const mapped = ctx?.roleDisplay?.[upper];
                if (mapped) return mapped;
                if (upper.startsWith("DEFAULT-ROLES-")) return "默认角色";
                return raw;
            });
        case "action": {
            const s = String(value || "").toLowerCase();
            const map: Record<string, string> = {
                create: "新增",
                update: "更新",
                delete: "删除",
                grantroles: "分配角色",
                revokeroles: "撤销角色",
                enable: "启用",
                disable: "禁用",
                resetpassword: "重置密码",
            };
            return map[s] || String(value);
        }
        case "actionDisplay":
            return String(value || "");
        case "username": {
            const u = String(value || "").trim();
            const mapped = ctx?.userDisplay?.[u] || ctx?.userDisplay?.[u.toLowerCase?.() || u];
            return mapped || u || "—";
        }
        case "scope":
            return SCOPE_LABELS[String(value)] || String(value);
        case "shareScope":
            return SHARE_SCOPE_LABELS[String(value)] || String(value);
        case "keycloakId":
            return "***";
        case "menuIds":
        case "menuBindings":
            return mapArray(value, (x) => `菜单${String(x)}`);
        default:
            if (typeof value === "boolean") return value ? "是" : "否";
            if (Array.isArray(value)) return value.map((x) => (typeof x === "string" ? x : JSON.stringify(x))).join("，");
            if (typeof value === "object") return JSON.stringify(value);
            return String(value);
    }
}

function getActionText(request: ChangeRequest): string {
	const actionLabel = ACTION_LABELS[request.action?.toUpperCase()] ?? request.action;
	const category = resolveCategory(request.resourceType);
	const categoryLabel = category ? CATEGORY_LABELS[category] : request.resourceType;
	return `${actionLabel || "操作"}${categoryLabel ? ` · ${categoryLabel}` : ""}`;
}

function getStatusLabel(status: string): string {
	return STATUS_LABELS[status?.toUpperCase()] ?? status;
}

function getStatusBadgeVariant(status: string): "outline" | "secondary" | "destructive" {
	return STATUS_BADGE[status?.toUpperCase()] ?? "outline";
}

function formatDateTime(value?: string | null): string {
	if (!value) return "-";
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) {
		return value;
	}
	return date.toLocaleString("zh-CN", { hour12: false });
}

function formatJson(value: Record<string, unknown> | null) {
	if (!value) return "—";
	try {
		const text = JSON.stringify(value, null, 2);
		return text.replaceAll("7f3868a1-9c8c-4122-b7e4-7f921a40c019", "***");
	} catch (error) {
		console.warn("Failed to stringify JSON content", error, value);
		return "—";
	}
}

function normalizeStatus(status?: string | null): DecisionStatus {
	const normalized = status?.toUpperCase();
	if (normalized === "APPROVED" || normalized === "REJECTED" || normalized === "APPLIED") {
		return normalized;
	}
	if (normalized === "ON_HOLD") {
		return "ON_HOLD";
	}
	return "PENDING";
}

export default function ApprovalCenterView() {
    const queryClient = useQueryClient();
	const sessionContext = useContext(AdminSessionContext);
	const userInfo = useUserInfo();
	const session = sessionContext ?? {
		role: "AUTHADMIN" as const,
		username: userInfo?.username || userInfo?.fullName || userInfo?.email,
		email: userInfo?.email,
	};
    const {
        data: changeRequests = [],
        isLoading,
        isError,
    } = useQuery<ChangeRequest[]>({
        queryKey: ["admin", "change-requests"],
        queryFn: () => adminApi.getChangeRequests(),
    });
    const { data: adminUsers = [] } = useQuery<AdminUser[]>({
        queryKey: ["admin", "users"],
        queryFn: () => adminApi.getAdminUsers(),
    });

    // 兼容性兜底：若后端仅返回“审批请求”而未返回“变更请求”，
    // 则从 /approval-requests 拉取并映射为 ChangeRequest 以便列表展示
    const { data: mappedFromApprovals = [] } = useQuery<ChangeRequest[]>({
        queryKey: ["admin", "kc-approvals-mapped"],
        queryFn: async () => {
            try {
                const list = await KeycloakApprovalService.getApprovalRequests();
                const out: ChangeRequest[] = [];
                // 按需拉取详情，以提取 payload 中的 changeRequestId
                for (const item of list || []) {
                    try {
                        const detail = await KeycloakApprovalService.getApprovalRequestById(item.id);
                        if (!detail?.items?.length) continue;
                        for (const it of detail.items) {
                            if (!it?.payload) continue;
                            let crid: number | null = null;
                            let payload: any = null;
                            try { payload = JSON.parse(it.payload); crid = Number(payload?.changeRequestId ?? NaN); } catch {}
                            if (!crid || Number.isNaN(crid)) continue;
                            // 已存在的变更请求以后端结果为准
                            if (Array.isArray(changeRequests) && changeRequests.some((x) => x.id === crid)) continue;
                            const resource = String(it.targetKind || "").toUpperCase();
                            const action = String(payload?.action || detail.type || "").toUpperCase();
                            out.push({
                                id: crid,
                                resourceType: resource || inferResourceTypeFromAction(action),
                                resourceId: it.targetId,
                                action,
                                payloadJson: it.payload,
                                diffJson: undefined,
                                status: (detail.status || "PENDING").toUpperCase(),
                                requestedBy: detail.requester || "",
                                requestedAt: detail.createdAt,
                                decidedBy: detail.approver || undefined,
                                decidedAt: detail.decidedAt || undefined,
                                reason: detail.reason || undefined,
                                category: detail.category || undefined,
                                lastError: detail.errorMessage || undefined,
                            });
                        }
                    } catch {
                        // ignore single item failure and continue
                    }
                }
                return out;
            } catch {
                return [] as ChangeRequest[];
            }
        },
    });

    const combinedChangeRequests = useMemo<ChangeRequest[]>(() => {
        if (!Array.isArray(mappedFromApprovals) || mappedFromApprovals.length === 0) return changeRequests;
        const map = new Map<number, ChangeRequest>();
        for (const cr of changeRequests || []) map.set(cr.id, cr);
        for (const cr of mappedFromApprovals) if (!map.has(cr.id)) map.set(cr.id, cr);
        return Array.from(map.values());
    }, [changeRequests, mappedFromApprovals]);

    const [decisions, setDecisions] = useState<Record<number, DecisionRecord>>({});
    const [categoryFilter, setCategoryFilter] = useState<TaskCategory>(CATEGORY_ORDER[0]);
    const [activeTaskId, setActiveTaskId] = useState<number | null>(null);
    const [decisionLoading, setDecisionLoading] = useState(false);
    const [operatorNameMap, setOperatorNameMap] = useState<Record<string, string>>({});
    const [roleDisplayNameMap, setRoleDisplayNameMap] = useState<Record<string, string>>({});
    const [userDisplayMap, setUserDisplayMap] = useState<Record<string, string>>({});

    // 载入角色中文名映射（来自管理端角色目录）
    useEffect(() => {
        (async () => {
            try {
                const roles = await adminApi.getAdminRoles();
                const map: Record<string, string> = {};
                for (const r of roles || []) {
                    const display = (r as any).nameZh || (r as any).displayName || (r as any).name || "";
                    const codeKeys = [
                        (r as any).name,
                        (r as any).code,
                        (r as any).roleId,
                        (r as any).legacyName,
                    ];
                    for (const k of codeKeys) {
                        const key = (k || "").toString().trim().toUpperCase();
                        if (key && display) map[key] = String(display);
                    }
                }
                setRoleDisplayNameMap(map);
            } catch {
                // ignore
            }
        })();
    }, []);

    // 收集请求中的用户名，并解析为中文姓名（优先后端接口，其次 Keycloak 搜索）
    useEffect(() => {
        const usernames = new Set<string>();
        const add = (u?: string | null) => {
            const v = (u || "").trim();
            if (v) usernames.add(v);
        };
        for (const cr of combinedChangeRequests || []) {
            add(cr.requestedBy);
            add(cr.decidedBy as any);
            if ((cr.resourceType || "").toUpperCase() === "USER") add(String(cr.resourceId || ""));
            const payload = asRecord(parseJson(cr.payloadJson));
            const diff = asRecord(parseJson(cr.diffJson));
            const after = diff && asRecord(diff.after);
            const scan = (obj: any) => {
                if (!obj || typeof obj !== "object") return;
                for (const [k, v] of Object.entries(obj)) {
                    if (k === "username" && typeof v === "string") add(v);
                    if (v && typeof v === "object") scan(v);
                }
            };
            scan(payload);
            scan(after);
        }
        const all = Array.from(usernames);
        const need = all.filter((u) => userDisplayMap[u] == null && userDisplayMap[u.toLowerCase?.() || u] == null);
        if (need.length === 0) return;
        (async () => {
            try {
                const data = await adminApi.resolveUserDisplayNames(need);
                if (data && typeof data === "object") {
                    setUserDisplayMap((prev) => ({ ...prev, ...data }));
                    return;
                }
            } catch {
                // Fallback: Keycloak 搜索
            }
            const updates: Record<string, string> = {};
            for (const u of need) {
                try {
                    const list = await KeycloakUserService.searchUsers(u);
                    const match = (list || []).find((x) => (x?.username || "").toLowerCase() === u.toLowerCase());
                    const name =
                        (match?.fullName || match?.firstName || match?.lastName || match?.attributes?.fullname?.[0] || u).toString();
                    updates[u] = name;
                    updates[u.toLowerCase()] = name;
                } catch {
                    // ignore single failure
                }
            }
            if (Object.keys(updates).length) setUserDisplayMap((prev) => ({ ...prev, ...updates }));
        })();
    }, [combinedChangeRequests, userDisplayMap]);

    useEffect(() => {
        if (!Array.isArray(adminUsers) || adminUsers.length === 0) return;
        setOperatorNameMap((prev) => {
            let changed = false;
            const next = { ...prev };
            for (const user of adminUsers) {
                const username = (user?.username || "").trim();
                if (!username) continue;
                const display = (user.fullName || user.displayName || username).trim();
                if (!display) continue;
                if (next[username] !== display) {
                    next[username] = display;
                    changed = true;
                }
                const lower = username.toLowerCase();
                if (next[lower] !== display) {
                    next[lower] = display;
                    changed = true;
                }
            }
            return changed ? next : prev;
        });
    }, [adminUsers]);

    const augmentedRequests = useMemo<AugmentedChangeRequest[]>(() => {
        return combinedChangeRequests.map((item) => {
            const override = decisions[item.id];
            const effectiveStatus = override?.status ?? normalizeStatus(item.status);
            return {
                ...item,
                effectiveStatus,
                effectiveDecidedAt: override?.decidedAt ?? item.decidedAt ?? null,
                effectiveDecidedBy: override?.decidedBy ?? item.decidedBy ?? null,
                override,
            };
        });
    }, [combinedChangeRequests, decisions]);

    function inferResourceTypeFromAction(action: string | undefined): string {
        const a = (action || "").toUpperCase();
        if (a.includes("ROLE")) return "ROLE";
        return "USER";
    }

    const pendingGroups = useMemo(() => {
        const groups: Record<TaskCategory, AugmentedChangeRequest[]> = {
            user: [],
            role: [],
        };
        for (const item of augmentedRequests) {
            const category = resolveCategory(item.resourceType);
            if (!category) continue;
            if (
                item.effectiveStatus === "APPROVED" ||
                item.effectiveStatus === "APPLIED" ||
                item.effectiveStatus === "REJECTED" ||
                (item.status && item.status.toUpperCase() === "FAILED")
            )
                continue;
            groups[category].push(item);
        }
        const byTimeDesc = (a: AugmentedChangeRequest, b: AugmentedChangeRequest) => {
            const ta = a.requestedAt ? new Date(a.requestedAt).getTime() : 0;
            const tb = b.requestedAt ? new Date(b.requestedAt).getTime() : 0;
            if (tb !== ta) return tb - ta;
            return (b.id || 0) - (a.id || 0);
        };
        groups.user.sort(byTimeDesc);
        groups.role.sort(byTimeDesc);
        return groups;
    }, [augmentedRequests]);

    const completedGroups = useMemo(() => {
        const groups: Record<TaskCategory, AugmentedChangeRequest[]> = {
            user: [],
            role: [],
        };
        for (const item of augmentedRequests) {
            const category = resolveCategory(item.resourceType);
            if (!category) continue;
            if (
                item.effectiveStatus === "APPROVED" ||
                item.effectiveStatus === "APPLIED" ||
                item.effectiveStatus === "REJECTED" ||
                (item.status && item.status.toUpperCase() === "FAILED")
            ) {
                groups[category].push(item);
            }
        }
        const byTimeDesc = (a: AugmentedChangeRequest, b: AugmentedChangeRequest) => {
            const ta = a.requestedAt ? new Date(a.requestedAt).getTime() : 0;
            const tb = b.requestedAt ? new Date(b.requestedAt).getTime() : 0;
            if (tb !== ta) return tb - ta;
            return (b.id || 0) - (a.id || 0);
        };
        groups.user.sort(byTimeDesc);
        groups.role.sort(byTimeDesc);
        return groups;
    }, [augmentedRequests]);

	const categoriesWithData = useMemo(() => {
		return CATEGORY_ORDER.filter((category) => {
			return pendingGroups[category].length > 0 || completedGroups[category].length > 0;
		});
	}, [pendingGroups, completedGroups]);

	useEffect(() => {
		if (categoriesWithData.length === 0) {
			if (categoryFilter !== CATEGORY_ORDER[0]) {
				setCategoryFilter(CATEGORY_ORDER[0]);
			}
			return;
		}
		if (!categoriesWithData.includes(categoryFilter)) {
			setCategoryFilter(categoriesWithData[0]);
		}
	}, [categoriesWithData, categoryFilter]);

	const selectedCategory = categoryFilter;
	const selectedLabel = CATEGORY_LABELS[selectedCategory];
	const selectedPending = pendingGroups[selectedCategory] ?? [];
	const selectedCompleted = completedGroups[selectedCategory] ?? [];
    const activeTask = useMemo(
        () => augmentedRequests.find((item) => item.id === activeTaskId) ?? null,
        [augmentedRequests, activeTaskId],
    );

    // 加载“操作人”的中文姓名（fullName），缓存到 operatorNameMap
    useEffect(() => {
        const usernames = new Set<string>();
        const collect = (value: unknown) => {
            if (value === null || value === undefined) {
                return;
            }
            const normalized = String(value).trim();
            if (normalized) {
                usernames.add(normalized);
            }
        };
        for (const it of combinedChangeRequests || []) {
            collect(it?.requestedBy);
            collect((it as any)?.decidedBy);
        }
        const need = Array.from(usernames).filter((u) => {
            if (!u) return false;
            const lowered = u.toLowerCase();
            return operatorNameMap[u] === undefined && operatorNameMap[lowered] === undefined;
        });
        if (need.length === 0) return;
        let cancelled = false;
        (async () => {
            try {
                const resolved = await adminApi.resolveUserDisplayNames(need);
                const updates: Record<string, string> = {};
                for (const username of need) {
                    const key = username.trim();
                    const lowered = key.toLowerCase();
                    const candidate = resolved?.[key] ?? resolved?.[lowered] ?? key;
                    const display = candidate && String(candidate).trim().length > 0 ? String(candidate).trim() : key;
                    updates[key] = display;
                    updates[lowered] = display;
                }
                if (!cancelled && Object.keys(updates).length) {
                    setOperatorNameMap((prev) => ({ ...prev, ...updates }));
                }
            } catch {
                if (cancelled) return;
                const fallbacks: Record<string, string> = {};
                for (const username of need) {
                    const key = username.trim();
                    const lowered = key.toLowerCase();
                    fallbacks[key] = key;
                    fallbacks[lowered] = key;
                }
                setOperatorNameMap((prev) => ({ ...prev, ...fallbacks }));
            }
        })();
        return () => {
            cancelled = true;
        };
    }, [combinedChangeRequests, operatorNameMap]);

    const pendingColumns = useMemo<ColumnsType<AugmentedChangeRequest>>(
        () => [
            {
                title: "操作编号",
                dataIndex: "id",
                width: 120,
                render: (id: number) => <span className="font-medium">CR-{id}</span>,
            },
            {
                title: "操作类型",
                dataIndex: "action",
                width: 160,
                render: (_: unknown, record) => getActionText(record),
            },
            {
                title: "变更前",
                dataIndex: "diffJson",
                ellipsis: true,
                width: 280,
                render: (_: unknown, record) => renderDiffSide(record, "before", { roleDisplay: roleDisplayNameMap, userDisplay: userDisplayMap }),
            },
            {
                title: "变更后",
                dataIndex: "diffJson2",
                ellipsis: true,
                width: 280,
                render: (_: unknown, record) => renderDiffSide(record, "after", { roleDisplay: roleDisplayNameMap, userDisplay: userDisplayMap }),
            },
            {
                title: "影响对象",
                dataIndex: "resourceId",
                width: 160,
                render: (_: unknown, record) => resolveTarget(record, { userDisplay: userDisplayMap }),
            },
            {
				title: "操作人",
				dataIndex: "requestedBy",
				width: 140,
				render: (_: unknown, record) => (
					<span className="text-xs">{resolveOperatorDisplayName(record.requestedBy, operatorNameMap)}</span>
				),
			},
			{
				title: "操作时间",
				dataIndex: "requestedAt",
				width: 180,
				render: (_: unknown, record) => <span className="text-xs">{formatDateTime(record.requestedAt)}</span>,
			},
			{
				title: "当前状态",
				dataIndex: "effectiveStatus",
				width: 140,
				render: (_: unknown, record) => (
					<Badge variant={getStatusBadgeVariant(record.effectiveStatus)}>
						{getStatusLabel(record.effectiveStatus)}
					</Badge>
				),
			},
			{
				title: "操作",
				key: "actions",
				width: 120,
				fixed: "right" as const,
				align: "right" as const,
				render: (_: unknown, record) => (
					<Button
						size="sm"
						variant="outline"
						onClick={() => setActiveTaskId(record.id)}
						disabled={decisionLoading && activeTaskId === record.id}
					>
						操作
					</Button>
				),
            },
        ],
        [activeTaskId, decisionLoading, operatorNameMap, roleDisplayNameMap, userDisplayMap],
    );

    const completedColumns = useMemo<ColumnsType<AugmentedChangeRequest>>(
        () => [
            {
                title: "操作编号",
                dataIndex: "id",
                width: 120,
                render: (id: number) => <span className="font-medium">CR-{id}</span>,
            },
            {
                title: "操作类型",
                dataIndex: "action",
                width: 160,
                render: (_: unknown, record) => getActionText(record),
            },
            {
                title: "变更前",
                dataIndex: "diffJson",
                ellipsis: true,
                width: 280,
                render: (_: unknown, record) => renderDiffSide(record, "before", { roleDisplay: roleDisplayNameMap, userDisplay: userDisplayMap }),
            },
            {
                title: "变更后",
                dataIndex: "diffJson2",
                ellipsis: true,
                width: 280,
                render: (_: unknown, record) => renderDiffSide(record, "after", { roleDisplay: roleDisplayNameMap, userDisplay: userDisplayMap }),
            },
            {
                title: "影响对象",
                dataIndex: "resourceId",
                width: 160,
                render: (_: unknown, record) => resolveTarget(record, { userDisplay: userDisplayMap }),
            },
            {
                title: "操作人",
                dataIndex: "effectiveDecidedBy",
                width: 140,
                render: (_: unknown, record) => {
                    return (
                        <span className="text-xs">
                            {resolveOperatorDisplayName(record.effectiveDecidedBy, operatorNameMap)}
                        </span>
                    );
                },
            },
			{
				title: "操作时间",
				dataIndex: "effectiveDecidedAt",
				width: 180,
				render: (_: unknown, record) => <span className="text-xs">{formatDateTime(record.effectiveDecidedAt)}</span>,
			},
			{
				title: "处理结果",
				dataIndex: "effectiveStatus",
				width: 140,
				render: (_: unknown, record) => (
					<Badge variant={getStatusBadgeVariant(record.effectiveStatus)}>
						{getStatusLabel(record.effectiveStatus)}
					</Badge>
				),
			},
        ],
        [operatorNameMap, roleDisplayNameMap, userDisplayMap],
    );


    const handleDecision = async (status: DecisionStatus) => {
        if (!activeTask) return;
        setDecisionLoading(true);
        const decisionId = activeTask.id;
        const decidedBy = session.username ?? session.email ?? "authadmin";
        try {
            const resourceType = (activeTask.resourceType || "").toUpperCase();
            const actionType = (activeTask.action || "").toUpperCase();
            const useKeycloakApproval =
                resourceType === "USER" ||
                (resourceType === "ROLE" && ["GRANT_ROLE", "REVOKE_ROLE"].includes(actionType));

            if (status === "APPROVED") {
                if (useKeycloakApproval) {
                    await KeycloakApprovalService.approveByChangeRequest(decisionId, decidedBy, "批准");
                } else {
                    await adminApi.approveChangeRequest(decisionId, "批准");
                }
            } else if (status === "REJECTED") {
                if (useKeycloakApproval) {
                    await KeycloakApprovalService.rejectByChangeRequest(decisionId, decidedBy, "拒绝");
                } else {
                    await adminApi.rejectChangeRequest(decisionId, "拒绝");
                }
            } else {
                // 待定：仅前端标注，方便继续处理
            }

            setDecisions((prev) => ({
                ...prev,
                [decisionId]: {
                    status,
                    decidedAt:
                        status === "APPROVED" || status === "REJECTED" || status === "APPLIED"
                            ? new Date().toISOString()
                            : null,
                    decidedBy,
                },
            }));
            if (decidedBy) {
    const fullName = userInfo?.fullName || userInfo?.username;
                if (fullName && fullName.trim().length > 0) {
                    setOperatorNameMap((prev) => {
                        const display = fullName.trim();
                        const existing = prev[decidedBy] || prev[decidedBy.toLowerCase()];
                        if (existing && existing === display) {
                            return prev;
                        }
                        return {
                            ...prev,
                            [decidedBy]: display,
                            [decidedBy.toLowerCase()]: display,
                        };
                    });
                }
            }
            toast.success(status === "APPROVED" ? "已批准该变更请求" : status === "REJECTED" ? "已拒绝该变更请求" : "已将该请求标记为待定");
            // 刷新变更请求列表
            await queryClient.invalidateQueries({ queryKey: ["admin", "change-requests"] });
            // 若为菜单/角色相关的本地可见性更新，联动刷新菜单与角色面板缓存
            try {
                await queryClient.invalidateQueries({ queryKey: ["admin", "portal-menus"] });
                await queryClient.invalidateQueries({ queryKey: ["admin", "role-assignments"] });
                await queryClient.invalidateQueries({ queryKey: ["admin", "roles"] });
            } catch {}
        } catch (e: any) {
            toast.error(e?.message || "审批操作失败");
        } finally {
            setDecisionLoading(false);
            setActiveTaskId(null);
        }
    };

	const handleCloseDialog = () => {
		if (!decisionLoading) {
			setActiveTaskId(null);
		}
	};

	const activePayload = useMemo(() => (activeTask ? asRecord(parseJson(activeTask.payloadJson)) : null), [activeTask]);
	const activeDiff = useMemo(() => (activeTask ? asRecord(parseJson(activeTask.diffJson)) : null), [activeTask]);
	const diffBefore = useMemo(() => (activeDiff ? asRecord(activeDiff["before"]) : null), [activeDiff]);
	const diffAfter = useMemo(() => (activeDiff ? asRecord(activeDiff["after"]) : null), [activeDiff]);

	return (
		<>
			<div className="space-y-6">
				<Card>
					<CardHeader className="space-y-2">
						<div className="flex flex-wrap items-center gap-3">
							<CardTitle>待审批任务</CardTitle>
							<Select value={selectedCategory} onValueChange={(value) => setCategoryFilter(value as TaskCategory)}>
								<SelectTrigger className="w-44">
									<SelectValue placeholder="请选择审批类型" />
								</SelectTrigger>
								<SelectContent>
									{CATEGORY_ORDER.map((category) => (
										<SelectItem key={category} value={category}>
											{CATEGORY_LABELS[category]}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						</div>
						<Text variant="body3" className="text-muted-foreground">
							展示最新待处理的变更请求，切换筛选以查看不同类型。
						</Text>
					</CardHeader>
					<CardContent className="space-y-3">
						{isLoading ? (
							<Text variant="body3" className="text-muted-foreground">
								数据加载中…
							</Text>
						) : isError ? (
							<Text variant="body3" className="text-destructive">
								加载审批列表失败，请稍后重试。
							</Text>
						) : (
							<>
								<div className="flex items-center justify-between">
									<Text variant="body2" className="font-semibold">
										{selectedLabel}
									</Text>
									<Badge variant="secondary">{selectedPending.length}</Badge>
								</div>
								{selectedPending.length === 0 ? (
									<Text variant="body3" className="text-muted-foreground">
										暂无待审批条目。
									</Text>
								) : (
                                <Table
                                    rowKey="id"
                                    columns={pendingColumns}
                                    dataSource={selectedPending}
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
                                    scroll={{ x: 1360 }}
                                />
								)}
							</>
						)}
					</CardContent>
				</Card>

				<Card>
					<CardHeader className="space-y-2">
						<CardTitle>已完成审批</CardTitle>
						<Text variant="body3" className="text-muted-foreground">
							已处理的审批会记录审批人、时间以及结论。
						</Text>
					</CardHeader>
					<CardContent className="space-y-3">
						{isLoading ? (
							<Text variant="body3" className="text-muted-foreground">
								数据加载中…
							</Text>
						) : isError ? (
							<Text variant="body3" className="text-destructive">
								加载审批列表失败，请稍后重试。
							</Text>
						) : (
							<>
								<div className="flex items-center justify-between">
									<Text variant="body2" className="font-semibold">
										{selectedLabel}
									</Text>
									<Badge variant="outline">{selectedCompleted.length}</Badge>
								</div>
								{selectedCompleted.length === 0 ? (
									<Text variant="body3" className="text-muted-foreground">
										暂无历史审批记录。
									</Text>
								) : (
                                <Table
                                    rowKey="id"
                                    columns={completedColumns}
                                    dataSource={selectedCompleted}
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
                                    scroll={{ x: 1360 }}
                                />
								)}
							</>
						)}
					</CardContent>
				</Card>
			</div>

			<Dialog open={Boolean(activeTask)} onOpenChange={(open) => (!open ? handleCloseDialog() : null)}>
				<DialogContent className="max-w-3xl">
					<DialogHeader>
						<DialogTitle>审批详情</DialogTitle>
						<DialogDescription>请核对变更内容后选择处理操作。</DialogDescription>
					</DialogHeader>
					{activeTask ? (
						<div className="space-y-5 text-sm max-h-[60vh] overflow-y-auto pr-1">
							<div className="grid gap-4 sm:grid-cols-2">
								<div className="space-y-1">
									<Text variant="body3" className="text-muted-foreground">
										操作编号
									</Text>
									<div className="font-medium">CR-{activeTask.id}</div>
								</div>
								<div className="space-y-1">
									<Text variant="body3" className="text-muted-foreground">
										当前状态
									</Text>
									<Badge variant={getStatusBadgeVariant(activeTask.effectiveStatus)}>
										{getStatusLabel(activeTask.effectiveStatus)}
									</Badge>
								</div>
								<div className="space-y-1">
									<Text variant="body3" className="text-muted-foreground">
										操作内容
									</Text>
									<div className="font-medium">{getActionText(activeTask)}</div>
									<div className="text-xs text-muted-foreground">{summarizeDetails(activeTask)}</div>
								</div>
								<div className="space-y-1">
									<Text variant="body3" className="text-muted-foreground">
										影响对象
									</Text>
                            <div className="font-medium">{resolveTarget(activeTask, { userDisplay: userDisplayMap })}</div>
								</div>
								<div className="space-y-1">
									<Text variant="body3" className="text-muted-foreground">
										提交人
									</Text>
									<div>{resolveOperatorDisplayName(activeTask.requestedBy, operatorNameMap)}</div>
								</div>
								<div className="space-y-1">
									<Text variant="body3" className="text-muted-foreground">
										提交时间
									</Text>
									<div>{formatDateTime(activeTask.requestedAt)}</div>
								</div>
							</div>

							{activePayload && Object.keys(activePayload).length > 0 ? (
								<div className="space-y-2">
									<Text variant="body3" className="text-muted-foreground">
										提交内容
									</Text>
									<pre className="max-h-64 overflow-auto rounded-md bg-muted/40 p-3 text-xs whitespace-pre-wrap break-words">
										{formatJson(activePayload)}
									</pre>
								</div>
							) : null}

							{(diffBefore && Object.keys(diffBefore).length > 0) ||
							(diffAfter && Object.keys(diffAfter).length > 0) ? (
								<div className="space-y-3">
									<Text variant="body3" className="text-muted-foreground">
										差异信息
									</Text>
									{diffBefore && Object.keys(diffBefore).length > 0 ? (
										<div className="space-y-1">
											<Text variant="body3" className="text-muted-foreground">
												变更前
											</Text>
											<pre className="max-h-48 overflow-auto rounded-md bg-muted/40 p-3 text-xs whitespace-pre-wrap break-words">
												{formatJson(diffBefore)}
											</pre>
										</div>
									) : null}
									{diffAfter && Object.keys(diffAfter).length > 0 ? (
										<div className="space-y-1">
											<Text variant="body3" className="text-muted-foreground">
												变更后
											</Text>
											<pre className="max-h-48 overflow-auto rounded-md bg-muted/40 p-3 text-xs whitespace-pre-wrap break-words">
												{formatJson(diffAfter)}
											</pre>
										</div>
									) : null}
								</div>
							) : null}
						</div>
					) : null}
					<DialogFooter>
						<Button
							type="button"
							variant="outline"
							onClick={() => handleDecision("ON_HOLD")}
							disabled={decisionLoading || !activeTask}
						>
							{decisionLoading ? "处理中..." : "待定"}
						</Button>
						<Button
							type="button"
							variant="destructive"
							onClick={() => handleDecision("REJECTED")}
							disabled={decisionLoading || !activeTask}
						>
							{decisionLoading ? "处理中..." : "拒绝"}
						</Button>
						<Button type="button" onClick={() => handleDecision("APPROVED")} disabled={decisionLoading || !activeTask}>
							{decisionLoading ? "处理中..." : "批准"}
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</>
	);
}
