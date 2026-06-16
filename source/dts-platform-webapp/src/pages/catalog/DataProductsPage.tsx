import { useEffect, useState } from "react";
import { Alert, Button, Card, Form, Input, Modal, Select, Space, Tag, Typography } from "antd";
import { } from "@ant-design/icons";
import { toast } from "sonner";
import { EmptyState } from "@/components/empty-state";
import {
	type DataProduct,
	createDataProduct,
	deleteDataProduct,
	listCatalogAssetsV2,
	listDataProducts,
	listIndicators,
	updateDataProduct,
} from "@/api/platformApi";
import { useRouter } from "@/routes/hooks";

const { Text } = Typography;

const STATUS_CONFIG = {
	DRAFT: { label: "草稿", color: "default" },
	PUBLISHED: { label: "已发布", color: "green" },
	OFFLINE: { label: "已下线", color: "orange" },
} as const;

const CLASSIFICATION_OPTIONS = [
	{ label: "公开", value: "PUBLIC" },
	{ label: "内部", value: "INTERNAL" },
	{ label: "敏感", value: "SENSITIVE" },
	{ label: "机密", value: "CONFIDENTIAL" },
];

const LIFECYCLE_OPTIONS = [
	{ label: "建设中", value: "BUILDING" },
	{ label: "可用", value: "ACTIVE" },
	{ label: "待治理", value: "PENDING_GOVERNANCE" },
	{ label: "退役中", value: "DEPRECATED" },
	{ label: "已归档", value: "ARCHIVED" },
];

const VISIBILITY_OPTIONS = [
	{ label: "仅负责人", value: "PRIVATE" },
	{ label: "内部可见", value: "INTERNAL" },
	{ label: "公开可见", value: "PUBLIC" },
];

const STATUS_OPTIONS = Object.entries(STATUS_CONFIG).map(([value, item]) => ({
	label: item.label,
	value,
}));

type CandidateOption = {
	label: string;
	value: string;
	meta?: string;
};

const parseList = (value?: string | null): string[] => {
	const raw = String(value || "").trim();
	if (!raw) return [];
	try {
		const parsed = JSON.parse(raw);
		if (Array.isArray(parsed)) {
			return parsed.map((item) => String(item || "").trim()).filter(Boolean);
		}
	} catch {
		// Fall back to delimiter parsing below.
	}
	return raw
		.split(/[\n,，;；]+/)
		.map((item) => item.trim())
		.filter(Boolean);
};

const serializeList = (value?: string[]): string =>
	(value || [])
		.map((item) => String(item || "").trim())
		.filter(Boolean)
		.join("\n");

const productMemberSummary = (product: DataProduct) => {
	const datasetIds = parseList(product.datasetIds);
	const indicatorCodes = parseList(product.indicatorCodes);
	return {
		datasetIds,
		indicatorCodes,
		datasetCount: datasetIds.length,
		indicatorCount: indicatorCodes.length,
	};
};

const resolveProductReadiness = (product: DataProduct) => {
	const members = productMemberSummary(product);
	const blockers: string[] = [];
	if (!members.datasetCount) blockers.push("未配置成员资产");
	if (!members.indicatorCount) blockers.push("未配置核心指标");
	if (!product.ownerDept) blockers.push("未配置负责部门");
	if (!product.classification) blockers.push("未配置产品密级");
	if (!product.freshnessSla) blockers.push("未配置刷新 SLA");
	return {
		blockers,
		ready: blockers.length === 0,
		members,
	};
};

export default function DataProductsPage() {
	const router = useRouter();
	const [products, setProducts] = useState<DataProduct[]>([]);
	const [total, setTotal] = useState(0);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [editTarget, setEditTarget] = useState<DataProduct | null>(null);
	const [saving, setSaving] = useState(false);
	const [assetOptions, setAssetOptions] = useState<CandidateOption[]>([]);
	const [indicatorOptions, setIndicatorOptions] = useState<CandidateOption[]>([]);
	const [candidateLoading, setCandidateLoading] = useState(false);
	const [detailTarget, setDetailTarget] = useState<DataProduct | null>(null);
	const [form] = Form.useForm();

	useEffect(() => {
		void load();
		void loadCandidates();
	}, []);

	const load = async () => {
		setLoading(true);
		try {
			const resp: any = await listDataProducts(0, 50);
			const content = Array.isArray(resp?.content) ? resp.content : [];
			setProducts(content);
			setTotal(Number(resp?.total ?? content.length));
		} catch {
			// global interceptor
		} finally {
			setLoading(false);
		}
	};

	const loadCandidates = async () => {
		setCandidateLoading(true);
		try {
			const [assetResp, indicatorResp]: any[] = await Promise.all([
				listCatalogAssetsV2({ page: 0, size: 200 }),
				listIndicators({ page: 0, size: 200 }),
			]);
			const assets = Array.isArray(assetResp?.content) ? assetResp.content : [];
			const indicators = Array.isArray(indicatorResp?.content) ? indicatorResp.content : [];
			setAssetOptions(
				assets
					.map((asset: any) => {
						const value = String(asset.id || asset.assetKey || asset.fqn || "").trim();
						if (!value) return null;
						const name = asset.displayName || asset.name || asset.table || asset.fqn || value;
						const meta = [asset.warehouseLayer, asset.classification, asset.governanceStatus].filter(Boolean).join(" / ");
						return {
							value,
							label: meta ? `${name}（${meta}）` : name,
							meta,
						};
					})
					.filter(Boolean) as CandidateOption[],
			);
			setIndicatorOptions(
				indicators
					.map((indicator: any) => {
						const value = String(indicator.code || indicator.metricCode || indicator.id || "").trim();
						if (!value) return null;
						const name = indicator.name || indicator.metricName || value;
						const meta = [indicator.domain, indicator.status].filter(Boolean).join(" / ");
						return {
							value,
							label: meta ? `${name}（${value}，${meta}）` : `${name}（${value}）`,
							meta,
						};
					})
					.filter(Boolean) as CandidateOption[],
			);
		} catch {
			// global interceptor
		} finally {
			setCandidateLoading(false);
		}
	};

	const openCreate = () => {
		setEditTarget(null);
		form.resetFields();
		form.setFieldsValue({
			status: "DRAFT",
			classification: "INTERNAL",
			lifecycleStatus: "BUILDING",
			visibility: "PRIVATE",
			datasetIds: [],
			indicatorCodes: [],
		});
		setModalOpen(true);
	};

	const openEdit = (product: DataProduct) => {
		setEditTarget(product);
		form.setFieldsValue({
			name: product.name,
			code: product.code,
			ownerDept: product.ownerDept,
			description: product.description,
			status: product.status ?? "DRAFT",
			classification: product.classification ?? "INTERNAL",
			freshnessSla: product.freshnessSla,
			lifecycleStatus: product.lifecycleStatus ?? "ACTIVE",
			visibility: product.visibility ?? "PRIVATE",
			consumerEntry: product.consumerEntry,
			datasetIds: parseList(product.datasetIds),
			indicatorCodes: parseList(product.indicatorCodes),
		});
		setModalOpen(true);
	};

	const openStatusEdit = (product: DataProduct, status: "PUBLISHED" | "OFFLINE") => {
		openEdit(product);
		form.setFieldValue("status", status);
	};

	const handleSave = async () => {
		const values = await form.validateFields();
		const payload: Omit<DataProduct, "id"> = {
			...values,
			datasetIds: serializeList(values.datasetIds),
			indicatorCodes: serializeList(values.indicatorCodes),
		};
		setSaving(true);
		try {
			if (editTarget?.id) {
				await updateDataProduct(editTarget.id, payload);
				toast.success("数据产品已更新");
			} else {
				await createDataProduct(payload);
				toast.success("数据产品已创建");
			}
			setModalOpen(false);
			void load();
		} catch {
			// global interceptor
		} finally {
			setSaving(false);
		}
	};

	const handleDelete = (product: DataProduct) => {
		if (!product.id) return;
		Modal.confirm({
			title: "确认删除",
			content: `确定要删除数据产品「${product.name || product.code || ""}」吗？此操作不可撤销。`,
			okText: "删除",
			okType: "danger",
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteDataProduct(product.id!);
					toast.success("已删除");
					void load();
				} catch {
					// global interceptor
				}
			},
		});
	};

	return (
		<div className="space-y-4">
			<Card
				title={`数据产品（${total}）`}
				extra={
					<Space>
						<Button onClick={() => void loadCandidates()} loading={candidateLoading}>
							刷新候选资产
						</Button>
						<Button
							type="primary"
							className="rounded-2xl"
							onClick={openCreate}
						>
							新建数据产品
						</Button>
					</Space>
				}
				loading={loading}
			>
				{products.length === 0 ? (
					<EmptyState
						title="暂无数据产品"
						description="将一组数据集和指标打包为数据产品，支持跨部门共享。"
					/>
				) : (
					<div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
						{products.map((product) => {
							const status = STATUS_CONFIG[product.status as keyof typeof STATUS_CONFIG] ?? STATUS_CONFIG.DRAFT;
							const readiness = resolveProductReadiness(product);
							const members = readiness.members;
							return (
								<div
									key={product.id}
									className="space-y-3 rounded-[20px] border border-slate-200 bg-white p-4 transition-all hover:border-blue-200 hover:shadow-sm"
								>
									<div className="flex items-start justify-between gap-2">
										<div className="min-w-0">
											<div className="truncate text-sm font-semibold text-slate-900">{product.name}</div>
											{product.code && <Text code className="text-xs">{product.code}</Text>}
										</div>
										<Space direction="vertical" size={2} align="end">
											<Tag color={status.color}>{status.label}</Tag>
											<Tag color={readiness.ready ? "green" : "orange"}>{readiness.ready ? "发布就绪" : "待补齐"}</Tag>
										</Space>
									</div>
									<div className="grid grid-cols-2 gap-2 text-xs">
										<div className="rounded-lg bg-slate-50 px-3 py-2">
											<div className="text-slate-500">成员资产</div>
											<div className="mt-1 font-semibold text-slate-900">{members.datasetCount}</div>
										</div>
										<div className="rounded-lg bg-slate-50 px-3 py-2">
											<div className="text-slate-500">核心指标</div>
											<div className="mt-1 font-semibold text-slate-900">{members.indicatorCount}</div>
										</div>
									</div>
									<div className="flex flex-wrap gap-1">
										<Tag>{product.classification || "未定密"}</Tag>
										<Tag color="blue">{product.lifecycleStatus || "未定生命周期"}</Tag>
										<Tag color={product.visibility === "PUBLIC" ? "green" : "default"}>{product.visibility || "PRIVATE"}</Tag>
									</div>
									<div className="space-y-1 text-xs text-slate-500">
										<div>负责部门：{product.ownerDept || "-"}</div>
										<div>刷新 SLA：{product.freshnessSla || "未配置"}</div>
										<div>消费入口：{product.consumerEntry || "未配置"}</div>
									</div>
									{readiness.blockers.length ? (
										<div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700">
											{readiness.blockers.join(" / ")}
										</div>
									) : null}
									<div className="space-y-1">
										<div className="text-xs font-medium text-slate-600">成员预览</div>
										<Space wrap size={[4, 4]}>
											{members.datasetIds.slice(0, 3).map((item) => <Tag key={item}>{item}</Tag>)}
											{members.datasetIds.length > 3 ? <Tag>+{members.datasetIds.length - 3}</Tag> : null}
											{!members.datasetIds.length ? <span className="text-xs text-slate-400">暂无成员资产</span> : null}
										</Space>
									</div>
									<div className="space-y-1">
										<div className="text-xs font-medium text-slate-600">核心指标预览</div>
										<Space wrap size={[4, 4]}>
											{members.indicatorCodes.slice(0, 3).map((item) => <Tag color="blue" key={item}>{item}</Tag>)}
											{members.indicatorCodes.length > 3 ? <Tag color="blue">+{members.indicatorCodes.length - 3}</Tag> : null}
											{!members.indicatorCodes.length ? <span className="text-xs text-slate-400">暂无核心指标</span> : null}
										</Space>
									</div>
									{product.description && (
										<div className="line-clamp-2 text-xs text-slate-600">{product.description}</div>
									)}
									<div className="flex flex-wrap items-center justify-end gap-2 border-t border-slate-100 pt-1">
										<Button size="small" onClick={() => openStatusEdit(product, "PUBLISHED")} disabled={!readiness.ready}>
											发布
										</Button>
										<Button size="small" onClick={() => router.push(`/security/dataset-access-approval?productId=${product.id}`)}>
											申请
										</Button>
										<Button
											size="small"
											disabled={!product.consumerEntry}
											onClick={() => product.consumerEntry && router.push(product.consumerEntry)}
											title={product.consumerEntry ? "进入配置的消费入口" : "请先配置消费入口"}
										>
											查看消费
										</Button>
										<Button size="small" onClick={() => router.push(`/governance/permission-audit?productId=${product.id}`)}>
											查看审计
										</Button>
										<Button size="small" onClick={() => setDetailTarget(product)}>
											合同
										</Button>
										<Button size="small" danger onClick={() => openStatusEdit(product, "OFFLINE")}>
											下线
										</Button>
										<Button size="small" onClick={() => openEdit(product)}>
											编辑
										</Button>
										<Button size="small" danger onClick={() => handleDelete(product)}>
											删除
										</Button>
									</div>
								</div>
							);
						})}
					</div>
				)}
			</Card>

			<Modal
				title={editTarget ? "编辑数据产品" : "新建数据产品"}
				open={modalOpen}
				onOk={() => void handleSave()}
				onCancel={() => setModalOpen(false)}
				confirmLoading={saving}
				width={780}
				destroyOnClose
			>
				<Form form={form} layout="vertical" className="mt-4">
					<Alert
						type="info"
						showIcon
						className="mb-4"
						message="数据产品应明确成员资产、核心指标、负责人、刷新 SLA 和消费边界；发布前仍需处理资产治理缺口与权限审批。"
					/>
					<Form.Item
						name="name"
						label="产品名称"
						rules={[{ required: true, message: "请输入产品名称" }]}
					>
						<Input placeholder="例：项目进度数据产品" />
					</Form.Item>
					<div className="grid gap-3 md:grid-cols-2">
						<Form.Item name="code" label="产品代码">
							<Input placeholder="例：pjm_progress_product（全局唯一）" />
						</Form.Item>
						<Form.Item name="ownerDept" label="负责部门">
							<Input placeholder="例：数字化部门" />
						</Form.Item>
					</div>
					<div className="grid gap-3 md:grid-cols-3">
						<Form.Item name="status" label="发布状态" initialValue="DRAFT">
							<Select options={STATUS_OPTIONS} />
						</Form.Item>
						<Form.Item name="classification" label="产品密级" initialValue="INTERNAL">
							<Select options={CLASSIFICATION_OPTIONS} />
						</Form.Item>
						<Form.Item name="lifecycleStatus" label="生命周期" initialValue="BUILDING">
							<Select options={LIFECYCLE_OPTIONS} />
						</Form.Item>
					</div>
					<div className="grid gap-3 md:grid-cols-3">
						<Form.Item name="freshnessSla" label="刷新 SLA">
							<Input placeholder="例：T+1 09:00 前 / 24h" />
						</Form.Item>
						<Form.Item name="visibility" label="消费可见性" initialValue="PRIVATE">
							<Select options={VISIBILITY_OPTIONS} />
						</Form.Item>
						<Form.Item name="consumerEntry" label="消费入口">
							<Input placeholder="BI 看板、API 或数据集链接" />
						</Form.Item>
					</div>
					<Form.Item
						name="datasetIds"
						label="成员资产"
						tooltip="候选项来自 assets-v2；也可以手工粘贴资产 ID / assetKey。"
					>
						<Select
							mode="tags"
							allowClear
							showSearch
							loading={candidateLoading}
							options={assetOptions}
							placeholder="选择或输入要打包的数据集、模型、BI 数据集"
						/>
					</Form.Item>
					<Form.Item
						name="indicatorCodes"
						label="核心指标"
						tooltip="候选项来自治理指标中心；也可以手工输入 metric code。"
					>
						<Select
							mode="tags"
							allowClear
							showSearch
							loading={candidateLoading}
							options={indicatorOptions}
							placeholder="选择或输入产品对外承诺的核心指标"
						/>
					</Form.Item>
					<Form.Item name="description" label="描述">
						<Input.TextArea rows={3} placeholder="数据产品的用途、消费对象和包含内容" />
					</Form.Item>
				</Form>
			</Modal>
			<DataProductContractModal
				product={detailTarget}
				onClose={() => setDetailTarget(null)}
				onOpenAsset={(assetId) => router.push(`/catalog/datasets/${assetId}`)}
			/>
		</div>
	);
}

function DataProductContractModal({
	product,
	onClose,
	onOpenAsset,
}: {
	product: DataProduct | null;
	onClose: () => void;
	onOpenAsset: (assetId: string) => void;
}) {
	if (!product) return null;
	const readiness = resolveProductReadiness(product);
	const members = readiness.members;
	return (
		<Modal
			title="数据产品合同"
			open
			onCancel={onClose}
			footer={<Button onClick={onClose}>关闭</Button>}
			width={820}
		>
			<Space direction="vertical" size={16} className="w-full">
				<Alert
					type={readiness.ready ? "success" : "warning"}
					showIcon
					message={readiness.ready ? "产品合同已具备发布前置条件" : "产品合同仍有缺口"}
					description={readiness.ready ? "成员资产、核心指标、密级、负责人和 SLA 已配置。" : readiness.blockers.join("；")}
				/>
				<div className="grid gap-3 md:grid-cols-3">
					<div className="rounded-lg border border-slate-200 bg-white px-3 py-2">
						<div className="text-xs text-slate-500">产品代码</div>
						<div className="mt-1 font-mono text-xs text-slate-900">{product.code || "-"}</div>
					</div>
					<div className="rounded-lg border border-slate-200 bg-white px-3 py-2">
						<div className="text-xs text-slate-500">密级</div>
						<div className="mt-1 text-sm font-semibold text-slate-900">{product.classification || "未配置"}</div>
					</div>
					<div className="rounded-lg border border-slate-200 bg-white px-3 py-2">
						<div className="text-xs text-slate-500">刷新 SLA</div>
						<div className="mt-1 text-sm font-semibold text-slate-900">{product.freshnessSla || "未配置"}</div>
					</div>
				</div>
				<div>
					<div className="mb-2 text-sm font-medium text-slate-700">成员资产</div>
					<Space wrap>
						{members.datasetIds.map((item) => (
							<Button size="small" key={item} onClick={() => onOpenAsset(item)}>
								{item}
							</Button>
						))}
						{!members.datasetIds.length ? <span className="text-sm text-slate-500">暂无成员资产</span> : null}
					</Space>
				</div>
				<div>
					<div className="mb-2 text-sm font-medium text-slate-700">核心指标</div>
					<Space wrap>
						{members.indicatorCodes.map((item) => <Tag color="blue" key={item}>{item}</Tag>)}
						{!members.indicatorCodes.length ? <span className="text-sm text-slate-500">暂无核心指标</span> : null}
					</Space>
				</div>
				{product.consumerEntry ? (
					<Alert type="info" showIcon message="消费入口" description={product.consumerEntry} />
				) : null}
			</Space>
		</Modal>
	);
}
