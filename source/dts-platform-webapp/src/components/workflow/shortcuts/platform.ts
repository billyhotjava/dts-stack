export function isEditableTarget(target: EventTarget | null): boolean {
	if (!(target instanceof HTMLElement)) return false;
	const tagName = target.tagName.toLowerCase();
	return tagName === "input" || tagName === "textarea" || tagName === "select" || target.isContentEditable;
}

export function isCommandKey(event: KeyboardEvent): boolean {
	return event.ctrlKey || event.metaKey;
}
