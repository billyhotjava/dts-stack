import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Button,
	Card,
	Form,
	Input,
	InputNumber,
	Modal,
	Select,
	Space,
	Switch,
	Table,
	Tag,
	Tooltip,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import {
	DeleteOutlined,
	EditOutlined,
	ExclamationCircleOutlined,
	LinkOutlined,
	PlusOutlined,
	ReloadOutlined,
	SearchOutlined,
} from "@ant-design/icons";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import reportsService, { type ReportLink, type ReportLinkUpsertRequest } from "@/api/services/reportsService";
import { useUserRoles } from "@/store/userStore";
import { normalizeBiLinkForSave, resolveBiLinkForOpen } from "@/utils/biLinkUrl";

const { Text } = Typography;

type FormValues = {
	code: string;
	title: string;
	url: string;
	engine?: string;
	reportType?: string;
	deptCodes?: string[];
	roleCodes?: string[];
	classification: string;
	enabled?: boolean;
	sortOrder?: number;
};

const CLASSIFICATION_OPTIONS = [
	{ label: "公开", value: "PUBLIC" },
	{ label: "内部", value: "INTERNAL" },
	{ label: "秘密", value: "SECRET" },
	{ label: "机密", value: "CONFIDENTIAL" },
];

const ENGINE_OPTIONS = [
	{ label: "河图 (Hetu)", value: "HETU" },
	{ label: "Tableau", value: "TABLEAU" },
	{ label: "Superset", value: "SUPERSET" },
	{ label: "Power BI", value: "POWERBI" },
	{ label: "FineBI", value: "FINEBI" },
	{ label: "Looker", value: "LOOKER" },
	{ label: "其它", value: "OTHER" },
];

const REPORT_TYPES = [
	{ label: "驾驶舱", value: "COCKPIT" },
	{ label: "主题看板", value: "DASHBOARD" },
	{ label: "分析报表", value: "REPORT" },
	{ label: "业务应用", value: "APP" },
];

const normalizeCodes = (value?: string[] | string | null): string[] => {
	if (!value) return [];
	const raw = Array.isArray(value) ? value : String(value).split(/[,，\s]+/);
	const cleaned = raw.map((item) => String(item || "").trim()).filter((item) => item.length > 0);
	return cleaned;
};

const normalizeText = (value?: string | null) => {
	const text = String(value || "").trim();
	return text.length ? text : undefined;
};

const toRequestPayload = (values: FormValues): ReportLinkUpsertRequest => ({
	code: normalizeText(values.code) || "",
	title: normalizeText(values.title) || "",
	url: normalizeBiLinkForSave(values.url, values.engine),
	engine: normalizeText(values.engine)?.toUpperCase() || "HETU",
	reportType: normalizeText(values.reportType),
	deptCodes: normalizeCodes(values.deptCodes),
	roleCodes: normalizeCodes(values.roleCodes),
	classification: normalizeText(values.classification)?.toUpperCase() || "INTERNAL",
	enabled: values.enabled !== false,
	sortOrder: typeof values.sortOrder === "number" ? values.sortOrder : undefined,
});

const toEditDefaults = (record: ReportLink): FormValues => ({
	code: record.code || "",
	title: record.title || "",
	url: record.url || "",
	engine: record.engine || "HETU",
	reportType: record.reportType || undefined,
	deptCodes: Array.isArray(record.deptCodes) ? record.deptCodes : [],
	roleCodes: Array.isArray(record.roleCodes) ? record.roleCodes : [],
	classification: record.classification || "INTERNAL",
	enabled: record.enabled !== false,
	sortOrder: typeof record.sortOrder === "number" ? record.sortOrder : undefined,
});

const renderClassificationTag = (value?: string | null) => {
	const key = String(value || "").toUpperCase();
	const color =
		key === "PUBLIC" ? "green" : key === "INTERNAL" ? "blue" : key === "SECRET" ? "orange" : key === "CONFIDENTIAL" ? "red" : "default";
	const label =
		key === "PUBLIC" ? "公开" : key === "INTERNAL" ? "内部" : key === "SECRET" ? "秘密" : key === "CONFIDENTIAL" ? "机密" : value || "-";
	return <Tag color={color}>{label}</Tag>;
};

const renderCodeTags = (values?: string[]) => {
	if (!values || values.length === 0) {
		return <Tag>全部</Tag>;
	}
	return (
		<Space size={[4, 4]} wrap>
			{values.map((item) => (
				<Tag key={item}>{item}</Tag>
			))}
		</Space>
	);
};

type Props = { embedded?: boolean };

export default function Page({ embedded }: Props) {
	const roles = useUserRoles();
	const hasPurgePermission = useMemo(() => {
		const normalized = new Set((roles || []).map((role) => String(role || "").trim().toUpperCase()));
		return normalized.has("ROLE_OP_ADMIN") || normalized.has("OPADMIN");
	}, [roles]);
	const [loading, setLoading] = useState(false);
	const [saving, setSaving] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [typeFilter, setTypeFilter] = useState<string | undefined>();
	const [enabledOnly, setEnabledOnly] = useState(false);
	const [records, setRecords] = useState<ReportLink[]>([]);
	const [editing, setEditing] = useState<ReportLink | null>(null);
	const [modalOpen, setModalOpen] = useState(false);
	const [form] = Form.useForm<FormValues>();

	const fetchList = useCallback(async () => {
		setLoading(true);
		try {
			const resp = await reportsService.listAll({
				keyword: normalizeText(keyword),
				type: normalizeText(typeFilter),
				enabledOnly,
			});
			setRecords(Array.isArray(resp) ? (resp as ReportLink[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "加载外部 BI 链接失败");
		} finally {
			setLoading(false);
		}
	}, [enabledOnly, keyword, typeFilter]);

	useEffect(() => {
		void fetchList();
	}, [fetchList]);

	const openCreate = () => {
		setEditing(null);
		form.resetFields();
		form.setFieldsValue({
			engine: "HETU",
			classification: "INTERNAL",
			enabled: true,
			deptCodes: [],
			roleCodes: [],
		});
		setModalOpen(true);
	};

	const openEdit = (record: ReportLink) => {
		setEditing(record);
		form.resetFields();
		form.setFieldsValue(toEditDefaults(record));
		setModalOpen(true);
	};

	const handleSave = async () => {
		try {
			const values = await form.validateFields();
			const payload = toRequestPayload(values);
			setSaving(true);
			if (editing?.id) {
				await reportsService.update(editing.id, payload);
				toast.success("外部 BI 链接已更新");
			} else {
				await reportsService.create(payload);
				toast.success("外部 BI 链接已创建");
			}
			setModalOpen(false);
			setEditing(null);
			form.resetFields();
			await fetchList();
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "保存外部 BI 链接失败");
		} finally {
			setSaving(false);
		}
	};

	const handleToggle = async (record: ReportLink, enabled: boolean) => {
		if (!record?.id) return;
		try {
			const payload: ReportLinkUpsertRequest = {
				...toRequestPayload(toEditDefaults(record)),
				enabled,
			};
			await reportsService.update(record.id, payload);
			setRecords((prev) => prev.map((item) => (item.id === record.id ? { ...item, enabled } : item)));
			toast.success(enabled ? "已启用" : "已停用");
		} catch (error: any) {
			toast.error(error?.message || "更新状态失败");
		}
	};

	const handleDisable = (record: ReportLink) => {
		if (!record?.id) return;
		Modal.confirm({
			title: "确认停用该 BI 链接？",
			icon: <ExclamationCircleOutlined />,
			content: "停用后入口将不再对业务端展示。",
			okText: "确认停用",
			cancelText: "取消",
			onOk: async () => {
				try {
					await reportsService.disable(record.id);
					setRecords((prev) => prev.map((item) => (item.id === record.id ? { ...item, enabled: false } : item)));
					toast.success("已停用");
				} catch (error: any) {
					toast.error(error?.message || "停用失败");
				}
			},
		});
	};

	const handlePurge = (record: ReportLink) => {
		if (!record?.id) return;
		Modal.confirm({
			title: "确认物理删除该 BI 链接？",
			icon: <ExclamationCircleOutlined />,
			content: "此操作不可恢复，仅 OP_ADMIN 可执行。",
			okText: "确认删除",
			okButtonProps: { danger: true },
			cancelText: "取消",
			onOk: async () => {
				try {
					await reportsService.purge(record.id);
					setRecords((prev) => prev.filter((item) => item.id !== record.id));
					toast.success("已物理删除");
				} catch (error: any) {
					toast.error(error?.message || "物理删除失败");
				}
			},
		});
	};

	const handleOpen = async (record: ReportLink) => {
		const url = resolveBiLinkForOpen(record?.url, record?.engine);
		if (!url) {
			toast.error("未配置跳转地址");
			return;
		}
		window.open(url, "_blank", "noopener,noreferrer");
		try {
			await reportsService.visit({
				id: record?.id,
				code: record?.code,
				title: record?.title,
				url,
				engine: record?.engine,
				classification: record?.classification,
			});
		} catch (error: any) {
			toast.error(error?.message || "访问记录失败");
		}
	};

	const columns: ColumnsType<ReportLink> = useMemo(
		() => [
			{
				title: "名称",
				dataIndex: "title",
				render: (value: string, record) => (
					<Space direction="vertical" size={2}>
						<Text strong>{value || "-"}</Text>
						<Text type="secondary" style={{ fontSize: 12 }}>
							{record?.code || "-"}
						</Text>
					</Space>
				),
			},
			{
				title: "引擎",
				dataIndex: "engine",
				width: 140,
				render: (value: string) => <Tag>{value || "HETU"}</Tag>,
			},
			{
				title: "类型",
				dataIndex: "reportType",
				width: 120,
				render: (value: string) => (value ? <Tag color="blue">{value}</Tag> : <Text type="secondary">-</Text>),
			},
			{
				title: "密级",
				dataIndex: "classification",
				width: 120,
				render: (value: string) => renderClassificationTag(value),
			},
			{
				title: "可见范围",
				dataIndex: "deptCodes",
				render: (_value, record) => (
					<Space direction="vertical" size={4}>
						<div>
							<Text type="secondary">部门：</Text>
							{renderCodeTags(record?.deptCodes)}
						</div>
						<div>
							<Text type="secondary">角色：</Text>
							{renderCodeTags(record?.roleCodes)}
						</div>
					</Space>
				),
			},
			{
				title: "状态",
				dataIndex: "enabled",
				width: 120,
				render: (value: boolean, record) => (
					<Switch checked={value !== false} onChange={(checked) => handleToggle(record, checked)} />
				),
			},
			{
				title: "更新时间",
				dataIndex: "updatedAt",
				width: 180,
				render: (value: string) => (value ? new Date(value).toLocaleString() : "-"),
			},
			{
				title: "操作",
				key: "actions",
				width: 180,
				render: (_value, record) => (
					<Space>
						<Tooltip title="打开">
							<Button type="link" icon={<LinkOutlined />} onClick={() => handleOpen(record)} />
						</Tooltip>
						<Tooltip title="编辑">
							<Button type="link" icon={<EditOutlined />} onClick={() => openEdit(record)} />
						</Tooltip>
						<Tooltip title="停用">
							<Button type="link" danger icon={<DeleteOutlined />} onClick={() => handleDisable(record)} />
						</Tooltip>
						{hasPurgePermission ? (
							<Tooltip title="物理删除">
								<Button type="link" danger onClick={() => handlePurge(record)}>
									物理删除
								</Button>
							</Tooltip>
						) : null}
					</Space>
				),
			},
		],
		[hasPurgePermission],
	);

	return (
		<div className="space-y-6">
			{!embedded && (
				<PageHeader
					title="数据可视化 / 外部 BI 集成"
					description="统一管理河图、Tableau、Superset 等外部 BI 的入口与访问策略。"
					actions={
						<div className="flex items-center gap-2">
							<Button icon={<PlusOutlined />} type="primary" onClick={openCreate}>
								新增 BI 链接
							</Button>
							<Button icon={<ReloadOutlined />} onClick={() => fetchList()}>
								刷新
							</Button>
						</div>
					}
				/>
			)}
			{embedded && (
				<div className="flex items-center justify-between">
					<div className="flex items-center gap-2">
						<Button icon={<PlusOutlined />} type="primary" onClick={openCreate}>
							新增 BI 链接
						</Button>
						<Button icon={<ReloadOutlined />} onClick={() => fetchList()}>
							刷新
						</Button>
					</div>
				</div>
			)}

			<Card>
				<div className="flex flex-wrap items-center justify-between gap-3 pb-4">
					<Space>
						<Input
							allowClear
							placeholder="搜索名称 / 标识"
							prefix={<SearchOutlined />}
							value={keyword}
							onChange={(event) => setKeyword(event.target.value)}
							onPressEnter={() => fetchList()}
							style={{ width: 260 }}
						/>
						<Select
							allowClear
							placeholder="类型筛选"
							options={REPORT_TYPES}
							value={typeFilter}
							onChange={(value) => setTypeFilter(value)}
							style={{ width: 160 }}
						/>
						<Space size={6}>
							<Switch checked={enabledOnly} onChange={setEnabledOnly} />
							<Text type="secondary">仅显示启用</Text>
						</Space>
					</Space>
					<Text type="secondary">共 {records.length} 条</Text>
				</div>

				<Table
					rowKey={(record) => record.id || record.code}
					loading={loading}
					dataSource={records}
					columns={columns}
					pagination={{ pageSize: 8 }}
					locale={{ emptyText: <EmptyState title="暂无外部 BI" description="请先添加 BI 入口或同步河图连接。" /> }}
				/>
			</Card>

			<Modal
				open={modalOpen}
				title={editing ? "编辑 BI 链接" : "新增 BI 链接"}
				onCancel={() => {
					setModalOpen(false);
					setEditing(null);
				}}
				onOk={handleSave}
				okText={editing ? "保存修改" : "创建链接"}
				confirmLoading={saving}
				width={640}
				destroyOnClose
			>
				<Form layout="vertical" form={form}>
					<Form.Item
						name="title"
						label="名称"
						rules={[{ required: true, message: "请输入 BI 名称" }]}
					>
						<Input placeholder="例如：河图驾驶舱 / 财务看板" />
					</Form.Item>
					<Form.Item
						name="code"
						label="标识"
						rules={[{ required: true, message: "请输入唯一标识" }]}
						extra="建议使用英文或数字，作为唯一标识。"
					>
						<Input placeholder="例如：hetu-main-dashboard" />
					</Form.Item>
					<Form.Item
						name="url"
						label="访问地址"
						rules={[{ required: true, message: "请输入访问地址" }]}
					>
						<Input placeholder="例如：http://bi.internal/hetu/dashboard" />
					</Form.Item>
					<Space size="large" className="w-full">
						<Form.Item name="engine" label="引擎">
							<Select
								options={ENGINE_OPTIONS}
								placeholder="选择或输入 BI 引擎"
								showSearch
								filterOption={(input, option) =>
									String(option?.label || "").toLowerCase().includes(input.toLowerCase())
								}
							/>
						</Form.Item>
						<Form.Item
							name="classification"
							label="密级"
							rules={[{ required: true, message: "请选择密级" }]}
						>
							<Select options={CLASSIFICATION_OPTIONS} />
						</Form.Item>
					</Space>
					<Space size="large" className="w-full">
						<Form.Item name="reportType" label="类型">
							<Select
								allowClear
								options={REPORT_TYPES}
								placeholder="可选"
							/>
						</Form.Item>
						<Form.Item name="sortOrder" label="排序">
							<InputNumber min={0} placeholder="默认 0" className="w-full" />
						</Form.Item>
					</Space>
					<Form.Item name="deptCodes" label="可见部门">
						<Select
							mode="tags"
							placeholder="输入部门编码，回车确认"
							tokenSeparators={[",", "，", " "]}
						/>
					</Form.Item>
					<Form.Item name="roleCodes" label="可见角色">
						<Select
							mode="tags"
							placeholder="输入角色编码，回车确认"
							tokenSeparators={[",", "，", " "]}
						/>
					</Form.Item>
					<Form.Item name="enabled" label="启用状态" valuePropName="checked">
						<Switch checkedChildren="启用" unCheckedChildren="停用" />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
