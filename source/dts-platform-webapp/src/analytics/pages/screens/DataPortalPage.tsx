import { useCallback, useEffect, useMemo, useState } from "react";
import { Button, Input, Spin, Tag, Tree } from "antd";
import { Link, useNavigate, useParams } from "react-router";
import {
	analyticsApi,
	type DashboardListItem,
	type DataPortalSnapshot,
	type ScreenListItem,
} from "../../api/analyticsApi";
import { PageContainer } from "../../components/PageContainer/PageContainer";
import { resolveRouteForOpen } from "../../helpers/resolveAnalyticsUrl";
import DashboardDetailPage from "../DashboardDetailPage";
import { ClassificationTag } from "./components/ClassificationTag";
import { DataPortalEditorDrawer } from "./DataPortalEditorDrawer";
import {
	buildDataPortalTree,
	countContentLeaves,
	findContentNode,
	findContentNodeByBinding,
	type DataPortalTreeNode,
} from "./dataPortalTree";

import { PortalPreviewFrame } from "./PortalPreviewFrame";

const EMPTY_PORTAL: DataPortalSnapshot = { can_write: false, directories: [], items: [] };

function branchKeys(nodes: DataPortalTreeNode[]): string[] {
	const keys: string[] = [];
	for (const node of nodes) {
		if (node.nodeType === "DIRECTORY") keys.push(node.key);
		if (node.children) keys.push(...branchKeys(node.children));
	}
	return keys;
}

function findNodeByKey(nodes: DataPortalTreeNode[], key: string): DataPortalTreeNode | null {
	for (const node of nodes) {
		if (node.key === key) return node;
		const child = node.children ? findNodeByKey(node.children, key) : null;
		if (child) return child;
	}
	return null;
}

function directoryPathToKey(
	nodes: DataPortalTreeNode[],
	targetKey: string,
	path: string[] = [],
): string[] | null {
	for (const node of nodes) {
		const nextPath = node.nodeType === "DIRECTORY" ? [...path, node.key] : path;
		if (node.key === targetKey) return nextPath;
		const child = node.children ? directoryPathToKey(node.children, targetKey, nextPath) : null;
		if (child) return child;
	}
	return null;
}

function isPublishedDashboard(dashboard: DashboardListItem): boolean {
	return dashboard.lifecycle_status === "PUBLISHED"
		&& dashboard.published_revision_id != null
		&& dashboard.registration_status === "AVAILABLE";
}

export default function DataPortalPage() {
	const { contentType, contentId, screenId, bindingId } = useParams<{
		contentType?: string;
		contentId?: string;
		screenId?: string;
		bindingId?: string;
	}>();
	const navigate = useNavigate();
	const [portal, setPortal] = useState<DataPortalSnapshot>(EMPTY_PORTAL);
	const [screens, setScreens] = useState<ScreenListItem[]>([]);
	const [dashboards, setDashboards] = useState<DashboardListItem[]>([]);
	const [searchKeyword, setSearchKeyword] = useState("");
	const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);
	const [loading, setLoading] = useState(true);
	const [directoryError, setDirectoryError] = useState<string | null>(null);
	const [catalogWarning, setCatalogWarning] = useState<string | null>(null);
	const [editorOpen, setEditorOpen] = useState(false);

	const loadPortal = useCallback(async (showLoading = true) => {
		if (showLoading) setLoading(true);
		setDirectoryError(null);
		setCatalogWarning(null);
		const [portalResult, screenResult, dashboardResult] = await Promise.allSettled([
			analyticsApi.getDataPortal(),
			analyticsApi.listScreens({ publishedOnly: true }),
			analyticsApi.listDashboards(),
		]);

		if (portalResult.status === "rejected") {
			setPortal(EMPTY_PORTAL);
			setDirectoryError("门户菜单加载失败，请稍后重试。");
		} else {
			setPortal(portalResult.value);
		}
		if (screenResult.status === "rejected") {
			setScreens([]);
			setCatalogWarning("部分大屏暂时无法加载，门户仍可浏览其他可用内容。");
		} else {
			setScreens(screenResult.value.filter((screen) => Number(screen.publishedVersionNo ?? 0) > 0));
		}
		if (dashboardResult.status === "rejected") {
			setDashboards([]);
			setCatalogWarning("部分看板暂时无法加载，门户仍可浏览其他可用内容。");
		} else {
			setDashboards(dashboardResult.value.filter(isPublishedDashboard));
		}
		setLoading(false);
	}, []);

	useEffect(() => {
		void loadPortal();
	}, [loadPortal]);

	const fullTree = useMemo(
		() => buildDataPortalTree(portal.directories, portal.items, screens, dashboards),
		[dashboards, portal.directories, portal.items, screens],
	);
	const treeData = useMemo(
		() => buildDataPortalTree(portal.directories, portal.items, screens, dashboards, searchKeyword),
		[dashboards, portal.directories, portal.items, screens, searchKeyword],
	);
	const routeType = (contentType ?? (screenId ? "SCREEN" : "")).toUpperCase();
	const routeId = contentId ?? screenId;
	const selectedContent = useMemo(
		() => bindingId
			? findContentNodeByBinding(fullTree, bindingId)
			: findContentNode(fullTree, routeType, routeId),
		[bindingId, fullTree, routeId, routeType],
	);
	const selectionRequested = Boolean(bindingId || routeId);

	useEffect(() => {
		if (searchKeyword.trim()) setExpandedKeys(branchKeys(treeData));
	}, [searchKeyword, treeData]);

	useEffect(() => {
		if (!selectedContent) return;
		const path = directoryPathToKey(fullTree, selectedContent.key);
		if (!path) return;
		setExpandedKeys((current) => Array.from(new Set([...current.map(String), ...path])));
	}, [fullTree, selectedContent]);

	const selectedScreen = selectedContent?.screen ?? null;
	const selectedDashboard = selectedContent?.dashboard ?? null;
	const screenRuntimeUrl = selectedScreen
		? resolveRouteForOpen(
				`/bi/screens/${encodeURIComponent(String(selectedScreen.id))}/preview?mode=published&fallbackDraft=false&embed=1&scaleMode=fit`,
			)
		: null;

	const handleTreeSelect = (_keys: React.Key[], info: { node: { key: React.Key } }) => {
		const key = String(info.node.key);
		const node = findNodeByKey(treeData, key);
		if (!node) return;
		if (node.nodeType === "DIRECTORY") {
			setExpandedKeys((current) => current.includes(key) ? current.filter((item) => item !== key) : [...current, key]);
			return;
		}
		if (node.availability === "AVAILABLE" && node.bindingId != null) {
			navigate(`/bi/portal/item/${encodeURIComponent(String(node.bindingId))}`);
		}
	};

	const editor = (
		<DataPortalEditorDrawer
			open={editorOpen}
			onClose={() => setEditorOpen(false)}
			directories={portal.directories}
			bindings={portal.items}
			screens={screens}
			dashboards={dashboards}
			onChanged={() => loadPortal(false)}
		/>
	);

	return (
		<PageContainer padding="md">
			<div data-testid="data-portal-page" className="flex min-h-[680px] min-w-0 flex-col gap-4">
				<header className="flex flex-wrap items-start justify-between gap-3">
					<div>
						<h1 className="m-0 text-2xl font-semibold text-text-primary">数据门户</h1>
						<p className="mb-0 mt-1 text-sm text-text-secondary">用自定义多级菜单统一浏览已发布的大屏与看板。</p>
					</div>
					<div className="flex flex-wrap items-center gap-2">
						<Button onClick={() => void loadPortal()}>刷新</Button>
						{portal.can_write ? <Button type="primary" onClick={() => setEditorOpen(true)}>门户编排</Button> : null}
						<Link to="/bi/dashboards" className="inline-flex min-h-8 items-center rounded-md border border-solid border-border px-3 text-sm text-text-primary no-underline">
							看板管理
						</Link>
						<Link to="/bi/screens" className="inline-flex min-h-8 items-center rounded-md border border-solid border-brand px-3 text-sm text-brand no-underline">
							大屏管理
						</Link>
					</div>
				</header>

				{loading ? (
					<div data-testid="data-portal-loading" className="flex min-h-[520px] items-center justify-center rounded-xl border border-solid border-border bg-bg-container">
						<Spin tip="正在加载门户菜单" size="large" />
					</div>
				) : directoryError ? (
					<div data-testid="data-portal-error" className="flex min-h-[420px] flex-col items-center justify-center gap-3 rounded-xl border border-solid border-border bg-bg-container p-8 text-center">
						<h2 className="m-0 text-lg font-semibold text-text-primary">数据门户暂不可用</h2>
						<p className="m-0 text-sm text-text-secondary">{directoryError}</p>
						<Button type="primary" onClick={() => void loadPortal()}>重新加载</Button>
					</div>
				) : portal.directories.length === 0 ? (
					<div data-testid="data-portal-empty" className="flex min-h-[420px] flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-border bg-bg-container p-8 text-center">
						<h2 className="m-0 text-lg font-semibold text-text-primary">门户尚未编排</h2>
						<p className="m-0 max-w-xl text-sm text-text-secondary">请先建立月份、业务主题等目录，再把已发布的大屏或看板加入相应目录。</p>
						{portal.can_write ? <Button type="primary" onClick={() => setEditorOpen(true)}>开始编排门户</Button> : <span className="text-sm text-text-secondary">请联系数据管理员完成门户编排。</span>}
					</div>
				) : (
					<>
						{catalogWarning ? <div className="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">{catalogWarning}</div> : null}
						<div className="flex min-h-0 flex-1 flex-col gap-4 lg:flex-row">
							<aside className="w-full shrink-0 rounded-xl border border-solid border-border bg-bg-container p-3 lg:w-[300px]">
								<Input.Search
									allowClear
									placeholder="搜索目录、大屏或看板"
									value={searchKeyword}
									onChange={(event) => setSearchKeyword(event.target.value)}
								/>
								<div className="mb-2 mt-3 flex items-center justify-between text-xs text-text-secondary">
									<span>门户菜单</span>
									<span>{countContentLeaves(fullTree)} 项可用内容</span>
								</div>
								{treeData.length > 0 ? (
									<Tree
										blockNode
										showLine={{ showLeafIcon: false }}
										treeData={treeData}
										expandedKeys={expandedKeys}
										selectedKeys={selectedContent ? [selectedContent.key] : []}
										onExpand={setExpandedKeys}
										onSelect={handleTreeSelect}
										titleRender={(node) => {
											const portalNode = node as DataPortalTreeNode;
											return (
												<span className="inline-flex min-w-0 items-center">
													<span className="truncate">{portalNode.title}</span>
													{portalNode.nodeType === "CONTENT" ? <Tag className="ml-2 shrink-0" color={portalNode.contentType === "SCREEN" ? "blue" : "purple"}>{portalNode.contentType === "SCREEN" ? "大屏" : "看板"}</Tag> : null}
												</span>
											);
										}}
									/>
								) : (
									<div className="py-10 text-center text-sm text-text-secondary">未找到匹配的目录或内容</div>
								)}
							</aside>

							<PortalPreviewFrame dark={Boolean(selectedScreen)} available={Boolean(selectedScreen || selectedDashboard)}>
								{screenRuntimeUrl && selectedScreen ? (
									<div className="flex h-full min-h-[680px] flex-col">
										<div className="flex flex-wrap items-center justify-between gap-2 border-0 border-b border-solid border-white/10 bg-[#101c2d] px-4 py-2 text-white">
											<div className="min-w-0">
												<div className="truncate font-medium">{selectedScreen.name || `大屏 ${String(selectedScreen.id)}`}</div>
												<div className="mt-0.5 text-xs text-white/55">已发布大屏 · v{selectedScreen.publishedVersionNo}</div>
											</div>
											<ClassificationTag value={selectedScreen.classification} size="small" />
										</div>
										<iframe
											data-testid="data-portal-runtime"
											title={`${selectedScreen.name || "数据大屏"}运行页`}
											src={screenRuntimeUrl}
											className="min-h-[620px] w-full flex-1 border-0 bg-[#08121f]"
										/>
									</div>
								) : selectedDashboard ? (
									<div data-testid="data-portal-runtime" className="min-h-[680px] p-4">
										<div className="mb-3 flex items-center justify-between border-0 border-b border-solid border-border pb-3">
											<div>
												<div className="font-medium text-text-primary">{selectedDashboard.name || `看板 ${selectedDashboard.id}`}</div>
												<div className="mt-0.5 text-xs text-text-secondary">已发布看板</div>
											</div>
											<Tag color="purple">看板</Tag>
										</div>
										<DashboardDetailPage key={String(selectedDashboard.id)} dashboardId={String(selectedDashboard.id)} embedded />
									</div>
								) : (
									<div data-testid={selectionRequested ? "data-portal-error" : "data-portal-selection-empty"} className="flex min-h-[520px] flex-col items-center justify-center gap-2 p-8 text-center">
										<h2 className="m-0 text-lg font-semibold text-text-primary">{selectionRequested ? "该门户内容当前不可用" : "从左侧选择大屏或看板"}</h2>
										<p className="m-0 text-sm text-text-secondary">{selectionRequested ? "内容可能已下线、尚未发布，或当前账号没有访问权限。" : "点击最末级内容菜单后，右侧将直接显示对应内容。"}</p>
									</div>
								)}
							</PortalPreviewFrame>
						</div>
					</>
				)}
			</div>
			{editor}
		</PageContainer>
	);
}
