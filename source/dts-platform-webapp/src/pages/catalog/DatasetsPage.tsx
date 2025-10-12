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
import deptService, { type DeptDto } from "@/api/services/deptService";
import { renderDataLevelLabel } from "@/constants/governance";
import { useActiveDept, useActiveScope } from "@/store/contextStore";

// Legacy display classification removed in favor of DATA_* levels

const DATA_LEVELS = [
  { value: "DATA_PUBLIC", label: "公开 (DATA_PUBLIC)" },
  { value: "DATA_INTERNAL", label: "内部 (DATA_INTERNAL)" },
  { value: "DATA_SECRET", label: "秘密 (DATA_SECRET)" },
  { value: "DATA_TOP_SECRET", label: "机密 (DATA_TOP_SECRET)" },
] as const;
type DataLevel = (typeof DATA_LEVELS)[number]["value"];
type Scope = "DEPT" | "INST";
type ShareScope = "PRIVATE_DEPT" | "SHARE_INST" | "PUBLIC_INST";

type ListItem = {
	id: string;
	name: string;
	owner: string;
	dataLevel?: string;
	scope?: string;
	ownerDept?: string;
	shareScope?: string;
	domainId: string;
	type: string;
	tags?: string[];
};

export default function DatasetsPage() {
	const router = useRouter();
	const activeScope = useActiveScope();
	const activeDept = useActiveDept();
	const [items, setItems] = useState<ListItem[]>([]);
	const [total, setTotal] = useState(0);
	const [loading, setLoading] = useState(false);
	const [page, setPage] = useState(0);
	const [size] = useState(10);
	const [keyword, setKeyword] = useState("");
	const [dataLevelFilter, setDataLevelFilter] = useState<string>("all");
	const [scopeFilter, setScopeFilter] = useState<string>("all");
	const [deptFilter, setDeptFilter] = useState<string>("");
	const [sourceFilter, setSourceFilter] = useState<string>("all");
	const [open, setOpen] = useState(false);
	const [form, setForm] = useState({
		name: "",
		owner: "",
		dataLevel: "DATA_INTERNAL" as DataLevel,
		scope: "DEPT" as Scope,
		ownerDept: "",
		shareScope: "SHARE_INST" as ShareScope,
		tags: "",
		description: "",
		sourceType: "INCEPTOR",
		hiveDatabase: "",
		hiveTable: "",
	});
	const [deptOptions, setDeptOptions] = useState<DeptDto[]>([]);
	const nonRootDeptOptions = useMemo(() => deptOptions.filter((d) => d.parentId != null && d.parentId !== 0), [deptOptions]);
	const [deptLoading, setDeptLoading] = useState(false);
	const [catalogConfig, setCatalogConfig] = useState({
		multiSourceEnabled: false,
		defaultSourceType: "INCEPTOR",
		hasPrimarySource: true,
		primarySourceType: "INCEPTOR",
	});
	const [dataSources, setDataSources] = useState<any[]>([]);
	const [multiSourceUnlocked, setMultiSourceUnlocked] = useState(false);
	const fileInputRef = useRef<HTMLInputElement | null>(null);

// const levels = SECURITY_LEVELS; // removed
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

const deptLabelMap = useMemo(() => {
  const map = new Map<string, string>();
  for (const d of deptOptions) {
    const label = d.nameZh || d.nameEn || d.code;
    map.set(d.code, label);
  }
  return map;
}, [deptOptions]);

const renderDept = (code?: string) => {
  if (!code) return "-";
  return deptLabelMap.get(code) || code;
};

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

// Chinese labels for scope/share-scope
const SCOPE_LABELS: Record<string, string> = { DEPT: "部门", INST: "研究所" };
const SHARE_SCOPE_LABELS: Record<string, string> = {
  PRIVATE_DEPT: "部门私有",
  SHARE_INST: "所内共享",
  PUBLIC_INST: "所内公开",
};
const renderScopeLabel = (v?: string) => (v ? SCOPE_LABELS[String(v).toUpperCase()] || v : "-");
const renderShareScopeLabel = (v?: string) => (v ? SHARE_SCOPE_LABELS[String(v).toUpperCase()] || v : "-");

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
				const params: any = { page, size, keyword };
				if (dataLevelFilter !== "all") params.dataLevel = dataLevelFilter;
				if (scopeFilter !== "all") params.scope = scopeFilter;
				if (deptFilter.trim()) params.ownerDept = deptFilter.trim();
				const resp = (await listDatasets(params)) as any;
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
						dataLevel: it.dataLevel || undefined,
						scope: it.scope || undefined,
						ownerDept: it.ownerDept || undefined,
						shareScope: it.shareScope || undefined,
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

	// Load department options for filters and creation dialog
	useEffect(() => {
		let mounted = true;
		setDeptLoading(true);
		deptService
			.listDepartments()
			.then((list) => mounted && setDeptOptions(list || []))
			.finally(() => mounted && setDeptLoading(false));
		return () => {
			mounted = false;
		};
	}, []);

	// Keep shareScope sane when switching scope in the creation dialog
	useEffect(() => {
		if (form.scope === "INST" && form.shareScope === "PRIVATE_DEPT") {
			setForm((f) => ({ ...f, shareScope: "SHARE_INST" }));
		}
	}, [form.scope, form.shareScope]);

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
	}, [page, size, resolvedDefaultSource, dataLevelFilter, scopeFilter, deptFilter, keyword, activeScope, activeDept]);

	// In keep-alive/multi-tab layouts, the list view may not unmount when navigating
	// to the detail page. Refresh the list on window focus/history navigation.
	useEffect(() => {
		const onFocus = () => void fetchList();
		const onPageShow = () => void fetchList();
		const onVisibility = () => { if (!document.hidden) void fetchList(); };
		window.addEventListener("focus", onFocus);
		window.addEventListener("pageshow", onPageShow);
		document.addEventListener("visibilitychange", onVisibility);
		return () => {
			window.removeEventListener("focus", onFocus);
			window.removeEventListener("pageshow", onPageShow);
			document.removeEventListener("visibilitychange", onVisibility);
		};
	// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

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
			if (sourceFilter !== "all" && normalizeSourceType(it.type) !== sourceFilter) return false;
			if (keyword && !it.name.toLowerCase().includes(keyword.toLowerCase())) return false;
			return true;
		});
	}, [items, sourceFilter, keyword, normalizeSourceType]);

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
		// Scope-dependent validation to align with backend checks
		if (form.scope === "DEPT" && !String(form.ownerDept || "").trim()) {
			toast.error("当作用域为 DEPT 时，必须选择所属部门");
			return;
		}
		if (form.scope === "INST" && !form.shareScope) {
			toast.error("当作用域为 INST 时，必须选择共享范围");
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
				// 兼容后端旧字段：按 DATA_* 推导 legacy classification（仅用于兼容，UI 不再展示）
				const legacyClassification = (
					form.dataLevel === "DATA_PUBLIC"
						? "PUBLIC"
						: form.dataLevel === "DATA_INTERNAL"
						? "INTERNAL"
						: form.dataLevel === "DATA_SECRET"
						? "SECRET"
						: "TOP_SECRET"
				) as string;

				const payload = {
					name: form.name.trim(),
					owner: form.owner.trim(),
					classification: legacyClassification,
					dataLevel: form.dataLevel,
					scope: form.scope,
					ownerDept: form.scope === "DEPT" ? (form.ownerDept || undefined) : undefined,
					shareScope: form.scope === "INST" ? form.shareScope : undefined,
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
					// 兼容：尝试以数据密级推导 legacy classification；若无则回退
					const legacyClassification = r.dataLevel
						? (String(r.dataLevel).toUpperCase() === "DATA_PUBLIC"
							? "PUBLIC"
							: String(r.dataLevel).toUpperCase() === "DATA_INTERNAL"
							? "INTERNAL"
							: String(r.dataLevel).toUpperCase() === "DATA_SECRET"
							? "SECRET"
							: "TOP_SECRET")
						: (r.classification || "INTERNAL");
					await createDataset({
						name: r.name,
						owner: r.owner || "",
						classification: legacyClassification,
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
						{/* 统一为 DATA_* 过滤，移除 legacy 密级过滤 */}
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
						{/* 需求：隐藏批量导入与新建入口（仅保留查询/刷新/筛选） */}
					</div>
				</CardHeader>
					<CardContent className="space-y-3">
						<div className="grid gap-2 md:grid-cols-4">
							<div>
								<Label>数据密级</Label>
								<Select value={dataLevelFilter} onValueChange={setDataLevelFilter}>
									<SelectTrigger>
										<SelectValue placeholder="全部" />
									</SelectTrigger>
									<SelectContent>
										<SelectItem value="all">全部</SelectItem>
										{DATA_LEVELS.map((l) => (
											<SelectItem key={l.value} value={l.value}>{l.label}</SelectItem>
										))}
									</SelectContent>
								</Select>
							</div>
							<div>
								<Label>作用域</Label>
								<Select value={scopeFilter} onValueChange={setScopeFilter}>
									<SelectTrigger>
										<SelectValue placeholder="全部" />
									</SelectTrigger>
									<SelectContent>
										<SelectItem value="all">全部</SelectItem>
										<SelectItem value="DEPT">DEPT</SelectItem>
										<SelectItem value="INST">INST</SelectItem>
									</SelectContent>
								</Select>
							</div>
                        <div>
                            <Label>所属部门</Label>
                            <Select
                                value={deptFilter || "all"}
                                onValueChange={(v) => setDeptFilter(v === "all" ? "" : v)}
                            >
									<SelectTrigger>
										<SelectValue placeholder={deptLoading ? "加载中…" : "全部"} />
									</SelectTrigger>
									<SelectContent>
										<SelectItem value="all">全部</SelectItem>
										{deptOptions.map((d) => (
											<SelectItem key={d.code} value={d.code}>
												{d.nameZh || d.nameEn || d.code}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
							</div>
						</div>
					{!hasPrimarySource && (
						<Alert variant="destructive">
							<AlertTitle>缺少默认数据源</AlertTitle>
							<AlertDescription>
								未检测到 {renderSourceLabel(primarySourceLabel)} 连接，请前往「基础管理 - 数据源」完成配置后再浏览或管理数据集。
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
									<th className="px-3 py-2">来源</th>
                        <th className="px-3 py-2">数据密级</th>
									<th className="px-3 py-2">作用域</th>
									<th className="px-3 py-2">所属部门</th>
									<th className="px-3 py-2">操作</th>
								</tr>
							</thead>
							<tbody>
								{filtered.map((d, idx) => (
									<tr key={d.id} className="border-b last:border-b-0">
										<td className="px-3 py-2 text-xs text-muted-foreground">{idx + 1}</td>
										<td className="px-3 py-2 font-medium">{d.name}</td>
										<td className="px-3 py-2">{d.owner || "-"}</td>
										<td className="px-3 py-2 text-xs">{renderSourceLabel(d.type)}</td>
                            <td className="px-3 py-2 text-xs">{renderDataLevelLabel(d.dataLevel)}</td>
                            <td className="px-3 py-2 text-xs">{renderScopeLabel(d.scope)}</td>
                            <td className="px-3 py-2 text-xs">{d.scope === "DEPT" ? renderDept(d.ownerDept) : renderShareScopeLabel(d.shareScope)}</td>
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
                                    <td colSpan={8} className="px-3 py-6 text-center text-xs text-muted-foreground">
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
							{/* 统一显示与提交 DATA_*，不再展示 legacy 密级 */}
							<div className="grid gap-2">
								<Label>数据密级</Label>
								<Select
									value={form.dataLevel}
									onValueChange={(v: DataLevel) => setForm((f) => ({ ...f, dataLevel: v }))}
								>
									<SelectTrigger>
										<SelectValue />
									</SelectTrigger>
									<SelectContent>
										{DATA_LEVELS.map((l) => (
											<SelectItem key={l.value} value={l.value}>
												{l.label}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
							</div>
							<div className="grid gap-2">
								<Label>作用域</Label>
								<Select value={form.scope} onValueChange={(v: Scope) => setForm((f) => ({ ...f, scope: v }))}>
									<SelectTrigger>
										<SelectValue />
									</SelectTrigger>
									<SelectContent>
										<SelectItem value="DEPT">DEPT（部门）</SelectItem>
										<SelectItem value="INST">INST（研究所）</SelectItem>
									</SelectContent>
								</Select>
							</div>
							{form.scope === "DEPT" ? (
								<div className="grid gap-2">
									<Label>所属部门</Label>
									<Select
										value={form.ownerDept || undefined}
										onValueChange={(v) => setForm((f) => ({ ...f, ownerDept: v }))}
									>
										<SelectTrigger>
											<SelectValue placeholder={deptLoading ? "加载中…" : "选择部门…"} />
										</SelectTrigger>
										<SelectContent>
											{nonRootDeptOptions.map((d) => (
												<SelectItem key={d.code} value={d.code}>
													{d.nameZh || d.nameEn || d.code}
												</SelectItem>
											))}
										</SelectContent>
									</Select>
								</div>
							) : (
								<div className="grid gap-2">
									<Label>共享范围（INST）</Label>
									<Select value={form.shareScope} onValueChange={(v: ShareScope) => setForm((f) => ({ ...f, shareScope: v }))}>
										<SelectTrigger>
											<SelectValue />
										</SelectTrigger>
										<SelectContent>
											<SelectItem value="SHARE_INST">SHARE_INST（所内共享）</SelectItem>
											<SelectItem value="PUBLIC_INST">PUBLIC_INST（所内公开）</SelectItem>
										</SelectContent>
									</Select>
								</div>
							)}
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
