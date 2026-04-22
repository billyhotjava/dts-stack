import { useEffect, useState } from "react";
import { Navigate, useParams } from "react-router";
import { Spin } from "antd";
import { analyticsApi } from "../api/analyticsApi";
import LegacyCardEditorPage from "./CardEditorPage";

function isSemanticCard(datasetQuery: unknown): boolean {
	if (!datasetQuery || typeof datasetQuery !== "object") {
		return false;
	}
	const value = datasetQuery as Record<string, unknown>;
	return String(value.type ?? "").toLowerCase() === "semantic" || Boolean(value.semantic_query);
}

export default function CardEditorRoutePage() {
	const { id } = useParams();
	const [state, setState] = useState<"loading" | "legacy" | "semantic">("loading");

	useEffect(() => {
		let cancelled = false;
		if (!id) {
			setState("semantic");
			return;
		}
		analyticsApi
			.getCard(id)
			.then((card) => {
				if (cancelled) return;
				setState(isSemanticCard(card.dataset_query) ? "semantic" : "legacy");
			})
			.catch(() => {
				if (cancelled) return;
				setState("legacy");
			});
		return () => {
			cancelled = true;
		};
	}, [id]);

	if (!id) {
		return <Navigate to="/bi/card/new" replace />;
	}
	if (state === "loading") {
		return (
			<div className="loading-container" style={{ padding: 48 }}>
				<Spin size="large" />
			</div>
		);
	}
	if (state === "semantic") {
		return <Navigate to={`/bi/card/${encodeURIComponent(String(id))}/edit`} replace />;
	}
	return <LegacyCardEditorPage />;
}
