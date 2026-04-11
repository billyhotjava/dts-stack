import { Star } from "lucide-react";
import { Navigate, useLocation } from "react-router";
import TechDataBackground from "@/assets/images/background/tech-data-platform.svg";
import TechDataBackgroundLight from "@/assets/images/background/tech-data-platform-light.svg";
import LocalePicker from "@/components/locale-picker";
import { GLOBAL_CONFIG } from "@/global-config";
import { useBilingualText } from "@/hooks/useBilingualText";
import SettingButton from "@/layouts/components/setting-button";
import { useUserToken } from "@/store/userStore";
import LoginForm from "./login-form";
import { LoginProvider } from "./providers/login-provider";
import RegisterForm from "./register-form";
import ResetForm from "./reset-form";

function isRecentLogin(): boolean {
	try {
		const ts = Number(localStorage.getItem("dts.session.loginTs") || "0");
		return ts > 0 && Date.now() - ts < 120_000;
	} catch {
		return false;
	}
}

function isTokenExpired(token?: string): boolean {
	if (!token) return true;
	if (token.startsWith("dev-access-")) return false;
	try {
		const parts = token.split(".");
		if (parts.length < 2) return !isRecentLogin();
		let payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
		while (payload.length % 4 !== 0) payload += "=";
		const obj = JSON.parse(atob(payload));
		if (typeof obj?.exp === "number") {
			return Date.now() > obj.exp * 1000 - 10_000;
		}
		return !isRecentLogin();
	} catch {
		return !isRecentLogin();
	}
}

function LoginPage() {
	const token = useUserToken();
	const bilingual = useBilingualText();
	const location = useLocation();

	const safeRedirect = (() => {
		try {
			const params = new URLSearchParams(location.search || "");
			const raw = (params.get("redirect") || "").trim();
			if (!raw) return null;
			if (!raw.startsWith("/")) return null;
			if (raw.startsWith("//")) return null;
			if (raw.includes("://")) return null;
			if (raw.length > 2048) return null;
			return raw;
		} catch {
			return null;
		}
	})();

	if (token.accessToken && !isTokenExpired(token.accessToken)) {
		// If we're already authenticated and this page was reached via embedded module redirect,
		// jump directly to that module with a hard navigation.
		if (safeRedirect?.startsWith("/analytics")) {
			window.location.replace(safeRedirect);
			return null;
		}
		return <Navigate to={safeRedirect || GLOBAL_CONFIG.defaultRoute} replace />;
	}

	const brandLabel = bilingual("sys.login.brandName");
	const brandIllustrationAlt = bilingual("sys.login.brandIllustrationAlt");

	return (
		<div className="relative grid min-h-screen lg:grid-cols-2 bg-background">
			{/* Illustration at left on desktop to distinguish from admin style */}
			<div className="relative hidden bg-background lg:block">
				<img
					src={TechDataBackgroundLight}
					alt={brandIllustrationAlt}
					className="absolute inset-0 h-full w-full object-cover dark:hidden"
				/>
				<img
					src={TechDataBackground}
					alt={brandIllustrationAlt}
					className="absolute inset-0 h-full w-full object-cover hidden dark:block"
				/>
			</div>

			{/* Login form on the right */}
			<div className="flex flex-col gap-4 p-6 md:p-10">
				<div className="flex justify-center gap-2 md:justify-start">
					<div className="flex items-center gap-3 font-medium cursor-default">
						<Star className="h-8 w-8 text-red-600" fill="currentColor" strokeWidth={1.5} />
						<span className="text-2xl font-semibold leading-tight text-foreground">{brandLabel}</span>
					</div>
				</div>
				<div className="flex flex-1 items-center justify-center">
					<div className="w-full max-w-xs">
						<LoginProvider>
							<LoginForm />
							<RegisterForm />
							<ResetForm />
						</LoginProvider>
					</div>
				</div>
			</div>

			<div className="absolute right-2 top-2 flex flex-row items-center gap-2">
				<LocalePicker />
				<SettingButton />
			</div>
		</div>
	);
}
export default LoginPage;
