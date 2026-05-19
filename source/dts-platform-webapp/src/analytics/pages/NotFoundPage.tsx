import { useMemo } from "react";
import { Link } from "react-router";
import { Button, Card } from "antd";
import { getEffectiveLocale, t, type Locale } from "../i18n";
// Icons
const AlertIcon = () => (
	<svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
		<circle cx="12" cy="12" r="10" />
		<line x1="12" y1="8" x2="12" y2="12" />
		<line x1="12" y1="16" x2="12.01" y2="16" />
	</svg>
);

export default function NotFoundPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	return (
		<div className="space-y-4">
			<Card>
				<div style={{ display: "flex", flexDirection: "column", alignItems: "center", padding: "var(--spacing-2xl)", textAlign: "center" }}>
					<div style={{ color: "var(--color-text-tertiary)", marginBottom: "var(--spacing-lg)" }}>
						<AlertIcon />
					</div>
					<h1 style={{ margin: 0, fontSize: "var(--font-size-2xl)", fontWeight: "var(--font-weight-bold)", color: "var(--color-text-primary)" }}>
						{t(locale, "notfound.title")}
					</h1>
					<p className="text-secondary" style={{ marginTop: "var(--spacing-md)", marginBottom: "var(--spacing-xl)", maxWidth: 400 }}>
						{t(locale, "notfound.desc")}
					</p>
					<Link to="/bi">
						<Button type="primary">
							{t(locale, "nav.home")}
						</Button>
					</Link>
				</div>
			</Card>
		</div>
	);
}
