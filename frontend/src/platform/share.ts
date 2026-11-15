/** Chia sẻ link mời (US-10). Bản native dùng màn hình chia sẻ của hệ điều hành. */
export interface ShareService {
  share(content: { title: string; url: string }): Promise<"shared" | "copied" | "cancelled">;
}

export class WebShareService implements ShareService {
  async share(content: { title: string; url: string }): Promise<"shared" | "copied" | "cancelled"> {
    if (typeof navigator.share === "function") {
      try {
        await navigator.share(content);
        return "shared";
      } catch (error: unknown) {
        // Người dùng đóng hộp chia sẻ thì không coi là lỗi.
        if (error instanceof DOMException && error.name === "AbortError") {
          return "cancelled";
        }
      }
    }
    await navigator.clipboard.writeText(content.url);
    return "copied";
  }
}
