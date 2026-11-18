import { describe, expect, it } from "vitest";
import { layoutLayer, wrapText } from "./layout";
import {
  addLayer,
  limitText,
  MAX_CAPTION_CHARACTERS,
  MAX_LAYERS,
  MAX_LAYER_TEXT_CHARACTERS,
  moveLayer,
  newLayer,
  toRequestLayers,
  transformLayer,
  type Layer,
} from "./model";

// Đo giả: mỗi ký tự rộng nửa cỡ chữ, đủ để thử ngắt dòng mà không cần canvas (jsdom không có).
const measure = (text: string, fontPx: number) => [...text].length * fontPx * 0.5;

describe("limitText (BR-11)", () => {
  it("stops at the character limit counted by graphemes", () => {
    const emoji = "👍🏽";
    expect(limitText(emoji.repeat(50), MAX_LAYER_TEXT_CHARACTERS)).toBe(emoji.repeat(40));
    expect(limitText("Việt Nam", 4)).toBe("Việt");
  });

  it("keeps at most the allowed number of lines", () => {
    expect(limitText("một\nhai\nba\nbốn", MAX_CAPTION_CHARACTERS, 3)).toBe("một\nhai\nba");
  });
});

describe("layer list (BR-11, DB mục 5)", () => {
  it("allows one caption: adding another replaces it", () => {
    const first = newLayer("caption", "trời đẹp");
    const second = newLayer("caption", "trời mưa");
    const layers = addLayer(addLayer([], first), second);

    expect(layers).toHaveLength(1);
    expect(layers[0]).toMatchObject({ type: "caption", text: "trời mưa" });
  });

  it("refuses more than 20 layers", () => {
    let layers: Layer[] = [];
    for (let i = 0; i < MAX_LAYERS + 3; i++) layers = addLayer(layers, newLayer("sticker", `s${i}`));
    expect(layers).toHaveLength(MAX_LAYERS);
  });

  it("keeps position inside the frame and scale and rotation in range", () => {
    const layer = newLayer("sticker", "hi");
    expect(moveLayer(layer, 1.4, -0.2)).toMatchObject({ x: 1, y: 0 });
    expect(transformLayer(layer, 9, 0)).toMatchObject({ scale: 5 });
    expect(transformLayer(layer, 0.05, 0)).toMatchObject({ scale: 0.2 });
    expect(transformLayer(layer, 1, 200).rotation).toBeCloseTo(-160);
    expect(transformLayer(layer, 1, -190).rotation).toBeCloseTo(170);
  });

  it("maps layers to the API shape with ids and codes the server accepts", () => {
    const caption = { ...newLayer("caption", "xin chào"), x: 0.123456, y: 0.9 };
    const banner = newLayer("banner", "Chào buổi sáng", "good-morning");
    const request = toRequestLayers([caption, banner]);

    expect(request[0]).toEqual({
      id: caption.id,
      type: "caption",
      text: "xin chào",
      x: 0.1235,
      y: 0.9,
      scale: 1,
      rotation: 0,
      style: "pill-dark",
    });
    expect(request[1]).toMatchObject({ type: "banner", bannerId: "good-morning", text: "Chào buổi sáng" });
    for (const layer of request) {
      expect(layer.id).toMatch(/^[A-Za-z0-9_-]{1,40}$/);
      expect(layer.style).toMatch(/^[a-z0-9-]{1,40}$/);
    }
  });
});

describe("text layout", () => {
  it("wraps words to the width and breaks a word that is too long on its own", () => {
    expect(wrapText("một hai ba bốn", 40, 10, measure)).toEqual(["một hai", "ba bốn"]);
    expect(wrapText("aaaaaaaaaaaa", 30, 10, measure)).toEqual(["aaaaaa", "aaaaaa"]);
    expect(wrapText("dòng một\ndòng hai", 400, 10, measure)).toEqual(["dòng một", "dòng hai"]);
  });

  // US-23: caption tự xuống dòng trong vùng an toàn và tự thu nhỏ cỡ chữ.
  it("shrinks a long caption so it fits in three lines inside the safe area", () => {
    // Chữ rộng (mỗi ký tự bằng cỡ chữ): ở cỡ lớn nhất câu này cần 4 dòng.
    const wide = (text: string, fontPx: number) => [...text].length * fontPx;
    const short = layoutLayer(newLayer("caption", "trời đẹp"), 400, wide);
    const long = layoutLayer(newLayer("caption", "nắng vàng ".repeat(5).trim()), 400, wide);

    expect(long.fontSize).toBeLessThan(short.fontSize);
    expect(long.lines.length).toBeLessThanOrEqual(3);
    expect(long.width).toBeLessThanOrEqual(400 * 0.84 + 1);
  });

  it("scales the layout with the frame width so every screen and the export look the same", () => {
    const small = layoutLayer(newLayer("sticker", "vibe check"), 300, measure);
    const large = layoutLayer(newLayer("sticker", "vibe check"), 1200, measure);
    expect(large.fontSize / small.fontSize).toBeCloseTo(4);
    expect(large.width / small.width).toBeCloseTo(4);
    expect(large.lines).toEqual(small.lines);
  });
});
