/**
 * Sonner toast wrapper with automatic deduplication.
 *
 * Patches `toast.error` so that identical error messages within a short
 * window (2 s) are collapsed into a single notification.  Wired via the
 * "sonner-dedup" Vite plugin which redirects `import "sonner"` here for
 * every file EXCEPT this one — so our own `import "sonner"` below
 * resolves to the real package without circular issues.
 */
export { Toaster } from "sonner";
export type { ExternalToast, ToasterProps, ToastT } from "sonner";
import { toast as originalToast } from "sonner";

const DEDUP_WINDOW_MS = 2000;
const recentErrors = new Map<string, number>();

function dedupError(
	message: Parameters<typeof originalToast.error>[0],
	data?: Parameters<typeof originalToast.error>[1],
) {
	const key = typeof message === "string" ? message : String(message);
	const now = Date.now();
	const prev = recentErrors.get(key);
	if (prev && now - prev < DEDUP_WINDOW_MS) {
		return; // suppress duplicate
	}
	recentErrors.set(key, now);
	// cleanup stale entries
	if (recentErrors.size > 20) {
		for (const [k, t] of recentErrors) {
			if (now - t > DEDUP_WINDOW_MS) recentErrors.delete(k);
		}
	}
	return originalToast.error(message, data);
}

/**
 * Drop-in replacement for sonner's `toast` with deduped `.error()`.
 * All other methods (success, info, warning, etc.) pass through unchanged.
 */
export const toast = Object.assign(
	(...args: Parameters<typeof originalToast>) => originalToast(...args),
	{ ...originalToast, error: dedupError },
);
