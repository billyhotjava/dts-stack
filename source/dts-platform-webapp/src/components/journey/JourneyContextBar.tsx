import { useState } from "react";
import { Button, Card, Space, Tag, Typography } from "antd";
import { useRouter } from "@/routes/hooks";
import { cn } from "@/utils";
import {
	JOURNEY_CONTEXT_PARAM_KEYS,
	JOURNEY_CONTEXT_PARAM_LABELS,
	buildJourneyParamClearUrl,
	buildJourneyUrl,
	journeyJoinDismissStorageKey,
	resolveJourneyBarMode,
	type DataProductJourneyStageKey,
	type JourneyContextParamKey,
} from "./journeyContext";
import { toArtifactValidationMap, type ArtifactValidationMap, type ArtifactValidationResult } from "./journeyArtifactValidation";
import { DATA_PRODUCT_JOURNEY_STAGE_DEFINITIONS, resolveDataProductJourneyStageState } from "./journeyStageState";
import { useDataProductJourneyContext } from "./useDataProductJourneyContext";

const { Text } = Typography;

type JourneyContextBarProps = {
	stage: DataProductJourneyStageKey;
	className?: string;
	// 页面可注入 artifact 校验结果：invalid 的上下文对象红标并提供清除入口。
	validations?: ArtifactValidationMap | ArtifactValidationResult[];
};

const STATUS_LABELS: Record<string, string> = {
	not_started: "未开始",
	blocked: "有缺口",
	ready: "可开始",
	in_progress: "进行中",
	done: "已就绪",
};

const STATUS_COLORS: Record<string, string> = {
	not_started: "default",
	blocked: "orange",
	ready: "blue",
	in_progress: "geekblue",
	done: "green",
};

export function JourneyContextBar({ stage, className, validations }: JourneyContextBarProps) {
	const router = useRouter();
	const context = useDataProductJourneyContext(stage);
	const stageState = resolveDataProductJourneyStageState(stage, context.params, validations);
	const validationMap = Array.isArray(validations) ? toArtifactValidationMap(validations) : (validations ?? {});
	const invalidKeys = new Set(
		Object.values(validationMap)
			.filter((item) => item?.status === "invalid")
			.map((item) => item.key),
	);
	const findParamKey = (label: string): JourneyContextParamKey | undefined =>
		JOURNEY_CONTEXT_PARAM_KEYS.find((key) => JOURNEY_CONTEXT_PARAM_LABELS[key] === label);
	const clearParam = (key: JourneyContextParamKey) => {
		router.push(buildJourneyParamClearUrl(stageState.route, context.params as Record<string, string>, key));
	};
	const [joinDismissed, setJoinDismissed] = useState(() => {
		try {
			return typeof window !== "undefined" && window.sessionStorage.getItem(journeyJoinDismissStorageKey(stage)) === "1";
		} catch {
			return false;
		}
	});
	const dismissJoinHint = () => {
		setJoinDismissed(true);
		try {
			window.sessionStorage.setItem(journeyJoinDismissStorageKey(stage), "1");
		} catch {
			// sessionStorage 不可用时仅在本次渲染周期内关闭。
		}
	};
	const barMode = resolveJourneyBarMode(context.enabled, joinDismissed);

	if (barMode === "hidden") return null;
	if (barMode === "joinable") {
		const stageIndex = DATA_PRODUCT_JOURNEY_STAGE_DEFINITIONS.findIndex((item) => item.stageKey === stage) + 1;
		return (
			<div
				className={cn(
					"flex flex-wrap items-center justify-between gap-2 rounded border border-dashed border-gray-200 bg-gray-50 px-3 py-1.5 text-sm",
					className,
				)}
				data-testid="journey-join-hint"
			>
				<Text type="secondary">
					此页面是数据产品旅程的第 {stageIndex} 步（{context.stageLabel}）· 从工作台开始可获得完整的上下文与下一步引导
				</Text>
				<Space size={4}>
					<Button
						size="small"
						type="link"
						data-testid="journey-join-enter"
						onClick={() => router.push(buildJourneyUrl(stageState.route))}
					>
						进入旅程
					</Button>
					<Button size="small" type="text" data-testid="journey-join-dismiss" onClick={dismissJoinHint}>
						×
					</Button>
				</Space>
			</div>
		);
	}

	return (
		<Card
			size="small"
			className={cn("border-dashed", className)}
			data-testid="data-product-journey-context-bar"
		>
			<div className="flex flex-wrap items-center justify-between gap-3">
				<Space wrap size={[8, 8]}>
					<Tag color="blue">端到端旅程</Tag>
					<Text type="secondary">当前阶段</Text>
					<Text strong>{context.stageLabel}</Text>
					<Tag color={STATUS_COLORS[stageState.status]}>{STATUS_LABELS[stageState.status]}</Tag>
					{stageState.status === "done" && stageState.verification === "unverified" ? (
						<Tag color="gold" data-testid="journey-context-unverified">
							待确认
						</Tag>
					) : null}
					<Text type="secondary">来源对象</Text>
					{context.contextLabels.length > 0 ? (
						context.contextLabels.map((item) => {
							const paramKey = findParamKey(item.label);
							const invalid = paramKey ? invalidKeys.has(paramKey) : false;
							return (
								<Tag
									key={`${item.label}-${item.value}`}
									color={invalid ? "red" : undefined}
									data-testid={invalid ? "journey-context-invalid-param" : undefined}
									closable={invalid}
									onClose={(event) => {
										event.preventDefault();
										if (paramKey) clearParam(paramKey);
									}}
								>
									{item.label}: {item.value}
									{invalid ? "（无效）" : ""}
								</Tag>
							);
						})
					) : (
						<Tag>待选择</Tag>
					)}
					<Text type="secondary">{stageState.gap}</Text>
					{stageState.blocker ? <Tag color="orange">{stageState.blocker.reason}</Tag> : null}
				</Space>
				<Space wrap>
					<Button
						data-testid="data-product-journey-return"
						onClick={() => router.push(context.returnUrl)}
					>
						返回工作台
					</Button>
					<Button
						data-testid="data-product-journey-evidence"
						onClick={() => router.push(context.evidenceUrl)}
					>
						查看证据
					</Button>
					<Button
						type="primary"
						data-testid="data-product-journey-next"
						onClick={() => router.push(stageState.nextAction.url || context.nextUrl)}
					>
						继续下一步
					</Button>
				</Space>
			</div>
		</Card>
	);
}
