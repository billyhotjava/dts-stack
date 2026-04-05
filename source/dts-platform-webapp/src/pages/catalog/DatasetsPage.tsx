import { useEffect, useMemo, useRef, useState } from "react";
import { Alert, Button, Card, Collapse, Input, Layout, Select, Space, Spin, Tag, Tree } from "antd";
import { EmptyState } from "@/components/empty-state";
import {
	getCatalogReconciliation,
	getDomainTree,
	listDatasets,
	listDomains,
} from "@/api/platformApi";
import { useRouter } from "@/routes/hooks";

type AssetRow = {
	id: string;
	name: string;
	type: string;
	domainId?: string;
	domain?: string;
	classification?: string;
	warehouseLayer?: string;
	status?: string;
};

type DomainNode = { id?: string; name?: string; code?: string; children?: DomainNode[] };

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

const CLASSIFICATION_LABEL: Record<string, string> = {
	PUBLIC: "公开",
	INTERNAL: "内部",
	SECRET: "秘密",
	CONFIDENTIAL: "机密",
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

const LAYER_TAG_COLORS: Record<string, string> = {
	ODS: "default",
	DWD: "blue",
	DWS: "cyan",
	ADS: "green",
};

export default function Page() {
	const router = useRouter();
	const [keyword, setKeyword] = useState("");
	const [domain, setDomain] = useState<string | undefined>();
	const [assetType, setAssetType] = useState<string>("ALL");
	const [classification, setClassification] = useState<string>("ALL");
	const [warehouseLayer, setWarehouseLayer] = useState<string>("ALL");
	const [loading, setLoading] = useState(false);
	const [records, setRecords] = useState<AssetRow[]>([]);
	const [pageState, setPageState] = useState({ page: 1, size: 10, total: 0 });
	const [domains, setDomains] = useState<{ id: string; name: string }[]>([]);
	const [reconciliation, setReconciliation] = useState<ReconciliationResult | null>(null);
	const [reconciliationLoading, setReconciliationLoading] = useState(false);
	const [domainTree, setDomainTree] = useState<DomainNode[]>([]);
	const [treeLoading, setTreeLoading] = useState(false);
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
		void (async () => {
			setTreeLoading(true);
			try {
				const tree = await getDomainTree() as any;
				const data = Array.isArray(tree) ? tree : (Array.isArray(tree?.data) ? tree.data : []);
				setDomainTree(data);
			} catch {
				// error toast handled by global interceptor
			} finally {
				setTreeLoading(false);
			}
		})();
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

	const loadReconciliation = async () => {
		setReconciliationLoading(true);
		try {
			const result: any = await getCatalogReconciliation(20);
			setReconciliation((result || null) as ReconciliationResult | null);
		} catch {
			// error toast handled by global interceptor
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
					domainId: item.domainId ? String(item.domainId) : undefined,
					domain: item.domainName || (item.domainId ? domainMap.get(String(item.domainId)) : undefined),
					classification: item.classification || undefined,
					warehouseLayer: item.warehouseLayer || undefined,
					status: item.enabled === false ? "停用" : "启用",
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

	// 按类型统计
	const typeStats = useMemo(() => {
		const map = new Map<string, number>();
		records.forEach((r) => {
			const t = r.type || "未知";
			map.set(t, (map.get(t) || 0) + 1);
		});
		return Array.from(map.entries()).sort((a, b) => b[1] - a[1]);
	}, [records]);

	// 按分层统计
	const layerStats = useMemo(() => {
		const map = new Map<string, number>();
		records.forEach((r) => {
			const l = r.warehouseLayer || "未分层";
			map.set(l, (map.get(l) || 0) + 1);
		});
		return Array.from(map.entries()).sort((a, b) => b[1] - a[1]);
	}, [records]);

	// 按密级统计
	const classificationStats = useMemo(() => {
		const map = new Map<string, number>();
		records.forEach((r) => {
			const c = r.classification ? (CLASSIFICATION_LABEL[r.classification.toUpperCase()] || r.classification) : "未设定";
			map.set(c, (map.get(c) || 0) + 1);
		});
		return Array.from(map.entries()).sort((a, b) => b[1] - a[1]);
	}, [records]);

	const buildTreeNodes = (nodes: DomainNode[]): any[] =>
		nodes.map((n) => ({
			key: n.id ?? n.code ?? n.name ?? Math.random().toString(),
			title: n.name ?? n.code ?? "未命名",
			children: n.children?.length ? buildTreeNodes(n.children) : undefined,
		}));

	const treeData = [
		{
			key: "ALL",
			title: "全部资产",
			children: buildTreeNodes(domainTree),
		},
	];

	const reconciliationContent = reconciliation ? (
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
	);

	return (
		<Layout className="min-h-full" style={{ background: "transparent" }}>
			<Layout.Sider
				width={240}
				theme="light"
				style={{ background: "#fff", borderRight: "1px solid #f0f0f0", padding: "12px 8px" }}
			>
				<div className="mb-2 px-2 text-xs font-semibold text-slate-500">主题域</div>
				<Spin spinning={treeLoading}>
					<Tree
						showLine
						defaultExpandAll
						treeData={treeData}
						defaultSelectedKeys={["ALL"]}
						onSelect={(keys) => {
							const selected = String(keys?.[0] ?? "ALL");
							setDomain(selected === "ALL" ? undefined : selected);
						}}
					/>
				</Spin>
			</Layout.Sider>
			<Layout.Content style={{ padding: "0 16px" }}>
				<div className="space-y-4">
					<Card
						title="资产地图"
						extra={
							<div className="flex flex-wrap items-center gap-2">
								<Button className="rounded-2xl" onClick={() => void loadDatasets(1, pageState.size)} loading={loading}>
									刷新资产
								</Button>
								<Button className="rounded-2xl" onClick={() => void loadReconciliation()} loading={reconciliationLoading}>
									刷新核对
								</Button>
							</div>
						}
					>
						<div className="mb-3 flex flex-wrap items-center gap-2">
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
					</Card>

					<Card title="资产概览">
						{records.length ? (
							<div className="space-y-4">
								<div className="grid gap-3 md:grid-cols-4">
									<Card size="small" title="资产总量">
										<div className="text-2xl font-semibold">{pageState.total}</div>
										<div className="text-xs text-gray-500">已纳入治理的资产记录</div>
									</Card>
									<Card size="small" title="主题域">
										<div className="text-2xl font-semibold">{domains.length}</div>
										<div className="text-xs text-gray-500">已配置主题域数量</div>
									</Card>
									<Card size="small" title="活跃资产">
										<div className="text-2xl font-semibold">
											{records.filter((item) => item.status === "启用").length}
										</div>
										<div className="text-xs text-gray-500">当前页启用资产</div>
									</Card>
									<Card size="small" title="资产详情">
										<Button type="link" onClick={() => router.push("/catalog/asset-detail")} className="p-0">
											查看全部资产 →
										</Button>
										<div className="text-xs text-gray-500">进入资产列表查看详情</div>
									</Card>
								</div>

								<div className="grid gap-3 md:grid-cols-3">
									<Card size="small" title="按类型分布">
										<div className="flex flex-wrap gap-1.5">
											{typeStats.map(([type, count]) => (
												<Tag key={type}>{type}: {count}</Tag>
											))}
										</div>
									</Card>
									<Card size="small" title="按分层分布">
										<div className="flex flex-wrap gap-1.5">
											{layerStats.map(([layer, count]) => (
												<Tag key={layer}>{layer}: {count}</Tag>
											))}
										</div>
									</Card>
									<Card size="small" title="按密级分布">
										<div className="flex flex-wrap gap-1.5">
											{classificationStats.map(([cls, count]) => (
												<Tag key={cls}>{cls}: {count}</Tag>
											))}
										</div>
									</Card>
								</div>
							</div>
						) : (
							<EmptyState title="暂无资产地图" description="请先完成元数据采集或同步资产数据。" />
						)}
					</Card>

					{records.length > 0 && (
						<div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
							{records.map((row) => (
								<div
									key={row.id}
									className="cursor-pointer rounded-[20px] border border-slate-200 bg-white px-4 py-3 transition-all hover:border-blue-300 hover:shadow-sm"
									onClick={() => router.push(`/catalog/asset-detail?id=${row.id}`)}
								>
									<div className="flex items-start justify-between gap-2">
										<div className="flex-1 truncate text-sm font-semibold text-slate-900">{row.name}</div>
										{row.warehouseLayer && (
											<Tag color={LAYER_TAG_COLORS[row.warehouseLayer] ?? "default"}>
												{row.warehouseLayer}
											</Tag>
										)}
									</div>
									<div className="mt-1 text-xs text-slate-500">{row.domain ?? "未归域"}</div>
									<div className="mt-2 flex flex-wrap gap-1">
										{row.classification && (
											<Tag color="orange" style={{ fontSize: 11 }}>
												{CLASSIFICATION_LABEL[row.classification.toUpperCase()] ?? row.classification}
											</Tag>
										)}
										<Tag style={{ fontSize: 11 }}>{row.type ?? "未知"}</Tag>
									</div>
								</div>
							))}
						</div>
					)}

					<Collapse
						defaultActiveKey={[]}
						items={[
							{
								key: "reconciliation",
								label: "发布前回归与一致性核对",
								extra: (
									<Button
										size="small"
										loading={reconciliationLoading}
										onClick={(e) => {
											e.stopPropagation();
											void loadReconciliation();
										}}
									>
										重新核对
									</Button>
								),
								children: reconciliationContent,
							},
						]}
					/>
				</div>
			</Layout.Content>
		</Layout>
	);
}
