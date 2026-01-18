import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { Button, Card, Form, Input, Modal, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { GLOBAL_CONFIG } from "@/global-config";
import userStore from "@/store/userStore";
import { useCatalogDomainOptions } from "@/hooks/useCatalogDomainOptions";
import {
	createMetadataStandard,
	deleteMetadataStandard,
	importMetadataStandards,
	listMetadataStandards,
	updateMetadataStandard,
} from "@/api/platformApi";

const { Text, Paragraph } = Typography;

type MetadataStandard = {
	id: string;
	fieldNameCn: string;
	fieldNameEn: string;
	dataType: string;
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
	createdDate?: string;
	lastModifiedDate?: string;
};

type FilterState = {
	keyword: string;
	domain: string;
	dataType: string;
	sourceSystem: string;
};

const RULES = [
	"必填字段：field_name_cn, field_name_en, data_type, nullable, domain, description, source_system",
	"唯一键：field_name_en + domain",
	"nullable 仅允许 Y/N（大小写不敏感）",
	"data_type 建议使用：VARCHAR/INT/BIGINT/DECIMAL/DATE/TIMESTAMP/BOOLEAN/DOUBLE",
	"VARCHAR 必须填写 data_length；DECIMAL 建议填写 data_precision/data_scale",
	"security_level 可选：INTERNAL/CONFIDENTIAL/SECRET/TOP_SECRET",
	"空行或全部为空的记录会被跳过",
];

const ERROR_RULES = [
	"缺少必填字段：请补齐必填列后再导入",
	"唯一键重复：同一 domain 下 field_name_en 重复",
	"类型不匹配：data_type 未在建议集合内",
	"长度/精度缺失：VARCHAR 未填 length 或 DECIMAL 未填 precision/scale",
];

const DATA_TYPE_OPTIONS = [
	"VARCHAR",
	"CHAR",
	"TEXT",
	"INT",
	"INTEGER",
	"BIGINT",
	"DECIMAL",
	"NUMERIC",
	"DOUBLE",
	"FLOAT",
	"DATE",
	"TIMESTAMP",
	"BOOLEAN",
];

export default function MetadataStandardsPage() {
	const [form] = Form.useForm();
	const [loading, setLoading] = useState(false);
	const [page, setPage] = useState(0);
	const [total, setTotal] = useState(0);
	const [rows, setRows] = useState<MetadataStandard[]>([]);
	const [filters, setFilters] = useState<FilterState>({
		keyword: "",
		domain: "ALL",
		dataType: "ALL",
		sourceSystem: "",
	});

	const [editOpen, setEditOpen] = useState(false);
	const [editMode, setEditMode] = useState<"create" | "edit">("create");
	const [editing, setEditing] = useState<MetadataStandard | null>(null);
	const [saving, setSaving] = useState(false);

	const [importOpen, setImportOpen] = useState(false);
	const [importing, setImporting] = useState(false);
	const [importResult, setImportResult] = useState<any | null>(null);
	const fileInputRef = useRef<HTMLInputElement | null>(null);

	const { options: domainTreeOptions } = useCatalogDomainOptions();
	const domainOptions = useMemo(() => {
		const list = [...domainTreeOptions];
		list.sort((a, b) => a.label.localeCompare(b.label, "zh-Hans-CN"));
		return list;
	}, [domainTreeOptions]);

	const load = useCallback(async () => {
		setLoading(true);
		try {
			const params: any = { page, size: 10 };
			if (filters.keyword.trim()) params.keyword = filters.keyword.trim();
			if (filters.domain !== "ALL") params.domain = filters.domain;
			if (filters.dataType !== "ALL") params.dataType = filters.dataType;
			if (filters.sourceSystem.trim()) params.sourceSystem = filters.sourceSystem.trim();
			const data: any = await listMetadataStandards(params);
			const content = Array.isArray(data?.content) ? data.content : [];
			setRows(content);
			setTotal(Number(data?.total ?? 0));
		} catch (err: any) {
			console.error(err);
			toast.error(err?.message ?? "加载元数据标准失败");
		} finally {
			setLoading(false);
		}
	}, [filters, page]);

	useEffect(() => {
		void load();
	}, [load]);

	const downloadTemplate = useCallback(async () => {
		try {
			const { userToken } = userStore.getState() as any;
			const raw = String(userToken?.accessToken || "").trim();
			const token = raw.startsWith("Bearer ") ? raw.slice(7).trim() : raw;
			const resp = await fetch(`${GLOBAL_CONFIG.apiBaseUrl}/modeling/metadata-standards/template`, {
				method: "GET",
				headers: token ? { Authorization: `Bearer ${token}` } : undefined,
			});
			if (!resp.ok) {
				throw new Error(`下载失败（${resp.status}）`);
			}
			const blob = await resp.blob();
			const url = window.URL.createObjectURL(blob);
			const link = document.createElement("a");
			link.href = url;
			link.download = "metadata-standards-template.zip";
			document.body.appendChild(link);
			link.click();
			link.remove();
			window.URL.revokeObjectURL(url);
		} catch (err: any) {
			console.error(err);
			toast.error(err?.message ?? "下载模板失败");
		}
	}, []);

	const openCreate = () => {
		setEditMode("create");
		setEditing(null);
		form.resetFields();
		form.setFieldsValue({
			nullable: true,
			isPk: false,
			securityLevel: "INTERNAL",
		});
		setEditOpen(true);
	};

	const openEdit = (row: MetadataStandard) => {
		setEditMode("edit");
		setEditing(row);
		form.resetFields();
		form.setFieldsValue({
			fieldNameCn: row.fieldNameCn,
			fieldNameEn: row.fieldNameEn,
			dataType: row.dataType,
			dataLength: row.dataLength,
			dataPrecision: row.dataPrecision,
			dataScale: row.dataScale,
			nullable: row.nullable ?? true,
			domain: row.domain,
			description: row.description,
			sourceSystem: row.sourceSystem,
			codeSet: row.codeSet,
			defaultValue: row.defaultValue,
			isPk: row.isPk ?? false,
			securityLevel: row.securityLevel ?? "INTERNAL",
		});
		setEditOpen(true);
	};

	const submitEdit = async () => {
		try {
			const values = await form.validateFields();
			setSaving(true);
			if (editMode === "create") {
				await createMetadataStandard(values);
				toast.success("已创建元数据标准");
			} else if (editing?.id) {
				await updateMetadataStandard(editing.id, values);
				toast.success("已更新元数据标准");
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

	const confirmDelete = (row: MetadataStandard) => {
		Modal.confirm({
			title: "确认删除",
			content: `确定删除元数据标准 “${row.fieldNameCn || row.fieldNameEn}”？`,
			okText: "删除",
			okButtonProps: { danger: true },
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteMetadataStandard(row.id);
					toast.success("已删除");
					void load();
				} catch (err: any) {
					console.error(err);
					toast.error(err?.message ?? "删除失败");
				}
			},
		});
	};

	const submitImport = async () => {
		if (!fileInputRef.current?.files?.length) {
			toast.error("请选择文件");
			return;
		}
		setImporting(true);
		try {
			const formData = new FormData();
			formData.append("file", fileInputRef.current.files[0]);
			const result = await importMetadataStandards(formData);
			setImportResult(result);
			toast.success("导入完成");
			void load();
		} catch (err: any) {
			console.error(err);
			toast.error(err?.message ?? "导入失败");
		} finally {
			setImporting(false);
		}
	};

	const columns: ColumnsType<MetadataStandard> = useMemo(
		() => [
			{ title: "字段中文名", dataIndex: "fieldNameCn", key: "fieldNameCn", width: 140 },
			{ title: "字段英文名", dataIndex: "fieldNameEn", key: "fieldNameEn", width: 160 },
			{ title: "类型", dataIndex: "dataType", key: "dataType", width: 120 },
			{ title: "长度/精度", key: "len", width: 140, render: (_, row) => row.dataLength ?? row.dataPrecision ?? "-" },
			{ title: "可空", key: "nullable", width: 80, render: (_, row) => (row.nullable ? "Y" : "N") },
			{ title: "业务域", dataIndex: "domain", key: "domain", width: 120 },
			{ title: "来源系统", dataIndex: "sourceSystem", key: "sourceSystem", width: 120 },
			{
				title: "密级",
				dataIndex: "securityLevel",
				key: "securityLevel",
				width: 110,
				render: (v) => <Tag>{v || "INTERNAL"}</Tag>,
			},
			{ title: "描述", dataIndex: "description", key: "description", ellipsis: true },
			{
				title: "操作",
				key: "op",
				width: 140,
				render: (_, row) => (
					<Space>
						<Button size="small" onClick={() => openEdit(row)}>
							编辑
						</Button>
						<Button size="small" danger onClick={() => confirmDelete(row)}>
							删除
						</Button>
					</Space>
				),
			},
		],
		[],
	);

	return (
		<div className="space-y-4">
			<Card
				title="元数据标准"
				extra={
					<Space>
						<Button onClick={downloadTemplate}>导出模板（含校验规则）</Button>
						<Button onClick={() => setImportOpen(true)}>导入</Button>
						<Button type="primary" onClick={openCreate}>
							新增
						</Button>
					</Space>
				}
			>
				<Paragraph style={{ marginBottom: 12 }}>
					<Text type="secondary">元数据标准用于沉淀字段字典，为数据标准与模型模板提供统一来源。</Text>
				</Paragraph>
				<div className="flex flex-wrap items-center gap-2">
					<Input
						placeholder="关键字（字段名/描述）"
						value={filters.keyword}
						onChange={(e) => setFilters((prev) => ({ ...prev, keyword: e.target.value }))}
						style={{ width: 200 }}
					/>
					<Select
						value={filters.domain}
						style={{ width: 180 }}
						onChange={(value) => setFilters((prev) => ({ ...prev, domain: value }))}
					>
						<Select.Option value="ALL">全部业务域</Select.Option>
						{domainOptions.map((opt) => (
							<Select.Option key={opt.value} value={opt.value}>
								{opt.label}
							</Select.Option>
						))}
					</Select>
					<Select
						value={filters.dataType}
						style={{ width: 160 }}
						onChange={(value) => setFilters((prev) => ({ ...prev, dataType: value }))}
					>
						<Select.Option value="ALL">全部类型</Select.Option>
						{DATA_TYPE_OPTIONS.map((opt) => (
							<Select.Option key={opt} value={opt}>
								{opt}
							</Select.Option>
						))}
					</Select>
					<Input
						placeholder="来源系统"
						value={filters.sourceSystem}
						onChange={(e) => setFilters((prev) => ({ ...prev, sourceSystem: e.target.value }))}
						style={{ width: 160 }}
					/>
					<Button onClick={() => setPage(0)}>搜索</Button>
				</div>
			</Card>

			<Card title="标准列表">
				<Table
					rowKey={(row) => row.id}
					loading={loading}
					columns={columns}
					dataSource={rows}
					pagination={{
						current: page + 1,
						pageSize: 10,
						total,
						onChange: (next) => setPage(next - 1),
					}}
				/>
			</Card>

			<Card title="校验规则">
				<ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">
					{RULES.map((rule) => (
						<li key={rule}>{rule}</li>
					))}
				</ul>
			</Card>

			<Card title="错误规则清单">
				<ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">
					{ERROR_RULES.map((rule) => (
						<li key={rule}>{rule}</li>
					))}
				</ul>
			</Card>

			<Modal
				open={editOpen}
				title={editMode === "create" ? "新增元数据标准" : "编辑元数据标准"}
				onCancel={() => setEditOpen(false)}
				onOk={submitEdit}
				confirmLoading={saving}
				destroyOnClose
			>
				<Form form={form} layout="vertical">
					<Form.Item name="fieldNameCn" label="字段中文名" rules={[{ required: true, message: "请输入字段中文名" }]}>
						<Input />
					</Form.Item>
					<Form.Item name="fieldNameEn" label="字段英文名" rules={[{ required: true, message: "请输入字段英文名" }]}>
						<Input />
					</Form.Item>
					<Form.Item name="dataType" label="数据类型" rules={[{ required: true, message: "请选择数据类型" }]}>
						<Select showSearch allowClear options={DATA_TYPE_OPTIONS.map((v) => ({ label: v, value: v }))} />
					</Form.Item>
					<Space style={{ width: "100%" }} size="middle">
						<Form.Item name="dataLength" label="长度" style={{ flex: 1 }}>
							<Input type="number" min={0} />
						</Form.Item>
						<Form.Item name="dataPrecision" label="精度" style={{ flex: 1 }}>
							<Input type="number" min={0} />
						</Form.Item>
						<Form.Item name="dataScale" label="小数位" style={{ flex: 1 }}>
							<Input type="number" min={0} />
						</Form.Item>
					</Space>
					<Form.Item name="nullable" label="是否可空" rules={[{ required: true, message: "请选择是否可空" }]}>
						<Select options={[{ label: "是", value: true }, { label: "否", value: false }]} />
					</Form.Item>
					<Form.Item name="domain" label="业务域" rules={[{ required: true, message: "请选择业务域" }]}>
						<Select
							showSearch
							allowClear
							options={domainOptions.map((opt) => ({ label: opt.label, value: opt.value }))}
						/>
					</Form.Item>
					<Form.Item name="sourceSystem" label="来源系统" rules={[{ required: true, message: "请输入来源系统" }]}>
						<Input />
					</Form.Item>
					<Form.Item name="description" label="字段描述" rules={[{ required: true, message: "请输入字段描述" }]}>
						<Input.TextArea rows={3} />
					</Form.Item>
					<Form.Item name="codeSet" label="码表/枚举（可选）">
						<Input />
					</Form.Item>
					<Form.Item name="defaultValue" label="默认值（可选）">
						<Input />
					</Form.Item>
					<Form.Item name="isPk" label="是否主键">
						<Select options={[{ label: "是", value: true }, { label: "否", value: false }]} />
					</Form.Item>
					<Form.Item name="securityLevel" label="密级">
						<Select
							options={[
								{ label: "INTERNAL", value: "INTERNAL" },
								{ label: "CONFIDENTIAL", value: "CONFIDENTIAL" },
								{ label: "SECRET", value: "SECRET" },
								{ label: "TOP_SECRET", value: "TOP_SECRET" },
							]}
						/>
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={importOpen}
				title="导入元数据标准"
				onCancel={() => setImportOpen(false)}
				onOk={submitImport}
				confirmLoading={importing}
				okText="开始导入"
				destroyOnClose
			>
				<input ref={fileInputRef} type="file" accept=".csv,text/csv" />
				{importResult && (
					<div className="mt-3 space-y-1 text-sm text-muted-foreground">
						<div>总行数：{importResult.totalRows ?? 0}</div>
						<div>新增：{importResult.created ?? 0}</div>
						<div>更新：{importResult.updated ?? 0}</div>
						<div>跳过：{importResult.skipped ?? 0}</div>
						{Array.isArray(importResult.errors) && importResult.errors.length > 0 && (
							<div>
								<div className="mt-2 font-medium text-foreground">错误明细：</div>
								<ul className="list-disc space-y-1 pl-5">
									{importResult.errors.slice(0, 20).map((item: string) => (
										<li key={item}>{item}</li>
									))}
								</ul>
							</div>
						)}
					</div>
				)}
			</Modal>
		</div>
	);
}
