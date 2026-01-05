import { createReadStream, promises as fs } from "node:fs";
import { extname, join, normalize } from "node:path";
import http from "node:http";
import { URL } from "node:url";

const PORT = Number.parseInt(process.env.PORT ?? "3001", 10);
const API_BASE = process.env.DTS_ANALYTICS_API_BASE ?? "http://dts-analytics:3000";
const DEFAULT_LOCALE = (process.env.DTS_ANALYTICS_DEFAULT_LOCALE ?? "zh").toLowerCase();
const PLATFORM_USERSTORE_KEY = process.env.DTS_PLATFORM_USERSTORE_KEY ?? "userStore";
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

function parseCookie(headerValue) {
  const out = new Map();
  if (!headerValue) return out;
  for (const part of headerValue.split(";")) {
    const [k, ...rest] = part.trim().split("=");
    if (!k) continue;
    out.set(k, rest.join("="));
  }
  return out;
}

function resolveLocale(req) {
  const url = new URL(req.url ?? "/", "http://local");
  const fromQuery = (url.searchParams.get("lang") ?? "").toLowerCase();
  if (fromQuery === "en" || fromQuery === "zh") return fromQuery;

  const cookie = parseCookie(header(req, "cookie"));
  const fromCookie = (cookie.get("dts_lang") ?? "").toLowerCase();
  if (fromCookie === "en" || fromCookie === "zh") return fromCookie;

  const accept = (header(req, "accept-language") ?? "").toLowerCase();
  if (accept.includes("en")) return "en";

  return DEFAULT_LOCALE === "en" ? "en" : "zh";
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

function safeJsonForHtmlText(jsonText) {
  // Avoid closing the <script> tag and keep JSON valid.
  return jsonText.replaceAll("<", "\\u003c");
}

function defaultEnLocalization() {
  return {
    headers: {
      language: "en",
      "plural-forms": "nplurals=2; plural=(n != 1);",
    },
    translations: {
      "": { Metabase: { msgid: "Metabase", msgstr: ["Metabase"] } },
    },
  };
}

const localizationCache = new Map();

async function loadLocalization(locale) {
  const key = locale === "en" ? "en" : "zh";
  if (localizationCache.has(key)) return localizationCache.get(key);

  if (key === "en") {
    const text = safeJsonForHtmlText(JSON.stringify(defaultEnLocalization()));
    localizationCache.set(key, text);
    return text;
  }

  const raw = await readText(join(LEGACY_DIR, "app/locales/zh.json"));
  let obj;
  try {
    obj = JSON.parse(raw);
  } catch {
    obj = defaultEnLocalization();
  }
  if (!obj.headers) obj.headers = {};
  // Normalize to the locale codes Metabase expects ("zh" / "en") for switching/moment mapping.
  obj.headers.language = "zh";
  const text = safeJsonForHtmlText(JSON.stringify(obj));
  localizationCache.set(key, text);
  return text;
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

function platformAuthBridgeScript({ baseHref, userStoreKey }) {
  const apiPrefix = (baseHref && baseHref !== "/" ? baseHref : "/") + "api/";
  return `(function(){try{
var STORAGE_KEY=${JSON.stringify(userStoreKey)};
var API_PREFIX=${JSON.stringify(apiPrefix)};
function readStore(){try{var raw=localStorage.getItem(STORAGE_KEY);if(!raw)return null;return JSON.parse(raw);}catch(e){return null;}}
function writeStore(store){try{localStorage.setItem(STORAGE_KEY,JSON.stringify(store));}catch(e){}}
function getTokenObj(store){var state=store&&store.state;return state&&state.userToken?state.userToken:null;}
function getTokens(){var store=readStore();var token=getTokenObj(store)||{};var access=(token.accessToken||token.token||"");var refresh=(token.refreshToken||"");access=String(access||"").trim();refresh=String(refresh||"").trim();return {store:store,token:token,accessToken:access,refreshToken:refresh};}
function setTokens(next){var current=readStore();if(!current||!current.state){current={state:{},version:0};}if(!current.state.userToken){current.state.userToken={};}
for(var k in next){if(Object.prototype.hasOwnProperty.call(next,k)){current.state.userToken[k]=next[k];}}
writeStore(current);}
function buildUrl(input){try{return new URL(input,window.location.href);}catch(e){return null;}}
function shouldAttach(url){return !!(url&&url.pathname&&url.pathname.indexOf(API_PREFIX)===0);}
function hasAuthHeader(headers){if(!headers)return false;try{if(headers.get){return !!headers.get('Authorization');}}catch(e){}
if(typeof headers==='object'){for(var k in headers){if(String(k).toLowerCase()==='authorization'){return !!headers[k];}}}
return false;}
function withAuth(init, token){var headers=init&&init.headers?init.headers:null;var nextInit=init?Object.assign({},init):{};
var nextHeaders;
try{nextHeaders=headers&&headers.get?new Headers(headers):new Headers(headers||{});}catch(e){nextHeaders=headers||{};}
if(!hasAuthHeader(nextHeaders)){
var raw=String(token||'').trim();
if(raw){try{if(nextHeaders.set){nextHeaders.set('Authorization','Bearer '+raw);}else{nextHeaders['Authorization']='Bearer '+raw;}}catch(e){}}
}
nextInit.headers=nextHeaders;
return nextInit;}
function pickToken(payload){if(!payload)return '';var direct=payload.accessToken||payload.access_token||payload.token;var data=payload.data||payload.result||payload.payload||null;
var nested=(data&& (data.accessToken||data.access_token||data.token))||'';var v=direct||nested||'';return String(v||'').trim();}
function pickRefresh(payload){if(!payload)return '';var direct=payload.refreshToken||payload.refresh_token;var data=payload.data||payload.result||payload.payload||null;
var nested=(data&& (data.refreshToken||data.refresh_token))||'';var v=direct||nested||'';return String(v||'').trim();}
function refreshSession(refreshToken){var rt=String(refreshToken||'').trim();if(!rt)return Promise.resolve(null);
return fetch('/api/keycloak/auth/refresh',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({refreshToken:rt})})
.then(function(r){if(!r||!r.ok)return null;return r.json().catch(function(){return null;});})
.then(function(body){if(!body)return null;var nextAccess=pickToken(body);var nextRefresh=pickRefresh(body);if(!nextAccess)return null;
setTokens({accessToken:nextAccess,refreshToken:nextRefresh||rt});return {accessToken:nextAccess,refreshToken:nextRefresh||rt};});}
var origFetch=window.fetch;
if(typeof origFetch==='function'){
window.fetch=function(input,init){
var url=buildUrl(typeof input==='string'?input:(input&&input.url?input.url:''));if(!shouldAttach(url))return origFetch(input,init);
var t=getTokens();var nextInit=withAuth(init,t.accessToken);var retried=nextInit&&nextInit.__dtsRetry?true:false;
return origFetch(input,nextInit).then(function(resp){
if(resp&&resp.status===401&&!retried){return refreshSession(t.refreshToken).then(function(next){if(!next||!next.accessToken)return resp;
var retryInit=withAuth(init,next.accessToken);retryInit.__dtsRetry=true;return origFetch(input,retryInit);});}
return resp;});
};}
var OrigXHR=window.XMLHttpRequest;
if(typeof OrigXHR==='function'){
function PatchedXHR(){var xhr=new OrigXHR();var open=xhr.open;xhr.open=function(method,url,async,user,pw){xhr.__dtsUrl=url;return open.call(xhr,method,url,async,user,pw);};
var send=xhr.send;xhr.send=function(body){try{var u=buildUrl(xhr.__dtsUrl||'');if(shouldAttach(u)){var t=getTokens();if(t.accessToken){try{xhr.setRequestHeader('Authorization','Bearer '+t.accessToken);}catch(e){}}}}catch(e){}
return send.call(xhr,body);};return xhr;}
window.XMLHttpRequest=PatchedXHR;}
}catch(e){}})();`;
}

function injectHeadScript(html, scriptText) {
  const marker = "</head>";
  const idx = html.toLowerCase().indexOf(marker);
  const scriptTag = `<script>${scriptText}</script>`;
  if (idx === -1) return `${html}\n${scriptTag}\n`;
  return html.slice(0, idx) + scriptTag + html.slice(idx);
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

    // Keep health checks cheap and deterministic (avoid coupling to backend availability).
    if (assetPathname === "/api/health" || assetPathname === "/health") {
      res.statusCode = 200;
      res.setHeader("Content-Type", "application/json; charset=utf-8");
      res.end(JSON.stringify({ status: "ok" }));
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

    const locale = resolveLocale(req);
    const localizationJSON = await loadLocalization(locale);

    const view = {
      language: locale,
      favicon: "app/assets/img/favicon.ico",
      baseHref,
      uri,
      embedCode: "",
      applicationName: "DTS Analytics",
      bootstrapJSON,
      // Metabase expects objects with {headers:{language,...}, translations:{...}}; empty objects crash i18n init.
      userLocalizationJSON: localizationJSON,
      siteLocalizationJSON: localizationJSON,
      bootstrapJS: bootstrapJs,
      enableAnonTracking: false,
      googleAnalyticsJS: "",
    };

    const template = await readText(templatePath);
    let html = renderTemplate(template, view);
    html = injectHeadScript(html, platformAuthBridgeScript({ baseHref, userStoreKey: PLATFORM_USERSTORE_KEY }));
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
