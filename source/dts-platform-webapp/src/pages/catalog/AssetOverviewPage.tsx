import {
	DatabaseOutlined,
	ReloadOutlined,
	SafetyCertificateOutlined,
	TagOutlined,
	WarningOutlined,
} from "@ant-design/icons";
import { Alert, Button, Layout, Tooltip } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import { getCatalogAssetsOverview, getDomainTree } from "@/api/platformApi";
import { DomainScopeNav, type DomainScopeNode } from "@/components/catalog/DomainScopeNav";
import { useRouter } from "@/routes/hooks";
import { AssetDomainBars } from "./assets/AssetDomainBars";
import { AssetGovernanceDonut } from "./assets/AssetGovernanceDonut";
import { buildDomainScopeNodes, MetricTile, UNASSIGNED_DOMAIN_KEY } from "./assets/assetPageShared";

// Sprint-88：资产概览 = 一屏统计仪表盘（KPI 四卡 + 治理状态环形 + 主题域条形）。
// 唯一出口 = /catalog/search?view=table；分层矩阵与待处置清单已按 ADR-88-03/05 移除。
export default function AssetOverviewPage() {
	const router = useRouter();
	const [searchParams, setSearchParams] = useSearchParams();
	const isLegacyTagRedirect = searchParams.get("tab") === "catalog-tags";
	const isLegacyLedgerRedirect = searchParams.get("view") === "table";
	const isLegacyRedirect = isLegacyTagRedirect || isLegacyLedgerRedirect;
	const isAssetMapActive = !isLegacyRedirect;
	const domain = searchParams.get("domain") || undefined;
	const setDomain = useCallback(
		(next: string | undefined) => {
			const params = new URLSearchParams(searchParams);
			if (next) params.set("domain", next);
			else params.delete("domain");
			setSearchParams(params);
		},
		[searchParams, setSearchParams],
	);

	// 旧深链兼容：?tab=catalog-tags / ?view=table 无感跳到数据查询（行为与现网一致）
	useEffect(() => {
		if (!isLegacyRedirect) return;
		const params = new URLSearchParams(searchParams);
		params.delete("view");
		if (isLegacyTagRedirect) params.set("tab", "catalog-tags");
		else params.delete("tab");
		const rest = params.toString();
		router.replace(`/catalog/assets/ledger${rest ? `?${rest}` : ""}`);
	}, [isLegacyRedirect, isLegacyTagRedirect, router, searchParams]);

	const [domainTree, setDomainTree] = useState<DomainScopeNode[]>([]);
	const [domainStats, setDomainStats] = useState<{
		all?: { total: number; attention: number };
		unassigned?: { total: number; attention: number };
		byDomain: Record<string, { total: number; attention: number }>;
		truncated: boolean;
	}>({
		byDomain: {},
		truncated: false,
	});
	const [treeLoading, setTreeLoading] = useState(false);
	const [overview, setOverview] = useState<any | null>(null);
	const [overviewLoading, setOverviewLoading] = useState(false);
	const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null);

	useEffect(() => {
		if (!isAssetMapActive) return;
		void (async () => {
			setTreeLoading(true);
			try {
				const resp = (await getDomainTree({ withStats: true })) as any;
				const payload = resp?.data ?? resp;
				const tree = Array.isArray(payload) ? payload : payload?.tree;
				setDomainTree(Array.isArray(tree) ? tree : []);
				const stats = payload?.stats;
				setDomainStats(
					stats
						? {
								all: stats.all,
								unassigned: stats.unassigned,
								byDomain: stats.byDomain || {},
								truncated: Boolean(stats.truncated),
							}
						: { byDomain: {}, truncated: false },
				);
			} catch {
				// error toast handled by global interceptor
			} finally {
				setTreeLoading(false);
			}
		})();
	}, [isAssetMapActive]);

	const loadOverview = useCallback(async () => {
		if (!isAssetMapActive) return;
		setOverviewLoading(true);
		try {
			const scope =
				domain && domain !== UNASSIGNED_DOMAIN_KEY
					? { domainId: domain, domainUnassigned: undefined }
					: domain === UNASSIGNED_DOMAIN_KEY
						? { domainId: undefined, domainUnassigned: true }
						: {};
			const result = (await getCatalogAssetsOverview(scope)) as any;
			setOverview(result || null);
			setLastUpdatedAt(new Date());
		} catch {
			// error toast handled by global interceptor
		} finally {
			setOverviewLoading(false);
		}
	}, [domain, isAssetMapActive]);

	useEffect(() => {
		if (!isAssetMapActive) return;
		void loadOverview();
	}, [isAssetMapActive, loadOverview]);

	const domainMap = useMemo(() => {
		const map = new Map<string, string>();
		const walk = (nodes: DomainScopeNode[]) => {
			for (const node of nodes) {
				if (node.id) map.set(String(node.id), node.name ?? node.code ?? "未命名");
				if (node.children?.length) walk(node.children);
			}
		};
		walk(domainTree);
		return map;
	}, [domainTree]);

	const scopeNodes = useMemo(
		() => buildDomainScopeNodes(domainTree, domainStats.byDomain),
		[domainTree, domainStats.byDomain],
	);

	const scopeLabel = domain
		? domain === UNASSIGNED_DOMAIN_KEY
			? "未归域"
			: domainMap.get(domain) || "当前主题域"
		: "全部主题域";
	const lastUpdatedText = useMemo(
		() => (lastUpdatedAt ? lastUpdatedAt.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" }) : "—"),
		[lastUpdatedAt],
	);

	const drillToSearch = (extra?: Record<string, string>) => {
		const params = new URLSearchParams();
		params.set("view", "table");
		if (domain) params.set("domain", domain);
		if (extra) {
			for (const [key, value] of Object.entries(extra)) params.set(key, value);
		}
		router.push(`/catalog/search?${params.toString()}`);
	};

	if (isLegacyRedirect) return null;

	const truncated = Boolean(overview?.truncated);
	const total = Number(overview?.total ?? 0);
	const attention = Number(overview?.attention ?? 0);
	const unclassified = Number(overview?.unclassified ?? 0);
	const tagged = Number(overview?.tagged ?? 0);
	const tagCoverage = Number(overview?.tagCoveragePercent ?? 0);
	const num = (value: number) => `${truncated ? "≥" : ""}${value}`;

	return (
		<Layout className="min-h-full bg-transparent">
			<Layout.Sider
				width={240}
				breakpoint="lg"
				collapsedWidth={0}
				theme="light"
				className="rounded-lg border border-slate-200 bg-white p-3"
			>
				<DomainScopeNav
					nodes={scopeNodes}
					allStats={domainStats.all}
					unassignedStats={domainStats.unassigned}
					value={domain}
					onChange={setDomain}
					loading={treeLoading}
					truncated={domainStats.truncated}
				/>
			</Layout.Sider>
			<Layout.Content style={{ padding: "0 16px" }}>
				<div className="space-y-4">
					<div className="flex flex-wrap items-center justify-between gap-2">
						<div className="flex items-center gap-3">
							<span className="text-lg font-bold text-slate-900">资产概览</span>
							<Tooltip title="范围切换请使用左侧导航">
								<span className="text-xs text-slate-500" data-testid="scope-echo">
									当前范围：{scopeLabel}
								</span>
							</Tooltip>
						</div>
						<div className="flex items-center gap-2 text-xs text-slate-500">
							<span>统计更新于 {lastUpdatedText}</span>
							<Button
								type="text"
								size="small"
								icon={<ReloadOutlined />}
								aria-label="刷新统计"
								loading={overviewLoading}
								onClick={() => void loadOverview()}
							/>
							<Button type="primary" size="small" data-testid="goto-search-table" onClick={() => drillToSearch()}>
								在数据查询中查看 →
							</Button>
						</div>
					</div>
					{truncated ? (
						<Alert
							type="warning"
							showIcon
							message={`统计基于前 ${overview?.scanned} 条可见资产（已达统计上限），实际总量可能更多`}
						/>
					) : null}

					<div className="grid grid-cols-2 gap-3 md:grid-cols-4">
						<MetricTile
							testId="kpi-total"
							icon={<DatabaseOutlined />}
							label="资产总量"
							value={num(total)}
							footnote={`${domainMap.size} 个主题域`}
						/>
						<div data-testid="kpi-attention">
							<MetricTile
								icon={<WarningOutlined />}
								label="待处置"
								value={num(attention)}
								footnote={`占比 ${total > 0 ? Math.round((attention / total) * 100) : 0}%`}
								tone="text-amber-600"
							/>
						</div>
						<button
							type="button"
							data-testid="kpi-unclassified"
							onClick={() => drillToSearch({ unclassified: "1" })}
							className="cursor-pointer rounded-lg text-left transition hover:ring-2 hover:ring-blue-200"
						>
							<MetricTile
								icon={<SafetyCertificateOutlined />}
								label="未定密"
								value={num(unclassified)}
								footnote="需补定密 →"
								tone="text-amber-600"
							/>
						</button>
						<MetricTile
							testId="kpi-tag-coverage"
							icon={<TagOutlined />}
							label="标签覆盖"
							value={`${truncated ? "≥" : ""}${tagCoverage}%`}
							footnote={`${num(tagged)}/${num(total)}`}
						/>
					</div>

					<div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
						<AssetGovernanceDonut
							counts={overview?.governanceStatusCounts || {}}
							total={total}
							truncated={truncated}
							loading={overviewLoading}
							onSliceClick={(status) => drillToSearch({ governance: status })}
							dataTestId="governance-donut-card"
						/>
						<AssetDomainBars
							byDomain={overview?.byDomain || {}}
							domainNames={domainMap}
							unassigned={
								Number(overview?.missingDomain ?? 0) > 0
									? { total: Number(overview?.missingDomain ?? 0), attention: 0 }
									: undefined
							}
							truncated={truncated}
							loading={overviewLoading}
							onBarClick={(domainKey) => drillToSearch({ domain: domainKey })}
							dataTestId="domain-bars-card"
						/>
					</div>
				</div>
			</Layout.Content>
		</Layout>
	);
}
