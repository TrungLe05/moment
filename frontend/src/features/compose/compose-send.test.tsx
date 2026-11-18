import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { I18nProvider } from "@/i18n/I18nProvider";
import { format, messages } from "@/i18n/messages";
import { ApiError } from "@/lib/api/errors";
import type { FriendList } from "@/lib/api/friends";
import type { Layer } from "@/features/layers/model";
import type { CapturedMedia } from "@/platform";
import { ComposeView } from "./compose-view";
import type { PendingDraft } from "./pending-moments";

const takePhoto = vi.fn<() => Promise<CapturedMedia>>();
const startRecording = vi.fn();
const listFriends = vi.fn<() => Promise<FriendList>>();
const submit = vi.fn<(draft: PendingDraft) => Promise<void>>();
const push = vi.fn<(href: string) => void>();
const exportComposed = vi.fn<(photo: Blob, layers: Layer[]) => Promise<Blob>>();

vi.mock("@/platform", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/platform")>()),
  platform: {
    camera: {
      isSupported: () => true,
      openStream: () => Promise.resolve({ id: "stream" } as unknown as MediaStream),
      closeStream: () => undefined,
      takePhoto: () => takePhoto(),
      startRecording: () => startRecording(),
    },
  },
}));

vi.mock("@/lib/api/friends", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api/friends")>()),
  friendsApi: { list: () => listFriends() },
}));

vi.mock("./pending-moments", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./pending-moments")>()),
  pendingMoments: { submit: (draft: PendingDraft) => submit(draft) },
}));

vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

// Canvas không có trong jsdom: xuất bản ghép thử trên trình duyệt thật.
vi.mock("@/features/layers/draw", () => ({
  exportComposed: (photo: Blob, layers: Layer[]) => exportComposed(photo, layers),
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

const t = messages.en;

const photoBlob = new Blob(["jpeg-bytes"], { type: "image/jpeg" });
const photo: CapturedMedia = { kind: "photo", blob: photoBlob, contentType: "image/jpeg", width: 1280, height: 720, durationMs: null };

function friend(id: string, displayName: string) {
  return { id, username: id, displayName, avatarUrl: null, friendsSince: "2026-11-01T00:00:00Z" };
}

const twoFriends: FriendList = { items: [friend("u2", "Minh Anh"), friend("u3", "Huy")], count: 2, limit: 15 };

async function captureAndOpenSendStep() {
  render(
    <I18nProvider>
      <ComposeView />
    </I18nProvider>,
  );
  await userEvent.click(await screen.findByRole("button", { name: t.compose.takePhoto }));
  await screen.findByRole("img", { name: t.compose.capturedPhoto });
}

describe("ComposeView send step (US-26, US-27, US-28)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    takePhoto.mockResolvedValue(photo);
    listFriends.mockResolvedValue(twoFriends);
    submit.mockResolvedValue(undefined);
    let created = 0;
    URL.createObjectURL = vi.fn(() => `blob:${++created}`);
    URL.revokeObjectURL = vi.fn();
  });

  // US-26 mặc định chia sẻ 24h, US-27 mặc định gửi tất cả bạn bè (BR-12).
  it("sends to everyone as a 24-hour share by default, then goes to the feed", async () => {
    await captureAndOpenSendStep();

    expect(screen.getByRole("radio", { name: t.compose.shared })).toBeChecked();
    expect(screen.getByText(t.compose.sharedHint)).toBeInTheDocument();
    const everyone = await screen.findByRole("button", { name: format(t.compose.everyone, { count: "2" }) });
    expect(everyone).toHaveAttribute("aria-pressed", "true");

    await userEvent.click(screen.getByRole("button", { name: t.compose.send }));

    expect(submit).toHaveBeenCalledWith({
      request: {
        mediaType: "PHOTO",
        source: "CAMERA",
        mode: "SHARED",
        audience: "ALL",
        recipientIds: [],
        caption: null,
        layers: [],
        layersVersion: 1,
        trimStartMs: null,
        files: [
          { variant: "ORIGINAL", contentType: "image/jpeg", sizeBytes: photoBlob.size },
          { variant: "COMPOSED", contentType: "image/jpeg", sizeBytes: photoBlob.size },
        ],
      },
      files: { ORIGINAL: photoBlob, COMPOSED: photoBlob },
      // URL riêng cho ảnh mờ ở feed (blob:1 là URL xem lại của trang soạn).
      previewUrl: "blob:2",
    });
    expect(push).toHaveBeenCalledWith("/");
    // Chưa có lớp thì bản ghép chính là ảnh chụp, không vẽ lại.
    expect(exportComposed).not.toHaveBeenCalled();
    expect(URL.revokeObjectURL).not.toHaveBeenCalledWith("blob:2");
  });

  // ADR 0002, T-136: ảnh có lớp trang trí thì gửi thêm bản ghép vẽ từ canvas; cột caption chép chữ của lớp caption.
  it("sends the composed image, the layers and the caption text", async () => {
    const composedBlob = new Blob(["composed-jpeg-bytes"], { type: "image/jpeg" });
    exportComposed.mockResolvedValue(composedBlob);
    await captureAndOpenSendStep();
    await userEvent.click(screen.getByRole("button", { name: t.compose.addCaption }));
    await userEvent.type(screen.getByRole("textbox", { name: t.compose.captionField }), "trời đẹp");
    await userEvent.click(screen.getByRole("button", { name: t.compose.done }));
    await screen.findByRole("button", { name: format(t.compose.everyone, { count: "2" }) });

    await userEvent.click(screen.getByRole("button", { name: t.compose.send }));

    expect(exportComposed).toHaveBeenCalledWith(photoBlob, [expect.objectContaining({ type: "caption", text: "trời đẹp" })]);
    const draft = submit.mock.calls[0]![0];
    expect(draft.request.caption).toBe("trời đẹp");
    expect(draft.request.layers).toEqual([expect.objectContaining({ type: "caption", text: "trời đẹp", style: "pill-dark" })]);
    expect(draft.request.files).toEqual([
      { variant: "ORIGINAL", contentType: "image/jpeg", sizeBytes: photoBlob.size },
      { variant: "COMPOSED", contentType: "image/jpeg", sizeBytes: composedBlob.size },
    ]);
    expect(draft.files).toEqual({ ORIGINAL: photoBlob, COMPOSED: composedBlob });
  });

  it("keeps the moment forever for the friends I pick", async () => {
    await captureAndOpenSendStep();

    await userEvent.click(screen.getByRole("radio", { name: t.compose.kept }));
    expect(screen.getByText(t.compose.keptHint)).toBeInTheDocument();
    await userEvent.click(await screen.findByRole("button", { name: "Huy" }));

    expect(screen.getByRole("button", { name: "Huy" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: format(t.compose.everyone, { count: "2" }) })).toHaveAttribute(
      "aria-pressed",
      "false",
    );

    await userEvent.click(screen.getByRole("button", { name: t.compose.send }));

    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({
        request: expect.objectContaining({ mode: "KEPT", audience: "SELECTED", recipientIds: ["u3"] }),
      }),
    );
  });

  // BR-12: bỏ chọn hết thì quay về "tất cả", không bao giờ gửi danh sách rỗng.
  it("goes back to everyone when the last picked friend is unpicked", async () => {
    await captureAndOpenSendStep();

    await userEvent.click(await screen.findByRole("button", { name: "Minh Anh" }));
    await userEvent.click(screen.getByRole("button", { name: "Minh Anh" }));

    expect(screen.getByRole("button", { name: format(t.compose.everyone, { count: "2" }) })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  // US-27: chưa có bạn thì không gửi được, có lối tắt sang trang mời.
  it("blocks sending without friends and links to the invite page", async () => {
    listFriends.mockResolvedValue({ items: [], count: 0, limit: 15 });
    await captureAndOpenSendStep();

    expect(await screen.findByText(t.compose.noFriends)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: t.compose.inviteFriends })).toHaveAttribute("href", "/friends");
    expect(screen.getByRole("button", { name: t.compose.send })).toBeDisabled();
  });

  it("shows a create error by code and stays on the page to try again", async () => {
    submit.mockRejectedValueOnce(new ApiError(422, "NOT_FRIENDS"));
    await captureAndOpenSendStep();
    await screen.findByRole("button", { name: format(t.compose.everyone, { count: "2" }) });

    await userEvent.click(screen.getByRole("button", { name: t.compose.send }));

    expect(await screen.findByRole("alert")).toHaveTextContent(t.errors.NOT_FRIENDS);
    expect(push).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: t.compose.send })).toBeEnabled();
    // URL xem trước tạo cho lần gửi hỏng được thu hồi ngay.
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:2");
  });

  it("sends a recorded video as a single original file", async () => {
    const videoBlob = new Blob(["webm-bytes-longer"], { type: "video/webm" });
    startRecording.mockResolvedValue({
      stop: () => undefined,
      result: Promise.resolve({ kind: "video", blob: videoBlob, contentType: "video/webm", width: 640, height: 480, durationMs: 4800 }),
    });
    render(
      <I18nProvider>
        <ComposeView />
      </I18nProvider>,
    );
    await userEvent.click(await screen.findByRole("radio", { name: t.compose.video }));
    await userEvent.click(screen.getByRole("button", { name: t.compose.startRecording }));
    await screen.findByLabelText(t.compose.capturedVideo);
    await screen.findByRole("button", { name: format(t.compose.everyone, { count: "2" }) });

    await userEvent.click(screen.getByRole("button", { name: t.compose.send }));

    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({
        request: expect.objectContaining({
          mediaType: "VIDEO",
          files: [{ variant: "ORIGINAL", contentType: "video/webm", sizeBytes: videoBlob.size }],
        }),
        files: { ORIGINAL: videoBlob },
      }),
    );
  });
});
