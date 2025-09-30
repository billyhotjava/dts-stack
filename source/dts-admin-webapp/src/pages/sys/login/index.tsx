import { Star } from "lucide-react";
import { Navigate } from "react-router";
import TechDataBackground from "@/assets/images/background/tech-data-platform.svg";
import LocalePicker from "@/components/locale-picker";
import { GLOBAL_CONFIG } from "@/global-config";
import { useBilingualText } from "@/hooks/useBilingualText";
import SettingButton from "@/layouts/components/setting-button";
import { useUserToken } from "@/store/userStore";
import LoginForm from "./login-form";
import { LoginProvider } from "./providers/login-provider";
import RegisterForm from "./register-form";
import ResetForm from "./reset-form";

function LoginPage() {
	const token = useUserToken();
	const bilingual = useBilingualText();

	if (token.accessToken) {
		return <Navigate to={GLOBAL_CONFIG.defaultRoute} replace />;
	}

	const brandLabel = bilingual("sys.login.brandName");
	const brandIllustrationAlt = bilingual("sys.login.brandIllustrationAlt");

	return (
		<div className="relative grid min-h-svh lg:grid-cols-2 bg-background">
			<div className="flex flex-col gap-4 p-6 md:p-10">
				<div className="flex justify-center gap-2 md:justify-start">
					<div className="flex items-center gap-3 font-medium cursor-default">
						<Star className="h-8 w-8 text-red-600" fill="currentColor" strokeWidth={1.5} />
						<span className="text-lg font-semibold leading-tight text-foreground">{brandLabel}</span>
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

			<div className="relative hidden bg-background-paper lg:block">
				<img
					src={TechDataBackground}
					alt={brandIllustrationAlt}
					className="absolute inset-0 h-full w-full object-cover dark:brightness-[0.5] dark:grayscale"
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
