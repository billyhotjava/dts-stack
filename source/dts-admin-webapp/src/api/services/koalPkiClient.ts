// Minimal Koal PKI client (copied/trimmed from platform-webapp)
// Provides connect/listCertificates/verifyPin/signData/exportCertificate

import { GLOBAL_CONFIG } from "@/global-config";
const IS_DEV = typeof import.meta !== "undefined" && Boolean((import.meta as any)?.env?.DEV);

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
  "https://127.0.0.1:18080/multiplex",
  "http://127.0.0.1:18080/multiplex",
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
    "deviceOperator.js",
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
      return; // loaded successfully from this base
    } catch (e) {
      lastErr = e;
      // try next base
    }
  }
  throw lastErr || new Error("未能加载 PKI 前端 SDK 脚本");
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

    const errors: Error[] = [];
    for (const url of endpoints) {
      try {
        const client = new KoalMiddlewareClient(url);
        await client.login();
        if (IS_DEV) console.info(`[koal] connected via ${url}`);
        return client;
      } catch (e) {
        errors.push(new Error(`${url}：${(e as Error).message}`));
      }
    }
    const agg = errors.map((e) => e.message).join("; ");
    throw new Error(`无法连接到 PKI 中间件：${agg}`);
  }

  private async login(): Promise<void> {
    const resp = await this.pkiClient.login({ appName: "dts-admin-webapp" });
    const session = resp?.session || resp;
    if (!session || !Number.isFinite(session.sessionID)) throw new Error("中间件登录失败");
    this.session = { sessionID: Number(session.sessionID), ticket: String(session.ticket || "") };
  }

  async logout(): Promise<void> {
    try {
      if (this.session) await this.pkiClient.logout(this.session.sessionID);
    } catch {}
    this.session = null;
  }

  async listCertificates(): Promise<KoalCertificate[]> {
    const rawList = await this.devClient.listCertificates?.() ?? [];
    const out: KoalCertificate[] = [];
    for (let i = 0; i < rawList.length; i++) {
      out.push(normalizeCertificate(rawList[i], i));
    }
    return out.filter((c) => c.canSign);
  }

  async verifyPin(cert: KoalCertificate, pin: string): Promise<void> {
    const ok = await this.devClient.verifyPin?.(cert.raw, pin);
    if (ok === false) throw new Error("PIN 验证失败");
  }

  async signData(cert: KoalCertificate, plainText: string): Promise<KoalSignedPayload> {
    const resp = await this.signClient.sign?.(cert.raw, plainText);
    const originDataB64 = String(resp?.originDataB64 || resp?.origin || btoa(plainText));
    const signDataB64 = String(resp?.signDataB64 || resp?.signature || "");
    if (!signDataB64) throw new Error("未返回签名数据");
    const mdType = String(resp?.mdType || "");
    const dupCertB64 = resp?.dupCertB64 ? String(resp.dupCertB64) : undefined;
    const signType = (normalizeCertificate(cert.raw).signType) as KoalCertificate["signType"];
    return { originDataB64, signDataB64, signType, mdType, dupCertB64 };
  }

  async exportCertificate(cert: KoalCertificate): Promise<string> {
    const certB64 = await this.devClient.exportCertificate?.(cert.raw);
    if (!certB64) throw new Error("未获取到证书内容");
    return String(certB64);
  }
}

function normalizeCertificate(item: Record<string, any>, index = 0): KoalCertificate {
  const subjectCn = item?.subjectName?.CN ?? item?.subject ?? item?.Subject ?? "";
  const issuerCn = item?.issuerName?.CN ?? item?.issuer ?? item?.Issuer ?? "";
  const signHint = String(item?.certType ?? item?.certAlgorithm ?? item?.algName ?? "").toUpperCase();
  const manufacturer = String(item?.manufacturer ?? item?.Manufacturer ?? item?.Vendor ?? "").trim();
  const devId = firstNonBlank(item?.devID, item?.devId, item?.deviceId, item?.deviceID, item?.device, item?.DeviceID) ?? "";
  const appName = firstNonBlank(item?.appName, item?.AppName, item?.appname, item?.application) ?? "";
  const conName = firstNonBlank(item?.containerName, item?.container, item?.conName, item?.ConName, item?.container_name, item?.containerID) ?? "";
  const sn = firstNonBlank(item?.SN, item?.sn, item?.serialNumber, item?.SerialNumber) ?? "";

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
