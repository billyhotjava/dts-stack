import { Skeleton, Space, Tag } from "antd";
import { Monitor } from "lucide-react";
import { useEffect, useState } from "react";
import { analyticsApi } from "@/analytics/api/analyticsApi";

/**
 * Sprint-15 F5/T03 — Compact published-screens chip strip.
 *
 * Replaces the old full table at the top of workbench. Hides itself entirely
 * when the user has zero visible published screens; shows a skeleton while
 * the API resolves to avoid layout flashes.
 */

interface PublishedScreen {
	id: number | string;
	name?: string;
	description?: string | null;
	publishedAt?: string | null;
}

const MAX_SHOWN = 6;

/**
 * P0-16: runtime shape check replacing the previous `as PublishedScreen[]`
 * cast. The cast accepted `{ screens: [...] }`, `{ data: [...] }`, or any
 * other arbitrary object as if it was already an array, silently producing
 * "no screens" for the user. Now we coerce the response into an array
 * (handling a couple of common envelope shapes), then validate each entry
 * has at least an `id`, dropping malformed rows.
 */
function coerceScreensList(raw: unknown): PublishedScreen[] {
	const candidates = Array.isArray(raw)
		? raw
		: raw && typeof raw === "object" && Array.isArray((raw as { screens?: unknown }).screens)
			? (raw as { screens: unknown[] }).screens
			: raw && typeof raw === "object" && Array.isArray((raw as { data?: unknown }).data)
				? (raw as { data: unknown[] }).data
				: [];
	const out: PublishedScreen[] = [];
	for (const item of candidates) {
		if (!item || typeof item !== "object") continue;
		const obj = item as Record<string, unknown>;
		const id = obj.id;
		if (typeof id !== "string" && typeof id !== "number") continue;
		out.push({
			id,
			name: typeof obj.name === "string" ? obj.name : undefined,
			description: typeof obj.description === "string" ? obj.description : null,
			publishedAt: typeof obj.publishedAt === "string" ? obj.publishedAt : null,
		});
	}
	return out;
}

export function ScreenStrip() {
	const [screens, setScreens] = useState<PublishedScreen[] | null>(null);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.listScreens()
			.then((list) => {
				if (cancelled) return;
				setScreens(coerceScreensList(list));
			})
			.catch(() => {
				if (cancelled) return;
				setScreens([]);
			})
			.finally(() => {
				if (!cancelled) setLoading(false);
			});
		return () => {
			cancelled = true;
		};
	}, []);

	if (loading) {
		return <Skeleton.Button active style={{ height: 48, width: "100%" }} />;
	}
	if (!screens || screens.length === 0) return null;

	const shown = screens.slice(0, MAX_SHOWN);
	const hasMore = screens.length > MAX_SHOWN;

	const handleChipClick = (id: PublishedScreen["id"]) => {
		// TODO: align with ScreensPage's resolveRouteForOpen once reachable without coupling.
		window.open(`/bi/screens/${id}/preview`, "_blank", "noopener,noreferrer");
	};

	return (
		<div
			style={{
				display: "flex",
				alignItems: "center",
				gap: 12,
				padding: "12px 16px",
				background: "#fafafa",
				borderRadius: 8,
			}}
		>
			<Monitor size={18} color="#4f6ef7" />
			<Space size={[8, 8]} wrap>
				{shown.map((s) => (
					<Tag
						key={s.id}
						color="processing"
						style={{ cursor: "pointer", padding: "4px 12px", fontSize: 13 }}
						onClick={() => handleChipClick(s.id)}
					>
						{s.name ?? `大屏 ${s.id}`}
					</Tag>
				))}
			</Space>
			{hasMore ? (
				<a href="/bi/screens" style={{ marginLeft: "auto" }}>
					更多 →
				</a>
			) : null}
		</div>
	);
}

export default ScreenStrip;
