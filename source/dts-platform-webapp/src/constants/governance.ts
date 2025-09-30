export const PERSON_SECURITY_LEVELS = [
	{ value: "NON_SECRET", label: "非密" },
	{ value: "GENERAL", label: "一般" },
	{ value: "IMPORTANT", label: "重要" },
	{ value: "CORE", label: "核心" },
];

export const DATA_LEVELS_BY_PERSON_LEVEL: Record<string, string[]> = {
	NON_SECRET: ["PUBLIC"],
	GENERAL: ["PUBLIC", "INTERNAL"],
	IMPORTANT: ["PUBLIC", "INTERNAL", "SECRET"],
	CORE: ["PUBLIC", "INTERNAL", "SECRET", "TOP_SECRET"],
};

export const GOVERNANCE_ROLE_NAMES = ["ROLE_SYS_ADMIN", "ROLE_AUTH_ADMIN", "ROLE_AUDITOR_ADMIN"] as const;
export const APPLICATION_ROLE_NAMES = ["ROLE_OP_ADMIN"] as const;
export const DATA_ROLE_NAMES = ["DATA_PUBLIC", "DATA_INTERNAL", "DATA_SECRET", "DATA_TOP_SECRET"] as const;

export function deriveDataLevels(level?: string | null): string[] {
	if (!level) return [];
	return DATA_LEVELS_BY_PERSON_LEVEL[level] ?? [];
}

export function isGovernanceRole(roleName: string): boolean {
	return GOVERNANCE_ROLE_NAMES.includes(roleName as (typeof GOVERNANCE_ROLE_NAMES)[number]);
}

export function isApplicationAdminRole(roleName: string): boolean {
	return APPLICATION_ROLE_NAMES.includes(roleName as (typeof APPLICATION_ROLE_NAMES)[number]);
}

export function isDataRole(roleName: string): boolean {
	return DATA_ROLE_NAMES.includes(roleName as (typeof DATA_ROLE_NAMES)[number]);
}
