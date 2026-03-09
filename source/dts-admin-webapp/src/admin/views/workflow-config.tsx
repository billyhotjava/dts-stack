import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Checkbox, Form, Input, InputNumber, Modal, Select, Space, Switch, Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { Check, ListFilter, Shield, Workflow } from "lucide-react";
import { adminApi } from "@/admin/api/adminApi";
import {
	AdminFilterBar,
	AdminMetaPill,
	AdminPageHeader,
	AdminSectionCard,
	AdminSummaryCards,
} from "@/admin/components/console-page";
import type { UpsertWorkflowTemplatePayload, WorkflowTemplateConfig, WorkflowStepConfig } from "@/admin/types";
import { EmptyState } from "@/components/empty-state";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Text } from "@/ui/typography";
import { toast } from "sonner";

type WorkflowTypeOption = { value: string; label: string; note: string };

const WORKFLOW_TYPES: WorkflowTypeOption[] = [
	{ value: "DATASET_DATA_ACCESS", label: "数据资产访问审批", note: "适用于查询、预览与导出类数据访问申请。" },
	{ value: "REPORT_ACCESS", label: "报表访问审批", note: "用于报表与 BI 页面访问授权。" },
	{ value: "SCREEN_ACCESS", label: "大屏访问审批", note: "用于驾驶舱、大屏链接与现场展示授权。" },
	{ value: "API_ACCESS", label: "API 调用审批", note: "用于 API、服务集成与自动任务调用授权。" },
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
		.filter((item) => Boolean(item?.approverRole?.trim()))
		.map((item, index) => ({
			stepOrder: index + 1,
			approverRole: String(item.approverRole).trim(),
			deptBinding: Boolean(item.deptBinding),
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

	const currentType = useMemo(
		() => WORKFLOW_TYPES.find((item) => item.value === workflowType) ?? WORKFLOW_TYPES[0],
		[workflowType],
	);

	const { data = [], isFetching, refetch } = useQuery({
		queryKey: ["admin", "workflow-templates", workflowType, enabledOnly],
		queryFn: () => adminApi.getWorkflowTemplates(workflowType, enabledOnly),
		enabled: Boolean(workflowType),
	});

	const stats = useMemo(() => {
		const enabledCount = data.filter((item) => item.enabled).length;
		const totalSteps = data.reduce((sum, item) => sum + (item.steps?.length ?? 0), 0);
		const scopedCount = data.filter((item) => item.ownerScope && item.ownerScope !== "ANY").length;
		return [
			{
				label: "当前模板",
				value: String(data.length),
				note: currentType?.label || "审批模板",
				icon: <Workflow className="h-5 w-5" />,
			},
			{
				label: "启用模板",
				value: String(enabledCount),
				note: enabledOnly ? "当前仅查看启用模板" : "包含启用与停用模板",
				icon: <Check className="h-5 w-5" />,
				tone: "success" as const,
			},
			{
				label: "审批节点",
				value: String(totalSteps),
				note: "当前筛选结果中的节点总数",
				icon: <ListFilter className="h-5 w-5" />,
				tone: "info" as const,
			},
			{
				label: "范围约束",
				value: String(scopedCount),
				note: "带资产范围或密级约束的模板",
				icon: <Shield className="h-5 w-5" />,
				tone: "warning" as const,
			},
		];
	}, [currentType?.label, data, enabledOnly]);

	const columns: ColumnsType<WorkflowTemplateConfig> = useMemo(
		() => [
			{ title: "名称", dataIndex: "name", key: "name", width: 260, ellipsis: true },
			{
				title: "启用状态",
				dataIndex: "enabled",
				key: "enabled",
				width: 110,
				render: (value?: boolean) => (
					<Badge variant={value ? "success" : "outline"} className="rounded-full px-2.5 py-1">
						{value ? "启用中" : "已停用"}
					</Badge>
				),
			},
			{
				title: "优先级",
				dataIndex: "priority",
				key: "priority",
				width: 100,
				render: (value?: number) => value ?? 0,
			},
			{
				title: "资产范围",
				dataIndex: "ownerScope",
				key: "ownerScope",
				width: 140,
				render: (value?: string) => {
					const label = OWNER_SCOPES.find((item) => item.value === value)?.label ?? value ?? "通用";
					return <Badge variant="info" className="rounded-full px-2.5 py-1">{label}</Badge>;
				},
			},
			{
				title: "密级范围",
				key: "classification",
				width: 180,
				render: (_, record) => {
					const min = record.classificationMin?.trim();
					const max = record.classificationMax?.trim();
					if (!min && !max) {
						return <span className="text-muted-foreground">不限</span>;
					}
					if (min && max) {
						return `${min} ~ ${max}`;
					}
					return min || max || "-";
				},
			},
			{
				title: "审批链",
				key: "steps",
				render: (_, record) => {
					const steps = Array.isArray(record.steps) ? record.steps : [];
					if (!steps.length) {
						return <span className="text-muted-foreground">未配置</span>;
					}
					return (
						<div className="flex flex-col gap-1.5">
							{steps.map((step) => (
								<div key={`${record.id}-${step.stepOrder}`} className="text-sm">
									<span className="font-medium">{step.stepOrder}. {step.approverRole}</span>
									{step.deptBinding ? <span className="text-muted-foreground">（绑定部门）</span> : null}
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
					<div className="flex items-center justify-end gap-2">
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
									steps: (record.steps ?? []).map((step) => ({
										stepOrder: step.stepOrder,
										approverRole: step.approverRole,
										deptBinding: Boolean(step.deptBinding),
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
								} catch (error: any) {
									toast.error(error?.message || "删除失败");
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
		} catch (error: any) {
			toast.error(error?.message || "保存失败");
		} finally {
			setModalLoading(false);
		}
	};

	return (
		<div className="space-y-6">
			<AdminPageHeader
				title="工作流配置"
				description="统一维护不同业务入口的审批模板，明确每类资产访问请求应该走哪条审批链。"
				eyebrow="Workflow Control"
				actions={
					<>
						<Button variant="outline" onClick={() => refetch()}>
							<ListFilter className="h-4 w-4" />
							刷新列表
						</Button>
						<Button onClick={openCreateModal}>
							<Workflow className="h-4 w-4" />
							新增配置
						</Button>
					</>
				}
				meta={
					<>
						<AdminMetaPill>{currentType?.label}</AdminMetaPill>
						<AdminMetaPill>{currentType?.note}</AdminMetaPill>
						<AdminMetaPill>{enabledOnly ? "仅显示启用模板" : "显示全部模板"}</AdminMetaPill>
					</>
				}
			/>

			<AdminSummaryCards items={stats} />

			<AdminFilterBar>
				<div>
					<div className="text-sm font-semibold text-foreground">按审批入口筛选模板</div>
					<div className="mt-1 text-sm text-muted-foreground">
						当前页负责模板治理，不再用“预留”字样掩盖真实的模板状态。
					</div>
				</div>
				<div className="flex flex-wrap items-center gap-2">
					<Select
						style={{ width: 240 }}
						options={WORKFLOW_TYPES.map(({ value, label }) => ({ value, label }))}
						value={workflowType}
						onChange={(value) => setWorkflowType(value)}
					/>
					<div className="flex items-center gap-2 rounded-2xl border border-border/70 bg-background/80 px-3 py-2">
						<span className="text-sm text-muted-foreground">仅显示启用</span>
						<Switch checked={enabledOnly} onChange={setEnabledOnly} />
					</div>
				</div>
			</AdminFilterBar>

			<AdminSectionCard
				title="模板清单"
				description="列表展示优先级、范围约束和审批链，便于统一核对配置口径。"
				action={<Badge variant="outline" className="rounded-full px-2.5 py-1">{data.length} 条结果</Badge>}
			>
				<Table
					rowKey="id"
					columns={columns}
					dataSource={data}
					loading={isFetching}
					scroll={{ x: 1100 }}
					pagination={{ pageSize: 20, showSizeChanger: true }}
					locale={{
						emptyText: (
							<EmptyState
								compact
								title="当前筛选下没有工作流模板"
								description="先创建模板，或切换到其他审批入口查看既有配置。"
							/>
						),
					}}
				/>
			</AdminSectionCard>

			<AdminSectionCard
				title="匹配说明"
				description="模板越清晰，审批分派越稳定。建议先确定资产范围，再补密级与节点顺序。"
			>
				<div className="grid gap-3 xl:grid-cols-3">
					{[
						"同一种工作流类型下，优先级高的模板会优先匹配。",
						"部门资产场景建议启用“绑定部门”，减少跨组织审批误派。",
						"报表、大屏和 API 模板可先配置，等业务入口接入后直接生效。",
					].map((item) => (
						<div key={item} className="rounded-[22px] border border-border/70 bg-muted/35 px-4 py-4 text-sm leading-6 text-muted-foreground">
							{item}
						</div>
					))}
				</div>
			</AdminSectionCard>

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
					<div className="grid grid-cols-1 gap-4 md:grid-cols-2">
						<Form.Item name="workflowType" label="工作流类型" rules={[{ required: true, message: "请选择工作流类型" }]}>
							<Select options={WORKFLOW_TYPES.map(({ value, label }) => ({ value, label }))} disabled={Boolean(editing)} />
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
										onClick={() =>
											add({ stepOrder: fields.length + 1, approverRole: "ROLE_DEPT_LEADER", deptBinding: true })
										}
									>
										新增节点
									</Button>
								</div>
								{fields.length === 0 ? <Text variant="body3">暂无节点。</Text> : null}
								{fields.map((field, index) => (
									<div key={field.key} className="rounded-2xl border border-border/70 p-4">
										<div className="flex items-center justify-between gap-3">
											<Text variant="body2" className="font-semibold">
												第 {index + 1} 节点
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
												<Select options={APPROVER_ROLES} placeholder="选择审批角色" showSearch optionFilterProp="label" />
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

					<div className="mt-4 rounded-2xl bg-muted/40 p-4 text-sm leading-6 text-muted-foreground">
						当前实现是“基于角色”的审批链配置。若后续需要把节点直接绑定到具体人，需要扩展模板字段并同步调整审批任务分配逻辑。
					</div>
				</Form>
			</Modal>
		</div>
	);
}
