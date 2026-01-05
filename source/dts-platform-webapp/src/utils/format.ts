const normalizeDateInput = (value: unknown): Date | null => {
	if (value instanceof Date) {
		return Number.isNaN(value.getTime()) ? null : value;
	}

	if (typeof value === "number") {
		const date = new Date(value);
		return Number.isNaN(date.getTime()) ? null : date;
	}

	if (typeof value === "string") {
		const trimmed = value.trim();
		if (!trimmed) return null;
		const date = new Date(trimmed);
		return Number.isNaN(date.getTime()) ? null : date;
	}

	return null;
};

export const formatDateTime = (value: unknown): string => {
	const date = normalizeDateInput(value);
	if (!date) return "";

	try {
		return new Intl.DateTimeFormat("zh-CN", {
			year: "numeric",
			month: "2-digit",
			day: "2-digit",
			hour: "2-digit",
			minute: "2-digit",
			second: "2-digit",
			hour12: false,
		}).format(date);
	} catch {
		return date.toISOString().replace("T", " ").slice(0, 19);
	}
};

