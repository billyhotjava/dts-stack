import { Alert, Button, Divider, Drawer, Empty, Skeleton, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { ReloadOutlined } from "@ant-design/icons";
import { formatDateTime, normalizeText } from "@/utils/textUtils";
import { buildDiagnosticsTitle, formatDiagnosticsRowCount, resolveDiagnosticsStatus } from "../dbtModelDiagnostics.helpers";
import type { DbtModelDependencyDiagnostic, DbtModelDiagnostics, SqlModel } from "../sqlModeling.types";

const { Paragraph, Text } = Typography;

type DbtModelDiagnosticsDrawerProps = {
	open: boolean;
	onClose: () => void;
	onReload: () => void;
	onOpenPreview: () => void;
	onOpenLogs: () => void;
	loading: boolean;
	model: SqlModel | null;
	diagnostics: DbtModelDiagnostics | null;
	errorMessage?: string | null;
};

const upstreamColumns: ColumnsType<DbtModelDependencyDiagnostic> = [
	{
		title: "上游",
		key: "name",
		width: 220,
		render: (_value, row) => (
			<div className="min-w-0">
				<div className="truncate font-medium">{normalizeText(row.dependency?.name) || "-"}</div>
				<div className="truncate text-xs text-muted-foreground">{normalizeText(row.dependency?.resourceType) || "-"}</div>
			</div>
		),
	},
	{
		title: "Relation",
		key: "relation",
		ellipsis: true,
		render: (_value, row) => normalizeText(row.stats?.relationName) || normalizeText(row.dependency?.relationName) || "-",
	},
	{
		title: "行数",
		key: "rowCount",
		width: 100,
		render: (_value, row) => formatDiagnosticsRowCount(row.stats?.rowCount),
	},
	{
		title: "状态",
		key: "exists",
		width: 90,
		render: (_value, row) => {
			if (row.stats?.exists === false) return <Tag color="red">缺失</Tag>;
			if (row.stats?.error) return <Tag color="orange">异常</Tag>;
			return <Tag color="green">可用</Tag>;
		},
	},
];

export default function DbtModelDiagnosticsDrawer({
	open,
	onClose,
	onReload,
	onOpenPreview,
	onOpenLogs,
	loading,
	model,
	diagnostics,
	errorMessage,
}: DbtModelDiagnosticsDrawerProps) {
	const status = resolveDiagnosticsStatus(diagnostics);
	const current = diagnostics?.current;
	const findings = diagnostics?.findings || [];
	const recommendedQueries = diagnostics?.recommendedQueries || [];
	const upstreams = diagnostics?.upstreams || [];

	return (
		<Drawer
			open={open}
			width={760}
			title={buildDiagnosticsTitle(model?.name || diagnostics?.model)}
			onClose={onClose}
			extra={
				<Button size="small" icon={<ReloadOutlined />} onClick={onReload} loading={loading}>
					刷新
				</Button>
			}
		>
			{loading && !diagnostics ? (
				<Skeleton active paragraph={{ rows: 10 }} />
			) : null}

			{errorMessage ? (
				<Alert
					type="error"
					showIcon
					message="诊断加载失败"
					description={errorMessage}
					className="mb-4"
				/>
			) : null}

			{!loading && !diagnostics && !errorMessage ? <Empty description="暂无诊断结果" /> : null}

			{diagnostics ? (
				<div className="space-y-4">
					<Alert
						type={status === "error" ? "error" : status === "warning" ? "warning" : "success"}
						showIcon
						message={status === "error" ? "模型存在明显断链信号" : status === "warning" ? "模型建议进一步排查" : "模型未发现明显断链"}
						description={
							<div className="space-y-1">
								<div>当前 relation: {normalizeText(current?.relationName) || normalizeText(diagnostics.relationName) || "-"}</div>
								<div>当前行数: {formatDiagnosticsRowCount(current?.rowCount)}</div>
							</div>
						}
					/>

					<Space wrap>
						<Button size="small" onClick={onOpenPreview}>
							查看预览
						</Button>
						<Button size="small" onClick={onOpenLogs}>
							查看日志
						</Button>
					</Space>

					<div className="grid gap-3 md:grid-cols-2">
						<div className="rounded-lg border border-border p-3">
							<div className="mb-2 text-xs font-semibold uppercase text-muted-foreground">当前模型</div>
							<div className="space-y-1 text-sm">
								<div>模型名: {normalizeText(diagnostics.model) || normalizeText(model?.name) || "-"}</div>
								<div>资源类型: {normalizeText(diagnostics.resourceType) || "-"}</div>
								<div>物理 relation: {normalizeText(current?.relationName) || normalizeText(diagnostics.relationName) || "-"}</div>
								<div>是否存在: {current?.exists === false ? "否" : "是"}</div>
								<div>行数: {formatDiagnosticsRowCount(current?.rowCount)}</div>
							</div>
						</div>

						<div className="rounded-lg border border-border p-3">
							<div className="mb-2 text-xs font-semibold uppercase text-muted-foreground">最近运行</div>
							<div className="space-y-1 text-sm">
								<div>
									dbt:{" "}
									{diagnostics.runtime?.dbtRun?.present ? (
										<Tag color={normalizeText(diagnostics.runtime?.dbtRun?.status) === "SUCCESS" ? "green" : "gold"}>
											{normalizeText(diagnostics.runtime?.dbtRun?.status) || "UNKNOWN"}
										</Tag>
									) : (
										<span>-</span>
									)}
								</div>
								<div>命令: {normalizeText(diagnostics.runtime?.dbtRun?.command) || "-"}</div>
								<div>生成时间: {formatDateTime(diagnostics.runtime?.dbtRun?.generatedAt) || "-"}</div>
								<div>
									Airflow:{" "}
									{diagnostics.runtime?.airflowRun?.present ? (
										<Tag color={normalizeText(diagnostics.runtime?.airflowRun?.state) === "failed" ? "red" : "blue"}>
											{normalizeText(diagnostics.runtime?.airflowRun?.state) || "UNKNOWN"}
										</Tag>
									) : (
										<span>-</span>
									)}
								</div>
								<div>DAG Run: {normalizeText(diagnostics.runtime?.airflowRun?.dagRunId) || "-"}</div>
							</div>
						</div>
					</div>

					<div>
						<div className="mb-2 text-sm font-semibold">断链判断</div>
						{findings.length ? (
							<div className="space-y-2">
								{findings.map((item, index) => (
									<Alert key={`${item}-${index}`} type="warning" showIcon message={item} />
								))}
							</div>
						) : (
							<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无断链提示" />
						)}
					</div>

					<div>
						<div className="mb-2 text-sm font-semibold">上游依赖</div>
						<Table
							rowKey={(row, index) => `${row.dependency?.uniqueId || row.dependency?.name || "upstream"}-${index}`}
							size="small"
							pagination={false}
							columns={upstreamColumns}
							dataSource={upstreams}
							scroll={{ y: 220 }}
							locale={{ emptyText: "暂无上游依赖" }}
						/>
					</div>

					<div>
						<div className="mb-2 text-sm font-semibold">推荐排查 SQL</div>
						{recommendedQueries.length ? (
							<div className="space-y-3">
								{recommendedQueries.map((query, index) => (
									<div key={`${query}-${index}`} className="rounded-lg border border-border bg-muted/20 p-3">
										<Space direction="vertical" size={4} className="w-full">
											<Text type="secondary">SQL {index + 1}</Text>
											<Paragraph
												className="mb-0 whitespace-pre-wrap rounded bg-background px-3 py-2 font-mono text-xs"
												copyable={{ text: query }}
											>
												{query}
											</Paragraph>
										</Space>
									</div>
								))}
							</div>
						) : (
							<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无推荐 SQL" />
						)}
					</div>

					{diagnostics.runtime?.airflowRun?.logSnippet ? (
						<>
							<Divider />
							<div>
								<div className="mb-2 text-sm font-semibold">最近 Airflow 日志摘要</div>
								<Paragraph className="mb-0 whitespace-pre-wrap rounded border border-border bg-background px-3 py-2 font-mono text-xs">
									{diagnostics.runtime.airflowRun.logSnippet}
								</Paragraph>
							</div>
						</>
					) : null}
				</div>
			) : null}
		</Drawer>
	);
}
