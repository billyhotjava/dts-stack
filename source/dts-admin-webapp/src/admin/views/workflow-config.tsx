import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Card, Checkbox, Form, Input, InputNumber, Modal, Select, Space, Switch, Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { ListFilter, Workflow } from "lucide-react";
import { adminApi } from "@/admin/api/adminApi";
import type { UpsertWorkflowTemplatePayload, WorkflowTemplateConfig, WorkflowStepConfig } from "@/admin/types";
import { EmptyState } from "@/components/empty-state";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Text } from "@/ui/typography";
import { toast } from "sonner";

type WorkflowTypeOption = { value: string; label: string; note: string };

const WORKFLOW_TYPES: WorkflowTypeOption[] = [
	{ value: "DATASET_DATA_ACCESS", label: "\u6570\u636e\u8d44\u4ea7\u8bbf\u95ee\u5ba1\u6279", note: "\u9002\u7528\u4e8e\u67e5\u8be2\u3001\u9884\u89c8\u4e0e\u5bfc\u51fa\u7c7b\u6570\u636e\u8bbf\u95ee\u7533\u8bf7\u3002" },
	{ value: "REPORT_ACCESS", label: "\u62a5\u8868\u8bbf\u95ee\u5ba1\u6279", note: "\u7528\u4e8e\u62a5\u8868\u4e0e BI \u9875\u9762\u8bbf\u95ee\u6388\u6743\u3002" },
	{ value: "SCREEN_ACCESS", label: "\u5927\u5c4f\u8bbf\u95ee\u5ba1\u6279", note: "\u7528\u4e8e\u9a7e\u9a76\u8231\u3001\u5927\u5c4f\u94fe\u63a5\u4e0e\u73b0\u573a\u5c55\u793a\u6388\u6743\u3002" },
	{ value: "API_ACCESS", label: "API \u8c03\u7528\u5ba1\u6279", note: "\u7528\u4e8e API\u3001\u670d\u52a1\u96c6\u6210\u4e0e\u81ea\u52a8\u4efb\u52a1\u8c03\u7528\u6388\u6743\u3002" },
];

const OWNER_SCOPES = [
	{ value: "ANY", label: "\u901a\u7528" },
	{ value: "INST", label: "\u6240\u7ea7\u8d44\u4ea7" },
	{ value: "DEPT", label: "\u90e8\u95e8\u8d44\u4ea7" },
];

const CLASSIFICATION_LEVELS = [
	{ value: "\u516c\u5f00", label: "\u516c\u5f00" },
	{ value: "\u5185\u90e8", label: "\u5185\u90e8" },
	{ value: "\u79d8\u5bc6", label: "\u79d8\u5bc6" },
	{ value: "\u673a\u5bc6", label: "\u673a\u5bc6" },
];

const APPROVER_ROLES = [
	{ value: "ROLE_INST_LEADER", label: "\u6240\u7ea7\u9886\u5bfc\uff08ROLE_INST_LEADER\uff09" },
	{ value: "ROLE_DEPT_LEADER", label: "\u90e8\u95e8\u9886\u5bfc\uff08ROLE_DEPT_LEADER\uff09" },
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
			title: "\u786e\u8ba4\u5220\u9664\uff1f",
			content: `\u5c06\u5220\u9664\u5de5\u4f5c\u6d41\u914d\u7f6e\u300c${name}\u300d`,
			okText: "\u5220\u9664",
			okType: "danger",
			cancelText: "\u53d6\u6d88",
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

	const { data = [], isFetching, refetch } = useQuery({
		queryKey: ["admin", "workflow-templates", workflowType, enabledOnly],
		queryFn: () => adminApi.getWorkflowTemplates(workflowType, enabledOnly),
		enabled: Boolean(workflowType),
	});

	const columns: ColumnsType<WorkflowTemplateConfig> = useMemo(
		() => [
			{ title: "\u540d\u79f0", dataIndex: "name", key: "name", width: 260, ellipsis: true },
			{
				title: "\u542f\u7528\u72b6\u6001",
				dataIndex: "enabled",
				key: "enabled",
				width: 110,
				render: (value?: boolean) => (
					<Badge variant={value ? "success" : "outline"} className="rounded-full px-2.5 py-1">
						{value ? "\u542f\u7528\u4e2d" : "\u5df2\u505c\u7528"}
					</Badge>
				),
			},
			{
				title: "\u4f18\u5148\u7ea7",
				dataIndex: "priority",
				key: "priority",
				width: 100,
				render: (value?: number) => value ?? 0,
			},
			{
				title: "\u8d44\u4ea7\u8303\u56f4",
				dataIndex: "ownerScope",
				key: "ownerScope",
				width: 140,
				render: (value?: string) => {
					const label = OWNER_SCOPES.find((item) => item.value === value)?.label ?? value ?? "\u901a\u7528";
					return <Badge variant="info" className="rounded-full px-2.5 py-1">{label}</Badge>;
				},
			},
			{
				title: "\u5bc6\u7ea7\u8303\u56f4",
				key: "classification",
				width: 180,
				render: (_, record) => {
					const min = record.classificationMin?.trim();
					const max = record.classificationMax?.trim();
					if (!min && !max) {
						return <span className="text-muted-foreground">\u4e0d\u9650</span>;
					}
					if (min && max) {
						return `${min} ~ ${max}`;
					}
					return min || max || "-";
				},
			},
			{
				title: "\u5ba1\u6279\u94fe",
				key: "steps",
				render: (_, record) => {
					const steps = Array.isArray(record.steps) ? record.steps : [];
					if (!steps.length) {
						return <span className="text-muted-foreground">\u672a\u914d\u7f6e</span>;
					}
					return (
						<div className="flex flex-col gap-1.5">
							{steps.map((step) => (
								<div key={`${record.id}-${step.stepOrder}`} className="text-sm">
									<span className="font-medium">{step.stepOrder}. {step.approverRole}</span>
									{step.deptBinding ? <span className="text-muted-foreground">\uff08\u7ed1\u5b9a\u90e8\u95e8\uff09</span> : null}
								</div>
							))}
						</div>
					);
				},
			},
			{
				title: "\u64cd\u4f5c",
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
							\u7f16\u8f91
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
									toast.success("\u5df2\u5220\u9664");
									queryClient.invalidateQueries({ queryKey: ["admin", "workflow-templates"] });
								} catch (error: any) {
									toast.error(error?.message || "\u5220\u9664\u5931\u8d25");
								}
							}}
						>
							\u5220\u9664
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
			toast.error("\u8bf7\u586b\u5199\u5de5\u4f5c\u6d41\u7c7b\u578b\u4e0e\u540d\u79f0");
			return;
		}
		if (!payload.steps?.length) {
			toast.error("\u81f3\u5c11\u9700\u8981\u914d\u7f6e\u4e00\u4e2a\u5ba1\u6279\u8282\u70b9");
			return;
		}

		setModalLoading(true);
		try {
			if (editing?.id) {
				await adminApi.updateWorkflowTemplate(editing.id, payload);
				toast.success("\u5df2\u4fdd\u5b58");
			} else {
				await adminApi.createWorkflowTemplate(payload);
				toast.success("\u5df2\u521b\u5efa");
			}
			setModalOpen(false);
			queryClient.invalidateQueries({ queryKey: ["admin", "workflow-templates"] });
		} catch (error: any) {
			toast.error(error?.message || "\u4fdd\u5b58\u5931\u8d25");
		} finally {
			setModalLoading(false);
		}
	};

	return (
		<div className="space-y-4">
			<Card
				title={"\u5de5\u4f5c\u6d41\u914d\u7f6e"}
				extra={
					<>
						<Button variant="outline" onClick={() => refetch()}>
							<ListFilter className="h-4 w-4" />
							\u5237\u65b0\u5217\u8868
						</Button>
						<Button onClick={openCreateModal}>
							<Workflow className="h-4 w-4" />
							\u65b0\u589e\u914d\u7f6e
						</Button>
					</>
				}
			>
				<div className="flex flex-wrap items-center gap-2 mb-4">
					<Select
						style={{ width: 240 }}
						options={WORKFLOW_TYPES.map(({ value, label }) => ({ value, label }))}
						value={workflowType}
						onChange={(value) => setWorkflowType(value)}
					/>
					<div className="flex items-center gap-2 rounded-2xl border border-border/70 bg-background/80 px-3 py-2">
						<span className="text-sm text-muted-foreground">{"\u4ec5\u663e\u793a\u542f\u7528"}</span>
						<Switch checked={enabledOnly} onChange={setEnabledOnly} />
					</div>
				</div>

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
								title={"\u5f53\u524d\u7b5b\u9009\u4e0b\u6ca1\u6709\u5de5\u4f5c\u6d41\u6a21\u677f"}
								description={"\u5148\u521b\u5efa\u6a21\u677f\uff0c\u6216\u5207\u6362\u5230\u5176\u4ed6\u5ba1\u6279\u5165\u53e3\u67e5\u770b\u65e2\u6709\u914d\u7f6e\u3002"}
							/>
						),
					}}
				/>
			</Card>

			<Card title={"\u5339\u914d\u8bf4\u660e"}>
				<div className="grid gap-3 xl:grid-cols-3">
					{[
						"\u540c\u4e00\u79cd\u5de5\u4f5c\u6d41\u7c7b\u578b\u4e0b\uff0c\u4f18\u5148\u7ea7\u9ad8\u7684\u6a21\u677f\u4f1a\u4f18\u5148\u5339\u914d\u3002",
						"\u90e8\u95e8\u8d44\u4ea7\u573a\u666f\u5efa\u8bae\u542f\u7528\u201c\u7ed1\u5b9a\u90e8\u95e8\u201d\uff0c\u51cf\u5c11\u8de8\u7ec4\u7ec7\u5ba1\u6279\u8bef\u6d3e\u3002",
						"\u62a5\u8868\u3001\u5927\u5c4f\u548c API \u6a21\u677f\u53ef\u5148\u914d\u7f6e\uff0c\u7b49\u4e1a\u52a1\u5165\u53e3\u63a5\u5165\u540e\u76f4\u63a5\u751f\u6548\u3002",
					].map((item) => (
						<div key={item} className="rounded-[22px] border border-border/70 bg-muted/35 px-4 py-4 text-sm leading-6 text-muted-foreground">
							{item}
						</div>
					))}
				</div>
			</Card>

			<Modal
				open={modalOpen}
				title={editing ? "\u7f16\u8f91\u5ba1\u6279\u5de5\u4f5c\u6d41" : "\u65b0\u589e\u5ba1\u6279\u5de5\u4f5c\u6d41"}
				onCancel={() => setModalOpen(false)}
				onOk={handleSave}
				confirmLoading={modalLoading}
				width={860}
				okText={"\u4fdd\u5b58"}
				cancelText={"\u53d6\u6d88"}
			>
				<Form form={form} layout="vertical">
					<div className="grid grid-cols-1 gap-4 md:grid-cols-2">
						<Form.Item name="workflowType" label={"\u5de5\u4f5c\u6d41\u7c7b\u578b"} rules={[{ required: true, message: "\u8bf7\u9009\u62e9\u5de5\u4f5c\u6d41\u7c7b\u578b" }]}>
							<Select options={WORKFLOW_TYPES.map(({ value, label }) => ({ value, label }))} disabled={Boolean(editing)} />
						</Form.Item>
						<Form.Item name="name" label={"\u540d\u79f0"} rules={[{ required: true, message: "\u8bf7\u8f93\u5165\u540d\u79f0" }]}>
							<Input placeholder={"\u5982\uff1a\u90e8\u95e8\u8d44\u4ea7-\u79d8\u5bc6\u53ca\u4ee5\u4e0a"} />
						</Form.Item>
						<Form.Item name="enabled" label={"\u542f\u7528"} valuePropName="checked">
							<Switch />
						</Form.Item>
						<Form.Item name="priority" label={"\u4f18\u5148\u7ea7"}>
							<InputNumber min={0} style={{ width: "100%" }} />
						</Form.Item>
						<Form.Item name="ownerScope" label={"\u8d44\u4ea7\u8303\u56f4"}>
							<Select options={OWNER_SCOPES} />
						</Form.Item>
						<Form.Item name="classificationMin" label={"\u5bc6\u7ea7\u4e0b\u9650\uff08\u53ef\u9009\uff09"}>
							<Select allowClear options={CLASSIFICATION_LEVELS} placeholder={"\u4e0d\u9650"} />
						</Form.Item>
						<Form.Item name="classificationMax" label={"\u5bc6\u7ea7\u4e0a\u9650\uff08\u53ef\u9009\uff09"}>
							<Select allowClear options={CLASSIFICATION_LEVELS} placeholder={"\u4e0d\u9650"} />
						</Form.Item>
					</div>

					<Form.List name="steps">
						{(fields, { add, remove }) => (
							<div className="space-y-3">
								<div className="flex items-center justify-between">
									<Text variant="body2" className="font-semibold">
										{"\u5ba1\u6279\u8282\u70b9\uff08\u6309\u987a\u5e8f\u6267\u884c\uff09"}
									</Text>
									<Button
										size="sm"
										variant="outline"
										onClick={() =>
											add({ stepOrder: fields.length + 1, approverRole: "ROLE_DEPT_LEADER", deptBinding: true })
										}
									>
										{"\u65b0\u589e\u8282\u70b9"}
									</Button>
								</div>
								{fields.length === 0 ? <Text variant="body3">{"\u6682\u65e0\u8282\u70b9\u3002"}</Text> : null}
								{fields.map((field, index) => (
									<div key={field.key} className="rounded-2xl border border-border/70 p-4">
										<div className="flex items-center justify-between gap-3">
											<Text variant="body2" className="font-semibold">
												{"\u7b2c"} {index + 1} {"\u8282\u70b9"}
											</Text>
											<Button size="sm" variant="ghost" onClick={() => remove(field.name)}>
												{"\u5220\u9664"}
											</Button>
										</div>
										<Space direction="vertical" style={{ width: "100%" }} size="middle">
											<Form.Item
												{...field}
												name={[field.name, "approverRole"]}
												label={"\u5ba1\u6279\u89d2\u8272"}
												rules={[{ required: true, message: "\u8bf7\u9009\u62e9\u5ba1\u6279\u89d2\u8272" }]}
											>
												<Select options={APPROVER_ROLES} placeholder={"\u9009\u62e9\u5ba1\u6279\u89d2\u8272"} showSearch optionFilterProp="label" />
											</Form.Item>
											<Form.Item {...field} name={[field.name, "deptBinding"]} valuePropName="checked">
												<Checkbox>{"\u7ed1\u5b9a\u8d44\u4ea7\u90e8\u95e8\uff08\u90e8\u95e8\u9886\u5bfc\u8282\u70b9\u5efa\u8bae\u52fe\u9009\uff09"}</Checkbox>
											</Form.Item>
										</Space>
									</div>
								))}
							</div>
						)}
					</Form.List>

					<div className="mt-4 rounded-2xl bg-muted/40 p-4 text-sm leading-6 text-muted-foreground">
						{"\u5f53\u524d\u5b9e\u73b0\u662f\u201c\u57fa\u4e8e\u89d2\u8272\u201d\u7684\u5ba1\u6279\u94fe\u914d\u7f6e\u3002\u82e5\u540e\u7eed\u9700\u8981\u628a\u8282\u70b9\u76f4\u63a5\u7ed1\u5b9a\u5230\u5177\u4f53\u4eba\uff0c\u9700\u8981\u6269\u5c55\u6a21\u677f\u5b57\u6bb5\u5e76\u540c\u6b65\u8c03\u6574\u5ba1\u6279\u4efb\u52a1\u5206\u914d\u903b\u8f91\u3002"}
					</div>
				</Form>
			</Modal>
		</div>
	);
}
