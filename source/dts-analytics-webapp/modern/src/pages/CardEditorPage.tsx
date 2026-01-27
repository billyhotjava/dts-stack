import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router";
import {
	analyticsApi,
	type CardDetail,
	type CardQueryResponse,
	type CollectionListItem,
	type DatabaseListItem,
} from "../api/analyticsApi";
import { ChartRenderer, type VisualizationType } from "../components/charts";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { PageContainer, PageHeader, Breadcrumb } from "../components/PageContainer/PageContainer";
import { QueryBuilder } from "../components/query/QueryBuilder";
import { Card, CardHeader, CardBody, CardFooter } from "../ui/Card/Card";
import { Button } from "../ui/Button/Button";
import { Input, TextArea } from "../ui/Input/Input";
import { NativeSelect } from "../ui/Input/Select";
import { Badge } from "../ui/Badge/Badge";
import { Spinner } from "../ui/Loading/Spinner";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

const VISUALIZATION_TYPES: { value: VisualizationType; label: string }[] = [
	{ value: "table", label: "Table" },
	{ value: "line", label: "Line" },
	{ value: "bar", label: "Bar" },
	{ value: "row", label: "Horizontal Bar" },
	{ value: "area", label: "Area" },
	{ value: "pie", label: "Pie" },
	{ value: "scalar", label: "Number" },
];

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

type EditorMode = "builder" | "sql";

function extractDatasetQuery(card: CardDetail): Record<string, unknown> | null {
	const dq: any = card.dataset_query;
	return dq && typeof dq === "object" ? (dq as Record<string, unknown>) : null;
}

function extractNativeSql(card: CardDetail): string {
	const dq: any = card.dataset_query;
	const q = dq?.native?.query;
	return typeof q === "string" ? q : "";
}

function extractDatabaseIdFromDatasetQuery(datasetQuery: Record<string, unknown> | null): number | null {
	const v: any = datasetQuery?.database;
	return typeof v === "number" && v > 0 ? v : null;
}

export default function CardEditorPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const navigate = useNavigate();
	const location = useLocation();
	const params = useParams();
	const cardId = params.id ? String(params.id) : null;

	const [databases, setDatabases] = useState<LoadState<DatabaseListItem[]>>({ state: "loading" });
	const [collections, setCollections] = useState<LoadState<CollectionListItem[]>>({ state: "loading" });
	const [card, setCard] = useState<LoadState<CardDetail> | null>(cardId ? { state: "loading" } : null);
	const [name, setName] = useState("");
	const [databaseId, setDatabaseId] = useState<number | null>(null);
	const [collectionId, setCollectionId] = useState<number | null>(null);
	const [mode, setMode] = useState<EditorMode>("builder");
	const [sql, setSql] = useState("");
	const [builderInitialDatasetQuery, setBuilderInitialDatasetQuery] = useState<Record<string, unknown> | null>(null);
	const [builderDatasetQuery, setBuilderDatasetQuery] = useState<Record<string, unknown> | null>(null);
	const [runState, setRunState] = useState<LoadState<CardQueryResponse> | null>(null);
	const [saveState, setSaveState] = useState<LoadState<CardDetail> | null>(null);
	const [displayType, setDisplayType] = useState<VisualizationType>("table");

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.listDatabases()
			.then((r) => {
				if (cancelled) return;
				setDatabases({ state: "loaded", value: r.data ?? [] });
			})
			.catch((e) => {
				if (cancelled) return;
				setDatabases({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, []);

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.listCollections()
			.then((r) => {
				if (cancelled) return;
				setCollections({ state: "loaded", value: r });
			})
			.catch((e) => {
				if (cancelled) return;
				setCollections({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, []);

	useEffect(() => {
		let cancelled = false;
		if (!cardId) return;
		analyticsApi
			.getCard(cardId)
			.then((v) => {
				if (cancelled) return;
				setCard({ state: "loaded", value: v });
				setName(v.name ?? "");
				setDisplayType((v.display as VisualizationType) || "table");
				const dq = extractDatasetQuery(v);
				setDatabaseId(extractDatabaseIdFromDatasetQuery(dq));
				setCollectionId(typeof v.collection_id === "number" ? v.collection_id : null);

				if (dq?.type === "query") {
					setMode("builder");
					setBuilderInitialDatasetQuery(dq);
					setBuilderDatasetQuery(dq);
					setSql("");
				} else {
					setMode("sql");
					setSql(extractNativeSql(v));
					setBuilderInitialDatasetQuery(null);
					setBuilderDatasetQuery(null);
				}
			})
			.catch((e) => {
				if (cancelled) return;
				setCard({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, [cardId]);

	useEffect(() => {
		if (databaseId) return;
		if (databases.state !== "loaded") return;
		if (databases.value.length > 0) {
			setDatabaseId(databases.value[0].id);
		}
	}, [databaseId, databases]);

	useEffect(() => {
		setRunState(null);
		if (mode === "builder") {
			setSql("");
		}
	}, [mode]);

	useEffect(() => {
		if (mode !== "builder") return;
		setBuilderInitialDatasetQuery(null);
		setBuilderDatasetQuery(null);
	}, [databaseId, mode]);

	useEffect(() => {
		if (cardId) return;
		const sp = new URLSearchParams(location.search);
		const preDb = Number.parseInt(sp.get("db") ?? "", 10);
		const preTable = Number.parseInt(sp.get("table") ?? "", 10);
		if (!Number.isFinite(preDb) || preDb <= 0) return;
		if (!Number.isFinite(preTable) || preTable <= 0) return;
		if (databases.state !== "loaded") return;
		const exists = databases.value.some((db) => db.id === preDb);
		if (!exists) return;
		setMode("builder");
		if (databaseId !== preDb) {
			setDatabaseId(preDb);
		}
		const init = { database: preDb, type: "query", query: { "source-table": preTable } } as Record<string, unknown>;
		setBuilderInitialDatasetQuery(init);
		setBuilderDatasetQuery(init);
	}, [cardId, location.search, databases, databaseId]);

	const canRun = mode === "sql" ? Boolean(databaseId && sql.trim()) : Boolean(builderDatasetQuery);
	const canSave =
		mode === "sql"
			? Boolean(name.trim() && databaseId && sql.trim())
			: Boolean(name.trim() && databaseId && builderDatasetQuery);
	const dbEmpty = databases.state === "loaded" && databases.value.length === 0;

	const run = async () => {
		if (!databaseId) return;
		const trimmedSql = sql.trim();
		if (mode === "sql" && !trimmedSql) return;
		if (mode === "builder" && !builderDatasetQuery) return;

		setRunState({ state: "loading" });
		try {
			const datasetQuery =
				mode === "builder"
					? { ...(builderDatasetQuery as Record<string, unknown>), context: "ad-hoc" }
					: { database: databaseId, type: "native", native: { query: trimmedSql }, context: "ad-hoc" };

			const res = await analyticsApi.runDatasetQuery(datasetQuery);
			setRunState({ state: "loaded", value: res });
		} catch (e) {
			setRunState({ state: "error", error: e });
		}
	};

	const save = async () => {
		if (!databaseId) return;
		const trimmedName = name.trim();
		const trimmedSql = sql.trim();
		if (!trimmedName) return;
		if (mode === "sql" && !trimmedSql) return;
		if (mode === "builder" && !builderDatasetQuery) return;

		setSaveState({ state: "loading" });
		try {
			const datasetQuery =
				mode === "builder"
					? builderDatasetQuery
					: {
							database: databaseId,
							type: "native",
							native: { query: trimmedSql },
						};
			const body = {
				name: trimmedName,
				collection_id: collectionId,
				display: displayType,
				dataset_query: datasetQuery,
				visualization_settings: {},
			};

			const saved = cardId ? await analyticsApi.updateCard(cardId, body) : await analyticsApi.createCard(body);
			setSaveState({ state: "loaded", value: saved });
			navigate(`/questions/${saved.id}`, { replace: true });
		} catch (e) {
			setSaveState({ state: "error", error: e });
		}
	};

	// Icons
	const PlayIcon = () => (
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<polygon points="6 3 20 12 6 21 6 3" />
		</svg>
	);

	const SaveIcon = () => (
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z" />
			<polyline points="17 21 17 13 7 13 7 21" />
			<polyline points="7 3 7 8 15 8" />
		</svg>
	);

	const databaseOptions = databases.state === "loaded"
		? databases.value.map((db) => ({ value: String(db.id), label: db.name ?? `db:${db.id}` }))
		: [{ value: "", label: t(locale, "loading") }];

	const collectionOptions = [
		{ value: "", label: `${t(locale, "collections.title")} (root)` },
		...(collections.state === "loaded"
			? collections.value
				.filter((c) => c.id !== "root")
				.map((c) => ({ value: String(c.id), label: c.name ?? String(c.id) }))
			: [])
	];

	return (
		<PageContainer>
			<PageHeader
				title={cardId ? `${t(locale, "questions.edit")} #${cardId}` : t(locale, "questions.new")}
				breadcrumbs={
					<Breadcrumb items={[
						{ label: t(locale, "nav.questions"), href: "/questions" },
						{ label: cardId ? `#${cardId}` : t(locale, "questions.unsaved") }
					]} />
				}
			/>

			{card?.state === "error" && <ErrorNotice locale={locale} error={card.error} />}
			{databases.state === "error" && <ErrorNotice locale={locale} error={databases.error} />}
			{collections.state === "error" && <ErrorNotice locale={locale} error={collections.error} />}
			{saveState?.state === "error" && <ErrorNotice locale={locale} error={saveState.error} />}

			{dbEmpty ? (
				<Card style={{ marginBottom: "var(--spacing-lg)" }}>
					<CardBody>
						<EmptyState
							title={t(locale, "questions.noDb")}
							action={
								<Link to="/data/new">
									<Button variant="primary">{t(locale, "data.add")}</Button>
								</Link>
							}
						/>
					</CardBody>
				</Card>
			) : null}

			<Card style={{ marginBottom: "var(--spacing-lg)" }}>
				<CardHeader title={t(locale, "questions.settings")} />
				<CardBody>
					<div className="form-grid" style={{ gridTemplateColumns: "1fr 260px 260px" }}>
						<Input
							label={t(locale, "common.name")}
							value={name}
							onChange={(e) => setName(e.target.value)}
							placeholder="My Question"
						/>

						<NativeSelect
							label={t(locale, "questions.database")}
							value={databaseId ? String(databaseId) : ""}
							onChange={(e) => setDatabaseId(Number.parseInt(e.target.value, 10) || null)}
							options={databaseOptions}
							disabled={databases.state !== "loaded"}
						/>

						<NativeSelect
							label={t(locale, "questions.collection")}
							value={collectionId ? String(collectionId) : ""}
							onChange={(e) => setCollectionId(e.target.value ? Number.parseInt(e.target.value, 10) || null : null)}
							options={collectionOptions}
							disabled={collections.state !== "loaded"}
						/>
					</div>

					<div style={{ marginTop: "var(--spacing-md)", display: "flex", alignItems: "center", gap: "var(--spacing-sm)" }}>
						<Button
							variant={mode === "builder" ? "primary" : "secondary"}
							size="sm"
							onClick={() => setMode("builder")}
							disabled={dbEmpty}
						>
							{t(locale, "questions.mode.builder")}
						</Button>
						<Button
							variant={mode === "sql" ? "primary" : "secondary"}
							size="sm"
							onClick={() => setMode("sql")}
							disabled={dbEmpty}
						>
							{t(locale, "questions.mode.sql")}
						</Button>
						<span className="text-muted" style={{ marginLeft: "var(--spacing-sm)" }}>
							{mode === "builder" ? t(locale, "questions.builder") : t(locale, "questions.sql")}
						</span>
					</div>
				</CardBody>
			</Card>

			<Card style={{ marginBottom: "var(--spacing-lg)" }}>
				<CardHeader title={mode === "builder" ? t(locale, "questions.mode.builder") : t(locale, "questions.mode.sql")} />
				<CardBody>
					{mode === "builder" ? (
						<QueryBuilder
							databaseId={databaseId}
							initialDatasetQuery={builderInitialDatasetQuery}
							onDatasetQueryChange={(dq) => setBuilderDatasetQuery(dq)}
						/>
					) : (
						<TextArea
							label={t(locale, "questions.sql")}
							value={sql}
							onChange={(e) => setSql(e.target.value)}
							placeholder="SELECT * FROM table"
							rows={10}
							style={{ fontFamily: "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace" }}
						/>
					)}
				</CardBody>
				<CardFooter align="between">
					<div style={{ display: "flex", gap: "var(--spacing-sm)", alignItems: "center" }}>
						<Button
							variant="primary"
							icon={<PlayIcon />}
							onClick={run}
							disabled={dbEmpty || !canRun || runState?.state === "loading"}
							loading={runState?.state === "loading"}
						>
							{t(locale, "questions.run")}
						</Button>
						<Button
							variant="secondary"
							icon={<SaveIcon />}
							onClick={save}
							disabled={dbEmpty || !canSave || saveState?.state === "loading"}
							loading={saveState?.state === "loading"}
						>
							{t(locale, "questions.save")}
						</Button>
					</div>
					<div />
				</CardFooter>
			</Card>

			<Card>
				<CardHeader
					title={t(locale, "questions.queryResult")}
					action={
						<div style={{ display: "flex", gap: "var(--spacing-xs)" }}>
							{VISUALIZATION_TYPES.map((vt) => (
								<Button
									key={vt.value}
									variant={displayType === vt.value ? "primary" : "tertiary"}
									size="sm"
									onClick={() => setDisplayType(vt.value)}
								>
									{vt.label}
								</Button>
							))}
						</div>
					}
				/>
				<CardBody>
					{runState === null && (
						<EmptyState title={t(locale, "questions.runFirst")} />
					)}
					{runState?.state === "loading" && (
						<div className="loading-container" style={{ padding: "var(--spacing-xl)" }}>
							<Spinner size="lg" />
						</div>
					)}
					{runState?.state === "error" && <ErrorNotice locale={locale} error={runState.error} />}
					{runState?.state === "loaded" && (
						<>
							{runState.value?.data?.native_form?.query ? (
								<div style={{ marginBottom: "var(--spacing-md)" }}>
									<div className="text-muted" style={{ marginBottom: "var(--spacing-xs)" }}>{t(locale, "questions.querySql")}</div>
									<pre style={{ whiteSpace: "pre-wrap", fontSize: "var(--font-size-sm)", margin: 0, padding: "var(--spacing-sm)", background: "var(--color-bg-tertiary)", borderRadius: "var(--radius-sm)" }}>
										{String(runState.value.data.native_form.query)}
									</pre>
								</div>
							) : null}
							<ChartRenderer
								data={{
									cols: (runState.value.data?.cols as any[]) ?? [],
									rows: (runState.value.data?.rows as any[]) ?? []
								}}
								display={displayType}
							/>
						</>
					)}
				</CardBody>
			</Card>

			<style>{`
				.form-grid {
					display: grid;
					gap: var(--spacing-md);
				}

				@media (max-width: 900px) {
					.form-grid {
						grid-template-columns: 1fr !important;
					}
				}
			`}</style>
		</PageContainer>
	);
}
