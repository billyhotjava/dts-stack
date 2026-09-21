import { Select } from "antd";
import { useEffect, useState } from "react";
import { listDepartments } from "@/api/services/deptService";
import { searchUsers } from "@/api/services/userDirectoryService";

export function MetricOwnershipSelect({
	kind,
	value,
	onChange,
}: {
	kind: "user" | "department";
	value: string;
	onChange: (value: string) => void;
}) {
	const [keyword, setKeyword] = useState("");
	const [options, setOptions] = useState<{ value: string; label: string }[]>([]);
	const [loading, setLoading] = useState(false);
	useEffect(() => {
		let cancelled = false;
		setLoading(true);
		const timer = window.setTimeout(async () => {
			try {
				const found =
					kind === "user"
						? (await searchUsers(keyword)).map((user) => ({
								value: user.username,
								label: `${user.displayName || user.fullName || user.username}（${user.username}）`,
							}))
						: (await listDepartments(keyword)).map((department) => ({
								value: department.code,
								label: `${department.nameZh || department.code}（${department.code}）`,
							}));
				if (!cancelled) setOptions(found);
			} catch {
				if (!cancelled) setOptions([]);
			} finally {
				if (!cancelled) setLoading(false);
			}
		}, 250);
		return () => {
			cancelled = true;
			window.clearTimeout(timer);
		};
	}, [kind, keyword]);
	const label = kind === "user" ? "负责人" : "责任部门";
	const displayedOptions =
		value && !options.some((option) => option.value === value) ? [{ value, label: value }, ...options] : options;
	return (
		<Select
			aria-label={label}
			allowClear
			showSearch
			filterOption={false}
			loading={loading}
			value={value || undefined}
			options={displayedOptions}
			onSearch={setKeyword}
			onChange={(selected: string | undefined) => {
				onChange(selected || "");
				setKeyword("");
			}}
			placeholder={`请选择${label}`}
			notFoundContent={loading ? "正在加载…" : "暂无匹配项，请调整关键词或稍后重试"}
			style={{ width: "100%" }}
		/>
	);
}
