import { Input, type InputRef } from "antd";
import type { Ref } from "react";

type DashboardNameFieldProps = {
	name: string;
	editable: boolean;
	autoFocus: boolean;
	placeholder: string;
	untitledLabel: string;
	inputRef: Ref<InputRef>;
	onNameChange: (name: string) => void;
};

export function DashboardNameField({
	name,
	editable,
	autoFocus,
	placeholder,
	untitledLabel,
	inputRef,
	onNameChange,
}: DashboardNameFieldProps) {
	if (!editable) {
		return <h2 className="m-0 min-w-0 flex-1 truncate text-lg font-semibold">{name || untitledLabel}</h2>;
	}

	return (
		<Input
			ref={inputRef}
			value={name}
			onChange={(event) => onNameChange(event.target.value)}
			aria-label="看板名称"
			aria-required="true"
			autoFocus={autoFocus}
			maxLength={255}
			placeholder={placeholder}
			status={name.trim() ? undefined : "warning"}
			variant="outlined"
			className="min-w-0 flex-1 text-lg font-semibold"
			style={{ fontSize: 18, fontWeight: 600 }}
		/>
	);
}
