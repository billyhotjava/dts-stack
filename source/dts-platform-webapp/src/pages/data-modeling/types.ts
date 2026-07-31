export type DataModelingWorkspace = "home" | "planning" | "standards" | "dimensions" | "metrics" | "tools" | "graphs";

export type DataModelingRoute = {
	workspace: DataModelingWorkspace;
	view: string;
	title: string;
	description: string;
};

export type DemoRow = Record<string, string | number>;

export type TableColumn = {
	key: string;
	title: string;
	width?: number;
};

export type WorkspacePageProps = {
	route: DataModelingRoute;
};
