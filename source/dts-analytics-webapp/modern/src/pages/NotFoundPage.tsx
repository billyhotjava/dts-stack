import { useMemo } from "react";
import { Link } from "react-router";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

export default function NotFoundPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	return (
		<div className="page">
			<h1 className="pageTitle">{t(locale, "notfound.title")}</h1>
			<div className="pageSub">{t(locale, "notfound.desc")}</div>
			<div style={{ height: 16 }} />
			<div className="card">
				<Link className="btn" to="/">
					{t(locale, "nav.home")}
				</Link>
			</div>
		</div>
	);
}

