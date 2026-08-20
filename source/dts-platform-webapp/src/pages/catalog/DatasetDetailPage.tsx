import { Alert, Button, Descriptions, Space, Spin, Tabs, Tag } from "antd";
import { useEffect, useRef, useState } from "react";
import { useLocation, useParams, useSearchParams } from "react-router";
import {
	getCatalogAssetV2,
	getCatalogAssetV2Contract,
	getCatalogAssetV2SchemaContract,
	getDataset,
} from "@/api/platformApi";
import { useCatalogDomainOptions } from "@/hooks/useCatalogDomainOptions";
import { useRouter } from "@/routes/hooks";
import { buildAssetGrantUrl, resolveAssetReadiness } from "./assetPortalUx.helpers";
import { AssetClassificationFactPanel } from "./assets/AssetClassificationFactPanel";
import { AssetDeliveryEligibilityNotice, AssetDeliveryStatusPanel } from "./assets/AssetDeliveryStatusPanel";
import { AssetLifecycleWorkbenchDrawer } from "./assets/AssetLifecycleWorkbenchDrawer";
import type { AssetRow } from "./assets/assetPageShared";
import {
	DatasetFieldsTab,
	DatasetGovernanceTab,
	DatasetLineageImpactTab,
	MetadataJsonBlock,
} from "./DatasetDetailSupportTabs";
import { resolveDatasetDetailId } from "./datasetDetailRoute";
import { OpenMetadataGovernanceTab } from "./OpenMetadataGovernanceTab";
const DETAIL_TAB_KEYS = [
	"overview",
	"classification-lifecycle",
	"schema-contract",
	"governance",
	"quality-sla",
	"lineage-impact",
	"access",
] as const;
const DETAIL_TAB_ALIASES: Record<string, (typeof DETAIL_TAB_KEYS)[number]> = {
	fields: "schema-contract",
	technical: "schema-contract",
	classification: "classification-lifecycle",
	lifecycle: "classification-lifecycle",
	lineage: "lineage-impact",
	quality: "quality-sla",
	sla: "quality-sla",
};

const resolveDetailTabKey = (value?: string | null) =>
	DETAIL_TAB_KEYS.includes(value as any) ? String(value) : DETAIL_TAB_ALIASES[String(value || "")] || "overview";

const toDatasetFromAssetV2Detail = (id: string, detail: any) => {
	const asset = detail?.asset || {};
	return {
		id: asset.id || id,
		name: asset.displayName || asset.table || asset.fqn || "-",
		type: asset.type || "-",
		warehouseLayer: asset.warehouseLayer,
		classification: asset.classification,
		owner: asset.owner,
		ownerDept: asset.ownerDept,
		lifecycleStatus: asset.lifecycleStatus,
		hiveDatabase: asset.database || asset.schema,
		hiveTable: asset.table,
		domainId: asset.domainId,
		description: asset.description || asset.fqn,
		governanceStatus: asset.governanceStatus,
		matchStatus: asset.matchStatus,
		matchReason: asset.matchReason,
		syncStatus: asset.syncStatus,
		syncMessage: asset.syncMessage,
		service: asset.service,
		schema: asset.schema,
		columnCount: asset.columnCount,
		securityPolicyRefs: asset.securityPolicyRefs,
		metadataSource: asset.metadataSource,
		statusAxes: asset.statusAxes,
		consumptionEligibility: asset.consumptionEligibility || "CONDITIONAL",
		eligibilityReasons: Array.isArray(asset.eligibilityReasons) ? asset.eligibilityReasons : [],
		projectionUpdatedAt: asset.projectionUpdatedAt,
		modelRefs: Array.isArray(asset.modelRefs) ? asset.modelRefs : [],
		servingSync: asset.servingSync || { status: "NOT_APPLICABLE", attempts: 0 },
		qualityStatus: asset.qualityStatus || "UNKNOWN",
		__fqn: asset.fqn,
		__legacyDatasetId: asset.legacyDatasetId,
		__columns: Array.isArray(detail?.columns) ? detail.columns : [],
		__rawJson: detail?.rawJson,
		__profileJson: detail?.profileJson,
		__tags: Array.isArray(detail?.asset?.tags) ? detail.asset.tags : [],
	};
};

export default function DatasetDetailPage() {
	const { id: routeId } = useParams<{ id?: string }>();
	const location = useLocation();
	const id = resolveDatasetDetailId(routeId, location.pathname);
	const [searchParams, setSearchParams] = useSearchParams();
	const router = useRouter();
	const requestedTab = searchParams.get("tab");
	const [activeTab, setActiveTab] = useState(() => resolveDetailTabKey(requestedTab));
	const [dataset, setDataset] = useState<Record<string, any> | null>(null);
	const [legacyOnly, setLegacyOnly] = useState(false);
	const [loading, setLoading] = useState(true);
	const [assetContract, setAssetContract] = useState<Record<string, any> | null>(null);
	const [schemaContract, setSchemaContract] = useState<Record<string, any> | null>(null);
	const [contractLoading, setContractLoading] = useState(false);
	const [lifecycleWorkbenchOpen, setLifecycleWorkbenchOpen] = useState(false);
	const datasetRequestSequence = useRef(0);
	const contractRequestSequence = useRef(0);
	const {
		options: domainOptions,
		labelByKey: domainLabelByKey,
		loading: domainLoading,
		error: domainError,
	} = useCatalogDomainOptions();

	useEffect(() => {
		const sequence = ++datasetRequestSequence.current;
		setDataset(null);
		setAssetContract(null);
		setSchemaContract(null);
		setLegacyOnly(false);
		if (!id) {
			setLoading(false);
			return;
		}
		setLoading(true);
		void (async () => {
			try {
				// ADR-85-03：详情页以 assets-v2 为唯一事实源，Tab 渲染不再分流
				const detail = await getCatalogAssetV2(id);
				if (sequence === datasetRequestSequence.current) {
					setDataset(toDatasetFromAssetV2Detail(id, detail));
				}
			} catch {
				if (sequence !== datasetRequestSequence.current) return;
				// 旧深链兼容：legacy 数据集仅用于识别并给出显式指引，不再参与 Tab 渲染
				try {
					await getDataset(id);
					if (sequence === datasetRequestSequence.current) setLegacyOnly(true);
				} catch {
					if (sequence === datasetRequestSequence.current) setDataset(null);
				}
			}
		})().finally(() => {
			if (sequence === datasetRequestSequence.current) setLoading(false);
		});
		return () => {
			if (sequence === datasetRequestSequence.current) datasetRequestSequence.current++;
		};
	}, [id]);

	useEffect(() => {
		setActiveTab(resolveDetailTabKey(requestedTab));
	}, [requestedTab]);

	useEffect(() => {
		const sequence = ++contractRequestSequence.current;
		setAssetContract(null);
		setSchemaContract(null);
		if (!id || !dataset) {
			setContractLoading(false);
			return;
		}
		const assetId = String(dataset.id || id);
		setContractLoading(true);
		void Promise.allSettled([getCatalogAssetV2Contract(assetId), getCatalogAssetV2SchemaContract(assetId)])
			.then(([contractResult, schemaResult]) => {
				if (sequence !== contractRequestSequence.current) return;
				setAssetContract(contractResult.status === "fulfilled" ? contractResult.value || null : null);
				setSchemaContract(schemaResult.status === "fulfilled" ? schemaResult.value || null : null);
			})
			.finally(() => {
				if (sequence === contractRequestSequence.current) setContractLoading(false);
			});
		return () => {
			if (sequence === contractRequestSequence.current) contractRequestSequence.current++;
		};
	}, [id, dataset]);

	if (loading) {
		return (
			<div className="flex h-64 items-center justify-center">
				<Spin />
			</div>
		);
	}
	if (legacyOnly) {
		return (
			<div className="space-y-3 p-8">
				<Alert
					type="info"
					showIcon
					message="该资产暂时无法打开治理详情。"
					description="请返回数据搜索重新选择；如仍无法打开，请联系数据管理员检查资产同步状态。"
				/>
				<Button type="primary" onClick={() => router.push("/catalog/search")}>
					返回数据搜索
				</Button>
			</div>
		);
	}
	if (!dataset) {
		return <div className="p-8 text-slate-500">数据集不存在或无权访问。</div>;
	}
	const grantAssetType = String(assetContract?.grantAssetType || "").trim();
	const assetKey = String(assetContract?.assetKey || "").trim();
	const schemaCount = schemaContract?.columnCount ?? dataset.columnCount ?? "-";
	const schemaCountLabel = schemaCount === "-" ? "未同步" : `${schemaCount} 个字段`;
	const domainId = String(assetContract?.domainId || dataset.domainId || "").trim();
	const domainName = dataset.domainName || dataset.domain || domainLabelByKey[domainId] || "";
	const readiness = resolveAssetReadiness({
		classification: dataset.classification ?? assetContract?.classification,
		domainId: dataset.domainId ?? assetContract?.domainId,
		domain: domainName,
		owner: dataset.owner ?? assetContract?.owner,
		ownerDept: dataset.ownerDept ?? assetContract?.ownerDept,
		lifecycleStatus: dataset.lifecycleStatus ?? assetContract?.lifecycleStatus,
		governanceStatus: dataset.governanceStatus ?? assetContract?.governanceStatus,
		matchStatus: dataset.matchStatus ?? assetContract?.matchStatus,
		metadataSource: dataset.metadataSource ?? assetContract?.metadataSource,
		legacyDatasetId: dataset.__legacyDatasetId ?? assetContract?.legacyDatasetId,
	});
	const contractReasons = Array.isArray(assetContract?.missingGovernanceFields)
		? assetContract.missingGovernanceFields.map((field: unknown) => {
				if (field === "domainId") return "缺少业务归属数据域";
				if (field === "classification") return "缺少密级";
				if (field === "owner" || field === "ownerDept") return "缺少归属部门";
				return String(field);
			})
		: [];
	const displayedReadiness =
		assetContract?.consumable === false
			? {
					...readiness,
					state: "BLOCKED" as const,
					label: "不可引用",
					color: "red",
					reasons:
						contractReasons.length > 0
							? contractReasons
							: readiness.reasons.length > 0
								? readiness.reasons
								: ["资产合同尚未满足引用条件"],
				}
			: readiness;
	const lifecycleActionRequired = displayedReadiness.reasons.some(
		(reason) => reason.includes("生命周期") || reason.includes("密级"),
	);
	const openGovernanceTab = () => {
		setActiveTab("governance");
		setSearchParams({ tab: "governance" });
	};
	const handleGovernanceChanged = (next: Record<string, any>) => {
		setDataset(next);
		setAssetContract((current) =>
			current
				? {
						...current,
						domainId: next.domainId,
						warehouseLayer: next.warehouseLayer,
						ownerDept: next.ownerDept,
						owner: next.owner,
						governanceStatus: next.governanceStatus,
						securityPolicyRefs: next.securityPolicyRefs,
					}
				: current,
		);
		const sequence = ++contractRequestSequence.current;
		void getCatalogAssetV2Contract(String(next.id || id))
			.then((contract) => {
				if (sequence === contractRequestSequence.current) setAssetContract(contract || null);
			})
			.catch(() => undefined);
	};
	const workbenchAsset: AssetRow = {
		id: String(dataset.id || id),
		name: String(dataset.name || "-"),
		type: String(dataset.type || "-"),
		classification: dataset.classification,
		warehouseLayer: dataset.warehouseLayer,
		lifecycleStatus: dataset.lifecycleStatus,
		owner: dataset.owner,
		ownerDept: dataset.ownerDept,
		domainId,
		domain: domainName,
		governanceStatus: dataset.governanceStatus,
		metadataSource: dataset.metadataSource,
		legacyDatasetId: dataset.__legacyDatasetId,
		description: dataset.description,
		hiveDatabase: dataset.hiveDatabase,
		hiveTable: dataset.hiveTable,
		assetKey,
	};

	return (
		<div className="space-y-4 p-4">
			<section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
				<div className="flex flex-wrap items-start gap-3 px-4 py-4 sm:px-5">
					<Button type="text" className="-ml-2" onClick={() => router.back()}>
						← 返回数据资产目录
					</Button>
					<div className="min-w-48 flex-1">
						<div className="text-xs text-slate-500">数据资产详情</div>
						<h2 className="mt-1 truncate text-xl font-semibold text-slate-900">{dataset.name ?? "-"}</h2>
						<div className="mt-1 truncate font-mono text-xs text-slate-500">
							{dataset.hiveDatabase && dataset.hiveTable
								? `${dataset.hiveDatabase}.${dataset.hiveTable}`
								: dataset.__fqn || "物理对象待同步"}
						</div>
					</div>
					<Space size={[4, 4]} wrap>
						<Tag color={dataset.metadataSource === "openmetadata" ? "blue" : "default"}>
							{dataset.metadataSource || "assets-v2"}
						</Tag>
						{dataset.warehouseLayer ? <Tag>{dataset.warehouseLayer}</Tag> : null}
						<Button
							disabled={!assetKey}
							title={assetKey ? "查看密级事实、生命周期时间轴和审批记录" : "资产身份合同尚未就绪"}
							onClick={() => setLifecycleWorkbenchOpen(true)}
						>
							密级与生命周期
						</Button>
					</Space>
				</div>
				<div className="grid border-t border-slate-100 sm:grid-cols-2 lg:grid-cols-4">
					<div className="border-b border-slate-100 px-5 py-3 sm:border-r lg:border-b-0">
						<div className="text-xs text-slate-500">治理状态</div>
						<div className="mt-1 flex items-center gap-2">
							<Tag color={displayedReadiness.color}>{displayedReadiness.label}</Tag>
							<span className="truncate text-xs text-slate-500">
								{assetContract?.governanceStatus || dataset.governanceStatus || (contractLoading ? "读取中" : "-")}
							</span>
						</div>
					</div>
					<div className="border-b border-slate-100 px-5 py-3 lg:border-b-0 lg:border-r">
						<div className="text-xs text-slate-500">业务归属数据域</div>
						<div className="mt-1 truncate text-sm font-medium text-slate-900">{domainName || "待设置"}</div>
					</div>
					<div className="border-b border-slate-100 px-5 py-3 sm:border-r sm:border-b-0">
						<div className="text-xs text-slate-500">治理责任人</div>
						<div className="mt-1 truncate text-sm font-medium text-slate-900">
							{dataset.owner || dataset.ownerDept || "待设置"}
						</div>
					</div>
					<div className="px-5 py-3">
						<div className="text-xs text-slate-500">字段契约</div>
						<div className="mt-1 text-sm font-medium text-slate-900">{schemaCountLabel}</div>
					</div>
				</div>
				{displayedReadiness.state !== "READY" ? (
					<div className="flex flex-wrap items-center gap-3 border-t border-amber-200 bg-amber-50 px-5 py-3">
						<div className="min-w-52 flex-1">
							<div className="text-sm font-semibold text-amber-950">当前治理待办</div>
							<div className="mt-1 text-sm text-amber-900">
								{displayedReadiness.reasons.length
									? displayedReadiness.reasons.join("；")
									: "请核验资产映射与治理信息。"}
							</div>
						</div>
						<Button
							type="primary"
							onClick={() => {
								if (lifecycleActionRequired) setLifecycleWorkbenchOpen(true);
								else openGovernanceTab();
							}}
						>
							{lifecycleActionRequired ? "处理密级与生命周期" : "完善治理信息"}
						</Button>
					</div>
				) : null}
			</section>
			<Tabs
				activeKey={activeTab}
				onChange={(key) => {
					const next = resolveDetailTabKey(key);
					setActiveTab(next);
					setSearchParams({ tab: next });
				}}
				items={[
					{
						key: "overview",
						label: "概览",
						children: (
							<DatasetOverviewTab
								dataset={dataset}
								domainName={domainName}
								assetContract={assetContract}
								schemaContract={schemaContract}
							/>
						),
					},
					{
						key: "governance",
						label: "治理信息",
						children: (
							<OpenMetadataGovernanceTab
								assetType={grantAssetType}
								assetKey={assetKey}
								canTag={assetContract?.canTag === true}
								dataset={dataset}
								domainOptions={domainOptions}
								domainLoading={domainLoading}
								domainError={domainError}
								onChanged={handleGovernanceChanged}
								onOpenLifecycle={() => setLifecycleWorkbenchOpen(true)}
							/>
						),
					},
					{
						key: "classification-lifecycle",
						label: "密级事实",
						children: (
							<AssetClassificationFactPanel
								assetKey={assetKey}
								columns={Array.isArray(schemaContract?.columns) ? schemaContract.columns : dataset.__columns}
							/>
						),
					},
					{
						key: "schema-contract",
						label: "字段契约",
						children: (
							<DatasetSchemaContractTab
								dataset={dataset}
								assetContract={assetContract}
								schemaContract={schemaContract}
								contractLoading={contractLoading}
								datasetId={String(dataset.__legacyDatasetId || id)}
								columns={Array.isArray(schemaContract?.columns) ? schemaContract?.columns : dataset.__columns}
							/>
						),
					},
					{
						key: "quality-sla",
						label: "质量与SLA",
						children: (
							<DatasetQualitySlaTab
								dataset={dataset}
								assetContract={assetContract}
								schemaContract={schemaContract}
								datasetId={String(dataset.__legacyDatasetId || id)}
							/>
						),
					},
					{
						key: "lineage-impact",
						label: "血缘与影响",
						children: <DatasetLineageImpactTab dataset={dataset} />,
					},
					{
						key: "access",
						label: "权限申请",
						children: (
							<AssetAccessTab dataset={dataset} assetContract={assetContract} contractLoading={contractLoading} />
						),
					},
				]}
			/>
			<AssetLifecycleWorkbenchDrawer
				open={lifecycleWorkbenchOpen}
				asset={workbenchAsset}
				onClose={() => setLifecycleWorkbenchOpen(false)}
				onChanged={() => {
					setActiveTab("classification-lifecycle");
					setSearchParams({ tab: "classification-lifecycle" });
				}}
			/>
		</div>
	);
}

const resolveProfileSummary = (
	profileJson?: string,
): { rowCount?: number; columnCount?: number; profileDate?: string } => {
	if (!profileJson) return {};
	try {
		const p = JSON.parse(profileJson);
		return {
			rowCount: p.rowCount ?? p.row_count ?? undefined,
			columnCount: p.columnCount ?? p.column_count ?? undefined,
			profileDate: p.profileDate ?? p.createDateTime ?? p.timestamp ?? undefined,
		};
	} catch {
		return {};
	}
};

function DatasetOverviewTab({
	dataset,
	domainName,
	assetContract,
	schemaContract,
}: {
	dataset: Record<string, any>;
	domainName?: string;
	assetContract?: Record<string, any> | null;
	schemaContract?: Record<string, any> | null;
}) {
	const missingFields = Array.isArray(assetContract?.missingGovernanceFields)
		? assetContract?.missingGovernanceFields
		: [];
	const tags: any[] = Array.isArray(dataset.__tags) ? dataset.__tags : [];
	const profile = resolveProfileSummary(dataset.__profileJson);
	return (
		<div className="space-y-3 py-2">
			{dataset.metadataSource === "openmetadata" && <Tag color="blue">OpenMetadata主目录</Tag>}
			<Descriptions bordered size="small" column={2}>
				<Descriptions.Item label="仓库分层">{dataset.warehouseLayer ?? "-"}</Descriptions.Item>
				<Descriptions.Item label="密级">{dataset.classification ?? "-"}</Descriptions.Item>
				<Descriptions.Item label="负责人">{dataset.owner ?? "-"}</Descriptions.Item>
				<Descriptions.Item label="所属部门">{dataset.ownerDept ?? "-"}</Descriptions.Item>
				<Descriptions.Item label="类型">{dataset.type ?? "-"}</Descriptions.Item>
				<Descriptions.Item label="生命周期">{dataset.lifecycleStatus ?? "-"}</Descriptions.Item>
				<Descriptions.Item label="技术表">
					{dataset.hiveDatabase && dataset.hiveTable ? `${dataset.hiveDatabase}.${dataset.hiveTable}` : "-"}
				</Descriptions.Item>
				<Descriptions.Item label="业务归属数据域">
					{domainName || dataset.domainName || dataset.domain || "-"}
				</Descriptions.Item>
				<Descriptions.Item label="治理状态">{dataset.governanceStatus ?? "-"}</Descriptions.Item>
				<Descriptions.Item label="映射状态">{dataset.matchStatus ?? "-"}</Descriptions.Item>
				<Descriptions.Item label="字段合同">
					{schemaContract?.columnCount ?? dataset.columnCount ?? "-"}
				</Descriptions.Item>
				<Descriptions.Item label="资产来源">
					{assetContract?.metadataSource ?? dataset.metadataSource ?? "-"}
				</Descriptions.Item>
				{profile.rowCount != null && (
					<Descriptions.Item label="数据行数">{profile.rowCount.toLocaleString()}</Descriptions.Item>
				)}
				{profile.profileDate && (
					<Descriptions.Item label="最近采样">{String(profile.profileDate).slice(0, 19)}</Descriptions.Item>
				)}
			</Descriptions>
			<AssetDeliveryStatusPanel dataset={dataset} />
			{tags.length > 0 && (
				<div className="flex flex-wrap items-center gap-1">
					<span className="text-xs text-slate-500 mr-1">OpenMetadata 技术标签：</span>
					{tags.map((t: any) => {
						const label = t?.tagFQN ?? t?.name ?? t?.label ?? String(t);
						const shortLabel = String(label).split(".").pop() ?? label;
						const tagKey = t?.id ?? t?.tagFQN ?? t?.name ?? JSON.stringify(t);
						return (
							<Tag key={String(tagKey)} title={label}>
								{shortLabel}
							</Tag>
						);
					})}
				</div>
			)}
			{missingFields.length ? (
				<Alert type="warning" showIcon message="治理字段待补齐" description={missingFields.join("、")} />
			) : null}
			<AssetDeliveryEligibilityNotice dataset={dataset} />
			{dataset.matchReason ? <Alert type="info" showIcon message="映射说明" description={dataset.matchReason} /> : null}
			{dataset.description && (
				<div className="text-sm text-slate-600 rounded border border-slate-100 bg-slate-50 p-3">
					{dataset.description}
				</div>
			)}
		</div>
	);
}

function DatasetSchemaContractTab({
	dataset,
	assetContract,
	schemaContract,
	contractLoading,
	datasetId,
	columns,
}: {
	dataset: Record<string, any>;
	assetContract?: Record<string, any> | null;
	schemaContract?: Record<string, any> | null;
	contractLoading?: boolean;
	datasetId: string;
	columns?: any[];
}) {
	const missingFields = Array.isArray(assetContract?.missingGovernanceFields)
		? assetContract?.missingGovernanceFields
		: [];
	const schemaSource = schemaContract?.schemaSource || dataset.metadataSource || "-";
	const columnCount =
		schemaContract?.columnCount ?? dataset.columnCount ?? (Array.isArray(columns) ? columns.length : undefined);
	return (
		<div className="space-y-4 py-2">
			<Alert
				type={missingFields.length ? "warning" : "info"}
				showIcon
				message={missingFields.length ? "字段契约暂不可作为生产引用" : "字段契约是指标、DWS/ADS 和 BI 消费的读取边界"}
				description={
					contractLoading
						? "正在读取资产合同和字段合同..."
						: missingFields.length
							? `仍缺少治理字段：${missingFields.join("、")}。发布指标或数据产品前需要先补齐。`
							: "下游应通过资产合同中的授权资产标识和字段清单引用该资产，避免直接依赖临时元数据 ID。"
				}
			/>
			<Descriptions bordered size="small" column={2}>
				<Descriptions.Item label="合同状态">
					{assetContract?.consumable === false ? "不可引用" : assetContract ? "可引用" : "读取中"}
				</Descriptions.Item>
				<Descriptions.Item label="字段来源">{schemaSource}</Descriptions.Item>
				<Descriptions.Item label="字段数量">{columnCount ?? "未同步"}</Descriptions.Item>
				<Descriptions.Item label="资产键">
					{assetContract?.assetKey || dataset.__fqn || dataset.id || "-"}
				</Descriptions.Item>
				<Descriptions.Item label="授权资产">
					{assetContract?.grantAssetType && assetContract?.grantAssetId
						? `${assetContract.grantAssetType}:${assetContract.grantAssetId}`
						: "-"}
				</Descriptions.Item>
				<Descriptions.Item label="物理对象">
					{dataset.hiveDatabase && dataset.hiveTable
						? `${dataset.hiveDatabase}.${dataset.hiveTable}`
						: dataset.__fqn || "-"}
				</Descriptions.Item>
			</Descriptions>
			<DatasetFieldsTab datasetId={datasetId} columns={columns} />
			<DatasetTechnicalTab dataset={dataset} assetContract={assetContract} schemaContract={schemaContract} />
		</div>
	);
}

function DatasetTechnicalTab({
	dataset,
	assetContract,
	schemaContract,
}: {
	dataset: Record<string, any>;
	assetContract?: Record<string, any> | null;
	schemaContract?: Record<string, any> | null;
}) {
	const metadataRows = [
		{ key: "assetKey", label: "资产键", value: assetContract?.assetKey },
		{
			key: "grantAsset",
			label: "授权资产",
			value:
				assetContract?.grantAssetType && assetContract?.grantAssetId
					? `${assetContract.grantAssetType}:${assetContract.grantAssetId}`
					: undefined,
		},
		{ key: "sourceRef", label: "来源引用", value: assetContract?.sourceRef },
		{ key: "service", label: "Service", value: dataset.service },
		{ key: "database", label: "Database", value: dataset.hiveDatabase },
		{ key: "schema", label: "Schema", value: dataset.schema },
		{ key: "table", label: "Table", value: dataset.hiveTable },
		{ key: "columnCount", label: "字段数", value: schemaContract?.columnCount ?? dataset.columnCount },
		{ key: "schemaSource", label: "字段来源", value: schemaContract?.schemaSource },
		{ key: "syncStatus", label: "同步状态", value: dataset.syncStatus },
		{ key: "syncMessage", label: "同步说明", value: dataset.syncMessage },
	].filter((item) => item.value !== undefined && item.value !== null && item.value !== "");

	return (
		<div className="space-y-4 py-2">
			{metadataRows.length ? (
				<Descriptions bordered size="small" column={2}>
					{metadataRows.map((item) => (
						<Descriptions.Item key={item.key} label={item.label}>
							{String(item.value)}
						</Descriptions.Item>
					))}
				</Descriptions>
			) : (
				<Alert type="info" showIcon message="暂无技术同步摘要" />
			)}
			<MetadataJsonBlock title="Profile JSON" value={dataset.__profileJson} />
			<MetadataJsonBlock title="Raw Metadata JSON" value={dataset.__rawJson} />
		</div>
	);
}

function DatasetQualitySlaTab({
	dataset,
	assetContract,
	schemaContract,
	datasetId,
}: {
	dataset: Record<string, any>;
	assetContract?: Record<string, any> | null;
	schemaContract?: Record<string, any> | null;
	datasetId: string;
}) {
	const hasLegacyDataset = Boolean(dataset.__legacyDatasetId);
	const freshnessLabel = assetContract?.maxStalenessMinutes
		? `${assetContract.maxStalenessMinutes} 分钟内`
		: assetContract?.expectedRefreshIntervalMinutes
			? `${assetContract.expectedRefreshIntervalMinutes} 分钟刷新`
			: "未配置";
	const qualityStatus = String(dataset.qualityStatus || "UNKNOWN")
		.trim()
		.toUpperCase();
	const qualityStatusLabel =
		qualityStatus === "PASSED"
			? "已通过"
			: qualityStatus === "FAILED"
				? "未通过"
				: qualityStatus === "RUNNING"
					? "校验中"
					: "待校验";
	return (
		<div className="space-y-4 py-2">
			<Alert
				type={hasLegacyDataset ? "info" : "warning"}
				showIcon
				message="质量与 SLA 决定资产是否可以进入指标和数据产品"
				description={
					hasLegacyDataset
						? "这里展示 legacy 治理资产上的质量运行、治理问题和关联指标；后续应由 assets-v2 直接输出统一 SLA 合同。"
						: "当前资产尚未映射到 DTS 治理资产，暂时没有质量规则、运行记录和指标依赖。"
				}
			/>
			<div className="grid gap-3 md:grid-cols-3">
				<div className="rounded-lg border border-slate-200 bg-white px-3 py-2">
					<div className="text-xs text-slate-500">刷新 SLA</div>
					<div className="mt-1 text-sm font-semibold text-slate-900">{freshnessLabel}</div>
				</div>
				<div className="rounded-lg border border-slate-200 bg-white px-3 py-2">
					<div className="text-xs text-slate-500">字段合同</div>
					<div className="mt-1 text-sm font-semibold text-slate-900">
						{schemaContract?.columnCount ?? dataset.columnCount ?? "未同步"}
					</div>
				</div>
				<div className="rounded-lg border border-slate-200 bg-white px-3 py-2">
					<div className="text-xs text-slate-500">治理质量状态</div>
					<div className="mt-1 text-sm font-semibold text-slate-900">{qualityStatusLabel}</div>
				</div>
			</div>
			{hasLegacyDataset ? (
				<DatasetGovernanceTab datasetId={datasetId} />
			) : (
				<Alert
					type="warning"
					showIcon
					message="未映射治理资产"
					description="请先在治理信息页补齐映射、密级、业务归属数据域和负责人，再配置质量规则与 SLA。"
				/>
			)}
		</div>
	);
}

function AssetAccessTab({
	dataset,
	assetContract,
	contractLoading,
}: {
	dataset: Record<string, any>;
	assetContract?: Record<string, any> | null;
	contractLoading?: boolean;
}) {
	const router = useRouter();
	const asset = assetContract || dataset;
	const grantAssetType = assetContract?.grantAssetType || "TABLE";
	const grantAssetId = assetContract?.grantAssetId || assetContract?.assetKey || dataset.id;
	const readiness = resolveAssetReadiness({
		classification: asset.classification,
		domainId: asset.domainId,
		domain: dataset.domainName ?? dataset.domain,
		owner: asset.owner,
		ownerDept: asset.ownerDept,
		lifecycleStatus: asset.lifecycleStatus,
		governanceStatus: asset.governanceStatus,
		matchStatus: asset.matchStatus,
		metadataSource: asset.metadataSource,
		legacyDatasetId: asset.legacyDatasetId ?? dataset.__legacyDatasetId,
	});

	return (
		<div className="space-y-4 py-2">
			<Alert
				type={readiness.state === "BLOCKED" ? "warning" : "info"}
				showIcon
				message={readiness.state === "BLOCKED" ? "当前资产需要先完成治理再授权使用" : "资产权限由 platform 统一管理"}
				description={
					contractLoading
						? "正在读取资产权限合同..."
						: readiness.reasons.length
							? readiness.reasons.join("；")
							: "申请或授予权限时，应使用下方授权资产标识，避免按 OpenMetadata 临时 ID 授权。"
				}
			/>
			<Descriptions bordered size="small" column={2}>
				<Descriptions.Item label="授权资产类型">{grantAssetType}</Descriptions.Item>
				<Descriptions.Item label="授权资产ID">{grantAssetId || "-"}</Descriptions.Item>
				<Descriptions.Item label="所需基础权限">READ / EDIT / MANAGE</Descriptions.Item>
				<Descriptions.Item label="密级约束">{asset.classification || "未定级，默认不可访问"}</Descriptions.Item>
			</Descriptions>
			<Space wrap>
				<Button
					type="primary"
					disabled={!grantAssetId}
					onClick={() => router.push(buildAssetGrantUrl({ assetType: grantAssetType, assetId: grantAssetId }))}
				>
					打开资产授权
				</Button>
				<Button onClick={() => router.push("/my/asset-grants")}>查看我的授权</Button>
				<Button onClick={() => router.push("/governance/permission-audit")}>查看权限审计</Button>
			</Space>
		</div>
	);
}
