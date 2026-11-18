/**
 * Lớp trang trí đè lên ảnh hoặc video (US-23, US-24, ADR 0002, DB mục 5). Tọa độ theo tỉ lệ 0–1 của khung, tâm lớp
 * nằm ở (x, y); `scale`, `rotation` (độ) áp quanh tâm. Luật ở đây chỉ để trải nghiệm, server kiểm tra lại (LayerRules).
 */
export type LayerType = "caption" | "sticker" | "banner";

export type Layer = {
  id: string;
  type: LayerType;
  text: string;
  x: number;
  y: number;
  scale: number;
  rotation: number;
  style: string;
  /** Chỉ biểu ngữ: mã biểu ngữ dựng sẵn; chữ của biểu ngữ chép vào `text` theo ngôn ngữ người gửi. */
  bannerId?: string;
};

/** BR-11. */
export const MAX_CAPTION_CHARACTERS = 80;
export const MAX_CAPTION_LINES = 3;
export const MAX_LAYERS = 20;
export const MAX_LAYER_TEXT_CHARACTERS = 40;
const MIN_SCALE = 0.2;
const MAX_SCALE = 5;

export const CAPTION_STYLE = "pill-dark";
export const STICKER_STYLES = ["paper", "peach", "night"] as const;

/** Biểu ngữ dựng sẵn (D-042): mã gửi lên server, chữ lấy từ i18n theo mã. */
export const BANNERS = [
  { id: "good-morning", style: "ribbon-cream" },
  { id: "miss-you", style: "ribbon-peach" },
  { id: "on-my-way", style: "ribbon-cream" },
  { id: "weekend", style: "ribbon-peach" },
  { id: "hello", style: "ribbon-cream" },
  { id: "no-cap", style: "ribbon-peach" },
] as const;

export type BannerId = (typeof BANNERS)[number]["id"];

/** Cắt chữ ở giới hạn ký tự hiển thị (grapheme) và số dòng: gõ quá thì không nhận thêm (US-23). */
export function limitText(text: string, maxCharacters: number, maxLines?: number): string {
  const lines = maxLines === undefined ? text : text.split("\n").slice(0, maxLines).join("\n");
  const segmenter = new Intl.Segmenter(undefined, { granularity: "grapheme" });
  let kept = "";
  let count = 0;
  for (const { segment } of segmenter.segment(lines)) {
    if (count === maxCharacters) break;
    kept += segment;
    count++;
  }
  return kept;
}

function newId(): string {
  return `l${Math.random().toString(36).slice(2, 10)}`;
}

/** Lớp mới đặt giữa khung (caption ở gần đáy như bản mẫu). */
export function newLayer(type: LayerType, text: string, bannerId?: BannerId): Layer {
  const banner = bannerId ? BANNERS.find((item) => item.id === bannerId) : undefined;
  return {
    id: newId(),
    type,
    text,
    x: 0.5,
    y: type === "caption" ? 0.86 : 0.45,
    scale: 1,
    rotation: type === "sticker" ? -4 : 0,
    style: type === "caption" ? CAPTION_STYLE : type === "banner" ? (banner?.style ?? "ribbon-cream") : STICKER_STYLES[0],
    ...(bannerId ? { bannerId } : {}),
  };
}

/** Thêm lớp: tối đa 1 caption (caption mới thay caption cũ), tối đa 20 lớp. */
export function addLayer(layers: Layer[], layer: Layer): Layer[] {
  if (layer.type === "caption") {
    const others = layers.filter((item) => item.type !== "caption");
    return others.length >= MAX_LAYERS ? layers : [...others, layer];
  }
  return layers.length >= MAX_LAYERS ? layers : [...layers, layer];
}

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

// Đưa góc về khoảng −180 → 180 như server nhận.
function normalizeRotation(degrees: number): number {
  const turned = ((((degrees + 180) % 360) + 360) % 360) - 180;
  return turned === -180 ? 180 : turned;
}

export function moveLayer(layer: Layer, x: number, y: number): Layer {
  return { ...layer, x: clamp(x, 0, 1), y: clamp(y, 0, 1) };
}

export function transformLayer(layer: Layer, scale: number, rotation: number): Layer {
  return { ...layer, scale: clamp(scale, MIN_SCALE, MAX_SCALE), rotation: normalizeRotation(rotation) };
}

/** Lớp có chữ (caption, sticker) bị xóa hết chữ thì bỏ lớp. */
export function isEmpty(layer: Layer): boolean {
  return layer.text.trim().length === 0;
}

export type RequestLayer = {
  id: string;
  type: LayerType;
  text: string;
  x: number;
  y: number;
  scale: number;
  rotation: number;
  style: string;
  bannerId?: string;
};

const round = (value: number) => Math.round(value * 10_000) / 10_000;

/** Đúng cấu trúc `layers` của API (mục 3.4, DB mục 5), số làm tròn 4 chữ số cho JSON gọn. */
export function toRequestLayers(layers: Layer[]): RequestLayer[] {
  return layers.map((layer) => ({
    id: layer.id,
    type: layer.type,
    text: layer.text,
    x: round(layer.x),
    y: round(layer.y),
    scale: round(layer.scale),
    rotation: round(layer.rotation),
    style: layer.style,
    ...(layer.bannerId ? { bannerId: layer.bannerId } : {}),
  }));
}

/** Đọc `layers` từ API (khoảnh khắc video vẽ lớp lúc phát); phần tử không hợp lệ bị bỏ qua. */
export function fromResponseLayers(raw: unknown[]): Layer[] {
  return raw.flatMap((item): Layer[] => {
    if (typeof item !== "object" || item === null) return [];
    const value = item as Record<string, unknown>;
    const type = value.type;
    if (type !== "caption" && type !== "sticker" && type !== "banner") return [];
    const number = (key: string, fallback: number) => (typeof value[key] === "number" ? (value[key] as number) : fallback);
    const text = typeof value.text === "string" ? value.text : "";
    if (text.length === 0) return [];
    return [
      {
        id: typeof value.id === "string" ? value.id : newId(),
        type,
        text,
        x: number("x", 0.5),
        y: number("y", 0.5),
        scale: number("scale", 1),
        rotation: number("rotation", 0),
        style: typeof value.style === "string" ? value.style : CAPTION_STYLE,
        ...(typeof value.bannerId === "string" ? { bannerId: value.bannerId } : {}),
      },
    ];
  });
}
