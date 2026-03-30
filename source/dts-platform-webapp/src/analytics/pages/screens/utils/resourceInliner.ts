/**
 * Scans a screen spec object for internal image URLs and converts them to
 * inline Base64 data URIs. External URLs and already-inlined data: URIs are skipped.
 */

const IMAGE_URL_PATTERN = /\.(png|jpe?g|gif|svg|webp)(\?.*)?$/i;
const INTERNAL_URL_PATTERN = /^(\/analytics\/|\/api\/|\/?uploads\/)/;

function isInternalImageUrl(value: unknown): value is string {
	if (typeof value !== 'string' || !value) return false;
	if (value.startsWith('data:')) return false;
	if (!INTERNAL_URL_PATTERN.test(value) && !IMAGE_URL_PATTERN.test(value)) return false;
	if (value.startsWith('http://') || value.startsWith('https://')) return false;
	return true;
}

async function fetchAsDataUrl(url: string): Promise<string> {
	const response = await fetch(url);
	if (!response.ok) throw new Error(`Failed to fetch ${url}: ${response.status}`);
	const blob = await response.blob();
	return new Promise<string>((resolve, reject) => {
		const reader = new FileReader();
		reader.onloadend = () => resolve(reader.result as string);
		reader.onerror = () => reject(new Error(`Failed to read blob for ${url}`));
		reader.readAsDataURL(blob);
	});
}

export async function inlineResources(spec: Record<string, unknown>): Promise<{
	spec: Record<string, unknown>;
	inlinedCount: number;
	errors: string[];
}> {
	const result = JSON.parse(JSON.stringify(spec)) as Record<string, unknown>;
	const errors: string[] = [];
	let inlinedCount = 0;

	const urlEntries: Array<{ obj: Record<string, unknown>; key: string; url: string }> = [];

	function scan(obj: unknown): void {
		if (!obj || typeof obj !== 'object') return;
		if (Array.isArray(obj)) {
			for (const item of obj) scan(item);
			return;
		}
		const record = obj as Record<string, unknown>;
		for (const key of Object.keys(record)) {
			const value = record[key];
			if (isInternalImageUrl(value)) {
				urlEntries.push({ obj: record, key, url: value });
			} else if (value && typeof value === 'object') {
				scan(value);
			}
		}
	}

	scan(result);

	for (const entry of urlEntries) {
		try {
			const dataUrl = await fetchAsDataUrl(entry.url);
			entry.obj[entry.key] = dataUrl;
			inlinedCount++;
		} catch (e) {
			const msg = e instanceof Error ? e.message : String(e);
			errors.push(`${entry.url}: ${msg}`);
			console.warn(`[resourceInliner] Failed to inline ${entry.url}:`, e);
		}
	}

	return { spec: result, inlinedCount, errors };
}
