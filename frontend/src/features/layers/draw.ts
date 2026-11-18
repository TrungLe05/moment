import { LAYER_FONT_FAMILY, layoutLayer, measureText, type Measure } from "./layout";
import type { Layer } from "./model";
import { layerColors } from "./styles";

/** Vẽ các lớp lên canvas khung `width` × `height`, cùng bố cục với LayerShape trên màn hình (ADR 0002). */
export function drawLayers(
  context: CanvasRenderingContext2D,
  layers: Layer[],
  width: number,
  height: number,
  measure: Measure = measureText,
): void {
  for (const layer of layers) {
    const box = layoutLayer(layer, width, measure);
    const colors = layerColors(layer.style);
    context.save();
    context.translate(layer.x * width, layer.y * height);
    context.rotate((layer.rotation * Math.PI) / 180);
    context.scale(layer.scale, layer.scale);
    context.fillStyle = colors.background;
    context.beginPath();
    context.roundRect(-box.width / 2, -box.height / 2, box.width, box.height, box.radius);
    context.fill();
    context.fillStyle = colors.color;
    context.font = `${box.fontWeight} ${box.fontSize}px ${LAYER_FONT_FAMILY}`;
    context.textAlign = "center";
    context.textBaseline = "middle";
    box.lines.forEach((line, index) => {
      context.fillText(line, 0, -box.height / 2 + box.paddingY + box.lineHeight * (index + 0.5));
    });
    context.restore();
  }
}

// Bản ghép không cần lớn hơn thế: worker thu về 1440 px (DB mục 6).
const MAX_COMPOSED_EDGE = 2560;
const JPEG_QUALITY = 0.9;

/** ADR 0002: xuất bản ghép (`COMPOSED`) từ ảnh gốc và các lớp, JPEG, giữ nguyên tỉ lệ ảnh. */
export async function exportComposed(photo: Blob, layers: Layer[]): Promise<Blob> {
  const bitmap = await createImageBitmap(photo);
  const ratio = Math.min(1, MAX_COMPOSED_EDGE / Math.max(bitmap.width, bitmap.height));
  const width = Math.round(bitmap.width * ratio);
  const height = Math.round(bitmap.height * ratio);
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext("2d");
  if (!context) throw new Error("Không vẽ được ảnh ghép");
  context.drawImage(bitmap, 0, 0, width, height);
  bitmap.close();
  drawLayers(context, layers, width, height);
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("Không xuất được ảnh ghép"))), "image/jpeg", JPEG_QUALITY);
  });
}
