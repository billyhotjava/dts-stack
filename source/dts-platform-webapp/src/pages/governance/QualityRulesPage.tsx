import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Button,
	Card,
	Form,
	Input,
	Modal,
	Select,
	Space,
	Table,
	Tag,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { PlusOutlined, PlayCircleOutlined, DeleteOutlined, EditOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import ComplianceCenterPanel from "@/pages/governance/components/ComplianceCenterPanel";
import IssueWorkflowPanel from "@/pages/governance/components/IssueWorkflowPanel";
import QualityTasksPanel from "@/pages/governance/components/QualityTasksPanel";
import {
	listQualityRules,
	createQualityRule,
	updateQualityRule,
	deleteQualityRule,
	toggleQualityRule,
	triggerQualityRun,
	listDatasets,
} from "@/api/platformApi";

const SEVERITY_OPTIONS = [
	{ label: "低", value: "LOW" },
	{ label: "中", value: "MEDIUM" },
	{ label: "高", value: "HIGH" },
	{ label: "致命", value: "CRITICAL" },
];

const TYPE_OPTIONS = [
	{ label: "完整性", value: "COMPLETENESS" },
	{ label: "一致性", value: "CONSISTENCY" },
	{ label: "准确性", value: "ACCURACY" },
	{ label: "唯一性", value: "UNIQUENESS" },
	{ label: "及时性", value: "TIMELINESS" },
];

type Rule = any;

type RuleForm = {
	code?: string;
	name: string;
	type?: string;
	severity?: string;
	datasetId?: string;
	enabled?: boolean;
	definition?: string;
};

const parseDefinition = (value?: string) => {
	if (!value) return undefined;
	try {
		return JSON.parse(value);
	} catch (error) {
		return undefined;
	}
};

export default function Page() {
	const [rules, setRules] = useState<Rule[]>([]);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [editing, setEditing] = useState<Rule | null>(null);
	const [form] = Form.useForm<RuleForm>();
	const [datasets, setDatasets] = useState<{ id: string; name: string }[]>([]);

	const datasetOptions = useMemo(
		() => datasets.map((item) => ({ label: item.name, value: item.id })),
		[datasets],
	);

	const loadRules = async () => {
		setLoading(true);
		try {
			const list = await listQualityRules();
			setRules(Array.isArray(list) ? (list as Rule[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "规则加载失败");
		} finally {
			setLoading(false);
		}
	};

	const loadDatasets = async () => {
		try {
			const resp: any = await listDatasets({ page: 0, size: 200 });
			const list = Array.isArray(resp?.content) ? resp.content : [];
			setDatasets(list.map((item: any) => ({ id: String(item.id), name: item.name || item.id })));
		} catch (error: any) {
			toast.error(error?.message || "数据集加载失败");
		}
	};

	useEffect(() => {
		void loadRules();
		void loadDatasets();
	}, []);

	const openModal = (rule?: Rule) => {
		setEditing(rule || null);
		form.setFieldsValue({
			code: rule?.code || "",
			name: rule?.name || "",
			type: rule?.type || "COMPLETENESS",
			severity: rule?.severity || "MEDIUM",
			datasetId: rule?.datasetId || undefined,
			enabled: rule?.enabled ?? true,
			definition: rule?.latestVersion?.definition
				? rule.latestVersion.definition
				: rule?.definition
					? JSON.stringify(rule.definition, null, 2)
					: "",
		});
		setModalOpen(true);
	};

	const saveRule = async () => {
		try {
			const values = await form.validateFields();
			const parsedDefinition = parseDefinition(values.definition);
			if (values.definition && !parsedDefinition) {
				toast.error("规则定义不是有效的 JSON");
				return;
			}
			const payload: any = {
				code: values.code || undefined,
				name: values.name,
				type: values.type,
				severity: values.severity,
				datasetId: values.datasetId || undefined,
				enabled: values.enabled ?? true,
				definition: parsedDefinition || undefined,
			};
			if (editing?.id) {
				await updateQualityRule(editing.id, payload);
				toast.success("规则已更新");
			} else {
				await createQualityRule(payload);
				toast.success("规则已新增");
			}
			setModalOpen(false);
			await loadRules();
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "保存失败");
		}
	};

	const removeRule = async (id?: string) => {
		if (!id) return;
		try {
			await deleteQualityRule(id);
			toast.success("规则已删除");
			await loadRules();
		} catch (error: any) {
			toast.error(error?.message || "删除失败");
		}
	};

	const toggleRule = async (rule: Rule, enabled: boolean) => {
		try {
			await toggleQualityRule(rule.id, enabled);
			toast.success(enabled ? "规则已启用" : "规则已停用");
			await loadRules();
		} catch (error: any) {
			toast.error(error?.message || "操作失败");
		}
	};

	const triggerRun = async (rule: Rule) => {
		if (!rule?.id) return;
		try {
			await triggerQualityRun({ ruleId: rule.id });
			toast.success("已触发执行");
		} catch (error: any) {
			toast.error(error?.message || "触发失败");
		}
	};

	const columns: ColumnsType<Rule> = [
		{ title: "规则名称", dataIndex: "name", render: (v) => v || "-" },
		{ title: "类型", dataIndex: "type", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "严重性", dataIndex: "severity", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "数据集", dataIndex: "datasetId", render: (v) => datasets.find((d) => d.id === v)?.name || v || "-" },
		{ title: "状态", dataIndex: "enabled", width: 100, render: (v) => <Tag color={v ? "green" : "default"}>{v ? "启用" : "停用"}</Tag> },
		{
			title: "操作",
			width: 220,
			render: (_, record) => (
				<Space>
					<Button size="small" icon={<PlayCircleOutlined />} onClick={() => triggerRun(record)}>
						执行
					</Button>
					<Button size="small" icon={<EditOutlined />} onClick={() => openModal(record)}>
						编辑
					</Button>
					<Button size="small" onClick={() => toggleRule(record, !record.enabled)}>
						{record.enabled ? "停用" : "启用"}
					</Button>
					<Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeRule(record.id)}>
						删除
					</Button>
				</Space>
			),
		},
	];

	return (
		<div className="space-y-6">
			<PageHeader
				title="数据治理中心 / 质量管控"
				description="质量规则配置与执行。"
				actions={
					<Button type="primary" icon={<PlusOutlined />} onClick={() => openModal()}>
						新增规则
					</Button>
				}
			/>
			<Card>
				<Table
					rowKey={(record) => record.id}
					columns={columns}
					dataSource={rules}
					loading={loading}
				/>
			</Card>
			<QualityTasksPanel />
			<ComplianceCenterPanel />
			<IssueWorkflowPanel />

			<Modal
				open={modalOpen}
				title={editing ? "编辑规则" : "新增规则"}
				onCancel={() => setModalOpen(false)}
				onOk={saveRule}
				okText="保存"
				destroyOnClose
				width={720}
			>
				<Form form={form} layout="vertical">
					<Form.Item label="规则名称" name="name" rules={[{ required: true, message: "请输入规则名称" }]}>
						<Input placeholder="例如：订单金额非空" />
					</Form.Item>
					<Form.Item label="规则编码" name="code">
						<Input placeholder="可选" />
					</Form.Item>
					<Form.Item label="类型" name="type">
						<Select options={TYPE_OPTIONS} />
					</Form.Item>
					<Form.Item label="严重性" name="severity">
						<Select options={SEVERITY_OPTIONS} />
					</Form.Item>
					<Form.Item label="数据集" name="datasetId">
						<Select options={datasetOptions} allowClear />
					</Form.Item>
					<Form.Item label="规则定义(JSON)" name="definition">
						<Input.TextArea rows={6} placeholder='例如: {"column":"amount","rule":"not_null"}' />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
