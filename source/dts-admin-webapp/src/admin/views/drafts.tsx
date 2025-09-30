import { useMemo } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import { ChangeRequestForm } from "@/admin/components/change-request-form";
import type { ChangeRequest } from "@/admin/types";
import { useAdminLocale } from "@/admin/lib/locale";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Text } from "@/ui/typography";
import { Badge } from "@/ui/badge";
import { useSearchParams } from "react-router";

export default function DraftsView() {
	const [params] = useSearchParams();
	const queryClient = useQueryClient();
	const initialTab = params.get("tab")?.replace("mine", "user") ?? "user";
	const { translateAction, translateResource, translateStatus } = useAdminLocale();

	const { data: mine, refetch } = useQuery({
		queryKey: ["admin", "change-requests", "mine"],
		queryFn: adminApi.getMyChangeRequests,
	});

	const grouped = useMemo(() => {
		return (mine ?? []).reduce<Record<string, ChangeRequest[]>>((acc, item) => {
			const status = item.status ?? "UNKNOWN";
			acc[status] = acc[status] ? [...acc[status], item] : [item];
			return acc;
		}, {});
	}, [mine]);

	return (
		<div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,0.8fr)]">
			<Card className="order-2 lg:order-1">
				<CardHeader>
					<CardTitle>我发起的变更</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4">
					{Object.entries(grouped).map(([status, items]) => (
						<section key={status} className="rounded-lg border p-3">
							<div className="mb-2 flex items-center justify-between">
								<Text variant="body3" className="font-semibold uppercase tracking-wide text-muted-foreground">
									{translateStatus(status, status)}
								</Text>
								<Badge variant={status === "FAILED" ? "destructive" : "outline"}>{items.length}</Badge>
							</div>
							<ul className="space-y-2">
								{items.map((item) => (
									<li key={item.id} className="rounded-md border bg-background px-3 py-2 text-sm">
									<div className="flex flex-wrap items-center justify-between gap-2">
										<span className="font-medium">{translateResource(item.resourceType, item.resourceType)}</span>
										<Badge variant="secondary">{translateAction(item.action, item.action)}</Badge>
										</div>
										<Text variant="body3" className="text-muted-foreground">
											提交时间：{item.requestedAt || "--"}
										</Text>
									</li>
								))}
							</ul>
						</section>
					))}
					{mine && mine.length === 0 ? <Text variant="body3">暂无草稿记录。</Text> : null}
				</CardContent>
			</Card>

			<Card className="order-1 lg:order-2">
				<CardHeader>
					<CardTitle>创建并提交变更</CardTitle>
				</CardHeader>
				<CardContent>
					<ChangeRequestForm
						initialTab={initialTab}
						onCreated={() => {
							queryClient.invalidateQueries({ queryKey: ["admin", "change-requests", "mine"] });
							refetch();
						}}
					/>
				</CardContent>
			</Card>
		</div>
	);
}
