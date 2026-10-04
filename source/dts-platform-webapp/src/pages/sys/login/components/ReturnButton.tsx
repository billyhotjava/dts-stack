import { Button } from "@/ui/button";
import { useBilingualText } from "@/hooks/useBilingualText";

interface ReturnButtonProps {
	onClick?: () => void;
}
export function ReturnButton({ onClick }: ReturnButtonProps) {
	const bilingual = useBilingualText();
	return (
		<Button variant="link" onClick={onClick} className="w-full cursor-pointer text-accent-foreground">
			<span className="text-sm">{bilingual("sys.login.backSignIn")}</span>
		</Button>
	);
}
