import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
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

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("next/link", () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}));

// Mật khẩu test sinh lúc chạy để không có chuỗi giống mật khẩu nằm cứng trong repo.
const testPassword = `pw-${crypto.randomUUID()}`;

function renderForm() {
  // jsdom báo ngôn ngữ en-US nên giao diện hiện tiếng Anh.
  return render(
    <I18nProvider>
      <LoginForm />
    </I18nProvider>,
  );
}

describe("LoginForm demo account (US-51)", () => {
  beforeEach(() => {
    login.mockReset();
    startSession.mockReset();
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("shows the demo sign-in details from the build settings and signs in with them", async () => {
    vi.stubEnv("NEXT_PUBLIC_DEMO_EMAIL", "demo@example.test");
    vi.stubEnv("NEXT_PUBLIC_DEMO_PASSWORD", testPassword);
    login.mockResolvedValue({ accessToken: "a", expiresIn: 900, user: { id: "demo" } });
    renderForm();

    const box = screen.getByRole("region", { name: messages.en.login.demoTitle });
    expect(box).toHaveTextContent("demo@example.test");
    expect(box).toHaveTextContent(testPassword);
    await userEvent.click(screen.getByRole("button", { name: messages.en.login.demoLogin }));

    expect(login).toHaveBeenCalledWith("demo@example.test", testPassword);
    expect(startSession).toHaveBeenCalled();
  });

  it("hides the demo box when no demo account is configured", () => {
    renderForm();

    expect(screen.queryByRole("region", { name: messages.en.login.demoTitle })).toBeNull();
  });
});

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
    await userEvent.type(screen.getByLabelText(messages.en.common.password), testPassword);
    await userEvent.click(screen.getByRole("button", { name: messages.en.login.submit }));

    expect(login).toHaveBeenCalledWith("a@b.co", testPassword);
    expect(startSession).toHaveBeenCalledWith(response);
  });

  it("shows the generic credentials message by error code", async () => {
    login.mockRejectedValue(new ApiError(401, "INVALID_CREDENTIALS"));
    renderForm();

    await userEvent.type(screen.getByLabelText(messages.en.common.email), "a@b.co");
    await userEvent.type(screen.getByLabelText(messages.en.common.password), testPassword);
    await userEvent.click(screen.getByRole("button", { name: messages.en.login.submit }));

    expect(await screen.findByRole("alert")).toHaveTextContent(messages.en.errors.INVALID_CREDENTIALS);
    expect(startSession).not.toHaveBeenCalled();
  });

  it("offers to resend the verification email when the email is not verified", async () => {
    login.mockRejectedValue(new ApiError(403, "EMAIL_NOT_VERIFIED"));
    resendVerification.mockResolvedValue(undefined);
    renderForm();

    await userEvent.type(screen.getByLabelText(messages.en.common.email), "a@b.co");
    await userEvent.type(screen.getByLabelText(messages.en.common.password), testPassword);
    await userEvent.click(screen.getByRole("button", { name: messages.en.login.submit }));
    await userEvent.click(await screen.findByRole("button", { name: messages.en.login.resendVerification }));

    expect(resendVerification).toHaveBeenCalledWith("a@b.co");
    expect(await screen.findByRole("status")).toHaveTextContent(messages.en.login.resendDone);
  });
});
