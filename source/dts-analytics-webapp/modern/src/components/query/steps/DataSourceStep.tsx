import { useEffect, useState } from "react";
import { analyticsApi, type TableSummary, type TableDetail, type VisibleTable } from "../../../api/analyticsApi";
import { TableSearchPicker } from "../shared/TableSearchPicker";
import { ErrorNotice } from "../../ErrorNotice";
import { t, type Locale } from "../../../i18n";

type LoadState<T> =
	| { state: "idle" }
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

type Props = {
	locale: Locale;
	databaseId: number | null;
	tableId: number | null;
	onTableSelected: (tableId: number, detail: TableDetail) => void;
};

export function DataSourceStep({ locale, databaseId, tableId, onTableSelected }: Props) {
	const [tables, setTables] = useState<LoadState<TableSummary[]>>({ state: "idle" });

	useEffect(() => {
		if (!databaseId) { setTables({ state: "idle" }); return; }
		let cancelled = false;
		setTables({ state: "loading" });

		Promise.all([
			analyticsApi.listVisibleTables().catch(() => null),
			analyticsApi.listTables(databaseId),
		])
			.then(([rawVisible, tableList]) => {
				if (cancelled) return;
				const ids = new Set<number>();
				if (Array.isArray(rawVisible)) {
					for (const it of rawVisible) {
						if (typeof it === "number") { ids.add(it); continue; }
						const obj = it as VisibleTable;
						const id = Number(obj?.tableId);
						const dbId = Number(obj?.dbId);
						if (Number.isFinite(id) && id > 0 && (!Number.isFinite(dbId) || dbId === databaseId)) ids.add(id);
					}
				}
				const safe = Array.isArray(tableList) ? tableList : [];
				const filtered = ids.size > 0 ? safe.filter((tb) => ids.has(tb.id)) : safe;
				setTables({ state: "loaded", value: filtered });
			})
			.catch((e) => { if (!cancelled) setTables({ state: "error", error: e }); });

		return () => { cancelled = true; };
	}, [databaseId]);

	const handleSelect = async (id: number) => {
		try {
			const detail = await analyticsApi.getTable(id);
			onTableSelected(id, detail);
		} catch {
			// error handled by caller
		}
	};

	if (!databaseId) return null;

	return (
		<div>
			{tables.state === "error" && <ErrorNotice locale={locale} error={tables.error} />}
			{tables.state === "loading" && <div className="muted">{t(locale, "loading")}</div>}
			{tables.state === "loaded" && (
				<TableSearchPicker
					locale={locale}
					tables={tables.value}
					value={tableId}
					onChange={handleSelect}
					placeholder={t(locale, "notebook.source.searchPlaceholder")}
				/>
			)}
		</div>
	);
}
