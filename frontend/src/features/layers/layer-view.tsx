"use client";

import { useLayoutEffect, useRef, useState, type CSSProperties, type RefObject } from "react";
import { LAYER_FONT_FAMILY, layoutLayer, measureText } from "./layout";
import { fromResponseLayers, type Layer } from "./model";
import { layerColors } from "./styles";

export type ContentRect = { left: number; top: number; width: number; height: number };

/**
 * Vùng ảnh hoặc video thật bên trong khung khi hiển thị kiểu contain (giữ tỉ lệ `aspect` = rộng / cao).
 * Lớp đặt theo vùng này để đúng chỗ dù khung bị giới hạn chiều cao.
 */
export function useContentRect(container: RefObject<HTMLElement | null>, aspect: number): ContentRect {
  const [size, setSize] = useState({ width: 0, height: 0 });

  useLayoutEffect(() => {
    const element = container.current;
    if (!element) return;
    const update = () => setSize({ width: element.clientWidth, height: element.clientHeight });
    update();
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(update);
    observer.observe(element);
    return () => observer.disconnect();
  }, [container]);

  if (size.width === 0 || size.height === 0) return { left: 0, top: 0, width: size.width, height: size.height };
  const byWidth = { width: size.width, height: size.width / aspect };
  const fitted = byWidth.height <= size.height ? byWidth : { width: size.height * aspect, height: size.height };
  return { left: (size.width - fitted.width) / 2, top: (size.height - fitted.height) / 2, ...fitted };
}

/** Kiểu CSS của một lớp trong vùng rộng `frameWidth`, cùng bố cục với bản vẽ canvas (drawLayers). */
export function layerStyle(layer: Layer, frameWidth: number): CSSProperties {
  const box = layoutLayer(layer, frameWidth, measureText);
  const colors = layerColors(layer.style);
  return {
    position: "absolute",
    left: `${layer.x * 100}%`,
    top: `${layer.y * 100}%`,
    width: box.width,
    minHeight: box.height,
    transform: `translate(-50%, -50%) rotate(${layer.rotation}deg) scale(${layer.scale})`,
    padding: `${box.paddingY}px ${box.paddingX}px`,
    borderRadius: box.radius,
    background: colors.background,
    color: colors.color,
    fontFamily: LAYER_FONT_FAMILY,
    fontSize: box.fontSize,
    fontWeight: box.fontWeight,
    lineHeight: `${box.lineHeight}px`,
    textAlign: "center",
    whiteSpace: "pre",
    boxSizing: "border-box",
  };
}

/** Các dòng chữ đã ngắt sẵn của lớp (cùng cách ngắt với bản xuất ảnh). */
export function LayerLines({ layer, frameWidth }: { layer: Layer; frameWidth: number }) {
  return layoutLayer(layer, frameWidth, measureText).lines.map((line, index) => (
    <span key={index} className="block">
      {line}
    </span>
  ));
}

/**
 * Lớp trang trí của một khoảnh khắc video đọc từ API, vẽ đè lên khung chứa nó (cần `position: relative`) theo tỉ lệ
 * video (ADR 0002: video không ghép lớp vào file, client vẽ lúc phát). Tự đo một lớp phủ kín khung của chính nó:
 * ref của component cha chưa được gắn khi effect của component con chạy lần đầu.
 */
export function VideoLayers({ layers, width, height }: { layers: unknown[]; width: number | null; height: number | null }) {
  const cover = useRef<HTMLDivElement>(null);
  const rect = useContentRect(cover, width && height ? width / height : 3 / 4);
  const parsed = fromResponseLayers(layers);
  if (parsed.length === 0) return null;
  return (
    <div ref={cover} className="pointer-events-none absolute inset-0">
      <LayerOverlay layers={parsed} rect={rect} />
    </div>
  );
}

/** Lớp trang trí chỉ để xem, đè lên video ở feed và kho (ADR 0002: video không ghép lớp vào file). */
export function LayerOverlay({ layers, rect }: { layers: Layer[]; rect: ContentRect }) {
  if (layers.length === 0) return null;
  return (
    <div className="pointer-events-none absolute" style={{ left: rect.left, top: rect.top, width: rect.width, height: rect.height }}>
      {layers.map((layer) => (
        <div key={layer.id} style={layerStyle(layer, rect.width)}>
          <LayerLines layer={layer} frameWidth={rect.width} />
        </div>
      ))}
    </div>
  );
}
