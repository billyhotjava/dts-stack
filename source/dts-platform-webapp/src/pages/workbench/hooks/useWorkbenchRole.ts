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
	/** Sprint-17 hotfix — human-readable department label; falls back to deptCode in UI. */
	deptName: string | null;
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
	if (typeof info.dept_code === "string" && info.dept_code.trim()) {
		return info.dept_code.trim();
	}
	const attrs = info.attributes;
	if (attrs && typeof attrs === "object") {
		const attrMap = attrs as Record<string, unknown>;
		const dept = attrMap.department ?? attrMap.dept_code ?? attrMap.deptCode;
		if (Array.isArray(dept)) {
			const first = dept[0];
			if (typeof first === "string" && first.trim()) return first.trim();
		}
		if (typeof dept === "string" && dept.trim()) return dept.trim();
	}
	return null;
}

function resolveDeptName(userInfo: unknown): string | null {
	if (!userInfo || typeof userInfo !== "object") return null;
	const info = userInfo as Record<string, unknown>;
	if (typeof info.deptName === "string" && info.deptName.trim()) {
		return info.deptName.trim();
	}
	const direct = info.dept_name ?? info.departmentName ?? info.department_name ?? info.orgName ?? info.org_name;
	if (typeof direct === "string" && direct.trim()) {
		return direct.trim();
	}
	const attrs = info.attributes;
	if (attrs && typeof attrs === "object") {
		const attrMap = attrs as Record<string, unknown>;
		const cand =
			attrMap.dept_name ??
			attrMap.deptName ??
			attrMap.department_name ??
			attrMap.departmentName ??
			attrMap.org_name ??
			attrMap.orgName;
		if (Array.isArray(cand)) {
			const first = cand[0];
			if (typeof first === "string" && first.trim()) return first.trim();
		}
		if (typeof cand === "string" && cand.trim()) return cand.trim();
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
		const deptName = resolveDeptName(userInfo);
		return {
			role,
			deptCode,
			deptName,
			isInstLeader: role === "INST_LEADER",
			isDeptLeader: role === "DEPT_LEADER",
			isEmp: role === "EMP",
			roleLabel: role === "INST_LEADER" ? "所领导" : role === "DEPT_LEADER" ? "部门领导" : "员工",
		};
	}, [userInfo, rawRoles]);
}
