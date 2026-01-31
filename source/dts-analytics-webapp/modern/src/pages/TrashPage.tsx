import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { analyticsApi, type TrashResponse } from "../api/analyticsApi";
import { PageContainer, PageHeader } from "../components/PageContainer/PageContainer";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Card, CardBody } from "../ui/Card/Card";
import { Badge } from "../ui/Badge/Badge";
import { Spinner } from "../ui/Loading/Spinner";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

// Icons
const TrashIcon = () => (
	<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M3 6h18" />
		<path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" />
		<path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" />
	</svg>
);

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

export default function TrashPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<TrashResponse>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.getTrash()
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

	const items = useMemo(() => {
		if (state.state !== "loaded") return [];
		return [...(state.value.dashboards ?? []), ...(state.value.cards ?? [])];
	}, [state]);

	return (
		<PageContainer>
			<PageHeader
				title={t(locale, "trash.title")}
				subtitle={t(locale, "trash.subtitle")}
			/>

			{state.state === "loading" && (
				<Card>
					<CardBody>
						<div className="loading-container" style={{ padding: "var(--spacing-xl)" }}>
							<Spinner size="lg" />
						</div>
					</CardBody>
				</Card>
			)}
			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
			{state.state === "loaded" && items.length === 0 && (
				<Card>
					<CardBody>
						<div style={{ display: "flex", flexDirection: "column", alignItems: "center", padding: "var(--spacing-xl)", textAlign: "center" }}>
							<div style={{ color: "var(--color-text-tertiary)", marginBottom: "var(--spacing-md)" }}>
								<TrashIcon />
							</div>
							<h3 style={{ margin: 0, color: "var(--color-text-secondary)" }}>{t(locale, "common.empty")}</h3>
							<p className="text-muted" style={{ marginTop: "var(--spacing-sm)" }}>
								{t(locale, "trash.emptyDesc")}
							</p>
						</div>
					</CardBody>
				</Card>
			)}
			{state.state === "loaded" && items.length > 0 && (
				<div className="trash-items">
					{items.map((it) => {
						const href = it.model === "dashboard" ? `/dashboards/${it.id}` : `/questions/${it.id}`;
						return (
							<Link key={`${it.model}:${it.id}`} to={href} className="trash-item">
								<div className="trash-item__icon">
									{it.model === "dashboard" ? <DashboardIcon /> : <CardIcon />}
								</div>
								<div className="trash-item__content">
									<span className="trash-item__name">{it.name ?? "-"}</span>
									<span className="trash-item__meta">
										<Badge variant={it.model === "dashboard" ? "info" : "success"} size="sm">
											{it.model === "dashboard" ? t(locale, "dashboards.title") : t(locale, "questions.title")}
										</Badge>
										<span className="text-muted">{t(locale, "common.id")}: {it.id}</span>
									</span>
								</div>
							</Link>
						);
					})}
				</div>
			)}

			<style>{`
				.trash-items {
					display: flex;
					flex-direction: column;
					gap: var(--spacing-xs);
					background: var(--color-bg-primary);
					border: 1px solid var(--color-border);
					border-radius: var(--radius-md);
					padding: var(--spacing-sm);
				}

				.trash-item {
					display: flex;
					align-items: center;
					gap: var(--spacing-md);
					padding: var(--spacing-sm) var(--spacing-md);
					border-radius: var(--radius-md);
					text-decoration: none;
					color: inherit;
					transition: background-color var(--transition-fast);
				}

				.trash-item:hover {
					background: var(--color-bg-hover);
				}

				.trash-item__icon {
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

				.trash-item__content {
					display: flex;
					flex-direction: column;
					gap: var(--spacing-xs);
					flex: 1;
					min-width: 0;
				}

				.trash-item__name {
					font-weight: var(--font-weight-medium);
					color: var(--color-text-primary);
				}

				.trash-item__meta {
					display: flex;
					align-items: center;
					gap: var(--spacing-sm);
					font-size: var(--font-size-sm);
				}
			`}</style>
		</PageContainer>
	);
}
