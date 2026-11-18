import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { I18nProvider } from "@/i18n/I18nProvider";
import { format, messages } from "@/i18n/messages";
import { MAX_LAYERS, newLayer, type Layer } from "@/features/layers/model";
import { LayerEditor, type EditorMedia } from "./layer-editor";

const t = messages.en;
const media: EditorMedia = { kind: "photo", url: "blob:photo", width: 400, height: 300 };
let latest: Layer[] = [];

function Harness({ initial = [] }: { initial?: Layer[] }) {
  const [layers, setLayers] = useState<Layer[]>(initial);
  const change = (next: Layer[]) => {
    latest = next;
    setLayers(next);
  };
  return <LayerEditor media={media} layers={layers} onChange={change} />;
}

function renderEditor(initial?: Layer[]) {
  return render(
    <I18nProvider>
      <Harness initial={initial} />
    </I18nProvider>,
  );
}

describe("LayerEditor (US-23, US-24, BR-11)", () => {
  beforeEach(() => {
    latest = [];
    // Khung 400 × 300 ở góc màn hình: jsdom không tự bố cục.
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockReturnValue(DOMRect.fromRect({ x: 0, y: 0, width: 400, height: 300 }));
    vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(400);
    vi.spyOn(HTMLElement.prototype, "clientHeight", "get").mockReturnValue(300);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("adds a caption that stops taking characters at 80 and shows the counter", async () => {
    renderEditor();

    await userEvent.click(screen.getByRole("button", { name: t.compose.addCaption }));
    const field = screen.getByRole("textbox", { name: t.compose.captionField });
    // Dán một lần thay vì gõ từng phím cho nhanh; giới hạn áp như nhau qua onChange.
    await userEvent.paste("a".repeat(85));

    expect(field).toHaveValue("a".repeat(80));
    expect(screen.getByText(format(t.compose.counter, { count: "80", max: "80" }))).toBeInTheDocument();
    expect(screen.getByRole("button", { name: format(t.compose.layerCaption, { text: "a".repeat(80) }) })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: t.compose.done }));
    expect(screen.queryByRole("textbox", { name: t.compose.captionField })).toBeNull();
    expect(latest).toEqual([expect.objectContaining({ type: "caption", style: "pill-dark" })]);
  });

  it("edits the existing caption instead of adding a second one", async () => {
    renderEditor([newLayer("caption", "trời đẹp")]);

    await userEvent.click(screen.getByRole("button", { name: t.compose.addCaption }));

    expect(screen.getByRole("textbox", { name: t.compose.captionField })).toHaveValue("trời đẹp");
  });

  it("adds a sticker in the chosen style and drops it if the text is left empty", async () => {
    renderEditor();

    await userEvent.click(screen.getByRole("button", { name: t.compose.addSticker }));
    await userEvent.type(screen.getByRole("textbox", { name: t.compose.stickerField }), "vibe check");
    await userEvent.click(screen.getByRole("radio", { name: t.compose.styles.peach }));
    await userEvent.click(screen.getByRole("button", { name: t.compose.done }));
    expect(latest).toEqual([expect.objectContaining({ type: "sticker", text: "vibe check", style: "peach" })]);

    await userEvent.click(screen.getByRole("button", { name: t.compose.addSticker }));
    await userEvent.click(screen.getByRole("button", { name: t.compose.done }));
    expect(latest).toHaveLength(1);
  });

  it("adds a ready-made banner with its text in my language", async () => {
    renderEditor();

    await userEvent.click(screen.getByRole("button", { name: t.compose.addBanner }));
    await userEvent.click(screen.getByRole("button", { name: t.compose.banners["good-morning"] }));

    expect(screen.getByRole("button", { name: format(t.compose.layerBanner, { text: "Good morning" }) })).toBeInTheDocument();
    expect(latest).toEqual([expect.objectContaining({ type: "banner", bannerId: "good-morning", text: "Good morning" })]);
  });

  it("moves, resizes, rotates and removes a layer with the keyboard", async () => {
    renderEditor([{ ...newLayer("sticker", "hi"), rotation: 0 }]);
    const layer = screen.getByRole("button", { name: format(t.compose.layerSticker, { text: "hi" }) });
    layer.focus();

    await userEvent.keyboard("{ArrowRight}{ArrowDown}+]");
    expect(latest[0]).toMatchObject({ x: 0.51, y: 0.46, rotation: 5 });
    expect(latest[0]?.scale).toBeCloseTo(1.1);

    await userEvent.keyboard("{Delete}");
    expect(latest).toEqual([]);
  });

  it("drags a layer with the pointer", () => {
    renderEditor([newLayer("sticker", "hi")]);
    const layer = screen.getByRole("button", { name: format(t.compose.layerSticker, { text: "hi" }) });

    fireEvent.pointerDown(layer, { pointerId: 1, clientX: 200, clientY: 135 });
    fireEvent.pointerMove(layer, { pointerId: 1, clientX: 240, clientY: 165 });
    fireEvent.pointerUp(layer, { pointerId: 1, clientX: 240, clientY: 165 });

    expect(latest[0]?.x).toBeCloseTo(0.6);
    expect(latest[0]?.y).toBeCloseTo(0.55);
  });

  it("rotates and resizes the selected layer with its handle", async () => {
    renderEditor([{ ...newLayer("sticker", "hi"), rotation: 0 }]);
    await userEvent.click(screen.getByRole("button", { name: format(t.compose.layerSticker, { text: "hi" }) }));
    const handle = screen.getByRole("button", { name: t.compose.transformLayer });

    // Tâm lớp ở (200, 135): kéo tay cầm từ 60 px bên phải xuống 120 px phía dưới tâm.
    fireEvent.pointerDown(handle, { pointerId: 2, clientX: 260, clientY: 135 });
    fireEvent.pointerMove(handle, { pointerId: 2, clientX: 200, clientY: 255 });
    fireEvent.pointerUp(handle, { pointerId: 2, clientX: 200, clientY: 255 });

    expect(latest[0]?.scale).toBeCloseTo(2);
    expect(latest[0]?.rotation).toBeCloseTo(90);
  });

  it("stops adding at 20 decorations", () => {
    renderEditor(Array.from({ length: MAX_LAYERS }, (_, i) => newLayer("sticker", `s${i}`)));

    expect(screen.getByRole("button", { name: t.compose.addSticker })).toBeDisabled();
    expect(screen.getByRole("button", { name: t.compose.addBanner })).toBeDisabled();
    expect(screen.getByText(t.compose.layerLimit)).toBeInTheDocument();
  });
});
