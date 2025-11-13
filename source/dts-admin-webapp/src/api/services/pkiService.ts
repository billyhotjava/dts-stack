import apiClient from "../apiClient";

export type PkiChallenge = {
	challengeId: string;
	nonce: string;
	aud: string;
	ts: number;
	exp: number;
};

export async function getPkiChallenge(): Promise<PkiChallenge> {
	const data = await apiClient.get<PkiChallenge>({ url: "/keycloak/auth/pki-challenge" });
	return data as PkiChallenge;
}

export type PkiLoginPayload = {
  // Preferred modern shape (matches backend validation)
  challengeId: string;
  nonce?: string;
  originDataB64?: string;
  signDataB64?: string;
  certContentB64?: string;
  signType?: string;
  dupCertB64?: string;
  mode?: "agent" | "gateway";
  username?: string;
  // Legacy fallback fields supported by backend
  plain?: string;
  p7Signature?: string;
  certB64?: string;
};

export async function pkiLogin(payload: PkiLoginPayload): Promise<any> {
	// Admin console PKI login: explicitly mark audience=admin so backend应用管理员角色校验
	const data = await apiClient.post<any>({
		url: "/keycloak/auth/pki-login",
		data: payload,
		headers: { "X-Login-Audience": "admin" },
	});
	return data;
}

// Optional helper to attempt local agent signing. Not wired by default.
export async function trySignWithLocalAgent(plain: string): Promise<{ p7: string; cert?: string } | null> {
	const endpoints = ["https://127.0.0.1:18080", "http://127.0.0.1:18080"];
	for (const base of endpoints) {
		try {
			const url = `${base}/api/sign`; // placeholder; adjust to actual agent API
			const resp = await fetch(url, {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ plain }),
			});
			if (resp.ok) {
				const data = (await resp.json()) as any;
				const p7 = String(data?.signature || data?.p7 || "").trim();
				const cert = String(data?.cert || data?.certB64 || "").trim();
				if (p7) return { p7, cert };
			}
		} catch (_e) {
			// continue to next endpoint
		}
	}
	return null;
}
