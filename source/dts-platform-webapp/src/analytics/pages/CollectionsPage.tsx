import { Link } from "react-router";
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type CardListItem, type DashboardListItem } from "../api/analyticsApi";
import { PageContainer, PageHeader } from "../components/PageContainer/PageContainer";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Button, Input, Spin, Table, Tag } from "antd";
import { PlusOutlined, SearchOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import { getEffectiveLocale, t, type Locale } from "../i18n";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

function formatTime(raw?: string | null): string {
	if (!raw) return "-";
	try {
		const d = new Date(raw);
		return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
	} catch {
		return raw;
	}
}

export default function CollectionsPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [cardsState, setCardsState] = useState<LoadState<CardListItem[]>>({ state: "loading" });
	const [dashboardsState, setDashboardsState] = useState<LoadState<DashboardListItem[]>>({ state: "loading" });
	const [cardSearch, setCardSearch] = useState("");
	const [dashSearch, setDashSearch] = useState("");

	useEffect(() => {
		let cancelled = false;
		analyticsApi.listCards("question").then((value) => {
			if (!cancelled) setCardsState({ state: "loaded", value: Array.isArray(value) ? value.filter((c) => !c.archived) : [] });
		}).catch((e) => {
			if (!cancelled) setCardsState({ state: "error", error: e });
		});
		analyticsApi.listDashboards().then((value) => {
			if (!cancelled) setDashboardsState({ state: "loaded", value: Array.isArray(value) ? value.filter((d) => !d.archived) : [] });
		}).catch((e) => {
			if (!cancelled) setDashboardsState({ state: "error", error: e });
		});
		return () => { cancelled = true; };
	}, []);

	const filteredCards = useMemo(() => {
		if (cardsState.state !== "loaded") return [];
		const kw = cardSearch.trim().toLowerCase();
		if (!kw) return cardsState.value;
		return cardsState.value.filter((c) =>
			(c.name ?? "").toLowerCase().includes(kw) || (c.description ?? "").toLowerCase().includes(kw)
		);
	}, [cardsState, cardSearch]);

	const filteredDashboards = useMemo(() => {
		if (dashboardsState.state !== "loaded") return [];
		const kw = dashSearch.trim().toLowerCase();
		if (!kw) return dashboardsState.value;
		return dashboardsState.value.filter((d) =>
			(d.name ?? "").toLowerCase().includes(kw) || (d.description ?? "").toLowerCase().includes(kw)
		);
	}, [dashboardsState, dashSearch]);

	const cardColumns: ColumnsType<CardListItem> = [
		{
			title: t(locale, "common.name"),
			dataIndex: "name",
			key: "name",
			render: (name: string, record) => (
				<Link to={`/bi/questions/${record.id}`} className="text-brand hover:underline font-medium">
					{name || "-"}
				</Link>
			),
		},
		{
			title: t(locale, "common.description"),
			dataIndex: "description",
			key: "description",
			ellipsis: true,
			render: (desc: string | null) => <span className="text-text-secondary">{desc || "-"}</span>,
		},
		{
			title: t(locale, "common.type"),
			dataIndex: "display",
			key: "display",
			width: 100,
			render: (display: string) => {
				const labelMap: Record<string, string> = {
					table: "表格", line: "折线", bar: "柱状", pie: "饼图", area: "面积",
					scalar: "数字", row: "横柱", combo: "组合", funnel: "漏斗", scatter: "散点",
				};
				return <Tag>{labelMap[display] ?? display ?? "-"}</Tag>;
			},
		},
		{
			title: t(locale, "common.updatedAt"),
			dataIndex: "updated_at",
			key: "updated_at",
			width: 160,
			render: (v: string) => <span className="text-text-muted text-xs">{formatTime(v)}</span>,
			sorter: (a, b) => (a.updated_at ?? "").localeCompare(b.updated_at ?? ""),
			defaultSortOrder: "descend",
		},
	];

	const dashColumns: ColumnsType<DashboardListItem> = [
		{
			title: t(locale, "common.name"),
			dataIndex: "name",
			key: "name",
			render: (name: string, record) => (
				<Link to={`/bi/dashboards/${record.id}`} className="text-brand hover:underline font-medium">
					{name || "-"}
				</Link>
			),
		},
		{
			title: t(locale, "common.description"),
			dataIndex: "description",
			key: "description",
			ellipsis: true,
			render: (desc: string | null) => <span className="text-text-secondary">{desc || "-"}</span>,
		},
		{
			title: t(locale, "common.updatedAt"),
			dataIndex: "updated_at",
			key: "updated_at",
			width: 160,
			render: (v: string) => <span className="text-text-muted text-xs">{formatTime(v)}</span>,
			sorter: (a, b) => (a.updated_at ?? "").localeCompare(b.updated_at ?? ""),
			defaultSortOrder: "descend",
		},
	];

	const isLoading = cardsState.state === "loading" || dashboardsState.state === "loading";
	const hasError = cardsState.state === "error" || dashboardsState.state === "error";

	return (
		<PageContainer>
			<PageHeader title={t(locale, "collections.title")} />

			{isLoading && (
				<div className="flex justify-center py-12">
					<Spin size="large" />
				</div>
			)}

			{hasError && (
				<>
					{cardsState.state === "error" && <ErrorNotice locale={locale} error={cardsState.error} />}
					{dashboardsState.state === "error" && <ErrorNotice locale={locale} error={dashboardsState.error} />}
				</>
			)}

			{!isLoading && !hasError && (
				<div className="flex flex-col gap-6">
					{/* 查询 Table */}
					<div className="bg-surface-card border border-border-default rounded-lg overflow-hidden">
						<div className="flex items-center justify-between px-4 py-3 border-b border-border-default">
							<div className="flex items-center gap-2">
								<h3 className="text-base font-semibold text-text-primary m-0">
									{t(locale, "questions.title")}
								</h3>
								<Tag color="blue">{filteredCards.length}</Tag>
							</div>
							<div className="flex items-center gap-2">
								<Input
									placeholder={t(locale, "common.search")}
									prefix={<SearchOutlined />}
									value={cardSearch}
									onChange={(e) => setCardSearch(e.target.value)}
									allowClear
									size="small"
									style={{ width: 200 }}
								/>
								<Link to="/bi/questions/new">
									<Button type="primary" size="small" icon={<PlusOutlined />}>
										{t(locale, "questions.new")}
									</Button>
								</Link>
							</div>
						</div>
						{filteredCards.length === 0 ? (
							<EmptyState title={t(locale, "common.empty")} />
						) : (
							<Table<CardListItem>
								columns={cardColumns}
								dataSource={filteredCards}
								rowKey={(r) => r.id}
								size="small"
								pagination={filteredCards.length > 10 ? { pageSize: 10, showSizeChanger: true, showTotal: (total) => `${total} 条` } : false}
							/>
						)}
					</div>

					{/* 看板 Table */}
					<div className="bg-surface-card border border-border-default rounded-lg overflow-hidden">
						<div className="flex items-center justify-between px-4 py-3 border-b border-border-default">
							<div className="flex items-center gap-2">
								<h3 className="text-base font-semibold text-text-primary m-0">
									{t(locale, "dashboards.title")}
								</h3>
								<Tag color="green">{filteredDashboards.length}</Tag>
							</div>
							<div className="flex items-center gap-2">
								<Input
									placeholder={t(locale, "common.search")}
									prefix={<SearchOutlined />}
									value={dashSearch}
									onChange={(e) => setDashSearch(e.target.value)}
									allowClear
									size="small"
									style={{ width: 200 }}
								/>
								<Link to="/bi/dashboards/new">
									<Button type="primary" size="small" icon={<PlusOutlined />}>
										{t(locale, "dashboards.new")}
									</Button>
								</Link>
							</div>
						</div>
						{filteredDashboards.length === 0 ? (
							<EmptyState title={t(locale, "common.empty")} />
						) : (
							<Table<DashboardListItem>
								columns={dashColumns}
								dataSource={filteredDashboards}
								rowKey={(r) => r.id}
								size="small"
								pagination={filteredDashboards.length > 10 ? { pageSize: 10, showSizeChanger: true, showTotal: (total) => `${total} 条` } : false}
							/>
						)}
					</div>
				</div>
			)}
		</PageContainer>
	);
}
