import { useMemo } from "react";
import { useUserRoles } from "@/store/userStore";

const ARCHITECTURE_WRITER_ROLES = new Set(["ADMIN", "OP_ADMIN", "INST_DATA_OWNER"]);

const normalizedRole = (value: unknown) =>
	String(value || "")
		.trim()
		.toUpperCase()
		.replace(/^ROLE_/, "");

export const hasArchitectureDictionaryWriteAccess = (roles: unknown[]) =>
	roles.some((role) => ARCHITECTURE_WRITER_ROLES.has(normalizedRole(role)));

export function useArchitectureDictionaryWriteAccess() {
	const roles = useUserRoles();
	return useMemo(() => hasArchitectureDictionaryWriteAccess(roles || []), [roles]);
}
