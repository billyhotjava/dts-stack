import { Form, Input, Modal, Select } from "antd";
import { useEffect } from "react";
import type { Connector, DataSource, DataSourceUpsertPayload } from "@/types/datasource";

interface DataSourceFormModalProps {
	open: boolean;
	/** 编辑时传入，新建为 null */
	editing: DataSource | null;
	connectors: Connector[];
	submitting?: boolean;
	onSubmit: (payload: DataSourceUpsertPayload) => void;
	onClose: () => void;
}

/** 新建/编辑数据源表单（仅本部门本地源）。 */
export function DataSourceFormModal({ open, editing, connectors, submitting, onSubmit, onClose }: DataSourceFormModalProps) {
	const [form] = Form.useForm<DataSourceUpsertPayload>();

	useEffect(() => {
		if (open) {
			if (editing) {
				form.setFieldsValue({
					name: editing.name,
					type: editing.type,
					connector: editing.connector,
					jdbcUrl: editing.jdbcUrl,
					username: editing.username,
					description: editing.description,
				});
			} else {
				form.resetFields();
			}
		}
	}, [open, editing, form]);

	const enabledConnectors = connectors.filter((c) => c.status === "enabled");

	return (
		<Modal
			open={open}
			title={editing ? "编辑数据源" : "新建数据源"}
			okText={editing ? "保存" : "创建"}
			cancelText="取消"
			confirmLoading={submitting}
			onCancel={onClose}
			onOk={() => form.submit()}
			destroyOnClose
		>
			<Form form={form} layout="vertical" onFinish={onSubmit} requiredMark="optional" style={{ marginTop: 8 }}>
				<Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入数据源名称" }]}>
					<Input placeholder="如：销售本地台账" />
				</Form.Item>
				<Form.Item name="type" label="系统类型" rules={[{ required: true, message: "请输入系统类型" }]}>
					<Input placeholder="如：PostgreSQL / Excel" />
				</Form.Item>
				<Form.Item name="connector" label="连接器" rules={[{ required: true, message: "请选择连接器" }]}>
					<Select
						placeholder="选择连接器"
						options={enabledConnectors.map((c) => ({ value: c.key, label: `${c.name}（${c.category}）` }))}
					/>
				</Form.Item>
				<Form.Item name="jdbcUrl" label="JDBC URL">
					<Input placeholder="jdbc:postgresql://host:5432/db" />
				</Form.Item>
				<Form.Item name="username" label="用户名">
					<Input placeholder="只读账号" />
				</Form.Item>
				<Form.Item name="description" label="描述">
					<Input.TextArea rows={2} placeholder="用途说明" />
				</Form.Item>
				<div style={{ fontSize: 12, color: "var(--ink-subtle)" }}>
					密钥/口令在平台层加密托管，不在此表单明文录入（原型从略）。
				</div>
			</Form>
		</Modal>
	);
}
