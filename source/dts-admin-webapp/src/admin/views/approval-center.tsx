import { useContext, useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { adminApi } from "@/admin/api/adminApi";
import type { ChangeRequest } from "@/admin/types";
import { AdminSessionContext } from "@/admin/lib/session-context";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Text } from "@/ui/typography";
import { toast } from "sonner";
import { KeycloakApprovalService } from "@/api/services/approvalService";
import { useUserInfo } from "@/store/userStore";

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

function resolveTarget(request: ChangeRequest): string {
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
	return target ? String(target) : "-";
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

function summarizeDiffPairs(request: ChangeRequest): string {
    const diff = asRecord(parseJson(request.diffJson));
    if (!diff) return "—";
    // Handle batch diff: { items: [{ id, before, after }] }
    if (Array.isArray((diff as any).items)) {
        const items = (diff as any).items as any[];
        const lines: string[] = [];
        for (const it of items.slice(0, 3)) {
            const id = it?.id ?? "?";
            const before = asRecord(it?.before) || {};
            const after = asRecord(it?.after) || {};
            const keys = Array.from(new Set([...Object.keys(before), ...Object.keys(after)]));
            const changes: string[] = [];
            for (const key of keys) {
                const a = (before as any)[key];
                const b = (after as any)[key];
                if (JSON.stringify(a) !== JSON.stringify(b)) {
                    changes.push(`${key}: ${fmtValue(a)} → ${fmtValue(b)}`);
                }
            }
            if (changes.length) {
                lines.push(`菜单${id}: ${changes.join("，")}`);
            }
        }
        const extra = items.length - Math.min(items.length, 3);
        return lines.length ? lines.join("；") + (extra > 0 ? `（等${extra}项）` : "") : "—";
    }
    const before = asRecord(diff.before) || {};
    const after = asRecord(diff.after) || {};
    const keys = Array.from(new Set([...Object.keys(before), ...Object.keys(after)]));
    const changes: string[] = [];
    for (const key of keys) {
        const a = (before as any)[key];
        const b = (after as any)[key];
        if (JSON.stringify(a) !== JSON.stringify(b)) {
            changes.push(`${key}: ${fmtValue(a)} → ${fmtValue(b)}`);
        }
    }
    return changes.length > 0 ? changes.join("；") : "—";
}

function fmtValue(v: unknown): string {
    if (v == null) return "—";
    if (Array.isArray(v)) return `[${v.map((x) => (typeof x === "string" ? x : JSON.stringify(x))).join(", ")}]`;
    if (typeof v === "string") return v;
    return JSON.stringify(v);
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
		return JSON.stringify(value, null, 2);
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

	const [decisions, setDecisions] = useState<Record<number, DecisionRecord>>({});
	const [categoryFilter, setCategoryFilter] = useState<TaskCategory>(CATEGORY_ORDER[0]);
	const [activeTaskId, setActiveTaskId] = useState<number | null>(null);
	const [decisionLoading, setDecisionLoading] = useState(false);

	const augmentedRequests = useMemo<AugmentedChangeRequest[]>(() => {
		return changeRequests.map((item) => {
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
	}, [changeRequests, decisions]);

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
				title: "操作内容",
				dataIndex: "diffJson",
				ellipsis: true,
				render: (_: unknown, record) => (
					<div className="text-xs text-muted-foreground">{summarizeDiffPairs(record)}</div>
				),
			},
			{
				title: "影响对象",
				dataIndex: "resourceId",
				width: 160,
				render: (_: unknown, record) => resolveTarget(record),
			},
			{
				title: "操作人",
				dataIndex: "requestedBy",
				width: 140,
				render: (_: unknown, record) => <span className="text-xs">{record.requestedBy}</span>,
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
		[activeTaskId, decisionLoading],
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
				title: "操作内容",
				dataIndex: "diffJson",
				ellipsis: true,
				render: (_: unknown, record) => (
					<div className="text-xs text-muted-foreground">{summarizeDiffPairs(record)}</div>
				),
			},
			{
				title: "影响对象",
				dataIndex: "resourceId",
				width: 160,
				render: (_: unknown, record) => resolveTarget(record),
			},
			{
				title: "操作人",
				dataIndex: "effectiveDecidedBy",
				width: 140,
				render: (_: unknown, record) => <span className="text-xs">{record.effectiveDecidedBy ?? "-"}</span>,
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
		[],
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
                                    scroll={{ x: 1100 }}
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
									<div className="font-medium">{resolveTarget(activeTask)}</div>
								</div>
								<div className="space-y-1">
									<Text variant="body3" className="text-muted-foreground">
										提交人
									</Text>
									<div>{activeTask.requestedBy}</div>
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
