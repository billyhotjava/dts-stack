// Minimal Koal PKI client (copied/trimmed from platform-webapp)
// Provides connect/listCertificates/verifyPin/signData/exportCertificate

import { GLOBAL_CONFIG } from "@/global-config";
const IS_DEV = typeof import.meta !== "undefined" && Boolean((import.meta as any)?.env?.DEV);

function isDebug(): boolean {
  try {
    // 开启条件：开发模式 或 运行时 __RUNTIME_CONFIG__.pkiDebug 为 'true'/'1'
    const rc: any = (typeof window !== "undefined" && (window as any).__RUNTIME_CONFIG__) || {};
    const flag = String(rc.pkiDebug ?? "").trim().toLowerCase();
    return IS_DEV || flag === "1" || flag === "true" || flag === "yes" || flag === "on";
  } catch {
    return IS_DEV;
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

export type KoalSignedPayload = {
  signDataB64: string;
  originDataB64: string;
  signType: KoalCertificate["signType"];
  mdType: string;
  dupCertB64?: string;
};

export type KoalConnectOptions = { endpoints?: readonly string[]; includeDefaults?: boolean };

const DEFAULT_ENDPOINTS = [
  // Align with platform-webapp defaults; Koal agent usually listens on 16080/18080
  "https://127.0.0.1:16080",
  "http://127.0.0.1:18080",
];

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

function derivePlatformVendorBase(): string | null {
  if (typeof window === "undefined") return null;
  try {
    const loc = window.location;
    const host = String(loc.hostname || "");
    const prefix = "biadmin.";
    if (host.startsWith(prefix)) {
      const baseDomain = host.substring(prefix.length);
      if (baseDomain) {
        return `${loc.protocol}//bi.${baseDomain}/vendor/koal`;
      }
    }
  } catch {}
  return null;
}

async function ensureKoalSdk(): Promise<void> {
  if (typeof window !== "undefined" && (window as any).Thrift) return;
  const bases: string[] = [
    "/koal",
    "/vendor/koal",
  ];
  const alt = derivePlatformVendorBase();
  if (alt) bases.push(alt);

  const files = [
    // Core thrift runtime + helpers
    "thrift.js",
    "base64.js",
    // Thrift generated type definitions (some vendor SDKs require them)
    "commdef_types.js",
    "pkiService_types.js",
    "devService_types.js",
    "signXService_types.js",
    // Service proxies
    "pkiService.js",
    // The SDK provides devService.js (client exported as window.devServiceClient)
    // Older naming like deviceOperator.js is not present in our bundled vendor assets
    "devService.js",
    "signXService.js",
  ];
  let lastErr: any = null;
  for (const base of bases) {
    try {
      for (const f of files) {
        const src = `${base}/${f}`;
        // eslint-disable-next-line no-await-in-loop
        await new Promise<void>((resolve, reject) => {
          const el = document.createElement("script");
          el.src = src;
          el.onload = () => resolve();
          el.onerror = () => reject(new Error(`Failed to load ${src}`));
          document.head.appendChild(el);
        });
      }
      if (isDebug()) console.info("[pki-sdk] loaded from base", base);
      return; // loaded successfully from this base
    } catch (e) {
      lastErr = e;
      if (isDebug()) console.warn("[pki-sdk] load failed at base", base, e);
      // try next base
    }
  }
  throw lastErr || new Error("未能加载 PKI 前端 SDK 脚本");
}

// Error code mapping (subset from platform-webapp for better messages)
const RESULT_ERROR_MESSAGES: Record<number, string> = {
  0x00000000: "调用成功",
  0x00000001: "session 不存在，请先调用 login 接口",
  0x00000006: "app 已经登录，建议业务完成后调用 logout 接口",
  0x00000007: "超时：调用耗时超过 30 秒，可能程序阻塞或存在人工交互",
  0x0a000000: "未知错误",
  0x0a000006: "无效的参数，请对照接口文档检查",
  0x0a00001c: "未发现证书：请在驱动工具中确认证书是否存在",
  0x0a000020: "缓冲区不足",
  0x0a000024: "PIN 错误：输入的 PIN 与预置值不匹配",
  0x0a000025: "PIN 锁死：错误次数过多，请使用驱动工具或调用 unlockPIN 解锁",
  0x0b000035: "容器不存在，请新建或更换已有容器名",
  0x8010006c: "智能钥匙服务未就绪，请确认客户端驱动已启动后重试",
};

function parseJson(value?: string | null): any {
  if (!value) return null;
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}

function normalizeErrCode(raw: number): number {
  if (!Number.isFinite(raw)) return 0;
  return raw >>> 0; // ensure unsigned
}

function mapKoalError(code: number, jsonBody?: string | null): string {
  const normalized = normalizeErrCode(code);
  const known = RESULT_ERROR_MESSAGES[normalized];
  if (known) return known;
  if (jsonBody) {
    const payload = parseJson(jsonBody);
    if (payload?.message) return String(payload.message);
  }
  return `中间件返回错误码 0x${normalized.toString(16).toUpperCase().padStart(8, "0")}`;
}

export class KoalMiddlewareClient {
  private readonly transport: any;
  private readonly multiplexer: any;
  private readonly pkiClient: any;
  private readonly devClient: any;
  private readonly signClient: any;
  private session: { sessionID: number; ticket: string } | null = null;

  private constructor(baseUrl: string) {
    // window.Thrift is provided by koal SDK scripts
    // @ts-ignore
    this.transport = new window.Thrift.TXHRTransport(baseUrl);
    // @ts-ignore
    this.multiplexer = new window.Thrift.Multiplexer();
    // @ts-ignore
    this.pkiClient = this.multiplexer.createClient("pkiService", window.pkiServiceClient, this.transport);
    // @ts-ignore
    this.devClient = this.multiplexer.createClient("deviceOperator", window.devServiceClient, this.transport);
    // @ts-ignore
    this.signClient = this.multiplexer.createClient("signxPlugin", window.signXServiceClient, this.transport);
  }

  static async connect(options?: KoalConnectOptions): Promise<KoalMiddlewareClient> {
    await ensureKoalSdk();
    const endpoints = collectEndpoints(options);
    if (!endpoints.length) throw new Error("未配置可用的 PKI 中间件地址");
    if (isDebug()) console.info("[pki] endpoints", endpoints);
    const errors: Error[] = [];
    for (const url of endpoints) {
      try {
        const client = new KoalMiddlewareClient(url);
        await client.login();
        if (isDebug()) console.info("[pki] connected via", url);
        return client;
      } catch (e) {
        errors.push(new Error(`${url}：${(e as Error).message}`));
      }
    }
    const agg = errors.map((e) => e.message).join("; ");
    throw new Error(`无法连接到 PKI 中间件：${agg}`);
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
    // @ts-ignore
    const req = new window.msgRequest();
    // simple counter for reqid
    const now = Date.now() & 0xffffffff;
    // @ts-ignore
    req.reqid = now >>> 0;
    // @ts-ignore
    req.msgType = msgType;
    // @ts-ignore
    req.version = 1;
    // @ts-ignore
    req.extend = 0;
    // @ts-ignore
    req.jsonBody = body ? JSON.stringify(body) : "{}";
    return req;
  }

  private buildTicket() {
    if (!this.session) throw new Error("尚未建立 PKI 会话");
    // @ts-ignore
    const ticket = new window.sessionTicket();
    // @ts-ignore
    ticket.sessionID = this.session.sessionID;
    // @ts-ignore
    ticket.ticket = this.session.ticket;
    return ticket;
  }

  private async login(): Promise<void> {
    // Use the same vendor demo credentials as platform-webapp
    const credentials = {
      appName: "ZF-App",
      appID: "7ea7b92a-3091-3d79-639f-3ef4c3e2d6d7",
      token: "7ea7b92a-3091-3d79-639f-3ef4c3e2d6d7",
    };
    const request = this.buildRequest(0x01, credentials);
    const response: any = await this.thriftCall<any>(this.pkiClient, "login", request);
    const code = Number(response?.errCode ?? 0);
    if (code !== 0 && code !== 6) throw new Error(mapKoalError(code, response?.jsonBody));
    const payload = parseJson(response?.jsonBody);
    if (!payload?.sessionID || !payload?.ticket) throw new Error("PKI 中间件响应缺少 session 信息");
    this.session = { sessionID: Number(payload.sessionID), ticket: String(payload.ticket) };
  }

  async logout(): Promise<void> {
    try {
      if (this.session) {
        const ticket = this.buildTicket();
        await this.thriftCall<any>(this.pkiClient, "logout", ticket);
      }
    } catch {}
    this.session = null;
  }

  async listCertificates(): Promise<KoalCertificate[]> {
    const ticket = this.buildTicket();
    const request = this.buildRequest(0x28);
    const response: any = await this.thriftCall<any>(this.devClient, "getAllCert", ticket, request);
    const code = Number(response?.errCode ?? 0);
    if (code !== 0) throw new Error(mapKoalError(code, response?.jsonBody));
    const payload = parseJson(response?.jsonBody);
    const list = Array.isArray(payload?.certs) ? payload.certs : [];
    const out: KoalCertificate[] = [];
    for (let i = 0; i < list.length; i++) out.push(normalizeCertificate(list[i], i));
    if (isDebug()) {
      const snap = out.map((c, i) => ({
        i,
        id: c.id,
        devId: c.devId,
        appName: c.appName,
        conName: c.conName,
        snTail: c.sn ? c.sn.slice(-8) : "",
        subjectCn: c.subjectCn,
        issuerCn: c.issuerCn,
        manufacturer: c.manufacturer,
        signType: c.signType,
        keyUsage: c.keyUsage,
        canSign: c.canSign,
        missing: c.missingFields,
      }));
      console.info("[pki] raw certs=", list.length, "normalized=", out.length, snap);
    }
    return out.filter((c) => c.canSign);
  }

  async verifyPin(cert: KoalCertificate, pin: string): Promise<void> {
    const ticket = this.buildTicket();
    const request = this.buildRequest(0x18, {
      devID: cert.devId,
      appName: cert.appName,
      PINType: "1",
      PIN: pin,
    });
    const response: any = await this.thriftCall<any>(this.devClient, "verifyPIN", ticket, request);
    const code = Number(response?.errCode ?? 0);
    if (code !== 0) throw new Error(mapKoalError(code, response?.jsonBody));
    if (isDebug()) console.info("[pki] PIN verified for", { id: cert.id, devId: cert.devId, conName: cert.conName });
  }

  async signData(cert: KoalCertificate, plainText: string): Promise<KoalSignedPayload> {
    const ticket = this.buildTicket();
    const originDataB64 = (window as any)?.Base64?.encode?.(plainText) ?? btoa(plainText);
    const signTypeCode = cert.signType === "PM-BD" ? "1" : "2";
    let mdType = "3"; // 默认 SM3
    if (cert.signType === "RSA" || cert.signType === "PM-BD") mdType = "2"; // SHA1
    const request = this.buildRequest(0x10, {
      devID: cert.devId,
      appName: cert.appName,
      conName: cert.conName,
      srcData: originDataB64,
      isBase64SrcData: "1",
      type: signTypeCode,
      mdType,
    });
    const response: any = await this.thriftCall<any>(this.signClient, "signData", ticket, request);
    const code = Number(response?.errCode ?? 0);
    if (code !== 0) throw new Error(mapKoalError(code, response?.jsonBody));
    const payload = parseJson(response?.jsonBody);
    const signDataB64: string | undefined = payload?.b64signData ?? payload?.signData;
    if (!signDataB64) throw new Error("签名失败：缺少签名数据");
    const dupCertB64: string | undefined = payload?.dupCert ?? payload?.dupCertB64 ?? payload?.dup_cert;
    const res = { originDataB64, signDataB64: String(signDataB64).trim(), signType: cert.signType, mdType, dupCertB64 };
    if (isDebug()) console.info("[pki] signed", {
      id: cert.id,
      mdType,
      signType: cert.signType,
      originLen: originDataB64.length,
      signLen: res.signDataB64.length,
      dupCertLen: res.dupCertB64 ? res.dupCertB64.length : 0,
    });
    return res;
  }

  async exportCertificate(cert: KoalCertificate): Promise<string> {
    const ticket = this.buildTicket();
    const request = this.buildRequest(0x22, {
      devID: cert.devId,
      appName: cert.appName,
      containerName: cert.conName,
      signFlag: "1",
    });
    const response: any = await this.thriftCall<any>(this.devClient, "exportCertificate", ticket, request);
    const code = Number(response?.errCode ?? 0);
    if (code !== 0) throw new Error(mapKoalError(code, response?.jsonBody));
    const payload = parseJson(response?.jsonBody);
    const certB64: string | undefined = payload?.cert;
    if (!certB64) throw new Error("未获取到证书内容");
    const out = String(certB64);
    if (isDebug()) console.info("[pki] export cert length", out.length, { id: cert.id });
    return out;
  }
}

function normalizeCertificate(item: Record<string, any>, index = 0): KoalCertificate {
  const subjectCn = item?.subjectName?.CN ?? item?.subject ?? item?.Subject ?? "";
  const issuerCn = item?.issuerName?.CN ?? item?.issuer ?? item?.Issuer ?? "";
  const signHint = String(item?.certType ?? item?.certAlgorithm ?? item?.algName ?? "").toUpperCase();
  const manufacturer = String(
    firstNonBlank(
      item?.manufacturer,
      item?.Manufacturer,
      item?.Vendor,
      item?.vendor,
      item?.vendorName,
      item?.VendorName,
    ) ?? "",
  ).trim();
  const devId =
    firstNonBlank(
      item?.devID,
      item?.devId,
      item?.deviceId,
      item?.deviceID,
      item?.device,
      item?.DeviceID,
      item?.deviceSN,
      item?.deviceSerial,
    ) ?? "";
  const appName = firstNonBlank(item?.appName, item?.AppName, item?.appname, item?.application, item?.applicationName) ?? "";
  const conName =
    firstNonBlank(
      item?.containerName,
      item?.container,
      item?.conName,
      item?.ConName,
      item?.container_name,
      item?.containerID,
      item?.containerId,
      item?.KeyContainerName,
    ) ?? "";
  const sn =
    firstNonBlank(
      item?.SN,
      item?.sn,
      item?.serialNumber,
      item?.SerialNumber,
      item?.Serial,
      item?.serial,
      item?.SerialNo,
      item?.serialNo,
      item?.SerialNoHex,
      item?.certSN,
      item?.CertSN,
      item?.CertSerial,
    ) ?? "";

  let signType: KoalCertificate["signType"] = "UNKNOWN";
  if (signHint.includes("PM") || signHint.includes("P7")) signType = "PM-BD";
  else if (signHint.includes("SM2")) signType = "SM2";
  else if (signHint.includes("RSA")) signType = "RSA";
  else signType = "SM2";

  const keyUsageRaw = item?.keyUsage ?? item?.KeyUsage;
  const keyUsage = typeof keyUsageRaw === "string" ? Number(keyUsageRaw) : Number(keyUsageRaw ?? undefined);
  const signFlagRaw = item?.signFlag ?? item?.SignFlag;
  const signFlag = typeof signFlagRaw === "string" ? Number(signFlagRaw) : Number(signFlagRaw ?? undefined);

  const idParts = [devId, appName, conName, sn].filter((part) => typeof part === "string" && part.trim() !== "");
  let id = idParts.join("::");
  if (!id) id = `cert-${index}`;

  const missingFields: string[] = [];
  if (!devId) missingFields.push("devId");
  if (!appName) missingFields.push("appName");
  if (!conName) missingFields.push("conName");

  let signableByFlags = true;
  if (Number.isFinite(signFlag)) signableByFlags = Number(signFlag) === 1;
  if (Number.isFinite(keyUsage)) signableByFlags = signableByFlags && Number(keyUsage) === 1;

  const canSign = missingFields.length === 0 && signableByFlags;

  return {
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
}

function firstNonBlank(...candidates: Array<unknown>): string | null {
  for (const candidate of candidates) {
    if (candidate === null || candidate === undefined) continue;
    const value = String(candidate).trim();
    if (value) return value;
  }
  return null;
}
