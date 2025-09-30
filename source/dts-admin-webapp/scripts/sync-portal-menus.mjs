#!/usr/bin/env node
// Copy the exported portal menus from dts-platform-webapp into
// dts-admin-webapp mock data so admin menu management uses the platform data.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PLATFORM_ENV_KEY = "DTS_PLATFORM_WEBAPP_PATH";
const REPO_MARKERS = [
  "docker-compose.yml",
  "pnpm-workspace.yaml",
  "pnpm-lock.yaml",
  "package.json",
];

const REQUIRED_FILES = [
  ["public", "portal-menus.demo.json"],
];

const isDir = (candidate) => {
  try {
    return fs.statSync(candidate).isDirectory();
  } catch {
    return false;
  }
};

const hasRequiredFiles = (candidate) => {
  if (!isDir(candidate)) return false;
  return REQUIRED_FILES.every((segments) => fs.existsSync(path.join(candidate, ...segments)));
};

const collectAncestors = (seed) => {
  const results = [];
  if (!seed) return results;
  let current = path.resolve(seed);
  const seen = new Set();
  while (!seen.has(current)) {
    results.push(current);
    seen.add(current);
    const parent = path.dirname(current);
    if (parent === current) break;
    current = parent;
  }
  return results;
};

const findRepoRoot = (seed) => {
  for (const dir of collectAncestors(seed)) {
    if (fs.existsSync(path.join(dir, ".git"))) return dir;
    if (REPO_MARKERS.some((marker) => fs.existsSync(path.join(dir, marker)))) {
      return dir;
    }
  }
  return null;
};

const candidateSet = new Set();
const addCandidate = (candidate) => {
  if (!candidate) return;
  const resolved = path.resolve(candidate);
  candidateSet.add(resolved);
};

const repoRoot = findRepoRoot(__dirname) || findRepoRoot(process.cwd());

if (process.env[PLATFORM_ENV_KEY]) {
  addCandidate(process.env[PLATFORM_ENV_KEY]);
}

for (const base of [__dirname, process.cwd(), repoRoot]) {
  for (const ancestor of collectAncestors(base)) {
    if (!ancestor) continue;
    addCandidate(path.join(ancestor, "dts-platform-webapp"));
    addCandidate(path.join(ancestor, "source", "dts-platform-webapp"));
    addCandidate(path.join(ancestor, "packages", "dts-platform-webapp"));
  }
}

const candidatePaths = Array.from(candidateSet);
const platformDir = candidatePaths.find((candidate) => hasRequiredFiles(candidate));

if (!platformDir) {
  console.error(
    "[sync-portal-menus] Unable to locate dts-platform-webapp with portal menus. Tried:",
    candidatePaths.join(", "),
  );
  process.exit(0);
}

const srcMenus = path.join(platformDir, ...REQUIRED_FILES[0]);
const destMenus = path.resolve(__dirname, "..", "src", "_mock", "data", "portal-menus.json");

const srcI18nZh = path.join(
  platformDir,
  "src",
  "locales",
  "lang",
  "zh_CN",
  "sys.json",
);
const destI18nZh = path.resolve(__dirname, "..", "src", "_mock", "data", "portal-i18n-zh.json");

try {
  // Ensure destination folder exists
  fs.mkdirSync(path.dirname(destMenus), { recursive: true });

  fs.copyFileSync(srcMenus, destMenus);
  console.log(`[sync-portal-menus] Synced: ${srcMenus} -> ${destMenus}`);

  if (fs.existsSync(srcI18nZh)) {
    try {
      const zh = JSON.parse(fs.readFileSync(srcI18nZh, "utf-8"));
      const portal = zh?.sys?.nav?.portal || {};
      const mapping = {};
      const indent = "\t";
      for (const [k, v] of Object.entries(portal)) {
        if (typeof v === "string") {
          mapping[`sys.nav.portal.${k}`] = v;
        }
      }
      fs.writeFileSync(destI18nZh, `${JSON.stringify(mapping, null, indent)}\n`, "utf-8");
      console.log(`[sync-portal-menus] Synced i18n zh: ${srcI18nZh} -> ${destI18nZh}`);
    } catch (e) {
      console.warn("[sync-portal-menus] Skip i18n zh extraction:", e.message);
    }
  } else {
    console.warn(`[sync-portal-menus] i18n zh not found: ${srcI18nZh}`);
  }
} catch (err) {
  console.error("[sync-portal-menus] Failed:", err);
  process.exit(1);
}
