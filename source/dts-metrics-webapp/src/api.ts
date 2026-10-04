export async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
	const response = await fetch(url, { credentials: "same-origin", ...options });
	const text = await response.text();
	if (!response.ok) {
		throw new Error(compactError(text) || `${response.status} ${response.statusText}`);
	}
	if (!text) return {} as T;
	try {
		return JSON.parse(text) as T;
	} catch (error) {
		const message = error instanceof Error ? error.message : String(error);
		throw new Error(`响应不是合法 JSON: ${compactError(text) || message}`);
	}
}

export function postYaml<T>(url: string, body: string): Promise<T> {
	return fetchJson<T>(url, {
		method: "POST",
		headers: { "Content-Type": "text/yaml" },
		body,
	});
}

function compactError(text: string): string {
	return String(text || "")
		.replace(/\s+/g, " ")
		.trim()
		.slice(0, 240);
}
