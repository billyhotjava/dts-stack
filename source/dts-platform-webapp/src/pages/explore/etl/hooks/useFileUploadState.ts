import { useReducer, useCallback } from "react";
import type { FileUploadResult } from "@/api/ingestion";
import type { ExcelImportErrorRow } from "@/api/services/dataSourcesService";
import type { TableInfo as SqlTableInfo, ColumnInfo } from "@/api/sql-workbench";
import type { ExtraColumnDef } from "../steps/types";

// ---------------------------------------------------------------------------
// State & Actions
// ---------------------------------------------------------------------------

export interface FileUploadStateShape {
	fileUploadResult: FileUploadResult | null;
	uploading: boolean;
	previewRows: number;
	previewCols: number;
	previewRefreshing: boolean;
	errorPreviewOpen: boolean;
	errorPreviewLoading: boolean;
	errorPreviewRows: ExcelImportErrorRow[];
	errorPreviewLimit: number;
	odsTableList: SqlTableInfo[];
	odsTableLoading: boolean;
	selectedOdsTable: string | undefined;
	odsColumns: ColumnInfo[];
	odsColumnsLoading: boolean;
	odsMatchApplied: boolean;
	extraColumns: ExtraColumnDef[];
}

type Action =
	| { type: "SET_FILE_RESULT"; value: FileUploadResult | null }
	| { type: "SET_UPLOADING"; value: boolean }
	| { type: "SET_PREVIEW_ROWS"; value: number }
	| { type: "SET_PREVIEW_COLS"; value: number }
	| { type: "SET_PREVIEW_REFRESHING"; value: boolean }
	| { type: "SET_ERROR_PREVIEW_OPEN"; value: boolean }
	| { type: "SET_ERROR_PREVIEW_LOADING"; value: boolean }
	| { type: "SET_ERROR_PREVIEW_ROWS"; value: ExcelImportErrorRow[] }
	| { type: "SET_ERROR_PREVIEW_LIMIT"; value: number }
	| { type: "SET_ODS_TABLES"; value: SqlTableInfo[] }
	| { type: "SET_ODS_LOADING"; value: boolean }
	| { type: "SET_SELECTED_ODS"; value: string | undefined }
	| { type: "SET_ODS_COLUMNS"; value: ColumnInfo[] }
	| { type: "SET_ODS_COLUMNS_LOADING"; value: boolean }
	| { type: "SET_ODS_MATCH_APPLIED"; value: boolean }
	| { type: "SET_EXTRA_COLUMNS"; value: ExtraColumnDef[] }
	| { type: "RESET_FILE_STATE" }
	| { type: "RESET_ODS" };

const initialState: FileUploadStateShape = {
	fileUploadResult: null,
	uploading: false,
	previewRows: 20,
	previewCols: 8,
	previewRefreshing: false,
	errorPreviewOpen: false,
	errorPreviewLoading: false,
	errorPreviewRows: [],
	errorPreviewLimit: 50,
	odsTableList: [],
	odsTableLoading: false,
	selectedOdsTable: undefined,
	odsColumns: [],
	odsColumnsLoading: false,
	odsMatchApplied: false,
	extraColumns: [],
};

function reducer(state: FileUploadStateShape, action: Action): FileUploadStateShape {
	switch (action.type) {
		case "SET_FILE_RESULT":
			return { ...state, fileUploadResult: action.value };
		case "SET_UPLOADING":
			return { ...state, uploading: action.value };
		case "SET_PREVIEW_ROWS":
			return { ...state, previewRows: action.value };
		case "SET_PREVIEW_COLS":
			return { ...state, previewCols: action.value };
		case "SET_PREVIEW_REFRESHING":
			return { ...state, previewRefreshing: action.value };
		case "SET_ERROR_PREVIEW_OPEN":
			return { ...state, errorPreviewOpen: action.value };
		case "SET_ERROR_PREVIEW_LOADING":
			return { ...state, errorPreviewLoading: action.value };
		case "SET_ERROR_PREVIEW_ROWS":
			return { ...state, errorPreviewRows: action.value };
		case "SET_ERROR_PREVIEW_LIMIT":
			return { ...state, errorPreviewLimit: action.value };
		case "SET_ODS_TABLES":
			return { ...state, odsTableList: action.value };
		case "SET_ODS_LOADING":
			return { ...state, odsTableLoading: action.value };
		case "SET_SELECTED_ODS":
			return { ...state, selectedOdsTable: action.value };
		case "SET_ODS_COLUMNS":
			return { ...state, odsColumns: action.value };
		case "SET_ODS_COLUMNS_LOADING":
			return { ...state, odsColumnsLoading: action.value };
		case "SET_ODS_MATCH_APPLIED":
			return { ...state, odsMatchApplied: action.value };
		case "SET_EXTRA_COLUMNS":
			return { ...state, extraColumns: action.value };
		case "RESET_FILE_STATE":
			return { ...initialState };
		case "RESET_ODS":
			return {
				...state,
				selectedOdsTable: undefined,
				odsColumns: [],
				odsMatchApplied: false,
			};
		default:
			return state;
	}
}

// ---------------------------------------------------------------------------
// Hook — exposes setter functions matching the original useState interface
// ---------------------------------------------------------------------------

export function useFileUploadState() {
	const [state, dispatch] = useReducer(reducer, initialState);

	const setFileUploadResult = useCallback(
		(value: FileUploadResult | null) => dispatch({ type: "SET_FILE_RESULT", value }),
		[],
	);
	const setUploadingFile = useCallback(
		(value: boolean) => dispatch({ type: "SET_UPLOADING", value }),
		[],
	);
	const setFilePreviewRows = useCallback(
		(value: number) => dispatch({ type: "SET_PREVIEW_ROWS", value }),
		[],
	);
	const setFilePreviewCols = useCallback(
		(value: number) => dispatch({ type: "SET_PREVIEW_COLS", value }),
		[],
	);
	const setPreviewRefreshing = useCallback(
		(value: boolean) => dispatch({ type: "SET_PREVIEW_REFRESHING", value }),
		[],
	);
	const setErrorPreviewOpen = useCallback(
		(value: boolean) => dispatch({ type: "SET_ERROR_PREVIEW_OPEN", value }),
		[],
	);
	const setErrorPreviewLoading = useCallback(
		(value: boolean) => dispatch({ type: "SET_ERROR_PREVIEW_LOADING", value }),
		[],
	);
	const setErrorPreviewRows = useCallback(
		(value: ExcelImportErrorRow[]) => dispatch({ type: "SET_ERROR_PREVIEW_ROWS", value }),
		[],
	);
	const setErrorPreviewLimit = useCallback(
		(value: number) => dispatch({ type: "SET_ERROR_PREVIEW_LIMIT", value }),
		[],
	);
	const setOdsTableList = useCallback(
		(value: SqlTableInfo[]) => dispatch({ type: "SET_ODS_TABLES", value }),
		[],
	);
	const setOdsTableLoading = useCallback(
		(value: boolean) => dispatch({ type: "SET_ODS_LOADING", value }),
		[],
	);
	const setSelectedOdsTable = useCallback(
		(value: string | undefined) => dispatch({ type: "SET_SELECTED_ODS", value }),
		[],
	);
	const setOdsColumns = useCallback(
		(value: ColumnInfo[]) => dispatch({ type: "SET_ODS_COLUMNS", value }),
		[],
	);
	const setOdsColumnsLoading = useCallback(
		(value: boolean) => dispatch({ type: "SET_ODS_COLUMNS_LOADING", value }),
		[],
	);
	const setOdsMatchApplied = useCallback(
		(value: boolean) => dispatch({ type: "SET_ODS_MATCH_APPLIED", value }),
		[],
	);
	const setExtraColumns = useCallback(
		(value: ExtraColumnDef[]) => dispatch({ type: "SET_EXTRA_COLUMNS", value }),
		[],
	);
	const resetFileState = useCallback(
		() => dispatch({ type: "RESET_FILE_STATE" }),
		[],
	);
	const resetOds = useCallback(
		() => dispatch({ type: "RESET_ODS" }),
		[],
	);

	return {
		// State fields (spread for direct access)
		fileUploadResult: state.fileUploadResult,
		uploadingFile: state.uploading,
		filePreviewRows: state.previewRows,
		filePreviewCols: state.previewCols,
		previewRefreshing: state.previewRefreshing,
		errorPreviewOpen: state.errorPreviewOpen,
		errorPreviewLoading: state.errorPreviewLoading,
		errorPreviewRows: state.errorPreviewRows,
		errorPreviewLimit: state.errorPreviewLimit,
		odsTableList: state.odsTableList,
		odsTableLoading: state.odsTableLoading,
		selectedOdsTable: state.selectedOdsTable,
		odsColumns: state.odsColumns,
		odsColumnsLoading: state.odsColumnsLoading,
		odsMatchApplied: state.odsMatchApplied,
		extraColumns: state.extraColumns,
		// Setters
		setFileUploadResult,
		setUploadingFile,
		setFilePreviewRows,
		setFilePreviewCols,
		setPreviewRefreshing,
		setErrorPreviewOpen,
		setErrorPreviewLoading,
		setErrorPreviewRows,
		setErrorPreviewLimit,
		setOdsTableList,
		setOdsTableLoading,
		setSelectedOdsTable,
		setOdsColumns,
		setOdsColumnsLoading,
		setOdsMatchApplied,
		setExtraColumns,
		resetFileState,
		resetOds,
	};
}
