import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router";
import { LineLoading } from "@/components/loading";
import { GLOBAL_CONFIG } from "@/global-config";
import { resolveSafeExternalRedirectTarget } from "@/utils/externalRedirect";

export default function ExternalRedirectPage() {
	const location = useLocation();
	const navigate = useNavigate();
	const homePath = GLOBAL_CONFIG.defaultRoute || "/workbench";

	useEffect(() => {
		const params = new URLSearchParams(location.search || "");
		const target = resolveSafeExternalRedirectTarget(params.get("target"));
		if (target) {
			window.location.replace(target);
			return;
		}
		// Invalid or untrusted relay targets are treated as a normal post-login fallback.
		navigate(homePath, { replace: true });
	}, [homePath, location.search, navigate]);

	return <LineLoading />;
}
