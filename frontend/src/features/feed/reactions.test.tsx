import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { I18nProvider } from "@/i18n/I18nProvider";
import { format, messages } from "@/i18n/messages";
import { ApiError } from "@/lib/api/errors";
import type { Moment, MomentPage, ReactionItem } from "@/lib/api/moments";
import { FeedView } from "./feed-view";

const feed = vi.fn<(cursor: string | null) => Promise<MomentPage>>();
const react = vi.fn<(id: string, emoji: string) => Promise<{ emoji: string }>>();
const unreact = vi.fn<(id: string) => Promise<void>>();
const reactions = vi.fn<(id: string) => Promise<ReactionItem[]>>();

vi.mock("@/lib/api/moments", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/moments")>();
  return {
    ...actual,
    momentsApi: {
      feed: (cursor: string | null) => feed(cursor),
      detail: vi.fn(),
      react: (id: string, emoji: string) => react(id, emoji),
      unreact: (id: string) => unreact(id),
      reactions: (id: string) => reactions(id),
    },
  };
});

vi.mock("@/features/auth/session", () => ({
  useSession: () => ({ user: { friendCount: 3, friendLimit: 15 } }),
}));

vi.mock("next/link", () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}));

const t = messages.en;
const HOUR = 3_600_000;

function moment(overrides: Partial<Moment> = {}): Moment {
  const createdAt = new Date(Date.now() - HOUR);
  return {
    id: "m1",
    isMine: false,
    owner: { id: "u2", username: "minhanh", displayName: "Minh Anh", avatarUrl: null },
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
    ownerView: null,
    ...overrides,
  };
}

const fireLabel = format(t.feed.reactWith, { name: t.feed.emojiNames["🔥"] });
const heartLabel = format(t.feed.reactWith, { name: t.feed.emojiNames["❤️"] });

function renderFeed() {
  return render(
    <I18nProvider>
      <FeedView />
    </I18nProvider>,
  );
}

describe("Reactions in the feed (US-32, US-33, BR-21 → BR-23)", () => {
  beforeEach(() => {
    for (const mock of [feed, react, unreact, reactions]) mock.mockReset();
  });

  it("offers the six fixed emoji on a friend's moment and marks mine", async () => {
    feed.mockResolvedValue({ items: [moment({ myReaction: "🔥" })], nextCursor: null });

    renderFeed();

    const bar = await screen.findByRole("group", { name: t.feed.reactions });
    expect(within(bar).getAllByRole("button")).toHaveLength(6);
    expect(within(bar).getByRole("button", { name: fireLabel })).toHaveAttribute("aria-pressed", "true");
    expect(within(bar).getByRole("button", { name: heartLabel })).toHaveAttribute("aria-pressed", "false");
  });

  it("sets a new reaction, replacing the previous one (BR-21)", async () => {
    feed.mockResolvedValue({ items: [moment({ myReaction: "🔥" })], nextCursor: null });
    react.mockResolvedValue({ emoji: "❤️" });

    renderFeed();
    await userEvent.click(await screen.findByRole("button", { name: heartLabel }));

    expect(react).toHaveBeenCalledWith("m1", "❤️");
    expect(screen.getByRole("button", { name: heartLabel })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: fireLabel })).toHaveAttribute("aria-pressed", "false");
  });

  it("removes the reaction when the same emoji is tapped again (BR-21)", async () => {
    feed.mockResolvedValue({ items: [moment({ myReaction: "🔥" })], nextCursor: null });
    unreact.mockResolvedValue(undefined);

    renderFeed();
    await userEvent.click(await screen.findByRole("button", { name: fireLabel }));

    expect(unreact).toHaveBeenCalledWith("m1");
    expect(react).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: fireLabel })).toHaveAttribute("aria-pressed", "false");
  });

  it("keeps the latest choice when an earlier request answers last", async () => {
    feed.mockResolvedValue({ items: [moment()], nextCursor: null });
    let answerFire: (value: { emoji: string }) => void = () => undefined;
    react
      .mockImplementationOnce(() => new Promise((resolve) => (answerFire = resolve)))
      .mockResolvedValueOnce({ emoji: "❤️" });

    renderFeed();
    await userEvent.click(await screen.findByRole("button", { name: fireLabel }));
    await userEvent.click(screen.getByRole("button", { name: heartLabel }));
    answerFire({ emoji: "🔥" });

    await vi.waitFor(() => expect(react).toHaveBeenCalledTimes(2));
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(screen.getByRole("button", { name: heartLabel })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: fireLabel })).toHaveAttribute("aria-pressed", "false");
  });

  it("restores the previous reaction and explains when the request fails", async () => {
    feed.mockResolvedValue({ items: [moment({ myReaction: "🔥" })], nextCursor: null });
    react.mockRejectedValue(new ApiError(0, "NETWORK_ERROR"));

    renderFeed();
    await userEvent.click(await screen.findByRole("button", { name: heartLabel }));

    expect(await screen.findByRole("alert")).toHaveTextContent(t.feed.reactionError);
    expect(screen.getByRole("button", { name: fireLabel })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: heartLabel })).toHaveAttribute("aria-pressed", "false");
  });

  // BR-23: chủ không tự thả; BR-22, US-33: chủ xem ai thả gì.
  it("shows the owner who reacted with what instead of the emoji bar", async () => {
    feed.mockResolvedValue({
      items: [
        moment({
          isMine: true,
          ownerView: { audience: "ALL", recipientCount: 2, reactionCount: 2, failureReason: null },
        }),
      ],
      nextCursor: null,
    });
    reactions.mockResolvedValue([
      { user: { id: "u3", username: "lan", displayName: "Lan", avatarUrl: null }, emoji: "😍", reactedAt: "" },
      { user: { id: "u4", username: "binh", displayName: null, avatarUrl: null }, emoji: "🔥", reactedAt: "" },
    ]);

    renderFeed();

    expect(await screen.findByRole("article")).toBeVisible();
    expect(screen.queryByRole("group", { name: t.feed.reactions })).toBeNull();
    const toggle = screen.getByRole("button", { name: format(t.feed.reactionCount, { count: "2" }) });
    expect(toggle).toHaveAttribute("aria-expanded", "false");

    await userEvent.click(toggle);

    expect(reactions).toHaveBeenCalledWith("m1");
    const list = await screen.findByRole("list", { name: t.feed.reactions });
    expect(within(list).getAllByRole("listitem").map((item) => item.textContent)).toEqual(["Lan😍", "binh🔥"]);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
  });

  it("does not offer the list to the owner while nobody has reacted", async () => {
    feed.mockResolvedValue({
      items: [moment({ isMine: true, ownerView: { audience: "ALL", recipientCount: 1, reactionCount: 0, failureReason: null } })],
      nextCursor: null,
    });

    renderFeed();

    expect(await screen.findByText(t.feed.noReactions)).toBeVisible();
    expect(screen.queryByRole("button", { name: format(t.feed.reactionCount, { count: "0" }) })).toBeNull();
  });
});
