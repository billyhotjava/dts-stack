import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Button, Card, Form, Input, Modal, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import {
	createMetadataStandard,
	deleteMetadataStandard,
	listMetadataStandards,
	listReferenceCodes,
	updateMetadataStandard,
} from "@/api/platformApi";

const { Text } = Typography;

const normalizeText = (value?: string) => String(value || "").trim();
const normalizeUpper = (value?: string) => normalizeText(value).toUpperCase();

type MetadataStandard = {
	id?: string;
	fieldNameCn?: string;
	fieldNameEn?: string;
	dataType?: string;
	dataLength?: number;
	dataPrecision?: number;
	dataScale?: number;
	nullable?: boolean;
	domain?: string;
	description?: string;
	sourceSystem?: string;
	codeSet?: string;
	defaultValue?: string;
	isPk?: boolean;
	securityLevel?: string;
};

type PagedPayload<T> = { content?: T[]; total?: number; page?: number; size?: number };
type ReferenceCodeDirectory = { codeTypeCode?: string; codeTypeName?: string };

const SECURITY_LEVELS = ["PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL"];

export default function ElementsPage() {
	const [keyword, setKeyword] = useState("");
	const [pageNum, setPageNum] = useState(0);
	const [pageSize, setPageSize] = useState(10);
	const [data, setData] = useState<PagedPayload<MetadataStandard> | null>(null);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [editing, setEditing] = useState<MetadataStandard | null>(null);
	const [codeOptions, setCodeOptions] = useState<ReferenceCodeDirectory[]>([]);
	const [form] = Form.useForm();

	const loadElements = useCallback(async () => {
		setLoading(true);
		try {
			const resp = (await listMetadataStandards({
				page: pageNum,
				size: pageSize,
				keyword: normalizeText(keyword) || undefined,
			})) as PagedPayload<MetadataStandard>;
			setData(resp || null);
		} catch (err: any) {
			toast.error(err?.message || "加载数据元失败");
		} finally {
			setLoading(false);
		}
	}, [keyword, pageNum, pageSize]);

	useEffect(() => {
		void loadElements();
	}, [loadElements]);

	useEffect(() => {
		const loadCodeOptions = async () => {
			try {
				const resp = (await listReferenceCodes({ page: 0, size: 200 })) as PagedPayload<ReferenceCodeDirectory>;
				setCodeOptions(resp?.content ?? []);
			} catch {
				setCodeOptions([]);
			}
		};
		void loadCodeOptions();
	}, []);

	const openModal = (row?: MetadataStandard) => {
		setEditing(row || null);
		form.resetFields();
		form.setFieldsValue({
			fieldNameCn: row?.fieldNameCn,
			fieldNameEn: row?.fieldNameEn,
			dataType: row?.dataType,
			dataLength: row?.dataLength,
			dataPrecision: row?.dataPrecision,
			dataScale: row?.dataScale,
			nullable: row?.nullable,
			domain: row?.domain,
			description: row?.description,
			sourceSystem: row?.sourceSystem,
			codeSet: row?.codeSet,
			defaultValue: row?.defaultValue,
			isPk: row?.isPk,
			securityLevel: row?.securityLevel,
		});
		setModalOpen(true);
	};

	const submit = async () => {
		setSaving(true);
		try {
			const values = await form.validateFields([
				"fieldNameCn",
				"fieldNameEn",
				"dataType",
				"domain",
				"description",
				"sourceSystem",
			]);
			const payload = {
				fieldNameCn: normalizeText(values.fieldNameCn),
				fieldNameEn: normalizeText(values.fieldNameEn),
				dataType: normalizeText(values.dataType),
				dataLength: form.getFieldValue("dataLength") ?? undefined,
				dataPrecision: form.getFieldValue("dataPrecision") ?? undefined,
				dataScale: form.getFieldValue("dataScale") ?? undefined,
				nullable: form.getFieldValue("nullable"),
				domain: normalizeText(values.domain),
				description: normalizeText(values.description),
				sourceSystem: normalizeText(values.sourceSystem),
				codeSet: normalizeText(form.getFieldValue("codeSet")) || undefined,
				defaultValue: normalizeText(form.getFieldValue("defaultValue")) || undefined,
				isPk: form.getFieldValue("isPk"),
				securityLevel: normalizeUpper(form.getFieldValue("securityLevel")) || undefined,
			};
			if (editing?.id) {
				await updateMetadataStandard(editing.id, payload);
				toast.success("数据元已更新");
			} else {
				await createMetadataStandard(payload);
				toast.success("数据元已创建");
			}
			setModalOpen(false);
			setEditing(null);
			await loadElements();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	};

	const removeElement = (row: MetadataStandard) => {
		if (!row?.id) return;
		Modal.confirm({
			title: "删除数据元？",
			content: "删除后无法恢复。",
			okText: "删除",
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteMetadataStandard(row.id as string);
					toast.success("数据元已删除");
					await loadElements();
				} catch (err: any) {
					toast.error(err?.message || "删除失败");
				}
			},
		});
	};

	const columns: ColumnsType<MetadataStandard> = [
		{ title: "数据元名称", dataIndex: "fieldNameCn", render: (t) => <Text strong>{t}</Text> },
		{ title: "标准字段名", dataIndex: "fieldNameEn", render: (c) => <Text className="font-mono text-xs">{c}</Text> },
		{ title: "标准类型", dataIndex: "dataType", render: (t) => <Tag color="blue">{t}</Tag> },
		{ title: "长度限制", dataIndex: "dataLength", render: (t) => (t == null ? "-" : t) },
		{ title: "格式规则", dataIndex: "description", ellipsis: true, render: (t) => t || "-" },
		{
			title: "脱敏等级",
			dataIndex: "securityLevel",
			render: (s) => <Tag color={s === "SECRET" || s === "CONFIDENTIAL" ? "red" : "default"}>{s || "-"}</Tag>,
		},
		{
			title: "操作",
			render: (_, row) => (
				<Space>
					<Button type="link" size="small" onClick={() => openModal(row)}>
						编辑
					</Button>
					<Button type="link" size="small" danger onClick={() => removeElement(row)}>
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
				title="数据治理中心 · 标准管理 / 数据元"
				description="定义数据元标准字段与技术规则，支撑字段级统一口径。"
				actions={
					<Button type="primary" onClick={() => openModal()}>
						+ 新增数据元
					</Button>
				}
			/>

			<Card>
				<Space className="mb-4">
					<Input.Search
						placeholder="搜索数据元..."
						style={{ width: 300 }}
						value={keyword}
						onChange={(e) => setKeyword(e.target.value)}
						onSearch={loadElements}
						allowClear
					/>
				</Space>
				{content.length === 0 && !loading ? (
					<EmptyState title="暂无数据元" description="请先新增数据元规范。" />
				) : (
					<Table
						rowKey={(row) => row.id || row.fieldNameEn || row.fieldNameCn || Math.random().toString(36)}
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
				title={editing ? "编辑数据元" : "新增数据元"}
				onCancel={() => setModalOpen(false)}
				onOk={submit}
				okText="保存"
				cancelText="取消"
				confirmLoading={saving}
				width={720}
			>
				<Form layout="vertical" form={form}>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="fieldNameCn" label="数据元名称" rules={[{ required: true, message: "请输入名称" }]}>
							<Input placeholder="手机号码" />
						</Form.Item>
						<Form.Item name="fieldNameEn" label="标准字段名" rules={[{ required: true, message: "请输入英文名" }]}>
							<Input placeholder="mobile_no" />
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="dataType" label="标准类型" rules={[{ required: true, message: "请输入类型" }]}>
							<Input placeholder="STRING" />
						</Form.Item>
						<Form.Item name="dataLength" label="长度限制">
							<Input placeholder="11" />
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="dataPrecision" label="精度">
							<Input placeholder="可选" />
						</Form.Item>
						<Form.Item name="dataScale" label="小数位">
							<Input placeholder="可选" />
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="domain" label="主题域" rules={[{ required: true, message: "请输入主题域" }]}>
							<Input placeholder="用户域" />
						</Form.Item>
						<Form.Item name="sourceSystem" label="来源系统" rules={[{ required: true, message: "请输入来源系统" }]}>
							<Input placeholder="ERP" />
						</Form.Item>
					</div>
					<Form.Item name="description" label="格式规则" rules={[{ required: true, message: "请输入描述" }]}>
						<Input.TextArea rows={2} placeholder="Regex 或规则说明" />
					</Form.Item>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="codeSet" label="码表编码">
							<Select
								allowClear
								showSearch
								placeholder="建议选择标准码表"
								options={codeOptions.map((item) => ({
									label: item.codeTypeName && item.codeTypeCode
										? `${item.codeTypeName} (${item.codeTypeCode})`
										: `${item.codeTypeName || item.codeTypeCode || ""}`.trim(),
									value: item.codeTypeCode,
								}))}
							/>
						</Form.Item>
						<Form.Item name="defaultValue" label="默认值">
							<Input placeholder="可选" />
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="isPk" label="主键字段">
							<Select
								allowClear
								options={[
									{ label: "是", value: true },
									{ label: "否", value: false },
								]}
							/>
						</Form.Item>
						<Form.Item name="nullable" label="可为空">
							<Select
								allowClear
								options={[
									{ label: "是", value: true },
									{ label: "否", value: false },
								]}
							/>
						</Form.Item>
					</div>
					<Form.Item name="securityLevel" label="脱敏等级">
						<Select allowClear options={SECURITY_LEVELS.map((item) => ({ label: item, value: item }))} />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
