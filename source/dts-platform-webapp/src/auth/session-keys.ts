import { createSessionStorageKeys } from "@dts-session-core/storage";

export const PLATFORM_SESSION_KEYS = createSessionStorageKeys("platform");

export const PLATFORM_LEGACY_USER_STORE_KEYS = ["userStore"];

export const PLATFORM_LEGACY_SESSION_KEYS = {
	sessionId: ["dts.session.id"],
	sessionUser: ["dts.session.user"],
	logoutTs: ["dts.session.logoutTs"],
	lastActivity: ["dts.session.lastActivity"],
	loginTs: ["dts.session.loginTs"],
};
