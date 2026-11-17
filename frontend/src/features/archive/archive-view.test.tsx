import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { I18nProvider } from "@/i18n/I18nProvider";
import { format, messages } from "@/i18n/messages";
import { ApiError } from "@/lib/api/errors";
import type { ArchiveScope, Moment, MomentPage } from "@/lib/api/moments";
import { ArchiveView } from "./archive-view";

const archive = vi.fn<(scope: ArchiveScope, cursor: string | null) => Promise<MomentPage>>();
const replace = vi.fn<(href: string, options?: { scroll?: boolean }) => void>();
let searchParams = new URLSearchParams();

vi.mock("@/lib/api/moments", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api/moments")>()),
  momentsApi: {
    archive: (scope: ArchiveScope, cursor: string | null) => archive(scope, cursor),
  },
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => searchParams,
  useRouter: () => ({ replace }),
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

// jsdom báo ngôn ngữ en-US nên giao diện hiện tiếng Anh.
const t = messages.en;

function kept(createdAt: string, overrides: Partial<Moment> = {}): Moment {
  return {
    id: crypto.randomUUID(),
    isMine: true,
    owner: { id: "u1", username: "me", displayName: "Me", avatarUrl: null },
    mediaType: "PHOTO",
    source: "CAMERA",
    mode: "KEPT",
    status: "READY",
    caption: null,
    layers: [],
    layersVersion: 1,
    width: 1440,
    height: 1920,
    durationMs: null,
    createdAt,
    expiresAt: createdAt,
    keptAt: createdAt,
    media: { displayUrl: "https://storage.test/d.jpg", videoUrl: null, thumbnailUrl: "https://storage.test/t.jpg", urlsExpireAt: "" },
    myReaction: null,
    ownerView: null,
    ...overrides,
  };
}

function renderArchive() {
  return render(
    <I18nProvider>
      <ArchiveView />
    </I18nProvider>,
  );
}

describe("ArchiveView (US-40, US-41)", () => {
  beforeEach(() => {
    archive.mockReset();
    replace.mockReset();
    searchParams = new URLSearchParams();
  });

  it("groups my kept moments by month, newest first, each opening its detail", async () => {
    const november = kept("2026-11-15T10:00:00Z");
    const october = kept("2026-10-02T10:00:00Z", { source: "UPLOAD" });
    archive.mockResolvedValue({ items: [november, october], nextCursor: null });

    renderArchive();

    const months = await screen.findAllByRole("heading", { level: 3 });
    expect(months.map((heading) => heading.textContent)).toEqual(["November 2026", "October 2026"]);
    expect(archive).toHaveBeenCalledWith("mine", null);
    expect(screen.getByRole("tab", { name: t.archive.mine })).toHaveAttribute("aria-selected", "true");
    const link = screen.getByRole("link", { name: format(t.archive.open, { date: "November 15, 2026" }) });
    expect(link).toHaveAttribute("href", `/archive/moment?id=${november.id}&from=mine`);
    // BR-09: khoảnh khắc tải lên luôn có nhãn "tải lên".
    const octoberSection = months[1]!.closest("section")!;
    expect(within(octoberSection).getByText(t.feed.uploaded)).toBeInTheDocument();
  });

  it("switches to the received tab and keeps it in the address", async () => {
    archive.mockResolvedValueOnce({ items: [], nextCursor: null });
    const fromFriend = kept("2026-11-10T10:00:00Z", {
      isMine: false,
      owner: { id: "u2", username: "minhanh", displayName: "Minh Anh", avatarUrl: null },
    });
    archive.mockResolvedValueOnce({ items: [fromFriend], nextCursor: null });

    renderArchive();
    expect(await screen.findByText(t.archive.emptyMineTitle)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: t.archive.received }));

    expect(await screen.findByRole("link", { name: format(t.archive.open, { date: "November 10, 2026" }) })).toHaveAttribute(
      "href",
      `/archive/moment?id=${fromFriend.id}&from=received`,
    );
    expect(archive).toHaveBeenLastCalledWith("received", null);
    expect(replace).toHaveBeenCalledWith("/archive?tab=received", { scroll: false });
    expect(screen.getByRole("tab", { name: t.archive.received })).toHaveAttribute("aria-selected", "true");
  });

  it("opens the tab named in the address", async () => {
    searchParams = new URLSearchParams("tab=received");
    archive.mockResolvedValue({ items: [], nextCursor: null });

    renderArchive();

    expect(await screen.findByText(t.archive.emptyReceivedTitle)).toBeInTheDocument();
    expect(archive).toHaveBeenCalledWith("received", null);
  });

  it("reports a load error by code and retries", async () => {
    archive.mockRejectedValueOnce(new ApiError(429, "RATE_LIMITED"));
    archive.mockResolvedValueOnce({ items: [kept("2026-11-15T10:00:00Z")], nextCursor: null });

    renderArchive();

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(t.errors.RATE_LIMITED);
    await userEvent.click(within(alert).getByRole("button", { name: t.archive.retry }));

    expect(await screen.findByRole("heading", { level: 3, name: "November 2026" })).toBeInTheDocument();
  });
});
