import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type Metric, type PlatformMetric } from "../api/analyticsApi";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";
import { ErrorNotice } from "../components/ErrorNotice";
import { Spin, Card, Statistic, Tag, Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { getEffectiveLocale, t, type Locale } from "../i18n";
type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

// Icons
const MetricIcon = () => (
	<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M3 3v18h18" />
		<path d="m19 9-5 5-4-4-3 3" />
	</svg>
);

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

	const metricColumns: ColumnsType<Metric> = [
		{ title: t(locale, "common.name"), dataIndex: "name", key: "name", render: (v: string | undefined) => v ?? "-" },
		{ title: t(locale, "common.id"), dataIndex: "id", key: "id" },
	];

	const platformMetricColumns: ColumnsType<PlatformMetric> = [
		{ title: t(locale, "common.name"), dataIndex: "name", key: "name", render: (v: string | undefined) => v ?? "-" },
		{ title: t(locale, "common.id"), dataIndex: "id", key: "id" },
	];

	return (
		<div className="space-y-4">
			<PageHeader
				title={t(locale, "metrics.title")}
			/>

			{/* Summary Stats */}
			<div className="grid grid-cols-3 gap-md" style={{ marginBottom: "var(--spacing-lg)" }}>
				<Card><Statistic title={t(locale, "metrics.analytics")} value={localMetrics.state === "loaded" ? localMetrics.value.length : "-"} prefix={<MetricIcon />} /></Card>
				<Card><Statistic title={t(locale, "metrics.platform")} value={platformMetrics.state === "loaded" ? platformMetrics.value.length : "-"} prefix={<MetricIcon />} /></Card>
				<Card><Statistic title={t(locale, "common.total")} value={localMetrics.state === "loaded" && platformMetrics.state === "loaded" ? localMetrics.value.length + platformMetrics.value.length : "-"} prefix={<MetricIcon />} /></Card>
			</div>

			{/* Analytics Metrics */}
			<Card style={{ marginBottom: "var(--spacing-lg)" }}>
				<div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "var(--spacing-md)" }}>
					<div style={{ fontWeight: 600, fontSize: "var(--font-size-md)" }}>{t(locale, "metrics.analytics")}</div>
					<div>{localMetrics.state === "loaded" && (
							<Tag>{localMetrics.value.length}</Tag>
						)}</div>
				</div>
					{localMetrics.state === "loading" && (
						<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
							<Spin />
						</div>
					)}
					{localMetrics.state === "error" && <ErrorNotice locale={locale} error={localMetrics.error} />}
					{localMetrics.state === "loaded" && localMetrics.value.length === 0 && (
						<EmptyState title={t(locale, "common.empty")} description={t(locale, "metrics.analyticsEmpty")} />
					)}
					{localMetrics.state === "loaded" && localMetrics.value.length > 0 && (
						<Table columns={metricColumns} dataSource={localMetrics.value} rowKey={(m) => String(m.id)} pagination={false} size="small" />
					)}
			</Card>

			{/* Platform Metrics */}
			<Card>
				<div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "var(--spacing-md)" }}>
					<div style={{ fontWeight: 600, fontSize: "var(--font-size-md)" }}>{t(locale, "metrics.platform")}</div>
					<div>{platformMetrics.state === "loaded" && (
							<Tag>{platformMetrics.value.length}</Tag>
						)}</div>
				</div>
					{platformMetrics.state === "loading" && (
						<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
							<Spin />
						</div>
					)}
					{platformMetrics.state === "error" && <ErrorNotice locale={locale} error={platformMetrics.error} />}
					{platformMetrics.state === "loaded" && platformMetrics.value.length === 0 && (
						<EmptyState title={t(locale, "common.empty")} description={t(locale, "metrics.platformEmpty")} />
					)}
					{platformMetrics.state === "loaded" && platformMetrics.value.length > 0 && (
						<Table columns={platformMetricColumns} dataSource={platformMetrics.value} rowKey={(m) => String(m.id)} pagination={false} size="small" />
					)}
			</Card>
		</div>
	);
}
