import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { Button, Card, Input, Modal, Spin, Tag, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { PageHeader } from "@/components/page-header";
import { actionColumn, CompactTable } from "@/components/table";
import {
	archiveAnalysis,
	listAnalyses,
	type Analysis,
	type AnalysisPage,
} from "../api/analysisApi";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, type Locale } from "../i18n";

type LoadState =
	| { state: "loading" }
	| { state: "loaded"; value: AnalysisPage }
	| { state: "error"; error: unknown };

const STATUS_LABELS: Record<Analysis["lifecycleStatus"], string> = {
	DRAFT: "草稿",
	PUBLISHED: "已发布",
	ARCHIVED: "已归档",
};

const STATUS_COLORS: Record<Analysis["lifecycleStatus"], string> = {
	DRAFT: "default",
	PUBLISHED: "green",
	ARCHIVED: "orange",
};

const VISUALIZATION_LABELS: Record<Analysis["visualization"]["type"], string> = {
	table: "表格",
	bar: "柱状图",
	line: "折线图",
	area: "面积图",
	pie: "饼图",
	number: "指标卡",
	scatter: "散点图",
};

function formatTime(raw?: string | null): string {
	if (!raw) return "-";
	const value = new Date(raw);
	if (Number.isNaN(value.getTime())) return raw;
	return new Intl.DateTimeFormat("zh-CN", {
		year: "numeric",
		month: "2-digit",
		day: "2-digit",
		hour: "2-digit",
		minute: "2-digit",
		hour12: false,
	}).format(value);
}

export default function CardsPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState>({ state: "loading" });
	const [page, setPage] = useState(0);
	const [pageSize, setPageSize] = useState(10);
	const [searchQuery, setSearchQuery] = useState("");

	const loadAnalyses = useCallback(async () => {
		setState({ state: "loading" });
		try {
			setState({ state: "loaded", value: await listAnalyses(page, pageSize) });
		} catch (error) {
			setState({ state: "error", error });
		}
	}, [page, pageSize]);

	useEffect(() => {
		void loadAnalyses();
	}, [loadAnalyses]);

	const analyses = useMemo(() => {
		if (state.state !== "loaded") return [];
		const keyword = searchQuery.trim().toLocaleLowerCase();
		if (!keyword) return state.value.items;
		return state.value.items.filter(
			(item) =>
				item.name.toLocaleLowerCase().includes(keyword) ||
				(item.description ?? "").toLocaleLowerCase().includes(keyword),
		);
	}, [searchQuery, state]);

	const handleArchive = (analysis: Analysis) => {
		Modal.confirm({
			title: "归档分析",
			content: `确定归档「${analysis.name}」？已发布版本和审计记录仍会保留。`,
			okText: "归档",
			cancelText: "取消",
			okButtonProps: { danger: true },
			onOk: async () => {
				try {
					await archiveAnalysis(analysis.id);
					message.success("分析已归档");
					await loadAnalyses();
				} catch (error) {
					message.error(error instanceof Error ? error.message : "归档分析失败");
					throw error;
				}
			},
		});
	};

	const columns: ColumnsType<Analysis> = [
		{
			title: "分析名称",
			dataIndex: "name",
			key: "name",
			ellipsis: true,
			render: (name: string, record) => (
				<Link to={`/bi/questions/${record.id}`} className="font-medium text-brand hover:underline">
					{name || "未命名分析"}
				</Link>
			),
		},
		{
			title: "说明",
			dataIndex: "description",
			key: "description",
			ellipsis: true,
			render: (description: string | null) => (
				<span className="text-text-secondary">{description || "-"}</span>
			),
		},
		{
			title: "状态",
			dataIndex: "lifecycleStatus",
			key: "lifecycleStatus",
			width: 100,
			render: (status: Analysis["lifecycleStatus"]) => (
				<Tag color={STATUS_COLORS[status]}>{STATUS_LABELS[status]}</Tag>
			),
		},
		{
			title: "图表",
			key: "visualization",
			width: 100,
			render: (_, record) => <Tag>{VISUALIZATION_LABELS[record.visualization.type]}</Tag>,
		},
		{
			title: "版本",
			dataIndex: "versionNo",
			key: "versionNo",
			width: 80,
			render: (version: number) => `v${version}`,
		},
		{
			title: "更新时间",
			dataIndex: "updatedAt",
			key: "updatedAt",
			width: 170,
			render: (value: string | null) => <span className="text-text-muted">{formatTime(value)}</span>,
		},
		actionColumn<Analysis>(
			(record) => [
				{ key: "view", label: "查看", href: `/bi/questions/${record.id}` },
				{
					key: "edit",
					label: "编辑",
					href: `/bi/questions/${record.id}/edit`,
					hidden: !record.permissions.write || record.lifecycleStatus === "ARCHIVED",
				},
				{
					key: "archive",
					label: "归档",
					danger: true,
					hidden: !record.permissions.write || record.lifecycleStatus === "ARCHIVED",
					onClick: () => handleArchive(record),
				},
			],
			{ maxActions: 3 },
		),
	];

	return (
		<div className="space-y-4">
			<PageHeader
				title="分析"
				actions={
					<Link to="/bi/data">
						<Button type="primary">从已发布数据集创建分析</Button>
					</Link>
				}
			/>

			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}

			<Card styles={{ body: { padding: 0 } }}>
				<div className="flex min-h-[480px] flex-col">
					<div className="flex flex-wrap items-center justify-between gap-3 border-b border-border-default p-4">
						<Input.Search
							allowClear
							placeholder="搜索分析名称或说明"
							style={{ width: 320 }}
							value={searchQuery}
							onChange={(event) => setSearchQuery(event.target.value)}
						/>
						{state.state === "loaded" && <Tag color="blue">共 {state.value.totalElements} 个分析</Tag>}
					</div>

					{state.state === "loading" ? (
						<div className="flex flex-1 items-center justify-center">
							<Spin size="large" />
						</div>
					) : state.state === "loaded" && analyses.length === 0 ? (
						<EmptyState
							title={searchQuery ? "没有匹配的分析" : "还没有分析"}
							description={searchQuery ? "请调整搜索条件。" : "请先从已发布数据集中创建分析。"}
							action={
								searchQuery ? (
									<Button onClick={() => setSearchQuery("")}>清除搜索</Button>
								) : (
									<Link to="/bi/data">
										<Button type="primary">选择已发布数据集</Button>
									</Link>
								)
							}
						/>
					) : state.state === "loaded" ? (
						<div className="p-4">
							<CompactTable<Analysis>
								columns={columns}
								dataSource={analyses}
								rowKey="id"
								pagination={{
									current: page + 1,
									pageSize,
									total: state.value.totalElements,
									showSizeChanger: true,
									showTotal: (total) => `共 ${total} 条`,
									onChange: (nextPage, nextPageSize) => {
										if (nextPageSize !== pageSize) {
											setPageSize(nextPageSize);
											setPage(0);
										} else {
											setPage(nextPage - 1);
										}
									},
								}}
							/>
						</div>
					) : null}
				</div>
			</Card>
		</div>
	);
}
