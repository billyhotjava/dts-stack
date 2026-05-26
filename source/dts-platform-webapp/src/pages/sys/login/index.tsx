import { Star } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Navigate, useLocation } from "react-router";
import { isWithinLoginProbeGrace } from "@/api/apiClient";
import { getPortalSessionStatus, type PortalSessionStatus } from "@/api/platformApi";
import TechDataBackground from "@/assets/images/background/tech-data-platform.svg";
import TechDataBackgroundLight from "@/assets/images/background/tech-data-platform-light.svg";
import LocalePicker from "@/components/locale-picker";
import { GLOBAL_CONFIG } from "@/global-config";
import { useBilingualText } from "@/hooks/useBilingualText";
import SettingButton from "@/layouts/components/setting-button";
import { resolvePostLoginRedirect } from "@/routes/constants";
import { useUserActions, useUserToken } from "@/store/userStore";
import { isDevFallbackAccessToken } from "@/utils/devAuthTokens";
import { markPortalSessionLogin, wasPortalLogoutBroadcastRecently } from "@/utils/portalSessionStorage";
import { resolvePortalTokenExpiresAt } from "@/utils/sessionExpiry";
import LoginForm from "./login-form";
import { LoginProvider } from "./providers/login-provider";
import RegisterForm from "./register-form";
import ResetForm from "./reset-form";

function isTokenExpired(token?: string): boolean {
	if (!token) return false;
	if (isDevFallbackAccessToken(token)) return false;
	try {
		const parts = token.split(".");
		if (parts.length >= 2) {
			let payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
			while (payload.length % 4 !== 0) payload += "=";
			const obj = JSON.parse(atob(payload));
			if (typeof obj?.exp === "number") {
				return Date.now() > obj.exp * 1000 - 10_000;
			}
		}
	} catch {}
	// Opaque platform tokens are validated by the backend portal session.
	return false;
}

function buildRecoveredUser(status: PortalSessionStatus) {
	const username = String(status.username || "").trim();
	const displayName = String(status.displayName || username || "portal-user").trim();
	const roles = Array.isArray(status.roles) ? status.roles : [];
	const permissions = Array.isArray(status.permissions) ? status.permissions : ["portal.view"];
	return {
		id: username || displayName,
		email: "",
		username: username || displayName,
		firstName: displayName,
		lastName: "",
		fullName: displayName,
		loginIp: status.loginIp ?? status.clientIp,
		clientIp: status.clientIp ?? status.loginIp,
		enabled: true,
		roles,
		permissions,
		deptCode: status.deptCode,
		attributes: {
			...(status.deptCode ? { dept_code: [status.deptCode] } : {}),
			...(status.personnelLevel ? { personnel_level: [status.personnelLevel] } : {}),
		},
	};
}

function LoginPage() {
	const token = useUserToken();
	const { clearUserInfoAndToken, setUserInfo, setUserToken } = useUserActions();
	const bilingual = useBilingualText();
	const location = useLocation();
	const [sessionChecked, setSessionChecked] = useState(false);
	const [sessionAuthenticated, setSessionAuthenticated] = useState(false);

	const hasLocallyValidToken = useMemo(
		() => Boolean((token.authenticated || token.accessToken) && !isTokenExpired(token.accessToken)),
		[token.authenticated, token.accessToken],
	);

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

	useEffect(() => {
		let alive = true;

		const verifySession = async () => {
			const checkedAccessToken = token.accessToken;
			if (!hasLocallyValidToken) {
				try {
					const status = await getPortalSessionStatus();
					if (!alive) return;
					if (status?.authenticated) {
						const tokenExpiresAt = resolvePortalTokenExpiresAt({
							portalExpiresAt: status.expiresAt,
							portalExpiresIn: status.remainingSeconds ?? undefined,
						});
						markPortalSessionLogin();
						setUserToken({ authenticated: true, tokenExpiresAt });
						setUserInfo(buildRecoveredUser(status));
						setSessionAuthenticated(true);
						setSessionChecked(true);
						return;
					}
				} catch {
					// No cookie-backed session to recover; render the login form.
				}
				if (alive) {
					setSessionAuthenticated(false);
					setSessionChecked(true);
				}
				return;
			}
			if (isDevFallbackAccessToken(checkedAccessToken)) {
				if (alive) {
					setSessionAuthenticated(true);
					setSessionChecked(true);
				}
				return;
			}

			if (wasPortalLogoutBroadcastRecently(15_000, Date.now(), checkedAccessToken)) {
				clearUserInfoAndToken();
				if (alive) {
					setSessionAuthenticated(false);
					setSessionChecked(true);
				}
				return;
			}

			// 登录后 grace window：portal_session 刚 save 可能还未对新请求可见，
			// 直接信任本地 token 跳转主页，让后续 SessionManager probe 继续负责失效感知。
			if (isWithinLoginProbeGrace()) {
				if (alive) {
					setSessionAuthenticated(true);
					setSessionChecked(true);
				}
				return;
			}

			try {
				const status = await getPortalSessionStatus();
				if (!alive) return;
				const authenticated = Boolean(status?.authenticated);
				setSessionAuthenticated(authenticated);
				setSessionChecked(true);
				if (!authenticated) {
					clearUserInfoAndToken();
				}
			} catch {
				if (!alive) return;
				setSessionAuthenticated(false);
				setSessionChecked(true);
				clearUserInfoAndToken();
			}
		};

		void verifySession();
		return () => {
			alive = false;
		};
	}, [clearUserInfoAndToken, hasLocallyValidToken, setUserInfo, setUserToken, token.accessToken]);

	if (hasLocallyValidToken && !sessionChecked) {
		return null;
	}

	if (hasLocallyValidToken && sessionAuthenticated) {
		const postLoginRedirect = resolvePostLoginRedirect(safeRedirect);
		// If we're already authenticated and this page was reached via embedded module redirect,
		// jump directly to that module with a hard navigation.
		if (postLoginRedirect.startsWith("/analytics")) {
			window.location.replace(postLoginRedirect);
			return null;
		}
		return <Navigate to={postLoginRedirect} replace />;
	}

	const brandLabel = bilingual("sys.login.brandName");
	const brandIllustrationAlt = bilingual("sys.login.brandIllustrationAlt");
	const showClassifiedLoginBadge = GLOBAL_CONFIG.showClassifiedLoginBadge;

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
				{showClassifiedLoginBadge ? (
					<div className="flex justify-center gap-2 md:justify-start">
						<div className="flex items-center gap-3 font-medium cursor-default">
							<Star className="h-8 w-8 text-red-600" fill="currentColor" strokeWidth={1.5} />
							<span className="text-2xl font-semibold leading-tight text-foreground">{brandLabel}</span>
						</div>
					</div>
				) : null}
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
