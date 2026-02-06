import { useState } from "react";
import { toast } from "sonner";
import { writeTextToClipboard } from "@/utils/clipboard";

type CopiedValue = string | null;
type CopyFn = (text: string) => Promise<boolean>;
type ReturnType = {
	copyFn: CopyFn;
	copiedText: CopiedValue;
};

export const useCopyToClipboard = (): ReturnType => {
	const [copiedText, setCopiedText] = useState<CopiedValue>(null);

	const copyFn: CopyFn = async (text) => {
		try {
			const copied = await writeTextToClipboard(text);
			if (!copied) {
				console.warn("Clipboard not supported");
				setCopiedText(null);
				return false;
			}
			setCopiedText(text);
			toast.success("Copied!");
			return true;
		} catch (error) {
			console.warn("Copy failed", error);
			setCopiedText(null);
			return false;
		}
	};

	return { copiedText, copyFn };
};
