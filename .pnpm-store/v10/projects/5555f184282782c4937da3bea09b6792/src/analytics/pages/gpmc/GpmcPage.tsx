import { useNavigate, useParams } from "react-router";
import GpmcApp, {
	getGpmcScreenPath,
	isGpmcScreenId,
	useGpmc,
	type GpmcScreenId,
} from "./GpmcApp";
import GpmcLayout from "./GpmcLayout";
import OverviewScreen from "./screens/OverviewScreen";
import ExecutionScreen from "./screens/ExecutionScreen";
import QualityScreen from "./screens/QualityScreen";
import TechStateScreen from "./screens/TechStateScreen";
import CostScreen from "./screens/CostScreen";
import RiskScreen from "./screens/RiskScreen";

const SCREEN_META: Record<GpmcScreenId, { title: string; description: string }> = {
	overview: {
		title: "集团多项目综合态势感知中心",
		description: "战略层：一屏看全局，聚焦健康度、投资执行、风险分布与事业部排名。",
	},
	execution: {
		title: "项目执行监控",
		description: "管控层：聚焦延期 TOP10、里程碑达成率、任务阻塞链路和责任人负载。",
	},
	quality: {
		title: "质量信息与跟进措施",
		description: "管控层：聚焦问题规模、分类、闭环率与专项措施落地。",
	},
	"tech-state": {
		title: "技术状态与跟进",
		description: "管控层：聚焦技术状态变更、签署完成率、未闭环项和跟进动作。",
	},
	cost: {
		title: "成本与预算控制",
		description: "管控层：聚焦预算执行率、偏差、周期支出和部门成本责任。",
	},
	risk: {
		title: "风险与预警中心",
		description: "管控层：聚焦风险等级、矩阵分布、重点清单和闭环动作。",
	},
};

function ScreenRouter() {
	const { screen } = useGpmc();
	const meta = SCREEN_META[screen] ?? SCREEN_META.overview;
	return (
		<GpmcLayout title={meta.title} description={meta.description}>
			{screen === "overview" && <OverviewScreen />}
			{screen === "execution" && <ExecutionScreen />}
			{screen === "quality" && <QualityScreen />}
			{screen === "tech-state" && <TechStateScreen />}
			{screen === "cost" && <CostScreen />}
			{screen === "risk" && <RiskScreen />}
		</GpmcLayout>
	);
}

export default function GpmcPage() {
	const navigate = useNavigate();
	const { screenId } = useParams();
	const screen = screenId && isGpmcScreenId(screenId) ? screenId : "overview";
	const layer = screen === "overview" ? "strategic" : "control";

	return (
		<GpmcApp
			initialScreen={screen}
			initialLayer={layer}
			onScreenChange={(nextScreen) => navigate(getGpmcScreenPath(nextScreen))}
			onLayerChange={(nextLayer, currentScreen) => {
				if (nextLayer === "strategic") {
					navigate(getGpmcScreenPath("overview"));
					return;
				}
				if (currentScreen === "overview") {
					navigate(getGpmcScreenPath("execution"));
					return;
				}
				navigate(getGpmcScreenPath(currentScreen));
			}}
			onDrillDown={() => {}}
		>
			<ScreenRouter />
		</GpmcApp>
	);
}
