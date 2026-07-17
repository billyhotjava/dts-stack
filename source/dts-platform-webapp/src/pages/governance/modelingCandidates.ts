import type { Sprint64BusinessProcess, Sprint64ConformedDimension } from "../../api/sprint64GovernanceApi.ts";

export const confirmedProcesses = (processes: Sprint64BusinessProcess[]) =>
	processes.filter((process) => process.confirmed === true);

export const confirmedDimensions = (dimensions: Sprint64ConformedDimension[]) =>
	dimensions.filter((dimension) => dimension.confirmed === true);

export const pendingCandidateCount = (processes: Sprint64BusinessProcess[], dimensions: Sprint64ConformedDimension[]) =>
	processes.filter((process) => !process.confirmed).length +
	dimensions.filter((dimension) => !dimension.confirmed).length;
