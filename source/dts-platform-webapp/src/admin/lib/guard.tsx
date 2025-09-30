import { useCallback, useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import { AdminSessionContext } from "@/admin/lib/session-context";
import ForbiddenView from "@/admin/views/forbidden";
import type { AdminRole } from "@/admin/types";
import { useRouter } from "@/routes/hooks";
import { useSignOut } from "@/store/userStore";
import { LineLoading } from "@/components/loading";

interface Props {
	children: React.ReactNode;
}

export default function AdminGuard({ children }: Props) {
	const router = useRouter();
	const signOut = useSignOut();
	const [blocked, setBlocked] = useState(false);

	const { data, isLoading, isError } = useQuery({
		queryKey: ["admin", "whoami"],
		queryFn: adminApi.getWhoami,
		retry: false,
	});

	const handleForbidden = useCallback(async () => {
		if (blocked) return;
		setBlocked(true);
		try {
			await signOut();
		} finally {
			router.replace("/403");
		}
	}, [blocked, router, signOut]);

	useEffect(() => {
		if (isLoading) return;
		if (isError || !data?.allowed || !data.role) {
			handleForbidden();
		}
	}, [data, handleForbidden, isError, isLoading]);

	const session = useMemo(() => {
		if (!data?.allowed || !data.role) return null;
		return {
			role: data.role as AdminRole,
			username: data.username,
			email: data.email,
		};
	}, [data]);

	if (isLoading && !session && !blocked) {
		return (
			<div className="flex h-full min-h-60 items-center justify-center">
				<LineLoading />
			</div>
		);
	}

	if (blocked) {
		return <ForbiddenView />;
	}

	if (!session) {
		return null;
	}

	return <AdminSessionContext.Provider value={session}>{children}</AdminSessionContext.Provider>;
}
