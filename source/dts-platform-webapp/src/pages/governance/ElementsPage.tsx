import { ArrowLeftOutlined } from "@ant-design/icons";
import {
	Alert,
	Breadcrumb,
	Button,
	Card,
	Descriptions,
	Divider,
	Drawer,
	Form,
	Input,
	List,
	Modal,
	Select,
	Space,
	Spin,
	Tag,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";
import {
	createMetadataStandard,
	deleteMetadataStandard,
	getMetadataStandardReferences,
	listMetadataStandards,
	listReferenceCodes,
	updateMetadataStandard,
} from "@/api/platformApi";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { CompactTable } from "@/components/table";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import { normalizeText } from "@/utils/textUtils";
import { resolveStandardOwnerReturnTo } from "./standardOwnerNavigation";

const { Text } = Typography;

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
type AssetReferenceItem = {
	type?: string;
	label?: string;
	id?: string;
	code?: string;
	name?: string;
	path?: string;
	reason?: string;
};
type AssetReferencePayload = { totalReferences?: number; items?: AssetReferenceItem[] };

const SECURITY_LEVELS = ["PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL"];
const parseIntOr = (value: string | null, fallback: number) => {
	const parsed = Number.parseInt(String(value || ""), 10);
	return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
};

export default function ElementsPage() {
	const navigate = useNavigate();
	const [searchParams, setSearchParams] = useSearchParams();
	const searchParamsValue = searchParams.toString();
	const keyword = searchParams.get("keyword") || "";
	const standardPackageApplied = searchParams.get("applied") === "1";
	const returnTarget = useMemo(() => resolveStandardOwnerReturnTo(searchParams), [searchParams]);
	const ownerModelSpecId = searchParams.get("modelSpecId")?.trim();
	const pageNum = parseIntOr(searchParams.get("page"), 0);
	const pageSize = parseIntOr(searchParams.get("size"), 10) || 10;
	const [data, setData] = useState<PagedPayload<MetadataStandard> | null>(null);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [editing, setEditing] = useState<MetadataStandard | null>(null);
	const [detailOpen, setDetailOpen] = useState(false);
	const [detailElement, setDetailElement] = useState<MetadataStandard | null>(null);
	const [referenceLoading, setReferenceLoading] = useState(false);
	const [references, setReferences] = useState<AssetReferencePayload | null>(null);
	const [codeOptions, setCodeOptions] = useState<ReferenceCodeDirectory[]>([]);
	const [loadError, setLoadError] = useState("");
	const [form] = Form.useForm();
	const canManage = useGovernanceManageAccess();

	const syncQuery = (patch?: { keyword?: string; page?: number; size?: number }) => {
		const params = new URLSearchParams(searchParamsValue);
		const nextKeyword = patch?.keyword ?? keyword;
		const nextPage = patch?.page ?? pageNum;
		const nextSize = patch?.size ?? pageSize;
		if (nextKeyword?.trim()) {
			params.set("keyword", nextKeyword.trim());
		} else {
			params.delete("keyword");
		}
		if (nextPage > 0) {
			params.set("page", String(nextPage));
		} else {
			params.delete("page");
		}
		if (nextSize !== 10) {
			params.set("size", String(nextSize));
		} else {
			params.delete("size");
		}
		if (params.toString() !== searchParamsValue) setSearchParams(params, { replace: true });
	};

	const loadElements = useCallback(async () => {
		setLoading(true);
		setLoadError("");
		try {
			const resp = (await listMetadataStandards({
				page: pageNum,
				size: pageSize,
				keyword: normalizeText(keyword) || undefined,
			})) as PagedPayload<MetadataStandard>;
			setData(resp || null);
		} catch (err: any) {
			const message = err?.message || "加载数据元失败";
			setLoadError(message);
			toast.error(message);
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

	const loadReferences = useCallback(async (id?: string) => {
		if (!id) {
			setReferences(null);
			return;
		}
		setReferenceLoading(true);
		try {
			const resp = (await getMetadataStandardReferences(id)) as AssetReferencePayload;
			setReferences(resp || null);
		} catch (err: any) {
			setReferences(null);
			toast.error(err?.message || "加载引用关系失败");
		} finally {
			setReferenceLoading(false);
		}
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
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
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
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!row?.id) return;
		void (async () => {
			try {
				const refs = (await getMetadataStandardReferences(row.id as string)) as AssetReferencePayload;
				const impactCount = Number(refs?.totalReferences || 0);
				if (impactCount > 0) {
					Modal.warning({
						title: `删除被拦截：存在 ${impactCount} 个引用对象`,
						content: (
							<List
								size="small"
								dataSource={(refs?.items || []).slice(0, 8)}
								renderItem={(item) => (
									<List.Item>
										<Text>
											{item.label || item.type}：{item.name || item.code || item.id}
										</Text>
									</List.Item>
								)}
							/>
						),
					});
					return;
				}
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
			} catch (err: any) {
				toast.error(err?.message || "删除前检查失败");
			}
		})();
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
					<Button
						type="link"
						size="small"
						data-testid="governance-elements-view-references"
						onClick={() => {
							setDetailElement(row);
							setDetailOpen(true);
							void loadReferences(row.id);
						}}
					>
						详情
					</Button>
					<Button type="link" size="small" onClick={() => openModal(row)} disabled={!canManage}>
						编辑
					</Button>
					<Button type="link" size="small" danger onClick={() => removeElement(row)} disabled={!canManage}>
						删除
					</Button>
				</Space>
			),
		},
	];

	const content = data?.content ?? [];

	return (
		<div className="space-y-4">
			<Breadcrumb items={[{ title: "数据治理中心" }, { title: "标准管理" }, { title: "数据元" }]} />
			<PageHeader
				title="数据治理中心 · 标准管理 / 数据元"
				actions={
					<Space wrap>
						{returnTarget ? (
							<Button icon={<ArrowLeftOutlined />} onClick={() => navigate(returnTarget.href)}>
								{returnTarget.label}
							</Button>
						) : null}
						<Button
							type="primary"
							onClick={() => openModal()}
							disabled={!canManage}
							data-testid="governance-elements-create"
						>
							+ 新增数据元
						</Button>
					</Space>
				}
			/>
			{returnTarget ? (
				<Alert
					showIcon
					data-testid="standard-owner-context"
					type="info"
					message={ownerModelSpecId ? "正在维护模型字段引用的数据元" : "正在维护建设规划引用的数据元"}
					description="数据元是全局标准正文，不归属于单个建设规划。完成维护后返回原模型字段，由模型保存稳定 ID 和版本引用。"
					action={
						<Button size="small" onClick={() => navigate(returnTarget.href)}>
							{returnTarget.label}
						</Button>
					}
				/>
			) : null}
			{loadError ? (
				<Alert
					showIcon
					type="error"
					message="数据元加载失败"
					description={loadError}
					action={
						<Button size="small" onClick={() => void loadElements()}>
							重新加载
						</Button>
					}
				/>
			) : null}
			<div className="rounded-md border border-border bg-muted/20 px-4 py-3 text-sm text-muted-foreground">
				数据元是模型字段的全局标准正文；模型字段只保存稳定 ID 和版本引用，通过引用关系可以查看实际使用位置。
			</div>
			{standardPackageApplied ? (
				<Alert
					type="success"
					showIcon
					message="标准包已应用"
					description="数据元目录已刷新。若从模型字段进入，请核对标准正文后返回模型完成稳定 ID 和版本绑定。"
				/>
			) : null}

			<Card>
				<Space wrap className="mb-4">
					<Input.Search
						placeholder="搜索数据元..."
						style={{ width: 300 }}
						value={keyword}
						onChange={(e) => {
							syncQuery({ keyword: e.target.value, page: 0 });
						}}
						onSearch={(value) => {
							syncQuery({ keyword: value || "", page: 0 });
						}}
						allowClear
					/>
					<Button
						onClick={() => {
							syncQuery({ keyword: "", page: 0 });
						}}
					>
						重置
					</Button>
				</Space>
				{loadError && content.length === 0 ? (
					<div className="rounded-md border border-dashed border-red-200 bg-red-50 px-4 py-8 text-center text-sm text-red-700">
						数据元加载失败，请使用上方“重新加载”恢复列表。
					</div>
				) : content.length === 0 && !loading ? (
					<EmptyState title="暂无数据元" description="请先新增数据元规范。" />
				) : (
					<CompactTable
						rowKey={(row) => row.id || row.fieldNameEn || row.fieldNameCn || Math.random().toString(36)}
						dataSource={content}
						columns={columns}
						loading={loading}
						pagination={{
							current: (data?.page ?? 0) + 1,
							pageSize: data?.size ?? pageSize,
							total: data?.total ?? 0,
							showSizeChanger: true,
							pageSizeOptions: [10, 20, 50, 100],
							showTotal: (total) => `共 ${total} 条`,
							onChange: (page, size) => {
								syncQuery({ page: size !== pageSize ? 0 : page - 1, size });
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
				okButtonProps={{ disabled: !canManage }}
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
									label:
										item.codeTypeName && item.codeTypeCode
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

			<Drawer
				open={detailOpen}
				title="数据元详情"
				width={720}
				onClose={() => setDetailOpen(false)}
				extra={
					canManage ? (
						<Button
							onClick={() => {
								if (!detailElement) return;
								openModal(detailElement);
							}}
						>
							编辑
						</Button>
					) : undefined
				}
			>
				<Descriptions bordered size="small" column={1}>
					<Descriptions.Item label="数据元名称">{detailElement?.fieldNameCn || "-"}</Descriptions.Item>
					<Descriptions.Item label="标准字段名">{detailElement?.fieldNameEn || "-"}</Descriptions.Item>
					<Descriptions.Item label="标准类型">{detailElement?.dataType || "-"}</Descriptions.Item>
					<Descriptions.Item label="长度">{detailElement?.dataLength ?? "-"}</Descriptions.Item>
					<Descriptions.Item label="精度">{detailElement?.dataPrecision ?? "-"}</Descriptions.Item>
					<Descriptions.Item label="小数位">{detailElement?.dataScale ?? "-"}</Descriptions.Item>
					<Descriptions.Item label="可空">
						{detailElement?.nullable == null ? "-" : detailElement?.nullable ? "是" : "否"}
					</Descriptions.Item>
					<Descriptions.Item label="主题域">{detailElement?.domain || "-"}</Descriptions.Item>
					<Descriptions.Item label="来源系统">{detailElement?.sourceSystem || "-"}</Descriptions.Item>
					<Descriptions.Item label="格式规则">{detailElement?.description || "-"}</Descriptions.Item>
					<Descriptions.Item label="码表编码">{detailElement?.codeSet || "-"}</Descriptions.Item>
					<Descriptions.Item label="默认值">{detailElement?.defaultValue || "-"}</Descriptions.Item>
					<Descriptions.Item label="主键字段">
						{detailElement?.isPk == null ? "-" : detailElement?.isPk ? "是" : "否"}
					</Descriptions.Item>
					<Descriptions.Item label="脱敏等级">{detailElement?.securityLevel || "-"}</Descriptions.Item>
				</Descriptions>
				<Divider />
				<Text strong>引用关系</Text>
				<div className="mt-2">
					{referenceLoading ? (
						<Spin size="small" />
					) : Number(references?.totalReferences || 0) === 0 ? (
						<Text type="secondary">暂无引用对象</Text>
					) : (
						<List
							size="small"
							dataSource={references?.items || []}
							renderItem={(item) => (
								<List.Item
									actions={[
										item.path ? (
											<Button
												key="jump"
												type="link"
												size="small"
												onClick={() => {
													navigate(item.path as string);
													setDetailOpen(false);
												}}
											>
												跳转
											</Button>
										) : null,
									]}
								>
									<List.Item.Meta
										title={`${item.label || item.type || "引用"} · ${item.name || item.code || item.id || "-"}`}
										description={item.reason || "-"}
									/>
								</List.Item>
							)}
						/>
					)}
				</div>
			</Drawer>
		</div>
	);
}
