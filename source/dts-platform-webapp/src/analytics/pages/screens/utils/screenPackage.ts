import { countInlinedResources } from "./resourceRestorer";
import { resolveScreenImageUploadUrl } from "./screenImageUpload";

export const SCREEN_PACKAGE_SCHEMA = "dts.screen.package";
export const SCREEN_PACKAGE_VERSION = 1;
export const SCREEN_PACKAGE_SCREEN_FILE = "screen.json";
export const SCREEN_PACKAGE_MANIFEST_FILE = "manifest.json";

type ScreenPackageResource = {
	kind: "image";
	originalUrl: string;
	path: string;
	contentType?: string;
};

type ScreenPackageManifest = {
	schema: typeof SCREEN_PACKAGE_SCHEMA;
	version: number;
	exportedAt?: string;
	resources: ScreenPackageResource[];
};

export type ScreenImportFileResult = {
	kind: "json" | "zip";
	fileName: string;
	payload: Record<string, unknown>;
	source: Record<string, unknown>;
	templateMeta?: { name: string; description?: string; category?: string; tags?: string[] };
	resourcesInlined: boolean;
	inlinedResourceCount: number;
	restoredResourceCount: number;
	warnings: string[];
};

export type BuildScreenPackageOptions = {
	screenName: string;
	screenSpec: Record<string, unknown>;
	templateMeta?: { name: string; description?: string; category?: string; tags?: string[] };
	fetchResource?: (url: string) => Promise<Blob>;
	now?: () => Date;
};

export type BuildScreenPackageResult = {
	blob: Blob;
	resourceCount: number;
	warnings: string[];
};

export type ParseScreenImportFileOptions = {
	uploadImage?: (blob: Blob, fileName: string, contentType: string) => Promise<string>;
};

const IMAGE_URL_PATTERN = /\.(png|jpe?g|gif|svg|webp)(\?.*)?(#.*)?$/i;
const INTERNAL_IMAGE_URL_PATTERN = /^(\/api\/infra\/screen-images\/|\/infra\/screen-images\/|\/analytics\/|\/?uploads\/)/i;

async function createZip() {
	const { default: JSZip } = await import("jszip");
	return new JSZip();
}

async function loadZip(data: ArrayBuffer) {
	const { default: JSZip } = await import("jszip");
	return JSZip.loadAsync(data);
}

function isRecord(value: unknown): value is Record<string, unknown> {
	return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function cloneRecord<T extends Record<string, unknown>>(value: T): T {
	return JSON.parse(JSON.stringify(value)) as T;
}

function isScreenPackageFile(file: File): boolean {
	const name = file.name.toLowerCase();
	const type = String(file.type || "").toLowerCase();
	return name.endsWith(".zip") || type.includes("zip");
}

function isPackageableImageUrl(value: unknown): value is string {
	if (typeof value !== "string") return false;
	const trimmed = value.trim();
	if (!trimmed || trimmed.startsWith("data:")) return false;
	if (/^https?:\/\//i.test(trimmed)) return false;
	return INTERNAL_IMAGE_URL_PATTERN.test(trimmed) || IMAGE_URL_PATTERN.test(trimmed);
}

function collectPackageableImageUrls(spec: unknown): string[] {
	const seen = new Set<string>();
	const urls: string[] = [];

	function scan(value: unknown): void {
		if (Array.isArray(value)) {
			value.forEach(scan);
			return;
		}
		if (!isRecord(value)) return;
		for (const item of Object.values(value)) {
			if (isPackageableImageUrl(item)) {
				const url = item.trim();
				if (!seen.has(url)) {
					seen.add(url);
					urls.push(url);
				}
				continue;
			}
			scan(item);
		}
	}

	scan(spec);
	return urls;
}

function stripQueryAndHash(url: string): string {
	return url.split("?")[0]?.split("#")[0] || url;
}

function sanitizePackageFileName(name: string, fallback: string): string {
	const decoded = (() => {
		try {
			return decodeURIComponent(name);
		} catch {
			return name;
		}
	})();
	const cleaned = decoded
		.trim()
		.replace(/[\\/:*?"<>|]+/g, "_")
		.replace(/^\.+/, "")
		.replace(/\s+/g, "_");
	return cleaned || fallback;
}

function fileNameFromUrl(url: string, index: number): string {
	const path = stripQueryAndHash(url);
	const rawName = path.split("/").filter(Boolean).pop() || `resource-${index + 1}.png`;
	return sanitizePackageFileName(rawName, `resource-${index + 1}.png`);
}

function uniqueImagePath(fileName: string, usedPaths: Set<string>): string {
	const dotIndex = fileName.lastIndexOf(".");
	const base = dotIndex > 0 ? fileName.slice(0, dotIndex) : fileName;
	const ext = dotIndex > 0 ? fileName.slice(dotIndex) : "";
	let candidate = `resources/images/${fileName}`;
	let suffix = 2;
	while (usedPaths.has(candidate)) {
		candidate = `resources/images/${base}-${suffix}${ext}`;
		suffix++;
	}
	usedPaths.add(candidate);
	return candidate;
}

function contentTypeFromFileName(fileName: string): string {
	const lower = fileName.toLowerCase();
	if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
	if (lower.endsWith(".gif")) return "image/gif";
	if (lower.endsWith(".svg")) return "image/svg+xml";
	if (lower.endsWith(".webp")) return "image/webp";
	return "image/png";
}

function normalizeResourceFetchUrl(url: string): string {
	return url.startsWith("/infra/") ? `/api${url}` : url;
}

async function defaultFetchResource(url: string): Promise<Blob> {
	const { fetchWithPlatformAuth } = await import("@/analytics/api/analyticsApi");
	const response = await fetchWithPlatformAuth(normalizeResourceFetchUrl(url));
	if (!response.ok) {
		throw new Error(`HTTP ${response.status}`);
	}
	return await response.blob();
}

async function blobToBytes(blob: Blob): Promise<Uint8Array> {
	return new Uint8Array(await blob.arrayBuffer());
}

async function defaultUploadImage(blob: Blob, fileName: string, _contentType: string): Promise<string> {
	const { default: apiClient } = await import("@/api/apiClient");
	const formData = new FormData();
	formData.append("file", blob, fileName);
	const response = await apiClient.post<unknown>({
		url: "/infra/screen-images/upload",
		data: formData,
		_skipErrorToast: true,
	} as any);
	const url = resolveScreenImageUploadUrl(response);
	if (!url) {
		throw new Error("screen image upload returned no url");
	}
	return url;
}

export async function buildScreenPackageZip({
	screenName,
	screenSpec,
	templateMeta,
	fetchResource = defaultFetchResource,
	now = () => new Date(),
}: BuildScreenPackageOptions): Promise<BuildScreenPackageResult> {
	const zip = await createZip();
	const warnings: string[] = [];
	const usedPaths = new Set<string>();
	const resources: ScreenPackageResource[] = [];
	const exportedAt = now().toISOString();
	const packageableUrls = collectPackageableImageUrls(screenSpec);

	for (let index = 0; index < packageableUrls.length; index++) {
		const originalUrl = packageableUrls[index];
		const fileName = fileNameFromUrl(originalUrl, index);
		const path = uniqueImagePath(fileName, usedPaths);
		try {
			const blob = await fetchResource(originalUrl);
			const contentType = blob.type || contentTypeFromFileName(fileName);
			zip.file(path, await blobToBytes(blob));
			resources.push({
				kind: "image",
				originalUrl,
				path,
				contentType,
			});
		} catch (error) {
			const message = error instanceof Error ? error.message : String(error);
			warnings.push(`${originalUrl}: ${message}`);
		}
	}

	const screenPayload = {
		schema: "dts.screen.spec",
		exportedAt,
		screenName,
		resourcesInlined: false,
		packagedResources: resources.length,
		...(templateMeta ? { templateMeta } : {}),
		screenSpec,
	};
	const manifest: ScreenPackageManifest = {
		schema: SCREEN_PACKAGE_SCHEMA,
		version: SCREEN_PACKAGE_VERSION,
		exportedAt,
		resources,
	};

	zip.file(SCREEN_PACKAGE_SCREEN_FILE, JSON.stringify(screenPayload, null, 2));
	zip.file(SCREEN_PACKAGE_MANIFEST_FILE, JSON.stringify(manifest, null, 2));

	const zipBytes = await zip.generateAsync({ type: "uint8array" });
	return {
		blob: new Blob([zipBytes], { type: "application/zip" }),
		resourceCount: resources.length,
		warnings,
	};
}

function readTemplateMeta(payload: Record<string, unknown>): ScreenImportFileResult["templateMeta"] {
	const raw = payload.templateMeta;
	if (!isRecord(raw)) return undefined;
	const name = typeof raw.name === "string" ? raw.name : "";
	return {
		name,
		description: typeof raw.description === "string" ? raw.description : undefined,
		category: typeof raw.category === "string" ? raw.category : undefined,
		tags: Array.isArray(raw.tags) ? raw.tags.filter((item): item is string => typeof item === "string") : undefined,
	};
}

function readScreenSource(payload: Record<string, unknown>): Record<string, unknown> {
	const source = payload.screenSpec || payload;
	if (!isRecord(source)) {
		throw new Error("screen package does not contain a valid screen spec");
	}
	return source;
}

function replaceResourceUrls(source: Record<string, unknown>, replacements: Map<string, string>): Record<string, unknown> {
	const result = cloneRecord(source);

	function visit(value: unknown): void {
		if (Array.isArray(value)) {
			value.forEach(visit);
			return;
		}
		if (!isRecord(value)) return;
		for (const [key, item] of Object.entries(value)) {
			if (typeof item === "string" && replacements.has(item)) {
				value[key] = replacements.get(item);
				continue;
			}
			visit(item);
		}
	}

	visit(result);
	return result;
}

async function parseJsonImportFile(file: File): Promise<ScreenImportFileResult> {
	const payload = JSON.parse(await file.text()) as Record<string, unknown>;
	const source = readScreenSource(payload);
	const resourcesInlined = payload.resourcesInlined === true;
	return {
		kind: "json",
		fileName: file.name,
		payload,
		source,
		templateMeta: readTemplateMeta(payload),
		resourcesInlined,
		inlinedResourceCount: resourcesInlined ? countInlinedResources(source) : 0,
		restoredResourceCount: 0,
		warnings: [],
	};
}

async function parseZipImportFile(
	file: File,
	{ uploadImage = defaultUploadImage }: ParseScreenImportFileOptions,
): Promise<ScreenImportFileResult> {
	const zip = await loadZip(await file.arrayBuffer());
	const screenEntry = zip.file(SCREEN_PACKAGE_SCREEN_FILE);
	if (!screenEntry) {
		throw new Error("screen package is missing screen.json");
	}
	const payload = JSON.parse(await screenEntry.async("string")) as Record<string, unknown>;
	const source = readScreenSource(payload);
	const manifestEntry = zip.file(SCREEN_PACKAGE_MANIFEST_FILE);
	const manifest = manifestEntry
		? (JSON.parse(await manifestEntry.async("string")) as Partial<ScreenPackageManifest>)
		: ({ resources: [] } satisfies Partial<ScreenPackageManifest>);
	const warnings: string[] = [];
	const replacements = new Map<string, string>();

	for (const resource of Array.isArray(manifest.resources) ? manifest.resources : []) {
		if (resource?.kind !== "image" || !resource.originalUrl || !resource.path) continue;
		const resourceEntry = zip.file(resource.path);
		if (!resourceEntry) {
			warnings.push(`${resource.originalUrl}: resource file missing from package`);
			continue;
		}
		const fileName = sanitizePackageFileName(resource.path.split("/").pop() || "resource.png", "resource.png");
		const contentType = resource.contentType || contentTypeFromFileName(fileName);
		try {
			const bytes = await resourceEntry.async("uint8array");
			const restoredUrl = await uploadImage(new Blob([bytes], { type: contentType }), fileName, contentType);
			replacements.set(resource.originalUrl, restoredUrl);
		} catch (error) {
			const message = error instanceof Error ? error.message : String(error);
			warnings.push(`${resource.originalUrl}: ${message}`);
		}
	}

	const restoredSource = replaceResourceUrls(source, replacements);
	const resourcesInlined = payload.resourcesInlined === true;
	return {
		kind: "zip",
		fileName: file.name,
		payload,
		source: restoredSource,
		templateMeta: readTemplateMeta(payload),
		resourcesInlined,
		inlinedResourceCount: resourcesInlined ? countInlinedResources(restoredSource) : 0,
		restoredResourceCount: replacements.size,
		warnings,
	};
}

export async function parseScreenImportFile(
	file: File,
	options: ParseScreenImportFileOptions = {},
): Promise<ScreenImportFileResult> {
	if (isScreenPackageFile(file)) {
		return parseZipImportFile(file, options);
	}
	return parseJsonImportFile(file);
}
