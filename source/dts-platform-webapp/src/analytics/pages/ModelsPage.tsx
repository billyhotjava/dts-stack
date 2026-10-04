import { Link } from "react-router";
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type CardListItem } from "../api/analyticsApi";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";
import { Button, Card, Input, Skeleton, Tag } from "antd";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { CardGrid } from "../components/DashboardGrid/DashboardGrid";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

// Icons
const ModelCardIcon = () => (
	<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M12 2L2 7l10 5 10-5-10-5Z" />
		<path d="m2 17 10 5 10-5" />
		<path d="m2 12 10 5 10-5" />
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

	const modelColumns: ColumnsType<CardListItem> = [
		{
			title: t(locale, "common.name"),
			dataIndex: "name",
			sorter: (a, b) => (a.name || "").localeCompare(b.name || ""),
			key: "name",
			render: (name: string, record) => (
				<>
					<Link to={`/bi/questions/${record.id}`} className="text-brand hover:underline font-medium">
						{name || t(locale, "common.untitled")}
					</Link>
					{record.description && (
						<span className="text-text-secondary ml-2 text-sm">{record.description}</span>
					)}
				</>
			),
		},
		{
			title: t(locale, "common.type"),
			dataIndex: "display",
			key: "display",
			width: 100,
			render: (display: string) => display ? <Tag>{display}</Tag> : "-",
		},
		{
			title: t(locale, "common.id"),
			dataIndex: "id",
			key: "id",
			width: 80,
			render: (id: number) => <span className="text-text-muted">{id}</span>,
		},
	];

	return (
		<div className="space-y-4">
			<PageHeader
				title={t(locale, "models.title")}
				actions={
					<Link to="/bi/card/new">
						<Button type="primary">
							{t(locale, "questions.new")}
						</Button>
					</Link>
				}
			/>

			{/* Filter Bar */}
			<div className="flex items-center gap-sm p-md bg-surface-card border rounded-md mb-md">
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
						onClick={() => setViewMode("grid")}
						aria-label="网格视图"
					>网格视图</Button>
					<Button
						type={viewMode === "list" ? "primary" : "default"}
						size="small"
						onClick={() => setViewMode("list")}
						aria-label="列表视图"
					>列表视图</Button>
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
					icon="lucide:inbox"
					title={t(locale, "common.empty")}
					description={t(locale, "models.emptyDesc")}
					actions={
						<Link to="/bi/card/new">
							<Button type="primary">
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
					actions={
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
						<Link key={c.id} to={`/bi/questions/${c.id}`} style={{ textDecoration: "none" }}>
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
				<Card>
					<CompactTable
						rowKey={(record) => String(record.id)}
						columns={modelColumns}
						dataSource={filteredModels}
						pagination={false}
						size="small"
					/>
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
		</div>
	);
}
