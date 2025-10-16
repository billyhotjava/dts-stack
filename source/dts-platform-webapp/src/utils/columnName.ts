export const normalizeColumnKey = (input: string): string => {
	if (typeof input !== "string") {
		return "";
	}
	let value = input.trim();
	if (!value) {
		return "";
	}

	// If expression contains " AS alias", prefer alias part.
	const lower = value.toLowerCase();
	const asIndex = lower.lastIndexOf(" as ");
	if (asIndex >= 0) {
		value = value.slice(asIndex + 4);
	}

	// Remove quoting characters and backticks that often wrap identifiers.
	value = value.replace(/[`"']/g, "");

	// When column uses table prefix, keep the trailing identifier.
	const dotIndex = value.lastIndexOf(".");
	if (dotIndex >= 0) {
		value = value.slice(dotIndex + 1);
	}

	return value.trim().toLowerCase();
};
