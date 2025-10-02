import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import type { AuditEvent } from "@/admin/types";
import { useAdminLocale } from "@/admin/lib/locale";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Button } from "@/ui/button";
import { Textarea } from "@/ui/textarea";
import { Text } from "@/ui/typography";
import { Badge } from "@/ui/badge";
import { useSearchParams } from "react-router";
import { toast } from "sonner";
import { AuditLogService } from "@/api/services/auditLogService";
import { GLOBAL_CONFIG } from "@/global-config";
import userStore from "@/store/userStore";

interface FilterState {
	from?: string;
	to?: string;
	actor?: string;
	action?: string;
	resource?: string;
	result?: string;
	module?: string;
	clientIp?: string;
}

export default function AuditCenterView() {
	const [params] = useSearchParams();
	const tab = params.get("tab") || "audit";
	const [filters, setFilters] = useState<FilterState>({
		resource: tab === "login" ? "LOGIN" : undefined,
	});

	const queryParams = useMemo(() => buildQuery(filters), [filters]);
	const { data = [], isLoading } = useQuery<AuditEvent[]>({
		queryKey: ["admin", "audit", queryParams],
		queryFn: async () => {
			const response = await AuditLogService.getAuditLogs(0, 100, "occurredAt,desc", queryParams);
			return response.content as AuditEvent[];
		},
	});

	const handleExport = async (format: "csv" | "json") => {
		const params = { ...queryParams, format };
		const query = new URLSearchParams(params).toString();
		const url = buildExportUrl(query);
		const token = resolveAccessToken();
		try {
			const response = await fetch(url, {
				headers: token ? { Authorization: `Bearer ${token}` } : undefined,
			});
			if (!response.ok) throw new Error("导出失败");
			const blob = await response.blob();
			const link = document.createElement("a");
			link.href = window.URL.createObjectURL(blob);
			link.download = `audit.${format}`;
			document.body.appendChild(link);
			link.click();
			link.remove();
			window.URL.revokeObjectURL(link.href);
		} catch (error) {
			toast.error("导出失败，请稍后重试");
		}
	};

	return (
		<div className="space-y-6">
			<Card>
				<CardHeader>
					<CardTitle>查询条件</CardTitle>
				</CardHeader>
				<CardContent className="grid gap-4 md:grid-cols-3">
					<Input
						placeholder="开始时间 (ISO)"
						value={filters.from || ""}
						onChange={(event) => setFilters((prev) => ({ ...prev, from: event.target.value }))}
					/>
					<Input
						placeholder="结束时间 (ISO)"
						value={filters.to || ""}
						onChange={(event) => setFilters((prev) => ({ ...prev, to: event.target.value }))}
					/>
					<Input
						placeholder="操作者"
						value={filters.actor || ""}
						onChange={(event) => setFilters((prev) => ({ ...prev, actor: event.target.value }))}
					/>
					<Input
						placeholder="动作"
						value={filters.action || ""}
						onChange={(event) => setFilters((prev) => ({ ...prev, action: event.target.value }))}
					/>
					<Input
						placeholder="模块"
						value={filters.module || ""}
						onChange={(event) => setFilters((prev) => ({ ...prev, module: event.target.value }))}
					/>
					<Input
						placeholder="资源关键字"
						value={filters.resource || ""}
						onChange={(event) => setFilters((prev) => ({ ...prev, resource: event.target.value }))}
					/>
					<Input
						placeholder="结果 (SUCCESS/FAILURE)"
						value={filters.result || ""}
						onChange={(event) => setFilters((prev) => ({ ...prev, result: event.target.value.toUpperCase() }))}
					/>
					<Input
						placeholder="客户端 IP"
						value={filters.clientIp || ""}
						onChange={(event) => setFilters((prev) => ({ ...prev, clientIp: event.target.value }))}
					/>
					<div className="flex gap-3">
						<Button type="button" variant="outline" onClick={() => setFilters({})}>
							重置
						</Button>
						<Button type="button" onClick={() => handleExport("csv")}>
							导出 CSV
						</Button>
						<Button type="button" variant="secondary" onClick={() => handleExport("json")}>
							导出 JSON
						</Button>
					</div>
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>审计记录</CardTitle>
				</CardHeader>
				<CardContent className="space-y-3">
					{isLoading ? <Text variant="body3">加载中...</Text> : null}
					{!isLoading && data.length === 0 ? <Text variant="body3">暂无数据。</Text> : null}
					{data.map((event) => (
						<AuditCard key={event.id} event={event} />
					))}
				</CardContent>
			</Card>
		</div>
	);
}

function AuditCard({ event }: { event: AuditEvent }) {
	const { translateAction, translateOutcome } = useAdminLocale();
	const occurredAt = useMemo(() => new Date(event.occurredAt).toLocaleString("zh-CN", { hour12: false }), [event.occurredAt]);
	const resource = useMemo(
		() => [event.resourceType, event.resourceId].filter(Boolean).join(" · ") || "--",
		[event.resourceType, event.resourceId],
	);
	const requestLine = event.requestUri ? `${event.httpMethod || "GET"} ${event.requestUri}` : "";
	return (
		<div className="rounded-lg border bg-muted/40 p-3 text-sm">
			<div className="flex flex-wrap items-center justify-between gap-2">
				<span className="font-semibold">
					{translateAction(event.action, event.action || "--")} · {event.module || "--"}
				</span>
				<Badge variant={event.result === "FAILURE" ? "destructive" : "outline"}>
					{translateOutcome(event.result, event.result || "--")}
				</Badge>
			</div>
			<Text variant="body3" className="text-muted-foreground">
				{occurredAt}
			</Text>
			<Text variant="body3" className="text-muted-foreground">
				操作者：{event.actor || "--"} · 角色：{event.actorRole || "--"}
			</Text>
			<Text variant="body3" className="text-muted-foreground">
				目标：{resource}
			</Text>
			{requestLine ? (
				<Text variant="body3" className="text-muted-foreground break-all">
					请求：{requestLine}
				</Text>
			) : null}
			{event.payloadPreview ? (
				<Textarea readOnly value={event.payloadPreview} rows={3} className="mt-2" />
			) : null}
		</div>
	);
}

function buildQuery(filters: FilterState): Record<string, string> {
	const params: Record<string, string> = {};
	if (filters.from) {
		params.from = new Date(filters.from).toISOString();
	}
	if (filters.to) {
		params.to = new Date(filters.to).toISOString();
	}
	if (filters.actor) {
		params.actor = filters.actor;
	}
	if (filters.action) {
		params.action = filters.action;
	}
	if (filters.module) {
		params.module = filters.module;
	}
	if (filters.resource) {
		params.resource = filters.resource;
	}
	if (filters.result) {
		params.result = filters.result.toUpperCase();
	}
	if (filters.clientIp) {
		params.clientIp = filters.clientIp;
	}
	return params;
}

function resolveAccessToken(): string {
	const { userToken } = userStore.getState();
	const raw = userToken?.accessToken;
	if (!raw) {
		return "";
	}
	const trimmed = String(raw).trim();
	if (!trimmed) {
		return "";
	}
	return trimmed.startsWith("Bearer ") ? trimmed.slice(7).trim() : trimmed;
}

function buildExportUrl(query: string): string {
	const base = GLOBAL_CONFIG.apiBaseUrl?.trim() || "/api";
	const normalized = base.endsWith("/") ? base.slice(0, -1) : base;
	return `${normalized}/audit-logs/export${query ? `?${query}` : ""}`;
}
