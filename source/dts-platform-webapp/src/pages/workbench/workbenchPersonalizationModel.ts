import type {
	WorkbenchComponentDescriptor,
	WorkbenchPreferenceItem,
} from "@/api/services/workbenchService";

export function normalizeWorkbenchPreferenceItems(
	items: WorkbenchPreferenceItem[] | undefined,
	availableComponents: WorkbenchComponentDescriptor[],
): WorkbenchPreferenceItem[] {
	const byKey = new Map((items ?? []).map((item) => [item.key, item]));
	const orderByKey = new Map(availableComponents.map((component, index) => [component.key, index]));
	const defaultVisible = !items || items.length === 0;
	const maxSavedOrder = Math.max(0, ...(items ?? []).map((item) => item.order));
	return availableComponents
		.map((component, index) => {
			const current = byKey.get(component.key);
			return {
				key: component.key,
				visible: current?.visible ?? defaultVisible,
				order: current?.order ?? (defaultVisible ? (index + 1) * 10 : maxSavedOrder + (index + 1) * 10),
			};
		})
		.sort(
			(a, b) =>
				a.order - b.order ||
				(orderByKey.get(a.key) ?? Number.MAX_SAFE_INTEGER) -
					(orderByKey.get(b.key) ?? Number.MAX_SAFE_INTEGER),
		)
		.map((item, index) => ({ ...item, order: (index + 1) * 10 }));
}

export function moveWorkbenchPreferenceItem(
	items: WorkbenchPreferenceItem[],
	key: string,
	offset: -1 | 1,
): WorkbenchPreferenceItem[] {
	const index = items.findIndex((item) => item.key === key);
	const target = index + offset;
	if (index < 0 || target < 0 || target >= items.length) {
		return items;
	}
	const next = [...items];
	const moving = next[index];
	next[index] = next[target];
	next[target] = moving;
	return next.map((item, itemIndex) => ({ ...item, order: (itemIndex + 1) * 10 }));
}
