export type OwnerDirectoryUser = { username: string; displayName?: string; deptCode?: string; deptName?: string };
export type OwnerDirectoryDept = { code: string; nameZh?: string };
export type SelectOption = { value: string; label: string };

const keepCurrent = (options: SelectOption[], current?: string | null): SelectOption[] => {
	const value = current?.trim();
	return value && !options.some((option) => option.value === value) ? [{ value, label: value }, ...options] : options;
};

export function deptSelectOptions(
	depts: OwnerDirectoryDept[],
	current?: string | null,
	users: OwnerDirectoryUser[] = [],
): SelectOption[] {
	const known = new Map(depts.map((dept) => [dept.code, dept]));
	for (const user of users) {
		const code = user.deptCode?.trim();
		const name = user.deptName?.trim();
		if (code && name && !known.get(code)?.nameZh) known.set(code, { code, nameZh: name });
	}
	const options = [...known.values()]
		.filter((dept) => dept.code?.trim())
		.map((dept) => ({ value: dept.code, label: dept.nameZh ? `${dept.nameZh}（${dept.code}）` : dept.code }));
	return keepCurrent(options, current);
}

/** People in the chosen department (or everyone when none is chosen), always keeping the saved owner visible. */
export function ownerSelectOptions(
	users: OwnerDirectoryUser[],
	deptCode?: string | null,
	current?: string | null,
): SelectOption[] {
	const dept = deptCode?.trim();
	const options = users
		.filter((user) => !dept || user.deptCode?.trim() === dept)
		.map((user) => ({
			value: user.username,
			label:
				user.displayName && user.displayName !== user.username
					? `${user.displayName}（${user.username}）`
					: user.username,
		}));
	return keepCurrent(options, current);
}

/** Owner defaults to the signed-in user, department to that user's department, only where the asset has none. */
export function defaultOwnerFields(
	dataset: { owner?: string | null; ownerDept?: string | null },
	user: { username?: string | null; deptCode?: string | null },
): { businessOwner?: string; ownerDept?: string } {
	return {
		businessOwner: dataset.owner?.trim() || user.username?.trim() || undefined,
		ownerDept: dataset.ownerDept?.trim() || user.deptCode?.trim() || undefined,
	};
}

/** Department implied by picking an owner; undefined keeps the current department. */
export function deptForOwner(users: OwnerDirectoryUser[], username?: string | null): string | undefined {
	return users.find((user) => user.username === username)?.deptCode?.trim() || undefined;
}

/** Whether a newly chosen department contradicts the owner's known department. */
export function ownerLeavesDept(
	users: OwnerDirectoryUser[],
	username?: string | null,
	deptCode?: string | null,
): boolean {
	const ownerDept = deptForOwner(users, username);
	return Boolean(username && deptCode && ownerDept !== deptCode.trim());
}
