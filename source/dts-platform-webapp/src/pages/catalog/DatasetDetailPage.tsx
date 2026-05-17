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

const DETAIL_TAB_KEYS = ["overview", "fields", "lineage", "technical", "governance", "access"] as const;

const resolveDetailTabKey = (value?: string | null) =>
	DETAIL_TAB_KEYS.includes(value as any) ? String(value) : "overview";

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
	};
};

export default function DatasetDetailPage() {
	const { id } = useParams<{ id: string }>();
	const [searchParams] = useSearchParams();
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
				onChange={setActiveTab}
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
						key: "fields",
						label: "字段详情",
						children: (
							<DatasetFieldsTab
								datasetId={String(dataset.__legacyDatasetId || id)}
								columns={Array.isArray(schemaContract?.columns) ? schemaContract?.columns : dataset.__columns}
							/>
						),
					},
					{
						key: "lineage",
						label: "血缘图",
						children: dataset.__legacyDatasetId ? (
							<DatasetLineageTab datasetId={String(dataset.__legacyDatasetId)} />
						) : dataset.__source === "openmetadata" ? (
							<OpenMetadataLineageTab assetId={String(dataset.id)} />
						) : (
							<div className="py-4 text-sm text-slate-500">未映射到DTS治理资产，暂无本地血缘图。</div>
						),
					},
					{
						key: "technical",
						label: "技术详情",
						children: <DatasetTechnicalTab dataset={dataset} assetContract={assetContract} schemaContract={schemaContract} />,
					},
					{
						key: "governance",
						label: "治理扩展",
						children: dataset.__source === "openmetadata" ? (
							<OpenMetadataGovernanceTab dataset={dataset} onChanged={setDataset} />
						) : dataset.__legacyDatasetId ? (
							<DatasetGovernanceTab datasetId={String(dataset.__legacyDatasetId)} />
						) : (
							<div className="py-4 text-sm text-slate-500">暂无治理健康数据。</div>
						),
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
			</Descriptions>
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
				<DatasetGovernanceTab datasetId={String(dataset.__legacyDatasetId)} />
			) : (
				<Alert type="warning" showIcon message="未映射到 legacy dataset，治理健康和权限规则只能展示 DTS 扩展层。" />
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
				pagination={{ pageSize: 8 }}
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

function DatasetLineageTab({ datasetId }: { datasetId: string }) {
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

	if (!lineageNodes.length) {
		return (
			<div className="py-4 text-sm text-slate-500 space-y-2">
				<div>暂无血缘数据。</div>
				<a href={`/catalog/lineage/graph`} className="text-blue-600 underline text-xs">
					前往血缘分析页 →
				</a>
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
				<a href={`/catalog/lineage/graph`} className="text-xs text-blue-500 hover:underline">
					查看完整血缘分析 →
				</a>
			</div>
		</div>
	);
}
