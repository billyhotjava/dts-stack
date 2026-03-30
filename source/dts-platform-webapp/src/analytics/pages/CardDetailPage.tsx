import { Link, useParams } from "react-router";
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type CardDetail, type CardQueryResponse, type ExplainabilityResponse } from "../api/analyticsApi";
import { ChartRenderer, type VisualizationType, type VisualizationSettings } from "../components/charts";
import { PageHeader } from "@/components/page-header";
import { ErrorNotice } from "../components/ErrorNotice";
import { Input, Spin, Button, Card, Collapse, Tag, Breadcrumb } from "antd";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import { writeTextToClipboard } from "../hooks/clipboard";
import { resolveRouteHref } from "../helpers/resolveAnalyticsUrl";
type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

export default function CardDetailPage() {
	const { id } = useParams();
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<CardDetail>>({ state: "loading" });
	const [queryState, setQueryState] = useState<LoadState<CardQueryResponse> | null>(null);
	const [explainState, setExplainState] = useState<LoadState<ExplainabilityResponse> | null>(null);
	const [showRaw, setShowRaw] = useState(false);
	const [shareUuid, setShareUuid] = useState<string>("");
	const [shareBusy, setShareBusy] = useState(false);
	const [shareCopied, setShareCopied] = useState(false);

	useEffect(() => {
		let cancelled = false;
		if (!id) return;
		analyticsApi
			.getCard(id)
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

	useEffect(() => {
		let cancelled = false;
		if (!id) return;
		setQueryState({ state: "loading" });
		analyticsApi
			.queryCard(id)
			.then((value) => {
				if (cancelled) return;
				setQueryState({ state: "loaded", value });
			})
			.catch((e) => {
				if (cancelled) return;
				setQueryState({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, [id]);

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

	const explain = async () => {
		if (!id) return;
		setExplainState({ state: "loading" });
		try {
			const value = await analyticsApi.explainCard(id, {});
			setExplainState({ state: "loaded", value });
		} catch (e) {
			setExplainState({ state: "error", error: e });
		}
	};

	return (
		<div className="space-y-4">
			{state.state === "loading" && (
				<div className="loading-container">
					<Spin size="large" />
				</div>
			)}
			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
			{state.state === "loaded" && (
				<>
					<PageHeader
						title={String(state.value.name ?? "-")}
						actions={
							<>
								<Button
									type="default"
									icon={<ShareIcon />}
									loading={shareBusy}
									onClick={async () => {
										if (!id) return;
										setShareBusy(true);
										setShareCopied(false);
										try {
											const r = await analyticsApi.createCardPublicLink(id);
											setShareUuid(r.uuid ?? "");
										} finally {
											setShareBusy(false);
										}
									}}
								>
									{t(locale, "share.create")}
								</Button>
								<Link to={`/bi/questions/${encodeURIComponent(String(state.value.id))}/edit`}>
									<Button type="primary" icon={<EditIcon />}>
										{t(locale, "questions.edit")}
									</Button>
								</Link>
								<Button type="text" onClick={explain} loading={explainState?.state === "loading"}>
									Explain
								</Button>
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
											const link = resolveRouteHref(`/bi/public/card/${encodeURIComponent(shareUuid)}`);
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
									value={resolveRouteHref(`/bi/public/card/${encodeURIComponent(shareUuid)}`)}
								/>
								<p className="text-secondary" style={{ marginTop: "var(--spacing-sm)", fontSize: "var(--font-size-sm)" }}>
									{t(locale, "share.note")}
								</p>
						</Card>
					)}

					<Collapse
						style={{ marginBottom: "var(--spacing-lg)" }}
						items={[{
							key: "detail",
							label: <>{t(locale, "questions.detailNote")} <span style={{ color: "var(--color-text-secondary)", fontSize: "var(--font-size-sm)", fontWeight: "normal" }}>JSON</span></>,
							children: (
								<pre style={{ whiteSpace: "pre-wrap", fontSize: 12, margin: 0, overflow: "auto" }}>
									{JSON.stringify(state.value, null, 2)}
								</pre>
							),
						}]}
					/>

					{explainState?.state === "error" && (
						<Card style={{ marginBottom: "var(--spacing-lg)" }}>
								<ErrorNotice locale={locale} error={explainState.error} />
						</Card>
					)}

					{explainState?.state === "loaded" && (
						<Card style={{ marginBottom: "var(--spacing-lg)" }}
							title="Explainability"
							extra={
									<Button
										type="text"
										size="small"
										onClick={() => {
											const text = explainState.value.copyJson ?? JSON.stringify(explainState.value.explainCard ?? {}, null, 2);
											void writeTextToClipboard(text);
										}}
									>
										Copy JSON
									</Button>
								}
						>
								<pre style={{ whiteSpace: "pre-wrap", margin: 0, padding: "var(--spacing-sm)", background: "var(--color-bg-tertiary)", borderRadius: "var(--radius-sm)", fontSize: 12 }}>
									{JSON.stringify(explainState.value.explainCard ?? {}, null, 2)}
								</pre>
						</Card>
					)}

					<Card
						title={t(locale, "questions.queryResult")}
						extra={
								<Button type="text" onClick={() => setShowRaw((v) => !v)}>
									{t(locale, "questions.queryRaw")}
								</Button>
							}
					>
							{queryState?.state === "loading" && (
								<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
									<Spin />
								</div>
							)}
							{queryState?.state === "error" && (
								<ErrorNotice locale={locale} error={queryState.error} />
							)}
							{queryState?.state === "loaded" && (
								<>
									{queryState.value?.data?.native_form?.query && (
										<div style={{ marginBottom: "var(--spacing-md)" }}>
											<Tag>{t(locale, "questions.querySql")}</Tag>
											<pre style={{ whiteSpace: "pre-wrap", fontSize: 12, margin: "var(--spacing-sm) 0 0", padding: "var(--spacing-sm)", background: "var(--color-bg-tertiary)", borderRadius: "var(--radius-sm)" }}>
												{String(queryState.value.data.native_form.query)}
											</pre>
										</div>
									)}

									{Array.isArray(queryState.value?.data?.cols) && Array.isArray(queryState.value?.data?.rows) ? (
										<ChartRenderer
											data={{
												cols: (queryState.value.data.cols ?? []) as { name: string; display_name?: string; base_type?: string }[],
												rows: (queryState.value.data.rows ?? []) as any[][]
											}}
											display={(state.value.display as VisualizationType) || 'table'}
											settings={(state.value.visualization_settings as VisualizationSettings) || {}}
										/>
									) : (
										<p className="text-secondary">{t(locale, "questions.noTabular")}</p>
									)}

									{showRaw && (
										<pre style={{ whiteSpace: "pre-wrap", fontSize: 12, marginTop: "var(--spacing-md)", padding: "var(--spacing-sm)", background: "var(--color-bg-tertiary)", borderRadius: "var(--radius-sm)" }}>
											{JSON.stringify(queryState.value, null, 2)}
										</pre>
									)}
								</>
							)}
					</Card>
				</>
			)}
		</div>
	);
}
