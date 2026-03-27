import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Badge, Button, Card, Divider, Form, Input, List, Modal, Select, Space, Spin, Tag, Typography } from "antd";
import { useNavigate, useSearchParams } from "react-router";
import { EmptyState } from "@/components/empty-state";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import { createModelTemplate, deleteModelTemplate, getModelTemplateReferences, listModelTemplates, updateModelTemplate } from "@/api/platformApi";
import { normalizeText } from "@/utils/textUtils";

const { Text } = Typography;

type ModelingTemplate = {
	id?: string;
	name?: string;
	layer?: string;
	status?: string;
	version?: string;
	versionNotes?: string;
	namingRule?: string;
	fieldsTemplate?: string;
	metadataStandardIds?: string;
	reviewChecklist?: string;
};

type ParsedField = {
	name: string;
	type?: string;
	desc?: string;
	required?: boolean;
};

type AssetReferenceItem = {
	type?: string;
	label?: string;
	id?: string;
	code?: string;
	name?: string;
	path?: string;
	reason?: string;
};

type AssetReferencePayload = {
	totalReferences?: number;
	items?: AssetReferenceItem[];
};

const splitTokens = (line: string) =>
	line
		.split(/[|,，\t]/)
		.map((token) => token.trim())
		.filter(Boolean);

const parseFieldsTemplate = (value?: string): ParsedField[] => {
	const raw = String(value || "").trim();
	if (!raw) return [];
	let lines = raw.split(/\r?\n/);
	if (lines.length === 1 && raw.includes(";")) {
		lines = raw.split(";").map((item) => item.trim());
	}
	return lines
		.map((line) => line.trim().replace(/^[-*•]\s*/, ""))
		.filter(Boolean)
		.map((line) => {
			const tokens = splitTokens(line);
			const name = tokens[0] || line;
			const type = tokens[1];
			const desc = tokens[2];
			const requiredHint = tokens[3] || line;
			const required = /必|required|yes|true/i.test(requiredHint);
			return { name, type, desc, required };
		});
};

const parseTags = (value?: string): string[] =>
	String(value || "")
		.split(/[,，\n]/)
		.map((item) => item.trim())
		.filter(Boolean);

export default function TemplatesPage() {
	const navigate = useNavigate();
	const [searchParams, setSearchParams] = useSearchParams();
	const [items, setItems] = useState<ModelingTemplate[]>([]);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [editing, setEditing] = useState<ModelingTemplate | null>(null);
	const [activeId, setActiveId] = useState<string | null>(searchParams.get("active") || null);
	const [query, setQuery] = useState(searchParams.get("keyword") || "");
	const [form] = Form.useForm();
	const [fieldModalOpen, setFieldModalOpen] = useState(false);
	const [fieldSaving, setFieldSaving] = useState(false);
	const [fieldForm] = Form.useForm();
	const [referenceLoading, setReferenceLoading] = useState(false);
	const [references, setReferences] = useState<AssetReferencePayload | null>(null);
	const canManage = useGovernanceManageAccess();

	const syncQuery = (patch?: { keyword?: string; active?: string | null }) => {
		const params = new URLSearchParams(searchParams);
		const keyword = patch?.keyword ?? query;
		const active = patch?.active ?? activeId;
		if (keyword?.trim()) {
			params.set("keyword", keyword.trim());
		} else {
			params.delete("keyword");
		}
		if (active?.trim()) {
			params.set("active", active.trim());
		} else {
			params.delete("active");
		}
		setSearchParams(params, { replace: true });
	};

	const loadTemplates = useCallback(async () => {
		setLoading(true);
		try {
			const resp = (await listModelTemplates()) as ModelingTemplate[];
			setItems(Array.isArray(resp) ? resp : []);
		} catch (err: any) {
			toast.error(err?.message || "加载模板失败");
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		void loadTemplates();
	}, [loadTemplates]);

	useEffect(() => {
		syncQuery();
	}, [query, activeId]);

	const openModal = (row?: ModelingTemplate) => {
		setEditing(row || null);
		form.resetFields();
		form.setFieldsValue({
			name: row?.name,
			layer: row?.layer,
			status: row?.status,
			version: row?.version,
			versionNotes: row?.versionNotes,
			namingRule: row?.namingRule,
			fieldsTemplate: row?.fieldsTemplate,
			metadataStandardIds: row?.metadataStandardIds,
			reviewChecklist: row?.reviewChecklist,
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
			const values = await form.validateFields(["name"]);
			const payload = {
				name: normalizeText(values.name),
				layer: normalizeText(form.getFieldValue("layer")) || undefined,
				status: normalizeText(form.getFieldValue("status")) || undefined,
				version: normalizeText(form.getFieldValue("version")) || undefined,
				versionNotes: normalizeText(form.getFieldValue("versionNotes")) || undefined,
				namingRule: normalizeText(form.getFieldValue("namingRule")) || undefined,
				fieldsTemplate: normalizeText(form.getFieldValue("fieldsTemplate")) || undefined,
				metadataStandardIds: normalizeText(form.getFieldValue("metadataStandardIds")) || undefined,
				reviewChecklist: normalizeText(form.getFieldValue("reviewChecklist")) || undefined,
			};
			if (editing?.id) {
				await updateModelTemplate(editing.id, payload);
				toast.success("模板已更新");
			} else {
				await createModelTemplate(payload);
				toast.success("模板已创建");
			}
			setModalOpen(false);
			setEditing(null);
			await loadTemplates();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	};

	const buildTemplatePayload = (template: ModelingTemplate, overrides?: Partial<ModelingTemplate>) => ({
		name: normalizeText(overrides?.name ?? template.name),
		layer: normalizeText(overrides?.layer ?? template.layer) || undefined,
		status: normalizeText(overrides?.status ?? template.status) || undefined,
		version: normalizeText(overrides?.version ?? template.version) || undefined,
		versionNotes: normalizeText(overrides?.versionNotes ?? template.versionNotes) || undefined,
		namingRule: normalizeText(overrides?.namingRule ?? template.namingRule) || undefined,
		fieldsTemplate: normalizeText(overrides?.fieldsTemplate ?? template.fieldsTemplate) || undefined,
		metadataStandardIds: normalizeText(overrides?.metadataStandardIds ?? template.metadataStandardIds) || undefined,
		reviewChecklist: normalizeText(overrides?.reviewChecklist ?? template.reviewChecklist) || undefined,
	});

	const removeTemplate = (row: ModelingTemplate) => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!row?.id) return;
		void (async () => {
			try {
				const refs = (await getModelTemplateReferences(row.id as string)) as AssetReferencePayload;
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
					title: "删除模板？",
					content: "删除后无法恢复。",
					okText: "删除",
					cancelText: "取消",
					onOk: async () => {
						try {
							await deleteModelTemplate(row.id as string);
							toast.success("模板已删除");
							await loadTemplates();
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

	useEffect(() => {
		if (items.length === 0) {
			setActiveId(null);
			return;
		}
		const hasActive = items.some((item) => item.id === activeId || (!item.id && item.name === activeId));
		if (!hasActive) {
			const first = items[0];
			setActiveId(first.id || first.name || null);
		}
	}, [items, activeId]);

	const filteredItems = useMemo(() => {
		if (!query.trim()) return items;
		const keyword = query.trim().toLowerCase();
		return items.filter((item) => {
			const name = String(item.name || "").toLowerCase();
			const layer = String(item.layer || "").toLowerCase();
			return name.includes(keyword) || layer.includes(keyword);
		});
	}, [items, query]);

	const activeTemplate = useMemo(
		() => items.find((item) => item.id === activeId || (!item.id && item.name === activeId)) || null,
		[items, activeId]
	);

	useEffect(() => {
		const loadReferences = async () => {
			if (!activeTemplate?.id) {
				setReferences(null);
				return;
			}
			setReferenceLoading(true);
			try {
				const resp = (await getModelTemplateReferences(activeTemplate.id)) as AssetReferencePayload;
				setReferences(resp || null);
			} catch (err: any) {
				setReferences(null);
				toast.error(err?.message || "加载引用关系失败");
			} finally {
				setReferenceLoading(false);
			}
		};
		void loadReferences();
	}, [activeTemplate?.id]);

	const fieldRows = useMemo(() => parseFieldsTemplate(activeTemplate?.fieldsTemplate), [activeTemplate?.fieldsTemplate]);
	const metadataTags = useMemo(() => parseTags(activeTemplate?.metadataStandardIds), [activeTemplate?.metadataStandardIds]);
	const checklistItems = useMemo(() => parseTags(activeTemplate?.reviewChecklist), [activeTemplate?.reviewChecklist]);

	const openFieldEditor = (template: ModelingTemplate | null) => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!template?.id) {
			toast.error("请先保存模板后再编辑字段");
			return;
		}
		const parsed = parseFieldsTemplate(template.fieldsTemplate).map((field) => ({
			name: field.name,
			type: field.type || "",
			desc: field.desc || "",
			required: field.required ?? false,
		}));
		fieldForm.resetFields();
		fieldForm.setFieldsValue({
			fields: parsed.length > 0 ? parsed : [{ name: "", type: "", desc: "", required: true }],
		});
		setFieldModalOpen(true);
	};

	const serializeFieldsTemplate = (fields: ParsedField[]) =>
		fields
			.map((field) => {
				const name = normalizeText(field.name);
				if (!name) return "";
				const type = normalizeText(field.type);
				const desc = normalizeText(field.desc);
				const required = field.required ? "必选" : "可选";
				return [name, type, desc, required].filter(Boolean).join(" | ");
			})
			.filter(Boolean)
			.join("\n");

	const saveFields = async () => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!activeTemplate?.id) {
			toast.error("模板不存在，无法保存字段");
			return;
		}
		setFieldSaving(true);
		try {
			const values = await fieldForm.validateFields();
			const fields = (values?.fields || []) as ParsedField[];
			const payload = buildTemplatePayload(activeTemplate, {
				fieldsTemplate: serializeFieldsTemplate(fields),
			});
			await updateModelTemplate(activeTemplate.id, payload);
			toast.success("模板字段已更新");
			setFieldModalOpen(false);
			await loadTemplates();
		} catch (err: any) {
			toast.error(err?.message || "保存字段失败");
		} finally {
			setFieldSaving(false);
		}
	};

	return (
		<div className="space-y-4">
			<Card
				title="标准模板"
				extra={
					<Button type="primary" onClick={() => openModal()} disabled={!canManage}>
						+ 新增模板
					</Button>
				}
			>
				<div className="mb-3 flex flex-wrap items-center gap-2">
					<Input
						placeholder="搜索模板名称或层级..."
						style={{ width: 400 }}
						value={query}
						onChange={(event) => setQuery(event.target.value)}
					/>
				</div>
			</Card>

			<div className="grid gap-6 lg:grid-cols-3">
				<Card
					className="lg:col-span-1"
					title="模板列表"
				>
					<div className="space-y-4">
						{items.length === 0 && !loading ? (
							<EmptyState title="暂无模板" description="请先新增标准模板。" />
						) : (
							<List
								dataSource={filteredItems}
								loading={loading}
								renderItem={(item) => {
									const itemId = item.id || item.name || "";
									const isActive = itemId && itemId === activeId;
									return (
										<List.Item
											className={`cursor-pointer rounded-lg px-2 ${isActive ? "bg-blue-50" : "hover:bg-slate-50"}`}
											onClick={() => setActiveId(itemId)}
										>
											<Space>
												<Badge status={isActive ? "processing" : "default"} />
												<Text strong>{item.name || "未命名模板"}</Text>
											</Space>
										</List.Item>
									);
								}}
							/>
						)}
						<Button block type="dashed" className="mt-4" onClick={() => openModal()} disabled={!canManage}>
							+ 创建新模板
						</Button>
					</div>
				</Card>

				<Card
					className="lg:col-span-2"
					title={`模板详情${activeTemplate?.name ? ` · ${activeTemplate.name}` : ""}`}
					extra={
						activeTemplate ? (
							<Space>
								<Button size="small" onClick={() => openModal(activeTemplate)} disabled={!canManage}>
									编辑
								</Button>
								<Button size="small" danger onClick={() => removeTemplate(activeTemplate)} disabled={!canManage}>
									删除
								</Button>
							</Space>
						) : null
					}
				>
					{!activeTemplate ? (
						<EmptyState title="暂无模板" description="请先在左侧选择或新建模板。" />
					) : (
						<div className="space-y-6">
							<div className="grid gap-4 md:grid-cols-3">
								<div>
									<Text type="secondary">层级</Text>
									<div className="mt-1">
										<Tag>{activeTemplate.layer || "未配置"}</Tag>
									</div>
								</div>
								<div>
									<Text type="secondary">状态</Text>
									<div className="mt-1">
										<Tag color="green">{activeTemplate.status || "ACTIVE"}</Tag>
									</div>
								</div>
								<div>
									<Text type="secondary">版本</Text>
									<div className="mt-1">
										<Text strong>{activeTemplate.version || "-"}</Text>
									</div>
								</div>
							</div>

							<div>
								<Text type="secondary">模板说明</Text>
								<div className="mt-2 text-sm">
									{activeTemplate.versionNotes || "暂无说明，可在编辑模板中补充。"}
								</div>
							</div>

							<Divider />
							<div>
								<Text strong>强制包含字段 (Required Fields)</Text>
								<div className="mt-4 space-y-2">
									{fieldRows.length === 0 ? (
										<Text type="secondary">暂未配置字段模板。</Text>
									) : (
										fieldRows.map((field) => (
											<div
												key={`${field.name}-${field.type || "default"}`}
												className="flex flex-wrap items-center justify-between rounded-md border border-dashed border-slate-200 bg-slate-50 px-3 py-2"
											>
												<Space wrap>
													<Text code>{field.name}</Text>
													{field.type ? <Tag color="blue">{field.type}</Tag> : null}
													{field.desc ? (
														<Text type="secondary" style={{ fontSize: 12 }}>
															{field.desc}
														</Text>
													) : null}
												</Space>
												<Tag color={field.required ? "red" : "default"}>{field.required ? "必选" : "可选"}</Tag>
											</div>
										))
									)}
									<Button type="link" className="px-0" onClick={() => openFieldEditor(activeTemplate)} disabled={!canManage}>
										+ 编辑模板字段
									</Button>
								</div>
							</div>

							<Divider />
							<div>
								<Text strong>关联数据元</Text>
								<div className="mt-2">
									{metadataTags.length === 0 ? (
										<Text type="secondary">暂无关联数据元。</Text>
									) : (
										<Space wrap>
											{metadataTags.map((tag) => (
												<Tag key={tag}>{tag}</Tag>
											))}
										</Space>
									)}
								</div>
							</div>

							<Divider />
							<div>
								<Text strong>评审清单</Text>
								<div className="mt-2">
									{checklistItems.length === 0 ? (
										<Text type="secondary">暂无评审清单。</Text>
									) : (
										<ul className="list-disc space-y-1 pl-5 text-sm text-slate-600">
											{checklistItems.map((item) => (
												<li key={item}>{item}</li>
											))}
										</ul>
									)}
								</div>
							</div>

							<Divider />
							<div>
								<Text strong>引用关系</Text>
								<div className="mt-2">
									{referenceLoading ? (
										<Spin size="small" />
									) : Number(references?.totalReferences || 0) === 0 ? (
										<Text type="secondary">暂无引用对象。</Text>
									) : (
										<List
											size="small"
											dataSource={references?.items || []}
											renderItem={(item) => (
												<List.Item
													actions={[
														item.path ? (
															<Button key="jump" type="link" size="small" onClick={() => navigate(item.path as string)}>
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
							</div>
						</div>
					)}
				</Card>
			</div>

			<Modal
				open={modalOpen}
				title={editing ? "编辑模板" : "新增模板"}
				onCancel={() => setModalOpen(false)}
				onOk={submit}
				okText="保存"
				cancelText="取消"
				confirmLoading={saving}
				okButtonProps={{ disabled: !canManage }}
				width={760}
			>
				<Form layout="vertical" form={form}>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="name" label="模板名称" rules={[{ required: true, message: "请输入名称" }]}>
							<Input placeholder="公共审计字段模板" />
						</Form.Item>
						<Form.Item name="layer" label="层级">
							<Input placeholder="ODS / DWD / DWS" />
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="status" label="状态">
							<Input placeholder="ACTIVE" />
						</Form.Item>
						<Form.Item name="version" label="版本">
							<Input placeholder="v1" />
						</Form.Item>
					</div>
					<Form.Item name="versionNotes" label="版本说明">
						<Input.TextArea rows={2} placeholder="版本变化说明" />
					</Form.Item>
					<Form.Item name="namingRule" label="命名规则">
						<Input.TextArea rows={2} placeholder="例如：dwd_{domain}_{biz}" />
					</Form.Item>
					<Form.Item name="fieldsTemplate" label="字段模板">
						<Input.TextArea rows={4} placeholder="字段清单或模板说明" />
					</Form.Item>
					<Form.Item name="metadataStandardIds" label="关联数据元">
						<Input placeholder="数据元 ID 列表，逗号分隔" />
					</Form.Item>
					<Form.Item name="reviewChecklist" label="评审清单">
						<Input.TextArea rows={3} placeholder="模板检查清单" />
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={fieldModalOpen}
				title="编辑模板字段"
				onCancel={() => setFieldModalOpen(false)}
				onOk={saveFields}
				okText="保存字段"
				cancelText="取消"
				confirmLoading={fieldSaving}
				okButtonProps={{ disabled: !canManage }}
				width={860}
			>
				<Form form={fieldForm} layout="vertical">
					<Form.List name="fields">
						{(fields, { add, remove }) => (
							<div className="space-y-2">
								<div className="grid grid-cols-12 gap-2 text-xs text-slate-500">
									<div className="col-span-3">字段名</div>
									<div className="col-span-3">类型</div>
									<div className="col-span-4">描述</div>
									<div className="col-span-2">是否必选</div>
								</div>
								{fields.map((field) => (
									<div key={field.key} className="grid grid-cols-12 items-start gap-2">
										<Form.Item
											name={[field.name, "name"]}
											className="col-span-3 mb-0"
											rules={[{ required: true, message: "请输入字段名" }]}
										>
											<Input placeholder="dw_create_at" />
										</Form.Item>
										<Form.Item name={[field.name, "type"]} className="col-span-3 mb-0">
											<Input placeholder="TIMESTAMP" />
										</Form.Item>
										<Form.Item name={[field.name, "desc"]} className="col-span-4 mb-0">
											<Input placeholder="中台入库时间" />
										</Form.Item>
										<Form.Item name={[field.name, "required"]} className="col-span-2 mb-0">
											<Select
												options={[
													{ label: "必选", value: true },
													{ label: "可选", value: false },
												]}
											/>
										</Form.Item>
										<div className="col-span-12 flex justify-end">
											<Button type="link" danger size="small" onClick={() => remove(field.name)} disabled={!canManage}>
												删除
											</Button>
										</div>
									</div>
								))}
								<Button type="dashed" onClick={() => add({ required: true })} disabled={!canManage}>
									+ 添加字段
								</Button>
							</div>
						)}
					</Form.List>
					<Divider />
					<Text type="secondary">字段保存后将同步为标准模板的字段清单，用于模型落标与检查。</Text>
				</Form>
			</Modal>
		</div>
	);
}
