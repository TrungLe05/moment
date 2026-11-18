"use client";

import { useId, useRef, useState, type KeyboardEvent, type PointerEvent } from "react";
import { useI18n } from "@/i18n/I18nProvider";
import { format, type Messages } from "@/i18n/messages";
import { layoutLayer, measureText } from "@/features/layers/layout";
import { LayerLines, layerStyle, useContentRect, type ContentRect } from "@/features/layers/layer-view";
import {
  addLayer,
  BANNERS,
  isEmpty,
  MAX_LAYERS,
  moveLayer,
  newLayer,
  transformLayer,
  type BannerId,
  type Layer,
} from "@/features/layers/model";
import { LayerTextPanel } from "./layer-text-panel";

/** Ảnh hoặc video đang soạn; `width`, `height` là kích thước thật để giữ đúng tỉ lệ khung. */
export type EditorMedia = { kind: "photo" | "video"; url: string; width: number; height: number };

type Props = {
  media: EditorMedia;
  layers: Layer[];
  onChange: (layers: Layer[]) => void;
};

type Gesture =
  | { kind: "move"; pointerId: number; startX: number; startY: number; layer: Layer }
  | { kind: "transform"; pointerId: number; centerX: number; centerY: number; distance: number; angle: number; layer: Layer };

type Panel = { kind: "text"; layerId: string } | { kind: "banner" } | null;

const MOVE_STEP = 0.01;
const MOVE_STEP_LARGE = 0.05;
const SCALE_STEP = 1.1;
const ROTATE_STEP = 5;
const CORNER_GAP = 10;
const toDegrees = (radians: number) => (radians * 180) / Math.PI;

function layerLabel(t: Messages, layer: Layer): string {
  const template = layer.type === "caption" ? t.compose.layerCaption : layer.type === "sticker" ? t.compose.layerSticker : t.compose.layerBanner;
  return format(template, { text: layer.text });
}

// Góc dưới phải và trên trái của lớp sau khi xoay, phóng to: chỗ đặt tay cầm và nút xóa.
function corner(layer: Layer, rect: ContentRect, sign: 1 | -1) {
  const box = layoutLayer(layer, rect.width, measureText);
  const radians = (layer.rotation * Math.PI) / 180;
  // Lùi ra ngoài góc một chút để nút không che chữ ở hai đầu lớp.
  const dx = sign * ((box.width * layer.scale) / 2 + CORNER_GAP);
  const dy = sign * ((box.height * layer.scale) / 2 + CORNER_GAP);
  return {
    left: layer.x * rect.width + dx * Math.cos(radians) - dy * Math.sin(radians),
    top: layer.y * rect.height + dx * Math.sin(radians) + dy * Math.cos(radians),
  };
}

/**
 * Trình soạn lớp trang trí (US-23, US-24): thêm caption, sticker, biểu ngữ; kéo để di chuyển, tay cầm để xoay và
 * phóng to; dùng được bằng bàn phím. Lớp đặt theo vùng ảnh thật trong khung, khớp với bản ghép xuất ra (ADR 0002).
 */
export function LayerEditor({ media, layers, onChange }: Props) {
  const { t } = useI18n();
  const helpId = useId();
  const frame = useRef<HTMLDivElement>(null);
  const overlay = useRef<HTMLDivElement>(null);
  const gesture = useRef<Gesture | null>(null);
  const aspect = media.width > 0 && media.height > 0 ? media.width / media.height : 3 / 4;
  const rect = useContentRect(frame, aspect);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [panel, setPanel] = useState<Panel>(null);
  const caption = layers.find((layer) => layer.type === "caption");
  const full = layers.length >= MAX_LAYERS;
  const selected = layers.find((layer) => layer.id === selectedId) ?? null;
  const editing = panel?.kind === "text" ? (layers.find((layer) => layer.id === panel.layerId) ?? null) : null;

  const replace = (next: Layer) => onChange(layers.map((layer) => (layer.id === next.id ? next : layer)));
  const remove = (id: string) => {
    onChange(layers.filter((layer) => layer.id !== id));
    if (selectedId === id) setSelectedId(null);
    if (panel?.kind === "text" && panel.layerId === id) setPanel(null);
  };

  const startText = (type: "caption" | "sticker") => {
    if (type === "caption" && caption) {
      setSelectedId(caption.id);
      setPanel({ kind: "text", layerId: caption.id });
      return;
    }
    const layer = newLayer(type, "");
    onChange(addLayer(layers, layer));
    setSelectedId(layer.id);
    setPanel({ kind: "text", layerId: layer.id });
  };

  const addBanner = (bannerId: BannerId) => {
    const layer = newLayer("banner", t.compose.banners[bannerId], bannerId);
    onChange(addLayer(layers, layer));
    setSelectedId(layer.id);
    setPanel(null);
  };

  const finishText = () => {
    if (editing && isEmpty(editing)) remove(editing.id);
    setPanel(null);
  };

  const contentBox = () => overlay.current?.getBoundingClientRect();

  const onLayerPointerDown = (event: PointerEvent<HTMLButtonElement>, layer: Layer) => {
    event.preventDefault();
    setSelectedId(layer.id);
    event.currentTarget.setPointerCapture?.(event.pointerId);
    gesture.current = { kind: "move", pointerId: event.pointerId, startX: event.clientX, startY: event.clientY, layer };
  };

  const onHandlePointerDown = (event: PointerEvent<HTMLButtonElement>, layer: Layer) => {
    event.preventDefault();
    event.stopPropagation();
    const box = contentBox();
    if (!box) return;
    const centerX = box.left + layer.x * box.width;
    const centerY = box.top + layer.y * box.height;
    event.currentTarget.setPointerCapture?.(event.pointerId);
    gesture.current = {
      kind: "transform",
      pointerId: event.pointerId,
      centerX,
      centerY,
      distance: Math.hypot(event.clientX - centerX, event.clientY - centerY),
      angle: Math.atan2(event.clientY - centerY, event.clientX - centerX),
      layer,
    };
  };

  const onPointerMove = (event: PointerEvent<HTMLButtonElement>) => {
    const current = gesture.current;
    if (!current || current.pointerId !== event.pointerId) return;
    if (current.kind === "move") {
      const box = contentBox();
      if (!box || box.width === 0 || box.height === 0) return;
      replace(
        moveLayer(
          current.layer,
          current.layer.x + (event.clientX - current.startX) / box.width,
          current.layer.y + (event.clientY - current.startY) / box.height,
        ),
      );
    } else if (current.distance > 0) {
      const distance = Math.hypot(event.clientX - current.centerX, event.clientY - current.centerY);
      const angle = Math.atan2(event.clientY - current.centerY, event.clientX - current.centerX);
      replace(
        transformLayer(
          current.layer,
          current.layer.scale * (distance / current.distance),
          current.layer.rotation + toDegrees(angle - current.angle),
        ),
      );
    }
  };

  const endGesture = (event: PointerEvent<HTMLButtonElement>) => {
    if (gesture.current?.pointerId === event.pointerId) gesture.current = null;
  };

  const onLayerKeyDown = (event: KeyboardEvent<HTMLButtonElement>, layer: Layer) => {
    const step = event.shiftKey ? MOVE_STEP_LARGE : MOVE_STEP;
    const moves: Record<string, [number, number]> = {
      ArrowLeft: [-step, 0],
      ArrowRight: [step, 0],
      ArrowUp: [0, -step],
      ArrowDown: [0, step],
    };
    const move = moves[event.key];
    if (move) {
      replace(moveLayer(layer, layer.x + move[0], layer.y + move[1]));
    } else if (event.key === "+" || event.key === "=") {
      replace(transformLayer(layer, layer.scale * SCALE_STEP, layer.rotation));
    } else if (event.key === "-") {
      replace(transformLayer(layer, layer.scale / SCALE_STEP, layer.rotation));
    } else if (event.key === "[" || event.key === "]") {
      replace(transformLayer(layer, layer.scale, layer.rotation + (event.key === "]" ? ROTATE_STEP : -ROTATE_STEP)));
    } else if (event.key === "Delete" || event.key === "Backspace") {
      remove(layer.id);
    } else if (event.key === "Enter" && layer.type !== "banner") {
      setPanel({ kind: "text", layerId: layer.id });
    } else {
      return;
    }
    event.preventDefault();
  };

  const toolButton =
    "flex h-[60px] flex-col items-center justify-center gap-1 rounded-2xl bg-night-surface text-xs font-medium disabled:opacity-40 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-peach";
  const cornerButton =
    "absolute flex size-11 -translate-x-1/2 -translate-y-1/2 touch-none items-center justify-center rounded-full bg-night-text text-night shadow focus-visible:outline-2 focus-visible:outline-peach";

  return (
    <div className="flex flex-col gap-3">
      <div ref={frame} className="relative aspect-[358/410] w-full overflow-hidden rounded-[28px] bg-night-surface">
        {media.kind === "photo" ? (
          // eslint-disable-next-line @next/next/no-img-element -- ảnh ở máy (object URL), next/image không dùng được.
          <img src={media.url} alt={t.compose.capturedPhoto} className="h-full w-full object-contain" />
        ) : (
          <video src={media.url} aria-label={t.compose.capturedVideo} autoPlay loop muted playsInline className="h-full w-full object-contain" />
        )}
        <div
          ref={overlay}
          className="absolute"
          style={{ left: rect.left, top: rect.top, width: rect.width, height: rect.height }}
          onPointerDown={(event) => {
            if (event.target === event.currentTarget) setSelectedId(null);
          }}
        >
          {layers.map((layer) => (
            <button
              key={layer.id}
              type="button"
              aria-label={layerLabel(t, layer)}
              aria-describedby={helpId}
              style={layerStyle(layer, rect.width)}
              className={`touch-none select-none focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-peach ${
                layer.id === selectedId ? "outline-[1.5px] outline-offset-4 outline-white outline-dashed" : ""
              }`}
              onPointerDown={(event) => onLayerPointerDown(event, layer)}
              onPointerMove={onPointerMove}
              onPointerUp={endGesture}
              onPointerCancel={endGesture}
              onKeyDown={(event) => onLayerKeyDown(event, layer)}
              onDoubleClick={() => layer.type !== "banner" && setPanel({ kind: "text", layerId: layer.id })}
            >
              <LayerLines layer={layer} frameWidth={rect.width} />
            </button>
          ))}
          {selected && (
            <>
              <button
                type="button"
                aria-label={t.compose.removeLayer}
                className={cornerButton}
                style={corner(selected, rect, -1)}
                onClick={() => remove(selected.id)}
              >
                <svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
                  <path d="M6 6l12 12M18 6L6 18" />
                </svg>
              </button>
              <button
                type="button"
                aria-label={t.compose.transformLayer}
                className={cornerButton}
                style={corner(selected, rect, 1)}
                onPointerDown={(event) => onHandlePointerDown(event, selected)}
                onPointerMove={onPointerMove}
                onPointerUp={endGesture}
                onPointerCancel={endGesture}
              >
                <svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M20 11a8 8 0 1 0-2.3 5.7" />
                  <path d="M20 4v7h-7" />
                </svg>
              </button>
            </>
          )}
        </div>
        {/* BR-09: nguồn của khoảnh khắc. */}
        <span className="pointer-events-none absolute top-3.5 right-3.5 rounded-full bg-night/60 px-3 py-1.5 text-xs font-medium text-white backdrop-blur-md">
          {t.compose.camera}
        </span>
      </div>
      <p id={helpId} className="sr-only">
        {t.compose.layerHelp}
      </p>

      {editing ? (
        <LayerTextPanel layer={editing} onChange={replace} onDone={finishText} onRemove={() => remove(editing.id)} />
      ) : panel?.kind === "banner" ? (
        <div role="group" aria-label={t.compose.pickBanner} className="flex flex-wrap gap-2">
          {BANNERS.map((banner) => (
            <button
              key={banner.id}
              type="button"
              onClick={() => addBanner(banner.id)}
              className="h-11 rounded-full bg-night-surface px-4 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-peach"
            >
              {t.compose.banners[banner.id]}
            </button>
          ))}
        </div>
      ) : (
        <div role="group" aria-label={t.compose.decorate} className="grid grid-cols-3 gap-2">
          <button type="button" className={toolButton} disabled={full && !caption} onClick={() => startText("caption")}>
            <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <path d="M5 6V4h14v2" />
              <path d="M12 4v16" />
              <path d="M9 20h6" />
            </svg>
            {t.compose.addCaption}
          </button>
          <button type="button" className={toolButton} disabled={full} onClick={() => startText("sticker")}>
            <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M14 3H6a3 3 0 0 0-3 3v12a3 3 0 0 0 3 3h7l8-8V6a3 3 0 0 0-3-3z" />
              <path d="M13 21v-5a3 3 0 0 1 3-3h5" />
            </svg>
            {t.compose.addSticker}
          </button>
          <button type="button" className={toolButton} disabled={full} onClick={() => setPanel({ kind: "banner" })}>
            <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M3 8l9-4 9 4-9 4z" />
              <path d="M7 10v5c3 2 7 2 10 0v-5" />
            </svg>
            {t.compose.addBanner}
          </button>
        </div>
      )}
      {full && <p className="text-center text-sm text-night-muted">{t.compose.layerLimit}</p>}
    </div>
  );
}
