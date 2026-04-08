import type { MajorProject, MajorProjectKPI, MajorProjectRisk, SubProject } from '../../screens/types';
import type { ProjectGanttTask } from '../components/ProjectGanttBoard';

export interface FlatProjectNodeRow {
    重大项目?: string | null;
    子项目?: string | null;
    任务?: string | null;
    类型?: string | null;
    计划日期?: string | null;
    实际日期?: string | null;
    基线日期?: string | null;
    是否完成?: boolean | null;
    是否超期完成?: boolean | null;
    是否未完成?: boolean | null;
    延期天数?: number | null;
    风险等级?: string | null;
    完成情况?: string | null;
    责任科室?: string | null;
    责任人?: string | null;
    项目经理?: string | null;
    所长?: string | null;
    风险内容?: string | null;
    延期影响?: string | null;
}

function rowToTask(r: FlatProjectNodeRow): ProjectGanttTask {
    const isCompleted = r.是否完成 === true;
    const isOverdue = r.是否超期完成 === true || (r.延期天数 != null && r.延期天数 > 0);
    return {
        name: String(r.任务 ?? '').trim(),
        type: String(r.类型 ?? '一般任务'),
        planDate: r.计划日期 ?? undefined,
        planEndDate: r.计划日期 ?? undefined,
        baselineStartDate: r.基线日期 ?? undefined,
        baselineEndDate: r.基线日期 ?? undefined,
        actualDate: r.实际日期 ?? undefined,
        delayDays: r.延期天数 ?? 0,
        riskLevel: String(r.风险等级 ?? ''),
        owner: String(r.责任人 ?? r.责任科室 ?? ''),
        majorProjectName: String(r.重大项目 ?? ''),
        subprojectName: String(r.子项目 ?? ''),
        status: String(r.完成情况 ?? ''),
        isCompleted,
        isOverdue,
        isIncomplete: r.是否未完成 === true,
    } as ProjectGanttTask;
}

function pickFirstNonEmpty(rows: FlatProjectNodeRow[], key: keyof FlatProjectNodeRow): string | undefined {
    for (const r of rows) {
        const v = r[key];
        if (v != null && String(v).trim() !== '') return String(v);
    }
    return undefined;
}

function aggregateMeta(rows: FlatProjectNodeRow[]): {
    responsibleDept?: string;
    manager?: string;
    instituteLeader?: string;
    startDate?: string;
    plannedDeliveryDate?: string;
    stage?: string;
} {
    const planDates = rows
        .map(r => r.计划日期)
        .filter((v): v is string => !!v)
        .slice()
        .sort();
    const stages = rows.map(r => r.完成情况).filter((v): v is string => !!v);
    const allCompleted = rows.length > 0 && rows.every(r => r.是否完成 === true);
    const anyDelayed = rows.some(r => (r.延期天数 ?? 0) > 0);
    let derivedStage: string;
    if (allCompleted) derivedStage = '已完成';
    else if (anyDelayed) derivedStage = '存在延期';
    else derivedStage = '进行中';
    return {
        responsibleDept: pickFirstNonEmpty(rows, '责任科室'),
        manager: pickFirstNonEmpty(rows, '项目经理'),
        instituteLeader: pickFirstNonEmpty(rows, '所长'),
        startDate: planDates[0],
        plannedDeliveryDate: planDates[planDates.length - 1],
        stage: stages[0] ?? derivedStage,
    };
}

function aggregateKpi(rows: FlatProjectNodeRow[]): MajorProjectKPI {
    const total = rows.length;
    const completed = rows.filter(r => r.是否完成 === true).length;
    const milestones = rows.filter(r => String(r.类型 ?? '').includes('里程碑'));
    const milestoneCompleted = milestones.filter(r => r.是否完成 === true).length;
    const highRiskOpen = rows.filter(r => String(r.风险等级 ?? '') === '高' && r.是否完成 !== true).length;
    const totalDelay = rows.reduce((sum, r) => sum + Math.max(0, r.延期天数 ?? 0), 0);
    return {
        completionRate: total === 0 ? 0 : Math.round((completed / total) * 1000) / 10,
        milestoneRate: milestones.length === 0 ? undefined : Math.round((milestoneCompleted / milestones.length) * 1000) / 1000,
        highRiskCount: highRiskOpen,
        delayDays: totalDelay,
    };
}

function aggregateRisks(rows: FlatProjectNodeRow[]): MajorProjectRisk[] {
    const candidates = rows
        .filter(r => String(r.风险等级 ?? '') === '高' || (r.延期天数 ?? 0) > 0)
        .slice()
        .sort((a, b) => (b.延期天数 ?? 0) - (a.延期天数 ?? 0))
        .slice(0, 8);
    return candidates.map(r => {
        const level: MajorProjectRisk['level'] = String(r.风险等级 ?? '') === '高' ? 'high' : 'warn';
        const days = r.延期天数 ?? 0;
        const desc = String(r.风险内容 ?? r.延期影响 ?? r.任务 ?? '').slice(0, 24);
        const suffix = days > 0 ? ` (${days}d)` : '';
        return {
            level,
            label: `${desc}${suffix}`,
            taskRef: String(r.任务 ?? ''),
        };
    });
}

export function aggregateMajorProjects(rows: FlatProjectNodeRow[]): MajorProject[] {
    const byProject = new Map<string, FlatProjectNodeRow[]>();
    for (const r of rows) {
        const key = String(r.重大项目 ?? '').trim();
        if (!key) continue;
        const list = byProject.get(key);
        if (list) {
            list.push(r);
        } else {
            byProject.set(key, [r]);
        }
    }
    const result: MajorProject[] = [];
    for (const [projectName, projectRows] of byProject.entries()) {
        const bySub = new Map<string, FlatProjectNodeRow[]>();
        for (const r of projectRows) {
            const subKey = String(r.子项目 ?? '(无子项目)').trim() || '(无子项目)';
            const list = bySub.get(subKey);
            if (list) {
                list.push(r);
            } else {
                bySub.set(subKey, [r]);
            }
        }
        const subprojects: SubProject[] = [];
        for (const [subName, subRows] of bySub.entries()) {
            subprojects.push({ name: subName, tasks: subRows.map(rowToTask) });
        }
        result.push({
            name: projectName,
            ...aggregateMeta(projectRows),
            kpi: aggregateKpi(projectRows),
            risks: aggregateRisks(projectRows),
            subprojects,
        });
    }
    return result;
}
