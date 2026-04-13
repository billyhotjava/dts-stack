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
  // Generation counter — increments on every submit; closures bail when stale
  const runIdRef = useRef(0);

  const stopPolling = useCallback(() => {
    if (pollTimerRef.current !== undefined) {
      clearTimeout(pollTimerRef.current);
      pollTimerRef.current = undefined;
    }
  }, []);

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
    (id: string, runId: number, attempt: number): Promise<void> => {
      return new Promise((resolve) => {
        const delay = BACKOFF[Math.min(attempt, BACKOFF.length - 1)];
        pollTimerRef.current = setTimeout(() => {
          // Bail if superseded or cancelled
          if (runId !== runIdRef.current) { resolve(); return; }
          if (cancelledRef.current) { resolve(); return; }

          getExecutionStatus(id)
            .then((status) => {
              // Bail again after async fetch
              if (runId !== runIdRef.current) { resolve(); return; }
              if (cancelledRef.current) { resolve(); return; }

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
                pollUntilDone(id, runId, attempt + 1).then(resolve);
              }
            })
            .catch(() => {
              if (runId !== runIdRef.current) { resolve(); return; }
              if (cancelledRef.current) { resolve(); return; }
              pollUntilDone(id, runId, attempt + 1).then(resolve);
            });
        }, delay);
      });
    },
    [stopElapsedTicker],
  );

  const submit = useCallback(
    async (payload: SubmitPayload): Promise<string | null> => {
      // Bump generation counter — any in-flight closure with old runId will bail
      runIdRef.current += 1;
      const runId = runIdRef.current;

      cancelledRef.current = false;
      stopPolling();
      stopElapsedTicker();

      setState("running");
      setElapsedMs(0);
      setRowCount(null);
      setErrorMessage(null);
      setExecutionId(null);

      // Fix #3 (Important): cancel previous server-side execution before starting a new one
      const prevId = executionId;
      if (prevId) {
        void cancelExecution(prevId).catch(() => {});
      }

      startedAtRef.current = Date.now();
      startElapsedTicker();

      try {
        const res = await submitSql(payload);
        // Fix #1 (Critical): bail if superseded or cancelled before touching state
        if (runId !== runIdRef.current) return null;
        if (cancelledRef.current) return null;
        setExecutionId(res.executionId);
        await pollUntilDone(res.executionId, runId, 0);
        return runId === runIdRef.current ? res.executionId : null;
      } catch (err) {
        // Fix #1 (Critical): bail if superseded
        if (runId !== runIdRef.current) return null;
        // Fix #4 (Important): don't overwrite "canceled" with "failed"
        if (cancelledRef.current) return null;
        stopElapsedTicker();
        const msg = err instanceof Error ? err.message : String(err);
        setErrorMessage(msg);
        setState("failed");
        return null;
      }
    },
    // executionId needed to cancel previous; stable refs don't need to be listed
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [executionId, startElapsedTicker, stopElapsedTicker, stopPolling, pollUntilDone],
  );

  const cancel = useCallback(() => {
    cancelledRef.current = true;
    stopPolling();
    stopElapsedTicker();
    setState("canceled");
    if (executionId) {
      void cancelExecution(executionId);
    }
  }, [executionId, stopPolling, stopElapsedTicker]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      cancelledRef.current = true;
      stopPolling();
      stopElapsedTicker();
    };
  }, [stopPolling, stopElapsedTicker]);

  return { state, executionId, elapsedMs, rowCount, errorMessage, submit, cancel };
}
