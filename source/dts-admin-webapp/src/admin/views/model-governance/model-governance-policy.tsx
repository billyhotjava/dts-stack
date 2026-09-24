import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Input, Modal, Radio, Space, Tag } from "antd";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { adminApi } from "@/admin/api/adminApi";
import type { ModelGovernanceQualityGate } from "@/types/infra";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Text } from "@/ui/typography";
import {
	describeBlockingImpact,
	QUALITY_GATE_DESCRIPTIONS,
	QUALITY_GATE_LABELS,
	REASON_MAX_LENGTH,
	requiresConfirmation,
	saveBlocker,
} from "./policyForm";

const POLICY_QUERY_KEY = ["admin", "model-governance-policy"];

export default function ModelGovernancePolicyView() {
	const queryClient = useQueryClient();
	const policyQuery = useQuery({ queryKey: POLICY_QUERY_KEY, queryFn: adminApi.getModelGovernancePolicy });
	const policy = policyQuery.data;
	const [selected, setSelected] = useState<ModelGovernanceQualityGate>("ADVISORY");
	const [reason, setReason] = useState("");
	const [saving, setSaving] = useState(false);

	useEffect(() => {
		if (policy) setSelected(policy.qualityGate);
	}, [policy]);

	const blocker = saveBlocker(policy?.qualityGate, selected, reason);

	const submit = async () => {
		if (!policy || blocker) return;
		setSaving(true);
		try {
			const updated = await adminApi.updateModelGovernancePolicy({
				qualityGate: selected,
				expectedRevision: policy.revision,
				reason: reason.trim(),
			});
			queryClient.setQueryData(POLICY_QUERY_KEY, updated);
			setReason("");
			toast.success(`模型发布治理策略已切换为"${QUALITY_GATE_LABELS[updated.qualityGate]}"`);
		} catch (error: unknown) {
			toast.error(error instanceof Error && error.message ? error.message : "策略未修改");
			await queryClient.invalidateQueries({ queryKey: POLICY_QUERY_KEY });
		} finally {
			setSaving(false);
		}
	};

	const handleSave = async () => {
		if (!policy || blocker || saving) return;
		if (!requiresConfirmation(selected)) {
			await submit();
			return;
		}
		setSaving(true);
		let impactText: string;
		try {
			impactText = describeBlockingImpact(await adminApi.getModelGovernancePolicyImpact());
		} catch (error: unknown) {
			toast.error(error instanceof Error && error.message ? error.message : "无法读取切换影响，策略未修改");
			setSaving(false);
			return;
		}
		setSaving(false);
		Modal.confirm({
			title: '确认切换为"阻断"？',
			content: impactText,
			okText: "确认切换",
			cancelText: "取消",
			okButtonProps: { danger: true },
			onOk: submit,
		});
	};

	return (
		<div className="flex flex-col gap-4">
			<Card>
				<CardHeader>
					<CardTitle>模型发布治理</CardTitle>
					<Text variant="body2" className="text-muted-foreground">
						决定模型发布前的业务质量检查是否阻止发布。默认为"提示"；只有系统管理员在此手动切换，系统不会自动切换为"阻断"。
					</Text>
				</CardHeader>
				<CardContent className="flex flex-col gap-4">
					{policyQuery.isError ? (
						<Alert
							type="error"
							showIcon
							message="模型发布治理策略读取失败"
							description={policyQuery.error instanceof Error ? policyQuery.error.message : undefined}
							action={<Button onClick={() => void policyQuery.refetch()}>重新读取</Button>}
						/>
					) : null}
					{policyQuery.isLoading ? <Text variant="body2">正在读取当前策略…</Text> : null}
					{policy ? (
						<>
							<Space wrap size="small">
								<Text variant="body2">当前策略：</Text>
								<Tag color={policy.qualityGate === "BLOCKING" ? "red" : "gold"}>
									{QUALITY_GATE_LABELS[policy.qualityGate]}
								</Tag>
								{policy.systemDefault ? <Tag>系统默认</Tag> : null}
								<Text variant="caption" className="text-muted-foreground">
									{policy.systemDefault
										? "尚未被管理员修改"
										: `最后由 ${policy.lastModifiedBy} 于 ${new Date(policy.lastModifiedDate).toLocaleString()} 修改`}
								</Text>
							</Space>
							<Radio.Group
								value={selected}
								onChange={(event) => setSelected(event.target.value as ModelGovernanceQualityGate)}
								disabled={saving}
							>
								<Space direction="vertical">
									{(["ADVISORY", "BLOCKING"] as const).map((gate) => (
										<Radio key={gate} value={gate}>
											<strong>{QUALITY_GATE_LABELS[gate]}</strong>
											<span className="ml-2 text-muted-foreground">{QUALITY_GATE_DESCRIPTIONS[gate]}</span>
										</Radio>
									))}
								</Space>
							</Radio.Group>
							<Alert
								type="info"
								showIcon
								message="已通过发布前检查的发布单按当时的策略继续；切换只影响之后发起的检查。权限、密级和资产登记检查在两种策略下都必须通过。"
							/>
							<div className="flex flex-col gap-1">
								<label htmlFor="model-governance-policy-reason">
									<Text variant="body2">
										修改原因<span className="text-error">*</span>
									</Text>
								</label>
								<Input.TextArea
									id="model-governance-policy-reason"
									value={reason}
									onChange={(event) => setReason(event.target.value)}
									maxLength={REASON_MAX_LENGTH}
									showCount
									rows={3}
									disabled={saving}
									placeholder="例如：质量规则已配置完成，上线前收紧发布要求"
								/>
							</div>
							<Space>
								<Button type="primary" loading={saving} disabled={Boolean(blocker)} onClick={() => void handleSave()}>
									保存
								</Button>
								{blocker && blocker !== "策略未变化" ? (
									<Text variant="caption" className="text-muted-foreground">
										{blocker}
									</Text>
								) : null}
							</Space>
						</>
					) : null}
				</CardContent>
			</Card>
		</div>
	);
}
