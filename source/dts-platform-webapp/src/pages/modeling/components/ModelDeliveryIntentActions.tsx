import { Alert, Button, Card, Space, Tag, Typography } from "antd";
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import {
	getReleaseCandidateWorkbench,
	type ModelPublicationIntentResult,
	type ReleaseCandidate,
	type ReleaseCandidateDeliveryStatus,
	startModelBuildIntent,
	startModelPublicationIntent,
} from "@/api/modelSpecApi";
import { resolveAppHref } from "@/routes/constants";
import type { CanonicalModelSpecView } from "../modelSpecV2Contract";

const { Text } = Typography;

const POLLED_STATUSES = new Set<ReleaseCandidateDeliveryStatus>(["BUILDING", "QUALITY_RUNNING", "PUBLISHING"]);
const REPLACEABLE_TERMINAL_STATUSES = new Set<ReleaseCandidateDeliveryStatus>([
	"REJECTED",
	"ROLLED_BACK",
	"CANCELLED",
	"STALE",
]);

const statusPresentation: Record<
	ReleaseCandidateDeliveryStatus,
	{ color?: string; label: string; description: string }
> = {
	DRAFT: { label: "候选已准备", description: "当前候选尚未开始构建。" },
	BUILDING: {
		color: "processing",
		label: "构建中",
		description: "构建已交给现有 Airflow/dbt 通道，正在等待运行和关系核验结果。",
	},
	BUILD_FAILED: { color: "error", label: "构建失败", description: "请在交付工作台查看失败证据并执行受控重试。" },
	BUILT: { color: "success", label: "构建完成", description: "真实目标关系已构建并通过核验，可以提交上线。" },
	QUALITY_RUNNING: {
		color: "processing",
		label: "质量检查中",
		description: "提交上线已被接受，当前停留在自动质量检查阶段。",
	},
	QUALITY_FAILED: { color: "error", label: "质量检查失败", description: "请修复质量问题，再到交付工作台显式重试。" },
	QUALITY_PASSED: { color: "success", label: "质量检查通过", description: "正在等待服务端提交人工审核。" },
	REVIEW_PENDING: { color: "warning", label: "等待审核", description: "本页不会代替审核人执行审核。" },
	REJECTED: { color: "error", label: "审核驳回", description: "当前候选不能继续，请在交付工作台创建替代候选。" },
	APPROVED: { color: "success", label: "等待发布", description: "审核已通过，等待发布角色在交付工作台执行发布。" },
	PUBLISHING: { color: "processing", label: "发布登记中", description: "正在登记资产、字段血缘和发布证据。" },
	PARTIAL: { color: "error", label: "发布未完成", description: "本地登记已回滚；请在交付工作台修复后重试。" },
	PUBLISHED: {
		color: "success",
		label: "已发布",
		description: "发布不等于上线完成；仍需等待 ACTIVE 运行绑定和关系健康结果。",
	},
	ROLLED_BACK: { label: "已回滚", description: "当前候选已回滚，历史证据仍保留。" },
	CANCELLED: { label: "已取消", description: "当前候选已取消，可重新发起当前模型构建。" },
	STALE: { color: "error", label: "候选已漂移", description: "模型版本已变化，请在交付工作台刷新或创建替代候选。" },
};

type Props = {
	model: CanonicalModelSpecView;
	implementationReady: boolean;
	readOnly: boolean;
	compact?: boolean;
	id?: string;
};

export type ModelDeliveryIntentActionsRef = {
	startBuild: () => Promise<boolean>;
	refresh: () => Promise<void>;
};

type IntentError = {
	response?: {
		data?: {
			code?: string;
			message?: string;
			data?: { workspaceHref?: string };
		};
	};
	message?: string;
};

const intentError = (error: unknown, fallback: string) => {
	const candidate = error as IntentError;
	const payload = candidate.response?.data;
	return {
		code: payload?.code || "",
		message: payload?.message || candidate.message || fallback,
		workspaceHref: payload?.data?.workspaceHref,
	};
};

const intentKey = (prefix: string, model: CanonicalModelSpecView) => {
	const nonce =
		typeof globalThis.crypto?.randomUUID === "function"
			? globalThis.crypto.randomUUID()
			: `${Date.now()}-${Math.random().toString(16).slice(2)}`;
	return `${prefix}:${model.id}:${model.revision}:${nonce}`;
};

export const releaseCandidateWorkbenchPath = (model: CanonicalModelSpecView, candidateId: string) =>
	`/modeling/plans/${encodeURIComponent(model.planId)}/implementation?candidateId=${encodeURIComponent(
		candidateId,
	)}&modelSpecId=${encodeURIComponent(model.id)}`;

export const ModelDeliveryIntentActions = forwardRef<ModelDeliveryIntentActionsRef, Props>(
	function ModelDeliveryIntentActions({ model, implementationReady, readOnly, compact = false, id }, ref) {
		const requestRef = useRef(0);
		const [loading, setLoading] = useState(true);
		const [busy, setBusy] = useState<"build" | "publish" | "refresh" | null>(null);
		const [candidate, setCandidate] = useState<ReleaseCandidate | null>(null);
		const [publication, setPublication] = useState<ModelPublicationIntentResult | null>(null);
		const [error, setError] = useState("");
		const [errorCode, setErrorCode] = useState("");
		const [recoveryHref, setRecoveryHref] = useState("");
		const modelIdentity = `${model.id}:${model.revision}:${model.checksum}`;

		const exactCandidate = useMemo(() => {
			if (
				!candidate ||
				candidate.origin !== "SINGLE_MODEL_INTENT" ||
				candidate.entries.length !== 1 ||
				candidate.entries[0].modelSpecId !== model.id ||
				candidate.entries[0].revision !== model.revision ||
				candidate.entries[0].checksum !== model.checksum
			) {
				return null;
			}
			return candidate;
		}, [candidate, model.checksum, model.id, model.revision]);
		const activeCandidate = candidate && !REPLACEABLE_TERMINAL_STATUSES.has(candidate.status) ? candidate : null;
		const exactActiveCandidate = activeCandidate === exactCandidate ? exactCandidate : null;
		const scopeConflict = Boolean(activeCandidate && !exactCandidate);

		const refresh = useCallback(
			async (manual = false) => {
				const requestId = ++requestRef.current;
				if (manual) setBusy("refresh");
				setError("");
				setErrorCode("");
				setRecoveryHref("");
				try {
					const workspace = await getReleaseCandidateWorkbench(model.planId);
					if (requestId !== requestRef.current) return;
					setCandidate(workspace.candidate);
				} catch (refreshError) {
					if (requestId !== requestRef.current) return;
					const resolved = intentError(refreshError, "候选状态暂时无法读取；已停止构建和上线操作。");
					setError(resolved.message);
					setErrorCode(resolved.code);
					setRecoveryHref(resolved.workspaceHref || "");
				} finally {
					if (requestId === requestRef.current) {
						setLoading(false);
						if (manual) setBusy(null);
					}
				}
			},
			[model.planId],
		);

		useEffect(() => {
			if (!modelIdentity) return;
			setCandidate(null);
			setPublication(null);
			setLoading(true);
			void refresh();
			return () => {
				requestRef.current += 1;
			};
		}, [modelIdentity, refresh]);

		useEffect(() => {
			if (!exactCandidate || !POLLED_STATUSES.has(exactCandidate.status) || error) return;
			const timer = window.setTimeout(() => void refresh(), 3000);
			return () => window.clearTimeout(timer);
		}, [error, exactCandidate, refresh]);

		const startBuild = useCallback(async () => {
			if (
				loading ||
				busy ||
				error ||
				readOnly ||
				!implementationReady ||
				scopeConflict ||
				model.status === "PUBLISHED" ||
				model.status === "ARCHIVED"
			) {
				return false;
			}
			if (exactActiveCandidate && exactActiveCandidate.status !== "DRAFT") return false;
			setBusy("build");
			setError("");
			setErrorCode("");
			setRecoveryHref("");
			try {
				const result = await startModelBuildIntent(
					{ id: model.id, revision: model.revision, checksum: model.checksum },
					intentKey("model-build", model),
					{
						planId: model.planId,
						environment: exactCandidate?.environment || "DEV",
					},
				);
				setCandidate(result.candidate);
				return true;
			} catch (buildError) {
				const resolved = intentError(buildError, "构建请求未被接受，请刷新候选状态后重试。");
				setError(resolved.message);
				setErrorCode(resolved.code);
				setRecoveryHref(resolved.workspaceHref || "");
				return false;
			} finally {
				setBusy(null);
				setLoading(false);
			}
		}, [
			busy,
			error,
			exactActiveCandidate,
			exactCandidate,
			implementationReady,
			loading,
			model,
			readOnly,
			scopeConflict,
		]);

		const submitPublication = async () => {
			if (busy || error || readOnly || !exactCandidate || exactCandidate.status !== "BUILT") return;
			setBusy("publish");
			setError("");
			setErrorCode("");
			setRecoveryHref("");
			try {
				const result = await startModelPublicationIntent(
					model.id,
					{ id: exactCandidate.id, version: exactCandidate.version },
					intentKey("model-publish", model),
					"提交当前模型上线",
				);
				setPublication(result);
				setCandidate((current) =>
					current?.id === result.candidateId
						? { ...current, status: result.candidateStatus, version: result.candidateVersion }
						: current,
				);
				void refresh();
			} catch (publishError) {
				const resolved = intentError(publishError, "提交上线未被接受，请刷新候选状态后重试。");
				setError(resolved.message);
				setErrorCode(resolved.code);
				setRecoveryHref(resolved.workspaceHref || "");
			} finally {
				setBusy(null);
			}
		};

		useImperativeHandle(ref, () => ({ startBuild, refresh: () => refresh(true) }), [refresh, startBuild]);

		const presentation = exactCandidate ? statusPresentation[exactCandidate.status] : null;
		const workbenchHref = candidate
			? releaseCandidateWorkbenchPath(model, candidate.id)
			: recoveryHref || publication?.workbenchUrl || "";
		const buildDisabled =
			loading ||
			Boolean(error) ||
			Boolean(busy) ||
			readOnly ||
			!implementationReady ||
			scopeConflict ||
			model.status === "PUBLISHED" ||
			model.status === "ARCHIVED" ||
			Boolean(exactActiveCandidate && exactActiveCandidate.status !== "DRAFT");
		const buildTitle =
			model.status === "PUBLISHED"
				? "当前 revision 已发布；后续计算应进入计划运行，不重复创建发布候选。"
				: !implementationReady
					? "请先保存并验证当前数据实现。"
					: scopeConflict
						? "当前计划已有其他候选占用交付控制面，请先查看候选。"
						: error || undefined;
		const actions = (
			<Space wrap>
				<Button
					type="primary"
					loading={busy === "build"}
					disabled={buildDisabled}
					title={buildTitle}
					onClick={() => void startBuild()}
					data-testid="model-build-intent"
				>
					构建
				</Button>
				<Button
					loading={busy === "publish"}
					disabled={Boolean(busy) || readOnly || exactCandidate?.status !== "BUILT"}
					title={exactCandidate?.status === "BUILT" ? undefined : "真实构建完成后才能提交上线。"}
					onClick={() => void submitPublication()}
					data-testid="model-publish-intent"
				>
					提交上线
				</Button>
				<Button loading={busy === "refresh"} disabled={Boolean(busy)} onClick={() => void refresh(true)}>
					刷新状态
				</Button>
				{workbenchHref ? (
					<Button href={resolveAppHref(workbenchHref)} data-testid="model-delivery-workbench-link">
						查看交付工作台
					</Button>
				) : null}
			</Space>
		);
		const status = (
			<div className="space-y-2">
				{loading && !candidate ? <Text type="secondary">正在读取当前候选状态…</Text> : null}
				{scopeConflict ? (
					<Alert
						type="warning"
						showIcon
						message={candidate?.origin === "BATCH_WORKBENCH" ? "当前模型已在批量交付中" : "当前计划已有其他单模型候选"}
						description="快捷入口不会修改、移出或覆盖现有候选；请进入交付工作台处理。"
					/>
				) : null}
				{presentation ? (
					<Space wrap>
						<Tag color={presentation.color}>{presentation.label}</Tag>
						<Text type="secondary">{presentation.description}</Text>
					</Space>
				) : null}
				{publication?.blocker ? <Alert type="info" showIcon message={publication.blocker.message} /> : null}
				{error ? <Alert type="error" showIcon message={errorCode ? `${errorCode} · ${error}` : error} /> : null}
			</div>
		);

		if (compact) {
			return (
				<div id={id} className="space-y-2" data-testid="model-delivery-intent-actions">
					{actions}
					{status}
				</div>
			);
		}
		return (
			<Card
				id={id}
				size="small"
				className="mb-4"
				title="构建与提交上线"
				extra={actions}
				data-testid="model-delivery-intent-actions"
			>
				<div className="mb-2 text-sm text-slate-600">
					“构建”通过统一候选触发 Airflow/dbt 物化；“提交上线”只启动质量检查并提交人工审核，不会自动审核或发布。
				</div>
				{status}
			</Card>
		);
	},
);
