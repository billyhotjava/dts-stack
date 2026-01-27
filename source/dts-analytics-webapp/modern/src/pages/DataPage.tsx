import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { analyticsApi, type DatabaseListItem } from "../api/analyticsApi";
import { PageContainer, PageHeader } from "../components/PageContainer/PageContainer";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Card, CardBody } from "../ui/Card/Card";
import { Button } from "../ui/Button/Button";
import { Badge } from "../ui/Badge/Badge";
import { Spinner } from "../ui/Loading/Spinner";
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

const DatabaseIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<ellipse cx="12" cy="5" rx="9" ry="3" />
		<path d="M3 5v14a9 3 0 0 0 18 0V5" />
		<path d="M3 12a9 3 0 0 0 18 0" />
	</svg>
);

export default function DataPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<DatabaseListItem[]>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.listDatabases()
			.then((r) => {
				if (cancelled) return;
				setState({ state: "loaded", value: r.data ?? [] });
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
				title={t(locale, "data.title")}
				subtitle={t(locale, "data.subtitle")}
				actions={
					<Link to="/data/new">
						<Button variant="primary" icon={<PlusIcon />}>
							{t(locale, "data.add")}
						</Button>
					</Link>
				}
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
			{state.state === "loaded" && state.value.length === 0 && (
				<EmptyState
					title={t(locale, "data.empty")}
					action={
						<Link to="/data/new">
							<Button variant="primary" icon={<PlusIcon />}>
								{t(locale, "data.add")}
							</Button>
						</Link>
					}
				/>
			)}
			{state.state === "loaded" && state.value.length > 0 && (
				<div className="grid3">
					{state.value.map((db) => (
						<Link key={db.id} to={`/data/${db.id}`} style={{ textDecoration: "none" }}>
							<Card variant="hoverable" style={{ height: "100%" }}>
								<CardBody>
									<div style={{ display: "flex", alignItems: "flex-start", gap: "var(--spacing-md)" }}>
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
											<DatabaseIcon />
										</div>
										<div style={{ flex: 1, minWidth: 0 }}>
											<h3 style={{ margin: 0, fontSize: "var(--font-size-md)", fontWeight: "var(--font-weight-semibold)" }}>
												{db.name ?? `db:${db.id}`}
											</h3>
											<p className="text-muted" style={{ margin: "var(--spacing-xs) 0 0", fontSize: "var(--font-size-sm)" }}>
												ID: {db.id}
											</p>
										</div>
										<Badge variant="default" size="sm">
											{db.engine ?? "-"}
										</Badge>
									</div>
								</CardBody>
							</Card>
						</Link>
					))}
				</div>
			)}
		</PageContainer>
	);
}
