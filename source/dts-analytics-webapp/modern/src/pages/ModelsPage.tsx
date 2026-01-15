import { useMemo } from "react";
import { Link } from "react-router";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

export default function ModelsPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	return (
		<div className="page">
			<h1 className="pageTitle">{t(locale, "models.title")}</h1>
			<div className="pageSub">{t(locale, "models.subtitle")}</div>

			<div style={{ height: 16 }} />

			<div className="card">
				<div className="muted">{t(locale, "common.empty")}</div>
				<div style={{ height: 12 }} />
				<div className="row">
					<Link className="btn" to="/data">
						{t(locale, "common.open")} {t(locale, "data.title")}
					</Link>
					<Link className="btn" to="/questions/new">
						{t(locale, "questions.new")}
					</Link>
				</div>
			</div>
		</div>
	);
}
