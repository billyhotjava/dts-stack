import { Space } from "antd";
import { useCallback } from "react";
import { auditLog } from "@/utils/audit";
import { useWorkbenchRole, type WorkbenchRoleInfo } from "../hooks/useWorkbenchRole";
import { BizDomainSelect } from "./BizDomainSelect";
import { DeptSelect } from "./DeptSelect";
import { TimeRangeSelect, type TimeRange } from "./TimeRangeSelect";

export interface WorkbenchFilterState {
	scope: "MINE" | "DEPT" | "ALL";
	/** Non-null when drilling into a specific department (leader scope=ALL with a picked dept) or dept leader. */
	deptCode: string | null;
	/** null == ALL business domains. */
	bizDomain: string | null;
	timeRange: TimeRange;
	bizDomainAvailable: boolean;
}

export interface WorkbenchFilterBarProps {
	value: WorkbenchFilterState;
	onChange: (next: WorkbenchFilterState) => void;
}

/**
 * Build a baseline filter state for a given role:
 *   - INST_LEADER: scope=ALL, deptCode=null (shows aggregate across institution)
 *   - DEPT_LEADER: scope=DEPT, deptCode=<self>
 *   - EMP:         scope=MINE, deptCode=null
 */
export function initialFilterState(roleInfo: WorkbenchRoleInfo): WorkbenchFilterState {
	if (roleInfo.isInstLeader) {
		return {
			scope: "ALL",
			deptCode: null,
			bizDomain: null,
			timeRange: "MONTH",
			bizDomainAvailable: false,
		};
	}
	if (roleInfo.isDeptLeader) {
		return {
			scope: "DEPT",
			deptCode: roleInfo.deptCode,
			bizDomain: null,
			timeRange: "MONTH",
			bizDomainAvailable: false,
		};
	}
	return {
		scope: "MINE",
		deptCode: null,
		bizDomain: null,
		timeRange: "MONTH",
		bizDomainAvailable: false,
	};
}

export function WorkbenchFilterBar({ value, onChange }: WorkbenchFilterBarProps) {
	const roleInfo = useWorkbenchRole();

	const handleDeptChange = useCallback(
		(deptSelected: string | "ALL") => {
			const nextScope: WorkbenchFilterState["scope"] = roleInfo.isInstLeader
				? "ALL"
				: roleInfo.isDeptLeader
					? "DEPT"
					: "MINE";
			const nextDeptCode: string | null = roleInfo.isInstLeader
				? deptSelected === "ALL"
					? null
					: deptSelected
				: roleInfo.isDeptLeader
					? roleInfo.deptCode
					: null;
			const next: WorkbenchFilterState = {
				...value,
				scope: nextScope,
				deptCode: nextDeptCode,
			};
			auditLog("WORKBENCH_FILTER_CHANGE", {
				dim: "dept",
				value: deptSelected,
				role: roleInfo.role,
			});
			onChange(next);
		},
		[roleInfo, value, onChange],
	);

	const handleDomainChange = useCallback(
		(domain: string | "ALL") => {
			const next: WorkbenchFilterState = {
				...value,
				bizDomain: domain === "ALL" ? null : domain,
			};
			auditLog("WORKBENCH_FILTER_CHANGE", {
				dim: "bizDomain",
				value: domain,
				role: roleInfo.role,
			});
			onChange(next);
		},
		[roleInfo.role, value, onChange],
	);

	const handleTimeChange = useCallback(
		(t: TimeRange) => {
			const next: WorkbenchFilterState = { ...value, timeRange: t };
			auditLog("WORKBENCH_FILTER_CHANGE", {
				dim: "timeRange",
				value: t,
				role: roleInfo.role,
			});
			onChange(next);
		},
		[roleInfo.role, value, onChange],
	);

	const handleBizAvailability = useCallback(
		(available: boolean) => {
			if (available === value.bizDomainAvailable) return;
			onChange({
				...value,
				bizDomainAvailable: available,
				bizDomain: available ? value.bizDomain : null,
			});
		},
		[value, onChange],
	);

	const deptSelectValue: string | "ALL" | null = roleInfo.isInstLeader
		? (value.deptCode ?? "ALL")
		: roleInfo.deptCode;

	return (
		<div
			style={{
				position: "sticky",
				top: 0,
				zIndex: 10,
				background: "#fff",
				padding: "12px 16px",
				borderBottom: "1px solid #f0f0f0",
			}}
		>
			<Space size="middle" wrap>
				<DeptSelect value={deptSelectValue} onChange={handleDeptChange} />
				<BizDomainSelect
					value={value.bizDomain ?? "ALL"}
					onChange={handleDomainChange}
					onAvailabilityChange={handleBizAvailability}
				/>
				<TimeRangeSelect value={value.timeRange} onChange={handleTimeChange} />
			</Space>
		</div>
	);
}

export default WorkbenchFilterBar;
