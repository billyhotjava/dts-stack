export type ProjectCockpitTheme = 'overview' | 'execution' | 'risk' | 'tree' | 'support';

export type ProjectCockpitQueryState = {
    theme: ProjectCockpitTheme;
    programId: string;
    majorProjectId: string;
    dateFrom: string;
    dateTo: string;
    deptId: string;
    riskLevel: string;
};

export const DEFAULT_PROJECT_COCKPIT_THEME: ProjectCockpitTheme = 'overview';

const VALID_THEMES = new Set<ProjectCockpitTheme>(['overview', 'execution', 'risk', 'tree', 'support']);

export function parseProjectCockpitTheme(value: string | null | undefined): ProjectCockpitTheme {
    return value && VALID_THEMES.has(value as ProjectCockpitTheme)
        ? (value as ProjectCockpitTheme)
        : DEFAULT_PROJECT_COCKPIT_THEME;
}

function todayIso(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const DEFAULT_DATE_FROM = '2026-01-01';

export function parseProjectCockpitQueryState(params: URLSearchParams): ProjectCockpitQueryState {
    return {
        theme: parseProjectCockpitTheme(params.get('theme')),
        programId: params.get('programId') ?? '',
        majorProjectId: params.get('majorProjectId') ?? '',
        dateFrom: params.get('dateFrom') ?? DEFAULT_DATE_FROM,
        dateTo: params.get('dateTo') ?? todayIso(),
        deptId: params.get('deptId') ?? '',
        riskLevel: params.get('riskLevel') ?? '',
    };
}

export function serializeProjectCockpitQueryState(state: ProjectCockpitQueryState): URLSearchParams {
    const params = new URLSearchParams();
    params.set('theme', parseProjectCockpitTheme(state.theme));

    if (state.programId) params.set('programId', state.programId);
    if (state.majorProjectId) params.set('majorProjectId', state.majorProjectId);
    if (state.dateFrom) params.set('dateFrom', state.dateFrom);
    if (state.dateTo) params.set('dateTo', state.dateTo);
    if (state.deptId) params.set('deptId', state.deptId);
    if (state.riskLevel) params.set('riskLevel', state.riskLevel);

    return params;
}
