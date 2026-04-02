import { Star } from "lucide-react";
import { Navigate, useLocation, useSearchParams } from "react-router";
import { shouldAutoRedirectFromLogin, shouldBlockWhileSessionBootstraps } from "@/auth/session-state";
import TechDataBackground from "@/assets/images/background/tech-data-platform.svg";
import TechDataBackgroundLight from "@/assets/images/background/tech-data-platform-light.svg";
import LocalePicker from "@/components/locale-picker";
import { GLOBAL_CONFIG } from "@/global-config";
import { useBilingualText } from "@/hooks/useBilingualText";
import SettingButton from "@/layouts/components/setting-button";
import { usePortalSession } from "@/store/userStore";
import { Alert, AlertDescription, AlertTitle } from "@/ui/alert";
import LoginForm from "./login-form";
import { LoginProvider } from "./providers/login-provider";
import RegisterForm from "./register-form";
import ResetForm from "./reset-form";

function LoginPage() {
	const session = usePortalSession();
	const [searchParams] = useSearchParams();
	const bilingual = useBilingualText();
	const location = useLocation();

	const safeRedirect = (() => {
		try {
			const params = new URLSearchParams(location.search || "");
			const raw = (params.get("redirect") || "").trim();
			if (!raw || !raw.startsWith("/") || raw.startsWith("//") || raw.includes("://") || raw.length > 2048) {
				return null;
			}
			return raw;
		} catch {
			return null;
		}
	})();

	if (shouldBlockWhileSessionBootstraps(session)) {
		return null;
	}

	if (shouldAutoRedirectFromLogin(session)) {
		return <Navigate to={safeRedirect || GLOBAL_CONFIG.defaultRoute} replace />;
	}

	const reason = searchParams.get("reason");
	let sessionMessage: string | null = null;
	switch (reason) {
		case "concurrent-login":
			sessionMessage = "检测到该账号已在其他浏览器登录，当前会话已退出，请重新登录。";
			break;
		case "session-expired":
			sessionMessage = "会话已过期，请重新登录以继续使用系统。";
			break;
		case "signed-out":
			sessionMessage = "您已退出登录，如需继续使用请重新登录。";
			break;
		default:
			sessionMessage = null;
	}

	const brandLabel = bilingual("sys.login.brandName");
	const brandIllustrationAlt = bilingual("sys.login.brandIllustrationAlt");

	return (
		<div className="relative grid min-h-screen lg:grid-cols-2 bg-background">
			<div className="flex flex-col gap-4 p-6 md:p-10">
				<div className="flex justify-center gap-2 md:justify-start">
					<div className="flex items-center gap-3 font-medium cursor-default">
						<Star className="h-8 w-8 text-red-600" fill="currentColor" strokeWidth={1.5} />
						<span className="text-2xl font-semibold leading-tight text-foreground">{brandLabel}</span>
					</div>
				</div>
				<div className="flex flex-1 items-center justify-center">
					<div className="w-full max-w-xs">
						{sessionMessage ? (
							<Alert variant="destructive" className="mb-4">
								<AlertTitle>安全提醒</AlertTitle>
								<AlertDescription>{sessionMessage}</AlertDescription>
							</Alert>
						) : null}
						<LoginProvider>
							<LoginForm />
							<RegisterForm />
							<ResetForm />
						</LoginProvider>
					</div>
				</div>
			</div>

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

			<div className="absolute right-2 top-2 flex flex-row items-center gap-2">
				<LocalePicker />
				<SettingButton />
			</div>
		</div>
	);
}
export default LoginPage;
