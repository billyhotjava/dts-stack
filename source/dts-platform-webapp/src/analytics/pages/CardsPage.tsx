import { Link } from "react-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import { analyticsApi, type CardListItem } from "../api/analyticsApi";
import { PageContainer, PageHeader } from "../components/PageContainer/PageContainer";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Button, Input, Modal, Space, Spin, Table, Tag, message } from "antd";
import { PlusOutlined, SearchOutlined, EyeOutlined, EditOutlined, DeleteOutlined } from "@ant-design/icons";
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

const DISPLAY_LABELS: Record<string, string> = {
	table: "表格", line: "折线", bar: "柱状", pie: "饼图", area: "面积",
	scalar: "数字", row: "横柱", combo: "组合", funnel: "漏斗", scatter: "散点",
	number: "数字", gauge: "仪表", map: "地图", progress: "进度", waterfall: "瀑布",
};

export default function CardsPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<CardListItem[]>>({ state: "loading" });
	const [searchQuery, setSearchQuery] = useState("");

	const loadCards = useCallback(() => {
		setState({ state: "loading" });
		analyticsApi.listCards("question")
			.then((value) => setState({ state: "loaded", value: Array.isArray(value) ? value.filter((c) => !c.archived) : [] }))
			.catch((e) => setState({ state: "error", error: e }));
	}, []);

	useEffect(() => { loadCards(); }, [loadCards]);

	const filteredCards = useMemo(() => {
		if (state.state !== "loaded") return [];
		const kw = searchQuery.trim().toLowerCase();
		if (!kw) return state.value;
		return state.value.filter((c) =>
			(c.name ?? "").toLowerCase().includes(kw) || (c.description ?? "").toLowerCase().includes(kw)
		);
	}, [state, searchQuery]);

	const handleDelete = (id: number, name: string) => {
		Modal.confirm({
			title: "移至废纸篓",
			content: `确定将「${name}」移至废纸篓？可在废纸篓中恢复。`,
			okText: "确定",
			cancelText: "取消",
			okButtonProps: { danger: true },
			onOk: async () => {
				await analyticsApi.deleteCard(id);
				message.success("已移至废纸篓");
				loadCards();
			},
		});
	};

	const columns: ColumnsType<CardListItem> = [
		{
			title: t(locale, "common.name"),
			dataIndex: "name",
			key: "name",
			ellipsis: true,
			render: (name: string, record) => (
				<Link to={`/analytics/questions/${record.id}`} className="text-brand hover:underline font-medium">
					{name || t(locale, "common.untitled")}
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
			width: 90,
			render: (display: string) => <Tag>{DISPLAY_LABELS[display] ?? display ?? "-"}</Tag>,
		},
		{
			title: t(locale, "common.updatedAt"),
			dataIndex: "updated_at",
			key: "updated_at",
			width: 155,
			render: (v: string) => <span className="text-text-muted text-xs">{formatTime(v)}</span>,
			sorter: (a, b) => (a.updated_at ?? "").localeCompare(b.updated_at ?? ""),
			defaultSortOrder: "descend",
		},
		{
			title: t(locale, "common.actions"),
			key: "actions",
			width: 150,
			render: (_, record) => (
				<Space size={4}>
					<Link to={`/analytics/questions/${record.id}`}>
						<Button type="text" size="small" icon={<EyeOutlined />}>查看</Button>
					</Link>
					<Link to={`/analytics/questions/${record.id}/edit`}>
						<Button type="text" size="small" icon={<EditOutlined />}>编辑</Button>
					</Link>
					<Button
						type="text"
						size="small"
						danger
						icon={<DeleteOutlined />}
						onClick={() => handleDelete(record.id, record.name || "")}
					>
						删除
					</Button>
				</Space>
			),
		},
	];

	return (
		<PageContainer>
			<PageHeader
				title={t(locale, "questions.title")}
				actions={
					<Link to="/analytics/questions/new">
						<Button type="primary" icon={<PlusOutlined />}>
							{t(locale, "questions.new")}
						</Button>
					</Link>
				}
			/>

			{state.state === "loading" && (
				<div className="flex justify-center py-12"><Spin size="large" /></div>
			)}
			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
			{state.state === "loaded" && (
				<div className="bg-surface-card border border-border-default rounded-lg overflow-hidden">
					<div className="flex items-center justify-between px-4 py-3 border-b border-border-default">
						<span className="text-sm text-text-secondary">
							{filteredCards.length} {filteredCards.length === state.value.length ? "条" : `/ ${state.value.length} 条`}
						</span>
						<Input
							placeholder={t(locale, "common.search")}
							prefix={<SearchOutlined />}
							value={searchQuery}
							onChange={(e) => setSearchQuery(e.target.value)}
							allowClear
							size="small"
							style={{ width: 240 }}
						/>
					</div>
					{filteredCards.length === 0 ? (
						<EmptyState title={searchQuery ? t(locale, "common.noResults") : t(locale, "common.empty")} />
					) : (
						<Table<CardListItem>
							columns={columns}
							dataSource={filteredCards}
							rowKey={(r) => r.id}
							size="small"
							pagination={filteredCards.length > 15 ? { pageSize: 15, showSizeChanger: true, showTotal: (total) => `${total} 条` } : false}
						/>
					)}
				</div>
			)}
		</PageContainer>
	);
}
