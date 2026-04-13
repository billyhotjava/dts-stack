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
  submit: (payload: SubmitPayload) => Promise<string | null>;
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
    (id: string, attempt: number): Promise<void> => {
      return new Promise((resolve) => {
        const delay = BACKOFF[Math.min(attempt, BACKOFF.length - 1)];
        pollTimerRef.current = setTimeout(() => {
          if (cancelledRef.current) {
            resolve();
            return;
          }
          getExecutionStatus(id)
            .then((status) => {
              if (cancelledRef.current) {
                resolve();
                return;
              }
              if (status.status === "SUCCESS") {
                stopElapsedTicker();
                setElapsedMs(status.elapsedMs ?? Date.now() - startedAtRef.current);
                setRowCount(status.rows ?? null);
                setState("success");
                resolve();
              } else if (status.status === "FAILED") {
                stopElapsedTicker();
                setElapsedMs(status.elapsedMs ?? Date.now() - startedAtRef.current);
                setErrorMessage(status.errorMessage ?? null);
                setState("failed");
                resolve();
              } else if (status.status === "CANCELED") {
                stopElapsedTicker();
                setState("canceled");
                resolve();
              } else {
                // PENDING or RUNNING — keep polling
                pollUntilDone(id, attempt + 1).then(resolve);
              }
            })
            .catch(() => {
              if (cancelledRef.current) {
                resolve();
                return;
              }
              pollUntilDone(id, attempt + 1).then(resolve);
            });
        }, delay);
      });
    },
    [stopElapsedTicker],
  );

  const submit = useCallback(
    async (payload: SubmitPayload): Promise<string | null> => {
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
        if (cancelledRef.current) return null;
        setExecutionId(res.executionId);
        await pollUntilDone(res.executionId, 0);
        return res.executionId;
      } catch (err) {
        stopElapsedTicker();
        const msg = err instanceof Error ? err.message : String(err);
        setErrorMessage(msg);
        setState("failed");
        return null;
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
