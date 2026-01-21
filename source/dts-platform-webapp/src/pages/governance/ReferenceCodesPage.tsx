import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Button, Card, Form, Input, Modal, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import {
	createStandard,
	deleteStandard,
	listStandards,
	updateStandard,
} from "@/api/platformApi";

const { Text } = Typography;

const normalizeText = (value?: string) => String(value || "").trim();
const normalizeUpper = (value?: string) => normalizeText(value).toUpperCase();

type DataStandard = {
	id?: string;
	name?: string;
	code?: string;
	domain?: string;
	scope?: string;
	status?: string;
	securityLevel?: string;
	owner?: string;
	tags?: string[];
	currentVersion?: string;
	description?: string;
	codeSet?: string;
	dataType?: string;
	nullable?: boolean;
};

type PagedPayload<T> = { content?: T[]; total?: number; page?: number; size?: number };

const SECURITY_LEVELS = ["PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL"];
const STANDARD_STATUSES = ["DRAFT", "IN_REVIEW", "ACTIVE", "DEPRECATED", "RETIRED", "ARCHIVED"];

export default function ReferenceCodesPage() {
	const [keyword, setKeyword] = useState("");
	const [pageNum, setPageNum] = useState(0);
	const [pageSize, setPageSize] = useState(10);
	const [data, setData] = useState<PagedPayload<DataStandard> | null>(null);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [editing, setEditing] = useState<DataStandard | null>(null);
	const [form] = Form.useForm();

	const loadReferences = useCallback(async () => {
		setLoading(true);
		try {
			const resp = (await listStandards({
				page: pageNum,
				size: pageSize,
				keyword: normalizeText(keyword) || undefined,
			})) as PagedPayload<DataStandard>;
			setData(resp || null);
		} catch (err: any) {
			toast.error(err?.message || "加载码表失败");
		} finally {
			setLoading(false);
		}
	}, [keyword, pageNum, pageSize]);

	useEffect(() => {
		void loadReferences();
	}, [loadReferences]);

	const openModal = (row?: DataStandard) => {
		setEditing(row || null);
		form.resetFields();
		form.setFieldsValue({
			name: row?.name,
			code: row?.code,
			domain: row?.domain,
			scope: row?.scope,
			status: row?.status,
			securityLevel: row?.securityLevel,
			owner: row?.owner,
			tags: row?.tags ? row.tags.join(",") : "",
			description: row?.description,
			codeSet: row?.codeSet,
			dataType: row?.dataType,
			nullable: row?.nullable,
		});
		setModalOpen(true);
	};

	const submit = async () => {
		setSaving(true);
		try {
			const values = await form.validateFields(["name", "code"]);
			const tags = normalizeText(form.getFieldValue("tags"));
			const payload = {
				name: normalizeText(values.name),
				code: normalizeText(values.code),
				domain: normalizeText(form.getFieldValue("domain")) || undefined,
				scope: normalizeText(form.getFieldValue("scope")) || undefined,
				status: normalizeUpper(form.getFieldValue("status")) || undefined,
				securityLevel: normalizeUpper(form.getFieldValue("securityLevel")) || undefined,
				owner: normalizeText(form.getFieldValue("owner")) || undefined,
				tags: tags ? tags.split(",").map((item: string) => item.trim()).filter(Boolean) : undefined,
				description: normalizeText(form.getFieldValue("description")) || undefined,
				codeSet: normalizeText(form.getFieldValue("codeSet")) || undefined,
				dataType: normalizeText(form.getFieldValue("dataType")) || undefined,
				nullable: form.getFieldValue("nullable"),
			};
			if (editing?.id) {
				await updateStandard(editing.id, payload);
				toast.success("码表已更新");
			} else {
				await createStandard(payload);
				toast.success("码表已创建");
			}
			setModalOpen(false);
			setEditing(null);
			await loadReferences();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	};

	const removeReference = (row: DataStandard) => {
		if (!row?.id) return;
		Modal.confirm({
			title: "删除码表？",
			content: "删除后无法恢复。",
			okText: "删除",
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteStandard(row.id as string);
					toast.success("码表已删除");
					await loadReferences();
				} catch (err: any) {
					toast.error(err?.message || "删除失败");
				}
			},
		});
	};

	const columns: ColumnsType<DataStandard> = [
		{ title: "码表名称", dataIndex: "name", render: (t) => <Text strong>{t}</Text> },
		{ title: "码表编码", dataIndex: "code", render: (c) => <Tag>{c}</Tag> },
		{ title: "枚举映射 (Code:Value)", dataIndex: "codeSet", ellipsis: true, render: (t) => t || "-" },
		{ title: "版本", dataIndex: "currentVersion", render: (t) => <Text type="secondary">{t || "-"}</Text> },
		{
			title: "状态",
			dataIndex: "status",
			render: (s) => <Tag color={s === "ACTIVE" ? "green" : "default"}>{s || "-"}</Tag>,
		},
		{
			title: "操作",
			render: (_, row) => (
				<Space>
					<Button type="link" size="small" onClick={() => openModal(row)}>
						明细管理
					</Button>
					<Button type="link" size="small" danger onClick={() => removeReference(row)}>
						删除
					</Button>
				</Space>
			),
		},
	];

	const content = data?.content ?? [];

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据治理中心 · 标准管理 / 公共码表"
				description="维护公共枚举码表与业务映射，统一字段取值标准。"
				actions={
					<Space>
						<Button disabled>更新 dbt Seeds</Button>
						<Button type="primary" onClick={() => openModal()}>
							+ 新增码表
						</Button>
					</Space>
				}
			/>

			<Card>
				<Space className="mb-4">
					<Input.Search
						placeholder="搜索码表..."
						style={{ width: 320 }}
						value={keyword}
						onChange={(e) => setKeyword(e.target.value)}
						onSearch={loadReferences}
						allowClear
					/>
				</Space>
				{content.length === 0 && !loading ? (
					<EmptyState title="暂无码表" description="请先新增公共码表。" />
				) : (
					<Table
						rowKey={(row) => row.id || row.code || row.name || Math.random().toString(36)}
						dataSource={content}
						columns={columns}
						loading={loading}
						pagination={{
							current: (data?.page ?? 0) + 1,
							pageSize: data?.size ?? pageSize,
							total: data?.total ?? 0,
							onChange: (page, size) => {
								setPageNum(page - 1);
								setPageSize(size);
							},
						}}
					/>
				)}
			</Card>

			<Modal
				open={modalOpen}
				title={editing ? "编辑码表" : "新增码表"}
				onCancel={() => setModalOpen(false)}
				onOk={submit}
				okText="保存"
				cancelText="取消"
				confirmLoading={saving}
				width={720}
			>
				<Form layout="vertical" form={form}>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="name" label="码表名称" rules={[{ required: true, message: "请输入名称" }]}>
							<Input placeholder="订单状态码" />
						</Form.Item>
						<Form.Item name="code" label="码表编码" rules={[{ required: true, message: "请输入编码" }]}>
							<Input placeholder="ORD_STS" />
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="domain" label="主题域">
							<Input placeholder="交易域" />
						</Form.Item>
						<Form.Item name="owner" label="负责人">
							<Input placeholder="责任人" />
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="status" label="状态">
							<Select allowClear options={STANDARD_STATUSES.map((item) => ({ label: item, value: item }))} />
						</Form.Item>
						<Form.Item name="securityLevel" label="安全级别">
							<Select allowClear options={SECURITY_LEVELS.map((item) => ({ label: item, value: item }))} />
						</Form.Item>
					</div>
					<Form.Item name="codeSet" label="枚举映射 (Code:Value)">
						<Input.TextArea rows={3} placeholder="0:待支付,1:已支付,2:已取消" />
					</Form.Item>
					<Form.Item name="description" label="说明">
						<Input.TextArea rows={2} placeholder="码表说明或业务规则" />
					</Form.Item>
					<Form.Item name="tags" label="标签">
						<Input placeholder="标签，逗号分隔" />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
