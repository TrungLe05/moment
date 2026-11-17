import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useSyncExternalStore } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { I18nProvider } from "@/i18n/I18nProvider";
import { messages } from "@/i18n/messages";
import { ApiError, NETWORK_ERROR } from "@/lib/api/errors";
import type { Moment, MomentPage } from "@/lib/api/moments";
import type { PendingMoment } from "@/features/compose/pending-moments";
import { FeedView } from "./feed-view";

const feed = vi.fn<(cursor: string | null) => Promise<MomentPage>>();

// Kho khoảnh khắc đang đăng giả: test đặt danh sách và phát sự kiện "đã đăng xong".
const fake = vi.hoisted(() => {
  let items: PendingMoment[] = [];
  const listeners = new Set<() => void>();
  const published = new Set<(moment: Moment) => void>();
  return {
    set(next: PendingMoment[]) {
      items = next;
      listeners.forEach((listener) => listener());
    },
    publish(moment: Moment) {
      published.forEach((listener) => listener(moment));
    },
    store: {
      subscribe: (listener: () => void) => {
        listeners.add(listener);
        return () => listeners.delete(listener);
      },
      getSnapshot: () => items,
      onPublished: (listener: (moment: Moment) => void) => {
        published.add(listener);
        return () => published.delete(listener);
      },
      retry: vi.fn<(localId: string) => void>(),
      cancel: vi.fn<(localId: string) => void>(),
    },
  };
});

vi.mock("@/features/compose/pending-moments", () => ({
  pendingMoments: fake.store,
  usePendingMoments: () => useSyncExternalStore(fake.store.subscribe, fake.store.getSnapshot, fake.store.getSnapshot),
}));

vi.mock("@/lib/api/moments", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api/moments")>()),
  momentsApi: { feed: (cursor: string | null) => feed(cursor) },
}));

vi.mock("@/features/auth/session", () => ({
  useSession: () => ({ user: { friendCount: 1, friendLimit: 15 } }),
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

const t = messages.en;

function pending(overrides: Partial<PendingMoment> = {}): PendingMoment {
  return {
    localId: "pending-1",
    momentId: "m1",
    mediaType: "PHOTO",
    previewUrl: "blob:preview",
    stage: "uploading",
    error: null,
    failureReason: null,
    ...overrides,
  };
}

function ready(id: string): Moment {
  const createdAt = new Date(Date.now() - 60_000);
  return {
    id,
    isMine: true,
    owner: { id: "me", username: "me", displayName: "Me", avatarUrl: null },
    mediaType: "PHOTO",
    source: "CAMERA",
    mode: "SHARED",
    status: "READY",
    caption: null,
    layers: [],
    layersVersion: 1,
    width: 1440,
    height: 1080,
    durationMs: null,
    createdAt: createdAt.toISOString(),
    expiresAt: new Date(createdAt.getTime() + 24 * 3_600_000).toISOString(),
    keptAt: null,
    media: { displayUrl: "https://storage.test/display.jpg", videoUrl: null, thumbnailUrl: null, urlsExpireAt: "" },
    myReaction: null,
    ownerView: { audience: "ALL", recipientCount: 1, reactionCount: 0, failureReason: null },
  };
}

function renderFeed() {
  return render(
    <I18nProvider>
      <FeedView />
    </I18nProvider>,
  );
}

describe("FeedView with moments being posted (US-28)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fake.set([]);
    feed.mockResolvedValue({ items: [], nextCursor: null });
  });

  // Ảnh xem trước tạo ở máy, làm mờ, không thanh tiến trình hay số phần trăm.
  it("shows the moment being posted blurred at the top, instead of the empty feed", async () => {
    fake.set([pending()]);
    renderFeed();

    const card = await screen.findByRole("article", { name: t.compose.pendingLabel });
    const preview = within(card).getByRole("img", { name: t.compose.pendingLabel });
    expect(preview).toHaveAttribute("src", "blob:preview");
    expect(preview.className).toContain("blur");
    expect(within(card).getByRole("status")).toHaveTextContent(t.compose.uploading);
    expect(within(card).queryByRole("progressbar")).toBeNull();
    expect(screen.queryByText(t.feed.emptyTitle)).toBeNull();
  });

  it("offers retry and cancel when posting failed", async () => {
    fake.set([pending({ stage: "upload-failed", error: new ApiError(0, NETWORK_ERROR) })]);
    renderFeed();

    const card = await screen.findByRole("article", { name: t.compose.pendingLabel });
    expect(within(card).getByRole("alert")).toHaveTextContent(t.errors.NETWORK_ERROR);
    await userEvent.click(within(card).getByRole("button", { name: t.compose.retryPost }));
    expect(fake.store.retry).toHaveBeenCalledWith("pending-1");
    await userEvent.click(within(card).getByRole("button", { name: t.compose.cancelPost }));
    expect(fake.store.cancel).toHaveBeenCalledWith("pending-1");
  });

  it("explains a processing failure by its reason", async () => {
    fake.set([pending({ stage: "processing-failed", failureReason: "VIDEO_TOO_LONG" })]);
    renderFeed();

    const card = await screen.findByRole("article", { name: t.compose.pendingLabel });
    expect(within(card).getByRole("alert")).toHaveTextContent(t.compose.VIDEO_TOO_LONG);
  });

  // Khi sẵn sàng, ảnh chuyển sang rõ nét: thẻ mờ biến mất, khoảnh khắc thật đứng đầu feed.
  it("puts the published moment at the top once it is ready", async () => {
    fake.set([pending({ stage: "processing" })]);
    renderFeed();
    await screen.findByRole("article", { name: t.compose.pendingLabel });

    act(() => {
      fake.set([]);
      fake.publish(ready("m1"));
    });

    const card = await screen.findByRole("article", { name: t.feed.myMoment });
    expect(within(card).getByRole("img")).toHaveAttribute("src", "https://storage.test/display.jpg");
    expect(screen.queryByRole("article", { name: t.compose.pendingLabel })).toBeNull();
  });
});
