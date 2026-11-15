// Chuỗi giao diện song ngữ. Mỗi khóa phải có đủ bản vi và en (CLAUDE.md, quy ước frontend).
export const locales = ["vi", "en"] as const;

export type Locale = (typeof locales)[number];

export const defaultLocale: Locale = "vi";

const vi = {
  appName: "Khoảnh khắc",
  appDescription: "Chia sẻ khoảnh khắc 24 giờ với nhóm bạn thân.",
  common: {
    loading: "Đang tải…",
    email: "Email",
    password: "Mật khẩu",
    or: "hoặc",
    backToLogin: "Quay lại đăng nhập",
  },
  login: {
    title: "Đăng nhập",
    submit: "Đăng nhập",
    forgotPassword: "Quên mật khẩu?",
    noAccount: "Chưa có tài khoản?",
    toRegister: "Đăng ký",
    resendVerification: "Gửi lại email xác thực",
    resendDone: "Đã gửi lại email xác thực. Hãy kiểm tra hộp thư.",
    google: "Đăng nhập bằng Google",
  },
  register: {
    title: "Tạo tài khoản",
    submit: "Đăng ký",
    passwordHint: "Tối thiểu 8 ký tự.",
    haveAccount: "Đã có tài khoản?",
    toLogin: "Đăng nhập",
    checkInboxTitle: "Kiểm tra hộp thư",
    checkInboxBody: "Mở link trong email để xác thực tài khoản. Link có hiệu lực 24 giờ.",
  },
  verifyEmail: {
    title: "Xác thực email",
    verifying: "Đang xác thực…",
    success: "Email đã được xác thực. Bạn có thể đăng nhập.",
    resendTitle: "Gửi lại link xác thực",
    resendSubmit: "Gửi lại",
    resendDone: "Nếu email đã đăng ký và chưa xác thực, link mới đã được gửi.",
  },
  forgotPassword: {
    title: "Quên mật khẩu",
    description: "Nhập email đã đăng ký, chúng tôi sẽ gửi link đặt lại mật khẩu.",
    submit: "Gửi link",
    done: "Nếu email đã đăng ký, link đặt lại mật khẩu đã được gửi. Link có hiệu lực 1 giờ.",
  },
  resetPassword: {
    title: "Đặt mật khẩu mới",
    newPassword: "Mật khẩu mới",
    submit: "Lưu mật khẩu",
    done: "Đã đổi mật khẩu. Hãy đăng nhập lại.",
    missingToken: "Link không đầy đủ. Hãy mở lại link trong email.",
  },
  onboarding: {
    title: "Chọn tên của bạn",
    description: "Bạn bè sẽ thấy username và tên hiển thị này.",
    username: "Username",
    usernameHint: "3–20 ký tự: a-z, 0-9, dấu chấm, gạch dưới.",
    usernameTaken: "Username này đã có người dùng.",
    usernameAvailable: "Username còn trống.",
    displayName: "Tên hiển thị",
    submit: "Tiếp tục",
  },
  home: {
    greeting: "Chào {name}!",
    comingSoon: "Feed khoảnh khắc đang được xây dựng.",
    settings: "Cài đặt",
  },
  settings: {
    title: "Cài đặt",
    language: "Ngôn ngữ",
    vietnamese: "Tiếng Việt",
    english: "English",
    logout: "Đăng xuất",
    back: "Về trang chính",
  },
  errors: {
    VALIDATION_FAILED: "Dữ liệu chưa hợp lệ. Hãy kiểm tra lại.",
    TOKEN_INVALID: "Link không hợp lệ hoặc đã được dùng.",
    TOKEN_EXPIRED: "Link đã hết hạn.",
    UNAUTHENTICATED: "Phiên đăng nhập đã hết. Hãy đăng nhập lại.",
    INVALID_CREDENTIALS: "Email hoặc mật khẩu không đúng.",
    GOOGLE_TOKEN_INVALID: "Không đăng nhập được bằng Google. Hãy thử lại.",
    TOKEN_REUSED: "Để bảo vệ tài khoản, hãy đăng nhập lại.",
    EMAIL_NOT_VERIFIED: "Email chưa được xác thực.",
    EMAIL_TAKEN: "Email này đã được đăng ký.",
    USERNAME_TAKEN: "Username này đã có người dùng.",
    DEMO_ACCOUNT_READONLY: "Tài khoản demo không dùng được thao tác này.",
    LOGIN_LOCKED: "Sai quá nhiều lần. Hãy thử lại sau 15 phút.",
    RATE_LIMITED: "Bạn thao tác quá nhanh. Hãy đợi một lát.",
    NETWORK_ERROR: "Không kết nối được máy chủ. Hãy kiểm tra mạng.",
    UNKNOWN: "Đã có lỗi xảy ra. Hãy thử lại sau.",
  },
  fieldErrors: {
    REQUIRED: "Trường này bắt buộc.",
    INVALID_FORMAT: "Sai định dạng.",
    TOO_SHORT: "Quá ngắn.",
    TOO_LONG: "Quá dài.",
    INVALID: "Giá trị không hợp lệ.",
  },
};

// Kiểu lấy từ bản vi để bản en thiếu khóa nào là lỗi biên dịch ngay.
export type Messages = typeof vi;

const en: Messages = {
  appName: "Moment",
  appDescription: "Share 24-hour moments with your close friends.",
  common: {
    loading: "Loading…",
    email: "Email",
    password: "Password",
    or: "or",
    backToLogin: "Back to sign in",
  },
  login: {
    title: "Sign in",
    submit: "Sign in",
    forgotPassword: "Forgot password?",
    noAccount: "No account yet?",
    toRegister: "Sign up",
    resendVerification: "Resend verification email",
    resendDone: "Verification email sent again. Please check your inbox.",
    google: "Sign in with Google",
  },
  register: {
    title: "Create account",
    submit: "Sign up",
    passwordHint: "At least 8 characters.",
    haveAccount: "Already have an account?",
    toLogin: "Sign in",
    checkInboxTitle: "Check your inbox",
    checkInboxBody: "Open the link in the email to verify your account. The link is valid for 24 hours.",
  },
  verifyEmail: {
    title: "Verify email",
    verifying: "Verifying…",
    success: "Your email is verified. You can sign in now.",
    resendTitle: "Resend verification link",
    resendSubmit: "Resend",
    resendDone: "If the email is registered and not verified yet, a new link has been sent.",
  },
  forgotPassword: {
    title: "Forgot password",
    description: "Enter your email and we will send you a link to reset your password.",
    submit: "Send link",
    done: "If the email is registered, a reset link has been sent. The link is valid for 1 hour.",
  },
  resetPassword: {
    title: "Set a new password",
    newPassword: "New password",
    submit: "Save password",
    done: "Your password has been changed. Please sign in again.",
    missingToken: "The link is incomplete. Please open the link from the email again.",
  },
  onboarding: {
    title: "Choose your name",
    description: "Your friends will see this username and display name.",
    username: "Username",
    usernameHint: "3–20 characters: a-z, 0-9, dot, underscore.",
    usernameTaken: "This username is taken.",
    usernameAvailable: "This username is available.",
    displayName: "Display name",
    submit: "Continue",
  },
  home: {
    greeting: "Hi {name}!",
    comingSoon: "The moment feed is under construction.",
    settings: "Settings",
  },
  settings: {
    title: "Settings",
    language: "Language",
    vietnamese: "Tiếng Việt",
    english: "English",
    logout: "Sign out",
    back: "Back to home",
  },
  errors: {
    VALIDATION_FAILED: "Some fields are invalid. Please check again.",
    TOKEN_INVALID: "This link is invalid or has already been used.",
    TOKEN_EXPIRED: "This link has expired.",
    UNAUTHENTICATED: "Your session has ended. Please sign in again.",
    INVALID_CREDENTIALS: "Incorrect email or password.",
    GOOGLE_TOKEN_INVALID: "Could not sign in with Google. Please try again.",
    TOKEN_REUSED: "For your security, please sign in again.",
    EMAIL_NOT_VERIFIED: "Your email is not verified yet.",
    EMAIL_TAKEN: "This email is already registered.",
    USERNAME_TAKEN: "This username is taken.",
    DEMO_ACCOUNT_READONLY: "This action is not available for the demo account.",
    LOGIN_LOCKED: "Too many failed attempts. Try again in 15 minutes.",
    RATE_LIMITED: "You are going too fast. Please wait a moment.",
    NETWORK_ERROR: "Cannot reach the server. Please check your connection.",
    UNKNOWN: "Something went wrong. Please try again later.",
  },
  fieldErrors: {
    REQUIRED: "This field is required.",
    INVALID_FORMAT: "Invalid format.",
    TOO_SHORT: "Too short.",
    TOO_LONG: "Too long.",
    INVALID: "Invalid value.",
  },
};

export const messages: Record<Locale, Messages> = { vi, en };

/** Thay {tên} trong chuỗi bằng giá trị tương ứng. */
export function format(template: string, values: Record<string, string>): string {
  return template.replace(/\{(\w+)\}/g, (match, key: string) => values[key] ?? match);
}
