import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import {
	analyticsApi,
	type CardDetail,
	type CardQueryResponse,
	type CollectionListItem,
	type DatabaseListItem,
} from "../api/analyticsApi";
import { DataTable } from "../components/DataTable";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

function extractNativeSql(card: CardDetail): string {
	const dq: any = card.dataset_query;
	const q = dq?.native?.query;
	return typeof q === "string" ? q : "";
}

function extractDatabaseId(card: CardDetail): number | null {
	const dq: any = card.dataset_query;
	const v = dq?.database;
	return typeof v === "number" && v > 0 ? v : null;
}

export default function CardEditorPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const navigate = useNavigate();
	const params = useParams();
	const cardId = params.id ? String(params.id) : null;

	const [databases, setDatabases] = useState<LoadState<DatabaseListItem[]>>({ state: "loading" });
	const [collections, setCollections] = useState<LoadState<CollectionListItem[]>>({ state: "loading" });
	const [card, setCard] = useState<LoadState<CardDetail> | null>(cardId ? { state: "loading" } : null);
	const [name, setName] = useState("");
	const [databaseId, setDatabaseId] = useState<number | null>(null);
	const [collectionId, setCollectionId] = useState<number | null>(null);
	const [sql, setSql] = useState("");
	const [runState, setRunState] = useState<LoadState<CardQueryResponse> | null>(null);
	const [saveState, setSaveState] = useState<LoadState<CardDetail> | null>(null);

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
				setSql(extractNativeSql(v));
				setDatabaseId(extractDatabaseId(v));
				setCollectionId(typeof v.collection_id === "number" ? v.collection_id : null);
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

	const canRun = Boolean(databaseId && sql.trim());
	const canSave = Boolean(name.trim() && databaseId && sql.trim());

	const run = async () => {
		if (!databaseId) return;
		const query = sql.trim();
		if (!query) return;
		setRunState({ state: "loading" });
		try {
			const res = await analyticsApi.runDatasetQuery({
				database: databaseId,
				type: "native",
				native: { query },
				context: "ad-hoc",
			});
			setRunState({ state: "loaded", value: res });
		} catch (e) {
			setRunState({ state: "error", error: e });
		}
	};

	const save = async () => {
		if (!databaseId) return;
		const trimmedName = name.trim();
		const query = sql.trim();
		if (!trimmedName || !query) return;

		setSaveState({ state: "loading" });
		try {
			const body = {
				name: trimmedName,
				collection_id: collectionId,
				display: "table",
				dataset_query: {
					database: databaseId,
					type: "native",
					native: { query },
				},
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
				<span className="muted">{t(locale, "questions.unsaved")}</span>
			</div>

			<div style={{ height: 16 }} />

			{card?.state === "error" && <ErrorNotice locale={locale} error={card.error} />}
			{databases.state === "error" && <ErrorNotice locale={locale} error={databases.error} />}
			{collections.state === "error" && <ErrorNotice locale={locale} error={collections.error} />}
			{saveState?.state === "error" && <ErrorNotice locale={locale} error={saveState.error} />}

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

				<div style={{ height: 12 }} />

				<div className="row">
					<button className="btn" type="button" onClick={run} disabled={!canRun || runState?.state === "loading"}>
						{t(locale, "questions.run")}
					</button>
					<button className="btn" type="button" onClick={save} disabled={!canSave || saveState?.state === "loading"}>
						{t(locale, "questions.save")}
					</button>
					{saveState?.state === "loading" && <span className="muted">{t(locale, "loading")}</span>}
				</div>
			</div>

			<div style={{ height: 16 }} />

			<div className="card">
				<div className="row">
					<strong>{t(locale, "questions.queryResult")}</strong>
				</div>
				<div style={{ height: 8 }} />

				{runState === null && <div className="muted">—</div>}
				{runState?.state === "loading" && <div>{t(locale, "loading")}</div>}
				{runState?.state === "error" && <ErrorNotice locale={locale} error={runState.error} />}
				{runState?.state === "loaded" && (
					<DataTable cols={(runState.value.data?.cols as any[]) ?? []} rows={(runState.value.data?.rows as any[]) ?? []} maxRows={200} />
				)}
			</div>
		</div>
	);
}
