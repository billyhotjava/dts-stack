import { Navigate } from "react-router";
import { useSearchParams } from "@/routes/hooks";
import { buildModelingJourneyRoute, modelingStagePath, resolveModelingJourneyContext } from "./modelingJourneyContext";

export default function ModelingWorkbenchPage() {
	const searchParams = useSearchParams();
	const context = resolveModelingJourneyContext(searchParams);

	return <Navigate replace to={buildModelingJourneyRoute(modelingStagePath(context.stage), context)} />;
}
