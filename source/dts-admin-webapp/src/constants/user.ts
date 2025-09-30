export const PERSONNEL_SECURITY_LEVEL_OPTIONS = ["非密", "一般", "重要", "核心"] as const;

export const DEPARTMENT_SUGGESTIONS = ["研究所", "财务", "二级部门A"] as const;

export const POSITION_SUGGESTIONS = ["所长", "副所长", "财务主管", "部门领导", "业务骨干"] as const;

export const CUSTOM_USER_ATTRIBUTE_KEYS = ["personnel_security_level", "department", "position"] as const;

export const CUSTOM_USER_ATTRIBUTE_KEY_SET = new Set<string>(CUSTOM_USER_ATTRIBUTE_KEYS);

export type CustomUserAttributeKey = (typeof CUSTOM_USER_ATTRIBUTE_KEYS)[number];
