const legacyCopy = (text: string): boolean => {
	if (typeof document === "undefined" || !document.body) return false;

	const textarea = document.createElement("textarea");
	textarea.value = text;
	textarea.setAttribute("readonly", "true");
	textarea.style.position = "fixed";
	textarea.style.left = "-9999px";
	textarea.style.top = "-9999px";
	document.body.appendChild(textarea);

	const selection = document.getSelection();
	const originalRange =
		selection && selection.rangeCount > 0 ? selection.getRangeAt(0) : null;

	textarea.focus();
	textarea.select();
	textarea.setSelectionRange(0, textarea.value.length);

	let copied = false;
	try {
		copied = document.execCommand("copy");
	} catch {
		copied = false;
	}

	document.body.removeChild(textarea);

	if (selection && originalRange) {
		selection.removeAllRanges();
		selection.addRange(originalRange);
	}

	return copied;
};

export const writeTextToClipboard = async (text: string): Promise<boolean> => {
	if (typeof navigator !== "undefined" && navigator.clipboard?.writeText) {
		try {
			await navigator.clipboard.writeText(text);
			return true;
		} catch {
			// Fallback to execCommand copy for insecure origins / restricted contexts.
		}
	}
	return legacyCopy(text);
};
