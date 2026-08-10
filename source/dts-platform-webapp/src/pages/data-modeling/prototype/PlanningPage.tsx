import { Archive, Plus, RotateCw, Search } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { deleteBusinessProcessApi, type Sprint64BusinessProcess } from "@/api/sprint64GovernanceApi";
import { deleteWarehouseLayer, type WarehouseLayerView } from "@/api/warehouseLayerApi";
import { type CompactColumns, CompactTable } from "@/components/table";
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
import { PlanningPolicyForm } from "./PlanningPolicyForm";
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

type ProjectionTableRow = {
	key: string;
	id: string;
	cells: string[];
	group?: string;
	source?: unknown;
};

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
		if (!window.confirm(`确认停用业务过程“${process.name}”？稳定标识和历史引用将继续保留。`)) return;
		try {
			await deleteBusinessProcessApi(process.domainId, process.processId);
			show("业务过程已停用");
			await load();
		} catch (error) {
			show(normalizeModelingRequestFailure(error, "业务过程停用失败。").message);
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

	const renderActions = useCallback(
		(row: PlanningProjectionRow) => {
			switch (route.view) {
				case "business-domains":
				case "business-categories":
				case "domains":
				case "marts":
				case "subjects":
					return <Button onClick={() => openRow(row)}>{canMaintain ? "编辑" : "查看"}</Button>;
				case "processes": {
					const process = row.source as Sprint64BusinessProcess;
					const retired = String(process.lifecycleStatus || "ACTIVE").toUpperCase() === "RETIRED";
					return (
						<div className="dmx-row-actions">
							<Button onClick={() => openRow(row)}>{canMaintain ? "编辑" : "查看"}</Button>
							<Button danger disabled={!canMaintain || retired} onClick={() => void deleteProcess(process)}>
								<Archive size={14} /> {retired ? "已停用" : "停用"}
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
		},
		[canMaintain, deleteLayer, deleteProcess, openRow, route.view],
	);

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

	const projectionTableRows = useMemo<ProjectionTableRow[]>(() => {
		if (route.view !== "layers") {
			return pageRows.map((row) => ({ key: row.id, id: row.id, cells: row.cells, source: row.source }));
		}
		const nodes: ProjectionTableRow[] = [];
		let lastGroup = "";
		for (const row of pageRows) {
			const layer = asWarehouseLayer(row.source);
			const group = layer ? LAYER_GROUP_LABEL[layer.layerGroup] || layer.layerGroup : "";
			if (group !== lastGroup) {
				nodes.push({ key: `group-${group}`, id: "", cells: [], group });
				lastGroup = group;
			}
			nodes.push({ key: row.id, id: row.id, cells: row.cells, source: row.source });
		}
		return nodes;
	}, [pageRows, route.view]);

	const headerNames = projection?.headers || [];
	const columns = useMemo<CompactColumns<ProjectionTableRow>>(() => {
		const total = headerNames.length + (hasActions ? 1 : 0);
		return [
			...headerNames.map((header, index) => ({
				title: header,
				dataIndex: index,
				onCell: (record: ProjectionTableRow) => (record.group ? { colSpan: index === 0 ? total : 0 } : {}),
				render: (_: unknown, record: ProjectionTableRow) => {
					if (record.group) return index === 0 ? record.group : null;
					const cell = record.cells[index];
					return ["已发布", "已确认", "启用"].includes(cell) ? (
						<Status tone="success">{cell}</Status>
					) : ["草稿", "候选"].includes(cell) ? (
						<Status tone="warning">{cell}</Status>
					) : (
						cell
					);
				},
			})),
			...(hasActions
				? [
						{
							title: "操作",
							dataIndex: "actions",
							onCell: (record: ProjectionTableRow) => (record.group ? { colSpan: 0 } : {}),
							render: (_: unknown, row: ProjectionTableRow) =>
								row.group ? null : renderActions(row as PlanningProjectionRow),
						},
					]
				: []),
		];
	}, [headerNames, hasActions, renderActions]);

	return (
		<main className="dmx-page dmx-planning-layout">
			<PlanningSidebar activeView={sidebarActiveView} surface={navigationSurface} />
			<section className="dmx-planning-content">
				<PageHeader
					actions={
						<>
							{route.view !== "system" ? (
								<Button disabled={loading} onClick={() => void load()}>
									<RotateCw size={15} />
									刷新
								</Button>
							) : null}
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
				{route.view === "system" ? (
					<PlanningPolicyForm canMaintain={canMaintain} />
				) : loading ? (
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
									<CompactTable<ProjectionTableRow>
										columns={columns}
										dataSource={projectionTableRows}
										pagination={false}
										rowClassName={(record) =>
											record.group ? "dmx-table-group" : record.id === activeId ? "selected" : ""
										}
										rowKey="key"
									/>
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
