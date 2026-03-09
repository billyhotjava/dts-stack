import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { BookOpenText, FolderTree, Link2, UserRound } from "lucide-react";
import { Button, Descriptions, Divider, Drawer, Form, Input, List, Modal, Space, Spin, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EyeOutlined } from "@ant-design/icons";
import { useNavigate, useSearchParams } from "react-router";
import {
	PlatformFilterBar,
	PlatformMetaPill,
	PlatformPageHero,
	PlatformSectionCard,
	PlatformSummaryCards,
} from "@/components/console-page";
import { EmptyState } from "@/components/empty-state";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import {
	createGlossaryTerm,
	deleteGlossaryTerm,
	getGlossaryTermReferences,
	listGlossaryTerms,
	updateGlossaryTerm,
} from "@/api/platformApi";

const { Text } = Typography;

const normalizeText = (value?: string) => String(value || "").trim();

type GlossaryTerm = {
	id?: string;
	name?: string;
	code?: string;
	aliases?: string;
	definition?: string;
	domain?: string;
	owner?: string;
	tags?: string;
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

export default function GlossaryPage() {
	const navigate = useNavigate();
	const [searchParams, setSearchParams] = useSearchParams();
	const [keyword, setKeyword] = useState(searchParams.get("keyword") || "");
	const [items, setItems] = useState<GlossaryTerm[]>([]);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [editing, setEditing] = useState<GlossaryTerm | null>(null);
	const [detailOpen, setDetailOpen] = useState(false);
	const [detailTerm, setDetailTerm] = useState<GlossaryTerm | null>(null);
	const [referenceLoading, setReferenceLoading] = useState(false);
	const [references, setReferences] = useState<AssetReferencePayload | null>(null);
	const [form] = Form.useForm();
	const canManage = useGovernanceManageAccess();

	const applyKeyword = (value: string) => {
		const normalized = value || "";
		setKeyword(normalized);
		const params = new URLSearchParams(searchParams);
		if (normalized.trim()) {
			params.set("keyword", normalized.trim());
		} else {
			params.delete("keyword");
		}
		setSearchParams(params, { replace: true });
	};

	const loadGlossary = useCallback(async () => {
		setLoading(true);
		try {
			const resp = (await listGlossaryTerms({
				keyword: normalizeText(keyword) || undefined,
			})) as GlossaryTerm[];
			setItems(Array.isArray(resp) ? resp : []);
		} catch (err: any) {
			toast.error(err?.message || "加载术语失败");
		} finally {
			setLoading(false);
		}
	}, [keyword]);

	const loadReferences = useCallback(async (id?: string) => {
		if (!id) {
			setReferences(null);
			return;
		}
		setReferenceLoading(true);
		try {
			const resp = (await getGlossaryTermReferences(id)) as AssetReferencePayload;
			setReferences(resp || null);
		} catch (err: any) {
			setReferences(null);
			toast.error(err?.message || "加载引用关系失败");
		} finally {
			setReferenceLoading(false);
		}
	}, []);

	useEffect(() => {
		void loadGlossary();
	}, [loadGlossary]);

	const openModal = (row?: GlossaryTerm) => {
		setEditing(row || null);
		form.resetFields();
		form.setFieldsValue({
			name: row?.name,
			code: row?.code,
			aliases: row?.aliases,
			definition: row?.definition,
			domain: row?.domain,
			owner: row?.owner,
			tags: row?.tags,
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
				code: normalizeText(form.getFieldValue("code")) || undefined,
				aliases: normalizeText(form.getFieldValue("aliases")) || undefined,
				definition: normalizeText(form.getFieldValue("definition")) || undefined,
				domain: normalizeText(form.getFieldValue("domain")) || undefined,
				owner: normalizeText(form.getFieldValue("owner")) || undefined,
				tags: normalizeText(form.getFieldValue("tags")) || undefined,
			};
			if (editing?.id) {
				await updateGlossaryTerm(editing.id, payload);
				toast.success("术语已更新");
			} else {
				await createGlossaryTerm(payload);
				toast.success("术语已创建");
			}
			setModalOpen(false);
			setEditing(null);
			await loadGlossary();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	};

	const removeGlossary = (row: GlossaryTerm) => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!row?.id) return;
		void (async () => {
			try {
				const refs = (await getGlossaryTermReferences(row.id as string)) as AssetReferencePayload;
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
					title: "删除术语？",
					content: "删除后无法恢复。",
					okText: "删除",
					cancelText: "取消",
					onOk: async () => {
						try {
							await deleteGlossaryTerm(row.id as string);
							toast.success("术语已删除");
							await loadGlossary();
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

	const columns: ColumnsType<GlossaryTerm> = [
		{ title: "术语名称", dataIndex: "name", render: (t) => <Text strong className="text-blue-600">{t}</Text> },
		{ title: "标准编码", dataIndex: "code", render: (c) => <Text className="font-mono text-xs">{c || "-"}</Text> },
		{ title: "口径定义", dataIndex: "definition", ellipsis: true, render: (t) => t || "-" },
		{ title: "主题域", dataIndex: "domain", render: (t) => t || "-" },
		{ title: "负责人", dataIndex: "owner", render: (t) => t || "-" },
		{
			title: "操作",
			render: (_, row) => (
				<Space>
					<Button
						type="link"
						size="small"
						icon={<EyeOutlined />}
						onClick={() => {
							setDetailTerm(row);
							setDetailOpen(true);
							void loadReferences(row.id);
						}}
					>
						详情
					</Button>
					<Button type="link" size="small" onClick={() => openModal(row)} disabled={!canManage}>
						编辑
					</Button>
					<Button type="link" size="small" danger onClick={() => removeGlossary(row)} disabled={!canManage}>
						删除
					</Button>
				</Space>
			),
		},
	];

	const domainCount = new Set(items.map((item) => normalizeText(item.domain)).filter(Boolean)).size;
	const ownerCount = new Set(items.map((item) => normalizeText(item.owner)).filter(Boolean)).size;
	const summaryCards = [
		{
			label: "术语总数",
			value: items.length,
			note: "当前术语库可检索条目",
			icon: <BookOpenText className="h-5 w-5" />,
		},
		{
			label: "主题域覆盖",
			value: domainCount,
			note: "有明确主题域归属的术语集合",
			icon: <FolderTree className="h-5 w-5" />,
			tone: "info" as const,
		},
		{
			label: "负责人覆盖",
			value: ownerCount,
			note: "已明确责任人的术语数",
			icon: <UserRound className="h-5 w-5" />,
			tone: "success" as const,
		},
		{
			label: "当前详情引用",
			value: Number(references?.totalReferences || 0),
			note: detailTerm?.name ? `当前查看 ${detailTerm.name}` : "打开详情后显示引用数",
			icon: <Link2 className="h-5 w-5" />,
			tone: "warning" as const,
		},
	];

	return (
		<div className="space-y-6">
			<PlatformPageHero
				title="业务术语"
				description="维护业务术语、标准编码、口径定义与负责人，避免业务语义在不同模块里漂移。"
				eyebrow="Glossary Standards"
				actions={
					<Space wrap>
						<Button className="rounded-2xl" disabled>
							同步至 OpenMetadata
						</Button>
						<Button className="rounded-2xl" type="primary" onClick={() => openModal()} disabled={!canManage}>
							+ 新增术语
						</Button>
					</Space>
				}
				meta={
					<>
						<PlatformMetaPill>标准管理主数据页</PlatformMetaPill>
						<PlatformMetaPill>{canManage ? "当前账号可维护" : "当前账号只读"}</PlatformMetaPill>
						<PlatformMetaPill>支持查看引用关系后再决定是否删除</PlatformMetaPill>
					</>
				}
			/>

			<PlatformSummaryCards items={summaryCards} />

			<PlatformFilterBar>
				<div>
					<div className="text-sm font-semibold text-foreground">先搜索，再维护术语与口径</div>
					<div className="mt-1 text-sm text-muted-foreground">
						先用关键字收敛范围，再决定新增、编辑或查看引用关系。
					</div>
				</div>
				<div className="flex flex-wrap items-center gap-2">
					<Input.Search
						placeholder="搜索术语名称..."
						style={{ width: 300 }}
						value={keyword}
						onChange={(e) => setKeyword(e.target.value)}
						onSearch={(value) => applyKeyword(value)}
						allowClear
					/>
					<Button className="rounded-2xl" onClick={() => applyKeyword("")}>
						重置
					</Button>
				</div>
			</PlatformFilterBar>

			<PlatformSectionCard
				title="术语清单"
				description="列表页只负责检索和打开详情，引用影响与删除拦截都在详情链路里完成。"
			>
				{items.length === 0 && !loading ? (
					<EmptyState title="暂无术语" description="请先新增业务术语。" />
				) : (
					<Table
						rowKey={(row) => row.id || row.code || row.name || Math.random().toString(36)}
						dataSource={items}
						columns={columns}
						loading={loading}
						pagination={{ pageSize: 10 }}
					/>
				)}
			</PlatformSectionCard>

			<Modal
				open={modalOpen}
				title={editing ? "编辑术语" : "新增术语"}
				onCancel={() => setModalOpen(false)}
				onOk={submit}
				okText="保存"
				cancelText="取消"
				confirmLoading={saving}
				okButtonProps={{ disabled: !canManage }}
			>
				<Form layout="vertical" form={form}>
					<Form.Item name="name" label="术语名称" rules={[{ required: true, message: "请输入术语名称" }]}>
						<Input placeholder="例如：订单总额" />
					</Form.Item>
					<Form.Item name="code" label="标准编码">
						<Input placeholder="ORDER_AMT" />
					</Form.Item>
					<Form.Item name="definition" label="口径定义">
						<Input.TextArea rows={3} placeholder="说明业务口径" />
					</Form.Item>
					<Form.Item name="aliases" label="别名">
						<Input placeholder="可用逗号分隔" />
					</Form.Item>
					<Form.Item name="domain" label="主题域">
						<Input placeholder="交易域" />
					</Form.Item>
					<Form.Item name="owner" label="负责人">
						<Input placeholder="责任人" />
					</Form.Item>
					<Form.Item name="tags" label="标签">
						<Input placeholder="标签，逗号分隔" />
					</Form.Item>
				</Form>
			</Modal>

			<Drawer
				open={detailOpen}
				title="术语详情"
				width={680}
				onClose={() => setDetailOpen(false)}
				extra={
					canManage ? (
						<Button
							onClick={() => {
								if (!detailTerm) return;
								openModal(detailTerm);
							}}
						>
							编辑
						</Button>
					) : undefined
				}
			>
				<Descriptions column={1} bordered size="small">
					<Descriptions.Item label="术语名称">{detailTerm?.name || "-"}</Descriptions.Item>
					<Descriptions.Item label="标准编码">{detailTerm?.code || "-"}</Descriptions.Item>
					<Descriptions.Item label="口径定义">{detailTerm?.definition || "-"}</Descriptions.Item>
					<Descriptions.Item label="别名">{detailTerm?.aliases || "-"}</Descriptions.Item>
					<Descriptions.Item label="主题域">{detailTerm?.domain || "-"}</Descriptions.Item>
					<Descriptions.Item label="负责人">{detailTerm?.owner || "-"}</Descriptions.Item>
					<Descriptions.Item label="标签">{detailTerm?.tags || "-"}</Descriptions.Item>
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
