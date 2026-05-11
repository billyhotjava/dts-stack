import { useEffect, useRef } from "react";
import reportsService from "@/api/services/reportsService";
import type { ScreenConfig } from "../types";

/**
 * Sprint-17 / F2 — fires {@code reportsService.visit({ code: "screen-{id}" })}
 * when the user opens a screen preview and stays for {@link STAY_MS} or longer.
 *
 * The platform-side reconcile (Sprint-17 F1) keeps a {@code BiReportLink}
 * mirror per screen, so a {@code screen-{id}} visit lands in
 * {@code bi_report_visit} and bubbles into leader-overview's "我常用的大屏".
 *
 * Behaviour:
 *  - timer starts when {@code enabled} flips to true (data loaded)
 *  - timer is cleared on unmount or when {@code screenId} changes
 *  - 30s in-tab dedupe via sessionStorage so flicker / nav doesn't double-count
 *  - visit failures are swallowed: this is auxiliary telemetry, not a blocker
 */
const STAY_MS = 3_000;
const DEDUPE_MS = 30_000;
const STORAGE_KEY = "dts.bi.screenVisit.lastFired.v1";

interface UseScreenVisitTrackerArgs {
	screenId: string | number | null | undefined;
	title?: string;
	classification?: ScreenConfig["classification"] | string | null;
	enabled: boolean;
}

export function useScreenVisitTracker({ screenId, title, classification, enabled }: UseScreenVisitTrackerArgs): void {
	const timerRef = useRef<number | null>(null);

	useEffect(() => {
		if (!enabled || screenId == null || screenId === "") return;

		const code = `screen-${screenId}`;
		if (recentlyFired(code)) return;

		timerRef.current = window.setTimeout(() => {
			void fire(code, title, screenId, classification);
		}, STAY_MS);

		return () => {
			if (timerRef.current != null) {
				window.clearTimeout(timerRef.current);
				timerRef.current = null;
			}
		};
	}, [enabled, screenId, title, classification]);
}

function recentlyFired(code: string): boolean {
	try {
		const raw = sessionStorage.getItem(STORAGE_KEY);
		if (!raw) return false;
		const map = JSON.parse(raw) as Record<string, number>;
		const last = map[code];
		return typeof last === "number" && Date.now() - last < DEDUPE_MS;
	} catch {
		return false;
	}
}

function markFired(code: string): void {
	try {
		const raw = sessionStorage.getItem(STORAGE_KEY);
		const map = raw ? (JSON.parse(raw) as Record<string, number>) : {};
		map[code] = Date.now();
		sessionStorage.setItem(STORAGE_KEY, JSON.stringify(map));
	} catch {
		/* swallow storage errors (private mode, quota, etc.) */
	}
}

async function fire(
	code: string,
	title: string | undefined,
	id: string | number,
	classification: UseScreenVisitTrackerArgs["classification"],
): Promise<void> {
	try {
		await reportsService.visit({
			code,
			title: title ?? "",
			engine: "DTS_BI",
			classification: normalizeClassification(classification) ?? "INTERNAL",
			url: `/bi/screens/${id}/preview`,
		});
		markFired(code);
	} catch {
		/* visit is auxiliary — never block preview rendering */
	}
}

function normalizeClassification(value: UseScreenVisitTrackerArgs["classification"]): ScreenConfig["classification"] | null {
	if (typeof value !== "string") return null;
	const upper = value.trim().toUpperCase();
	if (upper === "PUBLIC" || upper === "INTERNAL" || upper === "SECRET" || upper === "CONFIDENTIAL") {
		return upper;
	}
	return null;
}
