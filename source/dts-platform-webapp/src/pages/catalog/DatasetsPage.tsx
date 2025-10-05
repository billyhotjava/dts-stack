import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "@/routes/hooks";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";
import { Alert, AlertDescription, AlertTitle } from "@/ui/alert";
import { toast } from "sonner";
import { createDataset, deleteDataset, getCatalogConfig, listDatasets } from "@/api/platformApi";
import { listInfraDataSources } from "@/api/services/infraService";

const SECURITY_LEVELS = [
	{ value: "PUBLIC", label: "公开" },
	{ value: "INTERNAL", label: "内部" },
	{ value: "SECRET", label: "秘密" },
	{ value: "TOP_SECRET", label: "机密" },
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
		sourceType: "INCEPTOR",
		hiveDatabase: "",
		hiveTable: "",
	});
	const [catalogConfig, setCatalogConfig] = useState({
		multiSourceEnabled: false,
		defaultSourceType: "INCEPTOR",
		hasPrimarySource: true,
		primarySourceType: "INCEPTOR",
	});
	const [dataSources, setDataSources] = useState<any[]>([]);
	const [multiSourceUnlocked, setMultiSourceUnlocked] = useState(false);
	const fileInputRef = useRef<HTMLInputElement | null>(null);

const levels = SECURITY_LEVELS;
const normalizeSourceType = useCallback((value: string) => {
	const upper = (value || "").toString().toUpperCase();
	if (upper === "HIVE") return "INCEPTOR";
	return upper;
}, []);
const resolvedDefaultSource = useMemo(
	() => normalizeSourceType(catalogConfig.defaultSourceType || "INCEPTOR"),
	[catalogConfig.defaultSourceType, normalizeSourceType],
);
const primarySourceLabel = useMemo(
	() => normalizeSourceType(catalogConfig.primarySourceType || resolvedDefaultSource),
	[catalogConfig.primarySourceType, resolvedDefaultSource, normalizeSourceType],
);
const multiSourceAllowed = catalogConfig.multiSourceEnabled || multiSourceUnlocked;
const hasPrimarySource = useMemo(() => {
	if (catalogConfig.hasPrimarySource) return true;
	return dataSources.some((ds) => normalizeSourceType(String(ds?.type || "")) === resolvedDefaultSource);
}, [catalogConfig.hasPrimarySource, dataSources, resolvedDefaultSource, normalizeSourceType]);

const sources = useMemo(() => {
	const set = new Set<string>();
	items.forEach((it) => {
		const t = normalizeSourceType(String(it.type || ""));
		if (t) set.add(t);
	});
	set.add(resolvedDefaultSource);
	return Array.from(set);
}, [items, resolvedDefaultSource, normalizeSourceType]);

const renderSourceLabel = (value: string) => {
	const upper = normalizeSourceType(value);
	switch (upper) {
		case "INCEPTOR":
		case "HIVE":
			return "TDS Inceptor";
		case "TRINO":
			return "Trino Catalog";
			case "API":
				return "API 服务";
			case "EXTERNAL":
				return "外部数据";
			default:
				return upper || "-";
		}
	};

	const toggleSecretMultiSource = useCallback(() => {
		setMultiSourceUnlocked((prev) => {
			const next = !prev;
			if (next) {
				toast.info("多数据源选项已解锁（提交仍需正式授权）");
			} else {
				toast.success("已恢复单数据源视图");
			}
			return next;
		});
	}, []);

	const fetchList = async () => {
		setLoading(true);
		try {
			const resp = (await listDatasets({ page, size, keyword })) as any;
			const content = (resp && resp.content) || [];
			const mapped: ListItem[] = content.map((it: any) => {
				const rawType = String(it.type || "").trim().toUpperCase();
				const rawTags = it.tags;
				const tags: string[] = Array.isArray(rawTags)
					? rawTags
					: typeof rawTags === "string" && rawTags.length > 0
					? rawTags
							.split(/[;,\s]+/)
							.map((s: string) => s.trim())
							.filter(Boolean)
					: [];
				return {
					id: String(it.id),
					name: it.name,
					owner: it.owner || "",
					classification: (it.classification || "INTERNAL") as SecurityLevel,
					domainId: String(it.domainId || ""),
					type: normalizeSourceType(rawType || resolvedDefaultSource),
					tags,
				};
			});
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
		const loadBasics = async () => {
			try {
				const cfg = (await getCatalogConfig()) as any;
				if (cfg) {
					setCatalogConfig({
						multiSourceEnabled: Boolean(cfg.multiSourceEnabled),
						defaultSourceType: String(cfg.defaultSourceType || "HIVE"),
						hasPrimarySource: Boolean(cfg.hasPrimarySource),
						primarySourceType: String(cfg.primarySourceType || "HIVE"),
					});
				}
			} catch (e) {
				console.warn("加载数据目录配置失败", e);
			}
			try {
				const ds = (await listInfraDataSources()) as any[];
				if (Array.isArray(ds)) {
					setDataSources(ds);
				}
			} catch (e) {
				console.warn("加载数据源信息失败", e);
			}
		};
		void loadBasics();
	}, []);

	useEffect(() => {
		void fetchList();
	}, [page, size, resolvedDefaultSource]);

	useEffect(() => {
		const handler = (event: KeyboardEvent) => {
			if (event.altKey && event.shiftKey && (event.key === "M" || event.key === "m")) {
				toggleSecretMultiSource();
			}
		};
		window.addEventListener("keydown", handler);
		return () => window.removeEventListener("keydown", handler);
	}, [toggleSecretMultiSource]);

	useEffect(() => {
		if (!multiSourceAllowed) {
			setForm((prev) => (prev.sourceType === resolvedDefaultSource ? prev : { ...prev, sourceType: resolvedDefaultSource }));
		}
	}, [multiSourceAllowed, resolvedDefaultSource]);

	const filtered = useMemo(() => {
		return items.filter((it) => {
			if (levelFilter !== "all" && it.classification !== levelFilter) return false;
			if (sourceFilter !== "all" && normalizeSourceType(it.type) !== sourceFilter) return false;
			if (keyword && !it.name.toLowerCase().includes(keyword.toLowerCase())) return false;
			return true;
		});
	}, [items, levelFilter, sourceFilter, keyword, normalizeSourceType]);

	const totalPages = useMemo(() => Math.max(1, Math.ceil(total / size)), [total, size]);

	const onCreate = async () => {
		if (!hasPrimarySource) {
			toast.error("请先在基础管理中完善默认数据源连接");
			return;
		}
		if (!form.name.trim()) {
			toast.error("请填写数据集名称");
			return;
		}
		try {
			const selectedSourceType = normalizeSourceType(
				(multiSourceAllowed ? form.sourceType : resolvedDefaultSource) || resolvedDefaultSource,
			);
			const tagsList = form.tags
				.split(",")
				.map((s) => s.trim())
				.filter(Boolean);
			const hiveDatabase = form.hiveDatabase.trim();
			const hiveTable = form.hiveTable.trim();
				const payload = {
					name: form.name.trim(),
					owner: form.owner.trim(),
					classification: form.classification,
					tags: tagsList,
					description: form.description.trim(),
					type: selectedSourceType,
					hiveDatabase: selectedSourceType === "INCEPTOR" ? hiveDatabase || undefined : undefined,
					hiveTable: selectedSourceType === "INCEPTOR" ? hiveTable || undefined : undefined,
					source: {
						sourceType: selectedSourceType,
						hiveDatabase: selectedSourceType === "INCEPTOR" ? hiveDatabase || undefined : undefined,
						hiveTable: selectedSourceType === "INCEPTOR" ? hiveTable || undefined : undefined,
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
		if (!hasPrimarySource) {
			toast.error("请先配置默认数据源，再尝试导入");
			return;
		}
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
				const candidate = String((r.sourceType || "").toString().trim() || "").toUpperCase();
				const sourceType = normalizeSourceType(multiSourceAllowed && candidate ? candidate : resolvedDefaultSource);
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
						type: sourceType,
						hiveDatabase: sourceType === "INCEPTOR" ? r.hiveDatabase || undefined : undefined,
						hiveTable: sourceType === "INCEPTOR" ? r.hiveTable || undefined : undefined,
						source: { sourceType },
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
					<CardTitle className="flex items-center gap-2 text-base">
						数据资产目录
						<span className="rounded bg-muted px-2 py-0.5 text-[11px] font-normal text-muted-foreground">
							默认来源：{renderSourceLabel(primarySourceLabel)}
						</span>
					</CardTitle>
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
						<Select value={sourceFilter} onValueChange={(v) => setSourceFilter(v)}>
							<SelectTrigger className="w-[140px]">
								<SelectValue placeholder="来源" />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value="all">全部来源</SelectItem>
								{sources.map((s) => (
									<SelectItem key={s} value={s}>
										{renderSourceLabel(s)}
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
						<Button
							onClick={() => {
								if (!hasPrimarySource) {
									toast.error("请先配置默认数据源");
									return;
								}
								setOpen(true);
							}}
						>
							新建
						</Button>
					</div>
				</CardHeader>
				<CardContent className="space-y-3">
					{!hasPrimarySource && (
						<Alert variant="destructive">
							<AlertTitle>缺少默认数据源</AlertTitle>
							<AlertDescription>
								未检测到 {renderSourceLabel(primarySourceLabel)} 连接，请前往「基础管理 - 数据源」完成配置后再新建或导入数据集。
							</AlertDescription>
						</Alert>
					)}
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
									<td className="px-3 py-2 text-xs">{renderSourceLabel(d.type)}</td>
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
							<Label
								onDoubleClick={toggleSecretMultiSource}
								className="select-none"
								title="双击尝试解锁多源能力"
							>
								来源类型
							</Label>
							{!multiSourceAllowed && (
								<p className="text-xs text-muted-foreground">
									当前版本默认接入 {renderSourceLabel(primarySourceLabel)}。如需多数据源，请关注版本升级信息。
								</p>
							)}
							<Select
								value={form.sourceType}
								onValueChange={(v) => setForm((f) => ({ ...f, sourceType: v.toUpperCase() }))}
								disabled={!multiSourceAllowed}
							>
								<SelectTrigger>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value={resolvedDefaultSource}>
										{renderSourceLabel(resolvedDefaultSource)}
									</SelectItem>
									{multiSourceAllowed &&
										["TRINO", "API", "EXTERNAL"]
											.filter((option) => option !== resolvedDefaultSource)
											.map((option) => (
												<SelectItem key={option} value={option}>
													{renderSourceLabel(option)}
												</SelectItem>
											))}
								</SelectContent>
							</Select>
							{multiSourceUnlocked && !catalogConfig.multiSourceEnabled && (
								<Alert className="mt-2 bg-muted/40 py-2">
									<AlertDescription className="text-xs">
										多数据源为高级功能，提交后仍需管理员确认授权。
									</AlertDescription>
								</Alert>
							)}
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
