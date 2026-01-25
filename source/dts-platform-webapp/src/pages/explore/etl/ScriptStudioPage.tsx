import { Badge, Button, Card, Input, Segmented, Space, Table, Tabs, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useState } from "react";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";

type ScriptEntry = {
	id: string;
	name: string;
	type: "PYTHON" | "SPARK";
	owner?: string;
	status?: "DRAFT" | "READY" | "RUNNING";
	updatedAt?: string;
	version?: string;
	description?: string;
	tags?: string[];
};

const scripts: ScriptEntry[] = [
];

const templateCards: { title: string; desc: string; tags: string[] }[] = [];
const runHistory: { id: string; name: string; status: string; time: string; duration: string }[] = [];

export default function ScriptStudioPage() {
	const [activeType, setActiveType] = useState<"ALL" | "PYTHON" | "SPARK">("ALL");
	const [selectedId, setSelectedId] = useState<string | null>(null);

	const filteredScripts = useMemo(() => {
		if (activeType === "ALL") return scripts;
		return scripts.filter((item) => item.type === activeType);
	}, [activeType]);

	const summary = {
		published: scripts.filter((item) => item.status === "READY").length,
		draft: scripts.filter((item) => item.status === "DRAFT" || !item.status).length,
		running: scripts.filter((item) => item.status === "RUNNING").length,
		failed: runHistory.filter((item) => item.status === "FAILED").length,
	};

	const selected = useMemo(
		() => filteredScripts.find((item) => item.id === selectedId) || null,
		[filteredScripts, selectedId],
	);

	const columns: ColumnsType<ScriptEntry> = [
		{
			title: "脚本名称",
			dataIndex: "name",
			key: "name",
			width: 220,
			render: (_, row) => (
				<div className="space-y-1">
					<div className="font-medium text-text-primary">{row.name}</div>
					<div className="text-xs text-text-tertiary">{row.description || "暂无说明"}</div>
				</div>
			),
		},
		{
			title: "类型",
			dataIndex: "type",
			key: "type",
			width: 120,
			render: (v) => <Tag>{v}</Tag>,
		},
		{
			title: "负责人",
			dataIndex: "owner",
			key: "owner",
			width: 140,
			render: (v) => v || "-",
		},
		{
			title: "状态",
			dataIndex: "status",
			key: "status",
			width: 120,
			render: (v) => {
				const label = v || "DRAFT";
				const color = label === "READY" ? "green" : label === "RUNNING" ? "blue" : "gold";
				return <Tag color={color}>{label}</Tag>;
			},
		},
		{
			title: "更新时间",
			dataIndex: "updatedAt",
			key: "updatedAt",
			width: 180,
			render: (v) => v || "-",
		},
		{
			title: "",
			key: "actions",
			width: 120,
			render: () => (
				<Space size={8}>
					<Button size="small" type="link">
						打开
					</Button>
					<Button size="small" type="link">
						运行
					</Button>
				</Space>
			),
		},
	];

	const runColumns = [
		{ title: "任务", dataIndex: "name", key: "name" },
		{
			title: "状态",
			dataIndex: "status",
			key: "status",
			render: (value: string) => {
				const color = value === "SUCCESS" ? "green" : value === "FAILED" ? "red" : "blue";
				return <Tag color={color}>{value}</Tag>;
			},
		},
		{ title: "触发时间", dataIndex: "time", key: "time" },
		{ title: "耗时", dataIndex: "duration", key: "duration" },
	];

	return (
		<div className="space-y-5">
			<PageHeader
				title="数据开发中心 · 脚本开发（Python/Spark）"
				description="类 Notebook 的脚本管理体验，支持 Python/Spark 任务开发、版本与运行追踪。"
				actions={
					<Space>
						<Button>导入仓库</Button>
						<Button type="primary">新建脚本</Button>
					</Space>
				}
			/>

			<div className="grid gap-4 lg:grid-cols-4">
				<Card className="border border-slate-200/80">
					<div className="text-xs text-text-tertiary">已发布</div>
					<div className="mt-2 text-2xl font-semibold">{summary.published}</div>
					<div className="mt-2 text-xs text-text-secondary">今日更新 0</div>
				</Card>
				<Card className="border border-slate-200/80">
					<div className="text-xs text-text-tertiary">草稿</div>
					<div className="mt-2 text-2xl font-semibold">{summary.draft}</div>
					<div className="mt-2 text-xs text-text-secondary">待补充运行配置</div>
				</Card>
				<Card className="border border-slate-200/80">
					<div className="text-xs text-text-tertiary">运行中</div>
					<div className="mt-2 text-2xl font-semibold">{summary.running}</div>
					<div className="mt-2 text-xs text-text-secondary">暂无运行任务</div>
				</Card>
				<Card className="border border-slate-200/80">
					<div className="text-xs text-text-tertiary">失败</div>
					<div className="mt-2 text-2xl font-semibold">{summary.failed}</div>
					<div className="mt-2 text-xs text-text-secondary">最近 24h</div>
				</Card>
			</div>

			<Tabs
				defaultActiveKey="scripts"
				items={[
					{
						key: "scripts",
						label: "我的脚本",
						children: (
							<div className="space-y-4">
								<Card
									title="脚本清单"
									extra={
										<Space>
											<Segmented
												options={[
													{ label: "全部", value: "ALL" },
													{ label: "Python", value: "PYTHON" },
													{ label: "Spark", value: "SPARK" },
												]}
												value={activeType}
												onChange={(value) => setActiveType(value as "ALL" | "PYTHON" | "SPARK")}
											/>
											<Input.Search placeholder="搜索脚本" style={{ width: 200 }} allowClear />
										</Space>
									}
								>
									{filteredScripts.length ? (
										<Table
											columns={columns}
											dataSource={filteredScripts}
											pagination={false}
											rowKey="id"
											onRow={(record) => ({
												onClick: () => setSelectedId(record.id),
											})}
										/>
									) : (
										<EmptyState title="暂无脚本" description="新建脚本后即可进行版本管理与运行。" />
									)}
								</Card>

								<Card
									title="脚本预览"
									extra={
										<Space>
											<Button size="small">打开编辑器</Button>
											<Button size="small" type="primary">
												运行
											</Button>
										</Space>
									}
								>
									{selected ? (
										<div className="space-y-4">
											<div className="flex items-start justify-between">
												<div>
													<div className="text-lg font-semibold">{selected.name}</div>
													<div className="text-xs text-text-secondary">{selected.description}</div>
													<div className="mt-2 flex flex-wrap gap-2">
														<Tag>{selected.type}</Tag>
														<Tag color="blue">{selected.version}</Tag>
														{selected.tags?.map((tag) => (
															<Tag key={tag}>{tag}</Tag>
														))}
													</div>
												</div>
												<Badge status={selected.status === "RUNNING" ? "processing" : "default"} text={selected.status} />
											</div>
											<div className="rounded-lg bg-slate-950/90 p-4 text-xs text-slate-100">
												<pre className="whitespace-pre-wrap leading-5">
{`# ${selected.name}
def transform(df):
    return df`}
												</pre>
											</div>
											<div className="space-y-2 text-sm">
												<div className="flex items-center justify-between">
													<span className="text-xs text-text-tertiary">负责人</span>
													<span>{selected.owner || "-"}</span>
												</div>
												<div className="flex items-center justify-between">
													<span className="text-xs text-text-tertiary">最近更新</span>
													<span>{selected.updatedAt || "-"}</span>
												</div>
												<div className="flex items-center justify-between">
													<span className="text-xs text-text-tertiary">运行环境</span>
													<span>--</span>
												</div>
											</div>
										</div>
									) : (
										<EmptyState title="请选择脚本" description="从左侧列表选择脚本以查看详情。" compact />
									)}
								</Card>

								<Card title="运行历史">
									{runHistory.length ? (
										<Table columns={runColumns} dataSource={runHistory} pagination={false} size="small" rowKey="id" />
									) : (
										<EmptyState title="暂无运行记录" description="运行脚本后将在此处展示。" compact />
									)}
								</Card>

								<Card title="运行资源">
									<EmptyState title="暂无资源配置" description="请先配置脚本运行资源。" compact />
								</Card>
								<Card title="依赖与参数">
									<EmptyState title="暂无依赖配置" description="配置依赖与运行参数后展示。" compact />
								</Card>
								<Card title="协作状态">
									<EmptyState title="暂无协作记录" description="提交代码或评审后展示。" compact />
								</Card>
							</div>
						),
					},
					{
						key: "templates",
						label: "模板库",
						children: (
							<div className="space-y-4">
								{templateCards.length ? (
									templateCards.map((card) => (
										<Card key={card.title} className="border border-slate-200/80">
											<div className="text-base font-semibold">{card.title}</div>
											<div className="mt-2 text-sm text-text-secondary">{card.desc}</div>
											<div className="mt-4 flex flex-wrap gap-2">
												{card.tags.map((tag) => (
													<Tag key={tag}>{tag}</Tag>
												))}
											</div>
											<Button className="mt-4" type="primary" ghost>
												使用模板
											</Button>
										</Card>
									))
								) : (
									<EmptyState title="暂无模板" description="请先新建或同步脚本模板。" />
								)}
							</div>
						),
					},
					{
						key: "runs",
						label: "运行记录",
						children: (
							<Card>
								{runHistory.length ? (
									<Table columns={runColumns} dataSource={runHistory} pagination={false} rowKey="id" />
								) : (
									<EmptyState title="暂无运行记录" description="运行脚本后将在此处展示。" />
								)}
							</Card>
						),
					},
				]}
			/>
		</div>
	);
}
