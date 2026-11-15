// Chuỗi giao diện song ngữ. Mỗi khóa phải có đủ bản vi và en (CLAUDE.md, quy ước frontend).
export const locales = ["vi", "en"] as const;

export type Locale = (typeof locales)[number];

export const defaultLocale: Locale = "vi";

const vi = {
  appName: "Khoảnh khắc",
  appDescription: "Chia sẻ khoảnh khắc 24 giờ với nhóm bạn thân.",
  comingSoon: "Đang xây dựng.",
};

// Kiểu lấy từ bản vi để bản en thiếu khóa nào là lỗi biên dịch ngay.
export type Messages = typeof vi;

const en: Messages = {
  appName: "Moment",
  appDescription: "Share 24-hour moments with your close friends.",
  comingSoon: "Under construction.",
};

export const messages: Record<Locale, Messages> = { vi, en };
