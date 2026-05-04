import { Button, Checkbox, Form, Input, Modal, Select, Space, Switch, Tag } from "antd";
import { CompactTable } from "@/components/table";
import type { SqlModelGovernancePreviewItem, ProjectSpace } from "../sqlModeling.types";

const layerTag = (layer?: string) => {
	if (!layer) return <Tag>未分层</Tag>;
	const color =
		layer === "ODS" ? "blue"
		: layer === "STG" ? "gold"
		: layer === "DWD" ? "cyan"
		: layer === "DWS" ? "purple"
		: layer === "ADS" ? "geekblue"
		: "default";
	return <Tag color={color}>{layer}</Tag>;
};

const governanceRuleLabel = (rule?: string) => {
	if (rule === "duplicate-model") return "重复模型";
	if (rule === "preset:project-management-legacy-program") return "项目管理旧 program 模型";
	if (rule === "sql-keyword") return "SQL 关键字";
	if (rule === "name-pattern") return "模型名匹配";
	if (rule === "path-pattern") return "路径匹配";
	if (rule === "tag-match") return "标签匹配";
	return rule || "未知规则";
};

export type GovernanceModalProps = {
	open: boolean;
	onClose: () => void;
	onPreview: () => void;
	onExecute: () => void;
	previewLoading: boolean;
	executing: boolean;
	preview: SqlModelGovernancePreviewItem[];
	selection: string[];
	totalSelectionCount?: number;
	onSelectionChange: (keys: string[]) => void;
	onSelectAllPreview: () => void;
	onClearSelection: () => void;
	spaces: ProjectSpace[];
	form: ReturnType<typeof Form.useForm>[0];
};

export default function GovernanceModal({
	open,
	onClose,
	onPreview,
	onExecute,
	previewLoading,
	executing,
	preview,
	selection,
	totalSelectionCount,
	onSelectionChange,
	onSelectAllPreview,
	onClearSelection,
	spaces,
	form,
}: GovernanceModalProps) {
	return (
		<Modal
			open={open}
			title="模型治理"
			width={1100}
			onCancel={onClose}
			footer={
				<Space>
					<Button onClick={onClose}>关闭</Button>
					<Button onClick={onPreview} loading={previewLoading}>
						预览命中
					</Button>
					<Button
						type="primary"
						danger
						onClick={onExecute}
						loading={executing}
						disabled={!selection.length}
					>
						执行治理
					</Button>
				</Space>
			}
		>
			<Form layout="vertical" form={form}>
				<div className="grid gap-4 md:grid-cols-2">
					<Form.Item name="planId" label="项目空间" rules={[{ required: true, message: "请选择项目空间" }]}>
						<Select
							placeholder="选择项目空间"
							options={spaces.map((space) => ({ label: space.name || "未命名", value: space.id }))}
						/>
					</Form.Item>
					<Form.Item name="layer" label="分层">
						<Select
							allowClear
							placeholder="可选"
							options={[
								{ label: "ODS", value: "ODS" },
								{ label: "STG", value: "STG" },
								{ label: "DWD", value: "DWD" },
								{ label: "DWS", value: "DWS" },
								{ label: "ADS", value: "ADS" },
							]}
						/>
					</Form.Item>
				</div>
				<Form.Item
					name="ruleKeys"
					label="治理规则"
					rules={[{ required: true, message: "请至少选择一个治理规则" }]}
				>
					<Checkbox.Group
						options={[
							{ label: "重复模型", value: "duplicate-model" },
							{ label: "项目管理旧 program 模型", value: "preset:project-management-legacy-program" },
							{ label: "SQL 关键字", value: "sql-keyword" },
							{ label: "模型名匹配", value: "name-pattern" },
							{ label: "路径匹配", value: "path-pattern" },
							{ label: "标签匹配", value: "tag-match" },
						]}
					/>
				</Form.Item>
				<div className="grid gap-4 md:grid-cols-3">
					<Form.Item name="sqlKeywords" label="SQL 关键字">
						<Input.TextArea rows={2} placeholder={"program_id, program_name"} />
					</Form.Item>
					<Form.Item name="namePattern" label="模型名匹配">
						<Input placeholder="例如 major_project" />
					</Form.Item>
					<Form.Item name="modelPathPattern" label="路径匹配">
						<Input placeholder="例如 models/ads/prj1/" />
					</Form.Item>
				</div>
				<div className="grid gap-4 md:grid-cols-2">
					<Form.Item name="tag" label="标签匹配">
						<Input placeholder="例如 project-management" />
					</Form.Item>
					<Form.Item name="deleteFiles" label="同时清理无引用 dbt 文件" valuePropName="checked">
						<Switch checkedChildren="清理文件" unCheckedChildren="仅删记录" />
					</Form.Item>
				</div>
			</Form>
			<div className="mt-4">
				<div className="mb-3 flex items-center justify-between rounded-md border border-border bg-muted/20 px-3 py-2">
					<div className="text-xs text-muted-foreground">
						已选 {selection.length} 个命中模型
						{typeof totalSelectionCount === "number" && totalSelectionCount > selection.length
							? `（全局共 ${totalSelectionCount} 项）`
							: ""}
					</div>
					<Space size="small">
						<Button size="small" onClick={onSelectAllPreview} disabled={!preview.length}>
							全选命中
						</Button>
						<Button size="small" onClick={onClearSelection} disabled={!selection.length}>
							清空选择
						</Button>
					</Space>
				</div>
				<CompactTable<SqlModelGovernancePreviewItem>
					size="small"
					rowKey={(record, index) => record.modelId || record.modelPath || record.name || `record-${index}`}
					loading={previewLoading}
					dataSource={preview}
					rowSelection={{
						selectedRowKeys: selection,
						onChange: (keys) => onSelectionChange(keys.map((key) => String(key))),
					}}
					pagination={{ pageSize: 8, hideOnSinglePage: true }}
					columns={[
						{
							title: "模型",
							dataIndex: "name",
							render: (_, record) => (
								<div>
									<div className="font-medium">{record.name || "-"}</div>
									<div className="text-xs text-muted-foreground">{record.modelPath || "-"}</div>
								</div>
							),
						},
						{
							title: "分层",
							dataIndex: "layer",
							width: 90,
							render: (value) => layerTag(value),
						},
						{
							title: "状态",
							dataIndex: "status",
							width: 90,
							render: (value) => <Tag>{value || "-"}</Tag>,
						},
						{
							title: "命中规则",
							dataIndex: "ruleHits",
							render: (value: string[] | undefined) => (
								<Space wrap size={[4, 4]}>
									{(value || []).map((rule) => (
										<Tag key={rule}>{governanceRuleLabel(rule)}</Tag>
									))}
								</Space>
							),
						},
						{
							title: "影响",
							width: 180,
							render: (_, record) => (
								<div className="text-xs leading-6">
									<div>下游 ref: {record.downstreamRefCount || 0}</div>
									<div>数据集: {record.datasetBindingCount || 0}</div>
									<div>报表: {record.reportBindingCount || 0}</div>
								</div>
							),
						},
						{
							title: "建议动作",
							width: 140,
							render: (_, record) => (
								<Tag color={record.fileDeleteSafe ? "green" : "gold"}>
									{record.fileDeleteSafe ? "删记录+文件" : "仅删记录"}
								</Tag>
							),
						},
					]}
				/>
			</div>
		</Modal>
	);
}
