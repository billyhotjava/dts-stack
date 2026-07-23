import { Alert, Button, Form, Input, Select, Skeleton, Space, Tag, Typography } from "antd";
import { Database, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { getTechMetadataTables, listCatalogAssetsV2, listTablesByDataset } from "@/api/platformApi";
import dataSourcesService from "@/api/services/dataSourcesService";
import {
	getWarehousePlanSources,
	saveWarehousePlanSources,
	type WarehousePlanOnboardingMode,
	type WarehousePlanSourceBindingInput,
	type WarehousePlanSourceBindingView,
	type WarehousePlanSourceFreshness,
	type WarehousePlanSourceInventoryReadiness,
	type WarehousePlanSourceInventoryView,
	type WarehousePlanSourceType,
} from "@/api/warehousePlanApi";
import { createLatestRequestGuard } from "./warehousePlanCreateFlow";
import {
	type CatalogTableSummary,
	type ConnectionTableLocator,
	catalogSchemaOptions,
	catalogTableChoices,
	catalogTableNamespace,
	connectionTableRegistrationKey,
	mergeCandidateCatalogTableSource,
	mergeConfirmedConnectionTableSource,
	rebaseWarehousePlanSourceDrafts,
	selectableVerifiedConnections,
	sourceInventoryRequiresFurtherConfirmation,
	type VerifiedConnectionChoice,
	type WarehousePlanSourceDecisionDraft,
} from "./warehousePlanSourceRegistration";
import { resolveWarehousePlanConflictVersion, warehousePlanMutationErrorMessage } from "./warehousePlanViewModel";

const { Paragraph, Text, Title } = Typography;

type WarehousePlanSourceDraft = WarehousePlanSourceDecisionDraft;

type WarehousePlanSourcesFormValue = {
	bindings: WarehousePlanSourceDraft[];
};

type CatalogAssetOption = {
	value: string;
	label: string;
};

export type WarehousePlanSourcesSaveResult = {
	inventory: WarehousePlanSourceInventoryView;
	requiresFurtherConfirmation: boolean;
};

type WarehousePlanSourcesTabProps = {
	planId: string;
	onboardingMode: WarehousePlanOnboardingMode;
	conceptualDesignAllowed: boolean;
	editable: boolean;
	onOpenCatalog: () => void;
	onSaved: (result: WarehousePlanSourcesSaveResult) => Promise<void> | void;
	onSavingChange?: (saving: boolean) => void;
};

const SOURCE_TYPE_LABELS: Record<WarehousePlanSourceType, string> = {
	CONNECTION_TABLE: "连接中的表",
	CATALOG_TABLE: "资产目录表",
	EXCEL_FILE: "Excel 文件",
	DBT_NODE: "dbt 节点",
};

const SOURCE_FRESHNESS_LABELS: Record<WarehousePlanSourceFreshness, string> = {
	CURRENT: "CURRENT · 当前",
	STALE: "STALE · 已变化",
	UNKNOWN: "UNKNOWN · 无法核验",
};

const SOURCE_READINESS_LABELS: Record<WarehousePlanSourceInventoryReadiness, string> = {
	DRAFT: "待确认",
	READY: "来源已就绪",
	BLOCKED: "需要修复来源",
	NOT_REQUIRED_YET: "NOT_REQUIRED_YET · 当前阶段无需登记",
};

const SOURCE_ISSUE_MESSAGES: Record<string, string> = {
	SOURCE_INVENTORY_REQUIRED: "请至少登记一个当前可用的数据来源",
	SOURCE_BINDING_INVALID: "来源引用不完整，请从来源目录重新登记",
	SOURCE_CONFIRMATION_REQUIRED: "请确认该来源是否纳入计划",
	SOURCE_EXCLUSION_REASON_REQUIRED: "排除来源时必须填写原因",
	SOURCE_STALE: "来源已变化或删除，请核验后再继续",
	SOURCE_UNKNOWN: "当前无法核验来源，请检查权限或稍后重试",
};

const sourceIssueMessage = (code: string, fallback?: string | null): string => {
	if (SOURCE_ISSUE_MESSAGES[code]) return SOURCE_ISSUE_MESSAGES[code];
	if (fallback && /[\u3400-\u9fff]/.test(fallback)) return fallback;
	return "请检查当前来源设置";
};

const sourceDisplayName = (binding: WarehousePlanSourceBindingView): string => {
	if (binding.resolutionStatus === "FORBIDDEN") return "不可访问来源";
	if (binding.resolutionStatus === "MISSING") return "已删除来源";
	if (binding.resolutionStatus === "PROVIDER_ERROR") return "来源暂时无法核验";
	return binding.displayName || binding.sourceId || "未命名来源";
};

const freshnessColor = (freshness: WarehousePlanSourceFreshness): string => {
	if (freshness === "CURRENT") return "green";
	if (freshness === "STALE") return "orange";
	return "default";
};

const readinessColor = (readiness: WarehousePlanSourceInventoryReadiness): string => {
	if (readiness === "READY") return "green";
	if (readiness === "BLOCKED") return "red";
	if (readiness === "NOT_REQUIRED_YET") return "blue";
	return "gold";
};

const toDraftBindings = (bindings: WarehousePlanSourceBindingView[]): WarehousePlanSourceDraft[] =>
	bindings.map(({ confirmationStatus, exclusionReason }) => ({ confirmationStatus, exclusionReason }));

const sourceSaveResult = (inventory: WarehousePlanSourceInventoryView): WarehousePlanSourcesSaveResult => ({
	inventory,
	requiresFurtherConfirmation: sourceInventoryRequiresFurtherConfirmation(inventory.bindings),
});

export const buildWarehousePlanSourceSaveBindings = (
	bindings: WarehousePlanSourceBindingView[],
	drafts: WarehousePlanSourceDraft[],
): WarehousePlanSourceBindingInput[] => {
	return bindings.map((binding, index) => {
		const confirmationStatus = drafts[index]?.confirmationStatus || binding.confirmationStatus;
		return {
			bindingId: binding.bindingId,
			confirmationStatus,
			exclusionReason: confirmationStatus === "EXCLUDED" ? drafts[index]?.exclusionReason?.trim() || null : null,
		};
	});
};

export function WarehousePlanSourcesTab({
	planId,
	onboardingMode,
	conceptualDesignAllowed,
	editable,
	onOpenCatalog,
	onSaved,
	onSavingChange,
}: WarehousePlanSourcesTabProps) {
	const [form] = Form.useForm<WarehousePlanSourcesFormValue>();
	const [inventory, setInventory] = useState<WarehousePlanSourceInventoryView | null>(null);
	const [loading, setLoading] = useState(true);
	const [loadFailed, setLoadFailed] = useState(false);
	const [saving, setSaving] = useState(false);
	const [dirty, setDirtyValue] = useState(false);
	const [conflictVersion, setConflictVersion] = useState<number | null>(null);
	const [connectionOptions, setConnectionOptions] = useState<VerifiedConnectionChoice[]>([]);
	const [connectionCatalogTables, setConnectionCatalogTables] = useState<CatalogTableSummary[]>([]);
	const [connectionCatalogTotal, setConnectionCatalogTotal] = useState(0);
	const [connectionLoading, setConnectionLoading] = useState(false);
	const [connectionTableLoading, setConnectionTableLoading] = useState(false);
	const [connectionError, setConnectionError] = useState("");
	const [selectedConnectionId, setSelectedConnectionId] = useState<string | null>(null);
	const [selectedConnectionSchema, setSelectedConnectionSchema] = useState<string | null>(null);
	const [selectedConnectionTableId, setSelectedConnectionTableId] = useState<string | null>(null);
	const [selectedConnectionTable, setSelectedConnectionTable] = useState<CatalogTableSummary | null>(null);
	const [pendingConnectionTable, setPendingConnectionTable] = useState<ConnectionTableLocator | null>(null);
	const [catalogDatasetOptions, setCatalogDatasetOptions] = useState<CatalogAssetOption[]>([]);
	const [catalogOptions, setCatalogOptions] = useState<CatalogAssetOption[]>([]);
	const [catalogDatasetLoading, setCatalogDatasetLoading] = useState(false);
	const [catalogTableLoading, setCatalogTableLoading] = useState(false);
	const [selectedCatalogDatasetId, setSelectedCatalogDatasetId] = useState<string | null>(null);
	const [selectedAssetId, setSelectedAssetId] = useState<string | null>(null);
	const [pendingCatalogAssetId, setPendingCatalogAssetId] = useState<string | null>(null);
	const dirtyRef = useRef(false);
	const connectionSearchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
	const loadGuard = useMemo(() => createLatestRequestGuard(), []);
	const connectionLoadGuard = useMemo(() => createLatestRequestGuard(), []);
	const connectionTableLoadGuard = useMemo(() => createLatestRequestGuard(), []);
	const catalogDatasetLoadGuard = useMemo(() => createLatestRequestGuard(), []);
	const catalogTableLoadGuard = useMemo(() => createLatestRequestGuard(), []);
	const mutationGuard = useMemo(() => createLatestRequestGuard(), []);

	const setDirty = useCallback((value: boolean) => {
		dirtyRef.current = value;
		setDirtyValue(value);
	}, []);
	const setSavingState = useCallback(
		(value: boolean) => {
			setSaving(value);
			onSavingChange?.(value);
		},
		[onSavingChange],
	);

	useEffect(() => {
		return () => onSavingChange?.(false);
	}, [onSavingChange]);

	const loadSources = useCallback(
		async (replaceDraft = false) => {
			const isCurrent = loadGuard.begin();
			setLoading(true);
			setLoadFailed(false);
			try {
				const result = await getWarehousePlanSources(planId);
				if (!isCurrent()) return;
				setInventory(result);
				if (replaceDraft || !dirtyRef.current) {
					form.setFieldsValue({ bindings: toDraftBindings(result.bindings) });
					setDirty(false);
					setConflictVersion(null);
				}
			} catch {
				if (isCurrent()) setLoadFailed(true);
			} finally {
				if (isCurrent()) setLoading(false);
			}
		},
		[form, loadGuard, planId, setDirty],
	);
	const registeredCatalogAssetIds = useMemo(
		() =>
			new Set(
				(inventory?.bindings || [])
					.filter((binding) => binding.sourceType === "CATALOG_TABLE")
					.map((binding) => binding.locator?.assetId)
					.filter((assetId): assetId is string => Boolean(assetId)),
			),
		[inventory],
	);
	const registeredConnectionTableValues = useMemo(() => {
		const values = new Set<string>();
		for (const binding of inventory?.bindings || []) {
			if (binding.sourceType !== "CONNECTION_TABLE" || binding.locator?.connectionId !== selectedConnectionId) continue;
			const namespace = binding.locator.namespace || "";
			const objectName = binding.locator.objectName || "";
			if (namespace && objectName) values.add(connectionTableRegistrationKey(namespace, objectName));
		}
		return values;
	}, [inventory, selectedConnectionId]);
	const connectionSchemaOptions = useMemo(
		() => catalogSchemaOptions(connectionCatalogTables),
		[connectionCatalogTables],
	);
	const connectionTableOptions = useMemo(
		() => catalogTableChoices(connectionCatalogTables, registeredConnectionTableValues, selectedConnectionSchema || ""),
		[connectionCatalogTables, registeredConnectionTableValues, selectedConnectionSchema],
	);
	const loadVerifiedConnections = useCallback(async () => {
		const isCurrent = connectionLoadGuard.begin();
		setConnectionLoading(true);
		setConnectionError("");
		try {
			const result = await dataSourcesService.list();
			if (!isCurrent()) return;
			const options = selectableVerifiedConnections(Array.isArray(result) ? result : []);
			setConnectionOptions(options);
			if (options.length === 0) {
				setConnectionError("尚未找到已验证的 JDBC 连接，请先在数据源连接中完成连接测试");
			}
		} catch {
			if (!isCurrent()) return;
			setConnectionOptions([]);
			setConnectionError("已验证连接暂时无法加载，请稍后重试");
		} finally {
			if (isCurrent()) setConnectionLoading(false);
		}
	}, [connectionLoadGuard]);
	const loadConnectionTables = useCallback(
		async (connectionId: string, keyword = "") => {
			const isCurrent = connectionTableLoadGuard.begin();
			setConnectionTableLoading(true);
			setConnectionError("");
			try {
				const result = await getTechMetadataTables({
					sourceId: connectionId,
					size: 200,
					keyword: keyword.trim() || undefined,
				});
				if (!isCurrent()) return;
				const items = Array.isArray(result.items) ? result.items : [];
				setConnectionCatalogTables(items);
				setConnectionCatalogTotal(typeof result.total === "number" ? result.total : items.length);
				if (catalogSchemaOptions(items).length === 0) {
					setConnectionError(
						keyword.trim()
							? "没有找到匹配的已同步表，请换一个 Schema 或表名"
							: "该连接尚未同步出可用表，请先管理或同步元数据后重试",
					);
				}
			} catch {
				if (!isCurrent()) return;
				setConnectionCatalogTables([]);
				setConnectionCatalogTotal(0);
				setConnectionError("该连接的目录表暂时无法加载，请检查权限或稍后重试");
			} finally {
				if (isCurrent()) setConnectionTableLoading(false);
			}
		},
		[connectionTableLoadGuard],
	);
	const scheduleConnectionTableSearch = useCallback(
		(keyword: string) => {
			if (!selectedConnectionId) return;
			if (connectionSearchTimerRef.current) clearTimeout(connectionSearchTimerRef.current);
			connectionSearchTimerRef.current = setTimeout(() => {
				connectionSearchTimerRef.current = null;
				void loadConnectionTables(selectedConnectionId, keyword);
			}, 300);
		},
		[loadConnectionTables, selectedConnectionId],
	);
	const loadCatalogDatasets = useCallback(async () => {
		const isCurrent = catalogDatasetLoadGuard.begin();
		setCatalogDatasetLoading(true);
		try {
			const result: any = await listCatalogAssetsV2({ page: 0, size: 200 });
			if (!isCurrent()) return;
			const content = Array.isArray(result?.content)
				? result.content
				: Array.isArray(result?.data?.content)
					? result.data.content
					: [];
			const visibleDatasets = new Map<string, CatalogAssetOption>();
			for (const item of content) {
				const value = String(item?.legacyDatasetId || "");
				if (!value || visibleDatasets.has(value)) continue;
				const label = String(item?.displayName || item?.fqn || item?.table || value);
				visibleDatasets.set(value, { value, label });
			}
			setCatalogDatasetOptions(Array.from(visibleDatasets.values()));
		} catch {
			if (isCurrent()) {
				setCatalogDatasetOptions([]);
				toast.error("数据集目录加载失败，请稍后重试");
			}
		} finally {
			if (isCurrent()) setCatalogDatasetLoading(false);
		}
	}, [catalogDatasetLoadGuard]);
	const loadCatalogTables = useCallback(
		async (datasetId: string) => {
			const isCurrent = catalogTableLoadGuard.begin();
			setCatalogTableLoading(true);
			try {
				const result: any = await listTablesByDataset(datasetId);
				if (!isCurrent()) return;
				const content = Array.isArray(result?.content)
					? result.content
					: Array.isArray(result?.data?.content)
						? result.data.content
						: [];
				setCatalogOptions(
					content
						.map((item: any) => {
							const value = String(item?.id || "");
							const label = String(item?.name || item?.displayName || value);
							return { value, label };
						})
						.filter((option: CatalogAssetOption) => option.value && !registeredCatalogAssetIds.has(option.value)),
				);
			} catch {
				if (isCurrent()) {
					setCatalogOptions([]);
					toast.error("数据表目录加载失败，请稍后重试");
				}
			} finally {
				if (isCurrent()) setCatalogTableLoading(false);
			}
		},
		[catalogTableLoadGuard, registeredCatalogAssetIds],
	);

	useEffect(() => {
		mutationGuard.invalidate();
		if (connectionSearchTimerRef.current) {
			clearTimeout(connectionSearchTimerRef.current);
			connectionSearchTimerRef.current = null;
		}
		setInventory(null);
		setConflictVersion(null);
		setConnectionOptions([]);
		setConnectionCatalogTables([]);
		setConnectionCatalogTotal(0);
		setConnectionError("");
		setSelectedConnectionId(null);
		setSelectedConnectionSchema(null);
		setSelectedConnectionTableId(null);
		setSelectedConnectionTable(null);
		setPendingConnectionTable(null);
		setSelectedCatalogDatasetId(null);
		setSelectedAssetId(null);
		setPendingCatalogAssetId(null);
		setCatalogDatasetOptions([]);
		setCatalogOptions([]);
		setCatalogDatasetLoading(false);
		setCatalogTableLoading(false);
		setConnectionLoading(false);
		setConnectionTableLoading(false);
		setSavingState(false);
		setDirty(false);
		form.resetFields();
		void loadSources(true);
		void loadVerifiedConnections();
		return () => {
			if (connectionSearchTimerRef.current) {
				clearTimeout(connectionSearchTimerRef.current);
				connectionSearchTimerRef.current = null;
			}
			loadGuard.invalidate();
			connectionLoadGuard.invalidate();
			connectionTableLoadGuard.invalidate();
			catalogDatasetLoadGuard.invalidate();
			catalogTableLoadGuard.invalidate();
			mutationGuard.invalidate();
		};
	}, [
		catalogDatasetLoadGuard,
		catalogTableLoadGuard,
		connectionLoadGuard,
		connectionTableLoadGuard,
		form,
		loadGuard,
		loadSources,
		loadVerifiedConnections,
		mutationGuard,
		setDirty,
		setSavingState,
	]);

	const saveSources = async () => {
		if (!inventory) return;
		const isCurrent = mutationGuard.begin();
		const values = await form.validateFields().catch(() => null);
		if (!isCurrent()) return;
		if (!values) return;
		setSavingState(true);
		try {
			const savedInventory = await saveWarehousePlanSources(
				planId,
				inventory.version,
				buildWarehousePlanSourceSaveBindings(inventory.bindings, values.bindings || []),
			);
			if (!isCurrent()) return;
			setInventory(savedInventory);
			form.setFieldsValue({ bindings: toDraftBindings(savedInventory.bindings) });
			setDirty(false);
			setConflictVersion(null);
			setPendingCatalogAssetId(null);
			setPendingConnectionTable(null);
			toast.success("来源盘点已保存");
			const refreshResults = await Promise.allSettled([
				loadSources(true),
				Promise.resolve(onSaved(sourceSaveResult(savedInventory))),
			]);
			if (!isCurrent()) return;
			if (refreshResults.some((result) => result.status === "rejected")) {
				toast.warning("来源已保存，阶段状态刷新失败，可稍后重新加载");
			}
		} catch (error) {
			if (!isCurrent()) return;
			const currentVersion = resolveWarehousePlanConflictVersion(error);
			if (currentVersion != null) {
				setConflictVersion(currentVersion);
			} else {
				toast.error(warehousePlanMutationErrorMessage(error, "来源盘点保存失败"));
			}
		} finally {
			if (isCurrent()) setSavingState(false);
		}
	};

	const registerCatalogAsset = async () => {
		if (!inventory) return;
		const assetId = selectedAssetId;
		if (!assetId) {
			toast.error("请先选择目录资产");
			return;
		}
		const isCurrent = mutationGuard.begin();
		const values = inventory.bindings.length ? await form.validateFields().catch(() => null) : { bindings: [] };
		if (!isCurrent()) return;
		if (!values) return;
		setSavingState(true);
		try {
			const bindings = mergeCandidateCatalogTableSource(
				buildWarehousePlanSourceSaveBindings(inventory.bindings, values.bindings || []),
				inventory.bindings,
				assetId,
			);
			const savedInventory = await saveWarehousePlanSources(planId, inventory.version, bindings);
			if (!isCurrent()) return;
			setInventory(savedInventory);
			form.setFieldsValue({ bindings: toDraftBindings(savedInventory.bindings) });
			setDirty(false);
			setConflictVersion(null);
			setPendingCatalogAssetId(null);
			setSelectedAssetId(null);
			setCatalogOptions((options) => options.filter((option) => option.value !== assetId));
			toast.success("目录资产已登记，请将盘点结论设为“确认纳入”并保存");
			const refreshResults = await Promise.allSettled([
				loadSources(true),
				Promise.resolve(onSaved(sourceSaveResult(savedInventory))),
			]);
			if (!isCurrent()) return;
			if (refreshResults.some((result) => result.status === "rejected")) {
				toast.warning("资产已登记，阶段状态刷新失败，可稍后重新加载");
			}
		} catch (error) {
			if (!isCurrent()) return;
			const currentVersion = resolveWarehousePlanConflictVersion(error);
			if (currentVersion != null) {
				setPendingCatalogAssetId(assetId);
				setPendingConnectionTable(null);
				setConflictVersion(currentVersion);
			} else {
				toast.error(warehousePlanMutationErrorMessage(error, "目录资产登记失败"));
			}
		} finally {
			if (isCurrent()) setSavingState(false);
		}
	};

	const registerConnectionTable = async () => {
		if (!inventory) return;
		const locator = {
			connectionId: selectedConnectionId || "",
			namespace: selectedConnectionTable
				? catalogTableNamespace(selectedConnectionTable)
				: selectedConnectionSchema || "",
			objectName: String(selectedConnectionTable?.name || ""),
		};
		if (!locator.connectionId || !locator.namespace || !locator.objectName) {
			toast.error("请依次选择已验证连接、Schema 和具体表");
			return;
		}
		const isCurrent = mutationGuard.begin();
		const values = inventory.bindings.length ? await form.validateFields().catch(() => null) : { bindings: [] };
		if (!isCurrent()) return;
		if (!values) return;
		setSavingState(true);
		try {
			const existing = buildWarehousePlanSourceSaveBindings(inventory.bindings, values.bindings || []);
			const bindings = mergeConfirmedConnectionTableSource(existing, inventory.bindings, locator);
			const savedInventory = await saveWarehousePlanSources(planId, inventory.version, bindings);
			if (!isCurrent()) return;
			setInventory(savedInventory);
			form.setFieldsValue({ bindings: toDraftBindings(savedInventory.bindings) });
			setDirty(false);
			setConflictVersion(null);
			setPendingCatalogAssetId(null);
			setPendingConnectionTable(null);
			setSelectedConnectionTableId(null);
			setSelectedConnectionTable(null);
			toast.success(`${locator.namespace}.${locator.objectName} 已加入当前规划并确认`);
			const refreshResults = await Promise.allSettled([
				loadSources(true),
				Promise.resolve(onSaved(sourceSaveResult(savedInventory))),
			]);
			if (!isCurrent()) return;
			if (refreshResults.some((result) => result.status === "rejected")) {
				toast.warning("来源已确认，阶段状态刷新失败，可稍后重新加载");
			}
		} catch (error) {
			if (!isCurrent()) return;
			const currentVersion = resolveWarehousePlanConflictVersion(error);
			if (currentVersion != null) {
				setPendingCatalogAssetId(null);
				setPendingConnectionTable(locator);
				setConflictVersion(currentVersion);
			} else {
				toast.error(warehousePlanMutationErrorMessage(error, "具体表加入规划失败"));
			}
		} finally {
			if (isCurrent()) setSavingState(false);
		}
	};

	const retryAfterConflict = async () => {
		if (!inventory || conflictVersion == null) return;
		const isCurrent = mutationGuard.begin();
		const values = inventory.bindings.length ? await form.validateFields().catch(() => null) : { bindings: [] };
		if (!isCurrent()) return;
		if (!values) return;
		const originalBindings = inventory.bindings;
		const localDrafts = values.bindings || [];
		const pendingConnection = pendingConnectionTable;
		const pendingCatalog = pendingCatalogAssetId;
		setSavingState(true);
		try {
			const latestInventory = await getWarehousePlanSources(planId);
			if (!isCurrent()) return;
			const rebasedDrafts = rebaseWarehousePlanSourceDrafts(originalBindings, localDrafts, latestInventory.bindings);
			let bindings = buildWarehousePlanSourceSaveBindings(latestInventory.bindings, rebasedDrafts);
			if (pendingConnection) {
				bindings = mergeConfirmedConnectionTableSource(bindings, latestInventory.bindings, pendingConnection);
			} else if (pendingCatalog) {
				bindings = mergeCandidateCatalogTableSource(bindings, latestInventory.bindings, pendingCatalog);
			}
			const savedInventory = await saveWarehousePlanSources(planId, latestInventory.version, bindings);
			if (!isCurrent()) return;
			setInventory(savedInventory);
			form.setFieldsValue({ bindings: toDraftBindings(savedInventory.bindings) });
			setDirty(false);
			setConflictVersion(null);
			setPendingCatalogAssetId(null);
			setPendingConnectionTable(null);
			if (pendingConnection) {
				setSelectedConnectionTableId(null);
				setSelectedConnectionTable(null);
			}
			if (pendingCatalog) setSelectedAssetId(null);
			toast.success("已加载最新版并合并保存来源盘点");
			const refreshResults = await Promise.allSettled([
				loadSources(true),
				Promise.resolve(onSaved(sourceSaveResult(savedInventory))),
			]);
			if (!isCurrent()) return;
			if (refreshResults.some((result) => result.status === "rejected")) {
				toast.warning("来源已保存，阶段状态刷新失败，可稍后重新加载");
			}
		} catch (error) {
			if (!isCurrent()) return;
			const currentVersion = resolveWarehousePlanConflictVersion(error);
			if (currentVersion != null) {
				setConflictVersion(currentVersion);
				toast.warning("来源盘点再次发生并发更新，草稿仍已保留，请再次确认合并");
			} else {
				toast.error(warehousePlanMutationErrorMessage(error, "来源盘点合并保存失败"));
			}
		} finally {
			if (isCurrent()) setSavingState(false);
		}
	};

	if (loading && !inventory) {
		return <Skeleton active paragraph={{ rows: 5 }} />;
	}
	if (loadFailed && !inventory) {
		return (
			<div data-testid="warehouse-plan-sources-tab">
				<Alert
					type="warning"
					showIcon
					message="来源盘点暂时不可用"
					description="当前无法判断是否已有来源，请重新加载，系统不会把失败状态当成空清单。"
					action={<Button onClick={() => void loadSources(true)}>重新加载来源</Button>}
				/>
			</div>
		);
	}

	return (
		<div className="max-w-5xl space-y-4" data-testid="warehouse-plan-sources-tab">
			<div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-slate-50 px-4 py-3">
				<div>
					<div className="font-medium">数据从哪里来</div>
					<Text type="secondary">
						{onboardingMode === "ASSET_FIRST"
							? "核对已登记的数据来源，并明确确认或排除。"
							: conceptualDesignAllowed
								? "当前策略允许先完成概念设计；生成或实现模型前仍需补齐来源。"
								: "请先登记来源，或在数仓分层中显式允许先做概念设计。"}
					</Text>
				</div>
				<Tag color={inventory ? readinessColor(inventory.readiness) : "default"}>
					{inventory ? SOURCE_READINESS_LABELS[inventory.readiness] : "状态未知"}
				</Tag>
			</div>

			{loadFailed ? (
				<Alert
					type="warning"
					showIcon
					message="来源盘点暂时不可用"
					description="已保留的草稿不会被覆盖；重新加载后以服务端版本为准。"
					action={<Button onClick={() => void loadSources(true)}>重新加载来源</Button>}
				/>
			) : null}

			{conflictVersion != null ? (
				<Alert
					type="warning"
					showIcon
					message={`来源盘点已更新到版本 ${conflictVersion}`}
					description="当前草稿已保留，系统没有自动覆盖服务端内容。"
					action={
						<Space wrap>
							<Button
								onClick={() => {
									setPendingCatalogAssetId(null);
									setPendingConnectionTable(null);
									setDirty(false);
									void loadSources(true);
								}}
							>
								重新加载服务端最新版
							</Button>
							<Button type="primary" loading={saving} onClick={() => void retryAfterConflict()}>
								加载最新版、合并并重试
							</Button>
						</Space>
					}
				/>
			) : null}

			{inventory?.issues.map((issue) => (
				<Alert
					key={`${issue.code}-${issue.field || ""}`}
					type="warning"
					showIcon
					message={sourceIssueMessage(issue.code, issue.message)}
				/>
			))}

			{inventory?.readiness === "NOT_REQUIRED_YET" ? (
				<Alert
					type="info"
					showIcon
					message={conceptualDesignAllowed ? "当前策略允许先做概念设计" : "当前尚未登记来源"}
					description={
						conceptualDesignAllowed
							? "这不是完成状态；进入模型生成或实现前仍需登记可核验来源。"
							: "请先登记来源，或在数仓分层中开启“来源未齐时允许概念设计”。"
					}
				/>
			) : null}

			{inventory?.bindings.length ? (
				<Form
					form={form}
					layout="vertical"
					disabled={saving || !editable}
					onValuesChange={() => setDirty(true)}
					onFinish={() => void saveSources()}
				>
					<Form.List name="bindings">
						{(fields) => (
							<div className="space-y-3">
								{fields.map((field) => {
									const binding = inventory.bindings[field.name];
									if (!binding) return null;
									return (
										<div key={binding.bindingId} className="rounded-xl border border-slate-200 p-4">
											<div className="flex flex-wrap items-start justify-between gap-3">
												<div>
													<div className="font-medium text-slate-900">{sourceDisplayName(binding)}</div>
													<div className="mt-1 text-xs text-slate-500">
														{SOURCE_TYPE_LABELS[binding.sourceType]} · 系统自动关联
													</div>
												</div>
												<Tag color={freshnessColor(binding.freshness)}>
													{SOURCE_FRESHNESS_LABELS[binding.freshness]}
												</Tag>
											</div>
											<div className="mt-3 grid gap-3 md:grid-cols-[220px_1fr]">
												<Form.Item
													{...field}
													name={[field.name, "confirmationStatus"]}
													label="盘点结论"
													rules={[{ required: true, message: "请选择确认或排除" }]}
													className="mb-0"
												>
													<Select
														options={[
															{ value: "CANDIDATE", label: "待确认" },
															{ value: "CONFIRMED", label: "确认纳入" },
															{ value: "EXCLUDED", label: "排除" },
														]}
													/>
												</Form.Item>
												<Form.Item
													{...field}
													name={[field.name, "exclusionReason"]}
													label="排除原因（仅排除时必填）"
													dependencies={[["bindings", field.name, "confirmationStatus"]]}
													rules={[
														({ getFieldValue }) => ({
															validator: async (_, value) => {
																const status = getFieldValue(["bindings", field.name, "confirmationStatus"]);
																if (status === "EXCLUDED" && !String(value || "").trim()) {
																	throw new Error("请填写排除原因");
																}
															},
														}),
													]}
													className="mb-0"
												>
													<Input maxLength={500} placeholder="说明为什么不纳入本计划" />
												</Form.Item>
											</div>
											<div className="mt-3 flex flex-wrap gap-x-5 gap-y-1 text-xs text-slate-500">
												<span>解析版本：{binding.resolvedVersion || "暂不可用"}</span>
												<span>
													最近核验：
													{binding.lastValidatedAt ? new Date(binding.lastValidatedAt).toLocaleString() : "暂无"}
												</span>
											</div>
										</div>
									);
								})}
							</div>
						)}
					</Form.List>
					<div className="mt-5 flex flex-wrap items-center justify-end gap-3">
						<div className="flex items-center gap-3">
							{inventory ? (
								<Text type="secondary">
									版本 {inventory.version} · {inventory.etag}
								</Text>
							) : null}
							<Button
								type="primary"
								htmlType="submit"
								loading={saving}
								disabled={!dirty || conflictVersion != null || !editable}
							>
								保存来源盘点
							</Button>
						</div>
					</div>
				</Form>
			) : (
				<div className="rounded-xl border border-dashed border-slate-300 p-5">
					<Title level={5}>尚未登记来源</Title>
					<Paragraph type="secondary">
						连接测试只验证网络和凭据，不会自动把全部表加入规划。请在下方选择已验证连接、Schema
						和具体表；系统将自动关联来源标识和版本。
					</Paragraph>
				</div>
			)}

			{inventory && editable ? (
				<div
					className="rounded-xl border border-blue-200 bg-blue-50/40 p-4"
					data-testid="verified-connection-source-registration"
				>
					<div className="mb-3 flex flex-wrap items-start justify-between gap-2">
						<div>
							<div className="font-medium">从已验证连接加入具体表</div>
							<Text type="secondary">
								这里只显示正式目录中已同步且当前账号可读的表；点击加入即明确确认纳入当前规划。
							</Text>
						</div>
						<Typography.Link onClick={onOpenCatalog}>管理或同步元数据</Typography.Link>
					</div>
					<div className="grid gap-2 lg:grid-cols-[minmax(170px,1fr)_minmax(140px,0.7fr)_minmax(200px,1.2fr)_auto]">
						<Select
							showSearch
							optionFilterProp="label"
							placeholder="1. 选择已验证连接"
							value={selectedConnectionId}
							options={connectionOptions}
							loading={connectionLoading}
							notFoundContent={connectionLoading ? "正在加载…" : "暂无已验证 JDBC 连接"}
							onDropdownVisibleChange={(open) => {
								if (open && connectionOptions.length === 0) void loadVerifiedConnections();
							}}
							onChange={(connectionId) => {
								if (connectionSearchTimerRef.current) {
									clearTimeout(connectionSearchTimerRef.current);
									connectionSearchTimerRef.current = null;
								}
								setSelectedConnectionId(connectionId);
								setSelectedConnectionSchema(null);
								setSelectedConnectionTableId(null);
								setSelectedConnectionTable(null);
								setConnectionCatalogTables([]);
								setConnectionCatalogTotal(0);
								void loadConnectionTables(connectionId);
							}}
						/>
						<Select
							showSearch
							filterOption={false}
							placeholder="2. 选择 Schema"
							disabled={!selectedConnectionId || connectionTableLoading}
							value={selectedConnectionSchema}
							options={connectionSchemaOptions}
							loading={connectionTableLoading}
							notFoundContent={connectionTableLoading ? "正在读取目录…" : "暂无已同步 Schema"}
							onChange={(namespace) => {
								setSelectedConnectionSchema(namespace);
								setSelectedConnectionTableId(null);
								setSelectedConnectionTable(null);
							}}
							onSearch={scheduleConnectionTableSearch}
						/>
						<Select
							showSearch
							filterOption={false}
							placeholder="3. 选择具体表"
							disabled={!selectedConnectionSchema}
							value={selectedConnectionTableId}
							options={connectionTableOptions}
							notFoundContent={selectedConnectionSchema ? "该 Schema 暂无可加入表" : "请先选择 Schema"}
							onChange={(tableId) => {
								setSelectedConnectionTableId(tableId);
								setSelectedConnectionTable(
									connectionCatalogTables.find((table) => String(table.id || "") === tableId) || null,
								);
							}}
							onSearch={scheduleConnectionTableSearch}
						/>
						<Button
							type="primary"
							icon={<Database size={16} />}
							loading={saving}
							disabled={!selectedConnectionTableId || conflictVersion != null}
							onClick={() => void registerConnectionTable()}
						>
							4. 加入当前规划并确认
						</Button>
					</div>
					{connectionCatalogTotal > connectionCatalogTables.length ? (
						<Text className="mt-2 block text-xs" type="secondary">
							当前显示 {connectionCatalogTables.length} / {connectionCatalogTotal} 条，请输入 Schema 或表名继续查找
						</Text>
					) : null}
					{connectionError ? (
						<Alert
							className="mt-3"
							type="info"
							showIcon
							message={connectionError}
							action={
								selectedConnectionId ? (
									<Button size="small" onClick={() => void loadConnectionTables(selectedConnectionId)}>
										重新读取目录
									</Button>
								) : (
									<Button size="small" onClick={() => void loadVerifiedConnections()}>
										重新加载连接
									</Button>
								)
							}
						/>
					) : null}
				</div>
			) : null}

			{inventory && editable ? (
				<div className="rounded-xl border border-slate-200 p-4" data-testid="catalog-source-registration">
					<div className="mb-3 flex flex-wrap items-start justify-between gap-2">
						<div>
							<div className="font-medium">其他资产目录入口</div>
							<Text type="secondary">用于没有对应连接的目录资产；登记后再确认是否纳入当前计划。</Text>
						</div>
						<Typography.Link onClick={onOpenCatalog}>管理或同步资产目录</Typography.Link>
					</div>
					<div className="grid gap-2 md:grid-cols-[minmax(180px,1fr)_minmax(180px,1fr)_auto]">
						<Select
							showSearch
							optionFilterProp="label"
							placeholder="1. 选择数据集"
							value={selectedCatalogDatasetId}
							options={catalogDatasetOptions}
							loading={catalogDatasetLoading}
							notFoundContent={catalogDatasetLoading ? "正在加载…" : "暂无可用数据集"}
							onDropdownVisibleChange={(open) => {
								if (open) void loadCatalogDatasets();
							}}
							onChange={(datasetId) => {
								setSelectedCatalogDatasetId(datasetId);
								setSelectedAssetId(null);
								setCatalogOptions([]);
								void loadCatalogTables(datasetId);
							}}
						/>
						<Select
							showSearch
							optionFilterProp="label"
							placeholder="2. 选择数据表"
							disabled={!selectedCatalogDatasetId}
							value={selectedAssetId}
							options={catalogOptions}
							loading={catalogTableLoading}
							notFoundContent={catalogTableLoading ? "正在加载…" : "该数据集暂无可登记表"}
							onDropdownVisibleChange={(open) => {
								if (open && selectedCatalogDatasetId) void loadCatalogTables(selectedCatalogDatasetId);
							}}
							onChange={setSelectedAssetId}
						/>
						<Button
							type="primary"
							icon={<Database size={16} />}
							loading={saving}
							disabled={!selectedAssetId || conflictVersion != null}
							onClick={() => void registerCatalogAsset()}
						>
							3. 登记目录资产
						</Button>
					</div>
				</div>
			) : null}

			{loading && inventory ? (
				<div className="flex items-center gap-2 text-xs text-slate-500">
					<RefreshCw size={13} className="animate-spin" /> 正在核验服务端来源
				</div>
			) : null}
		</div>
	);
}
