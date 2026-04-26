import { Card, Col, Row, Skeleton, Statistic } from "antd";
import type { LeaderOverviewKpis } from "@/api/services/workbenchService";
import type { WorkbenchRole } from "../hooks/useWorkbenchRole";
import { MoMSecondary, RatioSecondary, StaticSecondary } from "./KpiSecondary";
import { timeRangeLabel } from "./TimeRangeSelect";
import type { WorkbenchFilterState } from "./WorkbenchFilterBar";

/**
 * Sprint-15 F4/T01 + T02 — Role-differentiated KPI row.
 *
 * | Role          | Cards | Semantics                                                           |
 * | ------------- | ----- | ------------------------------------------------------------------- |
 * | EMP           | 3     | 我常用的大屏 / 本期访问（个人） / 我常用的资产                      |
 * | DEPT_LEADER   | 3     | 本部门大屏 / 本期访问（部门） / 部门核心资产（S1+S2）               |
 * | INST_LEADER   | 4     | 所内大屏 / 本期访问（全所） / 数据资产 / 核心资产 S1                |
 *
 * "本期" follows `filter.timeRange` (本月 / 本季 / 本年).
 */

type CardDef =
	| { key: string; title: string; value: number; kind: "static"; text?: string }
	| { key: string; title: string; value: number; kind: "mom"; mom: number | null }
	| { key: string; title: string; value: number; kind: "ratio"; ratio: number | null }
	| { key: string; title: string; value: number; kind: "none" };

export interface KpiRowProps {
	// P1-9: single source of truth — `WorkbenchRole` is exported from the
	// hook so adding a 4th role only requires updating one literal.
	role: WorkbenchRole;
	filter: WorkbenchFilterState;
	kpis: LeaderOverviewKpis | null;
	loading: boolean;
	/** P0-14: surface the page-level fetch error so KPI cards do not display
	 *  fake "0" values that mask the retry-able Alert above us. */
	error?: boolean;
}

export function KpiRow({ role, filter, kpis, loading, error }: KpiRowProps) {
	// P0-14: error state must NOT render the skeleton (which suggests work in
	// progress) nor the placeholders (whose hard-coded "0" reads as real data).
	// Returning null lets the page-level <Alert> own the failure surface.
	if (error && !loading) {
		return null;
	}

	const cards = buildCards(role, filter, kpis);
	const span = role === "INST_LEADER" ? 6 : 8;
	const showSkeleton = loading || !kpis;

	return (
		<Row gutter={16}>
			{cards.map((c) => (
				<Col key={c.key} xs={24} sm={12} md={span}>
					<Card>
						{showSkeleton ? (
							<Skeleton active paragraph={{ rows: 1 }} />
						) : (
							<>
								<Statistic title={c.title} value={c.value} />
								<div style={{ marginTop: 8 }}>{renderSecondary(c)}</div>
							</>
						)}
					</Card>
				</Col>
			))}
		</Row>
	);
}

function renderSecondary(card: CardDef) {
	switch (card.kind) {
		case "mom":
			return <MoMSecondary mom={card.mom} />;
		case "ratio":
			return <RatioSecondary ratio={card.ratio} />;
		case "static":
			return <StaticSecondary text={card.text} />;
		case "none":
			return null;
	}
}

function buildCards(
	role: KpiRowProps["role"],
	filter: WorkbenchFilterState,
	kpis: LeaderOverviewKpis | null,
): CardDef[] {
	const periodLabel = timeRangeLabel(filter.timeRange);
	if (!kpis) return placeholders(role);

	if (role === "EMP") {
		return [
			{ key: "myReports", title: "我常用的大屏", value: kpis.reportsTotal, kind: "static", text: "近 30 天访问过" },
			{ key: "myVisits", title: `${periodLabel}访问`, value: kpis.visitsInPeriod, kind: "none" },
			{ key: "myAssets", title: "我常用的资产", value: kpis.assetsTotal, kind: "static", text: "近 30 天访问过" },
		];
	}

	if (role === "DEPT_LEADER") {
		return [
			{
				key: "deptReports",
				title: "本部门大屏",
				value: kpis.reportsTotal,
				kind: "static",
				text: `${periodLabel}新发布 ${kpis.reportsNewInPeriod}`,
			},
			{
				key: "deptVisits",
				title: `${periodLabel}访问`,
				value: kpis.visitsInPeriod,
				kind: "mom",
				mom: kpis.visitsMoM,
			},
			{
				key: "deptCoreAssets",
				title: "部门核心资产",
				value: kpis.assetsS1S2,
				kind: "static",
				text: "S1 + S2 总数",
			},
		];
	}

	// INST_LEADER
	return [
		{
			key: "instReports",
			title: "所内大屏",
			value: kpis.reportsTotal,
			kind: "static",
			text: `${periodLabel}新发布 ${kpis.reportsNewInPeriod}`,
		},
		{
			key: "instVisits",
			title: `${periodLabel}访问`,
			value: kpis.visitsInPeriod,
			kind: "mom",
			mom: kpis.visitsMoM,
		},
		{
			key: "instAssets",
			title: "数据资产",
			value: kpis.assetsTotal,
			kind: "static",
			text: `${periodLabel}新增 ${kpis.assetsNewInPeriod}`,
		},
		{
			key: "instS1",
			title: "核心资产（S1）",
			value: kpis.assetsS1,
			kind: "ratio",
			ratio: kpis.assetsS1Ratio,
		},
	];
}

function placeholders(role: KpiRowProps["role"]): CardDef[] {
	const count = role === "INST_LEADER" ? 4 : 3;
	return Array.from(
		{ length: count },
		(_, i): CardDef => ({
			key: `ph-${i}`,
			title: "",
			value: 0,
			kind: "none",
		}),
	);
}

export default KpiRow;
