import { Link, useParams } from "react-router";
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type CollectionItem } from "../api/analyticsApi";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";
import { ErrorNotice } from "../components/ErrorNotice";
import { Spin, Card, Tag, Breadcrumb } from "antd";
import { getEffectiveLocale, t, type Locale } from "../i18n";
type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

// Icons
const DashboardIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<rect width="7" height="9" x="3" y="3" rx="1" />
		<rect width="7" height="5" x="14" y="3" rx="1" />
		<rect width="7" height="9" x="14" y="12" rx="1" />
		<rect width="7" height="5" x="3" y="16" rx="1" />
	</svg>
);

const CardIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<rect width="18" height="18" x="3" y="3" rx="2" />
		<path d="M3 9h18" />
		<path d="M9 21V9" />
	</svg>
);

export default function CollectionItemsPage() {
	const { id } = useParams();
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<CollectionItem[]>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.getCollectionItems(id ?? "root")
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
	}, [id]);

	return (
		<div className="space-y-4">
			<Breadcrumb items={[
				{ title: <Link to="/bi/collections">{t(locale, "collections.title")}</Link> },
				{ title: id === "root" ? t(locale, "collections.rootName") : (id ?? "root") },
			]} />
			<PageHeader
				title={t(locale, "collections.itemsTitle")}
			/>

			{state.state === "loading" && (
				<Card>
					<div className="loading-container" style={{ padding: "var(--spacing-xl)" }}>
						<Spin size="large" />
					</div>
				</Card>
			)}
			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
			{state.state === "loaded" && state.value.length === 0 && <EmptyState title={t(locale, "common.empty")} />}
			{state.state === "loaded" && state.value.length > 0 && (
				<div className="collection-items">
					{state.value.map((item) => {
						const href = item.model === "dashboard" ? `/bi/dashboards/${item.id}` : `/bi/questions/${item.id}`;
						return (
							<Link key={`${item.model}:${item.id}`} to={href} className="collection-item">
								<div className="collection-item__icon">
									{item.model === "dashboard" ? <DashboardIcon /> : <CardIcon />}
								</div>
								<div className="collection-item__content">
									<span className="collection-item__name">{item.name ?? "-"}</span>
									<span className="collection-item__meta">
										<Tag color={item.model === "dashboard" ? "processing" : "success"}>
											{item.model === "dashboard" ? t(locale, "dashboards.title") : t(locale, "questions.title")}
										</Tag>
										<span className="text-secondary">{t(locale, "common.id")}: {item.id}</span>
									</span>
								</div>
							</Link>
						);
					})}
				</div>
			)}

			<style>{`
				.collection-items {
					display: flex;
					flex-direction: column;
					gap: var(--spacing-xs);
					background: var(--color-bg-primary);
					border: 1px solid var(--color-border);
					border-radius: var(--radius-md);
					padding: var(--spacing-sm);
				}

				.collection-item {
					display: flex;
					align-items: center;
					gap: var(--spacing-md);
					padding: var(--spacing-sm) var(--spacing-md);
					border-radius: var(--radius-md);
					text-decoration: none;
					color: inherit;
					transition: background-color var(--transition-fast);
				}

				.collection-item:hover {
					background: var(--color-bg-hover);
				}

				.collection-item__icon {
					display: flex;
					align-items: center;
					justify-content: center;
					width: 32px;
					height: 32px;
					border-radius: var(--radius-sm);
					background: var(--color-bg-tertiary);
					color: var(--color-text-secondary);
					flex-shrink: 0;
				}

				.collection-item__content {
					display: flex;
					flex-direction: column;
					gap: var(--spacing-xs);
					flex: 1;
					min-width: 0;
				}

				.collection-item__name {
					font-weight: var(--font-weight-medium);
					color: var(--color-text-primary);
				}

				.collection-item__meta {
					display: flex;
					align-items: center;
					gap: var(--spacing-sm);
					font-size: var(--font-size-sm);
				}
			`}</style>
		</div>
	);
}
