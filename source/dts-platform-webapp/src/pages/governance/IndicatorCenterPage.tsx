import { useEffect } from "react";
import { LineLoading } from "@/components/loading";

export default function IndicatorCenterPage() {
	useEffect(() => {
		window.location.assign("/bi-apps/metrics/center");
	}, []);

	return <LineLoading />;
}
