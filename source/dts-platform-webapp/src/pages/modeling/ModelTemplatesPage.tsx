import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Button,
	Card,
	Form,
	Input,
	Modal,
	Select,
	Space,
	Table,
	Tag,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import {
	archiveModelingPlan,
	createModelingPlan,
	listModelingPlans,
	publishModelingPlan,
	restoreModelingPlan,
	updateModelingPlan,
} from "@/api/platformApi";

const { Text } = Typography;

const formatDateTime = (value?: string) => {
	if (!value) return "-";
	try {
		return new Date(value).toLocaleString();
	} catch {
		return value;
	}
};

const normalizeText = (value?: string) => String(value || "").trim();
const normalizeUpper = (value?: string) => normalizeText(value).toUpperCase();

type ProjectSpace = {
	id?: string;
	name?: string;
	domain?: string;
	scope?: string;
	status?: string;
	version?: string;
	versionNotes?: string;
	owner?: string;
	ownerDept?: string;
	tags?: string;
	content?: string;
	createdDate?: string;
	lastModifiedDate?: string;
};

const STATUS_OPTIONS = ["DRAFT", "PUBLISHED", "ARCHIVED"];

const statusColor = (status?: string) => {
	const key = normalizeUpper(status);
	if (key === "PUBLISHED") return "green";
	if (key === "ARCHIVED") return "default";
	return "gold";
};

export default function Page() {
	const [loading, setLoading] = useState(false);
	const [spaces, setSpaces] = useState<ProjectSpace[]>([]);
	const [keyword, setKeyword] = useState("");
	const [status, setStatus] = useState<string | null>(null);
	const [editOpen, setEditOpen] = useState(false);
	const [editMode, setEditMode] = useState<"create" | "edit">("create");
	const [editing, setEditing] = useState<ProjectSpace | null>(null);
	const [saving, setSaving] = useState(false);
	const [publishOpen, setPublishOpen] = useState(false);
	const [publishing, setPublishing] = useState(false);
	const [publishTarget, setPublishTarget] = useState<ProjectSpace | null>(null);
	const [form] = Form.useForm();
	const [publishForm] = Form.useForm();

	const loadSpaces = useCallback(async () => {
		setLoading(true);
		try {
			const resp = (await listModelingPlans({
				keyword: normalizeText(keyword) || undefined,
				status: status || undefined,
			})) as ProjectSpace[];
			setSpaces(Array.isArray(resp) ? resp : []);
		} catch (err: any) {
			toast.error(err?.message || "加载项目空间失败");
		} finally {
			setLoading(false);
		}
	}, [keyword, status]);

	useEffect(() => {
		void loadSpaces();
	}, [loadSpaces]);

	const stats = useMemo(() => {
		const total = spaces.length;
		const draft = spaces.filter((row) => normalizeUpper(row.status) === "DRAFT").length;
		const published = spaces.filter((row) => normalizeUpper(row.status) === "PUBLISHED").length;
		const archived = spaces.filter((row) => normalizeUpper(row.status) === "ARCHIVED").length;
		return { total, draft, published, archived };
	}, [spaces]);

	const openCreate = () => {
		setEditMode("create");
		setEditing(null);
		form.resetFields();
		form.setFieldsValue({ status: "DRAFT", version: "v1" });
		setEditOpen(true);
	};

	const openEdit = (row: ProjectSpace) => {
		setEditMode("edit");
		setEditing(row);
		form.resetFields();
		form.setFieldsValue({
			name: row.name,
			domain: row.domain,
			scope: row.scope,
			status: row.status,
			version: row.version,
			versionNotes: row.versionNotes,
			owner: row.owner,
			ownerDept: row.ownerDept,
			tags: row.tags,
			content: row.content,
		});
		setEditOpen(true);
	};

	const submitEdit = async () => {
		setSaving(true);
		try {
			const values = await form.validateFields(["name"]);
			const payload: ProjectSpace = {
				name: normalizeText(values.name),
				domain: normalizeText(form.getFieldValue("domain")) || undefined,
				scope: normalizeText(form.getFieldValue("scope")) || undefined,
				status: normalizeUpper(form.getFieldValue("status")) || undefined,
				version: normalizeText(form.getFieldValue("version")) || undefined,
				versionNotes: normalizeText(form.getFieldValue("versionNotes")) || undefined,
				owner: normalizeText(form.getFieldValue("owner")) || undefined,
				ownerDept: normalizeText(form.getFieldValue("ownerDept")) || undefined,
				tags: normalizeText(form.getFieldValue("tags")) || undefined,
				content: normalizeText(form.getFieldValue("content")) || undefined,
			};
			if (editMode === "create") {
				await createModelingPlan(payload);
				toast.success("项目空间已创建");
			} else if (editing?.id) {
				await updateModelingPlan(editing.id, payload);
				toast.success("项目空间已更新");
			}
			setEditOpen(false);
			setEditing(null);
			await loadSpaces();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	};

	const openPublish = (row: ProjectSpace) => {
		setPublishTarget(row);
		publishForm.resetFields();
		publishForm.setFieldsValue({
			version: row.version || "v1",
			changeSummary: "",
		});
		setPublishOpen(true);
	};

	const submitPublish = async () => {
		if (!publishTarget?.id) return;
		setPublishing(true);
		try {
			const values = await publishForm.validateFields(["version"]);
			await publishModelingPlan(publishTarget.id, {
				version: normalizeText(values.version),
				changeSummary: normalizeText(values.changeSummary) || undefined,
			});
			toast.success("项目空间已发布");
			setPublishOpen(false);
			setPublishTarget(null);
			await loadSpaces();
		} catch (err: any) {
			toast.error(err?.message || "发布失败");
		} finally {
			setPublishing(false);
		}
	};

	const handleArchive = (row: ProjectSpace) => {
		if (!row.id) return;
		Modal.confirm({
			title: "归档项目空间？",
			content: "归档后将进入只读状态，可随时恢复。",
			okText: "确认归档",
			cancelText: "取消",
			onOk: async () => {
				try {
					await archiveModelingPlan(row.id!);
					toast.success("已归档");
					await loadSpaces();
				} catch (err: any) {
					toast.error(err?.message || "归档失败");
				}
			},
		});
	};

	const handleRestore = (row: ProjectSpace) => {
		if (!row.id) return;
		Modal.confirm({
			title: "恢复项目空间？",
			okText: "确认恢复",
			cancelText: "取消",
			onOk: async () => {
				try {
					await restoreModelingPlan(row.id!);
					toast.success("已恢复");
					await loadSpaces();
				} catch (err: any) {
					toast.error(err?.message || "恢复失败");
				}
			},
		});
	};

	const columns: ColumnsType<ProjectSpace> = useMemo(
		() => [
			{ title: "项目空间", dataIndex: "name", key: "name", width: 200 },
			{ title: "业务域", dataIndex: "domain", key: "domain", width: 140, render: (v) => v || "-" },
			{ title: "范围说明", dataIndex: "scope", key: "scope", ellipsis: true, render: (v) => v || "-" },
			{ title: "负责人", dataIndex: "owner", key: "owner", width: 120, render: (v) => v || "-" },
			{ title: "部门", dataIndex: "ownerDept", key: "ownerDept", width: 120, render: (v) => v || "-" },
			{
				title: "状态",
				dataIndex: "status",
				key: "status",
				width: 120,
				render: (v) => {
					const label = normalizeUpper(v) || "DRAFT";
					return <Tag color={statusColor(label)}>{label}</Tag>;
				},
			},
			{ title: "版本", dataIndex: "version", key: "version", width: 100, render: (v) => v || "-" },
			{ title: "更新时间", dataIndex: "lastModifiedDate", key: "lastModifiedDate", width: 180, render: (v) => formatDateTime(v) },
			{
				title: "操作",
				key: "actions",
				fixed: "right",
				width: 220,
				render: (_, row) => (
					<Space>
						<Button size="small" onClick={() => openEdit(row)}>
							编辑
						</Button>
						<Button size="small" onClick={() => openPublish(row)} disabled={normalizeUpper(row.status) === "ARCHIVED"}>
							发布
						</Button>
						{normalizeUpper(row.status) === "ARCHIVED" ? (
							<Button size="small" onClick={() => handleRestore(row)}>
								恢复
							</Button>
						) : (
							<Button size="small" danger onClick={() => handleArchive(row)}>
								归档
							</Button>
						)}
					</Space>
				),
			},
		],
		[],
	);

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据开发中心 · 项目空间管理"
				actions={
					<Space>
						<Button onClick={loadSpaces} loading={loading}>
							刷新
						</Button>
						<Button type="primary" onClick={openCreate}>
							新建项目空间
						</Button>
					</Space>
				}
			/>

			<div className="grid gap-4 lg:grid-cols-4">
				<Card>
					<div className="text-sm text-gray-500">空间总数</div>
					<div className="mt-2 text-2xl font-semibold">{stats.total}</div>
				</Card>
				<Card>
					<div className="text-sm text-gray-500">草稿</div>
					<div className="mt-2 text-2xl font-semibold">{stats.draft}</div>
				</Card>
				<Card>
					<div className="text-sm text-gray-500">已发布</div>
					<div className="mt-2 text-2xl font-semibold">{stats.published}</div>
				</Card>
				<Card>
					<div className="text-sm text-gray-500">已归档</div>
					<div className="mt-2 text-2xl font-semibold">{stats.archived}</div>
				</Card>
			</div>

			<Card
				title="项目空间列表"
				extra={
					<Space>
						<Text>状态</Text>
						<Select
							placeholder="全部"
							value={status}
							onChange={(value) => setStatus(value)}
							allowClear
							style={{ width: 160 }}
							options={STATUS_OPTIONS.map((item) => ({ value: item, label: item }))}
						/>
					</Space>
				}
			>
				<Space wrap className="mb-4">
					<Input
						placeholder="搜索项目/域/负责人"
						value={keyword}
						onChange={(e) => setKeyword(e.target.value)}
						allowClear
						style={{ width: 240 }}
					/>
					<Button onClick={loadSpaces} loading={loading}>
						查询
					</Button>
				</Space>
				{spaces.length === 0 && !loading ? (
					<EmptyState title="暂无项目空间" description="先创建一个项目空间，配置仓库与环境信息。" />
				) : (
					<Table
						rowKey={(row) => row.id || row.name || Math.random().toString(36)}
						columns={columns}
						dataSource={spaces}
						loading={loading}
						scroll={{ x: 1200 }}
						pagination={{ pageSize: 8 }}
					/>
				)}
			</Card>

			<Alert
				type="info"
				showIcon
				message="项目空间作为开发入口，后续将关联 SQL 建模、脚本开发与任务编排。"
			/>

			<Modal
				open={editOpen}
				title={editMode === "create" ? "新建项目空间" : "编辑项目空间"}
				onCancel={() => setEditOpen(false)}
				onOk={submitEdit}
				okText="保存"
				cancelText="取消"
				confirmLoading={saving}
				width={760}
			>
				<Form layout="vertical" form={form}>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="name" label="项目空间名称" rules={[{ required: true, message: "请输入名称" }]}>
							<Input placeholder="例如：ERP 经营分析" />
						</Form.Item>
						<Form.Item name="domain" label="业务域">
							<Input placeholder="例如：销售、库存" />
						</Form.Item>
					</div>
					<Form.Item name="scope" label="范围说明">
						<Input.TextArea rows={2} placeholder="描述该项目覆盖的主题、表范围或业务边界" />
					</Form.Item>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="owner" label="负责人">
							<Input placeholder="负责人姓名" />
						</Form.Item>
						<Form.Item name="ownerDept" label="负责部门">
							<Input placeholder="部门代码" />
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="status" label="状态">
							<Select allowClear options={STATUS_OPTIONS.map((item) => ({ value: item, label: item }))} />
						</Form.Item>
						<Form.Item name="version" label="版本号">
							<Input placeholder="v1" />
						</Form.Item>
					</div>
					<Form.Item name="versionNotes" label="版本说明">
						<Input.TextArea rows={2} placeholder="版本变化与关键说明" />
					</Form.Item>
					<Form.Item name="tags" label="标签">
						<Input placeholder="标签/关键字，逗号分隔" />
					</Form.Item>
					<Form.Item name="content" label="仓库与环境配置">
						<Input.TextArea rows={4} placeholder="例如：Git 仓库地址、分支、运行环境与成员配置" />
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={publishOpen}
				title="发布项目空间"
				onCancel={() => setPublishOpen(false)}
				onOk={submitPublish}
				okText="发布"
				cancelText="取消"
				confirmLoading={publishing}
				width={520}
			>
				<Form layout="vertical" form={publishForm}>
					<Form.Item name="version" label="发布版本" rules={[{ required: true, message: "请输入版本号" }]}>
						<Input placeholder="v1" />
					</Form.Item>
					<Form.Item name="changeSummary" label="变更说明">
						<Input.TextArea rows={3} placeholder="可选，记录发布说明" />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
