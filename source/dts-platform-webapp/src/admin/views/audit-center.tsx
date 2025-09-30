import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
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

interface FilterState {
	from?: string;
	to?: string;
	actor?: string;
	action?: string;
	resource?: string;
	outcome?: string;
}

export default function AuditCenterView() {
	const [params] = useSearchParams();
	const tab = params.get("tab") || "audit";
	const [filters, setFilters] = useState<FilterState>({
		resource: tab === "login" ? "LOGIN" : undefined,
	});

	const queryParams = useMemo(() => filters, [filters]);
	const { data = [], isLoading } = useQuery({
		queryKey: ["admin", "audit", queryParams],
		queryFn: () => adminApi.getAuditEvents(queryParams as Record<string, string>),
	});

	const handleExport = async (format: "csv" | "json") => {
		const search = new URLSearchParams({ ...filters, format }).toString();
		try {
			const response = await fetch(`/admin/audit/export?${search}`, {
				credentials: "include",
			});
			if (!response.ok) throw new Error("导出失败");
			const blob = await response.blob();
			const url = window.URL.createObjectURL(blob);
			const link = document.createElement("a");
			link.href = url;
			link.download = `audit.${format}`;
			document.body.appendChild(link);
			link.click();
			link.remove();
			window.URL.revokeObjectURL(url);
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
						placeholder="资源"
						value={filters.resource || ""}
						onChange={(event) => setFilters((prev) => ({ ...prev, resource: event.target.value }))}
					/>
					<Input
						placeholder="结果 (SUCCESS/FAILURE)"
						value={filters.outcome || ""}
						onChange={(event) => setFilters((prev) => ({ ...prev, outcome: event.target.value }))}
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
	return (
		<div className="rounded-lg border bg-muted/40 p-3 text-sm">
			<div className="flex flex-wrap items-center justify-between gap-2">
				<span className="font-semibold">{translateAction(event.action, event.action || "--")}</span>
				<Badge variant={event.outcome === "FAILURE" ? "destructive" : "outline"}>
					{translateOutcome(event.outcome, event.outcome || "--")}
				</Badge>
			</div>
			<Text variant="body3" className="text-muted-foreground">
				{event.timestamp}
			</Text>
			<Text variant="body3" className="text-muted-foreground">
				操作者：{event.actor || "--"} · 资源：{event.resource || "--"}
			</Text>
			{event.detailJson ? <Textarea readOnly value={event.detailJson} rows={3} className="mt-2" /> : null}
		</div>
	);
}
