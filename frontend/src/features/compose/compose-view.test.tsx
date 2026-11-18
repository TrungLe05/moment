import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { I18nProvider } from "@/i18n/I18nProvider";
import { messages } from "@/i18n/messages";
import { CameraError, type CameraFacing, type CapturedMedia, type VideoRecording } from "@/platform";
import { ComposeView } from "./compose-view";

const isSupported = vi.fn<() => boolean>();
const openStream = vi.fn<(facing: CameraFacing) => Promise<MediaStream>>();
const closeStream = vi.fn<(stream: MediaStream) => void>();
const takePhoto = vi.fn<(source: HTMLVideoElement) => Promise<CapturedMedia>>();
const startRecording = vi.fn<(stream: MediaStream, maxDurationMs: number) => Promise<VideoRecording>>();

vi.mock("@/platform", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/platform")>()),
  platform: {
    camera: {
      isSupported: () => isSupported(),
      openStream: (facing: CameraFacing) => openStream(facing),
      closeStream: (stream: MediaStream) => closeStream(stream),
      takePhoto: (source: HTMLVideoElement) => takePhoto(source),
      startRecording: (stream: MediaStream, maxDurationMs: number) => startRecording(stream, maxDurationMs),
    },
  },
}));

// Bước gửi (T-137) tải danh sách bạn và chuyển trang; ở đây chỉ thử phần chụp.
vi.mock("@/lib/api/friends", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api/friends")>()),
  friendsApi: { list: () => Promise.resolve({ items: [], count: 0, limit: 15 }) },
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

function fakeStream(name: string): MediaStream {
  return { id: name } as unknown as MediaStream;
}

const photo: CapturedMedia = {
  kind: "photo",
  blob: new Blob(["jpeg"], { type: "image/jpeg" }),
  contentType: "image/jpeg",
  width: 1280,
  height: 720,
  durationMs: null,
};

function renderCompose() {
  return render(
    <I18nProvider>
      <ComposeView />
    </I18nProvider>,
  );
}

describe("ComposeView capture (US-20, US-21)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    isSupported.mockReturnValue(true);
    openStream.mockImplementation((facing) => Promise.resolve(fakeStream(facing)));
    URL.createObjectURL = vi.fn(() => "blob:captured");
    URL.revokeObjectURL = vi.fn();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("opens the front camera and takes a photo, then lets me retake it", async () => {
    takePhoto.mockResolvedValue(photo);
    renderCompose();

    const shutter = await screen.findByRole("button", { name: t.compose.takePhoto });
    expect(openStream).toHaveBeenCalledWith("user");
    await userEvent.click(shutter);

    expect(await screen.findByRole("img", { name: t.compose.capturedPhoto })).toHaveAttribute("src", "blob:captured");
    // BR-09: nguồn chụp trực tiếp.
    expect(screen.getByText(t.compose.camera)).toBeInTheDocument();
    // Camera tắt khi xem lại để nhả thiết bị.
    expect(closeStream).toHaveBeenCalledWith(fakeStream("user"));

    await userEvent.click(screen.getByRole("button", { name: t.compose.retake }));

    expect(await screen.findByRole("button", { name: t.compose.takePhoto })).toBeInTheDocument();
    expect(openStream).toHaveBeenCalledTimes(2);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:captured");
  });

  it("switches between the front and back camera", async () => {
    renderCompose();
    await screen.findByRole("button", { name: t.compose.takePhoto });

    await userEvent.click(screen.getByRole("button", { name: t.compose.flip }));

    await vi.waitFor(() => expect(openStream).toHaveBeenLastCalledWith("environment"));
    expect(closeStream).toHaveBeenCalledWith(fakeStream("user"));
  });

  it("explains how to allow the camera when permission is denied, and retries", async () => {
    openStream.mockRejectedValueOnce(new CameraError("denied"));
    renderCompose();

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(t.compose.deniedTitle);
    expect(alert).toHaveTextContent(t.compose.deniedBody);

    await userEvent.click(screen.getByRole("button", { name: t.compose.retry }));

    expect(await screen.findByRole("button", { name: t.compose.takePhoto })).toBeInTheDocument();
    expect(openStream).toHaveBeenCalledTimes(2);
  });

  it("says the browser cannot use the camera without trying to open it", async () => {
    isSupported.mockReturnValue(false);
    renderCompose();

    expect(await screen.findByRole("alert")).toHaveTextContent(t.compose.unsupported);
    expect(openStream).not.toHaveBeenCalled();
  });

  it("records a video with a running timer and shows it when the recording ends", async () => {
    let finish: (media: CapturedMedia) => void = () => undefined;
    const stop = vi.fn();
    startRecording.mockResolvedValue({ stop, result: new Promise((resolve) => (finish = resolve)) });
    renderCompose();

    await userEvent.click(await screen.findByRole("radio", { name: t.compose.video }));
    vi.useFakeTimers({ shouldAdvanceTime: true });
    await userEvent.click(screen.getByRole("button", { name: t.compose.startRecording }));

    expect(startRecording).toHaveBeenCalledWith(fakeStream("user"), 5000);
    await act(async () => {
      vi.advanceTimersByTime(1500);
    });
    // Đồng hồ giả vẫn trôi theo giờ thật vài mili giây giữa các bước, nên chỉ chốt phần giây.
    expect(screen.getByRole("timer")).toHaveTextContent(/^Video · 1\.[5-9]s \/ 5s$/);

    await userEvent.click(screen.getByRole("button", { name: t.compose.stopRecording }));
    expect(stop).toHaveBeenCalled();
    await act(async () => {
      finish({ ...photo, kind: "video", contentType: "video/webm", durationMs: 1500 });
    });

    expect(await screen.findByLabelText(t.compose.capturedVideo)).toHaveAttribute("src", "blob:captured");
  });

  it("stays on the camera with a message when this browser cannot record", async () => {
    startRecording.mockRejectedValue(new CameraError("unsupported"));
    renderCompose();

    await userEvent.click(await screen.findByRole("radio", { name: t.compose.video }));
    await userEvent.click(screen.getByRole("button", { name: t.compose.startRecording }));

    expect(await screen.findByRole("alert")).toHaveTextContent(t.compose.recordUnsupported);
    expect(screen.getByRole("button", { name: t.compose.startRecording })).toBeInTheDocument();
  });

  it("releases the camera when leaving the page", async () => {
    const { unmount } = renderCompose();
    await screen.findByRole("button", { name: t.compose.takePhoto });

    unmount();

    expect(closeStream).toHaveBeenCalledWith(fakeStream("user"));
  });
});
