import type { Locale } from "@/i18n/messages";
import { apiClient } from "./index";
import type { LoginResponse, User } from "./types";

// Các endpoint nhóm Auth và hồ sơ (danh-sach-api.md mục 3.1, 3.2).

export const accountApi = {
  register: (email: string, password: string, locale: Locale) =>
    apiClient.request<void>("/auth/register", { method: "POST", body: { email, password, locale } }),

  verifyEmail: (token: string) => apiClient.request<void>("/auth/verify-email", { method: "POST", body: { token } }),

  resendVerification: (email: string) =>
    apiClient.request<void>("/auth/resend-verification", { method: "POST", body: { email } }),

  login: (email: string, password: string) =>
    apiClient.request<LoginResponse>("/auth/login", { method: "POST", body: { email, password } }),

  loginWithGoogle: (idToken: string, locale: Locale) =>
    apiClient.request<LoginResponse>("/auth/google", { method: "POST", body: { idToken, locale } }),

  logout: () => apiClient.request<void>("/auth/logout", { method: "POST", auth: true }),

  forgotPassword: (email: string) =>
    apiClient.request<void>("/auth/forgot-password", { method: "POST", body: { email } }),

  resetPassword: (token: string, newPassword: string) =>
    apiClient.request<void>("/auth/reset-password", { method: "POST", body: { token, newPassword } }),

  me: () => apiClient.request<User>("/me", { auth: true }),

  updateMe: (changes: { username?: string; displayName?: string; locale?: Locale }) =>
    apiClient.request<User>("/me", { method: "PATCH", body: changes, auth: true }),

  usernameAvailable: async (username: string) =>
    (
      await apiClient.request<{ available: boolean }>(`/usernames/${encodeURIComponent(username)}/availability`, {
        auth: true,
      })
    ).available,
};
