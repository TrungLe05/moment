import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { I18nProvider } from "@/i18n/I18nProvider";
import { messages } from "@/i18n/messages";
import { ApiError } from "@/lib/api/errors";
import { LoginForm } from "./login-form";

const login = vi.fn();
const resendVerification = vi.fn();
const startSession = vi.fn();

vi.mock("@/lib/api/account", () => ({
  accountApi: {
    login: (...args: unknown[]) => login(...args),
    loginWithGoogle: vi.fn(),
    resendVerification: (...args: unknown[]) => resendVerification(...args),
  },
}));

vi.mock("./session", () => ({
  useSession: () => ({ startSession }),
}));

vi.mock("next/link", () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}));

function renderForm() {
  // jsdom báo ngôn ngữ en-US nên giao diện hiện tiếng Anh.
  return render(
    <I18nProvider>
      <LoginForm />
    </I18nProvider>,
  );
}

describe("LoginForm (US-03)", () => {
  beforeEach(() => {
    login.mockReset();
    resendVerification.mockReset();
    startSession.mockReset();
  });

  it("starts the session after a successful login", async () => {
    const response = { accessToken: "a", expiresIn: 900, user: { id: "u1" } };
    login.mockResolvedValue(response);
    renderForm();

    await userEvent.type(screen.getByLabelText(messages.en.common.email), "a@b.co");
    await userEvent.type(screen.getByLabelText(messages.en.common.password), "matkhau-123");
    await userEvent.click(screen.getByRole("button", { name: messages.en.login.submit }));

    expect(login).toHaveBeenCalledWith("a@b.co", "matkhau-123");
    expect(startSession).toHaveBeenCalledWith(response);
  });

  it("shows the generic credentials message by error code", async () => {
    login.mockRejectedValue(new ApiError(401, "INVALID_CREDENTIALS"));
    renderForm();

    await userEvent.type(screen.getByLabelText(messages.en.common.email), "a@b.co");
    await userEvent.type(screen.getByLabelText(messages.en.common.password), "wrong-password");
    await userEvent.click(screen.getByRole("button", { name: messages.en.login.submit }));

    expect(await screen.findByRole("alert")).toHaveTextContent(messages.en.errors.INVALID_CREDENTIALS);
    expect(startSession).not.toHaveBeenCalled();
  });

  it("offers to resend the verification email when the email is not verified", async () => {
    login.mockRejectedValue(new ApiError(403, "EMAIL_NOT_VERIFIED"));
    resendVerification.mockResolvedValue(undefined);
    renderForm();

    await userEvent.type(screen.getByLabelText(messages.en.common.email), "a@b.co");
    await userEvent.type(screen.getByLabelText(messages.en.common.password), "matkhau-123");
    await userEvent.click(screen.getByRole("button", { name: messages.en.login.submit }));
    await userEvent.click(await screen.findByRole("button", { name: messages.en.login.resendVerification }));

    expect(resendVerification).toHaveBeenCalledWith("a@b.co");
    expect(await screen.findByRole("status")).toHaveTextContent(messages.en.login.resendDone);
  });
});
