import { Alert, Button, Card, DatePicker, Descriptions, Input, Popconfirm, Select, Space, Tag, Typography } from "antd";
import type { Dayjs } from "dayjs";
import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import iamPolicyService, {
	type AssetAction,
	type AssetActionEffect,
	type AssetActionMatrix,
	type AssetActionPolicyRequest,
	type AssetActionResourceType,
	type AssetActionSubjectType,
} from "@/api/services/iamPolicyService";
import { CompactTable } from "@/components/table";

const { Text } = Typography;

const ACTIONS: Array<{ action: AssetAction; label: string }> = [
	{ action: "CREATE", label: "新增" },
	{ action: "DELETE", label: "删除" },
	{ action: "UPDATE", label: "修改" },
	{ action: "COPY", label: "复制" },
	{ action: "IMPORT", label: "导入" },
	{ action: "EXPORT", label: "导出" },
	{ action: "ARCHIVE", label: "归档" },
	{ action: "DESTROY", label: "销毁" },
];

const EFFECT_OPTIONS = [
	{ label: "允许", value: "ALLOW" },
	{ label: "拒绝", value: "DENY" },
	{ label: "未配置", value: "NONE" },
];

const SUBJECT_TYPE_OPTIONS = [
	{ label: "角色", value: "ROLE" },
	{ label: "部门", value: "DEPARTMENT" },
	{ label: "用户", value: "USER" },
];

const RESOURCE_TYPE_OPTIONS = [
	{ label: "数据集", value: "DATASET" },
	{ label: "库表", value: "TABLE" },
	{ label: "数据目录", value: "CATALOG" },
];

const effectTag = (effect: AssetActionEffect) => {
	if (effect === "ALLOW") return <Tag color="success">允许</Tag>;
	if (effect === "DENY") return <Tag color="error">拒绝</Tag>;
	return <Tag>未配置</Tag>;
};

const policyStatusTag = (status?: AssetActionMatrix["actions"][number]["policyStatus"]) => {
	if (status === "ACTIVE") return <Tag color="processing">生效中</Tag>;
	if (status === "SCHEDULED") return <Tag color="warning">待生效</Tag>;
	if (status === "EXPIRED") return <Tag>已过期</Tag>;
	return <Tag>未配置</Tag>;
};

const parseChanges = (json?: string | null) => {
	try {
		const changes = JSON.parse(json || "{}") as Record<string, string>;
		return Object.entries(changes)
			.map(([action, effect]) => `${ACTIONS.find((item) => item.action === action)?.label || action}：${effect}`)
			.join("；");
	} catch {
		return "变更内容无法解析";
	}
};

type DatasetOption = { id: string; name: string };

export function AssetActionMatrixPanel({
	datasets,
	initialDatasetId,
}: {
	datasets: DatasetOption[];
	initialDatasetId?: string;
}) {
	const [subjectType, setSubjectType] = useState<AssetActionSubjectType>("ROLE");
	const [subjectId, setSubjectId] = useState<string>();
	const [subjectOptions, setSubjectOptions] = useState<Array<{ id: string; name: string }>>([]);
	const [resourceType, setResourceType] = useState<AssetActionResourceType>("DATASET");
	const [resourceId, setResourceId] = useState<string | undefined>(initialDatasetId);
	const [matrix, setMatrix] = useState<AssetActionMatrix | null>(null);
	const [desiredEffects, setDesiredEffects] = useState<Partial<Record<AssetAction, AssetActionEffect>>>({});
	const [reason, setReason] = useState("");
	const [validRange, setValidRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
	const [requests, setRequests] = useState<AssetActionPolicyRequest[]>([]);
	const [loading, setLoading] = useState(false);
	const [submitting, setSubmitting] = useState(false);

	const datasetOptions = useMemo(
		() => datasets.map((dataset) => ({ label: dataset.name, value: dataset.id })),
		[datasets],
	);
	const selectedSubject = subjectOptions.find((item) => item.id === subjectId);
	const selectedDataset = datasets.find((item) => item.id === resourceId);

	const loadSubjects = async (keyword = "") => {
		try {
			const searchType = subjectType === "DEPARTMENT" ? "org" : subjectType.toLowerCase();
			const subjects = await iamPolicyService.searchSubjects(searchType as "role" | "org" | "user", keyword);
			setSubjectOptions(subjects);
		} catch (error: any) {
			toast.error(error?.message || "授权对象加载失败");
		}
	};

	const loadRequests = async () => {
		try {
			const result = await iamPolicyService.listAssetActionPolicyRequests("PENDING");
			setRequests(Array.isArray(result) ? result : []);
		} catch (error: any) {
			toast.error(error?.message || "待审批权限变更加载失败");
		}
	};

	useEffect(() => {
		setSubjectId(undefined);
		void loadSubjects();
	}, [subjectType]);

	useEffect(() => {
		void loadRequests();
	}, []);

	useEffect(() => {
		if (initialDatasetId && resourceType === "DATASET") {
			setResourceId(initialDatasetId);
		}
	}, [initialDatasetId, resourceType]);

	const loadMatrix = async () => {
		if (!subjectId || !resourceId) {
			toast.warning("请先选择授权对象和资源");
			return;
		}
		setLoading(true);
		try {
			const result = await iamPolicyService.getAssetActionMatrix({
				subjectType,
				subjectId,
				resourceType,
				resourceId,
			});
			setMatrix(result);
			setDesiredEffects(
				Object.fromEntries(result.actions.map((item) => [item.action, item.effect])) as Record<
					AssetAction,
					AssetActionEffect
				>,
			);
		} catch (error: any) {
			toast.error(error?.message || "操作权限矩阵加载失败");
			setMatrix(null);
		} finally {
			setLoading(false);
		}
	};

	const changedEffects = useMemo(() => {
		if (!matrix) return {};
		const current = new Map(matrix.actions.map((item) => [item.action, item.effect]));
		return Object.fromEntries(
			ACTIONS.flatMap(({ action }) => {
				const desired = desiredEffects[action];
				return desired && desired !== current.get(action) ? [[action, desired]] : [];
			}),
		) as Partial<Record<AssetAction, AssetActionEffect>>;
	}, [desiredEffects, matrix]);

	const submitRequest = async () => {
		if (!matrix || !subjectId || !resourceId) {
			toast.warning("请先加载需要调整的权限矩阵");
			return;
		}
		if (matrix.pendingRequest) {
			toast.warning("该授权对象与资源已有待审批申请");
			return;
		}
		if (Object.keys(changedEffects).length === 0) {
			toast.warning("没有需要提交的权限变更");
			return;
		}
		if (!reason.trim()) {
			toast.warning("请填写变更原因");
			return;
		}
		setSubmitting(true);
		try {
			await iamPolicyService.requestAssetActionPolicyChange({
				subjectType,
				subjectId,
				subjectName: selectedSubject?.name,
				resourceType,
				resourceId,
				resourceName: resourceType === "DATASET" ? selectedDataset?.name : resourceId,
				desiredEffects: changedEffects,
				validFrom: validRange?.[0]?.startOf("day").toISOString(),
				validTo: validRange?.[1]?.endOf("day").toISOString(),
				reason: reason.trim(),
			});
			toast.success("权限变更已提交审批，批准前不会生效");
			setReason("");
			setValidRange(null);
			await Promise.all([loadMatrix(), loadRequests()]);
		} catch (error: any) {
			toast.error(error?.message || "权限变更提交失败");
		} finally {
			setSubmitting(false);
		}
	};

	const decideRequest = async (requestId: string, decision: "APPROVE" | "REJECT") => {
		try {
			await iamPolicyService.decideAssetActionPolicyRequest(requestId, { decision });
			toast.success(decision === "APPROVE" ? "权限变更已批准并生效" : "权限变更已驳回");
			await loadRequests();
			if (matrix?.pendingRequest?.id === requestId) {
				await loadMatrix();
			}
		} catch (error: any) {
			toast.error(error?.message || "审批操作失败");
		}
	};

	return (
		<Space direction="vertical" size="middle" className="w-full">
			<Card size="small" title="授权范围">
				<Space wrap align="end">
					<div>
						<Text type="secondary">授权对象类型</Text>
						<Select
							className="mt-1 block"
							style={{ width: 140 }}
							options={SUBJECT_TYPE_OPTIONS}
							value={subjectType}
							onChange={setSubjectType}
						/>
					</div>
					<div>
						<Text type="secondary">授权对象</Text>
						<Select
							className="mt-1 block"
							style={{ width: 260 }}
							showSearch
							filterOption={false}
							onSearch={(keyword) => void loadSubjects(keyword)}
							options={subjectOptions.map((item) => ({ label: item.name, value: item.id }))}
							value={subjectId}
							onChange={setSubjectId}
							placeholder="选择角色、部门或用户"
						/>
					</div>
					<div>
						<Text type="secondary">资源类型</Text>
						<Select
							className="mt-1 block"
							style={{ width: 140 }}
							options={RESOURCE_TYPE_OPTIONS}
							value={resourceType}
							onChange={(value) => {
								setResourceType(value);
								setResourceId(value === "DATASET" ? initialDatasetId : undefined);
								setMatrix(null);
							}}
						/>
					</div>
					<div>
						<Text type="secondary">受控资源</Text>
						{resourceType === "DATASET" ? (
							<Select
								className="mt-1 block"
								style={{ width: 260 }}
								showSearch
								optionFilterProp="label"
								options={datasetOptions}
								value={resourceId}
								onChange={setResourceId}
								placeholder="选择数据集"
							/>
						) : (
							<Input
								className="mt-1 block"
								style={{ width: 260 }}
								value={resourceId}
								onChange={(event) => setResourceId(event.target.value)}
								placeholder={resourceType === "TABLE" ? "库名.表名" : "数据目录标识"}
							/>
						)}
					</div>
					<Button type="primary" loading={loading} onClick={() => void loadMatrix()}>
						加载权限
					</Button>
				</Space>
			</Card>

			<Card size="small" title="动作权限矩阵">
				<CompactTable
					pagination={false}
					rowKey="action"
					loading={loading}
					dataSource={ACTIONS}
					columns={[
						{ title: "动作", dataIndex: "label", width: 120 },
						{
							title: "配置规则",
							width: 140,
							render: (_, row) =>
								effectTag(matrix?.actions.find((item) => item.action === row.action)?.effect || "NONE"),
						},
						{
							title: "运行时状态",
							width: 140,
							render: (_, row) =>
								policyStatusTag(matrix?.actions.find((item) => item.action === row.action)?.policyStatus),
						},
						{
							title: "申请调整为",
							render: (_, row) => (
								<Select
									style={{ width: 140 }}
									disabled={!matrix || Boolean(matrix.pendingRequest)}
									options={EFFECT_OPTIONS}
									value={desiredEffects[row.action] || "NONE"}
									onChange={(value) => setDesiredEffects((previous) => ({ ...previous, [row.action]: value }))}
								/>
							),
						},
					]}
				/>
				{matrix?.pendingRequest ? (
					<Alert
						className="mt-3"
						type="warning"
						showIcon
						message="当前范围已有待审批变更"
						description={`申请人：${matrix.pendingRequest.requestedBy}；原因：${matrix.pendingRequest.reason}`}
					/>
				) : null}
				<Space wrap className="mt-3" align="end">
					<div>
						<Text type="secondary">生效期限（可选）</Text>
						<DatePicker.RangePicker
							className="mt-1 block"
							value={validRange}
							onChange={(value) => setValidRange(value)}
						/>
					</div>
					<div>
						<Text type="secondary">变更原因</Text>
						<Input
							className="mt-1 block"
							style={{ width: 360 }}
							value={reason}
							onChange={(event) => setReason(event.target.value)}
							placeholder="说明业务职责或授权依据"
						/>
					</div>
					<Button
						type="primary"
						loading={submitting}
						disabled={!matrix || Boolean(matrix.pendingRequest)}
						onClick={() => void submitRequest()}
					>
						提交审批
					</Button>
				</Space>
				<Text type="secondary" className="mt-2 block">
					只提交发生变化的动作；“未配置”表示删除该动作的显式规则。
				</Text>
			</Card>

			<Card size="small" title="待审批变更">
				<Alert
					className="mb-3"
					type="warning"
					showIcon
					message="仅机构级管理员可以审批，且申请人不能审批自己的申请。"
				/>
				<CompactTable
					rowKey="id"
					pagination={{ pageSize: 10 }}
					dataSource={requests}
					columns={[
						{
							title: "授权对象",
							render: (_, row) => row.subjectName || row.subjectId,
						},
						{
							title: "受控资源",
							render: (_, row) => row.resourceName || row.resourceId,
						},
						{ title: "申请变更", render: (_, row) => parseChanges(row.changesJson) },
						{ title: "申请人", dataIndex: "requestedBy", width: 130 },
						{ title: "原因", dataIndex: "reason" },
						{
							title: "状态",
							width: 100,
							render: () => <Tag color="processing">PENDING</Tag>,
						},
						{
							title: "审批",
							width: 150,
							render: (_, row) => (
								<Space>
									<Popconfirm title="确认批准这项权限变更？" onConfirm={() => void decideRequest(row.id, "APPROVE")}>
										<Button size="small" type="primary">
											批准
										</Button>
									</Popconfirm>
									<Popconfirm title="确认驳回这项权限变更？" onConfirm={() => void decideRequest(row.id, "REJECT")}>
										<Button size="small" danger>
											驳回
										</Button>
									</Popconfirm>
								</Space>
							),
						},
					]}
					locale={{ emptyText: "暂无待审批的操作权限变更" }}
				/>
			</Card>

			{matrix ? (
				<Descriptions size="small" column={2} bordered>
					<Descriptions.Item label="授权主体">
						{matrix.subjectType} / {selectedSubject?.name || matrix.subjectId}
					</Descriptions.Item>
					<Descriptions.Item label="资源">
						{matrix.resourceType} / {selectedDataset?.name || matrix.resourceId}
					</Descriptions.Item>
				</Descriptions>
			) : null}
		</Space>
	);
}
