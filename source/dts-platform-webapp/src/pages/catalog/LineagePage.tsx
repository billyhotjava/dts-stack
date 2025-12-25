import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { createCatalogLineage, deleteCatalogLineage, getCatalogLineage, listDatasets } from "@/api/platformApi";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";

type DatasetOption = { id: string; name: string; ownerDept?: string | null; classification?: string | null };
type LineageEdge = {
	id: string;
	upstreamDatasetId: string;
	downstreamDatasetId: string;
	upstreamName?: string | null;
	downstreamName?: string | null;
	relationType?: string | null;
	notes?: string | null;
};

const PAGE_SIZE = 200;

export default function LineagePage() {
	const [datasetKeyword, setDatasetKeyword] = useState("");
	const [datasets, setDatasets] = useState<DatasetOption[]>([]);
	const [datasetsLoading, setDatasetsLoading] = useState(false);
	const [datasetId, setDatasetId] = useState("");

	const [loading, setLoading] = useState(false);
	const [upstreams, setUpstreams] = useState<LineageEdge[]>([]);
	const [downstreams, setDownstreams] = useState<LineageEdge[]>([]);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [direction, setDirection] = useState<"UPSTREAM_TO_CURRENT" | "CURRENT_TO_DOWNSTREAM">("UPSTREAM_TO_CURRENT");
	const [otherDatasetId, setOtherDatasetId] = useState("");
	const [relationType, setRelationType] = useState("ETL");
	const [notes, setNotes] = useState("");
	const [saving, setSaving] = useState(false);

	const loadDatasets = useCallback(async () => {
		setDatasetsLoading(true);
		try {
			const resp = (await listDatasets({ page: 0, size: PAGE_SIZE, keyword: datasetKeyword.trim() })) as any;
			const content = Array.isArray(resp?.content) ? resp.content : [];
			setDatasets(
				content.map((d: any) => ({
					id: String(d.id),
					name: String(d.name || d.id),
					ownerDept: d.ownerDept,
					classification: d.classification,
				})),
			);
		} catch (e) {
			console.error(e);
			toast.error("加载数据集失败");
		} finally {
			setDatasetsLoading(false);
		}
	}, [datasetKeyword]);

	const loadLineage = useCallback(async () => {
		if (!datasetId) {
			setUpstreams([]);
			setDownstreams([]);
			return;
		}
		setLoading(true);
		try {
			const resp = (await getCatalogLineage(datasetId)) as any;
			const ups = Array.isArray(resp?.upstreams) ? resp.upstreams : [];
			const downs = Array.isArray(resp?.downstreams) ? resp.downstreams : [];
			setUpstreams(ups.map((e: any) => ({ ...e, id: String(e.id) })));
			setDownstreams(downs.map((e: any) => ({ ...e, id: String(e.id) })));
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "加载血缘失败");
		} finally {
			setLoading(false);
		}
	}, [datasetId]);

	useEffect(() => {
		void loadDatasets();
	}, [loadDatasets]);

	useEffect(() => {
		void loadLineage();
	}, [loadLineage]);

	const currentDataset = useMemo(() => datasets.find((d) => d.id === datasetId) || null, [datasetId, datasets]);
	const selectableOtherDatasets = useMemo(() => datasets.filter((d) => d.id !== datasetId), [datasetId, datasets]);

	const openCreate = useCallback(() => {
		if (!datasetId) {
			toast.error("请先选择数据集");
			return;
		}
		setDirection("UPSTREAM_TO_CURRENT");
		setOtherDatasetId("");
		setRelationType("ETL");
		setNotes("");
		setDialogOpen(true);
	}, [datasetId]);

	const submit = useCallback(async () => {
		if (!datasetId) return;
		const other = otherDatasetId.trim();
		if (!other) {
			toast.error("请选择关联数据集");
			return;
		}
		let upstreamDatasetId = "";
		let downstreamDatasetId = "";
		if (direction === "UPSTREAM_TO_CURRENT") {
			upstreamDatasetId = other;
			downstreamDatasetId = datasetId;
		} else {
			upstreamDatasetId = datasetId;
			downstreamDatasetId = other;
		}
		setSaving(true);
		try {
			await createCatalogLineage({
				upstreamDatasetId,
				downstreamDatasetId,
				relationType: relationType.trim() || "ETL",
				notes: notes.trim() || null,
			});
			toast.success("已保存血缘关系");
			setDialogOpen(false);
			await loadLineage();
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "保存失败（可能无权限）");
		} finally {
			setSaving(false);
		}
	}, [datasetId, direction, loadLineage, notes, otherDatasetId, relationType]);

	const removeEdge = useCallback(
		async (edgeId: string) => {
			if (!edgeId) return;
			if (!confirm("确认删除该血缘关系？")) return;
			try {
				await deleteCatalogLineage(edgeId);
				toast.success("已删除");
				await loadLineage();
			} catch (e: any) {
				console.error(e);
				toast.error(e?.message || "删除失败（可能无权限）");
			}
		},
		[loadLineage],
	);

	return (
		<div className="flex flex-col gap-6">
			<Card>
				<CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
					<CardTitle>血缘与影响分析（轻量）</CardTitle>
					<div className="flex flex-col gap-2 md:flex-row md:items-center">
						<Input
							value={datasetKeyword}
							onChange={(e) => setDatasetKeyword(e.target.value)}
							placeholder="搜索数据集"
							className="w-full md:w-64"
						/>
						<Button variant="secondary" onClick={loadDatasets} disabled={datasetsLoading}>
							{datasetsLoading ? "加载中…" : "搜索"}
						</Button>
					</div>
				</CardHeader>
				<CardContent className="space-y-4">
					<div className="grid gap-4 md:grid-cols-2">
						<div className="space-y-2">
							<Label>选择数据集</Label>
							<Select value={datasetId || "__NONE__"} onValueChange={(v) => setDatasetId(v === "__NONE__" ? "" : v)}>
								<SelectTrigger>
									<SelectValue placeholder="请选择…" />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="__NONE__">（未选择）</SelectItem>
									{datasets.map((d) => (
										<SelectItem key={d.id} value={d.id}>
											{d.name}
											<span className="text-xs text-muted-foreground">{d.ownerDept ? ` · ${d.ownerDept}` : ""}</span>
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						</div>
						<div className="space-y-2">
							<Label>操作</Label>
							<div className="flex flex-wrap gap-2">
								<Button onClick={openCreate} disabled={!datasetId}>
									新增血缘
								</Button>
								<Button variant="outline" onClick={loadLineage} disabled={!datasetId || loading}>
									刷新
								</Button>
							</div>
							<div className="text-xs text-muted-foreground">
								当前实现为“手工血缘”，用于交付期快速形成影响分析闭环；后续可对接 ETL/SQL 解析自动补全。
							</div>
						</div>
					</div>

					{currentDataset ? (
						<div className="flex flex-wrap gap-2">
							<Badge variant="outline">{String(currentDataset.classification || "-")}</Badge>
							<Badge variant="secondary">{currentDataset.ownerDept ? `部门:${currentDataset.ownerDept}` : "部门:未指定"}</Badge>
						</div>
					) : null}
				</CardContent>
			</Card>

			<div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
				<Card>
					<CardHeader>
						<CardTitle className="text-base">上游（来源）</CardTitle>
					</CardHeader>
					<CardContent>
						<ScrollArea className="h-[520px] pr-2">
							<div className="space-y-2">
								{upstreams.map((e) => (
									<div key={e.id} className="rounded-md border px-3 py-2">
										<div className="flex items-center justify-between gap-2">
											<div className="min-w-0">
												<div className="truncate text-sm font-medium">{e.upstreamName || e.upstreamDatasetId}</div>
												<div className="truncate text-xs text-muted-foreground">{e.relationType || "ETL"}</div>
											</div>
											<Button size="sm" variant="outline" onClick={() => removeEdge(e.id)}>
												删除
											</Button>
										</div>
										{e.notes ? <div className="mt-1 text-xs text-muted-foreground">{e.notes}</div> : null}
									</div>
								))}
								{!loading && datasetId && upstreams.length === 0 ? (
									<div className="py-10 text-center text-sm text-muted-foreground">暂无上游</div>
								) : null}
								{loading ? <div className="py-10 text-center text-sm text-muted-foreground">加载中…</div> : null}
							</div>
						</ScrollArea>
					</CardContent>
				</Card>

				<Card>
					<CardHeader>
						<CardTitle className="text-base">下游（影响范围）</CardTitle>
					</CardHeader>
					<CardContent>
						<ScrollArea className="h-[520px] pr-2">
							<div className="space-y-2">
								{downstreams.map((e) => (
									<div key={e.id} className="rounded-md border px-3 py-2">
										<div className="flex items-center justify-between gap-2">
											<div className="min-w-0">
												<div className="truncate text-sm font-medium">{e.downstreamName || e.downstreamDatasetId}</div>
												<div className="truncate text-xs text-muted-foreground">{e.relationType || "ETL"}</div>
											</div>
											<Button size="sm" variant="outline" onClick={() => removeEdge(e.id)}>
												删除
											</Button>
										</div>
										{e.notes ? <div className="mt-1 text-xs text-muted-foreground">{e.notes}</div> : null}
									</div>
								))}
								{!loading && datasetId && downstreams.length === 0 ? (
									<div className="py-10 text-center text-sm text-muted-foreground">暂无下游</div>
								) : null}
								{loading ? <div className="py-10 text-center text-sm text-muted-foreground">加载中…</div> : null}
							</div>
						</ScrollArea>
					</CardContent>
				</Card>
			</div>

			<Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
				<DialogContent className="max-w-2xl">
					<DialogHeader>
						<DialogTitle>新增血缘关系</DialogTitle>
					</DialogHeader>
					<div className="grid gap-4 md:grid-cols-2">
						<div className="space-y-2">
							<Label>方向</Label>
							<Select value={direction} onValueChange={(v) => setDirection(v as any)}>
								<SelectTrigger>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="UPSTREAM_TO_CURRENT">上游 → 当前</SelectItem>
									<SelectItem value="CURRENT_TO_DOWNSTREAM">当前 → 下游</SelectItem>
								</SelectContent>
							</Select>
						</div>
						<div className="space-y-2">
							<Label>关联数据集</Label>
							<Select value={otherDatasetId || "__NONE__"} onValueChange={(v) => setOtherDatasetId(v === "__NONE__" ? "" : v)}>
								<SelectTrigger>
									<SelectValue placeholder="请选择…" />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="__NONE__">（未选择）</SelectItem>
									{selectableOtherDatasets.map((d) => (
										<SelectItem key={d.id} value={d.id}>
											{d.name}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						</div>
						<div className="space-y-2">
							<Label>关系类型</Label>
							<Input value={relationType} onChange={(e) => setRelationType(e.target.value)} placeholder="ETL/VIEW/API" />
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label>备注</Label>
							<Textarea value={notes} onChange={(e) => setNotes(e.target.value)} className="min-h-[120px]" />
						</div>
					</div>
					<DialogFooter className="mt-4">
						<Button variant="outline" onClick={() => setDialogOpen(false)}>
							取消
						</Button>
						<Button onClick={submit} disabled={saving}>
							{saving ? "保存中…" : "保存"}
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</div>
	);
}

