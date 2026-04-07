import { createContext, useCallback, useContext, useState } from "react";

export type LogPreviewParams = {
  entryKey: "AIRFLOW_DAG" | "INGESTION_TASK" | "DBT_RUN";
  /** For AIRFLOW_DAG / DBT_RUN */
  dagId?: string;
  dagRunId?: string;
  taskId?: string;
  /** Must be passed when known; defaults to 1 only as last resort */
  tryNumber?: number;
  /** For INGESTION_TASK */
  ingestionTaskId?: number;
  executionId?: number;
  /** Display title override */
  title?: string;
};

type LogPreviewContextValue = {
  params: LogPreviewParams | null;
  open: boolean;
  openLogPreview: (p: LogPreviewParams) => void;
  closeLogPreview: () => void;
};

const LogPreviewContext = createContext<LogPreviewContextValue | null>(null);

export function LogPreviewProvider({ children }: { children: React.ReactNode }) {
  const [params, setParams] = useState<LogPreviewParams | null>(null);
  const [open, setOpen] = useState(false);

  const openLogPreview = useCallback((p: LogPreviewParams) => {
    setParams(p);
    setOpen(true);
  }, []);

  const closeLogPreview = useCallback(() => {
    setOpen(false);
  }, []);

  return (
    <LogPreviewContext.Provider value={{ params, open, openLogPreview, closeLogPreview }}>
      {children}
    </LogPreviewContext.Provider>
  );
}

export function useLogPreview() {
  const ctx = useContext(LogPreviewContext);
  if (!ctx) throw new Error("useLogPreview must be used inside LogPreviewProvider");
  return ctx;
}
