import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type CurrentUser } from "../api/analyticsApi";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

export default function HomePage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [user, setUser] = useState<LoadState<CurrentUser>>({ state: "loading" });
	const [health, setHealth] = useState<LoadState<string>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.getCurrentUser()
			.then((value) => {
				if (cancelled) return;
				setUser({ state: "loaded", value });
			})
			.catch((e) => {
				if (cancelled) return;
				setUser({ state: "error", error: e });
			});

		analyticsApi
			.getHealth()
			.then((v) => {
				if (cancelled) return;
				setHealth({ state: "loaded", value: v?.status ?? "unknown" });
			})
			.catch((e) => {
				if (cancelled) return;
				setHealth({ state: "error", error: e });
			});

		return () => {
			cancelled = true;
		};
	}, []);

	const userLabel = (() => {
		if (user.state === "loading") return t(locale, "loading");
		if (user.state === "error") return t(locale, "error");
		const value = user.value;
		return value.common_name || [value.first_name, value.last_name].filter(Boolean).join(" ") || value.email || "-";
	})();

	const healthLabel = (() => {
		if (health.state === "loading") return t(locale, "loading");
		if (health.state === "error") return t(locale, "error");
		return health.value;
	})();

	return (
		<div className="page">
			<h1 className="pageTitle">{t(locale, "title")}</h1>
			<div className="pageSub">{t(locale, "subtitle")}</div>

			<div style={{ height: 16 }} />

			{user.state === "error" && <ErrorNotice locale={locale} error={user.error} />}
			{health.state === "error" && <ErrorNotice locale={locale} error={health.error} />}

			<div className="card">
				<div className="row">
					<div>
						<strong>{t(locale, "user")}:</strong> {userLabel}
					</div>
					<div className="muted">|</div>
					<div>
						<strong>{t(locale, "health")}:</strong> {healthLabel}
					</div>
				</div>
			</div>

			<div style={{ height: 16 }} />

			<div className="card">
				<div className="muted">{t(locale, "home.note")}</div>
			</div>
		</div>
	);
}
