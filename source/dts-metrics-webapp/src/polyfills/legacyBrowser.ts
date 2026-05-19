const globalScope: typeof globalThis = (() => {
	if (typeof globalThis !== "undefined") return globalThis;
	return Function("return this")() as typeof globalThis;
})();

if (typeof globalScope.structuredClone !== "function") {
	globalScope.structuredClone = (<T>(value: T): T => JSON.parse(JSON.stringify(value))) as typeof structuredClone;
}

export {};
