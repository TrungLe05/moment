"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useI18n } from "@/i18n/I18nProvider";
import { apiClient } from "@/lib/api";
import { accountApi } from "@/lib/api/account";
import type { LoginResponse, User } from "@/lib/api/types";
import { platform } from "@/platform";

type SessionStatus = "loading" | "signed-out" | "signed-in";

type SessionContextValue = {
  status: SessionStatus;
  user: User | null;
  startSession: (response: LoginResponse) => void;
  updateUser: (user: User) => void;
  signOut: () => Promise<void>;
};

const SessionContext = createContext<SessionContextValue | null>(null);

/**
 * Phiên đăng nhập phía client. Access token chỉ nằm trong bộ nhớ (TokenStore), nên khi tải lại trang
 * phải lấy lại bằng cookie refresh token qua /auth/refresh.
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const { setLocale } = useI18n();
  const [status, setStatus] = useState<SessionStatus>("loading");
  const [user, setUser] = useState<User | null>(null);

  const applyUser = useCallback(
    (next: User) => {
      setUser(next);
      setStatus("signed-in");
      // US-07: ngôn ngữ đã lưu trong tài khoản thắng ngôn ngữ của trình duyệt.
      setLocale(next.locale);
    },
    [setLocale],
  );

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const restored = await apiClient.refreshSession();
      if (!restored) {
        if (!cancelled) setStatus("signed-out");
        return;
      }
      try {
        const me = await accountApi.me();
        if (!cancelled) applyUser(me);
      } catch {
        if (!cancelled) setStatus("signed-out");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [applyUser]);

  const startSession = useCallback(
    (response: LoginResponse) => {
      platform.tokenStore.setAccessToken(response.accessToken);
      applyUser(response.user);
    },
    [applyUser],
  );

  const signOut = useCallback(async () => {
    try {
      await accountApi.logout();
    } catch {
      // Không gọi được server vẫn xóa phiên ở máy; refresh token hết hạn sau 30 ngày.
    }
    platform.tokenStore.setAccessToken(null);
    setUser(null);
    setStatus("signed-out");
  }, []);

  const value = useMemo(
    () => ({ status, user, startSession, updateUser: applyUser, signOut }),
    [status, user, startSession, applyUser, signOut],
  );
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionContextValue {
  const context = useContext(SessionContext);
  if (!context) {
    throw new Error("useSession phải nằm trong SessionProvider");
  }
  return context;
}
