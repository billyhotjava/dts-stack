import { Archive, Eye, Pencil, RefreshCw, Search, Upload } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router";
import { useCatalogManageAccess, useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import { ActionButton, EmptyState, Panel, StatusTag, WorkspacePage } from "../components/WorkspacePage";
import {
	StandardPackageImportDialog,
	StandardsDetailDialog,
	StandardsEditorDialog,
} from "../standards/StandardsDialogs";
import {
	archiveStandardsRow,
	loadStandardsCatalog,
	loadStandardsDetail,
	type StandardsCatalogRow,
	type StandardsDetail,
	type StandardsView,
	standardsCapabilityFor,
} from "../standards/standardsWorkspaceAdapter";
import type { WorkspacePageProps } from "../types";

type Column = { key: keyof StandardsCatalogRow; title: string; width?: number };
type ViewConfig = {
	createLabel: string;
	importLabel: string;
	catalogDescription: string;
	columns: Column[];
};

const VIEW_CONFIG: Record<StandardsView, ViewConfig> = {
	fields: {
		createLabel: "新建字段标准",
		importLabel: "导入标准包",
		catalogDescription: "统一查看字段技术定义、业务含义、稳定版本和生效状态。",
		columns: [
			{ key: "code", title: "标准编码", width: 180 },
			{ key: "name", title: "标准名称" },
			{ key: "dataType", title: "数据类型", width: 140 },
			{ key: "definition", title: "业务定义" },
			{ key: "version", title: "版本", width: 90 },
			{ key: "state", title: "状态", width: 120 },
		],
	},
	codes: {
		createLabel: "新建标准代码",
		importLabel: "导入标准包",
		catalogDescription: "管理代码集、代码值数量、稳定版本和生效范围。",
		columns: [
			{ key: "code", title: "代码集编码", width: 190 },
			{ key: "name", title: "代码集名称" },
			{ key: "valueCount", title: "代码值数", width: 100 },
			{ key: "scope", title: "适用范围" },
			{ key: "version", title: "版本", width: 90 },
			{ key: "state", title: "状态", width: 120 },
		],
	},
	roots: {
		createLabel: "新建词根",
		importLabel: "导入标准包",
		catalogDescription: "服务端尚无独立词根类型，本页不会把业务术语伪装成词根。",
		columns: [
			{ key: "code", title: "词根编码", width: 180 },
			{ key: "name", title: "词根名称" },
			{ key: "state", title: "状态", width: 120 },
		],
	},
	dictionary: {
		createLabel: "新建命名词条",
		importLabel: "导入标准包",
		catalogDescription: "复用业务术语专业域，对齐标准编码、名称、别名和口径定义。",
		columns: [
			{ key: "code", title: "词条编码", width: 180 },
			{ key: "name", title: "业务名称" },
			{ key: "aliases", title: "别名" },
			{ key: "definition", title: "口径定义" },
			{ key: "domain", title: "数据域" },
			{ key: "state", title: "状态", width: 120 },
		],
	},
	mappings: {
		createLabel: "新建标准映射",
		importLabel: "导入标准包",
		catalogDescription: "按数据元查看模型字段保存的稳定标准 ID、版本和码表引用证据。",
		columns: [
			{ key: "code", title: "数据元编码", width: 180 },
			{ key: "name", title: "数据元名称" },
			{ key: "standard", title: "码表编码" },
			{ key: "model", title: "数据域" },
			{ key: "method", title: "引用方式", width: 130 },
			{ key: "version", title: "版本", width: 90 },
		],
	},
};

const STANDARDS_VIEWS = new Set<StandardsView>(["fields", "codes", "roots", "dictionary", "mappings"]);
const resolveView = (candidate: string): StandardsView =>
	STANDARDS_VIEWS.has(candidate as StandardsView) ? (candidate as StandardsView) : "fields";

const matchesState = (row: StandardsCatalogRow, state: string) => state === "all" || row.state === state;

const statusTone = (state: string): "neutral" | "success" | "warning" | "danger" => {
	if (/生效|发布|有效/.test(state)) return "success";
	if (/归档|废弃|停用/.test(state)) return "danger";
	if (/草稿|未知/.test(state)) return "warning";
	return "neutral";
};

export function StandardsWorkspace({ route }: WorkspacePageProps) {
	const [searchParams, setSearchParams] = useSearchParams();
	const view = resolveView(route.view);
	const config = VIEW_CONFIG[view];
	const capability = standardsCapabilityFor(view);
	const canCatalogManage = useCatalogManageAccess();
	const canGovernanceManage = useGovernanceManageAccess();
	const canManage = view === "codes" ? canGovernanceManage : canCatalogManage;
	const [query, setQuery] = useState("");
	const [selectedState, setSelectedState] = useState("all");
	const [rows, setRows] = useState<StandardsCatalogRow[]>([]);
	const [total, setTotal] = useState(0);
	const [loading, setLoading] = useState(false);
	const [catalogLoaded, setCatalogLoaded] = useState(false);
	const [loadError, setLoadError] = useState("");
	const [successMessage, setSuccessMessage] = useState("");
	const [targetNotice, setTargetNotice] = useState<{ message: string; found: boolean } | null>(null);
	const requestedStandardIdRef = useRef(searchParams.get("standardId")?.trim() || "");
	const [editorOpen, setEditorOpen] = useState(false);
	const [editingRow, setEditingRow] = useState<StandardsCatalogRow | null>(null);
	const [importOpen, setImportOpen] = useState(false);
	const [archivingId, setArchivingId] = useState("");
	const [detailRow, setDetailRow] = useState<StandardsCatalogRow | null>(null);
	const [detail, setDetail] = useState<StandardsDetail | null>(null);
	const [detailLoading, setDetailLoading] = useState(false);
	const [detailError, setDetailError] = useState("");
	const catalogRequestSequenceRef = useRef(0);
	const detailRequestSequenceRef = useRef(0);

	const consumeRequestedStandard = useCallback(() => {
		setSearchParams(
			(current) => {
				const next = new URLSearchParams(current);
				next.delete("standardId");
				return next;
			},
			{ replace: true },
		);
	}, [setSearchParams]);

	const refresh = useCallback(async () => {
		const requestSequence = ++catalogRequestSequenceRef.current;
		setLoading(true);
		setCatalogLoaded(false);
		setLoadError("");
		try {
			const catalog = await loadStandardsCatalog(view, query);
			if (requestSequence !== catalogRequestSequenceRef.current) return;
			setRows(catalog.rows);
			setTotal(catalog.total);
			setCatalogLoaded(true);
		} catch (cause) {
			if (requestSequence !== catalogRequestSequenceRef.current) return;
			setRows([]);
			setTotal(0);
			setLoadError(cause instanceof Error ? cause.message : "标准目录加载失败，请稍后重试");
		} finally {
			if (requestSequence === catalogRequestSequenceRef.current) setLoading(false);
		}
	}, [query, view]);

	useEffect(() => {
		setSelectedState("all");
		setSuccessMessage("");
		const timer = window.setTimeout(() => void refresh(), 250);
		return () => {
			window.clearTimeout(timer);
			catalogRequestSequenceRef.current += 1;
		};
	}, [refresh]);

	const stateOptions = useMemo(() => Array.from(new Set(rows.map((row) => row.state))).filter(Boolean), [rows]);
	const visibleRows = useMemo(() => rows.filter((row) => matchesState(row, selectedState)), [rows, selectedState]);
	const archiveLabel = view === "codes" ? "废弃" : "归档";

	const openEditor = (row: StandardsCatalogRow | null = null) => {
		setEditingRow(row);
		setEditorOpen(true);
	};

	const handleSaved = async (message: string) => {
		setSuccessMessage(message);
		await refresh();
	};

	const handleArchive = async (row: StandardsCatalogRow) => {
		if (!canManage || !capability.archive) return;
		if (!window.confirm(`确认${archiveLabel}“${row.name}”？操作后仅保留历史引用。`)) return;
		setArchivingId(row.id);
		setLoadError("");
		try {
			await archiveStandardsRow(view, row);
			setSuccessMessage(`“${row.name}”已${archiveLabel}`);
			await refresh();
		} catch (cause) {
			setLoadError(cause instanceof Error ? cause.message : `${archiveLabel}失败，请稍后重试`);
		} finally {
			setArchivingId("");
		}
	};

	const loadDetail = useCallback(
		async (row: StandardsCatalogRow) => {
			const requestSequence = ++detailRequestSequenceRef.current;
			setDetailLoading(true);
			setDetailError("");
			try {
				const nextDetail = await loadStandardsDetail(view, row);
				if (requestSequence !== detailRequestSequenceRef.current) return;
				setDetail(nextDetail);
			} catch (cause) {
				if (requestSequence !== detailRequestSequenceRef.current) return;
				setDetail(null);
				setDetailError(cause instanceof Error ? cause.message : "详情加载失败，请稍后重试");
			} finally {
				if (requestSequence === detailRequestSequenceRef.current) setDetailLoading(false);
			}
		},
		[view],
	);

	const openDetail = (row: StandardsCatalogRow) => {
		setDetailRow(row);
		setDetail(null);
		void loadDetail(row);
	};

	// biome-ignore lint/correctness/useExhaustiveDependencies: changing views must invalidate in-flight detail reads.
	useEffect(() => {
		detailRequestSequenceRef.current += 1;
		setDetailRow(null);
		setDetail(null);
		setDetailError("");
		setDetailLoading(false);
	}, [view]);

	useEffect(() => {
		const requestedStandardId = requestedStandardIdRef.current;
		if (!catalogLoaded || !requestedStandardId) return;
		const targetRow = rows.find((row) => row.id === requestedStandardId);
		if (targetRow && capability.details) {
			setDetailRow(targetRow);
			setDetail(null);
			void loadDetail(targetRow);
			setTargetNotice({ found: true, message: `已打开标准“${targetRow.name}”的真实详情。` });
		} else {
			setTargetNotice({
				found: false,
				message: `目标标准 ${requestedStandardId} 不在当前“${route.title}”真实目录中，请调整标准分类后重试。`,
			});
		}
		requestedStandardIdRef.current = "";
		consumeRequestedStandard();
	}, [capability.details, catalogLoaded, consumeRequestedStandard, loadDetail, route.title, rows]);

	const disabledReason = capability.disabledReason || (!canManage ? "当前账号没有标准维护权限" : undefined);
	const importDisabledReason = !canCatalogManage ? "当前账号没有标准包导入权限" : undefined;

	return (
		<WorkspacePage
			actions={
				<>
					<ActionButton
						disabled={!capability.importPackage || !canCatalogManage}
						onClick={() => setImportOpen(true)}
						title={importDisabledReason}
					>
						<Upload aria-hidden="true" size={15} /> {config.importLabel}
					</ActionButton>
					<ActionButton
						disabled={!capability.create || !canManage}
						kind="primary"
						onClick={() => openEditor()}
						title={disabledReason}
					>
						{config.createLabel}
					</ActionButton>
				</>
			}
			description={route.description}
			eyebrow="数据建模 / 数据标准"
			title={route.title}
		>
			{successMessage ? (
				<output className="dm-stage-notice">
					<StatusTag tone="success">成功</StatusTag>
					{successMessage}
				</output>
			) : null}
			{targetNotice ? (
				<output className="dm-stage-notice">
					<StatusTag tone={targetNotice.found ? "success" : "warning"}>
						{targetNotice.found ? "已定位" : "目标不可见"}
					</StatusTag>
					{targetNotice.message}
				</output>
			) : null}
			{!canManage && capability.create ? (
				<div className="dm-stage-notice" role="note">
					<StatusTag tone="warning">只读</StatusTag>当前账号没有标准维护权限，可继续查询和查看详情。
				</div>
			) : null}
			{capability.disabledReason ? (
				<div className="dm-stage-notice" role="note">
					{capability.disabledReason}
				</div>
			) : null}
			{loadError ? (
				<div className="dm-stage-notice" role="alert">
					<StatusTag tone="danger">加载失败</StatusTag>
					{loadError}
					<ActionButton onClick={() => void refresh()}>
						<RefreshCw aria-hidden="true" size={14} />
						重新加载
					</ActionButton>
				</div>
			) : null}
			<Panel
				actions={<span>{loading ? "正在加载…" : `共 ${total} 条`}</span>}
				subtitle={config.catalogDescription}
				title={`${route.title}目录`}
			>
				<div className="dm-toolbar">
					<div className="dm-search-control">
						<Search aria-hidden="true" size={15} />
						<input
							aria-label={`搜索${route.title}`}
							className="dm-input"
							onChange={(event) => setQuery(event.target.value)}
							placeholder="搜索编码、名称或业务定义"
							type="search"
							value={query}
						/>
					</div>
					<select
						aria-label="标准状态"
						className="dm-select dm-compact-select"
						disabled={!stateOptions.length}
						onChange={(event) => setSelectedState(event.target.value)}
						value={selectedState}
					>
						<option value="all">全部状态</option>
						{stateOptions.map((state) => (
							<option key={state} value={state}>
								{state}
							</option>
						))}
					</select>
					<ActionButton disabled={loading} onClick={() => void refresh()}>
						<RefreshCw aria-hidden="true" size={14} />
						刷新
					</ActionButton>
				</div>

				{loading && !visibleRows.length ? <output>正在加载真实标准目录…</output> : null}
				{!loading && !loadError && !visibleRows.length ? (
					<EmptyState
						description={
							capability.list ? "当前筛选条件下没有标准记录。" : capability.disabledReason || "当前能力不可用。"
						}
						title={capability.list ? "暂无真实数据" : "能力契约待补齐"}
					/>
				) : null}
				{visibleRows.length ? (
					<div className="dm-table-wrap">
						<table className="dm-table">
							<thead>
								<tr>
									{config.columns.map((column) => (
										<th key={column.key} style={column.width ? { width: column.width } : undefined}>
											{column.title}
										</th>
									))}
									<th style={{ width: 210 }}>操作</th>
								</tr>
							</thead>
							<tbody>
								{visibleRows.map((row) => (
									<tr key={row.id || row.code}>
										{config.columns.map((column) => (
											<td key={column.key}>
												{column.key === "state" ? (
													<StatusTag tone={statusTone(row.state)}>{row.state}</StatusTag>
												) : (
													String(row[column.key] ?? "—")
												)}
											</td>
										))}
										<td>
											<div className="dm-page__actions">
												{capability.details ? (
													<ActionButton kind="quiet" onClick={() => openDetail(row)}>
														<Eye aria-hidden="true" size={14} />
														详情
													</ActionButton>
												) : null}
												{capability.edit ? (
													<ActionButton
														disabled={!canManage}
														kind="quiet"
														onClick={() => openEditor(row)}
														title={!canManage ? "当前账号没有标准维护权限" : undefined}
													>
														<Pencil aria-hidden="true" size={14} />
														编辑
													</ActionButton>
												) : null}
												{capability.archive ? (
													<ActionButton
														disabled={!canManage || archivingId === row.id}
														kind="danger"
														onClick={() => void handleArchive(row)}
														title={!canManage ? "当前账号没有标准维护权限" : undefined}
													>
														<Archive aria-hidden="true" size={14} />
														{archivingId === row.id ? `${archiveLabel}中…` : archiveLabel}
													</ActionButton>
												) : null}
											</div>
										</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				) : null}
			</Panel>

			{editorOpen ? (
				<StandardsEditorDialog
					canManage={canManage}
					onClose={() => setEditorOpen(false)}
					onSaved={handleSaved}
					row={editingRow}
					view={view}
				/>
			) : null}
			{importOpen ? (
				<StandardPackageImportDialog
					canManage={canCatalogManage}
					onApplied={handleSaved}
					onClose={() => setImportOpen(false)}
				/>
			) : null}
			{detailRow ? (
				<StandardsDetailDialog
					detail={detail}
					error={detailError}
					loading={detailLoading}
					onClose={() => {
						detailRequestSequenceRef.current += 1;
						setDetailRow(null);
						setDetail(null);
						setDetailError("");
					}}
					onRetry={() => void loadDetail(detailRow)}
				/>
			) : null}
		</WorkspacePage>
	);
}
