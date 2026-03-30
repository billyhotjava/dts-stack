// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import { Link, useParams } from "react-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import { analyticsApi, type DashboardCard, type DashboardDetail, type DashboardQueryResponse } from "../api/analyticsApi";
import { PageContainer, PageHeader } from "../components/PageContainer/PageContainer";
import { ErrorNotice } from "../components/ErrorNotice";
import { Input, Spin, Button, Card, Collapse, Tag, Select } from "antd";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import { writeTextToClipboard } from "../hooks/clipboard";
import { resolveRouteHref } from "../helpers/resolveAnalyticsUrl";
import { useDashboardCrossFilter } from "../hooks/useDashboardCrossFilter";
import { useDrillFilter } from "../hooks/useDrillFilter";
import { DashboardEditorGrid } from "./dashboard/DashboardEditorGrid";
import { DashboardFilterBar, type DashboardParameter } from "./dashboard/DashboardFilterBar";
import type { SeriesClickParams } from "../components/charts";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

export default function DashboardDetailPage() {
	const { id } = useParams();
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<DashboardDetail>>({ state: "loading" });
	const [paramOptions, setParamOptions] = useState<Record<string, string[]>>({});
	const [paramValues, setParamValues] = useState<Record<string, string>>({});
	const [dashcardResults, setDashcardResults] = useState<Record<number, LoadState<DashboardQueryResponse>>>({});
	const [showRaw, setShowRaw] = useState(false);
	const [shareUuid, setShareUuid] = useState<string>("");
	const [shareBusy, setShareBusy] = useState(false);
	const [shareCopied, setShareCopied] = useState(false);

	const crossFilter = useDashboardCrossFilter();
	const drill = useDrillFilter();

	useEffect(() => {
		let cancelled = false;
		if (!id) return;
		analyticsApi
			.getDashboard(id)
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
	}, [id]);

	const dashboardParams: DashboardParameter[] = useMemo(() => {
		if (state.state !== "loaded") return [];
		const raw = state.value.parameters;
		if (!Array.isArray(raw)) return [];
		return raw
			.map((p: any) => ({
				id: String(p?.id ?? ""),
				name: typeof p?.name === "string" ? p.name : undefined,
				slug: typeof p?.slug === "string" ? p.slug : undefined,
				type: typeof p?.type === "string" ? p.type : undefined,
			}))
			.filter((p) => p.id);
	}, [state]);

	const dashcards: DashboardCard[] = useMemo(() => {
		if (state.state !== "loaded") return [];
		const raw = Array.isArray(state.value.ordered_cards) ? (state.value.ordered_cards as DashboardCard[]) : [];
		// Fix overlapping cards: if all cards have row=0 (or same row), stack them vertically
		const allSameRow = raw.length > 1 && raw.every((dc) => (dc.row ?? 0) === (raw[0].row ?? 0));
		if (!allSameRow) return raw;
		let nextRow = 0;
		return raw.map((dc) => {
			const fixed = { ...dc, row: nextRow, col: dc.col ?? 0 };
			nextRow += (dc.size_y ?? 6);
			return fixed;
		});
	}, [state]);

	useEffect(() => {
		let cancelled = false;
		if (!id) return;
		if (dashboardParams.length === 0) return;

		(async () => {
			const next: Record<string, string[]> = {};
			for (const p of dashboardParams) {
				try {
					next[p.id] = await analyticsApi.listDashboardParamValues(id, p.id);
				} catch {
					next[p.id] = [];
				}
			}
			if (!cancelled) setParamOptions(next);
		})();

		return () => {
			cancelled = true;
		};
	}, [id, dashboardParams]);

	const queryParametersPayload = useMemo(() => {
		const out: any[] = [];
		for (const p of dashboardParams) {
			const value = (paramValues[p.id] ?? "").trim();
			if (!value) continue;
			const tagName = (p.slug || p.name || p.id).trim();
			if (!tagName) continue;
			out.push({
				type: p.type || "category",
				target: ["variable", ["template-tag", tagName]],
				value,
			});
		}
		return out;
	}, [dashboardParams, paramValues]);

	useEffect(() => {
		let cancelled = false;
		if (!id) return;
		if (dashcards.length === 0) return;

		(async () => {
			const next: Record<number, LoadState<DashboardQueryResponse>> = {};
			for (const dc of dashcards) {
				next[dc.id] = { state: "loading" };
			}
			if (!cancelled) setDashcardResults(next);

			for (const dc of dashcards) {
				const card: any = dc.card as any;
				const cardId = dc.card_id ?? (card && typeof card.id === "number" ? card.id : undefined);
				if (!cardId) {
					next[dc.id] = { state: "error", error: new Error("Missing card_id") };
					continue;
				}
				const params = crossFilter.buildCrossFilterParams(dc.id, queryParametersPayload);
				try {
					const value = await analyticsApi.queryDashcard(id, dc.id, cardId, { parameters: params });
					next[dc.id] = { state: "loaded", value };
				} catch (e) {
					next[dc.id] = { state: "error", error: e };
				}
				if (!cancelled) setDashcardResults({ ...next });
			}
		})();

		return () => {
			cancelled = true;
		};
	}, [id, dashcards, queryParametersPayload, crossFilter.activeFilter]);

	const handleParamChange = useCallback((paramId: string, value: string) => {
		setParamValues((prev) => ({ ...prev, [paramId]: value }));
	}, []);

	const handleSeriesClick = useCallback(
		(dashcardId: number, params: SeriesClickParams, _event?: React.MouseEvent) => {
			crossFilter.setFilter({
				sourceCardId: dashcardId,
				column: params.dimensionName,
				value: params.dimensionValue,
			});
		},
		[crossFilter],
	);

	// Stub layout change handler (read-only, does nothing)
	const noop = useCallback(() => {}, []);

	const ShareIcon = () => (
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<circle cx="18" cy="5" r="3" />
			<circle cx="6" cy="12" r="3" />
			<circle cx="18" cy="19" r="3" />
			<line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
			<line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
		</svg>
	);

	const EditIcon = () => (
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
			<path d="m15 5 4 4" />
		</svg>
	);

	const CopyIcon = () => (
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<rect width="14" height="14" x="8" y="8" rx="2" ry="2" />
			<path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2" />
		</svg>
	);

	const CheckIcon = () => (
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<polyline points="20 6 9 17 4 12" />
		</svg>
	);

	return (
		<PageContainer maxWidth="full">
			<div data-testid="analytics-dashboard-detail">
			{state.state === "loading" && (
				<div className="loading-container">
					<Spin size="large" />
				</div>
			)}
			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
			{state.state === "loaded" && (
				<>
					<PageHeader
						title={state.value.name ?? "-"}
						actions={
							<>
								<Button
									data-testid="analytics-dashboard-share"
									type="default"
									icon={<ShareIcon />}
									loading={shareBusy}
									onClick={async () => {
										if (!id) return;
										setShareBusy(true);
										setShareCopied(false);
										try {
											const r = await analyticsApi.createDashboardPublicLink(id);
											setShareUuid(r.uuid ?? "");
										} finally {
											setShareBusy(false);
										}
									}}
								>
									{t(locale, "share.create")}
								</Button>
								<Link to={`/bi/dashboards/${encodeURIComponent(String(state.value.id))}/edit`}>
									<Button type="primary" icon={<EditIcon />}>
										{t(locale, "dashboards.edit")}
									</Button>
								</Link>
							</>
						}
					/>

					{shareUuid && (
						<Card style={{ marginBottom: "var(--spacing-lg)" }}
							title={t(locale, "share.title")}
							extra={
									<Button
										type="default"
										size="small"
										icon={shareCopied ? <CheckIcon /> : <CopyIcon />}
										onClick={async () => {
											const link = resolveRouteHref(`/bi/public/dashboard/${encodeURIComponent(shareUuid)}`);
											const copied = await writeTextToClipboard(link);
											if (copied) {
												setShareCopied(true);
											} else {
												window.prompt("Copy link:", link);
												setShareCopied(true);
											}
										}}
									>
										{shareCopied ? t(locale, "share.copied") : t(locale, "share.copy")}
									</Button>
								}
						>
								<Input
									readOnly
									value={resolveRouteHref(`/bi/public/dashboard/${encodeURIComponent(shareUuid)}`)}
								/>
								<p className="text-secondary" style={{ marginTop: "var(--spacing-sm)", fontSize: "var(--font-size-sm)" }}>
									{t(locale, "share.note")}
								</p>
						</Card>
					)}

					{/* Cross-filter indicator */}
					{crossFilter.activeFilter && (
						<div className="flex items-center gap-2 mb-3 px-3 py-2 bg-blue-50 dark:bg-blue-900/20 rounded-md text-sm">
							<span>
								{t(locale, "filter.crossFilterActive")
									.replace("{column}", crossFilter.activeFilter.column)
									.replace("{value}", crossFilter.activeFilter.value)}
							</span>
							<Button type="link" size="small" onClick={crossFilter.clearFilter}>
								{t(locale, "filter.clearCrossFilter")}
							</Button>
						</div>
					)}

					{/* Filter bar using new component */}
					<DashboardFilterBar
						parameters={dashboardParams}
						paramValues={paramValues}
						paramOptions={paramOptions}
						onParamChange={handleParamChange}
						isEditing={false}
						locale={locale}
					/>

					{/* Dashboard grid using new component */}
					{dashcards.length > 0 && (
						<DashboardEditorGrid
							dashcards={dashcards}
							cardResults={dashcardResults}
							isEditing={false}
							locale={locale}
							onLayoutChange={noop}
							onRemoveCard={noop}
							onSeriesClick={handleSeriesClick}
							drillFilters={drill.filters}
							onDrillClear={drill.clearAll}
							onDrillRemoveFrom={drill.removeFiltersFrom}
						/>
					)}

					<Collapse
						items={[{
							key: "detail",
							label: <>{t(locale, "dashboards.detailNote")} <span style={{ color: "var(--color-text-secondary)", fontSize: "var(--font-size-sm)", fontWeight: "normal" }}>JSON</span></>,
							children: (
								<pre style={{ whiteSpace: "pre-wrap", fontSize: 12, margin: 0, overflow: "auto" }}>
									{JSON.stringify(state.value, null, 2)}
								</pre>
							),
						}]}
					/>
				</>
			)}
			</div>
		</PageContainer>
	);
}
