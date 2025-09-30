#!/usr/bin/env node
// Copy the exported portal menus from dts-platform-webapp into
// dts-admin-webapp mock data so admin menu management uses the platform data.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Source JSON exported by dts-platform-webapp (served under its public/)
const srcMenus = path.resolve(__dirname, "..", "..", "dts-platform-webapp", "public", "portal-menus.demo.json");
// Destination JSON consumed by admin MSW handlers
const destMenus = path.resolve(__dirname, "..", "src", "_mock", "data", "portal-menus.json");

// Also sync Chinese labels for portal i18n, so admin can render names
const srcI18nZh = path.resolve(
  __dirname,
  "..",
  "..",
  "dts-platform-webapp",
  "src",
  "locales",
  "lang",
  "zh_CN",
  "sys.json",
);
const destI18nZh = path.resolve(__dirname, "..", "src", "_mock", "data", "portal-i18n-zh.json");

try {
  if (!fs.existsSync(srcMenus)) {
    console.error(`[sync-portal-menus] Source not found: ${srcMenus}`);
    process.exit(0);
  }

  // Ensure destination folder exists
  fs.mkdirSync(path.dirname(destMenus), { recursive: true });

  fs.copyFileSync(srcMenus, destMenus);
  console.log(`[sync-portal-menus] Synced: ${srcMenus} -> ${destMenus}`);

  // Try to extract zh_CN labels for sys.nav.portal.*
  if (fs.existsSync(srcI18nZh)) {
    try {
      const zh = JSON.parse(fs.readFileSync(srcI18nZh, "utf-8"));
      const portal = zh?.sys?.nav?.portal || {};
      const mapping = {};
      for (const [k, v] of Object.entries(portal)) {
        if (typeof v === "string") {
          mapping[`sys.nav.portal.${k}`] = v;
        }
      }
      fs.writeFileSync(destI18nZh, JSON.stringify(mapping, null, 2), "utf-8");
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
