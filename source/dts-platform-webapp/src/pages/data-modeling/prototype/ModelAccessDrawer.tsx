import { Alert, Button, Drawer, List, Select, Space, Typography, message } from "antd";
import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
	getModelGrants,
	getModelAccessCandidates,
	grantModelEdit,
	revokeModelEdit,
	type ModelAccess,
	type ModelGrant,
	type ModelingCandidate,
} from "@/api/modelingAccessApi";
export function ModelAccessDrawer({
	modelId,
	access,
	open,
	onClose,
}: {
	modelId: string;
	access?: ModelAccess;
	open: boolean;
	onClose: () => void;
}) {
	const queries = useQueryClient();
	const [grants, setGrants] = useState<ModelGrant[]>([]);
	const [candidates, setCandidates] = useState<ModelingCandidate[]>([]);
	const [type, setType] = useState<"USER" | "ROLE">("USER");
	const [grantee, setGrantee] = useState<string>();
	const [busy, setBusy] = useState(false);
	const [failure, setFailure] = useState("");
	const [canManage, setCanManage] = useState(false);
	const refresh = async () => {
		const result = await getModelGrants(modelId);
		setGrants(result.items);
		setCanManage(result.canManage);
		if (result.canManage) setCandidates(await getModelAccessCandidates(modelId));
	};
	useEffect(() => {
		if (!open) return;
		let active = true;
		setFailure("");
		setGrants([]);
		setCandidates([]);
		setCanManage(false);
		setGrantee(undefined);
		setBusy(true);
		getModelGrants(modelId)
			.then(async (result) => {
				if (!active) return;
				setGrants(result.items);
				setCanManage(result.canManage);
				if (result.canManage) {
					const people = await getModelAccessCandidates(modelId);
					if (active) setCandidates(people);
				}
			})
			.catch(() => {
				if (active) setFailure("共享权限读取失败，请关闭后重试。");
			})
			.finally(() => {
				if (active) setBusy(false);
			});
		return () => {
			active = false;
		};
	}, [modelId, open]);
	const mutate = async (action: () => Promise<unknown>) => {
		setBusy(true);
		setFailure("");
		try {
			await action();
			await refresh();
			await queries.invalidateQueries({ queryKey: ["modeling-access"] });
			message.success("编辑共享已更新");
		} catch {
			setFailure("操作未完成，请刷新权限后重试。账号、部门或权限可能已变更。");
		} finally {
			setBusy(false);
		}
	};
	const options =
		type === "ROLE"
			? [
					{ value: "ROLE_DEPT_DATA_OWNER", label: "本部门数据管理员" },
					{ value: "ROLE_DEPT_LEADER", label: "本部门领导" },
				]
			: candidates.map((person) => ({ value: person.id, label: person.displayName || person.username }));
	return (
		<Drawer title="应用层模型编辑共享" width={480} open={open} onClose={onClose}>
			<Space direction="vertical" size="middle" style={{ width: "100%" }}>
				<Typography.Text>负责人：{access?.ownerName || "暂无显示名称"}</Typography.Text>
				<Alert
					type="info"
					showIcon
					message="共享仅授予本部门的编辑权"
					description="数据可见性、人员密级和发布审核职责仍分别校验。跨部门引用与共享需审批，当前暂未开放。"
				/>
				{failure && <Alert type="error" message={failure} />}
				{canManage ? (
					<>
						<Select
							aria-label="共享对象类型"
							value={type}
							options={[
								{ value: "USER", label: "按用户" },
								{ value: "ROLE", label: "按部门角色" },
							]}
							onChange={(value) => {
								setType(value);
								setGrantee(undefined);
							}}
							style={{ width: "100%" }}
						/>
						<Select
							aria-label="编辑共享对象"
							placeholder="选择本部门有效建模用户或角色"
							showSearch
							optionFilterProp="label"
							value={grantee}
							options={options}
							onChange={setGrantee}
							disabled={busy || Boolean(failure)}
							style={{ width: "100%" }}
						/>
						<Button
							type="primary"
							loading={busy}
							disabled={!grantee || Boolean(failure)}
							onClick={() => grantee && void mutate(() => grantModelEdit(modelId, type, grantee))}
						>
							授予编辑权
						</Button>
						<List
							loading={busy}
							dataSource={grants}
							locale={{ emptyText: "暂无额外编辑共享" }}
							renderItem={(grant) => (
								<List.Item
									actions={[
										<Button
											key="revoke"
											danger
											disabled={busy}
											onClick={() => void mutate(() => revokeModelEdit(modelId, grant.id))}
										>
											撤销
										</Button>,
									]}
								>
									<List.Item.Meta
										title={grant.granteeName || "已授权对象"}
										description={grant.granteeType === "ROLE" ? "本部门角色 · 编辑权" : "本部门用户 · 编辑权"}
									/>
								</List.Item>
							)}
						/>
					</>
				) : (
					!busy && <Typography.Text type="secondary">仅负责人、部门领导或院级管理人员可管理共享。</Typography.Text>
				)}
				<Button disabled title="跨部门模型引用审批尚未开放">
					申请跨部门引用
				</Button>
			</Space>
		</Drawer>
	);
}
