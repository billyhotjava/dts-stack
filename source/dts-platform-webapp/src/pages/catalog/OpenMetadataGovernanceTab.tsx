import { Alert, Button, Collapse, Descriptions, Form, Input, message, Select, Tag } from "antd";
import { useEffect, useState } from "react";
import {
	type ClassificationFactView,
	getCatalogClassificationFacts,
	raiseCatalogClassificationManualFloor,
	updateCatalogAssetV2Governance,
} from "@/api/platformApi";
import { AssetTagPanel } from "@/components/catalog/tags/AssetTagPanel";
import type { CatalogDomainOption } from "@/hooks/useCatalogDomainOptions";
import { classificationText } from "./assets/assetPageShared";

const LEVELS = [
	{ label: "公开", value: "PUBLIC" },
	{ label: "内部", value: "INTERNAL" },
	{ label: "秘密", value: "SECRET" },
	{ label: "机密", value: "CONFIDENTIAL" },
];
const LEVEL_RANK: Record<string, number> = {
	PUBLIC: 1,
	INTERNAL: 2,
	SECRET: 3,
	CONFIDENTIAL: 4,
};

type Props = {
	assetType: string;
	assetKey: string;
	canTag: boolean;
	dataset: Record<string, any>;
	domainOptions: CatalogDomainOption[];
	domainLoading: boolean;
	domainError: unknown;
	onChanged: (next: Record<string, any>) => void;
	onOpenLifecycle: () => void;
};

export function OpenMetadataGovernanceTab({
	assetType,
	assetKey,
	canTag,
	dataset,
	domainOptions,
	domainLoading,
	domainError,
	onChanged,
	onOpenLifecycle,
}: Props) {
	const [form] = Form.useForm();
	const [saving, setSaving] = useState(false);
	const [raisingFloor, setRaisingFloor] = useState(false);
	const [fact, setFact] = useState<ClassificationFactView | null>(null);
	const [floorDraft, setFloorDraft] = useState("");
	const [floorReason, setFloorReason] = useState("");

	useEffect(() => {
		form.setFieldsValue({
			domainId: dataset.domainId,
			warehouseLayer: dataset.warehouseLayer,
			ownerDept: dataset.ownerDept,
			businessOwner: dataset.owner,
			securityPolicyRefs: dataset.securityPolicyRefs,
		});
	}, [dataset, form]);

	useEffect(() => {
		if (!assetKey) {
			setFact(null);
			setFloorDraft("");
			return;
		}
		let cancelled = false;
		void getCatalogClassificationFacts([{ subjectType: "ASSET", subjectKey: assetKey }])
			.then((facts) => {
				if (cancelled) return;
				const next = Array.isArray(facts) ? facts[0] || null : null;
				setFact(next);
				setFloorDraft(String(next?.manualFloor || ""));
			})
			.catch(() => {
				if (!cancelled) {
					setFact(null);
					setFloorDraft("");
				}
			});
		return () => {
			cancelled = true;
		};
	}, [assetKey]);

	const saveGovernance = async () => {
		const values = await form.validateFields();
		setSaving(true);
		try {
			const detail: any = await updateCatalogAssetV2Governance(String(dataset.id), values);
			const asset = detail?.asset || {};
			const nextDomainId = asset.domainId ?? values.domainId ?? dataset.domainId;
			const nextDomainName = domainOptions.find((option) => option.key === nextDomainId)?.name;
			onChanged({
				...dataset,
				domainId: nextDomainId,
				domainName: nextDomainName ?? dataset.domainName,
				warehouseLayer: asset.warehouseLayer ?? values.warehouseLayer ?? dataset.warehouseLayer,
				ownerDept: asset.ownerDept ?? values.ownerDept ?? dataset.ownerDept,
				owner: asset.owner ?? values.businessOwner ?? dataset.owner,
				governanceStatus: asset.governanceStatus ?? dataset.governanceStatus,
				securityPolicyRefs: asset.securityPolicyRefs ?? values.securityPolicyRefs ?? dataset.securityPolicyRefs,
			});
			message.success("治理信息已保存");
		} finally {
			setSaving(false);
		}
	};

	const effectiveLevel = String(fact?.effectiveLevel || dataset.classification || "").toUpperCase();
	const raiseManualFloor = async () => {
		const candidate = floorDraft.trim().toUpperCase();
		if (!assetKey || !fact?.sealed) {
			message.error("当前资产缺少已封存的密级事实，不能提升人工下限");
			return;
		}
		if (!candidate) {
			message.warning("请选择新的人工密级下限");
			return;
		}
		if ((LEVEL_RANK[candidate] || 0) < (LEVEL_RANK[effectiveLevel] || 0)) {
			message.error("人工密级下限不能低于当前有效密级");
			return;
		}
		if (floorReason.trim().length < 5) {
			message.warning("升密原因至少填写 5 个字符");
			return;
		}
		setRaisingFloor(true);
		try {
			const result = await raiseCatalogClassificationManualFloor({
				subjectType: "ASSET",
				subjectKey: assetKey,
				candidateLevel: candidate,
				triggerRef: "asset-detail-governance",
				evidenceJson: JSON.stringify({ reason: floorReason.trim(), source: "DatasetDetailPage" }),
			});
			setFact((current) => ({
				subjectType: "ASSET",
				subjectKey: assetKey,
				sealed: true,
				...current,
				manualFloor: candidate,
				effectiveLevel: result.effectiveLevel,
				snapshotId: result.sealId,
				snapshotVersion: result.snapshotVersion,
				sealedAt: result.sealedAt,
				propagationStatus: result.propagationStatus,
			}));
			onChanged({ ...dataset, classification: result.effectiveLevel });
			setFloorReason("");
			message.success(`人工密级下限已提升，有效密级为 ${result.effectiveLevel}`);
		} finally {
			setRaisingFloor(false);
		}
	};

	return (
		<div className="space-y-4 py-2">
			<section className="rounded-xl border border-slate-200 bg-white p-4 sm:p-5">
				<div className="mb-4">
					<h3 className="text-base font-semibold text-slate-900">治理归属</h3>
					<p className="mt-1 text-sm text-slate-500">维护资产在目录中的业务归属、负责人和数仓分层。</p>
				</div>
				{domainError ? (
					<Alert
						type="warning"
						showIcon
						className="mb-4"
						message="业务归属数据域暂时无法加载"
						description="请稍后刷新页面重试；其他治理信息仍可继续维护。"
					/>
				) : null}
				<Form form={form} layout="vertical">
					<div className="grid gap-x-4 md:grid-cols-2">
						<Form.Item
							label="业务归属数据域"
							name="domainId"
							extra="用于资产目录归属、检索筛选和治理统计；不等同于数据建模中的主题域。"
						>
							<Select
								showSearch
								optionFilterProp="label"
								loading={domainLoading}
								disabled={Boolean(domainError)}
								placeholder="请选择资产所属数据域"
								options={domainOptions.map((option) => ({ label: option.label, value: option.key }))}
								notFoundContent={domainLoading ? "正在加载数据域..." : "暂无可用数据域"}
							/>
						</Form.Item>
						<Form.Item label="业务负责人" name="businessOwner">
							<Input allowClear placeholder="请输入业务负责人" />
						</Form.Item>
						<Form.Item label="归属部门" name="ownerDept">
							<Input allowClear placeholder="请输入归属部门" />
						</Form.Item>
						<Form.Item label="仓库分层" name="warehouseLayer">
							<Select
								allowClear
								placeholder="请选择数仓分层"
								options={["SOURCE", "ODS", "STG", "DWD", "DIM", "DWS", "ADS"].map((value) => ({
									label: value,
									value,
								}))}
							/>
						</Form.Item>
					</div>
					<Form.Item label="权限、脱敏与行过滤引用" name="securityPolicyRefs">
						<Input.TextArea rows={3} placeholder="例如 grant:<id>, masking:<id>, row-filter:<id>，或 JSON 引用清单" />
					</Form.Item>
					<Button type="primary" onClick={() => void saveGovernance()} loading={saving}>
						保存治理信息
					</Button>
				</Form>
			</section>
			{assetType && assetKey ? (
				<AssetTagPanel assetType={assetType} assetKey={assetKey} canEdit={canTag} />
			) : (
				<Alert
					type="info"
					showIcon
					message="业务数据标签暂不可维护"
					description="资产身份合同尚未就绪，请先完成资产同步或映射。"
				/>
			)}
			<Alert
				type="info"
				showIcon
				message="生命周期变更需要审批"
				description={`当前状态：${dataset.lifecycleStatus || "未设置"}。归档、临时销毁、恢复和永久销毁需进入统一工作台。`}
				action={<Button onClick={onOpenLifecycle}>打开密级与生命周期</Button>}
			/>
			<Collapse
				items={[
					{
						key: "classification-floor",
						label: "高级治理：提升人工密级下限",
						children: (
							<div className="space-y-3">
								<Descriptions bordered size="small" column={2}>
									<Descriptions.Item label="来源声明">{classificationText(fact?.declaredLevel)}</Descriptions.Item>
									<Descriptions.Item label="当前有效密级">
										<Tag color={effectiveLevel ? "orange" : "red"}>{classificationText(effectiveLevel)}</Tag>
									</Descriptions.Item>
									<Descriptions.Item label="当前人工下限">
										{fact?.manualFloor ? classificationText(fact.manualFloor) : "未设置"}
									</Descriptions.Item>
									<Descriptions.Item label="快照版本">v{fact?.snapshotVersion ?? 0}</Descriptions.Item>
								</Descriptions>
								{fact ? null : (
									<Alert
										type="warning"
										showIcon
										message="密级事实暂不可用"
										description="请先完成资产映射与密级封存；在事实加载成功前不能设置人工密级下限。"
									/>
								)}
								<div className="grid gap-3 md:grid-cols-2">
									<div className="space-y-1">
										<span className="text-sm text-slate-700">人工密级下限</span>
										<Select
											className="w-full"
											value={floorDraft || undefined}
											placeholder="只能选择当前有效密级或更高级别"
											disabled={!fact?.sealed || raisingFloor}
											onChange={setFloorDraft}
											options={LEVELS.map((option) => ({
												...option,
												disabled: (LEVEL_RANK[option.value] || 0) < (LEVEL_RANK[effectiveLevel] || 0),
											}))}
										/>
									</div>
									<div className="space-y-1">
										<span className="text-sm text-slate-700">升密原因</span>
										<Input.TextArea
											rows={2}
											maxLength={200}
											showCount
											value={floorReason}
											placeholder="说明业务依据和影响范围，至少 5 个字符"
											onChange={(event) => setFloorReason(event.target.value)}
										/>
									</div>
								</div>
								<Button
									type="primary"
									loading={raisingFloor}
									disabled={!fact?.sealed || !floorDraft || floorDraft === fact?.manualFloor}
									onClick={() => void raiseManualFloor()}
								>
									确认提升人工下限
								</Button>
							</div>
						),
					},
				]}
			/>
			{dataset.__legacyDatasetId ? (
				<Alert type="info" showIcon message="质量运行、治理健康和关联指标已移到“质量与SLA”页。" />
			) : (
				<Alert type="warning" showIcon message="未映射到 legacy dataset，治理健康和质量规则暂不可用。" />
			)}
		</div>
	);
}
