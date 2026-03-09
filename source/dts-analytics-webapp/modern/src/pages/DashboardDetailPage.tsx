import { Link, useParams } from "react-router";
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type DashboardCard, type DashboardDetail, type DashboardQueryResponse } from "../api/analyticsApi";
import { PageContainer, PageHeader } from "../components/PageContainer/PageContainer";
import { ErrorNotice } from "../components/ErrorNotice";
import { ChartRenderer, type VisualizationType, type VisualizationSettings } from "../components/charts";
import { Card, CardHeader, CardBody, CollapsibleCard } from "../ui/Card/Card";
import { Button } from "../ui/Button/Button";
import { Input } from "../ui/Input/Input";
import { NativeSelect } from "../ui/Input/Select";
import { Badge } from "../ui/Badge/Badge";
import { Spinner } from "../ui/Loading/Spinner";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import { writeTextToClipboard } from "../hooks/clipboard";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

type DashboardParam = {
	id: string;
	name?: string;
	slug?: string;
	type?: string;
};

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

	const dashboardParams: DashboardParam[] = useMemo(() => {
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
		return Array.isArray(state.value.ordered_cards) ? (state.value.ordered_cards as DashboardCard[]) : [];
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
				try {
					const value = await analyticsApi.queryDashcard(id, dc.id, cardId, { parameters: queryParametersPayload });
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
	}, [id, dashcards, queryParametersPayload]);

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

	const FilterIcon = () => (
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3" />
		</svg>
	);

	return (
		<PageContainer maxWidth="full">
			<div data-testid="analytics-dashboard-detail">
			{state.state === "loading" && (
				<div className="loading-container">
					<Spinner size="lg" />
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
									variant="secondary"
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
								<Link to={`/dashboards/${encodeURIComponent(String(state.value.id))}/edit`}>
									<Button variant="primary" icon={<EditIcon />}>
										{t(locale, "dashboards.edit")}
									</Button>
								</Link>
							</>
						}
					/>

					{shareUuid && (
						<Card style={{ marginBottom: "var(--spacing-lg)" }}>
							<CardHeader
								title={t(locale, "share.title")}
								action={
									<Button
										variant="secondary"
										size="sm"
										icon={shareCopied ? <CheckIcon /> : <CopyIcon />}
										onClick={async () => {
											const link = `${window.location.origin}/analytics/public/dashboard/${encodeURIComponent(shareUuid)}`;
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
							/>
							<CardBody>
								<Input
									readOnly
									value={`${window.location.origin}/analytics/public/dashboard/${encodeURIComponent(shareUuid)}`}
								/>
								<p className="text-muted" style={{ marginTop: "var(--spacing-sm)", fontSize: "var(--font-size-sm)" }}>
									{t(locale, "share.note")}
								</p>
							</CardBody>
						</Card>
					)}

					{dashboardParams.length > 0 && (
						<Card style={{ marginBottom: "var(--spacing-lg)" }}>
							<CardHeader
								title={t(locale, "filter.title")}
								icon={<FilterIcon />}
								action={
									<Button variant="tertiary" size="sm" onClick={() => setParamValues({})}>
										{t(locale, "filter.clear")}
									</Button>
								}
							/>
							<CardBody>
								<div style={{ display: "flex", flexWrap: "wrap", gap: "var(--spacing-md)" }}>
									{dashboardParams.map((p) => (
										<div key={p.id} style={{ minWidth: 200, flex: "1 1 200px", maxWidth: 300 }}>
											<NativeSelect
												label={p.name || p.slug || p.id}
												value={paramValues[p.id] ?? ""}
												onChange={(e) => setParamValues((prev) => ({ ...prev, [p.id]: e.target.value }))}
												options={[
													{ value: "", label: t(locale, "filter.all") },
													...(paramOptions[p.id] ?? []).map((v) => ({ value: String(v), label: String(v) }))
												]}
											/>
										</div>
									))}
								</div>
							</CardBody>
						</Card>
					)}

					<div
						className="dashboardGrid"
						style={{
							display: "grid",
							gridTemplateColumns: "repeat(24, minmax(0, 1fr))",
							gap: "var(--spacing-md)",
							alignItems: "stretch",
							marginBottom: "var(--spacing-lg)",
						}}
					>
						{dashcards.map((dc) => {
							const card: any = dc.card as any;
							const cardId = dc.card_id ?? (card && typeof card.id === "number" ? card.id : undefined);
							const name = (card && typeof card.name === "string" && card.name) || `Card ${cardId ?? "-"}`;
							const gridColumn =
								typeof dc.col === "number" && typeof dc.size_x === "number" ? `${dc.col + 1} / span ${dc.size_x}` : "auto";
							const gridRow =
								typeof dc.row === "number" && typeof dc.size_y === "number" ? `${dc.row + 1} / span ${dc.size_y}` : "auto";
							const result = dashcardResults[dc.id];

							return (
								<Card key={dc.id} style={{ gridColumn, gridRow, overflow: "hidden" }}>
									<CardHeader
										title={cardId ? <Link to={`/questions/${cardId}`}>{String(name)}</Link> : String(name)}
										action={<Badge variant="default" size="sm">card</Badge>}
									/>
									<CardBody>
										{!result || result.state === "loading" ? (
											<div className="loading-container" style={{ padding: "var(--spacing-md)" }}>
												<Spinner size="sm" />
											</div>
										) : result.state === "error" ? (
											<ErrorNotice locale={locale} error={result.error} />
										) : (
											<ChartRenderer
												data={{
													cols: (result.value.data?.cols ?? []) as { name: string; display_name?: string; base_type?: string }[],
													rows: (result.value.data?.rows ?? []) as any[][]
												}}
												display={(card?.display as VisualizationType) || "table"}
												settings={(card?.visualization_settings as VisualizationSettings) || {}}
											/>
										)}
									</CardBody>
								</Card>
							);
						})}
					</div>

					<CollapsibleCard
						title={t(locale, "dashboards.detailNote")}
						subtitle="JSON"
						defaultOpen={false}
					>
						<pre style={{ whiteSpace: "pre-wrap", fontSize: 12, margin: 0, overflow: "auto" }}>
							{JSON.stringify(state.value, null, 2)}
						</pre>
					</CollapsibleCard>
				</>
			)}
			</div>
		</PageContainer>
	);
}
