import type { Layer } from "./model";

/** Đo bề rộng một dòng chữ ở cỡ chữ cho trước (px). Trình duyệt dùng canvas; test dùng hàm đo giả. */
export type Measure = (text: string, fontPx: number, fontWeight: number) => number;

/** Bố cục một lớp ở `scale` 1, tính bằng px của khung rộng `frameWidth`. */
export type TextBox = {
  lines: string[];
  fontSize: number;
  fontWeight: number;
  lineHeight: number;
  paddingX: number;
  paddingY: number;
  width: number;
  height: number;
  radius: number;
};

// Font chung cho hiển thị trên màn hình và xuất ảnh ghép, để hai bên đo chữ giống nhau. Font chính thức chọn ở T-010.
export const LAYER_FONT_FAMILY = 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif';

type Spec = { maxFont: number; minFont: number; maxWidth: number; weight: number; padX: number; padY: number };

// Tỉ lệ theo bề rộng khung. Caption nằm trong vùng an toàn 84% bề rộng, tự thu nhỏ từ 5,5% xuống 3,8% (US-23).
const SPECS: Record<Layer["type"], Spec> = {
  caption: { maxFont: 0.055, minFont: 0.038, maxWidth: 0.84, weight: 600, padX: 0.6, padY: 0.35 },
  sticker: { maxFont: 0.06, minFont: 0.06, maxWidth: 0.7, weight: 600, padX: 0.5, padY: 0.3 },
  banner: { maxFont: 0.062, minFont: 0.062, maxWidth: 0.8, weight: 800, padX: 0.7, padY: 0.35 },
};
const MAX_CAPTION_WRAPPED_LINES = 3;
const LINE_HEIGHT = 1.25;
const SHRINK_STEP = 0.002;

function graphemes(text: string): string[] {
  return [...new Intl.Segmenter(undefined, { granularity: "grapheme" }).segment(text)].map((part) => part.segment);
}

/** Ngắt dòng theo từ cho vừa `maxWidth`; dấu xuống dòng của người dùng giữ nguyên; từ dài quá thì ngắt theo ký tự. */
export function wrapText(text: string, maxWidth: number, fontPx: number, measure: Measure, fontWeight = 600): string[] {
  const fits = (line: string) => measure(line, fontPx, fontWeight) <= maxWidth;
  const lines: string[] = [];
  for (const paragraph of text.split("\n")) {
    let current = "";
    for (const word of paragraph.split(" ").filter((part) => part.length > 0)) {
      const candidate = current ? `${current} ${word}` : word;
      if (fits(candidate)) {
        current = candidate;
        continue;
      }
      if (current) lines.push(current);
      current = "";
      if (fits(word)) {
        current = word;
        continue;
      }
      for (const character of graphemes(word)) {
        if (current && !fits(current + character)) {
          lines.push(current);
          current = "";
        }
        current += character;
      }
    }
    lines.push(current);
  }
  return lines;
}

export function layoutLayer(layer: Layer, frameWidth: number, measure: Measure): TextBox {
  const spec = SPECS[layer.type];
  const maxWidth = spec.maxWidth * frameWidth;
  let fontRatio = spec.maxFont;
  let fontSize = fontRatio * frameWidth;
  let lines = wrapText(layer.text, maxWidth - 2 * spec.padX * fontSize, fontSize, measure, spec.weight);
  while (layer.type === "caption" && lines.length > MAX_CAPTION_WRAPPED_LINES && fontRatio > spec.minFont) {
    fontRatio = Math.max(spec.minFont, fontRatio - SHRINK_STEP);
    fontSize = fontRatio * frameWidth;
    lines = wrapText(layer.text, maxWidth - 2 * spec.padX * fontSize, fontSize, measure, spec.weight);
  }
  const paddingX = spec.padX * fontSize;
  const paddingY = spec.padY * fontSize;
  const lineHeight = LINE_HEIGHT * fontSize;
  const textWidth = Math.max(0, ...lines.map((line) => measure(line, fontSize, spec.weight)));
  const width = Math.min(maxWidth, textWidth + 2 * paddingX);
  const height = lines.length * lineHeight + 2 * paddingY;
  const radius = layer.type === "caption" ? Math.min(height / 2, fontSize * 1.1) : layer.type === "sticker" ? fontSize * 0.25 : 0;
  return { lines, fontSize, fontWeight: spec.weight, lineHeight, paddingX, paddingY, width, height, radius };
}

let measureContext: CanvasRenderingContext2D | null | undefined;

/** Đo bằng canvas của trình duyệt; không có canvas (môi trường test) thì ước lượng theo số ký tự. */
export const measureText: Measure = (text, fontPx, fontWeight) => {
  if (measureContext === undefined) {
    measureContext = typeof document === "undefined" ? null : document.createElement("canvas").getContext("2d");
  }
  if (!measureContext) return graphemes(text).length * fontPx * 0.55;
  measureContext.font = `${fontWeight} ${fontPx}px ${LAYER_FONT_FAMILY}`;
  return measureContext.measureText(text).width;
};
