import { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";
import { ScrollArea } from "@/ui/scroll-area";
import { toast } from "sonner";
import {
    getDataset,
    previewSecurityViews,
    updateDataset,
    getAccessPolicy,
    upsertAccessPolicy,
    syncDatasetSchema,
    previewDataset,
    applyPolicy,
} from "@/api/platformApi";
import type { DatasetAsset, SecurityLevel } from "@/types/catalog";

const SECURITY_LEVELS = [
	{ value: "PUBLIC", label: "公开" },
	{ value: "INTERNAL", label: "内部" },
	{ value: "SECRET", label: "秘密" },
	{ value: "TOP_SECRET", label: "核心" },
] as const;

export default function DatasetDetailPage() {
	const params = useParams();
	const id = String(params.id || "");
	const [loading, setLoading] = useState(true);
	const [saving, setSaving] = useState(false);
	const [dataset, setDataset] = useState<DatasetAsset | null>(null);

	const [allowRoles, setAllowRoles] = useState("ROLE_PUBLIC,ROLE_INTERNAL");
	const [rowFilter, setRowFilter] = useState("");
	const [defaultMasking, setDefaultMasking] = useState("NONE");
    const [preview, setPreview] = useState<Record<string, string> | null>(null);
    const [dataPreview, setDataPreview] = useState<{ headers: string[]; rows: any[] } | null>(null);
    const [busy, setBusy] = useState(false);

	useEffect(() => {
		(async () => {
			try {
				const data = (await getDataset(id)) as any;
				setDataset(data);
				try {
					const p = (await getAccessPolicy(id)) as any;
					if (p) {
						setAllowRoles(p.allowRoles || allowRoles);
						setRowFilter(p.rowFilter || "");
						setDefaultMasking(p.defaultMasking || "NONE");
					}
				} catch {}
			} catch (e) {
				console.error(e);
				toast.error("加载失败");
			} finally {
				setLoading(false);
			}
		})();
	}, [id]);

	const onAddColumn = () => {
		if (!dataset) return;
		const next = { ...dataset } as DatasetAsset;
		const cols = next.table?.columns || [];
		const idc = `${dataset.id}_c_${Date.now()}`;
		const col = { id: idc, name: `col_${cols.length + 1}`, dataType: "STRING", sensitiveTags: [] as string[] };
		next.table = next.table || { tableName: dataset.name, columns: [] };
		next.table.columns = [...cols, col];
		setDataset(next);
	};

	const onRemoveColumn = (cid: string) => {
		if (!dataset || !dataset.table) return;
		setDataset({ ...dataset, table: { ...dataset.table, columns: dataset.table.columns.filter((c) => c.id !== cid) } });
	};

	const onSave = async () => {
		if (!dataset) return;
		setSaving(true);
		try {
			await updateDataset(dataset.id, dataset);
			await upsertAccessPolicy(dataset.id, { allowRoles, rowFilter, defaultMasking });
			toast.success("已保存");
		} catch (e) {
			console.error(e);
			toast.error("保存失败");
		} finally {
			setSaving(false);
		}
	};

	const levelOptions = SECURITY_LEVELS;

	const hasHive = dataset?.source?.sourceType === "HIVE";

	const viewKeys = useMemo(() => (preview ? Object.keys(preview) : []), [preview]);

	if (loading) return <div className="text-sm text-muted-foreground">加载中…</div>;
	if (!dataset) return <div className="text-sm text-muted-foreground">未找到该数据集</div>;

	return (
		<div className="space-y-4">
			<Card>
            <CardHeader className="flex items-center justify-between">
                <CardTitle className="text-base">基础信息</CardTitle>
                <div className="flex items-center gap-2">
                    <Button
                        variant="outline"
                        onClick={async () => {
                            try {
                                await syncDatasetSchema(id);
                                toast.success("已同步表结构");
                            } catch (e) {
                                console.error(e);
                                toast.error("同步失败");
                            }
                        }}
                    >
                        同步表结构
                    </Button>
                    <Button
                        variant="outline"
                        onClick={async () => {
                            try {
                                const r = (await previewDataset(id, 50)) as any;
                                setDataPreview({ headers: r.headers || [], rows: r.rows || [] });
                                toast.success("已加载数据预览");
                            } catch (e) {
                                console.error(e);
                                toast.error("预览失败");
                            }
                        }}
                    >
                        数据预览
                    </Button>
                    <Button
                        variant="outline"
                        onClick={async () => {
                            try {
                                const data = (await previewSecurityViews(dataset.id)) as any;
                                setPreview(data || {});
                                toast.success("已生成预览");
                            } catch (e) {
                                console.error(e);
                                toast.error("预览失败");
                            }
                        }}
                    >
                        预览安全视图SQL
                    </Button>
                    <Button
                        onClick={async () => {
                            setBusy(true);
                            try {
                                await applyPolicy(id);
                                toast.success("策略已生效，视图已生成/刷新");
                            } catch (e) {
                                console.error(e);
                                toast.error("策略生效失败");
                            } finally {
                                setBusy(false);
                            }
                        }}
                        disabled={busy}
                    >
                        {busy ? "生效中…" : "策略生效"}
                    </Button>
                    <Button onClick={onSave} disabled={saving}>
                        {saving ? "保存中…" : "保存"}
                    </Button>
                </div>
            </CardHeader>
				<CardContent className="grid gap-4 md:grid-cols-2">
					<div className="grid gap-2">
						<Label>名称</Label>
						<Input
							value={dataset.name}
							onChange={(e) => setDataset({ ...(dataset as DatasetAsset), name: e.target.value })}
						/>
					</div>
					<div className="grid gap-2">
						<Label>负责人</Label>
						<Input
							value={dataset.owner}
							onChange={(e) => setDataset({ ...(dataset as DatasetAsset), owner: e.target.value })}
						/>
					</div>
					<div className="grid gap-2">
						<Label>密级</Label>
						<Select
							value={dataset.classification}
							onValueChange={(v: SecurityLevel) => setDataset({ ...(dataset as DatasetAsset), classification: v })}
						>
							<SelectTrigger>
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								{levelOptions.map((l) => (
									<SelectItem key={l.value} value={l.value}>
										{l.label}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
					</div>
					<div className="grid gap-2">
						<Label>标签（逗号分隔）</Label>
						<Input
							value={dataset.tags.join(",")}
							onChange={(e) =>
								setDataset({
									...(dataset as DatasetAsset),
									tags: e.target.value
										.split(",")
										.map((s) => s.trim())
										.filter(Boolean),
								})
							}
						/>
					</div>
					<div className="md:col-span-2 grid gap-2">
						<Label>描述</Label>
						<Textarea
							value={dataset.description || ""}
							onChange={(e) => setDataset({ ...(dataset as DatasetAsset), description: e.target.value })}
						/>
					</div>
					<div className="grid gap-2">
						<Label>来源类型</Label>
						<Input disabled value={dataset.source?.sourceType || "EXTERNAL"} />
					</div>
					{hasHive && (
						<>
							<div className="grid gap-2">
								<Label>Hive Database</Label>
								<Input
									value={dataset.source?.hiveDatabase || ""}
									onChange={(e) =>
										setDataset({
											...(dataset as DatasetAsset),
											source: { ...(dataset.source as any), hiveDatabase: e.target.value },
										})
									}
								/>
							</div>
							<div className="grid gap-2">
								<Label>Hive Table</Label>
								<Input
									value={dataset.source?.hiveTable || ""}
									onChange={(e) =>
										setDataset({
											...(dataset as DatasetAsset),
											source: { ...(dataset.source as any), hiveTable: e.target.value },
										})
									}
								/>
							</div>
						</>
					)}
				</CardContent>
			</Card>

			<Card>
				<CardHeader className="flex items-center justify-between">
					<CardTitle className="text-base">表结构与敏感标注</CardTitle>
					<Button size="sm" variant="outline" onClick={onAddColumn}>
						新增字段
					</Button>
				</CardHeader>
				<CardContent className="p-0">
					<ScrollArea className="h-[360px]">
						<table className="w-full min-w-[840px] table-fixed text-sm">
							<thead className="sticky top-0 bg-muted/40 text-left text-xs uppercase text-muted-foreground">
								<tr>
									<th className="px-3 py-2 w-[40px]">#</th>
									<th className="px-3 py-2">字段名</th>
									<th className="px-3 py-2">类型</th>
									<th className="px-3 py-2">敏感标签</th>
									<th className="px-3 py-2">操作</th>
								</tr>
							</thead>
							<tbody>
								{(dataset.table?.columns || []).map((c, idx) => (
									<tr key={c.id} className="border-b last:border-b-0">
										<td className="px-3 py-2 text-xs text-muted-foreground">{idx + 1}</td>
										<td className="px-3 py-2">
											<Input
												value={c.name}
												onChange={(e) => {
													const cols = (dataset.table?.columns || []).map((it) =>
														it.id === c.id ? { ...it, name: e.target.value } : it,
													);
													setDataset({
														...(dataset as DatasetAsset),
														table: { ...(dataset.table as any), columns: cols },
													});
												}}
											/>
										</td>
										<td className="px-3 py-2">
											<Input
												value={c.dataType}
												onChange={(e) => {
													const cols = (dataset.table?.columns || []).map((it) =>
														it.id === c.id ? { ...it, dataType: e.target.value } : it,
													);
													setDataset({
														...(dataset as DatasetAsset),
														table: { ...(dataset.table as any), columns: cols },
													});
												}}
											/>
										</td>
										<td className="px-3 py-2">
											<Input
												value={(c.sensitiveTags || []).join(",")}
												onChange={(e) => {
													const tags = e.target.value
														.split(",")
														.map((s) => s.trim())
														.filter(Boolean);
													const cols = (dataset.table?.columns || []).map((it) =>
														it.id === c.id ? { ...it, sensitiveTags: tags } : it,
													);
													setDataset({
														...(dataset as DatasetAsset),
														table: { ...(dataset.table as any), columns: cols },
													});
												}}
												placeholder="例如: PII:phone, PII:id"
											/>
										</td>
										<td className="px-3 py-2">
											<Button size="sm" variant="ghost" onClick={() => onRemoveColumn(c.id)}>
												删除
											</Button>
										</td>
									</tr>
								))}
								{!(dataset.table?.columns || []).length && (
									<tr>
										<td colSpan={5} className="px-3 py-6 text-center text-xs text-muted-foreground">
											暂无字段
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</ScrollArea>
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle className="text-base">访问策略（RLS/列级脱敏）</CardTitle>
				</CardHeader>
				<CardContent className="grid gap-3">
					<div className="grid gap-2">
						<Label>允许角色（逗号分隔）</Label>
						<Input value={allowRoles} onChange={(e) => setAllowRoles(e.target.value)} />
					</div>
					<div className="grid gap-2">
						<Label>行级过滤表达式（可选）</Label>
						<Textarea value={rowFilter} onChange={(e) => setRowFilter(e.target.value)} rows={3} />
					</div>
					<div className="grid gap-2">
						<Label>默认兜底策略</Label>
						<Select value={defaultMasking} onValueChange={setDefaultMasking}>
							<SelectTrigger className="w-[200px]">
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value="NONE">NONE</SelectItem>
								<SelectItem value="PARTIAL">PARTIAL</SelectItem>
								<SelectItem value="HASH">HASH</SelectItem>
								<SelectItem value="TOKENIZE">TOKENIZE</SelectItem>
								<SelectItem value="CUSTOM">CUSTOM</SelectItem>
							</SelectContent>
						</Select>
					</div>
				</CardContent>
			</Card>

            {preview && (
				<Card>
					<CardHeader>
						<CardTitle className="text-base">安全视图预览</CardTitle>
					</CardHeader>
					<CardContent>
						<div className="grid gap-3 md:grid-cols-2">
							{viewKeys.map((k) => (
								<div key={k} className="rounded border bg-muted/30 p-2">
									<div className="mb-1 text-xs font-semibold">{k}</div>
									<pre className="text-xs whitespace-pre-wrap">{String(preview[k])}</pre>
								</div>
							))}
						</div>
					</CardContent>
				</Card>
            )}

            {dataPreview && (
                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">数据预览</CardTitle>
                    </CardHeader>
                    <CardContent className="overflow-x-auto">
                        <table className="w-full min-w-[800px] table-fixed border-collapse text-sm">
                            <thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
                                <tr>
                                    {dataPreview.headers.map((h) => (
                                        <th key={h} className="px-3 py-2">{h}</th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {dataPreview.rows.map((row, i) => (
                                    <tr key={i} className="border-b border-border/40 last:border-none">
                                        {dataPreview.headers.map((h) => (
                                            <td key={h} className="px-3 py-2 truncate" title={String(row[h])}>
                                                {String(row[h])}
                                            </td>
                                        ))}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </CardContent>
                </Card>
            )}
		</div>
	);
}
