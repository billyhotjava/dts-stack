import { useMemo } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import type { ChangeRequest } from "@/admin/types";
import { useAdminLocale } from "@/admin/lib/locale";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Button } from "@/ui/button";
import { Badge } from "@/ui/badge";
import { Text } from "@/ui/typography";
import { toast } from "sonner";

export default function DraftsView() {
	const { translateAction, translateResource, translateStatus } = useAdminLocale();
	const queryClient = useQueryClient();

	const { data = [], isLoading } = useQuery<ChangeRequest[]>({
		queryKey: ["admin", "change-requests", "mine"],
		queryFn: () => adminApi.getMyChangeRequests(),
	});

	const drafts = useMemo(() => {
		return (data || [])
			.filter((item) => (item.status || "").toUpperCase() === "DRAFT")
			.sort((a, b) => (b.requestedAt || "").localeCompare(a.requestedAt || ""));
	}, [data]);

	const submitMutation = useMutation({
		mutationFn: (id: number) => adminApi.submitChangeRequest(id),
		onSuccess: () => {
			toast.success("已提交审批");
			queryClient.invalidateQueries({ queryKey: ["admin", "change-requests", "mine"] });
		},
		onError: () => toast.error("提交失败，请稍后重试"),
	});

	return (
		<Card>
			<CardHeader>
				<div className="flex items-center justify-between">
					<CardTitle>我的草稿</CardTitle>
					<Button
						variant="outline"
						onClick={() => queryClient.invalidateQueries({ queryKey: ["admin", "change-requests", "mine"] })}
					>
						刷新
					</Button>
				</div>
			</CardHeader>
			<CardContent className="space-y-3">
				{isLoading ? <Text variant="body3">加载中...</Text> : null}
				{!isLoading && drafts.length === 0 ? <Text variant="body3">暂无草稿。</Text> : null}
				<ul className="space-y-2">
					{drafts.map((item) => (
						<li key={item.id} className="rounded-md border px-3 py-2 text-sm">
							<div className="flex flex-wrap items-center justify-between gap-2">
								<div className="flex items-center gap-2">
									<span className="font-medium">
										{translateResource(item.resourceType, item.resourceType)} · {translateAction(item.action, item.action)}
									</span>
									<Badge variant="outline">{translateStatus(item.status, item.status)}</Badge>
								</div>
								<div className="flex items-center gap-2">
									<Button
										size="sm"
										disabled={submitMutation.isPending}
										onClick={() => submitMutation.mutate(item.id)}
									>
										提交审批
									</Button>
								</div>
							</div>
							{item.requestedAt ? (
								<Text variant="body3" className="text-muted-foreground">
									创建时间：{item.requestedAt}
								</Text>
							) : null}
						</li>
					))}
				</ul>
			</CardContent>
		</Card>
	);
}

