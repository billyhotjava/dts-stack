import { createContext, useContext } from "react";
import type { AdminRole } from "@/admin/types";

export interface AdminSessionState {
	role: AdminRole;
	username?: string;
	email?: string;
}

export const AdminSessionContext = createContext<AdminSessionState | null>(null);

export function useAdminSession() {
	const ctx = useContext(AdminSessionContext);
	if (!ctx) {
		throw new Error("AdminSessionContext is not available. Wrap your component with <AdminGuard />");
	}
	return ctx;
}
