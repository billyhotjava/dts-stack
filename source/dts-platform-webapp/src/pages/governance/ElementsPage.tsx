import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Alert, Breadcrumb, Button, Card, Descriptions, Divider, Drawer, Form, Input, List, Modal, Select, Space, Spin, Tag, Typography } from "antd";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { DownloadOutlined, ImportOutlined } from "@ant-design/icons";
import { useNavigate, useSearchParams } from "react-router";
import { EmptyState } from "@/components/empty-state";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import { PageHeader } from "@/components/page-header";
import { JourneyContextBar } from "@/components/journey";
import { createStandardBindingDraft, type StandardBindingDraftInput } from "@/pages/modeling/standardBindingDraft";
import {
	createMetadataStandard,
	createStandardBindingDraftSnapshot,
	deleteMetadataStandard,
	downloadDataStandardPackageTemplate,
	getMetadataStandardReferences,
	listMetadataStandards,
	listReferenceCodes,
	updateMetadataStandard,
} from "@/api/platformApi";
import { normalizeText } from "@/utils/textUtils";
import {
	buildPlanningRoute,
	resolveWarehousePlanningContext,
	resolveWarehousePlanningStatus,
	saveWarehousePlanningContext,
	type WarehousePlanningContext,
} from "./warehousePlanningContext";

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
	const [keyword, setKeyword] = useState(searchParams.get("keyword") || "");
	const bindingDraftRequested = searchParams.get("bindingDraft") === "1";
	const [pageNum, setPageNum] = useState(parseIntOr(searchParams.get("page"), 0));
	const [pageSize, setPageSize] = useState(parseIntOr(searchParams.get("size"), 10) || 10);
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
	const [templateDownloading, setTemplateDownloading] = useState(false);
	const [draftCreating, setDraftCreating] = useState(false);
	const [form] = Form.useForm();
	const canManage = useGovernanceManageAccess();
	const planningResolution = useMemo(() => resolveWarehousePlanningContext(searchParams), [searchParams]);
	const planningContext = planningResolution.context;
	const hasPlanningContext = Boolean(searchParams.get("planningId") || planningContext);

	const syncQuery = (patch?: { keyword?: string; page?: number; size?: number }) => {
		const params = new URLSearchParams(searchParams);
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
		setSearchParams(params, { replace: true });
	};

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
		syncQuery();
	}, [keyword, pageNum, pageSize]);

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

	const downloadStandardPackageTemplate = async () => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		setTemplateDownloading(true);
		try {
			const blob = await downloadDataStandardPackageTemplate();
			const url = URL.createObjectURL(blob);
			const link = document.createElement("a");
			link.href = url;
			link.download = "data-standard-package-template.zip";
			document.body.appendChild(link);
			link.click();
			link.remove();
			URL.revokeObjectURL(url);
			toast.success("标准包模板已下载");
		} catch (err: any) {
			toast.error(err?.message || "下载标准包模板失败");
		} finally {
			setTemplateDownloading(false);
		}
	};

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
	const planningStatus = hasPlanningContext
		? resolveWarehousePlanningStatus(planningContext, {
				source: planningResolution.source,
				standardFieldCount: planningContext?.standardDraftId ? content.length : 0,
			})
		: null;
	const planningBlocked = Boolean(
		hasPlanningContext && (planningResolution.status === "blocked" || planningResolution.source !== "session"),
	);

	const buildFieldBindingDraftPayload = (): StandardBindingDraftInput => ({
		source: "metadata-elements",
		title: normalizeText(keyword) ? `数据元字段落标草稿：${normalizeText(keyword)}` : "数据元字段落标草稿",
		fields: content.map((row) => ({
			columnName: row.fieldNameEn,
			standardId: row.id,
			standardCode: row.fieldNameEn,
			standardName: row.fieldNameCn,
			dataType: row.dataType,
			nullable: row.nullable,
			codeSet: row.codeSet,
			securityLevel: row.securityLevel,
			description: row.description,
			domain: row.domain,
			sourceSystem: row.sourceSystem,
			isPk: row.isPk,
		})),
		metadata: {
			totalFields: content.length,
			keyword: normalizeText(keyword) || undefined,
			planningId: planningContext?.planningId,
			domainId: planningContext?.domainId,
			domainName: planningContext?.domainName,
			warehouseLayer: planningContext?.warehouseLayer,
			modelingMode: planningContext?.modelingMode,
			sourceId: planningContext?.sourceId,
		},
	});

	const continueToModeling = (draftId: string) => {
		const route = `/studio/low-code-development?standardDraftId=${encodeURIComponent(draftId)}&standardBindingSource=elements`;
		if (!planningContext || planningResolution.source !== "session") {
			navigate(route);
			return;
		}
		const nextContext: WarehousePlanningContext = {
			...planningContext,
			standardDraftId: draftId,
			updatedAt: new Date().toISOString(),
		};
		if (!saveWarehousePlanningContext(nextContext)) {
			toast.error("规划草稿更新失败，请重试");
			return;
		}
		navigate(buildPlanningRoute(route, nextContext));
	};

	const createFieldBindingDraft = async () => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!content.length) {
			toast.error("当前列表没有可输出的数据元");
			return;
		}
		if (planningBlocked) {
			toast.error(planningResolution.reason || "规划上下文不可用，请返回主题域重新确认规划");
			return;
		}
		const payload = buildFieldBindingDraftPayload();
		setDraftCreating(true);
		try {
			const draft = (await createStandardBindingDraftSnapshot(payload)) as { id?: string };
			if (!draft?.id) {
				throw new Error("missing_draft_id");
			}
			toast.success(`已保存字段落标快照：${content.length} 个数据元`);
			continueToModeling(draft.id);
		} catch (err: any) {
			const draft = createStandardBindingDraft(payload);
			toast.warning("后端快照保存失败，已使用浏览器会话草稿继续建模");
			continueToModeling(draft.id);
		} finally {
			setDraftCreating(false);
		}
	};

	return (
		<div className="space-y-4">
			<Breadcrumb items={[{ title: "数据治理中心" }, { title: "标准管理" }, { title: "数据元" }]} />
			<PageHeader
				title="数据治理中心 · 标准管理 / 数据元"
				actions={
					<Space>
						<Button
							icon={<DownloadOutlined />}
							onClick={downloadStandardPackageTemplate}
							loading={templateDownloading}
							disabled={!canManage}
							data-testid="governance-elements-template-download"
						>
							下载标准包模板
						</Button>
						<Button
							icon={<ImportOutlined />}
							onClick={() => navigate("/foundation/standard-package?from=elements")}
							disabled={!canManage}
							data-testid="governance-elements-standard-package-import"
						>
							导入标准包
						</Button>
						<Button
							onClick={createFieldBindingDraft}
							loading={draftCreating}
							disabled={!canManage || content.length === 0 || planningBlocked}
							data-testid="governance-elements-standard-binding-draft"
						>
							生成字段落标草稿
						</Button>
						<Button type="primary" onClick={() => openModal()} disabled={!canManage} data-testid="governance-elements-create">
							+ 新增数据元
						</Button>
					</Space>
				}
			/>
			<JourneyContextBar stage="standards" />
			{hasPlanningContext ? (
				<Alert
					showIcon
					data-testid="warehouse-planning-context"
					type={planningBlocked ? "error" : planningStatus?.status === "ready" ? "success" : "info"}
					message={planningBlocked ? "规划上下文不可用" : "已接入数仓规划上下文"}
					description={`主题域：${planningContext?.domainName || planningContext?.domainId || "-"} · 数仓层：${planningContext?.warehouseLayer || "-"} · 建模模式：${planningContext?.modelingMode === "dimension" ? "维度建模" : "-"}${planningResolution.reason ? ` · ${planningResolution.reason}` : ""}`}
					action={
						<Button
							size="small"
							onClick={() => {
								const route = `/governance/subjects${planningContext?.domainId ? `?active=${encodeURIComponent(planningContext.domainId)}` : ""}`;
								navigate(planningContext ? buildPlanningRoute(route, planningContext) : route);
							}}
						>
							返回主题域规划
						</Button>
					}
				/>
			) : null}
			<div className="rounded-md border border-border bg-muted/20 px-4 py-3 text-sm text-muted-foreground">
				数据元是 SQL 模型字段的标准来源；通过引用关系查看模型字段引用，避免标准只停留在治理台账。
			</div>
			{bindingDraftRequested ? (
				<Alert
					type="success"
					showIcon
					message="标准包已应用"
					description="请筛选或确认本批数据元，点击“生成字段落标草稿”后进入低代码建模，草稿会继续传递到 SQL 建模并生成可微调 SQL。"
				/>
			) : null}

			<Card>
				<Space className="mb-4">
					<Input.Search
						placeholder="搜索数据元..."
						style={{ width: 300 }}
						value={keyword}
						onChange={(e) => {
							setKeyword(e.target.value);
							setPageNum(0);
						}}
						onSearch={(value) => {
							setKeyword(value || "");
							setPageNum(0);
						}}
						allowClear
					/>
					<Button
						onClick={() => {
							setKeyword("");
							setPageNum(0);
						}}
					>
						重置
					</Button>
				</Space>
				{content.length === 0 && !loading ? (
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
								setPageNum(size !== pageSize ? 0 : page - 1);
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
					<Descriptions.Item label="可空">{detailElement?.nullable == null ? "-" : detailElement?.nullable ? "是" : "否"}</Descriptions.Item>
					<Descriptions.Item label="主题域">{detailElement?.domain || "-"}</Descriptions.Item>
					<Descriptions.Item label="来源系统">{detailElement?.sourceSystem || "-"}</Descriptions.Item>
					<Descriptions.Item label="格式规则">{detailElement?.description || "-"}</Descriptions.Item>
					<Descriptions.Item label="码表编码">{detailElement?.codeSet || "-"}</Descriptions.Item>
					<Descriptions.Item label="默认值">{detailElement?.defaultValue || "-"}</Descriptions.Item>
					<Descriptions.Item label="主键字段">{detailElement?.isPk == null ? "-" : detailElement?.isPk ? "是" : "否"}</Descriptions.Item>
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
