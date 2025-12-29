import { useLayoutEffect } from "react";

import { useRouter } from "@/routes/hooks";

type Props = {
	src: string;
};
export default function ExternalLink({ src }: Props) {
	const { back } = useRouter();
	useLayoutEffect(() => {
		window.open(src, "_blank");
		back();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);
	return <div />;
}
