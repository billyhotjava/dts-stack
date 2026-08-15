// @vitest-environment node
import { describe, it, expect } from "vitest";
import { aggregateMajorProjects, type FlatProjectNodeRow } from "./majorProjectAggregator";

const sampleRows: FlatProjectNodeRow[] = [
	{
		重大项目: "制造协同平台",
		子项目: "平台基础",
		任务: "需求分析",
		类型: "一般任务",
		计划日期: "2026-01-06",
		实际日期: "2026-02-15",
		是否完成: true,
		是否超期完成: false,
		延期天数: 0,
		风险等级: "低",
		责任科室: "研发部",
		项目经理: "张工",
	},
	{
		重大项目: "制造协同平台",
		子项目: "平台基础",
		任务: "系统设计",
		类型: "一般任务",
		计划日期: "2026-02-10",
		实际日期: "2026-03-20",
		是否完成: true,
		延期天数: 0,
		风险等级: "低",
		责任科室: "研发部",
		项目经理: "张工",
	},
	{
		重大项目: "制造协同平台",
		子项目: "平台基础",
		任务: "里程碑 M1",
		类型: "里程碑节点",
		计划日期: "2026-03-20",
		实际日期: "2026-03-20",
		是否完成: true,
		延期天数: 0,
		风险等级: "低",
		责任科室: "研发部",
		项目经理: "张工",
	},
	{
		重大项目: "制造协同平台",
		子项目: "核心模块",
		任务: "核心开发",
		类型: "一般任务",
		计划日期: "2026-03-01",
		是否完成: false,
		延期天数: 0,
		风险等级: "中",
		责任科室: "研发部",
	},
	{
		重大项目: "新能源工厂",
		子项目: "厂房建设",
		任务: "基建施工",
		类型: "一般任务",
		计划日期: "2026-01-15",
		是否完成: false,
		是否未完成: true,
		延期天数: 28,
		风险等级: "高",
		责任科室: "工程建设",
		项目经理: "刘工",
	},
];

describe("aggregateMajorProjects", () => {
	it("groups by 重大项目", () => {
		const result = aggregateMajorProjects(sampleRows);
		expect(result).toHaveLength(2);
		expect(result.map((p) => p.name).sort()).toEqual(["制造协同平台", "新能源工厂"]);
	});

	it("groups subprojects within each major project", () => {
		const result = aggregateMajorProjects(sampleRows);
		const platform = result.find((p) => p.name === "制造协同平台")!;
		expect(platform.subprojects).toHaveLength(2);
		const subNames = platform.subprojects.map((s) => s.name).sort();
		expect(subNames).toEqual(["平台基础", "核心模块"]);
	});

	it("computes completionRate correctly", () => {
		const result = aggregateMajorProjects(sampleRows);
		const platform = result.find((p) => p.name === "制造协同平台")!;
		// 4 rows, 3 completed → 75%
		expect(platform.kpi?.completionRate).toBe(75);
	});

	it("computes milestoneRate using only milestone rows", () => {
		const result = aggregateMajorProjects(sampleRows);
		const platform = result.find((p) => p.name === "制造协同平台")!;
		// 1 milestone, 1 completed → 1.000
		expect(platform.kpi?.milestoneRate).toBe(1);
	});

	it("counts highRiskCount as open high-risk only", () => {
		const result = aggregateMajorProjects(sampleRows);
		const factory = result.find((p) => p.name === "新能源工厂")!;
		expect(factory.kpi?.highRiskCount).toBe(1);
	});

	it("sums delayDays", () => {
		const result = aggregateMajorProjects(sampleRows);
		const factory = result.find((p) => p.name === "新能源工厂")!;
		expect(factory.kpi?.delayDays).toBe(28);
	});

	it("produces risks list sorted by delayDays desc", () => {
		const result = aggregateMajorProjects(sampleRows);
		const factory = result.find((p) => p.name === "新能源工厂")!;
		expect(factory.risks).toHaveLength(1);
		expect(factory.risks?.[0].level).toBe("high");
	});

	it("uses pickFirstNonEmpty for manager", () => {
		const result = aggregateMajorProjects(sampleRows);
		expect(result.find((p) => p.name === "制造协同平台")?.manager).toBe("张工");
		expect(result.find((p) => p.name === "新能源工厂")?.manager).toBe("刘工");
	});

	it("derives startDate as earliest planDate", () => {
		const result = aggregateMajorProjects(sampleRows);
		expect(result.find((p) => p.name === "制造协同平台")?.startDate).toBe("2026-01-06");
	});

	it("prefers explicit planned start/end aliases when present", () => {
		const rows: FlatProjectNodeRow[] = [
			{
				重大项目: "星链工程",
				子项目: "平台总装",
				任务: "总装集成",
				类型: "重大节点",
				计划开始日期: "2026-01-03",
				计划完成日期: "2026-01-20",
				实际开始日期: "2026-01-05",
				实际完成日期: "2026-01-22",
				是否完成: true,
				延期天数: 2,
				风险等级: "中",
				责任科室: "总装室",
				项目经理: "周工",
			},
		];
		const result = aggregateMajorProjects(rows);
		const project = result[0];
		const task = project.subprojects[0].tasks[0];

		expect(project.startDate).toBe("2026-01-03");
		expect(project.plannedDeliveryDate).toBe("2026-01-20");
		// planDate/planEndDate 是甘特“计划条”的范围，取计划开始/完成；实际日期另有字段。
		expect(task.planDate).toBe("2026-01-03");
		expect(task.planEndDate).toBe("2026-01-20");
		expect(task.baselineStartDate).toBe("2026-01-03");
		expect(task.baselineEndDate).toBe("2026-01-20");
		expect(task.actualStartDate).toBe("2026-01-05");
		expect(task.actualDate).toBe("2026-01-22");
	});

	it("handles rows with empty 重大项目 by skipping", () => {
		const rows: FlatProjectNodeRow[] = [
			...sampleRows,
			{ 重大项目: "", 子项目: "X", 任务: "noise" },
			{ 重大项目: undefined, 子项目: "X", 任务: "noise" },
		];
		const result = aggregateMajorProjects(rows);
		expect(result).toHaveLength(2);
	});

	it("uses (无子项目) for null subsystem", () => {
		const rows: FlatProjectNodeRow[] = [{ 重大项目: "A", 子项目: null, 任务: "t1", 计划日期: "2026-01-01" }];
		const result = aggregateMajorProjects(rows);
		expect(result[0].subprojects[0].name).toBe("(无子项目)");
	});
});
