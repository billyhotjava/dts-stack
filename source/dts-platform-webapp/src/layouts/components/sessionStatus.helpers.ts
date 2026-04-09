import type { PortalSessionStatus } from "@/api/platformApi";

export function formatPortalSessionStatus(status?: PortalSessionStatus | null): string {
	if (!status?.authenticated) {
		return "服务器会话未建立";
	}
	const remainingSeconds = status.remainingSeconds;
	if (remainingSeconds == null) {
		return "服务器会话有效";
	}
	if (remainingSeconds <= 0) {
		return "服务器会话即将失效";
	}
	if (remainingSeconds < 60) {
		return "服务器会话剩余不到 1 分钟";
	}
	if (remainingSeconds < 3600) {
		return `服务器会话剩余约 ${Math.ceil(remainingSeconds / 60)} 分钟`;
	}
	const hours = Math.floor(remainingSeconds / 3600);
	const minutes = Math.ceil((remainingSeconds % 3600) / 60);
	if (minutes <= 0) {
		return `服务器会话剩余约 ${hours} 小时`;
	}
	return `服务器会话剩余约 ${hours} 小时 ${minutes} 分钟`;
}

export function resolveSessionStatusTone(status?: PortalSessionStatus | null): "normal" | "warning" | "danger" {
	if (!status?.authenticated || status.remainingSeconds == null) {
		return "normal";
	}
	if (status.remainingSeconds <= 300) {
		return "danger";
	}
	if (status.remainingSeconds <= 900) {
		return "warning";
	}
	return "normal";
}
