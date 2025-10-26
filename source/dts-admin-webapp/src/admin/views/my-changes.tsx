import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { ColumnsType } from "antd/es/table";
import { Table } from "antd";
import { adminApi } from "@/admin/api/adminApi";
import type { ChangeRequest } from "@/admin/types";
import { useAdminLocale } from "@/admin/lib/locale";
import { beautifyBatchItemLabel, formatChangeValue, labelForChangeField } from "@/admin/lib/change-request-format";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
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
			render: (_: unknown, record) => <div className="text-xs text-muted-foreground">{summarizeChange(record)}</div>,
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
	];

	return (
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

function asRecord(value: unknown): Record<string, unknown> | null {
	if (value && typeof value === "object" && !Array.isArray(value)) return value as Record<string, unknown>;
	return null;
}

function fmtValue(v: unknown): string {
	if (v == null) return "—";
	if (Array.isArray(v)) return `[${v.map((x) => (typeof x === "string" ? x : JSON.stringify(x))).join(", ")}]`;
	if (typeof v === "string") return v;
	return JSON.stringify(v);
}

function isPortalMenuRequest(request: ChangeRequest): boolean {
	const resource = (request.resourceType || request.category || "").toString().toUpperCase();
	return resource === "PORTAL_MENU" || resource === "MENU" || resource === "MENU_MANAGEMENT";
}

function formatMenuStatus(value: unknown): string {
	if (value === true || value === "true") return "禁用";
	if (value === false || value === "false" || value == null) return "启用";
	return fmtValue(value);
}

function formatChangeEntry(request: ChangeRequest, key: string, before: unknown, after: unknown): string {
	if (isPortalMenuRequest(request) && key === "deleted") {
		const label = "状态";
		const beforeDefined = before !== undefined;
		const beforeLabel = beforeDefined ? formatMenuStatus(before) : null;
		const afterLabel = formatMenuStatus(after);
		return beforeDefined ? `${label}: ${beforeLabel} → ${afterLabel}` : `${label}: ${afterLabel}`;
	}
	const label = labelForChangeField(key);
	const beforeLabel = before !== undefined ? formatChangeValue(key, before) : null;
	const afterLabel = formatChangeValue(key, after);
	return beforeLabel != null ? `${label}: ${beforeLabel} → ${afterLabel}` : `${label}: ${afterLabel}`;
}

function formatSingleEntry(request: ChangeRequest, key: string, value: unknown): string {
	if (isPortalMenuRequest(request) && key === "deleted") {
		return `状态: ${formatMenuStatus(value)}`;
	}
	const label = labelForChangeField(key);
	return `${label}: ${formatChangeValue(key, value)}`;
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
		item?.id,
	];
	for (const candidate of candidates) {
		if (typeof candidate === "string" && candidate.trim().length > 0) {
			return beautifyBatchItemLabel(candidate.trim());
		}
	}
	if (item?.id != null) {
		return `菜单${item.id}`;
	}
	return `第${index + 1}项`;
}

function summarizeChange(request: ChangeRequest): string {
	const payload = asRecord(parseJson(request.payloadJson));
	const diff = asRecord(parseJson(request.diffJson));
	if (diff && Array.isArray((diff as any).items) && ((diff as any).items as any[]).length > 0) {
		const items = ((diff as any).items as any[]).slice(0, 3);
		const parts: string[] = [];
		items.forEach((it, itemIndex) => {
			const before = asRecord(it?.before) || {};
			const after = asRecord(it?.after) || {};
			const keys = Array.from(new Set([...Object.keys(before), ...Object.keys(after)]));
			const changed = keys.filter((k) => JSON.stringify((before as any)[k]) !== JSON.stringify((after as any)[k]));
			if (changed.length === 0) return;
			const detail = changed
				.slice(0, 2)
				.map((k) => formatChangeEntry(request, k, (before as any)[k], (after as any)[k]))
				.join("，");
			const label = resolveBatchItemLabel(asRecord(it) || null, before, after, itemIndex);
			parts.push(`${label}: ${detail}`);
		});
		const extra = ((diff as any).items as any[]).length - items.length;
		return parts.length ? parts.join("；") + (extra > 0 ? `（等${extra}项）` : "") : "—";
	}
	if (payload) {
		const entries = Object.entries(payload).filter(([, v]) => v != null);
		if (entries.length === 0) return "—";
		return entries
			.slice(0, 3)
			.map(([k, v]) => formatSingleEntry(request, k, v))
			.join("；");
	}
	if (diff) {
		const before = asRecord((diff as any).before) || {};
		const after = asRecord((diff as any).after) || {};
		const keys = Array.from(new Set([...Object.keys(before), ...Object.keys(after)]));
		const changed = keys.filter((k) => JSON.stringify((before as any)[k]) !== JSON.stringify((after as any)[k]));
		if (changed.length === 0) return "—";
		return changed
			.slice(0, 3)
			.map((k) => formatChangeEntry(request, k, (before as any)[k], (after as any)[k]))
			.join("；");
	}
	return "—";
}
