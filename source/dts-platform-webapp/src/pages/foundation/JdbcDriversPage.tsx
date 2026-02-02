import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Form, Input, Modal, Space, Table, Tag, Typography, Upload, message } from "antd";
import { PlusOutlined, ReloadOutlined, EditOutlined, DeleteOutlined, InboxOutlined } from "@ant-design/icons";
type UploadRequestOption = Parameters<NonNullable<import("antd").UploadProps["customRequest"]>>[0];
import jdbcDriversService, { type InfraJdbcDriver, type JdbcDriverUpdatePayload } from "@/api/services/jdbcDriversService";

const { Text } = Typography;

const formatTime = (value?: string) => {
	if (!value) return "-";
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) return value;
	return date.toLocaleString("zh-CN");
};

const renderJdk = (value?: string) => {
	if (!value) return "-";
	return `JDK ${value}`;
};

export default function JdbcDriversPage() {
	const [list, setList] = useState<InfraJdbcDriver[]>([]);
	const [loading, setLoading] = useState(false);
	const [uploadModalOpen, setUploadModalOpen] = useState(false);
	const [uploading, setUploading] = useState(false);
	const [editing, setEditing] = useState<InfraJdbcDriver | null>(null);
	const [editModalOpen, setEditModalOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [form] = Form.useForm();

	const loadList = async () => {
		setLoading(true);
		try {
			const data = await jdbcDriversService.list();
			setList(Array.isArray(data) ? data : []);
		} catch (error: any) {
			message.error(error?.message || "加载驱动列表失败");
			setList([]);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		loadList();
	}, []);

	const openUpload = () => {
		setUploadModalOpen(true);
	};

	const handleUpload = async (options: UploadRequestOption) => {
		const file = options.file as File;
		if (!file) return;
		if (!file.name.toLowerCase().endsWith(".jar")) {
			message.error("仅支持上传 .jar 驱动文件");
			options.onError?.(new Error("invalid_type"));
			return;
		}
		setUploading(true);
		try {
			await jdbcDriversService.upload(file);
			message.success("驱动已上传");
			setUploadModalOpen(false);
			loadList();
			options.onSuccess?.({});
		} catch (error: any) {
			message.error(error?.message || "上传失败");
			options.onError?.(error);
		} finally {
			setUploading(false);
		}
	};

	const openEdit = (record: InfraJdbcDriver) => {
		setEditing(record);
		form.setFieldsValue({
			driverClass: record.driverClass,
			version: record.version,
			jdkSpec: record.jdkSpec,
		});
		setEditModalOpen(true);
	};

	const handleUpdate = async () => {
		if (!editing) return;
		try {
			const values = await form.validateFields();
			setSaving(true);
			const payload: JdbcDriverUpdatePayload = {
				driverClass: values.driverClass?.trim() || undefined,
				version: values.version?.trim() || undefined,
				jdkSpec: values.jdkSpec?.trim() || undefined,
			};
			await jdbcDriversService.update(editing.id, payload);
			message.success("驱动已更新");
			setEditModalOpen(false);
			loadList();
		} catch (error: any) {
			if (error?.errorFields) return;
			message.error(error?.message || "更新失败");
		} finally {
			setSaving(false);
		}
	};

	const handleDelete = (record: InfraJdbcDriver) => {
		Modal.confirm({
			title: "确认删除",
			content: `确定删除驱动 "${record.fileName}" 吗？`,
			okType: "danger",
			onOk: async () => {
				try {
					await jdbcDriversService.remove(record.id);
					message.success("驱动已删除");
					loadList();
				} catch (error: any) {
					message.error(error?.message || "删除失败");
				}
			},
		});
	};

	const columns = useMemo(
		() => [
			{ title: "JAR 文件", dataIndex: "fileName", key: "fileName", width: 220 },
			{ title: "驱动主类", dataIndex: "driverClass", key: "driverClass", width: 260, ellipsis: true },
			{ title: "版本号", dataIndex: "version", key: "version", width: 120 },
			{
				title: "JDK",
				dataIndex: "jdkSpec",
				key: "jdkSpec",
				width: 100,
				render: (value: string) => renderJdk(value),
			},
			{ title: "路径", dataIndex: "filePath", key: "filePath", ellipsis: true },
			{
				title: "状态",
				dataIndex: "missing",
				key: "missing",
				width: 100,
				render: (value: boolean) => (value ? <Tag color="warning">缺失</Tag> : <Tag color="success">正常</Tag>),
			},
			{
				title: "更新时间",
				dataIndex: "lastUpdatedAt",
				key: "lastUpdatedAt",
				width: 180,
				render: (value: string) => formatTime(value),
			},
			{
				title: "操作",
				key: "action",
				width: 180,
				render: (_: any, record: InfraJdbcDriver) => (
					<Space>
						<Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>
							编辑
						</Button>
						<Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record)}>
							删除
						</Button>
					</Space>
				),
			},
		],
		[]
	);

	return (
		<Card
			title="JDBC 驱动管理"
			extra={
				<Space>
					<Button icon={<ReloadOutlined />} onClick={loadList} disabled={loading}>
						刷新
					</Button>
					<Button type="primary" icon={<PlusOutlined />} onClick={openUpload}>
						上传驱动
					</Button>
				</Space>
			}
		>
			<div className="mb-3 text-xs text-slate-500">
				默认驱动目录：<Text code>services/dts-platform/drivers</Text>（上传后自动同步到该目录）
			</div>
			<Table
				rowKey="id"
				columns={columns as any}
				dataSource={list}
				loading={loading}
				pagination={{ pageSize: 12 }}
			/>

			<Modal
				title="上传 JDBC 驱动"
				open={uploadModalOpen}
				onCancel={() => setUploadModalOpen(false)}
				footer={null}
				destroyOnClose
			>
				<Space direction="vertical" style={{ width: "100%" }}>
					<Alert
						type="warning"
						showIcon
						message="非密模块禁止上传涉密数据"
					/>
					<Upload.Dragger
						name="file"
						multiple={false}
						maxCount={1}
						showUploadList={false}
						accept=".jar"
						customRequest={handleUpload}
						disabled={uploading}
					>
						<p className="ant-upload-drag-icon">
							<InboxOutlined />
						</p>
						<p className="ant-upload-text">点击或拖拽上传 JDBC 驱动 JAR</p>
						<p className="ant-upload-hint">仅支持 .jar 文件</p>
					</Upload.Dragger>
				</Space>
			</Modal>

			<Modal
				title="编辑驱动信息"
				open={editModalOpen}
				onCancel={() => setEditModalOpen(false)}
				onOk={handleUpdate}
				okText="保存"
				confirmLoading={saving}
				destroyOnClose
			>
				<Form layout="vertical" form={form} preserve={false}>
					<Form.Item label="JAR 文件">
						<Text>{editing?.fileName || "-"}</Text>
					</Form.Item>
					<Form.Item label="路径">
						<Text>{editing?.filePath || "-"}</Text>
					</Form.Item>
					<Form.Item name="driverClass" label="驱动主类">
						<Input placeholder="例如：org.postgresql.Driver" />
					</Form.Item>
					<Form.Item name="version" label="版本号">
						<Input placeholder="例如：42.7.9" />
					</Form.Item>
					<Form.Item name="jdkSpec" label="JDK 版本">
						<Input placeholder="例如：8 / 11 / 17" />
					</Form.Item>
				</Form>
			</Modal>
		</Card>
	);
}
