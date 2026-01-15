import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type Metric, type PlatformMetric } from "../api/analyticsApi";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

export default function MetricsPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [localMetrics, setLocalMetrics] = useState<LoadState<Metric[]>>({ state: "loading" });
	const [platformMetrics, setPlatformMetrics] = useState<LoadState<PlatformMetric[]>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.listMetrics()
			.then((value) => {
				if (cancelled) return;
				setLocalMetrics({ state: "loaded", value: Array.isArray(value) ? value : [] });
			})
			.catch((e) => {
				if (cancelled) return;
				setLocalMetrics({ state: "error", error: e });
			});

		analyticsApi
			.listPlatformMetrics()
			.then((value) => {
				if (cancelled) return;
				setPlatformMetrics({ state: "loaded", value: Array.isArray(value) ? value : [] });
			})
			.catch((e) => {
				if (cancelled) return;
				setPlatformMetrics({ state: "error", error: e });
			});

		return () => {
			cancelled = true;
		};
	}, []);

	return (
		<div className="page">
			<h1 className="pageTitle">{t(locale, "metrics.title")}</h1>
			<div className="pageSub">{t(locale, "metrics.subtitle")}</div>

			<div style={{ height: 16 }} />

			{localMetrics.state === "loading" && <div className="card">{t(locale, "loading")}</div>}
			{localMetrics.state === "error" && <ErrorNotice locale={locale} error={localMetrics.error} />}
			{localMetrics.state === "loaded" && localMetrics.value.length === 0 && (
				<EmptyState title={t(locale, "common.empty")} description="当前还没有创建任何指标（Analytics 内置指标）。" />
			)}
			{localMetrics.state === "loaded" && localMetrics.value.length > 0 && (
				<div className="card">
					<div className="row" style={{ justifyContent: "space-between" }}>
						<strong>Analytics metrics</strong>
						<div className="muted">{localMetrics.value.length}</div>
					</div>
					<div style={{ height: 12 }} />
					<table className="table">
						<thead>
							<tr>
								<th>{t(locale, "common.name")}</th>
								<th>{t(locale, "common.id")}</th>
							</tr>
						</thead>
						<tbody>
							{localMetrics.value.map((m) => (
								<tr key={String(m.id)}>
									<td>{m.name ?? "-"}</td>
									<td>{m.id}</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			)}

			<div style={{ height: 16 }} />

			{platformMetrics.state === "loading" && <div className="card">{t(locale, "loading")}</div>}
			{platformMetrics.state === "error" && <ErrorNotice locale={locale} error={platformMetrics.error} />}
			{platformMetrics.state === "loaded" && platformMetrics.value.length === 0 && (
				<EmptyState title={t(locale, "common.empty")} description="平台指标接口（dummy）当前返回空列表。" />
			)}
			{platformMetrics.state === "loaded" && platformMetrics.value.length > 0 && (
				<div className="card">
					<div className="row" style={{ justifyContent: "space-between" }}>
						<strong>Platform metrics</strong>
						<div className="muted">{platformMetrics.value.length}</div>
					</div>
					<div style={{ height: 12 }} />
					<table className="table">
						<thead>
							<tr>
								<th>{t(locale, "common.name")}</th>
								<th>{t(locale, "common.id")}</th>
							</tr>
						</thead>
						<tbody>
							{platformMetrics.value.map((m) => (
								<tr key={String(m.id)}>
									<td>{m.name ?? "-"}</td>
									<td>{m.id}</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			)}
		</div>
	);
}
