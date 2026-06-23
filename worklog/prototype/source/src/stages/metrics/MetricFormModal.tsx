import { Form, Input, Modal, Select } from "antd";
import { useEffect } from "react";
import type { Dataset } from "@/types/asset";

export interface MetricFormValues {
	name: string;
	code: string;
	caliber: string;
	expression: string;
	unit: string;
	datasetId: string;
}

interface MetricFormModalProps {
	open: boolean;
	datasets: Dataset[];
	onSubmit: (values: MetricFormValues) => void;
	onClose: () => void;
}

/** 新建指标 —— 基于本部门已发布数据集设计。 */
export function MetricFormModal({ open, datasets, onSubmit, onClose }: MetricFormModalProps) {
	const [form] = Form.useForm<MetricFormValues>();

	useEffect(() => {
		if (open) form.resetFields();
	}, [open, form]);

	return (
		<Modal open={open} title="新建指标" okText="创建" cancelText="取消" onCancel={onClose} onOk={() => form.submit()} destroyOnClose>
			<Form form={form} layout="vertical" onFinish={onSubmit} requiredMark="optional" style={{ marginTop: 8 }}>
				<Form.Item name="name" label="指标名称" rules={[{ required: true, message: "请输入指标名称" }]}>
					<Input placeholder="如：销售达成率" />
				</Form.Item>
				<Form.Item name="code" label="编码" rules={[{ required: true, message: "请输入 ASCII 编码" }]}>
					<Input placeholder="sales_achieve_rate" />
				</Form.Item>
				<Form.Item name="datasetId" label="关联数据集" rules={[{ required: true, message: "请选择数据集" }]}>
					<Select placeholder="选择本部门数据集" options={datasets.map((d) => ({ value: d.id, label: d.name }))} />
				</Form.Item>
				<Form.Item name="expression" label="计算表达式" rules={[{ required: true, message: "请输入表达式" }]}>
					<Input placeholder="sum(amount) / target_amount" />
				</Form.Item>
				<Form.Item name="caliber" label="口径说明">
					<Input.TextArea rows={2} placeholder="指标的业务口径" />
				</Form.Item>
				<Form.Item name="unit" label="单位">
					<Input placeholder="% / 万元 / 批" />
				</Form.Item>
				<div style={{ fontSize: 12, color: "var(--ink-subtle)" }}>底层 dbt 模型将由系统自动生成（无需手写）。</div>
			</Form>
		</Modal>
	);
}
