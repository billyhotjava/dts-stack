import { Link } from "react-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import { analyticsApi, type DashboardListItem } from "../api/analyticsApi";
import { PageContainer, PageHeader, EmptyState } from "../components/PageContainer/PageContainer";
import { Button, Card, Checkbox, Dropdown, Input, Modal, Skeleton, message } from "antd";
import { EllipsisOutlined, DeleteOutlined } from "@ant-design/icons";
import { CardGrid } from "../components/DashboardGrid/DashboardGrid";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

// Icons
const PlusIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M5 12h14" />
		<path d="M12 5v14" />
	</svg>
);

const DashboardIcon = () => (
	<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<rect width="7" height="9" x="3" y="3" rx="1" />
		<rect width="7" height="5" x="14" y="3" rx="1" />
		<rect width="7" height="9" x="14" y="12" rx="1" />
		<rect width="7" height="5" x="3" y="16" rx="1" />
	</svg>
);

const GridIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<rect width="7" height="7" x="3" y="3" rx="1" />
		<rect width="7" height="7" x="14" y="3" rx="1" />
		<rect width="7" height="7" x="14" y="14" rx="1" />
		<rect width="7" height="7" x="3" y="14" rx="1" />
	</svg>
);

const ListIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<line x1="8" x2="21" y1="6" y2="6" />
		<line x1="8" x2="21" y1="12" y2="12" />
		<line x1="8" x2="21" y1="18" y2="18" />
		<line x1="3" x2="3.01" y1="6" y2="6" />
		<line x1="3" x2="3.01" y1="12" y2="12" />
		<line x1="3" x2="3.01" y1="18" y2="18" />
	</svg>
);

export default function DashboardsPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<DashboardListItem[]>>({ state: "loading" });
	const [viewMode, setViewMode] = useState<"grid" | "list">("grid");
	const [searchQuery, setSearchQuery] = useState("");
	const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());

	const loadDashboards = useCallback(() => {
		analyticsApi
			.listDashboards()
			.then((value) => {
				setState({ state: "loaded", value });
			})
			.catch((e) => {
				setState({ state: "error", error: e });
			});
	}, []);

	useEffect(() => {
		loadDashboards();
	}, [loadDashboards]);

	const filteredDashboards = useMemo(() => {
		if (state.state !== "loaded") return [];
		if (!searchQuery.trim()) return state.value;
		const query = searchQuery.toLowerCase();
		return state.value.filter((d) =>
			(d.name || "").toLowerCase().includes(query) ||
			(d.description || "").toLowerCase().includes(query)
		);
	}, [state, searchQuery]);

	/* ---- Delete handlers ---- */
	const handleDelete = (id: number, name: string) => {
		Modal.confirm({
			title: "移至回收站",
			content: `确定将「${name}」移至回收站？`,
			okText: "确定",
			cancelText: "取消",
			okButtonProps: { danger: true },
			onOk: async () => {
				await analyticsApi.deleteDashboard(id);
				message.success("已移至回收站");
				setSelectedIds((prev) => { const n = new Set(prev); n.delete(id); return n; });
				loadDashboards();
			},
		});
	};

	const handleBatchDelete = () => {
		if (selectedIds.size === 0) return;
		Modal.confirm({
			title: "批量移至回收站",
			content: `确定将 ${selectedIds.size} 项移至回收站？`,
			okText: "确定",
			cancelText: "取消",
			okButtonProps: { danger: true },
			onOk: async () => {
				const ids = Array.from(selectedIds);
				const results = await Promise.allSettled(ids.map((id) => analyticsApi.deleteDashboard(id)));
				const failed = results.filter((r) => r.status === "rejected").length;
				if (failed > 0) message.warning(`${ids.length - failed} 项已移至回收站，${failed} 项失败`);
				else message.success(`${ids.length} 项已移至回收站`);
				setSelectedIds(new Set());
				loadDashboards();
			},
		});
	};

	/* ---- Selection helpers ---- */
	const toggleSelect = (id: number) => {
		setSelectedIds((prev) => {
			const next = new Set(prev);
			if (next.has(id)) next.delete(id); else next.add(id);
			return next;
		});
	};
	const toggleSelectAll = () => {
		if (selectedIds.size === filteredDashboards.length) setSelectedIds(new Set());
		else setSelectedIds(new Set(filteredDashboards.map((d) => d.id)));
	};

	return (
		<PageContainer>
			<div data-testid="analytics-dashboards-page">
			<PageHeader
				title={t(locale, "dashboards.title")}
				actions={
					<Link to="/dashboards/new">
						<Button type="primary" icon={<PlusIcon />}>
							{t(locale, "dashboards.new")}
						</Button>
					</Link>
				}
			/>

			{/* Batch Action Bar */}
			{selectedIds.size > 0 && (
				<div style={{ display: "flex", alignItems: "center", gap: 12, padding: "8px 12px", background: "#f0f5ff", borderRadius: 6, marginBottom: 8 }}>
					<Checkbox
						checked={selectedIds.size === filteredDashboards.length}
						indeterminate={selectedIds.size > 0 && selectedIds.size < filteredDashboards.length}
						onChange={toggleSelectAll}
					/>
					<span>已选 {selectedIds.size} 项</span>
					<Button size="small" danger icon={<DeleteOutlined />} onClick={handleBatchDelete}>移至回收站</Button>
					<Button size="small" type="text" onClick={() => setSelectedIds(new Set())}>取消选择</Button>
				</div>
			)}

			{/* Filter Bar */}
			<div className="filterBar">
				<div style={{ flex: 1, maxWidth: 320 }}>
					<Input.Search
						data-testid="analytics-dashboard-search"
						placeholder={t(locale, "common.search")}
						value={searchQuery}
						onChange={(e) => setSearchQuery(e.target.value)}
						allowClear
						onSearch={() => {}}
					/>
				</div>
				<div style={{ marginLeft: "auto", display: "flex", gap: "var(--spacing-xs)" }}>
					<Button
						type={viewMode === "grid" ? "primary" : "default"}
						size="small"
						icon={<GridIcon />}
						onClick={() => setViewMode("grid")}
						aria-label={t(locale, "common.viewAll")}
					/>
					<Button
						type={viewMode === "list" ? "primary" : "default"}
						size="small"
						icon={<ListIcon />}
						onClick={() => setViewMode("list")}
						aria-label={t(locale, "common.viewAll")}
					/>
				</div>
			</div>

			{/* Loading State */}
			{state.state === "loading" && (
				<CardGrid columns={3} gap="md">
					{[1, 2, 3, 4, 5, 6].map((i) => (
						<Card key={i}><Skeleton active paragraph={{ rows: 2 }} /></Card>
					))}
				</CardGrid>
			)}

			{/* Error State */}
			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}

			{/* Empty State */}
			{state.state === "loaded" && state.value.length === 0 && (
				<EmptyState
					icon={<DashboardIcon />}
					title={t(locale, "common.empty")}
					description={t(locale, "dashboards.emptyDesc")}
					action={
						<Link to="/dashboards/new">
							<Button type="primary" icon={<PlusIcon />}>
								{t(locale, "dashboards.new")}
							</Button>
						</Link>
					}
				/>
			)}

			{/* No Results */}
			{state.state === "loaded" && state.value.length > 0 && filteredDashboards.length === 0 && (
				<EmptyState
					title={t(locale, "common.noResults")}
					description={t(locale, "common.noResultsDesc")}
					action={
						<Button type="default" onClick={() => setSearchQuery("")}>
							{t(locale, "common.clearSearch")}
						</Button>
					}
				/>
			)}

			{/* Grid View */}
			{state.state === "loaded" && filteredDashboards.length > 0 && viewMode === "grid" && (
				<CardGrid columns={3} gap="md">
					{filteredDashboards.map((d) => (
						<div key={d.id} style={{ position: "relative" }}>
							<div
								className="dashboard-card__checkbox"
								style={{ position: "absolute", top: 12, left: 12, zIndex: 1 }}
								onClick={(e) => e.stopPropagation()}
							>
								<Checkbox
									checked={selectedIds.has(d.id)}
									onChange={() => toggleSelect(d.id)}
								/>
							</div>
							<div
								className="dashboard-card__actions"
								style={{ position: "absolute", top: 12, right: 12, zIndex: 1 }}
								onClick={(e) => e.preventDefault()}
							>
								<Dropdown
									menu={{
										items: [
											{
												key: "delete",
												icon: <DeleteOutlined />,
												label: "移至回收站",
												danger: true,
												onClick: () => handleDelete(d.id, d.name || t(locale, "common.untitled")),
											},
										],
									}}
									trigger={["click"]}
								>
									<Button type="text" size="small" icon={<EllipsisOutlined />} onClick={(e) => e.stopPropagation()} />
								</Dropdown>
							</div>
							<Link data-testid={`analytics-dashboard-card-${d.id}`} to={`/dashboards/${d.id}`} style={{ textDecoration: "none" }}>
								<Card hoverable>
									<div className="dashboard-card">
										<div className="dashboard-card__icon">
											<DashboardIcon />
										</div>
										<div className="dashboard-card__content">
											<h3 className="dashboard-card__title">{d.name || t(locale, "common.untitled")}</h3>
											{d.description && (
												<p className="dashboard-card__desc">{d.description}</p>
											)}
										</div>
									</div>
								</Card>
							</Link>
						</div>
					))}
				</CardGrid>
			)}

			{/* List View */}
			{state.state === "loaded" && filteredDashboards.length > 0 && viewMode === "list" && (
				<Card styles={{ body: { padding: 0 } }}>
					<table className="table">
						<thead>
							<tr>
								<th style={{ width: 40 }}>
									<Checkbox
										checked={selectedIds.size === filteredDashboards.length}
										indeterminate={selectedIds.size > 0 && selectedIds.size < filteredDashboards.length}
										onChange={toggleSelectAll}
									/>
								</th>
								<th>{t(locale, "common.name")}</th>
								<th>{t(locale, "common.description")}</th>
								<th style={{ width: 80 }}>{t(locale, "common.id")}</th>
								<th style={{ width: 48 }} />
							</tr>
						</thead>
						<tbody>
							{filteredDashboards.map((d) => (
								<tr key={String(d.id)} data-testid={`analytics-dashboard-row-${d.id}`}>
									<td>
										<Checkbox
											checked={selectedIds.has(d.id)}
											onChange={() => toggleSelect(d.id)}
										/>
									</td>
									<td>
										<Link to={`/dashboards/${d.id}`} className="link">
											{d.name || t(locale, "common.untitled")}
										</Link>
									</td>
									<td className="muted truncate" style={{ maxWidth: 300 }}>
										{d.description || "-"}
									</td>
									<td className="muted">{d.id}</td>
									<td>
										<Dropdown
											menu={{
												items: [
													{
														key: "delete",
														icon: <DeleteOutlined />,
														label: "移至回收站",
														danger: true,
														onClick: () => handleDelete(d.id, d.name || t(locale, "common.untitled")),
													},
												],
											}}
											trigger={["click"]}
										>
											<Button type="text" size="small" icon={<EllipsisOutlined />} />
										</Dropdown>
									</td>
								</tr>
							))}
						</tbody>
					</table>
				</Card>
			)}

			</div>
			<style>{`
				.dashboard-card {
					display: flex;
					align-items: flex-start;
					gap: var(--spacing-md);
				}

				.dashboard-card__icon {
					display: flex;
					align-items: center;
					justify-content: center;
					width: 40px;
					height: 40px;
					border-radius: var(--radius-md);
					background: var(--color-bg-hover);
					color: var(--color-brand);
					flex-shrink: 0;
				}

				.dashboard-card__content {
					flex: 1;
					min-width: 0;
				}

				.dashboard-card__title {
					margin: 0;
					font-size: var(--font-size-md);
					font-weight: var(--font-weight-semibold);
					color: var(--color-text-primary);
					overflow: hidden;
					text-overflow: ellipsis;
					white-space: nowrap;
				}

				.dashboard-card__desc {
					margin: var(--spacing-xs) 0 0;
					font-size: var(--font-size-sm);
					color: var(--color-text-secondary);
					overflow: hidden;
					text-overflow: ellipsis;
					white-space: nowrap;
				}
			`}</style>
		</PageContainer>
	);
}
