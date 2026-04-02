import type { UserInfo } from "#/entity";
import apiClient from "../apiClient";

export interface SignInReq {
	username: string;
	password: string;
}

export interface SignUpReq extends SignInReq {
	email: string;
}

export type SignInRes = {
	user: UserInfo;
	accessToken?: string;
	refreshToken?: string;
	browserId?: string;
	sessionTakeover?: boolean;
	sessionNotice?: string;
};

export enum UserApi {
	SignIn = "/keycloak/auth/login",
	SignUp = "/auth/signup",
	Logout = "/keycloak/auth/logout",
	Refresh = "/keycloak/auth/refresh",
	User = "/user",
}

const signin = (data: SignInReq) => apiClient.post<SignInRes>({ url: UserApi.SignIn, data });
const signup = (data: SignUpReq) => apiClient.post<SignInRes>({ url: UserApi.SignUp, data });
const logout = (refreshToken?: string, username?: string, reason?: string) =>
	apiClient.post({
		url: UserApi.Logout,
		data: {
			...(refreshToken ? { refreshToken } : {}),
			...(username ? { username } : {}),
			...(reason ? { reason } : {}),
		},
	});
const findById = (id: string) => apiClient.get<UserInfo[]>({ url: UserApi.User + "/" + id });

const refresh = (refreshToken: string) => apiClient.post({ url: UserApi.Refresh, data: { refreshToken } });

export default {
	signin,
	signup,
	findById,
	logout,
	refresh,
};
