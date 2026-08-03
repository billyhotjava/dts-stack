import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Alert, Button, Card, Form, Input, List, Modal, Select, Space, Tag, Typography } from "antd";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { } from "@ant-design/icons";
import { useNavigate, useSearchParams } from "react-router";
import { EmptyState } from "@/components/empty-state";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import {
	applyStructuredReferenceCodeImport,
	createReferenceCode,
	createReferenceCodeItem,
	createReferenceCodeMapping,
	deleteReferenceCode,
	deleteReferenceCodeItem,
	deleteReferenceCodeMapping,
	getStructuredReferenceCodeImportRun,
	getReferenceCodeImportOpsOverview,
	getReferenceCodeReferences,
	listStructuredReferenceCodeImportRuns,
	listReferenceCodeItems,
	listReferenceCodeMappings,
	listReferenceCodes,
	previewStructuredReferenceCodeImport,
	rollbackStructuredReferenceCodeImport,
	syncReferenceCodeSeeds,
	updateReferenceCode,
	updateReferenceCodeItem,
	updateReferenceCodeMapping,
} from "@/api/platformApi";
import { normalizeText, formatDateTime } from "@/utils/textUtils";

const { Text } = Typography;

type ReferenceCodeDirectory = {
	codeTypeId?: string;
	codeTypeCode?: string;
	codeTypeName?: string;
	stdLevel?: string;
	bizCatalog?: string;
	dataType?: string;
	status?: number;
	ownerDept?: string;
	version?: string;
	itemCount?: number;
};

type ReferenceCodeItem = {
	itemId?: number;
	codeTypeId?: string;
	codeValue?: string;
	codeName?: string;
	description?: string;
	sortNum?: number;
	parentCode?: string;
	isDefault?: boolean;
};

type StructuredImportRow = {
	codeValue: string;
	codeName: string;
	description?: string;
	sortNum?: number;
	parentCode?: string;
	isDefault?: boolean;
};

type StructuredImportPreview = {
	runId?: string;
	total?: number;
	valid?: number;
	createCount?: number;
	updateCount?: number;
	conflictCount?: number;
	errorCount?: number;
	conflictPolicy?: string;
	strictMode?: boolean;
	conflicts?: Array<Record<string, any>>;
	errors?: Array<Record<string, any>>;
};

type StructuredImportRunSummary = {
	runId?: string;
	importMode?: string;
	conflictPolicy?: string;
	status?: string;
	summary?: string;
	createdBy?: string;
	createdDate?: string;
	previewTotal?: number;
	createCount?: number;
	updateCount?: number;
	conflictCount?: number;
	errorCount?: number;
	rollbackable?: boolean;
};

type StructuredImportRunDetail = StructuredImportRunSummary & {
	diffCount?: number;
	diffRows?: Array<Record<string, any>>;
	preview?: Record<string, any>;
	beforeSample?: Array<Record<string, any>>;
	afterSample?: Array<Record<string, any>>;
};
type ReferenceCodeOpsOverview = {
	windowHours?: number;
	directoryCount?: number;
	totalRuns?: number;
	appliedRuns?: number;
	rolledBackRuns?: number;
	previewRuns?: number;
	conflictTotal?: number;
	errorTotal?: number;
	queryCostMs?: number;
	failureTop?: Array<{ category?: string; count?: number }>;
};

type ReferenceCodeMapping = {
	mapId?: number;
	codeTypeId?: string;
	sourceSys?: string;
	srcCode?: string;
	stdCode?: string;
};

type PagedPayload<T> = { content?: T[]; total?: number; page?: number; size?: number };
type AssetReferenceItem = {
	type?: string;
	label?: string;
	id?: string;
	code?: string;
	name?: string;
	path?: string;
	reason?: string;
};
type AssetReferencePayload = {
	targetName?: string;
	totalReferences?: number;
	items?: AssetReferenceItem[];
};

const STATUS_LABELS: Record<number, { label: string; color: string }> = {
	0: { label: "草稿", color: "default" },
	1: { label: "发布", color: "green" },
	2: { label: "废弃", color: "red" },
};

const STRUCTURED_TEMPLATE = "codeValue,codeName,description,sortNum,parentCode,isDefault";

const parseBoolean = (value?: string) => {
	const text = normalizeText(value)?.toLowerCase();
	if (!text) return undefined;
	if (["1", "true", "yes", "y", "是"].includes(text)) return true;
	if (["0", "false", "no", "n", "否"].includes(text)) return false;
	return undefined;
};
const parseIntOr = (value: string | null, fallback: number) => {
	const parsed = Number.parseInt(String(value || ""), 10);
	return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
};
const parseStructuredRows = (raw: string): StructuredImportRow[] => {
	const lines = raw
		.split(/\r?\n/)
		.map((line) => line.trim())
		.filter(Boolean);
	if (lines.length === 0) return [];
	let start = 0;
	const first = lines[0].toLowerCase();
	if (first.includes("codevalue") && first.includes("codename")) {
		start = 1;
	}
	const rows: StructuredImportRow[] = [];
	for (let i = start; i < lines.length; i++) {
		const parts = lines[i].split(",").map((part) => part.trim());
		if (parts.length < 2) continue;
		const row: StructuredImportRow = {
			codeValue: parts[0],
			codeName: parts[1],
		};
		if (parts[2]) row.description = parts[2];
		if (parts[3] && !Number.isNaN(Number(parts[3]))) row.sortNum = Number(parts[3]);
		if (parts[4]) row.parentCode = parts[4];
		const boolValue = parseBoolean(parts[5]);
		if (typeof boolValue === "boolean") row.isDefault = boolValue;
		rows.push(row);
	}
	return rows;
};

export default function ReferenceCodesPage() {
	const navigate = useNavigate();
	const [searchParams, setSearchParams] = useSearchParams();
	const [keyword, setKeyword] = useState(searchParams.get("keyword") || "");
	const [pageNum, setPageNum] = useState(parseIntOr(searchParams.get("page"), 0));
	const [pageSize, setPageSize] = useState(parseIntOr(searchParams.get("size"), 10) || 10);
	const [data, setData] = useState<PagedPayload<ReferenceCodeDirectory> | null>(null);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [editing, setEditing] = useState<ReferenceCodeDirectory | null>(null);
	const [itemsOpen, setItemsOpen] = useState(false);
	const [activeDirectory, setActiveDirectory] = useState<ReferenceCodeDirectory | null>(null);
	const [itemsLoading, setItemsLoading] = useState(false);
	const [items, setItems] = useState<ReferenceCodeItem[]>([]);
	const [itemModalOpen, setItemModalOpen] = useState(false);
	const [itemSaving, setItemSaving] = useState(false);
	const [itemEditing, setItemEditing] = useState<ReferenceCodeItem | null>(null);
	const [structuredOpen, setStructuredOpen] = useState(false);
	const [structuredRaw, setStructuredRaw] = useState(STRUCTURED_TEMPLATE);
	const [structuredPolicy, setStructuredPolicy] = useState("STRICT");
	const [structuredPreview, setStructuredPreview] = useState<StructuredImportPreview | null>(null);
	const [structuredLoading, setStructuredLoading] = useState(false);
	const [structuredRunId, setStructuredRunId] = useState<string | null>(null);
	const [structuredHistoryOpen, setStructuredHistoryOpen] = useState(false);
	const [structuredHistoryLoading, setStructuredHistoryLoading] = useState(false);
	const [structuredHistory, setStructuredHistory] = useState<StructuredImportRunSummary[]>([]);
	const [structuredHistoryDetail, setStructuredHistoryDetail] = useState<StructuredImportRunDetail | null>(null);
	const [structuredHistoryDetailLoading, setStructuredHistoryDetailLoading] = useState(false);
	const [opsOverviewLoading, setOpsOverviewLoading] = useState(false);
	const [opsOverview, setOpsOverview] = useState<ReferenceCodeOpsOverview | null>(null);
	const [mappingsOpen, setMappingsOpen] = useState(false);
	const [mappingsLoading, setMappingsLoading] = useState(false);
	const [mappings, setMappings] = useState<ReferenceCodeMapping[]>([]);
	const [mappingModalOpen, setMappingModalOpen] = useState(false);
	const [mappingSaving, setMappingSaving] = useState(false);
	const [mappingEditing, setMappingEditing] = useState<ReferenceCodeMapping | null>(null);
	const [referenceOpen, setReferenceOpen] = useState(false);
	const [referenceLoading, setReferenceLoading] = useState(false);
	const [referencePayload, setReferencePayload] = useState<AssetReferencePayload | null>(null);
	const [seedSyncing, setSeedSyncing] = useState(false);
	const [form] = Form.useForm();
	const [itemForm] = Form.useForm();
	const [mappingForm] = Form.useForm();
	const canManage = useGovernanceManageAccess();

	const syncQuery = (patch?: { keyword?: string; page?: number; size?: number }) => {
		const params = new URLSearchParams(searchParams);
		const nextKeyword = patch?.keyword ?? keyword;
		const nextPage = patch?.page ?? pageNum;
		const nextSize = patch?.size ?? pageSize;
		if (nextKeyword?.trim()) {
			params.set("keyword", nextKeyword.trim());
		} else {
			params.delete("keyword");
		}
		if (nextPage > 0) {
			params.set("page", String(nextPage));
		} else {
			params.delete("page");
		}
		if (nextSize !== 10) {
			params.set("size", String(nextSize));
		} else {
			params.delete("size");
		}
		setSearchParams(params, { replace: true });
	};

	const loadDirectories = useCallback(async () => {
		setLoading(true);
		try {
			const resp = (await listReferenceCodes({
				page: pageNum,
				size: pageSize,
				keyword: normalizeText(keyword) || undefined,
			})) as PagedPayload<ReferenceCodeDirectory>;
			setData(resp || null);
		} catch (err: any) {
			toast.error(err?.message || "加载码表失败");
		} finally {
			setLoading(false);
		}
	}, [keyword, pageNum, pageSize]);

	const loadOpsOverview = useCallback(async () => {
		setOpsOverviewLoading(true);
		try {
			const resp = (await getReferenceCodeImportOpsOverview({ hours: 168 })) as ReferenceCodeOpsOverview;
			setOpsOverview(resp || null);
		} catch (err: any) {
			toast.error((err?.message || "加载导入运维概览失败") + "，请稍后重试");
		} finally {
			setOpsOverviewLoading(false);
		}
	}, []);

	useEffect(() => {
		void loadDirectories();
	}, [loadDirectories]);

	useEffect(() => {
		void loadOpsOverview();
	}, [loadOpsOverview]);

	useEffect(() => {
		syncQuery();
	}, [keyword, pageNum, pageSize]);

	const openModal = (row?: ReferenceCodeDirectory) => {
		setEditing(row || null);
		form.resetFields();
		form.setFieldsValue({
			codeTypeId: row?.codeTypeId,
			codeTypeCode: row?.codeTypeCode,
			codeTypeName: row?.codeTypeName,
			stdLevel: row?.stdLevel,
			bizCatalog: row?.bizCatalog,
			dataType: row?.dataType,
			status: row?.status ?? 1,
			ownerDept: row?.ownerDept,
			version: row?.version,
		});
		setModalOpen(true);
	};

	const submit = async () => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		setSaving(true);
		try {
			const values = await form.validateFields(["codeTypeCode", "codeTypeName"]);
			const payload = {
				codeTypeId: normalizeText(form.getFieldValue("codeTypeId")) || undefined,
				codeTypeCode: normalizeText(values.codeTypeCode),
				codeTypeName: normalizeText(values.codeTypeName),
				stdLevel: normalizeText(form.getFieldValue("stdLevel")) || undefined,
				bizCatalog: normalizeText(form.getFieldValue("bizCatalog")) || undefined,
				dataType: normalizeText(form.getFieldValue("dataType")) || undefined,
				status: form.getFieldValue("status"),
				ownerDept: normalizeText(form.getFieldValue("ownerDept")) || undefined,
				version: normalizeText(form.getFieldValue("version")) || undefined,
			};
			if (editing?.codeTypeId) {
				await updateReferenceCode(editing.codeTypeId, payload);
				toast.success("码表已更新");
			} else {
				await createReferenceCode(payload);
				toast.success("码表已创建");
			}
			setModalOpen(false);
			setEditing(null);
			await loadDirectories();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	};

	const removeDirectory = (row: ReferenceCodeDirectory) => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!row?.codeTypeId) return;
		void (async () => {
			try {
				const refs = (await getReferenceCodeReferences(row.codeTypeId as string)) as AssetReferencePayload;
				const impactCount = Number(refs?.totalReferences || 0);
				if (impactCount > 0) {
					Modal.warning({
						title: `删除被拦截：存在 ${impactCount} 个引用对象`,
						content: (
							<List
								size="small"
								dataSource={(refs?.items || []).slice(0, 8)}
								renderItem={(item: AssetReferenceItem) => (
									<List.Item>
										<Text>
											{item.label || item.type}：{item.name || item.code || item.id}
										</Text>
									</List.Item>
								)}
							/>
						),
					});
					return;
				}
				Modal.confirm({
					title: "删除码表？",
					content: "删除后无法恢复。",
					okText: "删除",
					cancelText: "取消",
					onOk: async () => {
						try {
							await deleteReferenceCode(row.codeTypeId as string);
							toast.success("码表已删除");
							await loadDirectories();
						} catch (err: any) {
							toast.error(err?.message || "删除失败");
						}
					},
				});
			} catch (err: any) {
				toast.error(err?.message || "删除前检查失败");
			}
		})();
	};

	const openReferences = async (row: ReferenceCodeDirectory) => {
		if (!row?.codeTypeId) return;
		setReferenceOpen(true);
		setReferenceLoading(true);
		try {
			const resp = (await getReferenceCodeReferences(row.codeTypeId)) as AssetReferencePayload;
			setReferencePayload(resp || null);
		} catch (err: any) {
			setReferencePayload(null);
			toast.error(err?.message || "加载引用关系失败");
		} finally {
			setReferenceLoading(false);
		}
	};

	const openItems = async (row: ReferenceCodeDirectory) => {
		setActiveDirectory(row);
		setItemsOpen(true);
		await refreshItems(row);
	};

	const refreshItems = async (row?: ReferenceCodeDirectory) => {
		const dir = row || activeDirectory;
		if (!dir?.codeTypeId) return;
		setItemsLoading(true);
		try {
			const resp = (await listReferenceCodeItems(dir.codeTypeId)) as ReferenceCodeItem[];
			setItems(Array.isArray(resp) ? resp : []);
		} catch (err: any) {
			toast.error(err?.message || "加载码表项失败");
		} finally {
			setItemsLoading(false);
		}
	};

	const openStructuredImport = () => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		setStructuredRaw(STRUCTURED_TEMPLATE);
		setStructuredPolicy("STRICT");
		setStructuredPreview(null);
		setStructuredRunId(null);
		setStructuredOpen(true);
	};

	const doStructuredPreview = async () => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!activeDirectory?.codeTypeId) return;
		const rows = parseStructuredRows(structuredRaw);
		if (rows.length === 0) {
			toast.error("请至少填写一行导入数据");
			return;
		}
		setStructuredLoading(true);
		try {
			const resp = (await previewStructuredReferenceCodeImport(activeDirectory.codeTypeId, {
				conflictPolicy: structuredPolicy,
				rows,
			})) as StructuredImportPreview;
			setStructuredPreview(resp || null);
			toast.success(
				`预检完成：新增 ${resp?.createCount ?? 0}，更新 ${resp?.updateCount ?? 0}，冲突 ${resp?.conflictCount ?? 0}，错误 ${resp?.errorCount ?? 0}`
			);
		} catch (err: any) {
			toast.error(err?.message || "预检失败");
		} finally {
			setStructuredLoading(false);
		}
	};

	const doStructuredApply = async () => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!activeDirectory?.codeTypeId) return;
		const rows = parseStructuredRows(structuredRaw);
		if (rows.length === 0) {
			toast.error("请至少填写一行导入数据");
			return;
		}
		setStructuredLoading(true);
		try {
			const resp = (await applyStructuredReferenceCodeImport(activeDirectory.codeTypeId, {
				conflictPolicy: structuredPolicy,
				rows,
			})) as any;
			const runId = typeof resp?.runId === "string" ? resp.runId : null;
			setStructuredRunId(runId);
			toast.success(`执行完成：新增 ${resp?.created ?? 0}，更新 ${resp?.updated ?? 0}，跳过 ${resp?.skipped ?? 0}`);
			await refreshItems();
			await doStructuredPreview();
			await loadOpsOverview();
		} catch (err: any) {
			toast.error(err?.message || "执行导入失败");
		} finally {
			setStructuredLoading(false);
		}
	};

	const doStructuredRollback = async () => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!activeDirectory?.codeTypeId || !structuredRunId) return;
		setStructuredLoading(true);
		try {
			const resp = (await rollbackStructuredReferenceCodeImport(activeDirectory.codeTypeId, structuredRunId)) as any;
			toast.success(`回滚完成：恢复 ${resp?.restoredCount ?? 0} 条`);
			await refreshItems();
			setStructuredRunId(null);
			await doStructuredPreview();
			await loadOpsOverview();
		} catch (err: any) {
			toast.error(err?.message || "回滚失败");
		} finally {
			setStructuredLoading(false);
		}
	};

	const loadStructuredHistory = async (focusRunId?: string) => {
		if (!activeDirectory?.codeTypeId) return;
		setStructuredHistoryLoading(true);
		try {
			const rows = (await listStructuredReferenceCodeImportRuns(activeDirectory.codeTypeId)) as StructuredImportRunSummary[];
			const list = Array.isArray(rows) ? rows : [];
			setStructuredHistory(list);
			const targetRunId = focusRunId || list[0]?.runId;
			if (targetRunId) {
				await loadStructuredHistoryDetail(targetRunId);
			} else {
				setStructuredHistoryDetail(null);
			}
		} catch (err: any) {
			toast.error(err?.message || "加载导入历史失败");
		} finally {
			setStructuredHistoryLoading(false);
		}
	};

	const loadStructuredHistoryDetail = async (runId?: string) => {
		if (!activeDirectory?.codeTypeId || !runId) return;
		setStructuredHistoryDetailLoading(true);
		try {
			const detail = (await getStructuredReferenceCodeImportRun(activeDirectory.codeTypeId, runId)) as StructuredImportRunDetail;
			setStructuredHistoryDetail(detail || null);
		} catch (err: any) {
			toast.error(err?.message || "加载导入详情失败");
		} finally {
			setStructuredHistoryDetailLoading(false);
		}
	};

	const openStructuredHistory = async () => {
		if (!activeDirectory?.codeTypeId) return;
		setStructuredHistoryOpen(true);
		setStructuredHistoryDetail(null);
		await loadStructuredHistory(structuredRunId || undefined);
	};

	const rollbackHistoryRun = async (runId?: string) => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!activeDirectory?.codeTypeId || !runId) return;
		setStructuredHistoryDetailLoading(true);
		try {
			const resp = (await rollbackStructuredReferenceCodeImport(activeDirectory.codeTypeId, runId)) as any;
			toast.success(`回滚完成：恢复 ${resp?.restoredCount ?? 0} 条`);
			setStructuredRunId(null);
			await refreshItems();
			await loadStructuredHistory(runId);
			await loadOpsOverview();
		} catch (err: any) {
			toast.error(err?.message || "回滚失败");
		} finally {
			setStructuredHistoryDetailLoading(false);
		}
	};

	const openMappings = async (row: ReferenceCodeDirectory) => {
		setActiveDirectory(row);
		setMappingsOpen(true);
		await refreshItems(row);
		await refreshMappings(row);
	};

	const refreshMappings = async (row?: ReferenceCodeDirectory) => {
		const dir = row || activeDirectory;
		if (!dir?.codeTypeId) return;
		setMappingsLoading(true);
		try {
			const resp = (await listReferenceCodeMappings(dir.codeTypeId)) as ReferenceCodeMapping[];
			setMappings(Array.isArray(resp) ? resp : []);
		} catch (err: any) {
			toast.error(err?.message || "加载码表映射失败");
		} finally {
			setMappingsLoading(false);
		}
	};

	const openMappingModal = (row?: ReferenceCodeMapping) => {
		setMappingEditing(row || null);
		mappingForm.resetFields();
		mappingForm.setFieldsValue({
			sourceSys: row?.sourceSys,
			srcCode: row?.srcCode,
			stdCode: row?.stdCode,
		});
		setMappingModalOpen(true);
	};

	const submitMapping = async () => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!activeDirectory?.codeTypeId) return;
		setMappingSaving(true);
		try {
			const values = await mappingForm.validateFields(["sourceSys", "srcCode", "stdCode"]);
			const payload = {
				sourceSys: normalizeText(values.sourceSys),
				srcCode: normalizeText(values.srcCode),
				stdCode: normalizeText(values.stdCode),
			};
			if (mappingEditing?.mapId) {
				await updateReferenceCodeMapping(activeDirectory.codeTypeId, mappingEditing.mapId, payload);
				toast.success("映射已更新");
			} else {
				await createReferenceCodeMapping(activeDirectory.codeTypeId, payload);
				toast.success("映射已创建");
			}
			setMappingModalOpen(false);
			setMappingEditing(null);
			await refreshMappings();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setMappingSaving(false);
		}
	};

	const removeMapping = (row: ReferenceCodeMapping) => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!activeDirectory?.codeTypeId || !row?.mapId) return;
		Modal.confirm({
			title: "删除映射？",
			content: "删除后无法恢复。",
			okText: "删除",
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteReferenceCodeMapping(activeDirectory.codeTypeId!, row.mapId!);
					toast.success("映射已删除");
					await refreshMappings();
				} catch (err: any) {
					toast.error(err?.message || "删除失败");
				}
			},
		});
	};

	const openItemModal = (row?: ReferenceCodeItem) => {
		setItemEditing(row || null);
		itemForm.resetFields();
		itemForm.setFieldsValue({
			codeValue: row?.codeValue,
			codeName: row?.codeName,
			description: row?.description,
			sortNum: row?.sortNum,
			parentCode: row?.parentCode,
			isDefault: row?.isDefault,
		});
		setItemModalOpen(true);
	};

	const submitItem = async () => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!activeDirectory?.codeTypeId) return;
		setItemSaving(true);
		try {
			const values = await itemForm.validateFields(["codeValue", "codeName"]);
			const payload = {
				codeValue: normalizeText(values.codeValue),
				codeName: normalizeText(values.codeName),
				description: normalizeText(itemForm.getFieldValue("description")) || undefined,
				sortNum: itemForm.getFieldValue("sortNum") ?? undefined,
				parentCode: normalizeText(itemForm.getFieldValue("parentCode")) || undefined,
				isDefault: itemForm.getFieldValue("isDefault"),
			};
			if (itemEditing?.itemId) {
				await updateReferenceCodeItem(activeDirectory.codeTypeId, itemEditing.itemId, payload);
				toast.success("码表项已更新");
			} else {
				await createReferenceCodeItem(activeDirectory.codeTypeId, payload);
				toast.success("码表项已创建");
			}
			setItemModalOpen(false);
			setItemEditing(null);
			await refreshItems();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setItemSaving(false);
		}
	};

	const removeItem = (row: ReferenceCodeItem) => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!activeDirectory?.codeTypeId || !row?.itemId) return;
		Modal.confirm({
			title: "删除码表项？",
			content: "删除后无法恢复。",
			okText: "删除",
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteReferenceCodeItem(activeDirectory.codeTypeId!, row.itemId!);
					toast.success("码表项已删除");
					await refreshItems();
				} catch (err: any) {
					toast.error(err?.message || "删除失败");
				}
			},
		});
	};

	const syncSeeds = async () => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		setSeedSyncing(true);
		try {
			const resp = (await syncReferenceCodeSeeds()) as any;
			const summary = resp?.seedPath
				? `已生成 ${resp?.rows ?? 0} 行，路径：${resp.seedPath}`
				: "已更新 dbt Seeds";
			toast.success(summary);
		} catch (err: any) {
			toast.error(err?.message || "更新 dbt Seeds 失败");
		} finally {
			setSeedSyncing(false);
		}
	};

	const columns: ColumnsType<ReferenceCodeDirectory> = [
		{ title: "码表名称", dataIndex: "codeTypeName", render: (t) => <Text strong>{t}</Text> , sorter: (a, b) => (a.codeTypeName || "").localeCompare(b.codeTypeName || "") },
		{ title: "码表编码", dataIndex: "codeTypeCode", render: (c) => <Tag>{c}</Tag> , sorter: (a, b) => (a.codeTypeCode || "").localeCompare(b.codeTypeCode || "") },
		{ title: "业务分类", dataIndex: "bizCatalog", render: (t) => t || "-" },
		{ title: "数据类型", dataIndex: "dataType", render: (t) => t || "-" },
		{ title: "码值数量", dataIndex: "itemCount", render: (t) => t ?? 0 },
		{ title: "版本", dataIndex: "version", render: (t) => <Text type="secondary">{t || "-"}</Text> },
		{
			title: "状态",
			dataIndex: "status",
			render: (s: number) => {
				const meta = STATUS_LABELS[s] || { label: "未知", color: "default" };
				return <Tag color={meta.color}>{meta.label}</Tag>;
			},
		},
		{
			title: "操作",
			render: (_, row) => (
				<Space>
					<Button type="link" size="small" onClick={() => openItems(row)}>
						明细管理
					</Button>
					<Button type="link" size="small" onClick={() => openMappings(row)}>
						映射管理
					</Button>
					<Button type="link" size="small" onClick={() => openReferences(row)}>
						引用关系
					</Button>
					<Button type="link" size="small" onClick={() => openModal(row)} disabled={!canManage}>
						编辑
					</Button>
					<Button type="link" size="small" danger onClick={() => removeDirectory(row)} disabled={!canManage}>
						删除
					</Button>
				</Space>
			),
		},
	];

	const itemColumns: ColumnsType<ReferenceCodeItem> = useMemo(
		() => [
			{ title: "代码值", dataIndex: "codeValue", render: (t) => <Tag>{t}</Tag> , sorter: (a, b) => (a.codeValue || "").localeCompare(b.codeValue || "") },
			{ title: "名称", dataIndex: "codeName", render: (t) => <Text strong>{t}</Text> , sorter: (a, b) => (a.codeName || "").localeCompare(b.codeName || "") },
			{ title: "业务定义", dataIndex: "description", ellipsis: true, render: (t) => t || "-" },
			{ title: "排序", dataIndex: "sortNum", render: (t) => (t == null ? "-" : t) },
			{ title: "父级", dataIndex: "parentCode", render: (t) => t || "-" },
			{
				title: "默认",
				dataIndex: "isDefault",
				render: (t) => <Tag color={t ? "blue" : "default"}>{t ? "是" : "否"}</Tag>,
			},
			{
				title: "操作",
				render: (_, row) => (
					<Space>
						<Button type="link" size="small" onClick={() => openItemModal(row)} disabled={!canManage}>
							编辑
						</Button>
						<Button type="link" size="small" danger onClick={() => removeItem(row)} disabled={!canManage}>
							删除
						</Button>
					</Space>
				),
			},
		],
		[items]
	);

	const mappingColumns: ColumnsType<ReferenceCodeMapping> = useMemo(
		() => [
			{ title: "源系统", dataIndex: "sourceSys", render: (t) => <Tag color="blue">{t}</Tag> },
			{ title: "源代码", dataIndex: "srcCode", render: (t) => <Text className="font-mono text-xs">{t}</Text> },
			{ title: "标准码值", dataIndex: "stdCode", render: (t) => <Tag>{t}</Tag> },
			{
				title: "操作",
				render: (_, row) => (
					<Space>
						<Button type="link" size="small" onClick={() => openMappingModal(row)} disabled={!canManage}>
							编辑
						</Button>
						<Button type="link" size="small" danger onClick={() => removeMapping(row)} disabled={!canManage}>
							删除
						</Button>
					</Space>
				),
			},
		],
		[mappings]
	);

	const conflictColumns: ColumnsType<Record<string, any>> = [
		{ title: "码值", dataIndex: "codeValue", width: 160, render: (value) => value || "-" , sorter: (a, b) => (a.codeValue || "").localeCompare(b.codeValue || "") },
		{ title: "当前名称", dataIndex: "currentName", render: (value) => value || "-" },
		{ title: "导入名称", dataIndex: "incomingName", render: (value) => value || "-" },
		{ title: "原因", dataIndex: "reason", render: (value) => value || "-" },
	];

	const errorColumns: ColumnsType<Record<string, any>> = [
		{ title: "行号", dataIndex: "line", width: 100, render: (value) => value ?? "-" },
		{ title: "码值", dataIndex: "codeValue", width: 160, render: (value) => value || "-" , sorter: (a, b) => (a.codeValue || "").localeCompare(b.codeValue || "") },
		{ title: "原因", dataIndex: "reason", render: (value) => value || "-" },
	];

	const importRunColumns: ColumnsType<StructuredImportRunSummary> = [
		{ title: "批次ID", dataIndex: "runId", width: 260, render: (value) => <Text className="font-mono text-xs">{value || "-"}</Text> },
		{ title: "状态", dataIndex: "status", width: 120, render: (value) => <Tag>{value || "-"}</Tag> },
		{ title: "策略", dataIndex: "conflictPolicy", width: 100, render: (value) => value || "-" },
		{ title: "新增", dataIndex: "createCount", width: 80, render: (value) => value ?? 0 },
		{ title: "更新", dataIndex: "updateCount", width: 80, render: (value) => value ?? 0 },
		{ title: "冲突", dataIndex: "conflictCount", width: 80, render: (value) => value ?? 0 },
		{ title: "错误", dataIndex: "errorCount", width: 80, render: (value) => value ?? 0 },
		{ title: "创建时间", dataIndex: "createdDate", width: 170, render: (value) => formatDateTime(value) , sorter: (a, b) => { const ta = a.createdDate ? new Date(a.createdDate as any).getTime() : 0; const tb = b.createdDate ? new Date(b.createdDate as any).getTime() : 0; return ta - tb; } },
		{
			title: "操作",
			width: 180,
			render: (_, row) => (
				<Space>
					<Button type="link" size="small" onClick={() => void loadStructuredHistoryDetail(row.runId)}>
						查看
					</Button>
					<Button
						type="link"
						size="small"
						danger
						disabled={!canManage || !row.rollbackable}
						onClick={() => void rollbackHistoryRun(row.runId)}
					>
						回滚
					</Button>
				</Space>
			),
		},
	];

	const importDiffColumns: ColumnsType<Record<string, any>> = [
		{ title: "类型", dataIndex: "changeType", width: 100, render: (value) => <Tag>{value || "-"}</Tag> },
		{ title: "码值", dataIndex: "codeValue", width: 160, render: (value) => <Text className="font-mono text-xs">{value || "-"}</Text> , sorter: (a, b) => (a.codeValue || "").localeCompare(b.codeValue || "") },
		{
			title: "变更前",
			dataIndex: "before",
			render: (value) => (
				<Typography.Text ellipsis style={{ maxWidth: 260, display: "inline-block" }}>
					{value ? JSON.stringify(value) : "-"}
				</Typography.Text>
			),
		},
		{
			title: "变更后",
			dataIndex: "after",
			render: (value) => (
				<Typography.Text ellipsis style={{ maxWidth: 260, display: "inline-block" }}>
					{value ? JSON.stringify(value) : "-"}
				</Typography.Text>
			),
		},
	];

	const content = data?.content ?? [];
	return (
		<div className="space-y-4">
			<Card
				title="公共码表"
				extra={
					<Space>
						<Button
							onClick={syncSeeds}
							disabled={!canManage}
							loading={seedSyncing}
							data-testid="governance-reference-sync-seeds"
						>
							更新 dbt Seeds
						</Button>
						<Button type="primary" onClick={() => openModal()} disabled={!canManage}>
							+ 新增码表
						</Button>
					</Space>
				}
			>
				<div className="mb-3 flex flex-wrap items-center gap-2">
					<Input.Search
						placeholder="搜索码表..."
						style={{ width: 320 }}
						value={keyword}
						onChange={(e) => {
							setKeyword(e.target.value);
							setPageNum(0);
						}}
						onSearch={(value) => {
							setKeyword(value || "");
							setPageNum(0);
						}}
						allowClear
					/>
					<Button
						onClick={() => {
							setKeyword("");
							setPageNum(0);
						}}
					>
						重置
					</Button>
				</div>
			</Card>

			<Card
				title="导入运维概览"
				extra={
					<Button size="small" onClick={() => void loadOpsOverview()}>
						刷新概览
					</Button>
				}
			>
				<Card size="small" loading={opsOverviewLoading} bordered={false} bodyStyle={{ padding: 0 }}>
				<Space wrap size={12}>
					<Card size="small" title="统计窗口(h)" style={{ minWidth: 120 }}>
						<Text strong>{opsOverview?.windowHours ?? "-"}</Text>
					</Card>
					<Card size="small" title="覆盖码表数" style={{ minWidth: 120 }}>
						<Text strong>{opsOverview?.directoryCount ?? 0}</Text>
					</Card>
					<Card size="small" title="导入运行总数" style={{ minWidth: 140 }}>
						<Text strong>{opsOverview?.totalRuns ?? 0}</Text>
					</Card>
					<Card size="small" title="成功执行" style={{ minWidth: 120 }}>
						<Text strong>{opsOverview?.appliedRuns ?? 0}</Text>
					</Card>
					<Card size="small" title="已回滚" style={{ minWidth: 120 }}>
						<Text strong>{opsOverview?.rolledBackRuns ?? 0}</Text>
					</Card>
					<Card size="small" title="冲突总数" style={{ minWidth: 120 }}>
						<Text strong>{opsOverview?.conflictTotal ?? 0}</Text>
					</Card>
					<Card size="small" title="错误总数" style={{ minWidth: 120 }}>
						<Text strong>{opsOverview?.errorTotal ?? 0}</Text>
					</Card>
					<Card size="small" title="查询耗时(ms)" style={{ minWidth: 140 }}>
						<Text strong>{opsOverview?.queryCostMs ?? "-"}</Text>
					</Card>
				</Space>
				<Space style={{ marginTop: 12 }}>
					<Text type="secondary">失败分类 TopN：</Text>
					{(opsOverview?.failureTop || []).length > 0 ? (
						<Space wrap>
							{(opsOverview?.failureTop || []).map((item) => (
								<Tag key={`${item.category || "-"}-${item.count || 0}`}>{`${item.category || "-"}:${item.count || 0}`}</Tag>
							))}
						</Space>
					) : (
						<Text type="secondary">暂无失败分类</Text>
					)}
				</Space>
				</Card>
			</Card>

			<Card title="码表目录">
				{content.length === 0 && !loading ? (
					<EmptyState title="暂无码表" description="请先新增公共码表。" />
				) : (
					<CompactTable
						rowKey={(row) => row.codeTypeId || row.codeTypeCode || row.codeTypeName || Math.random().toString(36)}
						dataSource={content}
						columns={columns}
						loading={loading}
						pagination={{
							current: (data?.page ?? 0) + 1,
							pageSize: data?.size ?? pageSize,
							total: data?.total ?? 0,
							showSizeChanger: true,
							pageSizeOptions: [10, 20, 50, 100],
							showTotal: (total) => `共 ${total} 条`,
							onChange: (page, size) => {
								setPageNum(size !== pageSize ? 0 : page - 1);
								setPageSize(size);
							},
						}}
					/>
				)}
			</Card>

			<Modal
				open={referenceOpen}
				title={referencePayload?.targetName ? `引用关系 · ${referencePayload.targetName}` : "引用关系"}
				onCancel={() => setReferenceOpen(false)}
				footer={null}
				width={860}
			>
				{referenceLoading ? (
					<div className="py-6 text-center">
						<Text type="secondary">加载中...</Text>
					</div>
				) : Number(referencePayload?.totalReferences || 0) === 0 ? (
					<EmptyState title="暂无引用对象" description="当前码表尚未被其他标准资产引用。" />
				) : (
					<List
						size="small"
						dataSource={referencePayload?.items || []}
						renderItem={(item: AssetReferenceItem) => (
							<List.Item
								actions={[
									item.path ? (
										<Button key="jump" type="link" size="small" onClick={() => navigate(item.path as string)}>
											跳转
										</Button>
									) : null,
								]}
							>
								<List.Item.Meta
									title={`${item.label || item.type || "引用"} · ${item.name || item.code || item.id || "-"}`}
									description={item.reason || "-"}
								/>
							</List.Item>
						)}
					/>
				)}
			</Modal>

			<Modal
				open={modalOpen}
				title={editing ? "编辑码表" : "新增码表"}
				onCancel={() => setModalOpen(false)}
				onOk={submit}
				okText="保存"
				cancelText="取消"
				confirmLoading={saving}
				okButtonProps={{ disabled: !canManage }}
			>
				<Form form={form} layout="vertical">
					<Form.Item name="codeTypeId" label="码表ID">
						<Input placeholder="例如：SEX_001（可不填，默认等同于码表编码）" />
					</Form.Item>
					<Form.Item
						name="codeTypeCode"
						label="码表编码"
						rules={[{ required: true, message: "请输入码表编码" }]}
					>
						<Input placeholder="例如：GENDER_CODE" />
					</Form.Item>
					<Form.Item
						name="codeTypeName"
						label="码表名称"
						rules={[{ required: true, message: "请输入码表名称" }]}
					>
						<Input placeholder="例如：性别代码表" />
					</Form.Item>
					<Form.Item name="stdLevel" label="标准层级">
						<Input placeholder="例如：国家标准 (GB/T 2261.1)" />
					</Form.Item>
					<Form.Item name="bizCatalog" label="业务分类">
						<Input placeholder="例如：基础人口信息" />
					</Form.Item>
					<Form.Item name="dataType" label="数据类型">
						<Input placeholder="例如：String / Integer" />
					</Form.Item>
					<Form.Item name="status" label="状态">
						<Select
							options={[
								{ label: "草稿", value: 0 },
								{ label: "发布", value: 1 },
								{ label: "废弃", value: 2 },
							]}
						/>
					</Form.Item>
					<Form.Item name="ownerDept" label="管理部门">
						<Input placeholder="例如：数据管理部" />
					</Form.Item>
					<Form.Item name="version" label="版本号">
						<Input placeholder="例如：V1.2" />
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={itemsOpen}
				title={
					activeDirectory
						? `码表明细 · ${activeDirectory.codeTypeName || activeDirectory.codeTypeCode}`
						: "码表明细"
				}
				onCancel={() => setItemsOpen(false)}
				footer={null}
				width={900}
			>
				<Space className="mb-3">
					<Button type="primary" onClick={() => openItemModal()} disabled={!canManage}>
						+ 新增码值
					</Button>
					<Button onClick={openStructuredImport} disabled={!canManage}>
						结构化导入
					</Button>
					<Button onClick={() => refreshItems()}>刷新</Button>
				</Space>
				<CompactTable
					rowKey={(row) => row.itemId || row.codeValue || Math.random().toString(36)}
					dataSource={items}
					columns={itemColumns}
					loading={itemsLoading}
					pagination={false}
				/>
			</Modal>

			<Modal
				open={itemModalOpen}
				title={itemEditing ? "编辑码表项" : "新增码表项"}
				onCancel={() => setItemModalOpen(false)}
				onOk={submitItem}
				okText="保存"
				cancelText="取消"
				confirmLoading={itemSaving}
				okButtonProps={{ disabled: !canManage }}
			>
				<Form form={itemForm} layout="vertical">
					<Form.Item name="codeValue" label="标准代码" rules={[{ required: true, message: "请输入码值" }]}>
						<Input placeholder="例如：1" />
					</Form.Item>
					<Form.Item name="codeName" label="标准名称" rules={[{ required: true, message: "请输入名称" }]}>
						<Input placeholder="例如：男" />
					</Form.Item>
					<Form.Item name="description" label="业务定义">
						<Input.TextArea rows={2} placeholder="描述该值的业务含义" />
					</Form.Item>
					<Form.Item name="sortNum" label="排序号">
						<Input type="number" placeholder="例如：1" />
					</Form.Item>
					<Form.Item name="parentCode" label="父级代码">
						<Input placeholder="例如：0" />
					</Form.Item>
					<Form.Item name="isDefault" label="是否默认">
						<Select
							options={[
								{ label: "否", value: false },
								{ label: "是", value: true },
							]}
						/>
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={structuredOpen}
				title="结构化导入码值（预检 / 执行 / 回滚）"
				onCancel={() => setStructuredOpen(false)}
				footer={null}
				width={980}
			>
				<Space direction="vertical" className="w-full" size={12}>
					<Alert
						type="info"
						showIcon
						message="支持 CSV 多行格式（含表头）。建议先“预检”，确认冲突/错误后再执行。"
					/>
					<Space>
						<Select
							value={structuredPolicy}
							style={{ width: 180 }}
							onChange={setStructuredPolicy}
							options={[
								{ label: "STRICT（冲突阻断）", value: "STRICT" },
								{ label: "MERGE（冲突更新）", value: "MERGE" },
								{ label: "SKIP（冲突跳过）", value: "SKIP" },
							]}
						/>
						<Button loading={structuredLoading} onClick={doStructuredPreview} disabled={!canManage}>
							预检
						</Button>
						<Button type="primary" loading={structuredLoading} onClick={doStructuredApply} disabled={!canManage}>
							执行导入
						</Button>
						<Button danger loading={structuredLoading} disabled={!canManage || !structuredRunId} onClick={doStructuredRollback}>
							回滚最近执行
						</Button>
						<Button onClick={openStructuredHistory}>导入历史</Button>
					</Space>
					<Input.TextArea
						rows={8}
						value={structuredRaw}
						onChange={(e) => setStructuredRaw(e.target.value)}
						placeholder={`${STRUCTURED_TEMPLATE}\n1,男,男性,1,,true`}
					/>
					{structuredPreview ? (
						<Space direction="vertical" className="w-full" size={8}>
							<div className="grid grid-cols-2 gap-2 md:grid-cols-4">
								<Card size="small">总行数：{structuredPreview.total ?? 0}</Card>
								<Card size="small">有效：{structuredPreview.valid ?? 0}</Card>
								<Card size="small">新增：{structuredPreview.createCount ?? 0}</Card>
								<Card size="small">更新：{structuredPreview.updateCount ?? 0}</Card>
							</div>
							<div className="grid grid-cols-2 gap-2 md:grid-cols-4">
								<Card size="small">冲突：{structuredPreview.conflictCount ?? 0}</Card>
								<Card size="small">错误：{structuredPreview.errorCount ?? 0}</Card>
								<Card size="small">策略：{structuredPreview.conflictPolicy || "-"}</Card>
								<Card size="small">预检ID：{structuredPreview.runId || "-"}</Card>
							</div>
							<CompactTable
								size="small"
								title={() => "冲突明细"}
								rowKey={(row, idx) => `${row.codeValue || "c"}-${idx}`}
								columns={conflictColumns}
								dataSource={Array.isArray(structuredPreview.conflicts) ? structuredPreview.conflicts : []}
								pagination={{ defaultPageSize: 10 }}
							/>
							<CompactTable
								size="small"
								title={() => "错误明细"}
								rowKey={(row, idx) => `${row.codeValue || row.line || "e"}-${idx}`}
								columns={errorColumns}
								dataSource={Array.isArray(structuredPreview.errors) ? structuredPreview.errors : []}
								pagination={{ defaultPageSize: 10 }}
							/>
						</Space>
					) : null}
				</Space>
			</Modal>

			<Modal
				open={structuredHistoryOpen}
				title={
					activeDirectory
						? `结构化导入历史 · ${activeDirectory.codeTypeName || activeDirectory.codeTypeCode}`
						: "结构化导入历史"
				}
				onCancel={() => setStructuredHistoryOpen(false)}
				footer={null}
				width={1160}
			>
				<Space direction="vertical" className="w-full" size={12}>
					<Space>
						<Button size="small" onClick={() => void loadStructuredHistory()}>
							刷新历史
						</Button>
						<Button
							size="small"
							onClick={() => void loadStructuredHistoryDetail(structuredHistoryDetail?.runId)}
							disabled={!structuredHistoryDetail?.runId}
						>
							重试详情
						</Button>
					</Space>
					<CompactTable
						size="small"
						rowKey={(row) => row.runId || Math.random().toString(36)}
						loading={structuredHistoryLoading}
						columns={importRunColumns}
						dataSource={structuredHistory}
						pagination={{ defaultPageSize: 10 }}
					/>
					{structuredHistoryDetail ? (
						<>
							<Card size="small" title={`批次详情：${structuredHistoryDetail.runId || "-"}`} loading={structuredHistoryDetailLoading}>
								<Space wrap split={<span>|</span>}>
									<Text>状态：{structuredHistoryDetail.status || "-"}</Text>
									<Text>策略：{structuredHistoryDetail.conflictPolicy || "-"}</Text>
									<Text>摘要：{structuredHistoryDetail.summary || "-"}</Text>
									<Text>变更数：{structuredHistoryDetail.diffCount ?? 0}</Text>
									<Text>创建时间：{formatDateTime(structuredHistoryDetail.createdDate)}</Text>
								</Space>
							</Card>
							<CompactTable
								size="small"
								title={() => "变更明细"}
								rowKey={(row, idx) => `${row.codeValue || "diff"}-${idx}`}
								columns={importDiffColumns}
								dataSource={structuredHistoryDetail.diffRows || []}
								pagination={{ defaultPageSize: 10 }}
							/>
						</>
					) : (
						<EmptyState title="请选择批次" description="点击上方“查看”加载批次变更详情。" />
					)}
				</Space>
			</Modal>

			<Modal
				open={mappingsOpen}
				title={
					activeDirectory
						? `码表映射 · ${activeDirectory.codeTypeName || activeDirectory.codeTypeCode}`
						: "码表映射"
				}
				onCancel={() => setMappingsOpen(false)}
				footer={null}
				width={900}
			>
				<Space className="mb-3">
					<Button type="primary" onClick={() => openMappingModal()} disabled={!canManage}>
						+ 新增映射
					</Button>
					<Button onClick={() => refreshMappings()}>刷新</Button>
				</Space>
				<CompactTable
					rowKey={(row) => row.mapId || `${row.sourceSys}-${row.srcCode}` || Math.random().toString(36)}
					dataSource={mappings}
					columns={mappingColumns}
					loading={mappingsLoading}
					pagination={false}
				/>
			</Modal>

			<Modal
				open={mappingModalOpen}
				title={mappingEditing ? "编辑码表映射" : "新增码表映射"}
				onCancel={() => setMappingModalOpen(false)}
				onOk={submitMapping}
				okText="保存"
				cancelText="取消"
				confirmLoading={mappingSaving}
				okButtonProps={{ disabled: !canManage }}
			>
				<Form form={mappingForm} layout="vertical">
					<Form.Item name="sourceSys" label="源系统" rules={[{ required: true, message: "请输入源系统" }]}>
						<Input placeholder="例如：CRM_SYSTEM" />
					</Form.Item>
					<Form.Item name="srcCode" label="源系统原始值" rules={[{ required: true, message: "请输入源代码" }]}>
						<Input placeholder="例如：Male" />
					</Form.Item>
					<Form.Item name="stdCode" label="标准映射值" rules={[{ required: true, message: "请选择标准码值" }]}>
						<Select
							showSearch
							allowClear
							options={items.map((item) => ({
								label: `${item.codeName || item.codeValue}`.trim(),
								value: item.codeValue,
							}))}
							placeholder="选择标准码值"
						/>
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
