import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { listDatasets } from "@/api/platformApi";
import { useRouter } from "@/routes/hooks";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";

type DatasetRow = { id: string; name: string; owner?: string | null; ownerDept?: string | null; classification?: string | null };

export default function AccessMatrixPage() {
	const router = useRouter();
	const [keyword, setKeyword] = useState("");
	const [loading, setLoading] = useState(false);
	const [items, setItems] = useState<DatasetRow[]>([]);

	const fetchList = useCallback(async () => {
		setLoading(true);
		try {
			const resp: any = await listDatasets({ keyword: keyword.trim() || undefined, page: 0, size: 20 });
			const content: any[] = Array.isArray(resp?.content) ? resp.content : [];
			setItems(
				content.map((d: any) => ({
					id: String(d?.id ?? ""),
					name: String(d?.name ?? ""),
					owner: d?.owner ?? null,
					ownerDept: d?.ownerDept ?? d?.owner_dept ?? null,
					classification: d?.classification ?? null,
				})),
			);
		} catch (e: any) {
			toast.error(e?.message || "加载数据集失败");
		} finally {
			setLoading(false);
		}
	}, [keyword]);

	useEffect(() => {
		void fetchList();
	}, [fetchList]);

	const filtered = useMemo(() => {
		const k = keyword.trim().toLowerCase();
		if (!k) return items;
		return items.filter((it) => (it.name || "").toLowerCase().includes(k) || String(it.owner || "").toLowerCase().includes(k));
	}, [items, keyword]);

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader>
					<CardTitle>访问权限矩阵</CardTitle>
				</CardHeader>
				<CardContent className="space-y-3">
					<div className="text-sm text-muted-foreground">
						说明：数据集授权（用户可见）在数据资产详情页维护；本页面提供“快速定位数据集并进入授权”入口。
					</div>
					<div className="flex flex-col gap-2 md:flex-row md:items-end">
						<div className="flex-1 space-y-2">
							<Label>搜索数据集</Label>
							<Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="按数据集名称/负责人搜索" />
						</div>
						<Button variant="secondary" disabled={loading} onClick={() => void fetchList()}>
							刷新
						</Button>
					</div>
					<div className="overflow-auto rounded border">
						<table className="w-full text-sm">
							<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
								<tr>
									<th className="px-3 py-2">数据集</th>
									<th className="px-3 py-2">负责人</th>
									<th className="px-3 py-2">部门</th>
									<th className="px-3 py-2">密级</th>
									<th className="px-3 py-2 w-[160px]">操作</th>
								</tr>
							</thead>
							<tbody>
								{filtered.map((row) => (
									<tr key={row.id} className="border-b last:border-b-0">
										<td className="px-3 py-2 text-xs font-medium">{row.name}</td>
										<td className="px-3 py-2 text-xs">{row.owner || "-"}</td>
										<td className="px-3 py-2 text-xs">{row.ownerDept || "-"}</td>
										<td className="px-3 py-2 text-xs">{row.classification || "-"}</td>
										<td className="px-3 py-2">
											<Button size="sm" variant="secondary" onClick={() => router.push(`/catalog/datasets/${row.id}`)}>
												进入授权
											</Button>
										</td>
									</tr>
								))}
								{filtered.length === 0 && (
									<tr>
										<td colSpan={5} className="px-3 py-10 text-center text-muted-foreground">
											{loading ? "加载中..." : "暂无数据"}
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>
				</CardContent>
			</Card>
		</div>
	);
}

