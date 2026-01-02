import { createReadStream, promises as fs } from "node:fs";
import { extname, join, normalize } from "node:path";
import http from "node:http";
import { URL } from "node:url";

const PORT = Number.parseInt(process.env.PORT ?? "3001", 10);
const API_BASE = process.env.DTS_ANALYTICS_API_BASE ?? "http://dts-analytics:3000";
const LEGACY_DIR =
  process.env.DTS_ANALYTICS_WEBAPP_LEGACY_DIR ??
  new URL("./legacy/frontend_client/", import.meta.url).pathname;

const MIME_TYPES = new Map([
  [".html", "text/html; charset=utf-8"],
  [".css", "text/css; charset=utf-8"],
  [".js", "application/javascript; charset=utf-8"],
  [".json", "application/json; charset=utf-8"],
  [".svg", "image/svg+xml"],
  [".png", "image/png"],
  [".jpg", "image/jpeg"],
  [".jpeg", "image/jpeg"],
  [".ico", "image/x-icon"],
  [".woff2", "font/woff2"],
  [".woff", "font/woff"],
  [".ttf", "font/ttf"],
]);

function header(req, name) {
  const v = req.headers[name.toLowerCase()];
  return Array.isArray(v) ? v[0] : v ?? "";
}

function normalizePrefix(prefix) {
  if (!prefix || prefix === "/" || prefix.trim() === "") return "";
  let p = prefix.split(",")[0].trim();
  if (!p.startsWith("/")) p = `/${p}`;
  if (p.endsWith("/")) p = p.slice(0, -1);
  return p;
}

function baseHrefFromPrefix(prefix) {
  const p = normalizePrefix(prefix);
  return p ? `${p}/` : "/";
}

function stripPrefix(pathname, prefix) {
  const p = normalizePrefix(prefix);
  if (!p) return pathname || "/";
  if (pathname === p) return "/";
  if (pathname.startsWith(`${p}/`)) return pathname.slice(p.length) || "/";
  return pathname || "/";
}

async function readText(path) {
  return await fs.readFile(path, "utf-8");
}

function renderTemplate(template, view) {
  let html = template;

  const enableAnon = Boolean(view.enableAnonTracking);
  html = html.replace(
    /{{#enableAnonTracking}}([\s\S]*?){{\/enableAnonTracking}}/g,
    enableAnon ? "$1" : "",
  );

  html = html.replace(/{{{\s*([a-zA-Z0-9_]+)\s*}}}/g, (_m, key) => {
    const v = view[key];
    return v === undefined || v === null ? "" : String(v);
  });

  return html;
}

async function fetchBootstrap(req, forwardedPrefix) {
  const u = new URL("/api/session/properties", API_BASE);

  const forwardedHost = header(req, "x-forwarded-host") || header(req, "host");
  const forwardedProto = header(req, "x-forwarded-proto") || "http";
  const cookie = header(req, "cookie");

  const res = await fetch(u, {
    headers: {
      ...(forwardedHost ? { "X-Forwarded-Host": forwardedHost } : {}),
      ...(forwardedProto ? { "X-Forwarded-Proto": forwardedProto } : {}),
      ...(forwardedPrefix ? { "X-Forwarded-Prefix": forwardedPrefix } : {}),
      ...(cookie ? { Cookie: cookie } : {}),
    },
  });

  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(`bootstrap fetch failed: ${res.status} ${body}`);
  }

  return await res.text();
}

async function serveFile(res, absolutePath) {
  const ext = extname(absolutePath).toLowerCase();
  res.statusCode = 200;
  res.setHeader("Content-Type", MIME_TYPES.get(ext) ?? "application/octet-stream");
  createReadStream(absolutePath).pipe(res);
}

function isStaticAsset(pathname) {
  if (pathname.startsWith("/app/")) return true;
  if (pathname.startsWith("/inline_js/")) return true;
  if (pathname === "/favicon.ico") return true;
  return false;
}

function selectTemplate(pathname) {
  if (pathname.startsWith("/embed/")) return "embed.html";
  if (pathname.startsWith("/public/")) return "public.html";
  return "index.html";
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url ?? "/", "http://local");
    const pathname = url.pathname || "/";

    const forwardedPrefix = normalizePrefix(header(req, "x-forwarded-prefix"));
    const assetPathname = stripPrefix(pathname, forwardedPrefix);

    if (req.method !== "GET" && req.method !== "HEAD") {
      res.statusCode = 405;
      res.setHeader("Content-Type", "text/plain; charset=utf-8");
      res.end("Method Not Allowed");
      return;
    }

    if (isStaticAsset(assetPathname)) {
      const rel =
        assetPathname === "/favicon.ico" ? "/app/assets/img/favicon.ico" : assetPathname;
      const safe = normalize(rel).replace(/^(\.\.(\/|\\|$))+/, "");
      const abs = join(LEGACY_DIR, safe);
      await serveFile(res, abs);
      return;
    }

    const templateName = selectTemplate(assetPathname);
    const templatePath = join(LEGACY_DIR, templateName);
    const bootstrapJs = await readText(join(LEGACY_DIR, "inline_js/index_bootstrap.js"));

    let bootstrapJSON = "{}";
    try {
      bootstrapJSON = await fetchBootstrap(req, forwardedPrefix);
    } catch (e) {
      console.warn(`[webapp] ${String(e)}`);
    }

    const baseHref = baseHrefFromPrefix(forwardedPrefix);
    const uri = stripPrefix(pathname, forwardedPrefix);

    const view = {
      language: "zh",
      favicon: "app/assets/img/favicon.ico",
      baseHref,
      uri,
      embedCode: "",
      applicationName: "DTS Analytics",
      bootstrapJSON,
      userLocalizationJSON: "{}",
      siteLocalizationJSON: "{}",
      bootstrapJS: bootstrapJs,
      enableAnonTracking: false,
      googleAnalyticsJS: "",
    };

    const template = await readText(templatePath);
    const html = renderTemplate(template, view);
    res.statusCode = 200;
    res.setHeader("Content-Type", "text/html; charset=utf-8");
    res.end(html);
  } catch (e) {
    res.statusCode = 500;
    res.setHeader("Content-Type", "text/plain; charset=utf-8");
    res.end(`Internal Server Error\n${String(e)}`);
  }
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`[webapp] listening on 0.0.0.0:${PORT}`);
  console.log(`[webapp] legacy=${LEGACY_DIR}`);
  console.log(`[webapp] api=${API_BASE}`);
});

