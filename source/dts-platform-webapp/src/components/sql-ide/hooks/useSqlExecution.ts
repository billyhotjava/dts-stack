import { useCallback, useEffect, useRef, useState } from "react";
import {
  cancelExecution,
  getExecutionStatus,
  submitSql,
  type SubmitPayload,
} from "../api/sqlIdeExecution";

export type ExecState = "idle" | "running" | "success" | "failed" | "canceled";

export interface SqlExecutionResult {
  state: ExecState;
  executionId: string | null;
  elapsedMs: number;
  rowCount: number | null;
  errorMessage: string | null;
  submit: (payload: SubmitPayload) => Promise<void>;
  cancel: () => void;
}

const BACKOFF = [500, 1500, 3000, 5000];

export function useSqlExecution(): SqlExecutionResult {
  const [state, setState] = useState<ExecState>("idle");
  const [executionId, setExecutionId] = useState<string | null>(null);
  const [elapsedMs, setElapsedMs] = useState(0);
  const [rowCount, setRowCount] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const cancelledRef = useRef(false);
  const pollTimerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const startedAtRef = useRef<number>(0);
  const elapsedIntervalRef = useRef<ReturnType<typeof setInterval> | undefined>(undefined);

  const stopElapsedTicker = useCallback(() => {
    if (elapsedIntervalRef.current !== undefined) {
      clearInterval(elapsedIntervalRef.current);
      elapsedIntervalRef.current = undefined;
    }
  }, []);

  const startElapsedTicker = useCallback(() => {
    stopElapsedTicker();
    elapsedIntervalRef.current = setInterval(() => {
      setElapsedMs(Date.now() - startedAtRef.current);
    }, 250);
  }, [stopElapsedTicker]);

  const pollUntilDone = useCallback(
    (id: string, attempt: number) => {
      const delay = BACKOFF[Math.min(attempt, BACKOFF.length - 1)];
      pollTimerRef.current = setTimeout(() => {
        if (cancelledRef.current) return;
        getExecutionStatus(id)
          .then((status) => {
            if (cancelledRef.current) return;
            if (status.status === "SUCCESS") {
              stopElapsedTicker();
              setElapsedMs(status.elapsedMs ?? Date.now() - startedAtRef.current);
              setRowCount(status.rows ?? null);
              setState("success");
            } else if (status.status === "FAILED") {
              stopElapsedTicker();
              setElapsedMs(status.elapsedMs ?? Date.now() - startedAtRef.current);
              setErrorMessage(status.errorMessage ?? null);
              setState("failed");
            } else if (status.status === "CANCELED") {
              stopElapsedTicker();
              setState("canceled");
            } else {
              // PENDING or RUNNING — keep polling
              pollUntilDone(id, attempt + 1);
            }
          })
          .catch(() => {
            if (cancelledRef.current) return;
            pollUntilDone(id, attempt + 1);
          });
      }, delay);
    },
    [stopElapsedTicker],
  );

  const submit = useCallback(
    async (payload: SubmitPayload) => {
      // Reset state
      cancelledRef.current = false;
      if (pollTimerRef.current !== undefined) {
        clearTimeout(pollTimerRef.current);
        pollTimerRef.current = undefined;
      }
      stopElapsedTicker();

      setState("running");
      setExecutionId(null);
      setElapsedMs(0);
      setRowCount(null);
      setErrorMessage(null);

      startedAtRef.current = Date.now();
      startElapsedTicker();

      try {
        const res = await submitSql(payload);
        if (cancelledRef.current) return;
        setExecutionId(res.executionId);
        pollUntilDone(res.executionId, 0);
      } catch (err) {
        stopElapsedTicker();
        const msg = err instanceof Error ? err.message : String(err);
        setErrorMessage(msg);
        setState("failed");
      }
    },
    [startElapsedTicker, stopElapsedTicker, pollUntilDone],
  );

  const cancel = useCallback(() => {
    cancelledRef.current = true;
    if (pollTimerRef.current !== undefined) {
      clearTimeout(pollTimerRef.current);
      pollTimerRef.current = undefined;
    }
    stopElapsedTicker();
    setState("canceled");
    if (executionId) {
      void cancelExecution(executionId);
    }
  }, [executionId, stopElapsedTicker]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      cancelledRef.current = true;
      if (pollTimerRef.current !== undefined) clearTimeout(pollTimerRef.current);
      stopElapsedTicker();
    };
  }, [stopElapsedTicker]);

  return { state, executionId, elapsedMs, rowCount, errorMessage, submit, cancel };
}
