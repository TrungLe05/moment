/**
 * Nơi giữ access token. Bản web chỉ giữ trong bộ nhớ, không ghi localStorage/sessionStorage (CLAUDE.md);
 * refresh token của web nằm trong cookie HttpOnly nên JavaScript không đụng tới.
 * Bản native (giai đoạn 7–8) sẽ cất refresh token vào Keychain/Keystore.
 */
export interface TokenStore {
  getAccessToken(): string | null;
  setAccessToken(token: string | null): void;
}

export class MemoryTokenStore implements TokenStore {
  private accessToken: string | null = null;

  getAccessToken(): string | null {
    return this.accessToken;
  }

  setAccessToken(token: string | null): void {
    this.accessToken = token;
  }
}
