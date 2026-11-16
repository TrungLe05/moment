import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { I18nProvider } from "@/i18n/I18nProvider";
import { format, messages } from "@/i18n/messages";
import type { FriendList as FriendListData } from "@/lib/api/friends";
import { FriendList } from "./friend-list";

const t = messages.en;

const data: FriendListData = {
  items: [{ id: "u2", username: "minhanh", displayName: "Minh Anh", avatarUrl: null, friendsSince: "2026-11-16T02:30:00Z" }],
  count: 1,
  limit: 15,
};

function renderList(onUnfriend: (userId: string) => Promise<void>, list: FriendListData = data) {
  return render(
    <I18nProvider>
      <FriendList data={list} onUnfriend={onUnfriend} />
    </I18nProvider>,
  );
}

describe("FriendList (US-12, US-13)", () => {
  it("shows the friend count against the limit", () => {
    renderList(vi.fn());

    expect(screen.getByRole("heading", { name: format(t.friends.listTitle, { count: "1", limit: "15" }) })).toBeVisible();
    expect(screen.getByText("Minh Anh")).toBeVisible();
  });

  it("asks for confirmation before removing a friend", async () => {
    const onUnfriend = vi.fn<(userId: string) => Promise<void>>().mockResolvedValue(undefined);
    renderList(onUnfriend);

    await userEvent.click(screen.getByRole("button", { name: `${t.friends.unfriend}: Minh Anh` }));
    expect(onUnfriend).not.toHaveBeenCalled();
    expect(await screen.findByRole("alertdialog")).toHaveTextContent(format(t.friends.unfriendTitle, { name: "Minh Anh" }));

    await userEvent.click(screen.getByRole("button", { name: t.friends.confirm }));

    expect(onUnfriend).toHaveBeenCalledWith("u2");
  });

  it("does nothing when the confirmation is cancelled", async () => {
    const onUnfriend = vi.fn<(userId: string) => Promise<void>>();
    renderList(onUnfriend);

    await userEvent.click(screen.getByRole("button", { name: `${t.friends.unfriend}: Minh Anh` }));
    await userEvent.click(await screen.findByRole("button", { name: t.friends.cancel }));

    expect(onUnfriend).not.toHaveBeenCalled();
  });

  it("shows the empty state", () => {
    renderList(vi.fn(), { items: [], count: 0, limit: 15 });

    expect(screen.getByText(t.friends.empty)).toBeVisible();
  });
});
