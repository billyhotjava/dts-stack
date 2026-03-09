import { Link } from "react-router";
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type DashboardListItem } from "../api/analyticsApi";
import { PageContainer, PageHeader, EmptyState } from "../components/PageContainer/PageContainer";
import { Card, CardBody } from "../ui/Card/Card";
import { Button } from "../ui/Button/Button";
import { Input, SearchInput } from "../ui/Input/Input";
import { Badge } from "../ui/Badge/Badge";
import { Spinner } from "../ui/Loading/Spinner";
import { CardSkeleton } from "../ui/Loading/Skeleton";
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

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.listDashboards()
			.then((value) => {
				if (cancelled) return;
				setState({ state: "loaded", value });
			})
			.catch((e) => {
				if (cancelled) return;
				setState({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, []);

	const filteredDashboards = useMemo(() => {
		if (state.state !== "loaded") return [];
		if (!searchQuery.trim()) return state.value;
		const query = searchQuery.toLowerCase();
		return state.value.filter((d) =>
			(d.name || "").toLowerCase().includes(query) ||
			(d.description || "").toLowerCase().includes(query)
		);
	}, [state, searchQuery]);

	return (
		<PageContainer>
			<div data-testid="analytics-dashboards-page">
			<PageHeader
				title={t(locale, "dashboards.title")}
				actions={
					<Link to="/dashboards/new">
						<Button variant="primary" icon={<PlusIcon />}>
							{t(locale, "dashboards.new")}
						</Button>
					</Link>
				}
			/>

			{/* Filter Bar */}
			<div className="filterBar">
				<div style={{ flex: 1, maxWidth: 320 }}>
					<SearchInput
						data-testid="analytics-dashboard-search"
						placeholder={t(locale, "common.search")}
						value={searchQuery}
						onChange={(e) => setSearchQuery(e.target.value)}
						onClear={() => setSearchQuery("")}
					/>
				</div>
				<div style={{ marginLeft: "auto", display: "flex", gap: "var(--spacing-xs)" }}>
					<Button
						variant={viewMode === "grid" ? "primary" : "secondary"}
						size="sm"
						icon={<GridIcon />}
						onClick={() => setViewMode("grid")}
						aria-label={t(locale, "common.viewAll")}
					/>
					<Button
						variant={viewMode === "list" ? "primary" : "secondary"}
						size="sm"
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
						<CardSkeleton key={i} lines={2} />
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
							<Button variant="primary" icon={<PlusIcon />}>
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
						<Button variant="secondary" onClick={() => setSearchQuery("")}>
							{t(locale, "common.clearSearch")}
						</Button>
					}
				/>
			)}

			{/* Grid View */}
			{state.state === "loaded" && filteredDashboards.length > 0 && viewMode === "grid" && (
				<CardGrid columns={3} gap="md">
					{filteredDashboards.map((d) => (
						<Link key={d.id} data-testid={`analytics-dashboard-card-${d.id}`} to={`/dashboards/${d.id}`} style={{ textDecoration: "none" }}>
							<Card variant="hoverable" padding="md">
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
					))}
				</CardGrid>
			)}

			{/* List View */}
			{state.state === "loaded" && filteredDashboards.length > 0 && viewMode === "list" && (
				<Card padding="none">
					<table className="table">
						<thead>
							<tr>
								<th>{t(locale, "common.name")}</th>
								<th>{t(locale, "common.description")}</th>
								<th style={{ width: 80 }}>{t(locale, "common.id")}</th>
							</tr>
						</thead>
						<tbody>
							{filteredDashboards.map((d) => (
								<tr key={String(d.id)} data-testid={`analytics-dashboard-row-${d.id}`}>
									<td>
										<Link to={`/dashboards/${d.id}`} className="link">
											{d.name || t(locale, "common.untitled")}
										</Link>
									</td>
									<td className="muted truncate" style={{ maxWidth: 300 }}>
										{d.description || "-"}
									</td>
									<td className="muted">{d.id}</td>
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
