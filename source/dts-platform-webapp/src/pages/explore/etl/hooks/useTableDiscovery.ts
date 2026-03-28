import { useReducer, useCallback } from "react";
import type { TableInfo } from "@/api/ingestion";

// ---------------------------------------------------------------------------
// State & Actions
// ---------------------------------------------------------------------------

export interface TableDiscoveryState {
	discovering: boolean;
	tables: TableInfo[];
	selectedKeys: string[];
	error: string;
}

type Action =
	| { type: "DISCOVER_START" }
	| { type: "DISCOVER_DONE"; tables: TableInfo[] }
	| { type: "DISCOVER_FAIL"; error: string }
	| { type: "SET_SELECTED_KEYS"; keys: string[] }
	| { type: "CLEAR" };

const initialState: TableDiscoveryState = {
	discovering: false,
	tables: [],
	selectedKeys: [],
	error: "",
};

function reducer(state: TableDiscoveryState, action: Action): TableDiscoveryState {
	switch (action.type) {
		case "DISCOVER_START":
			return { ...state, discovering: true, error: "" };
		case "DISCOVER_DONE":
			return { ...state, discovering: false, tables: action.tables, selectedKeys: [] };
		case "DISCOVER_FAIL":
			return { ...state, discovering: false, error: action.error };
		case "SET_SELECTED_KEYS":
			return { ...state, selectedKeys: action.keys };
		case "CLEAR":
			return { ...initialState };
		default:
			return state;
	}
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

export function useTableDiscovery() {
	const [state, dispatch] = useReducer(reducer, initialState);

	const setDiscoveringTables = useCallback((v: boolean) => {
		if (v) dispatch({ type: "DISCOVER_START" });
	}, []);

	const setDiscoveredTables = useCallback((tables: TableInfo[]) => {
		dispatch({ type: "DISCOVER_DONE", tables });
	}, []);

	const setDiscoverError = useCallback((error: string) => {
		if (error) dispatch({ type: "DISCOVER_FAIL", error });
		else dispatch({ type: "DISCOVER_START" }); // clear error only via start
	}, []);

	const setSelectedTableKeys = useCallback((keys: string[]) => {
		dispatch({ type: "SET_SELECTED_KEYS", keys });
	}, []);

	const clearDiscovery = useCallback(() => {
		dispatch({ type: "CLEAR" });
	}, []);

	return {
		discoveringTables: state.discovering,
		discoveredTables: state.tables,
		selectedTableKeys: state.selectedKeys,
		discoverError: state.error,
		setDiscoveringTables,
		setDiscoveredTables,
		setDiscoverError,
		setSelectedTableKeys,
		clearDiscovery,
		dispatch,
	};
}
