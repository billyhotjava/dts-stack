import { Alert, Button, Form, Input, Select, Skeleton, Space, Tag, Typography } from "antd";
import { Database, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { listCatalogAssetsV2, listTablesByDataset } from "@/api/platformApi";
import {
	getWarehousePlanSources,
	saveWarehousePlanSources,
	type WarehousePlanConfirmationStatus,
	type WarehousePlanOnboardingMode,
	type WarehousePlanSourceBindingInput,
	type WarehousePlanSourceBindingView,
	type WarehousePlanSourceFreshness,
	type WarehousePlanSourceInventoryReadiness,
	type WarehousePlanSourceInventoryView,
	type WarehousePlanSourceType,
} from "@/api/warehousePlanApi";
import { createLatestRequestGuard } from "./warehousePlanCreateFlow";
import { resolveWarehousePlanConflictVersion, warehousePlanMutationErrorMessage } from "./warehousePlanViewModel";

const { Paragraph, Text, Title } = Typography;

type WarehousePlanSourceDraft = {
	confirmationStatus: WarehousePlanConfirmationStatus;
	exclusionReason?: string | null;
};

type WarehousePlanSourcesFormValue = {
	bindings: WarehousePlanSourceDraft[];
};

type CatalogAssetOption = {
	value: string;
	label: string;
};

type WarehousePlanSourcesTabProps = {
	planId: string;
	onboardingMode: WarehousePlanOnboardingMode;
	conceptualDesignAllowed: boolean;
	editable: boolean;
	onOpenCatalog: () => void;
	onSaved: () => Promise<void> | void;
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
}: WarehousePlanSourcesTabProps) {
	const [form] = Form.useForm<WarehousePlanSourcesFormValue>();
	const [inventory, setInventory] = useState<WarehousePlanSourceInventoryView | null>(null);
	const [loading, setLoading] = useState(true);
	const [loadFailed, setLoadFailed] = useState(false);
	const [saving, setSaving] = useState(false);
	const [dirty, setDirtyValue] = useState(false);
	const [conflictVersion, setConflictVersion] = useState<number | null>(null);
	const [catalogDatasetOptions, setCatalogDatasetOptions] = useState<CatalogAssetOption[]>([]);
	const [catalogOptions, setCatalogOptions] = useState<CatalogAssetOption[]>([]);
	const [catalogDatasetLoading, setCatalogDatasetLoading] = useState(false);
	const [catalogTableLoading, setCatalogTableLoading] = useState(false);
	const [selectedCatalogDatasetId, setSelectedCatalogDatasetId] = useState<string | null>(null);
	const [selectedAssetId, setSelectedAssetId] = useState<string | null>(null);
	const [pendingCatalogAssetId, setPendingCatalogAssetId] = useState<string | null>(null);
	const dirtyRef = useRef(false);
	const loadGuard = useMemo(() => createLatestRequestGuard(), []);
	const catalogDatasetLoadGuard = useMemo(() => createLatestRequestGuard(), []);
	const catalogTableLoadGuard = useMemo(() => createLatestRequestGuard(), []);
	const mutationGuard = useMemo(() => createLatestRequestGuard(), []);

	const setDirty = useCallback((value: boolean) => {
		dirtyRef.current = value;
		setDirtyValue(value);
	}, []);

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
		setInventory(null);
		setConflictVersion(null);
		setSelectedCatalogDatasetId(null);
		setSelectedAssetId(null);
		setPendingCatalogAssetId(null);
		setCatalogDatasetOptions([]);
		setCatalogOptions([]);
		setCatalogDatasetLoading(false);
		setCatalogTableLoading(false);
		setSaving(false);
		setDirty(false);
		form.resetFields();
		void loadSources(true);
		return () => {
			loadGuard.invalidate();
			catalogDatasetLoadGuard.invalidate();
			catalogTableLoadGuard.invalidate();
			mutationGuard.invalidate();
		};
	}, [catalogDatasetLoadGuard, catalogTableLoadGuard, form, loadGuard, loadSources, mutationGuard, setDirty]);

	const saveSources = async (expectedVersion?: number) => {
		if (!inventory) return;
		const isCurrent = mutationGuard.begin();
		const values = await form.validateFields().catch(() => null);
		if (!isCurrent()) return;
		if (!values) return;
		setSaving(true);
		try {
			const savedInventory = await saveWarehousePlanSources(
				planId,
				expectedVersion ?? inventory.version,
				buildWarehousePlanSourceSaveBindings(inventory.bindings, values.bindings || []),
			);
			if (!isCurrent()) return;
			setInventory(savedInventory);
			form.setFieldsValue({ bindings: toDraftBindings(savedInventory.bindings) });
			setDirty(false);
			setConflictVersion(null);
			setPendingCatalogAssetId(null);
			toast.success("来源盘点已保存");
			const refreshResults = await Promise.allSettled([loadSources(true), Promise.resolve(onSaved())]);
			if (!isCurrent()) return;
			if (refreshResults.some((result) => result.status === "rejected")) {
				toast.warning("来源已保存，阶段状态刷新失败，可稍后重新加载");
			}
		} catch (error: any) {
			if (!isCurrent()) return;
			const currentVersion = resolveWarehousePlanConflictVersion(error);
			if (currentVersion != null) {
				setConflictVersion(currentVersion);
			} else {
				toast.error(warehousePlanMutationErrorMessage(error, "来源盘点保存失败"));
			}
		} finally {
			if (isCurrent()) setSaving(false);
		}
	};

	const registerCatalogAsset = async (expectedVersion?: number) => {
		if (!inventory) return;
		const assetId = expectedVersion != null && pendingCatalogAssetId ? pendingCatalogAssetId : selectedAssetId;
		if (!assetId) {
			toast.error("请先选择目录资产");
			return;
		}
		const isCurrent = mutationGuard.begin();
		const values = inventory.bindings.length ? await form.validateFields().catch(() => null) : { bindings: [] };
		if (!isCurrent()) return;
		if (!values) return;
		setSaving(true);
		try {
			const bindings: WarehousePlanSourceBindingInput[] = [
				...buildWarehousePlanSourceSaveBindings(inventory.bindings, values.bindings || []),
				{
					sourceType: "CATALOG_TABLE",
					locator: { assetId },
					confirmationStatus: "CANDIDATE",
					exclusionReason: null,
				},
			];
			const savedInventory = await saveWarehousePlanSources(planId, expectedVersion ?? inventory.version, bindings);
			if (!isCurrent()) return;
			setInventory(savedInventory);
			form.setFieldsValue({ bindings: toDraftBindings(savedInventory.bindings) });
			setDirty(false);
			setConflictVersion(null);
			setPendingCatalogAssetId(null);
			setSelectedAssetId(null);
			setCatalogOptions((options) => options.filter((option) => option.value !== assetId));
			toast.success("目录资产已登记到当前计划");
			const refreshResults = await Promise.allSettled([loadSources(true), Promise.resolve(onSaved())]);
			if (!isCurrent()) return;
			if (refreshResults.some((result) => result.status === "rejected")) {
				toast.warning("资产已登记，阶段状态刷新失败，可稍后重新加载");
			}
		} catch (error: any) {
			if (!isCurrent()) return;
			const currentVersion = resolveWarehousePlanConflictVersion(error);
			if (currentVersion != null) {
				setPendingCatalogAssetId(assetId);
				setConflictVersion(currentVersion);
			} else {
				toast.error(warehousePlanMutationErrorMessage(error, "目录资产登记失败"));
			}
		} finally {
			if (isCurrent()) setSaving(false);
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
									setDirty(false);
									void loadSources(true);
								}}
							>
								重新加载服务端最新版
							</Button>
							<Button
								type="primary"
								loading={saving}
								onClick={() =>
									void (pendingCatalogAssetId ? registerCatalogAsset(conflictVersion) : saveSources(conflictVersion))
								}
							>
								确认保留当前草稿并基于版本 {conflictVersion} 保存
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
														{SOURCE_TYPE_LABELS[binding.sourceType]} · 绑定标识：{binding.bindingId}
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
					<Paragraph type="secondary">请从来源目录登记稳定引用；系统不会根据同名表自动替换或推断来源。</Paragraph>
				</div>
			)}

			{inventory && editable ? (
				<div className="rounded-xl border border-slate-200 p-4" data-testid="catalog-source-registration">
					<div className="mb-3 flex flex-wrap items-start justify-between gap-2">
						<div>
							<div className="font-medium">登记目录资产</div>
							<Text type="secondary">按“数据集 → 数据表”的顺序选择，登记后再确认是否纳入当前计划。</Text>
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
