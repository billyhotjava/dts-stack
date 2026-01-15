import type { ReactNode } from "react";
import React from "react";
import { getEffectiveLocale, t } from "../i18n";

type Props = {
	children: ReactNode;
};

type State = {
	error: unknown;
};

export class ErrorBoundary extends React.Component<Props, State> {
	state: State = { error: null };

	static getDerivedStateFromError(error: unknown): State {
		return { error };
	}

	componentDidCatch(error: unknown) {
		if (import.meta.env.DEV) {
			// eslint-disable-next-line no-console
			console.error("Uncaught UI error", error);
		}
	}

	render() {
		if (!this.state.error) return this.props.children;

		const locale = getEffectiveLocale();
		const message =
			this.state.error instanceof Error ? this.state.error.message : String(this.state.error ?? "Unknown error");

		return (
			<div style={{ maxWidth: 1080, margin: "0 auto", padding: "40px 16px" }}>
				<div
					style={{
						background: "#fff",
						border: "1px solid rgba(0,0,0,0.08)",
						borderRadius: 12,
						padding: 16,
					}}
				>
					<div style={{ fontWeight: 700 }}>{t(locale, "error")}</div>
					<div className="muted" style={{ marginTop: 10, whiteSpace: "pre-wrap" }}>
						{message}
					</div>
					<div style={{ height: 12 }} />
					<div className="row" style={{ gap: 8 }}>
						<button className="btn" type="button" onClick={() => window.location.reload()}>
							{t(locale, "auth.reload")}
						</button>
					</div>
				</div>
			</div>
		);
	}
}
