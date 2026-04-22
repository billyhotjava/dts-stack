import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { Button, Card, Popconfirm, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { PlusOutlined, DeploymentUnitOutlined, EditOutlined } from "@ant-design/icons";
import { toast } from "sonner";
import { PageHeader } from "@/components/page-header";
import { useUserRoles } from "@/store/userStore";
import { analyticsApi, type SemanticPromoteResult, type SemanticVirtualDataset } from "../../api/analyticsApi";
import { ErrorNotice } from "../../components/ErrorNotice";
import { getEffectiveLocale, type Locale } from "../../i18n";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

function formatDateTime(value?: string): string {
	if (!value) return "-";
	const date = new Date(value);
	return Number.isNaN(date.getTime()) ? value : `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

function baseModelOf(item: SemanticVirtualDataset): string {
	return String(item.base_model || ((item.state as Record<string, unknown> | undefined)?.base ?? "-"));
}

export default function SemanticVirtualDatasetsPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const roles = useUserRoles();
	const roleSet = new Set((roles || []).map((role) => String(role || "").trim().toUpperCase()));
	const canPromote = roleSet.has("BI_DATA_ENGINEER") || roleSet.has("OP_ADMIN");
	const [state, setState] = useState<LoadState<SemanticVirtualDataset[]>>({ state: "loading" });
	const [promoteState, setPromoteState] = useState<LoadState<SemanticPromoteResult> | null>(null);

	const load = useCallback(() => {
		setState({ state: "loading" });
		analyticsApi
			.listSemanticVirtualDatasets({ owner: "me" })
			.then((value) => setState({ state: "loaded", value }))
			.catch((error) => setState({ state: "error", error }));
	}, []);

	useEffect(() => {
		load();
	}, [load]);

	const columns: ColumnsType<SemanticVirtualDataset> = [
		{
			title: "名称",
			dataIndex: "name",
			key: "name",
			render: (_value, record) => (
				<div className="flex flex-col">
					<Link to={`/bi/virtual-datasets/${encodeURIComponent(String(record.id ?? ""))}`}>{record.name || `VDS #${record.id}`}</Link>
					<span className="text-xs text-secondary">{record.description || "未填写描述"}</span>
				</div>
			),
		},
		{
			title: "基础模型",
			key: "base",
			width: 180,
			render: (_value, record) => baseModelOf(record),
		},
		{
			title: "更新时间",
			dataIndex: "updated_at",
			key: "updated_at",
			width: 180,
			render: (value) => formatDateTime(value),
		},
		{
			title: "状态",
			key: "status",
			width: 120,
			render: (_value, record) => <Tag color="blue">{record.archived ? "archived" : "shared"}</Tag>,
		},
		{
			title: "操作",
			key: "actions",
			width: 260,
			render: (_value, record) => (
				<Space>
					<Link to={`/bi/virtual-datasets/${encodeURIComponent(String(record.id ?? ""))}`}>
						<Button size="small" icon={<EditOutlined />}>编辑</Button>
					</Link>
					<Link to={`/bi/card/new?vds=${encodeURIComponent(String(record.id ?? ""))}`}>
						<Button size="small">生成卡片</Button>
					</Link>
					{canPromote && record.id != null && (
						<Popconfirm
							title="生成提升到 dbt 的草案？"
							onConfirm={async () => {
								try {
									setPromoteState({ state: "loading" });
									const value = await analyticsApi.promoteSemanticVirtualDataset(record.id as number);
									setPromoteState({ state: "loaded", value });
									toast.success("已生成提升草案");
								} catch (error) {
									setPromoteState({ state: "error", error });
									toast.error(error instanceof Error ? error.message : "提升失败");
								}
							}}
						>
							<Button size="small" icon={<DeploymentUnitOutlined />}>提升</Button>
						</Popconfirm>
					)}
				</Space>
			),
		},
	];

	return (
		<div className="space-y-4">
			<PageHeader
				title="虚拟数据集"
				actions={
					<Link to="/bi/virtual-datasets/new">
						<Button type="primary" icon={<PlusOutlined />}>新建虚拟数据集</Button>
					</Link>
				}
			/>

			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
			{promoteState?.state === "error" && <ErrorNotice locale={locale} error={promoteState.error} />}

			<Card title="我的虚拟数据集">
				<Table
					rowKey={(record) => String(record.id ?? Math.random())}
					loading={state.state === "loading"}
					columns={columns}
					dataSource={state.state === "loaded" ? state.value : []}
					pagination={state.state === "loaded" && state.value.length > 10 ? { pageSize: 10 } : false}
				/>
			</Card>

			{promoteState?.state === "loaded" && (
				<Card title="最新提升草案">
					<div className="mb-2 text-sm text-secondary">模型名</div>
					<div className="mb-4 font-medium">{promoteState.value.model_name || "-"}</div>
					<div className="mb-2 text-sm text-secondary">SQL</div>
					<pre style={{ whiteSpace: "pre-wrap", margin: 0 }}>{String(promoteState.value.sql ?? "")}</pre>
				</Card>
			)}
		</div>
	);
}
