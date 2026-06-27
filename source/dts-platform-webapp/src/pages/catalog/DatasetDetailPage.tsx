import { useEffect, useMemo, useState } from "react";
import { useParams, useSearchParams } from "react-router";
import { Alert, Button, Descriptions, Form, Input, Select, Space, Spin, Switch, Tabs, Tag, message } from "antd";
import { CompactTable } from "@/components/table";
import { LineageGraph } from "@/components/lineage";
import type { ImpactEdge, ImpactNode } from "./lineageShared";
import { useRouter } from "@/routes/hooks";
import {
	getCatalogAssetV2,
	getCatalogAssetV2Contract,
	getCatalogAssetV2Lineage,
	getCatalogAssetV2SchemaContract,
	getCatalogLineageImpact,
	getDataset,
	getDatasetFields,
	getDatasetGovernanceHealth,
	getDatasetIndicatorDeps,
	syncCatalogAssetV2Lineage,
	updateCatalogAssetV2Governance,
} from "@/api/platformApi";
import { buildAssetGrantUrl, resolveAssetReadiness } from "./assetPortalUx.helpers";

const DETAIL_TAB_KEYS = ["overview", "schema-contract", "governance", "quality-sla", "lineage-impact", "access"] as const;
const DETAIL_TAB_ALIASES: Record<string, (typeof DETAIL_TAB_KEYS)[number]> = {
	fields: "schema-contract",
	technical: "schema-contract",
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
		__source: "openmetadata",
		__fqn: asset.fqn,
		__legacyDatasetId: asset.legacyDatasetId,
		__columns: Array.isArray(detail?.columns) ? detail.columns : [],
		__rawJson: detail?.rawJson,
		__profileJson: detail?.profileJson,
		__tags: Array.isArray(detail?.asset?.tags) ? detail.asset.tags : [],
	};
};

export default function DatasetDetailPage() {
	const { id } = useParams<{ id: string }>();
	const [searchParams, setSearchParams] = useSearchParams();
	const router = useRouter();
	const requestedTab = searchParams.get("tab");
	const [activeTab, setActiveTab] = useState(() => resolveDetailTabKey(requestedTab));
	const [dataset, setDataset] = useState<Record<string, any> | null>(null);
	const [loading, setLoading] = useState(true);
	const [assetContract, setAssetContract] = useState<Record<string, any> | null>(null);
	const [schemaContract, setSchemaContract] = useState<Record<string, any> | null>(null);
	const [contractLoading, setContractLoading] = useState(false);

	useEffect(() => {
		if (!id) return;
		setLoading(true);
		void getCatalogAssetV2(id)
			.then((detail: any) => {
				setDataset(toDatasetFromAssetV2Detail(id, detail));
			})
			.catch(async () => {
				try {
					const legacyDataset: any = await getDataset(id);
					setDataset({ ...legacyDataset, __source: "dts-catalog" });
				} catch {
					setDataset(null);
				}
			})
			.finally(() => setLoading(false));
	}, [id]);

	useEffect(() => {
		setActiveTab(resolveDetailTabKey(requestedTab));
	}, [requestedTab]);

	useEffect(() => {
		if (!id || !dataset) return;
		const assetId = String(dataset.id || id);
		setContractLoading(true);
		void Promise.allSettled([
			getCatalogAssetV2Contract(assetId),
			getCatalogAssetV2SchemaContract(assetId),
		])
			.then(([contractResult, schemaResult]) => {
				setAssetContract(contractResult.status === "fulfilled" ? contractResult.value || null : null);
				setSchemaContract(schemaResult.status === "fulfilled" ? schemaResult.value || null : null);
			})
			.finally(() => setContractLoading(false));
	}, [id, dataset?.id]);

	if (loading) {
		return (
			<div className="flex h-64 items-center justify-center">
				<Spin />
			</div>
		);
	}
	if (!dataset) {
		return <div className="p-8 text-slate-500">数据集不存在或无权访问。</div>;
	}
	const grantAssetType = assetContract?.grantAssetType || (dataset.__source === "openmetadata" ? "DATASET" : "TABLE");
	const grantAssetId = assetContract?.grantAssetId || assetContract?.assetKey || dataset.id || "-";
	const assetKey = assetContract?.assetKey || dataset.__fqn || dataset.id || "-";
	const contractState = assetContract?.consumable === false ? "不可引用" : assetContract ? "可引用" : "合同读取中";
	const schemaCount = schemaContract?.columnCount ?? dataset.columnCount ?? "-";
	const schemaCountLabel = schemaCount === "-" ? "未同步" : `${schemaCount} 个字段`;

	return (
		<div className="space-y-4 p-4">
			<div className="space-y-3 border-b border-slate-200 pb-4">
				<div className="flex flex-wrap items-center gap-3">
					<Button type="text" onClick={() => router.back()}>← 返回</Button>
					<div className="min-w-0 flex-1">
						<div className="text-xs text-slate-500">企业级资产工作台</div>
						<h2 className="truncate text-xl font-bold text-slate-900">{dataset.name ?? "-"}</h2>
					</div>
					<Tag color={dataset.__source === "openmetadata" ? "blue" : "default"}>
						{dataset.__source === "openmetadata" ? "assets-v2" : "legacy dataset"}
					</Tag>
					{dataset.warehouseLayer && (
						<Tag color={
							dataset.warehouseLayer === "ODS" ? "default" :
							dataset.warehouseLayer === "STG" ? "gold" :
							dataset.warehouseLayer === "DWD" ? "blue" :
							dataset.warehouseLayer === "DWS" ? "cyan" :
							dataset.warehouseLayer === "ADS" ? "green" : "default"
						}>
							{dataset.warehouseLayer}
						</Tag>
					)}
				</div>
				<div className="grid gap-3 md:grid-cols-4">
					<div className="rounded-lg border border-slate-200 bg-white px-3 py-2">
						<div className="text-xs text-slate-500">授权资产</div>
						<div className="mt-1 truncate font-mono text-xs text-slate-800">{grantAssetType}:{grantAssetId}</div>
					</div>
					<div className="rounded-lg border border-slate-200 bg-white px-3 py-2">
						<div className="text-xs text-slate-500">资产键</div>
						<div className="mt-1 truncate font-mono text-xs text-slate-800">{assetKey}</div>
					</div>
					<div className="rounded-lg border border-slate-200 bg-white px-3 py-2">
						<div className="text-xs text-slate-500">字段契约</div>
						<div className="mt-1 text-sm font-semibold text-slate-900">{schemaCountLabel}</div>
					</div>
					<div className="rounded-lg border border-slate-200 bg-white px-3 py-2">
						<div className="text-xs text-slate-500">治理状态</div>
						<div className="mt-1 flex items-center gap-2">
							<Tag color={assetContract?.consumable === false ? "orange" : "green"}>{contractState}</Tag>
							<span className="truncate text-xs text-slate-500">{assetContract?.governanceStatus || dataset.governanceStatus || "-"}</span>
						</div>
					</div>
				</div>
			</div>
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
								assetContract={assetContract}
								schemaContract={schemaContract}
								contractLoading={contractLoading}
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
						key: "governance",
						label: "治理责任",
						children: dataset.__source === "openmetadata" ? (
							<OpenMetadataGovernanceTab dataset={dataset} onChanged={setDataset} />
						) : dataset.__legacyDatasetId ? (
							<LegacyGovernanceNotice dataset={dataset} />
						) : (
							<div className="py-4 text-sm text-slate-500">暂无治理责任数据。</div>
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
						children: <AssetAccessTab dataset={dataset} assetContract={assetContract} contractLoading={contractLoading} />,
					},
				]}
			/>
		</div>
	);
}

const resolveProfileSummary = (profileJson?: string): { rowCount?: number; columnCount?: number; profileDate?: string } => {
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
	assetContract,
	schemaContract,
	contractLoading,
}: {
	dataset: Record<string, any>;
	assetContract?: Record<string, any> | null;
	schemaContract?: Record<string, any> | null;
	contractLoading?: boolean;
}) {
	const router = useRouter();
	const asset = assetContract || dataset;
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
	const missingFields = Array.isArray(assetContract?.missingGovernanceFields) ? assetContract?.missingGovernanceFields : [];
	const tags: any[] = Array.isArray(dataset.__tags) ? dataset.__tags : [];
	const profile = resolveProfileSummary(dataset.__profileJson);

	const governanceActions = readiness.state === "BLOCKED" || readiness.state === "WARNING" ? (
		<Space size={4} wrap>
			<Button size="small" onClick={() => router.push("/governance/quality")}>质量规则</Button>
			<Button size="small" onClick={() => router.push("/governance/asset-grants")}>授权管理</Button>
		</Space>
	) : null;

	return (
		<div className="space-y-3 py-2">
			{dataset.__source === "openmetadata" && (
				<Tag color="blue">OpenMetadata主目录</Tag>
			)}
			<Alert
				type={readiness.state === "BLOCKED" ? "warning" : readiness.state === "READY" ? "success" : "info"}
				showIcon
				message={`资产状态：${readiness.label}`}
				description={
					contractLoading
						? "正在读取资产治理合同..."
						: readiness.reasons.length
							? readiness.reasons.join("；")
							: "密级、主题域、归属部门和生命周期满足引用前置条件。"
				}
				action={governanceActions}
			/>
			<Descriptions bordered size="small" column={2}>
				<Descriptions.Item label="仓库分层">{dataset.warehouseLayer ?? "-"}</Descriptions.Item>
				<Descriptions.Item label="密级">{dataset.classification ?? "-"}</Descriptions.Item>
				<Descriptions.Item label="负责人">{dataset.owner ?? "-"}</Descriptions.Item>
				<Descriptions.Item label="所属部门">{dataset.ownerDept ?? "-"}</Descriptions.Item>
				<Descriptions.Item label="类型">{dataset.type ?? "-"}</Descriptions.Item>
				<Descriptions.Item label="生命周期">{dataset.lifecycleStatus ?? "-"}</Descriptions.Item>
				<Descriptions.Item label="技术表">{dataset.hiveDatabase && dataset.hiveTable ? `${dataset.hiveDatabase}.${dataset.hiveTable}` : "-"}</Descriptions.Item>
				<Descriptions.Item label="主题域">{dataset.domainName ?? dataset.domain ?? "-"}</Descriptions.Item>
				<Descriptions.Item label="治理状态">{dataset.governanceStatus ?? "-"}</Descriptions.Item>
				<Descriptions.Item label="映射状态">{dataset.matchStatus ?? "-"}</Descriptions.Item>
				<Descriptions.Item label="字段合同">{schemaContract?.columnCount ?? dataset.columnCount ?? "-"}</Descriptions.Item>
				<Descriptions.Item label="资产来源">{assetContract?.metadataSource ?? dataset.__source ?? "-"}</Descriptions.Item>
				{profile.rowCount != null && (
					<Descriptions.Item label="数据行数">{profile.rowCount.toLocaleString()}</Descriptions.Item>
				)}
				{profile.profileDate && (
					<Descriptions.Item label="最近采样">{String(profile.profileDate).slice(0, 19)}</Descriptions.Item>
				)}
			</Descriptions>
			{tags.length > 0 && (
				<div className="flex flex-wrap items-center gap-1">
					<span className="text-xs text-slate-500 mr-1">标签：</span>
					{tags.map((t: any, i: number) => {
						const label = t?.tagFQN ?? t?.name ?? t?.label ?? String(t);
						const shortLabel = String(label).split(".").pop() ?? label;
						return <Tag key={i} title={label}>{shortLabel}</Tag>;
					})}
				</div>
			)}
			{missingFields.length ? (
				<Alert type="warning" showIcon message="治理字段待补齐" description={missingFields.join("、")} />
			) : null}
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
	const missingFields = Array.isArray(assetContract?.missingGovernanceFields) ? assetContract?.missingGovernanceFields : [];
	const schemaSource = schemaContract?.schemaSource || dataset.__source || "-";
	const columnCount = schemaContract?.columnCount ?? dataset.columnCount ?? (Array.isArray(columns) ? columns.length : undefined);
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
				<Descriptions.Item label="合同状态">{assetContract?.consumable === false ? "不可引用" : assetContract ? "可引用" : "读取中"}</Descriptions.Item>
				<Descriptions.Item label="字段来源">{schemaSource}</Descriptions.Item>
				<Descriptions.Item label="字段数量">{columnCount ?? "未同步"}</Descriptions.Item>
				<Descriptions.Item label="资产键">{assetContract?.assetKey || dataset.__fqn || dataset.id || "-"}</Descriptions.Item>
				<Descriptions.Item label="授权资产">{assetContract?.grantAssetType && assetContract?.grantAssetId ? `${assetContract.grantAssetType}:${assetContract.grantAssetId}` : "-"}</Descriptions.Item>
				<Descriptions.Item label="物理对象">{dataset.hiveDatabase && dataset.hiveTable ? `${dataset.hiveDatabase}.${dataset.hiveTable}` : dataset.__fqn || "-"}</Descriptions.Item>
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
		{ key: "grantAsset", label: "授权资产", value: assetContract?.grantAssetType && assetContract?.grantAssetId ? `${assetContract.grantAssetType}:${assetContract.grantAssetId}` : undefined },
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

function LegacyGovernanceNotice({ dataset }: { dataset: Record<string, any> }) {
	return (
		<div className="space-y-4 py-2">
			<Alert
				type="info"
				showIcon
				message="这是 DTS 原生资产"
				description="当前资产未进入 OpenMetadata 主目录扩展编辑链路；基础属性仍由原数据资产台账维护，质量和指标关系请在“质量与SLA”页查看。"
			/>
			<Descriptions bordered size="small" column={2}>
				<Descriptions.Item label="密级">{dataset.classification || "-"}</Descriptions.Item>
				<Descriptions.Item label="仓库分层">{dataset.warehouseLayer || "-"}</Descriptions.Item>
				<Descriptions.Item label="负责人">{dataset.owner || "-"}</Descriptions.Item>
				<Descriptions.Item label="归属部门">{dataset.ownerDept || "-"}</Descriptions.Item>
				<Descriptions.Item label="生命周期">{dataset.lifecycleStatus || "-"}</Descriptions.Item>
				<Descriptions.Item label="治理状态">{dataset.governanceStatus || "-"}</Descriptions.Item>
			</Descriptions>
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
					<div className="mt-1 text-sm font-semibold text-slate-900">{schemaContract?.columnCount ?? dataset.columnCount ?? "未同步"}</div>
				</div>
				<div className="rounded-lg border border-slate-200 bg-white px-3 py-2">
					<div className="text-xs text-slate-500">发布前状态</div>
					<div className="mt-1 text-sm font-semibold text-slate-900">{assetContract?.consumable === false ? "治理阻断" : "待质量校验"}</div>
				</div>
			</div>
			{hasLegacyDataset ? (
				<DatasetGovernanceTab datasetId={datasetId} />
			) : (
				<Alert type="warning" showIcon message="未映射治理资产" description="请先在治理责任页补齐映射、密级、主题域和负责人，再配置质量规则与 SLA。" />
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

function MetadataJsonBlock({ title, value }: { title: string; value?: string }) {
	if (!value) {
		return <Alert type="info" showIcon message={`${title} 未同步`} />;
	}
	let text = value;
	try {
		text = JSON.stringify(JSON.parse(value), null, 2);
	} catch {
		// keep original text
	}
	return (
		<div>
			<div className="mb-2 text-sm font-medium text-slate-700">{title}</div>
			<pre className="max-h-72 overflow-auto rounded border border-slate-200 bg-slate-950 p-3 text-xs text-slate-100">
				{text}
			</pre>
		</div>
	);
}

function OpenMetadataGovernanceTab({
	dataset,
	onChanged,
}: {
	dataset: Record<string, any>;
	onChanged: (next: Record<string, any>) => void;
}) {
	const [form] = Form.useForm();
	const [saving, setSaving] = useState(false);

	useEffect(() => {
		form.setFieldsValue({
			classification: dataset.classification,
			warehouseLayer: dataset.warehouseLayer,
			ownerDept: dataset.ownerDept,
			businessOwner: dataset.owner,
			lifecycleStatus: dataset.lifecycleStatus,
			enabled: dataset.lifecycleStatus !== "DISABLED",
			securityPolicyRefs: dataset.securityPolicyRefs,
		});
	}, [dataset, form]);

	const save = async () => {
		const values = await form.validateFields();
		setSaving(true);
		try {
			const detail: any = await updateCatalogAssetV2Governance(String(dataset.id), values);
			const asset = detail?.asset || {};
			onChanged({
				...dataset,
				classification: asset.classification,
				warehouseLayer: asset.warehouseLayer,
				ownerDept: asset.ownerDept,
				owner: asset.owner,
				lifecycleStatus: asset.lifecycleStatus,
				governanceStatus: asset.governanceStatus,
				securityPolicyRefs: asset.securityPolicyRefs,
			});
			message.success("治理扩展已保存");
		} finally {
			setSaving(false);
		}
	};

	return (
		<div className="space-y-4 py-2">
			<Alert
				type="info"
				showIcon
				message="治理属性保存在 DTS 扩展层"
				description="OpenMetadata 继续作为技术资产主目录；密级、归属部门、生命周期、权限/脱敏/行过滤引用由 DTS 维护。"
			/>
			<Form form={form} layout="vertical">
				<div className="grid gap-3 md:grid-cols-2">
					<Form.Item label="密级" name="classification">
						<Select
							allowClear
							options={[
								{ label: "公开", value: "PUBLIC" },
								{ label: "内部", value: "INTERNAL" },
								{ label: "秘密", value: "SECRET" },
								{ label: "机密", value: "CONFIDENTIAL" },
							]}
						/>
					</Form.Item>
					<Form.Item label="仓库分层" name="warehouseLayer">
						<Select
							allowClear
							options={["SOURCE", "ODS", "STG", "DWD", "DIM", "DWS", "ADS"].map((value) => ({ label: value, value }))}
						/>
					</Form.Item>
					<Form.Item label="归属部门" name="ownerDept">
						<Input allowClear />
					</Form.Item>
					<Form.Item label="业务负责人" name="businessOwner">
						<Input allowClear />
					</Form.Item>
					<Form.Item label="生命周期" name="lifecycleStatus">
						<Select
							allowClear
							options={[
								{ label: "启用", value: "ACTIVE" },
								{ label: "观察", value: "STALE" },
								{ label: "下线", value: "DISABLED" },
							]}
						/>
					</Form.Item>
					<Form.Item label="资产启用" name="enabled" valuePropName="checked">
						<Switch />
					</Form.Item>
				</div>
				<Form.Item label="权限 / 脱敏 / 行过滤引用" name="securityPolicyRefs">
					<Input.TextArea rows={4} placeholder="例如 grant:<id>, masking:<id>, row-filter:<id>，或 JSON 引用清单" />
				</Form.Item>
				<Button type="primary" onClick={() => void save()} loading={saving}>
					保存治理扩展
				</Button>
			</Form>
			{dataset.__legacyDatasetId ? (
				<Alert type="info" showIcon message="质量运行、治理健康和关联指标已移到“质量与SLA”页。" />
			) : (
				<Alert type="warning" showIcon message="未映射到 legacy dataset，治理健康和质量规则暂不可用。" />
			)}
		</div>
	);
}

function DatasetFieldsTab({ datasetId, columns }: { datasetId: string; columns?: any[] }) {
	const [fields, setFields] = useState<any[]>([]);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		if (Array.isArray(columns)) {
			setFields(
				columns.map((item) => ({
					name: item.name,
					dataType: item.dataType,
					comment: item.description,
					tableName: item.omColumnFqn || item.source,
				})),
			);
			setLoading(false);
			return;
		}
		void getDatasetFields(datasetId)
			.then((f: any) => setFields(Array.isArray(f) ? f : []))
			.catch(() => setFields([]))
			.finally(() => setLoading(false));
	}, [datasetId, columns]);

	if (loading) return <div className="py-4"><Spin /></div>;
	if (!fields.length) {
		return <div className="py-4 text-sm text-slate-500">暂无字段信息（未同步或数据集无表结构）。</div>;
	}

	return (
		<CompactTable
			size="small"
			rowKey={(_, idx) => String(idx)}
			dataSource={fields}
			pagination={false}
			columns={[
				{ title: "字段名", dataIndex: "name", render: (v) => <span className="font-mono text-xs">{v}</span> , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
				{ title: "类型", dataIndex: "dataType", width: 120 },
				{ title: "描述", dataIndex: "comment", render: (v) => v ?? "-" },
				{ title: "所属表", dataIndex: "tableName", render: (v) => v ?? "-" , sorter: (a, b) => (a.tableName || "").localeCompare(b.tableName || "") },
			]}
		/>
	);
}

function DatasetGovernanceTab({ datasetId }: { datasetId: string }) {
	const [health, setHealth] = useState<Record<string, any> | null>(null);
	const [loading, setLoading] = useState(true);
	const [indicators, setIndicators] = useState<any[]>([]);

	useEffect(() => {
		void Promise.all([
			getDatasetGovernanceHealth(datasetId)
				.then((h: any) => setHealth(h ?? null))
				.catch(() => setHealth(null)),
			getDatasetIndicatorDeps(datasetId)
				.then((r: any) => {
					const list = Array.isArray(r?.data?.data) ? r.data.data
						: Array.isArray(r?.data) ? r.data
						: Array.isArray(r) ? r
						: [];
					setIndicators(list);
				})
				.catch(() => setIndicators([])),
		]).finally(() => setLoading(false));
	}, [datasetId]);

	if (loading) return <div className="py-4"><Spin /></div>;

	const score = health?.healthScore ?? health?.quality?.healthScore;

	return (
		<div className="space-y-4 py-2 text-sm">
			{score != null && (
				<div className="flex items-center gap-2">
					<span className="text-slate-500">健康分：</span>
					<span className="text-lg font-bold text-blue-600">{score}</span>
					{health?.healthLevel && <Tag>{health.healthLevel}</Tag>}
				</div>
			)}
			{health?.quality?.totalRuns != null && (
				<div className="grid grid-cols-3 gap-3">
					<div><span className="text-slate-500">总运行：</span>{health.quality.totalRuns}</div>
					<div><span className="text-slate-500">通过：</span><span className="text-green-600">{health.quality.passRuns ?? 0}</span></div>
					<div><span className="text-slate-500">失败：</span><span className="text-red-500">{health.quality.failRuns ?? 0}</span></div>
				</div>
			)}
			{!score && !health?.quality && <div className="text-slate-500">暂无治理健康数据。</div>}
			{indicators.length > 0 && (
				<div>
					<div className="mb-2 font-medium text-slate-700">关联指标（{indicators.length}）</div>
					<CompactTable
						size="small"
						rowKey={(_, i) => String(i)}
						dataSource={indicators}
						pagination={false}
						columns={[
							{ title: "指标名称", dataIndex: "name", render: (v: any) => v ?? "-" , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
							{ title: "类型", dataIndex: "type", width: 100, render: (v: any) => v ? <Tag>{v}</Tag> : "-" },
							{ title: "状态", dataIndex: "status", width: 90, render: (v: any) => v ? <Tag color={v === "PUBLISHED" ? "green" : "default"}>{v}</Tag> : "-" },
						]}
					/>
				</div>
			)}
		</div>
	);
}

function OpenMetadataLineageTab({ assetId }: { assetId: string }) {
	const [lineage, setLineage] = useState<any>(null);
	const [loading, setLoading] = useState(true);
	const [syncing, setSyncing] = useState(false);

	const loadLineage = async () => {
		setLoading(true);
		try {
			const result = await getCatalogAssetV2Lineage(assetId);
			setLineage(result || null);
		} catch {
			setLineage(null);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadLineage();
	}, [assetId]);

	const syncLineage = async () => {
		setSyncing(true);
		try {
			await syncCatalogAssetV2Lineage(assetId, { upstreamDepth: 2, downstreamDepth: 2 });
			await loadLineage();
		} catch {
			// global interceptor handles
		} finally {
			setSyncing(false);
		}
	};

	if (loading) return <div className="py-6"><Spin /></div>;

	const edges = Array.isArray(lineage?.edges) ? lineage.edges : [];
	if (!edges.length) {
		return (
			<div className="space-y-3 py-4 text-sm text-slate-500">
				<div>暂无OpenMetadata血缘缓存。</div>
				<Button size="small" onClick={() => void syncLineage()} loading={syncing}>同步OpenMetadata血缘</Button>
			</div>
		);
	}
	return (
		<div className="space-y-3 py-2">
			<div className="flex items-center justify-between">
				<Tag color="blue">OpenMetadata血缘缓存</Tag>
				<Button size="small" onClick={() => void syncLineage()} loading={syncing}>同步血缘</Button>
			</div>
			<CompactTable
				size="small"
				rowKey={(row: any, idx) => row.id || `${row.fromFqn}-${row.toFqn}-${idx}`}
				dataSource={edges}
				pagination={{ defaultPageSize: 10 }}
				columns={[
					{ title: "上游", dataIndex: "fromFqn", render: (v: any) => <span className="font-mono text-xs">{v || "-"}</span> },
					{ title: "下游", dataIndex: "toFqn", render: (v: any) => <span className="font-mono text-xs">{v || "-"}</span> },
					{ title: "来源", dataIndex: "source", width: 140, render: (v: any) => <Tag>{v || "openmetadata"}</Tag> },
					{ title: "类型", dataIndex: "edgeType", width: 120, render: (v: any) => v || "TABLE" },
				]}
			/>
		</div>
	);
}

function DatasetLineageImpactTab({ dataset }: { dataset: Record<string, any> }) {
	const router = useRouter();
	const hasOpenMetadataAsset = dataset.__source === "openmetadata" && dataset.id;
	const hasLegacyDataset = Boolean(dataset.__legacyDatasetId);
	return (
		<div className="space-y-4 py-2">
			<Alert
				type={hasOpenMetadataAsset || hasLegacyDataset ? "info" : "warning"}
				showIcon
				message="血缘与影响用于判断资产变更会影响哪些指标、数据产品和看板"
				description={
					hasOpenMetadataAsset && hasLegacyDataset
						? "当前资产同时具备 OpenMetadata 血缘缓存和 DTS 本地治理资产影响链路。"
						: hasOpenMetadataAsset
							? "当前仅有 OpenMetadata 血缘缓存，尚未映射到 DTS 本地治理影响链路。"
							: hasLegacyDataset
								? "当前使用 DTS 本地治理资产影响链路。"
								: "当前资产没有可用血缘证据。"
				}
				action={
					<Button
						size="small"
						onClick={() =>
							dataset.__legacyDatasetId
								? router.push(`/catalog/lineage/graph?datasetId=${encodeURIComponent(String(dataset.__legacyDatasetId))}`)
								: router.push("/catalog/lineage/graph")
						}
					>
						血缘图 →
					</Button>
				}
			/>
			{hasOpenMetadataAsset ? (
				<div>
					<div className="mb-2 text-sm font-medium text-slate-700">OpenMetadata 血缘缓存</div>
					<OpenMetadataLineageTab assetId={String(dataset.id)} />
				</div>
			) : null}
			{hasLegacyDataset ? (
				<div>
					<div className="mb-2 text-sm font-medium text-slate-700">DTS 本地影响分析</div>
					<DatasetLineageTab datasetId={String(dataset.__legacyDatasetId)} />
				</div>
			) : null}
			{!hasOpenMetadataAsset && !hasLegacyDataset ? (
				<div className="py-4 text-sm text-slate-500">未映射到DTS治理资产，暂无本地血缘图。</div>
			) : null}
		</div>
	);
}

function DatasetLineageTab({ datasetId }: { datasetId: string }) {
	const router = useRouter();
	const [impact, setImpact] = useState<any>(null);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		void getCatalogLineageImpact(datasetId, { direction: "BOTH", depth: 3 })
			.then((r: any) => setImpact(r ?? null))
			.catch(() => setImpact(null))
			.finally(() => setLoading(false));
	}, [datasetId]);

	const lineageNodes: ImpactNode[] = useMemo(
		() => (Array.isArray(impact?.nodes) ? (impact.nodes as ImpactNode[]) : []),
		[impact],
	);

	const lineageEdges: ImpactEdge[] = useMemo(
		() => (Array.isArray(impact?.edges) ? (impact.edges as ImpactEdge[]) : []),
		[impact],
	);

	if (loading) return <div className="py-6"><Spin /></div>;

	const lineageGraphUrl = `/catalog/lineage/graph?datasetId=${encodeURIComponent(datasetId)}`;

	if (!lineageNodes.length) {
		return (
			<div className="py-4 text-sm text-slate-500 space-y-2">
				<div>暂无血缘数据。</div>
				<Button type="link" size="small" className="px-0" onClick={() => router.push(lineageGraphUrl)}>
					前往血缘分析页 →
				</Button>
			</div>
		);
	}

	return (
		<div className="space-y-2">
			<LineageGraph
				nodes={lineageNodes}
				edges={lineageEdges}
				height={400}
				layoutDirection="LR"
				showMiniMap={false}
				showToolbar={false}
				emptyText="暂无血缘节点"
			/>
			<div className="text-right">
				<Button type="link" size="small" onClick={() => router.push(lineageGraphUrl)}>
					查看完整血缘分析 →
				</Button>
			</div>
		</div>
	);
}
