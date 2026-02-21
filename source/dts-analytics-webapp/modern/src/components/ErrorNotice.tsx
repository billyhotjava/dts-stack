import { AuthError, HttpError } from "../api/analyticsApi";
import { resolveAnalyticsErrorCodeMessage } from "../api/errorCodeMessages";
import type { Locale } from "../i18n";
import { t } from "../i18n";

type Props = {
	locale: Locale;
	error: unknown;
};

function messageFromError(error: unknown): string {
	if (!error) return "Unknown error";
	if (error instanceof HttpError) {
		const hint = resolveAnalyticsErrorCodeMessage(error.code);
		if (hint) {
			const codeTag = error.code ? ` (${error.code})` : "";
			const requestTag = error.requestId ? ` [requestId=${error.requestId}]` : "";
			return `${hint}${codeTag}${requestTag}`;
		}
	}
	if (error instanceof Error) return error.message || String(error);
	return String(error);
}

export function ErrorNotice({ locale, error }: Props) {
	const message = messageFromError(error);
	const isAuth = error instanceof AuthError || message.includes("HTTP 401") || message.includes("HTTP 403");

	if (!isAuth) {
		return (
			<div className="card">
				<div className="muted">{t(locale, "error")}</div>
				<div style={{ marginTop: 8, whiteSpace: "pre-wrap" }}>{message}</div>
			</div>
		);
	}

	return (
		<div className="card">
			<div className="muted">{t(locale, "error")}</div>
			<div style={{ marginTop: 8 }}>{t(locale, "auth.expired")}</div>
			<div style={{ marginTop: 12, display: "flex", gap: 8, flexWrap: "wrap" }}>
				<a className="btn" href="/" rel="noreferrer">
					{t(locale, "auth.back")}
				</a>
				<button
					className="btn"
					type="button"
					onClick={() => {
						window.location.reload();
					}}
				>
					{t(locale, "auth.reload")}
				</button>
			</div>
			<div style={{ marginTop: 8, fontSize: 12, opacity: 0.7, whiteSpace: "pre-wrap" }}>{message}</div>
		</div>
	);
}
