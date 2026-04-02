// @vitest-environment jsdom
import React, { act } from "react";
import ReactDOM from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const state = vi.hoisted(() => ({
	fetchCurrentSession: vi.fn(async () => ({
		authenticated: true,
		username: "sysadmin",
		roles: ["ROLE_SYS_ADMIN"],
		permissions: ["admin.portal"],
	})),
	redirectToLoginWithReturn: vi.fn(),
	clearUserInfoAndToken: vi.fn(),
	markSessionChecking: vi.fn(),
	setAuthenticatedSession: vi.fn(),
	setSession: vi.fn(),
	logout: vi.fn(async () => undefined),
	toastError: vi.fn(),
	readStorageValue: vi.fn(),
}));

vi.mock("@/auth/session-auth", () => ({
	fetchCurrentSession: (...args: unknown[]) => state.fetchCurrentSession(...args),
	redirectToLoginWithReturn: (...args: unknown[]) => state.redirectToLoginWithReturn(...args),
}));

vi.mock("@/store/userStore", () => ({
	usePortalSession: () => ({
		authenticated: true,
		initialized: true,
		checking: false,
		reason: "authenticated",
	}),
	useUserInfo: () => ({
		username: "sysadmin",
		email: "sysadmin@example.com",
	}),
	useUserActions: () => ({
		clearUserInfoAndToken: (...args: unknown[]) => state.clearUserInfoAndToken(...args),
		markSessionChecking: (...args: unknown[]) => state.markSessionChecking(...args),
		setAuthenticatedSession: (...args: unknown[]) => state.setAuthenticatedSession(...args),
		setSession: (...args: unknown[]) => state.setSession(...args),
	}),
}));

vi.mock("@/api/services/userService", () => ({
	default: {
		logout: (...args: unknown[]) => state.logout(...args),
	},
}));

vi.mock("sonner", () => ({
	toast: {
		error: (...args: unknown[]) => state.toastError(...args),
	},
}));

vi.mock("@dts/session-core/logout-broadcast", () => ({
	parseLogoutBroadcast: () => null,
}));

vi.mock("@dts/session-core/storage", async (importOriginal) => {
	const actual = await importOriginal<typeof import("@dts/session-core/storage")>();
	return {
		...actual,
		readStorageValue: (...args: unknown[]) => state.readStorageValue(...args),
	};
});

describe("admin SessionManager", () => {
	let container: HTMLDivElement;
	let root: ReactDOM.Root;

	beforeEach(() => {
		(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
		vi.useFakeTimers();
		vi.setSystemTime(new Date("2026-04-02T00:00:00.000Z"));
		container = document.createElement("div");
		document.body.appendChild(container);
		root = ReactDOM.createRoot(container);
		localStorage.clear();
		state.fetchCurrentSession.mockClear();
		state.redirectToLoginWithReturn.mockClear();
		state.clearUserInfoAndToken.mockClear();
		state.markSessionChecking.mockClear();
		state.setAuthenticatedSession.mockClear();
		state.setSession.mockClear();
		state.logout.mockClear();
		state.toastError.mockClear();
		state.readStorageValue.mockReset();
		state.readStorageValue.mockReturnValue(String(Date.now()));
	});

	afterEach(async () => {
		await act(async () => {
			root.unmount();
		});
		container.remove();
		vi.useRealTimers();
	});

	it("does not trigger client-side logout when the tab stays idle", async () => {
		const { default: SessionManager } = await import("./session-manager");

		await act(async () => {
			root.render(<SessionManager />);
		});

		await act(async () => {
			vi.advanceTimersByTime(10 * 60 * 1000 + 1000);
			await Promise.resolve();
		});

		expect(state.logout).not.toHaveBeenCalled();
		expect(state.clearUserInfoAndToken).not.toHaveBeenCalledWith("expired");
		expect(state.redirectToLoginWithReturn).not.toHaveBeenCalled();
	});
});
