import type { UserInfo, UserToken } from "#/entity";
import apiClient from "../apiClient";

export interface SignInReq {
	username: string;
	password: string;
}

export interface SignUpReq extends SignInReq {
	email: string;
}
export type SignInRes = UserToken & { user: UserInfo };

export enum UserApi {
	SignIn = "/keycloak/auth/login",
	SignUp = "/auth/signup",
	Logout = "/keycloak/auth/logout",
	Refresh = "/keycloak/auth/refresh",
	User = "/user",
}

const signin = (data: SignInReq) => apiClient.post<SignInRes>({ url: UserApi.SignIn, data });
const signup = (data: SignUpReq) => apiClient.post<SignInRes>({ url: UserApi.SignUp, data });
const logout = (refreshToken: string) => apiClient.post({ url: UserApi.Logout, data: { refreshToken } });
const refresh = (refreshToken: string) => apiClient.post({ url: UserApi.Refresh, data: { refreshToken } });
const findById = (id: string) => apiClient.get<UserInfo[]>({ url: `${UserApi.User}/${id}` });

export default {
	signin,
	signup,
	findById,
	logout,
	refresh,
};
