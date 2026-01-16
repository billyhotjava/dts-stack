import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Table, Modal, Form, Input, Select, Switch, InputNumber, Space, Checkbox } from "antd";
import type { ColumnsType } from "antd/es/table";
import { adminApi } from "@/admin/api/adminApi";
import type { UpsertWorkflowTemplatePayload, WorkflowTemplateConfig, WorkflowStepConfig } from "@/admin/types";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Text } from "@/ui/typography";
import { toast } from "sonner";

type WorkflowTypeOption = { value: string; label: string };

const WORKFLOW_TYPES: WorkflowTypeOption[] = [
	{ value: "DATASET_DATA_ACCESS", label: "数据资产访问审批（查询/预览）" },
	{ value: "REPORT_ACCESS", label: "报表访问审批（预留）" },
	{ value: "SCREEN_ACCESS", label: "大屏访问审批（预留）" },
	{ value: "API_ACCESS", label: "API 调用审批（预留）" },
];

const OWNER_SCOPES = [
	{ value: "ANY", label: "通用" },
	{ value: "INST", label: "所级资产" },
	{ value: "DEPT", label: "部门资产" },
];

const CLASSIFICATION_LEVELS = [
	{ value: "公开", label: "公开" },
	{ value: "内部", label: "内部" },
	{ value: "秘密", label: "秘密" },
	{ value: "机密", label: "机密" },
];

const APPROVER_ROLES = [
	{ value: "ROLE_INST_LEADER", label: "所级领导（ROLE_INST_LEADER）" },
	{ value: "ROLE_DEPT_LEADER", label: "部门领导（ROLE_DEPT_LEADER）" },
];

function normalizeSteps(steps: WorkflowStepConfig[] | undefined): WorkflowStepConfig[] {
	const list = Array.isArray(steps) ? steps.filter(Boolean) : [];
	return list
		.filter((s) => Boolean(s?.approverRole?.trim()))
		.map((s, idx) => ({
			stepOrder: idx + 1,
			approverRole: String(s.approverRole).trim(),
			deptBinding: Boolean(s.deptBinding),
		}));
}

async function confirmDelete(name: string): Promise<boolean> {
	return await new Promise((resolve) => {
		Modal.confirm({
			title: "确认删除？",
			content: `将删除工作流配置「${name}」`,
			okText: "删除",
			okType: "danger",
			cancelText: "取消",
			onOk: () => resolve(true),
			onCancel: () => resolve(false),
		});
	});
}

export default function WorkflowConfigView() {
	const queryClient = useQueryClient();
	const [workflowType, setWorkflowType] = useState<string>(WORKFLOW_TYPES[0]?.value ?? "DATASET_DATA_ACCESS");
	const [enabledOnly, setEnabledOnly] = useState(false);

	const [modalOpen, setModalOpen] = useState(false);
	const [modalLoading, setModalLoading] = useState(false);
	const [editing, setEditing] = useState<WorkflowTemplateConfig | null>(null);
	const [form] = Form.useForm<UpsertWorkflowTemplatePayload>();

	const { data = [], isFetching } = useQuery({
		queryKey: ["admin", "workflow-templates", workflowType, enabledOnly],
		queryFn: () => adminApi.getWorkflowTemplates(workflowType, enabledOnly),
		enabled: Boolean(workflowType),
	});

	const columns: ColumnsType<WorkflowTemplateConfig> = useMemo(
		() => [
			{ title: "名称", dataIndex: "name", key: "name", width: 280, ellipsis: true },
			{
				title: "启用",
				dataIndex: "enabled",
				key: "enabled",
				width: 90,
				render: (val?: boolean) => (val ? "是" : "否"),
			},
			{ title: "优先级", dataIndex: "priority", key: "priority", width: 90 },
			{
				title: "资产范围",
				dataIndex: "ownerScope",
				key: "ownerScope",
				width: 120,
				render: (val?: string) => OWNER_SCOPES.find((o) => o.value === val)?.label ?? val ?? "-",
			},
			{
				title: "密级范围",
				key: "classification",
				width: 200,
				render: (_, record) => {
					const min = record.classificationMin?.trim();
					const max = record.classificationMax?.trim();
					if (!min && !max) return <span className="text-muted-foreground">不限</span>;
					if (min && max) return `${min} ~ ${max}`;
					return min || max || "-";
				},
			},
			{
				title: "审批链",
				key: "steps",
				render: (_, record) => {
					const steps = Array.isArray(record.steps) ? record.steps : [];
					if (!steps.length) return <span className="text-muted-foreground">未配置</span>;
					return (
						<div className="flex flex-col gap-1">
							{steps.map((s) => (
								<div key={`${record.id}-${s.stepOrder}`} className="text-sm">
									{s.stepOrder}. {s.approverRole}
									{s.deptBinding ? <span className="text-muted-foreground">（绑定部门）</span> : null}
								</div>
							))}
						</div>
					);
				},
			},
			{
				title: "操作",
				key: "actions",
				width: 180,
				fixed: "right",
				align: "right",
				render: (_, record) => (
					<div className="flex items-center gap-2 justify-end">
						<Button
							size="sm"
							variant="outline"
							onClick={() => {
								setEditing(record);
								form.setFieldsValue({
									workflowType: record.workflowType,
									name: record.name,
									enabled: record.enabled ?? true,
									priority: record.priority ?? 0,
									ownerScope: record.ownerScope ?? "ANY",
									classificationMin: record.classificationMin ?? undefined,
									classificationMax: record.classificationMax ?? undefined,
									steps: (record.steps ?? []).map((s) => ({
										stepOrder: s.stepOrder,
										approverRole: s.approverRole,
										deptBinding: Boolean(s.deptBinding),
									})),
								});
								setModalOpen(true);
							}}
						>
							编辑
						</Button>
						<Button
							size="sm"
							variant="destructive"
							onClick={async () => {
								const confirmed = await confirmDelete(record.name);
								if (!confirmed) {
									return;
								}
								try {
									await adminApi.deleteWorkflowTemplate(record.id);
									toast.success("已删除");
									queryClient.invalidateQueries({ queryKey: ["admin", "workflow-templates"] });
								} catch (e: any) {
									toast.error(e?.message || "删除失败");
								}
							}}
						>
							删除
						</Button>
					</div>
				),
			},
		],
		[form, queryClient],
	);

	const openCreateModal = () => {
		setEditing(null);
		form.resetFields();
		form.setFieldsValue({
			workflowType,
			enabled: true,
			priority: 0,
			ownerScope: "ANY",
			steps: [{ stepOrder: 1, approverRole: "ROLE_INST_LEADER", deptBinding: false }],
		});
		setModalOpen(true);
	};

	const handleSave = async () => {
		const values = await form.validateFields();
		const payload: UpsertWorkflowTemplatePayload = {
			workflowType: String(values.workflowType || "").trim(),
			name: String(values.name || "").trim(),
			enabled: Boolean(values.enabled),
			priority: typeof values.priority === "number" ? values.priority : 0,
			ownerScope: values.ownerScope || "ANY",
			classificationMin: values.classificationMin || undefined,
			classificationMax: values.classificationMax || undefined,
			steps: normalizeSteps(values.steps || []),
		};
		if (!payload.workflowType || !payload.name) {
			toast.error("请填写工作流类型与名称");
			return;
		}
		if (!payload.steps?.length) {
			toast.error("至少需要配置一个审批节点");
			return;
		}
		setModalLoading(true);
		try {
			if (editing?.id) {
				await adminApi.updateWorkflowTemplate(editing.id, payload);
				toast.success("已保存");
			} else {
				await adminApi.createWorkflowTemplate(payload);
				toast.success("已创建");
			}
			setModalOpen(false);
			queryClient.invalidateQueries({ queryKey: ["admin", "workflow-templates"] });
		} catch (e: any) {
			toast.error(e?.message || "保存失败");
		} finally {
			setModalLoading(false);
		}
	};

	return (
		<div className="mx-auto w-full max-w-[1400px] px-6 py-6 space-y-6">
			<div className="flex flex-wrap items-center gap-3">
				<Text variant="body1" className="text-lg font-semibold">
					审批工作流配置
				</Text>
				<div className="ml-auto flex flex-wrap items-center gap-2">
					<Select
						style={{ width: 260 }}
						options={WORKFLOW_TYPES}
						value={workflowType}
						onChange={(val) => setWorkflowType(val)}
					/>
					<div className="flex items-center gap-2 rounded-md border px-3 py-2">
						<span className="text-sm text-muted-foreground">仅显示启用</span>
						<Switch checked={enabledOnly} onChange={setEnabledOnly} />
					</div>
					<Button onClick={openCreateModal}>新增配置</Button>
				</div>
			</div>

			<Card>
				<CardHeader>
					<CardTitle>配置列表</CardTitle>
				</CardHeader>
				<CardContent>
					<Table
						rowKey="id"
						columns={columns}
						dataSource={data}
						loading={isFetching}
						scroll={{ x: 1100 }}
						pagination={{ pageSize: 20, showSizeChanger: true }}
					/>
				</CardContent>
			</Card>

			<Modal
				open={modalOpen}
				title={editing ? "编辑审批工作流" : "新增审批工作流"}
				onCancel={() => setModalOpen(false)}
				onOk={handleSave}
				confirmLoading={modalLoading}
				width={860}
				okText="保存"
				cancelText="取消"
			>
				<Form form={form} layout="vertical">
					<div className="grid grid-cols-1 md:grid-cols-2 gap-4">
						<Form.Item name="workflowType" label="工作流类型" rules={[{ required: true, message: "请选择工作流类型" }]}>
							<Select options={WORKFLOW_TYPES} disabled={Boolean(editing)} />
						</Form.Item>
						<Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
							<Input placeholder="如：部门资产-秘密及以上" />
						</Form.Item>
						<Form.Item name="enabled" label="启用" valuePropName="checked">
							<Switch />
						</Form.Item>
						<Form.Item name="priority" label="优先级">
							<InputNumber min={0} style={{ width: "100%" }} />
						</Form.Item>
						<Form.Item name="ownerScope" label="资产范围">
							<Select options={OWNER_SCOPES} />
						</Form.Item>
						<Form.Item name="classificationMin" label="密级下限（可选）">
							<Select allowClear options={CLASSIFICATION_LEVELS} placeholder="不限" />
						</Form.Item>
						<Form.Item name="classificationMax" label="密级上限（可选）">
							<Select allowClear options={CLASSIFICATION_LEVELS} placeholder="不限" />
						</Form.Item>
					</div>

					<Form.List name="steps">
						{(fields, { add, remove }) => (
							<div className="space-y-3">
								<div className="flex items-center justify-between">
									<Text variant="body2" className="font-semibold">
										审批节点（按顺序执行）
									</Text>
									<Button
										size="sm"
										variant="outline"
										onClick={() => add({ stepOrder: fields.length + 1, approverRole: "ROLE_DEPT_LEADER", deptBinding: true })}
									>
										新增节点
									</Button>
								</div>
								{fields.length === 0 ? <Text variant="body3">暂无节点。</Text> : null}
								{fields.map((field, idx) => (
									<div key={field.key} className="rounded-md border p-3">
										<div className="flex items-center justify-between gap-3">
											<Text variant="body2" className="font-semibold">
												第 {idx + 1} 节点
											</Text>
											<Button size="sm" variant="ghost" onClick={() => remove(field.name)}>
												删除
											</Button>
										</div>
										<Space direction="vertical" style={{ width: "100%" }} size="middle">
											<Form.Item
												{...field}
												name={[field.name, "approverRole"]}
												label="审批角色"
												rules={[{ required: true, message: "请选择审批角色" }]}
											>
												<Select options={APPROVER_ROLES} placeholder="选择审批角色（可扩展）" showSearch optionFilterProp="label" />
											</Form.Item>
											<Form.Item {...field} name={[field.name, "deptBinding"]} valuePropName="checked">
												<Checkbox>绑定资产部门（部门领导节点建议勾选）</Checkbox>
											</Form.Item>
										</Space>
									</div>
								))}
							</div>
						)}
					</Form.List>

					<div className="mt-4 rounded-md bg-muted/40 p-3 text-sm text-muted-foreground">
						当前实现为“基于角色”的审批链配置；平台侧会按模板匹配生成审批任务并分配给对应角色。若后续需要“指定到具体人”，需要扩展模板节点字段并调整任务分配逻辑。
					</div>
				</Form>
			</Modal>
		</div>
	);
}
