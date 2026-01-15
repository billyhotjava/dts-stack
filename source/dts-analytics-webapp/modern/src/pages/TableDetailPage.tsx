import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { analyticsApi, type TableDetail } from "../api/analyticsApi";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

export default function TableDetailPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const { dbId, tableId } = useParams();
	const [state, setState] = useState<LoadState<TableDetail>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;
		if (!tableId) return;
		analyticsApi
			.getTable(tableId)
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
	}, [tableId]);

	const newQuestionHref =
		dbId && tableId ? `/questions/new?db=${encodeURIComponent(String(dbId))}&table=${encodeURIComponent(String(tableId))}` : "/questions/new";

	const fieldHref = (id: number) => {
		if (!dbId || !tableId) return null;
		return `/data/${encodeURIComponent(String(dbId))}/tables/${encodeURIComponent(String(tableId))}/fields/${encodeURIComponent(String(id))}`;
	};

	return (
		<div className="page">
			<h1 className="pageTitle">
				{t(locale, "builder.table")} #{tableId}
			</h1>
			<div className="pageSub">
				<Link to="/data">{t(locale, "data.title")}</Link>
				<span className="muted"> · </span>
				{dbId ? <Link to={`/data/${encodeURIComponent(String(dbId))}`}>{t(locale, "data.db")} #{dbId}</Link> : <span>{t(locale, "data.db")}</span>}
			</div>

			<div style={{ height: 16 }} />

			{state.state === "loading" && <div className="card">{t(locale, "loading")}</div>}
			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
			{state.state === "loaded" && (
				<>
					<div className="card">
						<div className="row" style={{ justifyContent: "space-between" }}>
							<div>
								<strong>{state.value.display_name || state.value.name || `table:${state.value.id}`}</strong>
								<div className="muted" style={{ marginTop: 6 }}>
									{state.value.schema ? `Schema: ${state.value.schema}` : null}
								</div>
							</div>
							<Link className="btn" to={newQuestionHref}>
								{t(locale, "questions.new")}
							</Link>
						</div>
						{state.value.description ? <div className="muted" style={{ marginTop: 10 }}>{state.value.description}</div> : null}
					</div>

					<div style={{ height: 16 }} />

					{Array.isArray(state.value.fields) && state.value.fields.length > 0 ? (
						<div className="card">
							<div className="row">
								<strong>{t(locale, "builder.fields")}</strong>
								<span className="muted">({state.value.fields.length})</span>
							</div>
							<div style={{ height: 12 }} />
							<table className="table">
								<thead>
									<tr>
										<th>{t(locale, "common.name")}</th>
										<th>{t(locale, "common.id")}</th>
										<th>Type</th>
										<th>Semantic</th>
									</tr>
								</thead>
								<tbody>
									{state.value.fields.map((f) => (
										<tr key={String(f.id)}>
											<td>
												{typeof f.id === "number" && f.id > 0 && fieldHref(f.id) ? (
													<Link to={fieldHref(f.id) as string}>{f.display_name || f.name || "-"}</Link>
												) : (
													(f.display_name || f.name || "-")
												)}
											</td>
											<td>{String(f.id)}</td>
											<td>{String(f.base_type ?? "-")}</td>
											<td>{String(f.semantic_type ?? "-")}</td>
										</tr>
									))}
								</tbody>
							</table>
						</div>
					) : (
						<EmptyState title={t(locale, "common.empty")} description="提示：字段列表为空，可能需要先同步数据库元数据。" />
					)}
				</>
			)}
		</div>
	);
}
