/**
 * Scans a spec for inline Base64 data URLs.
 * MVP: does NOT upload/restore — just counts them for the preview modal.
 * Browsers render data: URLs natively, so the spec works as-is.
 */

export function countInlinedResources(spec: unknown): number {
	let count = 0;
	function scan(obj: unknown): void {
		if (!obj || typeof obj !== 'object') return;
		if (Array.isArray(obj)) {
			for (const item of obj) scan(item);
			return;
		}
		const record = obj as Record<string, unknown>;
		for (const key of Object.keys(record)) {
			const value = record[key];
			if (typeof value === 'string' && value.startsWith('data:image/')) {
				count++;
			} else if (value && typeof value === 'object') {
				scan(value);
			}
		}
	}
	scan(spec);
	return count;
}
