import { Navigate, useParams } from "react-router";

export default function LegacyConnectionProfileRedirect() {
	const { id } = useParams<{ id: string }>();
	return <Navigate to={id ? `/foundation/connections/${id}` : "/foundation/connections"} replace />;
}
