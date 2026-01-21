import React from "react";

type Props = {
	 title: string;
	 description?: string;
};

export default function V3Placeholder({ title, description }: Props) {
	return (
		<div className="min-h-[60vh] px-6 py-8">
			<div className="max-w-3xl space-y-3">
				<h1 className="text-2xl font-semibold text-text-primary">{title}</h1>
				{description ? <p className="text-sm text-text-secondary">{description}</p> : null}
				<p className="text-xs text-text-tertiary">V3 页面占位，后续将替换为正式功能。</p>
			</div>
		</div>
	);
}
