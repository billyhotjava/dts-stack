import { Archive, Plus, RotateCw, Search } from "lucide-react";
import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { deleteBusinessProcessApi, type Sprint64BusinessProcess } from "@/api/sprint64GovernanceApi";
import { deleteWarehouseLayer, type WarehouseLayerView } from "@/api/warehouseLayerApi";
import { useArchitectureDictionaryWriteAccess } from "@/pages/data-architecture/useArchitectureDictionaryWriteAccess";
import { useUserInfo } from "@/store/userStore";
import type { DataModelingRoute } from "../types";
import { CatalogDomainForm } from "./PlanningCatalogEditors";
import {
	asWarehouseLayer,
	BusinessProcessForm,
	DataMartForm,
	SubjectDomainForm,
	WarehouseLayerForm,
} from "./PlanningEditors";
import { PlanningSidebar } from "./PlanningSidebar";
import { Button, Drawer, PageHeader, RequestState, Status, Toast, useTransientMessage } from "./PrototypePrimitives";
import {
	loadPlanningProjection,
	normalizeModelingRequestFailure,
	type PlanningProjectionRow,
} from "./services/planningProjectionService";
import { useDataModelingMenuGrant } from "./useDataModelingMenuGrant";

const ownerIdOf = (userInfo: unknown) => {
	if (!userInfo || typeof userInfo !== "object") return "";
	const value = (userInfo as Record<string, unknown>).id;
	return value == null ? "" : String(value).trim();
};

const LAYER_GROUP_LABEL: Record<string, string> = {
	STAGING: "贴源层",
	COMMON: "公共层",
	APPLICATION: "应用层",
};

const CREATABLE_VIEWS = new Set([
	"business-domains",
	"business-categories",
	"domains",
	"layers",
	"processes",
	"marts",
	"subjects",
]);

const PAGE_SIZE = 10;

type PlanningPageProps = {
	route: DataModelingRoute;
	surface?: "modeling" | "architecture";
	navigationSurface?: "modeling" | "architecture";
	sidebarActiveView?: string;
	activeId?: string;
	onActiveChange?: (id: string | null) => void;
};

export function PlanningPage({
	route,
	surface = "modeling",
	navigationSurface = surface,
	sidebarActiveView = route.view,
	activeId = "",
	onActiveChange,
}: PlanningPageProps) {
	const modelingMenuGrant = useDataModelingMenuGrant();
	const architectureWriteAccess = useArchitectureDictionaryWriteAccess();
	const canMaintain = surface === "architecture" ? architectureWriteAccess : modelingMenuGrant;
	const userInfo = useUserInfo();
	const requestEpoch = useRef(0);
	const [projection, setProjection] = useState<Awaited<ReturnType<typeof loadPlanningProjection>> | null>(null);
	const [loading, setLoading] = useState(true);
	const [failure, setFailure] = useState<{ kind: "permission" | "request"; message: string } | null>(null);
	const [query, setQuery] = useState("");
	const [page, setPage] = useState(1);
	const previousView = useRef(route.view);
	const [drawer, setDrawer] = useState<{
		open: boolean;
		editing: unknown;
		catalogView?: "business-categories" | "domains";
	}>({ open: false, editing: null });
	const { message, show } = useTransientMessage();

	const load = useCallback(async () => {
		const epoch = ++requestEpoch.current;
		setLoading(true);
		setFailure(null);
		try {
			const next = await loadPlanningProjection(route.view);
			if (requestEpoch.current !== epoch) return;
			setProjection(next);
		} catch (error) {
			if (requestEpoch.current !== epoch) return;
			setProjection(null);
			setFailure(normalizeModelingRequestFailure(error, `${route.title}读取失败，请稍后重新加载。`));
		} finally {
			if (requestEpoch.current === epoch) setLoading(false);
		}
	}, [route.title, route.view]);

	useEffect(() => {
		void load();
		return () => {
			requestEpoch.current += 1;
		};
	}, [load]);

	useEffect(() => {
		if (previousView.current !== route.view) {
			setQuery("");
			setPage(1);
			setDrawer({ open: false, editing: null });
			previousView.current = route.view;
		}
	}, [route.view]);

	// biome-ignore lint/correctness/useExhaustiveDependencies: a search change intentionally returns to the first page.
	useEffect(() => setPage(1), [query]);

	const visibleRows = useMemo(() => {
		const normalized = query.trim().toLocaleLowerCase();
		if (!normalized) return projection?.rows || [];
		return (projection?.rows || []).filter((row) =>
			row.cells.some((cell) => cell.toLocaleLowerCase().includes(normalized)),
		);
	}, [projection?.rows, query]);
	const pageCount = Math.max(1, Math.ceil(visibleRows.length / PAGE_SIZE));
	const currentPage = Math.min(page, pageCount);
	const pageRows = visibleRows.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

	useEffect(() => {
		if (page > pageCount) setPage(pageCount);
	}, [page, pageCount]);
	useEffect(() => {
		if (!activeId) return;
		const index = visibleRows.findIndex((row) => row.id === activeId);
		if (index >= 0) setPage(Math.floor(index / PAGE_SIZE) + 1);
	}, [activeId, visibleRows]);

	const creatable = CREATABLE_VIEWS.has(route.view);
	const hasActions = CREATABLE_VIEWS.has(route.view);

	const handleDone = useCallback(
		async (result: string) => {
			setDrawer({ open: false, editing: null });
			onActiveChange?.(null);
			show(result);
			await load();
		},
		[load, onActiveChange, show],
	);

	const closeDrawer = () => {
		setDrawer({ open: false, editing: null });
		onActiveChange?.(null);
	};

	const openRow = (row: PlanningProjectionRow) => {
		const source = row.source as { parentId?: string | null } | undefined;
		setDrawer({
			open: true,
			editing: row.source,
			catalogView:
				route.view === "business-domains" ? (source?.parentId ? "domains" : "business-categories") : undefined,
		});
		onActiveChange?.(row.id);
	};

	const deleteProcess = async (process: Sprint64BusinessProcess) => {
		if (!window.confirm(`确认删除业务过程“${process.name}”？`)) return;
		try {
			await deleteBusinessProcessApi(process.domainId, process.processId);
			show("业务过程已删除");
			await load();
		} catch (error) {
			show(normalizeModelingRequestFailure(error, "业务过程删除失败。").message);
		}
	};

	const deleteLayer = async (layer: WarehouseLayerView) => {
		if (!layer || !window.confirm(`确认删除自定义分层“${layer.name}（${layer.code}）”？`)) return;
		try {
			await deleteWarehouseLayer(layer.code);
			show("数仓分层已删除");
			await load();
		} catch (error) {
			show(normalizeModelingRequestFailure(error, "数仓分层删除失败。").message);
		}
	};

	const renderActions = (row: PlanningProjectionRow) => {
		switch (route.view) {
			case "business-domains":
			case "business-categories":
			case "domains":
			case "marts":
			case "subjects":
				return <Button onClick={() => openRow(row)}>{canMaintain ? "编辑" : "查看"}</Button>;
			case "processes": {
				const process = row.source as Sprint64BusinessProcess;
				return (
					<div className="dmx-row-actions">
						<Button onClick={() => openRow(row)}>{canMaintain ? "编辑" : "查看"}</Button>
						<Button danger disabled={!canMaintain} onClick={() => void deleteProcess(process)}>
							<Archive size={14} /> 删除
						</Button>
					</div>
				);
			}
			case "layers": {
				const layer = asWarehouseLayer(row.source);
				return (
					<div className="dmx-row-actions">
						<Button onClick={() => openRow(row)}>{layer?.builtin || !canMaintain ? "查看" : "编辑"}</Button>
						{layer?.deletable ? (
							<Button danger disabled={!canMaintain} onClick={() => void deleteLayer(layer)}>
								<Archive size={14} /> 删除
							</Button>
						) : (
							<span className="dmx-capability-note">内置</span>
						)}
					</div>
				);
			}
			default:
				return null;
		}
	};

	const renderEditor = () => {
		switch (route.view) {
			case "business-domains":
			case "business-categories":
			case "domains":
				return (
					<CatalogDomainForm
						canMaintain={canMaintain}
						initial={drawer.editing}
						onDone={handleDone}
						view={
							route.view === "business-domains"
								? drawer.catalogView || "business-categories"
								: (route.view as "business-categories" | "domains")
						}
					/>
				);
			case "processes":
				return <BusinessProcessForm canMaintain={canMaintain} initial={drawer.editing} onDone={handleDone} />;
			case "layers":
				return <WarehouseLayerForm canMaintain={canMaintain} initial={drawer.editing} onDone={handleDone} />;
			case "marts":
				return (
					<DataMartForm
						canMaintain={canMaintain}
						initial={drawer.editing}
						onDone={handleDone}
						ownerId={ownerIdOf(userInfo)}
					/>
				);
			case "subjects":
				return <SubjectDomainForm canMaintain={canMaintain} initial={drawer.editing} onDone={handleDone} />;
			default:
				return null;
		}
	};

	return (
		<main className="dmx-page dmx-planning-layout">
			<PlanningSidebar activeView={sidebarActiveView} surface={navigationSurface} />
			<section className="dmx-planning-content">
				<PageHeader
					actions={
						<>
							<Button disabled={loading} onClick={() => void load()}>
								<RotateCw size={15} />
								刷新
							</Button>
							{creatable && route.view === "business-domains" ? (
								<>
									<Button
										disabled={!canMaintain}
										onClick={() => setDrawer({ open: true, editing: null, catalogView: "business-categories" })}
									>
										<Plus size={15} /> 新建业务分类
									</Button>
									<Button
										disabled={!canMaintain}
										onClick={() => setDrawer({ open: true, editing: null, catalogView: "domains" })}
										primary
									>
										<Plus size={15} /> 新建数据域
									</Button>
								</>
							) : creatable ? (
								<Button disabled={!canMaintain} primary onClick={() => setDrawer({ open: true, editing: null })}>
									<Plus size={15} />
									新建{route.title}
								</Button>
							) : null}
						</>
					}
					description={route.description}
					title={route.title}
					trail={navigationSurface === "architecture" ? "数据架构 / 平台全局架构" : "数据建模 / 数仓规划"}
				/>
				{loading ? (
					<RequestState description={`正在读取${route.title}权威数据。`} kind="loading" title="正在加载" />
				) : failure ? (
					<RequestState
						description={failure.message}
						kind={failure.kind === "permission" ? "permission" : "error"}
						onRetry={failure.kind === "request" ? () => void load() : undefined}
						title={failure.kind === "permission" ? "无权访问" : "读取失败"}
					/>
				) : projection ? (
					<section className="dmx-catalog-panel">
						{projection.headers.length ? (
							<>
								<div className="dmx-list-toolbar">
									<label>
										<Search size={15} />
										<input
											onChange={(event) => setQuery(event.target.value)}
											placeholder="搜索名称、编码或说明"
											value={query}
										/>
									</label>
									<span>
										共 {visibleRows.length} 条，每页 {PAGE_SIZE} 条
									</span>
								</div>
								<div className="dmx-table-scroll">
									<table className="dmx-table dmx-table--catalog">
										<thead>
											<tr>
												{projection.headers.map((header) => (
													<th key={header}>{header}</th>
												))}
												{hasActions ? <th>操作</th> : null}
											</tr>
										</thead>
										<tbody>
											{route.view === "layers"
												? renderGroupedLayerRows(pageRows, renderActions, activeId)
												: pageRows.map((row) => (
														<tr className={row.id === activeId ? "selected" : ""} key={row.id}>
															{row.cells.map((cell, index) => (
																<td key={`${row.id}-${index}`}>
																	{["已发布", "已确认", "启用"].includes(cell) ? (
																		<Status tone="success">{cell}</Status>
																	) : ["草稿", "候选"].includes(cell) ? (
																		<Status tone="warning">{cell}</Status>
																	) : (
																		cell
																	)}
																</td>
															))}
															{hasActions ? <td>{renderActions(row)}</td> : null}
														</tr>
													))}
										</tbody>
									</table>
								</div>
								{visibleRows.length ? (
									<nav aria-label={`${route.title}分页`} className="dmx-pagination">
										<Button disabled={currentPage <= 1} onClick={() => setPage((value) => Math.max(1, value - 1))}>
											上一页
										</Button>
										<span>
											第 {currentPage} / {pageCount} 页
										</span>
										<Button
											disabled={currentPage >= pageCount}
											onClick={() => setPage((value) => Math.min(pageCount, value + 1))}
										>
											下一页
										</Button>
									</nav>
								) : null}
								{!visibleRows.length ? (
									<RequestState description="当前目录暂无记录。" kind="empty" title={`暂无${route.title}`} />
								) : null}
							</>
						) : (
							<RequestState
								description={projection.readOnlyReason || "当前目录暂无可展示记录。"}
								kind="empty"
								title={`暂无${route.title}`}
							/>
						)}
					</section>
				) : null}
			</section>
			{drawer.open ? (
				<Drawer
					footer={<Button onClick={closeDrawer}>关闭</Button>}
					onClose={closeDrawer}
					title={`${drawer.editing ? (canMaintain ? "编辑" : "查看") : "新建"}${
						route.view === "business-domains" ? (drawer.catalogView === "domains" ? "数据域" : "业务分类") : route.title
					}`}
				>
					{renderEditor()}
				</Drawer>
			) : null}
			<Toast message={message} />
		</main>
	);
}

function renderGroupedLayerRows(
	rows: PlanningProjectionRow[],
	renderActions: (row: PlanningProjectionRow) => ReactNode,
	activeId = "",
) {
	const nodes: ReactNode[] = [];
	let lastGroup = "";
	for (const row of rows) {
		const layer = asWarehouseLayer(row.source);
		const group = layer ? LAYER_GROUP_LABEL[layer.layerGroup] || layer.layerGroup : "";
		if (group !== lastGroup) {
			nodes.push(
				<tr className="dmx-table-group" key={`group-${group}`}>
					<td colSpan={rows.length ? row.cells.length + 1 : 1}>{group}</td>
				</tr>,
			);
			lastGroup = group;
		}
		nodes.push(
			<tr className={row.id === activeId ? "selected" : ""} key={row.id}>
				{row.cells.map((cell, index) => (
					<td key={`${row.id}-${index}`}>
						{["已发布", "已确认", "启用"].includes(cell) ? (
							<Status tone="success">{cell}</Status>
						) : ["草稿", "候选"].includes(cell) ? (
							<Status tone="warning">{cell}</Status>
						) : (
							cell
						)}
					</td>
				))}
				<td>{renderActions(row)}</td>
			</tr>,
		);
	}
	return nodes;
}
