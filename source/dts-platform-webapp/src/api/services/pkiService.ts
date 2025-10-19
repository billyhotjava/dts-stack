import apiClient from "../apiClient";
import { GLOBAL_CONFIG } from "@/global-config";

export type PkiChallenge = {
  challengeId: string;
  nonce: string;
  aud: string;
  ts: number;
  exp: number;
};

export async function getPkiChallenge(): Promise<PkiChallenge> {
  const data = await apiClient.get<PkiChallenge>({
    baseURL: GLOBAL_CONFIG.adminApiBaseUrl,
    url: "/keycloak/auth/pki-challenge",
  });
  return data as PkiChallenge;
}

export type PkiLoginPayload = {
  challengeId: string;
  nonce: string;
  originDataB64: string;
  signDataB64: string;
  certContentB64: string;
  devId: string;
  appName: string;
  conName: string;
  signType?: string;
  dupCertB64?: string;
  mode?: "agent" | "gateway";
  username?: string;
};

export async function pkiLogin(payload: PkiLoginPayload): Promise<any> {
  const data = await apiClient.post<any>({
    baseURL: GLOBAL_CONFIG.adminApiBaseUrl,
    url: "/keycloak/auth/pki-login",
    data: payload,
  });
  return data;
}

// Exchange verified PKI identity for platform portal session tokens.
export async function createPortalSessionFromPki(username: string, user: any): Promise<any> {
  const data = await apiClient.post<any>({
    // Use platform base URL (default apiClient base)
    url: "/keycloak/auth/pki-session",
    data: { username, user },
  });
  return data;
}
