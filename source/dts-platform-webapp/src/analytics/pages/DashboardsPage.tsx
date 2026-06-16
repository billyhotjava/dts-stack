import { Link } from "react-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import { analyticsApi, type DashboardListItem } from "../api/analyticsApi";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Button, Card, Input, Modal, Space, Spin, message } from "antd";
import { CompactTable } from "@/components/table";
import { } from "@ant-design/icons";
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

export default function DashboardsPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<DashboardListItem[]>>({ state: "loading" });
	const [searchQuery, setSearchQuery] = useState("");

	const loadDashboards = useCallback(() => {
		setState({ state: "loading" });
		analyticsApi.listDashboards()
			.then((value) => setState({ state: "loaded", value: Array.isArray(value) ? value.filter((d) => !d.archived) : [] }))
			.catch((e) => setState({ state: "error", error: e }));
	}, []);

	useEffect(() => { loadDashboards(); }, [loadDashboards]);

	const filteredDashboards = useMemo(() => {
		if (state.state !== "loaded") return [];
		const kw = searchQuery.trim().toLowerCase();
		if (!kw) return state.value;
		return state.value.filter((d) =>
			(d.name ?? "").toLowerCase().includes(kw) || (d.description ?? "").toLowerCase().includes(kw)
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
				await analyticsApi.deleteDashboard(id);
				message.success("已移至废纸篓");
				loadDashboards();
			},
		});
	};

	const handleShare = async (id: number) => {
		const url = `${window.location.origin}/bi/dashboards/${id}`;
		try {
			await navigator.clipboard.writeText(url);
			message.success("看板链接已复制");
		} catch {
			message.error("复制失败，请手动复制浏览器地址");
		}
	};

	const columns: ColumnsType<DashboardListItem> = [
		{
			title: t(locale, "common.name"),
			dataIndex: "name",
			sorter: (a, b) => (a.name || "").localeCompare(b.name || ""),
			key: "name",
			ellipsis: true,
			render: (name: string, record) => (
				<Link to={`/bi/dashboards/${record.id}`} className="text-brand hover:underline font-medium">
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
			width: 240,
			render: (_, record) => (
				<Space size={4} wrap>
					<Link to={`/bi/dashboards/${record.id}`}>
						<Button type="link" size="small">查看</Button>
					</Link>
					<Link to={`/bi/dashboards/${record.id}/edit`}>
						<Button type="link" size="small">编辑</Button>
					</Link>
					<Button type="link" size="small" disabled title="请进入编辑器完成发布门禁">
						发布
					</Button>
					<Button type="link" size="small" onClick={() => void handleShare(record.id)}>
						分享
					</Button>
					<Button
						type="link"
						size="small"
						danger
						onClick={() => handleDelete(record.id, record.name || "")}
					>
						删除
					</Button>
				</Space>
			),
		},
	];

	return (
		<div className="space-y-4">
			<div data-testid="analytics-dashboards-page">
			<PageHeader
				title={t(locale, "dashboards.title")}
				actions={
					<Space>
						<Button disabled title="进入看板编辑器后添加图表">
							添加图表
						</Button>
						<Link to="/bi/dashboards/new">
							<Button type="primary">
								新建看板
							</Button>
						</Link>
					</Space>
				}
			/>

			{state.state === "loading" && (
				<div className="flex justify-center py-12"><Spin size="large" /></div>
			)}
			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
			{state.state === "loaded" && (
				<Card>
					<Space className="mb-4">
						<Input.Search
							data-testid="analytics-dashboard-search"
							placeholder={t(locale, "common.search")}
							value={searchQuery}
							onChange={(e) => setSearchQuery(e.target.value)}
							allowClear
							style={{ width: 300 }}
						/>
					</Space>
					{filteredDashboards.length === 0 ? (
						<EmptyState title={searchQuery ? t(locale, "common.noResults") : t(locale, "common.empty")} />
					) : (
						<CompactTable<DashboardListItem>
							columns={columns}
							dataSource={filteredDashboards}
							rowKey={(r) => r.id}
							size="small"
							pagination={filteredDashboards.length > 10 ? { pageSize: 10, showSizeChanger: true, showTotal: (total) => `${total} 条` } : false}
						/>
					)}
				</Card>
			)}
			</div>
		</div>
	);
}
