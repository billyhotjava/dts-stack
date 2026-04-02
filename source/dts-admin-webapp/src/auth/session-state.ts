export type SessionReason =
	| "bootstrapping"
	| "authenticated"
	| "anonymous"
	| "expired"
	| "taken_over"
	| "logged_out";

export type PortalSessionState = {
	initialized: boolean;
	checking: boolean;
	authenticated: boolean;
	reason: SessionReason;
	browserId?: string;
	expiresAt?: string;
};

export type CurrentSessionPayload = {
	authenticated: boolean;
	username?: string;
	displayName?: string;
	browserId?: string;
	roles?: string[];
	permissions?: string[];
	deptCode?: string;
	personnelLevel?: string;
	expiresAt?: string;
};

export function createBootstrappingSessionState(): PortalSessionState {
	return {
		initialized: false,
		checking: true,
		authenticated: false,
		reason: "bootstrapping",
	};
}

export function createAnonymousSessionState(reason: SessionReason = "anonymous"): PortalSessionState {
	return {
		initialized: true,
		checking: false,
		authenticated: false,
		reason,
	};
}

export function createAuthenticatedSessionState(
	overrides: Partial<PortalSessionState> = {},
): PortalSessionState {
	return {
		initialized: true,
		checking: false,
		authenticated: true,
		reason: "authenticated",
		...overrides,
	};
}

export function shouldBlockWhileSessionBootstraps(session: PortalSessionState): boolean {
	return !session.initialized || session.checking;
}

export function canAccessProtectedRoute(session: PortalSessionState): boolean {
	return session.initialized && !session.checking && session.authenticated;
}

export function shouldAutoRedirectFromLogin(session: PortalSessionState): boolean {
	return canAccessProtectedRoute(session);
}
