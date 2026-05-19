import { useCallback, useEffect, useMemo, useState } from "react";
import { analyticsApi, type TrashItem, type TrashResponse } from "../api/analyticsApi";
import { PageHeader } from "@/components/page-header";
import { ErrorNotice } from "../components/ErrorNotice";
import { Button, Spin, Tag, Checkbox, message } from "antd";
import { getEffectiveLocale, t, type Locale } from "../i18n";
type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

// Icons
const TrashIcon = () => (
	<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M3 6h18" />
		<path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" />
		<path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" />
	</svg>
);

export default function TrashPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<TrashResponse>>({ state: "loading" });
	const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

	const loadTrash = useCallback(() => {
		setState({ state: "loading" });
		analyticsApi
			.getTrash()
			.then((value) => {
				setState({ state: "loaded", value });
			})
			.catch((e) => {
				setState({ state: "error", error: e });
			});
	}, []);

	useEffect(() => {
		loadTrash();
	}, [loadTrash]);

	const allItems: TrashItem[] = useMemo(() => {
		if (state.state !== "loaded") return [];
		return [...(state.value.dashboards ?? []), ...(state.value.cards ?? [])];
	}, [state]);

	const loading = state.state === "loading";

	const restoreItem = async (model: string, id: number) => {
		if (model === "card") {
			await analyticsApi.updateCard(id, { archived: false });
		} else {
			await analyticsApi.updateDashboard(id, { archived: false });
		}
		message.success("已恢复");
		loadTrash();
	};

	const handleBatchRestore = async () => {
		const items = Array.from(selectedIds).map((key) => {
			const [model, idStr] = key.split("-");
			return { model, id: Number(idStr) };
		});
		const results = await Promise.allSettled(
			items.map((it) =>
				it.model === "card"
					? analyticsApi.updateCard(it.id, { archived: false })
					: analyticsApi.updateDashboard(it.id, { archived: false }),
			),
		);
		const failed = results.filter((r) => r.status === "rejected").length;
		if (failed > 0) message.warning(`${items.length - failed} 项已恢复，${failed} 项失败`);
		else message.success(`${items.length} 项已恢复`);
		setSelectedIds(new Set());
		loadTrash();
	};

	return (
		<div className="space-y-4">
			<PageHeader
				title={t(locale, "trash.title")}
				actions={
					selectedIds.size > 0 ? (
						<Button onClick={handleBatchRestore}>
							恢复选中项 ({selectedIds.size})
						</Button>
					) : undefined
				}
			/>

			{loading && (
				<div className="loading-container" style={{ padding: "var(--spacing-xl)", textAlign: "center" }}>
					<Spin size="large" />
				</div>
			)}
			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
			{state.state === "loaded" && allItems.length === 0 && (
				<div style={{ display: "flex", flexDirection: "column", alignItems: "center", padding: "var(--spacing-xl)", textAlign: "center" }}>
					<div style={{ color: "var(--color-text-tertiary)", marginBottom: "var(--spacing-md)" }}>
						<TrashIcon />
					</div>
					<h3 style={{ margin: 0, color: "var(--color-text-secondary)" }}>{t(locale, "common.empty")}</h3>
					<p className="text-secondary" style={{ marginTop: "var(--spacing-sm)" }}>
						{t(locale, "trash.emptyDesc")}
					</p>
				</div>
			)}
			{state.state === "loaded" && allItems.length > 0 && (
				<div style={{ background: "var(--color-bg-primary)", border: "1px solid var(--color-border)", borderRadius: "var(--radius-md)" }}>
					<div style={{ display: "flex", alignItems: "center", gap: 12, padding: "8px 12px", borderBottom: "1px solid #f0f0f0", background: "#fafafa", borderRadius: "var(--radius-md) var(--radius-md) 0 0" }}>
						<Checkbox
							checked={allItems.length > 0 && selectedIds.size === allItems.length}
							indeterminate={selectedIds.size > 0 && selectedIds.size < allItems.length}
							onChange={() => {
								if (selectedIds.size === allItems.length) setSelectedIds(new Set());
								else setSelectedIds(new Set(allItems.map((it) => `${it.model}-${it.id}`)));
							}}
						/>
						<span style={{ fontWeight: 500, color: "#666" }}>全选</span>
					</div>
					{allItems.map((it) => {
						const key = `${it.model}-${it.id}`;
						return (
							<div key={key} style={{ display: "flex", alignItems: "center", gap: 12, padding: "8px 12px", borderBottom: "1px solid #f0f0f0" }}>
								<Checkbox
									checked={selectedIds.has(key)}
									onChange={() => {
										setSelectedIds((prev) => {
											const next = new Set(prev);
											if (next.has(key)) next.delete(key);
											else next.add(key);
											return next;
										});
									}}
								/>
								<Tag color={it.model === "dashboard" ? "blue" : "green"}>
									{it.model === "dashboard" ? "仪表盘" : "查询"}
								</Tag>
								<span style={{ flex: 1 }}>{it.name ?? "-"}</span>
								{it.updated_at && (
									<span style={{ color: "#999", fontSize: 12 }}>
										{new Date(it.updated_at).toLocaleDateString()}
									</span>
								)}
								<Button type="link" size="small" onClick={() => restoreItem(it.model, it.id)}>
									恢复
								</Button>
							</div>
						);
					})}
				</div>
			)}
		</div>
	);
}
