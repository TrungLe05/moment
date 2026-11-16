import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { I18nProvider } from "@/i18n/I18nProvider";
import { format, messages } from "@/i18n/messages";
import { ApiError } from "@/lib/api/errors";
import type { Moment, MomentPage } from "@/lib/api/moments";
import { FeedView } from "./feed-view";

const feed = vi.fn<(cursor: string | null) => Promise<MomentPage>>();
const detail = vi.fn<(id: string) => Promise<Moment>>();

vi.mock("@/lib/api/moments", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api/moments")>()),
  momentsApi: {
    feed: (cursor: string | null) => feed(cursor),
    detail: (id: string) => detail(id),
  },
}));

vi.mock("@/features/auth/session", () => ({
  useSession: () => ({ user: { friendCount: 3, friendLimit: 15 } }),
}));

vi.mock("next/link", () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}));

// jsdom báo ngôn ngữ en-US nên giao diện hiện tiếng Anh.
const t = messages.en;
const HOUR = 3_600_000;

function moment(overrides: Partial<Moment> = {}): Moment {
  const createdAt = new Date(Date.now() - 2 * HOUR);
  return {
    id: crypto.randomUUID(),
    isMine: false,
    owner: { id: "u2", username: "minhanh", displayName: "Minh Anh", avatarUrl: null },
    mediaType: "PHOTO",
    source: "CAMERA",
    mode: "SHARED",
    status: "READY",
    caption: "trời hôm nay đẹp quá",
    layers: [],
    layersVersion: 1,
    width: 1440,
    height: 1920,
    durationMs: null,
    createdAt: createdAt.toISOString(),
    expiresAt: new Date(createdAt.getTime() + 24 * HOUR).toISOString(),
    keptAt: null,
    media: { displayUrl: "https://storage.test/display.jpg", videoUrl: null, thumbnailUrl: null, urlsExpireAt: "" },
    myReaction: null,
    ownerView: null,
    ...overrides,
  };
}

function renderFeed() {
  return render(
    <I18nProvider>
      <FeedView />
    </I18nProvider>,
  );
}

describe("FeedView (US-30, US-31)", () => {
  beforeEach(() => {
    feed.mockReset();
    detail.mockReset();
  });

  it("shows each moment with its owner, age and time left", async () => {
    feed.mockResolvedValue({ items: [moment()], nextCursor: null });

    renderFeed();

    const card = await screen.findByRole("article", { name: format(t.feed.momentOf, { name: "Minh Anh" }) });
    expect(card).toHaveTextContent(format(t.feed.ownerLine, { name: "Minh Anh", age: format(t.feed.postedHours, { count: "2" }) }));
    expect(card).toHaveTextContent(format(t.feed.leftHours, { count: "21" }));
    expect(screen.getByRole("img", { name: "trời hôm nay đẹp quá" })).toHaveAttribute("src", "https://storage.test/display.jpg");
    expect(screen.getByText(t.feed.end)).toBeVisible();
    expect(screen.getByRole("link", { name: format(t.feed.friendCount, { count: "3", limit: "15" }) })).toHaveAttribute("href", "/friends");
  });

  it("labels kept and uploaded moments instead of a countdown (US-31, BR-09)", async () => {
    feed.mockResolvedValue({ items: [moment({ mode: "KEPT", source: "UPLOAD" })], nextCursor: null });

    renderFeed();

    const card = await screen.findByRole("article");
    expect(card).toHaveTextContent(t.feed.kept);
    expect(card).toHaveTextContent(t.feed.uploaded);
    expect(card).not.toHaveTextContent(format(t.feed.leftHours, { count: "21" }));
  });

  it("names my own moments as mine", async () => {
    feed.mockResolvedValue({ items: [moment({ isMine: true })], nextCursor: null });

    renderFeed();

    const card = await screen.findByRole("article", { name: t.feed.myMoment });
    expect(card).toHaveTextContent(t.feed.you);
  });

  it("takes the avatar initial by grapheme, not by UTF-16 unit", async () => {
    feed.mockResolvedValue({
      items: [moment({ owner: { id: "u3", username: "lan", displayName: "🌸 Lan", avatarUrl: null } })],
      nextCursor: null,
    });

    renderFeed();

    const card = await screen.findByRole("article");
    expect(card.textContent?.startsWith("🌸🌸 Lan")).toBe(true);
  });

  it("hides moments whose 24 hours are over without reloading (BR-14)", async () => {
    const expired = moment({ expiresAt: new Date(Date.now() - 1000).toISOString(), caption: "đã hết hạn" });
    feed.mockResolvedValue({ items: [expired, moment()], nextCursor: null });

    renderFeed();

    expect(await screen.findAllByRole("article")).toHaveLength(1);
    expect(screen.queryByRole("img", { name: "đã hết hạn" })).toBeNull();
  });

  it("suggests inviting friends when the feed is empty", async () => {
    feed.mockResolvedValue({ items: [], nextCursor: null });

    renderFeed();

    expect(await screen.findByRole("heading", { name: t.feed.emptyTitle })).toBeVisible();
    expect(screen.getByRole("link", { name: t.feed.invite })).toHaveAttribute("href", "/friends");
  });

  it("shows the error by code and retries the same page", async () => {
    feed.mockRejectedValueOnce(new ApiError(0, "NETWORK_ERROR")).mockResolvedValueOnce({ items: [moment()], nextCursor: null });

    renderFeed();

    expect(await screen.findByRole("alert")).toHaveTextContent(t.feed.loadError);
    await userEvent.click(screen.getByRole("button", { name: t.feed.retry }));

    expect(await screen.findByRole("article")).toBeVisible();
    expect(feed).toHaveBeenLastCalledWith(null);
  });

  it("fetches fresh media URLs when an image fails to load", async () => {
    const stale = moment();
    feed.mockResolvedValue({ items: [stale], nextCursor: null });
    const freshUrl = "https://storage.test/display-fresh.jpg";
    detail.mockResolvedValue({ ...stale, media: { ...stale.media!, displayUrl: freshUrl } });

    renderFeed();
    fireEvent.error(await screen.findByRole("img"));

    expect(detail).toHaveBeenCalledWith(stale.id);
    expect(await screen.findByRole("img")).toHaveAttribute("src", freshUrl);
  });

  it("loads the next page with the server cursor when the end comes into view", async () => {
    // jsdom không có IntersectionObserver: bản giả báo mốc cuối danh sách đã hiện ngay khi được quan sát.
    class VisibleObserver {
      constructor(private readonly callback: IntersectionObserverCallback) {}
      observe() {
        this.callback([{ isIntersecting: true } as IntersectionObserverEntry], this as unknown as IntersectionObserver);
      }
      disconnect() {}
    }
    vi.stubGlobal("IntersectionObserver", VisibleObserver);
    const first = moment({ caption: "trang một" });
    const second = moment({ caption: "trang hai" });
    feed.mockResolvedValueOnce({ items: [first], nextCursor: "opaque-1" }).mockResolvedValueOnce({ items: [second], nextCursor: null });

    try {
      renderFeed();

      expect(await screen.findByRole("img", { name: "trang hai" })).toBeVisible();
      expect(feed).toHaveBeenNthCalledWith(2, "opaque-1");
      expect(screen.getAllByRole("article")).toHaveLength(2);
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("drops a moment from the feed when access to it is gone", async () => {
    const gone = moment();
    feed.mockResolvedValue({ items: [gone], nextCursor: null });
    detail.mockRejectedValue(new ApiError(404, "NOT_FOUND"));

    renderFeed();
    fireEvent.error(await screen.findByRole("img"));

    await vi.waitFor(() => expect(screen.queryByRole("article")).toBeNull());
  });
});
