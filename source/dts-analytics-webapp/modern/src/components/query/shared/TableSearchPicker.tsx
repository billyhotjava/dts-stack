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
				style={{
					padding: "6px 12px",
					cursor: disabled ? "default" : "pointer",
					background: isSelected ? "var(--color-primary-bg, #EBF5FF)" : undefined,
					display: "flex",
					justifyContent: "space-between",
					alignItems: "center",
				}}
			>
				<span>{tbl.schema ? `${tbl.schema}.` : ""}{label}</span>
				{fk && (
					<span style={{ fontSize: "12px", color: "var(--color-text-tertiary)" }}>
						{fk.originFieldName} → {fk.destinationFieldName}
					</span>
				)}
				{isSelected && <span style={{ color: "var(--color-primary, #3B82F6)" }}>✓</span>}
			</div>
		);
	};

	return (
		<div>
			<input
				className="input"
				type="text"
				value={search}
				onChange={(e) => setSearch(e.target.value)}
				placeholder={placeholder ?? t(locale, "notebook.source.searchPlaceholder")}
				disabled={disabled}
				style={{ width: "100%", marginBottom: 8 }}
			/>
			<div style={{ maxHeight: 240, overflowY: "auto", border: "1px solid var(--color-border, #e0e0e0)", borderRadius: "var(--radius-sm, 4px)" }}>
				{recommended.length > 0 && (
					<>
						<div style={{ padding: "4px 12px", fontSize: "12px", fontWeight: 600, color: "var(--color-text-secondary)", background: "var(--color-bg-secondary, #f8f9fa)" }}>
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
									<div style={{ padding: "4px 12px", fontSize: "12px", fontWeight: 600, color: "var(--color-text-secondary)", background: "var(--color-bg-secondary, #f8f9fa)" }}>
										{schema}
									</div>
								)}
								{items.map(renderTable)}
							</div>
						))}
					</>
				)}
				{filtered.length === 0 && (
					<div style={{ padding: "12px", textAlign: "center", color: "var(--color-text-tertiary)" }}>
						—
					</div>
				)}
			</div>
		</div>
	);
}
