import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import { analyticsApi, type SearchItem } from "../api/analyticsApi";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "idle" }
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

function resultHref(item: SearchItem): string {
	if (item.model === "dashboard") return `/dashboards/${item.id}`;
	if (item.model === "card") return `/questions/${item.id}`;
	if (item.model === "collection") return `/collections/${item.id}`;
	return "/";
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

	return (
		<div className="page">
			<h1 className="pageTitle">{t(locale, "search.title")}</h1>
			<div className="pageSub">{t(locale, "search.subtitle")}</div>
			<div style={{ height: 16 }} />

			<div className="card">
				<form
					onSubmit={(e) => {
						e.preventDefault();
						const next = value.trim();
						if (!next) {
							navigate({ pathname: location.pathname, search: "" }, { replace: true });
						} else {
							navigate({ pathname: location.pathname, search: `?q=${encodeURIComponent(next)}` }, { replace: true });
						}
					}}
				>
					<div className="row">
						<input
							style={{ flex: "1 1 360px", padding: "10px 12px", borderRadius: 10, border: "1px solid rgba(0,0,0,0.12)" }}
							value={value}
							onChange={(e) => setValue(e.target.value)}
							placeholder={t(locale, "search.placeholder")}
						/>
						<button className="btn" type="submit">
							Search
						</button>
					</div>
				</form>
			</div>

			<div style={{ height: 16 }} />

			<div className="card">
				{state.state === "idle" && <div className="muted">-</div>}
				{state.state === "loading" && <div>{t(locale, "loading")}</div>}
				{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
				{state.state === "loaded" && (
					<>
						<div className="row">
							<div className="muted">
								{t(locale, "search.total")}: {state.value.total}
							</div>
						</div>
						<div style={{ height: 8 }} />
						<table className="table">
							<thead>
								<tr>
									<th>{t(locale, "common.type")}</th>
									<th>{t(locale, "common.name")}</th>
									<th>{t(locale, "common.id")}</th>
								</tr>
							</thead>
							<tbody>
								{state.value.data.map((item) => (
									<tr key={`${item.model}:${item.id}`}>
										<td>
											<span className="tag">{String(item.model)}</span>
										</td>
										<td>
											<Link to={resultHref(item)}>{item.name ?? "-"}</Link>
										</td>
										<td>{String(item.id)}</td>
									</tr>
								))}
							</tbody>
						</table>
					</>
				)}
			</div>
		</div>
	);
}
