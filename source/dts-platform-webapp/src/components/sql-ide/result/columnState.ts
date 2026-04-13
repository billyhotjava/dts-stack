export interface SortState {
  name: string;
  direction: "asc" | "desc";
}

export interface GridColumnState {
  columnWidths: Record<string, number>;
  hiddenColumns: string[];
  pinnedColumns: { left: string[]; right: string[] };
  sort: SortState | null;
}

export type ColumnAction =
  | { type: "setWidth"; name: string; width: number }
  | { type: "toggleHidden"; name: string }
  | { type: "setPin"; name: string; side: "left" | "right" }
  | { type: "setUnpin"; name: string }
  | { type: "setSort"; name: string; direction: "asc" | "desc" }
  | { type: "clearSort" };

export function emptyGridColumnState(): GridColumnState {
  return {
    columnWidths: {},
    hiddenColumns: [],
    pinnedColumns: { left: [], right: [] },
    sort: null,
  };
}

export function applyColumnAction(state: GridColumnState, action: ColumnAction): GridColumnState {
  switch (action.type) {
    case "setWidth":
      return { ...state, columnWidths: { ...state.columnWidths, [action.name]: action.width } };
    case "toggleHidden": {
      const isHidden = state.hiddenColumns.includes(action.name);
      return {
        ...state,
        hiddenColumns: isHidden
          ? state.hiddenColumns.filter((n) => n !== action.name)
          : [...state.hiddenColumns, action.name],
      };
    }
    case "setPin": {
      const left =
        action.side === "left"
          ? [...state.pinnedColumns.left.filter((n) => n !== action.name), action.name]
          : state.pinnedColumns.left.filter((n) => n !== action.name);
      const right =
        action.side === "right"
          ? [...state.pinnedColumns.right.filter((n) => n !== action.name), action.name]
          : state.pinnedColumns.right.filter((n) => n !== action.name);
      return { ...state, pinnedColumns: { left, right } };
    }
    case "setUnpin":
      return {
        ...state,
        pinnedColumns: {
          left: state.pinnedColumns.left.filter((n) => n !== action.name),
          right: state.pinnedColumns.right.filter((n) => n !== action.name),
        },
      };
    case "setSort":
      return { ...state, sort: { name: action.name, direction: action.direction } };
    case "clearSort":
      return { ...state, sort: null };
    default: {
      const _exhaustive: never = action;
      void _exhaustive;
      return state;
    }
  }
}
