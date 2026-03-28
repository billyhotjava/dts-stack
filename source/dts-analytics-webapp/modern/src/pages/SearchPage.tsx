import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import { analyticsApi, type SearchItem } from "../api/analyticsApi";
import { PageContainer, PageHeader } from "../components/PageContainer/PageContainer";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Spin, Button, Card, Tag } from "antd";
import { SearchInput } from "../ui/Input/Input";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "idle" }
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

// Icons
const SearchIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<circle cx="11" cy="11" r="8" />
		<path d="m21 21-4.35-4.35" />
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

const FolderIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z" />
	</svg>
);

function resultHref(item: SearchItem): string {
	if (item.model === "dashboard") return `/dashboards/${item.id}`;
	if (item.model === "card") return `/questions/${item.id}`;
	if (item.model === "collection") return `/collections/${item.id}`;
	return "/";
}

function resultIcon(model: string) {
	if (model === "dashboard") return <DashboardIcon />;
	if (model === "card") return <CardIcon />;
	if (model === "collection") return <FolderIcon />;
	return <CardIcon />;
}

export default function SearchPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const location = useLocation();
	const navigate = useNavigate();
	const q = new URLSearchParams(location.search).get("q") ?? "";
	const [value, setValue] = useState(q);
	const [state, setState] = useState<LoadState<{ data: SearchItem[]; total: number }>>({ state: "idle" });

	useEffect(() => {
		setValue(q);
	}, [q]);

	useEffect(() => {
		const query = (q ?? "").trim();
		if (!query) {
			setState({ state: "idle" });
			return;
		}

		let cancelled = false;
		setState({ state: "loading" });
		analyticsApi
			.search(query)
			.then((r) => {
				if (cancelled) return;
				setState({ state: "loaded", value: r });
			})
			.catch((e) => {
				if (cancelled) return;
				setState({ state: "error", error: e });
			});

		return () => {
			cancelled = true;
		};
	}, [q]);

	const handleSubmit = (e: React.FormEvent) => {
		e.preventDefault();
		const next = value.trim();
		if (!next) {
			navigate({ pathname: location.pathname, search: "" }, { replace: true });
		} else {
			navigate({ pathname: location.pathname, search: `?q=${encodeURIComponent(next)}` }, { replace: true });
		}
	};

	return (
		<PageContainer>
			<PageHeader
				title={t(locale, "search.title")}
			/>

			<Card style={{ marginBottom: "var(--spacing-lg)" }}>
				<form onSubmit={handleSubmit}>
					<div style={{ display: "flex", gap: "var(--spacing-sm)" }}>
						<div style={{ flex: "1 1 360px" }}>
							<SearchInput
								value={value}
								onChange={(e) => setValue(e.target.value)}
								placeholder={t(locale, "search.placeholder")}
								size="large"
							/>
						</div>
						<Button type="primary" htmlType="submit" icon={<SearchIcon />}>
							{t(locale, "search.button")}
						</Button>
					</div>
				</form>
			</Card>

			{state.state === "idle" && (
				<Card>
					<p className="text-muted" style={{ textAlign: "center", margin: 0 }}>
						{t(locale, "search.placeholder")}
					</p>
				</Card>
			)}
			{state.state === "loading" && (
				<Card>
					<div className="loading-container" style={{ padding: "var(--spacing-xl)" }}>
						<Spin size="large" />
					</div>
				</Card>
			)}
			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
			{state.state === "loaded" && (
				<Card>
					<div style={{ marginBottom: "var(--spacing-md)" }}>
						<Tag>
							{t(locale, "search.total")}: {state.value.total}
						</Tag>
					</div>
					{state.value.data.length === 0 ? (
						<EmptyState title={t(locale, "common.empty")} />
					) : (
						<div className="search-results">
							{state.value.data.map((item) => (
								<Link
									key={`${item.model}:${item.id}`}
									to={resultHref(item)}
									className="search-result-item"
								>
									<div className="search-result-icon">
										{resultIcon(item.model)}
									</div>
									<div className="search-result-content">
										<span className="search-result-name">{item.name ?? "-"}</span>
										<span className="search-result-meta">
											<Tag color={item.model === "dashboard" ? "processing" : item.model === "card" ? "success" : undefined}>
												{item.model === "dashboard" ? t(locale, "dashboards.title") : item.model === "card" ? t(locale, "questions.title") : item.model === "collection" ? t(locale, "collections.title") : item.model}
											</Tag>
											<span className="text-muted">{t(locale, "common.id")}: {String(item.id)}</span>
										</span>
									</div>
								</Link>
							))}
						</div>
					)}
				</Card>
			)}

			<style>{`
				.search-results {
					display: flex;
					flex-direction: column;
					gap: var(--spacing-xs);
				}

				.search-result-item {
					display: flex;
					align-items: center;
					gap: var(--spacing-md);
					padding: var(--spacing-sm) var(--spacing-md);
					border-radius: var(--radius-md);
					text-decoration: none;
					color: inherit;
					transition: background-color var(--transition-fast);
				}

				.search-result-item:hover {
					background: var(--color-bg-hover);
				}

				.search-result-icon {
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

				.search-result-content {
					display: flex;
					flex-direction: column;
					gap: var(--spacing-xs);
					flex: 1;
					min-width: 0;
				}

				.search-result-name {
					font-weight: var(--font-weight-medium);
					color: var(--color-text-primary);
				}

				.search-result-meta {
					display: flex;
					align-items: center;
					gap: var(--spacing-sm);
					font-size: var(--font-size-sm);
				}
			`}</style>
		</PageContainer>
	);
}
