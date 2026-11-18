/** Màu của từng kiểu lớp (mã `style` gửi lên server, D-042). Chữ trên nền đều đạt tương phản 4.5:1. */
export type LayerColors = { background: string; color: string };

const STYLES: Record<string, LayerColors> = {
  // Caption kính tối như nhãn trên ảnh ở feed (bản mẫu trình soạn).
  "pill-dark": { background: "rgba(14, 14, 16, 0.62)", color: "#FFFFFF" },
  paper: { background: "#FFFDF8", color: "#2B2520" },
  peach: { background: "#FF9B71", color: "#2B2520" },
  night: { background: "#0E0E10", color: "#F4F1EC" },
  "ribbon-cream": { background: "#FFFDF8", color: "#8A3B1C" },
  "ribbon-peach": { background: "#FF9B71", color: "#2B2520" },
};

/** Kiểu lạ (khoảnh khắc từ bản app mới hơn) thì vẽ như giấy kem để vẫn đọc được. */
export function layerColors(style: string): LayerColors {
  return STYLES[style] ?? STYLES.paper!;
}
