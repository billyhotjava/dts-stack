import { useEffect, useMemo, useRef, useState } from "react";
import type { TableSummary } from "../../../api/analyticsApi";
import { t, type Locale } from "../../../i18n";

type FkInfo = {
	originFieldName: string;
	destinationFieldName: string;
	tableId: number;
};

type Props = {
	locale: Locale;
	tables: TableSummary[];
	fkRecommendations?: FkInfo[];
	value: number | null;
	onChange: (tableId: number) => void;
	placeholder?: string;
	disabled?: boolean;
};

export function TableSearchPicker({ locale, tables, fkRecommendations = [], value, onChange, placeholder, disabled }: Props) {
	const [search, setSearch] = useState("");
	const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
	const [debouncedSearch, setDebouncedSearch] = useState("");

	useEffect(() => {
		if (debounceRef.current) clearTimeout(debounceRef.current);
		debounceRef.current = setTimeout(() => setDebouncedSearch(search), 300);
		return () => { if (debounceRef.current) clearTimeout(debounceRef.current); };
	}, [search]);

	const fkTableIds = useMemo(() => new Set(fkRecommendations.map((f) => f.tableId)), [fkRecommendations]);

	const filtered = useMemo(() => {
		const q = debouncedSearch.toLowerCase().trim();
		if (!q) return tables;
		return tables.filter((t) => {
			const name = (t.display_name || t.name || "").toLowerCase();
			const desc = (t.description || "").toLowerCase();
			const schema = (t.schema || "").toLowerCase();
			return name.includes(q) || desc.includes(q) || schema.includes(q);
		});
	}, [tables, debouncedSearch]);

	const recommended = filtered.filter((t) => fkTableIds.has(t.id));
	const others = filtered.filter((t) => !fkTableIds.has(t.id));

	const schemaGroups = (items: TableSummary[]) => {
		const grouped = new Map<string, TableSummary[]>();
		for (const tbl of items) {
			const key = tbl.schema || "";
			const arr = grouped.get(key) ?? [];
			arr.push(tbl);
			grouped.set(key, arr);
		}
		return grouped;
	};

	const renderTable = (tbl: TableSummary) => {
		const fk = fkRecommendations.find((f) => f.tableId === tbl.id);
		const label = tbl.display_name || tbl.name || `table:${tbl.id}`;
		const isSelected = value === tbl.id;
		return (
			<div
				key={tbl.id}
				onClick={() => !disabled && onChange(tbl.id)}
				className={`flex justify-between items-center px-3 py-1.5 ${disabled ? "cursor-default" : "cursor-pointer"}`}
				style={isSelected ? { background: "var(--color-primary-bg, #EBF5FF)" } : undefined}
			>
				<span>{tbl.schema ? `${tbl.schema}.` : ""}{label}</span>
				{fk && (
					<span className="text-xs text-text-muted">
						{fk.originFieldName} → {fk.destinationFieldName}
					</span>
				)}
				{isSelected && <span className="text-brand">✓</span>}
			</div>
		);
	};

	return (
		<div>
			<input
				className="w-full box-border px-3 py-2 rounded-sm border border-border-default bg-surface-card text-text-primary mb-2"
				type="text"
				value={search}
				onChange={(e) => setSearch(e.target.value)}
				placeholder={placeholder ?? t(locale, "notebook.source.searchPlaceholder")}
				disabled={disabled}
			/>
			<div className="max-h-60 overflow-y-auto border border-border-default rounded-sm">
				{recommended.length > 0 && (
					<>
						<div className="px-3 py-1 text-xs font-semibold text-text-secondary bg-surface-muted">
							{t(locale, "notebook.source.recommended")}
						</div>
						{recommended.map(renderTable)}
					</>
				)}
				{others.length > 0 && (
					<>
						{Array.from(schemaGroups(others).entries()).map(([schema, items]) => (
							<div key={schema}>
								{schema && (
									<div className="px-3 py-1 text-xs font-semibold text-text-secondary bg-surface-muted">
										{schema}
									</div>
								)}
								{items.map(renderTable)}
							</div>
						))}
					</>
				)}
				{filtered.length === 0 && (
					<div className="p-3 text-center text-text-muted">
						—
					</div>
				)}
			</div>
		</div>
	);
}
