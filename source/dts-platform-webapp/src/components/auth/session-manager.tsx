import { useEffect, useMemo, useRef } from "react";
import { toast } from "sonner";
import { LOGIN_ROUTE } from "@/routes/constants";
import { useRouter } from "@/routes/hooks";
import { useUserActions, useUserInfo, useUserToken } from "@/store/userStore";
import userService from "@/api/services/userService";

const STORAGE_KEYS = {
  SESSION_ID: "dts.session.id",
  SESSION_USER: "dts.session.user",
  LOGOUT_TS: "dts.session.logoutTs",
} as const;

const genId = () => Math.random().toString(36).slice(2) + Date.now().toString(36);

function decodeJwtExp(token?: string): number | null {
  if (!token) return null;
  try {
    const parts = token.split(".");
    if (parts.length < 2) return null;
    let payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    while (payload.length % 4 !== 0) payload += "=";
    const json = atob(payload);
    const obj = JSON.parse(json);
    if (obj && typeof obj.exp === "number") {
      return obj.exp * 1000; // to ms
    }
    return null;
  } catch {
    return null;
  }
}

function nextRefreshDelayMs(accessToken?: string): number {
  const MIN_DELAY = 30 * 1000; // 30s
  const DEFAULT_DELAY = 4 * 60 * 1000; // 4m fallback
  const SKEW = 60 * 1000; // refresh 60s before expiry
  const expMs = decodeJwtExp(accessToken);
  if (!expMs) return DEFAULT_DELAY;
  const now = Date.now();
  const ms = Math.max(MIN_DELAY, expMs - now - SKEW);
  return ms;
}

export default function SessionManager() {
  const router = useRouter();
  const user = useUserInfo();
  const token = useUserToken();
  const { setUserToken, clearUserInfoAndToken } = useUserActions();

  const tabIdRef = useRef<string>(genId());
  const mySessionIdRef = useRef<string | null>(null);

  const isLoggedIn = useMemo(() => Boolean(token?.accessToken), [token?.accessToken]);
  const loginName = user?.username || user?.email || "";

  useEffect(() => {
    if (!isLoggedIn) {
      mySessionIdRef.current = null;
      return;
    }
    const current = localStorage.getItem(STORAGE_KEYS.SESSION_ID);
    if (!current) {
      const newId = `${loginName || "user"}#${genId()}#${tabIdRef.current}`;
      mySessionIdRef.current = newId;
      localStorage.setItem(STORAGE_KEYS.SESSION_ID, newId);
      if (loginName) localStorage.setItem(STORAGE_KEYS.SESSION_USER, loginName);
    } else {
      mySessionIdRef.current = current;
    }
  }, [isLoggedIn, loginName]);

  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (!e.key) return;
      if (e.key === STORAGE_KEYS.SESSION_ID) {
        const newId = e.newValue;
        if (isLoggedIn && newId && newId !== mySessionIdRef.current) {
          toast.error("当前账号已在其他页面登录，已强制退出");
          clearUserInfoAndToken();
          localStorage.setItem(STORAGE_KEYS.LOGOUT_TS, String(Date.now()));
          router.replace(LOGIN_ROUTE);
        }
      }
      if (e.key === STORAGE_KEYS.LOGOUT_TS && e.newValue) {
        if (isLoggedIn) {
          clearUserInfoAndToken();
          router.replace(LOGIN_ROUTE);
        }
      }
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, [isLoggedIn, clearUserInfoAndToken, router]);

  useEffect(() => {
    if (!isLoggedIn || !token?.refreshToken) return;
    let cancelled = false;
    let timer: number | undefined;

    const schedule = (delay: number) => {
      if (cancelled) return;
      timer = window.setTimeout(run, delay);
    };

    const run = async () => {
      if (cancelled) return;
      try {
        const res = await userService.refresh(token.refreshToken!);
        const nextAccess = (res as any)?.accessToken;
        const nextRefresh = (res as any)?.refreshToken;
        const normalizeDate = (value: unknown): string | undefined =>
          typeof value === "string" && value.trim() ? value.trim() : undefined;
        const nextAdminAccess = (res as any)?.adminAccessToken || token.adminAccessToken;
        const nextAdminRefresh = (res as any)?.adminRefreshToken || token.adminRefreshToken;
        const adminAccessExpiresAt =
          normalizeDate((res as any)?.adminAccessTokenExpiresAt) ?? token.adminAccessTokenExpiresAt;
        const adminRefreshExpiresAt =
          normalizeDate((res as any)?.adminRefreshTokenExpiresAt) ?? token.adminRefreshTokenExpiresAt;
        if (nextAccess) {
          setUserToken({
            accessToken: nextAccess,
            refreshToken: nextRefresh || token.refreshToken,
            adminAccessToken: nextAdminAccess,
            adminRefreshToken: nextAdminRefresh,
            adminAccessTokenExpiresAt: adminAccessExpiresAt,
            adminRefreshTokenExpiresAt: adminRefreshExpiresAt,
          });
          schedule(nextRefreshDelayMs(nextAccess));
          return;
        }
        // If backend didn't return new access token, reschedule based on current one.
        schedule(nextRefreshDelayMs(token.accessToken));
      } catch (err) {
        toast.error("会话已过期，请重新登录");
        clearUserInfoAndToken();
        localStorage.setItem(STORAGE_KEYS.LOGOUT_TS, String(Date.now()));
        router.replace(LOGIN_ROUTE);
      }
    };

    schedule(nextRefreshDelayMs(token.accessToken));

    return () => {
      cancelled = true;
      if (timer) window.clearTimeout(timer);
    };
  }, [isLoggedIn, token?.refreshToken, token?.accessToken, setUserToken, clearUserInfoAndToken, router]);

  return null;
}
