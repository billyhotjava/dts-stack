import { Select } from "antd";
import { useEffect, useRef, useState } from "react";
import catalogDomainService, { type CatalogDomain } from "@/api/services/catalogDomainService";
import { auditLog } from "@/utils/audit";

export interface BizDomainSelectProps {
	value: string | "ALL" | null;
	onChange: (value: string | "ALL") => void;
	/** Notifies the parent whether the domain select has renderable data. */
	onAvailabilityChange: (available: boolean) => void;
	/** Shares the configured code-to-name mapping with sibling workbench blocks. */
	onDomainLabelsChange?: (labels: Readonly<Record<string, string>>) => void;
}

export function BizDomainSelect({ value, onChange, onAvailabilityChange, onDomainLabelsChange }: BizDomainSelectProps) {
	const [domains, setDomains] = useState<CatalogDomain[] | null>(null);
	const [available, setAvailable] = useState(false);

	// Hold the latest onAvailabilityChange in a ref so we can fire it exactly
	// once per fetch result without re-running the effect on every parent
	// re-render.
	const availabilityRef = useRef(onAvailabilityChange);
	availabilityRef.current = onAvailabilityChange;
	const domainLabelsRef = useRef(onDomainLabelsChange);
	domainLabelsRef.current = onDomainLabelsChange;

	useEffect(() => {
		let cancelled = false;
		catalogDomainService
			.list()
			.then((list) => {
				if (cancelled) return;
				if (!Array.isArray(list) || list.length === 0) {
					setDomains(null);
					setAvailable(false);
					availabilityRef.current(false);
					domainLabelsRef.current?.({});
					auditLog("WORKBENCH_DOMAIN_API_EMPTY", { when: "fetch_list" });
					return;
				}
				const labels: Record<string, string> = {};
				for (const domain of list) labels[domain.code] = domain.name;
				setDomains(list);
				setAvailable(true);
				availabilityRef.current(true);
				domainLabelsRef.current?.(labels);
			})
			.catch(() => {
				if (cancelled) return;
				setDomains(null);
				setAvailable(false);
				availabilityRef.current(false);
				domainLabelsRef.current?.({});
				auditLog("WORKBENCH_DOMAIN_API_FAIL", { when: "fetch_list" });
			});
		return () => {
			cancelled = true;
		};
	}, []);

	if (!available || !domains) return null;

	const options = [{ value: "ALL", label: "全部业务域" }, ...domains.map((d) => ({ value: d.code, label: d.name }))];

	return (
		<Select
			value={value ?? "ALL"}
			onChange={(v) => onChange((typeof v === "string" && v) || "ALL")}
			options={options}
			style={{ minWidth: 200 }}
			showSearch
			optionFilterProp="label"
		/>
	);
}

export default BizDomainSelect;
