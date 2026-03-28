import { Link } from "react-router";
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type CollectionListItem } from "../api/analyticsApi";
import { PageContainer, PageHeader } from "../components/PageContainer/PageContainer";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Spin, Card, Tag } from "antd";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

// Icons
const FolderIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z" />
	</svg>
);

export default function CollectionsPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<CollectionListItem[]>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.listCollections()
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

	return (
		<PageContainer>
			<PageHeader
				title={t(locale, "collections.title")}
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
				<div className="grid3">
					{state.value.map((c) => (
						<Link key={String(c.id)} to={`/collections/${encodeURIComponent(String(c.id))}`} style={{ textDecoration: "none" }}>
							<Card hoverable style={{ height: "100%" }}>
								<div style={{ display: "flex", alignItems: "center", gap: "var(--spacing-md)" }}>
									<div style={{
										display: "flex",
										alignItems: "center",
										justifyContent: "center",
										width: 40,
										height: 40,
										borderRadius: "var(--radius-md)",
										background: "var(--color-bg-hover)",
										color: "var(--color-brand)",
										flexShrink: 0
									}}>
										<FolderIcon />
									</div>
									<div style={{ flex: 1, minWidth: 0 }}>
										<h3 style={{ margin: 0, fontSize: "var(--font-size-md)", fontWeight: "var(--font-weight-semibold)" }}>
											{c.id === "root" ? (
											<>{t(locale, "collections.rootName")} <Tag color="blue">个人</Tag></>
										) : (c.name ?? "-")}
										</h3>
										<p className="text-muted" style={{ margin: "var(--spacing-xs) 0 0", fontSize: "var(--font-size-sm)" }}>
											{t(locale, "common.id")}: {String(c.id)}
										</p>
									</div>
								</div>
							</Card>
						</Link>
					))}
				</div>
			)}
		</PageContainer>
	);
}
