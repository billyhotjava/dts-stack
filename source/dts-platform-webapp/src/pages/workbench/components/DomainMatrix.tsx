import { useMemo } from "react";
import { Card, Tooltip } from "antd";
import { auditLog } from "@/utils/audit";

/**
 * Sprint-15 F4/T03 — Business-domain visits heat strip.
 *
 * Renders one row of color-shaded cells (project blue family) whose fill
 * tracks visit-count within the current role/time scope. The two synthetic
 * buckets `__OTHER__` / `__UNCATEGORIZED__` render in neutral gray and are
 * non-interactive (there is no valid `bizDomain` value to apply).
 */

export interface DomainCell {
	domain: string;
	domainName: string;
	visits: number;
}

export interface DomainMatrixProps {
	/** Parent controls via (role === INST_LEADER && bizDomainAvailable && cells.length > 0). */
	visible: boolean;
	/** Backend-sorted descending, with `__OTHER__` bucket last if present. */
	cells: DomainCell[];
	/** Currently active global `bizDomain` filter; `null` when "全部". */
	activeDomain: string | null;
	/** Clicking the active cell toggles back to `null`. */
	onSelect: (domain: string | null) => void;
}

const BASE_COLOR = { r: 79, g: 110, b: 247 };
const OTHER_COLOR = "#bfbfbf";
const OTHER_DOMAIN = "__OTHER__";
const UNCATEGORIZED_DOMAIN = "__UNCATEGORIZED__";

function shadeColor(weight: number): string {
	// weight ∈ [0, 1]: 0 = lightest tint, 1 = full base color.
	const mix = (baseTint: number, target: number) =>
		Math.round(baseTint + (target - baseTint) * weight);
	const r = mix(235, BASE_COLOR.r);
	const g = mix(240, BASE_COLOR.g);
	const b = mix(255, BASE_COLOR.b);
	return `rgb(${r}, ${g}, ${b})`;
}

function weightOf(visits: number, min: number, max: number): number {
	if (max <= min) return 0.5;
	return Math.max(0.1, Math.min(1, (visits - min) / (max - min)));
}

function isSyntheticBucket(domain: string): boolean {
	return domain === OTHER_DOMAIN || domain === UNCATEGORIZED_DOMAIN;
}

export function DomainMatrix({ visible, cells, activeDomain, onSelect }: DomainMatrixProps) {
	const stats = useMemo(() => {
		const realCells = cells.filter((c) => !isSyntheticBucket(c.domain));
		if (realCells.length === 0) return { min: 0, max: 0 };
		const visits = realCells.map((c) => c.visits);
		return { min: Math.min(...visits), max: Math.max(...visits) };
	}, [cells]);

	if (!visible || cells.length === 0) return null;

	const handleClick = (cell: DomainCell) => {
		if (isSyntheticBucket(cell.domain)) return;
		auditLog("WORKBENCH_DOMAIN_DRILL", { domain: cell.domain });
		onSelect(cell.domain === activeDomain ? null : cell.domain);
	};

	return (
		<Card size="small" styles={{ body: { padding: 12 } }}>
			<div
				style={{
					display: "grid",
					gridTemplateColumns: `repeat(${cells.length}, 1fr)`,
					gap: 8,
				}}
			>
				{cells.map((c) => {
					const synthetic = isSyntheticBucket(c.domain);
					const isActive = c.domain === activeDomain;
					const bg = synthetic
						? OTHER_COLOR
						: shadeColor(weightOf(c.visits, stats.min, stats.max));
					const cursor = synthetic ? "default" : "pointer";
					return (
						<Tooltip key={c.domain} title={`${c.domainName} · 访问 ${c.visits}`}>
							<div
								onClick={() => handleClick(c)}
								style={{
									background: bg,
									padding: 16,
									borderRadius: 8,
									color: "#fff",
									cursor,
									textAlign: "center",
									border: isActive ? "2px solid #faad14" : "2px solid transparent",
									transition: "all 0.2s",
									minHeight: 72,
								}}
								role={synthetic ? undefined : "button"}
								aria-pressed={synthetic ? undefined : isActive}
							>
								<div style={{ fontSize: 14, fontWeight: 500, marginBottom: 4 }}>{c.domainName}</div>
								<div style={{ fontSize: 18, fontWeight: 600 }}>{c.visits.toLocaleString()}</div>
							</div>
						</Tooltip>
					);
				})}
			</div>
		</Card>
	);
}

export default DomainMatrix;
