// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

type FakeAxiosInstance = {
	interceptors: {
		request: { use: (fulfilled: (config: any) => any, rejected?: (error: unknown) => unknown) => number };
		response: { use: (fulfilled: (response: any) => any, rejected?: (error: unknown) => unknown) => number };
	};
	request: (config: any) => Promise<any>;
};

let requestFulfilled: ((config: any) => any) | undefined;
let responseFulfilled: ((response: any) => any) | undefined;
let responseRejected: ((error: any) => any) | undefined;
let nextResponseFactory:
	| ((config: any) => { status: number; data: any; config: any })
	| null = null;
let nextErrorFactory:
	| ((config: any) => { response: any; config: any; message: string })
	| null = null;

const fakeAxiosInstance: FakeAxiosInstance = {
	interceptors: {
		request: {
			use: (fulfilled) => {
				requestFulfilled = fulfilled;
				return 0;
			},
		},
		response: {
			use: (fulfilled, rejected) => {
				responseFulfilled = fulfilled;
				responseRejected = rejected;
				return 0;
			},
		},
	},
	request: async (config) => {
		const finalConfig = requestFulfilled ? await requestFulfilled(config) : config;
		if (nextErrorFactory) {
			const error = nextErrorFactory(finalConfig);
			if (responseRejected) {
				return responseRejected(error);
			}
			throw error;
		}
		const response = nextResponseFactory
			? nextResponseFactory(finalConfig)
			: {
					status: 200,
					data: { status: 200, data: { ok: true }, message: "ok" },
					config: finalConfig,
				};
		return responseFulfilled ? responseFulfilled(response) : response;
	},
};

vi.mock("axios", () => ({
	default: {
		create: vi.fn(() => fakeAxiosInstance),
	},
}));

const redirectSpy = vi.fn();
const fetchCurrentSessionSpy = vi.fn();
const clearSessionSpy = vi.fn();
const toastErrorSpy = vi.fn();

vi.mock("sonner", () => ({
	toast: {
		error: (...args: unknown[]) => toastErrorSpy(...args),
	},
}));

vi.mock("@/global-config", () => ({
	GLOBAL_CONFIG: { apiBaseUrl: "/api" },
}));

vi.mock("@/locales/i18n", () => ({
	t: (key: string) => key,
}));

vi.mock("@/auth/session-auth", () => ({
	currentRoutePath: () => "/admin/users",
	redirectToLoginWithReturn: () => redirectSpy(),
	useRedirectIntentStore: {
		getState: () => ({ intent: null }),
	},
	fetchCurrentSession: (...args: unknown[]) => fetchCurrentSessionSpy(...args),
}));

vi.mock("@/store/userStore", () => ({
	default: {
		getState: () => ({
			actions: {
				clearUserInfoAndToken: (...args: unknown[]) => clearSessionSpy(...args),
			},
		}),
	},
}));

const { default: apiClient } = await import("./apiClient");

describe("admin apiClient 401 handling", () => {
	beforeEach(() => {
		nextResponseFactory = null;
		nextErrorFactory = null;
		localStorage.clear();
		redirectSpy.mockReset();
		fetchCurrentSessionSpy.mockReset();
		clearSessionSpy.mockReset();
		toastErrorSpy.mockReset();
	});

	afterEach(() => {
		vi.clearAllMocks();
	});

	it("does not clear session when a stale 401 races after a valid session probe", async () => {
		fetchCurrentSessionSpy.mockResolvedValue({
			authenticated: true,
			username: "sysadmin",
			roles: ["ROLE_SYS_ADMIN"],
			permissions: ["admin.portal"],
		});
		nextErrorFactory = (config) => ({
			response: {
				status: 401,
				data: { message: "unauthorized" },
				headers: {},
				config,
			},
			config,
			message: "Request failed with status code 401",
		});

		await expect(apiClient.get({ url: "/admin/users" })).rejects.toBeDefined();

		expect(fetchCurrentSessionSpy).toHaveBeenCalledTimes(1);
		expect(clearSessionSpy).not.toHaveBeenCalled();
		expect(redirectSpy).not.toHaveBeenCalled();
	});

	it("clears session when the session probe confirms logout", async () => {
		fetchCurrentSessionSpy.mockResolvedValue({ authenticated: false });
		nextErrorFactory = (config) => ({
			response: {
				status: 401,
				data: { message: "unauthorized" },
				headers: {},
				config,
			},
			config,
			message: "Request failed with status code 401",
		});

		await expect(apiClient.get({ url: "/admin/users" })).rejects.toBeDefined();

		expect(fetchCurrentSessionSpy).toHaveBeenCalledTimes(1);
		expect(clearSessionSpy).toHaveBeenCalledWith("expired");
		expect(redirectSpy).toHaveBeenCalledTimes(1);
	});
});
