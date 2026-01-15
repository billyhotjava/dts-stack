import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { analyticsApi, type FieldDetail, type FieldValuesResponse } from "../api/analyticsApi";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

export default function FieldDetailPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const { dbId, tableId, fieldId } = useParams();

	const [fieldState, setFieldState] = useState<LoadState<FieldDetail>>({ state: "loading" });
	const [valuesState, setValuesState] = useState<LoadState<FieldValuesResponse>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;
		if (!fieldId) return;
		setFieldState({ state: "loading" });
		analyticsApi
			.getField(fieldId)
			.then((value) => {
				if (cancelled) return;
				setFieldState({ state: "loaded", value });
			})
			.catch((e) => {
				if (cancelled) return;
				setFieldState({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, [fieldId]);

	useEffect(() => {
		let cancelled = false;
		if (!fieldId) return;
		setValuesState({ state: "loading" });
		analyticsApi
			.getFieldValues(fieldId)
			.then((value) => {
				if (cancelled) return;
				setValuesState({ state: "loaded", value });
			})
			.catch((e) => {
				if (cancelled) return;
				setValuesState({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, [fieldId]);

	const title = (() => {
		if (fieldState.state !== "loaded") return `${t(locale, "field.title")} #${fieldId ?? "-"}`;
		const f = fieldState.value;
		return f.display_name || f.name || `${t(locale, "field.title")} #${String(f.id)}`;
	})();

	return (
		<div className="page">
			<h1 className="pageTitle">{title}</h1>
			<div className="pageSub">
				<Link to="/data">{t(locale, "data.title")}</Link>
				{dbId ? (
					<>
						<span className="muted"> · </span>
						<Link to={`/data/${encodeURIComponent(String(dbId))}`}>{t(locale, "data.db")} #{dbId}</Link>
					</>
				) : null}
				{dbId && tableId ? (
					<>
						<span className="muted"> · </span>
						<Link to={`/data/${encodeURIComponent(String(dbId))}/tables/${encodeURIComponent(String(tableId))}`}>
							{t(locale, "builder.table")} #{tableId}
						</Link>
					</>
				) : null}
			</div>

			<div style={{ height: 16 }} />

			{fieldState.state === "loading" && <div className="card">{t(locale, "loading")}</div>}
			{fieldState.state === "error" && <ErrorNotice locale={locale} error={fieldState.error} />}
			{fieldState.state === "loaded" ? (
				<div className="card">
					<table className="table">
						<tbody>
							<tr>
								<th style={{ width: 180 }}>{t(locale, "common.name")}</th>
								<td>{fieldState.value.display_name || fieldState.value.name || "-"}</td>
							</tr>
							<tr>
								<th>{t(locale, "common.id")}</th>
								<td>{String(fieldState.value.id)}</td>
							</tr>
							<tr>
								<th>{t(locale, "field.baseType")}</th>
								<td>{String(fieldState.value.base_type ?? "-")}</td>
							</tr>
							<tr>
								<th>{t(locale, "field.semanticType")}</th>
								<td>{String(fieldState.value.semantic_type ?? "-")}</td>
							</tr>
							<tr>
								<th>{t(locale, "field.visibility")}</th>
								<td>{String(fieldState.value.visibility_type ?? "-")}</td>
							</tr>
						</tbody>
					</table>
				</div>
			) : null}

			<div style={{ height: 16 }} />

			<div className="card">
				<div className="row" style={{ justifyContent: "space-between" }}>
					<strong>{t(locale, "field.values")}</strong>
					{valuesState.state === "loaded" ? (
						<span className="muted">
							{Array.isArray(valuesState.value.values) ? valuesState.value.values.length : 0}
							{valuesState.value.has_more_values ? "+" : ""}
						</span>
					) : null}
				</div>
				<div style={{ height: 12 }} />
				{valuesState.state === "loading" && <div>{t(locale, "loading")}</div>}
				{valuesState.state === "error" && <ErrorNotice locale={locale} error={valuesState.error} />}
				{valuesState.state === "loaded" && Array.isArray(valuesState.value.values) && valuesState.value.values.length === 0 ? (
					<EmptyState title={t(locale, "common.empty")} />
				) : null}
				{valuesState.state === "loaded" && Array.isArray(valuesState.value.values) && valuesState.value.values.length > 0 ? (
					<table className="table">
						<thead>
							<tr>
								<th>{t(locale, "field.value")}</th>
							</tr>
						</thead>
						<tbody>
							{valuesState.value.values.map((v, idx) => (
								<tr key={String(idx)}>
									<td style={{ fontFamily: "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace" }}>
										{v === null || v === undefined ? "" : String(v)}
									</td>
								</tr>
							))}
						</tbody>
					</table>
				) : null}
			</div>
		</div>
	);
}

