import { Alert, Button, Drawer, Form, Input, Select, Space, Switch } from "antd";
import type { ProjectSpace } from "../sqlModeling.types";
import type { InfraDataSource } from "@/api/services/dataSourcesService";

export type ModelEditDrawerProps = {
	open: boolean;
	onClose: () => void;
	onSubmit: () => void;
	submitting: boolean;
	isEditing: boolean;
	spaces: ProjectSpace[];
	dataSources: InfraDataSource[];
	layers: { layer: string; name: string; description: string }[];
	form: ReturnType<typeof Form.useForm>[0];
	advancedImplementation?: {
		modelSpecId: string;
		planId: string;
		implementationRevision: string;
		ownership?: string;
		blocked?: boolean;
		onRecover: () => void;
	};
};

export default function ModelEditDrawer({
	open,
	onClose,
	onSubmit,
	submitting,
	isEditing,
	spaces,
	dataSources,
	layers,
	form,
	advancedImplementation,
}: ModelEditDrawerProps) {
	const ordinaryOverwriteBlocked = Boolean(advancedImplementation?.blocked);
	return (
		<Drawer
			open={open}
			title={isEditing ? "编辑模型" : "新建模型"}
			width={720}
			onClose={onClose}
			footer={
				<Space>
					<Button onClick={onClose}>取消</Button>
					<Button type="primary" onClick={onSubmit} loading={submitting} disabled={ordinaryOverwriteBlocked}>
						保存
					</Button>
				</Space>
			}
		>
			{advancedImplementation ? (
				<Alert
					className="mb-4"
					type={ordinaryOverwriteBlocked ? "warning" : "info"}
					showIcon
					data-testid="dbt-managed-artifact-non-overwrite"
					message={ordinaryOverwriteBlocked ? "当前实现版本已有证据，不能原地覆盖" : "正在编辑 ModelSpec 的高级实现"}
					description={
						ordinaryOverwriteBlocked
							? `ModelSpec=${advancedImplementation.modelSpecId} · implementationRevision=${advancedImplementation.implementationRevision || "缺失"}。请先在数据实现阶段创建新的 implementation revision。`
							: `SQL 工作区已绑定 ModelSpec=${advancedImplementation.modelSpecId} · implementationRevision=${advancedImplementation.implementationRevision || "缺失"}；首次构建前可以保存，构建后将自动冻结。`
					}
					action={<Button size="small" onClick={advancedImplementation.onRecover}>返回数据实现</Button>}
				/>
			) : null}
			<Form layout="vertical" form={form} disabled={submitting || ordinaryOverwriteBlocked}>
				{/* 基本信息 */}
				<div className="mb-4 pb-2 border-b border-border">
					<div className="text-sm font-semibold text-foreground">基本信息</div>
				</div>
				<div className="grid gap-4 md:grid-cols-2">
					<Form.Item name="planId" label="项目空间" rules={[{ required: true, message: "请选择项目空间" }]}>
						<Select
							placeholder="选择项目空间"
							options={spaces.map((space) => ({ label: space.name || "未命名", value: space.id }))}
						/>
					</Form.Item>
					<Form.Item
						name="layer"
						label="数仓分层"
						rules={[{ required: true, message: "请选择分层" }]}
						tooltip="选择模型所在的数仓层级，系统会自动添加对应标签"
					>
						<Select
							placeholder="选择分层"
							options={layers.map((l) => ({
								label: `${l.layer} - ${l.description || l.name}`,
								value: l.layer,
							}))}
						/>
					</Form.Item>
				</div>
				<div className="grid gap-4 md:grid-cols-2">
					<Form.Item
						name="name"
						label="模型名称"
						rules={[{ required: true, message: "请输入模型名称" }]}
						tooltip="建议以分层前缀开头，如 dwd_sales_order"
					>
						<Input placeholder="例如 dwd_sales_order" />
					</Form.Item>
					<Form.Item
						name="sourceDataSourceId"
						label="来源数据源"
						rules={[{ required: true, message: "请选择来源数据源" }]}
						tooltip="选择模型绑定的 Platform 数据源；默认湖仓会优先展示，但可选择其他有权限的湖仓"
					>
						<Select
							placeholder="选择来源数据源"
							showSearch
							optionFilterProp="label"
							options={dataSources.map((ds) => ({
								label: `${ds?.name || ds?.id}${ds?.defaultSource ? "（默认湖仓）" : ""}`,
								value: ds?.id,
							}))}
						/>
					</Form.Item>
				</div>
				<Form.Item name="description" label="模型说明">
					<Input.TextArea rows={2} placeholder="描述模型的业务含义和用途" />
				</Form.Item>

				{/* dbt 配置 */}
				<div className="mb-4 mt-6 pb-2 border-b border-border">
					<div className="text-sm font-semibold text-foreground">dbt 配置</div>
					<div className="text-xs text-muted-foreground mt-1">
						以下配置会自动生成 dbt 的 config 块，您无需手动编写
					</div>
				</div>
				<div className="grid gap-4 md:grid-cols-3">
					<Form.Item
						name="materialized"
						label="物化方式"
						tooltip="table: 全量重建表；view: 视图；incremental: 增量更新"
					>
						<Select
							placeholder="选择物化方式"
							options={[
								{ label: "table（推荐）", value: "table" },
								{ label: "view", value: "view" },
								{ label: "incremental", value: "incremental" },
								{ label: "ephemeral（仅 STG 临时节点，无物理表）", value: "ephemeral" },
							]}
						/>
					</Form.Item>
					<Form.Item
						name="alias"
						label="物理表别名"
						tooltip="如果物理表名需要与模型名不同，在此指定"
					>
						<Input placeholder="可选，默认使用模型名" />
					</Form.Item>
					<Form.Item
						name="schemaName"
						label="目标 Schema"
						tooltip="模型输出的目标 Schema，留空使用默认配置"
					>
						<Input placeholder="留空使用默认" />
					</Form.Item>
				</div>
				<Form.Item
					name="tags"
					label="标签"
					tooltip="用于调度选择器和分组管理，系统会自动添加来源系统和分层标签"
				>
					<Input placeholder="多个标签用逗号分隔，如: daily,core" />
				</Form.Item>
				<Form.Item
					name="semanticContract"
					label="语义契约 (JSON)"
					tooltip="可选：定义 metrics/dimensions 元信息，发布与看板绑定会展示契约版本"
				>
					<Input.TextArea
						rows={4}
						className="font-mono text-sm"
						placeholder='{"metrics":[{"code":"order_cnt","name":"订单数"}],"dimensions":[{"code":"dept","name":"部门"}]}'
					/>
				</Form.Item>

				{/* SQL 编辑 */}
				<div className="mb-4 mt-6 pb-2 border-b border-border">
					<div className="text-sm font-semibold text-foreground">SQL 定义</div>
					<div className="text-xs text-muted-foreground mt-1">
						只需编写 SELECT 语句，使用 {"{{ source('schema', 'table') }}"} 引用源表，使用 {"{{ ref('model') }}"} 引用其他模型
					</div>
				</div>
				<Form.Item name="sql" rules={[{ required: true, message: "请输入 SQL" }]}>
					<Input.TextArea
						rows={12}
						className="font-mono text-sm"
						placeholder={`SELECT
  id,
  name,
  created_at
FROM {{ source('public', 'ods_your_table') }}
WHERE status = 'active'`}
					/>
				</Form.Item>

				{/* 状态管理 */}
				<div className="mb-4 mt-6 pb-2 border-b border-border">
					<div className="text-sm font-semibold text-foreground">状态管理</div>
				</div>
				<div className="grid gap-4 md:grid-cols-2">
					<Form.Item name="status" label="模型状态">
						<Select
							placeholder="选择状态"
							options={[
								{ label: "草稿 - 开发中", value: "DRAFT" },
								{ label: "就绪 - 可上线", value: "READY" },
								{ label: "暂停 - 暂停调度", value: "PAUSED" },
							]}
						/>
					</Form.Item>
					<Form.Item name="enabled" label="启用调度" valuePropName="checked">
						<Switch checkedChildren="启用" unCheckedChildren="禁用" />
					</Form.Item>
				</div>
			</Form>
		</Drawer>
	);
}
