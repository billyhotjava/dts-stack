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
const IS_DEV = typeof import.meta !== "undefined" && Boolean(import.meta.env?.DEV);

const RESULT_ERROR_MESSAGES: Record<number, string> = {
	0x00000000: "调用成功",
	0x00000001: "session 不存在，请先调用 login 接口",
	0x00000003: "session 已注册 Notify，请先调用 logout 释放会话",
	0x00000004: "消息类型错误",
	0x00000005: "消息 JsonBody 无效或缺少参数，请核对接口文档",
	0x00000006: "app 已经登录，建议业务完成后调用 logout 接口",
	0x00000007: "超时：调用耗时超过 30 秒，可能程序阻塞或存在人工交互",
	0x00000008: "登录参数未授权或未使用分配的登录参数",
	0x00000009: "可信驱动已设置",
	0x0000000a: "可信驱动未设置",
	0x0000000b: "获取登录临时参数失败",
	0x0000000c: "调用失败，请结合接口返回详情排查",
	0x0000000d: "内存不足，请检查终端内存",
	0x0000000e: "OpenSSL 接口调用失败，请结合接口返回详情排查",
	0x0a000000: "未知错误",
	0x0a000001: "失败：驱动接口调用失败，通常为参数异常",
	0x0a000002: "异常错误：驱动接口调用失败，通常为参数异常",
	0x0a000003: "不支持的服务：请核实调用的接口是否正确",
	0x0a000004: "文件操作错误",
	0x0a000005: "无效的句柄",
	0x0a000006: "无效的参数，请对照接口文档检查",
	0x0a000007: "读文件错误",
	0x0a000008: "写文件错误",
	0x0a000009: "名称长度错误",
	0x0a00000a: "密钥用途错误，请核对传入参数",
	0x0a00000b: "模的长度错误",
	0x0a00000c: "未初始化",
	0x0a00000d: "对象错误",
	0x0a00000e: "内存错误",
	0x0a00000f: "超时：驱动操作阻塞，请尝试插拔 key 后重试",
	0x0a000010: "输入数据长度错误",
	0x0a000011: "输入数据错误",
	0x0a000012: "生成随机数错误",
	0x0a000013: "HASH 对象错误",
	0x0a000014: "HASH 运算错误",
	0x0a000015: "产生 RSA 密钥错误",
	0x0a000016: "RSA 密钥模长错误",
	0x0a000017: "CSP 服务导入公钥错误",
	0x0a000018: "RSA 加密错误",
	0x0a000019: "RSA 解密错误",
	0x0a00001a: "HASH 值不相等，请检查输入数据",
	0x0a00001b: "未发现密钥：容器中不存在对应密钥，请更换介质或重签发证书",
	0x0a00001c: "未发现证书：请在驱动工具中确认证书是否存在",
	0x0a00001d: "对象未导出",
	0x0a00001e: "解密时补丁处理错误",
	0x0a00001f: "MAC 长度错误",
	0x0a000020: "缓冲区不足",
	0x0a000021: "密钥类型错误",
	0x0a000022: "无事件错误",
	0x0a000023: "设备已移除，请检查 USB 连接后重试",
	0x0a000024: "PIN 错误：输入的 PIN 与预置值不匹配",
	0x0a000025: "PIN 锁死：错误次数过多，请使用驱动工具或调用 unlockPIN 解锁",
	0x0a000026: "PIN 无效，请确认正确的 PIN 再试",
	0x0a000027: "PIN 长度错误，请设置至少 6 位的复杂 PIN",
	0x0a000028: "用户已经登录（已验证 PIN）",
	0x0a000029: "未初始化用户口令",
	0x0a00002a: "PIN 类型错误，请对照接口文档重试",
	0x0a00002b: "应用名称无效，请检查输入",
	0x0a00002c: "应用已经存在，请更换或删除现有应用",
	0x0a00002d: "用户没有登录，请先调用 verifyPin 校验 PIN",
	0x0a00002e: "应用不存在，请新建或更换已有应用名",
	0x0a00002f: "文件已经存在，请更换或删除现有文件",
	0x0a000030: "存储空间不足，请清理介质后重试",
	0x0a000031: "文件不存在，请新建或更换已有文件名",
	0x0a000032: "已达到最大可管理容器数，请清理介质后重试",
	0x0b000035: "容器不存在，请新建或更换已有容器名",
	0x0b000036: "容器已存在，请更换或删除现有容器",
	0x0d000000: "源数据过长，请核对输入长度",
	0x0d000001: "设备不存在，请确认驱动是否安装或重新插拔 key",
	0x0d000002: "应用打开失败，可能是程序异常，请收集日志反馈",
	0x0d000003: "容器打开失败，可能是程序异常，请收集日志反馈",
	0x0d000004: "容器中无密钥对，请检查介质或重新发证",
	0x0d000005: "加密密钥对结构转换失败，请核对密钥数据",
	0x0d000006: "字段加密失败",
	0x0d000007: "字段解密失败",
	0x0d000008: "写缓存失败",
	0x0d000009: "读缓存失败",
	0x0d00000a: "应用名编码非 UTF-8，请使用 UTF-8 编码",
	0x0d00000b: "容器名编码非 UTF-8，请使用 UTF-8 编码",
	0x0d00000c: "秘钥为空",
	0x8010006c: "智能钥匙服务未就绪，请确认客户端驱动已启动后重试",
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

const LEGACY_CHROME_MAX_VERSION = 109;

function detectLegacyChrome109(): boolean {
	if (typeof navigator === "undefined") return false;
	const ua = navigator.userAgent;
	const edgeMatch = ua.match(/Edg\/(\d+)/);
	const chromeMatch = ua.match(/Chrome\/(\d+)/);
	const criosMatch = ua.match(/CriOS\/(\d+)/); // Chrome on iOS reports CriOS
	const versionStr = edgeMatch?.[1] ?? chromeMatch?.[1] ?? criosMatch?.[1] ?? null;
	if (!versionStr) return false;
	const version = Number.parseInt(versionStr, 10);
	if (Number.isNaN(version)) return false;
	return version > 0 && version <= LEGACY_CHROME_MAX_VERSION;
}

let chrome109TransportPatched = false;
function applyChrome109TransportWorkaround() {
	if (chrome109TransportPatched) return;
	if (!detectLegacyChrome109()) return;
	if (typeof window === "undefined") return;
	const thrift = window.Thrift;
	if (!thrift || !thrift.TXHRTransport || !thrift.TXHRTransport.prototype) {
		return;
	}
	const proto = thrift.TXHRTransport.prototype as any;
	if ((proto.flush as any)?.__chrome109Patched) {
		chrome109TransportPatched = true;
		return;
	}

	const legacyContentType = "text/plain; charset=utf-8";

	const legacyFlush = function (this: any, async?: any, callback?: any) {
		const self = this;
		if ((async && !callback) || this.url === undefined || this.url === "") {
			return this.send_buf;
		}

		const xreq = this.getXmlHttpRequestObject() as XMLHttpRequest;

		if (xreq.overrideMimeType) {
			xreq.overrideMimeType(legacyContentType);
		}

		if (callback) {
			xreq.onreadystatechange = (function () {
				const clientCallback = callback;
				return function (this: XMLHttpRequest) {
					if (this.readyState === 4 && this.status === 200) {
						self.setRecvBuffer(this.responseText);
						clientCallback();
					}
				};
			})();

			xreq.onerror = (function () {
				const clientCallback = callback;
				return function (this: XMLHttpRequest) {
					clientCallback();
				};
			})();
		}

		xreq.open("POST", this.url, !!async);

		if (xreq.setRequestHeader) {
			xreq.setRequestHeader("Accept", "application/vnd.apache.thrift.json; charset=utf-8");
			xreq.setRequestHeader("Content-Type", legacyContentType);
		}

		xreq.send(this.send_buf);
		if (async && callback) {
			return;
		}

		if (xreq.readyState !== 4) {
			throw `encountered an unknown ajax ready state: ${xreq.readyState}`;
		}

		if (xreq.status !== 200) {
			throw `encountered a unknown request status: ${xreq.status}`;
		}

		this.recv_buf = xreq.responseText;
		this.recv_buf_sz = this.recv_buf.length;
		this.wpos = this.recv_buf.length;
		this.rpos = 0;
	};

	(legacyFlush as any).__chrome109Patched = true;
	proto.flush = legacyFlush;
	chrome109TransportPatched = true;
}

async function ensureKoalSdk() {
	if (!koalSdkPromise) {
		koalSdkPromise = (async () => {
			for (const path of KOAL_SCRIPT_PATHS) {
				try {
					await loadScript(path);
				} catch (error) {
					if (IS_DEV) {
						console.error("[koal] 脚本加载失败", path, error);
					}
					throw error;
				}
			}
			applyChrome109TransportWorkaround();
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
		__koalUseMockClient?: () => KoalMiddlewareClient | null;
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
	canSign: boolean;
	missingFields: string[];
};

export type PartialKoalCertificate = Omit<KoalCertificate, "canSign" | "missingFields"> & {
	canSign?: boolean;
	missingFields?: string[];
};

export type KoalSignedPayload = {
	signDataB64: string;
	originDataB64: string;
	signType: KoalCertificate["signType"];
	mdType: string;
	dupCertB64?: string;
};

export type KoalConnectOptions = {
	endpoints?: readonly string[];
	includeDefaults?: boolean;
};

function collectEndpoints(options?: KoalConnectOptions): string[] {
	const groups: ReadonlyArray<readonly string[] | undefined> = [
		options?.endpoints,
		GLOBAL_CONFIG.koalPkiEndpoints,
		options?.includeDefaults === false ? [] : DEFAULT_ENDPOINTS,
	];

	const seen = new Set<string>();
	const result: string[] = [];

	for (const group of groups) {
		if (!group) continue;
		for (const raw of group) {
			const normalized = typeof raw === "string" ? raw.trim() : "";
			if (!normalized || seen.has(normalized)) continue;
			seen.add(normalized);
			result.push(normalized);
		}
	}

	return result;
}

export class KoalMiddlewareClient {
	private readonly transport: any;
	private readonly multiplexer: any;
	private readonly pkiClient: any;
	private readonly devClient: any;
	private readonly signClient: any;
	private session: { sessionID: number; ticket: string } | null = null;

	private constructor(baseUrl: string) {
		this.transport = new window.Thrift.TXHRTransport(baseUrl);
		this.multiplexer = new window.Thrift.Multiplexer();
		this.pkiClient = this.multiplexer.createClient("pkiService", window.pkiServiceClient, this.transport);
		this.devClient = this.multiplexer.createClient("deviceOperator", window.devServiceClient, this.transport);
		this.signClient = this.multiplexer.createClient("signxPlugin", window.signXServiceClient, this.transport);
	}

	static async connect(options?: KoalConnectOptions): Promise<KoalMiddlewareClient> {
		if (IS_DEV && typeof window !== "undefined") {
			const mockFactory = window.__koalUseMockClient;
			if (typeof mockFactory === "function") {
				const mockClient = mockFactory();
				if (mockClient) {
					console.info("[koal] 使用模拟客户端");
					return mockClient;
				}
			}
		}

		await ensureKoalSdk();

		const endpoints = collectEndpoints(options);
		if (IS_DEV) {
			console.info("[koal] attempting endpoints", endpoints);
		}

		if (!endpoints.length) {
			throw new Error("未配置可用的 PKI 中间件地址，请检查环境变量 VITE_KOAL_PKI_ENDPOINTS");
		}

		const errors: Error[] = [];

		for (const url of endpoints) {
			try {
				const client = new KoalMiddlewareClient(url);
				await client.login();
				if (IS_DEV) {
					console.info(`[koal] connected via ${url}`);
				}
				return client;
			} catch (error) {
				if (IS_DEV) {
					console.warn(`[koal] ${url} 登录失败`, error);
				}
				if (error instanceof Error) {
					errors.push(new Error(`${url}：${error.message}`));
				} else {
					errors.push(new Error(`${url}：未知错误`));
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
			if (IS_DEV) {
				console.info("[koal] 未返回证书列表", payload);
			}
			return [];
		}
		if (IS_DEV) {
			console.info("[koal] 原始证书列表", payload.certs);
		}

		const filtered = payload.certs
			.filter((item: any) => filterCertificate(item))
			.map((item: any, index: number) => normalizeCertificate(item, index));

		if (IS_DEV) {
			console.info("[koal] 过滤后证书", filtered);
		}

		return filtered;
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
		let mdType = "3"; // 默认 SM3
		if (cert.signType === "RSA" || cert.signType === "PM-BD") {
			mdType = "2"; // SHA1 与厂商示例一致
		}

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
		const dupCertB64: Nullable<string> = payload?.dupCert ?? payload?.dupCertB64 ?? payload?.dup_cert;
		return {
			signDataB64: String(signDataB64).trim(),
			originDataB64,
			signType: cert.signType,
			mdType,
			dupCertB64: dupCertB64 ? String(dupCertB64).trim() : undefined,
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
	const keyUsageRaw = item?.keyUsage ?? item?.KeyUsage;
	const keyUsageNumber = typeof keyUsageRaw === "string" ? Number(keyUsageRaw) : Number(keyUsageRaw ?? Number.NaN);
	const signFlagRaw = item?.signFlag ?? item?.SignFlag;
	const signFlag = typeof signFlagRaw === "string" ? Number(signFlagRaw) : Number(signFlagRaw ?? 1);
	if (IS_DEV) {
		const hints: string[] = [];
		if (Number.isNaN(keyUsageNumber)) {
			hints.push(`keyUsage 未解析 -> ${String(keyUsageRaw)}`);
		} else if (keyUsageNumber !== 1) {
			hints.push(`keyUsage=${keyUsageNumber}`);
		}
		if (!Number.isNaN(signFlag) && signFlag !== 1) {
			hints.push(`signFlag=${signFlag}`);
		}
		if (hints.length > 0) {
			console.info("[koal] 非标准签名证书仍返回给前端", hints.join("; "), item);
		}
	}
	return true;
}

function normalizeCertificate(item: Record<string, any>, index = 0): KoalCertificate {
	const subjectCn = item?.subjectName?.CN ?? item?.subject ?? item?.Subject ?? "";
	const issuerCn = item?.issuerName?.CN ?? item?.issuer ?? item?.Issuer ?? "";
	const signHint = String(item?.certType ?? item?.certAlgorithm ?? item?.algName ?? "").toUpperCase();

	const manufacturer = String(item?.manufacturer ?? item?.Manufacturer ?? item?.Vendor ?? "").trim();

	const devId =
		firstNonBlank(item?.devID, item?.devId, item?.deviceId, item?.deviceID, item?.device, item?.DeviceID) ?? "";
	const appName = firstNonBlank(item?.appName, item?.AppName, item?.appname, item?.application) ?? "";
	const conName =
		firstNonBlank(
			item?.containerName,
			item?.container,
			item?.conName,
			item?.ConName,
			item?.container_name,
			item?.containerID
		) ?? "";
	const sn = firstNonBlank(item?.SN, item?.sn, item?.serialNumber, item?.SerialNumber) ?? "";

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
  const signFlagRaw = item?.signFlag ?? item?.SignFlag;
  const signFlag = typeof signFlagRaw === "string" ? Number(signFlagRaw) : Number(signFlagRaw ?? undefined);

	const idParts = [devId, appName, conName, sn].filter((part) => typeof part === "string" && part.trim() !== "");
	let id = idParts.join("::");
	if (!id) {
		id = `cert-${index}`;
	}

  const missingFields: string[] = [];
  if (!devId) missingFields.push("devId");
  if (!appName) missingFields.push("appName");
  if (!conName) missingFields.push("conName");

  // Heuristics for signable certificates:
  // - signFlag (if provided by middleware) must be 1
  // - keyUsage (if provided) should be 1 (digitalSignature) — other values treated as non-signing
  // - device identifiers must be present
  let signableByFlags = true;
  if (Number.isFinite(signFlag)) {
    signableByFlags = (Number(signFlag) === 1);
  }
  if (Number.isFinite(keyUsage)) {
    // Common vendor convention: 1 => digitalSignature; other values denote encipherment/nonRepudiation etc.
    signableByFlags = signableByFlags && (Number(keyUsage) === 1);
  }

  const canSign = missingFields.length === 0 && signableByFlags;

  const cert: KoalCertificate = {
		id,
		devId,
		appName,
		conName,
		subjectCn: String(subjectCn ?? ""),
		issuerCn: String(issuerCn ?? ""),
		sn: String(sn ?? ""),
		manufacturer,
    keyUsage: Number.isNaN(keyUsage) ? undefined : keyUsage,
    certType: item?.certType ? String(item.certType) : undefined,
    signType,
    raw: item,
    canSign,
    missingFields,
  };
	return cert;
}

function firstNonBlank(...candidates: Array<unknown>): string | null {
	for (const candidate of candidates) {
		if (candidate === null || candidate === undefined) continue;
		const value = String(candidate).trim();
		if (value) {
			return value;
		}
	}
	return null;
}

function parseJson(value: Nullable<string>): any {
	if (!value) return null;
	try {
		return JSON.parse(value);
	} catch (_error) {
		return null;
	}
}

function normalizeErrCode(raw: number): number {
	if (!Number.isFinite(raw)) return 0;
	// Ensure we treat vendor返回的 32 位有符号整数为无符号
	const unsigned = raw >>> 0;
	return unsigned;
}

function mapKoalError(code: number, fallback?: Nullable<string>): string {
	const normalized = normalizeErrCode(code);
	const known = RESULT_ERROR_MESSAGES[normalized];
	if (known) return known;
	if (fallback) {
		try {
			const payload = JSON.parse(fallback);
			if (payload?.message) return String(payload.message);
		} catch {
			// ignore
		}
	}
	return `中间件返回错误码 0x${normalized.toString(16).toUpperCase().padStart(8, "0")}`;
}

export function formatKoalError(error: unknown): string {
	if (error instanceof Error) return error.message;
	return typeof error === "string" ? error : "操作失败，请重试";
}

export const KoalErrorMessages = RESULT_ERROR_MESSAGES;

export async function preloadKoalSdk(): Promise<void> {
	await ensureKoalSdk();
}
