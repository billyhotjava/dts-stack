import { Link } from "react-router";
import { useEffect, useMemo, useState, type JSX } from "react";
import { analyticsApi, type CardListItem } from "../api/analyticsApi";
import { PageContainer, PageHeader, EmptyState } from "../components/PageContainer/PageContainer";
import { Card } from "../ui/Card/Card";
import { Button } from "../ui/Button/Button";
import { SearchInput } from "../ui/Input/Input";
import { Badge } from "../ui/Badge/Badge";
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

const QuestionIcon = () => (
	<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<rect width="18" height="18" x="3" y="3" rx="2" />
		<path d="M3 9h18" />
		<path d="M9 21V9" />
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

const displayTypeIcons: Record<string, () => JSX.Element> = {
	table: () => (
		<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<rect width="18" height="18" x="3" y="3" rx="2" />
			<path d="M3 9h18" />
			<path d="M3 15h18" />
			<path d="M9 3v18" />
		</svg>
	),
	bar: () => (
		<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<line x1="12" x2="12" y1="20" y2="10" />
			<line x1="18" x2="18" y1="20" y2="4" />
			<line x1="6" x2="6" y1="20" y2="14" />
		</svg>
	),
	line: () => (
		<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<path d="M3 3v18h18" />
			<path d="m19 9-5 5-4-4-3 3" />
		</svg>
	),
	pie: () => (
		<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<path d="M21.21 15.89A10 10 0 1 1 8 2.83" />
			<path d="M22 12A10 10 0 0 0 12 2v10z" />
		</svg>
	),
	scalar: () => (
		<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<path d="M12 2v20" />
			<path d="M2 12h20" />
		</svg>
	),
};

function getDisplayTypeIcon(display?: string) {
	const IconComponent = displayTypeIcons[display || ""] || displayTypeIcons.table;
	return <IconComponent />;
}

export default function CardsPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<CardListItem[]>>({ state: "loading" });
	const [viewMode, setViewMode] = useState<"grid" | "list">("grid");
	const [searchQuery, setSearchQuery] = useState("");

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.listCards()
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

	const filteredCards = useMemo(() => {
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
				title={t(locale, "questions.title")}
				subtitle={t(locale, "questions.subtitle")}
				actions={
					<Link to="/questions/new">
						<Button variant="primary" icon={<PlusIcon />}>
							{t(locale, "questions.new")}
						</Button>
					</Link>
				}
			/>

			{/* Filter Bar */}
			<div className="filterBar">
				<div style={{ flex: 1, maxWidth: 320 }}>
					<SearchInput
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
						aria-label="Grid view"
					/>
					<Button
						variant={viewMode === "list" ? "primary" : "secondary"}
						size="sm"
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
						<CardSkeleton key={i} lines={2} />
					))}
				</CardGrid>
			)}

			{/* Error State */}
			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}

			{/* Empty State */}
			{state.state === "loaded" && state.value.length === 0 && (
				<EmptyState
					icon={<QuestionIcon />}
					title={t(locale, "common.empty")}
					description={t(locale, "questions.emptyDesc")}
					action={
						<Link to="/questions/new">
							<Button variant="primary" icon={<PlusIcon />}>
								{t(locale, "questions.new")}
							</Button>
						</Link>
					}
				/>
			)}

			{/* No Results */}
			{state.state === "loaded" && state.value.length > 0 && filteredCards.length === 0 && (
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
			{state.state === "loaded" && filteredCards.length > 0 && viewMode === "grid" && (
				<CardGrid columns={3} gap="md">
					{filteredCards.map((c) => (
						<Link key={c.id} to={`/questions/${c.id}`} style={{ textDecoration: "none" }}>
							<Card variant="hoverable" padding="md">
								<div className="question-card">
									<div className="question-card__icon">
										{getDisplayTypeIcon(c.display)}
									</div>
									<div className="question-card__content">
										<h3 className="question-card__title">{c.name || t(locale, "common.untitled")}</h3>
										{c.display && (
											<Badge size="sm" variant="default">
												{c.display}
											</Badge>
										)}
									</div>
								</div>
							</Card>
						</Link>
					))}
				</CardGrid>
			)}

			{/* List View */}
			{state.state === "loaded" && filteredCards.length > 0 && viewMode === "list" && (
				<Card padding="none">
					<table className="table">
						<thead>
							<tr>
								<th>{t(locale, "common.name")}</th>
								<th>{t(locale, "common.type")}</th>
								<th style={{ width: 80 }}>{t(locale, "common.id")}</th>
							</tr>
						</thead>
						<tbody>
							{filteredCards.map((c) => (
								<tr key={String(c.id)}>
									<td>
										<Link to={`/questions/${c.id}`} className="link">
											{c.name || t(locale, "common.untitled")}
										</Link>
									</td>
									<td>
										{c.display && (
											<Badge size="sm" variant="default">
												{c.display}
											</Badge>
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
				.question-card {
					display: flex;
					align-items: flex-start;
					gap: var(--spacing-md);
				}

				.question-card__icon {
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

				.question-card__content {
					flex: 1;
					min-width: 0;
					display: flex;
					flex-direction: column;
					gap: var(--spacing-xs);
				}

				.question-card__title {
					margin: 0;
					font-size: var(--font-size-md);
					font-weight: var(--font-weight-semibold);
					color: var(--color-text-primary);
					overflow: hidden;
					text-overflow: ellipsis;
					white-space: nowrap;
				}
			`}</style>
		</PageContainer>
	);
}
