type CryptoRandomSource = {
	randomUUID?: () => string;
	getRandomValues?: (values: Uint8Array) => Uint8Array;
};

const randomHex = (randomSource: CryptoRandomSource): string => {
	if (typeof randomSource.randomUUID === "function") {
		return randomSource.randomUUID().replaceAll("-", "").toUpperCase();
	}
	if (typeof randomSource.getRandomValues !== "function") {
		throw new Error("A cryptographic random source is required to create a dimension code");
	}
	return Array.from(randomSource.getRandomValues(new Uint8Array(16)), (byte) => byte.toString(16).padStart(2, "0"))
		.join("")
		.toUpperCase();
};

export function createDimensionSystemCode(
	randomSource: CryptoRandomSource | undefined = globalThis.crypto as CryptoRandomSource | undefined,
): string {
	if (!randomSource) throw new Error("A cryptographic random source is required to create a dimension code");
	return `DIM_${randomHex(randomSource).slice(0, 32)}`;
}

export function nextDimensionHierarchyCode(hierarchies: readonly { code?: string | null }[]): string {
	let highest = 0;
	for (const hierarchy of hierarchies) {
		const match = /^HIERARCHY_(\d+)$/.exec(hierarchy.code?.trim().toUpperCase() || "");
		if (match) highest = Math.max(highest, Number(match[1]));
	}
	return `HIERARCHY_${highest + 1}`;
}
