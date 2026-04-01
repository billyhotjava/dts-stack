import type { CSSProperties } from "react";
import { isRouteErrorResponse, useRouteError } from "react-router";
import { themeVars } from "@/theme/theme.css";
import { ScrollArea } from "@/ui/scroll-area";
import { useBilingualText } from "@/hooks/useBilingualText";
import { Title } from "@/ui/typography";

/**
 * Detect stale-chunk errors caused by deployment: the browser's cached HTML references
 * a JS chunk that no longer exists on the server (hash changed after rebuild).
 * Auto-reload once to fetch the new HTML + chunks.
 */
function isStaleChunkError(error: unknown): boolean {
	if (!(error instanceof Error)) return false;
	const msg = error.message || '';
	return (
		msg.includes('Failed to fetch dynamically imported module') ||
		msg.includes('Importing a module script failed') ||
		msg.includes('Loading chunk') ||
		msg.includes('Loading CSS chunk')
	);
}

const RELOAD_KEY = 'dts.error-boundary.reload-attempted';

export default function ErrorBoundary() {
	const error = useRouteError();
	const _t = useBilingualText();

	// Auto-reload on stale chunk errors (once per session to avoid infinite reload loop)
	if (isStaleChunkError(error)) {
		const alreadyReloaded = sessionStorage.getItem(RELOAD_KEY);
		if (!alreadyReloaded) {
			sessionStorage.setItem(RELOAD_KEY, '1');
			window.location.reload();
			return null;
		}
		// Second time → clear flag and show error normally
		sessionStorage.removeItem(RELOAD_KEY);
	} else {
		// Successful navigation → clear the reload flag so next deploy can auto-reload again
		sessionStorage.removeItem(RELOAD_KEY);
	}

	return (
		<ScrollArea className="w-full h-screen">
			<div style={rootStyles()}>
				<div style={containerStyles()}>
					{renderErrorMessage(error, (k, f) => {
						try {
							const v = _t(k).trim();
							return v.length > 0 ? v : f;
						} catch {
							return f;
						}
					})}
				</div>
			</div>
		</ScrollArea>
	);
}

function parseStackTrace(stack?: string) {
	if (!stack) return { filePath: null, functionName: null };

	const filePathMatch = stack.match(/\/src\/[^?]+/);
	const functionNameMatch = stack.match(/at (\S+)/);

	return {
		filePath: filePathMatch ? filePathMatch[0] : null,
		functionName: functionNameMatch ? functionNameMatch[1] : null,
	};
}

function renderErrorMessage(error: any, translate?: (k: string, f: string) => string) {
	if (isRouteErrorResponse(error)) {
		return (
			<>
				<Title as="h2">
					{error.status}: {error.statusText}
				</Title>
				<p style={messageStyles()}>{error.data}</p>
			</>
		);
	}

	if (error instanceof Error) {
		const { filePath, functionName } = parseStackTrace(error.stack);

		return (
			<>
				<Title as="h2">
					{translate
						? translate("sys.error.unexpected", "Unexpected Application Error!")
						: "Unexpected Application Error!"}
				</Title>
				<p style={messageStyles()}>
					{error.name}: {error.message}
				</p>
				<pre style={detailsStyles()}>{error.stack}</pre>
				{(filePath || functionName) && (
					<p style={filePathStyles()}>
						{filePath} ({functionName})
					</p>
				)}
			</>
		);
	}

	return <Title as="h2">{translate ? translate("sys.error.unknown", "Unknown Error") : "Unknown Error"}</Title>;
}

const rootStyles = (): CSSProperties => {
	return {
		display: "flex",
		height: "100vh",
		flex: "1 1 auto",
		alignItems: "center",
		padding: "10vh 15px",
		flexDirection: "column",
		color: "white",
		backgroundColor: "#2c2c2e",
	};
};

const containerStyles = (): CSSProperties => {
	return {
		gap: 24,
		padding: 20,
		width: "100%",
		maxWidth: 960,
		display: "flex",
		borderRadius: 8,
		flexDirection: "column",
		backgroundColor: "#1c1c1e",
	};
};

const messageStyles = (): CSSProperties => {
	return {
		margin: 0,
		lineHeight: 1.5,
		padding: "12px 16px",
		whiteSpace: "pre-wrap",
		color: themeVars.colors.palette.error.default,
		backgroundColor: "#2a1e1e",
		borderLeft: `2px solid ${themeVars.colors.palette.error.default}`,
		fontWeight: 700,
	};
};

const detailsStyles = (): CSSProperties => {
	return {
		margin: 0,
		padding: 16,
		lineHeight: 1.5,
		overflow: "auto",
		borderRadius: "inherit",
		color: themeVars.colors.palette.warning.default,
		whiteSpace: "pre-wrap",
		backgroundColor: "#111111",
	};
};

const filePathStyles = (): CSSProperties => {
	return {
		marginTop: 16,
		color: themeVars.colors.palette.info.default,
	};
};
