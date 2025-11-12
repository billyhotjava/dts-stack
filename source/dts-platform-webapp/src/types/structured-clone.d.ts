declare module "@ungap/structured-clone" {
	export default function structuredClonePolyfill<T>(
		value: T,
		options?: { transfer?: any[]; json?: boolean; lossy?: boolean },
	): T;
}
