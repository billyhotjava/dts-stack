import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { analyticsApi, type TableDetail } from "../api/analyticsApi";
import { PageContainer, PageHeader, Breadcrumb } from "../components/PageContainer/PageContainer";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Card, CardHeader, CardBody } from "../ui/Card/Card";
import { Button } from "../ui/Button/Button";
import { Badge } from "../ui/Badge/Badge";
import { Spinner } from "../ui/Loading/Spinner";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

// Icons
const PlusIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M5 12h14" />
		<path d="M12 5v14" />
	</svg>
);

const FieldIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M4 22h14a2 2 0 0 0 2-2V7.5L14.5 2H6a2 2 0 0 0-2 2v4" />
		<polyline points="14 2 14 8 20 8" />
		<path d="M3 15h6" />
		<path d="M6 12v6" />
	</svg>
);

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
		<PageContainer>
			<PageHeader
				title={state.state === "loaded" ? (state.value.display_name || state.value.name || `Table #${tableId}`) : `${t(locale, "builder.table")} #${tableId}`}
				subtitle={state.state === "loaded" && state.value.schema ? `Schema: ${state.value.schema}` : undefined}
				breadcrumbs={
					<Breadcrumb items={[
						{ label: t(locale, "data.title"), href: "/data" },
						{ label: `${t(locale, "data.db")} #${dbId}`, href: dbId ? `/data/${encodeURIComponent(String(dbId))}` : undefined },
						{ label: `Table #${tableId}` }
					]} />
				}
				actions={
					<Link to={newQuestionHref}>
						<Button variant="primary" icon={<PlusIcon />}>
							{t(locale, "questions.new")}
						</Button>
					</Link>
				}
			/>

			{state.state === "loading" && (
				<Card>
					<CardBody>
						<div className="loading-container" style={{ padding: "var(--spacing-xl)" }}>
							<Spinner size="lg" />
						</div>
					</CardBody>
				</Card>
			)}
			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
			{state.state === "loaded" && (
				<>
					{state.value.description && (
						<Card style={{ marginBottom: "var(--spacing-lg)" }}>
							<CardBody>
								<p className="text-muted" style={{ margin: 0 }}>{state.value.description}</p>
							</CardBody>
						</Card>
					)}

					{Array.isArray(state.value.fields) && state.value.fields.length > 0 ? (
						<Card>
							<CardHeader
								title={t(locale, "builder.fields")}
								icon={<FieldIcon />}
								action={<Badge variant="default">{state.value.fields.length}</Badge>}
							/>
							<CardBody>
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
												<td>
													<Badge variant="default" size="sm">{String(f.base_type ?? "-")}</Badge>
												</td>
												<td>
													{f.semantic_type ? (
														<Badge variant="info" size="sm">{String(f.semantic_type)}</Badge>
													) : (
														<span className="text-muted">-</span>
													)}
												</td>
											</tr>
										))}
									</tbody>
								</table>
							</CardBody>
						</Card>
					) : (
						<EmptyState title={t(locale, "common.empty")} description="提示：字段列表为空，可能需要先同步数据库元数据。" />
					)}
				</>
			)}
		</PageContainer>
	);
}
