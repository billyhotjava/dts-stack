import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { ColumnsType } from "antd/es/table";
import { Table } from "antd";
import { adminApi } from "@/admin/api/adminApi";
import type { ChangeRequest } from "@/admin/types";
import { useAdminLocale } from "@/admin/lib/locale";
import {
	buildContextForChangeRequest,
	summarizeChangeDisplayContext,
} from "@/admin/utils/change-detail-context";
import { ChangeDiffViewer } from "@/admin/components/change-diff-viewer";
import { MenuChangeViewer } from "@/admin/components/menu-change-viewer";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Text } from "@/ui/typography";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";

const STATUS_BADGE: Record<string, "default" | "secondary" | "success" | "warning" | "destructive" | "outline"> = {
	PENDING: "warning",
	PROCESSING: "warning",
	APPROVED: "success",
	REJECTED: "destructive",
	APPLIED: "success",
	FAILED: "destructive",
	DRAFT: "secondary",
};

type CategoryKey = "user" | "role";

const USER_RESOURCE_TYPES = new Set(["USER"]);
const ROLE_RESOURCE_TYPES = new Set(["ROLE", "CUSTOM_ROLE", "ROLE_ASSIGNMENT", "PORTAL_MENU"]);

const CATEGORY_OPTIONS: Array<{ key: CategoryKey; label: string }> = [
	{ key: "user", label: "用户管理" },
	{ key: "role", label: "角色管理" },
];

function resolveCategory(request: ChangeRequest): CategoryKey | null {
	const raw = (request.resourceType || request.category || "").toString().toUpperCase();
	if (!raw) return null;
	if (USER_RESOURCE_TYPES.has(raw)) return "user";
	if (ROLE_RESOURCE_TYPES.has(raw)) return "role";
	return null;
}

export default function MyChangesView() {
	const queryClient = useQueryClient();
	const { translateAction, translateResource, translateStatus } = useAdminLocale();
	const { data, isLoading } = useQuery({
		queryKey: ["admin", "change-requests", "mine", "dashboard"],
		queryFn: adminApi.getMyChangeRequests,
	});

	const [categoryFilter, setCategoryFilter] = useState<CategoryKey | null>(null);
const [activeChange, setActiveChange] = useState<ChangeRequest | null>(null);

	const normalized = useMemo(() => {
		return (data ?? []).filter((item) => resolveCategory(item) !== null);
	}, [data]);

	const summary = useMemo(() => {
		const counts = new Map<string, number>();
		for (const item of normalized) {
			const status = item.status ?? "UNKNOWN";
			counts.set(status, (counts.get(status) ?? 0) + 1);
		}
		return Array.from(counts.entries())
			.map(([status, count]) => ({ status, count }))
			.sort((a, b) => b.count - a.count);
	}, [normalized]);

	const categories = useMemo(() => {
		const present = new Set<CategoryKey>();
		for (const item of normalized) {
			const category = resolveCategory(item);
			if (category) present.add(category);
		}
		return CATEGORY_OPTIONS.map((option) => ({
			...option,
			disabled: !present.has(option.key),
		}));
	}, [normalized]);

	const filtered = useMemo(() => {
		const source = normalized as ChangeRequest[];
		if (!categoryFilter) return source.slice().sort(byRequestedAtDesc);
		return source
			.filter((item) => resolveCategory(item) === categoryFilter)
			.slice()
			.sort(byRequestedAtDesc);
	}, [normalized, categoryFilter]);

const activeContext = useMemo(
	() => (activeChange ? buildContextForChangeRequest(activeChange) : null),
	[activeChange],
);
	const activePayloadData = useMemo(() => (activeChange ? parseJson(activeChange.payloadJson) : null), [activeChange]);

	const formatDateTime = (value?: string | null) => {
		if (!value) return "-";
		const d = new Date(value);
		if (Number.isNaN(d.getTime())) return value as string;
		return d.toLocaleString("zh-CN", { hour12: false });
	};

	const columns: ColumnsType<ChangeRequest> = [
		{ title: "ID", dataIndex: "id", key: "id", width: 80 },
		{
			title: "资源",
			dataIndex: "resourceType",
			key: "resourceType",
			width: 160,
			render: (value: string) => <span className="font-medium">{translateResource(value, value)}</span>,
		},
		{
			title: "动作",
			dataIndex: "action",
			key: "action",
			width: 120,
			render: (value: string) => <Badge variant="secondary">{translateAction(value, value)}</Badge>,
		},
		{
			title: "变更内容",
			dataIndex: "diffJson",
			key: "diffJson",
			ellipsis: true,
			render: (_: unknown, record) => {
				const context = buildContextForChangeRequest(record);
				const text = summarizeChangeDisplayContext(context, {
					maxEntries: 2,
					actionLabel: translateAction(record.action, record.action),
					request: record,
				});
				return <div className="text-xs text-muted-foreground">{text || "—"}</div>;
			},
		},
		{
			title: "状态",
			dataIndex: "status",
			key: "status",
			width: 120,
			render: (value: string) => (
				<Badge variant={STATUS_BADGE[value] ?? "default"}>{translateStatus(value, value)}</Badge>
			),
		},
		{
			title: "提交时间",
			dataIndex: "requestedAt",
			key: "requestedAt",
			width: 220,
			render: (value?: string) => <span>{formatDateTime(value) || "--"}</span>,
		},
		{
			title: "操作",
			key: "actions",
			width: 120,
			render: (_: unknown, record) => (
				<Button variant="ghost" size="sm" onClick={() => setActiveChange(record)}>
					查看详情
				</Button>
			),
		},
	];

	return (
		<>
			<div className="mx-auto w-full max-w-[1400px] px-6 py-6 space-y-6">
			{/* 页面标题 */}
			<div className="flex items-center justify-between">
				<Text variant="body1" className="text-lg font-semibold">
					我的申请
				</Text>
				<div className="flex items-center gap-2">
					<Button
						variant="outline"
						onClick={() =>
							queryClient.invalidateQueries({ queryKey: ["admin", "change-requests", "mine", "dashboard"] })
						}
					>
						刷新
					</Button>
				</div>
			</div>

			{/* 概览统计 */}
			<Card>
				<CardHeader className="pb-2">
					<CardTitle>概览</CardTitle>
				</CardHeader>
				<CardContent>
					{summary.length === 0 ? (
						<Text variant="body3" className="text-muted-foreground">
							暂无数据
						</Text>
					) : (
						<div className="flex flex-wrap gap-3">
							{summary.map(({ status, count }) => (
								<div key={status} className="flex items-center gap-2 rounded-md border px-3 py-2">
									<Text variant="body3" className="text-muted-foreground">
										{translateStatus(status, status)}
									</Text>
									<Badge variant={STATUS_BADGE[status] ?? "outline"}>{count}</Badge>
								</div>
							))}
						</div>
					)}
				</CardContent>
			</Card>

			{/* 筛选与列表 */}
			<Card>
				<CardHeader className="space-y-2">
					<div className="flex flex-wrap items-center gap-3">
						<CardTitle>变更列表</CardTitle>
						<div className="ml-auto flex items-center gap-2">
							<Select
								value={categoryFilter ?? undefined}
								onValueChange={(val) => setCategoryFilter((val as CategoryKey) || null)}
							>
								<SelectTrigger className="min-w-[160px]">
									<SelectValue placeholder="按类别筛选" />
								</SelectTrigger>
								<SelectContent>
									{categories.map((option) => (
										<SelectItem key={option.key} value={option.key} disabled={option.disabled}>
											{option.label}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
							{categoryFilter ? (
								<Button variant="ghost" onClick={() => setCategoryFilter(null)}>
									清除筛选
								</Button>
							) : null}
						</div>
					</div>
				</CardHeader>
				<CardContent>
					<Table
						rowKey="id"
						columns={columns}
						dataSource={filtered}
						loading={isLoading}
						pagination={{
							pageSize: 10,
							showSizeChanger: true,
							pageSizeOptions: [10, 20, 50, 100],
							showQuickJumper: true,
							showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
						}}
					/>
				</CardContent>
			</Card>
			</div>
			<Dialog
			open={Boolean(activeChange)}
			onOpenChange={(open) => {
				if (!open) {
					setActiveChange(null);
				}
			}}
		>
			<DialogContent className="max-w-3xl">
				<DialogHeader>
					<DialogTitle>变更详情</DialogTitle>
				</DialogHeader>
				{activeChange && activeContext ? (
					<div className="max-h-[70vh] space-y-4 overflow-auto pr-1 text-sm">
						<section>
							<Text variant="body3" className="text-muted-foreground">
								基础信息
							</Text>
							<div className="mt-2 grid gap-2 text-xs sm:grid-cols-2">
								<div>
									<span className="text-muted-foreground">操作编号：</span>CR-{activeChange.id}
								</div>
								<div>
									<span className="text-muted-foreground">资源类型：</span>
									{translateResource(activeChange.resourceType, activeChange.resourceType)}
								</div>
								<div>
									<span className="text-muted-foreground">动作：</span>
									{translateAction(activeChange.action, activeChange.action)}
								</div>
								<div>
									<span className="text-muted-foreground">状态：</span>
									{translateStatus(activeChange.status, activeChange.status)}
								</div>
								<div>
									<span className="text-muted-foreground">提交时间：</span>
									{formatDateTime(activeChange.requestedAt)}
								</div>
								<div>
									<span className="text-muted-foreground">审批人：</span>
									{activeChange.decidedBy || "—"}
								</div>
								<div>
									<span className="text-muted-foreground">审批时间：</span>
									{formatDateTime(activeChange.decidedAt)}
								</div>
								<div>
									<span className="text-muted-foreground">备注：</span>
									{activeChange.reason || "—"}
								</div>
							</div>
						</section>

							<section className="space-y-2">
								<Text variant="body3" className="text-muted-foreground">
									变更摘要
								</Text>
								<div className="rounded border border-border/60 bg-muted/20 px-3 py-2 text-xs leading-5">
									{summarizeChangeDisplayContext(activeContext, {
										actionLabel: translateAction(activeChange.action, activeChange.action),
										request: activeChange,
									}) || "—"}
								</div>
							</section>

						<section className="space-y-2">
							<Text variant="body3" className="text-muted-foreground">
								变更详情
							</Text>
							<ChangeDiffViewer
								snapshot={activeContext.snapshot}
								summary={activeContext.summary.length > 0 ? activeContext.summary : undefined}
								action={activeChange.action}
								operationTypeCode={activeChange.action}
								status={activeChange.status}
								className="text-xs"
							/>
						</section>

						{activeContext.menuChanges.length > 0 ? (
							<section className="space-y-2">
								<Text variant="body3" className="text-muted-foreground">
									菜单变动
								</Text>
								<MenuChangeViewer entries={activeContext.menuChanges} />
							</section>
						) : null}

						{activePayloadData ? (
							<section className="space-y-2">
								<Text variant="body3" className="text-muted-foreground">
									提交内容
								</Text>
								<pre className="max-h-56 overflow-auto rounded border border-border/60 bg-muted/20 px-3 py-2 text-xs whitespace-pre-wrap leading-5">
									{formatJson(activePayloadData)}
								</pre>
							</section>
						) : null}
					</div>
				) : null}
			</DialogContent>
			</Dialog>
		</>
	);
}

function byRequestedAtDesc(a: ChangeRequest, b: ChangeRequest) {
	const ta = a.requestedAt ? new Date(a.requestedAt).getTime() : 0;
	const tb = b.requestedAt ? new Date(b.requestedAt).getTime() : 0;
	return tb - ta;
}

// Helpers to summarize payload/diff into a short string (handles batch updates)
function parseJson(value?: string | null): unknown {
	if (!value) return null;
	try {
		return JSON.parse(value);
	} catch {
		return null;
	}
}

function formatJson(value: unknown): string {
	if (!value) {
		return "—";
	}
	try {
		return JSON.stringify(value, null, 2);
	} catch {
		return String(value);
	}
}
