import { defaultLocale, messages } from "@/i18n/messages";

// Trang giữ chỗ của khung repo (T-103); giao diện thật làm theo từng tính năng.
export default function Home() {
  const text = messages[defaultLocale];

  return (
    <main className="mx-auto flex w-full max-w-[390px] flex-1 flex-col items-center justify-center gap-3 px-6 text-center">
      <h1 className="text-3xl font-semibold">{text.appName}</h1>
      <p className="text-base">{text.appDescription}</p>
      <p className="text-sm">{text.comingSoon}</p>
    </main>
  );
}
