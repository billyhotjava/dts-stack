import { useCallback, useEffect, useMemo, useState } from "react";
import dayjs from "dayjs";
import { toast } from "sonner";
import { Button, Card, DatePicker, Form, Input, InputNumber, Modal, Select, Space, Switch, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useUserInfo } from "@/store/userStore";
import { createExchangeFile, deleteExchangeFile, listExchangeFiles, updateExchangeFile } from "@/api/platformApi";

const { Text } = Typography;

const ENTRY_KEY = "EXPLORE_ETL_FILE_EXCHANGE";

const STATUS_OPTIONS = [
	{ value: "RECEIVED", label: "已接收" },
	{ value: "PROCESSING", label: "处理中" },
	{ value: "SUCCESS", label: "已完成" },
	{ value: "FAILED", label: "失败" },
];

const CLASSIFICATION_OPTIONS = [
	{ value: "PUBLIC", label: "公开" },
	{ value: "INTERNAL", label: "内部" },
	{ value: "SECRET", label: "秘密" },
	{ value: "CONFIDENTIAL", label: "机密" },
];

type ExchangeFile = {
	id: string;
	entryKey: string;
	fileName: string;
	filePath?: string | null;
	fileSize?: number | null;
	checksum?: string | null;
	batchCode?: string | null;
	sourceSystem?: string | null;
	status?: string | null;
	receivedAt?: string | null;
	processedAt?: string | null;
	errorMessage?: string | null;
	classification?: string | null;
	ownerDept?: string | null;
	externalRef?: string | null;
	props?: string | null;
	enabled?: boolean | null;
};

type FilterState = {
	status: string;
	keyword: string;
	enabledOnly: boolean;
};

const formatTime = (value?: string | null) => {
	if (!value) return "-";
	const parsed = dayjs(value);
	return parsed.isValid() ? parsed.format("YYYY-MM-DD HH:mm") : value;
};

const formatBytes = (value?: number | null) => {
	if (typeof value !== "number" || Number.isNaN(value)) return "-";
	if (value < 1024) return `${value} B`;
	const kb = value / 1024;
	if (kb < 1024) return `${kb.toFixed(1)} KB`;
	const mb = kb / 1024;
	if (mb < 1024) return `${mb.toFixed(1)} MB`;
	return `${(mb / 1024).toFixed(1)} GB`;
};

const statusTagColor = (status?: string | null) => {
	switch ((status || "").toUpperCase()) {
		case "RECEIVED":
			return "blue";
		case "PROCESSING":
			return "gold";
		case "SUCCESS":
			return "green";
		case "FAILED":
			return "red";
		default:
			return "default";
	}
};

export default function FileExchangePage() {
	const userInfo = useUserInfo() as any;
	const roles: string[] = useMemo(() => (Array.isArray(userInfo?.roles) ? userInfo.roles.map((r: any) => String(r ?? "").toUpperCase()) : []), [userInfo]);
	const canEdit = useMemo(
		() =>
			roles.includes("ROLE_OP_ADMIN") ||
			roles.includes("ROLE_ADMIN") ||
			roles.includes("ROLE_INST_DATA_OWNER") ||
			roles.includes("ROLE_INST_LEADER") ||
			roles.includes("ROLE_DEPT_DATA_OWNER") ||
			roles.includes("ROLE_DEPT_LEADER"),
		[roles],
	);

	const [form] = Form.useForm();
	const [loading, setLoading] = useState(false);
	const [rows, setRows] = useState<ExchangeFile[]>([]);
	const [filters, setFilters] = useState<FilterState>({ status: "ALL", keyword: "", enabledOnly: true });

	const [editOpen, setEditOpen] = useState(false);
	const [editMode, setEditMode] = useState<"create" | "edit">("create");
	const [editing, setEditing] = useState<ExchangeFile | null>(null);
	const [saving, setSaving] = useState(false);

	const load = useCallback(async () => {
		setLoading(true);
		try {
			const params: any = {
				entryKey: ENTRY_KEY,
				enabledOnly: filters.enabledOnly,
				limit: 200,
			};
			if (filters.status !== "ALL") params.status = filters.status;
			if (filters.keyword.trim()) params.keyword = filters.keyword.trim();
			const data: any = await listExchangeFiles(params);
			setRows(Array.isArray(data) ? data : []);
		} catch (err: any) {
			console.error(err);
			toast.error(err?.message ?? "加载接入台账失败");
		} finally {
			setLoading(false);
		}
	}, [filters]);

	useEffect(() => {
		void load();
	}, [load]);

	const openCreate = () => {
		setEditMode("create");
		setEditing(null);
		form.resetFields();
		form.setFieldsValue({
			status: "RECEIVED",
			classification: "INTERNAL",
			enabled: true,
		});
		setEditOpen(true);
	};

	const openEdit = (row: ExchangeFile) => {
		setEditMode("edit");
		setEditing(row);
		form.resetFields();
		form.setFieldsValue({
			fileName: row.fileName,
			filePath: row.filePath ?? "",
			fileSize: row.fileSize ?? undefined,
			checksum: row.checksum ?? "",
			batchCode: row.batchCode ?? "",
			sourceSystem: row.sourceSystem ?? "",
			status: row.status ?? "RECEIVED",
			receivedAt: row.receivedAt ? dayjs(row.receivedAt) : null,
			processedAt: row.processedAt ? dayjs(row.processedAt) : null,
			errorMessage: row.errorMessage ?? "",
			classification: row.classification ?? "INTERNAL",
			ownerDept: row.ownerDept ?? "",
			externalRef: row.externalRef ?? "",
			props: row.props ?? "",
			enabled: row.enabled ?? true,
		});
		setEditOpen(true);
	};

	const submitEdit = async () => {
		try {
			const values = await form.validateFields();
			setSaving(true);
			const payload = {
				entryKey: ENTRY_KEY,
				fileName: values.fileName?.trim(),
				filePath: values.filePath?.trim() || null,
				fileSize: typeof values.fileSize === "number" ? values.fileSize : null,
				checksum: values.checksum?.trim() || null,
				batchCode: values.batchCode?.trim() || null,
				sourceSystem: values.sourceSystem?.trim() || null,
				status: values.status,
				receivedAt: values.receivedAt ? values.receivedAt.toISOString() : null,
				processedAt: values.processedAt ? values.processedAt.toISOString() : null,
				errorMessage: values.errorMessage?.trim() || null,
				classification: values.classification,
				ownerDept: values.ownerDept?.trim() || null,
				externalRef: values.externalRef?.trim() || null,
				props: values.props?.trim() || null,
				enabled: Boolean(values.enabled),
			};
			if (editMode === "create") {
				await createExchangeFile(payload);
				toast.success("已新增接入台账");
			} else if (editing?.id) {
				await updateExchangeFile(editing.id, payload);
				toast.success("已更新接入台账");
			}
			setEditOpen(false);
			void load();
		} catch (err: any) {
			if (err?.errorFields) return;
			console.error(err);
			toast.error(err?.message ?? "保存失败");
		} finally {
			setSaving(false);
		}
	};

	const confirmDelete = (row: ExchangeFile) => {
		Modal.confirm({
			title: "确认禁用",
			content: `确定禁用文件 “${row.fileName}” 吗？`,
			okText: "禁用",
			okButtonProps: { danger: true },
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteExchangeFile(row.id);
					toast.success("已禁用");
					void load();
				} catch (err: any) {
					console.error(err);
					toast.error(err?.message ?? "禁用失败");
				}
			},
		});
	};

	const columns: ColumnsType<ExchangeFile> = useMemo(
		() => [
			{ title: "文件名", dataIndex: "fileName", key: "fileName", width: 220, ellipsis: true },
			{ title: "来源系统", dataIndex: "sourceSystem", key: "sourceSystem", width: 140, ellipsis: true },
			{
				title: "状态",
				dataIndex: "status",
				key: "status",
				width: 110,
				render: (value: string) => <Tag color={statusTagColor(value)}>{value || "-"}</Tag>,
			},
			{ title: "批次号", dataIndex: "batchCode", key: "batchCode", width: 140, ellipsis: true },
			{
				title: "大小",
				dataIndex: "fileSize",
				key: "fileSize",
				width: 120,
				render: (value: number) => formatBytes(value),
			},
			{ title: "校验和", dataIndex: "checksum", key: "checksum", width: 160, ellipsis: true },
			{ title: "文件路径", dataIndex: "filePath", key: "filePath", width: 220, ellipsis: true },
			{
				title: "密级",
				dataIndex: "classification",
				key: "classification",
				width: 110,
				render: (value: string) => value || "-",
			},
			{ title: "归属部门", dataIndex: "ownerDept", key: "ownerDept", width: 140, ellipsis: true },
			{
				title: "接收时间",
				dataIndex: "receivedAt",
				key: "receivedAt",
				width: 160,
				render: (value: string) => formatTime(value),
			},
			{
				title: "处理时间",
				dataIndex: "processedAt",
				key: "processedAt",
				width: 160,
				render: (value: string) => formatTime(value),
			},
			{
				title: "启用",
				dataIndex: "enabled",
				key: "enabled",
				width: 90,
				render: (value: boolean) => (value === false ? <Tag color="default">否</Tag> : <Tag color="green">是</Tag>),
			},
			{
				title: "操作",
				key: "actions",
				width: 140,
				fixed: "right",
				render: (_, row) => (
					<Space>
						<Button type="link" size="small" disabled={!canEdit} onClick={() => openEdit(row)}>
							编辑
						</Button>
						<Button type="link" size="small" danger disabled={!canEdit} onClick={() => confirmDelete(row)}>
							禁用
						</Button>
					</Space>
				),
			},
		],
		[canEdit],
	);

	return (
		<div className="space-y-4">
			<Card>
				<div className="flex flex-col gap-3 p-4 md:flex-row md:items-center md:justify-between">
					<div>
						<div className="text-lg font-semibold">数据接入台账</div>
						<Text type="secondary">登记接入文件、校验结果与处理状态，作为后续资产入库依据。</Text>
					</div>
					<Space>
						<Button onClick={() => void load()} loading={loading}>
							刷新
						</Button>
						<Button type="primary" onClick={openCreate} disabled={!canEdit}>
							新增记录
						</Button>
					</Space>
				</div>
				<div className="flex flex-wrap items-center gap-3 px-4 pb-4">
					<Input
						allowClear
						placeholder="文件名/批次号/来源系统"
						value={filters.keyword}
						onChange={(e) => setFilters((prev) => ({ ...prev, keyword: e.target.value }))}
						style={{ width: 220 }}
					/>
					<Select
						value={filters.status}
						onChange={(value) => setFilters((prev) => ({ ...prev, status: value }))}
						style={{ width: 160 }}
					>
						<Select.Option value="ALL">全部状态</Select.Option>
						{STATUS_OPTIONS.map((opt) => (
							<Select.Option key={opt.value} value={opt.value}>
								{opt.label}
							</Select.Option>
						))}
					</Select>
					<div className="flex items-center gap-2 text-sm text-muted-foreground">
						<Switch checked={filters.enabledOnly} onChange={(value) => setFilters((prev) => ({ ...prev, enabledOnly: value }))} />
						<span>仅显示启用</span>
					</div>
					<Button type="primary" onClick={() => void load()}>
						查询
					</Button>
					<Text type="secondary">共 {rows.length} 条</Text>
				</div>
			</Card>

			<Card>
				<Table
					rowKey="id"
					loading={loading}
					columns={columns}
					dataSource={rows}
					pagination={false}
					scroll={{ x: 1400 }}
				/>
			</Card>

			<Modal
				open={editOpen}
				title={editMode === "create" ? "新增接入记录" : "编辑接入记录"}
				onCancel={() => setEditOpen(false)}
				onOk={submitEdit}
				okText="保存"
				cancelText="取消"
				confirmLoading={saving}
				width={720}
				destroyOnClose
			>
				<Form form={form} layout="vertical" preserve={false}>
					<Form.Item name="fileName" label="文件名" rules={[{ required: true, message: "请输入文件名" }]}>
						<Input placeholder="例如：erp_customer_20250101.xlsx" />
					</Form.Item>
					<Form.Item name="sourceSystem" label="来源系统">
						<Input placeholder="例如：ERP/CRM" />
					</Form.Item>
					<Form.Item name="filePath" label="文件路径">
						<Input placeholder="/data/incoming/..." />
					</Form.Item>
					<Form.Item name="fileSize" label="文件大小（字节）">
						<InputNumber min={0} precision={0} style={{ width: "100%" }} placeholder="例如：1048576" />
					</Form.Item>
					<Form.Item name="checksum" label="校验和">
						<Input placeholder="MD5/SHA256" />
					</Form.Item>
					<Form.Item name="batchCode" label="批次号">
						<Input placeholder="批次或批量标识" />
					</Form.Item>
					<Form.Item name="status" label="处理状态" rules={[{ required: true, message: "请选择状态" }]}>
						<Select>
							{STATUS_OPTIONS.map((opt) => (
								<Select.Option key={opt.value} value={opt.value}>
									{opt.label}
								</Select.Option>
							))}
						</Select>
					</Form.Item>
					<Form.Item name="receivedAt" label="接收时间">
						<DatePicker showTime style={{ width: "100%" }} />
					</Form.Item>
					<Form.Item name="processedAt" label="处理时间">
						<DatePicker showTime style={{ width: "100%" }} />
					</Form.Item>
					<Form.Item name="errorMessage" label="错误信息">
						<Input.TextArea rows={3} placeholder="处理失败时填写原因" />
					</Form.Item>
					<Form.Item name="classification" label="密级" rules={[{ required: true, message: "请选择密级" }]}>
						<Select>
							{CLASSIFICATION_OPTIONS.map((opt) => (
								<Select.Option key={opt.value} value={opt.value}>
									{opt.label}
								</Select.Option>
							))}
						</Select>
					</Form.Item>
					<Form.Item name="ownerDept" label="归属部门">
						<Input placeholder="部门编码/名称" />
					</Form.Item>
					<Form.Item name="externalRef" label="外部引用">
						<Input placeholder="外部系统ID或工单号" />
					</Form.Item>
					<Form.Item name="props" label="扩展属性">
						<Input.TextArea rows={2} placeholder="JSON或说明文本" />
					</Form.Item>
					<Form.Item name="enabled" label="启用状态" valuePropName="checked">
						<Switch />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
