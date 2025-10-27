import { useCallback, useEffect, useMemo, useState } from "react";
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
import { createDataset, getCatalogConfig, listDatasets } from "@/api/platformApi";
import { listInfraDataSources, refreshInceptorRegistry } from "@/api/services/infraService";
import deptService, { type DeptDto } from "@/api/services/deptService";
import { useUserInfo } from "@/store/userStore";
import { normalizeClassification } from "@/utils/classification";
import type { SecurityLevel } from "@/types/catalog";

const parseStringList = (value: unknown): string[] => {
	if (!value) return [];
	if (Array.isArray(value)) {
		return value
			.map((entry) => (typeof entry === "string" ? entry.trim() : String(entry ?? "").trim()))
			.filter(Boolean);
	}
	if (typeof value === "string") {
		return value
			.split(/[;,]/)
			.map((token) => token.trim())
			.filter(Boolean);
	}
	return [];
};

type ListItem = {
	id: string;
	name: string;
	owner: string;
	classification?: string;
	ownerDept?: string;
	domainId: string;
	type: string;
	tags?: string[];
    description?: string;
    editable?: boolean;
};

export default function DatasetsPage() {
	const router = useRouter();
	const userInfo = useUserInfo() as any;
	const roles: string[] = useMemo(() => {
		const raw = userInfo?.roles;
		if (!Array.isArray(raw)) return [];
		return raw.map((r: any) => String(r ?? "").toUpperCase()).filter(Boolean);
	}, [userInfo]);
	const roleSet = useMemo(() => new Set(roles), [roles]);
	const hasInstOwnerRole = useMemo(
		() => roleSet.has("INST_DATA_OWNER") || roleSet.has("ROLE_INST_DATA_OWNER"),
		[roleSet],
	);
	const hasDataMaintainerRole = useMemo(() => {
		const maintainers = [
			"ROLE_OP_ADMIN",
			"OPADMIN",
			"ROLE_ADMIN",
			"ADMIN",
			"ROLE_INST_DATA_OWNER",
			"INST_DATA_OWNER",
			"ROLE_INST_DATA_DEV",
			"INST_DATA_DEV",
			"ROLE_DEPT_DATA_OWNER",
			"DEPT_DATA_OWNER",
			"ROLE_DEPT_DATA_DEV",
			"DEPT_DATA_DEV",
		];
		return maintainers.some((role) => roleSet.has(role));
	}, [roleSet]);
	const preferredUsername = useMemo(() => {
		const candidates = [
			userInfo?.preferred_username,
			userInfo?.username,
			userInfo?.fullName,
		];
		for (const c of candidates) {
			if (typeof c === "string" && c.trim()) {
				return c.trim();
			}
		}
		return "";
	}, [userInfo]);
	const isOpadmin = useMemo(() => preferredUsername.toLowerCase() === "opadmin", [preferredUsername]);
	const userDeptCode = useMemo(() => {
		if (!userInfo) return undefined;
		const attrs = ((userInfo as any)?.attributes || {}) as Record<string, unknown>;
		const candidateKeys = ["dept_code", "deptCode", "department", "org_code", "orgCode", "organization"];
		for (const key of candidateKeys) {
			const tokens = parseStringList(attrs[key]);
			const first = tokens.find((token) => token && token.trim().length > 0);
			if (first) return first.trim();
		}
		const inlineCandidates = [
			(userInfo as any)?.deptCode,
			(userInfo as any)?.department,
			(userInfo as any)?.ownerDept,
		];
		for (const value of inlineCandidates) {
			if (typeof value === "string" && value.trim()) {
				return value.trim();
			}
			const tokens = parseStringList(value);
			const first = tokens.find((token) => token && token.trim().length > 0);
			if (first) return first.trim();
		}
		return undefined;
	}, [userInfo]);
	const normalizedUserDept = useMemo(() => {
		const trimmed = (userDeptCode || "").trim();
		return trimmed.length > 0 ? trimmed : undefined;
	}, [userDeptCode]);
	const [items, setItems] = useState<ListItem[]>([]);
	const [total, setTotal] = useState(0);
	const [loading, setLoading] = useState(false);
	const [page, setPage] = useState(0);
	const [size] = useState(10);
	const [keyword, setKeyword] = useState("");
const [deptFilter, setDeptFilter] = useState<string>("");
	const [instOwnerInitialized, setInstOwnerInitialized] = useState(false);
	const [open, setOpen] = useState(false);
	const [form, setForm] = useState({
		name: "",
		owner: "",
		classification: "INTERNAL" as SecurityLevel,
		ownerDept: "",
		tags: "",
		description: "",
		sourceType: "INCEPTOR",
		hiveDatabase: "",
		hiveTable: "",
	});
	const [deptOptions, setDeptOptions] = useState<DeptDto[]>([]);
const sortedDeptOptions = useMemo(
	() =>
		[...deptOptions].sort(
			(a, b) =>
				((a.parentId ?? 0) - (b.parentId ?? 0)) || String(a.code).localeCompare(String(b.code)),
		),
	[deptOptions],
);
const deptSelectOptions = useMemo(() => {
	const root = sortedDeptOptions.find((d) => d.isRoot);
	const others = sortedDeptOptions.filter((d) => !d.isRoot && d.parentId != null && d.parentId !== 0);
	return root ? [root, ...others] : others;
}, [sortedDeptOptions]);
const deptOptionsForDialog = useMemo(() => {
	if (isOpadmin) {
		return deptSelectOptions;
	}
	if (!normalizedUserDept) {
		return deptSelectOptions;
	}
	const exists = deptSelectOptions.some((d) => String(d.code) === normalizedUserDept);
	if (exists) {
		return deptSelectOptions;
	}
	return [
		...deptSelectOptions,
		{
			code: normalizedUserDept,
			nameZh: normalizedUserDept,
			nameEn: normalizedUserDept,
			parentId: null,
			isRoot: false,
		} as DeptDto,
	];
}, [deptSelectOptions, isOpadmin, normalizedUserDept]);
const [deptLoading, setDeptLoading] = useState(false);
	const [catalogConfig, setCatalogConfig] = useState({
		multiSourceEnabled: false,
		defaultSourceType: "INCEPTOR",
		hasPrimarySource: true,
		primarySourceType: "INCEPTOR",
	});
	const [dataSources, setDataSources] = useState<any[]>([]);
	const [multiSourceUnlocked, setMultiSourceUnlocked] = useState(false);
	    const [refreshing, setRefreshing] = useState(false);
    // Preview dialog state
const sourceTypeUpper = (form.sourceType || "").toUpperCase();
const isInceptorSource = sourceTypeUpper === "INCEPTOR" || sourceTypeUpper === "HIVE";
const isPostgresSource = sourceTypeUpper === "POSTGRES";
const databaseLabel = isPostgresSource ? "Schema" : "Hive Database";
const tableLabel = isPostgresSource ? "Table" : "Hive Table";
const ownerDeptSelectValue = useMemo(() => {
	const current = form.ownerDept && form.ownerDept.trim().length > 0 ? form.ownerDept.trim() : undefined;
	if (!isOpadmin) {
		if (normalizedUserDept) {
			return normalizedUserDept;
		}
		return current ?? "__PUBLIC__";
	}
	return current ?? "__PUBLIC__";
}, [form.ownerDept, isOpadmin, normalizedUserDept]);
// Note: import-from-file feature removed in this build; re-enable when UI wires the input

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
const fallbackEditable = useMemo(() => isOpadmin || hasDataMaintainerRole, [isOpadmin, hasDataMaintainerRole]);
const canSyncInfra = true; // 放开刷新，前端不再限制角色

const availableSourceTypes = useMemo(() => {
	const set = new Set<string>();
	dataSources.forEach((ds) => {
		const typ = normalizeSourceType(String(ds?.type || ""));
		if (typ) set.add(typ);
	});
	items.forEach((it) => {
		const typ = normalizeSourceType(String(it.type || ""));
		if (typ) set.add(typ);
	});
	set.add(resolvedDefaultSource);
	return Array.from(set);
}, [dataSources, items, normalizeSourceType, resolvedDefaultSource]);

	useEffect(() => {
	if (!hasInstOwnerRole) {
			if (instOwnerInitialized) {
				setInstOwnerInitialized(false);
			}
			return;
		}
		if (!instOwnerInitialized) {
			if (deptFilter !== "") {
				setDeptFilter("");
			}
			if (page !== 0) {
				setPage(0);
			}
			setInstOwnerInitialized(true);
		}
	}, [hasInstOwnerRole, instOwnerInitialized, deptFilter, page, setDeptFilter, setInstOwnerInitialized, setPage]);
const hasPrimarySource = useMemo(() => {
	if (multiSourceAllowed) {
		return availableSourceTypes.length > 0;
	}
	if (catalogConfig.hasPrimarySource) return true;
	return dataSources.some((ds) => normalizeSourceType(String(ds?.type || "")) === resolvedDefaultSource);
}, [availableSourceTypes, catalogConfig.hasPrimarySource, dataSources, multiSourceAllowed, normalizeSourceType, resolvedDefaultSource]);
const deptLabelMap = useMemo(() => {
  const map = new Map<string, string>();
  for (const d of deptOptions) {
    const baseLabel = d.nameZh || d.nameEn || d.code;
    const label = d.isRoot ? `${baseLabel}（ROOT）` : baseLabel;
    map.set(d.code, label);
  }
  map.set("__PUBLIC__", "未指定（全局可见）");
  return map;
}, [deptOptions]);

const renderDept = (code?: string) => {
  if (!code || !code.trim()) return "未指定（全局可见）";
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
		case "POSTGRES":
		case "POSTGRESQL":
			return "平台 PostgreSQL";
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
		const params: any = { page, size, keyword };
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
				const classification = normalizeClassification(it.classification ?? it.dataLevel) ?? "INTERNAL";
				return {
					id: String(it.id),
					name: it.name,
					owner: it.owner || "",
					classification,
					ownerDept: it.ownerDept || undefined,
					domainId: String(it.domainId || ""),
					type: normalizeSourceType(rawType || resolvedDefaultSource),
					tags,
					description: typeof it.description === "string" ? it.description : "",
					editable: typeof it.editable === "boolean" ? it.editable : fallbackEditable,
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
}, [page, size, resolvedDefaultSource, deptFilter, keyword]);

	useEffect(() => {
		if (!open) return;
		if (isOpadmin) return;
		const enforced = normalizedUserDept ?? "";
		setForm((prev) => {
			const previous = prev.ownerDept?.trim() ?? "";
			if (previous === enforced) {
				return prev;
			}
			return {
				...prev,
				ownerDept: enforced,
			};
		});
	}, [open, isOpadmin, normalizedUserDept]);

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
		if (keyword && !it.name.toLowerCase().includes(keyword.toLowerCase())) return false;
		return true;
	});
}, [items, keyword]);

	const totalPages = useMemo(() => Math.max(1, Math.ceil(total / size)), [total, size]);

	const handleRefresh = async () => {
		setRefreshing(true);
		let registrySynced = false;
		if (canSyncInfra) {
				try {
					await refreshInceptorRegistry();
					registrySynced = true;
				} catch (error: any) {
					console.error(error);
					if (error?.response?.status !== 403) {
						toast.error(error?.message || "同步失败");
						setRefreshing(false);
						return;
					}
				}
		}
		try {
			await fetchList();
			if (registrySynced) {
				try {
					const [cfg, ds] = await Promise.all([
						getCatalogConfig().catch(() => undefined),
						listInfraDataSources().catch(() => undefined),
					]);
					if (cfg) {
						setCatalogConfig({
							multiSourceEnabled: Boolean((cfg as any)?.multiSourceEnabled),
							defaultSourceType: String((cfg as any)?.defaultSourceType || "HIVE"),
							hasPrimarySource: Boolean((cfg as any)?.hasPrimarySource),
							primarySourceType: String((cfg as any)?.primarySourceType || "HIVE"),
						});
					}
					if (Array.isArray(ds)) {
						setDataSources(ds);
					}
				} catch (reloadError) {
					console.warn("刷新数据目录基础信息失败", reloadError);
				}
				toast.success("数据目录已重新同步最新数据源状态");
			} else {
				toast.success("数据资产列表已刷新");
			}
		} catch (error: any) {
			console.error(error);
			toast.error(error?.message || "刷新失败");
		} finally {
			setRefreshing(false);
		}
	};

	const onCreate = async () => {
		if (!hasPrimarySource) {
			toast.error("请先在基础管理中完善默认数据源连接");
			return;
		}
	if (!form.name.trim()) {
		toast.error("请填写数据集名称");
		return;
	}
	const ownerDeptValue = form.ownerDept ? form.ownerDept.trim() : "";
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
		const shouldPersistLocation = selectedSourceType === "INCEPTOR" || selectedSourceType === "POSTGRES";
		const locationDatabase = shouldPersistLocation && hiveDatabase ? hiveDatabase : undefined;
		const locationTable = shouldPersistLocation && hiveTable ? hiveTable : undefined;
		const classification = normalizeClassification(form.classification) ?? "INTERNAL";

		const payload = {
			name: form.name.trim(),
			owner: form.owner.trim(),
			classification,
			ownerDept: ownerDeptValue.length > 0 ? ownerDeptValue : undefined,
			tags: tagsList,
			description: form.description.trim(),
			type: selectedSourceType,
			hiveDatabase: locationDatabase,
			hiveTable: locationTable,
			source: {
				sourceType: selectedSourceType,
				hiveDatabase: locationDatabase,
				hiveTable: locationTable,
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



	/* const onImport = async (file: File) => {
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
				const shouldPersistLocation = sourceType === "INCEPTOR" || sourceType === "POSTGRES";
				const locationDatabase = shouldPersistLocation && r.hiveDatabase ? r.hiveDatabase : undefined;
				const locationTable = shouldPersistLocation && r.hiveTable ? r.hiveTable : undefined;
				const legacyClassification = normalizeClassification(r.classification) ?? "INTERNAL";
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
                    hiveDatabase: locationDatabase,
                    hiveTable: locationTable,
                    source: { sourceType, hiveDatabase: locationDatabase, hiveTable: locationTable },
						exposure: ["VIEW"],
					});
				ok += 1;
			} catch {}
		}
		toast.success(`导入完成：${ok}/${rows.length}`);
		await fetchList();
	}; */

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-wrap items-center justify-between gap-2">
					<CardTitle className="flex items-center gap-2 text-base">
						数据资产列表
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
					<Button variant="outline" onClick={handleRefresh} disabled={loading || refreshing}>
							{refreshing ? "同步中…" : "刷新"}
						</Button>
						{/* 需求：隐藏批量导入与新建入口（仅保留查询/刷新/筛选） */}
					</div>
				</CardHeader>
				<CardContent className="space-y-3">
					<div className="grid gap-2 md:grid-cols-4">
						<div>
			<Label>所属部门</Label>
			<Select
				value={deptFilter || "all"}
				onValueChange={(v) => {
					const next = v === "all" ? "" : v;
					setDeptFilter(next);
					setPage(0);
				}}
			>
				<SelectTrigger>
					<SelectValue placeholder={deptLoading ? "加载中…" : "全部"} />
				</SelectTrigger>
				<SelectContent>
					<SelectItem value="all">全部</SelectItem>
					{sortedDeptOptions.map((d) => (
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
						<table className="w-full min-w-[820px] table-fixed text-sm">
							<thead className="bg-muted/50">
								<tr className="text-left">
									<th className="px-3 py-2 w-[32px]">#</th>
									<th className="px-3 py-2">数据集名称</th>
									<th className="px-3 py-2">所属部门</th>
									<th className="px-3 py-2">描述</th>
									<th className="px-3 py-2">操作</th>
								</tr>
							</thead>
							<tbody>
								{filtered.map((d, idx) => (
									<tr key={d.id} className="border-b last:border-b-0">
										<td className="px-3 py-2 text-xs text-muted-foreground">{idx + 1}</td>
										<td className="px-3 py-2 font-medium">{d.name}</td>
										<td className="px-3 py-2 text-xs">{renderDept(d.ownerDept)}</td>
										<td className="px-3 py-2 text-xs truncate" title={d.description || "-"}>{d.description || "-"}</td>
										<td className="px-3 py-2">
											<div className="flex flex-wrap items-center gap-2">
												{d.editable ? (
													<Button variant="outline" size="sm" onClick={() => router.push(`/catalog/datasets/${d.id}`)}>
														编辑
													</Button>
												) : (
													<span className="text-xs text-muted-foreground">-</span>
												)}
											</div>
										</td>
									</tr>
								))}
								{!filtered.length && (
									<tr>
										<td colSpan={5} className="px-3 py-6 text-center text-xs text-muted-foreground">
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
			<Label>所属部门</Label>
			<p className="text-xs text-muted-foreground">
				未指定或选择 ROOT 节点时，数据集将对全部部门开放。
				{!isOpadmin && "（当前账号仅可查看所属部门）"}
			</p>
			<Select
				value={ownerDeptSelectValue}
				disabled={!isOpadmin}
				onValueChange={(v) =>
					setForm((f) => ({
						...f,
						ownerDept: v === "__PUBLIC__" ? "" : v,
					}))
				}
			>
				<SelectTrigger>
					<SelectValue
						placeholder={
							deptLoading ? "加载中…" : !isOpadmin ? "仅运维管理员可调整所属部门" : "选择部门…"
						}
					/>
				</SelectTrigger>
				<SelectContent>
					<SelectItem value="__PUBLIC__">未指定（全局可见）</SelectItem>
					{deptOptionsForDialog.map((d) => {
						const optionLabel = d.isRoot
							? `${d.nameZh || d.nameEn || d.code}（ROOT）`
							: d.nameZh || d.nameEn || d.code;
						return (
							<SelectItem key={d.code} value={d.code}>
								{optionLabel}
							</SelectItem>
						);
					})}
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
										availableSourceTypes
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
                        {isInceptorSource && (
                            <div className="grid grid-cols-2 gap-3">
                                <div className="grid gap-2">
                                    <Label>{databaseLabel}</Label>
                                    <Input
                                        value={form.hiveDatabase}
                                        onChange={(e) => setForm((f) => ({ ...f, hiveDatabase: e.target.value }))}
                                    />
                                </div>
                                <div className="grid gap-2">
                                    <Label>{tableLabel}</Label>
                                    <Input
                                        value={form.hiveTable}
                                        onChange={(e) => setForm((f) => ({ ...f, hiveTable: e.target.value }))}
                                    />
                                </div>
                            </div>
                        )}
                        {isPostgresSource && (
                            <div className="grid grid-cols-2 gap-3">
                                <div className="grid gap-2">
                                    <Label>{databaseLabel}</Label>
                                    <Input
                                        placeholder="OLAP"
                                        value={form.hiveDatabase}
                                        onChange={(e) => setForm((f) => ({ ...f, hiveDatabase: e.target.value }))}
                                    />
                                </div>
                                <div className="grid gap-2">
                                    <Label>{tableLabel}</Label>
                                    <Input
                                        placeholder="sample_orders"
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
