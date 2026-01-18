import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "react-router";
import { Button } from "@/ui/button";
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
	getDatasetSecurityMapping,
	upsertDatasetSecurityMapping,
	getDatasetOpenMetadata,
} from "@/api/platformApi";
import type { DatasetAsset, DatasetGrant, TableSchema } from "@/types/catalog";
import deptService, { type DeptDto } from "@/api/services/deptService";
import userDirectoryService, { type UserDirectoryEntry } from "@/api/services/userDirectoryService";
import { useRouter } from "@/routes/hooks";
import { normalizeClassification } from "@/utils/classification";
import { useUserInfo } from "@/store/userStore";
import { cn } from "@/utils";
import { Check, ChevronsUpDown } from "lucide-react";
import { normalizeColumnKey } from "@/utils/columnName";
import { DatasetAccessRequestDialog } from "@/components/security/DatasetAccessRequestDialog";

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
const DEFAULT_DEPT_LABEL = "默认（不指定）";

export default function DatasetDetailPage() {
	const params = useParams();
	const id = String(params.id || "");
	const [loading, setLoading] = useState(true);
	const [saving, setSaving] = useState(false);
	const [dataset, setDataset] = useState<DatasetAsset | null>(null);
	const [omLoading, setOmLoading] = useState(false);
	const [omInfo, setOmInfo] = useState<any | null>(null);
	const [columnsView, setColumnsView] = useState<"tech" | "business" | "merged">("business");
	const [diffOpen, setDiffOpen] = useState(false);
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
	const isInstituteDataAdmin = useMemo(
		() =>
			normalizedRoleSet.has("INST_DATA_OWNER") ||
			normalizedRoleSet.has("ROLE_INST_DATA_OWNER") ||
			normalizedRoleSet.has("INST_LEADER") ||
			normalizedRoleSet.has("ROLE_INST_LEADER"),
		[normalizedRoleSet],
	);
	const hasDataMaintainerRole = useMemo(() => {
		const maintainers = [
			"ROLE_OP_ADMIN",
			"OPADMIN",
			"ROLE_ADMIN",
			"ADMIN",
			"ROLE_INST_DATA_OWNER",
			"INST_DATA_OWNER",
			"ROLE_INST_LEADER",
			"INST_LEADER",
			"ROLE_INST_DATA_DEV",
			"INST_DATA_DEV",
			"ROLE_DEPT_DATA_OWNER",
			"DEPT_DATA_OWNER",
			"ROLE_DEPT_LEADER",
			"DEPT_LEADER",
			"ROLE_DEPT_DATA_DEV",
			"DEPT_DATA_DEV",
		];
		return maintainers.some((role) => normalizedRoleSet.has(role));
	}, [normalizedRoleSet]);
	const userDeptCode = useMemo(() => {
		const attrs = ((userInfo as any)?.attributes || {}) as Record<string, unknown>;
		const pickDept = (value: unknown): string | undefined => {
			const tokens = parseStringList(value);
			if (tokens.length > 0) {
				const first = tokens[0]?.trim();
				return first ? first : undefined;
			}
			if (typeof value === "string") {
				const trimmed = value.trim();
				return trimmed || undefined;
			}
			return undefined;
		};
		return (
			pickDept(attrs.dept_code) ||
			pickDept(attrs.deptCode) ||
			pickDept(attrs.department) ||
			pickDept((userInfo as any)?.dept_code) ||
			pickDept((userInfo as any)?.deptCode) ||
			pickDept((userInfo as any)?.department)
		);
	}, [userInfo]);
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
	const canProxyApply = useMemo(() => {
		const allowed = [
			"ROLE_OP_ADMIN",
			"OPADMIN",
			"ROLE_ADMIN",
			"ADMIN",
			"ROLE_INST_DATA_OWNER",
			"INST_DATA_OWNER",
			"ROLE_DEPT_DATA_OWNER",
			"DEPT_DATA_OWNER",
		];
		return allowed.some((role) => normalizedRoleSet.has(role));
	}, [normalizedRoleSet]);
	const [grants, setGrants] = useState<DatasetGrant[]>([]);
	const [grantLoading, setGrantLoading] = useState(false);
	const [grantDialogOpen, setGrantDialogOpen] = useState(false);
	const [grantSaving, setGrantSaving] = useState(false);
	const [grantForm, setGrantForm] = useState({
		username: "",
		displayName: "",
		deptCode: "",
	});

	const [securityMapping, setSecurityMapping] = useState<{ dataLevelField: string; deptField: string }>({
		dataLevelField: "",
		deptField: "",
	});
	const [securityMappingLoading, setSecurityMappingLoading] = useState(false);
	const [securityMappingSaving, setSecurityMappingSaving] = useState(false);

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
	const [accessDialogOpen, setAccessDialogOpen] = useState(false);
	const [deptOptions, setDeptOptions] = useState<DeptDto[]>([]);
	const [deptLoading, setDeptLoading] = useState(false);
	const [userOptions, setUserOptions] = useState<UserDirectoryEntry[]>([]);
	const [userLoading, setUserLoading] = useState(false);
	const [userSearch, setUserSearch] = useState("");
	const [userPickerOpen, setUserPickerOpen] = useState(false);
    const router = useRouter();
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
	const grantDeptOptions = useMemo(
		() => sortedDeptOptions.filter((dept) => !dept.isRoot && dept.parentId != null && dept.parentId !== 0),
		[sortedDeptOptions],
	);
	const deptLabelMap = useMemo(() => {
		const map = new Map<string, string>();
		sortedDeptOptions.forEach((dept) => {
			const code = String(dept.code ?? "").trim();
			if (!code) return;
			const baseLabel = dept.nameZh || dept.nameEn || code;
			const label = dept.isRoot ? `${baseLabel}（ROOT）` : baseLabel;
			map.set(code, label);
		});
		return map;
	}, [sortedDeptOptions]);
	const resolveDeptLabel = useCallback(
		(code?: string | null) => {
			if (!code) {
				return DEFAULT_DEPT_LABEL;
			}
			const trimmed = code.trim();
			if (!trimmed) {
				return DEFAULT_DEPT_LABEL;
			}
			return deptLabelMap.get(trimmed) ?? trimmed;
		},
		[deptLabelMap],
	);
	const renderGrantDept = useCallback(
		(grant: DatasetGrant) => {
			const rawName = pickFirstString(
				(grant as any)?.deptName,
				(grant as any)?.departmentName,
				(grant as any)?.deptLabel,
			);
			if (rawName) {
				return rawName;
			}
			return resolveDeptLabel(grant.deptCode);
		},
		[resolveDeptLabel],
	);
	const enforcedOwnerDept = useMemo(() => {
		if (isOpadmin || isInstituteDataAdmin) return undefined;
		const code = (userDeptCode || "").trim();
		return code || undefined;
	}, [isInstituteDataAdmin, isOpadmin, userDeptCode]);
	const deptOptionsForSelect = useMemo(() => {
		if (isInstituteDataAdmin) {
			return deptSelectOptions;
		}
		if (!enforcedOwnerDept) {
			return deptSelectOptions;
		}
		const matched = deptSelectOptions.find((dept) => String(dept.code) === enforcedOwnerDept);
		if (matched) {
			return [matched];
		}
		return [
			{
				code: enforcedOwnerDept,
				nameZh: enforcedOwnerDept,
				nameEn: enforcedOwnerDept,
				parentId: null,
				isRoot: false,
			},
		];
	}, [isInstituteDataAdmin, deptSelectOptions, enforcedOwnerDept]);
	const deptSelectDisabled = !editable || !isOpadmin;
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
					grantDeptOptions.some((dept) => String(dept.code) === normalizedDept);
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
		[grantDeptOptions],
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
				const errCode = (error as any)?.response?.data?.code;
				if (errCode === "dts-sec-0004") {
					if (canProxyApply) {
						setAccessDialogOpen(true);
					} else if (!silent) {
						toast.error("无权限预览数据内容，请联系数据管理员代申请");
					}
					return;
				}
				if (!silent) {
					toast.error("采样失败");
				}
			} finally {
				setSampleLoading(false);
			}
			},
			[id, canProxyApply],
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
            normalized.classification = normalizeClassification((data as any)?.classification) ?? "INTERNAL";
            delete normalized.dataLevel;
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
		if (!id) return;
		if (technicalColumns.length) {
			setColumnsView("tech");
		} else {
			setColumnsView("business");
		}
	}, [id, technicalColumns.length]);

	useEffect(() => {
		if (!id) return;
		let mounted = true;
		setOmLoading(true);
		(getDatasetOpenMetadata(id) as any)
			.then((resp: any) => {
				if (!mounted) return;
				setOmInfo(resp || null);
			})
			.catch((error: any) => {
				console.error(error);
				if (!mounted) return;
				setOmInfo(null);
			})
			.finally(() => {
				if (!mounted) return;
				setOmLoading(false);
			});
		return () => {
			mounted = false;
		};
	}, [id]);

	useEffect(() => {
		if (!id || !hasDataMaintainerRole) {
			return;
		}
		let mounted = true;
		setSecurityMappingLoading(true);
		(getDatasetSecurityMapping(id) as any)
			.then((res: any) => {
				if (!mounted) return;
				setSecurityMapping({
					dataLevelField: String(res?.dataLevelField ?? "").trim(),
					deptField: String(res?.deptField ?? "").trim(),
				});
			})
			.catch((e: any) => {
				console.error(e);
			})
			.finally(() => {
				if (mounted) setSecurityMappingLoading(false);
			});
		return () => {
			mounted = false;
		};
	}, [id, hasDataMaintainerRole]);

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

	// Force owner department for non-institute admins; otherwise keep legacy root fallback.
	useEffect(() => {
		if (!dataset) return;
	if (!enforcedOwnerDept) {
		return;
	}
	const normalized = enforcedOwnerDept.trim();
	if (!normalized) {
		return;
	}
	const current = String(dataset.ownerDept || "").trim();
	if (current === normalized) {
		return;
	}
	setDataset((prev) => (prev ? { ...prev, ownerDept: normalized } : prev));
}, [dataset, enforcedOwnerDept]);

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


	    const onSave = async () => {
	if (!dataset) return;
	if (!editable) {
		toast.error("当前用户无权保存该数据集");
		return;
	}
	// Sanitize payload for backend
	const payload: any = {
		...dataset,
		classification: normalizeClassification(dataset.classification ?? undefined) ?? "INTERNAL",
		ownerDept: dataset.ownerDept && dataset.ownerDept.trim().length > 0 ? dataset.ownerDept.trim() : undefined,
		// Backend expects a string; submit as comma-separated list
		tags: Array.isArray((dataset as any).tags)
			? ((dataset as any).tags as string[]).join(",")
			: String((dataset as any).tags || ""),
	};
	delete payload.dataLevel;
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

	const saveSecurityMapping = useCallback(async () => {
		if (!id) return;
		if (!hasDataMaintainerRole) {
			toast.error("当前用户无权配置字段映射");
			return;
		}
		setSecurityMappingSaving(true);
		try {
			await upsertDatasetSecurityMapping(id, {
				dataLevelField: securityMapping.dataLevelField?.trim() || null,
				deptField: securityMapping.deptField?.trim() || null,
			});
			toast.success("已保存字段映射");
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message ?? "保存字段映射失败");
		} finally {
			setSecurityMappingSaving(false);
		}
	}, [hasDataMaintainerRole, id, securityMapping.dataLevelField, securityMapping.deptField]);

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

	const allColumnNames = useMemo(() => {
		const set = new Set<string>();
		for (const table of tables) {
			for (const column of Array.isArray(table?.columns) ? table.columns : []) {
				const name = String(column?.name ?? "").trim();
				if (name) set.add(name);
			}
		}
		return Array.from(set).sort((a, b) => a.localeCompare(b));
	}, [tables]);

	const guessedDataLevelField = useMemo(() => {
		const candidates = [
			"data_level",
			"data_security_level",
			"data_secret_level",
			"security_level",
			"secret_level",
			"classification_level",
			"class_level",
			"protect_level",
			"data_protect_level",
			"level",
		];
		const lower = new Map(allColumnNames.map((name) => [name.toLowerCase(), name]));
		for (const c of candidates) {
			const hit = lower.get(c);
			if (hit) return hit;
		}
		return "";
	}, [allColumnNames]);

	const guessedDeptField = useMemo(() => {
		const candidates = [
			"dept_code",
			"department_code",
			"dept",
			"department",
			"dept_id",
			"department_id",
			"org_code",
			"org",
			"org_id",
			"organization_code",
			"organization_id",
		];
		const lower = new Map(allColumnNames.map((name) => [name.toLowerCase(), name]));
		for (const c of candidates) {
			const hit = lower.get(c);
			if (hit) return hit;
		}
		return "";
	}, [allColumnNames]);

	const omEntity = omInfo?.entity ?? null;
	const omOwner = useMemo(() => {
		const owner = omEntity?.owner;
		if (!owner) return "-";
		return owner.displayName || owner.name || owner.id || "-";
	}, [omEntity]);
	const omTags = useMemo(() => {
		const raw = Array.isArray(omEntity?.tags) ? omEntity.tags : [];
		const tags = raw
			.map((item: any) => item?.tagFQN || item?.tag?.name || item?.tag?.displayName || item?.name)
			.filter(Boolean);
		return tags.length ? tags.join(", ") : "-";
	}, [omEntity]);
	const omDomain = useMemo(() => {
		const domain = omEntity?.domain;
		if (!domain) return "-";
		return domain.displayName || domain.name || domain.id || "-";
	}, [omEntity]);
	const omDescription = useMemo(() => {
		const desc = String(omEntity?.description || "").trim();
		return desc || "-";
	}, [omEntity]);
	const omColumns = useMemo(() => {
		return Array.isArray(omEntity?.columns) ? omEntity.columns.length : 0;
	}, [omEntity]);
	const technicalColumns = useMemo(() => {
		if (!Array.isArray(omEntity?.columns)) return [];
		return omEntity.columns
			.map((col: any) => {
				const name = String(col?.name || "").trim();
				if (!name) return null;
				const displayName = String(col?.displayName || col?.description || col?.comment || "").trim();
				const tags = Array.isArray(col?.tags)
					? col.tags
							.map((t: any) => t?.tagFQN || t?.tag?.name || t?.tag?.displayName || t?.name)
							.filter(Boolean)
					: [];
				return {
					name,
					displayName: displayName || "",
					dataType: String(col?.dataType || col?.dataTypeDisplay || "").toUpperCase(),
					nullable: col?.constraint === "NOT_NULL" ? false : true,
					tags,
					description: String(col?.description || "").trim(),
				};
			})
			.filter(Boolean);
	}, [omEntity]);
	const businessColumns = useMemo(() => {
		const list: any[] = [];
		for (const table of tables) {
			for (const col of Array.isArray(table?.columns) ? table.columns : []) {
				if (!col?.name) continue;
				list.push({
					name: col.name,
					displayName: col.displayName || "",
					dataType: col.dataType || "",
					nullable: col.nullable !== false,
					tags: Array.isArray(col.tags) ? col.tags : [],
					sensitiveTags: Array.isArray(col.sensitiveTags) ? col.sensitiveTags : [],
					description: col.description || "",
				});
			}
		}
		return list;
	}, [tables]);
	const mergedColumns = useMemo(() => {
		const map = new Map<string, any>();
		for (const col of technicalColumns) {
			map.set(col.name, { name: col.name, tech: col, biz: null });
		}
		for (const col of businessColumns) {
			const existing = map.get(col.name);
			if (existing) {
				existing.biz = col;
			} else {
				map.set(col.name, { name: col.name, tech: null, biz: col });
			}
		}
		return Array.from(map.values()).sort((a, b) => a.name.localeCompare(b.name));
	}, [technicalColumns, businessColumns]);
	const columnDiff = useMemo(() => {
		const techSet = new Set(technicalColumns.map((c: any) => String(c.name || "").toLowerCase()));
		const bizSet = new Set(businessColumns.map((c: any) => String(c.name || "").toLowerCase()));
		const onlyTech = technicalColumns.filter((c: any) => !bizSet.has(String(c.name || "").toLowerCase()));
		const onlyBiz = businessColumns.filter((c: any) => !techSet.has(String(c.name || "").toLowerCase()));
		const typeMismatch: Array<{ name: string; techType: string; bizType: string }> = [];
		const techMap = new Map(technicalColumns.map((c: any) => [String(c.name || "").toLowerCase(), c]));
		for (const col of businessColumns) {
			const key = String(col.name || "").toLowerCase();
			const tech = techMap.get(key);
			if (!tech) continue;
			const techType = String(tech.dataType || "").toUpperCase();
			const bizType = String(col.dataType || "").toUpperCase();
			if (techType && bizType && techType !== bizType) {
				typeMismatch.push({ name: col.name, techType, bizType });
			}
		}
		return { onlyTech, onlyBiz, typeMismatch };
	}, [technicalColumns, businessColumns]);
	const omLink = useMemo(() => {
		if (!omInfo?.uiBaseUrl || !omInfo?.fqn) return "";
		const base = String(omInfo.uiBaseUrl || "").replace(/\/+$/, "");
		const fqn = encodeURIComponent(String(omInfo.fqn || ""));
		return `${base}/table/${fqn}`;
	}, [omInfo]);

	if (loading) return <div className="text-sm text-muted-foreground">加载中…</div>;
if (!dataset) return <div className="text-sm text-muted-foreground">未找到该数据集</div>;

	const ownerDeptSelectValue = dataset.ownerDept && dataset.ownerDept.trim().length > 0 ? dataset.ownerDept.trim() : "__PUBLIC__";

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
						{canProxyApply ? (
							<Button
								variant="outline"
								onClick={() => {
									setAccessDialogOpen(true);
								}}
							>
								代申请访问
							</Button>
						) : null}
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
				<Label>所属部门</Label>
				<p className="text-xs text-muted-foreground">
					未指定或选择 ROOT 节点时，数据集将对所有部门开放。
					{!isOpadmin && "（当前账号仅可查看所属部门）"}
				</p>
				<Select
					value={ownerDeptSelectValue}
					disabled={deptSelectDisabled}
					onValueChange={(v) =>
						setDataset({
							...(dataset as DatasetAsset),
							ownerDept: v === "__PUBLIC__" ? "" : v,
						})
					}
				>
					<SelectTrigger>
						<SelectValue
							placeholder={
								deptLoading
									? "加载中…"
									: !isOpadmin
									? "仅运维管理员可调整所属部门"
									: "选择部门…"
							}
						/>
					</SelectTrigger>
						<SelectContent>
							<SelectItem value="__PUBLIC__">未指定（全局可见）</SelectItem>
							{deptOptionsForSelect.map((d) => {
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
									{omInfo?.found && omDescription !== "-" ? (
										<div className="text-xs text-muted-foreground">技术描述：{omDescription}</div>
									) : null}
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
								<Card className="mt-4">
									<CardHeader>
										<CardTitle className="text-base">技术资产信息</CardTitle>
										<p className="text-sm text-muted-foreground">
											平台目录保留为业务视图，技术元数据用于增强展示。
										</p>
									</CardHeader>
									<CardContent className="space-y-3">
										{omLoading ? (
											<div className="text-sm text-muted-foreground">加载中…</div>
										) : !omInfo ? (
											<div className="text-sm text-muted-foreground">暂未获取技术元数据信息</div>
										) : !omInfo?.enabled ? (
											<div className="text-sm text-muted-foreground">元数据服务未启用</div>
										) : omInfo?.found ? (
											<div className="grid gap-3 text-sm md:grid-cols-2">
												<div>资产FQN：{omInfo?.fqn || "-"}</div>
												<div>Owner：{omOwner}</div>
												<div>Domain：{omDomain}</div>
												<div>标签：{omTags}</div>
												<div>字段数：{omColumns || "-"}</div>
												<div>
													元数据链接：
													{omLink ? (
														<a className="ml-1 text-primary underline" href={omLink} target="_blank" rel="noreferrer">
															打开
														</a>
													) : (
														<span className="ml-1 text-muted-foreground">未配置</span>
													)}
												</div>
												<div className="md:col-span-2">描述：{omDescription}</div>
											</div>
										) : (
											<div className="text-sm text-muted-foreground">
												{omInfo?.message || "未找到匹配的技术资产"}
												{omInfo?.fqn ? <span className="ml-2">候选FQN：{omInfo.fqn}</span> : null}
											</div>
										)}
									</CardContent>
								</Card>
								{hasDataMaintainerRole ? (
									<Card className="mt-4">
										<CardHeader>
											<CardTitle className="text-base">行级安全字段映射</CardTitle>
											<p className="text-sm text-muted-foreground">
												用于“数据密级 + 部门”行过滤；不配置则按字段名自动识别。
											</p>
										</CardHeader>
										<CardContent className="space-y-4">
											<div className="grid gap-4 md:grid-cols-2">
												<div className="grid gap-2">
													<Label>数据密级字段</Label>
													<Select
														value={securityMapping.dataLevelField ? securityMapping.dataLevelField : "__AUTO__"}
														disabled={securityMappingLoading || securityMappingSaving}
														onValueChange={(v) =>
															setSecurityMapping((prev) => ({
																...prev,
																dataLevelField: v === "__AUTO__" ? "" : v,
															}))
														}
													>
														<SelectTrigger>
															<SelectValue placeholder="自动识别" />
														</SelectTrigger>
														<SelectContent>
															<SelectItem value="__AUTO__">自动识别</SelectItem>
															{allColumnNames.map((name) => (
																<SelectItem key={name} value={name}>
																	{name}
																</SelectItem>
															))}
														</SelectContent>
													</Select>
													{!securityMapping.dataLevelField && (
														<div className="text-xs text-muted-foreground">
															自动识别：{guessedDataLevelField || "未识别"}
														</div>
													)}
												</div>
												<div className="grid gap-2">
													<Label>部门字段</Label>
													<Select
														value={securityMapping.deptField ? securityMapping.deptField : "__AUTO__"}
														disabled={securityMappingLoading || securityMappingSaving}
														onValueChange={(v) =>
															setSecurityMapping((prev) => ({
																...prev,
																deptField: v === "__AUTO__" ? "" : v,
															}))
														}
													>
														<SelectTrigger>
															<SelectValue placeholder="自动识别" />
														</SelectTrigger>
														<SelectContent>
															<SelectItem value="__AUTO__">自动识别</SelectItem>
															{allColumnNames.map((name) => (
																<SelectItem key={name} value={name}>
																	{name}
																</SelectItem>
															))}
														</SelectContent>
													</Select>
													{!securityMapping.deptField && (
														<div className="text-xs text-muted-foreground">
															自动识别：{guessedDeptField || "未识别"}
														</div>
													)}
												</div>
											</div>
											<div className="flex justify-end">
												<Button
													variant="outline"
													onClick={() => void saveSecurityMapping()}
													disabled={securityMappingLoading || securityMappingSaving}
												>
													{securityMappingSaving ? "保存中…" : "保存映射"}
												</Button>
											</div>
										</CardContent>
									</Card>
								) : null}
							</TabsContent>
						<TabsContent value="columns">
							<div className="space-y-4">
								{(technicalColumns.length || businessColumns.length) ? (
									<div className="rounded-md border border-dashed p-3 text-xs text-muted-foreground">
										<div className="mb-2 text-[13px] font-medium text-foreground">字段差异提示</div>
										<div className="flex flex-wrap gap-4">
											<span>仅技术字段：{columnDiff.onlyTech.length}</span>
											<span>仅业务字段：{columnDiff.onlyBiz.length}</span>
											<span>类型不一致：{columnDiff.typeMismatch.length}</span>
										</div>
										{columnDiff.typeMismatch.length ? (
											<div className="mt-2 text-[11px] text-muted-foreground">
												示例：{columnDiff.typeMismatch.slice(0, 5).map((item) => `${item.name}(${item.techType}/${item.bizType})`).join("，")}
											</div>
										) : null}
										<div className="mt-3">
											<Button variant="outline" size="sm" onClick={() => setDiffOpen(true)}>
												查看差异清单
											</Button>
										</div>
									</div>
								) : null}
								<div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
									<span>视图：</span>
									<Button
										variant={columnsView === "tech" ? "default" : "outline"}
										size="sm"
										onClick={() => setColumnsView("tech")}
										disabled={!technicalColumns.length}
									>
										技术字段
									</Button>
									<Button
										variant={columnsView === "business" ? "default" : "outline"}
										size="sm"
										onClick={() => setColumnsView("business")}
									>
										业务字段
									</Button>
									<Button
										variant={columnsView === "merged" ? "default" : "outline"}
										size="sm"
										onClick={() => setColumnsView("merged")}
										disabled={!mergedColumns.length}
									>
										融合视图
									</Button>
								</div>
								{columnsView === "tech" && technicalColumns.length ? (
									<div className="space-y-2">
										<div className="flex items-center justify-between">
											<span className="text-sm font-medium">技术字段</span>
											<span className="text-xs text-muted-foreground">列数 {technicalColumns.length}</span>
										</div>
										<div className="overflow-x-auto">
											<table className="w-full min-w-[640px] table-fixed border-collapse text-sm">
												<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
													<tr>
														<th className="px-3 py-2">列名</th>
														<th className="px-3 py-2">说明</th>
														<th className="px-3 py-2">类型</th>
														<th className="px-3 py-2">可为空</th>
														<th className="px-3 py-2">标签</th>
													</tr>
												</thead>
												<tbody>
													{technicalColumns.map((column: any) => {
														const tagsText = column.tags && column.tags.length ? column.tags.join(", ") : "-";
														return (
															<tr
																key={`om-${column.name}`}
																className="border-b border-border/40 last:border-b-0"
															>
																<td className="px-3 py-2 text-xs font-medium">{column.name}</td>
																<td className="px-3 py-2 text-xs text-muted-foreground">
																	{column.displayName || column.description || "-"}
																</td>
																<td className="px-3 py-2 text-xs">{column.dataType || "-"}</td>
																<td className="px-3 py-2 text-xs">{column.nullable === false ? "否" : "是"}</td>
																<td className="px-3 py-2 text-xs truncate" title={tagsText}>
																	{tagsText}
																</td>
															</tr>
														);
													})}
												</tbody>
											</table>
										</div>
									</div>
								) : null}
								{columnsView === "merged" && mergedColumns.length ? (
									<div className="space-y-2">
										<div className="flex items-center justify-between">
											<span className="text-sm font-medium">融合字段</span>
											<span className="text-xs text-muted-foreground">列数 {mergedColumns.length}</span>
										</div>
										<div className="overflow-x-auto">
											<table className="w-full min-w-[720px] table-fixed border-collapse text-sm">
												<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
													<tr>
														<th className="px-3 py-2">列名</th>
														<th className="px-3 py-2">技术说明</th>
														<th className="px-3 py-2">业务说明</th>
														<th className="px-3 py-2">类型</th>
														<th className="px-3 py-2">标签</th>
													</tr>
												</thead>
												<tbody>
													{mergedColumns.map((row: any) => {
														const tags = [
															...(row.tech?.tags || []),
															...(row.biz?.tags || []),
															...(row.biz?.sensitiveTags || []),
														]
															.filter(Boolean)
															.join(", ");
														const techDesc = row.tech?.displayName || row.tech?.description || "-";
														const bizDesc = row.biz?.displayName || row.biz?.description || "-";
														const dataType = row.tech?.dataType || row.biz?.dataType || "-";
														return (
															<tr key={`merged-${row.name}`} className="border-b border-border/40 last:border-b-0">
																<td className="px-3 py-2 text-xs font-medium">{row.name}</td>
																<td className="px-3 py-2 text-xs text-muted-foreground">{techDesc}</td>
																<td className="px-3 py-2 text-xs text-muted-foreground">{bizDesc}</td>
																<td className="px-3 py-2 text-xs">{dataType}</td>
																<td className="px-3 py-2 text-xs truncate" title={tags || "-"}>
																	{tags || "-"}
																</td>
															</tr>
														);
													})}
												</tbody>
											</table>
										</div>
									</div>
								) : null}
								{columnsView === "business" && technicalColumns.length ? (
									<div className="text-xs text-muted-foreground">平台登记字段（业务视图）</div>
								) : null}
								{columnsView === "business" && tables.length ? (
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
								) : columnsView === "business" ? (
									<div className="text-sm text-muted-foreground">暂无列信息，请在列表页刷新后重试。</div>
								) : null}
								{columnsView === "tech" && !technicalColumns.length ? (
									<div className="text-sm text-muted-foreground">暂无技术字段信息</div>
								) : null}
								{columnsView === "merged" && !mergedColumns.length ? (
									<div className="text-sm text-muted-foreground">暂无融合字段信息</div>
								) : null}
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
												<td className="px-3 py-2 text-xs">{renderGrantDept(grant)}</td>
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
                                                                            ? ` · ${resolveDeptLabel(option.deptCode)}`
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
					<SelectItem value={EMPTY_DEPT_VALUE}>{DEFAULT_DEPT_LABEL}</SelectItem>
					{grantDeptOptions.map((dept) => (
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
			<DatasetAccessRequestDialog
				open={accessDialogOpen}
				onOpenChange={setAccessDialogOpen}
				dataset={{
					id: id,
					name: dataset?.name,
					classification: (dataset as any)?.classification,
					warehouseLayer: (dataset as any)?.warehouseLayer ?? (dataset as any)?.warehouse_layer,
					ownerDept: (dataset as any)?.ownerDept ?? (dataset as any)?.owner_dept,
				}}
				defaultActions={["preview"]}
			/>
			<Dialog open={diffOpen} onOpenChange={setDiffOpen}>
				<DialogContent className="max-w-2xl">
					<DialogHeader>
						<DialogTitle>字段差异清单</DialogTitle>
					</DialogHeader>
					<div className="space-y-4 text-sm">
						<div>
							<div className="mb-2 font-medium">仅技术字段（{columnDiff.onlyTech.length}）</div>
							<div className="max-h-36 overflow-auto rounded border border-dashed p-2 text-xs text-muted-foreground">
								{columnDiff.onlyTech.length
									? columnDiff.onlyTech.map((c: any) => c.name).join(", ")
									: "无"}
							</div>
						</div>
						<div>
							<div className="mb-2 font-medium">仅业务字段（{columnDiff.onlyBiz.length}）</div>
							<div className="max-h-36 overflow-auto rounded border border-dashed p-2 text-xs text-muted-foreground">
								{columnDiff.onlyBiz.length
									? columnDiff.onlyBiz.map((c: any) => c.name).join(", ")
									: "无"}
							</div>
						</div>
						<div>
							<div className="mb-2 font-medium">类型不一致（{columnDiff.typeMismatch.length}）</div>
							<div className="max-h-36 overflow-auto rounded border border-dashed p-2 text-xs text-muted-foreground">
								{columnDiff.typeMismatch.length
									? columnDiff.typeMismatch.map((c) => `${c.name} (${c.techType} / ${c.bizType})`).join(", ")
									: "无"}
							</div>
						</div>
					</div>
					<DialogFooter>
						<Button variant="outline" onClick={() => setDiffOpen(false)}>
							关闭
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</div>
	);
}
