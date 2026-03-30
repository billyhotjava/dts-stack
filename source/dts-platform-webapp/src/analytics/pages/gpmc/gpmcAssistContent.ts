export type GpmcScreenId = "overview" | "execution" | "quality" | "tech-state" | "cost" | "risk";
export type GpmcBoardScreenId = Exclude<GpmcScreenId, "overview">;
export type GpmcDrillLayer = "strategic" | "control" | "execution";

function getGpmcScreenPath(screen: GpmcScreenId) {
	return screen === "overview" ? "/gpmc" : `/gpmc/${screen}`;
}

function getGpmcDrillPath(screen: GpmcBoardScreenId) {
	return `/gpmc/drill/${screen}`;
}

export type GpmcAssistAction = {
	label: string;
	description: string;
	href: string;
};

export type GpmcAssistCard = {
	title: string;
	summary: string;
	bullets: string[];
	actions?: GpmcAssistAction[];
};

export type GpmcAssistContent = {
	explanation: GpmcAssistCard;
	guide?: GpmcAssistCard;
};

function getStrategicAssistContent(): GpmcAssistContent {
	return {
		explanation: {
			title: "说明卡",
			summary: "面向集团领导，一屏判断项目是否健康、投资执行是否偏离、风险是否失控。",
			bullets: [
				"优先关注项目总数、在建、延期、高风险四组总览指标。",
				"结合预算与执行、完成率趋势、事业部排名判断全局健康度。",
				"从异常项目与专题聚焦区定位需要继续追问的管控专题。",
			],
		},
		guide: {
			title: "导览卡",
			summary: "建议按“总览态势 → 问题专题 → 执行明细”的顺序讲解，不打断全局浏览。",
			bullets: [
				'先讲“延期项目”和“高风险项目”，再进入对应管控看板。',
				"看板里的指标、榜单和清单继续下钻到执行层只读页面。",
				"执行层只承载明细与跟进情况，不在演示中编辑数据。",
			],
			actions: [
				{ label: "项目执行监控", description: "查看延期 TOP10、里程碑与阻塞链路。", href: getGpmcScreenPath("execution") },
				{ label: "质量信息与跟进", description: "查看质量问题规模、闭环率与措施清单。", href: getGpmcScreenPath("quality") },
				{ label: "技术状态与跟进", description: "查看技术状态变更、签署率与未闭环项。", href: getGpmcScreenPath("tech-state") },
				{ label: "成本与预算控制", description: "查看预算执行、偏差和周期支出趋势。", href: getGpmcScreenPath("cost") },
				{ label: "风险与预警中心", description: "查看风险矩阵、分布与闭环情况。", href: getGpmcScreenPath("risk") },
			],
		},
	};
}

function getControlAssistContent(screen: GpmcBoardScreenId): GpmcAssistContent {
	const explanationByScreen: Record<GpmcBoardScreenId, GpmcAssistCard> = {
		execution: {
			title: "说明卡",
			summary: "面向 PMO 和部门负责人，定位进度失控点、延期责任和阻塞链路。",
			bullets: [
				"重点看完成率、里程碑达成率、延期 TOP10 和责任科室负载。",
				"甘特与阻塞链路用于判断“哪里拖”“谁在拖”。",
				"点击排行、任务和 KPI 可进入执行层只读详情页。",
			],
		},
		quality: {
			title: "说明卡",
			summary: "聚焦质量问题规模、分类结构、闭环率和跟进措施覆盖情况。",
			bullets: [
				"先看新增问题、现存问题和未提交归零计划。",
				"再看分类分布、项目排名和质量问题清单。",
				"问题与措施摘要用于判断质量闭环是否真正落地。",
			],
		},
		"tech-state": {
			title: "说明卡",
			summary: "聚焦技术状态变更、签署进度、未闭环项和专项跟进动作。",
			bullets: [
				"先看状态变更数、签署完成率和未闭环项。",
				"再看变更类别分布和跟进措施摘要。",
				"技术状态看板用于判断变更是否受控，而不是只看变更多寡。",
			],
		},
		cost: {
			title: "说明卡",
			summary: "聚焦预算执行率、偏差项目、责任部门和周期性成本波动。",
			bullets: [
				"先看预算总额、实际执行和偏差额。",
				"再看月度支出趋势和部门预算执行对比。",
				"项目成本偏差排名用于快速锁定需要追问的项目。",
			],
		},
		risk: {
			title: "说明卡",
			summary: "聚焦风险等级、分布、矩阵位置和措施闭环效果。",
			bullets: [
				"先看风险总数、高风险数和闭环率。",
				"再看风险矩阵、分类分布和重点风险清单。",
				"措施摘要用于判断高风险是否有明确应对路径。",
			],
		},
	};

	const guideByScreen: Record<GpmcBoardScreenId, GpmcAssistCard> = {
		execution: {
			title: "导览卡",
			summary: "建议先讲延期和阻塞，再进入执行层明细承载节点、责任人和资源负载。",
			bullets: [
				"先用延期 TOP10 和阻塞链路回答“哪里出问题”。",
				"再下钻到执行层查看甘特、节点目标和责任人分析。",
				"如需回到大屏，只讲异常专题回路，不重复讲全量基础信息。",
			],
			actions: [
				{ label: "进入执行层详情", description: "查看甘特、节点、责任人、资源负载等只读明细。", href: getGpmcDrillPath("execution") },
				{ label: "返回战略层大屏", description: "回到全局态势总览继续讲解战略视角。", href: getGpmcScreenPath("overview") },
			],
		},
		quality: {
			title: "导览卡",
			summary: "建议按“问题规模 → 分类 → 清单 → 措施”讲解，再下钻质量只读详情。",
			bullets: [
				"优先讲高优未关和未提交归零计划，体现问题严重度。",
				"再进入问题清单和措施摘要，说明当前闭环状态。",
				"执行层详情页承载问题对象、责任人和措施时间线。",
			],
			actions: [
				{ label: "进入质量详情", description: "查看质量问题对象、状态和跟进措施明细。", href: getGpmcDrillPath("quality") },
				{ label: "查看技术状态看板", description: "继续讲解与质量相邻的技术状态专题。", href: getGpmcScreenPath("tech-state") },
			],
		},
		"tech-state": {
			title: "导览卡",
			summary: "建议按“状态变更 → 签署完成率 → 未闭环项 → 措施”讲解。",
			bullets: [
				"先用变更数和签署完成率说明变更是否受控。",
				"再下钻到执行层查看技术状态对象和措施细节。",
				"技术状态与质量相邻，可串联说明两类问题的差异。",
			],
			actions: [
				{ label: "进入技术状态详情", description: "查看状态对象、签署情况和跟进措施。", href: getGpmcDrillPath("tech-state") },
				{ label: "查看质量看板", description: "切换到质量专题，串联说明问题与状态。", href: getGpmcScreenPath("quality") },
			],
		},
		cost: {
			title: "导览卡",
			summary: "建议按“预算执行 → 偏差项目 → 部门对比 → 周期趋势”讲解。",
			bullets: [
				"先讲整体预算执行率，再聚焦偏差额最大的项目。",
				"部门对比用于解释偏差来源是否集中。",
				"执行层详情页承载项目和周期维度的成本只读明细。",
			],
			actions: [
				{ label: "进入成本详情", description: "查看项目、部门、周期三个维度的成本明细。", href: getGpmcDrillPath("cost") },
				{ label: "返回战略层大屏", description: "回到全局态势继续讲投资执行与风险。", href: getGpmcScreenPath("overview") },
			],
		},
		risk: {
			title: "导览卡",
			summary: "建议按“风险等级 → 矩阵分布 → 重点风险 → 措施闭环”讲解。",
			bullets: [
				"先用高风险数和矩阵解释风险集中在哪些象限。",
				"再聚焦重点风险清单和措施摘要，说明是否受控。",
				"执行层详情承载风险对象与应对措施的只读明细。",
			],
			actions: [
				{ label: "进入风险详情", description: "查看风险对象、等级、责任人和措施明细。", href: getGpmcDrillPath("risk") },
				{ label: "返回战略层大屏", description: "回到全局态势继续讲战略层健康度。", href: getGpmcScreenPath("overview") },
			],
		},
	};

	return {
		explanation: explanationByScreen[screen],
		guide: guideByScreen[screen],
	};
}

function getExecutionAssistContent(screen: GpmcBoardScreenId): GpmcAssistContent {
	const summaryByScreen: Record<GpmcBoardScreenId, string> = {
		execution: "执行层承载项目、节点、责任人、甘特和资源负载等只读明细，用于回答“怎么落地解决”。",
		quality: "执行层承载质量问题对象、责任人、闭环状态和措施明细，用于回答“问题如何收口”。",
		"tech-state": "执行层承载技术状态对象、签署状态和跟进动作明细，用于回答“变更如何受控”。",
		cost: "执行层承载项目、部门和周期成本明细，用于回答“偏差出在哪、影响多大”。",
		risk: "执行层承载风险对象、等级、责任人和措施明细，用于回答“风险如何闭环”。",
	};

	return {
		explanation: {
			title: "说明卡",
			summary: summaryByScreen[screen],
			bullets: [
				"执行层页面只读，不在领导演示阶段承载编辑和回写。",
				"页面用于展开项目对象、责任人、任务/问题/风险与跟进措施的细节。",
				"建议从上级看板进入，说明当前明细是对哪类专题问题的展开。",
			],
			actions: [{ label: "返回上级看板", description: "回到对应管控层看板继续整体讲解。", href: getGpmcScreenPath(screen) }],
		},
	};
}

export function getGpmcAssistContent(screen: GpmcScreenId, layer: GpmcDrillLayer): GpmcAssistContent {
	if (layer === "strategic" || screen === "overview") {
		return getStrategicAssistContent();
	}
	if (layer === "execution") {
		return getExecutionAssistContent(screen);
	}
	return getControlAssistContent(screen as GpmcBoardScreenId);
}

export function flattenAssistCardBody(card: GpmcAssistCard) {
	const lines = [card.summary, ...card.bullets.map((item) => `• ${item}`)];
	if (card.actions?.length) {
		lines.push("", ...card.actions.map((action) => `→ ${action.label}：${action.description}`));
	}
	return lines.join("\n");
}
