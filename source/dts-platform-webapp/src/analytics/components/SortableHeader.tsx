import type { CSSProperties, KeyboardEvent, ReactNode } from "react";
import type { SortDirection, SortState } from "../hooks/useTableSort";

/**
 * Header cell for analytics native-table pages, paired with `useTableSort`.
 *
 * Visually a drop-in replacement for the existing
 * `<th className="text-left font-medium px-4 py-3">` cells; adds a clickable
 * inner button with caret affordance, aria-sort, and Enter/Space activation.
 *
 * Right-aligned headers (e.g. action columns) typically aren't sortable, so
 * keep using a plain `<th>` for those.
 */

export interface SortableHeaderProps {
	/** Sort key passed to `requestSort`; must match a key in the column map. */
	sortKey: string;
	/** Current sort state from `useTableSort`. */
	sortState: SortState;
	/** Cycle handler from `useTableSort`. */
	onSort: (key: string) => void;
	children: ReactNode;
	/** Extra className applied to the `<th>` (e.g. `whitespace-nowrap`). */
	className?: string;
	/** Inline style applied to the `<th>` — for pages using CSS-var styling. */
	style?: CSSProperties;
	/** Header text alignment. Defaults to "center" (project-wide convention). */
	align?: "left" | "center" | "right";
}

const BASE_TH = "font-semibold px-4 py-3 select-none";
const BASE_BUTTON =
	"inline-flex items-center gap-1 cursor-pointer bg-transparent border-0 p-0 text-inherit font-inherit text-xs hover:text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand";

function caretFor(active: boolean, direction: SortDirection): string {
	if (!active || !direction) return "↕";
	return direction === "asc" ? "↑" : "↓";
}

function ariaSortFor(active: boolean, direction: SortDirection): "ascending" | "descending" | "none" {
	if (!active || !direction) return "none";
	return direction === "asc" ? "ascending" : "descending";
}

export function SortableHeader({
	sortKey,
	sortState,
	onSort,
	children,
	className,
	style,
	align = "center",
}: SortableHeaderProps) {
	const active = sortState.key === sortKey;
	const direction = active ? sortState.direction : null;
	const alignmentTh =
		align === "right" ? "text-right" : align === "left" ? "text-left" : "text-center";

	const handleKeyDown = (event: KeyboardEvent<HTMLButtonElement>): void => {
		if (event.key === "Enter" || event.key === " ") {
			event.preventDefault();
			onSort(sortKey);
		}
	};

	return (
		<th
			scope="col"
			aria-sort={ariaSortFor(active, direction)}
			className={`${BASE_TH} ${alignmentTh} ${className ?? ""}`.trim()}
			style={style}
		>
			<button
				type="button"
				className={BASE_BUTTON}
				onClick={() => onSort(sortKey)}
				onKeyDown={handleKeyDown}
				data-sort-key={sortKey}
				data-sort-active={active ? "true" : "false"}
				data-sort-direction={direction ?? "none"}
			>
				<span>{children}</span>
				<span aria-hidden="true" className="text-text-muted">
					{caretFor(active, direction)}
				</span>
			</button>
		</th>
	);
}
