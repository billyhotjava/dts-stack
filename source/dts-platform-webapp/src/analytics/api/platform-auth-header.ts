export function withPlatformAuthorization(headersInit?: HeadersInit, accessToken?: string): Headers {
	const headers = new Headers(headersInit ?? {});
	if (headers.has("authorization")) {
		return headers;
	}
	const token = String(accessToken ?? "").trim();
	if (!token) {
		return headers;
	}
	headers.set("authorization", `Bearer ${token}`);
	return headers;
}
