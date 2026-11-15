import type { Metadata } from "next";
import { defaultLocale, messages } from "@/i18n/messages";
import { Providers } from "./providers";
import "./globals.css";

const text = messages[defaultLocale];

export const metadata: Metadata = {
  title: text.appName,
  description: text.appDescription,
};

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
