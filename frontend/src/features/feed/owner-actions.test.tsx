import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { I18nProvider } from "@/i18n/I18nProvider";
import { messages } from "@/i18n/messages";
import { ApiError } from "@/lib/api/errors";
import type { Moment, MomentPage } from "@/lib/api/moments";
import { FeedView } from "./feed-view";

const feed = vi.fn<(cursor: string | null) => Promise<MomentPage>>();
const keep = vi.fn<(id: string) => Promise<Moment>>();
const remove = vi.fn<(id: string) => Promise<void>>();

vi.mock("@/lib/api/moments", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api/moments")>()),
  momentsApi: {
    feed: (cursor: string | null) => feed(cursor),
    detail: vi.fn(),
    keep: (id: string) => keep(id),
    remove: (id: string) => remove(id),
  },
}));

vi.mock("@/features/auth/session", () => ({
  useSession: () => ({ user: { friendCount: 3, friendLimit: 15 } }),
}));

vi.mock("next/link", () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}));

const t = messages.en;
const HOUR = 3_600_000;

function mine(overrides: Partial<Moment> = {}): Moment {
  const createdAt = new Date(Date.now() - HOUR);
  return {
    id: "m1",
    isMine: true,
    owner: { id: "u1", username: "lan", displayName: "Lan", avatarUrl: null },
    mediaType: "PHOTO",
    source: "CAMERA",
    mode: "SHARED",
    status: "READY",
    caption: null,
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
    ownerView: { audience: "ALL", recipientCount: 1, reactionCount: 0, failureReason: null },
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

describe("Owner actions in the feed (US-34, US-35)", () => {
  beforeEach(() => {
    for (const mock of [feed, keep, remove]) mock.mockReset();
  });

  it("keeps a shared moment and swaps the countdown for the kept label (BR-16)", async () => {
    const moment = mine();
    feed.mockResolvedValue({ items: [moment], nextCursor: null });
    keep.mockResolvedValue({ ...moment, mode: "KEPT", keptAt: new Date().toISOString() });

    renderFeed();
    await userEvent.click(await screen.findByRole("button", { name: t.feed.keep }));

    expect(keep).toHaveBeenCalledWith("m1");
    expect(await screen.findByText(t.feed.kept)).toBeVisible();
    expect(screen.queryByRole("button", { name: t.feed.keep })).toBeNull();
  });

  it("does not offer keeping a moment that is already kept", async () => {
    feed.mockResolvedValue({ items: [mine({ mode: "KEPT", keptAt: new Date().toISOString() })], nextCursor: null });

    renderFeed();

    expect(await screen.findByRole("button", { name: t.feed.delete })).toBeVisible();
    expect(screen.queryByRole("button", { name: t.feed.keep })).toBeNull();
  });

  it("explains by error code when the moment can no longer be kept", async () => {
    feed.mockResolvedValue({ items: [mine()], nextCursor: null });
    keep.mockRejectedValue(new ApiError(409, "MOMENT_NOT_KEEPABLE"));

    renderFeed();
    await userEvent.click(await screen.findByRole("button", { name: t.feed.keep }));

    expect(await screen.findByRole("alert")).toHaveTextContent(t.errors.MOMENT_NOT_KEEPABLE);
  });

  it("asks before deleting and removes the moment only after confirming (BR-18)", async () => {
    feed.mockResolvedValue({ items: [mine()], nextCursor: null });
    remove.mockResolvedValue(undefined);

    renderFeed();
    await userEvent.click(await screen.findByRole("button", { name: t.feed.delete }));

    expect(remove).not.toHaveBeenCalled();
    expect(await screen.findByRole("alertdialog")).toHaveTextContent(t.feed.deleteTitle);
    await userEvent.click(screen.getByRole("button", { name: t.feed.deleteConfirm }));

    expect(remove).toHaveBeenCalledWith("m1");
    await vi.waitFor(() => expect(screen.queryByRole("article")).toBeNull());
  });

  it("keeps the moment when deleting is cancelled", async () => {
    feed.mockResolvedValue({ items: [mine()], nextCursor: null });

    renderFeed();
    await userEvent.click(await screen.findByRole("button", { name: t.feed.delete }));
    await userEvent.click(await screen.findByRole("button", { name: t.feed.cancel }));

    expect(remove).not.toHaveBeenCalled();
    expect(screen.getByRole("article")).toBeVisible();
  });

  it("has no keep or delete on a friend's moment", async () => {
    feed.mockResolvedValue({ items: [mine({ isMine: false, ownerView: null })], nextCursor: null });

    renderFeed();

    expect(await screen.findByRole("article")).toBeVisible();
    expect(screen.queryByRole("button", { name: t.feed.keep })).toBeNull();
    expect(screen.queryByRole("button", { name: t.feed.delete })).toBeNull();
  });
});
