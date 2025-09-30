import { useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "@/routes/hooks";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";
import { toast } from "sonner";
import { createDataset, deleteDataset, listDatasets } from "@/api/platformApi";

const SECURITY_LEVELS = [
	{ value: "PUBLIC", label: "公开" },
	{ value: "INTERNAL", label: "内部" },
	{ value: "SECRET", label: "秘密" },
	{ value: "TOP_SECRET", label: "核心" },
] as const;

type SecurityLevel = (typeof SECURITY_LEVELS)[number]["value"];

type ListItem = {
	id: string;
	name: string;
	owner: string;
	classification: SecurityLevel;
	domainId: string;
	type: string;
	tags?: string[];
};

export default function DatasetsPage() {
	const router = useRouter();
	const [items, setItems] = useState<ListItem[]>([]);
	const [total, setTotal] = useState(0);
	const [loading, setLoading] = useState(false);
	const [page, setPage] = useState(0);
	const [size] = useState(10);
	const [keyword, setKeyword] = useState("");
	const [levelFilter, setLevelFilter] = useState<string>("all");
	const [sourceFilter, setSourceFilter] = useState<string>("all");
	const [open, setOpen] = useState(false);
	const [form, setForm] = useState({
		name: "",
		owner: "",
		classification: "INTERNAL" as SecurityLevel,
		tags: "",
		description: "",
		sourceType: "HIVE",
		hiveDatabase: "",
		hiveTable: "",
	});
	const fileInputRef = useRef<HTMLInputElement | null>(null);

	const levels = SECURITY_LEVELS;

	const sources = useMemo(() => Array.from(new Set(items.map((it) => it.type))), [items]);

	const fetchList = async () => {
		setLoading(true);
		try {
			const resp = (await listDatasets({ page, size, keyword })) as any;
			const content = (resp && resp.content) || [];
			const mapped: ListItem[] = content.map((it: any) => ({
				id: String(it.id),
				name: it.name,
				owner: it.owner || "",
				classification: (it.classification || "INTERNAL") as SecurityLevel,
				domainId: String(it.domainId || ""),
				type: String(it.type || "") || "EXTERNAL",
				tags: it.tags || [],
			}));
			setItems(mapped);
			setTotal(Number(resp?.total || mapped.length));
		} catch (e) {
			console.error(e);
			toast.error("加载失败");
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void fetchList();
	}, [page, size]);

	const filtered = useMemo(() => {
		return items.filter((it) => {
			if (levelFilter !== "all" && it.classification !== levelFilter) return false;
			if (sourceFilter !== "all" && it.type !== sourceFilter) return false;
			if (keyword && !it.name.toLowerCase().includes(keyword.toLowerCase())) return false;
			return true;
		});
	}, [items, levelFilter, sourceFilter, keyword]);

	const totalPages = useMemo(() => Math.max(1, Math.ceil(total / size)), [total, size]);

	const onCreate = async () => {
		if (!form.name.trim()) {
			toast.error("请填写数据集名称");
			return;
		}
		try {
			const payload = {
				name: form.name.trim(),
				owner: form.owner.trim(),
				classification: form.classification,
				tags: form.tags
					.split(",")
					.map((s) => s.trim())
					.filter(Boolean),
				description: form.description.trim(),
				source: {
					sourceType: form.sourceType,
					hiveDatabase: form.hiveDatabase || undefined,
					hiveTable: form.hiveTable || undefined,
				},
				exposure: ["VIEW"],
			};
			const created = (await createDataset(payload)) as any;
			toast.success("已创建");
			setOpen(false);
			setPage(0);
			await fetchList();
			if (created?.id) router.push(`/catalog/datasets/${created.id}`);
		} catch (e) {
			console.error(e);
			toast.error("创建失败");
		}
	};

	const onDelete = async (id: string) => {
		try {
			await deleteDataset(id);
			toast.success("已删除");
			await fetchList();
		} catch (e) {
			console.error(e);
			toast.error("删除失败");
		}
	};

	const onImport = async (file: File) => {
		const text = await file.text();
		let rows: any[] = [];
		try {
			if (file.name.endsWith(".json")) {
				rows = JSON.parse(text);
			} else {
				// naive CSV: name,owner,classification,sourceType,tags
				const lines = text.split(/\r?\n/).filter(Boolean);
				const [header, ...data] = lines;
				const keys = header.split(",").map((s) => s.trim());
				rows = data.map((line) => {
					const values = line.split(",");
					return Object.fromEntries(keys.map((k, i) => [k, (values[i] ?? "").trim()]));
				});
			}
		} catch (err) {
			toast.error("文件解析失败");
			return;
		}

		let ok = 0;
		for (const r of rows) {
			try {
				await createDataset({
					name: r.name,
					owner: r.owner || "",
					classification: (r.classification || "INTERNAL") as SecurityLevel,
					tags:
						typeof r.tags === "string"
							? r.tags
									.split(";")
									.map((s: string) => s.trim())
									.filter(Boolean)
							: [],
					source: { sourceType: (r.sourceType || "EXTERNAL") as any },
					exposure: ["VIEW"],
				});
				ok += 1;
			} catch {}
		}
		toast.success(`导入完成：${ok}/${rows.length}`);
		await fetchList();
	};

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-wrap items-center justify-between gap-2">
					<CardTitle className="text-base">数据资产目录</CardTitle>
					<div className="flex flex-wrap items-center gap-2">
						<Input
							placeholder="搜索数据集名"
							value={keyword}
							onChange={(e) => setKeyword(e.target.value)}
							onKeyDown={(e) => e.key === "Enter" && fetchList()}
							className="w-[200px]"
						/>
						<Select value={levelFilter} onValueChange={setLevelFilter}>
							<SelectTrigger className="w-[140px]">
								<SelectValue placeholder="密级" />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value="all">全部密级</SelectItem>
								{levels.map((l) => (
									<SelectItem key={l.value} value={l.value}>
										{l.label}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
						<Select value={sourceFilter} onValueChange={setSourceFilter}>
							<SelectTrigger className="w-[140px]">
								<SelectValue placeholder="来源" />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value="all">全部来源</SelectItem>
								{sources.map((s) => (
									<SelectItem key={s} value={s}>
										{s}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
						<Button variant="outline" onClick={fetchList} disabled={loading}>
							刷新
						</Button>
						<input
							ref={fileInputRef}
							type="file"
							accept=".csv,.json"
							className="hidden"
							onChange={(e) => {
								const f = e.target.files?.[0];
								if (f) void onImport(f);
								e.currentTarget.value = "";
							}}
						/>
						<Button variant="secondary" onClick={() => fileInputRef.current?.click()}>
							批量导入
						</Button>
						<Button onClick={() => setOpen(true)}>新建</Button>
					</div>
				</CardHeader>
				<CardContent className="space-y-3">
					<div className="overflow-hidden rounded-md border">
						<table className="w-full min-w-[920px] table-fixed text-sm">
							<thead className="bg-muted/50">
								<tr className="text-left">
									<th className="px-3 py-2 w-[32px]">#</th>
									<th className="px-3 py-2">名称</th>
									<th className="px-3 py-2">负责人</th>
									<th className="px-3 py-2">密级</th>
									<th className="px-3 py-2">来源</th>
									<th className="px-3 py-2">操作</th>
								</tr>
							</thead>
							<tbody>
								{filtered.map((d, idx) => (
									<tr key={d.id} className="border-b last:border-b-0">
										<td className="px-3 py-2 text-xs text-muted-foreground">{idx + 1}</td>
										<td className="px-3 py-2 font-medium">{d.name}</td>
										<td className="px-3 py-2">{d.owner || "-"}</td>
										<td className="px-3 py-2 text-xs">
											{SECURITY_LEVELS.find((l) => l.value === d.classification)?.label}
										</td>
										<td className="px-3 py-2 text-xs">{d.type}</td>
										<td className="px-3 py-2 space-x-1">
											<Button variant="ghost" size="sm" onClick={() => router.push(`/catalog/datasets/${d.id}`)}>
												编辑
											</Button>
											<Button variant="ghost" size="sm" onClick={() => onDelete(d.id)}>
												删除
											</Button>
										</td>
									</tr>
								))}
								{!filtered.length && (
									<tr>
										<td colSpan={6} className="px-3 py-6 text-center text-xs text-muted-foreground">
											{loading ? "加载中…" : "暂无数据"}
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>
					<div className="flex items-center justify-between text-xs text-muted-foreground">
						<span>
							第 {totalPages ? page + 1 : 0} / {totalPages} 页 · 共 {total} 条
						</span>
						<div className="flex items-center gap-2">
							<Button
								variant="outline"
								size="sm"
								disabled={page === 0}
								onClick={() => setPage((p) => Math.max(0, p - 1))}
							>
								上一页
							</Button>
							<Button
								variant="outline"
								size="sm"
								disabled={page >= totalPages - 1}
								onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
							>
								下一页
							</Button>
						</div>
					</div>
				</CardContent>
			</Card>

			<Dialog open={open} onOpenChange={setOpen}>
				<DialogContent className="max-w-xl">
					<DialogHeader>
						<DialogTitle>新建数据集</DialogTitle>
					</DialogHeader>
					<div className="grid gap-3">
						<div className="grid gap-2">
							<Label>名称 *</Label>
							<Input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} />
						</div>
						<div className="grid gap-2">
							<Label>负责人</Label>
							<Input value={form.owner} onChange={(e) => setForm((f) => ({ ...f, owner: e.target.value }))} />
						</div>
						<div className="grid gap-2">
							<Label>密级</Label>
							<Select
								value={form.classification}
								onValueChange={(v: SecurityLevel) => setForm((f) => ({ ...f, classification: v }))}
							>
								<SelectTrigger>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									{SECURITY_LEVELS.map((l) => (
										<SelectItem key={l.value} value={l.value}>
											{l.label}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						</div>
						<div className="grid gap-2">
							<Label>标签（用逗号分隔）</Label>
							<Input value={form.tags} onChange={(e) => setForm((f) => ({ ...f, tags: e.target.value }))} />
						</div>
						<div className="grid gap-2">
							<Label>描述</Label>
							<Textarea
								value={form.description}
								onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
							/>
						</div>
						<div className="grid gap-2">
							<Label>来源类型</Label>
							<Select value={form.sourceType} onValueChange={(v) => setForm((f) => ({ ...f, sourceType: v }))}>
								<SelectTrigger>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="HIVE">HIVE</SelectItem>
									<SelectItem value="TRINO">TRINO</SelectItem>
									<SelectItem value="API">API</SelectItem>
									<SelectItem value="EXTERNAL">EXTERNAL</SelectItem>
								</SelectContent>
							</Select>
						</div>
						{form.sourceType === "HIVE" && (
							<div className="grid grid-cols-2 gap-3">
								<div className="grid gap-2">
									<Label>Hive Database</Label>
									<Input
										value={form.hiveDatabase}
										onChange={(e) => setForm((f) => ({ ...f, hiveDatabase: e.target.value }))}
									/>
								</div>
								<div className="grid gap-2">
									<Label>Hive Table</Label>
									<Input
										value={form.hiveTable}
										onChange={(e) => setForm((f) => ({ ...f, hiveTable: e.target.value }))}
									/>
								</div>
							</div>
						)}
					</div>
					<DialogFooter>
						<Button variant="ghost" onClick={() => setOpen(false)}>
							取消
						</Button>
						<Button onClick={onCreate}>创建</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</div>
	);
}
