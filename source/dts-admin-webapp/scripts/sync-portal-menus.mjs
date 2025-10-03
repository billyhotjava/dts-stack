#!/usr/bin/env node
// Copy the canonical portal menu seed into admin mock data so the admin UI
// uses the same structure as the backend seed when MSW is enabled.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const SEED_ENV_KEY = "DTS_PORTAL_MENU_SEED_PATH";
const REPO_MARKERS = [
  "docker-compose.yml",
  "pnpm-workspace.yaml",
  "pnpm-lock.yaml",
  "package.json",
];

const isDir = (candidate) => {
  try {
    return fs.statSync(candidate).isDirectory();
  } catch {
    return false;
  }
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

const repoRoot = findRepoRoot(__dirname) || findRepoRoot(process.cwd());
const seedCandidates = [];

if (process.env[SEED_ENV_KEY]) {
  seedCandidates.push(path.resolve(process.env[SEED_ENV_KEY]));
}

const parentAnchors = [];
if (repoRoot) {
  const parent = path.resolve(repoRoot, "..");
  if (parent && parent !== repoRoot) {
    parentAnchors.push(parent);
    const grand = path.resolve(parent, "..");
    if (grand && grand !== parent) {
      parentAnchors.push(grand);
    }
  }
}

const anchorSet = new Set([repoRoot, ...parentAnchors, __dirname, process.cwd()].filter(Boolean));
const repoAnchors = Array.from(anchorSet);
const seedRelativePaths = [
  ["dts-admin", "src", "main", "resources", "config", "data", "portal-menu-seed.json"],
  ["source", "dts-admin", "src", "main", "resources", "config", "data", "portal-menu-seed.json"],
];

for (const anchor of repoAnchors) {
  for (const rel of seedRelativePaths) {
    seedCandidates.push(path.join(anchor, ...rel));
  }
}

const seedPath = seedCandidates.find((candidate) => fs.existsSync(candidate));

if (!seedPath) {
  console.error(
    "[sync-portal-menus] Unable to locate portal-menu-seed.json. Checked:",
    seedCandidates.join(", "),
  );
  process.exit(0);
}

const srcMenus = seedPath;
const mockRoot = path.resolve(__dirname, "..", "src", "_mock");

if (!fs.existsSync(mockRoot)) {
  console.warn("[sync-portal-menus] Mock directory removed; skip MSW seed export.");
  process.exit(0);
}

const destMenus = path.join(mockRoot, "data", "portal-menus.json");

const destI18nZh = path.join(mockRoot, "data", "portal-i18n-zh.json");

const normalizePathSegment = (segment) => {
  if (!segment) return "";
  return segment.replace(/^\/+|\/+$/g, "");
};

const toMenuItems = (nodes = [], parentId = null, parentPath = "", rootKey = null) => {
  const items = [];
  nodes.forEach((node, index) => {
    const key = node.key || `menu_${index}`;
    const segment = normalizePathSegment(node.path || key);
    const fullPath = parentPath ? `${parentPath}/${segment}` : `/${segment}`;
    const id = parentId ? `${parentId}.${key}` : key;
    const sectionKey = parentId ? rootKey : key;
    const metadata = {
      key,
      sectionKey,
      ...(parentId ? { entryKey: key } : {}),
      ...(node.titleKey ? { titleKey: node.titleKey } : {}),
      ...(node.title ? { title: node.title } : {}),
      ...(node.icon ? { icon: node.icon } : {}),
    };

    const menuItem = {
      id,
      name: node.titleKey || node.title || key,
      displayName: node.title || undefined,
      path: fullPath,
      icon: node.icon || undefined,
      sortOrder: index + 1,
      metadata: JSON.stringify(metadata),
      securityLevel: "GENERAL",
      parentId: parentId,
      children: [],
    };

    menuItem.children = toMenuItems(node.children || [], id, fullPath, sectionKey);
    items.push(menuItem);
  });
  return items;
};

try {
  // Ensure destination folder exists
  fs.mkdirSync(path.dirname(destMenus), { recursive: true });
  const seed = JSON.parse(fs.readFileSync(srcMenus, "utf-8"));
  const sections = seed?.portalNavSections || [];
  const flattened = toMenuItems(sections);
  fs.writeFileSync(destMenus, `${JSON.stringify(flattened, null, "\t")}\n`, "utf-8");
  console.log(`[sync-portal-menus] Generated portal menus from seed: ${srcMenus} -> ${destMenus}`);

  // Extract zh_CN portal labels if available
  const zhCandidates = [
    path.join(repoRoot || "", "dts-platform-webapp", "src", "locales", "lang", "zh_CN", "sys.json"),
    path.join(repoRoot || "", "source", "dts-platform-webapp", "src", "locales", "lang", "zh_CN", "sys.json"),
  ];
  const zhPath = zhCandidates.find((candidate) => fs.existsSync(candidate));
  if (zhPath) {
    try {
      const zh = JSON.parse(fs.readFileSync(zhPath, "utf-8"));
      const portal = zh?.sys?.nav?.portal || {};
      const mapping = {};
      const indent = "\t";
      for (const [k, v] of Object.entries(portal)) {
        if (typeof v === "string") {
          mapping[`sys.nav.portal.${k}`] = v;
        }
      }
      fs.writeFileSync(destI18nZh, `${JSON.stringify(mapping, null, indent)}\n`, "utf-8");
      console.log(`[sync-portal-menus] Synced i18n zh: ${zhPath} -> ${destI18nZh}`);
    } catch (e) {
      console.warn("[sync-portal-menus] Skip i18n zh extraction:", e.message);
    }
  } else {
    console.warn("[sync-portal-menus] zh_CN portal translations not found; skipped i18n export.");
  }
} catch (err) {
  console.error("[sync-portal-menus] Failed:", err);
  process.exit(1);
}
