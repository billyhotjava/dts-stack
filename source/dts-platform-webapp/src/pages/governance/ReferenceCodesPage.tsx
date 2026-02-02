import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Button, Card, Form, Input, Modal, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import {
	batchReferenceCodeItems,
	createReferenceCode,
	createReferenceCodeItem,
	createReferenceCodeMapping,
	deleteReferenceCode,
	deleteReferenceCodeItem,
	deleteReferenceCodeMapping,
	listReferenceCodeItems,
	listReferenceCodeMappings,
	listReferenceCodes,
	syncReferenceCodeSeeds,
	updateReferenceCode,
	updateReferenceCodeItem,
	updateReferenceCodeMapping,
} from "@/api/platformApi";

const { Text } = Typography;

const normalizeText = (value?: string) => String(value || "").trim();

type ReferenceCodeDirectory = {
	codeTypeId?: string;
	codeTypeCode?: string;
	codeTypeName?: string;
	stdLevel?: string;
	bizCatalog?: string;
	dataType?: string;
	status?: number;
	ownerDept?: string;
	version?: string;
	itemCount?: number;
};

type ReferenceCodeItem = {
	itemId?: number;
	codeTypeId?: string;
	codeValue?: string;
	codeName?: string;
	description?: string;
	sortNum?: number;
	parentCode?: string;
	isDefault?: boolean;
};

type ReferenceCodeMapping = {
	mapId?: number;
	codeTypeId?: string;
	sourceSys?: string;
	srcCode?: string;
	stdCode?: string;
};

type PagedPayload<T> = { content?: T[]; total?: number; page?: number; size?: number };

const STATUS_LABELS: Record<number, { label: string; color: string }> = {
	0: { label: "草稿", color: "default" },
	1: { label: "发布", color: "green" },
	2: { label: "废弃", color: "red" },
};

export default function ReferenceCodesPage() {
	const [keyword, setKeyword] = useState("");
	const [pageNum, setPageNum] = useState(0);
	const [pageSize, setPageSize] = useState(10);
	const [data, setData] = useState<PagedPayload<ReferenceCodeDirectory> | null>(null);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [editing, setEditing] = useState<ReferenceCodeDirectory | null>(null);
	const [itemsOpen, setItemsOpen] = useState(false);
	const [activeDirectory, setActiveDirectory] = useState<ReferenceCodeDirectory | null>(null);
	const [itemsLoading, setItemsLoading] = useState(false);
	const [items, setItems] = useState<ReferenceCodeItem[]>([]);
	const [itemModalOpen, setItemModalOpen] = useState(false);
	const [itemSaving, setItemSaving] = useState(false);
	const [itemEditing, setItemEditing] = useState<ReferenceCodeItem | null>(null);
	const [batchOpen, setBatchOpen] = useState(false);
	const [batchRaw, setBatchRaw] = useState("");
	const [batchSaving, setBatchSaving] = useState(false);
	const [mappingsOpen, setMappingsOpen] = useState(false);
	const [mappingsLoading, setMappingsLoading] = useState(false);
	const [mappings, setMappings] = useState<ReferenceCodeMapping[]>([]);
	const [mappingModalOpen, setMappingModalOpen] = useState(false);
	const [mappingSaving, setMappingSaving] = useState(false);
	const [mappingEditing, setMappingEditing] = useState<ReferenceCodeMapping | null>(null);
	const [form] = Form.useForm();
	const [itemForm] = Form.useForm();
	const [mappingForm] = Form.useForm();

	const loadDirectories = useCallback(async () => {
		setLoading(true);
		try {
			const resp = (await listReferenceCodes({
				page: pageNum,
				size: pageSize,
				keyword: normalizeText(keyword) || undefined,
			})) as PagedPayload<ReferenceCodeDirectory>;
			setData(resp || null);
		} catch (err: any) {
			toast.error(err?.message || "加载码表失败");
		} finally {
			setLoading(false);
		}
	}, [keyword, pageNum, pageSize]);

	useEffect(() => {
		void loadDirectories();
	}, [loadDirectories]);

	const openModal = (row?: ReferenceCodeDirectory) => {
		setEditing(row || null);
		form.resetFields();
		form.setFieldsValue({
			codeTypeId: row?.codeTypeId,
			codeTypeCode: row?.codeTypeCode,
			codeTypeName: row?.codeTypeName,
			stdLevel: row?.stdLevel,
			bizCatalog: row?.bizCatalog,
			dataType: row?.dataType,
			status: row?.status ?? 1,
			ownerDept: row?.ownerDept,
			version: row?.version,
		});
		setModalOpen(true);
	};

	const submit = async () => {
		setSaving(true);
		try {
			const values = await form.validateFields(["codeTypeCode", "codeTypeName"]);
			const payload = {
				codeTypeId: normalizeText(form.getFieldValue("codeTypeId")) || undefined,
				codeTypeCode: normalizeText(values.codeTypeCode),
				codeTypeName: normalizeText(values.codeTypeName),
				stdLevel: normalizeText(form.getFieldValue("stdLevel")) || undefined,
				bizCatalog: normalizeText(form.getFieldValue("bizCatalog")) || undefined,
				dataType: normalizeText(form.getFieldValue("dataType")) || undefined,
				status: form.getFieldValue("status"),
				ownerDept: normalizeText(form.getFieldValue("ownerDept")) || undefined,
				version: normalizeText(form.getFieldValue("version")) || undefined,
			};
			if (editing?.codeTypeId) {
				await updateReferenceCode(editing.codeTypeId, payload);
				toast.success("码表已更新");
			} else {
				await createReferenceCode(payload);
				toast.success("码表已创建");
			}
			setModalOpen(false);
			setEditing(null);
			await loadDirectories();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	};

	const removeDirectory = (row: ReferenceCodeDirectory) => {
		if (!row?.codeTypeId) return;
		Modal.confirm({
			title: "删除码表？",
			content: "删除后无法恢复。",
			okText: "删除",
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteReferenceCode(row.codeTypeId as string);
					toast.success("码表已删除");
					await loadDirectories();
				} catch (err: any) {
					toast.error(err?.message || "删除失败");
				}
			},
		});
	};

	const openItems = async (row: ReferenceCodeDirectory) => {
		setActiveDirectory(row);
		setItemsOpen(true);
		await refreshItems(row);
	};

	const refreshItems = async (row?: ReferenceCodeDirectory) => {
		const dir = row || activeDirectory;
		if (!dir?.codeTypeId) return;
		setItemsLoading(true);
		try {
			const resp = (await listReferenceCodeItems(dir.codeTypeId)) as ReferenceCodeItem[];
			setItems(Array.isArray(resp) ? resp : []);
		} catch (err: any) {
			toast.error(err?.message || "加载码表项失败");
		} finally {
			setItemsLoading(false);
		}
	};

	const openBatch = () => {
		setBatchRaw("");
		setBatchOpen(true);
	};

	const submitBatch = async () => {
		if (!activeDirectory?.codeTypeId || !batchRaw.trim()) return;
		setBatchSaving(true);
		try {
			const resp = (await batchReferenceCodeItems(activeDirectory.codeTypeId, { raw: batchRaw })) as any;
			toast.success(`导入完成：新增 ${resp?.created ?? 0}，跳过 ${resp?.skipped ?? 0}，无效 ${resp?.invalid ?? 0}`);
			setBatchOpen(false);
			await refreshItems();
		} catch (err: any) {
			toast.error(err?.message || "批量导入失败");
		} finally {
			setBatchSaving(false);
		}
	};

	const openMappings = async (row: ReferenceCodeDirectory) => {
		setActiveDirectory(row);
		setMappingsOpen(true);
		await refreshItems(row);
		await refreshMappings(row);
	};

	const refreshMappings = async (row?: ReferenceCodeDirectory) => {
		const dir = row || activeDirectory;
		if (!dir?.codeTypeId) return;
		setMappingsLoading(true);
		try {
			const resp = (await listReferenceCodeMappings(dir.codeTypeId)) as ReferenceCodeMapping[];
			setMappings(Array.isArray(resp) ? resp : []);
		} catch (err: any) {
			toast.error(err?.message || "加载码表映射失败");
		} finally {
			setMappingsLoading(false);
		}
	};

	const openMappingModal = (row?: ReferenceCodeMapping) => {
		setMappingEditing(row || null);
		mappingForm.resetFields();
		mappingForm.setFieldsValue({
			sourceSys: row?.sourceSys,
			srcCode: row?.srcCode,
			stdCode: row?.stdCode,
		});
		setMappingModalOpen(true);
	};

	const submitMapping = async () => {
		if (!activeDirectory?.codeTypeId) return;
		setMappingSaving(true);
		try {
			const values = await mappingForm.validateFields(["sourceSys", "srcCode", "stdCode"]);
			const payload = {
				sourceSys: normalizeText(values.sourceSys),
				srcCode: normalizeText(values.srcCode),
				stdCode: normalizeText(values.stdCode),
			};
			if (mappingEditing?.mapId) {
				await updateReferenceCodeMapping(activeDirectory.codeTypeId, mappingEditing.mapId, payload);
				toast.success("映射已更新");
			} else {
				await createReferenceCodeMapping(activeDirectory.codeTypeId, payload);
				toast.success("映射已创建");
			}
			setMappingModalOpen(false);
			setMappingEditing(null);
			await refreshMappings();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setMappingSaving(false);
		}
	};

	const removeMapping = (row: ReferenceCodeMapping) => {
		if (!activeDirectory?.codeTypeId || !row?.mapId) return;
		Modal.confirm({
			title: "删除映射？",
			content: "删除后无法恢复。",
			okText: "删除",
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteReferenceCodeMapping(activeDirectory.codeTypeId!, row.mapId!);
					toast.success("映射已删除");
					await refreshMappings();
				} catch (err: any) {
					toast.error(err?.message || "删除失败");
				}
			},
		});
	};

	const openItemModal = (row?: ReferenceCodeItem) => {
		setItemEditing(row || null);
		itemForm.resetFields();
		itemForm.setFieldsValue({
			codeValue: row?.codeValue,
			codeName: row?.codeName,
			description: row?.description,
			sortNum: row?.sortNum,
			parentCode: row?.parentCode,
			isDefault: row?.isDefault,
		});
		setItemModalOpen(true);
	};

	const submitItem = async () => {
		if (!activeDirectory?.codeTypeId) return;
		setItemSaving(true);
		try {
			const values = await itemForm.validateFields(["codeValue", "codeName"]);
			const payload = {
				codeValue: normalizeText(values.codeValue),
				codeName: normalizeText(values.codeName),
				description: normalizeText(itemForm.getFieldValue("description")) || undefined,
				sortNum: itemForm.getFieldValue("sortNum") ?? undefined,
				parentCode: normalizeText(itemForm.getFieldValue("parentCode")) || undefined,
				isDefault: itemForm.getFieldValue("isDefault"),
			};
			if (itemEditing?.itemId) {
				await updateReferenceCodeItem(activeDirectory.codeTypeId, itemEditing.itemId, payload);
				toast.success("码表项已更新");
			} else {
				await createReferenceCodeItem(activeDirectory.codeTypeId, payload);
				toast.success("码表项已创建");
			}
			setItemModalOpen(false);
			setItemEditing(null);
			await refreshItems();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setItemSaving(false);
		}
	};

	const removeItem = (row: ReferenceCodeItem) => {
		if (!activeDirectory?.codeTypeId || !row?.itemId) return;
		Modal.confirm({
			title: "删除码表项？",
			content: "删除后无法恢复。",
			okText: "删除",
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteReferenceCodeItem(activeDirectory.codeTypeId!, row.itemId!);
					toast.success("码表项已删除");
					await refreshItems();
				} catch (err: any) {
					toast.error(err?.message || "删除失败");
				}
			},
		});
	};

	const syncSeeds = async () => {
		try {
			const resp = (await syncReferenceCodeSeeds()) as any;
			const summary = resp?.seedPath
				? `已生成 ${resp?.rows ?? 0} 行，路径：${resp.seedPath}`
				: "已更新 dbt Seeds";
			toast.success(summary);
		} catch (err: any) {
			toast.error(err?.message || "更新 dbt Seeds 失败");
		}
	};

	const columns: ColumnsType<ReferenceCodeDirectory> = [
		{ title: "码表名称", dataIndex: "codeTypeName", render: (t) => <Text strong>{t}</Text> },
		{ title: "码表编码", dataIndex: "codeTypeCode", render: (c) => <Tag>{c}</Tag> },
		{ title: "业务分类", dataIndex: "bizCatalog", render: (t) => t || "-" },
		{ title: "数据类型", dataIndex: "dataType", render: (t) => t || "-" },
		{ title: "码值数量", dataIndex: "itemCount", render: (t) => t ?? 0 },
		{ title: "版本", dataIndex: "version", render: (t) => <Text type="secondary">{t || "-"}</Text> },
		{
			title: "状态",
			dataIndex: "status",
			render: (s: number) => {
				const meta = STATUS_LABELS[s] || { label: "未知", color: "default" };
				return <Tag color={meta.color}>{meta.label}</Tag>;
			},
		},
		{
			title: "操作",
			render: (_, row) => (
				<Space>
					<Button type="link" size="small" onClick={() => openItems(row)}>
						明细管理
					</Button>
					<Button type="link" size="small" onClick={() => openMappings(row)}>
						映射管理
					</Button>
					<Button type="link" size="small" onClick={() => openModal(row)}>
						编辑
					</Button>
					<Button type="link" size="small" danger onClick={() => removeDirectory(row)}>
						删除
					</Button>
				</Space>
			),
		},
	];

	const itemColumns: ColumnsType<ReferenceCodeItem> = useMemo(
		() => [
			{ title: "代码值", dataIndex: "codeValue", render: (t) => <Tag>{t}</Tag> },
			{ title: "名称", dataIndex: "codeName", render: (t) => <Text strong>{t}</Text> },
			{ title: "业务定义", dataIndex: "description", ellipsis: true, render: (t) => t || "-" },
			{ title: "排序", dataIndex: "sortNum", render: (t) => (t == null ? "-" : t) },
			{ title: "父级", dataIndex: "parentCode", render: (t) => t || "-" },
			{
				title: "默认",
				dataIndex: "isDefault",
				render: (t) => <Tag color={t ? "blue" : "default"}>{t ? "是" : "否"}</Tag>,
			},
			{
				title: "操作",
				render: (_, row) => (
					<Space>
						<Button type="link" size="small" onClick={() => openItemModal(row)}>
							编辑
						</Button>
						<Button type="link" size="small" danger onClick={() => removeItem(row)}>
							删除
						</Button>
					</Space>
				),
			},
		],
		[items]
	);

	const mappingColumns: ColumnsType<ReferenceCodeMapping> = useMemo(
		() => [
			{ title: "源系统", dataIndex: "sourceSys", render: (t) => <Tag color="blue">{t}</Tag> },
			{ title: "源代码", dataIndex: "srcCode", render: (t) => <Text className="font-mono text-xs">{t}</Text> },
			{ title: "标准码值", dataIndex: "stdCode", render: (t) => <Tag>{t}</Tag> },
			{
				title: "操作",
				render: (_, row) => (
					<Space>
						<Button type="link" size="small" onClick={() => openMappingModal(row)}>
							编辑
						</Button>
						<Button type="link" size="small" danger onClick={() => removeMapping(row)}>
							删除
						</Button>
					</Space>
				),
			},
		],
		[mappings]
	);

	const content = data?.content ?? [];

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据治理中心 · 标准管理 / 公共码表"
				description="维护公共枚举码表与业务映射，统一字段取值标准。"
				actions={
					<Space>
						<Button onClick={syncSeeds}>更新 dbt Seeds</Button>
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
						onSearch={loadDirectories}
						allowClear
					/>
				</Space>
				{content.length === 0 && !loading ? (
					<EmptyState title="暂无码表" description="请先新增公共码表。" />
				) : (
					<Table
						rowKey={(row) => row.codeTypeId || row.codeTypeCode || row.codeTypeName || Math.random().toString(36)}
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
			>
				<Form form={form} layout="vertical">
					<Form.Item name="codeTypeId" label="码表ID">
						<Input placeholder="例如：SEX_001（可不填，默认等同于码表编码）" />
					</Form.Item>
					<Form.Item
						name="codeTypeCode"
						label="码表编码"
						rules={[{ required: true, message: "请输入码表编码" }]}
					>
						<Input placeholder="例如：GENDER_CODE" />
					</Form.Item>
					<Form.Item
						name="codeTypeName"
						label="码表名称"
						rules={[{ required: true, message: "请输入码表名称" }]}
					>
						<Input placeholder="例如：性别代码表" />
					</Form.Item>
					<Form.Item name="stdLevel" label="标准层级">
						<Input placeholder="例如：国家标准 (GB/T 2261.1)" />
					</Form.Item>
					<Form.Item name="bizCatalog" label="业务分类">
						<Input placeholder="例如：基础人口信息" />
					</Form.Item>
					<Form.Item name="dataType" label="数据类型">
						<Input placeholder="例如：String / Integer" />
					</Form.Item>
					<Form.Item name="status" label="状态">
						<Select
							options={[
								{ label: "草稿", value: 0 },
								{ label: "发布", value: 1 },
								{ label: "废弃", value: 2 },
							]}
						/>
					</Form.Item>
					<Form.Item name="ownerDept" label="管理部门">
						<Input placeholder="例如：数据管理部" />
					</Form.Item>
					<Form.Item name="version" label="版本号">
						<Input placeholder="例如：V1.2" />
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={itemsOpen}
				title={
					activeDirectory
						? `码表明细 · ${activeDirectory.codeTypeName || activeDirectory.codeTypeCode}`
						: "码表明细"
				}
				onCancel={() => setItemsOpen(false)}
				footer={null}
				width={900}
			>
				<Space className="mb-3">
					<Button type="primary" onClick={() => openItemModal()}>
						+ 新增码值
					</Button>
					<Button onClick={openBatch}>批量导入</Button>
					<Button onClick={() => refreshItems()}>刷新</Button>
				</Space>
				<Table
					rowKey={(row) => row.itemId || row.codeValue || Math.random().toString(36)}
					dataSource={items}
					columns={itemColumns}
					loading={itemsLoading}
					pagination={false}
				/>
			</Modal>

			<Modal
				open={itemModalOpen}
				title={itemEditing ? "编辑码表项" : "新增码表项"}
				onCancel={() => setItemModalOpen(false)}
				onOk={submitItem}
				okText="保存"
				cancelText="取消"
				confirmLoading={itemSaving}
			>
				<Form form={itemForm} layout="vertical">
					<Form.Item name="codeValue" label="标准代码" rules={[{ required: true, message: "请输入码值" }]}>
						<Input placeholder="例如：1" />
					</Form.Item>
					<Form.Item name="codeName" label="标准名称" rules={[{ required: true, message: "请输入名称" }]}>
						<Input placeholder="例如：男" />
					</Form.Item>
					<Form.Item name="description" label="业务定义">
						<Input.TextArea rows={2} placeholder="描述该值的业务含义" />
					</Form.Item>
					<Form.Item name="sortNum" label="排序号">
						<Input type="number" placeholder="例如：1" />
					</Form.Item>
					<Form.Item name="parentCode" label="父级代码">
						<Input placeholder="例如：0" />
					</Form.Item>
					<Form.Item name="isDefault" label="是否默认">
						<Select
							options={[
								{ label: "否", value: false },
								{ label: "是", value: true },
							]}
						/>
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={batchOpen}
				title="批量导入码值"
				onCancel={() => setBatchOpen(false)}
				onOk={submitBatch}
				okText="导入"
				cancelText="取消"
				confirmLoading={batchSaving}
			>
				<Input.TextArea
					rows={6}
					value={batchRaw}
					onChange={(e) => setBatchRaw(e.target.value)}
					placeholder="格式：value:label,value:label，例如 1:男,2:女"
				/>
			</Modal>

			<Modal
				open={mappingsOpen}
				title={
					activeDirectory
						? `码表映射 · ${activeDirectory.codeTypeName || activeDirectory.codeTypeCode}`
						: "码表映射"
				}
				onCancel={() => setMappingsOpen(false)}
				footer={null}
				width={900}
			>
				<Space className="mb-3">
					<Button type="primary" onClick={() => openMappingModal()}>
						+ 新增映射
					</Button>
					<Button onClick={() => refreshMappings()}>刷新</Button>
				</Space>
				<Table
					rowKey={(row) => row.mapId || `${row.sourceSys}-${row.srcCode}` || Math.random().toString(36)}
					dataSource={mappings}
					columns={mappingColumns}
					loading={mappingsLoading}
					pagination={false}
				/>
			</Modal>

			<Modal
				open={mappingModalOpen}
				title={mappingEditing ? "编辑码表映射" : "新增码表映射"}
				onCancel={() => setMappingModalOpen(false)}
				onOk={submitMapping}
				okText="保存"
				cancelText="取消"
				confirmLoading={mappingSaving}
			>
				<Form form={mappingForm} layout="vertical">
					<Form.Item name="sourceSys" label="源系统" rules={[{ required: true, message: "请输入源系统" }]}>
						<Input placeholder="例如：CRM_SYSTEM" />
					</Form.Item>
					<Form.Item name="srcCode" label="源系统原始值" rules={[{ required: true, message: "请输入源代码" }]}>
						<Input placeholder="例如：Male" />
					</Form.Item>
					<Form.Item name="stdCode" label="标准映射值" rules={[{ required: true, message: "请选择标准码值" }]}>
						<Select
							showSearch
							allowClear
							options={items.map((item) => ({
								label: `${item.codeName || item.codeValue}`.trim(),
								value: item.codeValue,
							}))}
							placeholder="选择标准码值"
						/>
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
