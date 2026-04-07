export type ProjectCockpitTheme = 'overview' | 'execution' | 'risk' | 'tree' | 'support';

export type ProjectCockpitQueryState = {
    theme: ProjectCockpitTheme;
    majorProjectId: string;
    dateFrom: string;
    dateTo: string;
    deptId: string;
    riskLevel: string;
};

export type ProjectCockpitPublishedPeriod = {
    periodStart?: string;
    periodEnd?: string;
};

export const DEFAULT_PROJECT_COCKPIT_THEME: ProjectCockpitTheme = 'overview';

const VALID_THEMES = new Set<ProjectCockpitTheme>(['overview', 'execution', 'risk', 'tree', 'support']);

export function parseProjectCockpitTheme(value: string | null | undefined): ProjectCockpitTheme {
    return value && VALID_THEMES.has(value as ProjectCockpitTheme)
        ? (value as ProjectCockpitTheme)
        : DEFAULT_PROJECT_COCKPIT_THEME;
}

export function parseProjectCockpitQueryState(params: URLSearchParams): ProjectCockpitQueryState {
    return {
        theme: parseProjectCockpitTheme(params.get('theme')),
        majorProjectId: params.get('majorProjectId') ?? '',
        dateFrom: params.get('dateFrom') ?? '',
        dateTo: params.get('dateTo') ?? '',
        deptId: params.get('deptId') ?? '',
        riskLevel: params.get('riskLevel') ?? '',
    };
}

export function resolveProjectCockpitEffectiveQueryState(
    state: ProjectCockpitQueryState,
    publishedPeriod?: ProjectCockpitPublishedPeriod | null,
): ProjectCockpitQueryState {
    return {
        ...state,
        dateFrom: state.dateFrom || publishedPeriod?.periodStart || '',
        dateTo: state.dateTo || publishedPeriod?.periodEnd || '',
    };
}

export function createProjectCockpitScopeResetPatch(_state: ProjectCockpitQueryState) {
    return {
        majorProjectId: '',
        deptId: '',
        riskLevel: '',
    } satisfies Pick<ProjectCockpitQueryState, 'majorProjectId' | 'deptId' | 'riskLevel'>;
}

export type ProjectCockpitDrillParams = {
    drillTarget: string;
    drillDept: string;
    drillReason: string;
};

export function parseProjectCockpitDrillParams(params: URLSearchParams): ProjectCockpitDrillParams {
    return {
        drillTarget: params.get('drillTarget') ?? '',
        drillDept: params.get('drillDept') ?? '',
        drillReason: params.get('drillReason') ?? '',
    };
}

export function serializeProjectCockpitQueryState(state: ProjectCockpitQueryState): URLSearchParams {
    const params = new URLSearchParams();
    params.set('theme', parseProjectCockpitTheme(state.theme));

    if (state.majorProjectId) params.set('majorProjectId', state.majorProjectId);
    if (state.dateFrom) params.set('dateFrom', state.dateFrom);
    if (state.dateTo) params.set('dateTo', state.dateTo);
    if (state.deptId) params.set('deptId', state.deptId);
    if (state.riskLevel) params.set('riskLevel', state.riskLevel);

    return params;
}
