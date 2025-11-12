import structuredClonePolyfill from "@ungap/structured-clone";

const globalScope: typeof globalThis & { msCrypto?: Crypto } = (function resolveGlobal() {
	try {
		if (typeof globalThis !== "undefined") return globalThis;
		// @ts-ignore - Fallbacks for legacy browsers
		if (typeof self !== "undefined") return self;
		// @ts-ignore
		if (typeof window !== "undefined") return window;
		// @ts-ignore
		return Function("return this")();
	} catch {
		// @ts-ignore
		return {};
	}
})();

type StructuredCloneOptions = Parameters<typeof structuredClonePolyfill>[1];

function ensureStructuredClone() {
	if (typeof globalScope.structuredClone === "function") return;
	(globalScope as any).structuredClone = (value: unknown, options?: StructuredCloneOptions) =>
		structuredClonePolyfill(value, options);
}

function ensureUrlCanParse() {
	if (typeof globalScope.URL === "undefined") return;
	const ctor = globalScope.URL as typeof URL & { canParse?: typeof URL.canParse };
	if (typeof ctor.canParse === "function") return;
	ctor.canParse = (input: string | URL, base?: string) => {
		try {
			// eslint-disable-next-line no-new
			new URL(input, base);
			return true;
		} catch {
			return false;
		}
	};
}

function ensureRandomUUID() {
	const cryptoImpl = globalScope.crypto || globalScope.msCrypto;
	if (!cryptoImpl || typeof cryptoImpl.getRandomValues !== "function") return;
	if (typeof cryptoImpl.randomUUID === "function") return;

	const getRandomValues = cryptoImpl.getRandomValues.bind(cryptoImpl);
	const hex: string[] = [];
	for (let i = 0; i < 256; i += 1) {
		hex[i] = (i + 0x100).toString(16).substring(1);
	}

	cryptoImpl.randomUUID = (() => {
		const buffer = new Uint8Array(16);
		return () => {
			getRandomValues(buffer);
			buffer[6] = (buffer[6] & 0x0f) | 0x40;
			buffer[8] = (buffer[8] & 0x3f) | 0x80;
			return `${hex[buffer[0]]}${hex[buffer[1]]}${hex[buffer[2]]}${hex[buffer[3]]}-${hex[buffer[4]]}${hex[buffer[5]]}-${hex[buffer[6]]}${hex[buffer[7]]}-${hex[buffer[8]]}${hex[buffer[9]]}-${hex[buffer[10]]}${hex[buffer[11]]}${hex[buffer[12]]}${hex[buffer[13]]}${hex[buffer[14]]}${hex[buffer[15]]}`;
		};
	})();
}

ensureStructuredClone();
ensureUrlCanParse();
ensureRandomUUID();

export {};
