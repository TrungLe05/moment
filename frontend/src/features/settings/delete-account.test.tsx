import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { I18nProvider } from "@/i18n/I18nProvider";
import { messages } from "@/i18n/messages";
import { ApiError } from "@/lib/api/errors";
import { DeleteAccount } from "./delete-account";

const deleteAccount = vi.fn<(password: string | null) => Promise<void>>();
const loginWithGoogle = vi.fn();
const startSession = vi.fn();
const signOut = vi.fn<() => Promise<void>>();
const replace = vi.fn<(href: string) => void>();
let isDemo = false;

vi.mock("@/lib/api/account", () => ({
  accountApi: {
    deleteAccount: (password: string | null) => deleteAccount(password),
    loginWithGoogle: (...args: unknown[]) => loginWithGoogle(...args),
  },
}));

vi.mock("@/features/auth/session", () => ({
  useSession: () => ({ user: { isDemo }, startSession, signOut }),
}));

// Nút Google thật cần script của Google; bản giả trả về một ID token khi bấm.
vi.mock("@/features/auth/google-button", () => ({
  GoogleButton: ({ onIdToken }: { onIdToken: (idToken: string) => void }) => (
    <button type="button" onClick={() => onIdToken("google-id-token")}>
      google
    </button>
  ),
}));

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));

const t = messages.en;
// Mật khẩu test sinh lúc chạy để không có chuỗi giống mật khẩu nằm cứng trong repo.
const password = `pw-${crypto.randomUUID()}`;

function renderSection() {
  return render(
    <I18nProvider>
      <DeleteAccount />
    </I18nProvider>,
  );
}

describe("DeleteAccount (US-09)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    isDemo = false;
    signOut.mockResolvedValue(undefined);
  });

  it("deletes the account after the current password, then signs out to the sign-in page", async () => {
    deleteAccount.mockResolvedValue(undefined);
    renderSection();

    await userEvent.click(screen.getByRole("button", { name: t.settings.deleteOpen }));
    expect(screen.getByText(t.settings.deleteWarning)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(t.settings.deletePassword), password);
    await userEvent.click(screen.getByRole("button", { name: t.settings.deleteConfirm }));

    expect(deleteAccount).toHaveBeenCalledWith(password);
    expect(signOut).toHaveBeenCalled();
    expect(replace).toHaveBeenCalledWith("/login");
  });

  it("shows a wrong password by its code and keeps the session", async () => {
    deleteAccount.mockRejectedValue(new ApiError(401, "INVALID_CREDENTIALS"));
    renderSection();

    await userEvent.click(screen.getByRole("button", { name: t.settings.deleteOpen }));
    await userEvent.type(screen.getByLabelText(t.settings.deletePassword), password);
    await userEvent.click(screen.getByRole("button", { name: t.settings.deleteConfirm }));

    expect(await screen.findByRole("alert")).toHaveTextContent(t.errors.INVALID_CREDENTIALS);
    expect(signOut).not.toHaveBeenCalled();
  });

  // D-026: tài khoản chỉ có Google đăng nhập Google lại rồi xóa tiếp, không cần mật khẩu.
  it("asks a Google account to sign in with Google again, then deletes", async () => {
    deleteAccount.mockRejectedValueOnce(new ApiError(403, "REAUTH_REQUIRED")).mockResolvedValueOnce(undefined);
    const session = { accessToken: "fresh", expiresIn: 900, user: { id: "u1" } };
    loginWithGoogle.mockResolvedValue(session);
    renderSection();

    await userEvent.click(screen.getByRole("button", { name: t.settings.deleteOpen }));
    await userEvent.click(screen.getByRole("button", { name: t.settings.deleteConfirm }));
    expect(deleteAccount).toHaveBeenLastCalledWith(null);
    expect(await screen.findByText(t.settings.reauthGoogle)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "google" }));

    expect(loginWithGoogle).toHaveBeenCalledWith("google-id-token", "en");
    expect(startSession).toHaveBeenCalledWith(session);
    expect(deleteAccount).toHaveBeenCalledTimes(2);
    expect(replace).toHaveBeenCalledWith("/login");
  });

  // D-027: tài khoản demo không xóa được.
  it("does not offer deletion for the demo account", () => {
    isDemo = true;
    renderSection();

    expect(screen.queryByRole("button", { name: t.settings.deleteOpen })).toBeNull();
    expect(screen.getByText(t.settings.demoNoDelete)).toBeInTheDocument();
  });
});
