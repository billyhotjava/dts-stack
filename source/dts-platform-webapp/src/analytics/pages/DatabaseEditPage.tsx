import { useMemo } from "react";
import { Link, useParams } from "react-router";
import { PageContainer, PageHeader, Breadcrumb } from "../components/PageContainer/PageContainer";
import { Button, Card } from "antd";
import { getEffectiveLocale, t, type Locale } from "../i18n";
const LockIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<rect width="18" height="11" x="3" y="11" rx="2" ry="2" />
		<path d="M7 11V7a5 5 0 0 1 10 0v4" />
	</svg>
);

export default function DatabaseEditPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const { dbId } = useParams();

	return (
		<PageContainer>
			<PageHeader
				title={t(locale, "data.edit")}
				breadcrumbs={
					<Breadcrumb items={[
						{ label: t(locale, "data.title"), href: "/bi/data" },
						{ label: `${t(locale, "data.db")} #${dbId}`, href: `/bi/data/${dbId}` },
						{ label: t(locale, "data.edit") }
					]} />
				}
			/>

			<Card>
					<div style={{ display: "flex", gap: "var(--spacing-md)", alignItems: "center" }}>
						<div style={{
							display: "flex",
							alignItems: "center",
							justifyContent: "center",
							width: 40,
							height: 40,
							borderRadius: "var(--radius-md)",
							background: "var(--color-bg-hover)",
							color: "var(--color-warning)",
						}}>
							<LockIcon />
						</div>
						<div>
							<h3 style={{ margin: 0, fontSize: "var(--font-size-md)", fontWeight: "var(--font-weight-semibold)" }}>
								{t(locale, "data.platformReadOnly")}
							</h3>
							<p className="text-secondary" style={{ marginTop: "var(--spacing-xs)", fontSize: "var(--font-size-sm)" }}>
								{t(locale, "data.platformReadOnlyDesc")}
							</p>
						</div>
					</div>
				<div style={{ display: "flex", justifyContent: "space-between", paddingTop: "var(--spacing-md)", marginTop: "var(--spacing-md)", borderTop: "1px solid var(--color-border)" }}>
					<Link to="/bi/data">
						<Button type="text">{t(locale, "common.open")} {t(locale, "data.title")}</Button>
					</Link>
				</div>
			</Card>
		</PageContainer>
	);
}
