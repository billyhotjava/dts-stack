import { useCallback, useContext, useEffect, useMemo, useState } from "react";
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
import {
	ChangeDiffViewer,
	buildChangeSnapshotFromDiff,
	type ChangeSummaryEntry,
	type ChangeSnapshotLike,
} from "@/admin/components/change-diff-viewer";
import { MenuChangeViewer, type MenuChangeDisplayEntry } from "@/admin/components/menu-change-viewer";
import {
	extractMenuChanges,
	filterMenuSummaryRows,
	pruneMenuSnapshot,
	snapshotHasContent,
} from "@/admin/utils/menu-change-parser";
import { formatChangeValue, labelForChangeField, type ChangeRequestFormatContext } from "@/admin/lib/change-request-format";

type TaskCategory = "user" | "role" | "menu";

const CATEGORY_LABELS: Record<TaskCategory, string> = {
	user: "用户管理",
	role: "角色管理",
	menu: "菜单管理",
};

const USER_RESOURCE_TYPES = new Set(["USER"]);
const ROLE_RESOURCE_TYPES = new Set(["ROLE", "CUSTOM_ROLE", "ROLE_ASSIGNMENT"]);
const MENU_RESOURCE_TYPES = new Set(["PORTAL_MENU", "MENU"]);

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

type DecisionStatus = "PENDING" | "PROCESSING" | "ON_HOLD" | "APPROVED" | "APPLIED" | "REJECTED" | "FAILED";

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

const CATEGORY_ORDER: TaskCategory[] = ["user", "role", "menu"];

function resolveCategory(request: ChangeRequest): TaskCategory | null {
	const categoryHint = request.category ? request.category.trim().toUpperCase() : null;
	if (categoryHint === "USER_MANAGEMENT") {
		return "user";
	}
	if (categoryHint === "ROLE_MANAGEMENT") {
		return "role";
	}
	if (categoryHint === "MENU_MANAGEMENT") {
		return "menu";
	}

	const resourceType = request.resourceType;
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
	if (MENU_RESOURCE_TYPES.has(normalized)) {
		return "menu";
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

type DiffFormatContext = ChangeRequestFormatContext;

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

function summarizeDetails(request: ChangeRequest, ctx?: DiffFormatContext): string {
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
				.map((k) => formatChangeEntry(request, k, (before as any)[k], (after as any)[k], ctx))
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
			.map(([key, value]) => formatSingleEntry(request, key, value, ctx))
			.join("；");
	}
	if (diff) {
		const before = asRecord(diff.before) || {};
		const after = asRecord(diff.after) || {};
		const keys = Array.from(new Set([...Object.keys(before), ...Object.keys(after)]));
		const changed = keys.filter((key) => JSON.stringify((before as any)[key]) !== JSON.stringify((after as any)[key]));
		if (changed.length === 0) {
			return JSON.stringify(diff);
		}
		return changed
			.slice(0, 3)
			.map((key) => formatChangeEntry(request, key, (before as any)[key], (after as any)[key], ctx))
			.join("；");
	}
	return "—";
}

// 与 summarizeDiffSide 类似，但返回 JSX，并在“变更后”用红色高亮变动值
function isPortalMenuRequest(request: ChangeRequest): boolean {
	const resource = (request.resourceType || request.category || "").toString().toUpperCase();
	return resource === "PORTAL_MENU" || resource === "MENU" || resource === "MENU_MANAGEMENT";
}

function formatMenuStatus(value: unknown): string {
	if (value === true || value === "true") return "禁用";
	if (value === false || value === "false" || value == null) return "启用";
	return fmtValue(value);
}

function fmtValue(v: unknown): string {
	if (v == null) return "—";
	if (Array.isArray(v)) return `[${v.map((x) => (typeof x === "string" ? x : JSON.stringify(x))).join(", ")}]`;
	if (typeof v === "string") return v.replaceAll("7f3868a1-9c8c-4122-b7e4-7f921a40c019", "***");
	try {
		return JSON.stringify(v);
	} catch (error) {
		console.warn("Failed to stringify value", error, v);
		return String(v);
	}
}

function formatChangeEntry(
	request: ChangeRequest,
	key: string,
	before: unknown,
	after: unknown,
	ctx?: DiffFormatContext,
): string {
	if (isPortalMenuRequest(request) && key === "deleted") {
		const label = "状态";
		const beforeDefined = before !== undefined;
		const beforeLabel = beforeDefined ? formatMenuStatus(before) : null;
		const afterLabel = formatMenuStatus(after);
		return beforeDefined ? `${label}: ${beforeLabel} → ${afterLabel}` : `${label}: ${afterLabel}`;
	}
	const label = labelForChangeField(key);
	const beforeLabel = before !== undefined ? formatChangeValue(key, before, ctx) : null;
	const afterLabel = formatChangeValue(key, after, ctx);
	return beforeLabel != null ? `${label}: ${beforeLabel} → ${afterLabel}` : `${label}: ${afterLabel}`;
}

function formatSingleEntry(request: ChangeRequest, key: string, value: unknown, ctx?: DiffFormatContext): string {
	if (isPortalMenuRequest(request) && key === "deleted") {
		return `状态: ${formatMenuStatus(value)}`;
	}
	const label = labelForChangeField(key);
	return `${label}: ${formatChangeValue(key, value, ctx)}`;
}

function getActionText(request: ChangeRequest): string {
	const actionLabel = ACTION_LABELS[request.action?.toUpperCase()] ?? request.action;
	const category = resolveCategory(request);
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

interface ChangeDisplayContext {
	snapshot: ChangeSnapshotLike | null;
	summary: ChangeSummaryEntry[];
	menuChanges: MenuChangeDisplayEntry[];
}

function buildChangeDisplayContext(
	diff?: Record<string, unknown> | null,
	payload?: Record<string, unknown> | null,
): ChangeDisplayContext {
	const baseLayers: Array<Record<string, unknown> | null | undefined> = [];
	if (diff) {
		baseLayers.push(diff);
		baseLayers.push(coerceRecord(diff["detail"]));
		baseLayers.push(coerceRecord(diff["context"]));
		baseLayers.push(coerceRecord(diff["metadata"]));
		baseLayers.push(coerceRecord(diff["extraAttributes"]));
	}
	baseLayers.push(payload);

	const menuChanges = extractMenuChanges(baseLayers);
	const summary = collectSummaryEntries(baseLayers);
	const filteredSummary = menuChanges.length > 0 ? filterMenuSummaryRows(summary) : summary;

	const rawSnapshot = buildChangeSnapshotFromDiff(diff);
	const snapshot = menuChanges.length > 0 ? pruneMenuSnapshot(rawSnapshot) : rawSnapshot;

	return {
		snapshot,
		summary: filteredSummary,
		menuChanges,
	};
}

function collectSummaryEntries(layers: Array<Record<string, unknown> | null | undefined>): ChangeSummaryEntry[] {
	const result: ChangeSummaryEntry[] = [];
	const seen = new Set<string>();
	for (const layer of layers) {
		if (!layer) continue;
		const candidates = [layer["changeSummary"], layer["change_summary"], layer["summary"]];
		for (const candidate of candidates) {
			const entries = parseSummaryEntryList(candidate);
			for (const entry of entries) {
				const key = `${entry.field ?? entry.label ?? ""}|${JSON.stringify(entry.before)}|${JSON.stringify(entry.after)}`;
				if (seen.has(key)) continue;
				result.push(entry);
				seen.add(key);
			}
		}
	}
	return result;
}

function parseSummaryEntryList(value: unknown): ChangeSummaryEntry[] {
	const array = coerceArray(value);
	if (!array) {
		return [];
	}
	const rows: ChangeSummaryEntry[] = [];
	array.forEach((item, index) => {
		const record = coerceRecord(item);
		if (!record) return;
		const fieldRaw = record["field"] ?? record["code"] ?? record["name"] ?? record["key"] ?? `field_${index}`;
		const field = typeof fieldRaw === "string" ? fieldRaw.trim() : fieldRaw != null ? String(fieldRaw) : "";
		const labelRaw = record["label"] ?? record["title"] ?? record["name"];
		const label = typeof labelRaw === "string" && labelRaw.trim().length > 0 ? labelRaw.trim() : deriveFieldLabel(field);
		rows.push({
			field: field || label,
			label,
			before: record["before"],
			after: record["after"],
		});
	});
	return rows;
}

function coerceRecord(value: unknown): Record<string, unknown> | null {
	if (!value) {
		return null;
	}
	if (typeof value === "string") {
		return asRecord(parseJson(value));
	}
	if (typeof value === "object" && !Array.isArray(value)) {
		return value as Record<string, unknown>;
	}
	return null;
}

function coerceArray(value: unknown): unknown[] | null {
	if (!value) {
		return null;
	}
	if (Array.isArray(value)) {
		return value;
	}
	if (typeof value === "string") {
		const parsed = parseJson(value);
		return Array.isArray(parsed) ? parsed : null;
	}
	return null;
}

function deriveFieldLabel(field?: string): string {
	if (!field) {
		return "字段";
	}
	const replaced = field.replace(/[_\-.]+/g, " ").trim();
	if (!replaced) {
		return field;
	}
	return replaced
		.split(" ")
		.filter(Boolean)
		.map((token) => token.charAt(0).toUpperCase() + token.slice(1))
		.join(" ");
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
	const normalizedRole = String(session.role ?? "").toUpperCase();
	const isSysAdmin = normalizedRole === "SYSADMIN";
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
		enabled: isSysAdmin,
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
						const detail = await KeycloakApprovalService.getApprovalRequestById(item.id, { silent: true });
						if (!detail?.items?.length) continue;
						for (const it of detail.items) {
							if (!it?.payload) continue;
							let crid: number | null = null;
							let payload: any = null;
							try {
								payload = JSON.parse(it.payload);
								crid = Number(payload?.changeRequestId ?? NaN);
							} catch {}
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
	const [categoryInitialized, setCategoryInitialized] = useState(false);
	const [activeTaskId, setActiveTaskId] = useState<number | null>(null);
	const [decisionLoading, setDecisionLoading] = useState(false);
	const [operatorNameMap, setOperatorNameMap] = useState<Record<string, string>>({});
	const [userDisplayMap, setUserDisplayMap] = useState<Record<string, string>>({});

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
					const name = (
						match?.fullName ||
						match?.firstName ||
						match?.lastName ||
						match?.attributes?.fullName?.[0] ||
						u
					).toString();
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
			menu: [],
		};
		for (const item of augmentedRequests) {
			const category = resolveCategory(item);
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
		groups.menu.sort(byTimeDesc);
		return groups;
	}, [augmentedRequests]);

	const completedGroups = useMemo(() => {
		const groups: Record<TaskCategory, AugmentedChangeRequest[]> = {
			user: [],
			role: [],
			menu: [],
		};
		for (const item of augmentedRequests) {
			const category = resolveCategory(item);
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
		groups.menu.sort(byTimeDesc);
		return groups;
	}, [augmentedRequests]);

	useEffect(() => {
		if (categoryInitialized) return;
		const firstWithData = CATEGORY_ORDER.find((category) => {
			return (pendingGroups[category]?.length ?? 0) > 0 || (completedGroups[category]?.length ?? 0) > 0;
		});
		if (firstWithData && firstWithData !== categoryFilter) {
			setCategoryFilter(firstWithData);
		}
		setCategoryInitialized(true);
	}, [categoryInitialized, pendingGroups, completedGroups, categoryFilter]);

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
				title: "变更摘要",
				dataIndex: "summary",
				ellipsis: true,
				render: (_: unknown, record) => <span title={summarizeDetails(record)}>{summarizeDetails(record) || "—"}</span>,
			},
			{
				title: "影响对象",
				dataIndex: "resourceId",
				width: 180,
				render: (_: unknown, record) => resolveTarget(record, { userDisplay: userDisplayMap }),
			},
			{
				title: "提交人",
				dataIndex: "requestedBy",
				width: 140,
				render: (_: unknown, record) => (
					<span className="text-xs">{resolveOperatorDisplayName(record.requestedBy, operatorNameMap)}</span>
				),
			},
			{
				title: "提交时间",
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
		[activeTaskId, decisionLoading, operatorNameMap, userDisplayMap],
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
				title: "变更摘要",
				dataIndex: "summary",
				ellipsis: true,
				render: (_: unknown, record) => <span title={summarizeDetails(record)}>{summarizeDetails(record) || "—"}</span>,
			},
			{
				title: "影响对象",
				dataIndex: "resourceId",
				width: 180,
				render: (_: unknown, record) => resolveTarget(record, { userDisplay: userDisplayMap }),
			},
			{
				title: "审批人",
				dataIndex: "effectiveDecidedBy",
				width: 140,
				render: (_: unknown, record) => (
					<span className="text-xs">{resolveOperatorDisplayName(record.effectiveDecidedBy, operatorNameMap)}</span>
				),
			},
			{
				title: "审批时间",
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
		[operatorNameMap, userDisplayMap],
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
				resourceType === "USER" || (resourceType === "ROLE" && ["GRANT_ROLE", "REVOKE_ROLE"].includes(actionType));

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
						status === "APPROVED" || status === "REJECTED" || status === "APPLIED" ? new Date().toISOString() : null,
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
			toast.success(
				status === "APPROVED"
					? "已批准该变更请求"
					: status === "REJECTED"
						? "已拒绝该变更请求"
						: "已将该请求标记为待定",
			);
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

	const renderExpandedRow = useCallback(
		(record: AugmentedChangeRequest) => {
			const payload = asRecord(parseJson(record.payloadJson));
			const diff = asRecord(parseJson(record.diffJson));
			const displayContext = buildChangeDisplayContext(diff, payload);
			const snapshot = displayContext.snapshot;
			const summary = displayContext.summary;
			const menuChanges = displayContext.menuChanges;
			const showDiffViewer = summary.length > 0 || snapshotHasContent(snapshot);
			return (
				<div className="border-t border-muted pt-4 text-sm">
					<div className="grid gap-4 md:grid-cols-3">
						<div className="space-y-2">
							<Text variant="body3" className="text-muted-foreground">
								基础信息
							</Text>
							<div className="space-y-1 text-xs">
								<div>
									<span className="text-muted-foreground">操作编号：</span>
									CR-{record.id}
								</div>
								<div>
									<span className="text-muted-foreground">操作类型：</span>
									{getActionText(record)}
								</div>
								<div>
									<span className="text-muted-foreground">影响对象：</span>
									{resolveTarget(record, { userDisplay: userDisplayMap })}
								</div>
								<div>
									<span className="text-muted-foreground">提交人：</span>
									{resolveOperatorDisplayName(record.requestedBy, operatorNameMap)}
								</div>
								<div>
									<span className="text-muted-foreground">提交时间：</span>
									{formatDateTime(record.requestedAt)}
								</div>
								<div>
									<span className="text-muted-foreground">当前状态：</span>
									{getStatusLabel(record.effectiveStatus)}
								</div>
								{record.effectiveDecidedBy ? (
									<div>
										<span className="text-muted-foreground">审批人：</span>
										{resolveOperatorDisplayName(record.effectiveDecidedBy, operatorNameMap)}
									</div>
								) : null}
								{record.effectiveDecidedAt ? (
									<div>
										<span className="text-muted-foreground">审批时间：</span>
										{formatDateTime(record.effectiveDecidedAt)}
									</div>
								) : null}
								{record.reason ? (
									<div>
										<span className="text-muted-foreground">备注：</span>
										{record.reason}
									</div>
								) : null}
							</div>
						</div>
						<div className="md:col-span-2">
							<Text variant="body3" className="text-muted-foreground">
								变更详情
							</Text>
							{showDiffViewer ? (
								<div className="mt-2">
									<ChangeDiffViewer
										snapshot={snapshot}
										summary={summary.length > 0 ? summary : undefined}
										action={record.action}
										operationTypeCode={record.action}
										status={record.effectiveStatus}
										className="text-xs"
									/>
								</div>
							) : null}
							{menuChanges.length > 0 ? (
								<div className="mt-3">
									<MenuChangeViewer entries={menuChanges} />
								</div>
							) : null}
						</div>
					</div>
					{payload && Object.keys(payload).length > 0 ? (
						<div className="mt-4 space-y-2">
							<Text variant="body3" className="text-muted-foreground">
								提交内容
							</Text>
							<pre className="max-h-56 overflow-auto rounded-md bg-muted/30 px-3 py-2 text-xs whitespace-pre-wrap">
								{formatJson(payload)}
							</pre>
						</div>
					) : null}
				</div>
			);
		},
		[operatorNameMap, userDisplayMap],
	);

	const handleCloseDialog = () => {
		if (!decisionLoading) {
			setActiveTaskId(null);
		}
	};

	const activePayload = useMemo(() => (activeTask ? asRecord(parseJson(activeTask.payloadJson)) : null), [activeTask]);
	const activeDiff = useMemo(() => (activeTask ? asRecord(parseJson(activeTask.diffJson)) : null), [activeTask]);
	const activeContext = useMemo(() => buildChangeDisplayContext(activeDiff, activePayload), [activeDiff, activePayload]);
	const activeSnapshot = activeContext.snapshot;
	const activeSummary = activeContext.summary;
	const activeMenuChanges = activeContext.menuChanges;
	const showActiveDiffViewer = activeSummary.length > 0 || snapshotHasContent(activeSnapshot);

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
										scroll={{ x: 1100 }}
										expandable={{
											expandedRowRender: renderExpandedRow,
											expandRowByClick: true,
											columnWidth: 48,
										}}
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
										scroll={{ x: 1100 }}
										expandable={{
											expandedRowRender: renderExpandedRow,
											expandRowByClick: true,
											columnWidth: 48,
										}}
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

							{showActiveDiffViewer ? (
								<div className="space-y-2">
									<Text variant="body3" className="text-muted-foreground">
										差异信息
									</Text>
									<ChangeDiffViewer
										snapshot={activeSnapshot}
										summary={activeSummary.length > 0 ? activeSummary : undefined}
										action={activeTask.action}
										operationTypeCode={activeTask.action}
										status={activeTask.effectiveStatus}
										className="text-xs"
									/>
								</div>
							) : null}
							{activeMenuChanges.length > 0 ? (
								<div className="space-y-2">
									<Text variant="body3" className="text-muted-foreground">
										菜单变更
									</Text>
									<MenuChangeViewer entries={activeMenuChanges} />
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
