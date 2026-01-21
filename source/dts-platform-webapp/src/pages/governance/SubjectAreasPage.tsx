import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Badge,
	Button,
	Card,
	Divider,
	Empty,
	Form,
	Input,
	Layout,
	Modal,
	Select,
	Space,
	Tag,
	Tree,
	Typography,
} from "antd";
import type { DataNode } from "antd/es/tree";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { createDomain, deleteDomain, getDomainTree, updateDomain } from "@/api/platformApi";

const { Sider, Content } = Layout;
const { Title, Text } = Typography;

const ROOT_KEY = "root";

type DomainNode = {
	id?: string;
	name?: string;
	code?: string;
	owner?: string;
	description?: string;
	parentId?: string | null;
	children?: DomainNode[];
};

const normalizeText = (value?: string) => String(value || "").trim();

const buildDomainIndex = (nodes: DomainNode[], map: Map<string, DomainNode>) => {
	nodes.forEach((node) => {
		if (node.id) {
			map.set(String(node.id), node);
		}
		if (node.children?.length) {
			buildDomainIndex(node.children, map);
		}
	});
};

const flattenDomains = (nodes: DomainNode[], acc: DomainNode[] = []) => {
	nodes.forEach((node) => {
		acc.push(node);
		if (node.children?.length) {
			flattenDomains(node.children, acc);
		}
	});
	return acc;
};

const isSystemDomain = (node: DomainNode): boolean => {
	const code = String(node.code || "");
	const desc = String(node.description || "");
	if (code.toUpperCase().startsWith("DS:")) {
		return true;
	}
	if (desc.startsWith("Schema from ") || desc.startsWith("Auto-created domain for schema")) {
		return true;
	}
	return false;
};

const filterSystemDomains = (nodes: DomainNode[]): DomainNode[] =>
	nodes
		.filter((node) => !isSystemDomain(node))
		.map((node) => ({
			...node,
			children: node.children ? filterSystemDomains(node.children) : [],
		}));

const filterDomains = (nodes: DomainNode[], keyword: string): DomainNode[] => {
	if (!keyword) return nodes;
	const needle = keyword.toLowerCase();
	return nodes
		.map((node) => {
			const childMatches = node.children ? filterDomains(node.children, keyword) : [];
			const matches =
				String(node.name || "").toLowerCase().includes(needle) ||
				String(node.code || "").toLowerCase().includes(needle) ||
				String(node.owner || "").toLowerCase().includes(needle);
			if (matches || childMatches.length) {
				return { ...node, children: childMatches };
			}
			return null;
		})
		.filter(Boolean) as DomainNode[];
};

const toTreeNodes = (nodes: DomainNode[]): DataNode[] =>
	nodes.map((node) => ({
		key: node.id || Math.random().toString(36),
		title: (
			<Space size={6}>
				<span>{node.name || "未命名主题域"}</span>
				{node.code ? <Tag color="blue">{node.code}</Tag> : null}
			</Space>
		),
		children: node.children ? toTreeNodes(node.children) : undefined,
	}));

export default function SubjectAreasPage() {
	const [keyword, setKeyword] = useState("");
	const [domainTree, setDomainTree] = useState<DomainNode[]>([]);
	const [domainIndex, setDomainIndex] = useState<Map<string, DomainNode>>(new Map());
	const [domainOptions, setDomainOptions] = useState<DomainNode[]>([]);
	const [selectedKey, setSelectedKey] = useState<string>(ROOT_KEY);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [editing, setEditing] = useState<DomainNode | null>(null);
	const [form] = Form.useForm();

	const loadDomainTree = useCallback(async () => {
		setLoading(true);
		try {
			const resp = (await getDomainTree()) as DomainNode[];
			const list = Array.isArray(resp) ? filterSystemDomains(resp) : [];
			const index = new Map<string, DomainNode>();
			buildDomainIndex(list, index);
			setDomainTree(list);
			setDomainIndex(index);
			setDomainOptions(flattenDomains(list, []));
			if (selectedKey !== ROOT_KEY && !index.has(selectedKey)) {
				setSelectedKey(ROOT_KEY);
			}
		} catch (err: any) {
			toast.error(err?.message || "加载主题域失败");
		} finally {
			setLoading(false);
		}
	}, [selectedKey]);

	useEffect(() => {
		void loadDomainTree();
	}, [loadDomainTree]);

	const filteredTree = useMemo(() => filterDomains(domainTree, keyword), [domainTree, keyword]);
	const treeData = useMemo(() => {
		const children = toTreeNodes(filteredTree);
		return [
			{
				key: ROOT_KEY,
				title: "全域主题 (Root)",
				children,
			},
		];
	}, [filteredTree]);

	const activeDomain = selectedKey !== ROOT_KEY ? domainIndex.get(selectedKey) || null : null;
	const activeChildren = activeDomain?.children || [];

	const openModal = (domain?: DomainNode | null, parentId?: string | null) => {
		setEditing(domain || null);
		form.resetFields();
		form.setFieldsValue({
			name: domain?.name,
			code: domain?.code,
			owner: domain?.owner,
			description: domain?.description,
			parentId: parentId ?? domain?.parentId ?? undefined,
		});
		setModalOpen(true);
	};

	const submit = async () => {
		setSaving(true);
		try {
			const values = await form.validateFields(["name"]);
			const parentId = normalizeText(form.getFieldValue("parentId")) || undefined;
			const payload: DomainNode = {
				name: normalizeText(values.name),
				code: normalizeText(form.getFieldValue("code")) || undefined,
				owner: normalizeText(form.getFieldValue("owner")) || undefined,
				description: normalizeText(form.getFieldValue("description")) || undefined,
				parent: parentId ? { id: parentId } : undefined,
			} as any;
			if (editing?.id) {
				if (editing.id === parentId) {
					throw new Error("上级主题域不能选择自身");
				}
				await updateDomain(editing.id, payload);
				toast.success("主题域已更新");
			} else {
				await createDomain(payload);
				toast.success("主题域已创建");
			}
			setModalOpen(false);
			setEditing(null);
			await loadDomainTree();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	};

	const confirmDelete = (domain?: DomainNode | null) => {
		if (!domain?.id) return;
		Modal.confirm({
			title: "删除主题域？",
			content: "删除后无法恢复，请确认该域下没有关联资产。",
			okText: "删除",
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteDomain(domain.id as string);
					toast.success("主题域已删除");
					setSelectedKey(ROOT_KEY);
					await loadDomainTree();
				} catch (err: any) {
					toast.error(err?.message || "删除失败");
				}
			},
		});
	};

	const parentOptions = useMemo(
		() => domainOptions.filter((item) => item.id && item.id !== editing?.id),
		[domainOptions, editing?.id],
	);

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据治理中心 · 主题域管理"
				description="维护业务主题域结构，支撑资产归类与治理视角。"
				actions={
					<Space>
						<Button onClick={() => openModal(null, null)}>新增根域</Button>
						<Button
							type="primary"
							onClick={() => openModal(null, activeDomain?.id || undefined)}
							disabled={!activeDomain?.id}
						>
							+ 新增子域
						</Button>
					</Space>
				}
			/>

			<Layout className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
				<Sider width={320} theme="light" className="border-r border-slate-200 p-4">
					<Space direction="vertical" className="w-full" size="middle">
						<div className="flex items-center justify-between">
							<Text strong>域目录结构</Text>
							<Button type="link" size="small" onClick={() => openModal(null, null)}>
								+ 新增域
							</Button>
						</div>
						<Input.Search
							placeholder="搜索主题域..."
							value={keyword}
							onChange={(e) => setKeyword(e.target.value)}
							allowClear
						/>
						<Tree
							showLine
							defaultExpandAll
							selectedKeys={[selectedKey]}
							treeData={treeData}
							onSelect={(keys) => {
								const key = String(keys?.[0] ?? ROOT_KEY);
								setSelectedKey(key || ROOT_KEY);
							}}
						/>
						{!loading && domainTree.length === 0 ? (
							<EmptyState title="暂无主题域" description="请先创建主题域。" />
						) : null}
					</Space>
				</Sider>
				<Content className="p-6">
					{loading ? (
						<Card loading />
					) : !activeDomain ? (
						<Card>
							<Title level={4}>全域主题视角</Title>
							<Text type="secondary">请选择左侧主题域查看详情与治理指标。</Text>
							<Divider />
							{domainTree.length === 0 ? (
								<Empty description="暂无主题域结构" />
							) : (
								<Space wrap>
									{domainOptions.slice(0, 12).map((item) => (
										<Tag key={item.id}>{item.name}</Tag>
									))}
									{domainOptions.length > 12 ? <Tag>+{domainOptions.length - 12} 更多</Tag> : null}
								</Space>
							)}
						</Card>
					) : (
						<div className="space-y-6">
							<div className="flex items-start justify-between gap-4">
								<div>
									<Space size={8} wrap>
										<Title level={4} style={{ margin: 0 }}>
											{activeDomain.name || "未命名主题域"}
										</Title>
										{activeDomain.code ? <Tag color="blue">{activeDomain.code}</Tag> : null}
									</Space>
									<div className="mt-2 text-sm text-slate-500">
										负责人：{activeDomain.owner || "未指定"} ｜ 子域数：{activeChildren.length} ｜ 资产数：-
									</div>
									{activeDomain.description ? (
										<Text type="secondary" className="block mt-2">
											{activeDomain.description}
										</Text>
									) : null}
								</div>
								<Space>
									<Button onClick={() => openModal(activeDomain, activeDomain.parentId)}>编辑域属性</Button>
									<Button danger onClick={() => confirmDelete(activeDomain)}>
										删除域
									</Button>
								</Space>
							</div>

							<Divider />

							<div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
								<Card title="关联术语" size="small">
									<Space wrap>
										<Tag>暂无挂载</Tag>
									</Space>
									<Button className="mt-3" type="dashed" block disabled>
										+ 挂载术语
									</Button>
								</Card>
								<Card title="域级治理指标" size="small">
									<div className="flex items-center justify-between py-1">
										<Text>落标率</Text>
										<Text strong>-</Text>
									</div>
									<div className="flex items-center justify-between py-1">
										<Text>质量分</Text>
										<Text strong>-</Text>
									</div>
									<div className="flex items-center justify-between py-1">
										<Text>合规覆盖</Text>
										<Text strong>-</Text>
									</div>
								</Card>
							</div>

							<Card title="子域列表" size="small">
								{activeChildren.length ? (
									<Space wrap>
										{activeChildren.map((child) => (
											<Tag key={child.id || child.name}>
												{child.name}
											</Tag>
										))}
									</Space>
								) : (
									<Space align="center">
										<Badge status="default" />
										<Text type="secondary">暂无子域</Text>
									</Space>
								)}
							</Card>
						</div>
					)}
				</Content>
			</Layout>

			<Modal
				open={modalOpen}
				title={editing ? "编辑主题域" : "新增主题域"}
				onCancel={() => setModalOpen(false)}
				onOk={submit}
				okText="保存"
				cancelText="取消"
				confirmLoading={saving}
			>
				<Form layout="vertical" form={form}>
					<Form.Item name="name" label="主题域名称" rules={[{ required: true, message: "请输入主题域名称" }]}>
						<Input placeholder="例如：财务域" />
					</Form.Item>
					<Form.Item name="code" label="主题域编码">
						<Input placeholder="FINANCE" />
					</Form.Item>
					<Form.Item name="owner" label="负责人">
						<Input placeholder="负责人姓名" />
					</Form.Item>
					<Form.Item name="parentId" label="上级主题域">
						<Select
							allowClear
							placeholder="无上级主题域"
							options={parentOptions.map((item) => ({
								label: item.name || item.code || "未命名主题域",
								value: item.id,
							}))}
						/>
					</Form.Item>
					<Form.Item name="description" label="说明">
						<Input.TextArea rows={3} placeholder="主题域描述" />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
