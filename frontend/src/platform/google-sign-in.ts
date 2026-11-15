import type { Locale } from "@/i18n/messages";

/**
 * Đăng nhập Google luôn là client lấy ID token rồi gửi POST /auth/google (ARCHITECTURE.md mục 4).
 * Bản web dùng Google Identity Services; bản native dùng plugin đăng nhập Google.
 */
export interface GoogleSignIn {
  isConfigured(): boolean;
  renderButton(container: HTMLElement, locale: Locale, onIdToken: (idToken: string) => void): Promise<void>;
}

const GIS_SCRIPT_URL = "https://accounts.google.com/gsi/client";

type GoogleAccountsId = {
  initialize(options: { client_id: string; callback: (response: { credential?: string }) => void }): void;
  renderButton(element: HTMLElement, options: Record<string, string | number>): void;
};

function readGoogleAccountsId(): GoogleAccountsId | null {
  const google: unknown = Reflect.get(window, "google");
  if (typeof google !== "object" || google === null) {
    return null;
  }
  const accounts: unknown = Reflect.get(google, "accounts");
  if (typeof accounts !== "object" || accounts === null) {
    return null;
  }
  const id: unknown = Reflect.get(accounts, "id");
  if (typeof id !== "object" || id === null) {
    return null;
  }
  const initialize: unknown = Reflect.get(id, "initialize");
  const renderButton: unknown = Reflect.get(id, "renderButton");
  if (typeof initialize !== "function" || typeof renderButton !== "function") {
    return null;
  }
  return {
    initialize: (options) => initialize.call(id, options),
    renderButton: (element, options) => renderButton.call(id, element, options),
  };
}

let scriptLoading: Promise<void> | null = null;

function loadScript(): Promise<void> {
  scriptLoading ??= new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = GIS_SCRIPT_URL;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => {
      scriptLoading = null;
      reject(new Error("Không tải được Google Identity Services"));
    };
    document.head.appendChild(script);
  });
  return scriptLoading;
}

export class WebGoogleSignIn implements GoogleSignIn {
  constructor(private readonly clientId: string) {}

  isConfigured(): boolean {
    return this.clientId.length > 0;
  }

  async renderButton(container: HTMLElement, locale: Locale, onIdToken: (idToken: string) => void): Promise<void> {
    await loadScript();
    const accountsId = readGoogleAccountsId();
    if (!accountsId) {
      throw new Error("Google Identity Services chưa sẵn sàng");
    }
    accountsId.initialize({
      client_id: this.clientId,
      callback: (response) => {
        if (response.credential) {
          onIdToken(response.credential);
        }
      },
    });
    accountsId.renderButton(container, { theme: "outline", size: "large", width: 320, locale });
  }
}
