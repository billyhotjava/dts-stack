import { useEffect } from "react";
import { LineLoading } from "@/components/loading";

export default function IndicatorsPage() {
	useEffect(() => {
		window.location.assign("/metrics/dictionary");
	}, []);

	return <LineLoading />;
}
