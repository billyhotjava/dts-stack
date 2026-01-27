import { useEffect, useMemo, useState } from "react";
import { Button, Card, Form, Input, Modal, Select, Space, Table, Tag, Typography, message } from "antd";
import { PlusOutlined, ReloadOutlined, EditOutlined, DeleteOutlined, ExperimentOutlined } from "@ant-design/icons";
import dataSourcesService, { type DataSourceUpsertPayload, type InfraDataSource } from "@/api/services/dataSourcesService";

const { Text } = Typography;

const JDBC_TYPES = new Set([
	"dm",
	"dameng",
	"postgresql",
	"postgres",
	"pg",
	"mysql",
	"mariadb",
	"oracle",
	"sqlserver",
	"mssql",
	"clickhouse",
	"hive",
	"inceptor",
	"db2",
	"sqlite",
	"jdbc",
]);

const TYPE_OPTIONS = [
	{ label: "PostgreSQL", value: "postgresql" },
	{ label: "达梦 DM", value: "dm" },
	{ label: "ClickHouse", value: "clickhouse" },
	{ label: "MySQL / MariaDB", value: "mysql" },
	{ label: "Oracle", value: "oracle" },
	{ label: "SQLServer", value: "sqlserver" },
	{ label: "Hive", value: "hive" },
	{ label: "Inceptor", value: "inceptor" },
	{ label: "Excel", value: "excel" },
	{ label: "CSV", value: "csv" },
	{ label: "JSON 文件", value: "json" },
	{ label: "API", value: "api" },
	{ label: "通用 JDBC", value: "jdbc" },
];

const normalizeType = (value?: string) => String(value || "").trim().toLowerCase();

const isJdbcType = (type?: string, jdbcUrl?: string) => {
	if (jdbcUrl) return true;
	const normalized = normalizeType(type);
	if (!normalized) return false;
	if (normalized.includes("jdbc")) return true;
	return JDBC_TYPES.has(normalized);
};

const parseJson = (value?: string) => {
	const text = String(value || "").trim();
	if (!text) return undefined;
	return JSON.parse(text);
};

const formatTime = (value?: string) => {
	if (!value) return "-";
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) return value;
	return date.toLocaleString("zh-CN");
};

const omitReaderType = (props?: Record<string, any>) => {
	if (!props) return undefined;
	const { readerType, reader, ...rest } = props;
	return rest;
};

export default function DataSourcesPage() {
	const [list, setList] = useState<InfraDataSource[]>([]);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [testingId, setTestingId] = useState<string | null>(null);
	const [editing, setEditing] = useState<InfraDataSource | null>(null);
	const [form] = Form.useForm();

	const loadList = async () => {
		setLoading(true);
		try {
			const data = await dataSourcesService.list();
			setList(Array.isArray(data) ? data : []);
		} catch (error: any) {
			message.error(error?.message || "加载数据源失败");
			setList([]);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		loadList();
	}, []);

	const openCreate = () => {
		setEditing(null);
		form.resetFields();
		setModalOpen(true);
	};

	const openEdit = (record: InfraDataSource) => {
		setEditing(record);
		form.setFieldsValue({
			name: record.name,
			type: record.type,
			jdbcUrl: record.jdbcUrl,
			username: record.username,
			description: record.description,
			readerType: record.props?.readerType || record.props?.reader,
			propsJson: record.props ? JSON.stringify(omitReaderType(record.props), null, 2) : "",
		});
		setModalOpen(true);
	};

	const handleDelete = (record: InfraDataSource) => {
		Modal.confirm({
			title: "确认删除",
			content: `确定删除数据源 "${record.name}" 吗？`,
			okType: "danger",
			onOk: async () => {
				try {
					await dataSourcesService.remove(record.id);
					message.success("已删除数据源");
					loadList();
				} catch (error: any) {
					message.error(error?.message || "删除失败");
				}
			},
		});
	};

	const handleTest = async (record: InfraDataSource) => {
		if (!record.jdbcUrl) {
			message.warning("非 JDBC 数据源无需测试连接");
			return;
		}
		try {
			setTestingId(record.id);
			await dataSourcesService.test(record.id);
			message.success("连接测试已触发");
			loadList();
		} catch (error: any) {
			message.error(error?.message || "连接测试失败");
		} finally {
			setTestingId(null);
		}
	};

	const handleSave = async () => {
		try {
			const values = await form.validateFields();
			setSaving(true);
			const jdbc = isJdbcType(values.type, values.jdbcUrl);
			let props = values.propsJson ? parseJson(values.propsJson) : undefined;
			if (values.readerType) {
				props = { ...(props || {}), readerType: values.readerType };
			}
			const payload: DataSourceUpsertPayload = {
				name: String(values.name).trim(),
				type: String(values.type).trim(),
				jdbcUrl: jdbc ? String(values.jdbcUrl || "").trim() || undefined : undefined,
				username: jdbc ? String(values.username || "").trim() || undefined : undefined,
				description: String(values.description || "").trim() || undefined,
				props: props && Object.keys(props).length ? props : undefined,
				secrets: values.password ? { password: values.password } : undefined,
			};
			if (editing) {
				await dataSourcesService.update(editing.id, payload);
				message.success("数据源已更新");
			} else {
				await dataSourcesService.create(payload);
				message.success("数据源已创建");
			}
			setModalOpen(false);
			loadList();
		} catch (error: any) {
			if (error?.errorFields) return;
			message.error(error?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	};

	const columns = useMemo(
		() => [
			{ title: "名称", dataIndex: "name", key: "name", width: 180 },
			{ title: "类型", dataIndex: "type", key: "type", width: 120 },
			{ title: "JDBC URL", dataIndex: "jdbcUrl", key: "jdbcUrl", ellipsis: true },
			{ title: "用户名", dataIndex: "username", key: "username", width: 140 },
			{
				title: "状态",
				dataIndex: "status",
				key: "status",
				width: 120,
				render: (value: string) => {
					const text = value || "active";
					return <Tag color={text === "active" ? "success" : "default"}>{text}</Tag>;
				},
			},
			{
				title: "最近验证",
				dataIndex: "lastVerifiedAt",
				key: "lastVerifiedAt",
				width: 180,
				render: (value: string) => formatTime(value),
			},
			{
				title: "操作",
				key: "action",
				width: 220,
				render: (_: any, record: InfraDataSource) => (
					<Space>
						<Button size="small" icon={<ExperimentOutlined />} loading={testingId === record.id} onClick={() => handleTest(record)}>
							测试
						</Button>
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
		[testingId]
	);

	const typeValue = Form.useWatch("type", form);
	const jdbcValue = Form.useWatch("jdbcUrl", form);
	const jdbcRequired = isJdbcType(typeValue, jdbcValue);

	return (
		<Card
			title="数据源连接"
			extra={
				<Space>
					<Button icon={<ReloadOutlined />} onClick={loadList} disabled={loading}>
						刷新
					</Button>
					<Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
						新增数据源
					</Button>
				</Space>
			}
		>
			<Table
				rowKey="id"
				columns={columns as any}
				dataSource={list}
				loading={loading}
				pagination={{ pageSize: 12 }}
			/>

			<Modal
				title={editing ? "编辑数据源" : "新增数据源"}
				open={modalOpen}
				onCancel={() => setModalOpen(false)}
				onOk={handleSave}
				okText="保存"
				confirmLoading={saving}
				destroyOnClose
			>
				<Form layout="vertical" form={form} preserve={false}>
					<Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
						<Input placeholder="例如：ERP 数据库" />
					</Form.Item>
					<Form.Item name="type" label="类型" rules={[{ required: true, message: "请选择类型" }]}>
						<Select options={TYPE_OPTIONS} placeholder="请选择数据源类型" />
					</Form.Item>
					<Form.Item
						name="jdbcUrl"
						label="JDBC URL"
						rules={[{ required: jdbcRequired, message: "请输入 JDBC URL" }]}
					>
						<Input placeholder="jdbc:xxx://host:port/db" />
					</Form.Item>
					<Form.Item
						name="username"
						label="用户名"
						rules={[{ required: jdbcRequired, message: "请输入用户名" }]}
					>
						<Input placeholder="数据库账号" />
					</Form.Item>
					<Form.Item
						name="password"
						label={editing ? "密码（留空保持不变）" : "密码"}
						rules={[{ required: jdbcRequired && !editing, message: "请输入密码" }]}
					>
						<Input.Password placeholder="******" />
					</Form.Item>
					{!jdbcRequired && (
						<Form.Item
							name="readerType"
							label="Reader 类型"
							rules={[{ required: true, message: "请输入 Reader 类型" }]}
						>
							<Input placeholder="例如：excelreader、httpreader" />
						</Form.Item>
					)}
					<Form.Item name="description" label="描述">
						<Input.TextArea rows={2} placeholder="可选" />
					</Form.Item>
					<Form.Item
						name="propsJson"
						label="扩展配置 JSON（可选）"
						rules={[
							{
								validator: async (_: any, value: string) => {
									if (!value) return Promise.resolve();
									try {
										parseJson(value);
										return Promise.resolve();
									} catch {
										return Promise.reject(new Error("JSON 格式错误"));
									}
								},
							},
						]}
					>
						<Input.TextArea rows={4} placeholder='{"readerConfig":{"column":["*"]}}' />
					</Form.Item>
					{editing?.lastError && (
						<Text type="danger">最近错误：{editing.lastError}</Text>
					)}
				</Form>
			</Modal>
		</Card>
	);
}
