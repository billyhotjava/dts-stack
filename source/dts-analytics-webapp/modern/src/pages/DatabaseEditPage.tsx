import { useMemo } from "react";
import { Link, useParams } from "react-router";
import { PageContainer, PageHeader, Breadcrumb } from "../components/PageContainer/PageContainer";
import { Card, CardBody, CardFooter } from "../ui/Card/Card";
import { Button } from "../ui/Button/Button";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

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
				subtitle={`${t(locale, "data.db")} #${dbId}`}
				breadcrumbs={
					<Breadcrumb items={[
						{ label: t(locale, "data.title"), href: "/data" },
						{ label: `${t(locale, "data.db")} #${dbId}`, href: `/data/${dbId}` },
						{ label: t(locale, "data.edit") }
					]} />
				}
			/>

			<Card>
				<CardBody>
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
							<p className="text-muted" style={{ marginTop: "var(--spacing-xs)", fontSize: "var(--font-size-sm)" }}>
								{t(locale, "data.platformReadOnlyDesc")}
							</p>
						</div>
					</div>
				</CardBody>
				<CardFooter align="between">
					<Link to="/data">
						<Button variant="tertiary">{t(locale, "common.open")} {t(locale, "data.title")}</Button>
					</Link>
				</CardFooter>
			</Card>
		</PageContainer>
	);
}
