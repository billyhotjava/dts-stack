import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import type { ChangeRequest } from "@/admin/types";
import ChangeRequestDiff from "@/admin/components/change-request-diff";
import { useAdminLocale } from "@/admin/lib/locale";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Button } from "@/ui/button";
import { Textarea } from "@/ui/textarea";
import { Text } from "@/ui/typography";
import { Badge } from "@/ui/badge";
import { toast } from "sonner";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";

export default function ApprovalCenterView() {
	const queryClient = useQueryClient();
	const [selected, setSelected] = useState<ChangeRequest | null>(null);
	const [reason, setReason] = useState("");
	const { translateAction, translateResource, translateStatus } = useAdminLocale();

	const { data = [], isLoading } = useQuery({
		queryKey: ["admin", "change-requests", "pending"],
		queryFn: () => adminApi.getChangeRequests({ status: "PENDING" }),
	});

	const [typeFilter, setTypeFilter] = useState<string>("ALL");
	const filtered = (data || []).filter((item) => {
		if (typeFilter === "ALL") return true;
		const t = (item.resourceType || "").toUpperCase();
		if (typeFilter === "USER") return t === "USER";
		if (typeFilter === "ROLE") return t === "ROLE";
		return true;
	});

	const approveMutation = useMutation({
		mutationFn: ({ id, reason }: { id: number; reason?: string }) => adminApi.approveChangeRequest(id, reason),
		onSuccess: () => {
			toast.success("已审批通过");
			queryClient.invalidateQueries({ queryKey: ["admin", "change-requests", "pending"] });
			setSelected(null);
		},
		onError: () => toast.error("审批失败，请稍后重试"),
	});

	const rejectMutation = useMutation({
		mutationFn: ({ id, reason }: { id: number; reason?: string }) => adminApi.rejectChangeRequest(id, reason),
		onSuccess: () => {
			toast.success("已驳回变更");
			queryClient.invalidateQueries({ queryKey: ["admin", "change-requests", "pending"] });
			setSelected(null);
		},
		onError: () => toast.error("操作失败，请稍后重试"),
	});

	return (
		<div className="grid gap-6 lg:grid-cols-[minmax(0,0.55fr)_minmax(0,1fr)]">
			<Card>
				<CardHeader>
					<div className="flex items-center gap-3">
						<CardTitle>待我审批</CardTitle>
						<Select value={typeFilter} onValueChange={(v) => setTypeFilter(v)}>
							<SelectTrigger className="w-44">
								<SelectValue placeholder="筛选类型" />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value="ALL">全部类型</SelectItem>
								<SelectItem value="USER">用户变更</SelectItem>
								<SelectItem value="ROLE">角色变更</SelectItem>
							</SelectContent>
						</Select>
					</div>
				</CardHeader>
				<CardContent className="space-y-3">
					{isLoading ? <Text variant="body3">加载中...</Text> : null}
					{!isLoading && filtered.length === 0 ? <Text variant="body3">暂无待审批事项。</Text> : null}
					<ul className="space-y-2">
						{filtered.map((item) => (
							<li
								key={item.id}
								onClick={() => {
									setSelected(item);
									setReason("");
								}}
								className={`cursor-pointer rounded-md border px-3 py-2 text-sm transition ${
									selected?.id === item.id ? "border-primary bg-primary/10" : "hover:border-primary/50"
								}`}
							>
								<div className="flex items-center justify-between gap-2">
									<span className="font-medium">{translateResource(item.resourceType, item.resourceType)}</span>
									<Badge variant="outline">{translateAction(item.action, item.action)}</Badge>
								</div>
								<Text variant="body3" className="text-muted-foreground">
									提交人：{item.requestedBy} · 状态：{translateStatus(item.status, item.status)}
								</Text>
							</li>
						))}
					</ul>
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>审批详情</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4">
					{!selected ? (
						<Text variant="body3" className="text-muted-foreground">
							请选择左侧待审批事项查看详情。
						</Text>
					) : (
						<>
							<div className="flex flex-wrap items-center justify-between gap-2">
								<Text variant="body2" className="font-semibold">
									{translateResource(selected.resourceType, selected.resourceType)} ·{" "}
									{translateAction(selected.action, selected.action)}
								</Text>
								<Text variant="body3" className="text-muted-foreground">
									提交时间：{selected.requestedAt || "--"}
								</Text>
							</div>
							<ChangeRequestDiff payloadJson={selected.payloadJson} diffJson={selected.diffJson} />
							<div className="space-y-2">
								<Text variant="body3" className="text-muted-foreground">
									请填写审批意见（可选）
								</Text>
								<Textarea value={reason} onChange={(event) => setReason(event.target.value)} rows={3} />
							</div>
							<div className="flex flex-wrap gap-3">
								<Button
									onClick={() => approveMutation.mutate({ id: selected.id, reason: reason || undefined })}
									disabled={approveMutation.isPending}
								>
									审批通过
								</Button>
								<Button
									variant="destructive"
									onClick={() => rejectMutation.mutate({ id: selected.id, reason: reason || undefined })}
									disabled={rejectMutation.isPending}
								>
									驳回
								</Button>
							</div>
						</>
					)}
				</CardContent>
			</Card>
		</div>
	);
}
