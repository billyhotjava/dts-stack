import { ProfileOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Form, Input, Modal, Select, Space, Tabs, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import type { IndicatorDep } from "@/api/platformApi";
import {
	getCatalogAssetV2Contract,
	getCatalogLineageImpact,
	getClassificationMaskingLinkage,
	getDataset,
	getDatasetGovernanceHealth,
	getDatasetIndicatorDeps,
	getTechMetadataTableDetail,
	listDatasetGrants,
	listDatasets,
	listDomains,
	updateDataset,
} from "@/api/platformApi";
import { AssetTagPanel } from "@/components/catalog/tags/AssetTagPanel";
import { EmptyState } from "@/components/empty-state";
import { CompactTable } from "@/components/table";
import { useRouter } from "@/routes/hooks";
import { AssetGovernanceOverview } from "./AssetGovernanceOverview";
import { LIFECYCLE_STATUS_DICT, resolveEnumLabel } from "./assets/assetEnumLabels";
import { STALE_LIFECYCLE_STATUSES } from "./assetPortalUx.helpers";
import type {
	AssetRow,
	ColumnRow,
	DatasetSecurityLinkage,
	GovernanceHealth,
	GovernanceImpact,
	TableDetail,
} from "./assetDetailPage.types";
import {
	buildColumnRows,
	CLASSIFICATION_LABEL,
	CLASSIFICATION_OPTIONS,
	DATASET_FILTER_STORAGE_KEY,
	LAYER_OPTIONS,
	layerColor,
	SECURITY_LINKAGE_EVENT,
	SECURITY_LINKAGE_VERSION_KEY,
	TYPE_OPTIONS,
} from "./assetDetailPage.types";

const { Text } = Typography;

export default function AssetDetailPage() {
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
	const [assetContract, setAssetContract] = useState<Record<string, any> | null>(null);
	const [tableDetail, setTableDetail] = useState<TableDetail | null>(null);
	const [impact, setImpact] = useState<GovernanceImpact>({ grantsCount: 0, lineageNodeCount: 0, lineageEdgeCount: 0 });
	const [securityLinkage, setSecurityLinkage] = useState<DatasetSecurityLinkage | null>(null);
	const [governanceHealth, setGovernanceHealth] = useState<GovernanceHealth | null>(null);
	const [indicatorDeps, setIndicatorDeps] = useState<IndicatorDep[]>([]);
	const [savingProfile, setSavingProfile] = useState(false);
	const requestSeqRef = useRef(0);
	const detailRequestSeqRef = useRef(0);

	useEffect(() => {
		try {
			const raw = localStorage.getItem(DATASET_FILTER_STORAGE_KEY);
			if (!raw) return;
			const saved = JSON.parse(raw);
			setKeyword(typeof saved?.keyword === "string" ? saved.keyword : "");
			setDomain(typeof saved?.domain === "string" && saved.domain ? saved.domain : undefined);
			setAssetType(typeof saved?.assetType === "string" && saved.assetType ? saved.assetType : "ALL");
			setClassification(
				typeof saved?.classification === "string" && saved.classification ? saved.classification : "ALL",
			);
			setWarehouseLayer(
				typeof saved?.warehouseLayer === "string" && saved.warehouseLayer ? saved.warehouseLayer : "ALL",
			);
		} catch {
			// ignore malformed cache
		}
	}, []);

	useEffect(() => {
		void loadDomains();
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
		return [{ label: "全部主题域", value: "ALL" }, ...domains.map((item) => ({ label: item.name, value: item.id }))];
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
		} catch {
			// error toast handled by global interceptor
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
					editable: item.editable === true,
				})),
			);
			setPageState({
				page: Number(resp?.page ?? page - 1) + 1,
				size: Number(resp?.size ?? size),
				total: Number(resp?.total ?? 0),
			});
		} catch {
			if (reqId !== requestSeqRef.current) {
				return;
			}
			// error toast handled by global interceptor
			setRecords([]);
		} finally {
			if (reqId === requestSeqRef.current) {
				setLoading(false);
			}
		}
	};

	const loadSecurityLinkage = async (datasetId?: string) => {
		if (!datasetId) {
			setSecurityLinkage(null);
			return;
		}
		try {
			const linkage: any = await getClassificationMaskingLinkage(datasetId);
			setSecurityLinkage((linkage || null) as DatasetSecurityLinkage | null);
		} catch {
			// error toast handled by global interceptor
			setSecurityLinkage(null);
		}
	};

	const loadGovernanceHealth = async (datasetId?: string) => {
		if (!datasetId) {
			setGovernanceHealth(null);
			return;
		}
		try {
			const payload: any = await getDatasetGovernanceHealth(datasetId);
			setGovernanceHealth((payload || null) as GovernanceHealth | null);
		} catch {
			// error toast handled by global interceptor
			setGovernanceHealth(null);
		}
	};

	useEffect(() => {
		const refresh = () => {
			if (detailRow?.id) {
				void loadSecurityLinkage(detailRow.id);
				void loadGovernanceHealth(detailRow.id);
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

	const closeDetail = () => {
		detailRequestSeqRef.current += 1;
		setDetailOpen(false);
		setDetailRow(null);
		setAssetContract(null);
		setIndicatorDeps([]);
	};

	const openDetail = async (row: AssetRow) => {
		if (!row?.id) return;
		const detailRequestId = ++detailRequestSeqRef.current;
		setDetailRow(row);
		setDetailDataset(null);
		setAssetContract(null);
		setTableDetail(null);
		setSecurityLinkage(null);
		setGovernanceHealth(null);
		setIndicatorDeps([]);
		setImpact({ grantsCount: 0, lineageNodeCount: 0, lineageEdgeCount: 0 });
		setDetailOpen(true);
		setDetailLoading(true);
		try {
			const [
				datasetResp,
				tableResp,
				grantsResp,
				lineageResp,
				linkageResp,
				governanceResp,
				indicatorDepsResp,
				contractResp,
			] = await Promise.allSettled([
				getDataset(row.id),
				getTechMetadataTableDetail(`catalog:${row.id}`),
				listDatasetGrants(row.id),
				getCatalogLineageImpact(row.id, { direction: "BOTH", depth: 2 }),
				getClassificationMaskingLinkage(row.id),
				getDatasetGovernanceHealth(row.id),
				getDatasetIndicatorDeps(row.id),
				getCatalogAssetV2Contract(row.id),
			]);
			if (detailRequestId !== detailRequestSeqRef.current) return;
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
				const grants =
					grantsResp.status === "fulfilled" && Array.isArray(grantsResp.value) ? grantsResp.value.length : 0;
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
			if (indicatorDepsResp.status === "fulfilled") {
				const res: any = indicatorDepsResp.value;
				setIndicatorDeps(Array.isArray(res) ? res : Array.isArray(res?.data) ? res.data : []);
			}
			if (contractResp.status === "fulfilled") {
				setAssetContract(contractResp.value || null);
			}
		} catch {
			if (detailRequestId !== detailRequestSeqRef.current) return;
			// error toast handled by global interceptor
			setTableDetail(null);
			setDetailDataset(null);
			setSecurityLinkage(null);
			setGovernanceHealth(null);
			setAssetContract(null);
		} finally {
			if (detailRequestId === detailRequestSeqRef.current) {
				setDetailLoading(false);
			}
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
		if (detailDataset.editable !== true) {
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
		} catch {
			// error toast handled by global interceptor
		} finally {
			setSavingProfile(false);
		}
	};

	const columnColumns: ColumnsType<ColumnRow> = [
		{ title: "字段", dataIndex: "name", sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
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
			sorter: (a, b) => (a.name || "").localeCompare(b.name || ""),
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
			sorter: (a, b) => {
				const ta = a.updatedAt ? new Date(a.updatedAt as any).getTime() : 0;
				const tb = b.updatedAt ? new Date(b.updatedAt as any).getTime() : 0;
				return ta - tb;
			},
			render: (value) => value || "-",
		},
		{
			title: "最近同步",
			dataIndex: "snapshotTime",
			sorter: (a, b) => {
				const ta = a.snapshotTime ? new Date(a.snapshotTime as any).getTime() : 0;
				const tb = b.snapshotTime ? new Date(b.snapshotTime as any).getTime() : 0;
				return ta - tb;
			},
			render: (value) => value || "-",
		},
		{
			// 原标题为「同步状态」但 dataIndex 取的是 lifecycleStatus，且判定的 SYNCED
			// 不存在于任何行字段，该分支恒不命中——标题与数据不符，已按实际含义更正。
			title: "生命周期",
			dataIndex: "lifecycleStatus",
			render: (value) => {
				const normalized = String(value || "").toUpperCase();
				if (STALE_LIFECYCLE_STATUSES.has(normalized)) return <Tag color="red">失效</Tag>;
				return <Tag>{resolveEnumLabel(LIFECYCLE_STATUS_DICT, value, "未设定")}</Tag>;
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
					查看画像
				</Button>
			),
		},
	];

	return (
		<div className="space-y-4">
			<Card
				title={
					<Space size={8}>
						<ProfileOutlined />
						<span>资产明细台账</span>
					</Space>
				}
				extra={
					<Space wrap>
						<Button onClick={() => router.push("/catalog/assets")}>资产地图</Button>
						<Button onClick={() => void loadDatasets(1, pageState.size)} loading={loading}>
							刷新
						</Button>
					</Space>
				}
			>
				<div className="mb-3 flex flex-wrap items-center gap-2">
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
				{records.length ? (
					<CompactTable
						rowKey="id"
						columns={columns}
						dataSource={records}
						loading={loading}
						scroll={{ x: 1400 }}
						pagination={{
							current: pageState.page,
							pageSize: pageState.size,
							total: pageState.total,
							showSizeChanger: true,
						}}
						onChange={(pagination) => {
							const nextPage = (pagination.pageSize || 10) !== pageState.size ? 1 : pagination.current || 1;
							const nextSize = pagination.pageSize || 10;
							void loadDatasets(nextPage, nextSize);
						}}
					/>
				) : (
					<EmptyState title="暂无资产" description="当前筛选条件下未找到资产。" />
				)}
			</Card>

			<Modal
				title="资产画像与字段详情"
				open={detailOpen}
				onCancel={closeDetail}
				footer={<Button onClick={closeDetail}>关闭</Button>}
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
									{assetContract?.grantAssetType && assetContract?.assetKey ? (
										<AssetTagPanel
											assetType={assetContract.grantAssetType}
											assetKey={assetContract.assetKey}
											canEdit={assetContract?.canTag === true}
										/>
									) : !detailLoading ? (
										<Alert
											type="info"
											showIcon
											message="业务数据标签暂不可维护"
											description="资产身份合同不可用，暂不能维护业务数据标签。"
										/>
									) : null}
									<Form
										form={profileForm}
										layout="vertical"
										disabled={detailDataset?.editable !== true || detailLoading}
									>
										<Form.Item label="负责人" name="owner">
											<Input placeholder="请输入负责人账号或姓名" />
										</Form.Item>
										<Form.Item
											label="历史自由文本标签（兼容字段）"
											name="tags"
											extra="该字段仅用于兼容既有数据，不会自动转成业务数据标签。"
										>
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
											disabled={!profileChanged || detailDataset?.editable !== true}
										>
											保存画像
										</Button>
										{detailDataset?.editable !== true ? (
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
									<CompactTable
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
									<AssetGovernanceOverview
										governanceHealth={governanceHealth}
										impact={impact}
										onNavigate={(path) => router.push(path)}
									/>
									<Card size="small" title="密级与脱敏联动">
										<div className="mb-2 text-xs text-slate-600">
											当前密级：
											{securityLinkage?.classification ||
												detailDataset?.classification ||
												detailRow?.classification ||
												"-"}
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
									{indicatorDeps.length > 0 && (
										<div className="rounded-[22px] border border-slate-200 bg-slate-50 px-4 py-4">
											<div className="mb-3 text-sm font-semibold text-slate-900">
												关联指标（{indicatorDeps.length}）
											</div>
											<Space wrap>
												{indicatorDeps.map((ind) => (
													<Tag key={ind.id} color={ind.isDerived ? "purple" : "blue"}>
														{ind.name}（{ind.code}）
													</Tag>
												))}
											</Space>
										</div>
									)}
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
