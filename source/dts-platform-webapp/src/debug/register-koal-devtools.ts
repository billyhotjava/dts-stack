import {
	KoalMiddlewareClient,
	formatKoalError,
	preloadKoalSdk,
	type KoalCertificate,
	type KoalConnectOptions,
	type KoalSignedPayload,
} from "@/api/services/koalPkiClient";

type KoalMockOptions = {
	certificates?: Array<Partial<KoalCertificate>>;
	pinValidator?: (pin: string, cert: KoalCertificate) => boolean | Promise<boolean>;
	signData?: (plainText: string, cert: KoalCertificate) => KoalSignedPayload | Promise<KoalSignedPayload>;
	exportCertificate?: (cert: KoalCertificate) => string | Promise<string>;
};

type KoalDevtools = {
	preload: () => Promise<void>;
	connect: (options?: KoalConnectOptions) => Promise<KoalMiddlewareClient>;
	ensureClient: (options?: KoalConnectOptions) => Promise<KoalMiddlewareClient>;
	getActiveClient: () => KoalMiddlewareClient | null;
	logout: () => Promise<void>;
	listCertificates: (options?: KoalConnectOptions) => Promise<KoalCertificate[]>;
	formatError: (error: unknown) => string;
	KoalMiddlewareClient: typeof KoalMiddlewareClient;
	enableMock: (options?: KoalMockOptions) => void;
	disableMock: () => void;
	isMockEnabled: () => boolean;
	getMockCertificates: () => KoalCertificate[];
};

declare global {
	interface Window {
		__koalDevtools?: KoalDevtools;
		__koalUseMockClient?: () => KoalMiddlewareClient | null;
	}
}

if (import.meta.env.DEV && typeof window !== "undefined") {
	let activeClient: KoalMiddlewareClient | null = null;
	let mockOptions: KoalMockOptions | null = null;

	const setActiveClient = (client: KoalMiddlewareClient) => {
		activeClient = client;
		return client;
	};

	const ensureClient = async (options?: KoalConnectOptions) => {
		if (activeClient) {
			return activeClient;
		}
		return setActiveClient(await KoalMiddlewareClient.connect(options));
	};

	const normalizeCertificate = (partial: Partial<KoalCertificate>, index: number): KoalCertificate => {
		const id = partial.id ?? `mock-cert-${index + 1}`;
		return {
			id,
			devId: partial.devId ?? `dev-${index + 1}`,
			appName: partial.appName ?? "MockApp",
			conName: partial.conName ?? `container-${index + 1}`,
			subjectCn: partial.subjectCn ?? `测试用户${index + 1}`,
			issuerCn: partial.issuerCn ?? "Mock CA",
			sn: partial.sn ?? `SN-${index + 1}`,
			manufacturer: partial.manufacturer ?? "MOCK",
			keyUsage: partial.keyUsage ?? 1,
			certType: partial.certType ?? "SM2",
			signType: partial.signType ?? "SM2",
			raw: partial.raw ?? partial,
		};
	};

	const createMockClient = (): KoalMiddlewareClient => {
		const options = mockOptions ?? {};
		const inputCerts = options.certificates ?? [];
		const normalized =
			inputCerts.length > 0 ? inputCerts.map((cert, index) => normalizeCertificate(cert, index)) : [normalizeCertificate({}, 0)];

		const mockClient: Partial<KoalMiddlewareClient> = {
			async logout() {
				/* noop */
			},
			async listCertificates() {
				console.info("[koal-mock] 返回模拟证书", normalized);
				return normalized;
			},
			async verifyPin(cert, pin) {
				if (!options.pinValidator) return;
				const ok = await options.pinValidator(pin, cert);
				if (!ok) {
					throw new Error("模拟 PIN 验证失败");
				}
			},
			async signData(cert, plainText) {
				if (options.signData) {
					return options.signData(plainText, cert);
				}
				const originDataB64 = window.Base64?.encode?.(plainText) ?? btoa(plainText);
				return {
					signDataB64: originDataB64,
					originDataB64,
					signType: cert.signType ?? "SM2",
					mdType: cert.signType === "RSA" ? "4" : "3",
				};
			},
			async exportCertificate(cert) {
				if (options.exportCertificate) {
					return options.exportCertificate(cert);
				}
				return btoa(`mock-cert-${cert.id}`);
			},
		};

		return mockClient as KoalMiddlewareClient;
	};

	const enableMock = (options?: KoalMockOptions) => {
		mockOptions = options ?? {};
		activeClient = null;
		window.__koalUseMockClient = () => createMockClient();
		console.info("[koal-mock] 已启用模拟客户端");
	};

	const disableMock = () => {
		mockOptions = null;
		activeClient = null;
		if (window.__koalUseMockClient) {
			delete window.__koalUseMockClient;
		}
		console.info("[koal-mock] 已关闭模拟客户端");
	};

	const devtools: KoalDevtools = {
		preload: preloadKoalSdk,
		connect: async (options) => setActiveClient(await KoalMiddlewareClient.connect(options)),
		ensureClient,
		getActiveClient: () => activeClient,
		async logout() {
			if (!activeClient) return;
			try {
				await activeClient.logout();
			} finally {
				activeClient = null;
			}
		},
		async listCertificates(options) {
			const client = await ensureClient(options);
			return client.listCertificates();
		},
		formatError: formatKoalError,
		KoalMiddlewareClient,
		enableMock,
		disableMock,
		isMockEnabled: () => mockOptions !== null,
		getMockCertificates: () =>
			mockOptions?.certificates?.map((cert, index) => normalizeCertificate(cert, index)) ?? [],
	};

	window.__koalDevtools = devtools;

	console.info(
		`[koal-devtools] 已注册。示例: const client = await window.__koalDevtools.connect(); await window.__koalDevtools.listCertificates();`,
	);
}
