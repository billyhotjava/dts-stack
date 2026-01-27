import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { analyticsApi, type FieldDetail, type FieldValuesResponse } from "../api/analyticsApi";
import { PageContainer, PageHeader, Breadcrumb } from "../components/PageContainer/PageContainer";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Card, CardHeader, CardBody } from "../ui/Card/Card";
import { Badge } from "../ui/Badge/Badge";
import { Spinner } from "../ui/Loading/Spinner";
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

	const breadcrumbItems: { label: string; href?: string }[] = [
		{ label: t(locale, "data.title"), href: "/data" },
	];
	if (dbId) {
		breadcrumbItems.push({ label: `${t(locale, "data.db")} #${dbId}`, href: `/data/${encodeURIComponent(String(dbId))}` });
	}
	if (dbId && tableId) {
		breadcrumbItems.push({ label: `${t(locale, "builder.table")} #${tableId}`, href: `/data/${encodeURIComponent(String(dbId))}/tables/${encodeURIComponent(String(tableId))}` });
	}
	breadcrumbItems.push({ label: `Field #${fieldId}` });

	return (
		<PageContainer>
			<PageHeader
				title={title}
				breadcrumbs={<Breadcrumb items={breadcrumbItems} />}
			/>

			{fieldState.state === "loading" && (
				<Card>
					<CardBody>
						<div className="loading-container" style={{ padding: "var(--spacing-xl)" }}>
							<Spinner size="lg" />
						</div>
					</CardBody>
				</Card>
			)}
			{fieldState.state === "error" && <ErrorNotice locale={locale} error={fieldState.error} />}
			{fieldState.state === "loaded" && (
				<Card style={{ marginBottom: "var(--spacing-lg)" }}>
					<CardHeader title="Field Details" />
					<CardBody>
						<div className="field-details">
							<div className="field-detail-row">
								<span className="field-detail-label">{t(locale, "common.name")}</span>
								<span className="field-detail-value">{fieldState.value.display_name || fieldState.value.name || "-"}</span>
							</div>
							<div className="field-detail-row">
								<span className="field-detail-label">{t(locale, "common.id")}</span>
								<span className="field-detail-value">{String(fieldState.value.id)}</span>
							</div>
							<div className="field-detail-row">
								<span className="field-detail-label">{t(locale, "field.baseType")}</span>
								<span className="field-detail-value">
									<Badge variant="default" size="sm">{String(fieldState.value.base_type ?? "-")}</Badge>
								</span>
							</div>
							<div className="field-detail-row">
								<span className="field-detail-label">{t(locale, "field.semanticType")}</span>
								<span className="field-detail-value">
									{fieldState.value.semantic_type ? (
										<Badge variant="info" size="sm">{String(fieldState.value.semantic_type)}</Badge>
									) : "-"}
								</span>
							</div>
							<div className="field-detail-row">
								<span className="field-detail-label">{t(locale, "field.visibility")}</span>
								<span className="field-detail-value">{String(fieldState.value.visibility_type ?? "-")}</span>
							</div>
						</div>
					</CardBody>
				</Card>
			)}

			<Card>
				<CardHeader
					title={t(locale, "field.values")}
					action={
						valuesState.state === "loaded" && (
							<Badge variant="default">
								{Array.isArray(valuesState.value.values) ? valuesState.value.values.length : 0}
								{valuesState.value.has_more_values ? "+" : ""}
							</Badge>
						)
					}
				/>
				<CardBody>
					{valuesState.state === "loading" && (
						<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
							<Spinner size="md" />
						</div>
					)}
					{valuesState.state === "error" && <ErrorNotice locale={locale} error={valuesState.error} />}
					{valuesState.state === "loaded" && Array.isArray(valuesState.value.values) && valuesState.value.values.length === 0 && (
						<EmptyState title={t(locale, "common.empty")} />
					)}
					{valuesState.state === "loaded" && Array.isArray(valuesState.value.values) && valuesState.value.values.length > 0 && (
						<div className="field-values-list">
							{valuesState.value.values.map((v, idx) => (
								<div key={String(idx)} className="field-value-item">
									{v === null || v === undefined ? <span className="text-muted">(null)</span> : String(v)}
								</div>
							))}
						</div>
					)}
				</CardBody>
			</Card>

			<style>{`
				.field-details {
					display: flex;
					flex-direction: column;
					gap: var(--spacing-sm);
				}

				.field-detail-row {
					display: flex;
					align-items: center;
					gap: var(--spacing-md);
					padding: var(--spacing-sm) 0;
					border-bottom: 1px solid var(--color-border);
				}

				.field-detail-row:last-child {
					border-bottom: none;
				}

				.field-detail-label {
					width: 160px;
					flex-shrink: 0;
					font-weight: var(--font-weight-medium);
					color: var(--color-text-secondary);
				}

				.field-detail-value {
					flex: 1;
					color: var(--color-text-primary);
				}

				.field-values-list {
					display: flex;
					flex-wrap: wrap;
					gap: var(--spacing-sm);
				}

				.field-value-item {
					padding: var(--spacing-xs) var(--spacing-sm);
					background: var(--color-bg-tertiary);
					border-radius: var(--radius-sm);
					font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
					font-size: var(--font-size-sm);
				}
			`}</style>
		</PageContainer>
	);
}
