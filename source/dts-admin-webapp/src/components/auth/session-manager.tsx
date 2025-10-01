import { useEffect, useMemo, useRef } from "react";
import { toast } from "sonner";
import { useRouter } from "@/routes/hooks";
import { useUserActions, useUserInfo, useUserToken } from "@/store/userStore";
import userService from "@/api/services/userService";

const STORAGE_KEYS = {
  SESSION_ID: "dts.session.id",
  SESSION_USER: "dts.session.user",
  LOGOUT_TS: "dts.session.logoutTs",
} as const;

const genId = () => Math.random().toString(36).slice(2) + Date.now().toString(36);

export function SessionManager() {
  const router = useRouter();
  const user = useUserInfo();
  const token = useUserToken();
  const { setUserToken, clearUserInfoAndToken } = useUserActions();

  const tabIdRef = useRef<string>(genId());
  const mySessionIdRef = useRef<string | null>(null);

  const isLoggedIn = useMemo(() => Boolean(token?.accessToken), [token?.accessToken]);
  const loginName = user?.username || user?.email || "";

  // Establish or update session marker on login
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
      // Adopt existing session id in case we opened new tab after login
      mySessionIdRef.current = current;
    }
  }, [isLoggedIn, loginName]);

  // Cross-tab session change detection (force single active session in browser)
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (!e.key) return;
      if (e.key === STORAGE_KEYS.SESSION_ID) {
        const newId = e.newValue;
        if (isLoggedIn && newId && newId !== mySessionIdRef.current) {
          toast.error("当前账号已在其他页面登录，已强制退出");
          clearUserInfoAndToken();
          // Mark a logout broadcast for other listeners
          localStorage.setItem(STORAGE_KEYS.LOGOUT_TS, String(Date.now()));
          router.replace("/auth/login");
        }
      }
      if (e.key === STORAGE_KEYS.LOGOUT_TS && e.newValue) {
        if (isLoggedIn) {
          clearUserInfoAndToken();
          router.replace("/auth/login");
        }
      }
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, [isLoggedIn, clearUserInfoAndToken, router]);

  // Heartbeat/refresh to detect timeout and keep token fresh
  useEffect(() => {
    if (!isLoggedIn || !token?.refreshToken) return;
    let stopped = false;
    const interval = setInterval(async () => {
      if (stopped) return;
      try {
        const res = await userService.refresh(token.refreshToken!);
        // If backend returns new tokens, update them; otherwise ignore
        const nextAccess = (res as any)?.accessToken;
        const nextRefresh = (res as any)?.refreshToken;
        if (nextAccess) {
          setUserToken({ accessToken: nextAccess, refreshToken: nextRefresh || token.refreshToken });
        }
      } catch (err) {
        // Refresh failed (likely timeout). Force logout.
        toast.error("会话已过期，请重新登录");
        clearUserInfoAndToken();
        localStorage.setItem(STORAGE_KEYS.LOGOUT_TS, String(Date.now()));
        // Redirect to login under basename
        router.replace("/auth/login");
      }
    }, 4 * 60 * 1000); // every 4 minutes

    return () => {
      stopped = true;
      clearInterval(interval);
    };
  }, [isLoggedIn, token?.refreshToken, setUserToken, clearUserInfoAndToken, router]);

  return null;
}

export default SessionManager;
