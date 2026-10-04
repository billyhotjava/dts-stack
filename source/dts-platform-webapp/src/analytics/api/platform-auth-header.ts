export function withPlatformAuthorization(headersInit?: HeadersInit): Headers {
	const headers = new Headers(headersInit ?? {});
	return headers;
}
