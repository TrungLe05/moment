import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Layer } from "@/features/layers/model";
import { I18nProvider } from "@/i18n/I18nProvider";
import { format, messages } from "@/i18n/messages";
import type { FriendList } from "@/lib/api/friends";
import type { MediaInfo } from "@/lib/media/probe";
import { CameraError, type CameraFacing } from "@/platform";
import { ComposeView } from "./compose-view";
import type { PendingDraft } from "./pending-moments";

const openStream = vi.fn<(facing: CameraFacing) => Promise<MediaStream>>();
const closeStream = vi.fn<(stream: MediaStream) => void>();
const probeMedia = vi.fn<(file: Blob, kind: "photo" | "video") => Promise<MediaInfo>>();
const exportComposed = vi.fn<(photo: Blob, layers: Layer[]) => Promise<Blob>>();
const submit = vi.fn<(draft: PendingDraft) => Promise<void>>();

vi.mock("@/platform", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/platform")>()),
  platform: {
    camera: {
      isSupported: () => true,
      openStream: (facing: CameraFacing) => openStream(facing),
      closeStream: (stream: MediaStream) => closeStream(stream),
      takePhoto: vi.fn(),
      startRecording: vi.fn(),
    },
  },
}));

vi.mock("@/lib/media/probe", () => ({
  probeMedia: (file: Blob, kind: "photo" | "video") => probeMedia(file, kind),
}));

vi.mock("@/features/layers/draw", () => ({
  exportComposed: (photo: Blob, layers: Layer[]) => exportComposed(photo, layers),
}));

vi.mock("@/lib/api/friends", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api/friends")>()),
  friendsApi: {
    list: (): Promise<FriendList> =>
      Promise.resolve({
        items: [{ id: "u2", username: "minh", displayName: "Minh", avatarUrl: null, friendsSince: "2026-11-01T00:00:00Z" }],
        count: 1,
        limit: 15,
      }),
  },
}));

vi.mock("./pending-moments", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./pending-moments")>()),
  pendingMoments: { submit: (draft: PendingDraft) => submit(draft) },
}));

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: () => undefined }) }));

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

const t = messages.en;

function picked(type: string, size = 2048): File {
  const file = new File(["bytes"], "anything.bin", { type });
  Object.defineProperty(file, "size", { value: size });
  return file;
}

async function renderAndPick(file: File) {
  render(
    <I18nProvider>
      <ComposeView />
    </I18nProvider>,
  );
  // Không lọc theo `accept` để thử cả file sai định dạng.
  const user = userEvent.setup({ applyAccept: false });
  await user.upload(await screen.findByLabelText(t.compose.upload), file);
  return user;
}

async function send(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole("button", { name: format(t.compose.everyone, { count: "1" }) });
  await user.click(screen.getByRole("button", { name: t.compose.send }));
  return submit.mock.calls[0]![0];
}

describe("ComposeView upload from device (US-22, BR-09, BR-10)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    openStream.mockImplementation((facing) => Promise.resolve({ id: facing } as unknown as MediaStream));
    submit.mockResolvedValue(undefined);
    let created = 0;
    URL.createObjectURL = vi.fn(() => `blob:${++created}`);
    URL.revokeObjectURL = vi.fn();
  });

  it("uploads a JPEG photo labelled as uploaded, without redrawing it", async () => {
    probeMedia.mockResolvedValue({ width: 1200, height: 1600, durationMs: null });
    const file = picked("image/jpeg");
    const user = await renderAndPick(file);

    expect(await screen.findByRole("img", { name: t.compose.pickedPhoto })).toBeInTheDocument();
    // BR-09: khoảnh khắc tải lên luôn có nhãn "tải lên".
    expect(screen.getByText(t.feed.uploaded)).toBeInTheDocument();
    expect(closeStream).toHaveBeenCalled();

    const draft = await send(user);
    expect(draft.request).toMatchObject({ mediaType: "PHOTO", source: "UPLOAD", trimStartMs: null });
    expect(draft.request.files[0]).toEqual({ variant: "ORIGINAL", contentType: "image/jpeg", sizeBytes: 2048 });
    expect(exportComposed).not.toHaveBeenCalled();
  });

  // API mục 3.4: COMPOSED chỉ nhận JPEG hoặc WebP, nên ảnh PNG luôn được ghép lại thành JPEG.
  it("redraws a PNG photo as a JPEG composed image even without decorations", async () => {
    probeMedia.mockResolvedValue({ width: 800, height: 600, durationMs: null });
    const composed = new Blob(["jpeg"], { type: "image/jpeg" });
    exportComposed.mockResolvedValue(composed);
    const user = await renderAndPick(picked("image/png"));
    await screen.findByRole("img", { name: t.compose.pickedPhoto });

    const draft = await send(user);
    expect(exportComposed).toHaveBeenCalledWith(expect.any(Blob), []);
    expect(draft.request.files).toEqual([
      { variant: "ORIGINAL", contentType: "image/png", sizeBytes: 2048 },
      { variant: "COMPOSED", contentType: "image/jpeg", sizeBytes: composed.size },
    ]);
  });

  it("rejects a file of another format or over the size limit before uploading", async () => {
    await renderAndPick(picked("image/heic"));
    expect(await screen.findByRole("alert")).toHaveTextContent(t.compose.uploadErrors.UNSUPPORTED_MEDIA_TYPE);

    const user = userEvent.setup({ applyAccept: false });
    await user.upload(screen.getByLabelText(t.compose.upload), picked("video/mp4", 50 * 1024 * 1024 + 1));
    expect(await screen.findByRole("alert")).toHaveTextContent(t.compose.uploadErrors.PAYLOAD_TOO_LARGE);
    expect(probeMedia).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: t.compose.takePhoto })).toBeInTheDocument();
  });

  it("rejects a video longer than 60 seconds", async () => {
    probeMedia.mockResolvedValue({ width: 1280, height: 720, durationMs: 75_000 });
    await renderAndPick(picked("video/mp4"));

    expect(await screen.findByRole("alert")).toHaveTextContent(t.compose.uploadErrors.VIDEO_TOO_LONG);
  });

  it("says when the file cannot be opened", async () => {
    probeMedia.mockRejectedValue(new Error("unreadable"));
    await renderAndPick(picked("video/webm"));

    expect(await screen.findByRole("alert")).toHaveTextContent(t.compose.uploadErrors.MEDIA_UNREADABLE);
  });

  // US-22: video dài hơn 5 giây thì chọn đoạn; điểm bắt đầu gửi lên server (BR-10).
  it("lets me pick the 5-second clip of a longer video and sends its start", async () => {
    probeMedia.mockResolvedValue({ width: 1280, height: 720, durationMs: 20_000 });
    const user = await renderAndPick(picked("video/quicktime"));

    const slider = await screen.findByRole("slider", { name: t.compose.trimLabel });
    expect(slider).toHaveAttribute("max", "15");
    fireEvent.change(slider, { target: { value: "7" } });
    expect(screen.getByText(format(t.compose.trimRange, { start: "7", end: "12" }))).toBeInTheDocument();

    const draft = await send(user);
    expect(draft.request).toMatchObject({ mediaType: "VIDEO", source: "UPLOAD", trimStartMs: 7000 });
    expect(draft.request.files).toEqual([{ variant: "ORIGINAL", contentType: "video/quicktime", sizeBytes: 2048 }]);
  });

  it("needs no clip picker for a video of 5 seconds or less", async () => {
    probeMedia.mockResolvedValue({ width: 720, height: 1280, durationMs: 4_000 });
    const user = await renderAndPick(picked("video/mp4"));
    await screen.findByLabelText(t.compose.pickedVideo);

    expect(screen.queryByRole("slider")).toBeNull();
    const draft = await send(user);
    expect(draft.request.trimStartMs).toBeNull();
  });

  // US-20: trình duyệt chưa cấp quyền camera thì vẫn tải từ máy được.
  it("keeps upload available when camera access is denied", async () => {
    openStream.mockRejectedValue(new CameraError("denied"));
    probeMedia.mockResolvedValue({ width: 1200, height: 1600, durationMs: null });
    await renderAndPick(picked("image/webp"));

    expect(await screen.findByRole("img", { name: t.compose.pickedPhoto })).toBeInTheDocument();
  });
});
