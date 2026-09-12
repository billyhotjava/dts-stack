import { useQuery } from "@tanstack/react-query";
import { getModelAccess, getModelingAuthorization, selectModelingDepartment } from "@/api/modelingAccessApi";
import { useUserInfo } from "@/store/userStore";
export function useModelingAuthorization() {
	const user = useUserInfo();
	return useQuery({
		queryKey: ["modeling-authorization", user?.id],
		queryFn: async () => {
			const current = await getModelingAuthorization();
			if (!current.canSelectDepartment) selectModelingDepartment(current.departmentCode);
			return current;
		},
		retry: false,
		staleTime: 0,
		refetchOnWindowFocus: true,
		refetchInterval: 30000,
	});
}
export function useModelAccess(ids: string[]) {
	const user = useUserInfo();
	return useQuery({
		queryKey: ["modeling-access", user?.id, Array.from(new Set(ids)).sort().join(",")],
		queryFn: () => getModelAccess(ids),
		retry: false,
		staleTime: 0,
		refetchOnWindowFocus: true,
		refetchInterval: 30000,
	});
}
