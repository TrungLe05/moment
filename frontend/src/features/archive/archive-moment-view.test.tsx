import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { I18nProvider } from "@/i18n/I18nProvider";
import { format, messages } from "@/i18n/messages";
import { ApiError } from "@/lib/api/errors";
import type { Moment } from "@/lib/api/moments";
import { ArchiveMomentView } from "./archive-moment-view";

const detail = vi.fn<(id: string) => Promise<Moment>>();
let searchParams = new URLSearchParams();

vi.mock("@/lib/api/moments", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api/moments")>()),
  momentsApi: { detail: (id: string) => detail(id) },
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => searchParams,
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

const t = messages.en;

function kept(overrides: Partial<Moment> = {}): Moment {
  return {
    id: "m1",
    isMine: false,
    owner: { id: "u2", username: "minhanh", displayName: "Minh Anh", avatarUrl: null },
    mediaType: "PHOTO",
    source: "CAMERA",
    mode: "KEPT",
    status: "READY",
    caption: "trời hôm nay đẹp quá",
    layers: [],
    layersVersion: 1,
    width: 1440,
    height: 1920,
    durationMs: null,
    createdAt: "2026-10-02T10:00:00Z",
    expiresAt: "2026-10-03T10:00:00Z",
    keptAt: "2026-10-02T11:00:00Z",
    media: { displayUrl: "https://storage.test/d.jpg", videoUrl: null, thumbnailUrl: "https://storage.test/t.jpg", urlsExpireAt: "" },
    myReaction: null,
    ownerView: null,
    ...overrides,
  };
}

function renderDetail() {
  return render(
    <I18nProvider>
      <ArchiveMomentView />
    </I18nProvider>,
  );
}

describe("ArchiveMomentView (US-42)", () => {
  beforeEach(() => {
    detail.mockReset();
    searchParams = new URLSearchParams("id=m1&from=received");
  });

  it("shows the full photo, owner, caption and posting date", async () => {
    detail.mockResolvedValue(kept());

    renderDetail();

    const article = await screen.findByRole("article", { name: format(t.feed.momentOf, { name: "Minh Anh" }) });
    expect(detail).toHaveBeenCalledWith("m1");
    expect(screen.getByRole("img", { name: "trời hôm nay đẹp quá" })).toHaveAttribute("src", "https://storage.test/d.jpg");
    expect(article).toHaveTextContent(format(t.archive.postedOn, { date: "October 2, 2026" }));
    expect(article).toHaveTextContent(t.feed.kept);
    expect(screen.getByRole("link", { name: t.archive.backToArchive })).toHaveAttribute("href", "/archive?tab=received");
  });

  it("plays a video with controls and shows its caption", async () => {
    detail.mockResolvedValue(
      kept({
        mediaType: "VIDEO",
        source: "UPLOAD",
        media: { displayUrl: null, videoUrl: "https://storage.test/v.mp4", thumbnailUrl: "https://storage.test/t.jpg", urlsExpireAt: "" },
      }),
    );

    const { container } = renderDetail();

    const article = await screen.findByRole("article");
    const video = container.querySelector("video");
    expect(video).toHaveAttribute("src", "https://storage.test/v.mp4");
    expect(video).toHaveAttribute("controls");
    expect(article).toHaveTextContent("trời hôm nay đẹp quá");
    // BR-09: khoảnh khắc tải lên luôn có nhãn "tải lên".
    expect(article).toHaveTextContent(t.feed.uploaded);
  });

  it("says the moment cannot be found when access is gone", async () => {
    detail.mockRejectedValue(new ApiError(404, "NOT_FOUND"));

    renderDetail();

    expect(await screen.findByRole("alert")).toHaveTextContent(t.errors.NOT_FOUND);
  });

  it("goes back to the mine tab by default", async () => {
    searchParams = new URLSearchParams("id=m1");
    detail.mockResolvedValue(kept({ isMine: true }));

    renderDetail();

    await screen.findByRole("article", { name: t.feed.myMoment });
    expect(screen.getByRole("link", { name: t.archive.backToArchive })).toHaveAttribute("href", "/archive");
  });
});
