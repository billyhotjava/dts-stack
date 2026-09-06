import { useState } from "react";
import { Button, Modal } from "./PrototypePrimitives";

export function ModelUnsavedChangesDialog({
	onSave,
	onDiscard,
	onSaved,
	onStay,
}: {
	onSave: () => Promise<boolean>;
	onDiscard: () => void;
	onSaved: () => void;
	onStay: () => void;
}) {
	const [saving, setSaving] = useState(false);
	const save = async () => {
		if (saving) return;
		setSaving(true);
		try {
			if (await onSave()) onSaved();
		} finally {
			setSaving(false);
		}
	};
	return (
		<Modal
			title="有未保存的修改"
			onClose={() => {
				if (!saving) onStay();
			}}
		>
			<div className="dmx-dialog-actions">
				<Button disabled={saving} onClick={onStay}>
					留在当前页
				</Button>
				<Button disabled={saving} onClick={onDiscard}>
					放弃修改并离开
				</Button>
				<Button primary disabled={saving} onClick={() => void save()}>
					{saving ? "保存中…" : "保存后离开"}
				</Button>
			</div>
		</Modal>
	);
}
