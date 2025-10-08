import type { HTMLAttributes } from "react";
import { Badge } from "@/ui/badge";

export default function SensitiveNotice(props: HTMLAttributes<HTMLDivElement>) {
	return (
		<div
			{...props}
			className={
				"flex flex-wrap items-center gap-3 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600 " +
				(props.className || "")
			}
		>
			<span aria-hidden className="text-red-500 text-lg">
				★
			</span>
            <span className="font-semibold">此功能涉及数据密级（DATA_*）数据，请注意保密！</span>
			<Badge variant="secondary" className="bg-red-100 text-red-700">
				最小权限原则
			</Badge>
			<Badge variant="outline" className="border-red-300 text-red-700">
				导出需审批
			</Badge>
		</div>
	);
}
