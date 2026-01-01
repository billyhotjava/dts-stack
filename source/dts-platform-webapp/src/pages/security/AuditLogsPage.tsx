import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { getAuditLog, listAuditLogs } from "@/api/platformApi";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { formatDateTime } from "@/utils/format";

type AuditRow = {
	id: string;
	occurredAt?: string | null;
	actor?: string | null;
	actionDisplay?: string | null;
	moduleTitle?: string | null;
	entryTitle?: string | null;
	result?: string | null;
	resourceType?: string | null;
	resourceId?: string | null;
};

export default function AuditLogsPage() {
	const [loading, setLoading] = useState(false);
	const [page, setPage] = useState(0);
	const [keyword, setKeyword] = useState("");
	const [actor, setActor] = useState("");

	const [total, setTotal] = useState(0);
	const [items, setItems] = useState<AuditRow[]>([]);

	const [detailOpen, setDetailOpen] = useState(false);
	const [detailLoading, setDetailLoading] = useState(false);
	const [detail, setDetail] = useState<any>(null);

	const fetchList = useCallback(async () => {
		setLoading(true);
		try {
			const resp: any = await listAuditLogs({
				page,
				size: 20,
				keyword: keyword.trim() || undefined,
				actor: actor.trim() || undefined,
			});
			const content: any[] = Array.isArray(resp?.content) ? resp.content : [];
			setItems(
				content.map((x: any) => ({
					id: String(x?.id ?? ""),
					occurredAt: x?.occurredAt ?? x?.occurred_at ?? null,
					actor: x?.actor ?? null,
					actionDisplay: x?.actionDisplay ?? x?.action ?? null,
					moduleTitle: x?.moduleTitle ?? x?.module ?? null,
					entryTitle: x?.entryTitle ?? x?.resourceType ?? null,
					result: x?.result ?? null,
					resourceType: x?.resourceType ?? null,
					resourceId: x?.resourceId ?? x?.resource ?? null,
				})),
			);
			setTotal(Number(resp?.totalElements ?? resp?.total ?? 0));
		} catch (e: any) {
			toast.error(e?.message || "加载审计日志失败");
		} finally {
			setLoading(false);
		}
	}, [page, keyword, actor]);

	useEffect(() => {
		void fetchList();
	}, [fetchList]);

	const canPrev = page > 0;
	const canNext = (page + 1) * 20 < total;

	const openDetail = useCallback(async (row: AuditRow) => {
		setDetailOpen(true);
		setDetailLoading(true);
		setDetail(null);
		try {
			const resp: any = await getAuditLog(row.id);
			setDetail(resp);
		} catch (e: any) {
			toast.error(e?.message || "加载详情失败");
		} finally {
			setDetailLoading(false);
		}
	}, []);

	const summaryText = useMemo(() => {
		if (!total) return "";
		return `共 ${total} 条`;
	}, [total]);

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader>
					<CardTitle>日志审计与报表</CardTitle>
				</CardHeader>
				<CardContent className="space-y-3">
					<div className="grid grid-cols-1 gap-3 md:grid-cols-3">
						<div className="space-y-2">
							<Label>关键字</Label>
							<Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="模块/动作/资源/详情" />
						</div>
						<div className="space-y-2">
							<Label>用户</Label>
							<Input value={actor} onChange={(e) => setActor(e.target.value)} placeholder="用户名" />
						</div>
						<div className="flex items-end gap-2">
							<Button variant="secondary" disabled={loading} onClick={() => void fetchList()}>
								查询
							</Button>
							<div className="text-xs text-muted-foreground">{summaryText}</div>
						</div>
					</div>

					<div className="overflow-auto rounded border">
						<table className="w-full text-sm">
							<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
								<tr>
									<th className="px-3 py-2">时间</th>
									<th className="px-3 py-2">用户</th>
									<th className="px-3 py-2">模块</th>
									<th className="px-3 py-2">动作</th>
									<th className="px-3 py-2">结果</th>
									<th className="px-3 py-2">资源</th>
									<th className="px-3 py-2 w-[120px]">操作</th>
								</tr>
							</thead>
							<tbody>
								{items.map((row) => (
									<tr key={row.id} className="border-b last:border-b-0">
										<td className="px-3 py-2 text-xs">{formatDateTime(row.occurredAt)}</td>
										<td className="px-3 py-2 text-xs">{row.actor || "-"}</td>
										<td className="px-3 py-2 text-xs">{row.moduleTitle || "-"}</td>
										<td className="px-3 py-2 text-xs">{row.actionDisplay || "-"}</td>
										<td className="px-3 py-2 text-xs">{row.result || "-"}</td>
										<td className="px-3 py-2 text-xs">
											{row.resourceType ? `${row.resourceType}${row.resourceId ? `:${row.resourceId}` : ""}` : "-"}
										</td>
										<td className="px-3 py-2">
											<Button size="sm" variant="secondary" onClick={() => void openDetail(row)}>
												查看
											</Button>
										</td>
									</tr>
								))}
								{items.length === 0 && (
									<tr>
										<td colSpan={7} className="px-3 py-10 text-center text-muted-foreground">
											{loading ? "加载中..." : "暂无数据"}
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>

					<div className="flex items-center justify-between">
						<Button variant="outline" disabled={!canPrev || loading} onClick={() => setPage((p) => Math.max(0, p - 1))}>
							上一页
						</Button>
						<div className="text-xs text-muted-foreground">第 {page + 1} 页</div>
						<Button variant="outline" disabled={!canNext || loading} onClick={() => setPage((p) => p + 1)}>
							下一页
						</Button>
					</div>
				</CardContent>
			</Card>

			<Dialog open={detailOpen} onOpenChange={setDetailOpen}>
				<DialogContent className="sm:max-w-[900px]">
					<DialogHeader>
						<DialogTitle>审计详情</DialogTitle>
					</DialogHeader>
					{detailLoading ? (
						<div className="text-sm text-muted-foreground">加载中...</div>
					) : detail ? (
						<pre className="max-h-[520px] overflow-auto rounded bg-muted/30 p-3 text-xs">{JSON.stringify(detail, null, 2)}</pre>
					) : (
						<div className="text-sm text-muted-foreground">暂无详情</div>
					)}
					<DialogFooter>
						<Button variant="secondary" onClick={() => setDetailOpen(false)}>
							关闭
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</div>
	);
}

