import { useCallback, useEffect, useMemo, useState } from "react";
import { Button, Input, Spin, Tree } from "antd";
import { Link, useNavigate, useParams } from "react-router";
import { getDomainTree } from "@/api/platformApi";
import { analyticsApi, type ScreenListItem } from "../../api/analyticsApi";
import { PageContainer } from "../../components/PageContainer/PageContainer";
import { resolveRouteForOpen } from "../../helpers/resolveAnalyticsUrl";
import { ClassificationTag } from "./components/ClassificationTag";
import {
	buildDataPortalTree,
	countScreenLeaves,
	type DataPortalDomainNode,
	type DataPortalTreeNode,
} from "./dataPortalTree";

function extractDomainTree(response: unknown): DataPortalDomainNode[] {
	if (Array.isArray(response)) return response as DataPortalDomainNode[];
	if (!response || typeof response !== "object") return [];
	const data = (response as { data?: unknown }).data;
	if (Array.isArray(data)) return data as DataPortalDomainNode[];
	if (data && typeof data === "object" && Array.isArray((data as { data?: unknown }).data)) {
		return (data as { data: DataPortalDomainNode[] }).data;
	}
	return [];
}

function branchKeys(nodes: DataPortalTreeNode[]): string[] {
	const keys: string[] = [];
	for (const node of nodes) {
		if (node.children?.length) {
			keys.push(node.key);
			keys.push(...branchKeys(node.children));
		}
	}
	return keys;
}

function firstScreenId(nodes: DataPortalTreeNode[]): string | null {
	for (const node of nodes) {
		if (node.screenId !== undefined) return String(node.screenId);
		const nested = node.children ? firstScreenId(node.children) : null;
		if (nested) return nested;
	}
	return null;
}

export default function DataPortalPage() {
	const { screenId } = useParams<{ screenId?: string }>();
	const navigate = useNavigate();
	const [screens, setScreens] = useState<ScreenListItem[]>([]);
	const [domains, setDomains] = useState<DataPortalDomainNode[]>([]);
	const [searchKeyword, setSearchKeyword] = useState("");
	const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);
	const [loading, setLoading] = useState(true);
	const [directoryError, setDirectoryError] = useState<string | null>(null);
	const [domainWarning, setDomainWarning] = useState(false);

	const loadPortal = useCallback(async () => {
		setLoading(true);
		setDirectoryError(null);
		setDomainWarning(false);

		const [screenResult, domainResult] = await Promise.allSettled([
			analyticsApi.listScreens({ publishedOnly: true }),
			getDomainTree(),
		]);

		if (screenResult.status === "rejected") {
			setScreens([]);
			setDirectoryError("已发布大屏目录加载失败，请稍后重试。");
		} else {
			setScreens(screenResult.value.filter((screen) => Number(screen.publishedVersionNo ?? 0) > 0));
		}

		if (domainResult.status === "rejected") {
			setDomains([]);
			setDomainWarning(true);
		} else {
			setDomains(extractDomainTree(domainResult.value));
		}
		setLoading(false);
	}, []);

	useEffect(() => {
		void loadPortal();
	}, [loadPortal]);

	const treeData = useMemo(
		() => buildDataPortalTree(domains, screens, searchKeyword),
		[domains, screens, searchKeyword],
	);

	useEffect(() => {
		setExpandedKeys(branchKeys(treeData));
	}, [treeData]);

	useEffect(() => {
		if (loading || directoryError || screenId) return;
		const firstId = firstScreenId(treeData);
		if (firstId) navigate(`/bi/portal/${encodeURIComponent(firstId)}`, { replace: true });
	}, [directoryError, loading, navigate, screenId, treeData]);

	const selectedScreen = useMemo(
		() => screens.find((screen) => String(screen.id) === String(screenId)) ?? null,
		[screenId, screens],
	);
	const runtimeUrl = selectedScreen
		? resolveRouteForOpen(
				`/bi/screens/${encodeURIComponent(String(selectedScreen.id))}/preview?mode=published&fallbackDraft=false&embed=1&scaleMode=fit`,
			)
		: null;

	const handleTreeSelect = (keys: React.Key[]) => {
		const key = String(keys[0] ?? "");
		if (!key.startsWith("screen:")) return;
		navigate(`/bi/portal/${encodeURIComponent(key.slice("screen:".length))}`);
	};

	return (
		<PageContainer padding="md">
			<div data-testid="data-portal-page" className="flex min-h-[680px] min-w-0 flex-col gap-4">
				<header className="flex flex-wrap items-start justify-between gap-3">
					<div>
						<h1 className="m-0 text-2xl font-semibold text-text-primary">数据门户</h1>
						<p className="mb-0 mt-1 text-sm text-text-secondary">按主题域浏览并使用已发布的数据大屏。</p>
					</div>
					<div className="flex items-center gap-2">
						<Button onClick={() => void loadPortal()}>刷新</Button>
						<Link to="/bi/screens" className="inline-flex min-h-8 items-center rounded-md border border-solid border-brand px-3 text-sm text-brand no-underline">
							大屏管理
						</Link>
					</div>
				</header>

				{loading ? (
					<div data-testid="data-portal-loading" className="flex min-h-[520px] items-center justify-center rounded-xl border border-solid border-border bg-bg-container">
						<Spin tip="正在加载已发布大屏目录" size="large" />
					</div>
				) : directoryError ? (
					<div data-testid="data-portal-error" className="flex min-h-[420px] flex-col items-center justify-center gap-3 rounded-xl border border-solid border-border bg-bg-container p-8 text-center">
						<h2 className="m-0 text-lg font-semibold text-text-primary">数据门户暂不可用</h2>
						<p className="m-0 text-sm text-text-secondary">{directoryError}</p>
						<Button type="primary" onClick={() => void loadPortal()}>重新加载</Button>
					</div>
				) : screens.length === 0 ? (
					<div data-testid="data-portal-empty" className="flex min-h-[420px] flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-border bg-bg-container p-8 text-center">
						<h2 className="m-0 text-lg font-semibold text-text-primary">暂无可浏览的大屏</h2>
						<p className="m-0 max-w-xl text-sm text-text-secondary">数据门户只展示当前用户有权访问且已发布的大屏。请先在大屏管理中完成设计、校验和发布。</p>
						<Link to="/bi/screens" className="text-brand">大屏管理</Link>
					</div>
				) : (
					<div className="flex min-h-0 flex-1 flex-col gap-4 lg:flex-row">
						<aside className="w-full shrink-0 rounded-xl border border-solid border-border bg-bg-container p-3 lg:w-[286px]">
							<Input.Search
								allowClear
								placeholder="搜索大屏名称或描述"
								value={searchKeyword}
								onChange={(event) => setSearchKeyword(event.target.value)}
							/>
							<div className="mb-2 mt-3 flex items-center justify-between text-xs text-text-secondary">
								<span>主题域目录</span>
								<span>{countScreenLeaves(treeData)} 个大屏</span>
							</div>
							{domainWarning ? (
								<div className="mb-2 rounded-md bg-amber-50 px-2 py-1.5 text-xs text-amber-800">主题域暂不可用，大屏已归入“未归类”。</div>
							) : null}
							{treeData.length > 0 ? (
								<Tree
									blockNode
									showLine={{ showLeafIcon: false }}
									treeData={treeData}
									expandedKeys={expandedKeys}
									selectedKeys={screenId ? [`screen:${screenId}`] : []}
									onExpand={(keys) => setExpandedKeys(keys)}
									onSelect={handleTreeSelect}
								/>
							) : (
								<div className="py-10 text-center text-sm text-text-secondary">未找到匹配的大屏</div>
							)}
						</aside>

						<section className="min-h-[520px] min-w-0 flex-1 overflow-hidden rounded-xl border border-solid border-border bg-[#08121f]">
							{runtimeUrl && selectedScreen ? (
								<div className="flex h-full min-h-[680px] flex-col">
									<div className="flex flex-wrap items-center justify-between gap-2 border-0 border-b border-solid border-white/10 bg-[#101c2d] px-4 py-2 text-white">
										<div className="min-w-0">
											<div className="truncate font-medium">{selectedScreen.name || `大屏 ${String(selectedScreen.id)}`}</div>
											<div className="mt-0.5 text-xs text-white/55">已发布版本 v{selectedScreen.publishedVersionNo}</div>
										</div>
										<ClassificationTag value={selectedScreen.classification} size="small" />
									</div>
									<iframe
										data-testid="data-portal-runtime"
										title={`${selectedScreen.name || "数据大屏"}运行页`}
										src={runtimeUrl}
										className="min-h-[620px] w-full flex-1 border-0 bg-[#08121f]"
									/>
								</div>
							) : (
								<div data-testid="data-portal-error" className="flex min-h-[520px] flex-col items-center justify-center gap-2 p-8 text-center text-white">
									<h2 className="m-0 text-lg font-semibold">未找到该已发布大屏</h2>
									<p className="m-0 text-sm text-white/60">请从左侧目录重新选择，或确认该大屏仍处于发布状态。</p>
								</div>
							)}
						</section>
					</div>
				)}
			</div>
		</PageContainer>
	);
}
