import { useContext, useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
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
import { useUserInfo } from "@/store/userStore";

type TaskCategory = "user" | "role";

const CATEGORY_LABELS: Record<TaskCategory, string> = {
	user: "用户管理",
	role: "角色管理",
};

const ACTION_LABELS: Record<string, string> = {
	CREATE: "新增",
	UPDATE: "更新",
	DELETE: "删除",
	ENABLE: "启用",
	DISABLE: "禁用",
};

const STATUS_LABELS: Record<string, string> = {
	PENDING: "待审批",
	ON_HOLD: "待定",
	APPROVED: "已通过",
	REJECTED: "已驳回",
	DRAFT: "草稿",
};

const STATUS_BADGE: Record<string, "outline" | "secondary" | "destructive"> = {
	PENDING: "outline",
	ON_HOLD: "outline",
	APPROVED: "secondary",
	REJECTED: "destructive",
	DRAFT: "outline",
};

type DecisionStatus = "PENDING" | "ON_HOLD" | "APPROVED" | "REJECTED";

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
	const normalized = resourceType?.toUpperCase();
	if (normalized === "USER") return "user";
	if (normalized === "ROLE") return "role";
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
	const before = asRecord(diff.before) || {};
	const after = asRecord(diff.after) || {};
	const keys = Array.from(new Set([...Object.keys(before), ...Object.keys(after)]));
	const changes: string[] = [];
	for (const key of keys) {
		const a = (before as any)[key];
		const b = (after as any)[key];
		if (JSON.stringify(a) !== JSON.stringify(b)) {
			const aText = typeof a === "string" ? a : a == null ? "—" : JSON.stringify(a);
			const bText = typeof b === "string" ? b : b == null ? "—" : JSON.stringify(b);
			changes.push(`${key}: ${aText} → ${bText}`);
		}
	}
	return changes.length > 0 ? changes.join("；") : "—";
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
	if (normalized === "APPROVED" || normalized === "REJECTED") {
		return normalized;
	}
	if (normalized === "ON_HOLD") {
		return "ON_HOLD";
	}
	return "PENDING";
}

export default function ApprovalCenterView() {
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
			if (item.effectiveStatus === "APPROVED" || item.effectiveStatus === "REJECTED") continue;
			groups[category].push(item);
		}
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
			if (item.effectiveStatus === "APPROVED" || item.effectiveStatus === "REJECTED") {
				groups[category].push(item);
			}
		}
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

	const handleDecision = (status: DecisionStatus) => {
		if (!activeTask) return;
		setDecisionLoading(true);
		const decisionId = activeTask.id;
		const decidedBy = session.username ?? session.email ?? "authadmin";
		window.setTimeout(() => {
			setDecisions((prev) => ({
				...prev,
				[decisionId]: {
					status,
					decidedAt: status === "APPROVED" || status === "REJECTED" ? new Date().toISOString() : null,
					decidedBy,
				},
			}));
			setDecisionLoading(false);
			setActiveTaskId(null);
			const message =
				status === "APPROVED"
					? "已批准该变更请求"
					: status === "REJECTED"
						? "已拒绝该变更请求"
						: "已将该请求标记为待定";
			toast.success(message);
		}, 480);
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
									<div className="overflow-x-auto">
										<table className="min-w-full table-fixed text-sm">
											<thead className="bg-muted/60">
												<tr className="text-left">
													<th className="px-4 py-3 font-medium">操作编号</th>
													<th className="px-4 py-3 font-medium">操作类型</th>
													<th className="px-4 py-3 font-medium">操作内容</th>
													<th className="px-4 py-3 font-medium">影响对象</th>
													<th className="px-4 py-3 font-medium">操作人</th>
													<th className="px-4 py-3 font-medium">操作时间</th>
													<th className="px-4 py-3 font-medium">当前状态</th>
													<th className="px-4 py-3 font-medium text-right">操作</th>
												</tr>
											</thead>
											<tbody>
												{selectedPending.map((task) => (
													<tr key={task.id} className="border-b last:border-b-0">
														<td className="px-4 py-3 font-medium">CR-{task.id}</td>
														<td className="px-4 py-3">{getActionText(task)}</td>
														<td className="px-4 py-3 text-xs">
															<div className="text-muted-foreground">{summarizeDiffPairs(task)}</div>
														</td>
														<td className="px-4 py-3">{resolveTarget(task)}</td>
														<td className="px-4 py-3 text-xs">{task.requestedBy}</td>
														<td className="px-4 py-3 text-xs">{formatDateTime(task.requestedAt)}</td>
														<td className="px-4 py-3">
															<Badge variant={getStatusBadgeVariant(task.effectiveStatus)}>
																{getStatusLabel(task.effectiveStatus)}
															</Badge>
														</td>
														<td className="px-4 py-3 text-right">
															<Button
																size="sm"
																variant="outline"
																onClick={() => setActiveTaskId(task.id)}
																disabled={decisionLoading && activeTaskId === task.id}
															>
																操作
															</Button>
														</td>
													</tr>
												))}
											</tbody>
										</table>
									</div>
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
									<div className="overflow-x-auto">
										<table className="min-w-full table-fixed text-sm">
											<thead className="bg-muted/60">
												<tr className="text-left">
													<th className="px-4 py-3 font-medium">操作编号</th>
													<th className="px-4 py-3 font-medium">操作类型</th>
													<th className="px-4 py-3 font-medium">操作内容</th>
													<th className="px-4 py-3 font-medium">影响对象</th>
													<th className="px-4 py-3 font-medium">操作人</th>
													<th className="px-4 py-3 font-medium">操作时间</th>
													<th className="px-4 py-3 font-medium">处理结果</th>
												</tr>
											</thead>
											<tbody>
												{selectedCompleted.map((task) => (
													<tr key={task.id} className="border-b last:border-b-0">
														<td className="px-4 py-3 font-medium">CR-{task.id}</td>
														<td className="px-4 py-3">{getActionText(task)}</td>
														<td className="px-4 py-3 text-xs">
															<div className="text-muted-foreground">{summarizeDiffPairs(task)}</div>
														</td>
														<td className="px-4 py-3">{resolveTarget(task)}</td>
														<td className="px-4 py-3 text-xs">{task.effectiveDecidedBy ?? "-"}</td>
														<td className="px-4 py-3 text-xs">{formatDateTime(task.effectiveDecidedAt)}</td>
														<td className="px-4 py-3">
															<Badge variant={getStatusBadgeVariant(task.effectiveStatus)}>
																{getStatusLabel(task.effectiveStatus)}
															</Badge>
														</td>
													</tr>
												))}
											</tbody>
										</table>
									</div>
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
						<div className="space-y-5 text-sm">
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
									<pre className="max-h-64 overflow-auto rounded-md bg-muted/40 p-3 text-xs">
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
											<pre className="max-h-48 overflow-auto rounded-md bg-muted/40 p-3 text-xs">
												{formatJson(diffBefore)}
											</pre>
										</div>
									) : null}
									{diffAfter && Object.keys(diffAfter).length > 0 ? (
										<div className="space-y-1">
											<Text variant="body3" className="text-muted-foreground">
												变更后
											</Text>
											<pre className="max-h-48 overflow-auto rounded-md bg-muted/40 p-3 text-xs">
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
