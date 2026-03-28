import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Drawer } from "../../../ui/Drawer/Drawer";
import { Input, Spin, Button, Tag } from "antd";
import { analyticsApi, type ProjectCockpitDrillItem } from "../../../api/analyticsApi";
import { useProjectCockpitContext, type DrillTarget } from "../ProjectCockpitContext";
import "./DrillDownDrawer.css";

type SortKey = keyof ProjectCockpitDrillItem;
type SortDir = "asc" | "desc";

const DRILL_TITLES: Record<string, string> = {
	"high-risk": "高风险节点明细",
	overdue: "延期节点明细",
	completion: "节点完成情况",
	milestone: "里程碑明细",
	"delay-reason": "延期原因追溯",
	"delay-dept": "部门延期明细",
	"trend-week": "周度数据明细",
};

type ColumnDef = {
	key: SortKey;
	label: string;
	render?: (item: ProjectCockpitDrillItem) => React.ReactNode;
};

function riskBadge(level?: string) {
	if (!level) return null;
	const color = level === "高" ? "error" : level === "中" ? "warning" : undefined;
	return <Tag color={color}>{level}</Tag>;
}

function buildColumns(target: DrillTarget): ColumnDef[] {
	const base: ColumnDef[] = [
		{ key: "name", label: "名称" },
		{ key: "majorProjectName", label: "项目" },
		{ key: "subprojectName", label: "子项目" },
	];

	switch (target) {
		case "high-risk":
			return [
				...base,
				{ key: "riskLevel", label: "风险等级", render: (item) => riskBadge(item.riskLevel) },
				{ key: "ownerDept", label: "责任部门" },
				{ key: "delayDays", label: "延期天数" },
			];
		case "overdue":
			return [
				...base,
				{ key: "planDate", label: "计划日期" },
				{ key: "actualDate", label: "实际日期" },
				{ key: "delayDays", label: "延期天数" },
				{ key: "reason", label: "延期原因" },
			];
		case "delay-reason":
		case "delay-dept":
			return [
				...base,
				{ key: "ownerDept", label: "责任部门" },
				{ key: "reason", label: "延期原因" },
				{ key: "delayDays", label: "延期天数" },
				{ key: "riskLevel", label: "风险等级", render: (item) => riskBadge(item.riskLevel) },
			];
		case "completion":
		case "milestone":
			return [
				...base,
				{ key: "status", label: "状态" },
				{ key: "progressRate", label: "进度%" },
				{ key: "planDate", label: "计划日期" },
			];
		default:
			return [
				...base,
				{ key: "status", label: "状态" },
				{ key: "delayDays", label: "延期天数" },
			];
	}
}

function compareValues(a: unknown, b: unknown, dir: SortDir): number {
	const av = a ?? "";
	const bv = b ?? "";
	if (typeof av === "number" && typeof bv === "number") {
		return dir === "asc" ? av - bv : bv - av;
	}
	const cmp = String(av).localeCompare(String(bv), "zh-CN");
	return dir === "asc" ? cmp : -cmp;
}

export function DrillDownDrawer() {
	const { drillState, closeDrill, effectiveQueryState } = useProjectCockpitContext();
	const { target, params } = drillState;

	const [items, setItems] = useState<ProjectCockpitDrillItem[]>([]);
	const [loading, setLoading] = useState(false);
	const [search, setSearch] = useState("");
	const [sortKey, setSortKey] = useState<SortKey | null>(null);
	const [sortDir, setSortDir] = useState<SortDir>("asc");
	const abortRef = useRef<AbortController | null>(null);

	const isOpen = target !== null;

	useEffect(() => {
		if (!target) return;
		setSearch("");
		setSortKey(null);
		setLoading(true);

		abortRef.current?.abort();
		const ctrl = new AbortController();
		abortRef.current = ctrl;

		const apiParams: Record<string, string> = {
			...(effectiveQueryState.majorProjectId ? { majorProjectId: effectiveQueryState.majorProjectId } : {}),
			...(effectiveQueryState.dateFrom ? { dateFrom: effectiveQueryState.dateFrom } : {}),
			...(effectiveQueryState.dateTo ? { dateTo: effectiveQueryState.dateTo } : {}),
			...(effectiveQueryState.deptId ? { deptId: effectiveQueryState.deptId } : {}),
			...(effectiveQueryState.riskLevel ? { riskLevel: effectiveQueryState.riskLevel } : {}),
		};
		for (const [k, v] of Object.entries(params)) {
			if (v != null) apiParams[k] = String(v);
		}

		analyticsApi
			.getProjectCockpitDrillDetail(target, apiParams)
			.then((resp) => {
				if (!ctrl.signal.aborted) {
					setItems(resp?.items ?? []);
				}
			})
			.catch((err) => {
				if (!ctrl.signal.aborted) {
					console.warn("[DrillDownDrawer] drill detail fetch failed:", err);
					setItems([]);
				}
			})
			.finally(() => {
				if (!ctrl.signal.aborted) setLoading(false);
			});

		return () => ctrl.abort();
	}, [target, params, effectiveQueryState]);

	const columns = useMemo(() => buildColumns(target), [target]);

	const handleSort = useCallback(
		(key: SortKey) => {
			if (sortKey === key) {
				setSortDir((prev) => (prev === "asc" ? "desc" : "asc"));
			} else {
				setSortKey(key);
				setSortDir("asc");
			}
		},
		[sortKey],
	);

	const filtered = useMemo(() => {
		const q = search.trim().toLowerCase();
		let result = items;
		if (q) {
			result = items.filter((item) =>
				Object.values(item).some((v) => v != null && String(v).toLowerCase().includes(q)),
			);
		}
		if (sortKey) {
			result = [...result].sort((a, b) => compareValues(a[sortKey], b[sortKey], sortDir));
		}
		return result;
	}, [items, search, sortKey, sortDir]);

	const title = DRILL_TITLES[target ?? ""] ?? "明细";
	const drillParams = params;
	const subtitle = useMemo(() => {
		const parts: string[] = [];
		if (drillParams.dept) parts.push(`部门: ${drillParams.dept}`);
		if (drillParams.reason) parts.push(`原因: ${drillParams.reason}`);
		if (drillParams.week) parts.push(`周度: ${drillParams.week}`);
		return parts.join(" | ");
	}, [drillParams]);

	const handleExportCsv = useCallback(() => {
		if (filtered.length === 0) return;
		const header = columns.map((c) => c.label).join(",");
		const rows = filtered.map((item) =>
			columns.map((c) => `"${String(item[c.key] ?? "").replace(/"/g, '""')}"`).join(","),
		);
		const bom = "\uFEFF";
		const csv = bom + [header, ...rows].join("\n");
		const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
		const url = URL.createObjectURL(blob);
		const a = document.createElement("a");
		a.href = url;
		a.download = `${title}.csv`;
		a.click();
		URL.revokeObjectURL(url);
	}, [filtered, columns, title]);

	return (
		<Drawer
			isOpen={isOpen}
			onClose={closeDrill}
			title={title}
			description={subtitle || undefined}
			size="large"
			footer={
				<div style={{ display: "flex", gap: 8 }}>
					<Button type="default" onClick={handleExportCsv} disabled={filtered.length === 0}>
						导出 CSV
					</Button>
					<Button type="text" onClick={closeDrill}>
						关闭
					</Button>
				</div>
			}
		>
			<div className="drill-down-drawer__toolbar">
				<Input
					type="text"
					placeholder="搜索..."
					value={search}
					onChange={(e) => setSearch(e.target.value)}
					style={{ maxWidth: 280 }}
				/>
				<span className="drill-down-drawer__count">
					共 {filtered.length} 条{search && items.length !== filtered.length ? ` / ${items.length}` : ""}
				</span>
			</div>
			{loading ? (
				<div className="drill-down-drawer__loading">
					<Spin />
				</div>
			) : filtered.length === 0 ? (
				<div className="drill-down-drawer__empty">暂无数据</div>
			) : (
				<div className="drill-down-drawer__table-wrap">
					<table className="drill-down-drawer__table">
						<thead>
							<tr>
								{columns.map((col) => (
									<th
										key={col.key}
										onClick={() => handleSort(col.key)}
										className="drill-down-drawer__th"
									>
										{col.label}
										{sortKey === col.key ? (sortDir === "asc" ? " ↑" : " ↓") : ""}
									</th>
								))}
							</tr>
						</thead>
						<tbody>
							{filtered.map((item, idx) => (
								<tr key={item.id ?? idx} className="drill-down-drawer__tr">
									{columns.map((col) => (
										<td key={col.key} className="drill-down-drawer__td">
											{col.render ? col.render(item) : String(item[col.key] ?? "-")}
										</td>
									))}
								</tr>
							))}
						</tbody>
					</table>
				</div>
			)}
		</Drawer>
	);
}
