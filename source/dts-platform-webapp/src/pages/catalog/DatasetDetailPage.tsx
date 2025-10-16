import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "react-router";
import { Button } from "@/ui/button";
import { Badge } from "@/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Popover, PopoverContent, PopoverTrigger } from "@/ui/popover";
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/ui/command";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/ui/tabs";
import { toast } from "sonner";
import {
	getDataset,
	updateDataset,
	previewDataset,
	listDatasetGrants,
	createDatasetGrant,
	deleteDatasetGrant,
} from "@/api/platformApi";
import type { DatasetAsset, DataLevel, DatasetGrant, TableSchema } from "@/types/catalog";
import deptService, { type DeptDto } from "@/api/services/deptService";
import userDirectoryService, { type UserDirectoryEntry } from "@/api/services/userDirectoryService";
import { useRouter } from "@/routes/hooks";
import { normalizeClassification } from "@/utils/classification";
import { renderDataLevelLabel } from "@/constants/governance";
import { useUserInfo } from "@/store/userStore";
import { cn } from "@/utils";
import { Check, ChevronsUpDown } from "lucide-react";
import { normalizeColumnKey } from "@/utils/columnName";

const parseStringList = (value: unknown): string[] => {
	if (Array.isArray(value)) {
		return value
			.map((item) => String(item ?? "").trim())
			.filter((token) => token.length > 0);
	}
	if (typeof value === "string") {
		return value
			.split(/[,;，；\s]+/)
			.map((token) => token.trim())
			.filter((token) => token.length > 0);
	}
	return [];
};

const pickFirstString = (...values: unknown[]): string | undefined => {
	for (const value of values) {
		if (typeof value === "string") {
			const trimmed = value.trim();
			if (trimmed.length > 0) {
				return trimmed;
			}
		}
	}
	return undefined;
};

const resolveColumnDisplayName = (column: any): string | undefined => {
	const direct = pickFirstString(
		column?.displayName,
		column?.alias,
		column?.label,
		column?.bizName,
		column?.bizLabel,
		column?.cnName,
		column?.zhName,
		column?.nameZh,
		column?.nameCn,
		column?.chineseName,
		column?.description,
		column?.comment,
	);
	if (direct) {
		return direct;
	}
	if (column?.metadata && typeof column.metadata === "object" && column.metadata !== null) {
		return pickFirstString(
			(column.metadata as any).displayName,
			(column.metadata as any).alias,
			(column.metadata as any).label,
			(column.metadata as any).cnName,
			(column.metadata as any).zhName,
			(column.metadata as any).nameZh,
			(column.metadata as any).nameCn,
			(column.metadata as any).description,
			(column.metadata as any).comment,
		);
	}
	return undefined;
};

const EMPTY_DEPT_VALUE = "__EMPTY__";

export default function DatasetDetailPage() {
	const params = useParams();
	const id = String(params.id || "");
	const [loading, setLoading] = useState(true);
	const [saving, setSaving] = useState(false);
	const [dataset, setDataset] = useState<DatasetAsset | null>(null);
	const userInfo = useUserInfo() as any;
	const userRoles = useMemo(() => {
		if (!userInfo || !Array.isArray(userInfo.roles)) return [] as string[];
		return userInfo.roles as string[];
	}, [userInfo]);
	const normalizedRoleSet = useMemo(() => {
		const set = new Set<string>();
		for (const role of userRoles) {
			if (role) set.add(String(role || "").toUpperCase());
		}
		return set;
	}, [userRoles]);
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
		return maintainers.some((role) => normalizedRoleSet.has(role));
	}, [normalizedRoleSet]);
	const currentUsername = useMemo(() => {
		const candidates = [
			(userInfo as any)?.preferred_username,
			(userInfo as any)?.username,
			(userInfo as any)?.fullName,
		];
		for (const c of candidates) {
			if (typeof c === "string" && c.trim()) return c.trim();
		}
		return "";
	}, [userInfo]);
	const isOpadmin = useMemo(() => currentUsername.toLowerCase() === "opadmin", [currentUsername]);
	const canManageGrants = useMemo(() => isOpadmin || hasDataMaintainerRole, [hasDataMaintainerRole, isOpadmin]);
	const [grants, setGrants] = useState<DatasetGrant[]>([]);
	const [grantLoading, setGrantLoading] = useState(false);
	const [grantDialogOpen, setGrantDialogOpen] = useState(false);
	const [grantSaving, setGrantSaving] = useState(false);
	const [grantForm, setGrantForm] = useState({
		username: "",
		displayName: "",
		deptCode: "",
	});

	const editable = useMemo(() => {
		if (dataset && "editable" in dataset) {
			return Boolean((dataset as any).editable);
		}
		return isOpadmin || hasDataMaintainerRole;
	}, [dataset, hasDataMaintainerRole, isOpadmin]);
	const [activeTab, setActiveTab] = useState("overview");
	const [sampleData, setSampleData] = useState<{ headers: string[]; rows: any[] } | null>(null);
	const [sampleLoading, setSampleLoading] = useState(false);
	const [sampleInitialized, setSampleInitialized] = useState(false);
	const [deptOptions, setDeptOptions] = useState<DeptDto[]>([]);
	const [deptLoading, setDeptLoading] = useState(false);
	const [userOptions, setUserOptions] = useState<UserDirectoryEntry[]>([]);
	const [userLoading, setUserLoading] = useState(false);
	const [userSearch, setUserSearch] = useState("");
	const [userPickerOpen, setUserPickerOpen] = useState(false);
    const router = useRouter();
	const rootDept = useMemo(
		() => deptOptions.find((d) => d.parentId == null || d.parentId === 0),
		[deptOptions],
	);
	const sortedDeptOptions = useMemo(
		() =>
			[...deptOptions].sort(
				(a, b) =>
					((a.parentId ?? 0) - (b.parentId ?? 0)) || String(a.code).localeCompare(String(b.code)),
			),
		[deptOptions],
	);
	const nonRootDeptOptions = useMemo(
		() => deptOptions.filter((d) => d.parentId != null && d.parentId !== 0),
		[deptOptions],
	);
	const resetGrantForm = useCallback(() => {
		setGrantForm({
			username: "",
			displayName: "",
			deptCode: "",
		});
		setUserSearch("");
	}, []);
	const loadGrants = useCallback(async () => {
		if (!id || !editable) {
			setGrants([]);
			return;
		}
		setGrantLoading(true);
		try {
			const resp = (await listDatasetGrants(id)) as any;
			setGrants(Array.isArray(resp) ? resp : []);
		} catch (error) {
			console.error(error);
			toast.error("加载授权用户失败");
		} finally {
			setGrantLoading(false);
		}
	}, [id, editable]);

	const loadUsers = useCallback(
		async (keyword: string) => {
			const query = keyword.trim();
			setUserLoading(true);
			try {
				const list = await userDirectoryService.searchUsers(query);
				setUserOptions(list);
			} catch (error) {
				console.error(error);
				if (!query) {
					toast.error("加载用户列表失败");
				}
				setUserOptions([]);
			} finally {
				setUserLoading(false);
			}
		},
		[],
	);

	const handleUserSelect = useCallback(
		(option: UserDirectoryEntry) => {
			setGrantForm((prev) => {
				const normalizedDept = option.deptCode && option.deptCode.trim();
				const existsInTree =
					normalizedDept &&
					nonRootDeptOptions.some((dept) => String(dept.code) === normalizedDept);
				const preferredName = option.fullName?.trim() || option.displayName?.trim() || option.username;
				return {
					username: option.username,
					displayName: preferredName,
					deptCode: existsInTree ? normalizedDept : prev.deptCode || "",
				};
			});
			setUserSearch("");
			setUserPickerOpen(false);
		},
		[nonRootDeptOptions],
	);
	const fetchSample = useCallback(
		async (rows = 10, options?: { silent?: boolean }) => {
			setSampleLoading(true);
			const silent = Boolean(options?.silent);
			try {
				const resp = (await previewDataset(id, rows)) as any;
				const headers: string[] = Array.isArray(resp?.headers) ? resp.headers : [];
				const rowsData: any[] = Array.isArray(resp?.rows) ? resp.rows : [];
				setSampleData({ headers, rows: rowsData });
				if (!silent && headers.length === 0) {
					toast.info("采样结果为空");
				}
			} catch (error) {
				console.error(error);
				if (!silent) {
					toast.error("采样失败");
				}
			} finally {
				setSampleLoading(false);
			}
			},
			[id],
		);
	const formatDateTime = useCallback((value?: string) => {
		if (!value) return "-";
		try {
			return new Date(value).toLocaleString();
		} catch {
			return value;
		}
	}, []);
	const onGrantSubmit = useCallback(async () => {
		if (!editable) {
			toast.error("当前用户无权分配访问权限");
			return;
		}
		if (!canManageGrants) {
			toast.error("当前用户无权分配访问权限");
			return;
		}
		const username = (grantForm.username || "").trim();
		if (!username) {
			toast.error("请选择用户");
			return;
		}
		const payload: any = {
			username,
			displayName: (grantForm.displayName || "").trim() || undefined,
			deptCode: (grantForm.deptCode || "").trim() || undefined,
		};
		setGrantSaving(true);
		try {
			await createDatasetGrant(id, payload);
			toast.success("已添加访问授权");
			setGrantDialogOpen(false);
			resetGrantForm();
			await loadGrants();
		} catch (error) {
			console.error(error);
			toast.error("添加授权失败");
		} finally {
			setGrantSaving(false);
		}
	}, [editable, canManageGrants, grantForm, id, loadGrants, resetGrantForm]);
	const handleRemoveGrant = useCallback(
		async (grant: DatasetGrant) => {
			if (!canManageGrants) {
				toast.error("当前用户无权移除访问权限");
				return;
			}
			if (!grant?.id) {
				return;
			}
			if (!window.confirm(`确定要移除用户 ${grant.username} 的访问权限吗？`)) {
				return;
			}
			try {
				await deleteDatasetGrant(id, String(grant.id));
				toast.success("已移除访问授权");
				await loadGrants();
			} catch (error) {
				console.error(error);
				toast.error("移除授权失败");
			}
		},
		[canManageGrants, id, loadGrants],
	);

    // Normalize legacy classification -> DATA_* for UI binding
    const fromLegacy = (c?: string): DataLevel => {
        const normalized = normalizeClassification(c);
        if (normalized === "PUBLIC") return "DATA_PUBLIC" as DataLevel;
        if (normalized === "INTERNAL") return "DATA_INTERNAL" as DataLevel;
        if (normalized === "SECRET") return "DATA_SECRET" as DataLevel;
        if (normalized === "TOP_SECRET") return "DATA_TOP_SECRET" as DataLevel;
        // default minimal
        return "DATA_INTERNAL" as DataLevel;
    };

    const loadDataset = async (withSpinner = false) => {
        if (withSpinner) setLoading(true);
        try {
            const data = (await getDataset(id)) as any;
            // Normalize server payload for UI editing
            const normalized: any = {
                ...data,
                // Ensure tags is an array for the input join/split logic below
                tags: Array.isArray((data as any)?.tags)
                    ? (data as any).tags
                    : (typeof (data as any)?.tags === "string" && (data as any).tags.trim().length
                        ? String((data as any).tags)
                              .split(",")
                              .map((s) => s.trim())
                              .filter(Boolean)
                        : []),
            };
            // Backfill initial values for edit form to avoid empty saves
            // 1) dataLevel: prefer DATA_* if provided; otherwise map from legacy classification
            if (!normalized.dataLevel) {
                normalized.dataLevel = fromLegacy((data as any)?.classification);
            }
            if (Array.isArray((data as any)?.tables)) {
                normalized.tables = ((data as any).tables as any[])
                    .map((table) => {
                        const rawName = String(table?.name ?? table?.tableName ?? "").trim();
                        const columns = Array.isArray(table?.columns)
                            ? (table.columns as any[])
                                  .map((col) => {
                                      const columnName = String(col?.name ?? col?.columnName ?? "").trim();
                                      if (!columnName) {
                                          return null;
                                      }
                                      const localizedName = resolveColumnDisplayName(col);
                                      return {
                                          id: String(col?.id ?? columnName),
                                          name: columnName,
                                          displayName: localizedName,
                                          dataType: String(col?.dataType ?? "").toUpperCase(),
                                          nullable: col?.nullable !== false,
                                          tags: parseStringList(col?.tags),
                                          sensitiveTags: parseStringList(col?.sensitiveTags),
                                          description: pickFirstString(col?.description, col?.comment),
                                      };
                                  })
                                  .filter(Boolean)
                            : [];
                        return {
                            id: table?.id ? String(table.id) : undefined,
                            name: rawName,
                            tableName: table?.tableName ? String(table.tableName) : rawName,
                            columns,
                        };
                    })
                    .filter((table: any) => table.name);
            }
            // 3) primitive text fields: coerce to strings to keep controlled inputs stable
            normalized.name = String((data as any)?.name || "");
            normalized.owner = String((data as any)?.owner || "");
            normalized.description = String((data as any)?.description || "");
            setDataset(normalized);
        } catch (e) {
            console.error(e);
            toast.error("加载失败");
        } finally {
            if (withSpinner) setLoading(false);
        }
    };

	useEffect(() => {
		void loadDataset(true);
	}, [id]);

	useEffect(() => {
		setActiveTab("overview");
		setSampleData(null);
		setSampleInitialized(false);
	}, [id]);

	useEffect(() => {
		if (!dataset || sampleInitialized) {
			return;
		}
		setSampleInitialized(true);
		void fetchSample(5, { silent: true });
	}, [dataset, sampleInitialized, fetchSample]);

	useEffect(() => {
		if (!editable) {
			setGrants([]);
			return;
		}
		void loadGrants();
	}, [editable, loadGrants]);

    // removed sync-schema feature: runtime source precheck no longer needed

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

	// If ownerDept is empty, default to the root department (fallback to active dept or first entry).
	useEffect(() => {
		if (!dataset) return;
		const current = String(dataset.ownerDept || "").trim();
		if (current) return;
	const fallback = rootDept?.code ?? deptOptions[0]?.code;
		if (!fallback) return;
		setDataset((prev) => (prev ? { ...prev, ownerDept: fallback } : prev));
	}, [dataset, rootDept, deptOptions]);

	useEffect(() => {
		if (!grantDialogOpen) {
			return;
		}
		setUserSearch("");
		void loadUsers("");
	}, [grantDialogOpen, loadUsers]);

	useEffect(() => {
		if (!grantDialogOpen) {
			return;
		}
		const handle = window.setTimeout(() => {
			void loadUsers(userSearch);
		}, 300);
		return () => window.clearTimeout(handle);
	}, [grantDialogOpen, userSearch, loadUsers]);


    const toLegacyClassification = (dl?: string): string => {
        const v = String(dl || "").toUpperCase();
        if (v === "DATA_PUBLIC") return "PUBLIC";
        if (v === "DATA_INTERNAL") return "INTERNAL";
        if (v === "DATA_SECRET") return "SECRET";
        if (v === "DATA_TOP_SECRET") return "TOP_SECRET";
        return "INTERNAL";
    };

    const onSave = async () => {
	if (!dataset) return;
	if (!editable) {
		toast.error("当前用户无权保存该数据集");
		return;
	}
	if (!String(dataset.ownerDept || "").trim()) {
		toast.error("请选择所属部门");
		return;
	}
	// Sanitize payload for backend
	const payload: any = {
		...dataset,
		classification: toLegacyClassification(dataset.dataLevel as string),
		dataLevel: dataset.dataLevel,
		ownerDept: dataset.ownerDept || undefined,
		// Backend expects a string; submit as comma-separated list
		tags: Array.isArray((dataset as any).tags)
			? ((dataset as any).tags as string[]).join(",")
			: String((dataset as any).tags || ""),
	};
	delete payload.scope;
	delete payload.shareScope;
	setSaving(true);
	try {
		await updateDataset(dataset.id, payload);
		toast.success("已保存");
		try {
			router.push("/catalog/assets");
		} catch {
			// ignore navigation errors
		}
	} catch (e) {
		console.error(e);
		toast.error("保存失败");
	} finally {
		setSaving(false);
	}
    };

    // Legacy classification UI removed; only DATA_* is used going forward

    const hasHive = useMemo(() => {
        const t = String((dataset as any)?.type || "").trim().toUpperCase();
        if (t === "INCEPTOR" || t === "HIVE") return true;
        const hasLegacyHive = Boolean((dataset as any)?.hiveTable) || Boolean((dataset as any)?.hiveDatabase);
        return hasLegacyHive;
    }, [dataset?.type, (dataset as any)?.hiveTable, (dataset as any)?.hiveDatabase]);
	const tables = useMemo<TableSchema[]>(() => {
		if (!dataset) return [];
		const raw = (dataset as any).tables;
		return Array.isArray(raw) ? (raw as TableSchema[]) : [];
	}, [dataset]);

	const columnLabelMap = useMemo(() => {
		const map = new Map<string, string>();
		for (const table of tables) {
			const columnList = Array.isArray(table?.columns) ? table.columns : [];
			for (const column of columnList) {
				const columnName = String(column?.name ?? "").trim();
				if (!columnName) continue;
				const key = normalizeColumnKey(columnName);
				if (!key || map.has(key)) continue;
				const label = column.displayName || column.description;
				if (label) {
					map.set(key, label);
				}
			}
		}
		return map;
	}, [tables]);

	if (loading) return <div className="text-sm text-muted-foreground">加载中…</div>;
	if (!dataset) return <div className="text-sm text-muted-foreground">未找到该数据集</div>;

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex items-center justify-between">
					<CardTitle className="text-base">数据集详情</CardTitle>
					<div className="flex items-center gap-2">
						<Button
							variant="outline"
							onClick={() => {
								setActiveTab("sample");
								void fetchSample(10);
							}}
							disabled={sampleLoading}
						>
							{sampleLoading ? "采样中…" : "刷新采样"}
						</Button>
						<Button
							variant="outline"
							disabled={saving}
							onClick={() => {
								try {
									router.push("/catalog/assets");
								} catch {
									// ignore navigation errors
								}
							}}
						>
							取消
						</Button>
						<Button onClick={onSave} disabled={saving || !editable}>
							{saving ? "保存中…" : "保存"}
						</Button>
					</div>
				</CardHeader>
				<CardContent>
					<Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-4">
						<TabsList className="w-full justify-start">
							<TabsTrigger value="overview">概览</TabsTrigger>
							<TabsTrigger value="columns">列信息</TabsTrigger>
							<TabsTrigger value="sample">数据采样</TabsTrigger>
						</TabsList>
						<TabsContent value="overview">
							<div className="grid gap-4 md:grid-cols-2">
								<div className="grid gap-2">
									<Label>名称</Label>
									<Input
										value={dataset.name || ""}
										disabled={!editable}
										onChange={(e) => setDataset({ ...(dataset as DatasetAsset), name: e.target.value })}
									/>
								</div>
								<div className="grid gap-2">
									<Label>负责人</Label>
									<Input
										value={dataset.owner || ""}
										disabled={!editable}
										onChange={(e) => setDataset({ ...(dataset as DatasetAsset), owner: e.target.value })}
									/>
								</div>
								<div className="grid gap-2">
									<Label>数据密级</Label>
									<div className="flex items-center gap-2 rounded-md border border-dashed border-muted-foreground/30 bg-muted/20 px-3 py-2">
										<Badge variant="outline">
											{renderDataLevelLabel((dataset.dataLevel as DataLevel) || "DATA_INTERNAL")}
										</Badge>
										<span className="text-xs text-muted-foreground">密级由系统自动判定</span>
									</div>
								</div>
								<div className="grid gap-2">
									<Label>所属部门 *</Label>
									<Select
										value={dataset.ownerDept || undefined}
										disabled={!editable}
										onValueChange={(v) =>
											setDataset({
												...(dataset as DatasetAsset),
												ownerDept: v,
											})
										}
									>
										<SelectTrigger>
											<SelectValue placeholder={deptLoading ? "加载中…" : "选择部门…"} />
										</SelectTrigger>
										<SelectContent>
											{sortedDeptOptions.map((d) => (
												<SelectItem key={d.code} value={d.code}>
													{d.nameZh || d.nameEn || d.code}
												</SelectItem>
											))}
										</SelectContent>
									</Select>
								</div>
								<div className="grid gap-2">
									<Label>标签（逗号分隔）</Label>
									<Input
										value={(dataset.tags || []).join(",")}
										disabled={!editable}
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
										disabled={!editable}
										onChange={(e) => setDataset({ ...(dataset as DatasetAsset), description: e.target.value })}
									/>
								</div>
								<div className="grid gap-2">
									<Label>来源类型</Label>
									<Input disabled value={(dataset as any)?.type || "INCEPTOR"} />
								</div>
								{hasHive && (
									<>
										<div className="grid gap-2">
											<Label>Hive Database</Label>
											<Input
												value={(dataset as any)?.hiveDatabase || ""}
												disabled={!editable}
												onChange={(e) =>
													setDataset({
														...(dataset as any),
														hiveDatabase: e.target.value,
													} as any)
												}
											/>
										</div>
										<div className="grid gap-2">
											<Label>Hive Table</Label>
											<Input
												value={(dataset as any)?.hiveTable || ""}
												disabled={!editable}
												onChange={(e) =>
													setDataset({
														...(dataset as any),
														hiveTable: e.target.value,
													} as any)
												}
											/>
										</div>
									</>
								)}
							</div>
						</TabsContent>
						<TabsContent value="columns">
							<div className="space-y-4">
								{tables.length ? (
									tables.map((table) => (
										<div key={table.id || table.name} className="space-y-2">
											<div className="flex items-center justify-between">
												<span className="text-sm font-medium">{table.name || table.tableName || "-"}</span>
												<span className="text-xs text-muted-foreground">
													列数 {table.columns?.length ?? 0}
												</span>
											</div>
											{table.columns && table.columns.length ? (
												<div className="overflow-x-auto">
													<table className="w-full min-w-[640px] table-fixed border-collapse text-sm">
														<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
															<tr>
																<th className="px-3 py-2">列名</th>
																<th className="px-3 py-2">中文名</th>
																<th className="px-3 py-2">类型</th>
																<th className="px-3 py-2">可为空</th>
																<th className="px-3 py-2">标签</th>
																<th className="px-3 py-2">敏感标记</th>
															</tr>
														</thead>
														<tbody>
															{table.columns.map((column) => {
																const tagsText =
																	column.tags && column.tags.length ? column.tags.join(", ") : "-";
																const sensitiveText =
																	column.sensitiveTags && column.sensitiveTags.length
																		? column.sensitiveTags.join(", ")
																		: "-";
																return (
																	<tr
																		key={column.id || `${table.name}-${column.name}`}
																		className="border-b border-border/40 last:border-b-0"
																	>
																		<td className="px-3 py-2 text-xs font-medium">{column.name}</td>
																		<td className="px-3 py-2 text-xs text-muted-foreground">
																			{column.displayName || "-"}
																		</td>
																		<td className="px-3 py-2 text-xs">{column.dataType || "-"}</td>
																		<td className="px-3 py-2 text-xs">
																			{column.nullable === false ? "否" : "是"}
																		</td>
																		<td className="px-3 py-2 text-xs truncate" title={tagsText}>
																			{tagsText}
																		</td>
																		<td className="px-3 py-2 text-xs truncate" title={sensitiveText}>
																			{sensitiveText}
																		</td>
																	</tr>
																);
															})}
														</tbody>
													</table>
												</div>
											) : (
												<div className="text-sm text-muted-foreground">暂无列信息</div>
											)}
										</div>
									))
								) : (
									<div className="text-sm text-muted-foreground">暂无列信息，请在列表页刷新后重试。</div>
								)}
							</div>
						</TabsContent>
						<TabsContent value="sample">
								<div className="space-y-3">
									<p className="text-sm text-muted-foreground">默认展示前 10 条数据</p>
								{sampleLoading ? (
									<div className="text-sm text-muted-foreground">加载中…</div>
								) : sampleData && sampleData.headers.length ? (
									sampleData.rows.length ? (
										<div className="overflow-x-auto">
											<table className="w-full min-w-[640px] table-fixed border-collapse text-sm">
												<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
													<tr>
														{sampleData.headers.map((header) => {
															const label = columnLabelMap.get(normalizeColumnKey(header)) ?? header;
															return (
																<th key={header} className="px-3 py-2">
																	{label}
																</th>
															);
														})}
													</tr>
												</thead>
												<tbody>
													{sampleData.rows.map((row, rowIndex) => (
														<tr key={rowIndex} className="border-b border-border/40 last:border-b-0">
															{sampleData.headers.map((header) => {
																const cell = (row ?? {})[header];
																const text = cell == null ? "" : String(cell);
																return (
																	<td key={header} className="px-3 py-2 text-xs truncate" title={text}>
																		{text}
																	</td>
																);
															})}
														</tr>
													))}
												</tbody>
											</table>
										</div>
									) : (
										<div className="text-sm text-muted-foreground">采样结果为空</div>
									)
								) : (
									<div className="text-sm text-muted-foreground">暂无采样数据，可尝试刷新采样。</div>
								)}
							</div>
						</TabsContent>
					</Tabs>
				</CardContent>
			</Card>
            {editable && (
                <Card>
                    <CardHeader className="flex items-center justify-between">
                        <CardTitle className="text-base">访问授权用户</CardTitle>
                        {canManageGrants ? (
                            <Button variant="outline" size="sm" onClick={() => setGrantDialogOpen(true)}>
                                添加用户
                            </Button>
                        ) : null}
                    </CardHeader>
                    <CardContent className="space-y-3">
                        {grantLoading ? (
                            <div className="text-sm text-muted-foreground">加载中…</div>
                        ) : grants.length ? (
                            <div className="overflow-hidden rounded border">
                                <table className="w-full table-fixed text-sm">
                                    <thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
                                        <tr>
                                            <th className="px-3 py-2">用户名</th>
                                            <th className="px-3 py-2">姓名</th>
                                            <th className="px-3 py-2">所属部门</th>
                                            <th className="px-3 py-2">分配人</th>
                                            <th className="px-3 py-2">分配时间</th>
                                            {canManageGrants ? <th className="px-3 py-2 w-[80px]">操作</th> : null}
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {grants.map((grant) => (
                                            <tr key={grant.id} className="border-b last:border-b-0">
                                                <td className="px-3 py-2 text-xs font-medium">{grant.username}</td>
                                                <td className="px-3 py-2 text-xs">{grant.displayName || "-"}</td>
                                                <td className="px-3 py-2 text-xs">{grant.deptCode || "-"}</td>
                                                <td className="px-3 py-2 text-xs">{grant.createdBy || "-"}</td>
                                                <td className="px-3 py-2 text-xs">{formatDateTime(grant.createdDate)}</td>
                                                {canManageGrants ? (
                                                    <td className="px-3 py-2">
                                                        <Button
                                                            variant="ghost"
                                                            size="sm"
                                                            className="text-destructive hover:text-destructive"
                                                            onClick={() => handleRemoveGrant(grant)}
                                                        >
                                                            删除
                                                        </Button>
                                                    </td>
                                                ) : null}
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        ) : (
                            <div className="text-sm text-muted-foreground">尚未分配任何用户访问该数据集。</div>
                        )}
                    </CardContent>
                </Card>
            )}
            <Dialog
                open={grantDialogOpen}
                onOpenChange={(open) => {
                    setGrantDialogOpen(open);
                    if (!open) {
                        resetGrantForm();
                        setUserOptions([]);
                        setUserPickerOpen(false);
                    }
                }}
            >
                <DialogContent className="sm:max-w-[420px]">
                    <DialogHeader>
                        <DialogTitle>添加访问用户</DialogTitle>
                    </DialogHeader>
                    <div className="space-y-3 py-2">
                        <div className="grid gap-1.5">
                            <Label>选择用户 *</Label>
                            <Popover open={userPickerOpen} onOpenChange={setUserPickerOpen}>
                                <PopoverTrigger asChild>
                                    <Button
                                        variant="outline"
                                        role="combobox"
                                        aria-expanded={userPickerOpen}
                                        className={cn(
                                            "justify-between",
                                            grantForm.username ? "" : "text-muted-foreground",
                                        )}
                                    >
                                        {grantForm.username
                                            ? `${grantForm.displayName || grantForm.username} (${grantForm.username})`
                                            : "选择用户"}
                                        <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                                    </Button>
                                </PopoverTrigger>
                                <PopoverContent className="w-[320px] p-0">
                                    <Command>
                                        <CommandInput
                                            placeholder="搜索用户名..."
                                            value={userSearch}
                                            onValueChange={setUserSearch}
                                        />
                                        <CommandList>
                                        {userLoading ? (
                                                <div className="px-3 py-4 text-sm text-muted-foreground">加载中…</div>
                                            ) : (
                                                <>
                                                    <CommandEmpty>未找到匹配用户</CommandEmpty>
                                                    <CommandGroup heading="用户">
                                                        {userOptions.map((option) => (
                                                            <CommandItem
                                                                key={option.id}
                                                                value={option.username}
                                                                onSelect={() => handleUserSelect(option)}
                                                            >
                                                                <div className="flex flex-col overflow-hidden">
                                                                    <span className="truncate font-medium">
                                                                        {option.fullName || option.displayName || option.username}
                                                                    </span>
                                                                    <span className="truncate text-xs text-muted-foreground">
                                                                        {option.username}
                                                                        {option.deptCode
                                                                            ? ` · ${option.deptCode}`
                                                                            : ""}
                                                                    </span>
                                                                </div>
                                                                <Check
                                                                    className={cn(
                                                                        "ml-2 h-4 w-4",
                                                                        grantForm.username === option.username
                                                                            ? "opacity-100"
                                                                            : "opacity-0",
                                                                    )}
                                                                />
                                                            </CommandItem>
                                                        ))}
                                                    </CommandGroup>
                                                </>
                                            )}
                                        </CommandList>
                                    </Command>
                                </PopoverContent>
                            </Popover>
                            {grantForm.username ? (
                                <span className="text-xs text-muted-foreground">
                                    已选择账号：{grantForm.username}
                                </span>
                            ) : null}
                        </div>
                        <div className="grid gap-1.5">
                            <Label>显示名称</Label>
                            <Input
                                value={grantForm.displayName}
                                onChange={(e) => setGrantForm((prev) => ({ ...prev, displayName: e.target.value }))}
                                placeholder="用于展示的姓名"
                            />
                        </div>
                        <div className="grid gap-1.5">
                            <Label>所属部门</Label>
                            <Select
                                value={grantForm.deptCode && grantForm.deptCode.trim() ? grantForm.deptCode : EMPTY_DEPT_VALUE}
                                onValueChange={(value) =>
                                    setGrantForm((prev) => ({ ...prev, deptCode: value === EMPTY_DEPT_VALUE ? "" : value }))
                                }
                                disabled={deptLoading}
                            >
                                <SelectTrigger>
                                    <SelectValue placeholder="选择部门（可选）" />
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value={EMPTY_DEPT_VALUE}>默认（不指定）</SelectItem>
                                    {nonRootDeptOptions.map((dept) => (
                                        <SelectItem key={dept.code} value={dept.code}>
                                            {dept.nameZh || dept.nameEn || dept.code}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                    </div>
                    <DialogFooter>
                        <Button
                            variant="outline"
                            onClick={() => {
                                resetGrantForm();
                                setGrantDialogOpen(false);
                            }}
                        >
                            取消
                        </Button>
                        <Button onClick={onGrantSubmit} disabled={grantSaving || !grantForm.username}>
                            {grantSaving ? "提交中…" : "确认添加"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
		</div>
	);
}
