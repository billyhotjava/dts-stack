import { Alert, Button, Descriptions, Form, Input, message, Select, Tag } from "antd";
import { useEffect, useState } from "react";
import {
	type ClassificationFactView,
	getCatalogClassificationFacts,
	raiseCatalogClassificationManualFloor,
	updateCatalogAssetV2Governance,
} from "@/api/platformApi";
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
	assetKey: string;
	dataset: Record<string, any>;
	onChanged: (next: Record<string, any>) => void;
	onOpenLifecycle: () => void;
};

export function OpenMetadataGovernanceTab({ assetKey, dataset, onChanged, onOpenLifecycle }: Props) {
	const [form] = Form.useForm();
	const [saving, setSaving] = useState(false);
	const [raisingFloor, setRaisingFloor] = useState(false);
	const [fact, setFact] = useState<ClassificationFactView | null>(null);
	const [floorDraft, setFloorDraft] = useState("");
	const [floorReason, setFloorReason] = useState("");

	useEffect(() => {
		form.setFieldsValue({
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
			onChanged({
				...dataset,
				warehouseLayer: asset.warehouseLayer,
				ownerDept: asset.ownerDept,
				owner: asset.owner,
				governanceStatus: asset.governanceStatus,
				securityPolicyRefs: asset.securityPolicyRefs,
			});
			message.success("治理扩展已保存");
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
			<div className="space-y-3 rounded-lg border border-amber-200 bg-amber-50 p-4">
				<div>
					<div className="font-semibold text-slate-900">不可降级密级事实</div>
				</div>
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
					<label className="space-y-1">
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
					</label>
					<label className="space-y-1">
						<span className="text-sm text-slate-700">升密原因</span>
						<Input.TextArea
							rows={2}
							maxLength={200}
							showCount
							value={floorReason}
							placeholder="说明业务依据和影响范围，至少 5 个字符"
							onChange={(event) => setFloorReason(event.target.value)}
						/>
					</label>
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
			<Alert
				type="warning"
				showIcon
				message="生命周期状态只能通过审批动作改变"
				description={`当前状态：${dataset.lifecycleStatus || "未设置"}。归档、临时销毁、恢复和永久销毁必须进入统一工作台审批。`}
				action={<Button onClick={onOpenLifecycle}>打开密级与生命周期工作台</Button>}
			/>
			<Form form={form} layout="vertical">
				<div className="grid gap-3 md:grid-cols-2">
					<Form.Item label="仓库分层" name="warehouseLayer">
						<Select
							allowClear
							options={["SOURCE", "ODS", "STG", "DWD", "DIM", "DWS", "ADS"].map((value) => ({
								label: value,
								value,
							}))}
						/>
					</Form.Item>
					<Form.Item label="归属部门" name="ownerDept">
						<Input allowClear />
					</Form.Item>
					<Form.Item label="业务负责人" name="businessOwner">
						<Input allowClear />
					</Form.Item>
				</div>
				<Form.Item label="权限 / 脱敏 / 行过滤引用" name="securityPolicyRefs">
					<Input.TextArea rows={4} placeholder="例如 grant:<id>, masking:<id>, row-filter:<id>，或 JSON 引用清单" />
				</Form.Item>
				<Button type="primary" onClick={() => void saveGovernance()} loading={saving}>
					保存治理扩展
				</Button>
			</Form>
			{dataset.__legacyDatasetId ? (
				<Alert type="info" showIcon message="质量运行、治理健康和关联指标已移到“质量与SLA”页。" />
			) : (
				<Alert type="warning" showIcon message="未映射到 legacy dataset，治理健康和质量规则暂不可用。" />
			)}
		</div>
	);
}
