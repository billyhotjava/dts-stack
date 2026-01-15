import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { analyticsApi, type DatabaseMetadataResponse } from "../api/analyticsApi";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

export default function DatabaseDetailPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const { dbId } = useParams();
	const [state, setState] = useState<LoadState<DatabaseMetadataResponse>>({ state: "loading" });
	const [syncing, setSyncing] = useState(false);
	const [q, setQ] = useState("");

	const reload = useCallback(() => {
		let cancelled = false;
		if (!dbId) return () => {};
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
		<div className="page">
			<h1 className="pageTitle">
				{t(locale, "data.db")} #{dbId}
			</h1>
			<div className="pageSub">
				<Link to="/data">{t(locale, "data.title")}</Link>
			</div>

			<div style={{ height: 16 }} />

			<div className="card">
				<div className="row" style={{ justifyContent: "space-between" }}>
					<div className="row">
						<strong>{t(locale, "data.tables")}</strong>
						<span className="muted">({filteredTables.length})</span>
					</div>
					<button className="btn" type="button" disabled={syncing} onClick={syncSchema}>
						{syncing ? t(locale, "data.syncing") : t(locale, "data.sync")}
					</button>
				</div>
			</div>

			<div style={{ height: 16 }} />

			<div className="card">
				<div className="row" style={{ justifyContent: "space-between" }}>
					<input
						className="input"
						value={q}
						onChange={(e) => setQ(e.target.value)}
						placeholder={t(locale, "search.placeholder")}
						style={{ maxWidth: 520 }}
					/>
					{q.trim() ? (
						<button className="btn" type="button" onClick={() => setQ("")}>
							{t(locale, "builder.remove")}
						</button>
					) : null}
				</div>
			</div>

			<div style={{ height: 16 }} />

			{state.state === "loading" && <div className="card">{t(locale, "loading")}</div>}
			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
			{state.state === "loaded" && tables.length === 0 && (
				<EmptyState
					title={t(locale, "common.empty")}
					description="提示：需要先同步元数据（同步后才会出现表/字段）。"
					action={
						<button className="btn" type="button" disabled={syncing} onClick={syncSchema}>
							{syncing ? t(locale, "data.syncing") : t(locale, "data.sync")}
						</button>
					}
				/>
			)}
			{state.state === "loaded" && tables.length > 0 && filteredTables.length === 0 && (
				<EmptyState title={t(locale, "common.empty")} description={t(locale, "search.total") + ": 0"} />
			)}
			{state.state === "loaded" && filteredTables.length > 0 && (
				<div className="card">
					{tablesBySchema.map(([schema, list]) => (
						<details key={schema} open={tablesBySchema.length <= 1}>
							<summary className="muted" style={{ cursor: "pointer" }}>
								{schema} <span className="muted">({list.length})</span>
							</summary>
							<div style={{ height: 12 }} />
							<table className="table">
								<thead>
									<tr>
										<th>{t(locale, "common.name")}</th>
										<th>{t(locale, "common.id")}</th>
										<th>{t(locale, "common.open")}</th>
									</tr>
								</thead>
								<tbody>
									{list.map((tb) => (
										<tr key={String(tb?.id ?? tb?.name ?? Math.random())}>
											<td>
												{tb?.id ? (
													<Link to={`/data/${encodeURIComponent(String(dbId))}/tables/${encodeURIComponent(String(tb.id))}`}>
														{tb?.name ?? "-"}
													</Link>
												) : (
													<span>{tb?.name ?? "-"}</span>
												)}
											</td>
											<td>{String(tb?.id ?? "-")}</td>
											<td>
												{tb?.id ? (
													<Link className="btn" to={`/questions/new?db=${encodeURIComponent(String(dbId))}&table=${encodeURIComponent(String(tb.id))}`}>
														{t(locale, "questions.new")}
													</Link>
												) : null}
											</td>
										</tr>
									))}
								</tbody>
							</table>
							<div style={{ height: 16 }} />
						</details>
					))}
					<div style={{ height: 12 }} />
					<details>
						<summary className="muted">Raw JSON</summary>
						<pre style={{ whiteSpace: "pre-wrap", fontSize: 12, margin: "12px 0 0" }}>
							{JSON.stringify(state.value, null, 2)}
						</pre>
					</details>
				</div>
			)}
		</div>
	);
}
