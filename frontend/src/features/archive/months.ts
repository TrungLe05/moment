import type { Moment } from "@/lib/api/moments";

export type MonthGroup = { key: string; label: string; items: Moment[] };

/**
 * US-40: nhóm khoảnh khắc theo tháng đăng, giữ thứ tự server trả (mới nhất trước). Client tự nhóm theo `createdAt`
 * (API mục 3.4), theo múi giờ của máy người xem.
 */
export function groupByMonth(items: Moment[], locale: string): MonthGroup[] {
  const label = new Intl.DateTimeFormat(locale, { month: "long", year: "numeric" });
  const groups: MonthGroup[] = [];
  for (const moment of items) {
    const posted = new Date(moment.createdAt);
    const key = `${posted.getFullYear()}-${posted.getMonth()}`;
    const last = groups.at(-1);
    if (last?.key === key) {
      last.items.push(moment);
    } else {
      groups.push({ key, label: capitalize(label.format(posted), locale), items: [moment] });
    }
  }
  return groups;
}

/** Ngày đăng đầy đủ, ví dụ "15 tháng 11, 2026" hay "November 15, 2026" (US-42). */
export function postedDate(createdAt: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: "long" }).format(new Date(createdAt));
}

// "tháng 11 năm 2026" → "Tháng 11 năm 2026" cho tiêu đề nhóm; tách theo grapheme để không cắt đôi ký tự có dấu.
function capitalize(text: string, locale: string): string {
  const first = new Intl.Segmenter(locale, { granularity: "grapheme" }).segment(text)[Symbol.iterator]().next();
  return first.done ? text : first.value.segment.toLocaleUpperCase(locale) + text.slice(first.value.segment.length);
}
