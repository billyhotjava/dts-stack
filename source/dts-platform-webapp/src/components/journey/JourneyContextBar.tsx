import { Button, Card, Space, Tag, Typography } from "antd";
import { useRouter } from "@/routes/hooks";
import { cn } from "@/utils";
import type { DataProductJourneyStageKey } from "./journeyContext";
import { resolveDataProductJourneyStageState } from "./journeyStageState";
import { useDataProductJourneyContext } from "./useDataProductJourneyContext";

const { Text } = Typography;

type JourneyContextBarProps = {
	stage: DataProductJourneyStageKey;
	className?: string;
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

export function JourneyContextBar({ stage, className }: JourneyContextBarProps) {
	const router = useRouter();
	const context = useDataProductJourneyContext(stage);
	const stageState = resolveDataProductJourneyStageState(stage, context.params);

	if (!context.enabled) return null;

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
					<Text type="secondary">来源对象</Text>
					{context.contextLabels.length > 0 ? (
						context.contextLabels.map((item) => (
							<Tag key={`${item.label}-${item.value}`}>
								{item.label}: {item.value}
							</Tag>
						))
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
