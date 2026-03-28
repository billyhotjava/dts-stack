import { Link } from "react-router";
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type CardListItem } from "../api/analyticsApi";
import { PageContainer, PageHeader, EmptyState } from "../components/PageContainer/PageContainer";
import { Button, Card, Input, Skeleton, Tag } from "antd";
import { CardGrid } from "../components/DashboardGrid/DashboardGrid";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

// Icons
const ModelIcon = () => (
	<svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
		<path d="M12 2L2 7l10 5 10-5-10-5Z" />
		<path d="m2 17 10 5 10-5" />
		<path d="m2 12 10 5 10-5" />
	</svg>
);

const ModelCardIcon = () => (
	<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M12 2L2 7l10 5 10-5-10-5Z" />
		<path d="m2 17 10 5 10-5" />
		<path d="m2 12 10 5 10-5" />
	</svg>
);

const PlusIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M5 12h14" />
		<path d="M12 5v14" />
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

export default function ModelsPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<CardListItem[]>>({ state: "loading" });
	const [viewMode, setViewMode] = useState<"grid" | "list">("grid");
	const [searchQuery, setSearchQuery] = useState("");

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.listModels()
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

	const filteredModels = useMemo(() => {
		if (state.state !== "loaded") return [];
		if (!searchQuery.trim()) return state.value;
		const query = searchQuery.toLowerCase();
		return state.value.filter((c) =>
			(c.name || "").toLowerCase().includes(query) ||
			(c.description || "").toLowerCase().includes(query)
		);
	}, [state, searchQuery]);

	return (
		<PageContainer>
			<PageHeader
				title={t(locale, "models.title")}
				actions={
					<Link to="/questions/new">
						<Button type="primary" icon={<PlusIcon />}>
							{t(locale, "questions.new")}
						</Button>
					</Link>
				}
			/>

			{/* Filter Bar */}
			<div className="filterBar">
				<div style={{ flex: 1, maxWidth: 320 }}>
					<Input.Search
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
						aria-label="Grid view"
					/>
					<Button
						type={viewMode === "list" ? "primary" : "default"}
						size="small"
						icon={<ListIcon />}
						onClick={() => setViewMode("list")}
						aria-label="List view"
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
					icon={<ModelIcon />}
					title={t(locale, "common.empty")}
					description={t(locale, "models.emptyDesc")}
					action={
						<Link to="/questions/new">
							<Button type="primary" icon={<PlusIcon />}>
								{t(locale, "questions.new")}
							</Button>
						</Link>
					}
				/>
			)}

			{/* No Results */}
			{state.state === "loaded" && state.value.length > 0 && filteredModels.length === 0 && (
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
			{state.state === "loaded" && filteredModels.length > 0 && viewMode === "grid" && (
				<CardGrid columns={3} gap="md">
					{filteredModels.map((c) => (
						<Link key={c.id} to={`/questions/${c.id}`} style={{ textDecoration: "none" }}>
							<Card hoverable>
								<div className="model-card">
									<div className="model-card__icon">
										<ModelCardIcon />
									</div>
									<div className="model-card__content">
										<h3 className="model-card__title">{c.name || t(locale, "common.untitled")}</h3>
										{c.description && (
											<p className="model-card__desc">{c.description}</p>
										)}
										{c.display && (
											<Tag>
												{c.display}
											</Tag>
										)}
									</div>
								</div>
							</Card>
						</Link>
					))}
				</CardGrid>
			)}

			{/* List View */}
			{state.state === "loaded" && filteredModels.length > 0 && viewMode === "list" && (
				<Card styles={{ body: { padding: 0 } }}>
					<table className="table">
						<thead>
							<tr>
								<th>{t(locale, "common.name")}</th>
								<th>{t(locale, "common.type")}</th>
								<th style={{ width: 80 }}>{t(locale, "common.id")}</th>
							</tr>
						</thead>
						<tbody>
							{filteredModels.map((c) => (
								<tr key={String(c.id)}>
									<td>
										<Link to={`/questions/${c.id}`} className="link">
											{c.name || t(locale, "common.untitled")}
										</Link>
										{c.description && (
											<span className="muted" style={{ marginLeft: 8, fontSize: "0.85em" }}>{c.description}</span>
										)}
									</td>
									<td>
										{c.display && (
											<Tag>
												{c.display}
											</Tag>
										)}
									</td>
									<td className="muted">{c.id}</td>
								</tr>
							))}
						</tbody>
					</table>
				</Card>
			)}

			<style>{`
				.model-card {
					display: flex;
					align-items: flex-start;
					gap: var(--spacing-md);
				}

				.model-card__icon {
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

				.model-card__content {
					flex: 1;
					min-width: 0;
					display: flex;
					flex-direction: column;
					gap: var(--spacing-xs);
				}

				.model-card__title {
					margin: 0;
					font-size: var(--font-size-md);
					font-weight: var(--font-weight-semibold);
					color: var(--color-text-primary);
					overflow: hidden;
					text-overflow: ellipsis;
					white-space: nowrap;
				}

				.model-card__desc {
					margin: 0;
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
