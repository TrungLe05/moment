import type { Metadata, Viewport } from "next";
import { defaultLocale, messages } from "@/i18n/messages";
import { Providers } from "./providers";
import "./globals.css";

const text = messages[defaultLocale];

export const metadata: Metadata = {
  title: text.appName,
  description: text.appDescription,
  // US-50: biểu tượng khi thêm vào màn hình chính trên Safari; manifest (app/manifest.ts) lo phần còn lại.
  icons: { icon: "/icon-192.png", apple: "/apple-icon-180.png" },
  appleWebApp: { capable: true, title: text.appName, statusBarStyle: "black-translucent" },
};

// Thanh tiêu đề cửa sổ app cài đặt cùng màu nền tối của không gian chia sẻ.
export const viewport: Viewport = { themeColor: "#0e0e10" };

// Font hiển thị tốt tiếng Việt chọn ở T-010; tạm dùng font hệ thống (globals.css).
export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang={defaultLocale} className="h-full antialiased">
      <body className="min-h-full flex flex-col">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
