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
	const is403 = (error instanceof HttpError && error.status === 403) || message.includes("HTTP 403");
	const isAuth = error instanceof AuthError || message.includes("HTTP 401") || is403;

	if (!isAuth) {
		return (
			<div className="rounded-lg border border-border-default bg-surface-card p-4">
				<div className="text-text-muted">{t(locale, "error")}</div>
				<div className="mt-2 whitespace-pre-wrap">{message}</div>
			</div>
		);
	}

	if (is403) {
		return (
			<div className="rounded-lg border border-border-default bg-surface-card text-center py-6 px-4">
				<div className="text-[32px] opacity-30 mb-2">&#128274;</div>
				<div className="text-text-secondary">{t(locale, "auth.forbidden")}</div>
			</div>
		);
	}

	return (
		<div className="rounded-lg border border-border-default bg-surface-card p-4">
			<div className="text-text-muted">{t(locale, "error")}</div>
			<div className="mt-2">{t(locale, "auth.expired")}</div>
			<div className="mt-3 flex gap-2 flex-wrap">
				<a className="inline-flex items-center px-3 py-1.5 rounded-md bg-brand text-white text-sm" href="/analytics" rel="noreferrer">
					{t(locale, "auth.back")}
				</a>
				<button
					className="inline-flex items-center px-3 py-1.5 rounded-md bg-brand text-white text-sm cursor-pointer"
					type="button"
					onClick={() => {
						window.location.reload();
					}}
				>
					{t(locale, "auth.reload")}
				</button>
			</div>
			<div className="mt-2 text-xs opacity-70 whitespace-pre-wrap">{message}</div>
		</div>
	);
}
