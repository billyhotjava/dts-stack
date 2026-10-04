import { Alert, Button, Card, Checkbox, Drawer, Empty, Form, Input, List, Modal, Space, Tag, Typography } from "antd";
import { useMemo, useState } from "react";
import { toast } from "sonner";
import {
	confirmModelingCandidatesApi,
	createConformedDimensionApi,
	deleteConformedDimensionApi,
	installModelingTemplateApi,
	listModelingTemplatesApi,
	type ModelingTemplate,
	type Sprint64BusinessProcess,
	type Sprint64ConformedDimension,
} from "@/api/sprint64GovernanceApi";
import { normalizeText } from "@/utils/textUtils";

const { Text } = Typography;

type CandidateKey = `process:${string}` | `dimension:${string}`;

export type ConformedDimensionCatalogCardProps = {
	domainId: string;
	canManage: boolean;
	processes: Sprint64BusinessProcess[];
	dimensions: Sprint64ConformedDimension[];
	onChanged: () => Promise<void> | void;
};

const sourceLabel = (sourceType: string) => {
	if (sourceType === "MANUAL") return "人工登记";
	if (sourceType === "TEMPLATE") return "模板候选";
	if (sourceType === "IMPORTED") return "资产导入";
	if (sourceType === "SCANNED") return "扫描发现";
	return sourceType || "未知来源";
};

export function ConformedDimensionCatalogCard({
	domainId,
	canManage,
	processes,
	dimensions,
	onChanged,
}: ConformedDimensionCatalogCardProps) {
	const [createOpen, setCreateOpen] = useState(false);
	const [creating, setCreating] = useState(false);
	const [templateOpen, setTemplateOpen] = useState(false);
	const [templateLoading, setTemplateLoading] = useState(false);
	const [templates, setTemplates] = useState<ModelingTemplate[]>([]);
	const [installingTemplateId, setInstallingTemplateId] = useState<string>();
	const [selectedCandidates, setSelectedCandidates] = useState<CandidateKey[]>([]);
	const [confirming, setConfirming] = useState(false);
	const [form] = Form.useForm();

	const candidates = useMemo(
		() => [
			...processes
				.filter((process) => !process.confirmed)
				.map((process) => ({
					key: `process:${process.processId}` as CandidateKey,
					kind: "业务过程",
					id: process.processId,
					name: process.name,
					sourceType: process.sourceType,
					sourceId: process.sourceId,
					sourceVersion: process.sourceVersion,
				})),
			...dimensions
				.filter((dimension) => !dimension.confirmed)
				.map((dimension) => ({
					key: `dimension:${dimension.dimensionId}` as CandidateKey,
					kind: "一致性维度",
					id: dimension.dimensionId,
					name: dimension.name,
					sourceType: dimension.sourceType,
					sourceId: dimension.sourceId,
					sourceVersion: dimension.sourceVersion,
				})),
		],
		[dimensions, processes],
	);

	const openCreateModal = () => {
		form.resetFields();
		setCreateOpen(true);
	};

	const createDimension = async () => {
		setCreating(true);
		try {
			const values = await form.validateFields();
			await createConformedDimensionApi(domainId, {
				dimensionId: normalizeText(values.dimensionId).toLowerCase(),
				name: normalizeText(values.name),
				sourceModel: normalizeText(values.sourceModel) || undefined,
			});
			await onChanged();
			form.resetFields();
			setCreateOpen(false);
			toast.success("一致性维度已登记，可直接用于总线矩阵");
		} catch (error: any) {
			if (!error?.errorFields) toast.error(error?.message || "一致性维度登记失败");
		} finally {
			setCreating(false);
		}
	};

	const deleteDimension = (dimension: Sprint64ConformedDimension) => {
		Modal.confirm({
			title: "删除一致性维度？",
			content: `将删除“${dimension.name}”。已被总线矩阵引用的维度不能删除。`,
			okText: "删除",
			okButtonProps: { danger: true },
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteConformedDimensionApi(domainId, dimension.dimensionId);
					await onChanged();
					toast.success("一致性维度已删除");
				} catch (error: any) {
					toast.error(
						error?.response?.status === 409
							? "该维度已被总线矩阵引用，请先取消对应勾选"
							: error?.message || "一致性维度删除失败",
					);
					throw error;
				}
			},
		});
	};

	const openTemplateDrawer = async () => {
		setTemplateOpen(true);
		setTemplateLoading(true);
		try {
			const result = await listModelingTemplatesApi();
			setTemplates(Array.isArray(result) ? result : []);
		} catch (error: any) {
			setTemplates([]);
			toast.error(error?.message || "可选模板加载失败");
		} finally {
			setTemplateLoading(false);
		}
	};

	const installTemplate = async (template: ModelingTemplate) => {
		setInstallingTemplateId(template.templateId);
		try {
			const result = await installModelingTemplateApi(template.templateId, domainId);
			await onChanged();
			toast.success(
				result.status === "ALREADY_INSTALLED"
					? "该版本已安装，现有候选保持不变"
					: `已生成 ${result.createdProcesses} 个过程候选、${result.createdDimensions} 个维度候选`,
			);
		} catch (error: any) {
			toast.error(error?.message || "模板安装失败");
		} finally {
			setInstallingTemplateId(undefined);
		}
	};

	const confirmCandidates = async (candidateKeys: CandidateKey[]) => {
		if (!candidateKeys.length) return;
		setConfirming(true);
		try {
			const processIds = candidateKeys
				.filter((key) => key.startsWith("process:"))
				.map((key) => key.slice("process:".length));
			const dimensionIds = candidateKeys
				.filter((key) => key.startsWith("dimension:"))
				.map((key) => key.slice("dimension:".length));
			await confirmModelingCandidatesApi(domainId, { processIds, dimensionIds });
			await onChanged();
			setSelectedCandidates((current) => current.filter((key) => !candidateKeys.includes(key)));
			toast.success("候选已确认，现在可以进入总线矩阵和后续建模");
		} catch (error: any) {
			toast.error(error?.message || "候选确认失败");
		} finally {
			setConfirming(false);
		}
	};

	return (
		<Card
			className="border-slate-200"
			title="一致性维度与候选审查"
			extra={
				<Space wrap>
					<Button size="small" onClick={() => void openTemplateDrawer()}>
						可选模板
					</Button>
					<Button size="small" type="primary" onClick={openCreateModal} disabled={!canManage}>
						新增一致性维度
					</Button>
				</Space>
			}
		>
			<Space direction="vertical" size="middle" className="w-full">
				{candidates.length ? (
					<Alert
						showIcon
						type="warning"
						message={`待确认候选 ${candidates.length} 个`}
						description="候选不会进入总线矩阵、推荐或后续建模。请核对业务含义后确认使用。"
					/>
				) : null}

				{candidates.length ? (
					<div className="rounded-xl border border-amber-200 bg-amber-50/60 p-3">
						<div className="mb-2 flex flex-wrap items-center justify-between gap-2">
							<Text strong>候选审查</Text>
							<Button
								size="small"
								type="primary"
								onClick={() => void confirmCandidates(selectedCandidates)}
								disabled={!canManage || !selectedCandidates.length}
								loading={confirming}
							>
								批量确认（{selectedCandidates.length}）
							</Button>
						</div>
						<Checkbox.Group
							className="w-full"
							value={selectedCandidates}
							onChange={(values) => setSelectedCandidates(values as CandidateKey[])}
						>
							<div className="grid grid-cols-1 gap-2 xl:grid-cols-2">
								{candidates.map((candidate) => (
									<div
										key={candidate.key}
										className="flex items-start justify-between gap-2 rounded-lg border border-amber-100 bg-white px-3 py-2"
									>
										<span className="flex min-w-0 items-start gap-2">
											<Checkbox value={candidate.key} disabled={!canManage} />
											<span className="min-w-0">
												<span className="block font-medium text-slate-800">{candidate.name}</span>
												<span className="block text-xs text-slate-500">
													{candidate.kind} · {candidate.id} · {sourceLabel(candidate.sourceType)}
													{candidate.sourceId ? ` · ${candidate.sourceId}` : ""}
													{candidate.sourceVersion ? ` v${candidate.sourceVersion}` : ""}
												</span>
											</span>
										</span>
										<Button
											size="small"
											type="link"
											disabled={!canManage || confirming}
											onClick={() => void confirmCandidates([candidate.key])}
										>
											单独确认
										</Button>
									</div>
								))}
							</div>
						</Checkbox.Group>
					</div>
				) : null}

				{dimensions.length ? (
					<List
						size="small"
						dataSource={dimensions}
						renderItem={(dimension) => (
							<List.Item
								actions={[
									<Button
										key="delete"
										type="link"
										danger
										size="small"
										disabled={!canManage}
										onClick={() => deleteDimension(dimension)}
									>
										删除
									</Button>,
								]}
							>
								<List.Item.Meta
									title={
										<Space wrap size={6}>
											<span>{dimension.name}</span>
											<Tag color={dimension.confirmed ? "green" : "gold"}>
												{dimension.confirmed ? "已确认" : "确认后使用"}
											</Tag>
											<Tag>{sourceLabel(dimension.sourceType)}</Tag>
										</Space>
									}
									description={`${dimension.dimensionId}${dimension.sourceModel ? ` · ${dimension.sourceModel}` : ""}${dimension.sourceId ? ` · ${dimension.sourceId}` : ""}${dimension.sourceVersion ? ` v${dimension.sourceVersion}` : ""}`}
								/>
							</List.Item>
						)}
					/>
				) : (
					<Empty description="暂无一致性维度，可人工登记或从可选模板生成候选" image={Empty.PRESENTED_IMAGE_SIMPLE} />
				)}
			</Space>

			<Modal
				open={createOpen}
				title="新增一致性维度"
				onCancel={() => setCreateOpen(false)}
				onOk={() => void createDimension()}
				okText="登记维度"
				cancelText="取消"
				confirmLoading={creating}
			>
				<Form form={form} layout="vertical">
					<Form.Item name="name" label="维度名称" rules={[{ required: true, message: "请输入维度名称" }]}>
						<Input placeholder="例如：组织机构" />
					</Form.Item>
					<Form.Item
						name="dimensionId"
						label="维度编码"
						rules={[
							{ required: true, message: "请输入维度编码" },
							{ pattern: /^[a-z0-9][a-z0-9_-]{1,127}$/, message: "使用小写字母、数字、下划线和连字符" },
						]}
					>
						<Input placeholder="organization" />
					</Form.Item>
					<Form.Item name="sourceModel" label="来源模型（可选）">
						<Input placeholder="dim_organization" />
					</Form.Item>
				</Form>
			</Modal>

			<Drawer open={templateOpen} title="可选模板" width={520} onClose={() => setTemplateOpen(false)}>
				<List
					loading={templateLoading}
					dataSource={templates}
					locale={{ emptyText: "当前没有可安装的模板包" }}
					renderItem={(template) => (
						<List.Item
							actions={[
								<Button
									key="install"
									type="primary"
									size="small"
									disabled={!canManage}
									loading={installingTemplateId === template.templateId}
									onClick={() => void installTemplate(template)}
								>
									显式安装
								</Button>,
							]}
						>
							<List.Item.Meta
								title={
									<Space wrap>
										<span>{template.name}</span>
										<Tag>v{template.version}</Tag>
										{template.industry ? <Tag color="blue">{template.industry}</Tag> : null}
									</Space>
								}
								description={`${template.description || "无说明"} · ${template.businessProcesses.length} 个过程 / ${template.conformedDimensions.length} 个维度`}
							/>
						</List.Item>
					)}
				/>
			</Drawer>
		</Card>
	);
}
