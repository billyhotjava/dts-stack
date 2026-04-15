/**
 * Global auth setup for the SQL IDE smoke suite.
 *
 * Strategy: POST to the platform login API, extract the JWT, then write a
 * Playwright storageState JSON that seeds localStorage with `userStore` so
 * every test starts already authenticated — no UI login flow required.
 *
 * Credentials come from environment variables:
 *   E2E_USERNAME  (default: opadmin)
 *   E2E_PASSWORD  (default: opadmin123)
 * E2E_BASE_URL controls where the API is reached.
 */

import fs from "node:fs";
import path from "node:path";

const AUTH_FILE = path.resolve(import.meta.dirname, ".auth/user.json");
const baseURL = (process.env.E2E_BASE_URL ?? "http://localhost:3001").replace(/\/$/, "");
const username = process.env.E2E_USERNAME ?? "opadmin";
const password = process.env.E2E_PASSWORD ?? "opadmin123";

function pickToken(obj: Record<string, unknown>, keys: string[]): string {
  for (const key of keys) {
    const v = obj[key];
    if (typeof v === "string" && v.trim()) return v.trim();
  }
  return "";
}

async function globalSetup(): Promise<void> {
  const loginUrl = `${baseURL}/api/keycloak/auth/login`;

  const res = await fetch(loginUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ username, password }),
  });

  if (!res.ok) {
    const body = await res.text();
    throw new Error(
      `Auth setup: login failed (${res.status}) at ${loginUrl}.\n` +
        `Body: ${body.slice(0, 300)}\n` +
        `Check E2E_USERNAME / E2E_PASSWORD env vars.`,
    );
  }

  const envelope = (await res.json()) as Record<string, unknown>;
  // Backend wraps payload in { status, data: { accessToken, ... } }
  const data: Record<string, unknown> =
    (envelope["data"] as Record<string, unknown>) ?? envelope;
  const accessToken = pickToken(data, ["accessToken", "access_token", "token"]);
  const refreshToken = pickToken(data, ["refreshToken", "refresh_token"]);

  if (!accessToken) {
    throw new Error(
      `Auth setup: login responded OK but no accessToken found.\nBody: ${JSON.stringify(envelope).slice(0, 300)}`,
    );
  }

  const rawUser: Record<string, unknown> =
    (data["user"] as Record<string, unknown>) ??
    (data["userInfo"] as Record<string, unknown>) ??
    {};

  const userInfo: Record<string, unknown> = {
    username: rawUser["username"] ?? username,
    fullName: rawUser["fullName"] ?? rawUser["firstName"] ?? username,
    roles: Array.isArray(rawUser["roles"]) ? rawUser["roles"] : ["ROLE_OP_ADMIN"],
    permissions: Array.isArray(rawUser["permissions"]) ? rawUser["permissions"] : [],
    enabled: true,
    avatar: "/assets/icons/ic-user.svg",
    ...rawUser,
  };

  const storeRecord = {
    state: {
      userInfo,
      userToken: { accessToken, refreshToken },
    },
    version: 0,
  };

  const storeJson = JSON.stringify(storeRecord);
  const now = String(Date.now());
  const origin = new URL(baseURL).origin;

  const storageState = {
    cookies: [],
    origins: [
      {
        origin,
        localStorage: [
          { name: "userStore", value: storeJson },
          { name: "platformUserStore", value: storeJson },
          { name: "dts.platform.session.loginTs", value: now },
          { name: "dts.platform.session.lastActivity", value: now },
        ],
      },
    ],
  };

  fs.mkdirSync(path.dirname(AUTH_FILE), { recursive: true });
  fs.writeFileSync(AUTH_FILE, JSON.stringify(storageState, null, 2), "utf-8");

  console.log(`[auth.setup] Logged in as ${username}, storageState written to ${AUTH_FILE}`);
}

export default globalSetup;
