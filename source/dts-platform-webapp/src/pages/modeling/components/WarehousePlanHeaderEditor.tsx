import { Alert, Button, Card, Descriptions, Form, Input, Select, Space, Typography } from "antd";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { searchUsers, type UserDirectoryEntry } from "@/api/services/userDirectoryService";
import {
	getWarehousePlan,
	type UpdateWarehousePlanInput,
	updateWarehousePlan,
	type WarehousePlanHeader,
} from "@/api/warehousePlanApi";
import { createLatestRequestGuard } from "../warehousePlanCreateFlow";
import {
	canEditWarehousePlanHeader,
	resolveWarehousePlanConflictVersion,
	warehousePlanLifecycleLabel,
	warehousePlanMutationErrorMessage,
} from "../warehousePlanViewModel";

const { Text } = Typography;

type WarehousePlanHeaderForm = {
	name: string;
	objective?: string;
	scope?: string;
	ownerId: string;
	ownerDepartmentId?: string;
};

type Props = {
	open: boolean;
	plan: WarehousePlanHeader | null;
	canMaintainPlan: boolean;
	onClose: () => void;
	onPlanChange: (plan: WarehousePlanHeader) => void;
	onUnavailable: () => void;
};

type PendingVerification = {
	input: UpdateWarehousePlanInput;
	version: number;
};

const onboardingLabel = (plan: WarehousePlanHeader): string =>
	plan.onboardingMode === "ASSET_FIRST" ? "从现有数据开始" : "从业务目标开始";

const planFormValues = (plan: WarehousePlanHeader): WarehousePlanHeaderForm => ({
	name: plan.name,
	objective: plan.objective || "",
	scope: plan.scope || "",
	ownerId: plan.ownerId,
	ownerDepartmentId: plan.ownerDepartmentId || "",
});

const normalizedOptional = (value: string | null | undefined): string | null => value?.trim() || null;

const headerMatchesInput = (plan: WarehousePlanHeader, input: UpdateWarehousePlanInput): boolean =>
	plan.name.trim() === input.name.trim() &&
	normalizedOptional(plan.objective) === normalizedOptional(input.objective) &&
	normalizedOptional(plan.scope) === normalizedOptional(input.scope) &&
	plan.ownerId === input.ownerId &&
	normalizedOptional(plan.ownerDepartmentId) === normalizedOptional(input.ownerDepartmentId);

const responseCode = (error: unknown): string => {
	if (!error || typeof error !== "object") return "";
	const data = (error as { response?: { data?: unknown } }).response?.data;
	if (!data || typeof data !== "object") return "";
	const code = (data as { code?: unknown }).code;
	return typeof code === "string" ? code : "";
};

const userLabel = (user: UserDirectoryEntry): string => {
	const name = user.fullName?.trim() || user.displayName?.trim() || user.username;
	const department = user.deptName?.trim() || user.deptCode?.trim();
	return department ? `${name}（${user.username} · ${department}）` : `${name}（${user.username}）`;
};

export function WarehousePlanHeaderEditor({
	open,
	plan,
	canMaintainPlan,
	onClose,
	onPlanChange,
	onUnavailable,
}: Props) {
	const [form] = Form.useForm<WarehousePlanHeaderForm>();
	const [saving, setSaving] = useState(false);
	const [conflictVersion, setConflictVersion] = useState<number | null>(null);
	const [submitError, setSubmitError] = useState("");
	const [writeDenied, setWriteDenied] = useState(false);
	const [unavailable, setUnavailable] = useState(false);
	const [lockedPlan, setLockedPlan] = useState<WarehousePlanHeader | null>(null);
	const [pendingVerification, setPendingVerification] = useState<PendingVerification | null>(null);
	const [directoryUsers, setDirectoryUsers] = useState<UserDirectoryEntry[]>([]);
	const [directoryLoading, setDirectoryLoading] = useState(false);
	const [directoryHint, setDirectoryHint] = useState("");
	const [directoryQuery, setDirectoryQuery] = useState("");
	const mutationGuard = useMemo(() => createLatestRequestGuard(), []);
	const directoryRequestRef = useRef(0);

	const editable = Boolean(
		plan &&
			canEditWarehousePlanHeader(canMaintainPlan, plan.lifecycleStatus) &&
			!writeDenied &&
			!unavailable &&
			!lockedPlan,
	);

	const loadDirectoryUsers = useCallback(async (keyword: string) => {
		const requestId = ++directoryRequestRef.current;
		setDirectoryLoading(true);
		const result = await searchUsers(keyword);
		if (requestId !== directoryRequestRef.current) return;
		setDirectoryUsers(result);
		setDirectoryHint(result.length === 0 ? "人员目录暂未返回可选账号；可以保留当前负责人，转派前请稍后重新搜索。" : "");
		setDirectoryLoading(false);
	}, []);

	useEffect(() => {
		if (!open || !plan) return;
		mutationGuard.invalidate();
		directoryRequestRef.current += 1;
		form.resetFields();
		form.setFieldsValue(planFormValues(plan));
		setSaving(false);
		setConflictVersion(null);
		setSubmitError("");
		setWriteDenied(false);
		setUnavailable(false);
		setLockedPlan(null);
		setPendingVerification(null);
		setDirectoryUsers([]);
		setDirectoryHint("");
		setDirectoryQuery("");
		void loadDirectoryUsers(plan.ownerId);
		return () => {
			mutationGuard.invalidate();
			directoryRequestRef.current += 1;
		};
	}, [form, loadDirectoryUsers, mutationGuard, open, plan]);

	useEffect(() => {
		if (!open || !directoryQuery.trim()) return;
		const handle = window.setTimeout(() => void loadDirectoryUsers(directoryQuery.trim()), 300);
		return () => window.clearTimeout(handle);
	}, [directoryQuery, loadDirectoryUsers, open]);

	const ownerOptions = useMemo(() => {
		const options = new Map<string, { value: string; label: string }>();
		if (plan) {
			options.set(plan.ownerId, {
				value: plan.ownerId,
				label: plan.ownerDepartmentId ? `${plan.ownerId}（${plan.ownerDepartmentId}）` : plan.ownerId,
			});
		}
		for (const user of directoryUsers) options.set(user.id, { value: user.id, label: userLabel(user) });
		return [...options.values()];
	}, [directoryUsers, plan]);

	const changeOwner = (ownerId: string) => {
		const selected = directoryUsers.find((user) => user.id === ownerId);
		if (selected) {
			form.setFieldValue("ownerDepartmentId", selected.deptCode || "");
			return;
		}
		if (plan?.ownerId === ownerId) form.setFieldValue("ownerDepartmentId", plan.ownerDepartmentId || "");
	};

	const applyServerFieldErrors = (error: unknown): boolean => {
		const response = (error as { response?: { status?: number; data?: unknown } })?.response;
		if (response?.status !== 400) return false;
		const responseData = response.data && typeof response.data === "object" ? response.data : {};
		const rawFieldErrors = (responseData as { fieldErrors?: unknown }).fieldErrors;
		const supportedFields = new Set<keyof WarehousePlanHeaderForm>([
			"name",
			"objective",
			"scope",
			"ownerId",
			"ownerDepartmentId",
		]);
		const fieldErrors = Array.isArray(rawFieldErrors)
			? rawFieldErrors.flatMap((item) => {
					if (!item || typeof item !== "object") return [];
					const field = String((item as { field?: unknown }).field || "") as keyof WarehousePlanHeaderForm;
					if (!supportedFields.has(field)) return [];
					return [{ name: field, errors: ["该字段不符合服务端规则，请修正后重试"] }];
				})
			: [];
		if (fieldErrors.length > 0) {
			form.setFields(fieldErrors);
			setSubmitError("请修正标红字段后再保存，表单已保留。");
			return true;
		}
		if (responseCode(error) === "WAREHOUSE_PLAN_HEADER_INVALID") {
			form.setFields([
				{ name: "name", errors: ["请检查规划名称"] },
				{ name: "ownerId", errors: ["请重新选择有效负责人"] },
			]);
			setSubmitError("请检查规划名称和负责人，表单已保留。");
			return true;
		}
		return false;
	};

	const reconcileLifecycleConflict = async (isCurrent: () => boolean) => {
		if (!plan) return;
		try {
			const latest = await getWarehousePlan(plan.id);
			if (!isCurrent()) return;
			if (latest.lifecycleStatus === "PUBLISHED" || latest.lifecycleStatus === "ARCHIVED") {
				setLockedPlan(latest);
				setSubmitError(
					`规划已变为“${warehousePlanLifecycleLabel(latest.lifecycleStatus)}”，当前输入已保留但不能再提交。`,
				);
				return;
			}
			setSubmitError("规划状态已变化，请加载最新版后再确认是否继续。");
		} catch (error) {
			if (!isCurrent()) return;
			if ((error as { response?: { status?: number } })?.response?.status === 404) setUnavailable(true);
			setSubmitError("规划状态核对失败，表单已保留，请返回建设规划台账重新选择。");
		}
	};

	const submit = async (retryVersion?: number, verifiedPending?: PendingVerification) => {
		if (!plan || !editable || (pendingVerification && verifiedPending !== pendingVerification)) return;
		const isCurrent = mutationGuard.begin();
		const attemptedVersion = retryVersion ?? plan.version;
		let submittedInput: UpdateWarehousePlanInput | null = null;
		setSaving(true);
		setSubmitError("");
		try {
			const values = await form.validateFields();
			if (!isCurrent()) return;
			const input: UpdateWarehousePlanInput = {
				name: values.name.trim(),
				objective: values.objective?.trim() || null,
				scope: values.scope?.trim() || null,
				ownerId: values.ownerId,
				ownerDepartmentId: values.ownerDepartmentId?.trim() || null,
			};
			submittedInput = input;
			const updated = await updateWarehousePlan(plan.id, attemptedVersion, input);
			if (!isCurrent()) return;
			setConflictVersion(null);
			setPendingVerification(null);
			onPlanChange(updated);
			toast.success("规划信息已保存");
			onClose();
		} catch (error) {
			if (!isCurrent()) return;
			if (error && typeof error === "object" && "errorFields" in error) return;
			const currentVersion = resolveWarehousePlanConflictVersion(error);
			if (currentVersion != null) {
				setConflictVersion(currentVersion);
				setSubmitError("");
				return;
			}
			const response = (error as { response?: { status?: number } })?.response;
			if (responseCode(error) === "WAREHOUSE_PLAN_LIFECYCLE_CONFLICT") {
				await reconcileLifecycleConflict(isCurrent);
				return;
			}
			if (applyServerFieldErrors(error)) return;
			if (response?.status === 403) {
				setWriteDenied(true);
				setSubmitError("当前账号没有规划维护权限，表单已保留。");
			} else if (response?.status === 404) {
				setUnavailable(true);
				setSubmitError("规划已不可访问，表单已保留，请返回建设规划台账重新选择。");
			} else if (!response && submittedInput) {
				setConflictVersion(null);
				setPendingVerification({ input: submittedInput, version: attemptedVersion });
				setSubmitError("");
			} else {
				setSubmitError(`${warehousePlanMutationErrorMessage(error, "规划保存失败")}，表单已保留。`);
			}
		} finally {
			if (isCurrent()) setSaving(false);
		}
	};

	const verifyPendingUpdate = async () => {
		if (!plan || !pendingVerification) return;
		const pending = pendingVerification;
		const isCurrent = mutationGuard.begin();
		setSaving(true);
		setSubmitError("");
		try {
			const latest = await getWarehousePlan(plan.id);
			if (!isCurrent()) return;
			if (latest.lifecycleStatus === "PUBLISHED" || latest.lifecycleStatus === "ARCHIVED") {
				setLockedPlan(latest);
				setPendingVerification(null);
				setSubmitError(
					`服务端规划已变为“${warehousePlanLifecycleLabel(latest.lifecycleStatus)}”，当前输入已保留但不能重试。`,
				);
				return;
			}
			if (latest.version === pending.version) {
				setPendingVerification(null);
				setSaving(false);
				void submit(latest.version, pending);
				return;
			}
			if (latest.version > pending.version && headerMatchesInput(latest, pending.input)) {
				setPendingVerification(null);
				onPlanChange(latest);
				toast.success("规划信息已保存（已核对服务端状态）");
				onClose();
				return;
			}
			if (latest.version > pending.version) {
				setPendingVerification(null);
				setConflictVersion(latest.version);
				return;
			}
			setSubmitError("服务端版本异常，当前输入已保留，请稍后重新核对。");
		} catch (error) {
			if (!isCurrent()) return;
			if ((error as { response?: { status?: number } })?.response?.status === 404) {
				setUnavailable(true);
				setSubmitError("规划已不可访问，表单已保留，请返回建设规划台账重新选择。");
			} else {
				setSubmitError("服务端状态核对失败，当前输入和原版本已保留，请稍后重试核对。");
			}
		} finally {
			if (isCurrent()) setSaving(false);
		}
	};

	const loadLatest = async () => {
		if (!plan) return;
		const isCurrent = mutationGuard.begin();
		setSaving(true);
		setSubmitError("");
		try {
			const latest = await getWarehousePlan(plan.id);
			if (!isCurrent()) return;
			form.setFieldsValue(planFormValues(latest));
			setConflictVersion(null);
			setPendingVerification(null);
			onPlanChange(latest);
			toast.success("已加载规划最新版");
		} catch (error) {
			if (!isCurrent()) return;
			const response = (error as { response?: { status?: number } })?.response;
			if (response?.status === 404) setUnavailable(true);
			setSubmitError(
				response?.status === 404
					? "规划已不可访问，表单已保留，请返回建设规划台账重新选择。"
					: "最新版加载失败，表单已保留，请稍后重试。",
			);
		} finally {
			if (isCurrent()) setSaving(false);
		}
	};

	const close = () => {
		if (saving) return;
		mutationGuard.invalidate();
		directoryRequestRef.current += 1;
		if (lockedPlan) onPlanChange(lockedPlan);
		onClose();
	};

	if (!open || !plan) return null;

	return (
		<Card title="规划概览" data-testid="warehouse-plan-header-editor">
			<div className="space-y-4">
				{!canMaintainPlan ? <Alert type="info" showIcon message="当前账号没有规划维护权限" /> : null}
				{canMaintainPlan && !canEditWarehousePlanHeader(canMaintainPlan, plan.lifecycleStatus) ? (
					<Alert type="info" showIcon message="当前生命周期只允许查看规划信息" />
				) : null}
				{submitError ? (
					<Alert
						type="error"
						showIcon
						message={submitError}
						action={
							unavailable ? (
								<Button type="link" onClick={onUnavailable}>
									返回建设规划台账
								</Button>
							) : undefined
						}
					/>
				) : null}
				{pendingVerification ? (
					<Alert
						type="warning"
						showIcon
						message="保存结果未知"
						description="当前输入和原版本已保留。再次提交前必须先 GET 核对服务端状态，避免重复写入。"
						action={
							<Button type="link" loading={saving} onClick={() => void verifyPendingUpdate()}>
								核对服务端状态后重试
							</Button>
						}
					/>
				) : null}
				{conflictVersion != null && pendingVerification == null ? (
					<Alert
						type="warning"
						showIcon
						message="规划已被其他用户更新"
						description="当前表单已保留。请选择基于服务端最新版重试，或放弃当前输入并加载最新版。"
						action={
							<Space direction="vertical" size={4}>
								<Button type="link" loading={saving} onClick={() => void submit(conflictVersion)}>
									保留当前输入并基于版本 {conflictVersion} 重试
								</Button>
								<Button type="link" disabled={saving} onClick={() => void loadLatest()}>
									放弃并加载最新版
								</Button>
							</Space>
						}
					/>
				) : null}

				<Descriptions size="small" column={1} bordered>
					<Descriptions.Item label="计划编码">{plan.code}</Descriptions.Item>
					<Descriptions.Item label="开始方式">{onboardingLabel(plan)}</Descriptions.Item>
					<Descriptions.Item label="生命周期">
						{warehousePlanLifecycleLabel(lockedPlan?.lifecycleStatus ?? plan.lifecycleStatus)}
					</Descriptions.Item>
				</Descriptions>

				<Form<WarehousePlanHeaderForm>
					form={form}
					layout="vertical"
					disabled={!editable || saving || pendingVerification != null}
				>
					<Form.Item
						name="name"
						label="规划名称"
						rules={[{ required: true, whitespace: true, max: 128, message: "请输入 1-128 个字符的规划名称" }]}
					>
						<Input maxLength={128} placeholder="请输入规划名称" />
					</Form.Item>
					<Form.Item name="objective" label="建设目标">
						<Input.TextArea rows={3} placeholder="这次建设要解决什么业务问题" />
					</Form.Item>
					<Form.Item name="scope" label="建设范围">
						<Input.TextArea rows={2} placeholder="涉及的业务、组织或数据边界" />
					</Form.Item>
					<Form.Item
						name="ownerId"
						label="负责人"
						rules={[{ required: true, message: "请选择负责人" }]}
						extra={directoryHint || "负责人必须来自人员目录，不能手工填写账号。"}
					>
						<Select
							showSearch
							filterOption={false}
							loading={directoryLoading}
							options={ownerOptions}
							onSearch={setDirectoryQuery}
							onChange={changeOwner}
							placeholder="输入姓名或账号搜索人员目录"
							notFoundContent={directoryLoading ? "正在查询人员目录" : "未找到可选负责人"}
						/>
					</Form.Item>
					<Form.Item name="ownerDepartmentId" label="负责部门" extra="由所选负责人的目录信息带入">
						<Input disabled placeholder="人员目录未提供部门" />
					</Form.Item>
				</Form>
				<Text type="secondary">保存不会改变当前计划、页面位置或阶段证据。</Text>
				<div className="flex justify-end gap-2 border-t border-slate-200 pt-4">
					<Button disabled={saving} onClick={close}>
						取消
					</Button>
					{editable ? (
						<Button
							type="primary"
							loading={saving}
							disabled={conflictVersion != null || pendingVerification != null}
							onClick={() => void submit()}
						>
							保存规划
						</Button>
					) : null}
				</div>
			</div>
		</Card>
	);
}
