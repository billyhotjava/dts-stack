import { useMemo } from "react";
import { Link } from "react-router";
import { PageContainer, PageHeader } from "../components/PageContainer/PageContainer";
import { Card, CardBody } from "../ui/Card/Card";
import { Button } from "../ui/Button/Button";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

// Icons
const ModelIcon = () => (
	<svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
		<path d="M12 2L2 7l10 5 10-5-10-5Z" />
		<path d="m2 17 10 5 10-5" />
		<path d="m2 12 10 5 10-5" />
	</svg>
);

const DatabaseIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<ellipse cx="12" cy="5" rx="9" ry="3" />
		<path d="M3 5v14a9 3 0 0 0 18 0V5" />
		<path d="M3 12a9 3 0 0 0 18 0" />
	</svg>
);

const PlusIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M5 12h14" />
		<path d="M12 5v14" />
	</svg>
);

export default function ModelsPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	return (
		<PageContainer>
			<PageHeader
				title={t(locale, "models.title")}
				subtitle={t(locale, "models.subtitle")}
			/>

			<Card>
				<CardBody>
					<div style={{ display: "flex", flexDirection: "column", alignItems: "center", padding: "var(--spacing-xl)", textAlign: "center" }}>
						<div style={{ color: "var(--color-text-tertiary)", marginBottom: "var(--spacing-lg)" }}>
							<ModelIcon />
						</div>
						<h3 style={{ margin: 0, color: "var(--color-text-secondary)", marginBottom: "var(--spacing-sm)" }}>
							{t(locale, "common.empty")}
						</h3>
						<p className="text-muted" style={{ marginBottom: "var(--spacing-lg)", maxWidth: 400 }}>
							{t(locale, "models.emptyDesc")}
						</p>
						<div style={{ display: "flex", gap: "var(--spacing-sm)" }}>
							<Link to="/data">
								<Button variant="secondary" icon={<DatabaseIcon />}>
									{t(locale, "common.open")} {t(locale, "data.title")}
								</Button>
							</Link>
							<Link to="/questions/new">
								<Button variant="primary" icon={<PlusIcon />}>
									{t(locale, "questions.new")}
								</Button>
							</Link>
						</div>
					</div>
				</CardBody>
			</Card>
		</PageContainer>
	);
}
