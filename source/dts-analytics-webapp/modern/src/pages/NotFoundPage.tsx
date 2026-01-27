import { useMemo } from "react";
import { Link } from "react-router";
import { PageContainer } from "../components/PageContainer/PageContainer";
import { Card, CardBody } from "../ui/Card/Card";
import { Button } from "../ui/Button/Button";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

// Icons
const AlertIcon = () => (
	<svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
		<circle cx="12" cy="12" r="10" />
		<line x1="12" y1="8" x2="12" y2="12" />
		<line x1="12" y1="16" x2="12.01" y2="16" />
	</svg>
);

const HomeIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
		<polyline points="9 22 9 12 15 12 15 22" />
	</svg>
);

export default function NotFoundPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	return (
		<PageContainer>
			<Card>
				<CardBody>
					<div style={{ display: "flex", flexDirection: "column", alignItems: "center", padding: "var(--spacing-2xl)", textAlign: "center" }}>
						<div style={{ color: "var(--color-text-tertiary)", marginBottom: "var(--spacing-lg)" }}>
							<AlertIcon />
						</div>
						<h1 style={{ margin: 0, fontSize: "var(--font-size-2xl)", fontWeight: "var(--font-weight-bold)", color: "var(--color-text-primary)" }}>
							{t(locale, "notfound.title")}
						</h1>
						<p className="text-muted" style={{ marginTop: "var(--spacing-md)", marginBottom: "var(--spacing-xl)", maxWidth: 400 }}>
							{t(locale, "notfound.desc")}
						</p>
						<Link to="/">
							<Button variant="primary" icon={<HomeIcon />}>
								{t(locale, "nav.home")}
							</Button>
						</Link>
					</div>
				</CardBody>
			</Card>
		</PageContainer>
	);
}
