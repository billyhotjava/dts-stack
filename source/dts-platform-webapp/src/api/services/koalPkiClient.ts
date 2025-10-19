import { GLOBAL_CONFIG } from "@/global-config";

type Nullable<T> = T | null | undefined;

const KOAL_SCRIPT_PATHS = [
	"/vendor/koal/thrift.js",
	"/vendor/koal/base64.js",
	"/vendor/koal/commdef_types.js",
	"/vendor/koal/pkiService_types.js",
	"/vendor/koal/devService_types.js",
	"/vendor/koal/signXService_types.js",
	"/vendor/koal/pkiService.js",
	"/vendor/koal/devService.js",
	"/vendor/koal/signXService.js",
] as const;

const DEFAULT_ENDPOINTS = ["https://127.0.0.1:16080", "http://127.0.0.1:18080"] as const;

const RESULT_ERROR_MESSAGES: Record<number, string> = {
	0x0a000000: "未知错误",
	0x0a000001: "中间件调用失败",
	0x0a000002: "中间件内部异常",
	0x0a000003: "当前服务暂不支持",
	0x0a000004: "中间件文件操作失败",
	0x0a000005: "无效的句柄",
	0x0a000006: "请求参数无效",
	0x0a000007: "读取数据失败",
	0x0a000024: "PIN码错误",
	0x0a000025: "PIN码已锁定，请联系管理员解锁",
	0x0a000026: "PIN码无效",
	0x0a000027: "PIN码长度不正确",
	0x0a000028: "用户已登录",
	0x0a00002c: "应用已存在",
	0x0a00002e: "指定的应用不存在",
};

let koalSdkPromise: Promise<void> | null = null;
let requestCounter = 1;

async function loadScript(src: string): Promise<void> {
	if (typeof document === "undefined") return;
	const existing = document.querySelector<HTMLScriptElement>(`script[data-koal-sdk="${src}"]`);
	if (existing?.dataset.loaded === "true") return;
	if (existing) {
		await new Promise<void>((resolve, reject) => {
			existing.addEventListener("load", () => resolve(), { once: true });
			existing.addEventListener("error", () => reject(new Error(`加载 ${src} 失败`)), { once: true });
		});
		return;
	}
	await new Promise<void>((resolve, reject) => {
		const script = document.createElement("script");
		script.src = src;
		script.dataset.koalSdk = src;
		script.async = false;
		script.addEventListener("load", () => {
			script.dataset.loaded = "true";
			resolve();
		});
		script.addEventListener("error", () => reject(new Error(`加载 ${src} 失败`)));
		document.head.appendChild(script);
	});
}

async function ensureKoalSdk() {
	if (!koalSdkPromise) {
		koalSdkPromise = (async () => {
			for (const path of KOAL_SCRIPT_PATHS) {
				await loadScript(path);
			}
		})();
	}
	return koalSdkPromise;
}

declare global {
	interface Window {
		Thrift: any;
		msgRequest: any;
		sessionTicket: any;
		pkiServiceClient: any;
		devServiceClient: any;
		signXServiceClient: any;
		Base64: {
			encode(value: string): string;
			decode(value: string): string;
		};
	}
}

export type KoalCertificate = {
	id: string;
	devId: string;
	appName: string;
	conName: string;
	subjectCn: string;
	issuerCn: string;
	sn: string;
	manufacturer: string;
	keyUsage?: number;
	certType?: string;
	signType: "SM2" | "RSA" | "PM-BD" | "UNKNOWN";
	raw: Record<string, any>;
};

export type KoalSignedPayload = {
	signDataB64: string;
	originDataB64: string;
	signType: KoalCertificate["signType"];
	mdType: string;
	dupCertB64?: string;
};

export class KoalMiddlewareClient {
	private readonly baseUrl: string;
	private readonly transport: any;
	private readonly multiplexer: any;
	private readonly pkiClient: any;
	private readonly devClient: any;
	private readonly signClient: any;
	private session: { sessionID: number; ticket: string } | null = null;

	private constructor(baseUrl: string) {
		this.baseUrl = baseUrl;
		this.transport = new window.Thrift.TXHRTransport(baseUrl);
		this.multiplexer = new window.Thrift.Multiplexer();
		this.pkiClient = this.multiplexer.createClient("pkiService", window.pkiServiceClient, this.transport);
		this.devClient = this.multiplexer.createClient("deviceOperator", window.devServiceClient, this.transport);
		this.signClient = this.multiplexer.createClient("signxPlugin", window.signXServiceClient, this.transport);
	}

	static async connect(): Promise<KoalMiddlewareClient> {
		await ensureKoalSdk();

		const endpoints = [
			...(GLOBAL_CONFIG?.koalPkiEndpoints ?? []),
			...DEFAULT_ENDPOINTS,
		];

		const errors: Error[] = [];

		for (const url of endpoints) {
			try {
				const client = new KoalMiddlewareClient(url);
				await client.login();
				return client;
			} catch (error) {
				if (error instanceof Error) {
					errors.push(error);
				}
			}
		}

		const message = errors.length
			? errors.map((err) => err.message).join("；")
			: "无法连接到本地 PKI 中间件";
		throw new Error(message);
	}

	private async thriftCall<TResult>(client: any, method: string, ...args: any[]): Promise<TResult> {
		return new Promise<TResult>((resolve, reject) => {
			try {
				client[method](...args, (result: TResult | Error) => {
					if (result instanceof Error) {
						reject(result);
						return;
					}
					resolve(result);
				});
			} catch (error) {
				reject(error);
			}
		});
	}

	private buildRequest(msgType: number, body?: Record<string, unknown>) {
		const req = new window.msgRequest();
		req.reqid = (requestCounter++ & 0xffffffff) >>> 0;
		req.msgType = msgType;
		req.version = 1;
		req.extend = 0;
		req.jsonBody = body ? JSON.stringify(body) : "{}";
		return req;
	}

	private buildTicket() {
		if (!this.session) {
			throw new Error("尚未建立 PKI 会话");
		}
		const ticket = new window.sessionTicket();
		ticket.sessionID = this.session.sessionID;
		ticket.ticket = this.session.ticket;
		return ticket;
	}

	async login(): Promise<void> {
		const credentials = {
			appName: "ZF-App",
			appID: "7ea7b92a-3091-3d79-639f-3ef4c3e2d6d7",
			token: "7ea7b92a-3091-3d79-639f-3ef4c3e2d6d7",
		};

		const request = this.buildRequest(0x01, credentials);
		const response = await this.thriftCall<any>(this.pkiClient, "login", request);
		const code = Number(response?.errCode ?? 0);
		if (code !== 0 && code !== 6) {
			throw new Error(mapKoalError(code, response?.jsonBody));
		}
		const payload = parseJson(response?.jsonBody);
		if (!payload?.sessionID || !payload?.ticket) {
			throw new Error("PKI 中间件响应缺少 session 信息");
		}
		this.session = { sessionID: Number(payload.sessionID), ticket: String(payload.ticket) };
	}

	async logout(): Promise<void> {
		if (!this.session) return;
		try {
			const ticket = this.buildTicket();
			await this.thriftCall<any>(this.pkiClient, "logout", ticket);
		} catch (error) {
			// ignore logout failure
		} finally {
			this.session = null;
		}
	}

	async listCertificates(): Promise<KoalCertificate[]> {
		const ticket = this.buildTicket();
		const request = this.buildRequest(0x28);
		const response = await this.thriftCall<any>(this.devClient, "getAllCert", ticket, request);
		const code = Number(response?.errCode ?? 0);
		if (code !== 0) {
			throw new Error(mapKoalError(code, response?.jsonBody));
		}
		const payload = parseJson(response?.jsonBody);
		if (!payload || !Array.isArray(payload.certs)) {
			return [];
		}
		return payload.certs
			.filter((item: any) => filterCertificate(item))
			.map((item: any) => normalizeCertificate(item));
	}

	async verifyPin(cert: KoalCertificate, pin: string): Promise<void> {
		const ticket = this.buildTicket();
		const request = this.buildRequest(0x18, {
			devID: cert.devId,
			appName: cert.appName,
			PINType: "1",
			PIN: pin,
		});
		const response = await this.thriftCall<any>(this.devClient, "verifyPIN", ticket, request);
		const code = Number(response?.errCode ?? 0);
		if (code !== 0) {
			throw new Error(mapKoalError(code, response?.jsonBody));
		}
	}

	async signData(cert: KoalCertificate, plainText: string): Promise<KoalSignedPayload> {
		const ticket = this.buildTicket();
		const originDataB64 = window.Base64?.encode?.(plainText) ?? btoa(plainText);
		const signTypeCode = cert.signType === "PM-BD" ? "1" : "2";
		const mdType = cert.signType === "RSA" ? "4" : "3";

		const request = this.buildRequest(0x10, {
			devID: cert.devId,
			appName: cert.appName,
			conName: cert.conName,
			srcData: originDataB64,
			isBase64SrcData: "1",
			type: signTypeCode,
			mdType,
		});
		const response = await this.thriftCall<any>(this.signClient, "signData", ticket, request);
		const code = Number(response?.errCode ?? 0);
		if (code !== 0) {
			throw new Error(mapKoalError(code, response?.jsonBody));
		}
		const payload = parseJson(response?.jsonBody);
		const signDataB64: Nullable<string> = payload?.b64signData ?? payload?.signData;
		if (!signDataB64) {
			throw new Error("签名失败：缺少签名数据");
		}
		return {
			signDataB64: String(signDataB64),
			originDataB64,
			signType: cert.signType,
			mdType,
		};
	}

	async exportCertificate(cert: KoalCertificate): Promise<string> {
		const ticket = this.buildTicket();
		const request = this.buildRequest(0x22, {
			devID: cert.devId,
			appName: cert.appName,
			containerName: cert.conName,
			signFlag: "1",
		});
		const response = await this.thriftCall<any>(this.devClient, "exportCertificate", ticket, request);
		const code = Number(response?.errCode ?? 0);
		if (code !== 0) {
			throw new Error(mapKoalError(code, response?.jsonBody));
		}
		const payload = parseJson(response?.jsonBody);
		const certB64: Nullable<string> = payload?.cert;
		if (!certB64) {
			throw new Error("未获取到证书内容");
		}
		return String(certB64);
	}
}

function filterCertificate(item: Record<string, any>): boolean {
	const manufacturer = String(item?.manufacturer ?? "").toUpperCase();
	if (manufacturer.includes("KOAL") || manufacturer.startsWith("MICROSOFT")) {
		return false;
	}

	const keyUsageRaw = item?.keyUsage ?? item?.KeyUsage;
	const keyUsageNumber = typeof keyUsageRaw === "string" ? Number(keyUsageRaw) : Number(keyUsageRaw ?? 0);
	if (!Number.isNaN(keyUsageNumber) && keyUsageNumber !== 1 && keyUsageNumber !== 2) {
		return false;
	}

	const signFlagRaw = item?.signFlag ?? item?.SignFlag;
	const signFlag = typeof signFlagRaw === "string" ? Number(signFlagRaw) : Number(signFlagRaw ?? 1);
	if (!Number.isNaN(signFlag) && signFlag !== 1) {
		return false;
	}

	return true;
}

function normalizeCertificate(item: Record<string, any>): KoalCertificate {
	const subjectCn = item?.subjectName?.CN ?? item?.subject ?? "";
	const issuerCn = item?.issuerName?.CN ?? item?.issuer ?? "";
	const signHint = String(item?.certType ?? item?.certAlgorithm ?? item?.algName ?? "").toUpperCase();

	let signType: KoalCertificate["signType"] = "UNKNOWN";
	if (signHint.includes("PM") || signHint.includes("P7")) {
		signType = "PM-BD";
	} else if (signHint.includes("SM2")) {
		signType = "SM2";
	} else if (signHint.includes("RSA")) {
		signType = "RSA";
	} else {
		signType = "SM2";
	}

	const keyUsageRaw = item?.keyUsage ?? item?.KeyUsage;
	const keyUsage = typeof keyUsageRaw === "string" ? Number(keyUsageRaw) : Number(keyUsageRaw ?? undefined);

	const cert: KoalCertificate = {
		id: [
			item?.devID ?? item?.devId ?? "",
			item?.appName ?? "",
			item?.containerName ?? "",
			item?.SN ?? "",
		]
			.map((part) => String(part ?? "").trim())
			.join("::"),
		devId: String(item?.devID ?? item?.devId ?? ""),
		appName: String(item?.appName ?? ""),
		conName: String(item?.containerName ?? ""),
		subjectCn: String(subjectCn ?? ""),
		issuerCn: String(issuerCn ?? ""),
		sn: String(item?.SN ?? ""),
		manufacturer: String(item?.manufacturer ?? ""),
		keyUsage: Number.isNaN(keyUsage) ? undefined : keyUsage,
		certType: item?.certType ? String(item.certType) : undefined,
		signType,
		raw: item,
	};
	return cert;
}

function parseJson(value: Nullable<string>): any {
	if (!value) return null;
	try {
		return JSON.parse(value);
	} catch (_error) {
		return null;
	}
}

function mapKoalError(code: number, fallback?: Nullable<string>): string {
	const known = RESULT_ERROR_MESSAGES[code];
	if (known) return known;
	if (fallback) {
		try {
			const payload = JSON.parse(fallback);
			if (payload?.message) return String(payload.message);
		} catch {
			// ignore
		}
	}
	return `中间件返回错误码 0x${code.toString(16).padStart(8, "0")}`;
}

export function formatKoalError(error: unknown): string {
	if (error instanceof Error) return error.message;
	return typeof error === "string" ? error : "操作失败，请重试";
}

export const KoalErrorMessages = RESULT_ERROR_MESSAGES;

