/** Một khoảng thời gian hiển thị: số giờ, hoặc số phút khi dưới 1 giờ. */
export type Span = { unit: "hours" | "minutes"; value: number };

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;

function toSpan(milliseconds: number): Span {
  if (milliseconds >= HOUR) {
    return { unit: "hours", value: Math.floor(milliseconds / HOUR) };
  }
  return { unit: "minutes", value: Math.max(1, Math.ceil(milliseconds / MINUTE)) };
}

/**
 * US-31: "còn X giờ", dưới 1 giờ thì "còn X phút". Làm tròn xuống theo giờ để không hứa nhiều hơn thời gian thật,
 * theo phút thì làm tròn lên để không hiện "còn 0 phút" khi vẫn còn xem được. Hết hạn thì trả null.
 */
export function timeLeft(expiresAt: string, now: number): Span | null {
  const remaining = Date.parse(expiresAt) - now;
  return remaining > 0 ? toSpan(remaining) : null;
}

/** Khoảnh khắc đăng cách đây bao lâu, cho nhãn "Minh Anh · 12 phút". */
export function postedAgo(createdAt: string, now: number): Span {
  return toSpan(Math.max(0, now - Date.parse(createdAt)));
}
