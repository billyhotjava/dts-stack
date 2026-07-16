// 业务对象编码由系统生成：手工编码会带来重复、空格、大小写漂移等治理问题。
// 格式：BO{YYYYMMDD}{三位序号}，同日内按已有编码顺延，冲突自动跳号。
const CODE_PREFIX = "BO";

const formatDatePart = (now: Date): string => {
	const year = now.getFullYear();
	const month = String(now.getMonth() + 1).padStart(2, "0");
	const day = String(now.getDate()).padStart(2, "0");
	return `${year}${month}${day}`;
};

export const generateBusinessObjectCode = (existingCodes: Iterable<string>, now: Date = new Date()): string => {
	const taken = new Set<string>();
	for (const code of existingCodes) {
		if (code) taken.add(code.trim().toUpperCase());
	}
	const datePart = formatDatePart(now);
	for (let seq = 1; seq <= 999; seq += 1) {
		const candidate = `${CODE_PREFIX}${datePart}${String(seq).padStart(3, "0")}`;
		if (!taken.has(candidate)) return candidate;
	}
	// 单日超过 999 个对象的极端情况：退化为时间戳后缀，保证仍然唯一。
	return `${CODE_PREFIX}${datePart}${Date.now().toString(36).toUpperCase().slice(-4)}`;
};
