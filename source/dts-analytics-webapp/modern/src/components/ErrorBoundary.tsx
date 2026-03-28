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
			<div className="max-w-[1080px] mx-auto px-4 py-10">
				<div className="bg-surface-card border border-border-default rounded-xl p-4">
					<div className="font-bold">{t(locale, "error")}</div>
					<div className="text-text-secondary mt-2.5 whitespace-pre-wrap">
						{message}
					</div>
					<div className="h-3" />
					<div className="flex gap-2">
						<button className="btn" type="button" onClick={() => window.location.reload()}>
							{t(locale, "auth.reload")}
						</button>
					</div>
				</div>
			</div>
		);
	}
}
