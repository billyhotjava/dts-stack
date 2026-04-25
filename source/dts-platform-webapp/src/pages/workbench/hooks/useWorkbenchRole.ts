import { useMemo } from "react";
import { useUserInfo, useUserRoles } from "@/store/userStore";

const INST_LEADER_CODES = new Set(["ROLE_INST_LEADER", "INST_LEADER"]);
const DEPT_LEADER_CODES = new Set([
	"ROLE_DEPT_LEADER",
	"DEPT_LEADER",
	"ROLE_SUB_INST_LEADER",
	"SUB_INST_LEADER",
	"ROLE_FIN_MANAGER",
	"FIN_MANAGER",
]);

export type WorkbenchRole = "EMP" | "DEPT_LEADER" | "INST_LEADER";

export interface WorkbenchRoleInfo {
	role: WorkbenchRole;
	deptCode: string | null;
	isInstLeader: boolean;
	isDeptLeader: boolean;
	isEmp: boolean;
	roleLabel: string;
}

function resolveRole(roles: readonly string[]): WorkbenchRole {
	const set = new Set(roles.map((r) => String(r ?? "").toUpperCase()));
	for (const code of INST_LEADER_CODES) if (set.has(code)) return "INST_LEADER";
	for (const code of DEPT_LEADER_CODES) if (set.has(code)) return "DEPT_LEADER";
	return "EMP";
}

function resolveDeptCode(userInfo: unknown): string | null {
	if (!userInfo || typeof userInfo !== "object") return null;
	const info = userInfo as Record<string, unknown>;
	if (typeof info.deptCode === "string" && info.deptCode.trim()) {
		return info.deptCode.trim();
	}
	const attrs = info.attributes;
	if (attrs && typeof attrs === "object") {
		const attrMap = attrs as Record<string, unknown>;
		const dept = attrMap.department ?? attrMap.dept_code;
		if (Array.isArray(dept)) {
			const first = dept[0];
			if (typeof first === "string" && first.trim()) return first.trim();
		}
		if (typeof dept === "string" && dept.trim()) return dept.trim();
	}
	return null;
}

export function useWorkbenchRole(): WorkbenchRoleInfo {
	const userInfo = useUserInfo();
	const rawRoles = useUserRoles();

	return useMemo(() => {
		const roles = Array.isArray(rawRoles) ? (rawRoles as string[]) : [];
		const role = resolveRole(roles);
		const deptCode = resolveDeptCode(userInfo);
		return {
			role,
			deptCode,
			isInstLeader: role === "INST_LEADER",
			isDeptLeader: role === "DEPT_LEADER",
			isEmp: role === "EMP",
			roleLabel: role === "INST_LEADER" ? "所领导" : role === "DEPT_LEADER" ? "部门领导" : "员工",
		};
	}, [userInfo, rawRoles]);
}
