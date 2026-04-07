import { useEffect, useRef, useState } from "react";
import { ingestionTaskAPI, resolveExecutionPollIntervalMs, type IngestionExecutionDTO } from "@/api/ingestion";
import {
	createAsyncRunInitialProgress,
	createAsyncRunRetryProgress,
	createAsyncRunTimeoutProgress,
	mapExecutionToProgressView,
	type AsyncRunProgressView,
} from "./transformCreateAsyncRun.helpers";

type UseTransformAsyncRunProgressResult = {
	asyncRunModalOpen: boolean;
	asyncRunTaskId: number | null;
	asyncRunTaskName: string;
	asyncRunExecution: IngestionExecutionDTO | null;
	asyncRunProgress: AsyncRunProgressView;
	closeAsyncRunModal: () => void;
	startAsyncRunProgress: (taskId: number, taskName: string, pollIntervalMs?: number) => void;
};

export function useTransformAsyncRunProgress(): UseTransformAsyncRunProgressResult {
	const [asyncRunModalOpen, setAsyncRunModalOpen] = useState(false);
	const [asyncRunTaskId, setAsyncRunTaskId] = useState<number | null>(null);
	const [asyncRunTaskName, setAsyncRunTaskName] = useState("");
	const [asyncRunExecution, setAsyncRunExecution] = useState<IngestionExecutionDTO | null>(null);
	const [asyncRunProgress, setAsyncRunProgress] = useState<AsyncRunProgressView>({
		progress: 0,
		status: "active",
		stage: "等待提交",
		detail: "",
		terminal: false,
	});
	const asyncRunPollTimerRef = useRef<number | null>(null);
	const asyncRunStartedAtRef = useRef<number>(0);

	const stopAsyncRunPolling = () => {
		if (asyncRunPollTimerRef.current !== null) {
			window.clearInterval(asyncRunPollTimerRef.current);
			asyncRunPollTimerRef.current = null;
		}
	};

	useEffect(() => {
		return () => {
			if (asyncRunPollTimerRef.current !== null) {
				window.clearInterval(asyncRunPollTimerRef.current);
				asyncRunPollTimerRef.current = null;
			}
		};
	}, []);

	const closeAsyncRunModal = () => {
		stopAsyncRunPolling();
		setAsyncRunModalOpen(false);
	};

	const startAsyncRunProgress = (taskId: number, taskName: string, pollIntervalMs?: number) => {
		stopAsyncRunPolling();
		asyncRunStartedAtRef.current = Date.now();
		setAsyncRunTaskId(taskId);
		setAsyncRunTaskName(taskName);
		setAsyncRunExecution(null);
		setAsyncRunModalOpen(true);
		setAsyncRunProgress(createAsyncRunInitialProgress());

		const pollExecution = async () => {
			const elapsedMs = Date.now() - asyncRunStartedAtRef.current;
			if (elapsedMs > 5 * 60 * 1000) {
				stopAsyncRunPolling();
				setAsyncRunProgress(createAsyncRunTimeoutProgress());
				return;
			}
			try {
				const latest = await ingestionTaskAPI.getLatestExecution(taskId);
				setAsyncRunExecution(latest);
				const nextProgress = mapExecutionToProgressView(latest, elapsedMs);
				setAsyncRunProgress(nextProgress);
				if (nextProgress.terminal) {
					stopAsyncRunPolling();
				}
			} catch {
				setAsyncRunProgress((previous) => createAsyncRunRetryProgress(previous));
			}
		};

		void pollExecution();
		asyncRunPollTimerRef.current = window.setInterval(() => {
			void pollExecution();
		}, resolveExecutionPollIntervalMs(pollIntervalMs));
	};

	return {
		asyncRunModalOpen,
		asyncRunTaskId,
		asyncRunTaskName,
		asyncRunExecution,
		asyncRunProgress,
		closeAsyncRunModal,
		startAsyncRunProgress,
	};
}
