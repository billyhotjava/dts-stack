import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { analyticsApi, type DatabaseMetadataResponse } from "../api/analyticsApi";
import { PageContainer, PageHeader, Breadcrumb } from "../components/PageContainer/PageContainer";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Card, CardHeader, CardBody, CollapsibleCard } from "../ui/Card/Card";
import { Button } from "../ui/Button/Button";
import { SearchInput } from "../ui/Input/Input";
import { Badge } from "../ui/Badge/Badge";
import { Spinner } from "../ui/Loading/Spinner";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

// Icons
const SyncIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
		<path d="M3 3v5h5" />
		<path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16" />
		<path d="M16 16h5v5" />
	</svg>
);

const TableIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M12 3v18" />
		<rect width="18" height="18" x="3" y="3" rx="2" />
		<path d="M3 9h18" />
		<path d="M3 15h18" />
	</svg>
);

const PlusIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M5 12h14" />
		<path d="M12 5v14" />
	</svg>
);

const ClearIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M18 6 6 18" />
		<path d="m6 6 12 12" />
	</svg>
);

export default function DatabaseDetailPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const { dbId } = useParams();
	const [state, setState] = useState<LoadState<DatabaseMetadataResponse>>({ state: "loading" });
	const [syncing, setSyncing] = useState(false);
	const [q, setQ] = useState("");

	const reload = useCallback(() => {
		let cancelled = false;
		if (!dbId) return () => { };
		setState({ state: "loading" });
		analyticsApi
			.getDatabaseMetadata(dbId)
			.then((v) => {
				if (cancelled) return;
				setState({ state: "loaded", value: v });
			})
			.catch((e) => {
				if (cancelled) return;
				setState({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, [dbId]);

	useEffect(() => {
		return reload();
	}, [reload]);

	const tables: any[] = useMemo(() => {
		if (state.state !== "loaded") return [];
		const v: any = state.value;
		return Array.isArray(v?.tables) ? v.tables : [];
	}, [state]);

	const filteredTables = useMemo(() => {
		const needle = q.trim().toLowerCase();
		if (!needle) return tables;
		return tables.filter((t) => {
			const name = String(t?.name ?? "").toLowerCase();
			const schema = String(t?.schema_name ?? t?.schema ?? "").toLowerCase();
			return name.includes(needle) || schema.includes(needle);
		});
	}, [tables, q]);

	const tablesBySchema = useMemo(() => {
		const m = new Map<string, any[]>();
		for (const t of filteredTables) {
			const schema = String(t?.schema_name ?? t?.schema ?? "").trim() || "(default)";
			const list = m.get(schema) ?? [];
			list.push(t);
			m.set(schema, list);
		}
		return Array.from(m.entries()).sort(([a], [b]) => a.localeCompare(b, "en"));
	}, [filteredTables]);

	async function syncSchema() {
		if (!dbId) return;
		setSyncing(true);
		try {
			await analyticsApi.syncDatabaseSchema(dbId);
			reload();
		} finally {
			setSyncing(false);
		}
	}

	return (
		<PageContainer>
			<PageHeader
				title={`${t(locale, "data.db")} #${dbId}`}
				breadcrumbs={
					<Breadcrumb items={[
						{ label: t(locale, "data.title"), href: "/data" },
						{ label: `Database #${dbId}` }
					]} />
				}
				actions={
					<Button
						variant="secondary"
						icon={<SyncIcon />}
						loading={syncing}
						onClick={syncSchema}
					>
						{syncing ? t(locale, "data.syncing") : t(locale, "data.sync")}
					</Button>
				}
			/>

			<Card style={{ marginBottom: "var(--spacing-lg)" }}>
				<CardBody>
					<div style={{ display: "flex", alignItems: "center", gap: "var(--spacing-md)" }}>
						<div style={{ flex: 1, maxWidth: 520 }}>
							<SearchInput
								value={q}
								onChange={(e) => setQ(e.target.value)}
								placeholder={t(locale, "search.placeholder")}
							/>
						</div>
						{q.trim() && (
							<Button variant="tertiary" size="sm" icon={<ClearIcon />} onClick={() => setQ("")}>
								{t(locale, "builder.remove")}
							</Button>
						)}
						<div style={{ marginLeft: "auto" }}>
							<Badge variant="default">
								{t(locale, "data.tables")}: {filteredTables.length}
							</Badge>
						</div>
					</div>
				</CardBody>
			</Card>

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
			{state.state === "loaded" && tables.length === 0 && (
				<EmptyState
					title={t(locale, "common.empty")}
					description={t(locale, "data.metaEmpty")}
					action={
						<Button variant="primary" icon={<SyncIcon />} loading={syncing} onClick={syncSchema}>
							{syncing ? t(locale, "data.syncing") : t(locale, "data.sync")}
						</Button>
					}
				/>
			)}
			{state.state === "loaded" && tables.length > 0 && filteredTables.length === 0 && (
				<EmptyState title={t(locale, "common.empty")} description={t(locale, "search.total") + ": 0"} />
			)}
			{state.state === "loaded" && filteredTables.length > 0 && (
				<>
					{tablesBySchema.map(([schema, list]) => (
						<CollapsibleCard
							key={schema}
							title={schema}
							subtitle={`${list.length} ${t(locale, "data.tables")}`}
							defaultOpen={tablesBySchema.length <= 1}
							style={{ marginBottom: "var(--spacing-md)" }}
						>
							<div className="table-list">
								{list.map((tb) => (
									<div key={String(tb?.id ?? tb?.name ?? Math.random())} className="table-list-item">
										<div className="table-list-item__icon">
											<TableIcon />
										</div>
										<div className="table-list-item__content">
											{tb?.id ? (
												<Link to={`/data/${encodeURIComponent(String(dbId))}/tables/${encodeURIComponent(String(tb.id))}`} className="table-list-item__name">
													{tb?.name ?? "-"}
												</Link>
											) : (
												<span className="table-list-item__name">{tb?.name ?? "-"}</span>
											)}
											<span className="table-list-item__id text-muted">ID: {String(tb?.id ?? "-")}</span>
										</div>
										{tb?.id && (
											<Link to={`/questions/new?db=${encodeURIComponent(String(dbId))}&table=${encodeURIComponent(String(tb.id))}`}>
												<Button variant="tertiary" size="sm" icon={<PlusIcon />}>
													{t(locale, "questions.new")}
												</Button>
											</Link>
										)}
									</div>
								))}
							</div>
						</CollapsibleCard>
					))}

					<CollapsibleCard title="Raw JSON" defaultOpen={false}>
						<pre style={{ whiteSpace: "pre-wrap", fontSize: 12, margin: 0, overflow: "auto" }}>
							{JSON.stringify(state.value, null, 2)}
						</pre>
					</CollapsibleCard>
				</>
			)}

			<style>{`
				.table-list {
					display: flex;
					flex-direction: column;
					gap: var(--spacing-xs);
				}

				.table-list-item {
					display: flex;
					align-items: center;
					gap: var(--spacing-md);
					padding: var(--spacing-sm) var(--spacing-md);
					border-radius: var(--radius-sm);
					transition: background-color var(--transition-fast);
				}

				.table-list-item:hover {
					background: var(--color-bg-hover);
				}

				.table-list-item__icon {
					display: flex;
					align-items: center;
					justify-content: center;
					width: 28px;
					height: 28px;
					border-radius: var(--radius-sm);
					background: var(--color-bg-tertiary);
					color: var(--color-text-secondary);
					flex-shrink: 0;
				}

				.table-list-item__content {
					display: flex;
					flex-direction: column;
					gap: 2px;
					flex: 1;
					min-width: 0;
				}

				.table-list-item__name {
					font-weight: var(--font-weight-medium);
					color: var(--color-text-primary);
					text-decoration: none;
				}

				.table-list-item__name:hover {
					color: var(--color-brand);
				}

				.table-list-item__id {
					font-size: var(--font-size-sm);
				}
			`}</style>
		</PageContainer>
	);
}
