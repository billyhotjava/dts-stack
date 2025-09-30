export interface AdminSessionState {
	allowed: boolean;
	role: "SYSADMIN" | "AUTHADMIN" | "AUDITADMIN";
	username: string;
	email: string;
}

let activeAdmin: AdminSessionState = {
	allowed: true,
	role: "SYSADMIN",
	username: "sysadmin",
	email: "sysadmin@example.com",
};

export const setActiveAdmin = (session: AdminSessionState) => {
	activeAdmin = session;
};

export const getActiveAdmin = () => activeAdmin;

export const resetActiveAdmin = () => {
	activeAdmin = {
		allowed: true,
		role: "SYSADMIN",
		username: "sysadmin",
		email: "sysadmin@example.com",
	};
};
