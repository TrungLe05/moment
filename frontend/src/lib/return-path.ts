/**
 * Đường dẫn quay lại sau khi đăng nhập (US-11: mở link mời khi chưa đăng nhập thì xong quay lại đúng trang mời).
 * Chỉ nhận đường dẫn nội bộ bắt đầu bằng một dấu "/" để không bị lợi dụng chuyển hướng ra trang ngoài.
 */
export function safeReturnPath(value: string | null | undefined): string | null {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.includes("\\")) {
    return null;
  }
  return value;
}

/** Thêm ?next= vào một trang, ví dụ withReturnPath("/login", "/invite?code=abc"). */
export function withReturnPath(page: string, returnPath: string): string {
  return `${page}?next=${encodeURIComponent(returnPath)}`;
}
