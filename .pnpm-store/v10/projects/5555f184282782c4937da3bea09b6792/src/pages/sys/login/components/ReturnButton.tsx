import { Icon } from "@/components/icon";
import { Button } from "@/ui/button";
import { useBilingualText } from "@/hooks/useBilingualText";

interface ReturnButtonProps {
	onClick?: () => void;
}
export function ReturnButton({ onClick }: ReturnButtonProps) {
	const bilingual = useBilingualText();
	return (
		<Button variant="link" onClick={onClick} className="w-full cursor-pointer text-accent-foreground">
			<Icon icon="solar:alt-arrow-left-linear" size={20} />
			<span className="text-sm">{bilingual("sys.login.backSignIn")}</span>
		</Button>
	);
}
