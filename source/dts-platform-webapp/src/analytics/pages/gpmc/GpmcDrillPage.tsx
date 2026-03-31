import { useRef } from "react";
import { useNavigate, useParams } from "react-router";
import GpmcApp, {
	getGpmcScreenPath,
	isGpmcBoardScreenId,
	type GpmcBoardScreenId,
} from "./GpmcApp";
import GpmcLayout from "./GpmcLayout";
import GpmcDrillScreen from "./screens/GpmcDrillScreen";

const DRILL_META: Record<GpmcBoardScreenId, { title: string; description: string }> = {
	execution: {
		title: "执行层·项目执行详情",
		description: "承载项目节点、延期排行、资源负载等执行控制信息。",
	},
	quality: {
		title: "执行层·质量问题与跟进措施",
		description: "承载质量问题明细、闭环状态和跟进措施摘要。",
	},
	"tech-state": {
		title: "执行层·技术状态与跟进",
		description: "承载技术状态变更、签署状态与跟进动作。",
	},
	cost: {
		title: "执行层·成本与预算控制",
		description: "承载预算执行、周期成本和偏差明细。",
	},
	risk: {
		title: "执行层·风险与预警中心",
		description: "承载风险清单、等级分布与应对措施。",
	},
};

export default function GpmcDrillPage() {
	const renderCount = useRef(0);
	renderCount.current += 1;
	if (renderCount.current % 50 === 0 || renderCount.current <= 5) {
		console.warn('[GPMC-DRILL] render #', renderCount.current);
	}
	const navigate = useNavigate();
	const { domain } = useParams();
	const screen = domain && isGpmcBoardScreenId(domain) ? domain : "execution";
	const meta = DRILL_META[screen];

	return (
		<GpmcApp
			initialScreen={screen}
			initialLayer="execution"
			onScreenChange={(nextScreen) => navigate(getGpmcScreenPath(nextScreen))}
			onLayerChange={(nextLayer, currentScreen) => {
				if (nextLayer === "strategic") {
					navigate(getGpmcScreenPath("overview"));
					return;
				}
				navigate(getGpmcScreenPath(currentScreen));
			}}
			onDrillDown={() => {}}
		>
			<GpmcLayout title={meta.title} description={meta.description}>
				<GpmcDrillScreen domain={screen} />
			</GpmcLayout>
		</GpmcApp>
	);
}
