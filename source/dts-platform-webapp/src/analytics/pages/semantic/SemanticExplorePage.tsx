import { } from "@ant-design/icons";
import { Button, Card, Space, Spin, Tag, Typography } from "antd";
import { CompactTable, RecordDetailDrawer, appendDetailAction } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { PageHeader } from "@/components/page-header";
import { useMenuStore } from "@/store/menuStore";
import {
	analyticsApi,
	type SemanticGraphResponse,
	type SemanticMetaResponse,
	type SemanticModelMeta,
} from "../../api/analyticsApi";
import { ErrorNotice } from "../../components/ErrorNotice";
import { getEffectiveLocale, type Locale } from "../../i18n";
import SemanticModelingEmptyState from "./SemanticModelingEmptyState";
import { hasSemanticModelingMenuAccess } from "./semanticAccess";

type LoadState<T> = { state: "loading" } | { state: "loaded"; value: T } | { state: "error"; error: unknown };

function safeArray<T>(value: T[] | undefined | null): T[] {
	return Array.isArray(value) ? value : [];
}

export default function SemanticExplorePage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const menus = useMenuStore((state) => state.menus);
	const canModel = useMemo(() => hasSemanticModelingMenuAccess(menus), [menus]);
	const [metaState, setMetaState] = useState<LoadState<SemanticMetaResponse>>({ state: "loading" });
	const [graphState, setGraphState] = useState<LoadState<SemanticGraphResponse>>({ state: "loading" });
	const [detailRow, setDetailRow] = useState<SemanticModelMeta | null>(null);

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.getSemanticMeta({ exposedToModeler: true })
			.then((value) => {
				if (cancelled) return;
				setMetaState({ state: "loaded", value });
			})
			.catch((error) => {
				if (cancelled) return;
				setMetaState({ state: "error", error });
			});
		analyticsApi
			.getSemanticGraph()
			.then((value) => {
				if (cancelled) return;
				setGraphState({ state: "loaded", value });
			})
			.catch((error) => {
				if (cancelled) return;
				setGraphState({ state: "error", error });
			});
		return () => {
			cancelled = true;
		};
	}, []);

	const models = metaState.state === "loaded" ? safeArray(metaState.value.models) : [];
	const modelBaseColumns: ColumnsType<SemanticModelMeta> = [
		{
			title: "主题模型",
			dataIndex: "label",
			sorter: (a, b) => (a.label || "").localeCompare(b.label || ""),
			key: "label",
			render: (_value, record) => (
				<div className="flex flex-col">
					<Link to={`/bi/card/new?base=${encodeURIComponent(String(record.id ?? ""))}`}>
						{record.label || record.id || "-"}
					</Link>
					<span className="text-xs text-secondary">
						{record.schema_name || "public"}.{record.table_name || record.id || "-"}
					</span>
				</div>
			),
		},
		{
			title: "主题域",
			dataIndex: "subject_area",
			key: "subject_area",
			width: 140,
			render: (value) => value || <span className="text-secondary">未分域</span>,
		},
		{
			title: "密级",
			dataIndex: "security_level",
			key: "security_level",
			width: 120,
			render: (value) => (
				<Tag color={value === "CONFIDENTIAL" ? "red" : value === "SECRET" ? "orange" : "blue"}>
					{String(value || "INTERNAL")}
				</Tag>
			),
		},
		{
			title: "指标 / 维度",
			key: "stats",
			width: 140,
			render: (_value, record) => `${safeArray(record.metrics).length} / ${safeArray(record.dimensions).length}`,
		},
		{
			title: "操作",
			dataIndex: "actions",
			key: "actions",
			width: 280,
			fixed: "right",
			render: (_value, record) => (
				<Space>
					{canModel && (
						<>
							<Link to={`/bi/card/new?base=${encodeURIComponent(String(record.id ?? ""))}`}>
								<Button type="primary" size="small">
									新建卡片
								</Button>
							</Link>
							<Link to={`/bi/virtual-datasets/new?base=${encodeURIComponent(String(record.id ?? ""))}`}>
								<Button size="small">新建 VDS</Button>
							</Link>
						</>
					)}
				</Space>
			),
		},
	];

	const modelColumns = useMemo(
		() => appendDetailAction(modelBaseColumns, (row) => setDetailRow(row)),
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[canModel],
	);

	return (
		<div className="space-y-4">
			<PageHeader
				title="语义探索"
				actions={
					<Space>
						<Link to="/bi/virtual-datasets">
							<Button>虚拟数据集</Button>
						</Link>
						{canModel && (
							<Link to="/bi/card/new">
								<Button type="primary">
									新建语义卡片
								</Button>
							</Link>
						)}
					</Space>
				}
			/>

			{metaState.state === "loading" && (
				<div className="loading-container" style={{ padding: 48 }}>
					<Spin size="large" />
				</div>
			)}
			{metaState.state === "error" && <ErrorNotice locale={locale} error={metaState.error} />}
			{graphState.state === "error" && <ErrorNotice locale={locale} error={graphState.error} />}

			{metaState.state === "loaded" && (
				<>
					<div style={{ display: "grid", gridTemplateColumns: "repeat(3, minmax(0, 1fr))", gap: 16 }}>
						<Card>
							<Typography.Text type="secondary">可建模主题模型</Typography.Text>
							<div style={{ fontSize: 28, fontWeight: 600 }}>{models.length}</div>
						</Card>
						<Card>
							<Typography.Text type="secondary">Join 边数量</Typography.Text>
							<div style={{ fontSize: 28, fontWeight: 600 }}>
								{graphState.state === "loaded" ? safeArray(graphState.value.edges).length : "-"}
							</div>
						</Card>
						<Card>
							<Typography.Text type="secondary">主题域</Typography.Text>
							<div style={{ fontSize: 28, fontWeight: 600 }}>
								{new Set(models.map((item) => item.subject_area || "未分域")).size}
							</div>
						</Card>
					</div>

					<Card title="已开放的语义模型">
						{models.length === 0 ? (
							<SemanticModelingEmptyState title="当前还没有开放给分析师的语义模型" compact />
						) : (
							<CompactTable<SemanticModelMeta>
								rowKey={(record) => String(record.id ?? Math.random())}
								columns={modelColumns}
								dataSource={models}
								pagination={models.length > 10 ? undefined : false}
							/>
						)}
					</Card>
				</>
			)}
			<RecordDetailDrawer<SemanticModelMeta>
				open={detailRow !== null}
				onClose={() => setDetailRow(null)}
				record={detailRow}
				columns={modelBaseColumns}
				title="语义模型详情"
			/>
		</div>
	);
}
