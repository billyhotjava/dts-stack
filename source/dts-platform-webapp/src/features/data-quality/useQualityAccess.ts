import { useMemo } from "react";
import { useUserRoles } from "@/store/userStore";
import { hasQualityMaintainerRole, hasQualityTaskDeleteRole } from "./qualityAccess";

export const useQualityMaintainerAccess = () => {
	const roles = useUserRoles();
	return useMemo(() => hasQualityMaintainerRole(roles || []), [roles]);
};

export const useQualityTaskDeleteAccess = () => {
	const roles = useUserRoles();
	return useMemo(() => hasQualityTaskDeleteRole(roles || []), [roles]);
};
