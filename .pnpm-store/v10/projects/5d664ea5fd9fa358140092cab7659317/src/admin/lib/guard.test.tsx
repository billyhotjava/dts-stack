// @vitest-environment jsdom
import { act } from "react";
import ReactDOM from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const state = vi.hoisted(() => ({
	queryState: {
		data: undefined as unknown,
		isLoading: false,
		isError: false,
		error: undefined as unknown,
	},
	removeQueries: vi.fn(),
	redirectToLoginWithReturn: vi.fn(),
	signOut: vi.fn(),
	toastError: vi.fn(),
}));

vi.mock("@tanstack/react-query", () => ({
	useQuery: () => state.queryState,
	useQueryClient: () => ({
		removeQueries: (...args: unknown[]) => state.removeQueries(...args),
	}),
}));

vi.mock("sonner", () => ({
	toast: {
		error: (...args: unknown[]) => state.toastError(...args),
	},
}));

vi.mock("@/admin/api/adminApi", () => ({
	adminApi: {
		getWhoami: vi.fn(),
	},
}));

vi.mock("@/store/userStore", () => ({
	usePortalSession: () => ({
		initialized: true,
		checking: false,
		authenticated: true,
		reason: "authenticated",
	}),
	useSignOut: () => state.signOut,
}));

vi.mock("@/auth/session-state", () => ({
	canAccessProtectedRoute: () => true,
	shouldBlockWhileSessionBootstraps: () => false,
}));

vi.mock("@/auth/session-auth", () => ({
	redirectToLoginWithReturn: (...args: unknown[]) => state.redirectToLoginWithReturn(...args),
}));

vi.mock("@/components/loading", () => ({
	LineLoading: () => <div data-testid="guard-loading">loading</div>,
}));

vi.mock("@/admin/views/forbidden", () => ({
	default: () => <div data-testid="guard-forbidden">forbidden</div>,
}));

vi.mock("@/admin/types", () => ({
	normalizeAdminRole: (value: unknown) => value,
}));

vi.mock("@/admin/lib/session-context", async () => {
	const ReactModule = await import("react");
	return {
		AdminSessionContext: ReactModule.createContext(null),
	};
});

describe("AdminGuard", () => {
	let container: HTMLDivElement;
	let root: ReactDOM.Root;

	beforeEach(() => {
		vi.resetModules();
		(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
		container = document.createElement("div");
		document.body.appendChild(container);
		root = ReactDOM.createRoot(container);
		state.queryState = {
			data: undefined,
			isLoading: false,
			isError: false,
			error: undefined,
		};
		state.removeQueries.mockReset();
		state.redirectToLoginWithReturn.mockReset();
		state.signOut.mockReset();
		state.toastError.mockReset();
	});

	afterEach(async () => {
		await act(async () => {
			root.unmount();
		});
		container.remove();
	});

	it("shows a loading fallback instead of a blank screen when whoami fails with a non-axios error", async () => {
		state.queryState = {
			data: undefined,
			isLoading: false,
			isError: true,
			error: new Error("whoami transport failed"),
		};

		const { default: AdminGuard } = await import("./guard");

		await act(async () => {
			root.render(
				<AdminGuard>
					<div data-testid="protected">protected</div>
				</AdminGuard>,
			);
		});

		expect(container.querySelector('[data-testid="guard-loading"]')).not.toBeNull();
		expect(container.textContent).toContain("loading");
		expect(state.redirectToLoginWithReturn).not.toHaveBeenCalled();
		expect(state.signOut).not.toHaveBeenCalled();
	});
});
