import { useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { Database, FolderTree, ShieldCheck, Sparkles } from "lucide-react";
import { Alert, Button, Card, Form, Input, Modal, Select, Space, Table, Tabs, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import {
	PlatformFilterBar,
	PlatformMetaPill,
	PlatformPageHero,
	PlatformSectionCard,
	PlatformSummaryCards,
} from "@/components/console-page";
import { EmptyState } from "@/components/empty-state";
import {
	getCatalogReconciliation,
	getCatalogLineageImpact,
	getClassificationMaskingLinkage,
	getDataset,
	getDatasetGovernanceHealth,
	getTechMetadataTableDetail,
	listDatasetGrants,
	listDatasets,
	listDomains,
	updateDataset,
} from "@/api/platformApi";
import { useRouter } from "@/routes/hooks";

const { Text } = Typography;

type AssetRow = {
	id: string;
	name: string;
	type: string;
	sourceId?: string;
	domainId?: string;
	domain?: string;
	classification?: string;
	ownerDept?: string;
	owner?: string;
	tags?: string;
	description?: string;
	hiveDatabase?: string;
	hiveTable?: string;
	warehouseLayer?: string;
	updatedAt?: string;
	snapshotTime?: string;
	lifecycleStatus?: string;
	status?: string;
	editable?: boolean;
};

type TableDetail = {
	enabled?: boolean;
	found?: boolean;
	message?: string;
	entity?: Record<string, any>;
};

type ColumnRow = {
	key: string;
	name: string;
	type: string;
	comment: string;
	status?: string;
};

type GovernanceImpact = {
	grantsCount: number;
	lineageNodeCount: number;
	lineageEdgeCount: number;
};

type DatasetSecurityLinkage = {
	classification?: string;
	requiresMasking?: boolean;
	maskingRuleCount?: number;
	conflict?: boolean;
	effectiveRules?: Array<{ id?: string; column?: string; function?: string; args?: string }>;
	suggestions?: string[];
};

type GovernanceHealth = {
	healthScore?: number;
	healthLevel?: string;
	quality?: {
		totalRuns?: number;
		passRuns?: number;
		failRuns?: number;
		runningRuns?: number;
		latestRunAt?: string;
		latestStatus?: string;
		failureTop?: Array<{ category?: string; count?: number }>;
		trend?: Array<{ date?: string; total?: number; passed?: number; failed?: number }>;
	};
	issues?: {
		total?: number;
		open?: number;
		closed?: number;
		overdue?: number;
		top?: Array<{ id?: string; title?: string; status?: string; priority?: string; severity?: string; dueAt?: string }>;
	};
	links?: {
		qualityRulesPath?: string;
		qualityReportPath?: string;
		issuesPath?: string;
	};
};

type ReconciliationAssertion = {
	code?: string;
	name?: string;
	passed?: boolean;
	severity?: string;
	detail?: string;
	suggestion?: string;
};

type ReconciliationResult = {
	generatedAt?: string;
	assertionCount?: number;
	failedCount?: number;
	errorCount?: number;
	warningCount?: number;
	assertions?: ReconciliationAssertion[];
	regressionChecklist?: Array<{ code?: string; name?: string; route?: string; description?: string }>;
};

const TYPE_OPTIONS = [
	{ label: "全部类型", value: "ALL" },
	{ label: "Hive", value: "HIVE" },
	{ label: "JDBC", value: "JDBC" },
	{ label: "文件", value: "FILE" },
];

const CLASSIFICATION_OPTIONS = [
	{ label: "全部密级", value: "ALL" },
	{ label: "公开", value: "PUBLIC" },
	{ label: "内部", value: "INTERNAL" },
	{ label: "秘密", value: "SECRET" },
	{ label: "机密", value: "CONFIDENTIAL" },
];

const LAYER_OPTIONS = [
	{ label: "全部分层", value: "ALL" },
	{ label: "ODS", value: "ODS" },
	{ label: "DWD", value: "DWD" },
	{ label: "DWS", value: "DWS" },
	{ label: "ADS", value: "ADS" },
];

const DATASET_FILTER_STORAGE_KEY = "catalog.asset.filter.v1";
const SECURITY_LINKAGE_VERSION_KEY = "catalog.security.linkage.version";
const SECURITY_LINKAGE_EVENT = "catalog-security-linkage-updated";

const CLASSIFICATION_LABEL: Record<string, string> = {
	PUBLIC: "公开",
	INTERNAL: "内部",
	SECRET: "秘密",
	CONFIDENTIAL: "机密",
};

const layerColor = (layer?: string) => {
	const key = String(layer || "").toUpperCase();
	if (key === "ODS") return "default";
	if (key === "DWD") return "blue";
	if (key === "DWS") return "cyan";
	if (key === "ADS") return "green";
	if (key === "DIM") return "purple";
	return "processing";
};

const buildColumnRows = (detail?: TableDetail | null): ColumnRow[] => {
	if (!detail?.entity) return [];
	const columns = Array.isArray(detail.entity.columns) ? detail.entity.columns : [];
	return columns.map((item: any, idx: number) => ({
		key: String(item?.name || item?.displayName || idx),
		name: String(item?.name || item?.displayName || "-").trim(),
		type: String(item?.dataType || item?.dataTypeDisplay || "-").trim(),
		comment: String(item?.description || item?.comment || "").trim(),
		status: String(item?.status || "").trim(),
	}));
};

export default function Page() {
	const router = useRouter();
	const [profileForm] = Form.useForm();
	const watchOwner = Form.useWatch("owner", profileForm);
	const watchTags = Form.useWatch("tags", profileForm);
	const watchDescription = Form.useWatch("description", profileForm);
	const [keyword, setKeyword] = useState("");
	const [domain, setDomain] = useState<string | undefined>();
	const [assetType, setAssetType] = useState<string>("ALL");
	const [classification, setClassification] = useState<string>("ALL");
	const [warehouseLayer, setWarehouseLayer] = useState<string>("ALL");
	const [loading, setLoading] = useState(false);
	const [records, setRecords] = useState<AssetRow[]>([]);
	const [pageState, setPageState] = useState({ page: 1, size: 10, total: 0 });
	const [domains, setDomains] = useState<{ id: string; name: string }[]>([]);
	const [detailOpen, setDetailOpen] = useState(false);
	const [detailLoading, setDetailLoading] = useState(false);
	const [detailRow, setDetailRow] = useState<AssetRow | null>(null);
	const [detailDataset, setDetailDataset] = useState<Record<string, any> | null>(null);
	const [tableDetail, setTableDetail] = useState<TableDetail | null>(null);
	const [impact, setImpact] = useState<GovernanceImpact>({ grantsCount: 0, lineageNodeCount: 0, lineageEdgeCount: 0 });
	const [securityLinkage, setSecurityLinkage] = useState<DatasetSecurityLinkage | null>(null);
	const [governanceHealth, setGovernanceHealth] = useState<GovernanceHealth | null>(null);
	const [reconciliation, setReconciliation] = useState<ReconciliationResult | null>(null);
	const [reconciliationLoading, setReconciliationLoading] = useState(false);
	const [savingProfile, setSavingProfile] = useState(false);
	const requestSeqRef = useRef(0);

	useEffect(() => {
			try {
			const raw = localStorage.getItem(DATASET_FILTER_STORAGE_KEY);
			if (!raw) return;
			const saved = JSON.parse(raw);
			setKeyword(typeof saved?.keyword === "string" ? saved.keyword : "");
			setDomain(typeof saved?.domain === "string" && saved.domain ? saved.domain : undefined);
			setAssetType(typeof saved?.assetType === "string" && saved.assetType ? saved.assetType : "ALL");
			setClassification(typeof saved?.classification === "string" && saved.classification ? saved.classification : "ALL");
			setWarehouseLayer(typeof saved?.warehouseLayer === "string" && saved.warehouseLayer ? saved.warehouseLayer : "ALL");
		} catch {
			// ignore malformed cache
		}
	}, []);

	useEffect(() => {
		void loadDomains();
	}, []);

	useEffect(() => {
		void loadReconciliation();
	}, []);

	useEffect(() => {
		const timer = window.setTimeout(() => {
			void loadDatasets(1, pageState.size);
		}, 280);
		return () => window.clearTimeout(timer);
	}, [keyword, domain, assetType, classification, warehouseLayer, pageState.size]);

	useEffect(() => {
		const payload = {
			keyword,
			domain: domain || "",
			assetType,
			classification,
			warehouseLayer,
		};
		localStorage.setItem(DATASET_FILTER_STORAGE_KEY, JSON.stringify(payload));
	}, [keyword, domain, assetType, classification, warehouseLayer]);

	const domainMap = useMemo(() => {
		return new Map(domains.map((item) => [item.id, item.name]));
	}, [domains]);

	const domainOptions = useMemo(() => {
		return [
			{ label: "全部主题域", value: "ALL" },
			...domains.map((item) => ({ label: item.name, value: item.id })),
		];
	}, [domains]);

	const loadDomains = async () => {
		try {
			const resp: any = await listDomains(0, 200, "");
			const list = Array.isArray(resp?.content) ? resp.content : [];
			setDomains(
				list
					.map((item: any) => ({ id: String(item.id || ""), name: String(item.name || "").trim() }))
					.filter((item: any) => item.id && item.name),
			);
		} catch (error: any) {
			toast.error(error?.message || "主题域加载失败");
		}
	};

	const loadReconciliation = async () => {
		setReconciliationLoading(true);
		try {
			const result: any = await getCatalogReconciliation(20);
			setReconciliation((result || null) as ReconciliationResult | null);
		} catch (error: any) {
			const message = String(error?.message || "");
			if (!message.toLowerCase().includes("403") && !message.toLowerCase().includes("forbidden")) {
				toast.error(message || "回归核对加载失败");
			}
			setReconciliation(null);
		} finally {
			setReconciliationLoading(false);
		}
	};

	const loadDatasets = async (page = 1, size = 10) => {
		const reqId = ++requestSeqRef.current;
		setLoading(true);
		try {
			const resp: any = await listDatasets({
				page: page - 1,
				size,
				keyword: keyword.trim() || undefined,
				domainId: domain && domain !== "ALL" ? domain : undefined,
				type: assetType === "ALL" ? undefined : assetType,
				classification: classification === "ALL" ? undefined : classification,
				warehouseLayer: warehouseLayer === "ALL" ? undefined : warehouseLayer,
				sortBy: "lastModifiedDate",
				sortDir: "desc",
			});
			if (reqId !== requestSeqRef.current) {
				return;
			}
			const content = Array.isArray(resp?.content) ? resp.content : [];
			setRecords(
				content.map((item: any) => ({
					id: String(item.id || ""),
					name: item.name || "-",
					type: item.type || "-",
					sourceId: item.sourceId ? String(item.sourceId) : undefined,
					domainId: item.domainId ? String(item.domainId) : undefined,
					domain: item.domainName || (item.domainId ? domainMap.get(String(item.domainId)) : undefined),
					classification: item.classification || undefined,
					ownerDept: item.ownerDept || undefined,
					owner: item.owner || item.ownerDept || "-",
					tags: item.tags || undefined,
					description: item.description || undefined,
					hiveDatabase: item.hiveDatabase || undefined,
					hiveTable: item.hiveTable || undefined,
					warehouseLayer: item.warehouseLayer || undefined,
					status: item.enabled === false ? "停用" : "启用",
					updatedAt: item.lastModifiedDate || item.createdDate || undefined,
					snapshotTime: item.snapshotTime || undefined,
					lifecycleStatus: item.lifecycleStatus || undefined,
					editable: item.editable !== false,
				})),
			);
			setPageState({
				page: Number(resp?.page ?? page - 1) + 1,
				size: Number(resp?.size ?? size),
				total: Number(resp?.total ?? 0),
			});
		} catch (error: any) {
			if (reqId !== requestSeqRef.current) {
				return;
			}
			toast.error(error?.message || "资产加载失败");
			setRecords([]);
		} finally {
			if (reqId === requestSeqRef.current) {
				setLoading(false);
			}
		}
	};

	const loadSecurityLinkage = async (datasetId?: string, silent = false) => {
		if (!datasetId) {
			setSecurityLinkage(null);
			return;
		}
		try {
			const linkage: any = await getClassificationMaskingLinkage(datasetId);
			setSecurityLinkage((linkage || null) as DatasetSecurityLinkage | null);
		} catch (error: any) {
			if (!silent) {
				toast.error(error?.message || "密级与脱敏策略加载失败");
			}
			setSecurityLinkage(null);
		}
	};

	const loadGovernanceHealth = async (datasetId?: string, silent = false) => {
		if (!datasetId) {
			setGovernanceHealth(null);
			return;
		}
		try {
			const payload: any = await getDatasetGovernanceHealth(datasetId);
			setGovernanceHealth((payload || null) as GovernanceHealth | null);
		} catch (error: any) {
			if (!silent) {
				toast.error(error?.message || "治理健康信息加载失败");
			}
			setGovernanceHealth(null);
		}
	};

	useEffect(() => {
		const refresh = () => {
			if (detailRow?.id) {
				void loadSecurityLinkage(detailRow.id, true);
				void loadGovernanceHealth(detailRow.id, true);
			}
			void loadDatasets(pageState.page, pageState.size);
		};
		const onStorage = (event: StorageEvent) => {
			if (event.key === SECURITY_LINKAGE_VERSION_KEY) {
				refresh();
			}
		};
		const onLocalEvent = () => refresh();
		window.addEventListener("storage", onStorage);
		window.addEventListener(SECURITY_LINKAGE_EVENT, onLocalEvent as EventListener);
		return () => {
			window.removeEventListener("storage", onStorage);
			window.removeEventListener(SECURITY_LINKAGE_EVENT, onLocalEvent as EventListener);
		};
	}, [detailRow?.id, pageState.page, pageState.size, keyword, domain, assetType, classification, warehouseLayer]);

	const openDetail = async (row: AssetRow) => {
		if (!row?.id) return;
		setDetailRow(row);
		setDetailDataset(null);
		setTableDetail(null);
		setSecurityLinkage(null);
		setGovernanceHealth(null);
		setImpact({ grantsCount: 0, lineageNodeCount: 0, lineageEdgeCount: 0 });
		setDetailOpen(true);
		setDetailLoading(true);
		try {
			const [datasetResp, tableResp, grantsResp, lineageResp, linkageResp, governanceResp] = await Promise.allSettled([
				getDataset(row.id),
				getTechMetadataTableDetail(`catalog:${row.id}`),
				listDatasetGrants(row.id),
				getCatalogLineageImpact(row.id, { direction: "BOTH", depth: 2 }),
				getClassificationMaskingLinkage(row.id),
				getDatasetGovernanceHealth(row.id),
				]);
				if (datasetResp.status === "fulfilled") {
					const ds: any = datasetResp.value || null;
					setDetailDataset(ds);
					profileForm.setFieldsValue({
						owner: ds?.owner || "",
						tags: ds?.tags || "",
						description: ds?.description || "",
				});
			}
			if (tableResp.status === "fulfilled") {
				setTableDetail(tableResp.value || null);
			}
			if (grantsResp.status === "fulfilled" || lineageResp.status === "fulfilled") {
				const grants = grantsResp.status === "fulfilled" && Array.isArray(grantsResp.value) ? grantsResp.value.length : 0;
				const lineage: any = lineageResp.status === "fulfilled" ? lineageResp.value : {};
				setImpact({
					grantsCount: grants,
					lineageNodeCount: Number(lineage?.nodeCount || 0),
					lineageEdgeCount: Number(lineage?.edgeCount || 0),
				});
			}
			if (linkageResp.status === "fulfilled") {
				setSecurityLinkage((linkageResp.value || null) as DatasetSecurityLinkage | null);
			}
			if (governanceResp.status === "fulfilled") {
				setGovernanceHealth((governanceResp.value || null) as GovernanceHealth | null);
			}
		} catch (error: any) {
			toast.error(error?.message || "加载资产字段失败");
			setTableDetail(null);
			setDetailDataset(null);
			setSecurityLinkage(null);
			setGovernanceHealth(null);
		} finally {
			setDetailLoading(false);
		}
	};

	const columnRows = useMemo(() => buildColumnRows(tableDetail), [tableDetail]);
	const columnStatusStats = useMemo(() => {
		let draft = 0;
		let active = 0;
		let other = 0;
		columnRows.forEach((row) => {
			const label = String(row.status || "").toUpperCase();
			if (label === "DRAFT") draft += 1;
			else if (label === "ACTIVE") active += 1;
			else other += 1;
		});
		return { draft, active, other };
	}, [columnRows]);

	const profileChanged = useMemo(() => {
		if (!detailDataset) return false;
		return (
			String(watchOwner || "") !== String(detailDataset.owner || "") ||
			String(watchTags || "") !== String(detailDataset.tags || "") ||
			String(watchDescription || "") !== String(detailDataset.description || "")
		);
	}, [detailDataset, watchOwner, watchTags, watchDescription]);

	const saveProfile = async () => {
		if (!detailDataset?.id) return;
		if (detailDataset.editable === false) {
			toast.warning("当前账号无该资产编辑权限");
			return;
		}
		const values = await profileForm.validateFields();
		const payload = {
			...detailDataset,
			owner: (values?.owner || "").trim(),
			tags: (values?.tags || "").trim(),
			description: (values?.description || "").trim(),
		};
		setSavingProfile(true);
		try {
			const saved: any = await updateDataset(String(detailDataset.id), payload);
			setDetailDataset(saved || payload);
			setDetailRow((prev) =>
				prev
					? {
							...prev,
							owner: payload.owner || prev.owner,
							tags: payload.tags || undefined,
							description: payload.description || undefined,
						}
					: prev,
			);
			toast.success("资产画像已保存");
			await loadDatasets(pageState.page, pageState.size);
		} catch (error: any) {
			toast.error(error?.message || "资产画像保存失败");
		} finally {
			setSavingProfile(false);
		}
	};

	const columnColumns: ColumnsType<ColumnRow> = [
		{ title: "字段", dataIndex: "name" },
		{ title: "类型", dataIndex: "type" },
		{
			title: "状态",
			dataIndex: "status",
			render: (value) => {
				const normalized = String(value || "").toUpperCase();
				if (!normalized) return <Tag>未知</Tag>;
				if (normalized === "DRAFT") return <Tag color="orange">草稿</Tag>;
				if (normalized === "ACTIVE") return <Tag color="green">正式</Tag>;
				return <Tag>{value}</Tag>;
			},
		},
		{ title: "备注", dataIndex: "comment" },
	];

	const columns: ColumnsType<AssetRow> = [
		{
			title: "资产名称",
			dataIndex: "name",
			render: (value) => value || "-",
		},
		{
			title: "类型",
			dataIndex: "type",
			render: (value) => (value ? <Tag>{value}</Tag> : "-"),
		},
		{
			title: "主题域",
			dataIndex: "domain",
			render: (value, row) => {
				if (value) return value;
				if (row.domainId) {
					return domainMap.get(row.domainId) || "-";
				}
				return "-";
			},
		},
		{
			title: "密级",
			dataIndex: "classification",
			render: (value) => {
				const key = String(value || "").toUpperCase();
				if (!key) return "-";
				return <Tag>{CLASSIFICATION_LABEL[key] || key}</Tag>;
			},
		},
		{
			title: "负责人",
			dataIndex: "owner",
			render: (value) => value || "-",
		},
		{
			title: "更新时间",
			dataIndex: "updatedAt",
			render: (value) => value || "-",
		},
		{
			title: "最近同步",
			dataIndex: "snapshotTime",
			render: (value) => value || "-",
		},
		{
			title: "同步状态",
			dataIndex: "lifecycleStatus",
			render: (value, row) => {
				const normalized = String(value || "").toUpperCase();
				if (normalized === "STALE" || row.status === "停用") return <Tag color="red">失效</Tag>;
				if (normalized === "SYNCED") return <Tag color="green">已同步</Tag>;
				return <Tag>未同步</Tag>;
			},
		},
		{
			title: "状态",
			dataIndex: "status",
			render: (value) => {
				if (!value) return "-";
				return <Tag color={value === "停用" ? "red" : "green"}>{value}</Tag>;
			},
		},
		{
			title: "操作",
			dataIndex: "actions",
			render: (_, row) => (
				<Button type="link" size="small" onClick={() => void openDetail(row)}>
					详情
				</Button>
			),
		},
	];

	const summaryCards = [
		{
			label: "资产总量",
			value: pageState.total,
			note: "当前筛选范围内的总资产记录",
			icon: <Database className="h-5 w-5" />,
		},
		{
			label: "主题域",
			value: domains.length,
			note: "资产归属主题域数量",
			icon: <FolderTree className="h-5 w-5" />,
			tone: "info" as const,
		},
		{
			label: "活跃资产",
			value: records.filter((item) => item.status === "启用").length,
			note: "当前页启用状态资产",
			icon: <Sparkles className="h-5 w-5" />,
			tone: "success" as const,
		},
		{
			label: "一致性失败",
			value: Number(reconciliation?.failedCount || 0),
			note: "发布前回归与一致性断言阻断数",
			icon: <ShieldCheck className="h-5 w-5" />,
			tone: "warning" as const,
		},
	];

	return (
		<div className="space-y-6">
			<PlatformPageHero
				title="资产地图"
				description="按主题域、资产类型、密级和分层组织资产门户，让全局盘点、详情核查和发布前回归都在一个页面里完成。"
				eyebrow="Catalog Overview"
				actions={
					<div className="flex flex-wrap items-center gap-2">
						<Button className="rounded-2xl" onClick={() => void loadDatasets(1, pageState.size)} loading={loading}>
							刷新资产
						</Button>
						<Button className="rounded-2xl" onClick={() => void loadReconciliation()} loading={reconciliationLoading}>
							刷新核对
						</Button>
					</div>
				}
				meta={
					<>
						<PlatformMetaPill>{domain ? `主题域 ${domain}` : "主题域全部"}</PlatformMetaPill>
						<PlatformMetaPill>{assetType === "ALL" ? "资产类型全部" : `资产类型 ${assetType}`}</PlatformMetaPill>
						<PlatformMetaPill>{classification === "ALL" ? "密级全部" : `密级 ${classification}`}</PlatformMetaPill>
					</>
				}
			/>

			<PlatformSummaryCards items={summaryCards} />

			<PlatformFilterBar>
				<div>
					<div className="text-sm font-semibold text-foreground">先筛资产，再打开详情或回归核对</div>
					<div className="mt-1 text-sm text-muted-foreground">
						筛选条件会同时影响资产列表和上方概览卡片，方便快速收敛治理范围。
					</div>
				</div>
				<div className="flex flex-wrap items-center gap-2">
					<Select
						allowClear
						placeholder="主题域"
						style={{ minWidth: 180 }}
						value={domain || "ALL"}
						onChange={(value) => setDomain(value === "ALL" ? undefined : value)}
						options={domainOptions}
					/>
					<Select
						allowClear
						placeholder="资产类型"
						style={{ minWidth: 180 }}
						value={assetType}
						onChange={(value) => setAssetType(value || "ALL")}
						options={TYPE_OPTIONS}
					/>
					<Select
						allowClear
						placeholder="密级"
						style={{ minWidth: 160 }}
						value={classification}
						onChange={(value) => setClassification(value || "ALL")}
						options={CLASSIFICATION_OPTIONS}
					/>
					<Select
						allowClear
						placeholder="分层"
						style={{ minWidth: 160 }}
						value={warehouseLayer}
						onChange={(value) => setWarehouseLayer(value || "ALL")}
						options={LAYER_OPTIONS}
					/>
					<Input
						placeholder="搜索资产名称 / 描述"
						style={{ width: 260 }}
						value={keyword}
						onChange={(event) => setKeyword(event.target.value)}
						allowClear
					/>
					<Button
						onClick={() => {
							setKeyword("");
							setDomain(undefined);
							setAssetType("ALL");
							setClassification("ALL");
							setWarehouseLayer("ALL");
						}}
					>
						重置筛选
					</Button>
				</div>
			</PlatformFilterBar>

			<PlatformSectionCard title="资产地图视图" description="用轻量概览卡片快速判断资产规模、主题域覆盖和活跃情况。">
				{records.length ? (
					<div className="grid gap-3 md:grid-cols-3">
						<Card size="small" title="资产总量">
							<div className="text-lg font-semibold">{pageState.total}</div>
							<div className="text-xs text-muted-foreground">已纳入治理的资产记录</div>
						</Card>
						<Card size="small" title="主题域">
							<div className="text-lg font-semibold">{domains.length}</div>
							<div className="text-xs text-muted-foreground">已配置主题域数量</div>
						</Card>
						<Card size="small" title="活跃资产">
							<div className="text-lg font-semibold">
								{records.filter((item) => item.status === "启用").length}
							</div>
							<div className="text-xs text-muted-foreground">当前页启用资产</div>
						</Card>
					</div>
				) : (
					<EmptyState title="暂无资产地图" description="请先完成元数据采集或同步资产数据。" />
				)}
			</PlatformSectionCard>

				<PlatformSectionCard title="资产列表" description="列表保持分页、详情和筛选联动，不改动原有资产详情链路。">
					{records.length ? (
					<Table
						rowKey="id"
						columns={columns}
						dataSource={records}
						loading={loading}
						pagination={{
							current: pageState.page,
							pageSize: pageState.size,
							total: pageState.total,
							showSizeChanger: true,
						}}
						onChange={(pagination) => {
							const nextPage = pagination.current || 1;
							const nextSize = pagination.pageSize || 10;
							void loadDatasets(nextPage, nextSize);
						}}
					/>
				) : (
					<EmptyState title="暂无资产" description="当前筛选条件下未找到资产。" />
					)}
				</PlatformSectionCard>
				<PlatformSectionCard
					title="发布前回归与一致性核对"
					description="在发布前先看断言阻断、风险建议和核心页面回归清单。"
					action={
						<Button loading={reconciliationLoading} onClick={() => void loadReconciliation()}>
							重新核对
						</Button>
					}
				>
					{reconciliation ? (
						<Space direction="vertical" size={12} className="w-full">
							<div className="grid gap-3 md:grid-cols-4">
								<Card size="small" title="断言总数">
									<div className="text-lg font-semibold">{Number(reconciliation.assertionCount || 0)}</div>
								</Card>
								<Card size="small" title="失败项">
									<div className="text-lg font-semibold text-red-600">{Number(reconciliation.failedCount || 0)}</div>
								</Card>
								<Card size="small" title="错误级">
									<div className="text-lg font-semibold text-red-600">{Number(reconciliation.errorCount || 0)}</div>
								</Card>
								<Card size="small" title="告警级">
									<div className="text-lg font-semibold text-amber-600">{Number(reconciliation.warningCount || 0)}</div>
								</Card>
							</div>
							{Array.isArray(reconciliation.assertions) && reconciliation.assertions.some((item) => item.passed === false) ? (
								<div className="rounded border border-amber-200 bg-amber-50 p-3 text-xs text-amber-700">
									{reconciliation.assertions
										.filter((item) => item.passed === false)
										.slice(0, 6)
										.map((item) => (
											<div key={item.code || item.name}>
												[{item.code || "-"}] {item.name || "未命名检查"}：{item.detail || "-"}；建议：{item.suggestion || "-"}
											</div>
										))}
								</div>
							) : (
								<Alert type="success" showIcon message="一致性断言通过，未发现阻断项。" />
							)}
							<div className="rounded border border-slate-200 bg-slate-50 p-3">
								<div className="mb-2 text-sm font-medium text-slate-700">核心页面回归清单</div>
								<Space direction="vertical" size={6} className="w-full">
									{Array.isArray(reconciliation.regressionChecklist) && reconciliation.regressionChecklist.length > 0 ? (
										reconciliation.regressionChecklist.map((item) => (
											<div key={item.code || item.name} className="flex items-center justify-between gap-3 text-xs text-slate-700">
												<div>
													<span className="font-medium">[{item.code || "-"}] {item.name || "-"}</span>
													<div className="text-slate-500">{item.description || "-"}</div>
												</div>
												<Button
													size="small"
													onClick={() => {
														if (item.route) router.push(item.route);
													}}
												>
													打开页面
												</Button>
											</div>
										))
									) : (
										<div className="text-xs text-slate-500">暂无回归清单</div>
									)}
								</Space>
							</div>
						</Space>
					) : (
						<EmptyState title="暂无核对结果" description="当前账号无权限或尚未执行核对。" />
					)}
				</PlatformSectionCard>
				<Modal
					title="资产字段详情"
				open={detailOpen}
				onCancel={() => setDetailOpen(false)}
				footer={<Button onClick={() => setDetailOpen(false)}>关闭</Button>}
				width={860}
			>
				<Tabs
					items={[
						{
							key: "base",
							label: "基础信息",
							children: (
								<Space direction="vertical" size={12} className="w-full">
									<div className="rounded border border-slate-100 bg-slate-50/60 px-3 py-2 text-xs text-slate-600">
										<div className="text-sm font-medium text-slate-700">{detailRow?.name || "未命名资产"}</div>
										<div className="mt-1 flex flex-wrap gap-2">
											<Tag>{detailDataset?.hiveDatabase || detailRow?.hiveDatabase || "-"}</Tag>
											<Tag>{detailDataset?.hiveTable || detailRow?.hiveTable || "-"}</Tag>
											<Tag color={layerColor(detailDataset?.warehouseLayer || detailRow?.warehouseLayer)}>
												{String(detailDataset?.warehouseLayer || detailRow?.warehouseLayer || "UNKNOWN").toUpperCase()}
											</Tag>
										</div>
									</div>
									<Form form={profileForm} layout="vertical" disabled={detailDataset?.editable === false || detailLoading}>
										<Form.Item label="负责人" name="owner">
											<Input placeholder="请输入负责人账号或姓名" />
										</Form.Item>
										<Form.Item label="标签（逗号分隔）" name="tags">
											<Input placeholder="如：patent,erp,core" />
										</Form.Item>
										<Form.Item label="描述" name="description">
											<Input.TextArea rows={3} placeholder="请输入资产说明" />
										</Form.Item>
									</Form>
									<Space size={8}>
										<Button
											type="primary"
											onClick={() => void saveProfile()}
											loading={savingProfile}
											disabled={!profileChanged || detailDataset?.editable === false}
										>
											保存画像
										</Button>
										{detailDataset?.editable === false ? (
											<Tag color="orange">当前账号仅可查看</Tag>
										) : (
											<Tag color="green">可编辑</Tag>
										)}
									</Space>
									{profileChanged ? (
										<Alert
											type="info"
											showIcon
											message={`保存后可能影响权限 ${impact.grantsCount} 条、血缘节点 ${impact.lineageNodeCount} 个、血缘关系 ${impact.lineageEdgeCount} 条。`}
										/>
									) : null}
								</Space>
							),
						},
						{
							key: "structure",
							label: "结构信息",
							children: (
								<Space direction="vertical" size={12} className="w-full">
									<div className="rounded border border-slate-100 bg-slate-50/60 px-3 py-2 text-xs text-slate-600">
										<div className="mt-1 flex flex-wrap gap-2">
											<Tag color="orange">草稿 {columnStatusStats.draft}</Tag>
											<Tag color="green">正式 {columnStatusStats.active}</Tag>
											<Tag>其他 {columnStatusStats.other}</Tag>
										</div>
									</div>
									<Table
										rowKey="key"
										columns={columnColumns}
										dataSource={columnRows}
										loading={detailLoading}
										size="small"
										pagination={false}
										scroll={{ y: 360 }}
									/>
									{tableDetail?.message ? <Text type="danger">{tableDetail.message}</Text> : null}
								</Space>
							),
						},
						{
							key: "governance",
							label: "治理状态",
							children: (
									<Space direction="vertical" size={12} className="w-full">
										<div className="grid gap-3 md:grid-cols-3">
										<Card size="small" title="权限授权">
											<div className="text-lg font-semibold">{impact.grantsCount}</div>
										</Card>
										<Card size="small" title="血缘节点">
											<div className="text-lg font-semibold">{impact.lineageNodeCount}</div>
										</Card>
											<Card size="small" title="血缘关系">
												<div className="text-lg font-semibold">{impact.lineageEdgeCount}</div>
											</Card>
											</div>
											<Card size="small" title="治理健康">
												<div className="mb-2 flex items-center gap-2 text-xs text-slate-600">
													<Tag color={governanceHealth?.healthLevel === "HEALTHY" ? "green" : governanceHealth?.healthLevel === "WARN" ? "gold" : "red"}>
														{governanceHealth?.healthLevel || "UNKNOWN"}
													</Tag>
													<span>健康分 {Number(governanceHealth?.healthScore ?? 0)}</span>
												</div>
												<div className="mb-2 text-xs text-slate-600">
													质量运行：总 {Number(governanceHealth?.quality?.totalRuns ?? 0)} / 成功 {Number(governanceHealth?.quality?.passRuns ?? 0)} / 失败 {Number(governanceHealth?.quality?.failRuns ?? 0)}
												</div>
												<div className="mb-2 text-xs text-slate-600">
													问题工单：总 {Number(governanceHealth?.issues?.total ?? 0)} / 打开 {Number(governanceHealth?.issues?.open ?? 0)} / 逾期 {Number(governanceHealth?.issues?.overdue ?? 0)}
												</div>
												<div className="mb-2 flex flex-wrap gap-2">
													{Array.isArray(governanceHealth?.quality?.failureTop) && governanceHealth?.quality?.failureTop.length > 0 ? (
														governanceHealth?.quality?.failureTop.map((item, idx) => (
															<Tag key={`fail-cat-${idx}`}>
																{item.category || "UNKNOWN"}: {Number(item.count || 0)}
															</Tag>
														))
													) : (
														<Tag color="green">近期开窗内无失败分类</Tag>
													)}
												</div>
												<Space size={8} wrap>
													<Button
														size="small"
														onClick={() => {
															if (governanceHealth?.links?.qualityReportPath) {
																router.push(governanceHealth.links.qualityReportPath);
															}
														}}
													>
														查看质量报告
													</Button>
													<Button
														size="small"
														onClick={() => {
															if (governanceHealth?.links?.qualityRulesPath) {
																router.push(governanceHealth.links.qualityRulesPath);
															}
														}}
													>
														查看质量运行
													</Button>
													<Button
														size="small"
														onClick={() => {
															if (governanceHealth?.links?.issuesPath) {
																router.push(governanceHealth.links.issuesPath);
															}
														}}
													>
														查看问题工单
													</Button>
												</Space>
											</Card>
												<Card size="small" title="密级与脱敏联动">
													<div className="mb-2 text-xs text-slate-600">
														当前密级：{securityLinkage?.classification || detailDataset?.classification || detailRow?.classification || "-"}
													{" -> "}生效脱敏策略：
													{Number(securityLinkage?.maskingRuleCount || 0)} 条
												</div>
											<div className="flex flex-wrap gap-2">
												{Array.isArray(securityLinkage?.effectiveRules) && securityLinkage?.effectiveRules.length > 0 ? (
													securityLinkage?.effectiveRules.slice(0, 8).map((rule, idx) => (
														<Tag key={rule.id || `${rule.column || "col"}-${idx}`}>
															{rule.column || "-"} / {rule.function || "-"}
														</Tag>
													))
												) : (
													<Tag>未配置</Tag>
												)}
											</div>
										</Card>
										{securityLinkage?.conflict ? (
											<Alert
												type="warning"
												showIcon
												message="当前密级与脱敏策略不一致"
												description={(securityLinkage?.suggestions || []).join("；") || "请补齐脱敏规则。"}
											/>
										) : null}
										<Alert
											type="info"
											showIcon
										message="治理状态用于评估修改影响面：包括授权范围、血缘传播范围、以及后续质量校验范围。"
									/>
								</Space>
							),
						},
					]}
				/>
			</Modal>
		</div>
	);
}
