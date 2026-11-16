import type { Locale } from "@/i18n/messages";

// Khớp đối tượng user ở danh-sach-api.md mục 2.
export type User = {
  id: string;
  email: string;
  username: string | null;
  displayName: string | null;
  avatarUrl: string | null;
  locale: Locale;
  needsOnboarding: boolean;
  friendCount: number;
  friendLimit: number;
  isDemo: boolean;
};

export type LoginResponse = {
  accessToken: string;
  expiresIn: number;
  user: User;
  isNewUser?: boolean;
};
