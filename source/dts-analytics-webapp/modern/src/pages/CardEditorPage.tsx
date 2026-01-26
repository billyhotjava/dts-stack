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
import { QueryBuilder } from "../components/query/QueryBuilder";
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

	return (
			<div className="page">
				<h1 className="pageTitle">
					{cardId ? `${t(locale, "questions.edit")} #${cardId}` : t(locale, "questions.new")}
				</h1>
				<div className="pageSub">
					<Link to="/questions">{t(locale, "nav.questions")}</Link>
					<span className="muted"> · </span>
					<span className="muted">{cardId ? `#${cardId}` : t(locale, "questions.unsaved")}</span>
				</div>

			<div style={{ height: 16 }} />

			{card?.state === "error" && <ErrorNotice locale={locale} error={card.error} />}
			{databases.state === "error" && <ErrorNotice locale={locale} error={databases.error} />}
				{collections.state === "error" && <ErrorNotice locale={locale} error={collections.error} />}
				{saveState?.state === "error" && <ErrorNotice locale={locale} error={saveState.error} />}

				{dbEmpty ? (
					<>
						<EmptyState
							title={t(locale, "questions.noDb")}
							action={
								<Link className="btn" to="/data/new">
									{t(locale, "data.add")}
								</Link>
							}
						/>
						<div style={{ height: 16 }} />
					</>
				) : null}

				<div className="card">
					<div className="row">
						<label style={{ flex: 1 }}>
							<div className="muted">{t(locale, "common.name")}</div>
							<input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="..." />
					</label>

					<label style={{ width: 260 }}>
						<div className="muted">{t(locale, "questions.database")}</div>
						<select
							className="input"
							value={databaseId ?? ""}
							onChange={(e) => setDatabaseId(Number.parseInt(e.target.value, 10) || null)}
							disabled={databases.state !== "loaded"}
						>
							<option value="" disabled>
								{t(locale, "loading")}
							</option>
							{databases.state === "loaded" &&
								databases.value.map((db) => (
									<option key={db.id} value={db.id}>
										{db.name ?? `db:${db.id}`}
									</option>
								))}
						</select>
					</label>

					<label style={{ width: 260 }}>
						<div className="muted">{t(locale, "questions.collection")}</div>
						<select
							className="input"
							value={collectionId ?? ""}
							onChange={(e) => setCollectionId(e.target.value ? Number.parseInt(e.target.value, 10) || null : null)}
							disabled={collections.state !== "loaded"}
						>
							<option value="">{t(locale, "collections.title")} (root)</option>
							{collections.state === "loaded" &&
								collections.value
									.filter((c) => c.id !== "root")
									.map((c) => (
										<option key={String(c.id)} value={String(c.id)}>
											{c.name ?? String(c.id)}
										</option>
									))}
						</select>
					</label>
				</div>

				<div style={{ height: 12 }} />

				<div className="row">
					<button
						className={mode === "builder" ? "btn btnActive" : "btn"}
						type="button"
						onClick={() => setMode("builder")}
						disabled={dbEmpty}
					>
						{t(locale, "questions.mode.builder")}
					</button>
					<button
						className={mode === "sql" ? "btn btnActive" : "btn"}
						type="button"
						onClick={() => setMode("sql")}
						disabled={dbEmpty}
					>
						{t(locale, "questions.mode.sql")}
					</button>
					{mode === "builder" ? <span className="muted">{t(locale, "questions.builder")}</span> : null}
					{mode === "sql" ? <span className="muted">{t(locale, "questions.sql")}</span> : null}
				</div>

				<div style={{ height: 12 }} />

				{mode === "builder" ? (
					<QueryBuilder
						databaseId={databaseId}
						initialDatasetQuery={builderInitialDatasetQuery}
						onDatasetQueryChange={(dq) => setBuilderDatasetQuery(dq)}
					/>
				) : (
				<label>
					<div className="muted">{t(locale, "questions.sql")}</div>
					<textarea
						className="textarea"
						style={{ width: "100%", minHeight: 200, fontFamily: "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace" }}
						value={sql}
						onChange={(e) => setSql(e.target.value)}
						placeholder="select 1"
					/>
				</label>
				)}

				<div style={{ height: 12 }} />

					<div className="row">
						<button
							className="btn"
							type="button"
							onClick={run}
							disabled={dbEmpty || !canRun || runState?.state === "loading"}
						>
							{t(locale, "questions.run")}
						</button>
						<button
							className="btn"
							type="button"
							onClick={save}
							disabled={dbEmpty || !canSave || saveState?.state === "loading"}
						>
							{t(locale, "questions.save")}
						</button>
						{saveState?.state === "loading" && <span className="muted">{t(locale, "loading")}</span>}
					</div>
				</div>

			<div style={{ height: 16 }} />

			<div className="card">
				<div className="row" style={{ justifyContent: "space-between", alignItems: "center" }}>
					<strong>{t(locale, "questions.queryResult")}</strong>
					<div className="row" style={{ gap: 4 }}>
						{VISUALIZATION_TYPES.map((vt) => (
							<button
								key={vt.value}
								className={displayType === vt.value ? "btn btnActive" : "btn"}
								type="button"
								onClick={() => setDisplayType(vt.value)}
								style={{ padding: "4px 8px", fontSize: 12 }}
							>
								{vt.label}
							</button>
						))}
					</div>
				</div>
				<div style={{ height: 8 }} />

				{runState === null && <div className="muted">—</div>}
				{runState?.state === "loading" && <div>{t(locale, "loading")}</div>}
				{runState?.state === "error" && <ErrorNotice locale={locale} error={runState.error} />}
				{runState?.state === "loaded" && (
					<>
						{runState.value?.data?.native_form?.query ? (
							<div style={{ marginBottom: 12 }}>
								<div className="muted">{t(locale, "questions.querySql")}</div>
								<pre style={{ whiteSpace: "pre-wrap", fontSize: 12, margin: "8px 0 0" }}>
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
			</div>
		</div>
	);
}
