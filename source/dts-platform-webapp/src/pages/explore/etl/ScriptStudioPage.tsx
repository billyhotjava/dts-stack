import { Button, Card, Input, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";

type ScriptEntry = {
	id: string;
	name: string;
	type: "PYTHON" | "SPARK";
	owner?: string;
	status?: "DRAFT" | "READY" | "RUNNING";
	updatedAt?: string;
};

const columns: ColumnsType<ScriptEntry> = [
	{ title: "脚本名称", dataIndex: "name", key: "name", width: 220 },
	{ title: "类型", dataIndex: "type", key: "type", width: 120, render: (v) => <Tag>{v}</Tag> },
	{ title: "负责人", dataIndex: "owner", key: "owner", width: 140, render: (v) => v || "-" },
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
	{ title: "更新时间", dataIndex: "updatedAt", key: "updatedAt", width: 180, render: (v) => v || "-" },
];

export default function ScriptStudioPage() {
	return (
		<div className="space-y-4">
			<PageHeader
				title="数据开发中心 · 脚本开发（Python/Spark）"
				description="统一管理非 SQL 逻辑脚本，后续可挂接调度与运行资源。"
				actions={
					<Space>
						<Button>导入脚本</Button>
						<Button type="primary">新建脚本</Button>
					</Space>
				}
			/>

			<div className="grid gap-4 lg:grid-cols-3">
				<Card title="运行资源">
					<p className="text-sm text-gray-500">默认执行集群</p>
					<p className="mt-2 text-base font-semibold">Spark on K8s</p>
					<p className="mt-2 text-xs text-gray-400">资源与队列配置将在此统一管理。</p>
				</Card>
				<Card title="脚本仓库">
					<p className="text-sm text-gray-500">当前仓库</p>
					<p className="mt-2 text-base font-semibold">未绑定</p>
					<p className="mt-2 text-xs text-gray-400">绑定 Git 仓库后可同步脚本模板。</p>
				</Card>
				<Card title="调度入口">
					<p className="text-sm text-gray-500">脚本调度</p>
					<p className="mt-2 text-base font-semibold">尚未配置</p>
					<p className="mt-2 text-xs text-gray-400">在任务编排中设置定时与依赖。</p>
				</Card>
			</div>

			<Card title="脚本清单" extra={<Input.Search placeholder="搜索脚本" style={{ width: 240 }} allowClear />}>
				<EmptyState title="暂无脚本" description="新建脚本后即可进行版本管理与运行。" />
				<Table columns={columns} dataSource={[]} pagination={false} className="hidden" />
			</Card>
		</div>
	);
}
