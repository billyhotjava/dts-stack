export type DataModelingWorkspace = "home" | "planning" | "standards" | "dimensions" | "metrics" | "tools" | "graphs";

export type DataModelingRoute = {
	workspace: DataModelingWorkspace;
	view: string;
	title: string;
	description: string;
};
